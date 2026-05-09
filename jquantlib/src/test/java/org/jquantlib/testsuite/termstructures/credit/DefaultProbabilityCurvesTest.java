/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Java port of QuantLib v1.42.1 test-suite/defaultprobabilitycurves.cpp.
 Phase 3a L2.
*/
package org.jquantlib.testsuite.termstructures.credit;

import static org.junit.Assert.assertEquals;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 {@code test-suite/defaultprobabilitycurves.cpp}
 * {@code BOOST_AUTO_TEST_SUITE(DefaultProbabilityCurveTests)} (533 LOC).
 *
 * <p>Per binding rigor directive 2026-05-08: every {@code BOOST_AUTO_TEST_CASE}
 * is mapped to a faithful Java {@code @Test}. CDS-dependent cases (which
 * require {@code CreditDefaultSwap} and its pricing engines) are
 * {@code @Ignore}'d with the rationale "Phase 3b: needs CreditDefaultSwap";
 * they remain present as unimplemented placeholders so the C++ → Java
 * test-case lineage is auditable.
 *
 * <h3>Active cases (CDS-free, Phase 3a)</h3>
 * <ul>
 *   <li>{@code testDefaultProbability} — date/time accessor consistency on
 *       {@link FlatHazardRate}.
 *   <li>{@code testFlatHazardRate} — {@code S(t) = exp(-h t)} via the
 *       {@code FlatHazardRate} curve.
 * </ul>
 *
 * <h3>Deferred cases (Phase 3b)</h3>
 * <ul>
 *   <li>{@code testFlatHazardConsistency}
 *   <li>{@code testFlatDensityConsistency}
 *   <li>{@code testLinearDensityConsistency}
 *   <li>{@code testLogLinearSurvivalConsistency}
 *   <li>{@code testSingleInstrumentBootstrap}
 *   <li>{@code testUpfrontBootstrap}
 *   <li>{@code testIterativeBootstrapRetries}
 * </ul>
 */
public class DefaultProbabilityCurvesTest {

    //
    // testDefaultProbability — CDS-free
    //

    /**
     * Java port of {@code BOOST_AUTO_TEST_CASE(testDefaultProbability)}.
     *
     * <p>Tests that the default-probability term-structure surface is
     * internally consistent: {@code p(d1,d2) == p(d2) - p(d1)},
     * {@code p(t) == p(d)} when {@code t = yearFraction(today, d)},
     * {@code p(t1,t2) == p(d1,d2)} likewise.
     */
    @Test
    public void testDefaultProbability() {
        final double hazardRate = 0.0100;
        final Handle<Quote> hazardRateQuote = new Handle<Quote>(new SimpleQuote(hazardRate));
        final DayCounter dayCounter = new Actual360();
        final Calendar calendar = new Target();
        final int n = 20;
        final double tolerance = 1.0e-10;

        final Date today = new Settings().evaluationDate();
        Date startDate = today;
        Date endDate = startDate;

        final FlatHazardRate flatHazardRate = new FlatHazardRate(startDate, hazardRateQuote, dayCounter);

        for (int i = 0; i < n; ++i) {
            startDate = endDate;
            endDate = calendar.advance(endDate, new Period(1, TimeUnit.Years));

            final double pStart = flatHazardRate.defaultProbability(startDate);
            final double pEnd = flatHazardRate.defaultProbability(endDate);
            final double pBetweenComputed = flatHazardRate.defaultProbability(startDate, endDate);
            final double pBetween = pEnd - pStart;
            assertEquals("p(d1,d2) at iteration " + i,
                    pBetween, pBetweenComputed, tolerance);

            final double t2 = dayCounter.yearFraction(today, endDate);
            final double timeProbability = flatHazardRate.defaultProbability(t2);
            final double dateProbability = flatHazardRate.defaultProbability(endDate);
            assertEquals("single-time probability vs single-date probability " + i,
                    dateProbability, timeProbability, tolerance);

            final double t1 = dayCounter.yearFraction(today, startDate);
            final double timeProbability2 = flatHazardRate.defaultProbability(t1, t2);
            final double dateProbability2 = flatHazardRate.defaultProbability(startDate, endDate);
            assertEquals("double-time probability vs double-date probability " + i,
                    dateProbability2, timeProbability2, tolerance);
        }
    }

