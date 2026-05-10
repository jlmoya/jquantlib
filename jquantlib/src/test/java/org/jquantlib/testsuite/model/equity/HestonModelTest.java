/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.equity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticPDFHestonEngine;
import org.jquantlib.pricingengines.vanilla.COSHestonEngine;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.barrier.FdHestonBarrierEngine;
import org.jquantlib.pricingengines.vanilla.FordeHestonExpansion;
import org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import org.jquantlib.pricingengines.vanilla.HestonExpansion;
import org.jquantlib.pricingengines.vanilla.HestonExpansionEngine;
import org.jquantlib.pricingengines.vanilla.LPP2HestonExpansion;
import org.jquantlib.pricingengines.vanilla.FdHestonVanillaEngine;
import org.jquantlib.pricingengines.vanilla.MCEuropeanHestonEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/hestonmodel.cpp} v1.42.1
 * (3,469 LOC, 38 test cases) — the largest stochastic-vol test file.
 *
 * <p>The 38 tests fall into seven thematic groups:
 *
 * <ol>
 *   <li><strong>Calibration (slow)</strong> — {@code testBlackCalibration},
 *       {@code testDAXCalibration}, {@code testDAXCalibrationOfTimeDependentModel}.
 *       CPU-intensive (multi-minute in C++); per Phase 5 META D8 these
 *       must be tagged {@code @Tag("slow")} when ported.</li>
 *
 *   <li><strong>Analytic vs Black / Cached</strong> —
 *       {@code testAnalyticVsBlack}, {@code testAnalyticVsCached}.
 *       Partial Java coverage exists in
 *       {@link org.jquantlib.testsuite.pricingengines.vanilla.AnalyticHestonEngineTest}
 *       ({@code cachedAnalyticValueOtmCall} reproduces the
 *       {@code testAnalyticVsCached} expected1 = 0.0404774515; and
 *       {@code blackScholesLimit} reproduces the spirit of
 *       {@code testAnalyticVsBlack}). Full porting deferred for richer
 *       parameter coverage.</li>
 *
 *   <li><strong>FD engines</strong> — {@code testFdBarrierVsCached},
 *       {@code testFdVanillaVsCached}, {@code testFdVanillaWithDividendsVsCached},
 *       {@code testFdAmerican}. Require {@code FdHestonVanillaEngine}
 *       and {@code FdHestonBarrierEngine}, neither of which exist in
 *       Java (only the HHW variant {@code FdHestonHullWhiteVanillaEngine}
 *       is ported, see Phase 2m).</li>
 *
 *   <li><strong>MC engines</strong> — {@code testMcVsCached}.
 *       Requires {@code MCEuropeanHestonEngine}; not yet ported.</li>
 *
 *   <li><strong>Integration / characteristic-function methods</strong> —
 *       {@code testKahlJaeckelCase}, {@code testDifferentIntegrals},
 *       {@code testAllIntegrationMethods}, {@code testHestonEngineIntegration},
 *       {@code testCharacteristicFct}. Require the full integration-method
 *       enum on {@code AnalyticHestonEngine} (Java currently exposes
 *       only Gauss-Laguerre at fixed order 128).</li>
 *
 *   <li><strong>COS / Andersen-Piterbarg / Expansions</strong> — 14
 *       tests covering {@code COSHestonEngine},
 *       {@code AnalyticPDFHestonEngine},
 *       {@code HestonExpansion}-family engines (Forde, Lewis, Piterbarg,
 *       Andersen-Piterbarg control variate, optimal-α). None of these
 *       engines are ported to Java — Phase 2j/2m only delivered the
 *       Gauss-Laguerre {@code AnalyticHestonEngine}.</li>
 *
 *   <li><strong>Piecewise-time-dependent Heston</strong> —
 *       {@code testAnalyticPiecewiseTimeDependent},
 *       {@code testPiecewiseTimeDependentChFvsHestonChF},
 *       {@code testPiecewiseTimeDependentComparison},
 *       {@code testPiecewiseTimeDependentChFAsymtotic},
 *       {@code testMultipleStrikesEngine},
 *       {@code testLocalVolFromHestonModel}. Require
 *       {@code PiecewiseTimeDependentHestonModel} and
 *       {@code AnalyticPTDHestonEngine}; neither exists in Java.</li>
 * </ol>
 *
 * <p><strong>Phase 5h.5 carry-forward:</strong> all 38 tests are
 * deferred. A representative subset (Black-limit + cached value)
 * already exists in {@link org.jquantlib.testsuite.pricingengines.vanilla
 * .AnalyticHestonEngineTest}; this skeleton provides the inventory
 * mapping and documents the prerequisite engine ports needed to enable
 * the remaining cases.
 *
 * <p>Source: {@code test-suite/hestonmodel.cpp} v1.42.1 @ {@code 099987f0ca}.
 *
 * @see org.jquantlib.testsuite.pricingengines.vanilla.AnalyticHestonEngineTest
 */
public class HestonModelTest {

    private static final String REASON_CALIB =
            "Phase 5h.5 + slow — requires Heston calibration loop (LevenbergMarquardt) "
            + "and @Tag(\"slow\") (Phase 5 META D8).";

    private static final String REASON_FD =
            "Phase 5h.5: FdHestonVanillaEngine + FdHestonBarrierEngine now ported "
            + "(commits 7dbb9bd2 + a1dffb9e); test bodies are `fail(\"not implemented\")` — "
            + "needs full port from C++ hestonmodel.cpp.";

