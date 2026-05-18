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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Helper class building a sequence of {@link MultipleResetsCoupon} cashflows.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::MultipleResetsLeg} in
 * {@code ql/cashflows/multipleresetscoupon.{hpp,cpp}}.
 *
 * <p>Phase 5d.5-MR.
 */
public class MultipleResetsLeg {

    private final Schedule schedule_;
    private final IborIndex index_;
    private final int resetsPerCoupon_;
    private List<Double> notionals_;
    private DayCounter paymentDayCounter_;
    private Calendar paymentCalendar_;
    private BusinessDayConvention paymentAdjustment_;
    private int paymentLag_;
    private List<Integer> fixingDays_;
    private List<Double> gearings_;
    private List<Double> couponSpreads_;
    private List<Double> rateSpreads_;
    private RateAveraging.Type averagingMethod_;
    private Period exCouponPeriod_;
    private Calendar exCouponCalendar_;
    private BusinessDayConvention exCouponAdjustment_;
    private boolean exCouponEndOfMonth_;

    public MultipleResetsLeg(final Schedule fullResetSchedule,
                             final IborIndex index,
                             final int resetsPerCoupon) {
        this.schedule_ = fullResetSchedule;
        this.index_ = index;
        this.resetsPerCoupon_ = resetsPerCoupon;
        this.paymentCalendar_ = schedule_.calendar();

        QL.require(index_ != null, "no index provided");
        QL.require(!schedule_.empty(), "empty schedule provided");
        QL.require((schedule_.size() - 1) % resetsPerCoupon_ == 0,
                "number of resets per coupon does not divide exactly number of periods in schedule");

        // sensible defaults matching C++
        this.paymentDayCounter_ = new DayCounter();
        this.paymentAdjustment_ = BusinessDayConvention.Following;
        this.paymentLag_ = 0;
        this.notionals_ = new ArrayList<Double>();
        this.fixingDays_ = new ArrayList<Integer>();
        this.gearings_ = new ArrayList<Double>();
        this.couponSpreads_ = new ArrayList<Double>();
        this.rateSpreads_ = new ArrayList<Double>();
        this.averagingMethod_ = RateAveraging.Type.Compound;
        this.exCouponPeriod_ = new Period();
        this.exCouponCalendar_ = new NullCalendar();
        this.exCouponAdjustment_ = BusinessDayConvention.Unadjusted;
        this.exCouponEndOfMonth_ = false;
    }

    public MultipleResetsLeg withNotionals(final double notional) {
        this.notionals_ = new ArrayList<Double>();
        this.notionals_.add(notional);
        return this;
    }

    public MultipleResetsLeg withNotionals(final List<Double> notionals) {
        this.notionals_ = new ArrayList<Double>(notionals);
        return this;
    }

    public MultipleResetsLeg withPaymentDayCounter(final DayCounter dc) {
        this.paymentDayCounter_ = dc;
        return this;
    }

    public MultipleResetsLeg withPaymentAdjustment(final BusinessDayConvention convention) {
        this.paymentAdjustment_ = convention;
        return this;
    }

    public MultipleResetsLeg withPaymentCalendar(final Calendar cal) {
        this.paymentCalendar_ = cal;
        return this;
    }

    public MultipleResetsLeg withPaymentLag(final int lag) {
        this.paymentLag_ = lag;
        return this;
    }

    public MultipleResetsLeg withFixingDays(final int fixingDays) {
        this.fixingDays_ = new ArrayList<Integer>();
        this.fixingDays_.add(fixingDays);
        return this;
    }

    public MultipleResetsLeg withFixingDays(final List<Integer> fixingDays) {
        this.fixingDays_ = new ArrayList<Integer>(fixingDays);
        return this;
    }

    public MultipleResetsLeg withGearings(final double gearing) {
        this.gearings_ = new ArrayList<Double>();
        this.gearings_.add(gearing);
        return this;
    }

    public MultipleResetsLeg withGearings(final List<Double> gearings) {
        this.gearings_ = new ArrayList<Double>(gearings);
        return this;
    }

    public MultipleResetsLeg withCouponSpreads(final double spread) {
        this.couponSpreads_ = new ArrayList<Double>();
        this.couponSpreads_.add(spread);
        return this;
    }

    public MultipleResetsLeg withCouponSpreads(final List<Double> spreads) {
        this.couponSpreads_ = new ArrayList<Double>(spreads);
        return this;
    }

    public MultipleResetsLeg withRateSpreads(final double spread) {
        this.rateSpreads_ = new ArrayList<Double>();
        this.rateSpreads_.add(spread);
        return this;
    }

    public MultipleResetsLeg withRateSpreads(final List<Double> spreads) {
        this.rateSpreads_ = new ArrayList<Double>(spreads);
        return this;
    }

    public MultipleResetsLeg withAveragingMethod(final RateAveraging.Type method) {
        this.averagingMethod_ = method;
        return this;
    }

    public MultipleResetsLeg withExCouponPeriod(final Period period,
                                                final Calendar cal,
                                                final BusinessDayConvention convention,
                                                final boolean endOfMonth) {
        this.exCouponPeriod_ = period;
        this.exCouponCalendar_ = cal;
        this.exCouponAdjustment_ = convention;
        this.exCouponEndOfMonth_ = endOfMonth;
        return this;
    }

