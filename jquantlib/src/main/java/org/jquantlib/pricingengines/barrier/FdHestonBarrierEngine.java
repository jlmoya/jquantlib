/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008, 2009 Ralph Schreyer
 Copyright (C) 2008, 2009 Klaus Spanderen

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmHestonVarianceMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmHestonSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.BoundaryCondition;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmDividendHandler;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.methods.finitedifferences.utilities.FdmTimeDepDirichletBoundary;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.vanilla.FdHestonVanillaEngine;
import org.jquantlib.processes.HestonProcess;

/**
 * Finite-differences Heston barrier-option engine.
 * <p>
 * Java port of v1.42.1
 * {@code ql/pricingengines/barrier/fdhestonbarrierengine.{hpp,cpp}}.
 * <p>
 * Solves the 2-factor Heston PDE on a (log-S, v) grid with a Dirichlet
 * boundary at the barrier (set to the rebate value), using the same ADI
 * machinery as {@link org.jquantlib.pricingengines.vanilla.FdHestonVanillaEngine}.
 *
 * <p>All four barrier types are supported. In-barriers are computed via
 * parity {@code IN = vanilla + rebate - OUT}, delegating to
 * {@link FdHestonRebateEngine} for the rebate-on-touch leg and
 * {@link org.jquantlib.pricingengines.vanilla.FdHestonVanillaEngine
 * FdHestonVanillaEngine} for the unconstrained vanilla NPV.
 *
 * <h3>Limitations vs. C++ v1.42.1</h3>
 * <ul>
 *   <li>Only European exercise is supported (American/Bermudan barriers
 *       require additional step-condition wiring).</li>
 *   <li>{@code FdmQuantoHelper} not yet ported — quanto-adjustment path omitted.</li>
 *   <li>{@code LocalVolTermStructure} leverage function not yet ported — pure-Heston only.</li>
 *   <li>The Java {@link HestonModel} does not expose {@code .process()}, so this
 *       engine takes the model and process as separate arguments (matches
 *       the existing {@code FdHestonHullWhiteVanillaEngine} pattern).</li>
 * </ul>
 *
 * @author Phase 4n.5 port
 */
public class FdHestonBarrierEngine extends BarrierOption.EngineImpl {

    private final HestonModel hestonModel;
    private final HestonProcess hestonProcess;
    private final DividendSchedule dividends;
    private final int tGrid, xGrid, vGrid, dampingSteps;
    private final FdmSchemeDesc schemeDesc;
    private final double mixingFactor;

    /** Convenience — all C++ defaults, no dividends. */
    public FdHestonBarrierEngine(final HestonModel hestonModel,
                                 final HestonProcess hestonProcess) {
        this(hestonModel, hestonProcess, null,
                100, 100, 50, 0, FdmSchemeDesc.Hundsdorfer(), 1.0);
    }

    /** Convenience — explicit grid + scheme, no dividends. */
    public FdHestonBarrierEngine(final HestonModel hestonModel,
                                 final HestonProcess hestonProcess,
                                 final int tGrid,
                                 final int xGrid,
                                 final int vGrid,
                                 final int dampingSteps,
                                 final FdmSchemeDesc schemeDesc) {
        this(hestonModel, hestonProcess, null,
                tGrid, xGrid, vGrid, dampingSteps, schemeDesc, 1.0);
    }

    /** Full constructor. */
    public FdHestonBarrierEngine(final HestonModel hestonModel,
                                 final HestonProcess hestonProcess,
                                 final DividendSchedule dividends,
                                 final int tGrid,
                                 final int xGrid,
                                 final int vGrid,
                                 final int dampingSteps,
                                 final FdmSchemeDesc schemeDesc,
                                 final double mixingFactor) {
        super();
        QL.require(hestonModel   != null, "null Heston model");
        QL.require(hestonProcess != null, "null Heston process");
        QL.require(schemeDesc    != null, "null scheme descriptor");
        this.hestonModel   = hestonModel;
        this.hestonProcess = hestonProcess;
        this.dividends     = (dividends != null) ? dividends : new DividendSchedule();
        this.tGrid         = tGrid;
        this.xGrid         = xGrid;
        this.vGrid         = vGrid;
        this.dampingSteps  = dampingSteps;
        this.schemeDesc    = schemeDesc;
        this.mixingFactor  = mixingFactor;
    }

    @Override
    public void calculate() {
        final BarrierOption.ArgumentsImpl args =
                (BarrierOption.ArgumentsImpl) arguments_;

        QL.require(args.exercise.type() == Exercise.Type.European,
                "only European-style barrier options are supported");

        final BarrierType barrier = args.barrierType;

        // 1. Mesher
        final double maturity = hestonProcess.time(args.exercise.lastDate());

        // 1.1 Variance mesher
        final int tGridMin = 5;
        final FdmHestonVarianceMesher varianceMesher = new FdmHestonVarianceMesher(
                vGrid, hestonProcess, maturity,
                Math.max(tGridMin, tGrid / 50), 0.0001);

        // 1.2 Equity mesher with barrier-aligned bounds. Per C++ v1.42.1,
        //     Down{In,Out} both anchor xMin and Up{In,Out} both anchor xMax;
        //     the FD solution is the OUT value (rebate-on-survival), and the
        //     IN value is recovered below via parity.
        final StrikedTypePayoff payoff = (StrikedTypePayoff) args.payoff;
        QL.require(payoff != null, "non-striked payoff given");

        final double xMin = (barrier == BarrierType.DownOut
                          || barrier == BarrierType.DownIn)
                ? JQuantMath.log(args.barrier) : Double.NaN;
        final double xMax = (barrier == BarrierType.UpOut
                          || barrier == BarrierType.UpIn)
                ? JQuantMath.log(args.barrier) : Double.NaN;

        // C++ uses scale=1.5, eps=0.0001, no concentration point (Null<Real>).
        // Our equity-mesher constructor accepts NaN for "no constraint" and
        // null cPoint via the long form below.
        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid,
                FdmBlackScholesMesher.processHelper(
                        hestonProcess.s0(),
                        hestonProcess.dividendYield(),
                        hestonProcess.riskFreeRate(),
                        varianceMesher.volaEstimate()),
                maturity, payoff.strike(),
                xMin, xMax,
                0.0001, 1.5,
                Double.NaN, Double.NaN,
                dividends, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher, varianceMesher);

