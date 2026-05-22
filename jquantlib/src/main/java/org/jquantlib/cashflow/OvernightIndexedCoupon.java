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

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.time.*;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Overnight-indexed coupon paying interest based on daily overnight fixings, either compounded or arithmetically
 * averaged.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/overnightindexedcoupon.hpp/cpp} {@code OvernightIndexedCoupon}.
 * <p>
 * <b>Phase 5e.5b-CFC-d-107:</b> production port of the lookback /
 * lockout / observation-shift / telescopic-value-dates machinery.
 *
 * @author JQuantLib migration team
 * @category cashflows
 */
public class OvernightIndexedCoupon extends FloatingRateCoupon {

    private final List< Date > valueDates_;
    private final List< Date > interestDates_;
    private final List< Date > fixingDates_;
    private final double[] dt_;
    private final int n_;
    private final RateAveraging.Type averagingMethod_;
    private final int lockoutDays_;
    private final boolean applyObservationShift_;
    private final boolean compoundSpreadDaily_;
    private final Date rateComputationStartDate_;
    private final Date rateComputationEndDate_;

    /**
     * Full constructor mirroring C++ {@code OvernightIndexedCoupon::OvernightIndexedCoupon}.
     */
    public OvernightIndexedCoupon(final Date paymentDate, final double nominal, final Date startDate,
            final Date endDate, final OvernightIndex overnightIndex, final double gearing, final double spread,
            final Date refPeriodStart, final Date refPeriodEnd, final DayCounter dayCounter,
            final boolean telescopicValueDates, final RateAveraging.Type averagingMethod, final int lookbackDays,
            final int lockoutDays, final boolean applyObservationShift, final boolean compoundSpreadDaily) {
        this(paymentDate, nominal, startDate, endDate, overnightIndex, gearing, spread, refPeriodStart, refPeriodEnd,
                dayCounter, telescopicValueDates, averagingMethod, lookbackDays, lockoutDays, applyObservationShift,
                compoundSpreadDaily, new Date(), new Date());
    }

