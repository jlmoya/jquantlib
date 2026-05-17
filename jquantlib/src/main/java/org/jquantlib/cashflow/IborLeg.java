/*
Copyright (C) 2009 Ueli Hofstetter
Copyright (C) 2009 John Martin

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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2007 Cristina Duminuco
 Copyright (C) 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/
package org.jquantlib.cashflow;

import java.lang.reflect.Field;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * Helper class building a sequence of capped/floored ibor-rate coupons
 *
 * @author Ueli Hofstetter
 * @author John Martin
 */
public class IborLeg {

    private final Schedule schedule_;
    private final IborIndex index_;
    private Array notionals_;
    private DayCounter paymentDayCounter_;
    private BusinessDayConvention paymentAdjustment_;
    private Array fixingDays_;
    private Array gearings_;
    private Array spreads_;
    private Array caps_, floors_;
    private boolean inArrears_, zeroPayments_;
    /** Phase 5d.5-Bonds-b — payment-date calendar (null = use schedule's). */
    private Calendar paymentCalendar_;
    /** Phase 5d.5-Bonds-b — business-day payment lag (default 0). */
    private int paymentLag_;
    /** Phase 5d.5-Bonds-b — ex-coupon period (default empty). */
    private Period exCouponPeriod_;
    /** Phase 5d.5-Bonds-b — ex-coupon calendar (default empty). */
    private Calendar exCouponCalendar_;
    /** Phase 5d.5-Bonds-b — ex-coupon adjustment (default Unadjusted, per C++). */
    private BusinessDayConvention exCouponAdjustment_;
    /** Phase 5d.5-Bonds-b — ex-coupon end-of-month flag (default false). */
    private boolean exCouponEndOfMonth_;

    public IborLeg(final Schedule schedule, final IborIndex index) {
        schedule_ = (schedule);
        index_ = (index);
        paymentAdjustment_ = BusinessDayConvention.Following;
        // Mirror C++ v1.42.1 ql/cashflows/iborcoupon.hpp: paymentDayCounter_
        // is a default-constructed DayCounter (empty()) until withPaymentDayCounter
        // is called. The downstream FloatingRateCoupon ctor checks
        // dayCounter_.empty() and substitutes index.dayCounter() — which
        // requires a non-null DayCounter instance, not Java's default null.
        paymentDayCounter_ = new DayCounter();

        // TODO : review initialization
        // these are vectors in quantlib, therfor they must be initalized to default
        // values or nullable. since we have decided to write the code without null checks
        // all over the place we are going to initialze them for now to be consistent with
        // quantlib behavoir
        fixingDays_ = new Array(0);
        gearings_ = new Array(0);
        spreads_ = new Array(0);
        caps_ = new Array(0);
        floors_ = new Array(0);
        inArrears_ = false;
        zeroPayments_ = false;

        // Phase 5d.5-Bonds-b — payment / ex-coupon defaults match C++.
        paymentCalendar_ = null;
        paymentLag_ = 0;
        exCouponPeriod_ = new Period();
        exCouponCalendar_ = new Calendar();
        exCouponAdjustment_ = BusinessDayConvention.Unadjusted;
        exCouponEndOfMonth_ = false;
    }

    public final IborLeg withNotionals(/* @Real */final double notional) {
        notionals_ = new Array(new double[] { notional });// std::vector<Real>(1,notional);
        return this;
    }

    public final IborLeg withNotionals(final Array notionals) {
        notionals_ = notionals;
        return this;
    }

    public final IborLeg withPaymentDayCounter(final DayCounter dayCounter) {
        paymentDayCounter_ = dayCounter;
        return this;
    }

