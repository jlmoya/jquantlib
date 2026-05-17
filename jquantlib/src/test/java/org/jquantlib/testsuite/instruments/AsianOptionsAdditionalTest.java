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
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.asian.TurnbullWakemanAsianEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
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

    private static final String REASON_ANALYTIC_HESTON =
            "Phase 5i.5 — analytic Heston Asian engines exist under "
          + "experimental.asian; the in-instruments-package (non-experimental) "
          + "wrapper test is deferred until the experimental classes are "
          + "promoted out of experimental";

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

    @Ignore(REASON_ANALYTIC_HESTON)
    @Test
    public void testAnalyticContinuousGeometricAveragePriceHeston() { fail("not implemented"); }

    @Ignore(REASON_ANALYTIC_HESTON)
    @Test
    public void testAnalyticDiscreteGeometricAveragePriceHeston() { fail("not implemented"); }

    @Ignore(REASON_ANALYTIC_HESTON + " + past-fixings semantics")
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

    @Ignore(REASON_LEVY)
    @Test
    public void testLevyEngine() { fail("not implemented"); }

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
