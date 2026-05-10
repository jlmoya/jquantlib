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
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
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
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.methods.finitedifferences.utilities.FdmTimeDepDirichletBoundary;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.processes.HestonProcess;

/**
 * Finite-differences Heston rebate-helper engine.
 * <p>
 * Java port of v1.42.1
 * {@code ql/pricingengines/barrier/fdhestonrebateengine.{hpp,cpp}}.
 *
 * <p>Prices the standalone <em>rebate</em> component of a barrier option
 * — i.e., the present value of the constant cash {@code R} that is paid
 * either when the barrier is hit (in-barriers) or when it is NOT hit
 * (out-barriers). This is the helper engine that
 * {@link FdHestonBarrierEngine} delegates to for the in-barrier pricing
 * path (where the value is {@code rebatePV} on hits, plus the standard
 * out-engine contribution for the residual probability mass).
 *
 * <p>The pricing PDE is the same Heston 2-factor PDE used by
 * {@link FdHestonBarrierEngine}, but with:
 * <ul>
 *   <li>a {@link CashOrNothingPayoff} of {@code R} as the inner-value
 *       calculator (so the terminal condition is {@code R} everywhere
 *       within the surviving region);</li>
 *   <li>a constant Dirichlet boundary {@code = R} at the barrier
 *       (Lower for {@code Down*}, Upper for {@code Up*});</li>
 *   <li>no in/out distinction in the engine itself — both
 *       {@code Down*}/{@code Up*} types share the same Lower/Upper
 *       boundary respectively. (The split between in-rebate and
 *       out-rebate is the caller's responsibility — typically by
 *       combining the two via barrier-parity.)</li>
 * </ul>
 *
 * <h3>Limitations vs. C++ v1.42.1</h3>
 * <ul>
 *   <li>{@code FdmQuantoHelper} not yet ported — quanto-adjustment path omitted.</li>
 *   <li>{@code LocalVolTermStructure} leverage function not yet ported — pure-Heston only.</li>
 *   <li>The Java {@link HestonModel} does not expose {@code .process()}, so this
 *       engine takes the model and process as separate arguments (matches
 *       the existing {@code FdHestonBarrierEngine} pattern).</li>
 * </ul>
 *
 * @author Phase 4n.5b port
 */
public class FdHestonRebateEngine extends BarrierOption.EngineImpl {

    private final HestonModel hestonModel;
    private final HestonProcess hestonProcess;
    private final DividendSchedule dividends;
    private final int tGrid, xGrid, vGrid, dampingSteps;
    private final FdmSchemeDesc schemeDesc;
    private final double mixingFactor;

    /** Convenience — all C++ defaults, no dividends. */
    public FdHestonRebateEngine(final HestonModel hestonModel,
                                final HestonProcess hestonProcess) {
        this(hestonModel, hestonProcess, null,
                100, 100, 50, 0, FdmSchemeDesc.Hundsdorfer(), 1.0);
    }

    /** Convenience — explicit grid + scheme, no dividends. */
    public FdHestonRebateEngine(final HestonModel hestonModel,
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
    public FdHestonRebateEngine(final HestonModel hestonModel,
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

        // 1. Mesher
        final double maturity = hestonProcess.time(args.exercise.lastDate());

        // 1.1 Variance mesher
        final int tGridMin = 5;
        final FdmHestonVarianceMesher varianceMesher = new FdmHestonVarianceMesher(
                vGrid, hestonProcess, maturity,
                Math.max(tGridMin, tGrid / 50), 0.0001);

        // 1.2 Equity mesher with barrier-aligned bounds (per C++: Down* sets
        //     xMin, Up* sets xMax — applies regardless of In/Out).
        final BarrierType barrier = args.barrierType;
        final StrikedTypePayoff payoff = (StrikedTypePayoff) args.payoff;
        QL.require(payoff != null, "non-striked payoff given");

        final double xMin = (barrier == BarrierType.DownOut
                          || barrier == BarrierType.DownIn)
                ? JQuantMath.log(args.barrier) : Double.NaN;
        final double xMax = (barrier == BarrierType.UpOut
                          || barrier == BarrierType.UpIn)
                ? JQuantMath.log(args.barrier) : Double.NaN;

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

        // 2. Calculator — terminal condition is the constant rebate, modeled
        //    as a CashOrNothingPayoff with a zero strike and "Call" type so
        //    the cash payoff is unconditionally R inside the surviving region.
        final StrikedTypePayoff rebatePayoff = new CashOrNothingPayoff(
                Option.Type.Call, 0.0, args.rebate);
        final FdmLogInnerValue calculator =
                new FdmLogInnerValue(rebatePayoff, mesher, 0);

        // 3. Step conditions: dividends + European exercise (no early-exercise).
        //    C++ uses vanillaComposite which handles all three exercise types;
        //    this engine restricts to European but uses the same helper for
        //    consistency / reuse of dividend logic.
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividends, args.exercise, mesher, calculator,
                        hestonProcess.riskFreeRate().currentLink().referenceDate(),
                        hestonProcess.riskFreeRate().currentLink().dayCounter());

        // 4. Boundary conditions: Dirichlet at the barrier with constant rebate.
        //    C++ Dirichlet boundary uses a constant value (the rebate);
        //    we use FdmTimeDepDirichletBoundary with a constant lambda.
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
    }
}