    /**
     * Full constructor including rate-computation start/end dates, mirroring the C++ v1.42.1 ctor signature
     * (overnightindexedcoupon.hpp:57-75).
     */
    public OvernightIndexedCoupon(final Date paymentDate, final double nominal, final Date startDate,
            final Date endDate, final OvernightIndex overnightIndex, final double gearing, final double spread,
            final Date refPeriodStart, final Date refPeriodEnd, final DayCounter dayCounter,
            final boolean telescopicValueDates, final RateAveraging.Type averagingMethod, final int lookbackDays,
            final int lockoutDays, final boolean applyObservationShift, final boolean compoundSpreadDaily,
            final Date rateComputationStartDate, final Date rateComputationEndDate) {
        super(paymentDate, nominal, startDate, endDate,
                lookbackDays == Constants.NULL_NATURAL ? overnightIndex.fixingDays() : lookbackDays, overnightIndex,
                gearing, spread, refPeriodStart, refPeriodEnd, dayCounter, false /* isInArrears */);

        this.averagingMethod_ = averagingMethod;
        this.lockoutDays_ = lockoutDays;
        this.applyObservationShift_ = applyObservationShift;
        this.compoundSpreadDaily_ = compoundSpreadDaily;
        this.rateComputationStartDate_ = rateComputationStartDate;
        this.rateComputationEndDate_ = rateComputationEndDate;

        QL.require(paymentDate.ge(endDate), "Payment date cannot be earlier than accrual end date");

        // C++ overnightindexedcoupon.cpp:85-91: valueStart/valueEnd are
        // computed (rateComputation overrides plus lookback shift) but in
        // upstream C++ they are then unused in the schedule build, which
        // uses the original startDate/endDate. We omit them here too — they
        // would be dead variables — and apply the lookback shift later via
        // applyLookbackPeriod() per-iteration.

        // Telescopic-value-dates guard. With lookback applied (fixingDays_
        // differs from index.fixingDays()) telescopic value dates cannot be
        // used — unless observation shift is on AND the index has no fixing
        // delay (canApplyTelescopicFormula() captures this rule).
        QL.require(canApplyTelescopicFormula() || !telescopicValueDates,
                "Telescopic formula cannot be applied for a coupon with lookback.");

        // Build the value-dates schedule on the index calendar (1-day tenor,
        // following BDC, backward generation). If telescopic-value-dates is
        // on, only build the front stub up to max(eval, start) + 7 business
        // days (C++ overnightindexedcoupon.cpp:107-114).
        Date tmpEndDate = endDate;
        if ( telescopicValueDates ) {
            final Date evalDate = new Settings().evaluationDate();
            tmpEndDate = overnightIndex.fixingCalendar()
                    .advance(Date.max(startDate, evalDate), 7, TimeUnit.Days, BusinessDayConvention.Following, false);
            tmpEndDate = Date.min(tmpEndDate, endDate);
        }

        final Schedule sch = new MakeSchedule(startDate, tmpEndDate, new Period(1, TimeUnit.Days),
                overnightIndex.fixingCalendar(), overnightIndex.businessDayConvention()).backwards().schedule();
        this.valueDates_ = new ArrayList<>(sch.dates());

        // C++ overnightindexedcoupon.cpp:126-140 — if telescopic AND lockout
        // is set, ensure the lockout dates are covered in the back stub.
        if ( telescopicValueDates ) {
            final Date backStop = overnightIndex.fixingCalendar()
                    .adjust(endDate, overnightIndex.businessDayConvention());
            Date tmpLockoutDate = overnightIndex.fixingCalendar()
                    .advance(endDate, -Math.max(lockoutDays_, 1), TimeUnit.Days, BusinessDayConvention.Preceding,
                            false);
            while ( tmpLockoutDate.le(backStop) ) {
                if ( tmpLockoutDate.gt(valueDates_.get(valueDates_.size() - 1)) ) {
                    valueDates_.add(tmpLockoutDate);
                }
                tmpLockoutDate = overnightIndex.fixingCalendar()
                        .advance(tmpLockoutDate, 1, TimeUnit.Days, BusinessDayConvention.Following, false);
            }
        }

        QL.ensure(valueDates_.size() >= 2, "degenerate schedule");
        this.n_ = valueDates_.size() - 1;

        this.interestDates_ = new ArrayList<>(valueDates_);

        // Fixing dates: when fixingDays_ matches the index default and equals
        // zero, fixing date is the value date itself. Otherwise apply the
        // lookback shift (with optional observation-shift correction on
        // interest dates) — C++ overnightindexedcoupon.cpp:148-178.
        this.fixingDates_ = new ArrayList<>(n_);
        // Pre-fill so we can index-assign below.
        for ( int i = 0; i < n_; ++i ) {
            fixingDates_.add(null);
        }
        if ( fixingDays_ == overnightIndex.fixingDays() && fixingDays_ == 0 ) {
            for ( int i = 0; i < n_; ++i ) {
                fixingDates_.set(i, valueDates_.get(i));
            }
        } else {
            for ( int i = 0; i <= n_; ++i ) {
                final Date tmp = applyLookbackPeriod(overnightIndex, valueDates_.get(i), fixingDays_);
                if ( i < n_ ) {
                    fixingDates_.set(i, tmp);
                }
                if ( applyObservationShift_ ) {
                    // observation-shift: interest dates align with the
                    // lookback-shifted fixing dates.
                    interestDates_.set(i, tmp);
                }
                if ( fixingDays_ != overnightIndex.fixingDays() ) {
                    // Lookback without observation shift: correct value
                    // dates so they match a deposit-instrument value date
                    // (avoids convexity adjustments in the forward
                    // projection).
                    valueDates_.set(i, overnightIndex.valueDate(tmp));
                }
            }
        }

        // Lockout: freeze the last `lockoutDays_` fixing dates to the
        // fixing date observed `lockoutDays_` days before the period end.
        // C++ overnightindexedcoupon.cpp:181-187.
        if ( lockoutDays_ != 0 ) {
            QL.require(lockoutDays_ > 0 && lockoutDays_ < n_,
                    "Lockout period cannot be negative or exceed the number of fixing days.");
            final Date lockoutDate = fixingDates_.get(n_ - 1 - lockoutDays_);
            for ( int i = n_ - 1; i > n_ - 1 - lockoutDays_; --i ) {
                fixingDates_.set(i, lockoutDate);
            }
        }

        // Accrual fractions per sub-period using the index day counter.
        this.dt_ = new double[n_];
        final DayCounter dc = overnightIndex.dayCounter();
        for ( int i = 0; i < n_; ++i ) {
            dt_[i] = dc.yearFraction(interestDates_.get(i), interestDates_.get(i + 1));
        }

        switch ( averagingMethod ) {
        case Simple:
            QL.require(fixingDays_ == overnightIndex.fixingDays() && !applyObservationShift_ && lockoutDays_ == 0,
                    "Cannot price an overnight coupon with simple averaging " + "with lookback or lockout.");
            setPricer(new ArithmeticAveragedOvernightIndexedCouponPricer(telescopicValueDates));
            break;
        case Compound:
            setPricer(new CompoundingOvernightIndexedCouponPricer());
            break;
        default:
            throw new org.jquantlib.lang.exceptions.LibraryException("unknown compounding convention");
        }
    }

    /**
     * Convenience constructor with Compound averaging and default gearing/spread.
     */
    public OvernightIndexedCoupon(final Date paymentDate, final double nominal, final Date startDate,
            final Date endDate, final OvernightIndex overnightIndex) {
        this(paymentDate, nominal, startDate, endDate, overnightIndex, 1.0, 0.0, new Date(), new Date(),
                overnightIndex.dayCounter(), false, RateAveraging.Type.Compound, Constants.NULL_NATURAL, 0, false,
                false);
    }

