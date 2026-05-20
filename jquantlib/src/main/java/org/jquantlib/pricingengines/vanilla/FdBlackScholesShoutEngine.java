/*
 Copyright (C) 2021 Klaus Spanderen

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
import org.jquantlib.cashflow.Dividend;
import org.jquantlib.cashflow.FixedDividend;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmBlackScholesSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.EscrowedDividendAdjustment;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmShoutLogInnerValueCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

/**
 * Finite-differences Black-Scholes shout-option engine.
 * <p>
 * Java port of v1.42.1 {@code ql/pricingengines/vanilla/fdblackscholesshoutengine.{hpp,cpp}}.
 * Prices a shout option — a European-style option whose holder is granted a single
 * opportunity to "shout" (lock in) the current intrinsic value while retaining the option
 * to receive the better of the locked-in payoff or the European payoff at maturity.
 *
 * <h3>Method</h3>
 * Implements the shout via the standard FDM five-step workflow with two specialisations:
 * <ol>
 *   <li>Uses the {@link FdmShoutLogInnerValueCalculator} (escrowed-dividend model)
 *       which encodes the shout intrinsic: black-formula value of an at-spot-forward
 *       option plus discounted intrinsic at the locked-in spot.</li>
 *   <li>Cash dividends always use the {@code Escrowed} model: the deterministic PV
 *       of future dividends is subtracted from the spot via
 *       {@link EscrowedDividendAdjustment}. At each dividend date a zero-amount stopping
 *       time is inserted so the FDM still respects dividend-date events.</li>
 * </ol>
 *
 * <h3>Constructor variants</h3>
 * <ul>
 *   <li>Process only — defaults {@code tGrid=100, xGrid=100, dampingSteps=0, Douglas}.</li>
 *   <li>Process + grid + scheme.</li>
 *   <li>Process + dividends + grid + scheme.</li>
 * </ul>
 *
 * @author Phase 1-closure A3-B-546-shout port
 */
public class FdBlackScholesShoutEngine extends OneAssetOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final DividendSchedule dividends;
    private final int tGrid;
    private final int xGrid;
    private final int dampingSteps;
    private final FdmSchemeDesc schemeDesc;

    private final OneAssetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;

    /**
     * Convenience — all C++ defaults ({@code tGrid=100, xGrid=100, dampingSteps=0, Douglas}, no dividends).
     */
    public FdBlackScholesShoutEngine(final GeneralizedBlackScholesProcess process) {
        this(process, null, 100, 100, 0, FdmSchemeDesc.Douglas());
    }

    /**
     * Grid + scheme parameters, no dividends.
     */
    public FdBlackScholesShoutEngine(final GeneralizedBlackScholesProcess process, final int tGrid, final int xGrid,
            final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(process, null, tGrid, xGrid, dampingSteps, schemeDesc);
    }

    /**
     * Full ctor mirroring C++ v1.42.1 second constructor.
     *
     * @param process      GBS process (non-null)
     * @param dividends    discrete cash dividends; {@code null} = empty schedule
     * @param tGrid        number of time steps
     * @param xGrid        number of log-space grid points
     * @param dampingSteps number of leading implicit-Euler damping steps
     * @param schemeDesc   FDM scheme descriptor
     */
    public FdBlackScholesShoutEngine(final GeneralizedBlackScholesProcess process, final DividendSchedule dividends,
            final int tGrid, final int xGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        super();
        QL.require(process != null, "null GBS process");
        this.process = process;
        this.dividends = (dividends != null) ? dividends : new DividendSchedule();
        this.tGrid = tGrid;
        this.xGrid = xGrid;
        this.dampingSteps = dampingSteps;
        this.schemeDesc = schemeDesc;
        this.a = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.greeks = r.greeks();
        process.addObserver(this);
    }

    @Override
    public void calculate() {

        final Date exerciseDate = a.exercise.lastDate();
        final double maturity = process.time(exerciseDate);
        final Date settlementDate = process.riskFreeRate().currentLink().referenceDate();

        // Escrowed dividend adjustment
        final GeneralizedBlackScholesProcess procRef = process;
        final EscrowedDividendAdjustment escrowedDividendAdj = new EscrowedDividendAdjustment(dividends,
                process.riskFreeRate(), process.dividendYield(), (final Date d) -> procRef.time(d), maturity);

        final double divAdj = escrowedDividendAdj.dividendAdjustment(process.time(settlementDate));

        QL.require(process.x0() + divAdj > 0.0, "spot minus dividends becomes negative");

        // Payoff must be plain-vanilla
        final PlainVanillaPayoff payoff;
        if ( a.payoff instanceof PlainVanillaPayoff ) {
            payoff = (PlainVanillaPayoff) a.payoff;
        } else {
            payoff = null;
        }
        QL.require(payoff != null, "non plain vanilla payoff given");

        // 1. Mesher — log-space, concentrated at strike (cPoint = (strike, 0.1))
        //    Pass an empty dividend schedule to the mesher: in the escrowed model,
        //    discrete dividend events are handled via spotAdjustment (divAdj) and the
        //    zeroDividendSchedule stopping-time hook below.
        final DividendSchedule emptyDividendSchedule = new DividendSchedule();
        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(xGrid, process, maturity, payoff.strike(), Double.NaN,
                Double.NaN, 0.0001, 1.5, payoff.strike(), 0.1, emptyDividendSchedule, divAdj);
        final FdmMesher mesher = new FdmMesherComposite(equityMesher);

        // 2. Inner-value calculator — shout-specific
        final FdmShoutLogInnerValueCalculator innerValueCalculator = new FdmShoutLogInnerValueCalculator(
                process.blackVolatility(), escrowedDividendAdj, maturity, payoff, mesher, 0);

        // 3. Zero-amount dividend schedule — preserves dividend dates as stopping times
        //    without re-applying the dividend amount (already in divAdj).
        final DividendSchedule zeroDividendSchedule = new DividendSchedule();
        for ( final Dividend cf : dividends ) {
            zeroDividendSchedule.add(new FixedDividend(0.0, cf.date()));
        }

        // 4. Step conditions — vanilla composite (handles exercise + dividend stopping times)
        final FdmStepConditionComposite conditions = FdmStepConditionComposite.vanillaComposite(zeroDividendSchedule,
                a.exercise, mesher, innerValueCalculator, process.riskFreeRate().currentLink().referenceDate(),
                process.riskFreeRate().currentLink().dayCounter());

        // 5. Solver — empty boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, innerValueCalculator,
                maturity, tGrid, dampingSteps);

        final FdmBlackScholesSolver solver = new FdmBlackScholesSolver(process, payoff.strike(), solverDesc,
                schemeDesc);

        final double spot = process.x0() + divAdj;

        r.value = solver.valueAt(spot);
        greeks.delta = solver.deltaAt(spot);
        greeks.gamma = solver.gammaAt(spot);
        greeks.theta = solver.thetaAt(spot);
    }
}
