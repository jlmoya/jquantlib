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
import org.jquantlib.methods.finitedifferences.utilities.FdmQuantoHelper;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.QuantoTermStructure;
import org.jquantlib.time.Date;

/**
 * Finite-differences Black-Scholes vanilla option engine.
 * <p>
 * Java port of v1.42.1 {@code ql/pricingengines/vanilla/fdblackscholesvanillaengine.{hpp,cpp}}. Supports European and
 * American exercises. Spot cash-dividend model (discrete dividends absorbed into the grid). Local-vol and the Escrowed
 * dividend model are deferred to Phase 2m.5; the Quanto-helper hook is supported via an analytic-equivalent process
 * re-write (the helper's continuous drift adjustment {@code r_d - r_f + corr * sigma_eq * sigma_fx} is folded into the
 * dividend yield through {@link org.jquantlib.termstructures.yieldcurves.QuantoTermStructure}, which gives the same
 * forward-rate drift as the C++ FdmBlackScholesOp quanto branch for non-localVol pricing).
 *
 * <h3>Constructor variants</h3>
 * <ul>
 *   <li>{@link #FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess)} — all defaults.</li>
 *   <li>{@link #FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess, int, int, int, FdmSchemeDesc)}
 *       — full grid parameters.</li>
 *   <li>{@link #FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess, DividendSchedule, int, int, int, FdmSchemeDesc)}
 *       — with discrete dividends (Spot model).</li>
 *   <li>{@link #FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess, FdmQuantoHelper, int, int, int)}
 *       — with quanto helper (Phase 5e.5b-CFC-d-214).</li>
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

    private final GeneralizedBlackScholesProcess process;

    // --------------------------------------------------------------------
    // fields
    // --------------------------------------------------------------------
    private final DividendSchedule dividends;
    private final int tGrid;
    private final int xGrid;
    private final int dampingSteps;
    private final FdmSchemeDesc schemeDesc;
    private final CashDividendModel cashDividendModel;
    private final FdmQuantoHelper quantoHelper;
    private final boolean localVol;
    private final double illegalLocalVolOverwrite;
    private final OneAssetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;
    /**
     * Convenience — all C++ defaults (tGrid=100, xGrid=100, dampingSteps=0, scheme=Douglas, no dividends, Spot model).
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process) {
        this(process, null, null, 100, 100, 0, FdmSchemeDesc.Douglas(), CashDividendModel.Spot, false, Double.NaN);
    }

    // --------------------------------------------------------------------
    // constructors
    // --------------------------------------------------------------------

    /**
     * Grid parameters only (no dividends, Spot model).
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process, final int tGrid, final int xGrid,
            final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(process, null, null, tGrid, xGrid, dampingSteps, schemeDesc, CashDividendModel.Spot, false, Double.NaN);
    }

    /**
     * Local-vol-aware overload mirroring the C++ ctor
     * {@code FdBlackScholesVanillaEngine(process, tGrid, xGrid, dampingSteps, schemeDesc, localVol,
     * illegalLocalVolOverwrite)}.
     *
     * @param illegalLocalVolOverwrite fallback {@code sigma} substituted when
     *                                 {@code process.localVolatility().localVol(t, S)} throws; pass {@link Double#NaN}
     *                                 (or any negative value) to disable the fallback, mirroring C++'s
     *                                 {@code -Null<Real>::value} sentinel.
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process, final int tGrid, final int xGrid,
            final int dampingSteps, final FdmSchemeDesc schemeDesc, final boolean localVol,
            final double illegalLocalVolOverwrite) {
        this(process, null, null, tGrid, xGrid, dampingSteps, schemeDesc, CashDividendModel.Spot, localVol,
                illegalLocalVolOverwrite);
    }

    /**
     * With discrete dividends (Spot model).
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process, final DividendSchedule dividends,
            final int tGrid, final int xGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(process, dividends, null, tGrid, xGrid, dampingSteps, schemeDesc, CashDividendModel.Spot, false,
                Double.NaN);
    }

    /**
     * Quanto overload — adds an {@link FdmQuantoHelper} hook (matches the C++ v1.42.1 constructor
     * {@code (process, quantoHelper, tGrid, xGrid, damping)}). No discrete dividends; Spot model; Douglas scheme.
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process, final FdmQuantoHelper quantoHelper,
            final int tGrid, final int xGrid, final int dampingSteps) {
        this(process, null, quantoHelper, tGrid, xGrid, dampingSteps, FdmSchemeDesc.Douglas(), CashDividendModel.Spot,
                false, Double.NaN);
    }

    /**
     * Quanto overload with custom scheme.
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process, final FdmQuantoHelper quantoHelper,
            final int tGrid, final int xGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(process, null, quantoHelper, tGrid, xGrid, dampingSteps, schemeDesc, CashDividendModel.Spot, false,
                Double.NaN);
    }

    /**
     * Quanto + discrete dividends overload.
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process, final DividendSchedule dividends,
            final FdmQuantoHelper quantoHelper, final int tGrid, final int xGrid, final int dampingSteps) {
        this(process, dividends, quantoHelper, tGrid, xGrid, dampingSteps, FdmSchemeDesc.Douglas(),
                CashDividendModel.Spot, false, Double.NaN);
    }

    /**
     * Back-compat full constructor (no quanto helper) mirroring the previous signature; delegates to the quanto-aware
     * full constructor.
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process, final DividendSchedule dividends,
            final int tGrid, final int xGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc,
            final CashDividendModel cashDividendModel) {
        this(process, dividends, null, tGrid, xGrid, dampingSteps, schemeDesc, cashDividendModel, false, Double.NaN);
    }

    /**
     * Full constructor mirroring C++ v1.42.1.
     *
     * @param process                  GBS process
     * @param dividends                discrete cash dividends (null / empty = none)
     * @param quantoHelper             optional FDM quanto helper (null = no quanto)
     * @param tGrid                    number of time steps
     * @param xGrid                    number of log-space grid points
     * @param dampingSteps             number of leading implicit-Euler damping steps
     * @param schemeDesc               FDM scheme descriptor
     * @param cashDividendModel        dividend model (only Spot supported currently)
     * @param localVol                 when {@code true} the FDM operator uses {@code process.localVolatility()} instead
     *                                 of the constant-vol forward-variance lookup
     * @param illegalLocalVolOverwrite fallback {@code sigma} when local-vol evaluation throws; {@link Double#NaN} (or
     *                                 any negative value) disables it
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process, final DividendSchedule dividends,
            final FdmQuantoHelper quantoHelper, final int tGrid, final int xGrid, final int dampingSteps,
            final FdmSchemeDesc schemeDesc, final CashDividendModel cashDividendModel, final boolean localVol,
            final double illegalLocalVolOverwrite) {
        super();
        QL.require(process != null, "null GBS process");
        QL.require(cashDividendModel == CashDividendModel.Spot, "Escrowed dividend model is not yet supported");
        QL.require(!(localVol && quantoHelper != null), "localVol + quantoHelper combination is not yet supported");

        this.process = process;
        this.dividends = (dividends != null) ? dividends : new DividendSchedule();
        this.tGrid = tGrid;
        this.xGrid = xGrid;
        this.dampingSteps = dampingSteps;
        this.schemeDesc = schemeDesc;
        this.cashDividendModel = cashDividendModel;
        this.quantoHelper = quantoHelper;
        this.localVol = localVol;
        this.illegalLocalVolOverwrite = illegalLocalVolOverwrite;

        this.a = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.greeks = r.greeks();

        process.addObserver(this);
        if ( quantoHelper != null ) {
            quantoHelper.addObserver(this);
        }
    }

    /**
     * Back-compat full constructor pre-dating the {@code localVol} parameters.
     */
    public FdBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess process, final DividendSchedule dividends,
            final FdmQuantoHelper quantoHelper, final int tGrid, final int xGrid, final int dampingSteps,
            final FdmSchemeDesc schemeDesc, final CashDividendModel cashDividendModel) {
        this(process, dividends, quantoHelper, tGrid, xGrid, dampingSteps, schemeDesc, cashDividendModel, false,
                Double.NaN);
    }

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
     * When a non-null {@link FdmQuantoHelper} is supplied the GBS process is
     * re-wrapped with a {@link QuantoTermStructure}-driven dividend yield, so
     * that the BS PDE drift {@code (r - q_quanto - 0.5 sigma^2)} equals the
     * original {@code (r - q - 0.5 sigma^2)} minus the helper's
     * {@code quantoAdjustment(sigma, t1, t2)} — i.e. identical to the C++
     * {@code FdmBlackScholesOp::setTime} non-localVol quanto branch.
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

        // Effective process: when a quanto helper is provided, fold the
        // continuous quanto drift adjustment into the dividend yield.
        final GeneralizedBlackScholesProcess effectiveProcess = (quantoHelper != null) ? buildQuantoProcess(
                payoff.strike()) : process;

        // maturity
        final Date exerciseDate = a.exercise.lastDate();
        final double maturity = effectiveProcess.time(exerciseDate);

        // 1. Mesher
        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(xGrid, effectiveProcess, maturity, payoff.strike(),
                dividendSchedule, spotAdjustment);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher);

        // 2. Calculator
        final FdmInnerValueCalculator calculator = new FdmLogInnerValue(payoff, mesher, 0);

        // 3. Step conditions
        final Date refDate = effectiveProcess.riskFreeRate().currentLink().referenceDate();
        final org.jquantlib.daycounters.DayCounter dc = effectiveProcess.riskFreeRate().currentLink().dayCounter();

        final FdmStepConditionComposite conditions = FdmStepConditionComposite.vanillaComposite(dividendSchedule,
                a.exercise, mesher, calculator, refDate, dc);

        // 4. Boundary conditions (empty)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid,
                dampingSteps);

        final FdmBlackScholesSolver solver = new FdmBlackScholesSolver(effectiveProcess, payoff.strike(), solverDesc,
                schemeDesc, localVol, illegalLocalVolOverwrite);

        final double spot = effectiveProcess.x0() + spotAdjustment;

        r.value = solver.valueAt(spot);
        greeks.delta = solver.deltaAt(spot);
        greeks.gamma = solver.gammaAt(spot);
        greeks.theta = solver.thetaAt(spot);
    }

    // --------------------------------------------------------------------
    // PricingEngine implementation
    // --------------------------------------------------------------------

    /**
     * Build a quanto-adjusted GBS process: the dividend yield is the original dividend yield plus the helper's
     * continuous quanto drift adjustment (folded via {@link QuantoTermStructure}). All other handles are inherited from
     * {@link #process}.
     */
    private GeneralizedBlackScholesProcess buildQuantoProcess(final double strike) {
        final Handle< YieldTermStructure > origDividend = process.dividendYield();
        final Handle< YieldTermStructure > origRiskFree = process.riskFreeRate();
        final Handle< YieldTermStructure > foreignTS = new Handle< YieldTermStructure >(quantoHelper.fTS());
        final Handle< BlackVolTermStructure > fxVolTS = new Handle< BlackVolTermStructure >(quantoHelper.fxVolTS());
        final Handle< BlackVolTermStructure > origBlackVol = process.blackVolatility();

        final QuantoTermStructure quantoTS = new QuantoTermStructure(origDividend, origRiskFree, foreignTS,
                origBlackVol, strike, fxVolTS, quantoHelper.exchRateATMlevel(), quantoHelper.equityFxCorrelation());

        final Handle< YieldTermStructure > dividendYield = new Handle< YieldTermStructure >(quantoTS);

        return new GeneralizedBlackScholesProcess(process.stateVariable(), dividendYield, origRiskFree, origBlackVol);
    }

    /** Cash-dividend model for discrete dividends. */
    public enum CashDividendModel {
        /**
         * Spot model: dividends are deducted from the spot at payment time. Grid nodes shift at each dividend date.
         */
        Spot,
        /**
         * Escrowed model: PV of future dividends is subtracted from spot. Deferred to Phase 2m.5.
         */
        Escrowed
    }
}
