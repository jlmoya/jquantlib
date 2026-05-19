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

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Range-accrual floating-rate coupon.
 *
 * <p>Java port of QuantLib v1.42.1 {@code RangeAccrualFloatersCoupon}
 * defined in {@code ql/cashflows/rangeaccrual.hpp}.</p>
 *
 * <p>Tracks lower/upper triggers and an observation schedule; the actual
 * range-accrual price is delegated to a {@link RangeAccrualPricer}.</p>
 *
 * @author JQuantLib Phase 5e.7 port
 */
public class RangeAccrualFloatersCoupon extends FloatingRateCoupon {

    private final double startTime_;
    private final double endTime_;

    private final Schedule observationSchedule_;
    private final List< Date > observationDates_;
    private final List< Double > observationTimes_;
    private final int observationsNo_;

    private final double lowerTrigger_;
    private final double upperTrigger_;

    public RangeAccrualFloatersCoupon(final Date paymentDate, final double nominal, final IborIndex index,
            final Date startDate, final Date endDate, final int fixingDays, final DayCounter dayCounter,
            final double gearing, final double spread, final Date refPeriodStart, final Date refPeriodEnd,
            final Schedule observationSchedule, final double lowerTrigger, final double upperTrigger) {
        super(paymentDate, nominal, startDate, endDate, fixingDays, index, gearing, spread, refPeriodStart,
                refPeriodEnd, dayCounter, false);

        this.observationSchedule_ = observationSchedule;
        this.lowerTrigger_ = lowerTrigger;
        this.upperTrigger_ = upperTrigger;

        QL.require(lowerTrigger_ < upperTrigger, "lowerTrigger_>=upperTrigger");
        QL.require(observationSchedule.startDate().eq(startDate), "incompatible start date");
        QL.require(observationSchedule.endDate().eq(endDate), "incompatible end date");

        // Build observation dates: drop start and end. C++ uses Schedule::dates,
        // pop_back, erase(begin) — match that semantics exactly.
        final List< Date > rawDates = observationSchedule.dates();
        this.observationDates_ = new ArrayList< Date >(rawDates.size());
        // Skip first (start) and last (end).
        for ( int i = 1; i < rawDates.size() - 1; ++i ) {
            observationDates_.add(rawDates.get(i));
        }
        this.observationsNo_ = observationDates_.size();

        final Handle< YieldTermStructure > rateCurve = index.termStructure();
        final Date referenceDate = rateCurve.currentLink().referenceDate();

        this.startTime_ = dayCounter.yearFraction(referenceDate, startDate);
        this.endTime_ = dayCounter.yearFraction(referenceDate, endDate);

        this.observationTimes_ = new ArrayList< Double >(observationsNo_);
        for ( int i = 0; i < observationsNo_; ++i ) {
            observationTimes_.add(dayCounter.yearFraction(referenceDate, observationDates_.get(i)));
        }
    }

    public double startTime() {
        return startTime_;
    }

    public double endTime() {
        return endTime_;
    }

    public double lowerTrigger() {
        return lowerTrigger_;
    }

    public double upperTrigger() {
        return upperTrigger_;
    }

    public int observationsNo() {
        return observationsNo_;
    }

    public List< Date > observationDates() {
        return observationDates_;
    }

    public List< Double > observationTimes() {
        return observationTimes_;
    }

    public Schedule observationSchedule() {
        return observationSchedule_;
    }

    /**
     * Mirrors C++ {@code priceWithoutOptionality} —
     * {@code accrualPeriod() * (gearing*indexFixing()+spread) * nominal * discountingCurve->discount(date())}.
     */
    public double priceWithoutOptionality(final Handle< YieldTermStructure > discountingCurve) {
        return accrualPeriod() * (gearing_ * indexFixing() + spread_) * nominal() * discountingCurve.currentLink()
                .discount(date());
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        @SuppressWarnings( "unchecked" )
        final Visitor< RangeAccrualFloatersCoupon > v = (pv != null)
                ? (Visitor< RangeAccrualFloatersCoupon >) pv.visitor(this.getClass())
                : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
