/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009 Roland Lichters
 Copyright (C) 2009 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * Helper class building a sequence of overnight-indexed coupons, fluent
 * Java translation of C++ {@code OvernightLeg}.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/cashflows/overnightindexedcoupon.hpp/cpp}
 * {@code OvernightLeg}.
 *
 * @category cashflows
 *
 * @author JQuantLib migration team
 */
public class OvernightLeg {

    private final Schedule schedule_;
    private final OvernightIndex overnightIndex_;
    private List<Double> notionals_ = new ArrayList<Double>();
    private DayCounter paymentDayCounter_ = new DayCounter();
    private Calendar paymentCalendar_;
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.Following;
    private int paymentLag_ = 0;
    private List<Double> gearings_ = new ArrayList<Double>();
    private List<Double> spreads_ = new ArrayList<Double>();
    private boolean telescopicValueDates_ = false;
    private RateAveraging.Type averagingMethod_ = RateAveraging.Type.Compound;
    private int lookbackDays_ = Constants.NULL_NATURAL;
    private int lockoutDays_ = 0;
    private boolean applyObservationShift_ = false;

    public OvernightLeg(final Schedule schedule, final OvernightIndex overnightIndex) {
        QL.require(overnightIndex != null, "no index provided");
        this.schedule_ = schedule;
        this.overnightIndex_ = overnightIndex;
        this.paymentCalendar_ = schedule.calendar();
    }

    public OvernightLeg withNotionals(final double notional) {
        notionals_ = new ArrayList<Double>();
        notionals_.add(notional);
        return this;
    }

    public OvernightLeg withNotionals(final List<Double> notionals) {
        notionals_ = new ArrayList<Double>(notionals);
        return this;
    }

    public OvernightLeg withPaymentDayCounter(final DayCounter dc) {
        paymentDayCounter_ = dc;
        return this;
    }

    public OvernightLeg withPaymentAdjustment(final BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public OvernightLeg withPaymentCalendar(final Calendar cal) {
        paymentCalendar_ = cal;
        return this;
    }

    public OvernightLeg withPaymentLag(final int lag) {
        paymentLag_ = lag;
        return this;
    }

    public OvernightLeg withGearings(final double gearing) {
        gearings_ = new ArrayList<Double>();
        gearings_.add(gearing);
        return this;
    }

    public OvernightLeg withGearings(final List<Double> gearings) {
        gearings_ = new ArrayList<Double>(gearings);
        return this;
    }

    public OvernightLeg withSpreads(final double spread) {
        spreads_ = new ArrayList<Double>();
        spreads_.add(spread);
        return this;
    }

    public OvernightLeg withSpreads(final List<Double> spreads) {
        spreads_ = new ArrayList<Double>(spreads);
        return this;
    }

    public OvernightLeg withTelescopicValueDates(final boolean v) {
        telescopicValueDates_ = v;
        return this;
    }

    public OvernightLeg withAveragingMethod(final RateAveraging.Type avg) {
        averagingMethod_ = avg;
        return this;
    }

    public OvernightLeg withLookbackDays(final int lookbackDays) {
        lookbackDays_ = lookbackDays;
        return this;
    }

    public OvernightLeg withLockoutDays(final int lockoutDays) {
        lockoutDays_ = lockoutDays;
        return this;
    }

    public OvernightLeg withObservationShift(final boolean shift) {
        applyObservationShift_ = shift;
        return this;
    }

    /**
     * Build the leg.
     */
    public Leg leg() {
        QL.require(!notionals_.isEmpty(), "no notional given");
        final List<Date> dates = schedule_.dates();
        final Leg cashflows = new Leg();
        final DayCounter dc = paymentDayCounter_.empty()
                ? overnightIndex_.dayCounter() : paymentDayCounter_;

        for (int i = 1; i < dates.size(); ++i) {
            final Date startDate = dates.get(i - 1);
            final Date endDate = dates.get(i);
            Date paymentDate = paymentCalendar_.adjust(endDate, paymentAdjustment_);
            if (paymentLag_ != 0) {
                paymentDate = paymentCalendar_.advance(paymentDate,
                                                       new org.jquantlib.time.Period(paymentLag_, TimeUnit.Days),
                                                       paymentAdjustment_);
            }
            final double nominal = pickValue(notionals_, i - 1);
            final double gearing = pickValueOrDefault(gearings_, i - 1, 1.0);
            final double spread  = pickValueOrDefault(spreads_, i - 1, 0.0);

            final OvernightIndexedCoupon coupon = new OvernightIndexedCoupon(
                    paymentDate, nominal, startDate, endDate,
                    overnightIndex_, gearing, spread,
                    new Date(), new Date(), dc,
                    telescopicValueDates_, averagingMethod_,
                    lookbackDays_, lockoutDays_, applyObservationShift_,
                    false /* compoundSpreadDaily */);
            cashflows.add(coupon);
        }
        return cashflows;
    }

    private static double pickValue(final List<Double> vec, final int index) {
        if (vec.isEmpty()) {
            throw new org.jquantlib.lang.exceptions.LibraryException(
                "no value provided");
        }
        return vec.get(index >= vec.size() ? vec.size() - 1 : index);
    }

    private static double pickValueOrDefault(final List<Double> vec, final int index,
                                             final double dflt) {
        if (vec.isEmpty()) {
            return dflt;
        }
        return vec.get(index >= vec.size() ? vec.size() - 1 : index);
    }
}
