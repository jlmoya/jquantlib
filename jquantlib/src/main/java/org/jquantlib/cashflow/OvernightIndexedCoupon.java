/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009 Roland Lichters
 Copyright (C) 2009 Ferdinando Ametrano
 Copyright (C) 2014 Peter Caspers
 Copyright (C) 2017 Joseph Jeisman
 Copyright (C) 2017 Fabrice Lecuyer

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
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.time.Date;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Overnight-indexed coupon paying interest based on daily overnight fixings,
 * either compounded or arithmetically averaged.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/cashflows/overnightindexedcoupon.hpp/cpp}
 * {@code OvernightIndexedCoupon}.
 * <p>
 * <b>Scope (Phase 5d.5 MVP):</b> implements the canonical compounding /
 * simple-averaging logic with full daily fixing schedule (no telescopic
 * shortcut). Lookback days, lockout days, and observation-shift parameters
 * are accepted but treated as zero / disabled — an exception is raised on
 * non-default values until those features are ported in a follow-up phase.
 *
 * @category cashflows
 *
 * @author JQuantLib migration team
 */
public class OvernightIndexedCoupon extends FloatingRateCoupon {

    private final List<Date> valueDates_;
    private final List<Date> interestDates_;
    private final List<Date> fixingDates_;
    private final double[] dt_;
    private final int n_;
    private final RateAveraging.Type averagingMethod_;
    private final int lockoutDays_;
    private final boolean applyObservationShift_;
    private final boolean compoundSpreadDaily_;

    /**
     * Full constructor mirroring C++
     * {@code OvernightIndexedCoupon::OvernightIndexedCoupon}.
     */
    public OvernightIndexedCoupon(
            final Date paymentDate,
            final double nominal,
            final Date startDate,
            final Date endDate,
            final OvernightIndex overnightIndex,
            final double gearing,
            final double spread,
            final Date refPeriodStart,
            final Date refPeriodEnd,
            final DayCounter dayCounter,
            final boolean telescopicValueDates,
            final RateAveraging.Type averagingMethod,
            final int lookbackDays,
            final int lockoutDays,
            final boolean applyObservationShift,
            final boolean compoundSpreadDaily) {
        super(paymentDate, nominal, startDate, endDate,
              lookbackDays == Constants.NULL_NATURAL ? overnightIndex.fixingDays() : lookbackDays,
              overnightIndex,
              gearing, spread,
              refPeriodStart, refPeriodEnd,
              dayCounter, false /* isInArrears */);

        this.averagingMethod_ = averagingMethod;
        this.lockoutDays_ = lockoutDays;
        this.applyObservationShift_ = applyObservationShift;
        this.compoundSpreadDaily_ = compoundSpreadDaily;

        QL.require(paymentDate.ge(endDate),
            "Payment date cannot be earlier than accrual end date");

        // Build value-dates schedule on the index calendar (1-day tenor,
        // following BDC, backward generation).
        // Phase 5d.5 MVP: telescopicValueDates ignored — schedule always full.
        final Schedule sch = new MakeSchedule(
                startDate, endDate, new Period(1, TimeUnit.Days),
                overnightIndex.fixingCalendar(),
                overnightIndex.businessDayConvention())
                .backwards()
                .schedule();
        this.valueDates_ = new ArrayList<Date>(sch.dates());

        QL.ensure(valueDates_.size() >= 2, "degenerate schedule");
        this.n_ = valueDates_.size() - 1;

        this.interestDates_ = new ArrayList<Date>(valueDates_);

        // Fixing dates: trivial case — fixingDays==0 means fixing date is
        // the value date itself.
        this.fixingDates_ = new ArrayList<Date>(n_);
        if (fixingDays_ == overnightIndex.fixingDays() && fixingDays_ == 0) {
            for (int i = 0; i < n_; ++i) {
                fixingDates_.add(valueDates_.get(i));
            }
        } else {
            // Lookback path (Phase 5d.5 MVP): defer to follow-up port.
            QL.require(lookbackDays == 0 || lookbackDays == Constants.NULL_NATURAL,
                "Phase 5d.5 MVP: lookback days unsupported in OvernightIndexedCoupon");
            for (int i = 0; i < n_; ++i) {
                fixingDates_.add(valueDates_.get(i));
            }
        }

        // Lockout: Phase 5d.5 MVP defers; emit guard.
        QL.require(lockoutDays_ == 0,
            "Phase 5d.5 MVP: lockout days unsupported in OvernightIndexedCoupon");

        // Accrual fractions per sub-period using the index day counter.
        this.dt_ = new double[n_];
        final DayCounter dc = overnightIndex.dayCounter();
        for (int i = 0; i < n_; ++i) {
            dt_[i] = dc.yearFraction(interestDates_.get(i), interestDates_.get(i + 1));
        }

        switch (averagingMethod) {
        case Simple:
            QL.require(fixingDays_ == overnightIndex.fixingDays()
                       && !applyObservationShift_ && lockoutDays_ == 0,
                "Cannot price an overnight coupon with simple averaging "
                + "with lookback or lockout.");
            // pricer is set explicitly below to keep imports tight
            setPricer(new ArithmeticAveragedOvernightIndexedCouponPricer(false));
            break;
        case Compound:
            setPricer(new CompoundingOvernightIndexedCouponPricer());
            break;
        default:
            throw new org.jquantlib.lang.exceptions.LibraryException(
                "unknown compounding convention");
        }
    }

    /**
     * Convenience constructor with Compound averaging and default
     * gearing/spread.
     */
    public OvernightIndexedCoupon(
            final Date paymentDate,
            final double nominal,
            final Date startDate,
            final Date endDate,
            final OvernightIndex overnightIndex) {
        this(paymentDate, nominal, startDate, endDate, overnightIndex,
             1.0, 0.0, new Date(), new Date(),
             overnightIndex.dayCounter(),
             false, RateAveraging.Type.Compound,
             Constants.NULL_NATURAL, 0, false, false);
    }

    //
    // public inspectors
    //

    public List<Date> fixingDates() {
        return fixingDates_;
    }

    public double[] dt() {
        return dt_;
    }

    public List<Date> valueDates() {
        return valueDates_;
    }

    public List<Date> interestDates() {
        return interestDates_;
    }

    public RateAveraging.Type averagingMethod() {
        return averagingMethod_;
    }

    public int lockoutDays() {
        return lockoutDays_;
    }

    public boolean applyObservationShift() {
        return applyObservationShift_;
    }

    public boolean compoundSpreadDaily() {
        return compoundSpreadDaily_;
    }

    public int n() {
        return n_;
    }

    public OvernightIndex overnightIndex() {
        return (OvernightIndex) index_;
    }

    public List<Double> indexFixings() {
        final List<Double> out = new ArrayList<Double>(n_);
        for (int i = 0; i < n_; ++i) {
            out.add(index_.fixing(fixingDates_.get(i)));
        }
        return out;
    }

    public boolean canApplyTelescopicFormula() {
        return fixingDays_ == ((OvernightIndex) index_).fixingDays()
                || (applyObservationShift_ && ((OvernightIndex) index_).fixingDays() == 0);
    }

    //
    // FloatingRateCoupon overrides
    //

    @Override
    public Date fixingDate() {
        return fixingDates_.get(fixingDates_.size() - 1);
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<OvernightIndexedCoupon> v =
            (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
