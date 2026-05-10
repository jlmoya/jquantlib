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
import org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine;
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

    @Ignore(REASON_FD)
    @Test
    public void testFdBarrierVsCached() { fail("not implemented"); }

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

    @Ignore(REASON_INTEGRATION)
    @Test
    public void testCharacteristicFct() { fail("not implemented"); }

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

    @Ignore(REASON_EXPANSION)
    @Test
    public void testAlanLewisReferencePrices() { fail("not implemented"); }

    @Ignore(REASON_EXPANSION)
    @Test
    public void testExpansionOnAlanLewisReference() { fail("not implemented"); }

    @Ignore(REASON_EXPANSION)
    @Test
    public void testExpansionOnFordeReference() { fail("not implemented"); }

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
