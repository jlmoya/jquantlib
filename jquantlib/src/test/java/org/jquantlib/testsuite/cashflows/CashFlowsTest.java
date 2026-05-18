/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/cashflows.cpp} v1.42.1
 * (623 LOC, 11 cases).
 *
 * <p>Exercises the {@link org.jquantlib.cashflow.CashFlows} static
 * facade — settlement-date semantics, default-settlement-date logic,
 * NPV/duration/yield computations across legs, ex-coupon date treatment,
 * irregular first/last coupon reference dates (end-of-month and
 * end-of-calendar-month variants), partial-schedule leg construction,
 * fixed/IBOR coupon behavior without a forecast curve, and the
 * "IborCoupon knows when it has fixed" regression.
 *
 * <p><strong>Phase 5e.5b-CFC-d-21</strong> bodied {@code testSettings}
 * after porting {@link org.jquantlib.Settings#includeReferenceDateEvents()}
 * and {@link org.jquantlib.Settings#includeTodaysCashFlows()} accessors and
 * the C++-aligned {@code CashFlow.hasOccurred(Date, Boolean)} override
 * (cashflow.cpp v1.42.1 lines 27-49).
 *
 * <p><strong>Phase 5e.5b-CFC-d-63</strong> bodied
 * {@code testAccessViolation} (wide {@link FloatingRateCoupon} ctor +
 * {@link BlackIborCouponPricer}) and {@code testNullFixingDays}
 * ({@code Constants.NULL_NATURAL} sentinel into {@link IborLeg#withFixingDays(double)}
 * under the {@code IborCoupon.Settings.usingAtParCoupons()} precondition guard).
 *
 * <p>Remaining cases require production-side work tracked in the per-test
 * {@code @Ignore} reasons below. Source: {@code test-suite/cashflows.cpp}
 * v1.42.1 @ {@code 099987f0ca}.
 */
public class CashFlowsTest {

    @Test
    public void testSettings() {
        // Save Settings state for restoration at the end (mirrors C++
        // SavedSettings RAII; Java has no destructor, so we restore manually).
        final Settings settings = new Settings();
        final Date savedEval = settings.evaluationDate();
        final boolean savedIncludeRef = settings.includeReferenceDateEvents();
        final Boolean savedIncludeToday = settings.includeTodaysCashFlows();

        try {
            final Date today = Date.todaysDate();
            settings.setEvaluationDate(today);

            // cash flows at T+0, T+1, T+2
            final Leg leg = new Leg();
            for (int i = 0; i < 3; ++i) {
                leg.add((CashFlow) new SimpleCashFlow(1.0, today.add(i)));
            }

            // case 1: don't include reference-date payments, no override at
            //         today's date
            settings.setIncludeReferenceDateEvents(false);
            settings.setIncludeTodaysCashFlows(null);

            checkInclusion(leg, 0, 0, false);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, false);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, false);
            checkInclusion(leg, 2, 3, false);

            // case 2: same, but with explicit setting at today's date
            settings.setIncludeReferenceDateEvents(false);
            settings.setIncludeTodaysCashFlows(Boolean.FALSE);

            checkInclusion(leg, 0, 0, false);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, false);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, false);
            checkInclusion(leg, 2, 3, false);

            // case 3: do include reference-date payments, no override at
            //         today's date
            settings.setIncludeReferenceDateEvents(true);
            settings.setIncludeTodaysCashFlows(null);

            checkInclusion(leg, 0, 0, true);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, true);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, true);
            checkInclusion(leg, 2, 3, false);

            // case 4: do include reference-date payments, explicit (and same)
            //         setting at today's date
            settings.setIncludeReferenceDateEvents(true);
            settings.setIncludeTodaysCashFlows(Boolean.TRUE);

            checkInclusion(leg, 0, 0, true);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, true);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, true);
            checkInclusion(leg, 2, 3, false);

            // case 5: do include reference-date payments, override at
            //         today's date
            settings.setIncludeReferenceDateEvents(true);
            settings.setIncludeTodaysCashFlows(Boolean.FALSE);

            checkInclusion(leg, 0, 0, false);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, true);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, true);
            checkInclusion(leg, 2, 3, false);

            // NPV section — no discount to make calculations easier.
            // C++ uses InterestRate(0.0, Actual365Fixed, Continuous, Annual);
            // Java equivalent is FlatForward at the same parameters wrapping
            // a 0.0 rate (discount factor = 1.0 at every date).
            final DayCounter dc = new Actual365Fixed();
            final FlatForward noDiscount = new FlatForward(today, 0.0, dc,
                    Compounding.Continuous, Frequency.Annual);

            // no override
            settings.setIncludeTodaysCashFlows(null);
            checkNpv(leg, noDiscount, false, today, 2.0);
            checkNpv(leg, noDiscount, true, today, 3.0);

            // override
            settings.setIncludeTodaysCashFlows(Boolean.FALSE);
            checkNpv(leg, noDiscount, false, today, 2.0);
            checkNpv(leg, noDiscount, true, today, 2.0);
        } finally {
            settings.setIncludeTodaysCashFlows(savedIncludeToday);
            settings.setIncludeReferenceDateEvents(savedIncludeRef);
            settings.setEvaluationDate(savedEval);
        }
    }

    /**
     * Mirrors C++ {@code CHECK_INCLUSION(n, days, expected)} macro: asserts
     * {@code !leg[n].hasOccurred(today + days) == expected} (i.e., the cash
     * flow is "included" when it has NOT yet occurred).
     */
    private static void checkInclusion(final Leg leg, final int n, final int days,
                                       final boolean expected) {
        final Date today = new Settings().evaluationDate();
        final boolean included = !leg.get(n).hasOccurred(today.add(days));
        if (included != expected) {
            fail("cashflow at T+" + n + " " + (expected ? "not " : "")
                    + "included at T+" + days);
        }
    }

    /**
     * Mirrors C++ {@code CHECK_NPV(includeRef, expected)} macro.
     */
    private static void checkNpv(final Leg leg, final FlatForward noDiscount,
                                 final boolean includeRef, final Date today,
                                 final double expected) {
        final double npv = CashFlows.npv(leg, noDiscount, includeRef, today, today);
        assertEquals("NPV mismatch (includeRef=" + includeRef + ")",
                expected, npv, 1e-6);
    }

    /**
     * Mirrors C++ {@code CashFlowTests::testAccessViolation}
     * (test-suite/cashflows.cpp v1.42.1 lines 181-222). The original C++
     * regression: in v1.0, constructing a bare {@link FloatingRateCoupon}
     * (not an {@link IborCoupon}) and then asking it for {@code amount()}
     * caused a dynamic-cast access violation inside the Black ibor-coupon
     * pricer. The fix made the pricer fail gracefully — either by throwing
     * a proper {@code Error} or by succeeding when the coupon type is
     * compatible. The Java port mirrors the test as a "must not crash"
     * smoke: the call either returns normally or throws a checked
     * RuntimeException (Java has no SIGSEGV equivalent — a JVM access
     * violation surfaces as a thrown exception either way).
     */
    @Test
    public void testAccessViolation() {
        final Settings settings = new Settings();
        final Date savedEval = settings.evaluationDate();
        try {
            final Date todaysDate = new Date(7, Month.April, 2010);
            final Date settlementDate = new Date(9, Month.April, 2010);
            settings.setEvaluationDate(todaysDate);
            final Calendar calendar = new Target();

            final Handle<YieldTermStructure> rhTermStructure =
                    new Handle<YieldTermStructure>(new FlatForward(
                            settlementDate, 0.04875825, new Actual365Fixed()));

            final double volatility = 0.10;
            final Handle<OptionletVolatilityStructure> vol =
                    new Handle<OptionletVolatilityStructure>(
                            new ConstantOptionletVolatility(
                                    2,
                                    calendar,
                                    BusinessDayConvention.ModifiedFollowing,
                                    volatility,
                                    new Actual365Fixed()));

            final IborIndex index3m = new USDLibor(new Period(3, TimeUnit.Months),
                    rhTermStructure);

            final Date payDate = new Date(20, Month.December, 2013);
            final Date startDate = new Date(20, Month.September, 2013);
            final Date endDate = new Date(20, Month.December, 2013);
            final double spread = 0.0115;
            final BlackIborCouponPricer pricer = new BlackIborCouponPricer(vol);
            // C++ constructs a bare FloatingRateCoupon (not IborCoupon):
            //   FloatingRateCoupon(payDate, 100, startDate, endDate, 2,
            //                      index3m, 1.0, spread / 100);
            // Java wide ctor: paymentDate, nominal, startDate, endDate,
            //   fixingDays, index, gearing, spread,
            //   refPeriodStart, refPeriodEnd, dayCounter, isInArrears.
            // C++ refPeriodStart/refPeriodEnd default to Date() (null),
            // dayCounter defaults to DayCounter() (empty -> falls back to
            // index.dayCounter()), and isInArrears defaults to false.
            final FloatingRateCoupon coupon = new FloatingRateCoupon(
                    payDate, 100, startDate, endDate, 2,
                    index3m, 1.0, spread / 100,
                    new Date(), new Date(),
                    index3m.dayCounter(), false);
            coupon.setPricer(pricer);

            try {
                // this caused an access violation in v1.0;
                // post-fix it either succeeds or throws a proper exception
                coupon.amount();
            } catch (final RuntimeException ok) {
                // ok; proper exception thrown (matches C++ `catch (Error&)`)
            }
        } finally {
            settings.setEvaluationDate(savedEval);
        }
    }

    /**
     * Mirrors C++ {@code CashFlowTests::testNullFixingDays}
     * (test-suite/cashflows.cpp v1.42.1 lines 254-271). The C++ case is
     * guarded by {@code precondition(usingAtParCoupons())}, i.e. it only
     * runs when {@code IborCoupon::Settings::usingAtParCoupons() == true}.
     * The Java port uses {@link Assume#assumeTrue} for the equivalent
     * precondition skip semantics.
     *
     * <p>The regression: building an {@link IborLeg} with
     * {@code withFixingDays(Null<Natural>)} caused an exception when the
     * null sentinel was not handled — Constants.NULL_NATURAL is the Java
     * mirror of {@code Null<Natural>}. The fix in
     * {@link FloatingRateCoupon} substitutes {@code index.fixingDays()}
     * when the sentinel is seen.
     */
    @Test
    public void testNullFixingDays() {
        Assume.assumeTrue("requires IborCoupon.Settings.usingAtParCoupons() == true",
                IborCoupon.Settings.getInstance().usingAtParCoupons());

        final Settings settings = new Settings();
        final Date today = settings.evaluationDate();
        // C++ MakeSchedule().from(today-2*Months).to(today+4*Months)
        //     .withFrequency(Semiannual).withCalendar(TARGET())
        //     .withConvention(Following).backwards()
        // Java MakeSchedule's constructor takes the same args directly;
        // the fluent .from/.to/.withFrequency/.withCalendar/.withConvention
        // wrappers are not yet ported (tracked in the @Ignore reasons
        // below for the schedule-generator cases that need them).
        final Date from = today.sub(new Period(2, TimeUnit.Months));
        final Date to = today.add(new Period(4, TimeUnit.Months));
        final Schedule schedule = new Schedule(
                from, to,
                new Period(Frequency.Semiannual),
                new Target(),
                BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward,
                false,
                new Date(), new Date());

        final IborIndex index = new USDLibor(new Period(6, TimeUnit.Months));
        // The case is about "this can happen with default values, and
        // caused an exception when the null was not managed properly".
        // Constants.NULL_NATURAL is the Java mirror of Null<Natural>.
        // Successful construction of the leg (no thrown exception) IS the
        // assertion — matching C++.
        new IborLeg(schedule, index)
                .withNotionals(100.0)
                .withFixingDays(Constants.NULL_NATURAL)
                .Leg();
    }

    /**
     * Mirrors C++ {@code CashFlowTests::testDefaultSettlementDate}
     * (test-suite/cashflows.cpp v1.42.1 lines 224-252). Verifies that the
     * default-settlement-date overloads of
     * {@link CashFlows#accruedPeriod(Leg, boolean)},
     * {@link CashFlows#accruedDays(Leg, boolean)}, and
     * {@link CashFlows#accruedAmount(Leg, boolean)} fall back to
     * {@link Settings#evaluationDate()} (matching C++ header default
     * {@code settlementDate = Date()}).
     *
     * <p>Phase 5e.5b-CFC-d-97 — bodied after porting the default-date
     * static overloads + MakeSchedule fluent API
     * ({@code .from/.to/.withFrequency/.withCalendar/.withConvention}).
     */
    @Test
    public void testDefaultSettlementDate() {
        final Settings settings = new Settings();
        final Date savedEval = settings.evaluationDate();
        try {
            final Date today = settings.evaluationDate();
            // C++ MakeSchedule().from(today-2*Months).to(today+4*Months)
            //   .withFrequency(Semiannual).withCalendar(TARGET())
            //   .withConvention(Unadjusted).backwards()
            final Schedule schedule = new MakeSchedule()
                    .from(today.sub(new Period(2, TimeUnit.Months)))
                    .to(today.add(new Period(4, TimeUnit.Months)))
                    .withFrequency(Frequency.Semiannual)
                    .withCalendar(new Target())
                    .withConvention(BusinessDayConvention.Unadjusted)
                    .backwards()
                    .schedule();

            // C++ FixedRateLeg(schedule).withNotionals(100.0)
            //   .withCouponRates(0.03, Actual360()).withPaymentCalendar(TARGET())
            //   .withPaymentAdjustment(Following).
            // Java FixedRateLeg takes the day counter at construction time.
            final Leg leg = new FixedRateLeg(schedule, new Actual360())
                    .withNotionals(100.0)
                    .withCouponRates(0.03)
                    .withPaymentCalendar(new Target())
                    .withPaymentAdjustment(BusinessDayConvention.Following)
                    .Leg();

            final double accruedPeriod = CashFlows.accruedPeriod(leg, false);
            if (accruedPeriod == 0.0) {
                fail("null accrued period with default settlement date");
            }

            final long accruedDays = CashFlows.accruedDays(leg, false);
            if (accruedDays == 0L) {
                fail("no accrued days with default settlement date");
            }

            final double accruedAmount = CashFlows.accruedAmount(leg, false);
            if (accruedAmount == 0.0) {
                fail("null accrued amount with default settlement date");
            }
        } finally {
            settings.setEvaluationDate(savedEval);
        }
    }

    /**
     * Mirror of C++ {@code CashFlowTests::testExCouponDates}
     * (test-suite/cashflows.cpp v1.42.1 lines 273-354). Verifies that
     * {@link Coupon#exCouponDate()} returns {@code Date()} (null) when
     * no ex-coupon period is configured, and the expected calendar /
     * business-days advance from {@code accrualEndDate} otherwise, for
     * both fixed-rate and ibor legs.
     *
     * <p>Phase 5e.5b-CFC-d-111: un-ignored after porting
     * {@link IborLeg#withExCouponPeriod} threading via post-construction
     * reflection onto {@link Coupon#exCouponDate_} (mirrors C++
     * {@code IborLeg::operator Leg()} ex-coupon block in
     * ql/cashflows/iborcoupon.cpp:277-295). {@link FixedRateLeg}
     * already threaded {@code exCouponDate} via its
     * {@link FixedRateCoupon} ctor (Phase 5e.5b-CFC-d-93).
     */
    @Test
    public void testExCouponDates() {
        final Date today = Date.todaysDate();
        final Schedule schedule = new MakeSchedule()
                .from(today)
                .to(today.add(new Period(5, TimeUnit.Years)))
                .withFrequency(Frequency.Monthly)
                .withCalendar(new Target())
                .withConvention(BusinessDayConvention.Following)
                .schedule();

        // no ex-coupon dates (fixed leg)
        final Leg l1 = new FixedRateLeg(schedule, new Actual360())
                .withNotionals(100.0)
                .withCouponRates(0.03)
                .Leg();
        for (int i = 0; i < l1.size(); ++i) {
            final Coupon c = (Coupon) l1.get(i);
            if (!c.exCouponDate().isNull()) {
                fail("ex-coupon date found (none expected)");
            }
        }

        // same for floating legs
        final IborIndex index = new Euribor3M();
        final Leg l2 = new IborLeg(schedule, index)
                .withNotionals(100.0)
                .Leg();
        for (int i = 0; i < l2.size(); ++i) {
            final Coupon c = (Coupon) l2.get(i);
            if (!c.exCouponDate().isNull()) {
                fail("ex-coupon date found (none expected)");
            }
        }

        // calendar days (NullCalendar, Unadjusted) — fixed
        final Leg l5 = new FixedRateLeg(schedule, new Actual360())
                .withNotionals(100.0)
                .withCouponRates(0.03)
                .withExCouponPeriod(new Period(2, TimeUnit.Days),
                        new NullCalendar(), BusinessDayConvention.Unadjusted, false)
                .Leg();
        for (int i = 0; i < l5.size(); ++i) {
            final Coupon c = (Coupon) l5.get(i);
            final Date expected = c.accrualEndDate().sub(2);
            if (!c.exCouponDate().equals(expected)) {
                fail("ex-coupon date = " + c.exCouponDate()
                        + " (" + expected + " expected)");
            }
        }

        // calendar days (NullCalendar, Unadjusted) — floating
        final Leg l6 = new IborLeg(schedule, index)
                .withNotionals(100.0)
                .withExCouponPeriod(new Period(2, TimeUnit.Days),
                        new NullCalendar(), BusinessDayConvention.Unadjusted, false)
                .Leg();
        for (int i = 0; i < l6.size(); ++i) {
            final Coupon c = (Coupon) l6.get(i);
            final Date expected = c.accrualEndDate().sub(2);
            if (!c.exCouponDate().equals(expected)) {
                fail("ex-coupon date = " + c.exCouponDate()
                        + " (" + expected + " expected)");
            }
        }

        // business days (TARGET, Preceding) — fixed
        final Calendar target = new Target();
        final Leg l7 = new FixedRateLeg(schedule, new Actual360())
                .withNotionals(100.0)
                .withCouponRates(0.03)
                .withExCouponPeriod(new Period(2, TimeUnit.Days),
                        target, BusinessDayConvention.Preceding, false)
                .Leg();
        for (int i = 0; i < l7.size(); ++i) {
            final Coupon c = (Coupon) l7.get(i);
            final Date expected = target.advance(c.accrualEndDate(),
                    -2, TimeUnit.Days, BusinessDayConvention.Preceding, false);
            if (!c.exCouponDate().equals(expected)) {
                fail("ex-coupon date = " + c.exCouponDate()
                        + " (" + expected + " expected)");
            }
        }

        // business days (TARGET, Preceding) — floating
        final Leg l8 = new IborLeg(schedule, index)
                .withNotionals(100.0)
                .withExCouponPeriod(new Period(2, TimeUnit.Days),
                        target, BusinessDayConvention.Preceding, false)
                .Leg();
        for (int i = 0; i < l8.size(); ++i) {
            final Coupon c = (Coupon) l8.get(i);
            final Date expected = target.advance(c.accrualEndDate(),
                    -2, TimeUnit.Days, BusinessDayConvention.Preceding, false);
            if (!c.exCouponDate().equals(expected)) {
                fail("ex-coupon date = " + c.exCouponDate()
                        + " (" + expected + " expected)");
            }
        }
    }

    /**
     * Mirror of C++ {@code CashFlowTests::testIrregularFirstCouponReferenceDatesAtEndOfMonth}
     * (test-suite/cashflows.cpp v1.42.1 lines 356-376). Verifies that the
     * irregular first coupon of a semi-annual EOM schedule (17-Jan-2017 ->
     * 28-Feb-2018) carries a reference period start of 31-Aug-2016 — i.e.
     * the prior end-of-month obtained by walking back one tenor from the
     * first regular date (28-Feb-2017) using
     * {@code Calendar.advance(end, -tenor, BDC, endOfMonth=true)}.
     *
     * <p>Phase 5e.5b-CFC-d-137 — un-ignored after fixing the
     * {@link FixedRateLeg} irregular-reference-period computation to
     * honor {@code schedule.endOfMonth()} (mirrors C++
     * {@code FixedRateLeg::operator Leg()} lines 198-204).
     */
    @Test
    public void testIrregularFirstCouponReferenceDatesAtEndOfMonth() {
        final Schedule schedule = new MakeSchedule()
                .from(new Date(17, Month.January, 2017))
                .to(new Date(28, Month.February, 2018))
                .withFrequency(Frequency.Semiannual)
                .withConvention(BusinessDayConvention.Unadjusted)
                .endOfMonth()
                .backwards()
                .schedule();

        final Leg leg = new FixedRateLeg(schedule, new Actual360())
                .withNotionals(100.0)
                .withCouponRates(0.01)
                .Leg();

        final Coupon firstCoupon = (Coupon) leg.get(0);
        final Date expected = new Date(31, Month.August, 2016);
        if (!firstCoupon.referencePeriodStart().equals(expected)) {
            fail("Expected reference start date at end of month, got "
                    + firstCoupon.referencePeriodStart());
        }
    }

    /**
     * Mirror of C++ {@code CashFlowTests::testIrregularFirstCouponReferenceDatesAtEndOfCalendarMonth}
     * (test-suite/cashflows.cpp v1.42.1 lines 378-403). Verifies that for a
     * 30-Sep-2017 -> 30-Sep-2022 semi-annual EOM schedule with explicit
     * firstDate=31-Mar-2018 and nextToLastDate=31-Mar-2022, the irregular
     * first coupon's reference period start lands on the effective date
     * (30-Sep-2017) and the first cashflow amount is 0.9375 (notional 100,
     * coupon 1.875%, ActualActual ISMA).
     *
     * <p>Phase 5e.5b-CFC-d-137 — un-ignored after fixing both the Schedule
     * generator (removing the in-branch {@code convention=Preceding}
     * mutation that mis-snapped EOM-flagged schedule dates) and the
     * {@link FixedRateLeg} irregular-reference-period computation (now
     * honoring {@code schedule.endOfMonth()}).
     */
    @Test
    public void testIrregularFirstCouponReferenceDatesAtEndOfCalendarMonth() {
        final Schedule schedule = new MakeSchedule()
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .from(new Date(30, Month.September, 2017))
                .to(new Date(30, Month.September, 2022))
                .withTenor(new Period(6, TimeUnit.Months))
                .withConvention(BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .withFirstDate(new Date(31, Month.March, 2018))
                .withNextToLastDate(new Date(31, Month.March, 2022))
                .endOfMonth()
                .backwards()
                .schedule();

        final Leg leg = new FixedRateLeg(schedule,
                new ActualActual(ActualActual.Convention.ISMA))
                .withNotionals(100.0)
                .withCouponRates(0.01875)
                .Leg();

        final Coupon firstCoupon = (Coupon) leg.get(0);
        final Date expectedRefStart = new Date(30, Month.September, 2017);
        if (!firstCoupon.referencePeriodStart().equals(expectedRefStart)) {
            fail("Expected reference start date at end of calendar day of "
                    + "the month, got " + firstCoupon.referencePeriodStart());
        }
        // C++ tolerance is boost::test_tools::tolerance<Real>(0.0001) =
        // 0.01% relative; first coupon amount is 0.9375.
        assertEquals("First coupon amount mismatch", 0.9375,
                firstCoupon.amount(), 0.9375 * 1.0e-4);
    }

    /**
     * Mirror of C++ {@code CashFlowTests::testIrregularLastCouponReferenceDatesAtEndOfMonth}
     * (test-suite/cashflows.cpp v1.42.1 lines 405-426). Verifies that the
     * irregular last coupon of a semi-annual EOM schedule (17-Jan-2017 ->
     * 15-Sep-2018 with nextToLastDate=28-Feb-2018) carries a reference
     * period end of 31-Aug-2018 — i.e. the next end-of-month obtained by
     * walking forward one tenor from the last regular date (28-Feb-2018)
     * using {@code Calendar.advance(start, +tenor, BDC, endOfMonth=true)}.
     *
     * <p>Phase 5e.5b-CFC-d-137 — un-ignored after fixing the
     * {@link FixedRateLeg} irregular-reference-period computation to
     * honor {@code schedule.endOfMonth()} (mirrors C++
     * {@code FixedRateLeg::operator Leg()} lines 264-268). The previous
     * Java impl used {@code start.add(tenor) + calendar.adjust(...)}
     * which ignored the EOM flag and snapped to 28-Aug-2018 instead of
     * 31-Aug-2018.
     */
    @Test
    public void testIrregularLastCouponReferenceDatesAtEndOfMonth() {
        final Schedule schedule = new MakeSchedule()
                .from(new Date(17, Month.January, 2017))
                .to(new Date(15, Month.September, 2018))
                .withNextToLastDate(new Date(28, Month.February, 2018))
                .withFrequency(Frequency.Semiannual)
                .withConvention(BusinessDayConvention.Unadjusted)
                .endOfMonth()
                .backwards()
                .schedule();

        final Leg leg = new FixedRateLeg(schedule, new Actual360())
                .withNotionals(100.0)
                .withCouponRates(0.01)
                .Leg();

        final Coupon lastCoupon = (Coupon) leg.get(leg.size() - 1);
        final Date expected = new Date(31, Month.August, 2018);
        if (!lastCoupon.referencePeriodEnd().equals(expected)) {
            fail("Expected reference end date at end of month, got "
                    + lastCoupon.referencePeriodEnd());
        }
    }

    /**
     * Mirror of C++ {@code CashFlowTests::testPartialScheduleLegConstruction}
     * (test-suite/cashflows.cpp v1.42.1 lines 428-534). Exercises that a
     * date-based {@link Schedule} cloned with full metadata
     * (8-arg ctor: dates + calendar + convention + termination convention +
     * tenor + rule + endOfMonth + isRegular vector) preserves the irregular
     * first/last reference periods of the source schedule, while the bare
     * date-vector ctor falls back to schedule-period boundaries.
     *
     * <p>Phase 5e.5b-CFC-d-191: un-ignored — the 8-arg metadata-preserving
     * {@link Schedule} ctor landed in Phase 5e.5b-CFC-d-93 (Schedule.java:111),
     * making the previous "missing ctor" reason stale. Test body mirrors the
     * C++ assertions exactly: build a {@code MakeSchedule} with irregular
     * first/last period, clone it twice (with and without metadata),
     * attach to {@link FixedRateLeg} / {@link IborLeg} variants, and check
     * the resulting first/last coupon {@code referencePeriodStart/End}
     * dates.
     */
    @Test
    public void testPartialScheduleLegConstruction() {
        // schedule with irregular first and last period
        final Schedule schedule = new MakeSchedule()
                .from(new Date(15, Month.September, 2017))
                .to(new Date(30, Month.September, 2020))
                .withNextToLastDate(new Date(25, Month.September, 2020))
                .withFrequency(Frequency.Semiannual)
                .backwards()
                .schedule();

        // same schedule, date based, with metadata
        final Schedule schedule2 = new Schedule(schedule.dates(),
                new NullCalendar(),
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                new Period(6, TimeUnit.Months),
                /* rule= */ null,
                schedule.endOfMonth(),
                schedule.isRegular());

        // same schedule, date based, without metadata
        final Schedule schedule3 = new Schedule(schedule.dates());

        // fixed rate legs based on the three schedules
        final Leg leg = new FixedRateLeg(schedule, new ActualActual(ActualActual.Convention.ISMA))
                .withNotionals(100.0)
                .withCouponRates(0.01)
                .Leg();
        final Leg leg2 = new FixedRateLeg(schedule2, new ActualActual(ActualActual.Convention.ISMA))
                .withNotionals(100.0)
                .withCouponRates(0.01)
                .Leg();
        final Leg leg3 = new FixedRateLeg(schedule3, new ActualActual(ActualActual.Convention.ISMA))
                .withNotionals(100.0)
                .withCouponRates(0.01)
                .Leg();

        // check reference period of first and last coupon in all variants —
        // for the first two we expect a 6M reference period, for the third
        // it can not be constructed, so it equals the schedule period.
        checkFixedReferencePeriod(leg,
                new Date(25, Month.March,     2017), new Date(25, Month.September, 2017),
                new Date(25, Month.September, 2020), new Date(25, Month.March,     2021),
                "leg");
        checkFixedReferencePeriod(leg2,
                new Date(25, Month.March,     2017), new Date(25, Month.September, 2017),
                new Date(25, Month.September, 2020), new Date(25, Month.March,     2021),
                "leg2");
        checkFixedReferencePeriod(leg3,
                new Date(15, Month.September, 2017), new Date(25, Month.September, 2017),
                new Date(25, Month.September, 2020), new Date(30, Month.September, 2020),
                "leg3");

        // same check as above for a floating leg
        final IborIndex iborIndex = new USDLibor(new Period(3, TimeUnit.Months));
        final Leg legf = new IborLeg(schedule, iborIndex)
                .withNotionals(100.0)
                .withPaymentDayCounter(new ActualActual(ActualActual.Convention.ISMA))
                .Leg();
        final Leg legf2 = new IborLeg(schedule2, iborIndex)
                .withNotionals(100.0)
                .withPaymentDayCounter(new ActualActual(ActualActual.Convention.ISMA))
                .Leg();
        final Leg legf3 = new IborLeg(schedule3, iborIndex)
                .withNotionals(100.0)
                .withPaymentDayCounter(new ActualActual(ActualActual.Convention.ISMA))
                .Leg();

        checkFloatingReferencePeriod(legf,
                new Date(25, Month.March,     2017), new Date(25, Month.September, 2017),
                new Date(25, Month.September, 2020), new Date(25, Month.March,     2021),
                "legf");
        checkFloatingReferencePeriod(legf2,
                new Date(25, Month.March,     2017), new Date(25, Month.September, 2017),
                new Date(25, Month.September, 2020), new Date(25, Month.March,     2021),
                "legf2");
        checkFloatingReferencePeriod(legf3,
                new Date(15, Month.September, 2017), new Date(25, Month.September, 2017),
                new Date(25, Month.September, 2020), new Date(30, Month.September, 2020),
                "legf3");
    }

    /** Helper for testPartialScheduleLegConstruction — fixed-rate leg checks. */
    private static void checkFixedReferencePeriod(final Leg leg,
            final Date expFirstStart, final Date expFirstEnd,
            final Date expLastStart,  final Date expLastEnd,
            final String tag) {
        final CashFlow firstCf = leg.get(0);
        final CashFlow lastCf  = leg.get(leg.size() - 1);
        if (!(firstCf instanceof FixedRateCoupon)) {
            fail(tag + ": first cashflow is not a FixedRateCoupon (got " + firstCf + ")");
        }
        if (!(lastCf instanceof FixedRateCoupon)) {
            fail(tag + ": last cashflow is not a FixedRateCoupon (got " + lastCf + ")");
        }
        final FixedRateCoupon firstCpn = (FixedRateCoupon) firstCf;
        final FixedRateCoupon lastCpn  = (FixedRateCoupon) lastCf;
        assertEquals(tag + ": firstCpn.referencePeriodStart",
                expFirstStart, firstCpn.referencePeriodStart());
        assertEquals(tag + ": firstCpn.referencePeriodEnd",
                expFirstEnd, firstCpn.referencePeriodEnd());
        assertEquals(tag + ": lastCpn.referencePeriodStart",
                expLastStart, lastCpn.referencePeriodStart());
        assertEquals(tag + ": lastCpn.referencePeriodEnd",
                expLastEnd, lastCpn.referencePeriodEnd());
    }

    /** Helper for testPartialScheduleLegConstruction — floating-rate leg checks. */
    private static void checkFloatingReferencePeriod(final Leg leg,
            final Date expFirstStart, final Date expFirstEnd,
            final Date expLastStart,  final Date expLastEnd,
            final String tag) {
        final CashFlow firstCf = leg.get(0);
        final CashFlow lastCf  = leg.get(leg.size() - 1);
        if (!(firstCf instanceof FloatingRateCoupon)) {
            fail(tag + ": first cashflow is not a FloatingRateCoupon (got " + firstCf + ")");
        }
        if (!(lastCf instanceof FloatingRateCoupon)) {
            fail(tag + ": last cashflow is not a FloatingRateCoupon (got " + lastCf + ")");
        }
        final FloatingRateCoupon firstCpn = (FloatingRateCoupon) firstCf;
        final FloatingRateCoupon lastCpn  = (FloatingRateCoupon) lastCf;
        assertEquals(tag + ": firstCpn.referencePeriodStart",
                expFirstStart, firstCpn.referencePeriodStart());
        assertEquals(tag + ": firstCpn.referencePeriodEnd",
                expFirstEnd, firstCpn.referencePeriodEnd());
        assertEquals(tag + ": lastCpn.referencePeriodStart",
                expLastStart, lastCpn.referencePeriodStart());
        assertEquals(tag + ": lastCpn.referencePeriodEnd",
                expLastEnd, lastCpn.referencePeriodEnd());
    }

    /**
     * Mirror of C++ {@code CashFlowTests::testFixedIborCouponWithoutForecastCurve}
     * (test-suite/cashflows.cpp v1.42.1 lines 536-564). Verifies that an
     * {@link IborCoupon} whose fixing date is in the past and whose
     * fixing was stored via {@link IborIndex#addFixing} can compute its
     * {@code amount()} <em>without</em> a forecast term structure on
     * the index — i.e., {@link IborCoupon#indexFixing()} must short-
     * circuit on the past fixing before consulting the (null)
     * termStructure.
     *
     * <p>Phase 5e.5b-CFC-d-111: un-ignored after threading hasFixed() /
     * past-fixing short-circuit into {@link IborCoupon#indexFixing()}
     * (mirrors C++ iborcoupon.cpp:110-128).
     */
    @Test
    public void testFixedIborCouponWithoutForecastCurve() {
        final Settings settings = new Settings();
        final Date today = settings.evaluationDate();

        // C++ test constructs USDLibor(6*Months) with no forwarding curve.
        // In Java, the single-arg ctor wraps a *non-empty* handle to a
        // throwing AbstractYieldTermStructure (default-construed),
        // which trips BlackIborCouponPricer.initialize's
        // {@code rateCurve.currentLink().referenceDate()} call. Use the
        // explicit two-arg ctor with an empty handle to match the C++
        // semantics where {@code rateCurve.empty() == true}.
        final USDLibor index = new USDLibor(new Period(6, TimeUnit.Months),
                new Handle<YieldTermStructure>());
        final Calendar calendar = index.fixingCalendar();

        final Date fixingDate = calendar.advance(today, -2, TimeUnit.Months);
        final double pastFixing = 0.01;
        index.addFixing(fixingDate, pastFixing);
        try {
            final Date startDate = index.valueDate(fixingDate);
            final Date endDate = index.maturityDate(startDate);

            final IborCoupon coupon = new IborCoupon(endDate, 100.0,
                    startDate, endDate, index.fixingDays(), index);
            coupon.setPricer(new BlackIborCouponPricer());

            // The main check is that this does NOT throw (no forecast curve).
            final double amount = coupon.amount();

            // Consistency check: amount = pastFixing * nominal * accrualPeriod.
            final double expected = pastFixing * coupon.nominal()
                    * coupon.accrualPeriod();
            if (Math.abs(amount - expected) > 1e-8) {
                fail("amount mismatch:"
                        + "\n    calculated: " + amount
                        + "\n    expected:   " + expected);
            }
        } finally {
            index.clearFixings();
        }
    }

    /** Helper mirroring C++ {@code iborCouponForFixingDate} (cashflows.cpp:566-574). */
    private static IborCoupon iborCouponForFixingDate(final IborIndex index,
                                                      final Date fixingDate) {
        final Date startDate = index.valueDate(fixingDate);
        final Date endDate = index.maturityDate(startDate);
        final IborCoupon coupon = new IborCoupon(endDate, 100.0,
                startDate, endDate, index.fixingDays(), index);
        coupon.setPricer(new BlackIborCouponPricer());
        return coupon;
    }

    /**
     * Mirror of C++ {@code CashFlowTests::testIborCouponKnowsWhenitHasFixed}
     * (test-suite/cashflows.cpp v1.42.1 lines 576-619). Exercises the
     * {@link IborCoupon#hasFixed()} contract:
     * <ul>
     *   <li>{@code fixingDate < today}: always {@code true} (regardless of
     *       whether a fixing was stored — and a subsequent {@code rate()}
     *       must throw if it isn't).</li>
     *   <li>{@code fixingDate == today} with no fixing and
     *       {@code enforcesTodaysHistoricFixings == false}:
     *       {@code false}.</li>
     *   <li>{@code fixingDate == today} with a stored fixing:
     *       {@code true}.</li>
     *   <li>{@code fixingDate == today} with
     *       {@code enforcesTodaysHistoricFixings == true}: {@code true}
     *       (and {@code rate()} throws when no fixing is stored).</li>
     *   <li>{@code fixingDate > today}: always {@code false}.</li>
     * </ul>
     *
     * <p>Phase 5e.5b-CFC-d-111: un-ignored after porting
     * {@link IborCoupon#hasFixed()} and relying on the existing
     * {@link Settings#setEnforcesTodaysHistoricFixings} accessor.
     */
    @Test
    public void testIborCouponKnowsWhenitHasFixed() {
        final Settings settings = new Settings();
        final Date savedEval = settings.evaluationDate();
        final boolean savedEnforce = settings.isEnforcesTodaysHistoricFixings();

        final Euribor3M index = new Euribor3M();
        final Calendar calendar = index.fixingCalendar();
        // Force evaluationDate onto a TARGET business day so that
        // {@code today} is itself a valid Euribor fixing date — the C++
        // test implicitly assumes this. Without the adjustment a
        // weekend/holiday evaluation date trips Index.isValidFixingDate.
        final Date today = calendar.adjust(savedEval,
                BusinessDayConvention.Following);
        settings.setEvaluationDate(today);
        try {
            {
                // fixingDate strictly in the past, no stored fixing.
                final IborCoupon coupon = iborCouponForFixingDate(index,
                        calendar.advance(today, -1, TimeUnit.Days));
                index.clearFixings();
                // hasFixed() must return true without throwing (no fixing lookup).
                if (!coupon.hasFixed()) {
                    fail("hasFixed() expected true for past fixing date");
                }
                // but rate() must throw — the fixing is missing.
                boolean threw = false;
                try {
                    coupon.rate();
                } catch (final RuntimeException expected) {
                    threw = true;
                }
                if (!threw) {
                    fail("rate() should have thrown (missing past fixing)");
                }
            }

            {
                // fixingDate == today, no enforcement, no stored fixing → false.
                final IborCoupon coupon = iborCouponForFixingDate(index, today);
                settings.setEnforcesTodaysHistoricFixings(false);
                index.clearFixings();
                if (coupon.hasFixed()) {
                    fail("hasFixed() expected false for today (no enforce, no fixing)");
                }
            }

            {
                // fixingDate == today, fixing stored → true regardless of enforce.
                final IborCoupon coupon = iborCouponForFixingDate(index, today);
                settings.setEnforcesTodaysHistoricFixings(false);
                index.clearFixings();
                index.addFixing(coupon.fixingDate(), 0.01);
                if (!coupon.hasFixed()) {
                    fail("hasFixed() expected true for today with stored fixing");
                }
            }

            {
                // fixingDate == today, enforce on, no fixing → true (and rate() throws).
                final IborCoupon coupon = iborCouponForFixingDate(index, today);
                settings.setEnforcesTodaysHistoricFixings(true);
                index.clearFixings();
                if (!coupon.hasFixed()) {
                    fail("hasFixed() expected true under enforcesTodaysHistoricFixings");
                }
                boolean threw = false;
                try {
                    coupon.rate();
                } catch (final RuntimeException expected) {
                    threw = true;
                }
                if (!threw) {
                    fail("rate() should have thrown (today, no fixing, enforce on)");
                }
            }

            {
                // fixingDate strictly in the future → always false.
                final IborCoupon coupon = iborCouponForFixingDate(index,
                        calendar.advance(today, 1, TimeUnit.Days));
                if (coupon.hasFixed()) {
                    fail("hasFixed() expected false for future fixing date");
                }
            }
        } finally {
            settings.setEnforcesTodaysHistoricFixings(savedEnforce);
            settings.setEvaluationDate(savedEval);
            index.clearFixings();
        }
    }
}
