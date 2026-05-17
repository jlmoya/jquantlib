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

    @Ignore("Phase 5d.5 — Schedule(...,endOfMonth=true) generator currently snaps "
            + "irregular first-coupon reference start to schedule's first regular date "
            + "rather than to the prior end-of-month per C++ Schedule::nextTwentieth/EOM "
            + "logic. Reference value (31-Aug-2016 for the 17-Jan-2017 -> 28-Feb-2018 "
            + "semi-annual schedule) must come from a probe against C++ v1.42.1 once "
            + "the Schedule generator is aligned.")
    @Test public void testIrregularFirstCouponReferenceDatesAtEndOfMonth() { fail("not implemented"); }

    @Ignore("Phase 5e.5b-CFC-d-97 — MakeSchedule fluent setters "
            + "(.withCalendar / .withTenor / .from / .to / .withConvention / "
            + ".withTerminationDateConvention / .withFirstDate / .withNextToLastDate) "
            + "are now ported and the test compiles cleanly against the C++ "
            + "fluent shape (see commented body below). Remaining blocker is "
            + "the Java Schedule generator: for the 30-Sep-2017 → 30-Sep-2022 "
            + "semi-annual schedule with endOfMonth=true + Unadjusted + "
            + "firstDate=31-Mar-2018, the first regular reference start currently "
            + "snaps to 29-Sep-2017 instead of 30-Sep-2017. Fix requires changes "
            + "to time.Schedule (out of scope for this commit — Schedule is owned "
            + "by a parallel-running agent).")
    @Test public void testIrregularFirstCouponReferenceDatesAtEndOfCalendarMonth() { fail("not implemented"); }
    /*
     * Ready-to-uncomment body once Schedule generator is aligned:
     *
     *   final Schedule schedule = new MakeSchedule()
     *           .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
     *           .from(new Date(30, Month.September, 2017))
     *           .to(new Date(30, Month.September, 2022))
     *           .withTenor(new Period(6, TimeUnit.Months))
     *           .withConvention(BusinessDayConvention.Unadjusted)
     *           .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
     *           .withFirstDate(new Date(31, Month.March, 2018))
     *           .withNextToLastDate(new Date(31, Month.March, 2022))
     *           .endOfMonth()
     *           .backwards()
     *           .schedule();
     *   final Leg leg = new FixedRateLeg(schedule,
     *           new ActualActual(ActualActual.Convention.ISMA))
     *           .withNotionals(100.0)
     *           .withCouponRates(0.01875)
     *           .Leg();
     *   final Coupon firstCoupon = (Coupon) leg.get(0);
     *   final Date expectedRefStart = new Date(30, Month.September, 2017);
     *   if (!firstCoupon.referencePeriodStart().equals(expectedRefStart)) {
     *       fail("Expected reference start date at end of calendar day of "
     *           + "the month, got " + firstCoupon.referencePeriodStart());
     *   }
     *   assertEquals("First coupon amount mismatch", 0.9375,
     *           firstCoupon.amount(), 0.9375 * 1.0e-4);
     */

    @Ignore("Phase 5d.5 — same Schedule(...,endOfMonth=true) generator divergence as "
            + "testIrregularFirstCouponReferenceDatesAtEndOfMonth, but applied to the "
            + "LAST coupon (referencePeriodEnd snapping to end-of-month). Expected "
            + "value 31-Aug-2018 for the 17-Jan-2017 -> 15-Sep-2018 schedule with "
            + "nextToLastDate=28-Feb-2018.")
    @Test public void testIrregularLastCouponReferenceDatesAtEndOfMonth() { fail("not implemented"); }

    @Ignore("Phase 5d.5 — requires Schedule(List<Date>, Calendar, BusinessDayConvention, "
            + "BusinessDayConvention, Period, DateGeneration.Rule, boolean endOfMonth, "
            + "List<Boolean> isRegular) metadata-preserving constructor. Java Schedule "
            + "currently has only the (dates), (dates, calendar), and (dates, calendar, "
            + "convention) date-based ctors — the 8-arg metadata variant is missing.")
    @Test public void testPartialScheduleLegConstruction() { fail("not implemented"); }

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
