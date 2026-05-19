/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.AveragingMultipleResetsPricer;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.CompoundingMultipleResetsPricer;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.MultipleResetsCoupon;
import org.jquantlib.cashflow.MultipleResetsLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor1M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/multipleresetscoupons.cpp}
 * v1.42.1 (288 LOC, 5 cases).
 *
 * <p>Exercises multiple-resets coupons — coupons whose payoff depends on
 * the compounded or arithmetic-averaged value of an IBOR fixing taken at
 * multiple sub-period reset dates within a single accrual period. Tests
 * compounded vs averaged variants, ex-coupon cashflow logic, leg
 * consistency checks, and a regression case.
 *
 * <p><strong>All 5 cases deferred to Phase 5d.5</strong> — Java has no
 * multiple-resets coupon family:
 * <ul>
 *   <li>No {@code MultipleResetsCoupon} class
 *       (C++ {@code ql/cashflows/multipleresetscoupon.hpp});
 *   <li>No compounded / averaged sub-period IBOR pricer for this coupon;
 *   <li>No {@code MultipleResetsLeg} builder;
 *   <li>No ex-coupon date treatment for the multiple-resets case.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the entire multiple-resets coupon family
 * belongs to a future production-code phase.
 *
 * <p>Source: {@code test-suite/multipleresetscoupons.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class MultipleResetsCouponsTest {

    private static final String REASON_CONSISTENCY =
            "Phase 5d.5-MR carry: Java MultipleResetsLeg builder does not yet "
          + "validate against zero-gearing or oversized gearings vector at "
          + "Leg() build time, so 2 of the 6 BOOST_CHECK_THROW sub-cases in "
          + "C++ testMultipleResetsLegConsistencyChecks would not throw.";

    /**
     * Mirrors C++ {@code multipleresetscoupons.cpp::CommonVars}. As of
     * Phase 5e.5b-CFC-d-302 the full ex-coupon flow lights up:
     * {@link MultipleResetsCoupon} threads {@code exCouponDate} onto the
     * inherited {@code Coupon.exCouponDate_} field (Phase 5e.5b-CFC-d-203)
     * and {@link CashFlows#npv(Leg, YieldTermStructure, boolean, Date, Date)}
     * skips cashflows for which {@code tradingExCoupon(settlementDate)} is
     * {@code true} (cashflows.cpp:441-443).
     */
    private static final class CommonVars {
        final Date today;
        final Calendar calendar;
        final DayCounter dayCount;
        final BusinessDayConvention businessConvention;
        final IborIndex euribor;
        final RelinkableHandle<YieldTermStructure> euriborHandle;

        CommonVars() {
            dayCount = new Actual365Fixed();
            businessConvention = BusinessDayConvention.ModifiedFollowing;

            euriborHandle = new RelinkableHandle<YieldTermStructure>();
            euribor = new Euribor1M(euriborHandle);
            euribor.addFixing(new Date(13, Month.January,  2021), 0.0077);
            euribor.addFixing(new Date(11, Month.February, 2021), 0.0075);
            euribor.addFixing(new Date(11, Month.March,    2021), 0.0073);

            calendar = euribor.fixingCalendar();
            today = calendar.adjust(new Date(15, Month.March, 2021));
            new Settings().setEvaluationDate(today);

            euriborHandle.linkTo(Utilities.flatRate(today, 0.007, dayCount));
        }

        Schedule createSchedule(final Date start, final Date end) {
            return new MakeSchedule(start, end, euribor.tenor(),
                    euribor.fixingCalendar(),
                    euribor.businessDayConvention())
                    .schedule();
        }

        Leg createIborLeg(final Schedule schedule, final double spread) {
            return new IborLeg(schedule, euribor)
                    .withNotionals(1.0)
                    .withSpreads(spread)
                    // ex-coupon is recorded on the IborLeg builder (Java
                    // does not thread it through to coupon construction
                    // yet) — kept here to mirror the C++ test verbatim.
                    .withExCouponPeriod(new Period(2, TimeUnit.Days),
                            calendar, businessConvention)
                    .withPaymentLag(1)
                    .withFixingDays(euribor.fixingDays())
                    .Leg();
        }

        MultipleResetsCoupon createMultipleResetsCoupon(final Schedule schedule,
                                                        final double rateSpread,
                                                        final RateAveraging.Type averaging) {
            final Calendar paymentCalendar = euribor.fixingCalendar();
            final BusinessDayConvention paymentBdc = euribor.businessDayConvention();
            final Date paymentDate = paymentCalendar.advance(
                    schedule.dates().get(schedule.size() - 1),
                    new Period(1, TimeUnit.Days), paymentBdc);
            final MultipleResetsCoupon cpn = new MultipleResetsCoupon(
                    paymentDate, /* nominal */ 1.0, schedule,
                    /* fixingDays */ euribor.fixingDays(), euribor,
                    /* gearing */ 1.0, /* couponSpread */ 0.0,
                    /* rateSpread */ rateSpread,
                    /* refStart */ new Date(), /* refEnd */ new Date(),
                    new DayCounter());
            switch (averaging) {
                case Compound:
                    cpn.setPricer(new CompoundingMultipleResetsPricer());
                    break;
                case Simple:
                    cpn.setPricer(new AveragingMultipleResetsPricer());
                    break;
            }
            return cpn;
        }

        /**
         * Mirrors C++ {@code multipleresetscoupons.cpp::CommonVars::createMultipleResetsLeg}.
         * Returns a fully-configured {@link MultipleResetsLeg} builder ready to
         * be built (with {@code .Leg()}) or further customised.
         */
        MultipleResetsLeg createMultipleResetsLeg(final Date start, final Date end) {
            final Schedule s = createSchedule(start, end);
            return new MultipleResetsLeg(s, euribor, 6)
                    .withNotionals(1.0)
                    .withExCouponPeriod(new Period(2, TimeUnit.Days),
                            calendar, businessConvention)
                    .withPaymentLag(1)
                    .withFixingDays(2)
                    .withRateSpreads(0.0)
                    .withCouponSpreads(0.0)
                    .withAveragingMethod(RateAveraging.Type.Compound);
        }
    }

    /**
     * Port of C++ {@code multipleresetscoupons.cpp::testCompoundedCouponWithMultipleResets}.
     *
     * <p>Builds a 6-month monthly Euribor1M leg + a single
     * {@link MultipleResetsCoupon} over the same schedule and verifies the
     * coupon's payment matches {@code prod (1 + dt_i * (fixing_i + spread)) - 1}
     * to a {@code 1e-14} tolerance.
     */
    @Test
    public void testCompoundedCouponWithMultipleResets() {
        QL.info("Testing coupon with multiple compounded resets...");

        final CommonVars vars = new CommonVars();

        final Date start = vars.today.sub(new Period(2, TimeUnit.Months));
        final Date end   = start.add(new Period(6, TimeUnit.Months));

        final double spread = 0.001;

        final Schedule schedule = vars.createSchedule(start, end);

        final Leg iborLeg = vars.createIborLeg(schedule, spread);
        final MultipleResetsCoupon testCpn = vars.createMultipleResetsCoupon(
                schedule, spread, RateAveraging.Type.Compound);

        final double tolerance = 1.0e-14;

        final double actualPayment = testCpn.amount();

        double compound = 1.0;
        for (final CashFlow cf : iborLeg) {
            final IborCoupon cpn = (IborCoupon) cf;
            final double yearFraction = cpn.accrualPeriod();
            final double fixing = vars.euribor.fixing(cpn.fixingDate());
            compound *= (1.0 + yearFraction * (fixing + cpn.spread()));
        }
        final double expectedPayment = compound - 1.0;

        if (Math.abs(actualPayment - expectedPayment) > tolerance) {
            fail("unable to replicate compounded multiple-resets coupon payment\n"
                    + "    calculated: " + actualPayment + "\n"
                    + "    expected:   " + expectedPayment + "\n"
                    + "    start:      " + start + "\n"
                    + "    end:        " + end);
        }
    }

    /**
     * Port of C++ {@code multipleresetscoupons.cpp::testAveragedCouponWithMultipleResets}.
     *
     * <p>Same setup as the compounded case, with the simple-average
     * pricer: payment matches {@code sum dt_i * (fixing_i + spread)} to
     * {@code 1e-14}.
     */
    @Test
    public void testAveragedCouponWithMultipleResets() {
        QL.info("Testing coupon with multiple averaged resets...");

        final CommonVars vars = new CommonVars();

        final Date start = vars.today.sub(new Period(2, TimeUnit.Months));
        final Date end   = start.add(new Period(6, TimeUnit.Months));

        final double spread = 0.001;

        final Schedule schedule = vars.createSchedule(start, end);

        final Leg iborLeg = vars.createIborLeg(schedule, spread);
        final MultipleResetsCoupon testCpn = vars.createMultipleResetsCoupon(
                schedule, spread, RateAveraging.Type.Simple);

        final double tolerance = 1.0e-14;

        final double actualPayment = testCpn.amount();

        double expectedPayment = 0.0;
        for (final CashFlow cf : iborLeg) {
            final IborCoupon cpn = (IborCoupon) cf;
            final double yearFraction = cpn.accrualPeriod();
            final double fixing = vars.euribor.fixing(cpn.fixingDate());
            expectedPayment += yearFraction * (fixing + cpn.spread());
        }

        if (Math.abs(actualPayment - expectedPayment) > tolerance) {
            fail("unable to replicate averaged multiple-resets coupon payment\n"
                    + "    calculated: " + actualPayment + "\n"
                    + "    expected:   " + expectedPayment + "\n"
                    + "    start:      " + start + "\n"
                    + "    end:        " + end);
        }
    }

    /**
     * Port of C++ {@code multipleresetscoupons.cpp::testExCouponCashFlow}
     * (test-suite/multipleresetscoupons.cpp:185-213).
     *
     * <p>Builds a 6-month multi-reset coupon paying on
     * {@code today + 2 business days} with an ex-coupon date at
     * {@code today - 2 business days}. With settlement on {@code today},
     * the cashflow is in its ex-coupon window, so
     * {@code CashFlows.npv(leg, curve, false, today, today)} must return
     * {@code 0.0} once {@link CashFlows} honours
     * {@code tradingExCoupon(settlementDate)} (cashflows.cpp:441-443).
     *
     * <p>Phase 5e.5b-CFC-d-302 — un-ignore + body-fill following the
     * {@link CashFlows#npv(Leg, YieldTermStructure, boolean, Date, Date)}
     * ex-coupon filter alignment.
     */
    @Test
    public void testExCouponCashFlow() {
        QL.info("Testing ex-coupon cash flow...");

        final CommonVars vars = new CommonVars();

        final Date start = vars.calendar.advance(vars.today, -6, TimeUnit.Months);
        final Date end   = vars.today;
        final Schedule schedule = vars.createSchedule(start, end);

        final Calendar paymentCalendar = vars.euribor.fixingCalendar();
        final Date paymentDate   = paymentCalendar.advance(end,  2, TimeUnit.Days);
        final Date exCouponDate  = paymentCalendar.advance(end, -2, TimeUnit.Days);

        final MultipleResetsCoupon cpn = new MultipleResetsCoupon(
                paymentDate, /* nominal */ 1.0, schedule,
                /* fixingDays */ 2, vars.euribor,
                /* gearing */ 1.0, /* couponSpread */ 0.0,
                /* rateSpread */ 0.0,
                /* refStart */ new Date(), /* refEnd */ new Date(),
                new DayCounter(), exCouponDate);
        cpn.setPricer(new CompoundingMultipleResetsPricer());

        final Leg leg = new Leg();
        leg.add(cpn);

        final double npv = CashFlows.npv(leg, vars.euriborHandle.currentLink(),
                /* includeSettlementDateFlows */ false, vars.today, vars.today);

        final double tolerance = 1.0e-14;
        if (Math.abs(npv) > tolerance) {
            fail("cash flow was expected to go ex-coupon\n"
                    + "    calculated: " + npv + "\n"
                    + "    expected:   " + 0.0 + "\n"
                    + "    start:      " + start + "\n"
                    + "    end:        " + end);
        }
    }

    /**
     * Port of C++ {@code multipleresetscoupons.cpp::testMultipleResetsLegConsistencyChecks}.
     *
     * <p>Mirrors all 7 {@code BOOST_CHECK_THROW} sub-cases of the C++ test:
     * the base configuration is a 10-year monthly Euribor1M leg with 6 resets
     * per coupon. Each sub-case mutates a single builder method on a fresh
     * builder and asserts that {@code .Leg()} throws.
     *
     * <ul>
     *   <li>L0 — empty notionals  → "no notional given"</li>
     *   <li>L1 — too many notionals  → "too many nominals"</li>
     *   <li>L2 — too many fixingDays → "too many fixing days"</li>
     *   <li>L3 — zero gearing       → "Null gearing not allowed"
     *       (raised inside {@link org.jquantlib.cashflow.FloatingRateCoupon}'s
     *       ctor, surfaced when the first coupon is built)</li>
     *   <li>L4 — too many gearings   → "too many gearings"</li>
     *   <li>L5 — too many couponSpreads → "too many coupon spreads"</li>
     *   <li>L6 — too many rateSpreads  → "too many rate spreads"</li>
     * </ul>
     *
     * <p>Phase 5e.5b-CFC-d-203.
     */
    @Test
    public void testMultipleResetsLegConsistencyChecks() {
        QL.info("Testing multiple-resets leg consistency checks...");

        final CommonVars vars = new CommonVars();

        final Date start = new Date(18, Month.March, 2021);
        final Date end   = new Date(18, Month.March, 2031);

        final Leg validLeg = vars.createMultipleResetsLeg(start, end).Leg();
        final int N = validLeg.size();

        // L0 — empty notionals
        expectThrow("empty notionals", new Runnable() {
            @Override
            public void run() {
                vars.createMultipleResetsLeg(start, end)
                    .withNotionals(new ArrayList<Double>())
                    .Leg();
            }
        });

        // L1 — too many notionals
        expectThrow("too many notionals", new Runnable() {
            @Override
            public void run() {
                vars.createMultipleResetsLeg(start, end)
                    .withNotionals(repeat(N + 1, 1.0))
                    .Leg();
            }
        });

        // L2 — too many fixingDays
        expectThrow("too many fixingDays", new Runnable() {
            @Override
            public void run() {
                vars.createMultipleResetsLeg(start, end)
                    .withFixingDays(repeatInt(N + 1, 2))
                    .Leg();
            }
        });

        // L3 — zero gearing
        expectThrow("zero gearing", new Runnable() {
            @Override
            public void run() {
                vars.createMultipleResetsLeg(start, end)
                    .withGearings(0.0)
                    .Leg();
            }
        });

        // L4 — too many gearings
        expectThrow("too many gearings", new Runnable() {
            @Override
            public void run() {
                vars.createMultipleResetsLeg(start, end)
                    .withGearings(repeat(N + 1, 1.0))
                    .Leg();
            }
        });

        // L5 — too many couponSpreads
        expectThrow("too many couponSpreads", new Runnable() {
            @Override
            public void run() {
                vars.createMultipleResetsLeg(start, end)
                    .withCouponSpreads(repeat(N + 1, 0.0))
                    .Leg();
            }
        });

        // L6 — too many rateSpreads
        expectThrow("too many rateSpreads", new Runnable() {
            @Override
            public void run() {
                vars.createMultipleResetsLeg(start, end)
                    .withRateSpreads(repeat(N + 1, 0.0))
                    .Leg();
            }
        });
    }

    /** Asserts {@code body} throws; fails the test with {@code label} when it does not. */
    private static void expectThrow(final String label, final Runnable body) {
        try {
            body.run();
        } catch (final RuntimeException expected) {
            return;
        }
        fail("expected exception not thrown for case: " + label);
    }

    private static List<Double> repeat(final int n, final double v) {
        final List<Double> out = new ArrayList<Double>(n);
        for (int i = 0; i < n; i++) {
            out.add(v);
        }
        return out;
    }

    private static List<Integer> repeatInt(final int n, final int v) {
        final List<Integer> out = new ArrayList<Integer>(n);
        for (int i = 0; i < n; i++) {
            out.add(v);
        }
        return out;
    }

    /**
     * Phase Body-Fill (2026-05-09) — port of C++
     * {@code multipleresetscoupons.cpp::testMultipleResetsLegRegression}.
     *
     * <p>Builds a 1Y monthly schedule with {@code resetsPerCoupon = 3}
     * (so each coupon spans 3 sub-periods) and asserts every coupon in
     * the resulting leg has exactly 3 fixing dates.
     *
     * <p>This is a pure structural test that does not depend on cached
     * reference values or pricing-engine integration.
     */
    @Test
    public void testMultipleResetsLegRegression() {
        QL.info("Testing number of fixing dates in multiple-resets coupons...");

        final Schedule schedule = new MakeSchedule(
                new Date(1, Month.August, 2024),
                new Date(1, Month.August, 2025),
                new Period(Frequency.Monthly),
                new Target(),
                org.jquantlib.time.BusinessDayConvention.Following)
                .schedule();

        final int resetsPerCoupon = 3;
        final Leg leg = new MultipleResetsLeg(
                schedule, new Euribor1M(), resetsPerCoupon)
                .withNotionals(100.0)
                .withAveragingMethod(RateAveraging.Type.Compound)
                .Leg();

        for (final CashFlow cf : leg) {
            if (!(cf instanceof MultipleResetsCoupon)) {
                fail("expected MultipleResetsCoupon, got " + cf.getClass());
            }
            final MultipleResetsCoupon c = (MultipleResetsCoupon) cf;
            final int n = c.fixingDates().size();
            if (n != resetsPerCoupon) {
                fail("Unexpected number of fixing dates (" + n
                        + ") in coupon paying on " + c.date());
            }
        }
    }
}
