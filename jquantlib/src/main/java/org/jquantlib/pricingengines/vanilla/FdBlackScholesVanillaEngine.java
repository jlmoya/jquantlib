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

/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008, 2009 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.OneAssetOption;
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
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

/**
 * Finite-differences Black-Scholes vanilla option engine.
 * <p>
 * Java port of v1.42.1
 * {@code ql/pricingengines/vanilla/fdblackscholesvanillaengine.{hpp,cpp}}.
 * Supports European and American exercises. Spot cash-dividend model
 * (discrete dividends absorbed into the grid). Local-vol and Quanto-helper
 * options (and the Escrowed dividend model) are deferred to Phase 2m.5.
 *
 * <h3>Constructor variants</h3>
 * <ul>
 *   <li>{@link #FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess)} — all defaults.</li>
 *   <li>{@link #FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess, int, int, int, FdmSchemeDesc)}
 *       — full grid parameters.</li>
 *   <li>{@link #FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess, DividendSchedule, int, int, int, FdmSchemeDesc)}
 *       — with discrete dividends (Spot model).</li>
 * </ul>
 *
 * <h3>Default parameters (matching C++ v1.42.1)</h3>
 * {@code tGrid=100, xGrid=100, dampingSteps=0, scheme=Douglas}.
 *
 * @author Phase 2m Track A port
 */
public class FdBlackScholesVanillaEngine extends OneAssetOption.EngineImpl {

    // --------------------------------------------------------------------
    // Cash-dividend model enum (mirrors C++ inner enum)
    // --------------------------------------------------------------------

    /** Cash-dividend model for discrete dividends. */
    public enum CashDividendModel {
        /**
         * Spot model: dividends are deducted from the spot at payment time.
         * Grid nodes shift at each dividend date.
         */
        Spot,
        /**
         * Escrowed model: PV of future dividends is subtracted from spot.
         * Deferred to Phase 2m.5.
         */
        Escrowed
    }

    // --------------------------------------------------------------------
    // fields
    // --------------------------------------------------------------------

    private final GeneralizedBlackScholesProcess process;
    private final DividendSchedule dividends;
    private final int tGrid;
    private final int xGrid;
    private final int dampingSteps;
    private final FdmSchemeDesc schemeDesc;
    private final CashDividendModel cashDividendModel;

    private final OneAssetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl   r;
    private final Option.GreeksImpl            greeks;

    // --------------------------------------------------------------------
    // constructors
    // --------------------------------------------------------------------

    /**
     * Convenience — all C++ defaults (tGrid=100, xGrid=100, dampingSteps=0,
     * scheme=Douglas, no dividends, Spot model).
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process) {
        this(process, null, 100, 100, 0, FdmSchemeDesc.Douglas(), CashDividendModel.Spot);
    }

    /**
     * Grid parameters only (no dividends, Spot model).
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process,
                                       final int tGrid,
                                       final int xGrid,
                                       final int dampingSteps,
                                       final FdmSchemeDesc schemeDesc) {
        this(process, null, tGrid, xGrid, dampingSteps, schemeDesc,
                CashDividendModel.Spot);
    }

    /**
     * With discrete dividends (Spot model).
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process,
                                       final DividendSchedule dividends,
                                       final int tGrid,
                                       final int xGrid,
                                       final int dampingSteps,
                                       final FdmSchemeDesc schemeDesc) {
        this(process, dividends, tGrid, xGrid, dampingSteps, schemeDesc,
                CashDividendModel.Spot);
    }

    /**
     * Full constructor mirroring C++ v1.42.1.
     *
     * @param process           GBS process
     * @param dividends         discrete cash dividends (null / empty = none)
     * @param tGrid             number of time steps
     * @param xGrid             number of log-space grid points
     * @param dampingSteps      number of leading implicit-Euler damping steps
     * @param schemeDesc        FDM scheme descriptor
     * @param cashDividendModel dividend model (only Spot supported currently)
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process,
                                       final DividendSchedule dividends,
                                       final int tGrid,
                                       final int xGrid,
                                       final int dampingSteps,
                                       final FdmSchemeDesc schemeDesc,
                                       final CashDividendModel cashDividendModel) {
        super();
        QL.require(process != null, "null GBS process");
        QL.require(cashDividendModel == CashDividendModel.Spot,
                "Escrowed dividend model is not yet supported");

        this.process          = process;
        this.dividends        = (dividends != null) ? dividends : new DividendSchedule();
        this.tGrid            = tGrid;
        this.xGrid            = xGrid;
        this.dampingSteps     = dampingSteps;
        this.schemeDesc       = schemeDesc;
        this.cashDividendModel = cashDividendModel;

        this.a      = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r      = (OneAssetOption.ResultsImpl)   results_;
        this.greeks = r.greeks();

        process.addObserver(this);
    }

    // --------------------------------------------------------------------
    // PricingEngine implementation
    // --------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * Implements the five-step FDM workflow from C++:
     * <ol>
     *   <li>Mesher ({@link FdmBlackScholesMesher} in ln-S space)</li>
     *   <li>Inner-value calculator ({@link FdmLogInnerValue})</li>
     *   <li>Step conditions ({@link FdmStepConditionComposite#vanillaComposite})</li>
     *   <li>Boundary conditions (empty)</li>
     *   <li>Solver ({@link FdmBlackScholesSolver} → {@link FdmBlackScholesSolver})</li>
     * </ol>
     */
    @Override
    public void calculate() {

        // --- retrieve arguments ---
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        QL.require(payoff != null, "non-striked payoff given");

        // 0. Cash dividend model — only Spot supported
        // (spotAdjustment = 0 for Spot model)
        final double spotAdjustment = 0.0;
        final DividendSchedule dividendSchedule = dividends;

        // maturity
        final Date exerciseDate = a.exercise.lastDate();
        final double maturity = process.time(exerciseDate);

        // 1. Mesher
        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid, process, maturity, payoff.strike(),
                dividendSchedule, spotAdjustment);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher);

        // 2. Calculator
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(payoff, mesher, 0);

        // 3. Step conditions
        final Date refDate = process.riskFreeRate().currentLink().referenceDate();
        final org.jquantlib.daycounters.DayCounter dc =
                process.riskFreeRate().currentLink().dayCounter();

        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividendSchedule, a.exercise, mesher,
                        calculator, refDate, dc);

        // 4. Boundary conditions (empty)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid, dampingSteps);

        final FdmBlackScholesSolver solver = new FdmBlackScholesSolver(
                process, payoff.strike(), solverDesc, schemeDesc);

        final double spot = process.x0() + spotAdjustment;

        r.value     = solver.valueAt(spot);
        greeks.delta = solver.deltaAt(spot);
        greeks.gamma = solver.gammaAt(spot);
        greeks.theta = solver.thetaAt(spot);
    }
}