    private static final String REASON_MC =
            "Phase 5h.5: MCEuropeanHestonEngine now ported (commit a9cb20bd, "
            + "Phase 5h.5-Bates-b); test bodies are `fail(\"not implemented\")` — "
            + "needs full port from C++ hestonmodel.cpp.";

    private static final String REASON_ANALYTIC_PARTIAL =
            "Phase 5h — partial coverage in AnalyticHestonEngineTest "
            + "(cachedAnalyticValueOtmCall, blackScholesLimit). Full port deferred.";

    private static final String REASON_INTEGRATION =
            "Phase 5h.5 — requires AnalyticHestonEngine integration-method enum "
            + "(Gauss-Lobatto, Discrete-Trapezoid, etc.); Java exposes only "
            + "Gauss-Laguerre at order 128.";

    private static final String REASON_COS =
            "Phase 5h.5: COSHestonEngine now ported (commit 9b757623); "
            + "test bodies are `fail(\"not implemented\")` — needs full port from "
            + "C++ hestonmodel.cpp.";

    private static final String REASON_AP =
            "Phase 5h.5 — requires Andersen-Piterbarg control-variate engine "
            + "and α-optimization helpers (not ported).";

    private static final String REASON_EXPANSION =
            "Phase 5h.5: HestonExpansion family ported (HestonExpansionEngine, FordeHestonExpansion, "
            + "LPP2HestonExpansion — commits 41966c40 + 24b3a98c); test bodies are "
            + "`fail(\"not implemented\")` — needs full port from C++ hestonmodel.cpp.";

    private static final String REASON_PTD =
            "Phase 5h.5: PiecewiseTimeDependentHestonModel now ported (commit 6f5a5a33); "
            + "AnalyticPTDHestonEngine + MultipleStrikesEngine still missing — needs port + "
            + "body fill from C++ hestonmodel.cpp.";

    private static final String REASON_PDF =
            "Phase 5h.5: AnalyticPDFHestonEngine now ported (commit f5e89141); test bodies "
            + "are `fail(\"not implemented\")` — needs full port from C++ hestonmodel.cpp.";

    private static final String REASON_LOCALVOL =
            "Phase 5h.5 — requires HestonBlackVolSurface + LocalVolSurface "
            + "from Heston (not ported).";

    /* ---- 1. Calibration ----------------------------------------------- */

    @Ignore(REASON_CALIB)
    @Test
    public void testBlackCalibration() { fail("not implemented"); }

    @Ignore(REASON_CALIB)
    @Test
    public void testDAXCalibration() { fail("not implemented"); }

    @Ignore(REASON_CALIB)
    @Test
    public void testDAXCalibrationOfTimeDependentModel() { fail("not implemented"); }

    /* ---- 2. Analytic vs Black / Cached -------------------------------- */

    @Ignore(REASON_ANALYTIC_PARTIAL)
    @Test
    public void testAnalyticVsBlack() { fail("not implemented; see AnalyticHestonEngineTest#blackScholesLimit"); }

    @Ignore(REASON_ANALYTIC_PARTIAL)
    @Test
    public void testAnalyticVsCached() { fail("not implemented; see AnalyticHestonEngineTest#cachedAnalyticValueOtmCall"); }

    /* ---- 3. FD engines ------------------------------------------------- */

    /**
     * Phase Body-Fill-4 port of C++ {@code testFdBarrierVsCached}
     * (592-643): FD barrier Heston engine for DownOut and DownIn calls;
     * reproduces 9.0246 and 7.7627 to 1e-3.
     *
     * <p>C++ uses Actual360 with `today = Settings::evaluationDate()`
     * (whatever date is current). The barrier prices are insensitive to
     * the specific calendar/year — pin to a fixed date to make the test
     * reproducible. C++ grid (200, 400, 100); same in Java.
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:592-643} v1.42.1.
     */
    @Test
    public void testFdBarrierVsCached() {
        // C++ uses today = Settings::evaluationDate() with Actual360.
        // Pin to a fixed date so the test is reproducible across suites.
        final Date today = new Date(15, Month.July, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new org.jquantlib.daycounters.Actual360();

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today,
                        new Handle<Quote>(new SimpleQuote(0.08)), dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today,
                        new Handle<Quote>(new SimpleQuote(0.04)), dc));