    public final IborLeg withPaymentAdjustment(final BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public final IborLeg withFixingDays(/* @Natural */final double fixingDays) {
        fixingDays_ = new Array(new double[] { fixingDays });// std::vector<Natural>(1,fixingDays);
        return this;
    }

    public final IborLeg withFixingDays(final Array fixingDays) {
        fixingDays_ = fixingDays;
        return this;
    }

    public IborLeg withGearings(/* @Real */final double gearing) {
        gearings_ = new Array(new double[] { gearing });
        return this;
    }

    public IborLeg withGearings(final Array gearings) {
        gearings_ = gearings;
        return this;
    }

    public IborLeg withSpreads(/* @Spread */final double spread) {
        spreads_ = new Array(new double[] { spread });
        return this;
    }

    public IborLeg withSpreads(final Array spreads) {
        spreads_ = spreads;
        return this;
    }

    public IborLeg withCaps(/* @Rate */final double cap) {
        caps_ = new Array(1).fill(cap);
        return this;
    }

    public IborLeg withCaps(final Array caps) {
        caps_ = caps;
        return this;
    }

    public IborLeg withFloors(/* @Rate */final double floor) {
        floors_ = new Array(1).fill(floor);
        return this;
    }

    public IborLeg withFloors(final Array floors) {
        floors_ = floors;
        return this;
    }

    public IborLeg inArrears(final boolean flag) {
        inArrears_ = flag;
        return this;
    }

    /** Phase 5d.5-Bonds-b — convenience overload mirroring C++
     *  default {@code IborLeg::inArrears(bool flag = true)}
     *  (ql/cashflows/iborcoupon.hpp:155). */
    public IborLeg inArrears() {
        return inArrears(true);
    }

    public IborLeg withZeroPayments(final boolean flag) {
        zeroPayments_ = flag;
        return this;
    }

    /** Phase 5d.5-Bonds-b — overload mirroring C++ default flag=true
     *  ({@code IborLeg::withZeroPayments(bool flag = true)}). */
    public IborLeg withZeroPayments() {
        return withZeroPayments(true);
    }

    /** Phase 5d.5-Bonds-b — mirror of C++
     *  {@code IborLeg::withPaymentLag(Integer lag)}
     *  (ql/cashflows/iborcoupon.cpp).  Number of business days to advance
     *  from the period-end before applying the payment adjustment. */
    public IborLeg withPaymentLag(final int lag) {
        paymentLag_ = lag;
        return this;
    }

    /** Phase 5d.5-Bonds-b — mirror of C++
     *  {@code IborLeg::withPaymentCalendar(const Calendar&)}.  Overrides
     *  the calendar used for payment-date advancement; defaults to
     *  schedule.calendar(). */
    public IborLeg withPaymentCalendar(final Calendar cal) {
        paymentCalendar_ = cal;
        return this;
    }

    /** Phase 5d.5-Bonds-b — mirror of C++
     *  {@code IborLeg::withExCouponPeriod(Period, Calendar,
     *  BusinessDayConvention, bool endOfMonth = false)}. Records ex-coupon
     *  parameters for downstream coupon construction.
     *
     *  <p>NOTE: the Java {@link FloatingRateCoupon} hierarchy does not yet
     *  carry an exCouponDate field (mirrors the gap noted on
     *  {@code FixedRateCoupon}/{@code CPICoupon}). The values are recorded
     *  on the builder and a TODO carry-forward is tracked for Phase
     *  5d.5-Bonds-c (FloatingRateCoupon ex-coupon parameter +
     *  Coupon.exCouponDate accessor). */
    public IborLeg withExCouponPeriod(final Period period,
                                      final Calendar cal,
                                      final BusinessDayConvention convention,
                                      final boolean endOfMonth) {
        exCouponPeriod_ = period;
        exCouponCalendar_ = cal;
        exCouponAdjustment_ = convention;
        exCouponEndOfMonth_ = endOfMonth;
        return this;
    }

    public IborLeg withExCouponPeriod(final Period period,
                                      final Calendar cal,
                                      final BusinessDayConvention convention) {
        return withExCouponPeriod(period, cal, convention, false);
    }

    public Leg Leg() /* @ReadOnly */{

        // Phase 5d.5-Bonds-b — thread paymentCalendar_/paymentLag_ through
        // to FloatingLeg's extended ctor.
        final Leg cashflows = new FloatingLeg(
        		IborIndex.class, IborCoupon.class, CappedFlooredIborCoupon.class,
                notionals_, schedule_, index_,
                paymentDayCounter_, paymentAdjustment_, fixingDays_,
                gearings_, spreads_, caps_, floors_, inArrears_, zeroPayments_,
                paymentCalendar_, paymentLag_);

        // Phase 5e.5b-CFC-d-111 — thread exCouponDate_ onto each Coupon
        // post-construction. FloatingLeg's reflective constructor does not
        // yet accept ex-coupon parameters (mirrors a gap noted on
        // FloatingRateCoupon vs C++ v1.42.1); writing the protected
        // {@code Coupon.exCouponDate_} field here matches the C++
        // IborLeg::operator Leg() ex-coupon block in
        // ql/cashflows/iborcoupon.cpp:277-295 (v1.42.1) without altering
        // the shared FloatingLeg / FloatingRateCoupon classes (which are
        // owned by parallel-running agents this commit cycle).
        applyExCouponDates(cashflows);

        if (caps_.empty() && floors_.empty() && !inArrears_) {
            PricerSetter.setCouponPricer(cashflows, new BlackIborCouponPricer(new Handle <OptionletVolatilityStructure>()));
        }
        return cashflows;
    }

    /** Phase 5e.5b-CFC-d-111 — compute the payment date for each
     *  generated coupon (mirroring {@code FloatingLeg}'s own
     *  {@code payCal.advance(end, paymentLag, Days, paymentAdj)}
     *  formula) and, when an ex-coupon period was configured, set
     *  the corresponding {@code Coupon.exCouponDate_} via reflection.
     *
     *  <p>The reflective write is justified because (i) the field is
     *  already present on the Java {@link Coupon} base class (mirror of
     *  the C++ {@code Coupon::exCouponDate_} field) and (ii) extending
     *  {@code FloatingLeg}'s reflective coupon-construction signature
     *  to thread an extra {@code Date} argument is out of scope for
     *  this commit (FloatingLeg / FloatingRateCoupon are owned by
     *  another in-flight agent). */
    private void applyExCouponDates(final Leg cashflows) {
        final boolean hasExCoupon =
                exCouponPeriod_ != null && exCouponPeriod_.length() != 0;
        if (!hasExCoupon) {
            return;
        }
        final Calendar payCal = (paymentCalendar_ == null)
                ? schedule_.calendar() : paymentCalendar_;
        final Field exCouponDateField;
        try {
            exCouponDateField = Coupon.class.getDeclaredField("exCouponDate_");
            exCouponDateField.setAccessible(true);
        } catch (final NoSuchFieldException nsfe) {
            // Coupon.exCouponDate_ should always exist in this branch;
            // surface a clear error if a future refactor renames it.
            throw new IllegalStateException(
                    "Coupon.exCouponDate_ not found — IborLeg "
                  + "ex-coupon threading is broken", nsfe);
        }
        for (int i = 0; i < cashflows.size(); ++i) {
            final CashFlow cf = (CashFlow) cashflows.get(i);
            if (!(cf instanceof Coupon)) {
                continue;
            }
            final Coupon coupon = (Coupon) cf;
            // Recompute the payment date the same way FloatingLeg does.
            // Schedule indices for coupon i correspond to schedule.date(i)
            // and schedule.date(i+1).
            final Date end = schedule_.date(i + 1);
            final Date paymentDate = payCal.advance(
                    end, paymentLag_, TimeUnit.Days, paymentAdjustment_, false);
            final Date exCouponDate = exCouponCalendar_.advance(
                    paymentDate,
                    exCouponPeriod_.negative(),
                    exCouponAdjustment_,
                    exCouponEndOfMonth_);
            try {
                exCouponDateField.set(coupon, exCouponDate);
            } catch (final IllegalAccessException iae) {
                QL.error("failed to set exCouponDate_ on " + coupon);
            }
        }
    }

}