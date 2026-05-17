/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.asian.AnalyticContinuousGeometricAveragePriceAsianHestonEngine;
import org.jquantlib.experimental.asian.AnalyticDiscreteGeometricAveragePriceAsianHestonEngine;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.ContinuousAveragingAsianOption;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.asian.ContinuousArithmeticAsianLevyEngine;
import org.jquantlib.pricingengines.asian.TurnbullWakemanAsianEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackVarianceCurve;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5i skeleton port of {@code test-suite/asianoptions.cpp} v1.42.1
 * test cases NOT already covered by {@link AsianOptionTest}.
 *
 * <p>{@link AsianOptionTest} (Phase 1 / 2) exercises the 4 base analytic
 * cases plus their Greeks:
 * <ul>
 *   <li>{@code testAnalyticContinuousGeometricAveragePrice}</li>
 *   <li>{@code testAnalyticContinuousGeometricAveragePriceGreeks}</li>
 *   <li>{@code testAnalyticDiscreteGeometricAveragePrice}</li>
 *   <li>{@code testAnalyticDiscreteGeometricAveragePriceGreeks}</li>
 * </ul>
 *
 * <p>The remaining ~19 cases below exercise:
 * <ul>
 *   <li><strong>MC discrete arithmetic / geometric engines</strong> —
 *       require {@code MCDiscreteGeometricAPEngine}, {@code
 *       MCDiscreteArithmeticAPEngine}, {@code MCDiscreteArithmeticASEngine}
 *       (Java has the {@code DiscreteAveragingAsianOption} instrument and
 *       the {@code MakeMCDiscreteGeometricAPEngine} factory family is
 *       partially scaffolded under {@code pricingengines.asian}, but the
 *       MC engines themselves are not yet ported);</li>
 *   <li><strong>Heston-driven Asian engines</strong> —
 *       {@code MCDiscreteGeometricAPHestonEngine},
 *       {@code MCDiscreteArithmeticAPHestonEngine},
 *       {@code AnalyticContinuousGeometricAveragePriceAsianHestonEngine},
 *       {@code AnalyticDiscreteGeometricAveragePriceAsianHestonEngine}
 *       (the analytic-Heston engines exist under
 *       {@code experimental.asian}; their tests live there too —
 *       these wrap the in-instrument-package C++ cases);</li>
 *   <li><strong>Turnbull-Wakeman / Levy / Vecer / Choi analytic engines</strong>
 *       — require {@code TurnbullWakemanAsianEngine},
 *       {@code AnalyticContinuousArithmeticAsianLevyEngine},
 *       {@code ContinuousArithmeticAsianVecerEngine},
 *       {@code ChoiAsianEngine}.  The Vecer engine has Java coverage
 *       under {@code experimental.exoticoptions.ContinuousArithmeticAsianVecerEngine};
 *       Turnbull-Wakeman ported in Phase 5e.5b-CFC-d-72;
 *       Levy / Choi are not yet ported.</li>
 *   <li><strong>Past fixings semantics</strong> — require completed past-fixing
 *       wiring on {@link org.jquantlib.instruments.DiscreteAveragingAsianOption}
 *       (Java instrument exists; past-fixing accumulator path is not
 *       fully ported).</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/asianoptions.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class AsianOptionsAdditionalTest {

    private static final String REASON_MC =
            "Phase 5i.5 — requires MC discrete-geometric / discrete-arithmetic "
          + "Asian engines (MakeMCDiscreteGeometricAPEngine family)";

    private static final String REASON_MC_HESTON =
            "Phase 5i.5 — requires MC Heston-driven Asian engines "
          + "(MCDiscreteGeometricAPHestonEngine, MCDiscreteArithmeticAPHestonEngine)";

    private static final String REASON_ANALYTIC_HESTON_PAST_FIXINGS =
            "Phase 5i.5 — past-fixings variant compares the analytic Heston "
          + "engine against MakeMCDiscreteGeometricAPHestonEngine, which is "
          + "not yet ported (analytic engine alone exercised by the "
          + "non-past-fixings body-fills above)";

    private static final String REASON_LEVY =
            "Phase 5i.5 — requires AnalyticContinuousArithmeticAsianLevyEngine "
          + "port (no Java equivalent yet)";

    private static final String REASON_VECER =
            "Phase 5i.5 — Vecer engine ported under experimental.exoticoptions; "
          + "in-instruments-package wrapper test deferred until the experimental "
          + "engine is promoted";

    private static final String REASON_CHOI =
            "Phase 5i.5 — requires ChoiAsianEngine port (newer v1.41+ engine, "
          + "no Java equivalent yet)";

    private static final String REASON_PAST_FIXINGS =
            "Phase 5i.5 — past-fixing accumulator path on "
          + "DiscreteAveragingAsianOption requires completing the running-sum "
          + "/ running-product wiring against C++ semantics";

    private static final String REASON_SEASONED =
            "Phase 5i.5 — Choi engine prereq + seasoned-option time-step "
          + "schedule generation against C++ v1.42.1 semantics";

    /**
     * Port of {@code test-suite/asianoptions.cpp::testAnalyticContinuousGeometricAveragePriceHeston}.
     *
     * <p>Reference data from Kim & Wee, "Pricing of Geometric Asian Options under
     * Heston's Stochastic Volatility Model", Quant. Finance 14:10, 1795-1809 (2011),
     * Table 1 (Feller condition obeyed) and Table 4 (Feller condition violated); plus
     * Kim, Kim, Kim & Wee, "A Recursive Method for Discretely Monitored Geometric
     * Asian Option Prices", Bull. Korean Math. Soc. 53, 733-749 (2016), Tables 1-3
     * (continuous limit). Engine: experimental
     * {@link AnalyticContinuousGeometricAveragePriceAsianHestonEngine} (Kim-Wee 2014).
     *
     * <p>Tolerance: 1e-2 absolute for the 2011 paper data (matches C++ — limited by
     * day-bracket precision around 547.5d ≈ 1.5y); per-case 1e-2 / 2e-2 for the 2016
     * recursive-paper data (30-day options need the looser bracket).
     */
    @Test
    public void testAnalyticContinuousGeometricAveragePriceHeston() {

        // 73, 548 and 1095 are 0.2, 1.5 and 3.0 years respectively in Actual365Fixed
        final int[] days = { 73, 73, 73, 73, 73,
                             548, 548, 548, 548, 548,
                             1095, 1095, 1095, 1095, 1095 };
        final double[] strikes = { 90.0, 95.0, 100.0, 105.0, 110.0,
                                   90.0, 95.0, 100.0, 105.0, 110.0,
                                   90.0, 95.0, 100.0, 105.0, 110.0 };

        // Prices from Table 1 (params obey Feller condition)
        final double[] prices = { 10.6571, 6.5871, 3.4478, 1.4552, 0.4724,
                                  16.5030, 13.7625, 11.3374, 9.2245, 7.4122,
                                  20.5102, 18.3060, 16.2895, 14.4531, 12.7882 };

        // Prices from Table 4 (params do not obey Feller condition)
        final double[] prices_2 = { 10.6425, 6.4362, 3.1578, 1.1936, 0.3609,
                                    14.9955, 11.6707, 8.7767, 6.3818, 4.5118,
                                    18.1219, 15.2009, 12.5707, 10.2539, 8.2611 };

        // 0.2 and 3.0 match to 1e-4. Unfortunately 1.5 corresponds to 547.5 days,
        // 547 and 548 bound the expected answer but are both out by ~5e-3.
        final double tolerance = 1.0e-2;

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Settings().evaluationDate();
        final Option.Type type = Option.Type.Call;
        final AverageType averageType = AverageType.Geometric;

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.05);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);

        // ---- Set 1: Feller condition obeyed ----
        final double v0     = 0.09;
        final double kappa  = 1.15;
        final double theta  = 0.348;
        final double sigma  = 0.39;
        final double rho    = -0.64;
        final HestonProcess hestonProcess = new HestonProcess(
                new Handle<YieldTermStructure>(rTS),
                new Handle<YieldTermStructure>(qTS),
                spot, v0, kappa, theta, sigma, rho);
        final AnalyticContinuousGeometricAveragePriceAsianHestonEngine engine =
                new AnalyticContinuousGeometricAveragePriceAsianHestonEngine(hestonProcess);

        for (int i = 0; i < strikes.length; i++) {
            final double strike = strikes[i];
            final int day = days[i];
            final double expected = prices[i];
            final Date expiryDate = today.add(day);
            final Exercise europeanExercise = new EuropeanExercise(expiryDate);
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);
            final ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(averageType, payoff, europeanExercise);
            option.setPricingEngine(engine);
            final double calculated = option.NPV();
            final double error = Math.abs(calculated - expected);
            if (error > tolerance) {
                fail("Failed to reproduce Kim-Wee 2014 Table 1 NPV:"
                        + "\n    strike:     " + strike
                        + "\n    days:       " + day
                        + "\n    expected:   " + expected
                        + "\n    calculated: " + calculated
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + tolerance);
            }
        }

        // ---- Set 2: Feller condition violated ----
        final double v0_2     = 0.09;
        final double kappa_2  = 2.0;
        final double theta_2  = 0.09;
        final double sigma_2  = 1.0;
        final double rho_2    = -0.3;
        final HestonProcess hestonProcess_2 = new HestonProcess(
                new Handle<YieldTermStructure>(rTS),
                new Handle<YieldTermStructure>(qTS),
                spot, v0_2, kappa_2, theta_2, sigma_2, rho_2);
        final AnalyticContinuousGeometricAveragePriceAsianHestonEngine engine_2 =
                new AnalyticContinuousGeometricAveragePriceAsianHestonEngine(hestonProcess_2);

        for (int i = 0; i < strikes.length; i++) {
            final double strike = strikes[i];
            final int day = days[i];
            final double expected = prices_2[i];
            final Date expiryDate = today.add(day);
            final Exercise europeanExercise = new EuropeanExercise(expiryDate);
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);
            final ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(averageType, payoff, europeanExercise);
            option.setPricingEngine(engine_2);
            final double calculated = option.NPV();
            final double error = Math.abs(calculated - expected);
            if (error > tolerance) {
                fail("Failed to reproduce Kim-Wee 2014 Table 4 NPV (Feller violated):"
                        + "\n    strike:     " + strike
                        + "\n    days:       " + day
                        + "\n    expected:   " + expected
                        + "\n    calculated: " + calculated
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + tolerance);
            }
        }

        // ---- Set 3: data from Kim-Kim-Kim-Wee 2016, continuous limit ----
        final int[] days_3 = { 30, 91, 182, 365, 730, 1095,
                               30, 91, 182, 365, 730, 1095,
                               30, 91, 182, 365, 730, 1095 };
        final double[] strikes_3 = { 90, 90, 90, 90, 90, 90,
                                     100, 100, 100, 100, 100, 100,
                                     110, 110, 110, 110, 110, 110 };
        // 30-day options need wider tolerance due to the day-bracket issue.
        final double[] tol_3 = { 2.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2,
                                 2.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2,
                                 2.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2 };
        // Prices from Tables 1, 2 and 3
        final double[] prices_3 = { 10.1513, 10.8175, 11.8664, 13.5931, 16.0988, 17.9475,
                                    2.0472, 3.5735, 5.0588, 7.1132, 9.9139, 11.9959,
                                    0.0350, 0.4869, 1.3376, 2.8569, 5.2804, 7.2682 };

        // Note that although these parameters look similar to the first set above,
        // theta is a factor of 10 smaller. (C++ comment: "I guess there is a
        // mis-transcription somewhere!")
        final double v0_3     = 0.09;
        final double kappa_3  = 1.15;
        final double theta_3  = 0.0348;
        final double sigma_3  = 0.39;
        final double rho_3    = -0.64;
        final HestonProcess hestonProcess_3 = new HestonProcess(
                new Handle<YieldTermStructure>(rTS),
                new Handle<YieldTermStructure>(qTS),
                spot, v0_3, kappa_3, theta_3, sigma_3, rho_3);
        final AnalyticContinuousGeometricAveragePriceAsianHestonEngine engine_3 =
                new AnalyticContinuousGeometricAveragePriceAsianHestonEngine(hestonProcess_3);

        for (int i = 0; i < strikes_3.length; i++) {
            final double strike = strikes_3[i];
            final int day = days_3[i];
            final double expected = prices_3[i];
            final double caseTolerance = tol_3[i];
            final Date expiryDate = today.add(day);
            final Exercise europeanExercise = new EuropeanExercise(expiryDate);
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);
            final ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(averageType, payoff, europeanExercise);
            option.setPricingEngine(engine_3);
            final double calculated = option.NPV();
            final double error = Math.abs(calculated - expected);
            if (error > caseTolerance) {
                fail("Failed to reproduce Kim-Kim-Kim-Wee 2016 continuous-limit NPV:"
                        + "\n    strike:     " + strike
                        + "\n    days:       " + day
                        + "\n    expected:   " + expected
                        + "\n    calculated: " + calculated
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + caseTolerance);
            }
        }
    }

    /**
     * Port of {@code test-suite/asianoptions.cpp::testAnalyticDiscreteGeometricAveragePriceHeston}.
     *
     * <p>Reference data from Kim, Kim, Kim & Wee, "A Recursive Method for
     * Discretely Monitored Geometric Asian Option Prices", Bull. Korean Math.
     * Soc. 53, 733-749 (2016), Tables 1-3 (weekly fixings). Engine:
     * experimental {@link AnalyticDiscreteGeometricAveragePriceAsianHestonEngine}
     * (Kim-Kim-Kim-Wee 2016 recursive method).
     *
     * <p>Tolerance: per-case 1e-2 to 8e-2 (matches the C++ per-case tolerance
     * table). 30-day options need wider tolerance due to uncertainty around
     * what "weekly fixing" dates mean over a 30-day month.
     */
    @Test
    public void testAnalyticDiscreteGeometricAveragePriceHeston() {

        // Per-case tolerances matching C++ tol[] in asianoptions.cpp.
        final double[] tol = { 3.0e-2, 2.0e-2, 2.0e-2, 2.0e-2, 3.0e-2, 4.0e-2,
                               8.0e-2, 1.0e-2, 2.0e-2, 3.0e-2, 3.0e-2, 4.0e-2,
                               2.0e-2, 1.0e-2, 1.0e-2, 2.0e-2, 3.0e-2, 4.0e-2 };

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Settings().evaluationDate();

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.05);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);

        final double v0     = 0.09;
        final double kappa  = 1.15;
        final double theta  = 0.0348;
        final double sigma  = 0.39;
        final double rho    = -0.64;
        final HestonProcess hestonProcess = new HestonProcess(
                new Handle<YieldTermStructure>(rTS),
                new Handle<YieldTermStructure>(qTS),
                spot, v0, kappa, theta, sigma, rho);
        final AnalyticDiscreteGeometricAveragePriceAsianHestonEngine engine =
                new AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(hestonProcess);

        // Tables 1, 2, 3 reference data (from helper testDiscreteGeometricAveragePriceHeston)
        final int[] days = { 30, 91, 182, 365, 730, 1095,
                             30, 91, 182, 365, 730, 1095,
                             30, 91, 182, 365, 730, 1095 };
        final double[] strikes = { 90, 90, 90, 90, 90, 90,
                                   100, 100, 100, 100, 100, 100,
                                   110, 110, 110, 110, 110, 110 };
        final double[] prices = { 10.2732, 10.9554, 11.9916, 13.6950, 16.1773, 18.0146,
                                  2.4389, 3.7881, 5.2132, 7.2243, 9.9948, 12.0639,
                                  0.1012, 0.5949, 1.4444, 2.9479, 5.3531, 7.3315 };

        final Option.Type type = Option.Type.Call;
        final AverageType averageType = AverageType.Geometric;
        final double runningAccumulator = 1.0;
        final int pastFixings = 0;

        for (int i = 0; i < strikes.length; i++) {
            final double strike = strikes[i];
            final int day = days[i];
            final double expected = prices[i];
            final double caseTolerance = tol[i];

            // "weekly fixings" — floor(day/7) future fixings; C++ loop:
            //   for (int i=futureFixings-1; i>=0; i--) fixingDates[i] = expiryDate - i*7;
            // so fixingDates[0] = expiryDate and fixingDates[futureFixings-1] =
            // expiryDate - (futureFixings-1)*7 (earliest). The engine sorts internally,
            // so traversal order is immaterial for pricing.
            final int futureFixings = (int) Math.floor(day / 7.0);
            final List<Date> fixingDates = new ArrayList<Date>(futureFixings);
            final Date expiryDate = today.add(day);
            for (int j = 0; j < futureFixings; j++) {
                fixingDates.add(null);
            }
            for (int j = futureFixings - 1; j >= 0; j--) {
                fixingDates.set(j, expiryDate.add(-j * 7));
            }

            final Exercise europeanExercise = new EuropeanExercise(expiryDate);
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

            final DiscreteAveragingAsianOption option =
                    new DiscreteAveragingAsianOption(averageType, runningAccumulator,
                            pastFixings, fixingDates, payoff, europeanExercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - expected);
            if (error > caseTolerance) {
                fail("Failed to reproduce Kim-Kim-Kim-Wee 2016 discrete NPV:"
                        + "\n    strike:     " + strike
                        + "\n    days:       " + day
                        + "\n    expected:   " + expected
                        + "\n    calculated: " + calculated
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + caseTolerance);
            }
        }
    }

    @Ignore(REASON_ANALYTIC_HESTON_PAST_FIXINGS)
    @Test
    public void testDiscreteGeometricAveragePriceHestonPastFixings() { fail("not implemented"); }

    @Ignore("AsianOptionTest covers Strike-flavour discrete geometric analytic")
    @Test
    public void testAnalyticDiscreteGeometricAverageStrike() { fail("not implemented"); }

    @Ignore(REASON_MC)
    @Test
    public void testMCDiscreteGeometricAveragePrice() { fail("not implemented"); }

    @Ignore(REASON_MC_HESTON)
    @Test
    public void testMCDiscreteGeometricAveragePriceHeston() { fail("not implemented"); }

    @Ignore(REASON_MC)
    @Test
    public void testMCDiscreteArithmeticAveragePrice() { fail("not implemented"); }

    @Ignore(REASON_MC_HESTON)
    @Test
    public void testMCDiscreteArithmeticAveragePriceHeston() { fail("not implemented"); }

    @Ignore(REASON_MC)
    @Test
    public void testMCDiscreteArithmeticAverageStrike() { fail("not implemented"); }

    @Ignore(REASON_MC + " + EuropeanExercise-date scheduling variant")
    @Test
    public void testMCDiscreteArithmeticAverageStrikeExerciseDate() { fail("not implemented"); }

    @Ignore(REASON_PAST_FIXINGS)
    @Test
    public void testPastFixings() { fail("not implemented"); }

    @Ignore(REASON_PAST_FIXINGS + " + model-dependence verification (MC / FD)")
    @Test
    public void testPastFixingsModelDependency() { fail("not implemented"); }

    @Ignore(REASON_PAST_FIXINGS + " + degenerate all-past schedule")
    @Test
    public void testAllFixingsInThePast() { fail("not implemented"); }

    /**
     * Port of {@code test-suite/asianoptions.cpp::testTurnbullWakemanAsianEngine}.
     *
     * <p>Data from Haug, "Option Pricing Formulas", Table 4-28, p.201.
     * Tests reproduction of analytical NPV against literature, and verifies
     * the analytical Delta / Gamma against bump-and-revalue numerical greeks
     * for 30 (Type, strike, slope) cases x flat/up/down term structures.
     */
    @Test
    public void testTurnbullWakemanAsianEngine() {

        // {type, underlying, strike, b, rfRate, t1, expiry, fixings, baseVol, slope, expected}
        final TWCase[] cases = new TWCase[] {
            new TWCase(Option.Type.Call, 100, 80,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat", 19.5152),
            new TWCase(Option.Type.Call, 100, 80,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",   19.5063),
            new TWCase(Option.Type.Call, 100, 80,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "down", 19.5885),
            new TWCase(Option.Type.Put,  100, 80,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat",  0.0090),
            new TWCase(Option.Type.Put,  100, 80,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",    0.0001),
            new TWCase(Option.Type.Put,  100, 80,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "down",  0.0823),

            new TWCase(Option.Type.Call, 100, 90,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat", 10.1437),
            new TWCase(Option.Type.Call, 100, 90,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",    9.8313),
            new TWCase(Option.Type.Call, 100, 90,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "down", 10.7062),
            new TWCase(Option.Type.Put,  100, 90,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat",  0.3906),
            new TWCase(Option.Type.Put,  100, 90,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",    0.0782),
            new TWCase(Option.Type.Put,  100, 90,  0, 0.05, 1.0/52, 0.5, 26, 0.2, "down",  0.9531),

            new TWCase(Option.Type.Call, 100, 100, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat",  3.2700),
            new TWCase(Option.Type.Call, 100, 100, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",    2.2819),
            new TWCase(Option.Type.Call, 100, 100, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "down",  4.3370),
            new TWCase(Option.Type.Put,  100, 100, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat",  3.2700),
            new TWCase(Option.Type.Put,  100, 100, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",    2.2819),
            new TWCase(Option.Type.Put,  100, 100, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "down",  4.3370),

            new TWCase(Option.Type.Call, 100, 110, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat",  0.5515),
            new TWCase(Option.Type.Call, 100, 110, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",    0.1314),
            new TWCase(Option.Type.Call, 100, 110, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "down",  1.2429),
            new TWCase(Option.Type.Put,  100, 110, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat", 10.3046),
            new TWCase(Option.Type.Put,  100, 110, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",    9.8845),
            new TWCase(Option.Type.Put,  100, 110, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "down", 10.9960),

            new TWCase(Option.Type.Call, 100, 120, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat",  0.0479),
            new TWCase(Option.Type.Call, 100, 120, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",    0.0016),
            new TWCase(Option.Type.Call, 100, 120, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "down",  0.2547),
            new TWCase(Option.Type.Put,  100, 120, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "flat", 19.5541),
            new TWCase(Option.Type.Put,  100, 120, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "up",   19.5078),
            new TWCase(Option.Type.Put,  100, 120, 0, 0.05, 1.0/52, 0.5, 26, 0.2, "down", 19.7609),
        };

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();
        final double volSlope = 0.005;

        for (final TWCase l : cases) {
            final double dt = (l.expiry - l.first) / (l.fixings - 1);
            final Date[] fixingDates = new Date[l.fixings];
            fixingDates[0] = today.add(timeToDays360(l.first));
            for (int i = 1; i < l.fixings; i++) {
                fixingDates[i] = today.add(timeToDays360(i * dt + l.first));
            }

            // Market data
            final SimpleQuote spot = new SimpleQuote(l.underlying);
            final YieldTermStructure qTS = Utilities.flatRate(today, l.b + l.riskFreeRate, dc);
            final YieldTermStructure rTS = Utilities.flatRate(today, l.riskFreeRate, dc);

            final BlackVolTermStructure volTS;
            if ("flat".equals(l.slope)) {
                volTS = Utilities.flatVol(today, l.volatility, dc);
            } else if ("up".equals(l.slope)) {
                // Vols rise from 7.5% to 20% (l.volatility = 20%, volSlope = 0.005,
                // l.fixings - 1 = 25, so first vol = 0.2 - 25*0.005 = 0.075).
                final double[] volatilities = new double[l.fixings];
                for (int i = 0; i < l.fixings; ++i) {
                    volatilities[i] = l.volatility - (l.fixings - 1) * volSlope + i * volSlope;
                }
                final BlackVarianceCurve curve =
                        new BlackVarianceCurve(today, fixingDates, volatilities, dc, true);
                curve.setInterpolation();
                volTS = curve;
            } else if ("down".equals(l.slope)) {
                // Vols fall from 32.5% to 20% (forceMonotoneVariance = false).
                final double[] volatilities = new double[l.fixings];
                for (int i = 0; i < l.fixings; ++i) {
                    volatilities[i] = l.volatility + (l.fixings - 1) * volSlope - i * volSlope;
                }
                final BlackVarianceCurve curve =
                        new BlackVarianceCurve(today, fixingDates, volatilities, dc, false);
                curve.setInterpolation();
                volTS = curve;
            } else {
                throw new AssertionError("unexpected slope type: " + l.slope);
            }

            final AverageType averageType = AverageType.Arithmetic;
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(l.type, l.strike);

            final Date maturity = today.add(timeToDays360(l.expiry));
            final Exercise exercise = new EuropeanExercise(maturity);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engine = new TurnbullWakemanAsianEngine(stochProcess);

            final List<Date> fixingList = new ArrayList<Date>(l.fixings);
            for (final Date d : fixingDates) {
                fixingList.add(d);
            }

            final DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                    averageType, 0.0, 0, fixingList, payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double expected = l.result;
            final double tolerance = 2.5e-3;
            final double error = Math.abs(expected - calculated);
            if (error > tolerance) {
                fail("Failed to reproduce expected NPV:"
                        + "\n    type:            " + l.type
                        + "\n    strike:          " + l.strike
                        + "\n    slope:           " + l.slope
                        + "\n    expected:        " + expected
                        + "\n    calculated:      " + calculated
                        + "\n    error:           " + error);
            }

            // Compare greeks to numerical bump-and-revalue greeks
            final double dS = 0.001;
            final double delta = option.delta();
            final double gamma = option.gamma();

            final SimpleQuote spotUp = new SimpleQuote(l.underlying + dS);
            final SimpleQuote spotDown = new SimpleQuote(l.underlying - dS);

            final BlackScholesMertonProcess stochProcessUp = new BlackScholesMertonProcess(
                    new Handle<Quote>(spotUp),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final BlackScholesMertonProcess stochProcessDown = new BlackScholesMertonProcess(
                    new Handle<Quote>(spotDown),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engineUp = new TurnbullWakemanAsianEngine(stochProcessUp);
            final PricingEngine engineDown = new TurnbullWakemanAsianEngine(stochProcessDown);

            option.setPricingEngine(engineUp);
            final double calculatedUp = option.NPV();

            option.setPricingEngine(engineDown);
            final double calculatedDown = option.NPV();

            final double deltaBump = (calculatedUp - calculatedDown) / (2 * dS);
            final double gammaBump = (calculatedUp + calculatedDown - 2 * calculated) / (dS * dS);

            final double greekTolerance = 1.0e-6;
            final double deltaError = Math.abs(deltaBump - delta);
            if (deltaError > greekTolerance) {
                fail("Analytical delta failed to match bump delta:"
                        + "\n    type:    " + l.type
                        + "\n    strike:  " + l.strike
                        + "\n    slope:   " + l.slope
                        + "\n    analytic: " + delta
                        + "\n    bump:     " + deltaBump
                        + "\n    error:    " + deltaError);
            }

            final double gammaError = Math.abs(gammaBump - gamma);
            if (gammaError > greekTolerance) {
                fail("Analytical gamma failed to match bump gamma:"
                        + "\n    type:    " + l.type
                        + "\n    strike:  " + l.strike
                        + "\n    slope:   " + l.slope
                        + "\n    analytic: " + gamma
                        + "\n    bump:     " + gammaBump
                        + "\n    error:    " + gammaError);
            }
        }
    }

    /**
     * Port of {@code test-suite/asianoptions.cpp::testLevyEngine}.
     *
     * <p>Data from Haug, "Option Pricing Formulas", p. 99-100.  Verifies the
     * Levy two-moment matching engine for continuously-averaged arithmetic
     * Asian options against the published reference values, including
     * seasoned options (where averaging has already begun before today).
     *
     * <p>Tolerance: LOOSE {@code 1e-4} — matches the C++ reference tolerance
     * for this moment-matching approximation.
     */
    @Test
    public void testLevyEngine() {

        final LevyCase[] cases = new LevyCase[] {
            new LevyCase(Option.Type.Call,  6.80,   6.80,   6.90, 0.09, 0.07, 0.14, 180,   0, 0.0944),
            new LevyCase(Option.Type.Put,   6.80,   6.80,   6.90, 0.09, 0.07, 0.14, 180,   0, 0.2237),
            new LevyCase(Option.Type.Call, 100.0, 100.0,   95.0, 0.05, 0.10, 0.15, 270,   0, 7.0544),
            new LevyCase(Option.Type.Call, 100.0, 100.0,   95.0, 0.05, 0.10, 0.15, 270,  90, 5.6731),
            new LevyCase(Option.Type.Call, 100.0, 100.0,   95.0, 0.05, 0.10, 0.15, 270, 180, 5.0806),
            new LevyCase(Option.Type.Call, 100.0, 100.0,   95.0, 0.05, 0.10, 0.35, 270,   0, 10.1213),
            new LevyCase(Option.Type.Call, 100.0, 100.0,   95.0, 0.05, 0.10, 0.35, 270,  90, 6.9705),
            new LevyCase(Option.Type.Call, 100.0, 100.0,   95.0, 0.05, 0.10, 0.35, 270, 180, 5.1411),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  100.0, 0.05, 0.10, 0.15, 270,   0, 3.7845),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  100.0, 0.05, 0.10, 0.15, 270,  90, 1.9964),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  100.0, 0.05, 0.10, 0.15, 270, 180, 0.6722),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  100.0, 0.05, 0.10, 0.35, 270,   0, 7.5038),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  100.0, 0.05, 0.10, 0.35, 270,  90, 4.0687),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  100.0, 0.05, 0.10, 0.35, 270, 180, 1.4222),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  105.0, 0.05, 0.10, 0.15, 270,   0, 1.6729),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  105.0, 0.05, 0.10, 0.15, 270,  90, 0.3565),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  105.0, 0.05, 0.10, 0.15, 270, 180, 0.0004),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  105.0, 0.05, 0.10, 0.35, 270,   0, 5.4071),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  105.0, 0.05, 0.10, 0.35, 270,  90, 2.1359),
            new LevyCase(Option.Type.Call, 100.0, 100.0,  105.0, 0.05, 0.10, 0.35, 270, 180, 0.1552),
        };

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        for (final LevyCase l : cases) {
            final SimpleQuote spot = new SimpleQuote(l.spot);
            final YieldTermStructure qTS = Utilities.flatRate(today, l.dividendYield, dc);
            final YieldTermStructure rTS = Utilities.flatRate(today, l.riskFreeRate, dc);
            final BlackVolTermStructure volTS = Utilities.flatVol(today, l.volatility, dc);

            final AverageType averageType = AverageType.Arithmetic;
            final SimpleQuote average = new SimpleQuote(l.currentAverage);

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(l.type, l.strike);

            final Date startDate = today.sub(l.elapsed);
            final Date maturity = startDate.add(l.length);

            final Exercise exercise = new EuropeanExercise(maturity);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engine =
                    new ContinuousArithmeticAsianLevyEngine(stochProcess,
                            new Handle<Quote>(average));

            final ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(averageType, startDate, payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double expected = l.result;
            final double tolerance = 1.0e-4;
            final double error = Math.abs(expected - calculated);
            if (error > tolerance) {
                fail("Asian option with Levy engine:"
                        + "\n    spot:            " + l.spot
                        + "\n    current average: " + l.currentAverage
                        + "\n    strike:          " + l.strike
                        + "\n    dividend yield:  " + l.dividendYield
                        + "\n    risk-free rate:  " + l.riskFreeRate
                        + "\n    volatility:      " + l.volatility
                        + "\n    reference date:  " + today
                        + "\n    length:          " + l.length
                        + "\n    elapsed:         " + l.elapsed
                        + "\n    expected value:  " + expected
                        + "\n    calculated:      " + calculated
                        + "\n    error:           " + error);
            }
        }
    }

    /** Local row-data holder for {@link #testLevyEngine}. */
    private static final class LevyCase {
        final Option.Type type;
        final double spot;
        final double currentAverage;
        final double strike;
        final double dividendYield;
        final double riskFreeRate;
        final double volatility;
        final int length;   // days from startDate to maturity
        final int elapsed;  // days from startDate to today
        final double result;

        LevyCase(final Option.Type type, final double spot, final double currentAverage,
                 final double strike, final double dividendYield, final double riskFreeRate,
                 final double volatility, final int length, final int elapsed,
                 final double result) {
            this.type = type;
            this.spot = spot;
            this.currentAverage = currentAverage;
            this.strike = strike;
            this.dividendYield = dividendYield;
            this.riskFreeRate = riskFreeRate;
            this.volatility = volatility;
            this.length = length;
            this.elapsed = elapsed;
            this.result = result;
        }
    }

    @Ignore(REASON_VECER)
    @Test
    public void testVecerEngine() { fail("not implemented"); }

    @Ignore(REASON_CHOI + " — vs MC reference")
    @Test
    public void testChoiAsianEngineVsMC() { fail("not implemented"); }

    @Ignore(REASON_CHOI + " — special cases (deep ITM/OTM, very short maturity)")
    @Test
    public void testChoiAsianEngineSpecialCases() { fail("not implemented"); }

    @Ignore(REASON_SEASONED)
    @Test
    public void testContinuousSeasonedAsianOptions() { fail("not implemented"); }

    /** Port of {@code test-suite/utilities.hpp::timeToDays(t, 360)}. */
    private static int timeToDays360(final double t) {
        return (int) Math.round(t * 360.0);
    }

    /** Local row-data holder for {@link #testTurnbullWakemanAsianEngine}. */
    private static final class TWCase {
        final Option.Type type;
        final double underlying;
        final double strike;
        final double b;
        final double riskFreeRate;
        final double first;
        final double expiry;
        final int fixings;
        final double volatility;
        final String slope;
        final double result;

        TWCase(final Option.Type type, final double underlying, final double strike,
               final double b, final double riskFreeRate, final double first,
               final double expiry, final int fixings, final double volatility,
               final String slope, final double result) {
            this.type = type;
            this.underlying = underlying;
            this.strike = strike;
            this.b = b;
            this.riskFreeRate = riskFreeRate;
            this.first = first;
            this.expiry = expiry;
            this.fixings = fixings;
            this.volatility = volatility;
            this.slope = slope;
            this.result = result;
        }
    }
}
