/*
 Copyright (C) 2020 Lew Wei Hao
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.meshers.*;
import org.jquantlib.methods.finitedifferences.operators.FdmCIROp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm2DimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.processes.CoxIngersollRossProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

/**
 * Finite-differences vanilla-option engine with stochastic short rate (Cox-Ingersoll-Ross) and Black-Scholes equity
 * dynamics.
 * <p>
 * Java port of v1.42.1 {@code ql/pricingengines/vanilla/fdcirvanillaengine.{hpp,cpp}}.
 * <p>
 * The 2-D PDE is rolled back on a (log-S, r) grid using a configurable ADI scheme (default ModifiedHundsdorfer per
 * C++). Greeks {@code delta}, {@code gamma}, and {@code theta} come from the bicubic-spline derivatives of
 * {@link Fdm2DimSolver} in log-spot space (with the chain-rule jacobian applied for delta/gamma).
 *
 * <h3>Constructor variants</h3>
 * <ul>
 *   <li>{@link #FdCIRVanillaEngine(CoxIngersollRossProcess, GeneralizedBlackScholesProcess,
 *       int, int, int, int, double, FdmSchemeDesc)} — full grid parameters.</li>
 *   <li>{@link #FdCIRVanillaEngine(CoxIngersollRossProcess, GeneralizedBlackScholesProcess,
 *       DividendSchedule, int, int, int, int, double, FdmSchemeDesc)} — with discrete
 *       dividends (Spot model).</li>
 * </ul>
 *
 * <h3>Limitations vs. C++ v1.42.1</h3>
 * <ul>
 *   <li>{@code FdmQuantoHelper} not yet ported — quanto path omitted.</li>
 *   <li>{@code LocalVolTermStructure} leverage function not yet ported —
 *       constant-vol Black-Scholes only.</li>
 * </ul>
 *
 * @author Phase 5e.5b-CFC-d-86 port
 */
public class FdCIRVanillaEngine extends OneAssetOption.EngineImpl {

    private final CoxIngersollRossProcess cirProcess;
    private final GeneralizedBlackScholesProcess bsProcess;
    private final DividendSchedule dividends;
    private final int tGrid, xGrid, rGrid, dampingSteps;
    private final double rho;
    private final FdmSchemeDesc schemeDesc;

    private final OneAssetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;

    // ----------------------------------------------------------------
    // constructors
    // ----------------------------------------------------------------

    /** Full constructor without dividends. */
    public FdCIRVanillaEngine(final CoxIngersollRossProcess cirProcess, final GeneralizedBlackScholesProcess bsProcess,
            final int tGrid, final int xGrid, final int rGrid, final int dampingSteps, final double rho,
            final FdmSchemeDesc schemeDesc) {
        this(cirProcess, bsProcess, null, tGrid, xGrid, rGrid, dampingSteps, rho, schemeDesc);
    }

    /** Full constructor mirroring C++ v1.42.1. */
    public FdCIRVanillaEngine(final CoxIngersollRossProcess cirProcess, final GeneralizedBlackScholesProcess bsProcess,
            final DividendSchedule dividends, final int tGrid, final int xGrid, final int rGrid, final int dampingSteps,
            final double rho, final FdmSchemeDesc schemeDesc) {
        super();
        QL.require(cirProcess != null, "null CIR process");
        QL.require(bsProcess != null, "null BS process");
        QL.require(schemeDesc != null, "null scheme descriptor");

        this.cirProcess = cirProcess;
        this.bsProcess = bsProcess;
        this.dividends = (dividends != null) ? dividends : new DividendSchedule();
        this.tGrid = tGrid;
        this.xGrid = xGrid;
        this.rGrid = rGrid;
        this.dampingSteps = dampingSteps;
        this.rho = rho;
        this.schemeDesc = schemeDesc;

        this.a = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.greeks = this.r.greeks();
    }

    // ----------------------------------------------------------------
    // public solver descriptor (mirrors C++ getSolverDesc)
    // ----------------------------------------------------------------

    /**
     * Build the {@link FdmSolverDesc} for the configured grid sizes.
     *
     * @param equityScaleFactor scale factor (unused in this port — mirrors the C++ signature which also ignores it
     *                          since the quanto helper is not wired)
     */
    public FdmSolverDesc getSolverDesc(final double equityScaleFactor) {
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        QL.require(payoff != null, "non-striked payoff given");

        final double maturity = bsProcess.time(a.exercise.lastDate());

        // The short-rate mesher (CIR)
        final Fdm1dMesher shortRateMesher = new FdmSimpleProcess1dMesher(rGrid, cirProcess, maturity, tGrid, 0.0001,
                Double.NaN);

        // The equity mesher (log-spot Black-Scholes)
        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(xGrid, bsProcess, maturity, payoff.strike(),
                Double.NaN, Double.NaN, 0.0001, 1.5, payoff.strike(), 0.1, dividends, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher, shortRateMesher);

        // Calculator
        final FdmLogInnerValue calculator = new FdmLogInnerValue(a.payoff, mesher, 0);

        // Step conditions
        final Date refDate = bsProcess.riskFreeRate().currentLink().referenceDate();
        final DayCounter dc = bsProcess.riskFreeRate().currentLink().dayCounter();

        final FdmStepConditionComposite conditions = FdmStepConditionComposite.vanillaComposite(dividends, a.exercise,
                mesher, calculator, refDate, dc);

        // Boundary conditions (empty)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        return new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid, dampingSteps);
    }

    // ----------------------------------------------------------------
    // PricingEngine
    // ----------------------------------------------------------------

    @Override
    public void calculate() {
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;

        final FdmSolverDesc solverDesc = getSolverDesc(1.5);

        // FdmCIRSolver wiring — inlined here since the only consumer is
        // this engine. Mirrors C++ FdmCIRSolver::performCalculations.
        final FdmCIROp op = new FdmCIROp(solverDesc.mesher, cirProcess, bsProcess, rho, payoff.strike());
        final Fdm2DimSolver solver = new Fdm2DimSolver(solverDesc, schemeDesc, op);

        final double r0 = cirProcess.x0();
        final double spot = bsProcess.x0();
        final double logS = JQuantMath.log(spot);

        this.r.value = solver.interpolateAt(logS, r0);
        this.greeks.delta = solver.derivativeX(logS, r0) / spot;
        this.greeks.gamma = (solver.derivativeXX(logS, r0) - solver.derivativeX(logS, r0)) / (spot * spot);
        this.greeks.theta = solver.thetaAt(logS, r0);
    }
}
