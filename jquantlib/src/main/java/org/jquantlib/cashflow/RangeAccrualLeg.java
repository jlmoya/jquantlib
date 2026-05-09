/*
 Copyright (C) 2026 The JQuantLib contributors

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
 Copyright (C) 2006, 2007 Giorgio Facchinetti
 Copyright (C) 2006, 2007 Mario Pucci

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/

package org.jquantlib.cashflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;

/**
 * Helper class building a sequence of range-accrual floating-rate coupons.
 *
 * <p>Java port of QuantLib v1.42.1 {@code RangeAccrualLeg}
 * defined in {@code ql/cashflows/rangeaccrual.{hpp,cpp}}.</p>
 *
 * <p>Fluent builder API: configure with {@code withXxx(...)} setters, then
 * call {@link #Leg()} to materialise the leg.</p>
 *
 * @author JQuantLib Phase 5e.7 port
 */
public class RangeAccrualLeg {

    private final Schedule schedule_;
    private final IborIndex index_;
    private List<Double> notionals_ = new ArrayList<Double>();
    private DayCounter paymentDayCounter_;
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.Following;
    private List<Integer> fixingDays_ = new ArrayList<Integer>();
    private List<Double> gearings_ = new ArrayList<Double>();
    private List<Double> spreads_ = new ArrayList<Double>();
    private List<Double> lowerTriggers_ = new ArrayList<Double>();
    private List<Double> upperTriggers_ = new ArrayList<Double>();
    private Period observationTenor_;
    private BusinessDayConvention observationConvention_ = BusinessDayConvention.ModifiedFollowing;

    public RangeAccrualLeg(final Schedule schedule, final IborIndex index) {
        this.schedule_ = schedule;
        this.index_ = index;
    }

    public RangeAccrualLeg withNotionals(final double notional) {
        notionals_ = new ArrayList<Double>(Arrays.asList(notional));
        return this;
    }

    public RangeAccrualLeg withNotionals(final List<Double> notionals) {
        notionals_ = notionals;
        return this;
    }

    public RangeAccrualLeg withPaymentDayCounter(final DayCounter dayCounter) {
        paymentDayCounter_ = dayCounter;
        return this;
    }