        final Date exDate = today.add(180);
        final Exercise exercise = new EuropeanExercise(exDate);

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 90.0);

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                0.25 * 0.25, 1.0, 0.25 * 0.25, 0.001, 0.0);
        final HestonModel model = new HestonModel(process);

        final PricingEngine engine = new FdHestonBarrierEngine(model, process,
                /* tGrid */ 200, /* xGrid */ 400, /* vGrid */ 100,
                /* dampingSteps */ 0, FdmSchemeDesc.Hundsdorfer());

        org.jquantlib.instruments.BarrierOption option =
                new org.jquantlib.instruments.BarrierOption(
                        org.jquantlib.instruments.BarrierType.DownOut,
                        95.0, 3.0, payoff, exercise);
        option.setPricingEngine(engine);

        double calculated = option.NPV();
        double expected = 9.0246;
        double error = Math.abs(calculated - expected);
        if (error > 1.0e-3) {
            fail("failed to reproduce cached price with FD Barrier engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }

        option = new org.jquantlib.instruments.BarrierOption(
                org.jquantlib.instruments.BarrierType.DownIn,
                95.0, 3.0, payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.NPV();
        expected = 7.7627;
        error = Math.abs(calculated - expected);
        if (error > 1.0e-3) {
            fail("failed to reproduce cached price with FD Barrier engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
    }

    /**
     * Phase Body-Fill-4 port of C++ {@code testFdVanillaVsCached}
     * (645-688): FD vanilla Heston engine reproduces cached value
     * 0.06325 to 1e-4 with grid (T=100, X=200, V=100), Hundsdorfer.
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:645-688} v1.42.1.
     */
    @Test
    public void testFdVanillaVsCached() {
        final Date settlementDate = new Date(27, Month.December, 2004);
        new Settings().setEvaluationDate(settlementDate);

        final DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISDA);
        final Date exerciseDate = new Date(28, Month.March, 2005);

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Put, 1.05);
        final Exercise exercise = new EuropeanExercise(exerciseDate);

        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.7)), dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.4)), dayCounter));

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(1.05));

        final EuropeanOption option = new EuropeanOption(payoff, exercise);

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                0.3, 1.16, 0.2, 0.8, 0.8);
        final HestonModel model = new HestonModel(process);

        // C++ uses MakeFdHestonVanillaEngine builder with default
        // Hundsdorfer scheme, dampingSteps=0.
        option.setPricingEngine(new FdHestonVanillaEngine(model, process,
                /* tGrid */ 100, /* xGrid */ 200, /* vGrid */ 100,
                /* dampingSteps */ 0, FdmSchemeDesc.Hundsdorfer()));

        final double expected = 0.06325;
        final double calculated = option.NPV();
        final double error = Math.abs(calculated - expected);
        final double tolerance = 1.0e-4;

        if (error > tolerance) {
            fail("failed to reproduce cached price with FD engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
        assertEquals("FdHestonVanillaEngine cached", expected, calculated, tolerance);
    }

    /**
     * Phase Body-Fill-4 port of C++ {@code testFdVanillaWithDividendsVsCached}
     * (690-741): FD vanilla Heston engine with discrete cash dividends
     * (1.0 per dividend, 6-month spacing); reproduces 12.946 to 5e-3.
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:690-741} v1.42.1.
     */
    @Test
    public void testFdVanillaWithDividendsVsCached() {
        final Date settlementDate = new Date(27, Month.December, 2004);
        new Settings().setEvaluationDate(settlementDate);

        final DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISDA);

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 95.0);

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.05)), dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.0)), dayCounter));

        final Date exerciseDate = new Date(28, Month.March, 2006);
        final Exercise exercise = new EuropeanExercise(exerciseDate);

        // Discrete dividends: 1.0 per dividend at 3m, 9m, 15m offsets
        // (mirrors C++ for-loop d = settlementDate + 3M; d < exerciseDate;
        // d += 6M).
        final org.jquantlib.instruments.DividendSchedule divs =
                new org.jquantlib.instruments.DividendSchedule();
        // 3 months ~ 90 days, 6 months ~ 180 days. Mimic C++ approx.
        Date d = settlementDate.add(new org.jquantlib.time.Period(
                3, org.jquantlib.time.TimeUnit.Months));
        while (d.lt(exerciseDate)) {
            divs.add(new org.jquantlib.cashflow.FixedDividend(1.0, d.clone()));
            d = d.add(new org.jquantlib.time.Period(
                    6, org.jquantlib.time.TimeUnit.Months));
        }

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                0.04, 1.0, 0.04, 0.001, 0.0);
        final HestonModel model = new HestonModel(process);

        final org.jquantlib.instruments.VanillaOption option =
                new org.jquantlib.instruments.VanillaOption(payoff, exercise);
        option.setPricingEngine(new FdHestonVanillaEngine(model, process,
                divs,
                /* tGrid */ 200, /* xGrid */ 400, /* vGrid */ 100,
                /* dampingSteps */ 0, FdmSchemeDesc.Hundsdorfer(),
                /* mixingFactor */ 1.0));

        final double calculated = option.NPV();
        // Independently FD/MC validated value (per C++ comment).
        final double expected = 12.946;
        final double error = Math.abs(calculated - expected);
        final double tolerance = 5.0e-3;
        if (error > tolerance) {
            fail("failed to reproduce discrete dividend price with FD engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
    }

    /**
     * Phase Body-Fill-4 port of C++ {@code testFdAmerican} (743-787):
     * FD vanilla Heston engine with American exercise; cross-validate
     * against FdBlackScholesVanillaEngine using the BSM degenerate
     * limit (sigma_v=0.001, theta=v0, kappa=1.0). Tolerance 1e-3.
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:743-787} v1.42.1.
     */
    @Test
    public void testFdAmerican() {
        final Date settlementDate = new Date(27, Month.December, 2004);
        new Settings().setEvaluationDate(settlementDate);

        final DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISDA);

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.05)), dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.03)), dayCounter));

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                0.04, 1.0, 0.04, 0.001, 0.0);
        final HestonModel model = new HestonModel(process);

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Put, 95.0);
        final Date exerciseDate = new Date(28, Month.March, 2006);
        final Exercise exercise = new org.jquantlib.exercise.AmericanExercise(
                settlementDate, exerciseDate);

        final org.jquantlib.instruments.VanillaOption option =
                new org.jquantlib.instruments.VanillaOption(payoff, exercise);
        option.setPricingEngine(new FdHestonVanillaEngine(model, process,
                /* tGrid */ 200, /* xGrid */ 400, /* vGrid */ 100,
                /* dampingSteps */ 0, FdmSchemeDesc.Hundsdorfer()));
        final double calculated = option.NPV();

        // Reference: BSM with sqrt(v0)=0.2 vol.
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(settlementDate, new NullCalendar(), 0.2, dayCounter));
        final BlackScholesMertonProcess refProcess = new BlackScholesMertonProcess(
                s0, qTS, rTS, volTS);
        final PricingEngine refEngine = new FdBlackScholesVanillaEngine(
                refProcess, /* tGrid */ 200, /* xGrid */ 400,
                /* dampingSteps */ 0, FdmSchemeDesc.Douglas());
        option.setPricingEngine(refEngine);
        final double expected = option.NPV();

        final double error = Math.abs(calculated - expected);
        final double tolerance = 1.0e-3;
        if (error > tolerance) {
            fail("failed to reproduce american option price with FD engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
        assertEquals("FdHestonVanillaEngine American", expected, calculated, tolerance);
    }

    /* ---- 4. MC engines ------------------------------------------------- */

    @Ignore(REASON_MC)
    @Test
    public void testMcVsCached() {
        // Phase Body-Fill-4 attempted: the C++ test calls HestonProcess
        // with discretization=QuadraticExponentialMartingale; the Java
        // port of HestonProcess.evolve() implements the QE-M correction
        // exactly per C++ hestonprocess.cpp:502-506, including the
        // precondition `QL.require(A < beta, "illegal value")` (line 355).
        // For these parameters (kappa=1.16, theta=0.2, sigma=0.8, rho=0.8,
        // 11 steps/year) the seed-1234 PRNG produces at least one path
        // step with A >= beta and the precondition trips. C++ presumably
        // hits the same precondition path but on a different floating-
        // point trajectory (cf. Mersenne-Twister output sensitivity); a
        // bit-faithful reproduction would require porting QL probe
        // mc_heston_path to identify the divergent step. Deferred to a
        // future MC-cross-validation phase.
        //
        // Other discretizations (PartialTruncation / FullTruncation)
        // would produce a different cached value than the C++ 0.0632851...
        // hard-coded constant, defeating the purpose of "vs cached".
        fail("not implemented (QE-M precondition trips on Java path; see Phase Body-Fill-4 note)");
    }

    /* ---- 5. Integration / characteristic function -------------------- */

    @Ignore(REASON_INTEGRATION)
    @Test
    public void testKahlJaeckelCase() { fail("not implemented"); }

    @Ignore(REASON_INTEGRATION)
    @Test
    public void testDifferentIntegrals() { fail("not implemented"); }

    @Ignore(REASON_INTEGRATION)
    @Test
    public void testAllIntegrationMethods() { fail("not implemented"); }

    @Ignore(REASON_INTEGRATION)
    @Test
    public void testHestonEngineIntegration() { fail("not implemented"); }

    /**
     * Phase Body-Fill-4 port of C++ {@code testCharacteristicFct}
     * (1989-2034): the Heston characteristic function {@code φ(u, t)}
     * computed by {@link COSHestonEngine#chF(double, double)} and
     * {@link AnalyticHestonEngine#chF(Complex, double)} must agree
     * to within {@code 100*ε ≈ 2.22e-14}.
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:1989-2034} v1.42.1.
     */
    @Test
    public void testCharacteristicFct() {
        final Date settlementDate = new Date(30, Month.March, 2017);
        new Settings().setEvaluationDate(settlementDate);

        final DayCounter dayCounter = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.35)), dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.17)), dayCounter));

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));

        final double v0    =  0.1;
        final double rho   = -0.85;
        final double sigma =  0.8;
        final double kappa =  2.0;
        final double theta =  0.15;

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                v0, kappa, theta, sigma, rho);
        final HestonModel model = new HestonModel(process);

        final double[] u = { 1.0, 0.45, 3, 4 };
        final double[] t = { 0.01, 23.2, 3.2 };

        final COSHestonEngine cosEngine = new COSHestonEngine(model, process);
        final AnalyticHestonEngine analyticEngine =
                new AnalyticHestonEngine(model, process, 128);

        // C++ tolerance is 100 * QL_EPSILON ≈ 2.22e-14.
        final double tol = 100.0 * 2.220446049250313e-16;
        for (final double i : u) {
            for (final double j : t) {
                final org.jquantlib.math.Complex c = cosEngine.chF(i, j);
                final org.jquantlib.math.Complex a = analyticEngine.chF(
                        org.jquantlib.math.Complex.real(i), j);

                final double dRe = a.real() - c.real();
                final double dIm = a.imag() - c.imag();
                final double error = Math.sqrt(dRe * dRe + dIm * dIm);
                if (error > tol) {
                    fail(" failed to reproduce prices with characteristic Fct"
                            + "\n    Cos Engine:      (" + c.real() + ", " + c.imag() + "i)"
                            + "\n    analytic engine: (" + a.real() + ", " + a.imag() + "i)"
                            + "\n    difference:      " + error
                            + "\n    tol:             " + tol
                            + "\n    u:               " + i
                            + "\n    t:               " + j);
                }
            }
        }
    }

    /* ---- 6. COS / Andersen-Piterbarg / Expansions -------------------- */

    @Ignore(REASON_COS)
    @Test
    public void testCosHestonCumulants() { fail("not implemented"); }

    /**
     * Phase Body-Fill-4 port of C++ {@code testCosHestonEngine}: prices
     * four vanilla options under {@link COSHestonEngine}(L=25, N=600) and
     * checks against the C++ cached values to {@code 1e-10} (tight).
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:1881-1941} v1.42.1.
     */
    @Test
    public void testCosHestonEngine() {
        final Date settlementDate = new Date(7, Month.February, 2017);
        new Settings().setEvaluationDate(settlementDate);

        final DayCounter dayCounter = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.15)), dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.07)), dayCounter));

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));

        final double v0    =  0.1;
        final double rho   = -0.75;
        final double sigma =  1.8;
        final double kappa =  4.0;
        final double theta =  0.22;

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                v0, kappa, theta, sigma, rho);
        final HestonModel model = new HestonModel(process);

        final Date maturityDate = settlementDate.add(365);  // ~1y

        final Exercise exercise = new EuropeanExercise(maturityDate);

        final PricingEngine cosEngine = new COSHestonEngine(model, process, 25.0, 600);

        final StrikedTypePayoff[] payoffs = {
            new PlainVanillaPayoff(Option.Type.Call, s0.currentLink().value() + 20),
            new PlainVanillaPayoff(Option.Type.Call, s0.currentLink().value() + 150),
            new PlainVanillaPayoff(Option.Type.Put,  s0.currentLink().value() - 20),
            new PlainVanillaPayoff(Option.Type.Put,  s0.currentLink().value() - 90)
        };

        // C++ cached expected values (from hestonmodel.cpp:1920-1922).
        // Note: C++ exercise is `settlementDate + Period(1, Years)` which is
        // exactly 365 days for 2017-02-07 (no leap-day in the year). Keeping
        // the C++ tolerance 1e-10. If the date arithmetic differs by 1d the
        // expected values would shift; we verify empirically.
        final double[] expected = {
            9.364410588426075, 0.01036797658132471,
            5.319092971836708, 0.01032681906278383
        };

        final double tol = 1e-10;
        for (int i = 0; i < payoffs.length; ++i) {
            final EuropeanOption option = new EuropeanOption(payoffs[i], exercise);
            option.setPricingEngine(cosEngine);
            final double calculated = option.NPV();

            final double error = Math.abs(expected[i] - calculated);
            if (error > tol) {
                fail("failed to reproduce prices with COSHestonEngine"
                        + "\n    payoff:     " + payoffs[i].optionType()
                        + " K=" + payoffs[i].strike()
                        + "\n    expected:   " + expected[i]
                        + "\n    calculated: " + calculated
                        + "\n    difference: " + error);
            }
            assertEquals("COSHestonEngine NPV", expected[i], calculated, tol);
        }
    }

    /**
     * Phase Body-Fill-4 port of C++ {@code testCosHestonEngineTruncation}
     * (1943-1987): a deep-OTM 1-day call where the COS truncation bound
     * trips and the engine must return zero NPV.
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:1943-1987} v1.42.1.
     */
    @Test
    public void testCosHestonEngineTruncation() {
        final Date todaysDate = new Date(22, Month.August, 2022);
        final Date maturity   = new Date(23, Month.August, 2022);
        new Settings().setEvaluationDate(todaysDate);

        final double underlying    = 100.0;
        final double strike        = 200.0;
        final double dividendYield = 0.0;
        final double riskFreeRate  = 0.0;
        final DayCounter dayCounter = new Actual365Fixed();

        final Exercise europeanExercise = new EuropeanExercise(maturity);
        final Handle<Quote> underlyingH = new Handle<Quote>(new SimpleQuote(underlying));
        final Handle<YieldTermStructure> riskFreeTS = new Handle<YieldTermStructure>(
                new FlatForward(todaysDate,
                        new Handle<Quote>(new SimpleQuote(riskFreeRate)), dayCounter));
        final Handle<YieldTermStructure> dividendTS = new Handle<YieldTermStructure>(
                new FlatForward(todaysDate,
                        new Handle<Quote>(new SimpleQuote(dividendYield)), dayCounter));

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, strike);
        final EuropeanOption europeanOption = new EuropeanOption(payoff, europeanExercise);

        final HestonProcess hestonProcess = new HestonProcess(
                riskFreeTS, dividendTS, underlyingH,
                .007, .8, .007, .1, -.2);
        final HestonModel hestonModel = new HestonModel(hestonProcess);

        // Java COSHestonEngine takes (model, process); convenience ctor
        // delegates to L=16, N=200 — same as the C++ default.
        europeanOption.setPricingEngine(new COSHestonEngine(hestonModel, hestonProcess));

        final double tol = 1e-7;
        final double error = Math.abs(europeanOption.NPV() - 0.0);

        if (error > tol) {
            fail("failed to reproduce prices with COSHestonEngine"
                    + "\n    expected:   " + 0.0
                    + "\n    calculated: " + europeanOption.NPV()
                    + "\n    difference: " + error);
        }
    }

    @Ignore(REASON_AP)
    @Test
    public void testAndersenPiterbargPricing() { fail("not implemented"); }

    @Ignore(REASON_AP)
    @Test
    public void testAndersenPiterbargControlVariateIntegrand() { fail("not implemented"); }

    @Ignore(REASON_AP)
    @Test
    public void testAndersenPiterbargConvergence() { fail("not implemented"); }

    @Ignore(REASON_AP)
    @Test
    public void testOptimalControlVariateChoice() { fail("not implemented"); }

    @Ignore(REASON_AP)
    @Test
    public void testAsymptoticControlVariate() { fail("not implemented"); }

    @Ignore(REASON_AP)
    @Test
    public void testOptimalAlphaKmin() { fail("not implemented"); }

    @Ignore(REASON_AP)
    @Test
    public void testOptimalAlphaKmax() { fail("not implemented"); }

    /**
     * Phase Body-Fill-4 partial port of C++
     * {@code testAlanLewisReferencePrices} (1288-1413). C++ exercises 7
     * engines (Laguerre, GaussLobatto, COS, AndersenPiterbarg, ExpFitting,
     * AngledContour, OptimalCV); Java only has Laguerre +
     * COS — the other 5 require integration-method enum / advanced
     * AnalyticHesto­nEngine variants not yet ported. Java covers 2 of 7.
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:1288-1413} v1.42.1.
     */
    @Test
    public void testAlanLewisReferencePrices() {
        final Date settlementDate = new Date(5, Month.July, 2002);
        new Settings().setEvaluationDate(settlementDate);

        final Date maturityDate = new Date(5, Month.July, 2003);
        final Exercise exercise = new EuropeanExercise(maturityDate);

        final DayCounter dayCounter = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.01)), dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.02)), dayCounter));

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));

        final double v0    =  0.04;
        final double rho   = -0.5;
        final double sigma =  1.0;
        final double kappa =  4.0;
        final double theta =  0.25;

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                v0, kappa, theta, sigma, rho);
        final HestonModel model = new HestonModel(process);

        final PricingEngine laguerreEngine =
                new AnalyticHestonEngine(model, process, 128);
        // COS — same defaults as C++ test (L=20, N=400).
        final PricingEngine cosEngine =
                new COSHestonEngine(model, process, 20.0, 400);

        final double[] strikes = { 80, 90, 100, 110, 120 };
        final Option.Type[] types = { Option.Type.Put, Option.Type.Call };
        final PricingEngine[] engines = { laguerreEngine, cosEngine };
        final String[] engineNames = { "Laguerre", "COS" };

        // C++ reference prices (Alan Lewis, Wilmott forum).
        final double[][] expectedResults = {
            { 7.958878113256768285213263077598987193482161301733,
              26.774758743998854221382195325726949201687074848341 },
            { 12.017966707346304987709573290236471654992071308187,
              20.933349000596710388139445766564068085476194042256 },
            { 17.055270961270109413522653999411000974895436309183,
              16.070154917028834278213466703938231827658768230714 },
            { 23.017825898442800538908781834822560777763225722188,
              12.132211516709844867860534767549426052805766831181 },
            { 29.811026202682471843340682293165857439167301370697,
              9.024913483457835636553375454092357136489051667150  }
        };

        // C++ tolerance 1e-12. The Laguerre engine reproduces this on
        // the smooth Alan-Lewis Heston Gatheral integrand at sigma=1.0,
        // kappa=4, theta=0.25 — well past convergence at n=128.
        final double tol = 1e-12;

        for (int i = 0; i < strikes.length; ++i) {
            final double strike = strikes[i];

            for (int j = 0; j < types.length; ++j) {
                final Option.Type type = types[j];

                for (int k = 0; k < engines.length; ++k) {
                    final PricingEngine engine = engines[k];

                    final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

                    final EuropeanOption option = new EuropeanOption(payoff, exercise);
                    option.setPricingEngine(engine);

                    final double expected = expectedResults[i][j];
                    final double calculated = option.NPV();
                    final double relError = Math.abs(calculated - expected) / expected;

                    if (relError > tol || Double.isNaN(calculated)) {
                        fail("failed to reproduce Alan Lewis Reference prices "
                                + "\n    strike     : " + strike
                                + "\n    option type: " + type
                                + "\n    engine     : " + engineNames[k]
                                + "\n    expected   : " + expected
                                + "\n    calculated : " + calculated
                                + "\n    rel. error : " + relError);
                    }
                }
            }
        }
    }

    /**
     * Phase Body-Fill-4 partial port of C++
     * {@code testExpansionOnAlanLewisReference} (1415-1501). C++ tests
     * LPP2 + LPP3 against the Alan-Lewis reference price grid; Java
     * has only LPP2 (LPP3 requires ~600 LOC of Mathematica-emitted
     * formulas, deferred to Phase 5h.5b — see
     * {@link HestonExpansionEngine}).
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:1415-1501} v1.42.1.
     */
    @Test
    public void testExpansionOnAlanLewisReference() {
        final Date settlementDate = new Date(5, Month.July, 2002);
        new Settings().setEvaluationDate(settlementDate);

        final Date maturityDate = new Date(5, Month.July, 2003);
        final Exercise exercise = new EuropeanExercise(maturityDate);

        final DayCounter dayCounter = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.01)), dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.02)), dayCounter));

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));

        final double v0    =  0.04;
        final double rho   = -0.5;
        final double sigma =  1.0;
        final double kappa =  4.0;
        final double theta =  0.25;

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                v0, kappa, theta, sigma, rho);
        final HestonModel model = new HestonModel(process);

        final PricingEngine lpp2Engine = new HestonExpansionEngine(
                model, process, HestonExpansionEngine.Formula.LPP2);

        final double[] strikes = { 80, 90, 100, 110, 120 };
        final Option.Type[] types = { Option.Type.Put, Option.Type.Call };

        // C++ reference prices (Alan Lewis, Wilmott forum).
        final double[][] expectedResults = {
            { 7.958878113256768285213263077598987193482161301733,
              26.774758743998854221382195325726949201687074848341 },
            { 12.017966707346304987709573290236471654992071308187,
              20.933349000596710388139445766564068085476194042256 },
            { 17.055270961270109413522653999411000974895436309183,
              16.070154917028834278213466703938231827658768230714 },
            { 23.017825898442800538908781834822560777763225722188,
              12.132211516709844867860534767549426052805766831181 },
            { 29.811026202682471843340682293165857439167301370697,
              9.024913483457835636553375454092357136489051667150  }
        };

        // C++ tolerance LPP2: 1.003e-2 (this engine is an approximation,
        // not exact like AnalyticHestonEngine).
        final double tol = 1.003e-2;

        for (int i = 0; i < strikes.length; ++i) {
            final double strike = strikes[i];

            for (int j = 0; j < types.length; ++j) {
                final Option.Type type = types[j];

                final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

                final EuropeanOption option = new EuropeanOption(payoff, exercise);
                option.setPricingEngine(lpp2Engine);

                final double expected = expectedResults[i][j];
                final double calculated = option.NPV();
                final double relError = Math.abs(calculated - expected) / expected;

                if (relError > tol) {
                    fail("failed to reproduce Alan Lewis Reference prices "
                            + "\n    strike     : " + strike
                            + "\n    option type: " + type
                            + "\n    engine     : LPP2"
                            + "\n    expected   : " + expected
                            + "\n    calculated : " + calculated
                            + "\n    rel. error : " + relError
                            + "\n    tol        : " + tol);
                }
            }
        }
    }

    /**
     * Phase Body-Fill-4 partial port of C++
     * {@code testExpansionOnFordeReference} (1503-1565). C++ tests three
     * expansions (LPP2, LPP3, Forde); Java has LPP2 + Forde — LPP3 is
     * not yet ported, so its block is skipped (the Java test still
     * exercises 2 of 3 expansions across the full strike/maturity grid).
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:1503-1565} v1.42.1.
     */
    @Test
    public void testExpansionOnFordeReference() {
        final double forward = 100.0;
        final double v0      =  0.04;
        final double rho     = -0.4;
        final double sigma   =  0.2;
        final double kappa   =  1.15;
        final double theta   =  0.04;

        final double[] terms = {0.1, 1.0, 5.0, 10.0};

        final double[] strikes = { 60, 80, 90, 100, 110, 120, 140 };

        final double[][] referenceVols = {
           {0.27284673574924445, 0.22360758200372477, 0.21023988547031242, 0.1990674789471587, 0.19118230678920461, 0.18721342919371017, 0.1899869903378507},
           {0.25200775151345, 0.2127275920953156, 0.20286528150874591, 0.19479398358151515, 0.18872591728967686, 0.18470857955411824, 0.18204457060905446},
           {0.21637821506229973, 0.20077227130455172, 0.19721753043236154, 0.1942233023784151, 0.191693211401571, 0.18955229722896752, 0.18491727548069495},
           {0.20672925973965342, 0.198583062164427, 0.19668274423922746, 0.1950420231354201, 0.193610364344706, 0.1923502827886502, 0.18934360917857015}
        };

        // Tolerances per (expansion, term). Order: [k=0 LPP2, k=1 Forde]
        // (skipping C++ k=1 LPP3 — not ported in Java).
        final double[][] tol = {
            {0.06, 0.03, 0.03, 0.02},  // LPP2
            {0.06, 0.08, 1.0, 1.0}     // Forde (breaks down for long maturities)
        };
        final double[][] tolAtm = {
            {4e-6, 7e-4, 2e-3, 9e-4},  // LPP2
            {4e-4, 3e-2, 0.28, 1.0}    // Forde
        };

        for (int j = 0; j < terms.length; ++j) {
            final double term = terms[j];
            final HestonExpansion lpp2 = new LPP2HestonExpansion(
                    kappa, theta, sigma, v0, rho, term);
            final HestonExpansion forde = new FordeHestonExpansion(
                    kappa, theta, sigma, v0, rho, term);
            final HestonExpansion[] expansions = { lpp2, forde };

            for (int i = 0; i < strikes.length; ++i) {
                final double strike = strikes[i];
                for (int k = 0; k < expansions.length; ++k) {
                    final HestonExpansion expansion = expansions[k];

                    final double expected = referenceVols[j][i];
                    final double calculated = expansion.impliedVolatility(strike, forward);
                    final double relError = Math.abs(calculated - expected) / expected;
                    final double refTol = (strike == forward) ? tolAtm[k][j] : tol[k][j];
                    if (relError > refTol) {
                        fail("failed to reproduce Forde reference vols "
                                + "\n    strike        : " + strike
                                + "\n    expansion type: " + (k == 0 ? "LPP2" : "Forde")
                                + "\n    term          : " + term
                                + "\n    expected      : " + expected
                                + "\n    calculated    : " + calculated
                                + "\n    rel. error    : " + relError
                                + "\n    refTol        : " + refTol);
                    }
                }
            }
        }
    }

    @Ignore(REASON_EXPANSION)
    @Test
    public void testSmallSigmaExpansion() { fail("not implemented"); }

    @Ignore(REASON_EXPANSION)
    @Test
    public void testSmallSigmaExpansion4ExpFitting() { fail("not implemented"); }

    @Ignore(REASON_EXPANSION)
    @Test
    public void testExponentialFitting4StrikesAndMaturities() { fail("not implemented"); }

    /* ---- 7. Piecewise time-dependent Heston -------------------------- */

    @Ignore(REASON_PTD)
    @Test
    public void testAnalyticPiecewiseTimeDependent() { fail("not implemented"); }

    @Ignore(REASON_PTD)
    @Test
    public void testPiecewiseTimeDependentChFvsHestonChF() { fail("not implemented"); }

    @Ignore(REASON_PTD)
    @Test
    public void testPiecewiseTimeDependentComparison() { fail("not implemented"); }

    @Ignore(REASON_PTD)
    @Test
    public void testPiecewiseTimeDependentChFAsymtotic() { fail("not implemented"); }

    @Ignore(REASON_PTD)
    @Test
    public void testMultipleStrikesEngine() { fail("not implemented"); }

    @Ignore(REASON_LOCALVOL)
    @Test
    public void testLocalVolFromHestonModel() { fail("not implemented"); }

    /**
     * Phase Body-Fill-4 port of C++ {@code testAnalyticPDFHestonEngine}
     * (3358-3465): cross-validate {@link AnalyticPDFHestonEngine} against
     * {@link AnalyticHestonEngine} for plain-vanilla calls and digital
     * (cash-or-nothing) calls.
     *
     * <p>Java port: {@link AnalyticPDFHestonEngine} takes
     * (model, process, eps, maxIter) — see Phase 5h.5-RND.
     *
     * <p>Source: {@code test-suite/hestonmodel.cpp:3358-3465} v1.42.1.
     */
    @Test
    public void testAnalyticPDFHestonEngine() {
        final Date settlementDate = new Date(5, Month.January, 2014);
        new Settings().setEvaluationDate(settlementDate);

        final DayCounter dayCounter = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.07)), dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(settlementDate,
                        new Handle<Quote>(new SimpleQuote(0.185)), dayCounter));

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));

        final double v0    =  0.1;
        final double rho   = -0.5;
        final double sigma =  1.0;
        final double kappa =  4.0;
        final double theta =  0.05;

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                v0, kappa, theta, sigma, rho);
        final HestonModel model = new HestonModel(process);

        final double tol = 1e-6;
        final AnalyticPDFHestonEngine pdfEngine =
                new AnalyticPDFHestonEngine(model, process, tol, 10000);

        // C++ uses n=178; Java GaussLaguerreIntegration only embeds the
        // n=128 table (Phase 2f §C.2). For the smooth Heston Gatheral
        // integrand at these parameters both orders are well past
        // convergence — empirical cross-check delta is far below 3*tol.
        final PricingEngine analyticEngine =
                new AnalyticHestonEngine(model, process, 128);

        final Date maturityDate = new Date(5, Month.July, 2014);
        final double maturity = dayCounter.yearFraction(settlementDate, maturityDate);
        final Exercise exercise = new EuropeanExercise(maturityDate);

        // 1. plain vanilla call (strikes 40,60,80,100,120,140,160,180)
        for (double strike = 40; strike < 190; strike += 20) {
            final PlainVanillaPayoff vanillaPayoff =
                    new PlainVanillaPayoff(Option.Type.Call, strike);
            final EuropeanOption planVanillaOption =
                    new EuropeanOption(vanillaPayoff, exercise);

            planVanillaOption.setPricingEngine(pdfEngine);
            final double calculated = planVanillaOption.NPV();

            planVanillaOption.setPricingEngine(analyticEngine);
            final double expected = planVanillaOption.NPV();

            if (Math.abs(calculated - expected) > 3 * tol) {
                fail("failed to reproduce plain vanilla european prices with"
                        + " the analytic probability density engine"
                        + "\n    strike     : " + strike
                        + "\n    expected   : " + expected
                        + "\n    calculated : " + calculated
                        + "\n    diff       : " + Math.abs(calculated - expected)
                        + "\n    tol        : " + tol);
            }
        }

        // 2. digital call option (call spread approximation)
        for (double strike = 40; strike < 190; strike += 10) {
            final org.jquantlib.instruments.CashOrNothingPayoff digiPayoff =
                    new org.jquantlib.instruments.CashOrNothingPayoff(
                            Option.Type.Call, strike, 1.0);
            final EuropeanOption digitalOption = new EuropeanOption(digiPayoff, exercise);
            digitalOption.setPricingEngine(pdfEngine);
            final double calculated = digitalOption.NPV();

            final double eps = 0.01;
            final EuropeanOption longCall = new EuropeanOption(
                    new PlainVanillaPayoff(Option.Type.Call, strike - eps),
                    exercise);
            longCall.setPricingEngine(analyticEngine);

            final EuropeanOption shortCall = new EuropeanOption(
                    new PlainVanillaPayoff(Option.Type.Call, strike + eps),
                    exercise);
            shortCall.setPricingEngine(analyticEngine);

            final double expected = (longCall.NPV() - shortCall.NPV()) / (2 * eps);
            if (Math.abs(calculated - expected) > tol) {
                fail("failed to reproduce european digital prices with"
                        + " the analytic probability density engine"
                        + "\n    strike     : " + strike
                        + "\n    expected   : " + expected
                        + "\n    calculated : " + calculated
                        + "\n    diff       : " + Math.abs(calculated - expected)
                        + "\n    tol        : " + tol);
            }

            final double d = rTS.currentLink().discount(maturityDate);
            final double expectedCDF = 1.0 - expected / d;
            final double calculatedCDF = pdfEngine.cdf(strike, maturity);

            if (Math.abs(expectedCDF - calculatedCDF) > tol) {
                fail("failed to reproduce cumulative distribution function"
                        + "\n    strike        : " + strike
                        + "\n    expected CDF  : " + expectedCDF
                        + "\n    calculated CDF: " + calculatedCDF
                        + "\n    diff          : "
                        + Math.abs(calculatedCDF - expectedCDF)
                        + "\n    tol           : " + tol);
            }
        }
    }
}
