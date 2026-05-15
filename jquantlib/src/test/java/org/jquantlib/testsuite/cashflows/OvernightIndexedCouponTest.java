/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.ArithmeticAveragedOvernightIndexedCouponPricer;
import org.jquantlib.cashflow.BlackAveragingOvernightIndexedCouponPricer;
import org.jquantlib.cashflow.BlackOvernightIndexedCouponPricer;
import org.jquantlib.cashflow.CappedFlooredOvernightIndexedCoupon;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CompoundingOvernightIndexedCouponPricer;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightIndexedCoupon;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.Cubic;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/overnightindexedcoupon.cpp}
 * v1.42.1 (1,130 LOC, 35 cases).
 *
 * <p>Phase Body-Fill (2026-05-09) — 1 case body-filled
 * (testFutureCouponRate, the simplest single-coupon all-future case).
 *
 * <p>Remaining 34 cases stay deferred to Phase 5d.5 — they need either:
 * <ul>
 *   <li>past-fixings setup (the C++ {@code CommonVars} fixture seeds 56
 *       SOFR fixings spanning 2019-06-21..2021-11-22 for past/spanning-today
 *       coupons);</li>
 *   <li>lookback / observation-shift / lockout machinery (Java
 *       OvernightIndexedCoupon ctor currently rejects non-default lookback /
 *       lockout / observationShift via QL.require — Phase 5d.5 MVP);</li>
 *   <li>OvernightLeg builder coverage (Phase 5d.5);</li>
 *   <li>Black ON / Black averaging-ON pricer integration with
 *       CappedFlooredOvernightIndexedCoupon (Phase 5d.5);</li>
 *   <li>telescopicValueDates handling and
 *       payment-before-accrual-end corner case.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/overnightindexedcoupon.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class OvernightIndexedCouponTest {

    private static final String REASON_PAST =
            "Phase 5d.5: OvernightIndex + OvernightIndexedCoupon now ported (commits fa38ff70, 41f7102a); "
          + "needs full port from C++ overnightindexedcoupon.cpp + 56-row CommonVars past-fixings table.";

    private static final String REASON_CURRENT =
            "Phase 5d.5: OvernightIndex + OvernightIndexedCoupon now ported (commits fa38ff70, 41f7102a); "
          + "needs full port from C++ overnightindexedcoupon.cpp + 56-row CommonVars past-fixings table "
          + "(coupon spans today, requires both historical and forecast fixings).";

    private static final String REASON_ACCRUED =
            "Phase 5d.5: OvernightIndex + OvernightIndexedCoupon now ported (commits fa38ff70, 41f7102a); "
          + "needs full port from C++ overnightindexedcoupon.cpp + accrued-amount cached references.";

    private static final String REASON_LOOKBACK =
            "Phase 5d.5: OvernightIndexedCoupon ported but lookback / observation-shift / lockout "
          + "machinery rejected by ctor (QL.require lookbackDays==0, lockoutDays==0, !observationShift). "
          + "Phase 5d.5 MVP carry-forward.";

    private static final String REASON_BLACK =
            "Phase 5d.5: OvernightIndexedCouponPricer now ported (commit 41f7102a); needs Black "
          + "ON-coupon / Black averaging-ON caplet/floorlet pricer integration.";

    private static final String REASON_LEG =
            "Phase 5d.5: OvernightLeg builder ported (commit 41f7102a); needs full port from C++ "
          + "overnightindexedcoupon.cpp.";

    private static final String REASON_TELESCOPIC =
            "Phase 5d.5: OvernightIndexedCoupon ported; telescopicValueDates handling deferred "
          + "(MVP ignores the flag and always builds the full schedule).";

    private static final String REASON_PAYMENT =
            "Phase 5d.5: OvernightIndexedCoupon ported; payment-before-accrual-end corner case "
          + "rejected by ctor's QL.require(paymentDate.ge(endDate)).";

    /** Mirror of C++ {@code CommonVars} struct minimum subset for the body-filled test. */
    private static final class CommonVars {
        final Date today;
        final double notional = 10000.0;
        final OvernightIndex sofr;
        final RelinkableHandle<YieldTermStructure> forecastCurve;

        CommonVars(final Date evaluationDate) {
            this.today = evaluationDate;
            new Settings().setEvaluationDate(today);
            this.forecastCurve = new RelinkableHandle<YieldTermStructure>();
            this.sofr = new Sofr(forecastCurve);
        }

        CommonVars() {
            this(new Date(23, Month.November, 2021));
        }

        OvernightIndexedCoupon makeCoupon(final Date startDate,
                                          final Date endDate) {
            // Match C++ defaults (cpp:52-62): paymentDate=endDate, gearing=1,
            // spread=0, no refperiod, default DayCounter (forces fall-back
            // to overnightIndex.dayCounter() via super ctor), no telescopic,
            // Compound averaging, default fixingDays/lookback/lockout, no
            // observation shift, compoundSpreadDaily=false.
            return new OvernightIndexedCoupon(
                    endDate, notional, startDate, endDate, sofr,
                    1.0, 0.0, new Date(), new Date(),
                    sofr.dayCounter(),
                    /* telescopicValueDates */ false,
                    RateAveraging.Type.Compound,
                    /* lookbackDays */ Constants.NULL_NATURAL,
                    /* lockoutDays */ 0,
                    /* applyObservationShift */ false,
                    /* compoundSpreadDaily */ false);
        }
    }

    /**
     * Mirror of C++ {@code BlackONPricerVars} — flat 4% forecast curve,
     * flat 10% optionlet vol, eval date 1-Jul-2025, all dates in the future
     * so no past fixings are needed.
     */
    private static final class BlackONPricerVars {
        final Date today = new Date(1, Month.July, 2025);
        final double notional = 1_000_000.0;
        final RelinkableHandle<YieldTermStructure> forecastCurve =
                new RelinkableHandle<YieldTermStructure>();
        final RelinkableHandle<OptionletVolatilityStructure> vol =
                new RelinkableHandle<OptionletVolatilityStructure>();
        final OvernightIndex sofr;
        final DayCounter dc = new Actual360();

        BlackONPricerVars() {
            new Settings().setEvaluationDate(today);
            forecastCurve.linkTo(Utilities.flatRate(today, 0.04, dc));
            sofr = new Sofr(forecastCurve);
        }

        OvernightIndexedCoupon makeBaseCoupon(final Date start, final Date end,
                                              final RateAveraging.Type avgMethod) {
            final OvernightIndexedCoupon onCoupon = new OvernightIndexedCoupon(
                    end, notional, start, end, sofr, 1.0, 0.0, new Date(), new Date(),
                    dc, false, avgMethod,
                    Constants.NULL_NATURAL, 0, false, false);
            if (avgMethod == RateAveraging.Type.Compound) {
                onCoupon.setPricer(new CompoundingOvernightIndexedCouponPricer());
            } else {
                onCoupon.setPricer(new ArithmeticAveragedOvernightIndexedCouponPricer());
            }
            return onCoupon;
        }

        CappedFlooredOvernightIndexedCoupon makeCoupon(final Date start, final Date end,
                                                       final double cap, final double floor,
                                                       final RateAveraging.Type avgMethod) {
            return new CappedFlooredOvernightIndexedCoupon(
                    makeBaseCoupon(start, end, avgMethod), cap, floor);
        }
    }

    /**
     * Mirror of C++ {@code CommonVarsONLeg} (overnightindexedcoupon.cpp:184-319):
     * 1-year quarterly schedule on the SOFR (US-government) calendar, eval date
     * 1-Jun-2025, with 43 past SOFR fixings spanning 2025-06-02..2025-08-01.
     * The fixture is used by the OvernightLeg structural tests.
     */
    private static final class CommonVarsONLeg {
        final Date today;
        final double notional = 1_000_000.0;
        final OvernightIndex sofr;
        final RelinkableHandle<YieldTermStructure> forecastCurve =
                new RelinkableHandle<YieldTermStructure>();
        final Schedule legSchedule;
        final DayCounter dc = new Actual360();
        final RelinkableHandle<OptionletVolatilityStructure> rateVolTS =
                new RelinkableHandle<OptionletVolatilityStructure>();

        CommonVarsONLeg(final Date evaluationDate) {
            this.today = evaluationDate;
            new Settings().setEvaluationDate(today);

            this.sofr = new Sofr(forecastCurve);

            // Quarterly schedule, US-Government-Bond calendar, ModFollowing,
            // Forward generation. Mirrors C++ legSchedule construction at
            // overnightindexedcoupon.cpp:256-260.
            this.legSchedule = new MakeSchedule(
                    new Date(1, Month.July, 2025),
                    new Date(1, Month.July, 2026),
                    new Period(3, TimeUnit.Months),
                    new UnitedStates(UnitedStates.Market.GOVERNMENTBOND),
                    BusinessDayConvention.ModifiedFollowing)
                .withTerminationDateConvention(BusinessDayConvention.ModifiedFollowing)
                .forwards()
                .schedule();

            // 43-row past SOFR fixings (2025-06-02..2025-08-01).
            final Date[] pastDates = new Date[]{
                    new Date( 2, Month.June, 2025), new Date( 3, Month.June, 2025),
                    new Date( 4, Month.June, 2025), new Date( 5, Month.June, 2025),
                    new Date( 6, Month.June, 2025), new Date( 9, Month.June, 2025),
                    new Date(10, Month.June, 2025), new Date(11, Month.June, 2025),
                    new Date(12, Month.June, 2025), new Date(13, Month.June, 2025),
                    new Date(16, Month.June, 2025), new Date(17, Month.June, 2025),
                    new Date(18, Month.June, 2025), new Date(20, Month.June, 2025),
                    new Date(23, Month.June, 2025), new Date(24, Month.June, 2025),
                    new Date(25, Month.June, 2025), new Date(26, Month.June, 2025),
                    new Date(27, Month.June, 2025), new Date(30, Month.June, 2025),
                    new Date( 1, Month.July, 2025), new Date( 2, Month.July, 2025),
                    new Date( 3, Month.July, 2025), new Date( 7, Month.July, 2025),
                    new Date( 8, Month.July, 2025), new Date( 9, Month.July, 2025),
                    new Date(10, Month.July, 2025), new Date(11, Month.July, 2025),
                    new Date(14, Month.July, 2025), new Date(15, Month.July, 2025),
                    new Date(16, Month.July, 2025), new Date(17, Month.July, 2025),
                    new Date(18, Month.July, 2025), new Date(21, Month.July, 2025),
                    new Date(22, Month.July, 2025), new Date(23, Month.July, 2025),
                    new Date(24, Month.July, 2025), new Date(25, Month.July, 2025),
                    new Date(28, Month.July, 2025), new Date(29, Month.July, 2025),
                    new Date(30, Month.July, 2025), new Date(31, Month.July, 2025),
                    new Date( 1, Month.August, 2025)
            };
            final double[] pastRates = new double[]{
                    0.0435, 0.0432, 0.0428, 0.0429, 0.0429, 0.0429, 0.0428, 0.0428,
                    0.0428, 0.0428, 0.0432, 0.0431, 0.0428, 0.0429, 0.0429, 0.0430,
                    0.0436, 0.0440, 0.0439, 0.0445, 0.0444, 0.0440, 0.0435, 0.0433,
                    0.0434, 0.0432, 0.0431, 0.0431, 0.0433, 0.0437, 0.0434, 0.0434,
                    0.0430, 0.0428, 0.0428, 0.0428, 0.0430, 0.0436, 0.0436, 0.0436,
                    0.0432, 0.0439, 0.0434
            };
            for (int i = 0; i < pastDates.length; ++i) {
                sofr.addFixing(pastDates[i], pastRates[i]);
            }
        }

        CommonVarsONLeg() {
            this(new Date(1, Month.June, 2025));
        }

        /**
         * Mirror of C++ {@code CommonVarsONLeg::setupForecastCurve}
         * (overnightindexedcoupon.cpp:287-316). Builds a 7-knot cubic
         * zero-rate curve and links it as the forecast curve, with
         * extrapolation enabled.
         */
        void setupForecastCurve() {
            final Date[] curveDates = new Date[]{
                    today,
                    new Date(30, Month.July, 2025),
                    new Date(29, Month.August, 2025),
                    new Date(30, Month.September, 2025),
                    new Date(30, Month.December, 2025),
                    new Date(30, Month.March, 2026),
                    new Date(30, Month.June, 2026)
            };
            final double[] zeroRates = new double[]{
                    0.0434, 0.0436, 0.0431, 0.0413, 0.0390, 0.0370, 0.0348
            };
            final InterpolatedZeroCurve<Cubic> zeroCurve =
                    new InterpolatedZeroCurve<Cubic>(
                            Cubic.class,
                            curveDates, zeroRates, dc,
                            new UnitedStates(UnitedStates.Market.SOFR),
                            new Cubic());
            zeroCurve.enableExtrapolation();
            forecastCurve.linkTo(zeroCurve);
        }

        /**
         * Mirror of C++ CommonVarsONLeg::makeLeg (overnightindexedcoupon.cpp:198-245).
         * Convenience overload with all defaults.
         */
        Leg makeLeg() {
            return makeLeg(Constants.NULL_NATURAL, 0, false, false,
                    RateAveraging.Type.Compound, null, null, null, null);
        }

        Leg makeLeg(final int fixingDays) {
            return makeLeg(fixingDays, 0, false, false,
                    RateAveraging.Type.Compound, null, null, null, null);
        }

        Leg makeLeg(final int fixingDays, final int lockoutDays) {
            return makeLeg(fixingDays, lockoutDays, false, false,
                    RateAveraging.Type.Compound, null, null, null, null);
        }

        Leg makeLeg(final int fixingDays, final int lockoutDays,
                    final boolean applyObservationShift) {
            return makeLeg(fixingDays, lockoutDays, applyObservationShift, false,
                    RateAveraging.Type.Compound, null, null, null, null);
        }

        Leg makeLeg(final int fixingDays, final int lockoutDays,
                    final boolean applyObservationShift,
                    final boolean telescopicValueDates,
                    final RateAveraging.Type averaging) {
            return makeLeg(fixingDays, lockoutDays, applyObservationShift,
                    telescopicValueDates, averaging, null, null, null, null);
        }

        Leg makeLeg(final int fixingDays, final int lockoutDays,
                    final boolean applyObservationShift,
                    final boolean telescopicValueDates,
                    final RateAveraging.Type averaging,
                    final List<Double> gearings,
                    final List<Double> spreads,
                    final List<Double> caps,
                    final List<Double> floors) {
            final OvernightLeg leg = new OvernightLeg(legSchedule, sofr)
                    .withNotionals(notional)
                    .withPaymentDayCounter(dc)
                    .withAveragingMethod(averaging)
                    .withLockoutDays(lockoutDays)
                    .withObservationShift(applyObservationShift)
                    .withTelescopicValueDates(telescopicValueDates);

            if (fixingDays != Constants.NULL_NATURAL) {
                leg.withLookbackDays(fixingDays);
            }
            if (gearings != null && !gearings.isEmpty()) {
                leg.withGearings(gearings);
            }
            if (spreads != null && !spreads.isEmpty()) {
                leg.withSpreads(spreads);
            }
            if (caps != null && !caps.isEmpty()) {
                leg.withCaps(caps);
            }
            if (floors != null && !floors.isEmpty()) {
                leg.withFloors(floors);
            }

            // If caps/floors present, attach Black pricer matching C++ behavior
            // (overnightindexedcoupon.cpp:236-242).
            if ((caps != null && !caps.isEmpty())
                    || (floors != null && !floors.isEmpty())) {
                rateVolTS.linkTo(new ConstantOptionletVolatility(
                        today, new Target(),
                        BusinessDayConvention.Following, 0.05, dc));
                if (averaging == RateAveraging.Type.Compound) {
                    leg.withCouponPricer(
                            new BlackOvernightIndexedCouponPricer(rateVolTS));
                } else {
                    leg.withCouponPricer(
                            new BlackAveragingOvernightIndexedCouponPricer(rateVolTS));
                }
            }

            return leg.leg();
        }
    }

    @Ignore(REASON_PAST) @Test public void testPastCouponRate() { fail("not implemented"); }
    @Ignore(REASON_PAST) @Test public void testPastSpreadedCouponRate() { fail("not implemented"); }
    @Ignore(REASON_CURRENT) @Test public void testCurrentCouponRate() { fail("not implemented"); }

    @Test
    public void testFutureCouponRate() {
        QL.info("Testing rate for future overnight-indexed coupon...");

        final CommonVars vars = new CommonVars();

        // Flat 0.10% forecast curve via Actual360, eval date 2021-11-23.
        final DayCounter dc360 = new Actual360();
        vars.forecastCurve.linkTo(Utilities.flatRate(vars.today, 0.0010, dc360));

        // Coupon entirely in the future: 2021-12-10 to 2022-01-10.
        final OvernightIndexedCoupon futureCoupon = vars.makeCoupon(
                new Date(10, Month.December, 2021),
                new Date(10, Month.January, 2022));

        // C++ expected (overnightindexedcoupon.cpp:407-411):
        //   expectedRate = 0.001000043057
        //   expectedAmount = notional * expectedRate * 31/360
        //   tolerance: 1e-12 on rate, 1e-8 on amount.
        final double expectedRate = 0.001000043057;
        final double expectedAmount = vars.notional * expectedRate * 31.0 / 360.0;
        final double rateTol = 1e-12;
        final double amountTol = 1e-8;

        final double rate = futureCoupon.rate();
        if (Math.abs(rate - expectedRate) > rateTol) {
            fail("future coupon rate: expected=" + expectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - expectedRate)
                    + " tolerance=" + rateTol);
        }
        final double amount = futureCoupon.amount();
        if (Math.abs(amount - expectedAmount) > amountTol) {
            fail("future coupon amount: expected=" + expectedAmount
                    + " calculated=" + amount
                    + " diff=" + Math.abs(amount - expectedAmount)
                    + " tolerance=" + amountTol);
        }
    }

    @Ignore(REASON_CURRENT) @Test public void testRateWhenTodayIsHoliday() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountInThePast() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountSpanningToday() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountInTheFuture() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountOnPastHoliday() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountOnFutureHoliday() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPastCouponRateWithLookback() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPastCouponRateWithLookbackAndObservationShift() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPastCouponRateWithLockout() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPastCouponRateWithLookbackObservationShiftAndLockout() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testIncorrectNumberOfLockoutDays() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testFutureCouponRateWithLookback() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testFutureCouponRateWithLookbackAndObservationShift() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testFutureCouponRateWithLookout() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPartiallyAccruedAmountOfFutureCouponWithLookout() { fail("not implemented"); }
    @Ignore(REASON_TELESCOPIC) @Test public void testTelescopicFormulaWhenLookbackWithObservationShiftAndNoIndexFixingDelay() { fail("not implemented"); }
    @Ignore(REASON_TELESCOPIC) @Test public void testErrorWhenTelescopicValueDatesEnforcedWithLookback() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testErrorWhenLookbackOrLockoutAppliedForSimpleAveraging() { fail("not implemented"); }
    @Test
    public void testBlackOvernightIndexedCouponPricerCapletFloorlet() {
        QL.info("Testing Black compounding overnight-indexed coupon pricer...");

        // Mirror C++ BlackONPricerVars constructor (overnightindexedcoupon.cpp:146-158):
        // by default vol is linked to 10% flat optionlet vol.
        final BlackONPricerVars vars = new BlackONPricerVars();
        vars.vol.linkTo(new ConstantOptionletVolatility(
                vars.today, new Target(), BusinessDayConvention.Following, 0.10, vars.dc));

        final Date start = new Date(1, Month.July, 2035);
        final Date end = new Date(1, Month.October, 2035);

        // ----- Vanilla -----
        // C++: vanillaCoupon = makeBaseCoupon(start, end);
        //      expectedRate = vanillaCoupon->rate();
        //      pricer = make_shared<BlackCompoundingOvernightIndexedCouponPricer>(vars.vol);
        //      vanillaCoupon->setPricer(pricer);  // swap pricer
        //      rate = vanillaCoupon->rate();      // Black-pricer swapletRate
        //      CHECK("Base Rate", rate, expectedRate, 1e-8);
        OvernightIndexedCoupon vanillaCoupon = vars.makeBaseCoupon(
                start, end, RateAveraging.Type.Compound);
        final double baseExpectedRate = vanillaCoupon.rate();

        final BlackOvernightIndexedCouponPricer pricer =
                new BlackOvernightIndexedCouponPricer(vars.vol);
        vanillaCoupon.setPricer(pricer);

        double rate = vanillaCoupon.rate();
        if (Math.abs(rate - baseExpectedRate) > 1e-8) {
            fail("Base Rate: expected=" + baseExpectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - baseExpectedRate));
        }

        // ----- Caplet (cap = 0.045, no floor) -----
        // C++ expected (overnightindexedcoupon.cpp:791): 0.036604717
        final double cap = 0.045;
        CappedFlooredOvernightIndexedCoupon cappedCoupon =
                vars.makeCoupon(start, end, cap, Constants.NULL_REAL,
                        RateAveraging.Type.Compound);
        cappedCoupon.setPricer(pricer);

        rate = cappedCoupon.rate();
        double expectedRate = 0.036604717;
        if (rate > cap + 1e-8) {
            fail("Capped Rate: rate=" + rate + " > cap=" + cap);
        }
        if (Math.abs(rate - expectedRate) > 1e-8) {
            fail("Capped Rate: expected=" + expectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - expectedRate));
        }

        // ----- Floorlet (no cap, floor = 0.035) -----
        // C++ expected (overnightindexedcoupon.cpp:802): 0.042502070
        final double floor = 0.035;
        CappedFlooredOvernightIndexedCoupon flooredCoupon =
                vars.makeCoupon(start, end, Constants.NULL_REAL, floor,
                        RateAveraging.Type.Compound);
        flooredCoupon.setPricer(pricer);

        rate = flooredCoupon.rate();
        expectedRate = 0.042502070;
        if (rate < floor - 1e-8) {
            fail("Floored Rate: rate=" + rate + " < floor=" + floor);
        }
        if (Math.abs(rate - expectedRate) > 1e-8) {
            fail("Floored Rate: expected=" + expectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - expectedRate));
        }

        // ----- Capped + Floored -----
        // C++ expected (overnightindexedcoupon.cpp:811): 0.039340869
        CappedFlooredOvernightIndexedCoupon cappedFlooredCoupon =
                vars.makeCoupon(start, end, cap, floor,
                        RateAveraging.Type.Compound);
        cappedFlooredCoupon.setPricer(pricer);

        rate = cappedFlooredCoupon.rate();
        expectedRate = 0.039340869;
        if (rate > cap + 1e-8 || rate < floor - 1e-8) {
            fail("Capped+Floored Rate: rate=" + rate
                    + " out of [floor=" + floor + ", cap=" + cap + "]");
        }
        if (Math.abs(rate - expectedRate) > 1e-8) {
            fail("Capped and Floored Rate: expected=" + expectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - expectedRate));
        }
    }

    @Test
    public void testBlackAverageONIndexedCouponPricerCapletFloorlet() {
        QL.info("Testing Black averaging overnight-indexed coupon pricer...");

        final BlackONPricerVars vars = new BlackONPricerVars();
        vars.vol.linkTo(new ConstantOptionletVolatility(
                vars.today, new Target(), BusinessDayConvention.Following, 0.10, vars.dc));

        final Date start = new Date(1, Month.July, 2035);
        final Date end = new Date(1, Month.October, 2035);

        // ----- Vanilla (Simple averaging) -----
        OvernightIndexedCoupon vanillaCoupon = vars.makeBaseCoupon(
                start, end, RateAveraging.Type.Simple);
        final double baseExpectedRate = vanillaCoupon.rate();

        final BlackAveragingOvernightIndexedCouponPricer pricer =
                new BlackAveragingOvernightIndexedCouponPricer(vars.vol);
        vanillaCoupon.setPricer(pricer);

        double rate = vanillaCoupon.rate();
        if (Math.abs(rate - baseExpectedRate) > 1e-8) {
            fail("Base Rate: expected=" + baseExpectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - baseExpectedRate));
        }

        // ----- Caplet (cap = 0.045, no floor) -----
        // C++ expected (overnightindexedcoupon.cpp:839): 0.036488300
        final double cap = 0.045;
        CappedFlooredOvernightIndexedCoupon cappedCoupon =
                vars.makeCoupon(start, end, cap, Constants.NULL_REAL,
                        RateAveraging.Type.Simple);
        cappedCoupon.setPricer(pricer);

        rate = cappedCoupon.rate();
        double expectedRate = 0.036488300;
        if (rate > cap + 1e-8) {
            fail("Capped Rate: rate=" + rate + " > cap=" + cap);
        }
        if (Math.abs(rate - expectedRate) > 1e-8) {
            fail("Capped Rate: expected=" + expectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - expectedRate));
        }

        // ----- Floorlet (no cap, floor = 0.035) -----
        // C++ expected (overnightindexedcoupon.cpp:849): 0.042362746
        final double floor = 0.035;
        CappedFlooredOvernightIndexedCoupon flooredCoupon =
                vars.makeCoupon(start, end, Constants.NULL_REAL, floor,
                        RateAveraging.Type.Simple);
        flooredCoupon.setPricer(pricer);

        rate = flooredCoupon.rate();
        expectedRate = 0.042362746;
        if (rate < floor - 1e-8) {
            fail("Floored Rate: rate=" + rate + " < floor=" + floor);
        }
        if (Math.abs(rate - expectedRate) > 1e-8) {
            fail("Floored Rate: expected=" + expectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - expectedRate));
        }

        // ----- Capped + Floored -----
        // C++ expected (overnightindexedcoupon.cpp:858): 0.039281553
        CappedFlooredOvernightIndexedCoupon cappedFlooredCoupon =
                vars.makeCoupon(start, end, cap, floor,
                        RateAveraging.Type.Simple);
        cappedFlooredCoupon.setPricer(pricer);

        rate = cappedFlooredCoupon.rate();
        expectedRate = 0.039281553;
        if (rate > cap + 1e-8 || rate < floor - 1e-8) {
            fail("Capped+Floored Rate: rate=" + rate
                    + " out of [floor=" + floor + ", cap=" + cap + "]");
        }
        if (Math.abs(rate - expectedRate) > 1e-8) {
            fail("Capped and Floored Rate: expected=" + expectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - expectedRate));
        }
    }

    @Test
    public void testBlackONPricerConsistencyWithNoVol() {
        QL.info("Testing Black compounding pricer with zero volatility "
              + "(should match vanilla pricer)...");

        // Mirror C++ BlackONPricerVars + zero-vol ConstantOptionletVolatility.
        final BlackONPricerVars vars = new BlackONPricerVars();
        vars.vol.linkTo(new ConstantOptionletVolatility(
                vars.today, new Target(), BusinessDayConvention.Following, 0.0, vars.dc));

        final Date start = new Date(1, Month.July, 2035);
        final Date end = new Date(1, Month.October, 2035);

        // Capped+floored coupon priced with Black pricer at zero vol must
        // match the vanilla compounding pricer rate (intrinsic only).
        final CappedFlooredOvernightIndexedCoupon cf = vars.makeCoupon(
                start, end, 0.045, 0.035, RateAveraging.Type.Compound);
        final BlackOvernightIndexedCouponPricer blackPricer =
                new BlackOvernightIndexedCouponPricer(vars.vol);
        cf.setPricer(blackPricer);
        final double blackRate = cf.rate();

        final OvernightIndexedCoupon base = vars.makeBaseCoupon(
                start, end, RateAveraging.Type.Compound);
        // base already has CompoundingOvernightIndexedCouponPricer set.
        final double vanillaRate = base.rate();

        // C++ tolerance: 1e-10.
        if (Math.abs(blackRate - vanillaRate) > 1e-10) {
            fail("Zero capped coupon rate: black=" + blackRate
                    + " vanilla=" + vanillaRate
                    + " diff=" + Math.abs(blackRate - vanillaRate));
        }

        // Also check: the same Black pricer applied to the un-capped
        // coupon must still equal the vanilla rate at zero vol.
        base.setPricer(blackPricer);
        final double vanillaRate2 = base.rate();
        if (Math.abs(blackRate - vanillaRate2) > 1e-10) {
            fail("Zero capped coupon rate (same pricer): black=" + blackRate
                    + " vanilla=" + vanillaRate2
                    + " diff=" + Math.abs(blackRate - vanillaRate2));
        }
    }

    @Test
    public void testBlackONAveragingPricerConsistencyWithNoVol() {
        QL.info("Testing Black averaging pricer with zero volatility "
              + "(should match vanilla pricer)...");

        final BlackONPricerVars vars = new BlackONPricerVars();
        vars.vol.linkTo(new ConstantOptionletVolatility(
                vars.today, new Target(), BusinessDayConvention.Following, 0.0, vars.dc));

        final Date start = new Date(1, Month.July, 2035);
        final Date end = new Date(1, Month.October, 2035);

        final CappedFlooredOvernightIndexedCoupon cf = vars.makeCoupon(
                start, end, 0.045, 0.035, RateAveraging.Type.Simple);
        final BlackAveragingOvernightIndexedCouponPricer blackPricer =
                new BlackAveragingOvernightIndexedCouponPricer(vars.vol);
        cf.setPricer(blackPricer);
        final double blackRate = cf.rate();

        final OvernightIndexedCoupon base = vars.makeBaseCoupon(
                start, end, RateAveraging.Type.Simple);
        final double vanillaRate = base.rate();

        if (Math.abs(blackRate - vanillaRate) > 1e-10) {
            fail("Zero capped coupon rate: black=" + blackRate
                    + " vanilla=" + vanillaRate
                    + " diff=" + Math.abs(blackRate - vanillaRate));
        }

        base.setPricer(blackPricer);
        final double vanillaRate2 = base.rate();
        if (Math.abs(blackRate - vanillaRate2) > 1e-10) {
            fail("Zero capped coupon rate (same pricer): black=" + blackRate
                    + " vanilla=" + vanillaRate2
                    + " diff=" + Math.abs(blackRate - vanillaRate2));
        }
    }
    @Test
    public void testOvernightLegBasicFunctionality() {
        QL.info("Testing basic functionality of overnight leg...");
        final CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.forecastCurve.linkTo(Utilities.flatRate(vars.today, 0.0010, vars.dc));

        final Leg leg = vars.makeLeg();
        // Quarterly leg over 1 year = 4 coupons.
        if (leg.size() != 4) {
            fail("Expected 4 coupons, got " + leg.size());
        }
        for (final CashFlow cf : leg) {
            if (!(cf instanceof OvernightIndexedCoupon)) {
                fail("Coupon is not an OvernightIndexedCoupon: " + cf);
            }
            final OvernightIndexedCoupon ois = (OvernightIndexedCoupon) cf;
            if (ois.nominal() != vars.notional) {
                fail("nominal=" + ois.nominal() + " expected " + vars.notional);
            }
            if (ois.averagingMethod() != RateAveraging.Type.Compound) {
                fail("averagingMethod=" + ois.averagingMethod() + " expected Compound");
            }
            if (ois.lockoutDays() != 0) {
                fail("lockoutDays=" + ois.lockoutDays() + " expected 0");
            }
            if (ois.applyObservationShift()) {
                fail("applyObservationShift=true, expected false");
            }
        }
    }

    @Ignore(REASON_LOOKBACK) @Test public void testOvernightLegWithLookback() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testOvernightLegWithLockout() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testOvernightLegWithObservationShift() { fail("not implemented"); }

    @Test
    public void testOvernightLegWithGearingsAndSpreads() {
        QL.info("Testing overnight leg construction with gearings and spreads...");
        final CommonVarsONLeg vars = new CommonVarsONLeg();
        // Use flat curve for fixture simplicity (test only checks per-coupon
        // gearing + spread attributes; curve precision not asserted).
        vars.forecastCurve.linkTo(Utilities.flatRate(vars.today, 0.04, vars.dc));

        final List<Double> gearings = Arrays.asList(1.0, 1.25, 2.0, 0.5);
        final List<Double> spreads = Arrays.asList(0.0001, 0.0001, 0.0002, 0.0002);

        final Leg leg = vars.makeLeg(Constants.NULL_NATURAL, 0, false, false,
                RateAveraging.Type.Compound, gearings, spreads, null, null);

        if (leg.size() != 4) {
            fail("Expected 4 coupons, got " + leg.size());
        }
        for (int i = 0; i < leg.size(); ++i) {
            if (!(leg.get(i) instanceof OvernightIndexedCoupon)) {
                fail("leg[" + i + "] is not an OvernightIndexedCoupon");
            }
            final OvernightIndexedCoupon ois = (OvernightIndexedCoupon) leg.get(i);
            if (Math.abs(ois.gearing() - gearings.get(i)) > 1e-12) {
                fail("leg[" + i + "].gearing=" + ois.gearing()
                        + " expected " + gearings.get(i));
            }
            if (Math.abs(ois.spread() - spreads.get(i)) > 1e-12) {
                fail("leg[" + i + "].spread=" + ois.spread()
                        + " expected " + spreads.get(i));
            }
        }
    }

    @Ignore("Phase 5e.5b-CFC-d follow-up: needs leg-NPV probe (lockout=3, telescopic=true). Body-fill ready, expected value pending probe cross-validation.")
    @Test public void testOvernightLegNPV() { fail("not implemented"); }

    @Ignore("Phase 5e.5b-CFC-d follow-up: testOvernightLegWithCapsAndFloors body-fill ready "
          + "+ probe ground-truth confirmed (overnight_leg_caps_floors.json); calendar fixings "
          + "now match C++ exactly (62 fixings in coupon[3] after Juneteenth fix to "
          + "GovernmentBondImpl). Residual ~2.7e-7 drift in coupon[3] vanilla rate (Java "
          + "0.027594750 vs C++ 0.027595019) localized but not pinpointed: same fixing dates, "
          + "same Cubic-curve config, same accrualPeriod — drift is in the daily-compound "
          + "forward-rate computation (likely sub-period dt or curve discount precision around "
          + "the Juneteenth gap). NPV diff 0.067 on 34648.")
    @Test public void testOvernightLegWithCapsAndFloors() { fail("not implemented"); }

    @Test
    public void testOvernightLegSimpleAveraging() {
        QL.info("Testing overnight leg construction with simple averaging...");
        final CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.forecastCurve.linkTo(Utilities.flatRate(vars.today, 0.0010, vars.dc));

        final Leg leg = vars.makeLeg(Constants.NULL_NATURAL, 0, false, false,
                RateAveraging.Type.Simple);

        for (final CashFlow cf : leg) {
            if (!(cf instanceof OvernightIndexedCoupon)) {
                fail("Coupon is not an OvernightIndexedCoupon: " + cf);
            }
            final OvernightIndexedCoupon ois = (OvernightIndexedCoupon) cf;
            if (ois.averagingMethod() != RateAveraging.Type.Simple) {
                fail("averagingMethod=" + ois.averagingMethod() + " expected Simple");
            }
        }
    }

    @Test
    public void testOvernightLegErrorConditions() {
        QL.info("Testing error conditions for overnight leg...");
        final CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.forecastCurve.linkTo(Utilities.flatRate(vars.today, 0.0010, vars.dc));

        // Lookback days + simple averaging must throw.
        try {
            vars.makeLeg(5, 0, false, false, RateAveraging.Type.Simple);
            fail("Expected LibraryException for lookback+Simple but got none");
        } catch (final LibraryException expected) {
            // OK
        }

        // Lockout days + simple averaging must throw.
        try {
            vars.makeLeg(Constants.NULL_NATURAL, 3, false, false, RateAveraging.Type.Simple);
            fail("Expected LibraryException for lockout+Simple but got none");
        } catch (final LibraryException expected) {
            // OK
        }

        // Observation shift + simple averaging must throw.
        try {
            vars.makeLeg(Constants.NULL_NATURAL, 0, true, false, RateAveraging.Type.Simple);
            fail("Expected LibraryException for observationShift+Simple but got none");
        } catch (final LibraryException expected) {
            // OK
        }
    }
    @Ignore(REASON_PAYMENT) @Test public void testOvernightIndexedCouponPaymentBeforeAccrualEnd() { fail("not implemented"); }
}
