/*
 Copyright (C) 2026 JQuantLib team

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 Toyin Akin
 Copyright (C) 2021 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.cashflow;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.time.*;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Multiple-reset coupon.
 *
 * <p>Coupon paying a rate calculated by compounding or averaging multiple
 * fixings during its accrual period.  Mirrors C++ v1.42.1 {@code ql/cashflows/multipleresetscoupon.{hpp,cpp}} (Toyin
 * Akin 2008, Marcin Rybacki 2021).
 *
 * <p>Phase 5d.5-MR.
 *
 * @author JQuantLib team
 */
public class MultipleResetsCoupon extends FloatingRateCoupon {

    private final List< Date > valueDates_;
    private final List< Date > fixingDates_;
    private final int n_;
    private final List< Double > dt_;
    private final double rateSpread_;

    /**
     * Construct a multiple-reset coupon.
     *
     * @param paymentDate    when the coupon is paid
     * @param nominal        notional amount
     * @param resetSchedule  schedule for the multiple resets.  Its first and last dates are also the start and end
     *                       dates of the coupon.  Each period in the schedule is the underlying period of one fixing;
     *                       the corresponding fixing date is {@code fixingDays} business days before the start of the
     *                       period (per the index's fixing calendar).
     * @param fixingDays     number of business days before each sub-period start to take the fixing; pass {@code 0} to
     *                       use each value date as its own fixing date.
     * @param index          the underlying ibor index providing the fixings
     * @param gearing        multiplier for the final coupon rate (default 1.0)
     * @param couponSpread   spread added to the final coupon rate (default 0.0)
     * @param rateSpread     spread added to each underlying fixing (default 0.0)
     * @param refPeriodStart reference period start (for the day counter)
     * @param refPeriodEnd   reference period end
     * @param dayCounter     day counter for the coupon's accrual period
     */
    public MultipleResetsCoupon(final Date paymentDate, final double nominal, final Schedule resetSchedule,
            final int fixingDays, final IborIndex index, final double gearing, final double couponSpread,
            final double rateSpread, final Date refPeriodStart, final Date refPeriodEnd, final DayCounter dayCounter) {
        this(paymentDate, nominal, resetSchedule, fixingDays, index, gearing, couponSpread, rateSpread, refPeriodStart,
                refPeriodEnd, dayCounter,
                /* exCouponDate */ new Date());
    }

    /**
     * Construct a multiple-reset coupon with an ex-coupon date.
     *
     * <p>Mirrors C++ v1.42.1 {@code MultipleResetsCoupon::MultipleResetsCoupon}
     * full signature (ql/cashflows/multipleresetscoupon.cpp:30-46). The Java {@link FloatingRateCoupon} ctor does not
     * yet thread {@code exCouponDate} (Phase 5d.5-MR carry-forward); we set the inherited {@link Coupon#exCouponDate_}
     * field directly to mirror the C++ effect of {@code FloatingRateCoupon(..., false, exCouponDate)} which forwards to
     * {@code Coupon(..., exCouponDate)}.
     *
     * @param exCouponDate ex-coupon date; pass a default-construed {@link Date} (or {@code null}) for "no ex-coupon
     *                     date"
     */
    public MultipleResetsCoupon(final Date paymentDate, final double nominal, final Schedule resetSchedule,
            final int fixingDays, final IborIndex index, final double gearing, final double couponSpread,
            final double rateSpread, final Date refPeriodStart, final Date refPeriodEnd, final DayCounter dayCounter,
            final Date exCouponDate) {
        super(paymentDate, nominal, resetSchedule.dates().get(0), resetSchedule.dates().get(resetSchedule.size() - 1),
                fixingDays, index, gearing, couponSpread, refPeriodStart, refPeriodEnd, dayCounter,
                false /* not in arrears */);
        // Thread exCouponDate onto the inherited Coupon.exCouponDate_ field
        // (mirrors C++ FloatingRateCoupon → Coupon ctor forwarding which the
        // Java FloatingRateCoupon ctor does not yet carry — Phase 5d.5-MR).
        this.exCouponDate_ = (exCouponDate == null) ? new Date() : exCouponDate.clone();
        this.rateSpread_ = rateSpread;
        this.valueDates_ = new ArrayList<>(resetSchedule.dates());

        // fixing dates
        n_ = valueDates_.size() - 1;
        fixingDates_ = new ArrayList<>(n_);
        if ( this.fixingDays_ == 0 ) {
            for ( int i = 0; i < n_; i++ ) {
                fixingDates_.add(valueDates_.get(i));
            }
        } else {
            for ( int i = 0; i < n_; i++ ) {
                fixingDates_.add(fixingDate(valueDates_.get(i)));
            }
        }

        // accrual times of sub-periods
        dt_ = new ArrayList<>(n_);
        final DayCounter dc = index.dayCounter();
        for ( int i = 0; i < n_; i++ ) {
            dt_.add(dc.yearFraction(valueDates_.get(i), valueDates_.get(i + 1)));
        }
    }

    /**
     * Convenience ctor with
     * {@code gearing=1, couponSpread=0, rateSpread=0, refPeriodStart=null Date, refPeriodEnd=null Date,
     * dayCounter=empty}.
     */
    public MultipleResetsCoupon(final Date paymentDate, final double nominal, final Schedule resetSchedule,
            final int fixingDays, final IborIndex index) {
        this(paymentDate, nominal, resetSchedule, fixingDays, index, 1.0, 0.0, 0.0, new Date(), new Date(),
                new DayCounter());
    }

    /**
     * Fixing dates for the rates to be compounded / averaged. Mirrors C++
     * {@code MultipleResetsCoupon::fixingDates() const}.
     */
    public List< Date > fixingDates() {
        return fixingDates_;
    }

    /**
     * Accrual (sub-period) times. Mirrors C++ {@code MultipleResetsCoupon::dt() const}.
     */
    public List< Double > dt() {
        return dt_;
    }

    /**
     * Value dates for the rates to be compounded / averaged. Mirrors C++
     * {@code MultipleResetsCoupon::valueDates() const}.
     */
    public List< Date > valueDates() {
        return valueDates_;
    }

    /**
     * Rate spread (added to each fixing). Mirrors C++ {@code MultipleResetsCoupon::rateSpread() const}.
     */
    public double rateSpread() {
        return rateSpread_;
    }

    /**
     * The date when the coupon is fully determined: the last fixing date. Mirrors C++
     * {@code MultipleResetsCoupon::fixingDate() const} which returns {@code fixingDates_.back()}.
     */
    @Override
    public Date fixingDate() {
        return fixingDates_.get(fixingDates_.size() - 1);
    }

    /**
     * Compute the fixing date for a given sub-period value date by stepping {@code fixingDays_} business days backwards
     * on the index's fixing calendar (Preceding adjustment, like C++).
     */
    private Date fixingDate(final Date valueDate) {
        return index_.fixingCalendar()
                .advance(valueDate, new Period(-fixingDays_, TimeUnit.Days), BusinessDayConvention.Preceding);
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< MultipleResetsCoupon > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
