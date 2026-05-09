/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Java port of QuantLib v1.42.1 test-suite/defaultprobabilitycurves.cpp.
 Phase 3a L2 + Phase 3b Track B re-enable.
*/
package org.jquantlib.testsuite.termstructures.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.Protection;
import org.jquantlib.math.interpolations.factories.BackwardFlat;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.interpolations.factories.LogLinear;
import org.jquantlib.pricingengines.credit.MidPointCdsEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.DefaultProbabilityHelper;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.termstructures.credit.PiecewiseDefaultCurve;
import org.jquantlib.termstructures.credit.SpreadCdsHelper;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.termstructures.yieldcurves.InterpolatedDiscountCurve;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.time.calendars.WeekendsOnly;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 {@code test-suite/defaultprobabilitycurves.cpp}
 * {@code BOOST_AUTO_TEST_SUITE(DefaultProbabilityCurveTests)} (533 LOC).
 *
 * <p>Per binding rigor directive 2026-05-08: every {@code BOOST_AUTO_TEST_CASE}
 * is mapped to a faithful Java {@code @Test}.
 *
 * <h3>Active cases</h3>
 * <ul>
 *   <li>{@code testDefaultProbability} — Phase 3a, no CDS dependency</li>
 *   <li>{@code testFlatHazardRate} — Phase 3a, no CDS dependency</li>
 *   <li>{@code testFlatHazardConsistency} — Phase 3b, spread half only
 *       (upfront half ignored — needs DateGeneration.CDS)</li>
 *   <li>{@code testFlatDensityConsistency} — Phase 3b, spread half only</li>
 *   <li>{@code testLinearDensityConsistency} — Phase 3b, spread half only</li>
 *   <li>{@code testLogLinearSurvivalConsistency} — Phase 3b, spread half only</li>
 *   <li>{@code testSingleInstrumentBootstrap} — Phase 3b</li>
 * </ul>
 *
 * <h3>Deferred cases</h3>
 * <ul>
 *   <li>{@code testUpfrontBootstrap} — needs DateGeneration.CDS / cdsMaturity</li>
 *   <li>{@code testIterativeBootstrapRetries} — needs IterativeBootstrap retries
 *       + DateGeneration.CDS2015 + InterpolatedDiscountCurve constructor</li>
 * </ul>
 */
public class DefaultProbabilityCurvesTest {

    public DefaultProbabilityCurvesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    //
    // testDefaultProbability — CDS-free
    //

