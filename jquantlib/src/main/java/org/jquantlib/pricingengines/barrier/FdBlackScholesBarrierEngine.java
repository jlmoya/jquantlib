/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008 Klaus Spanderen

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
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmBlackScholesSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmDividendHandler;
import org.jquantlib.methods.finitedifferences.utilities.BoundaryCondition;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.methods.finitedifferences.utilities.FdmTimeDepDirichletBoundary;
import org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

import java.util.ArrayList;
import java.util.List;

/**
 * Finite-differences Black-Scholes barrier-option engine.
 * <p>
 * Java port of v1.42.1 {@code ql/pricingengines/barrier/fdblackscholesbarrierengine.{hpp,cpp}}.
 * Supports European-style barrier options (down-in/out, up-in/out) with optional
 * discrete cash dividends.
 *
 * <h3>In-barrier handling</h3>
 * For {@link BarrierType#DownIn} / {@link BarrierType#UpIn} the value is computed
 * via the in-out parity: {@code V_in = V_vanilla + V_rebate - V_out_FDM}, where
 * {@code V_vanilla} comes from {@link FdBlackScholesVanillaEngine} and
 * {@code V_rebate} from {@link FdBlackScholesRebateEngine}.
 *
 * @author Phase 1-closure A2-C-555 port
 */
