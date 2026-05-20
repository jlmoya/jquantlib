/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008, 2009 Ralph Schreyer
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
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmBlackScholesSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.BoundaryCondition;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.methods.finitedifferences.utilities.FdmTimeDepDirichletBoundary;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

/**
 * Finite-differences rebate engine for barrier options.
 * <p>
 * Java port of v1.42.1 {@code ql/pricingengines/barrier/fdblackscholesrebateengine.{hpp,cpp}}.
 * Used internally by {@link FdBlackScholesBarrierEngine} to value the rebate cash-flow leg of
 * an in-barrier (down-in / up-in) option.
 *
 * @author Phase 1-closure A2-C-555 port
 */
public class FdBlackScholesRebateEngine extends BarrierOption.EngineImpl {

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

    public FdBlackScholesRebateEngine(final GeneralizedBlackScholesProcess process, final int tGrid, final int xGrid,
            final int dampingSteps, final FdmSchemeDesc schemeDesc, final boolean localVol,
            final double illegalLocalVolOverwrite) {
        this(process, null, tGrid, xGrid, dampingSteps, schemeDesc, localVol, illegalLocalVolOverwrite);
    }

    public FdBlackScholesRebateEngine(final GeneralizedBlackScholesProcess process, final DividendSchedule dividends,
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
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        QL.require(payoff != null, "non-striked payoff given");
        final double maturity = process.time(a.exercise.lastDate());

        // 1. Mesher — barrier-aligned in log space
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

        // 2. Calculator — cash-or-nothing-call with strike=0, payout=rebate (matches C++).
        final StrikedTypePayoff rebatePayoff = new CashOrNothingPayoff(Option.Type.Call, 0.0, a.rebate);
        final FdmInnerValueCalculator calculator = new FdmLogInnerValue(rebatePayoff, mesher, 0);

        // 3. Step conditions — European only
        QL.require(a.exercise.type() == Exercise.Type.European, "only european style option are supported");
        final Date refDate = process.riskFreeRate().currentLink().referenceDate();
        final DayCounter dc = process.riskFreeRate().currentLink().dayCounter();
        final FdmStepConditionComposite conditions = FdmStepConditionComposite.vanillaComposite(dividends, a.exercise,
                mesher, calculator, refDate, dc);

        // 4. Boundary conditions — Dirichlet @ barrier with constant rebate value
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

        final double spot = process.x0();
        r.value = solver.valueAt(spot);
        greeks.delta = solver.deltaAt(spot);
        greeks.gamma = solver.gammaAt(spot);
        greeks.theta = solver.thetaAt(spot);
    }
}
