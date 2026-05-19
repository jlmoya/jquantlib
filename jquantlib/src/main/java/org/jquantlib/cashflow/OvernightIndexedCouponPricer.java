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
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * Base pricer for {@link OvernightIndexedCoupon}.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/overnightindexedcouponpricer.hpp/cpp}.
 *
 * @author JQuantLib migration team
 * @category cashflows
 */
public abstract class OvernightIndexedCouponPricer extends FloatingRateCouponPricer {

    protected OvernightIndexedCoupon coupon_;
    protected Handle< OptionletVolatilityStructure > capletVol_;
    protected boolean effectiveVolatilityInput_ = false;
    /** Set by Black-style pricers after each {@link #capletRate(double, boolean)} call. */
    protected double effectiveCapletVolatility_ = Constants.NULL_REAL;
    /** Set by Black-style pricers after each {@link #floorletRate(double, boolean)} call. */
    protected double effectiveFloorletVolatility_ = Constants.NULL_REAL;

    protected OvernightIndexedCouponPricer() {
        this(new Handle< OptionletVolatilityStructure >(), false);
    }

    protected OvernightIndexedCouponPricer(final Handle< OptionletVolatilityStructure > capletVol,
            final boolean effectiveVolatilityInput) {
        this.capletVol_ = capletVol;
        this.effectiveVolatilityInput_ = effectiveVolatilityInput;
        if ( capletVol_ != null ) {
            capletVol_.addObserver(this);
        }
    }

    public Handle< OptionletVolatilityStructure > capletVolatility() {
        return capletVol_;
    }

    public void setCapletVolatility(final Handle< OptionletVolatilityStructure > v) {
        if ( capletVol_ != null ) {
            capletVol_.deleteObserver(this);
        }
        capletVol_ = v;
        if ( capletVol_ != null ) {
            capletVol_.addObserver(this);
        }
        update();
    }

    public boolean effectiveVolatilityInput() {
        return effectiveVolatilityInput_;
    }

    public double effectiveCapletVolatility() {
        return effectiveCapletVolatility_;
    }

    public double effectiveFloorletVolatility() {
        return effectiveFloorletVolatility_;
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        // C++ initialize() unwraps a CappedFlooredOvernightIndexedCoupon to its underlying.
        if ( coupon instanceof CappedFlooredOvernightIndexedCoupon ) {
            final OvernightIndexedCoupon underlying = ((CappedFlooredOvernightIndexedCoupon) coupon).underlying();
            QL.require(underlying != null,
                    "OvernightIndexedCouponPricer: CappedFlooredOvernightIndexedCoupon underlying coupon not defined");
            coupon_ = underlying;
        } else if ( coupon instanceof OvernightIndexedCoupon ) {
            coupon_ = (OvernightIndexedCoupon) coupon;
        } else {
            QL.require(false, "OvernightIndexedCouponPricer: unsupported coupon type");
        }
    }

    public abstract /*Rate*/ double averageRate(Date date);

    /**
     * Black-style cap pricer entry point with explicit daily/global selection. Default is to forward to
     * {@link #capletRate(double)}; Black variants override.
     */
    public double capletRate(final double effectiveCap, final boolean dailyCapFloor) {
        return capletRate(effectiveCap);
    }

    public double floorletRate(final double effectiveFloor, final boolean dailyCapFloor) {
        return floorletRate(effectiveFloor);
    }

    @Override
    public double swapletPrice() {
        throw new UnsupportedOperationException("swapletPrice not implemented");
    }

    @Override
    public double capletPrice(double effectiveCap) {
        throw new UnsupportedOperationException("capletPrice not implemented");
    }

    @Override
    public double capletRate(double effectiveCap) {
        throw new UnsupportedOperationException("capletRate not implemented");
    }

    @Override
    public double floorletPrice(double effectiveFloor) {
        throw new UnsupportedOperationException("floorletPrice not implemented");
    }

    @Override
    public double floorletRate(double effectiveFloor) {
        throw new UnsupportedOperationException("floorletRate not implemented");
    }

    /**
     * Helper: determine number of fixings used up to {@code date}.
     */
    protected int determineNumberOfFixings(final List< Date > interestDates, final Date date,
            final boolean applyObservationShift) {
        int n = 0;
        for ( final Date d : interestDates ) {
            if ( !d.lt(date) ) {
                break;
            }
            ++n;
        }
        if ( n == interestDates.size() && applyObservationShift ) {
            return n - 1;
        }
        return n;
    }
}
