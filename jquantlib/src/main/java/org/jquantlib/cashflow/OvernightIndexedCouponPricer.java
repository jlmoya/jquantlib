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
 * Port of C++ QuantLib v1.43 {@code ql/cashflows/overnightindexedcouponpricer.hpp/cpp}.
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
        switch (coupon) {
            case final CappedFlooredOvernightIndexedCoupon cf -> {
                final OvernightIndexedCoupon underlying = cf.underlying();
                QL.require(underlying != null,
                        "OvernightIndexedCouponPricer: CappedFlooredOvernightIndexedCoupon underlying coupon not defined");
                coupon_ = underlying;
            }
            case final OvernightIndexedCoupon oc -> coupon_ = oc;
            default -> QL.require(false, "OvernightIndexedCouponPricer: unsupported coupon type");
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

    // Base class does NOT provide swapletPrice / capletPrice / capletRate /
    // floorletPrice / floorletRate. These remain abstract from
    // FloatingRateCouponPricer; each derived pricer decides whether they apply.
    // Mirrors C++ v1.42.1 overnightindexedcouponpricer.hpp where the base
    // OvernightIndexedCouponPricer leaves them pure-virtual and only the
    // CompoundingOvernightIndexedCouponPricer / ArithmeticAveragedOvernightIndexedCouponPricer
    // subclasses install the QL_FAIL "not available" stubs (overnightindexedcouponpricer.hpp:113-117, 155-159).
    //
    // The Black* / Capped-Floored Black* subclasses provide actual Black-formula
    // implementations of capletRate / floorletRate (see BlackOvernightIndexedCouponPricer
    // and CappedFlooredBlackOvernightIndexedCouponPricer).

    /**
     * Helper: determine the number of fixings used up to {@code date}.
     * <p>
     * Mirror of the C++ v1.43 anonymous-namespace helper
     * (ql/cashflows/overnightindexedcouponpricer.cpp:32-37):
     * <pre>
     *   std::lower_bound(interestDates.begin(), interestDates.end()-1, date) - interestDates.begin()
     * </pre>
     * The search range stops one short of the end, so the result never exceeds the number of fixing dates (which is
     * always one less than the number of interest dates). v1.42.1 searched the full range and clamped afterwards, but
     * only when observation shift was on; v1.43 clamps unconditionally.
     */
    protected int determineNumberOfFixings(final List< Date > interestDates, final Date date) {
        final int upperBound = interestDates.size() - 1;
        int n = 0;
        while ( n < upperBound && interestDates.get(n).lt(date) ) {
            ++n;
        }
        return n;
    }
}