    /**
     * Java port of {@code BOOST_AUTO_TEST_CASE(testDefaultProbability)}.
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
    // CDS-spread bootstrap consistency — Phase 3b Track B
    //

    /**
     * Equivalent of C++ {@code testBootstrapFromSpread<T,I>} where
     * {@code (T, I)} parameterise the curve flavor and interpolator.
     *
     * <p>Builds a {@link PiecewiseDefaultCurve} from a few CDS spread quotes,
     * then re-prices each generating CDS via {@link MidPointCdsEngine} on the
     * resulting curve and verifies the recovered fair spread equals the input.
     */
    private static <I extends org.jquantlib.math.interpolations.Interpolation.Interpolator>
    void testBootstrapFromSpread(final PiecewiseDefaultCurve.Flavor flavor,
                                 final Class<I> interpClass) {
        final Calendar calendar = new Target();
        final Date today = new Settings().evaluationDate();

        final int settlementDays = 1;
        final double[] quote = {0.005, 0.006, 0.007, 0.009};
        final int[] n = {1, 2, 3, 5};

        final Frequency frequency = Frequency.Quarterly;
        final BusinessDayConvention convention = BusinessDayConvention.Following;
        final DateGeneration.Rule rule = DateGeneration.Rule.TwentiethIMM;
        final DayCounter dayCounter = new Thirty360(Thirty360.Convention.BondBasis);
        final double recoveryRate = 0.4;

        final Handle<YieldTermStructure> discountCurve =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.06, new Actual360()));

        final List<DefaultProbabilityHelper> helpers = new ArrayList<>();
        for (int i = 0; i < n.length; ++i) {
            helpers.add(new SpreadCdsHelper(
                    quote[i], new Period(n[i], TimeUnit.Years),
                    settlementDays, calendar, frequency, convention, rule,
                    dayCounter, recoveryRate, discountCurve));
        }

        final PiecewiseDefaultCurve<I> piecewiseCurve = new PiecewiseDefaultCurve<I>(
                flavor, interpClass, today, helpers,
                new Thirty360(Thirty360.Convention.BondBasis));
        final Handle<DefaultProbabilityTermStructure> probabilityHandle =
                new Handle<DefaultProbabilityTermStructure>(piecewiseCurve);

        final double notional = 1.0;
        final double tolerance = 1.0e-6;

        final Settings s = new Settings();
        final boolean prevTodaysPayments = s.isTodaysPayments();
        s.setTodaysPayments(true);
        try {
            for (int i = 0; i < n.length; ++i) {
                final Date protectionStart = today.add(settlementDays);
                final Date startDate = calendar.adjust(protectionStart, convention);
                final Date endDate = today.add(new Period(n[i], TimeUnit.Years));

                final Schedule schedule = new Schedule(
                        startDate, endDate, new Period(frequency), calendar,
                        convention, BusinessDayConvention.Unadjusted,
                        rule, false);

                final CreditDefaultSwap cds = new CreditDefaultSwap(
                        Protection.Side.Buyer, notional, quote[i],
                        schedule, convention, dayCounter,
                        true, true, protectionStart);
                cds.setPricingEngine(new MidPointCdsEngine(
                        probabilityHandle, recoveryRate, discountCurve));

                final double inputRate = quote[i];
                final double computedRate = cds.fairSpread();
                if (Math.abs(inputRate - computedRate) > tolerance) {
                    fail("Failed to reproduce fair spread for " + n[i]
                            + "Y CDS\n  computed=" + computedRate
                            + "\n  input=" + inputRate);
                }
            }
        } finally {
            s.setTodaysPayments(prevTodaysPayments);
        }
    }

    /** Java port of {@code BOOST_AUTO_TEST_CASE(testFlatHazardConsistency)}. */
    @Test
    public void testFlatHazardConsistency() {
        // C++: testBootstrapFromSpread<HazardRate, BackwardFlat>();
        //      testBootstrapFromUpfront<HazardRate, BackwardFlat>();
        // Java: spread half only — upfront half needs DateGeneration.CDS.
        testBootstrapFromSpread(
                PiecewiseDefaultCurve.Flavor.HAZARD_RATE, BackwardFlat.class);
    }

    /** Java port of {@code BOOST_AUTO_TEST_CASE(testFlatDensityConsistency)}. */
    @Test
    public void testFlatDensityConsistency() {
        testBootstrapFromSpread(
                PiecewiseDefaultCurve.Flavor.DEFAULT_DENSITY, BackwardFlat.class);
    }

    /** Java port of {@code BOOST_AUTO_TEST_CASE(testLinearDensityConsistency)}. */
    @Test
    public void testLinearDensityConsistency() {
        testBootstrapFromSpread(
                PiecewiseDefaultCurve.Flavor.DEFAULT_DENSITY, Linear.class);
    }

    /** Java port of {@code BOOST_AUTO_TEST_CASE(testLogLinearSurvivalConsistency)}.
     *
     *  <p>Phase 3c: the C++ test relies on {@code IterativeBootstrap}'s
     *  initial-guess refinement (multiple solver passes with progressively
     *  expanded value bounds) to converge a log-linear interpolated survival
     *  probability through the spread quotes. The Phase 3a Java
     *  {@link PiecewiseDefaultCurve} runs a single Brent solve with
     *  {@code traits.guess()}'s default initial guess, which rejects the
     *  intermediate iterate as a "negative hazard rate". Porting the
     *  retry/initial-guess machinery is deferred to Phase 3c.
     */
    @Test
    public void testLogLinearSurvivalConsistency() {
        testBootstrapFromSpread(
                PiecewiseDefaultCurve.Flavor.SURVIVAL_PROBABILITY,
                LogLinear.class);
    }

    /** Java port of {@code BOOST_AUTO_TEST_CASE(testSingleInstrumentBootstrap)}. */
    @Test
    public void testSingleInstrumentBootstrap() {
        final Calendar calendar = new Target();
        final Date today = new Settings().evaluationDate();
        final int settlementDays = 0;
        final double quote = 0.005;
        final Period tenor = new Period(2, TimeUnit.Years);
        final Frequency frequency = Frequency.Quarterly;
        final BusinessDayConvention convention = BusinessDayConvention.Following;
        final DateGeneration.Rule rule = DateGeneration.Rule.TwentiethIMM;
        final DayCounter dayCounter = new Thirty360(Thirty360.Convention.BondBasis);
        final double recoveryRate = 0.4;

        final Handle<YieldTermStructure> discountCurve =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.06, new Actual360()));

        final List<DefaultProbabilityHelper> helpers = new ArrayList<>();
        helpers.add(new SpreadCdsHelper(
                quote, tenor, settlementDays, calendar, frequency, convention,
                rule, dayCounter, recoveryRate, discountCurve));

        final PiecewiseDefaultCurve<BackwardFlat> defaultCurve =
                new PiecewiseDefaultCurve<BackwardFlat>(
                        PiecewiseDefaultCurve.Flavor.HAZARD_RATE,
                        BackwardFlat.class,
                        today, helpers, dayCounter);
        // Force calculation; if the bootstrap throws, the test fails.
        assertTrue("bootstrap should produce a positive max date",
                defaultCurve.maxDate().gt(today));
    }

    /** Phase 3c: needs DateGeneration.CDS rule + cdsMaturity helper. */
    @Ignore("Phase 3c: testBootstrapFromUpfront needs DateGeneration.CDS rule")
    @Test
    public void testUpfrontBootstrap() {
        // C++: testBootstrapFromUpfront<HazardRate, BackwardFlat>(); after
        // toggling Settings::includeTodaysCashFlows() to false to verify
        // UpfrontCdsHelper::impliedQuote() flips it.
    }

    /** Java port of {@code BOOST_AUTO_TEST_CASE(testIterativeBootstrapRetries)}.
     *
     *  <p>The C++ test exercises three flavours of {@code IterativeBootstrap}
     *  configuration on a 1-Apr-2020 distressed-CDS dataset:
     *  <ol>
     *    <li>default {@code IterativeBootstrap()} — must throw at the first
     *        alive instrument with a specific error-message prefix
     *        ({@code "1st iteration: failed at 1st alive instrument"});</li>
     *    <li>{@code IterativeBootstrap(..., maxAttempts=5, minFactor=1.0,
     *        maxFactor=10.0)} — must still throw, but at the third alive
     *        instrument;</li>
     *    <li>same as above + {@code dontThrow=true, dontThrowSteps=2} — must
     *        return a fallback curve without throwing.</li>
     *  </ol>
     *
     *  <p><b>Phase 3c status:</b> Phase 3c L0 A.2 added retry-with-widened-
     *  bounds + progressive-interpolation fallback to
     *  {@link PiecewiseDefaultCurve} (sufficient to unblock
     *  {@link #testLogLinearSurvivalConsistency}). The remaining work is
     *  structural: a dedicated {@code IterativeBootstrap} configuration
     *  object passed through the {@link PiecewiseDefaultCurve} constructor
     *  (currently the parameters are hard-coded), plus the
     *  {@code dontThrow} / fallback-search mechanism. Both are pure-additive
     *  ports of {@code ql/termstructures/iterativebootstrap.hpp}; deferred to
     *  Phase 3d (along with the {@code BOOST_CHECK_EXCEPTION} pattern that
     *  matches specific error-message prefixes).
     */
    @Test
    public void testIterativeBootstrapRetries() {
        // C++: 1 Apr 2020 distressed-CDS scenario testing IterativeBootstrap
        // retry/fallback paths against an inverted CDS spread curve.
        final Date asof = new Date(1, Month.April, 2020);
        final Settings settings = new Settings();
        final Date prevEval = settings.evaluationDate();
        try {
            settings.setEvaluationDate(asof);

            final Actual365Fixed tsDayCounter = new Actual365Fixed();

            // USD discount curve dates / DFs (FedFunds OIS) — verbatim from
            // C++ test-suite/defaultprobabilitycurves.cpp:412-469.
            final Date[] usdCurveDates = {
                new Date(1, Month.April, 2020),
                new Date(2, Month.April, 2020),
                new Date(14, Month.April, 2020),
                new Date(21, Month.April, 2020),
                new Date(28, Month.April, 2020),
                new Date(6, Month.May, 2020),
                new Date(5, Month.June, 2020),
                new Date(7, Month.July, 2020),
                new Date(5, Month.August, 2020),
                new Date(8, Month.September, 2020),
                new Date(7, Month.October, 2020),
                new Date(5, Month.November, 2020),
                new Date(7, Month.December, 2020),
                new Date(6, Month.January, 2021),
                new Date(5, Month.February, 2021),
                new Date(5, Month.March, 2021),
                new Date(7, Month.April, 2021),
                new Date(4, Month.April, 2022),
                new Date(3, Month.April, 2023),
                new Date(3, Month.April, 2024),
                new Date(3, Month.April, 2025),
                new Date(5, Month.April, 2027),
                new Date(3, Month.April, 2030),
                new Date(3, Month.April, 2035),
                new Date(3, Month.April, 2040),
                new Date(4, Month.April, 2050)
            };
            final double[] usdCurveDfs = {
                1.000000000, 0.999955835, 0.999931070, 0.999914629, 0.999902799,
                0.999887990, 0.999825782, 0.999764392, 0.999709076, 0.999647785,
                0.999594638, 0.999536198, 0.999483093, 0.999419291, 0.999379417,
                0.999324981, 0.999262356, 0.999575101, 0.996135441, 0.995228348,
                0.989366687, 0.979271200, 0.961150726, 0.926265361, 0.891640651,
                0.839314063
            };
            final Handle<YieldTermStructure> usdYts =
                    new Handle<YieldTermStructure>(
                            new InterpolatedDiscountCurve<LogLinear>(
                                    LogLinear.class, usdCurveDates,
                                    usdCurveDfs, tsDayCounter));

            // CDS spreads, 6M..5Y (LinkedHashMap to preserve order).
            final java.util.LinkedHashMap<Period, Double> cdsSpreads =
                    new java.util.LinkedHashMap<Period, Double>();
            cdsSpreads.put(new Period(6, TimeUnit.Months), 2.957980250);
            cdsSpreads.put(new Period(1, TimeUnit.Years),  3.076933100);
            cdsSpreads.put(new Period(2, TimeUnit.Years),  2.944524520);
            cdsSpreads.put(new Period(3, TimeUnit.Years),  2.844498960);
            cdsSpreads.put(new Period(4, TimeUnit.Years),  2.769234420);
            cdsSpreads.put(new Period(5, TimeUnit.Years),  2.713474100);
            final double recoveryRate = 0.035;

            // Conventions
            final int settlementDays = 1;
            final WeekendsOnly calendar = new WeekendsOnly();
            final Frequency frequency = Frequency.Quarterly;
            final BusinessDayConvention paymentConvention =
                    BusinessDayConvention.Following;
            final DateGeneration.Rule rule = DateGeneration.Rule.CDS2015;
            final Actual360 dayCounter = new Actual360();
            // Last-period day counter — wired through SpreadCdsHelper into the
            // generated CDS leg via FixedRateLeg.withLastPeriodDayCounter
            // (Phase 3d L0 A.2). Uses Actual360(true) ("Actual/360 (inc)")
            // matching C++ test exactly.
            final Actual360 lastPeriodDayCounter = new Actual360(true);

            final List<DefaultProbabilityHelper> helpers = new ArrayList<>();
            for (final java.util.Map.Entry<Period, Double> e : cdsSpreads.entrySet()) {
                helpers.add(new SpreadCdsHelper(
                        e.getValue().doubleValue(), e.getKey(),
                        settlementDays, calendar, frequency,
                        paymentConvention, rule, dayCounter,
                        recoveryRate, usdYts, true, true,
                        null, lastPeriodDayCounter, true,
                        CreditDefaultSwap.PricingModel.Midpoint));
            }

            // Curve with default IterativeBootstrap — must throw at 1st alive
            // helper with the standard message.
            final PiecewiseDefaultCurve<LogLinear> dpts1 =
                    new PiecewiseDefaultCurve<LogLinear>(
                            PiecewiseDefaultCurve.Flavor.SURVIVAL_PROBABILITY,
                            LogLinear.class, asof, helpers, tsDayCounter);
            final Date testDate = new Date(21, Month.December, 2020);
            try {
                dpts1.survivalProbability(testDate, true);
                fail("Expected default-IterativeBootstrap to throw");
            } catch (final RuntimeException e) {
                assertTrue(
                    "expected '1st iteration: failed at 1st alive instrument' but got: " +
                            e.getMessage(),
                    e.getMessage().contains(
                            "1st iteration: failed at 1st alive instrument"));
            }

            // Curve with IterativeBootstrap(maxAttempts=5, minFactor=1.0, maxFactor=10.0)
            // — still throws but later (3rd alive helper).
            final PiecewiseDefaultCurve.Config cfg2 =
                    new PiecewiseDefaultCurve.Config(5, 1.0, 10.0, false, 10);
            final PiecewiseDefaultCurve<LogLinear> dpts2 =
                    new PiecewiseDefaultCurve<LogLinear>(
                            PiecewiseDefaultCurve.Flavor.SURVIVAL_PROBABILITY,
                            LogLinear.class, asof, helpers, tsDayCounter, cfg2);
            try {
                dpts2.survivalProbability(testDate, true);
                fail("Expected retry-bootstrap to throw at 3rd alive helper");
            } catch (final RuntimeException e) {
                assertTrue(
                    "expected '1st iteration: failed at 3rd alive instrument' but got: " +
                            e.getMessage(),
                    e.getMessage().contains(
                            "1st iteration: failed at 3rd alive instrument"));
            }

            // Curve with dontThrow=true — must produce a fallback curve and
            // return without throwing.
            final PiecewiseDefaultCurve.Config cfg3 =
                    new PiecewiseDefaultCurve.Config(5, 1.0, 10.0, true, 2);
            final PiecewiseDefaultCurve<LogLinear> dpts3 =
                    new PiecewiseDefaultCurve<LogLinear>(
                            PiecewiseDefaultCurve.Flavor.SURVIVAL_PROBABILITY,
                            LogLinear.class, asof, helpers, tsDayCounter, cfg3);
            try {
                dpts3.survivalProbability(testDate, true);
                // No exception expected.
            } catch (final RuntimeException e) {
                fail("dontThrow=true should not throw, but got: " + e.getMessage());
            }
        } finally {
            settings.setEvaluationDate(prevEval);
        }
    }
}
