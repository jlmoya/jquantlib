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
import org.jquantlib.math.Rounding;
import org.jquantlib.time.*;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Overnight-indexed coupon paying interest based on daily overnight fixings, either compounded or arithmetically
 * averaged.
 * <p>
 * Port of C++ QuantLib v1.43 {@code ql/cashflows/overnightindexedcoupon.hpp/cpp} {@code OvernightIndexedCoupon}.
 * <p>
 * The v1.43 rework: the value dates come from {@code Calendar.businessDayList} rather than a daily
 * {@code MakeSchedule}; both the schedule and the telescopic front stub are anchored on the rate-computation dates;
 * {@code interestDates} keeps the rate-computation dates at its two ends so a period boundary landing on a fixing
 * holiday still accrues to that boundary; observation shift drives {@code dt} off the value dates instead of
 * rewriting {@code interestDates}; and the constructor gained an ex-coupon date, an optional rate-rounding
 * precision, and a {@code startDate < endDate} precondition.
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
     * Optional rounding precision applied to the coupon rate in {@link #amount()}. {@code null} means "no rounding",
     * mirroring C++ {@code ext::optional<Integer> roundingPrecision_} (overnightindexedcoupon.hpp:143).
     */
    private final Integer roundingPrecision_;

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
     * Constructor including rate-computation start/end dates, without an ex-coupon date or rate rounding.
     */
    public OvernightIndexedCoupon(final Date paymentDate, final double nominal, final Date startDate,
            final Date endDate, final OvernightIndex overnightIndex, final double gearing, final double spread,
            final Date refPeriodStart, final Date refPeriodEnd, final DayCounter dayCounter,
            final boolean telescopicValueDates, final RateAveraging.Type averagingMethod, final int lookbackDays,
            final int lockoutDays, final boolean applyObservationShift, final boolean compoundSpreadDaily,
            final Date rateComputationStartDate, final Date rateComputationEndDate) {
        this(paymentDate, nominal, startDate, endDate, overnightIndex, gearing, spread, refPeriodStart, refPeriodEnd,
                dayCounter, telescopicValueDates, averagingMethod, lookbackDays, lockoutDays, applyObservationShift,
                compoundSpreadDaily, rateComputationStartDate, rateComputationEndDate, new Date(), null);
    }

    /**
     * Full constructor mirroring the C++ v1.43 ctor signature (overnightindexedcoupon.hpp:57-78), including the
     * ex-coupon date and the optional rate-rounding precision.
     */
    public OvernightIndexedCoupon(final Date paymentDate, final double nominal, final Date startDate,
            final Date endDate, final OvernightIndex overnightIndex, final double gearing, final double spread,
            final Date refPeriodStart, final Date refPeriodEnd, final DayCounter dayCounter,
            final boolean telescopicValueDates, final RateAveraging.Type averagingMethod, final int lookbackDays,
            final int lockoutDays, final boolean applyObservationShift, final boolean compoundSpreadDaily,
            final Date rateComputationStartDate, final Date rateComputationEndDate, final Date exCouponDate,
            final Integer roundingPrecision) {
        super(paymentDate, nominal, startDate, endDate,
                lookbackDays == Constants.NULL_NATURAL ? overnightIndex.fixingDays() : lookbackDays, overnightIndex,
                gearing, spread, refPeriodStart, refPeriodEnd, dayCounter, false /* isInArrears */, exCouponDate);

        this.averagingMethod_ = averagingMethod;
        this.lockoutDays_ = lockoutDays;
        this.applyObservationShift_ = applyObservationShift;
        this.compoundSpreadDaily_ = compoundSpreadDaily;
        this.rateComputationStartDate_ = rateComputationStartDate;
        this.rateComputationEndDate_ = rateComputationEndDate;
        this.roundingPrecision_ = roundingPrecision;

        // ctor guards prevent construction of an object with illogically ordered dates
        // (C++ overnightindexedcoupon.cpp:86-89).
        QL.require(startDate.lt(endDate), "startDate must be less than endDate");
        QL.require(paymentDate.ge(endDate), "Payment date cannot be earlier than accrual end date");

        // C++ overnightindexedcoupon.cpp:91-93: rate-computation dates override the
        // accrual dates for everything schedule-related.
        final Date rateCalcStartDate = rateComputationStartDate_.isNull() ? startDate : rateComputationStartDate_;
        final Date rateCalcEndDate = rateComputationEndDate_.isNull() ? endDate : rateComputationEndDate_;

        // Telescopic-value-dates guard. With lookback applied (fixingDays_
        // differs from index.fixingDays()) telescopic value dates cannot be
        // used — unless observation shift is on AND the index has no fixing
        // delay (canApplyTelescopicFormula() captures this rule).
        QL.require(canApplyTelescopicFormula() || !telescopicValueDates,
                "Telescopic formula cannot be applied for a coupon with lookback.");

        // Value dates. If telescopic-value-dates is on, only build the front stub,
        // running from rateCalcStartDate to
        // min(max(rateCalcStartDate, evalDate) + 7bd, rateCalcEndDate)
        // (C++ overnightindexedcoupon.cpp:106-118).
        final Calendar fixingCal = overnightIndex.fixingCalendar();
        Date tmpEndDate = rateCalcEndDate;
        if ( telescopicValueDates ) {
            final Date evalDate = new Settings().evaluationDate();
            tmpEndDate = fixingCal.advance(Date.max(rateCalcStartDate, evalDate), 7, TimeUnit.Days,
                    BusinessDayConvention.Following, false);
            tmpEndDate = Date.min(tmpEndDate, rateCalcEndDate);
        }
        this.valueDates_ = new ArrayList<>(fixingCal.businessDayList(
                fixingCal.adjust(rateCalcStartDate, BusinessDayConvention.Preceding),
                fixingCal.adjust(tmpEndDate, BusinessDayConvention.Following)));

        // C++ overnightindexedcoupon.cpp:120-133 — if telescopic, extend the back stub
        // so that the (possible) lockout period is covered by the value dates.
        if ( telescopicValueDates ) {
            final Date backStop = fixingCal.adjust(rateCalcEndDate, BusinessDayConvention.Following);
            final Date tmpLockoutDate = fixingCal.advance(rateCalcEndDate, -Math.max(lockoutDays_, 1), TimeUnit.Days);
            Date nextValueDate = tmpLockoutDate.gt(valueDates_.get(valueDates_.size() - 1))
                    ? tmpLockoutDate
                    : fixingCal.advance(valueDates_.get(valueDates_.size() - 1), 1, TimeUnit.Days);
            while ( nextValueDate.le(backStop) ) {
                valueDates_.add(nextValueDate);
                nextValueDate = fixingCal.advance(nextValueDate, 1, TimeUnit.Days);
            }
        }

        QL.ensure(valueDates_.size() >= 2, "degenerate schedule");
        this.n_ = valueDates_.size() - 1;

        // Interest dates equal the value dates except at the two ends, where the
        // rate-computation (or accrual) dates are used verbatim — this is what makes
        // interest accrue over the true period even when an end lands on a fixing
        // holiday (C++ overnightindexedcoupon.cpp:137-140).
        this.interestDates_ = new ArrayList<>(valueDates_);
        interestDates_.set(0, rateCalcStartDate);
        interestDates_.set(n_, rateCalcEndDate);

        // Fixing dates: when fixingDays_ matches the index default and equals
        // zero, fixing date is the value date itself. Otherwise apply the
        // lookback shift — C++ overnightindexedcoupon.cpp:142-163.
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
                if ( fixingDays_ != overnightIndex.fixingDays() ) {
                    // Lookback: correct value dates so they match a
                    // deposit-instrument value date (avoids convexity
                    // adjustments in the forward projection).
                    valueDates_.set(i, overnightIndex.valueDate(tmp));
                }
            }
        }

        // Lockout: freeze the last `lockoutDays_` fixing dates to the
        // fixing date observed `lockoutDays_` days before the period end.
        // C++ overnightindexedcoupon.cpp:165-171.
        if ( lockoutDays_ != 0 ) {
            QL.require(lockoutDays_ > 0 && lockoutDays_ < n_,
                    "Lockout period cannot be negative or exceed the number of fixing days.");
            final Date lockoutDate = fixingDates_.get(n_ - 1 - lockoutDays_);
            for ( int i = n_ - lockoutDays_; i < n_; ++i ) {
                fixingDates_.set(i, lockoutDate);
            }
        }

        // Accrual (compounding) periods, using the index day counter. With observation
        // shift AND a lookback the interest accrues over the *observation* (value) dates
        // rather than the interest dates — C++ overnightindexedcoupon.cpp:173-178.
        // NOTE: `lookbackDays` is the raw ctor argument, so the Null sentinel
        // (Constants.NULL_NATURAL == Integer.MAX_VALUE, matching C++ Null<Natural>() ==
        // INT_MAX) compares greater than zero exactly as it does in C++.
        this.dt_ = new double[n_];
        final DayCounter dc = overnightIndex.dayCounter();
        final List< Date > accrualDates = (applyObservationShift_ && lookbackDays > 0) ? valueDates_ : interestDates_;
        for ( int i = 0; i < n_; ++i ) {
            dt_[i] = dc.yearFraction(accrualDates.get(i), accrualDates.get(i + 1));
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

    /**
     * Rounding precision applied to the rate in {@link #amount()}, or {@code null} when no rounding is configured.
     * Mirror of the C++ {@code ext::optional<Integer> roundingPrecision_} member.
     */
    public Integer roundingPrecision() {
        return roundingPrecision_;
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
     */
    @Override
    public double accruedAmount(final Date d) {
        if ( d.le(accrualStartDate_) || d.gt(paymentDate_) ) {
            // out of coupon range
            return 0.0;
        } else if ( tradingExCoupon(d) ) {
            return nominal() * averageRate(d) * accruedPeriod(d);
        } else {
            // usual case: compounded rate computed over [start, min(d, end)]
            return nominal() * averageRate(Date.min(d, accrualEndDate_)) * accruedPeriod(d);
        }
    }

    /**
     * Coupon amount, with the rate optionally rounded to {@link #roundingPrecision()} decimal places first.
     * <p>
     * Mirror of C++ {@code OvernightIndexedCoupon::amount()} (ql/cashflows/overnightindexedcoupon.cpp:208-216), new in
     * v1.43.
     */
    @Override
    public double amount() {
        double r = rate();
        if ( roundingPrecision_ != null ) {
            r = new Rounding.ClosestRounding(roundingPrecision_.intValue()).operator(r);
        }
        return r * accrualPeriod() * nominal();
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
