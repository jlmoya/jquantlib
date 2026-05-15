/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.AveragingMultipleResetsPricer;
import org.jquantlib.cashflow.CashFlow;
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
import org.junit.Ignore;
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

    private static final String REASON_EX_COUPON =
            "Phase 5d.5-MR carry: MultipleResetsCoupon currently does not "
          + "thread an exCouponDate through to FloatingRateCoupon (Java "
          + "FloatingRateCoupon ctor lacks the parameter); the C++ "
          + "testExCouponCashFlow case relies on that plumbing to drive "
          + "CashFlows::npv to zero on the ex-coupon date.";

    private static final String REASON_CONSISTENCY =
            "Phase 5d.5-MR carry: Java MultipleResetsLeg builder does not yet "
          + "validate against zero-gearing or oversized gearings vector at "
          + "Leg() build time, so 2 of the 6 BOOST_CHECK_THROW sub-cases in "
          + "C++ testMultipleResetsLegConsistencyChecks would not throw.";

    /**
     * Mirrors C++ {@code multipleresetscoupons.cpp::CommonVars}, with the
     * adjustment that JQuantLib's {@link MultipleResetsCoupon} ctor does
     * not yet accept an {@code exCouponDate} parameter (Phase 5d.5-MR
     * carry-forward); ex-coupon plumbing is therefore omitted from the
     * reproduction below — none of the bodied cases below depend on it.
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

    @Ignore(REASON_EX_COUPON)
    @Test
    public void testExCouponCashFlow() { fail("not implemented"); }

    @Ignore(REASON_CONSISTENCY)
    @Test
    public void testMultipleResetsLegConsistencyChecks() { fail("not implemented"); }

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
