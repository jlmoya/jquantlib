/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import org.jquantlib.QL;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CallabilitySchedule;
import org.jquantlib.time.*;

/**
 * Callable / puttable fixed-rate bond.
 * <p>
 * Port of C++ v1.42.1 {@code ql/experimental/callablebonds/callablebond.{hpp,cpp}} (the {@code CallableFixedRateBond}
 * portion).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>Phase 5e.5b-CFC-d-253 — the five-extra-arg ex-coupon ctor overload
 *     ({@code exCouponPeriod, exCouponCalendar, exCouponConvention,
 *     exCouponEndOfMonth}) now threads through to
 *     {@link FixedRateLeg#withExCouponPeriod(Period, Calendar,
 *     BusinessDayConvention, boolean)}, matching the C++ ctor exactly.
 * </ul>
 */
public class CallableFixedRateBond extends CallableBond {

    /**
     * Full ctor — mirrors C++ v1.42.1
     * {@code CallableFixedRateBond(Natural settlementDays, Real faceAmount, const Schedule&, const std::vector<Rate>&,
     * const DayCounter&, BusinessDayConvention, Real redemption, const Date& issueDate, const CallabilitySchedule&,
     * const Period& exCouponPeriod, const Calendar& exCouponCalendar, BusinessDayConvention exCouponConvention, bool
     * exCouponEndOfMonth)}.
     */
    public CallableFixedRateBond(final int settlementDays, final double faceAmount, final Schedule schedule,
            final double[] coupons, final DayCounter accrualDayCounter, final BusinessDayConvention paymentConvention,
            final double redemption, final Date issueDate, final CallabilitySchedule putCallSchedule,
            final Period exCouponPeriod, final Calendar exCouponCalendar,
            final BusinessDayConvention exCouponConvention, final boolean exCouponEndOfMonth) {
        super(settlementDays, schedule.dates().get(schedule.dates().size() - 1), schedule.calendar(), accrualDayCounter,
                faceAmount, issueDate, putCallSchedule);

        // Mirrors C++ ql/experimental/callablebonds/callablebond.cpp:
        //   frequency_ = schedule.hasTenor() ? schedule.tenor().frequency()
        //                                    : NoFrequency;
        // The hasTenor() guard is required for arbitrary-date schedules
        // (Schedule(List<Date>, Calendar, BDC)) where calling tenor() would
        // throw "full interface (tenor) not available". Phase 5e.5b-CFC-d-159.
        frequency_ = (schedule.hasTenor() && schedule.tenor().length() != 0)
                ? schedule.tenor().frequency()
                : Frequency.NoFrequency;

        final FixedRateLeg legBuilder = new FixedRateLeg(schedule, accrualDayCounter).withNotionals(faceAmount)
                .withCouponRates(coupons).withPaymentAdjustment(paymentConvention);
        // Mirror C++: only thread ex-coupon when the period is non-trivial.
        // {@link FixedRateLeg#computeExCouponDate} guards on length()==0,
        // but its branch dereferences the calendar — guard upstream so we
        // don't have to pass a non-null calendar in the default case.
        if ( exCouponPeriod != null && exCouponPeriod.length() != 0 ) {
            legBuilder.withExCouponPeriod(exCouponPeriod, exCouponCalendar, exCouponConvention, exCouponEndOfMonth);
        }
        cashflows_ = legBuilder.Leg();

        addRedemptionsToCashflows(new double[] { redemption });

        QL.ensure(!cashflows().isEmpty(), "bond with no cashflows!");
        QL.ensure(redemptions_.size() == 1, "multiple redemptions created");
    }

    /**
     * Convenience overload: no ex-coupon (period=empty, calendar=null, convention=Unadjusted, endOfMonth=false).
     * Mirrors the C++ default arguments of the full ctor.
     */
    public CallableFixedRateBond(final int settlementDays, final double faceAmount, final Schedule schedule,
            final double[] coupons, final DayCounter accrualDayCounter, final BusinessDayConvention paymentConvention,
            final double redemption, final Date issueDate, final CallabilitySchedule putCallSchedule) {
        this(settlementDays, faceAmount, schedule, coupons, accrualDayCounter, paymentConvention, redemption, issueDate,
                putCallSchedule, new Period(), null, BusinessDayConvention.Unadjusted, false);
    }

    /** Convenience overload: default {@code redemption=100}, no put/call schedule. */
    public CallableFixedRateBond(final int settlementDays, final double faceAmount, final Schedule schedule,
            final double[] coupons, final DayCounter accrualDayCounter, final BusinessDayConvention paymentConvention,
            final double redemption, final Date issueDate) {
        this(settlementDays, faceAmount, schedule, coupons, accrualDayCounter, paymentConvention, redemption, issueDate,
                new CallabilitySchedule());
    }

    /** Convenience overload: default {@code paymentConvention=Following}. */
    public CallableFixedRateBond(final int settlementDays, final double faceAmount, final Schedule schedule,
            final double[] coupons, final DayCounter accrualDayCounter, final double redemption, final Date issueDate,
            final CallabilitySchedule putCallSchedule) {
        this(settlementDays, faceAmount, schedule, coupons, accrualDayCounter, BusinessDayConvention.Following,
                redemption, issueDate, putCallSchedule);
    }
}