public class FdBlackScholesBarrierEngine extends BarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final DividendSchedule dividends;
    private final int tGrid;
    private final int xGrid;
    private final int dampingSteps;
    private final FdmSchemeDesc schemeDesc;
    private final boolean localVol;
    private final double illegalLocalVolOverwrite;

    private final BarrierOption.ArgumentsImpl a;
    private final BarrierOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;

    public FdBlackScholesBarrierEngine(final GeneralizedBlackScholesProcess process) {
        this(process, null, 100, 100, 0, FdmSchemeDesc.Douglas(), false, Double.NaN);
    }

    public FdBlackScholesBarrierEngine(final GeneralizedBlackScholesProcess process, final int tGrid, final int xGrid,
            final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(process, null, tGrid, xGrid, dampingSteps, schemeDesc, false, Double.NaN);
    }

    public FdBlackScholesBarrierEngine(final GeneralizedBlackScholesProcess process, final int tGrid, final int xGrid,
            final int dampingSteps, final FdmSchemeDesc schemeDesc, final boolean localVol,
            final double illegalLocalVolOverwrite) {
        this(process, null, tGrid, xGrid, dampingSteps, schemeDesc, localVol, illegalLocalVolOverwrite);
    }

    public FdBlackScholesBarrierEngine(final GeneralizedBlackScholesProcess process, final DividendSchedule dividends,
            final int tGrid, final int xGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(process, dividends, tGrid, xGrid, dampingSteps, schemeDesc, false, Double.NaN);
    }

    public FdBlackScholesBarrierEngine(final GeneralizedBlackScholesProcess process, final DividendSchedule dividends,
            final int tGrid, final int xGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc,
            final boolean localVol, final double illegalLocalVolOverwrite) {
        super();
        QL.require(process != null, "null GBS process");
        this.process = process;
        this.dividends = (dividends != null) ? dividends : new DividendSchedule();
        this.tGrid = tGrid;
        this.xGrid = xGrid;
        this.dampingSteps = dampingSteps;
        this.schemeDesc = schemeDesc;
        this.localVol = localVol;
        this.illegalLocalVolOverwrite = illegalLocalVolOverwrite;
        this.a = (BarrierOption.ArgumentsImpl) arguments_;
        this.r = (BarrierOption.ResultsImpl) results_;
        this.greeks = r.greeks();
        process.addObserver(this);
    }

    @Override
    public void calculate() {

        // 1. Mesher — barrier-aligned in log-space
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        QL.require(payoff != null, "non-striked type payoff given");
        QL.require(payoff.strike() > 0.0, "strike must be positive");

        QL.require(a.exercise.type() == Exercise.Type.European, "only european style option are supported");

        final double spot = process.x0();
        QL.require(spot > 0.0, "negative or null underlying given");
        QL.require(!triggered(spot), "barrier touched");

        final double maturity = process.time(a.exercise.lastDate());

        double xMin = Double.NaN;
        double xMax = Double.NaN;
        if ( a.barrierType == BarrierType.DownIn || a.barrierType == BarrierType.DownOut ) {
            xMin = Math.log(a.barrier);
        }
        if ( a.barrierType == BarrierType.UpIn || a.barrierType == BarrierType.UpOut ) {
            xMax = Math.log(a.barrier);
        }

        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(xGrid, process, maturity, payoff.strike(), xMin, xMax,
                0.0001, 1.5, Double.NaN, Double.NaN, dividends, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher);

        // 2. Calculator
        final FdmInnerValueCalculator calculator = new FdmLogInnerValue(payoff, mesher, 0);

        // 3. Step conditions — discrete dividend handler if needed
        final FdmStepConditionComposite.Conditions stepConditions = new FdmStepConditionComposite.Conditions();
        final List< List< Double > > stoppingTimes = new ArrayList<>();

        if ( dividends != null && !dividends.isEmpty() ) {
            final FdmDividendHandler dividendCondition = new FdmDividendHandler(dividends, mesher,
                    process.riskFreeRate().currentLink().referenceDate(),
                    process.riskFreeRate().currentLink().dayCounter(), 0);
            stepConditions.add(dividendCondition);
            final List< Double > dividendTimes = new ArrayList<>();
            for ( final double t : dividendCondition.dividendTimes() ) {
                dividendTimes.add(Math.min(maturity, t));
            }
            stoppingTimes.add(dividendTimes);
        }

        final FdmStepConditionComposite conditions = new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 4. Boundary conditions — constant Dirichlet at the barrier with the rebate value
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();
        if ( a.barrierType == BarrierType.DownIn || a.barrierType == BarrierType.DownOut ) {
            boundaries.add(new FdmTimeDepDirichletBoundary(mesher, t -> a.rebate, 0, BoundaryCondition.Side.Lower));
        }
        if ( a.barrierType == BarrierType.UpIn || a.barrierType == BarrierType.UpOut ) {
            boundaries.add(new FdmTimeDepDirichletBoundary(mesher, t -> a.rebate, 0, BoundaryCondition.Side.Upper));
        }

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid,
                dampingSteps);

        final FdmBlackScholesSolver solver = new FdmBlackScholesSolver(process, payoff.strike(), solverDesc, schemeDesc,
                localVol, illegalLocalVolOverwrite);

        r.value = solver.valueAt(spot);
        greeks.delta = solver.deltaAt(spot);
        greeks.gamma = solver.gammaAt(spot);
        greeks.theta = solver.thetaAt(spot);

        // 6. Calculate vanilla option and rebate for in-barriers
        if ( a.barrierType == BarrierType.DownIn || a.barrierType == BarrierType.UpIn ) {
            // Vanilla option (same payoff/exercise) — calls FdBlackScholesVanillaEngine.
            final VanillaOption vanillaOption = new VanillaOption(payoff, a.exercise);
            vanillaOption.setPricingEngine(new FdBlackScholesVanillaEngine(process, dividends, null, tGrid, xGrid,
                    0, // dampingSteps
                    schemeDesc, FdBlackScholesVanillaEngine.CashDividendModel.Spot, localVol,
                    illegalLocalVolOverwrite));

            // Rebate option (same barrier/payoff/exercise) — calls FdBlackScholesRebateEngine.
            final BarrierOption rebateOption = new BarrierOption(a.barrierType, a.barrier, a.rebate, payoff,
                    a.exercise);
            final int minGridSize = 50;
            final int rebateDampingSteps = (dampingSteps > 0) ? Math.min(1, dampingSteps / 2) : 0;
            rebateOption.setPricingEngine(new FdBlackScholesRebateEngine(process, dividends, tGrid,
                    Math.max(minGridSize, xGrid / 5), rebateDampingSteps, schemeDesc, localVol,
                    illegalLocalVolOverwrite));

            r.value = vanillaOption.NPV() + rebateOption.NPV() - r.value;
            greeks.delta = vanillaOption.delta() + rebateOption.delta() - greeks.delta;
            greeks.gamma = vanillaOption.gamma() + rebateOption.gamma() - greeks.gamma;
            greeks.theta = vanillaOption.theta() + rebateOption.theta() - greeks.theta;
        }
    }
}