        // 2. Calculator
        final FdmLogInnerValue calculator = new FdmLogInnerValue(payoff, mesher, 0);

        // 3. Step conditions (only dividends if any; European exercise so no
        //    early-exercise condition).
        final FdmStepConditionComposite.Conditions stepConditions =
                new FdmStepConditionComposite.Conditions();
        final List<List<Double>> stoppingTimes = new ArrayList<List<Double>>();

        if (!dividends.isEmpty()) {
            final FdmDividendHandler dividendCondition = new FdmDividendHandler(
                    dividends, mesher,
                    hestonProcess.riskFreeRate().currentLink().referenceDate(),
                    hestonProcess.riskFreeRate().currentLink().dayCounter(),
                    /*equityDirection=*/0);
            stepConditions.add(dividendCondition);
            // exclude times after maturity, mirror C++.
            final List<Double> divTimes = new ArrayList<Double>();
            for (final double t : dividendCondition.dividendTimes()) {
                divTimes.add(Math.min(maturity, t));
            }
            stoppingTimes.add(divTimes);
        }

        final FdmStepConditionComposite conditions =
                new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 4. Boundary conditions: Dirichlet at the barrier with constant rebate.
        //    Both Down{In,Out} take Lower; both Up{In,Out} take Upper.
        final List<BoundaryCondition<FdmLinearOp>> bcList =
                new ArrayList<BoundaryCondition<FdmLinearOp>>();
        final double rebateValue = args.rebate;
        if (barrier == BarrierType.DownOut || barrier == BarrierType.DownIn) {
            bcList.add(new FdmTimeDepDirichletBoundary(
                    mesher, t -> rebateValue, /*direction=*/0,
                    BoundaryCondition.Side.Lower));
        }
        if (barrier == BarrierType.UpOut || barrier == BarrierType.UpIn) {
            bcList.add(new FdmTimeDepDirichletBoundary(
                    mesher, t -> rebateValue, /*direction=*/0,
                    BoundaryCondition.Side.Upper));
        }
        final FdmBoundaryConditionSet boundaries =
                new FdmBoundaryConditionSet(Collections.unmodifiableList(bcList));

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid, dampingSteps);

        final FdmHestonSolver solver = new FdmHestonSolver(
                hestonProcess, solverDesc, schemeDesc, mixingFactor);

        final double spot = hestonProcess.s0().currentLink().value();
        final double v0   = hestonProcess.v0().currentLink().value();

        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;
        r.value          = solver.valueAt(spot, v0);
        r.greeks().delta = solver.deltaAt(spot, v0);
        r.greeks().gamma = solver.gammaAt(spot, v0);
        r.greeks().theta = solver.thetaAt(spot, v0);

        // 6. In-barriers via parity: IN = vanilla + rebate - OUT.
        //    The FD computation above produces the OUT value (the rebate
        //    is paid on barrier non-touch). For Down*In/Up*In we add a
        //    standalone vanilla NPV and the rebate-on-touch leg, and
        //    subtract the OUT value already in r.value.
        if (barrier == BarrierType.DownIn || barrier == BarrierType.UpIn) {
            // Standalone vanilla option.
            final VanillaOption vanillaOption = new VanillaOption(payoff, args.exercise);
            vanillaOption.setPricingEngine(new FdHestonVanillaEngine(
                    hestonModel, hestonProcess, dividends,
                    tGrid, xGrid, vGrid, dampingSteps, schemeDesc, mixingFactor));

            // Rebate (per C++ defaults: x/v grids reduced to 1/4 size, min
            // 20/10; damping reduced to min(1, dampingSteps/2) when nonzero).
            final BarrierOption rebateOption = new BarrierOption(
                    args.barrierType, args.barrier, args.rebate, payoff, args.exercise);
            final int xGridMin = 20;
            final int vGridMin = 10;
            final int rebateDampingSteps =
                    (dampingSteps > 0) ? Math.min(1, dampingSteps / 2) : 0;
            rebateOption.setPricingEngine(new FdHestonRebateEngine(
                    hestonModel, hestonProcess, dividends,
                    tGrid,
                    Math.max(xGridMin, xGrid / 4),
                    Math.max(vGridMin, vGrid / 4),
                    rebateDampingSteps,
                    schemeDesc, mixingFactor));

            r.value          = vanillaOption.NPV()       + rebateOption.NPV()       - r.value;
            r.greeks().delta = vanillaOption.delta()     + rebateOption.delta()     - r.greeks().delta;
            r.greeks().gamma = vanillaOption.gamma()     + rebateOption.gamma()     - r.greeks().gamma;
            r.greeks().theta = vanillaOption.theta()     + rebateOption.theta()     - r.greeks().theta;
        }
    }
}
