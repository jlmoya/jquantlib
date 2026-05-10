/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008, 2009 Ralph Schreyer
 Copyright (C) 2008, 2009, 2015 Klaus Spanderen
 Copyright (C) 2015 Johannes Göttker-Schnetmann

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
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmHestonVarianceMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmHestonSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.processes.HestonProcess;

/**
 * Finite-differences Heston vanilla option engine.
 * <p>
 * Java port of v1.42.1
 * {@code ql/pricingengines/vanilla/fdhestonvanillaengine.{hpp,cpp}}.
 * <p>
 * Solves the 2-factor Heston PDE on a (log-S, v) grid using a configurable
 * ADI scheme (default Hundsdorfer).
 *
 * <h3>Constructor variants</h3>
 * <ul>
 *   <li>{@link #FdHestonVanillaEngine(HestonModel, HestonProcess)} — all defaults
 *       (tGrid=100, xGrid=100, vGrid=50, dampingSteps=0, scheme=Hundsdorfer).</li>
 *   <li>{@link #FdHestonVanillaEngine(HestonModel, HestonProcess, int, int, int, int, FdmSchemeDesc)}
 *       — full grid parameters.</li>
 *   <li>{@link #FdHestonVanillaEngine(HestonModel, HestonProcess, DividendSchedule, int, int, int, int, FdmSchemeDesc)}
 *       — with discrete dividends.</li>
 * </ul>
 *
 * <h3>Limitations vs. C++ v1.42.1</h3>
 * <ul>
 *   <li>{@code FdmQuantoHelper} not yet ported — quanto-adjustment path omitted.</li>
 *   <li>{@code LocalVolTermStructure} leverage function not yet ported — pure-Heston only.</li>
 *   <li>Multiple-strikes caching ({@code enableMultipleStrikesCaching}) not implemented.</li>
 *   <li>The Java {@link HestonModel} does not expose {@code .process()},
 *       so the engine takes the model and process as separate constructor
 *       arguments (matching {@link FdHestonHullWhiteVanillaEngine}).</li>
 * </ul>
 *
 * @author Phase 4n.5 port
 */
public class FdHestonVanillaEngine
        extends GenericModelEngine<HestonModel,
                                   OneAssetOption.Arguments,
                                   OneAssetOption.Results> {

    private final HestonProcess hestonProcess;
    private final DividendSchedule dividends;
    private final int tGrid, xGrid, vGrid, dampingSteps;
    private final FdmSchemeDesc schemeDesc;
    private final double mixingFactor;

    /** Convenience constructor — all C++ defaults, no dividends. */
    public FdHestonVanillaEngine(final HestonModel hestonModel,
                                 final HestonProcess hestonProcess) {
        this(hestonModel, hestonProcess, null,
                100, 100, 50, 0, FdmSchemeDesc.Hundsdorfer(), 1.0);
    }

    /** Convenience constructor — explicit grid + scheme, no dividends. */
    public FdHestonVanillaEngine(final HestonModel hestonModel,
                                 final HestonProcess hestonProcess,
                                 final int tGrid,
                                 final int xGrid,
                                 final int vGrid,
                                 final int dampingSteps,
                                 final FdmSchemeDesc schemeDesc) {
        this(hestonModel, hestonProcess, null,
                tGrid, xGrid, vGrid, dampingSteps, schemeDesc, 1.0);
    }

    /** Full constructor — explicit grid + dividends + mixing. */
    public FdHestonVanillaEngine(final HestonModel hestonModel,
                                 final HestonProcess hestonProcess,
                                 final DividendSchedule dividends,
                                 final int tGrid,
                                 final int xGrid,
                                 final int vGrid,
                                 final int dampingSteps,
                                 final FdmSchemeDesc schemeDesc,
                                 final double mixingFactor) {
        super(hestonModel,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        QL.require(hestonModel != null,    "null Heston model");
        QL.require(hestonProcess != null,  "null Heston process");
        QL.require(schemeDesc != null,     "null scheme descriptor");
        this.hestonProcess = hestonProcess;
        this.dividends     = (dividends != null) ? dividends : new DividendSchedule();
        this.tGrid         = tGrid;
        this.xGrid         = xGrid;
        this.vGrid         = vGrid;
        this.dampingSteps  = dampingSteps;
        this.schemeDesc    = schemeDesc;
        this.mixingFactor  = mixingFactor;
    }

    /**
     * Build the {@link FdmSolverDesc} for the configured grid sizes. Public
     * helper to allow callers (e.g. {@code FdHestonBarrierEngine}) to share
     * the meshing logic.
     *
     * @return solver descriptor with mesher / boundaries / step conditions /
     *         calculator / maturity / time-grid / damping-steps fields
     */
    public FdmSolverDesc getSolverDesc() {
        final OneAssetOption.ArgumentsImpl args =
                (OneAssetOption.ArgumentsImpl) arguments_;

        final double maturity = hestonProcess.time(args.exercise.lastDate());

        // 1.1 Variance mesher (Heston CIR v-process)
        final int tGridMin = 5;
        final FdmHestonVarianceMesher varianceMesher = new FdmHestonVarianceMesher(
                vGrid, hestonProcess, maturity,
                Math.max(tGridMin, tGrid / 50), 0.0001);

        // 1.2 Equity mesher (log-spot)
        final StrikedTypePayoff payoff = (StrikedTypePayoff) args.payoff;
        QL.require(payoff != null, "non-striked payoff given");

        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid,
                FdmBlackScholesMesher.processHelper(
                        hestonProcess.s0(),
                        hestonProcess.dividendYield(),
                        hestonProcess.riskFreeRate(),
                        varianceMesher.volaEstimate()),
                maturity, payoff.strike(),
                dividends, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher, varianceMesher);

        // 2. Calculator
        final FdmLogInnerValue calculator =
                new FdmLogInnerValue(args.payoff, mesher, 0);

        // 3. Step conditions (American/Bermudan/dividends — all handled by helper)
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividends, args.exercise, mesher, calculator,
                        hestonProcess.riskFreeRate().currentLink().referenceDate(),
                        hestonProcess.riskFreeRate().currentLink().dayCounter());

        // 4. Boundary conditions (none)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        return new FdmSolverDesc(mesher, boundaries, conditions, calculator,
                maturity, tGrid, dampingSteps);
    }

    @Override
    public void calculate() {
        final FdmSolverDesc solverDesc = getSolverDesc();
        final FdmHestonSolver solver = new FdmHestonSolver(
                hestonProcess, solverDesc, schemeDesc, mixingFactor);

        final double spot = hestonProcess.s0().currentLink().value();
        final double v0   = hestonProcess.v0().currentLink().value();

        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;
        r.value             = solver.valueAt(spot, v0);
        r.greeks().delta    = solver.deltaAt(spot, v0);
        r.greeks().gamma    = solver.gammaAt(spot, v0);
        r.greeks().theta    = solver.thetaAt(spot, v0); // NaN until snapshot-theta wired
    }
}
