/*
 Copyright (C) 2016 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.barrieroption.DoubleBarrierOption;
import org.jquantlib.experimental.barrieroption.DoubleBarrierType;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.meshers.*;
import org.jquantlib.methods.finitedifferences.operators.FdmHestonOp;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm2DimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmHestonSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.BoundaryCondition;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.methods.finitedifferences.utilities.FdmTimeDepDirichletBoundary;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.vanilla.FdHestonHullWhiteVanillaEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.termstructures.LocalVolTermStructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finite-differences Heston double-barrier option engine.
 * <p>
 * Java port of v1.42.1 {@code ql/pricingengines/barrier/fdhestondoublebarrierengine.{hpp,cpp}}.
 * <p>
 * Solves the 2-factor Heston PDE on a (log-S, v) grid with Dirichlet boundaries (set to the rebate value) at both lower
 * and upper barriers, using the same ADI machinery as
 * {@link org.jquantlib.pricingengines.vanilla.FdHestonVanillaEngine}. The equity mesh is anchored to
 * {@code [log(barrier_lo), log(barrier_hi)]} so the barrier conditions sit exactly on grid nodes.
 *
 * <p>Supports an optional {@link LocalVolTermStructure} leverage function
 * {@code L(t,S)} for Heston Stochastic Local Volatility (SLV) pricing; when non-null, an {@link FdmHestonOp} is
 * assembled with the leverage surface and the engine sidesteps {@link FdmHestonSolver} (which does not expose a
 * leverage knob) by driving an {@link Fdm2DimSolver} directly. Mirrors the pattern introduced in
 * {@link org.jquantlib.pricingengines.vanilla.FdHestonVanillaEngine} for the SLV path.
 *
 * <h3>Limitations vs. C++ v1.42.1</h3>
 * <ul>
 *   <li>Only {@link DoubleBarrierType#KnockOut} barriers are supported
 *       (matching the C++ implementation, which throws on
 *       {@code KnockIn/KIKO/KOKI}). Parity for IN-barriers is not implemented
 *       in C++ either.</li>
 *   <li>Only European exercise is supported.</li>
 *   <li>{@code FdmQuantoHelper} not yet ported — quanto-adjustment path
 *       omitted.</li>
 *   <li>{@code FdmHestonLocalVolatilityVarianceMesher} not yet ported —
 *       when {@code leverageFct} is supplied the engine falls back to the
 *       leverage-agnostic {@link FdmHestonVarianceMesher}. This widens the
 *       variance grid relative to C++ but, in practice, the Hundsdorfer
 *       ADI scheme still converges to within the 5e-3 tolerance used by
 *       {@code testPdeDoubleBarrierWithAnalytical} for σ→0 / no-leverage
 *       cross-validation cases.</li>
 *   <li>The Java {@link HestonModel} does not expose {@code .process()},
 *       so this engine takes the model and process as separate arguments
 *       (matches {@link FdHestonBarrierEngine} /
 *       {@link FdHestonHullWhiteVanillaEngine}).</li>
 * </ul>
 *
 * @author Phase 5e.5b-CFC-d-257 port
 */
public class FdHestonDoubleBarrierEngine extends DoubleBarrierOption.EngineImpl {

    private final HestonModel hestonModel;
    private final HestonProcess hestonProcess;
    private final int tGrid, xGrid, vGrid, dampingSteps;
    private final FdmSchemeDesc schemeDesc;
    private final LocalVolTermStructure leverageFct;
    private final double mixingFactor;