    /**
     * Java port of {@code BOOST_AUTO_TEST_CASE(testFlatHazardRate)}.
     *
     * <p>Tests that {@code FlatHazardRate.defaultProbability(t) ==
     * 1 - exp(-h t)} at annual intervals.
     */
    @Test
    public void testFlatHazardRate() {
        final double hazardRate = 0.0100;
        final Handle<Quote> hazardRateQuote = new Handle<Quote>(new SimpleQuote(hazardRate));
        final DayCounter dayCounter = new Actual360();
        final Calendar calendar = new Target();
        final int n = 20;
        final double tolerance = 1.0e-10;

        final Date today = new Settings().evaluationDate();
        Date startDate = today;
        Date endDate = startDate;

        final FlatHazardRate flatHazardRate = new FlatHazardRate(today, hazardRateQuote, dayCounter);

        for (int i = 0; i < n; ++i) {
            endDate = calendar.advance(endDate, new Period(1, TimeUnit.Years));
            final double t = dayCounter.yearFraction(startDate, endDate);
            final double probability = 1.0 - Math.exp(-hazardRate * t);
            final double computedProbability = flatHazardRate.defaultProbability(t);
            assertEquals("Failed to reproduce probability for flat hazard rate at year " + (i + 1),
                    probability, computedProbability, tolerance);
        }
    }

    //
    // CDS-dependent cases — deferred to Phase 3b. All implemented as @Ignore'd
    // placeholders so the lineage with the C++ test suite is auditable.
    //

    /** Phase 3b: needs CreditDefaultSwap + SpreadCdsHelper + UpfrontCdsHelper + MidPointCdsEngine. */
    @Ignore("Phase 3b: needs CreditDefaultSwap")
    @Test
    public void testFlatHazardConsistency() {
        // C++: testBootstrapFromSpread<HazardRate, BackwardFlat>();
        //      testBootstrapFromUpfront<HazardRate, BackwardFlat>();
    }

    /** Phase 3b: needs CreditDefaultSwap + SpreadCdsHelper + UpfrontCdsHelper + MidPointCdsEngine. */
    @Ignore("Phase 3b: needs CreditDefaultSwap")
    @Test
    public void testFlatDensityConsistency() {
        // C++: testBootstrapFromSpread<DefaultDensity, BackwardFlat>();
        //      testBootstrapFromUpfront<DefaultDensity, BackwardFlat>();
    }

    /** Phase 3b: needs CreditDefaultSwap + SpreadCdsHelper + UpfrontCdsHelper + MidPointCdsEngine. */
    @Ignore("Phase 3b: needs CreditDefaultSwap")
    @Test
    public void testLinearDensityConsistency() {
        // C++: testBootstrapFromSpread<DefaultDensity, Linear>();
        //      testBootstrapFromUpfront<DefaultDensity, Linear>();
    }

    /** Phase 3b: needs CreditDefaultSwap + SpreadCdsHelper + UpfrontCdsHelper + MidPointCdsEngine. */
    @Ignore("Phase 3b: needs CreditDefaultSwap")
    @Test
    public void testLogLinearSurvivalConsistency() {
        // C++: testBootstrapFromSpread<SurvivalProbability, LogLinear>();
        //      testBootstrapFromUpfront<SurvivalProbability, LogLinear>();
    }

    /** Phase 3b: needs CreditDefaultSwap + SpreadCdsHelper + MidPointCdsEngine. */
    @Ignore("Phase 3b: needs CreditDefaultSwap")
    @Test
    public void testSingleInstrumentBootstrap() {
        // C++: builds PiecewiseDefaultCurve<HazardRate, BackwardFlat> from
        // a single SpreadCdsHelper and calls recalculate().
    }

    /** Phase 3b: needs CreditDefaultSwap + UpfrontCdsHelper + MidPointCdsEngine. */
    @Ignore("Phase 3b: needs CreditDefaultSwap")
    @Test
    public void testUpfrontBootstrap() {
        // C++: testBootstrapFromUpfront<HazardRate, BackwardFlat>(); after
        // toggling Settings::includeTodaysCashFlows() to false to verify
        // UpfrontCdsHelper::impliedQuote() flips it.
    }

    /** Phase 3b: needs CreditDefaultSwap + SpreadCdsHelper + IterativeBootstrap with retries. */
    @Ignore("Phase 3b: needs CreditDefaultSwap")
    @Test
    public void testIterativeBootstrapRetries() {
        // C++: 1 Apr 2020 distressed-CDS scenario testing IterativeBootstrap
        // retry/fallback paths against an inverted CDS spread curve.
    }
}
