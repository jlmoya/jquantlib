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
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for {@link RangeAccrualFloatersCoupon} pricers.
 *
 * <p>Java port of QuantLib v1.42.1 {@code RangeAccrualPricer}
 * defined in {@code ql/cashflows/rangeaccrual.hpp}.</p>
 *
 * <p>{@link #initialize(FloatingRateCoupon)} caches all the per-coupon
 * inputs (start/end times, observation times, initial Libor fixings, discount factor, accrual). Subclasses implement
 * {@link #swapletPrice()}.</p>
 *
 * @author JQuantLib Phase 5e.7 port
 */
public abstract class RangeAccrualPricer extends FloatingRateCouponPricer {

    protected RangeAccrualFloatersCoupon coupon_;
    protected double startTime_;                    // S
    protected double endTime_;                      // T
    protected double accrualFactor_;                // T-S
    protected List< Double > observationTimeLags_;    // d
    protected List< Double > observationTimes_;       // U
    protected List< Double > initialValues_;
    protected int observationsNo_;
    protected double lowerTrigger_;
    protected double upperTrigger_;
    protected double discount_;
    protected double gearing_;
    protected double spread_;
    protected double spreadLegValue_;

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        QL.require(coupon instanceof RangeAccrualFloatersCoupon, "range-accrual coupon required");
        coupon_ = (RangeAccrualFloatersCoupon) coupon;
        gearing_ = coupon_.gearing();
        spread_ = coupon_.spread();

        final Date paymentDate = coupon_.date();

        QL.require(coupon_.index() instanceof IborIndex, "IborIndex required for range-accrual pricer");
        final IborIndex index = (IborIndex) coupon_.index();
        final Handle< YieldTermStructure > rateCurve = index.termStructure();
        discount_ = rateCurve.currentLink().discount(paymentDate);
        accrualFactor_ = coupon_.accrualPeriod();
        spreadLegValue_ = spread_ * accrualFactor_ * discount_;

        startTime_ = coupon_.startTime();
        endTime_ = coupon_.endTime();
        observationTimes_ = coupon_.observationTimes();
        lowerTrigger_ = coupon_.lowerTrigger();
        upperTrigger_ = coupon_.upperTrigger();
        observationsNo_ = coupon_.observationsNo();

        final List< Date > observationDates = coupon_.observationSchedule().dates();
        QL.require(observationDates.size() == observationsNo_ + 2, "incompatible size of initialValues vector");

        initialValues_ = new ArrayList< Double >(observationDates.size());
        final Calendar calendar = index.fixingCalendar();
        for ( int i = 0; i < observationDates.size(); ++i ) {
            final Date fixingDate = calendar.advance(observationDates.get(i), -coupon_.fixingDays(), TimeUnit.Days);
            initialValues_.add(index.fixing(fixingDate));
        }
    }

    @Override
    public double swapletRate() {
        return swapletPrice() / (accrualFactor_ * discount_);
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        throw new LibraryException("RangeAccrualPricer::capletPrice not implemented");
    }

    @Override
    public double capletRate(final double effectiveCap) {
        throw new LibraryException("RangeAccrualPricer::capletRate not implemented");
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        throw new LibraryException("RangeAccrualPricer::floorletPrice not implemented");
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        throw new LibraryException("RangeAccrualPricer::floorletRate not implemented");
    }
}
