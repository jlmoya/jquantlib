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
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticPDFHestonEngine;
import org.jquantlib.pricingengines.vanilla.COSHestonEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
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

    @Ignore(REASON_FD)
    @Test
    public void testFdVanillaVsCached() { fail("not implemented"); }

    @Ignore(REASON_FD)
    @Test
    public void testFdVanillaWithDividendsVsCached() { fail("not implemented"); }

    @Ignore(REASON_FD)
    @Test
    public void testFdAmerican() { fail("not implemented"); }

    /* ---- 4. MC engines ------------------------------------------------- */

    @Ignore(REASON_MC)
    @Test
    public void testMcVsCached() { fail("not implemented"); }

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

    @Ignore(REASON_PDF)
    @Test
    public void testAnalyticPDFHestonEngine() { fail("not implemented"); }
}