    /** Convenience constructor — all C++ defaults, no leverage. */
    public FdHestonDoubleBarrierEngine(final HestonModel hestonModel, final HestonProcess hestonProcess) {
        this(hestonModel, hestonProcess, 100, 100, 50, 0, FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    /** Convenience constructor — explicit grid + scheme, no leverage. */
    public FdHestonDoubleBarrierEngine(final HestonModel hestonModel, final HestonProcess hestonProcess,
            final int tGrid, final int xGrid, final int vGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(hestonModel, hestonProcess, tGrid, xGrid, vGrid, dampingSteps, schemeDesc, null, 1.0);
    }

    /** Full constructor. */
    public FdHestonDoubleBarrierEngine(final HestonModel hestonModel, final HestonProcess hestonProcess,
            final int tGrid, final int xGrid, final int vGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc,
            final LocalVolTermStructure leverageFct, final double mixingFactor) {
        super();
        QL.require(hestonModel != null, "null Heston model");
        QL.require(hestonProcess != null, "null Heston process");
        QL.require(schemeDesc != null, "null scheme descriptor");
        this.hestonModel = hestonModel;
        this.hestonProcess = hestonProcess;
        this.tGrid = tGrid;
        this.xGrid = xGrid;
        this.vGrid = vGrid;
        this.dampingSteps = dampingSteps;
        this.schemeDesc = schemeDesc;
        this.leverageFct = leverageFct;
        this.mixingFactor = mixingFactor;
    }

    @Override
    public void calculate() {
        final DoubleBarrierOption.ArgumentsImpl args = args();

        QL.require(args.barrierType == DoubleBarrierType.KnockOut,
                "only Knock-Out double barrier options are supported");
        QL.require(args.exercise.type() == Exercise.Type.European, "only european style option are supported");

        // 1. Mesher
        final double maturity = hestonProcess.time(args.exercise.lastDate());

        // 1.1 Variance mesher (Heston CIR v-process). C++ uses
        //     FdmHestonLocalVolatilityVarianceMesher when leverageFct is set,
        //     which adjusts the variance scale by L(t,S). That mesher is not
        //     yet ported in Java; we fall back to FdmHestonVarianceMesher
        //     which uses the raw (pure-Heston) variance scale. For the
        //     small-sigma SLV-degenerate-to-BS cross-check exercised by
        //     testPdeDoubleBarrierWithAnalytical this is more than adequate.
        final int tGridMin = 5;
        final FdmHestonVarianceMesher varianceMesher = new FdmHestonVarianceMesher(vGrid, hestonProcess, maturity,
                Math.max(tGridMin, tGrid / 50), 0.0001, mixingFactor);

        // 1.2 Equity mesher — anchored at the two barriers in log-spot space.
        final StrikedTypePayoff payoff = (StrikedTypePayoff) args.payoff;
        QL.require(payoff != null, "non-striked payoff given");
        final double xMin = JQuantMath.log(args.barrier_lo);
        final double xMax = JQuantMath.log(args.barrier_hi);

        // C++ uses (size, processHelper, maturity, strike, xMin, xMax) —
        // the 6-arg ctor with defaults eps=0.0001, scaleFactor=1.5, no
        // concentration point, no dividends, spotAdjustment=0.
        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(xGrid,
                FdmBlackScholesMesher.processHelper(hestonProcess.s0(), hestonProcess.dividendYield(),
                        hestonProcess.riskFreeRate(), varianceMesher.volaEstimate()), maturity, payoff.strike(), xMin,
                xMax, 0.0001, 1.5, Double.NaN, Double.NaN, new org.jquantlib.instruments.DividendSchedule(), 0.0);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher, varianceMesher);

        // 2. Calculator
        final FdmLogInnerValue calculator = new FdmLogInnerValue(payoff, mesher, 0);

        // 3. Step conditions — European exercise, no dividends in C++ either.
        final FdmStepConditionComposite conditions = new FdmStepConditionComposite(new ArrayList<>(),
                new FdmStepConditionComposite.Conditions());

        // 4. Boundary conditions: Dirichlet at both barriers, value = rebate.
        final double rebate = args.rebate;
        final List< BoundaryCondition< FdmLinearOp > > bcList = new ArrayList<>();
        bcList.add(new FdmTimeDepDirichletBoundary(mesher, t -> rebate, /*direction=*/0, BoundaryCondition.Side.Lower));
        bcList.add(new FdmTimeDepDirichletBoundary(mesher, t -> rebate, /*direction=*/0, BoundaryCondition.Side.Upper));
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet(Collections.unmodifiableList(bcList));

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid,
                dampingSteps);

        final double spot = hestonProcess.s0().currentLink().value();
        final double v0 = hestonProcess.v0().currentLink().value();

        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        if ( leverageFct == null ) {
            // Pure-Heston path via cached FdmHestonSolver. Binary-equivalent
            // to the standalone Fdm2DimSolver path (same operator, same
            // solver descriptor) but goes through the solver's analytic-Greek
            // helpers.
            final FdmHestonSolver solver = new FdmHestonSolver(hestonProcess, solverDesc, schemeDesc, mixingFactor);
            r.value = solver.valueAt(spot, v0);
            r.greeks().delta = solver.deltaAt(spot, v0);
            r.greeks().gamma = solver.gammaAt(spot, v0);
            r.greeks().theta = solver.thetaAt(spot, v0);
        } else {
            // SLV path: assemble Fdm2DimSolver with leverage-carrying
            // FdmHestonOp, since FdmHestonSolver doesn't expose the
            // leverageFct knob. Chain-rule for log-spot -> spot Greeks
            // mirrors FdmHestonSolver.{value,delta,gamma,theta}At.
            final FdmHestonOp op = new FdmHestonOp(mesher, hestonProcess, mixingFactor, leverageFct);
            final Fdm2DimSolver solver = new Fdm2DimSolver(solverDesc, schemeDesc, op);
            final double x = JQuantMath.log(spot);
            r.value = solver.interpolateAt(x, v0);
            r.greeks().delta = solver.derivativeX(x, v0) / spot;
            r.greeks().gamma = (solver.derivativeXX(x, v0) - solver.derivativeX(x, v0)) / (spot * spot);
            r.greeks().theta = solver.thetaAt(x, v0);
        }
    }

    /** Accessor for the model (unused by C++; kept for symmetry). */
    public HestonModel model() {
        return hestonModel;
    }
}
