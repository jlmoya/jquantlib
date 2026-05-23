/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.asian;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.asian.FdBlackScholesAsianEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Cross-validation tests for {@link FdBlackScholesAsianEngine}.
 *
 * <p>Engine and prerequisite step condition
 * ({@link org.jquantlib.methods.finitedifferences.stepconditions.FdmArithmeticAverageCondition})
 * were ported in SKIP-E1 (commit 205f8b4b). This file (SKIP-E1-FOLLOWUP)
 * adds the strict numerical cross-validation against the published Levy 1997
 * reference values that was deferred when SKIP-E1 landed.
 *
 * <h2>Levy 1997 cross-validation</h2>
 *
 * <p>{@link #testCases4LevyReferenceValues} mirrors the first 5 entries of
 * the C++ {@code testMCDiscreteArithmeticAveragePrice} cases4 dataset
 * (test-suite/asianoptions.cpp, "Asian Option", Levy 1997 in "Exotic
 * Options: The State of the Art", ed. Clewlow / Strickland). Java FdEngine
 * agrees with the published Levy values within the same {@code 2.0e-2}
 * tolerance the C++ test enforces — see the SKIP-E1-FOLLOWUP completion
 * note for the per-case diffs (all roughly 0.003, well within tolerance).
 *
 * <h2>Single-fixing degeneracy</h2>
 *
 * <p>{@link #testSingleFixingMatchesVanillaPut}: with exactly one fixing
 * date at maturity, an arithmetic-average Asian option degenerates to a
 * vanilla European on the spot at maturity. The FdEngine value must agree
 * with the Black-Scholes analytic value within the FDM discretization
 * tolerance.
 *
 * <h2>Multi-fixing smoke test</h2>
 *
 * <p>{@link #testMultiFixingEngineRunsAndProducesFiniteResult}: structural
 * sanity check that all multi-fixing scenarios produce finite, non-negative
 * NPVs bounded above by the corresponding vanilla put (averaging reduces
 * variance ⇒ Asian put ≤ vanilla put).
 */
public class FdBlackScholesAsianEngineTest {

    /** Tolerance for the single-fixing-vs-vanilla degeneracy. */
    private static final double FD_VS_ANALYTIC_TOL = 0.10;

    /**
     * Tolerance for cases4 Levy 1997 reference cross-validation. Mirrors
     * the {@code 2.0e-2} tolerance the C++ test
     * {@code testMCDiscreteArithmeticAveragePrice} enforces for the same
     * FdEngine + dataset.
     */
    private static final double LEVY_REF_TOL = 2.0e-2;

    /**
     * Cross-validation against Levy 1997 reference values for the discrete
     * arithmetic-average Asian put — first 5 cases4 entries from
     * C++ test-suite/asianoptions.cpp. Same evaluation date (2015-09-16),
     * same parameters, same engine grid (tGrid=xGrid=aGrid=100). Same
     * tolerance the C++ test enforces.
     */
    @Test
    public void testCases4LevyReferenceValues() {
        // Mirror the C++ global fixture evaluation date.
        final Date today = new Date(16, Month.September, 2015);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual360();

        // cases4 fields: type, underlying, strike, dividendYield, riskFreeRate,
        // first, length, fixings, vol, controlVariate, result
        // Common params for first 5 entries: spot=90, K=87, q=0.06, r=0.025,
        // first=0, length=11/12, vol=0.13
        final SimpleQuote spot = new SimpleQuote(90.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, 0.06, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, 0.025, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, 0.13, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Put, 87.0);

        // First 5 cases4 entries — all fixings < 100 (FdEngine exercised in C++).
        final int[] fixingsArr = { 2, 4, 8, 12, 26 };
        final double[] expectedNpv = {
                1.3942835683, 1.5852442983, 1.66970673, 1.6980019214, 1.7255070456
        };

        for (int idx = 0; idx < fixingsArr.length; idx++) {
            final int fixings = fixingsArr[idx];
            final double expected = expectedNpv[idx];

            final List<Date> fixingDates = buildFixingDates(today, 0.0, 11.0 / 12.0, fixings);
            final Exercise exercise = new EuropeanExercise(fixingDates.get(fixings - 1));

            final DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                    AverageType.Arithmetic, 0.0, 0, fixingDates, payoff, exercise);
            // Same grid as C++ test (line 768 of asianoptions.cpp).
            option.setPricingEngine(new FdBlackScholesAsianEngine(process, 100, 100, 100));
            final double calculated = option.NPV();

            assertEquals(
                    String.format(
                            "Levy 1997 cases4 fixings=%d: expected=%.10f calculated=%.10f diff=%.6e tol=%.6e",
                            fixings, expected, calculated, Math.abs(calculated - expected), LEVY_REF_TOL),
                    expected, calculated, LEVY_REF_TOL);
        }
    }

    /**
     * Single-fixing-at-maturity arithmetic Asian degenerates to a vanilla
     * European on the spot. Cross-validates against the Black-Scholes
     * analytic engine.
     */
    @Test
    public void testSingleFixingMatchesVanillaPut() {
        final Date today = new Settings().evaluationDate();
        final DayCounter dc = new Actual360();
        final SimpleQuote spot = new SimpleQuote(90.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, 0.06, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, 0.025, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, 0.13, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Put, 87.0);
        // Single fixing at maturity (11/12 year)
        final double T = 11.0 / 12.0;
        final Date matDate = today.clone().addAssign((int) Math.round(T * 360.0));
        final List<Date> fixingDates = new ArrayList<>();
        fixingDates.add(matDate);
        final Exercise exercise = new EuropeanExercise(matDate);

        final DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                AverageType.Arithmetic, 0.0, 0, fixingDates, payoff, exercise);
        option.setPricingEngine(new FdBlackScholesAsianEngine(process, 100, 100, 100));
        final double asianNpv = option.NPV();

        // Compare against vanilla European put.
        final VanillaOption vanilla = new VanillaOption(payoff, exercise);
        vanilla.setPricingEngine(new AnalyticEuropeanEngine(process));
        final double vanillaNpv = vanilla.NPV();

        assertEquals(
                "Single-fixing arithmetic Asian must match vanilla European put: "
                        + " FD=" + asianNpv + ", BS=" + vanillaNpv,
                vanillaNpv, asianNpv, FD_VS_ANALYTIC_TOL);
    }

    /**
     * Structural sanity check: exercises the multi-fixing code path and
     * verifies the produced NPV is finite, non-negative, and bounded above
     * by the corresponding vanilla European put.
     */
    @Test
    public void testMultiFixingEngineRunsAndProducesFiniteResult() {
        final Date today = new Settings().evaluationDate();
        final DayCounter dc = new Actual360();
        final SimpleQuote spot = new SimpleQuote(90.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, 0.06, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, 0.025, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, 0.13, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Put, 87.0);

        // (fixings) — multiple fixing counts, all with first=0, length=11/12.
        for (final int fixings : new int[] { 2, 4, 8, 12, 26 }) {
            final List<Date> fixingDates = buildFixingDates(today, 0.0, 11.0 / 12.0, fixings);
            final Exercise exercise = new EuropeanExercise(fixingDates.get(fixings - 1));

            final DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                    AverageType.Arithmetic, 0.0, 0, fixingDates, payoff, exercise);
            final PricingEngine engine = new FdBlackScholesAsianEngine(process, 100, 100, 100);
            option.setPricingEngine(engine);

            final double npv = option.NPV();

            assertTrue("NPV must be finite for fixings=" + fixings + ", got " + npv,
                    Double.isFinite(npv));
            assertTrue("NPV must be non-negative for fixings=" + fixings + ", got " + npv,
                    npv >= 0.0);
            // Asian-average reduces variance, so put price must be at or below vanilla put.
            final VanillaOption vanilla = new VanillaOption(payoff, exercise);
            vanilla.setPricingEngine(new AnalyticEuropeanEngine(process));
            final double vanillaNpv = vanilla.NPV();
            assertTrue(String.format(
                    "Asian put NPV (%.4f) must be <= vanilla put NPV (%.4f) plus 1e-3 for fixings=%d",
                    npv, vanillaNpv, fixings),
                    npv <= vanillaNpv + 1.0e-3);
        }
    }

    /** Mirrors the C++ {@code timeIncrements / fixingDates} construction in cases4 loop. */
    private static List<Date> buildFixingDates(final Date today, final double first, final double length,
            final int fixings) {
        final double dt = length / (fixings - 1);
        final List<Date> fixingDates = new ArrayList<>(fixings);
        for (int i = 0; i < fixings; i++) {
            final double t = i * dt + first;
            fixingDates.add(today.clone().addAssign((int) Math.round(t * 360.0)));
        }
        return fixingDates;
    }
}