    public RangeAccrualLeg withPaymentAdjustment(final BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public RangeAccrualLeg withFixingDays(final int fixingDays) {
        fixingDays_ = new ArrayList<Integer>(Arrays.asList(fixingDays));
        return this;
    }

    public RangeAccrualLeg withFixingDays(final List<Integer> fixingDays) {
        fixingDays_ = fixingDays;
        return this;
    }

    public RangeAccrualLeg withGearings(final double gearing) {
        gearings_ = new ArrayList<Double>(Arrays.asList(gearing));
        return this;
    }

    public RangeAccrualLeg withGearings(final List<Double> gearings) {
        gearings_ = gearings;
        return this;
    }

    public RangeAccrualLeg withSpreads(final double spread) {
        spreads_ = new ArrayList<Double>(Arrays.asList(spread));
        return this;
    }

    public RangeAccrualLeg withSpreads(final List<Double> spreads) {
        spreads_ = spreads;
        return this;
    }

    public RangeAccrualLeg withLowerTriggers(final double trigger) {
        lowerTriggers_ = new ArrayList<Double>(Arrays.asList(trigger));
        return this;
    }

    public RangeAccrualLeg withLowerTriggers(final List<Double> triggers) {
        lowerTriggers_ = triggers;
        return this;
    }

    public RangeAccrualLeg withUpperTriggers(final double trigger) {
        upperTriggers_ = new ArrayList<Double>(Arrays.asList(trigger));
        return this;
    }

    public RangeAccrualLeg withUpperTriggers(final List<Double> triggers) {
        upperTriggers_ = triggers;
        return this;
    }

    public RangeAccrualLeg withObservationTenor(final Period tenor) {
        observationTenor_ = tenor;
        return this;
    }

    public RangeAccrualLeg withObservationConvention(final BusinessDayConvention convention) {
        observationConvention_ = convention;
        return this;
    }

    /**
     * Build the leg as a {@link Leg}. Mirrors C++ {@code operator Leg() const}.
     */
    public Leg Leg() {
        QL.require(!notionals_.isEmpty(), "no notional given");

        final int n = schedule_.size() - 1;
        QL.require(notionals_.size() <= n,
                   "too many nominals (" + notionals_.size() + "), only " + n + " required");
        QL.require(fixingDays_.size() <= n,
                   "too many fixingDays (" + fixingDays_.size() + "), only " + n + " required");
        QL.require(gearings_.size() <= n,
                   "too many gearings (" + gearings_.size() + "), only " + n + " required");
        QL.require(spreads_.size() <= n,
                   "too many spreads (" + spreads_.size() + "), only " + n + " required");
        QL.require(lowerTriggers_.size() <= n,
                   "too many lowerTriggers (" + lowerTriggers_.size() + "), only " + n + " required");
        QL.require(upperTriggers_.size() <= n,
                   "too many upperTriggers (" + upperTriggers_.size() + "), only " + n + " required");

        final Leg leg = new Leg();
        // the following is not always correct
        final Calendar calendar = schedule_.calendar();

        for (int i = 0; i < n; ++i) {
            Date refStart = schedule_.at(i);
            Date start = refStart;
            Date refEnd = schedule_.at(i + 1);
            Date end = refEnd;
            final Date paymentDate = calendar.adjust(end, paymentAdjustment_);
            // Schedule.hasIsRegular not available in Java port; emulate with
            // a try/catch around isRegular which throws when full interface
            // not available — preserve C++ semantics where isRegular is only
            // checked when the schedule has the full interface.
            if (i == 0 && hasIsRegular(schedule_) && !schedule_.isRegular(i + 1)) {
                final BusinessDayConvention bdc = schedule_.businessDayConvention();
                refStart = calendar.adjust(end.sub(schedule_.tenor()), bdc);
            }
            if (i == n - 1 && hasIsRegular(schedule_) && !schedule_.isRegular(i + 1)) {
                final BusinessDayConvention bdc = schedule_.businessDayConvention();
                refEnd = calendar.adjust(start.add(schedule_.tenor()), bdc);
            }
            if (getDouble(gearings_, i, 1.0) == 0.0) {
                // fixed coupon — Java FixedRateCoupon arg order:
                // (nominal, paymentDate, rate, dayCounter, accrualStart, accrualEnd, refStart, refEnd)
                leg.add(new FixedRateCoupon(
                    getDouble(notionals_, i, Constants.NULL_REAL),
                    paymentDate,
                    getDouble(spreads_, i, 0.0),
                    paymentDayCounter_,
                    start, end, refStart, refEnd));
            } else {
                // floating coupon — observation sub-schedule is freshly generated.
                final Schedule observationSchedule = new Schedule(
                    start, end,
                    observationTenor_, calendar,
                    observationConvention_,
                    observationConvention_,
                    DateGeneration.Rule.Forward, false);

                leg.add(new RangeAccrualFloatersCoupon(
                    paymentDate,
                    getDouble(notionals_, i, Constants.NULL_REAL),
                    index_,
                    start, end,
                    getInt(fixingDays_, i, 2),
                    paymentDayCounter_,
                    getDouble(gearings_, i, 1.0),
                    getDouble(spreads_, i, 0.0),
                    refStart, refEnd,
                    observationSchedule,
                    getDouble(lowerTriggers_, i, Constants.NULL_RATE),
                    getDouble(upperTriggers_, i, Constants.NULL_RATE)));
            }
        }
        return leg;
    }

    private static boolean hasIsRegular(final Schedule s) {
        // Schedule does not expose hasIsRegular; full interface is only
        // available when the date-generation constructor was used. The probe
        // is a side-effect-free isRegular(0) call wrapped in try/catch.
        try {
            s.isRegular(0);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    private static double getDouble(final List<Double> v, final int i, final double dflt) {
        if (v.isEmpty()) {
            return dflt;
        }
        if (i < v.size()) {
            return v.get(i);
        }
        return v.get(v.size() - 1);
    }

    private static int getInt(final List<Integer> v, final int i, final int dflt) {
        if (v.isEmpty()) {
            return dflt;
        }
        if (i < v.size()) {
            return v.get(i);
        }
        return v.get(v.size() - 1);
    }
}
