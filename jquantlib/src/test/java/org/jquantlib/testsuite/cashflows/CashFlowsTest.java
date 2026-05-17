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
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
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
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
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

    @Ignore("Phase 5d.5 — needs CashFlows.accruedPeriod(Leg, boolean), "
            + "CashFlows.accruedDays(Leg, boolean), and CashFlows.accruedAmount(Leg, boolean) "
            + "static overloads that default the settlement date to Settings.evaluationDate. "
            + "Java CashFlows currently exposes only the (Leg, boolean, Date) variants; "
            + "the default-date overloads need porting from C++ cashflows.cpp.")
    @Test public void testDefaultSettlementDate() { fail("not implemented"); }

    @Ignore("Phase 5d.5 — needs Coupon.exCouponDate() accessor + "
            + "FixedRateLeg.withExCouponPeriod(Period, Calendar, BusinessDayConvention, boolean) "
            + "and the matching IborLeg.withExCouponPeriod chain to propagate the period to "
            + "FixedRateCoupon / FloatingRateCoupon constructors. The current Java Coupon "
            + "base class does not carry an ex-coupon-date field; production port required.")
    @Test public void testExCouponDates() { fail("not implemented"); }

    @Ignore("Phase 5d.5 — Schedule(...,endOfMonth=true) generator currently snaps "
            + "irregular first-coupon reference start to schedule's first regular date "
            + "rather than to the prior end-of-month per C++ Schedule::nextTwentieth/EOM "
            + "logic. Reference value (31-Aug-2016 for the 17-Jan-2017 -> 28-Feb-2018 "
            + "semi-annual schedule) must come from a probe against C++ v1.42.1 once "
            + "the Schedule generator is aligned.")
    @Test public void testIrregularFirstCouponReferenceDatesAtEndOfMonth() { fail("not implemented"); }

    @Ignore("Phase 5d.5 — requires MakeSchedule fluent methods .withCalendar / "
            + ".withTenor / .withTerminationDateConvention / .withFirstDate / "
            + ".withNextToLastDate (currently only constructor + .withRule/.endOfMonth/"
            + ".withFirstDate/.withNextToLastDate are ported) AND the Schedule generator "
            + "must handle the GovernmentBond-calendar end-of-calendar-month snapping "
            + "exposed by the 30-Sep-2017 -> 30-Sep-2022 semi-annual schedule.")
    @Test public void testIrregularFirstCouponReferenceDatesAtEndOfCalendarMonth() { fail("not implemented"); }

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

    @Ignore("Phase 5d.5 — needs IborCoupon.indexFixing() to short-circuit on a stored "
            + "fixing-history hit BEFORE consulting the (possibly absent) forecast term "
            + "structure, mirroring C++ iborcoupon.cpp v1.42.1 indexFixing() ordering. "
            + "Java FloatingRateCoupon.indexFixing() currently calls index_.fixing(date) "
            + "which short-circuits correctly only if IborIndex.fixing handles missing "
            + "termStructure when a stored fixing exists — port needs verification.")
    @Test public void testFixedIborCouponWithoutForecastCurve() { fail("not implemented"); }

    @Ignore("Phase 5d.5 — needs IborCoupon.hasFixed() accessor + "
            + "Settings.enforcesTodaysHistoricFixings() flag (with setter). The "
            + "hasFixed() contract: true iff fixingDate < today, OR fixingDate == today "
            + "AND enforcesTodaysHistoricFixings(). Java has neither the accessor nor "
            + "the per-session flag; production port required before this test can run.")
    @Test public void testIborCouponKnowsWhenitHasFixed() { fail("not implemented"); }
}