    /**
     * Apply a (positive) {@code lookbackDays} shift to {@code valueDate} on the index's fixing calendar. Mirrors C++
     * anonymous-namespace {@code applyLookbackPeriod} (overnightindexedcoupon.cpp:42-47): a straight
     * {@code calendar.advance(valueDate, -lookbackDays, Days)} with the default Following BDC.
     */
    private static Date applyLookbackPeriod(final OvernightIndex index, final Date valueDate, final int lookbackDays) {
        return index.fixingCalendar().advance(valueDate, -lookbackDays, TimeUnit.Days);
    }

    //
    // public inspectors
    //

    public List< Date > fixingDates() {
        return fixingDates_;
    }

    public double[] dt() {
        return dt_;
    }

    public List< Date > valueDates() {
        return valueDates_;
    }

    public List< Date > interestDates() {
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

    public Date rateComputationStartDate() {
        return rateComputationStartDate_;
    }

    public Date rateComputationEndDate() {
        return rateComputationEndDate_;
    }

    public int n() {
        return n_;
    }

    public OvernightIndex overnightIndex() {
        return (OvernightIndex) index_;
    }

    public List< Double > indexFixings() {
        final List< Double > out = new ArrayList<>(n_);
        for ( int i = 0; i < n_; ++i ) {
            out.add(index_.fixing(fixingDates_.get(i)));
        }
        return out;
    }

    public boolean canApplyTelescopicFormula() {
        return fixingDays_ == index_.fixingDays() || (applyObservationShift_
                && index_.fixingDays() == 0);
    }

    //
    // FloatingRateCoupon overrides
    //

    @Override
    public Date fixingDate() {
        return fixingDates_.get(fixingDates_.size() - 1);
    }

    /**
     * Compounded accrued amount truncated to the sub-period {@code [accrualStartDate, min(d, accrualEndDate)]}.
     * <p>
     * Mirror of C++ {@code OvernightIndexedCoupon::accruedAmount(d)}
     * (ql/cashflows/overnightindexedcoupon.cpp:210-220):
     * <pre>
     *   if (d &lt;= accrualStartDate || d &gt; paymentDate) return 0.0;
     *   if (tradingExCoupon(d))
     *       return nominal * averageRate(d) * accruedPeriod(d);
     *   else
     *       return nominal * averageRate(min(d, accrualEndDate)) * accruedPeriod(d);
     * </pre>
     * <p>
     * Overrides the generic {@link FloatingRateCoupon#accruedAmount(Date)} (which uses
     * {@code rate() * yearFraction[start, min(d,end)]}) to compute the compounded rate over the truncated
     * {@code [start, d]} sub-period via {@link CompoundingOvernightIndexedCouponPricer#averageRate(Date)}.
     * <p>
     * {@code accruedPeriod(d)} is computed inline (Java {@link Coupon} does not yet expose a
     * {@code accruedPeriod(Date)} method); the semantics mirror C++ {@code Coupon::accruedPeriod(d)}
     * (ql/cashflows/coupon.cpp:57-69).
     */
    @Override
    public double accruedAmount(final Date d) {
        if ( d.le(accrualStartDate_) || d.gt(paymentDate_) ) {
            // out of coupon range
            return 0.0;
        }
        final double accruedPeriod;
        if ( tradingExCoupon(d) ) {
            accruedPeriod = -dayCounter().yearFraction(d, Date.max(d, accrualEndDate_), refPeriodStart_, refPeriodEnd_);
            return nominal() * averageRate(d) * accruedPeriod;
        } else {
            accruedPeriod = dayCounter().yearFraction(accrualStartDate_, Date.min(d, accrualEndDate_), refPeriodStart_,
                    refPeriodEnd_);
            // usual case: compounded rate computed over [start, min(d, end)]
            return nominal() * averageRate(Date.min(d, accrualEndDate_)) * accruedPeriod;
        }
    }

    /**
     * Compounded (or arithmetic) average rate over {@code [accrualStartDate, d]} including spread and gearing.
     * <p>
     * Mirror of C++ {@code OvernightIndexedCoupon::averageRate(d)} (ql/cashflows/overnightindexedcoupon.cpp:222-230) —
     * delegates to the pricer's {@code averageRate(d)} when the pricer is an {@link OvernightIndexedCouponPricer},
     * otherwise falls back to {@link FloatingRateCouponPricer#swapletRate()}.
     */
    public double averageRate(final Date d) {
        QL.require(pricer_ != null, "pricer not set");
        pricer_.initialize(this);
        if ( pricer_ instanceof OvernightIndexedCouponPricer ) {
            return ((OvernightIndexedCouponPricer) pricer_).averageRate(d);
        }
        return pricer_.swapletRate();
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< OvernightIndexedCoupon > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