    /** Convenience overload — defaults {@code endOfMonth=false}. */
    public MultipleResetsLeg withExCouponPeriod(final Period period,
                                                final Calendar cal,
                                                final BusinessDayConvention convention) {
        return withExCouponPeriod(period, cal, convention, false);
    }

    /**
     * Build the leg.  Mirrors C++ {@code MultipleResetsLeg::operator Leg() const}.
     */
    public Leg Leg() {
        final Leg cashflows = new Leg();

        final int n = (schedule_.size() - 1) / resetsPerCoupon_;
        QL.require(!notionals_.isEmpty(), "no notional given");
        QL.require(notionals_.size() <= n,
                "too many nominals (" + notionals_.size() + "), only " + n + " required");
        QL.require(gearings_.size() <= n,
                "too many gearings (" + gearings_.size() + "), only " + n + " required");
        QL.require(couponSpreads_.size() <= n,
                "too many coupon spreads (" + couponSpreads_.size() + "), only " + n + " required");
        QL.require(rateSpreads_.size() <= n,
                "too many rate spreads (" + rateSpreads_.size() + "), only " + n + " required");
        QL.require(fixingDays_.size() <= n,
                "too many fixing days (" + fixingDays_.size() + "), only " + n + " required");

        final Calendar legCalendar = schedule_.calendar();
        for (int i = 0; i < n; ++i) {
            final Date start = schedule_.date(i * resetsPerCoupon_);
            final Date end = schedule_.date((i + 1) * resetsPerCoupon_);
            // Build sub-schedule by slicing the full reset schedule between
            // index i*resetsPerCoupon_ and (i+1)*resetsPerCoupon_ inclusive.
            // Equivalent to C++ schedule_.after(start).until(end).
            final List<Date> subDates = new ArrayList<Date>(resetsPerCoupon_ + 1);
            for (int k = i * resetsPerCoupon_; k <= (i + 1) * resetsPerCoupon_; ++k) {
                subDates.add(schedule_.date(k));
            }
            final Schedule subSchedule = new Schedule(subDates, schedule_.calendar(),
                                                     schedule_.businessDayConvention());

            final Date paymentDate = paymentCalendar_.advance(end, paymentLag_, TimeUnit.Days, paymentAdjustment_, false);

            // Phase 5e.5b-CFC-d-203 — compute ex-coupon date and thread it
            // through to the MultipleResetsCoupon ctor (which sets the
            // inherited Coupon.exCouponDate_ field). Mirrors C++
            // multipleresetscoupon.cpp:271-280.
            Date exCouponDate = new Date();
            if (exCouponPeriod_ != null && exCouponPeriod_.length() != 0) {
                final Calendar exCal = (exCouponCalendar_ == null || exCouponCalendar_.empty())
                        ? legCalendar
                        : exCouponCalendar_;
                exCouponDate = exCal.advance(paymentDate,
                                             exCouponPeriod_.negative(),
                                             exCouponAdjustment_,
                                             exCouponEndOfMonth_);
            }

            final double notional = pick(notionals_, i, notionals_.get(notionals_.size() - 1));
            final int couponFixingDays = fixingDays_.isEmpty()
                    ? index_.fixingDays()
                    : pickInt(fixingDays_, i, fixingDays_.get(fixingDays_.size() - 1));
            final double gearing = pick(gearings_, i, 1.0);
            final double couponSpread = pick(couponSpreads_, i, 0.0);
            final double rateSpread = pick(rateSpreads_, i, 0.0);

            cashflows.add(new MultipleResetsCoupon(
                    paymentDate, notional, subSchedule, couponFixingDays, index_,
                    gearing, couponSpread, rateSpread, start, end, paymentDayCounter_,
                    exCouponDate));
        }

        switch (averagingMethod_) {
            case Simple:
                setMultipleResetsPricer(cashflows, new AveragingMultipleResetsPricer());
                break;
            case Compound:
                setMultipleResetsPricer(cashflows, new CompoundingMultipleResetsPricer());
                break;
            default:
                throw new LibraryException("unknown compounding convention (" + averagingMethod_ + ")");
        }

        return cashflows;
    }

    private static void setMultipleResetsPricer(final Leg cashflows,
                                                final MultipleResetsPricer pricer) {
        // PricerSetter's visitor list does not yet include MultipleResetsCoupon
        // (Phase 5d.5-MR delivers the production family; PricerSetter will be
        // extended in 5d.5-MRb if we want generic dispatch). Wire directly.
        for (final CashFlow cf : cashflows) {
            if (cf instanceof MultipleResetsCoupon) {
                ((MultipleResetsCoupon) cf).setPricer(pricer);
            }
        }
    }

    private static double pick(final List<Double> v, final int i, final double dflt) {
        if (v.isEmpty()) return dflt;
        if (i < v.size()) return v.get(i);
        return dflt;
    }

    private static int pickInt(final List<Integer> v, final int i, final int dflt) {
        if (v.isEmpty()) return dflt;
        if (i < v.size()) return v.get(i);
        return dflt;
    }
}
