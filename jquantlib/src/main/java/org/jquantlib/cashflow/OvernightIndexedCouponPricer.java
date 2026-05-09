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

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Base pricer for {@link OvernightIndexedCoupon}.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/cashflows/overnightindexedcouponpricer.hpp/cpp}.
 *
 * @category cashflows
 *
 * @author JQuantLib migration team
 */
public abstract class OvernightIndexedCouponPricer extends FloatingRateCouponPricer {

    protected OvernightIndexedCoupon coupon_;

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        QL.require(coupon instanceof OvernightIndexedCoupon,
            "OvernightIndexedCouponPricer: coupon is not an OvernightIndexedCoupon");
        coupon_ = (OvernightIndexedCoupon) coupon;
    }

    public abstract /*Rate*/ double averageRate(Date date);

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
    protected int determineNumberOfFixings(final List<Date> interestDates,
                                           final Date date,
                                           final boolean applyObservationShift) {
        int n = 0;
        for (final Date d : interestDates) {
            if (!d.lt(date)) {
                break;
            }
            ++n;
        }
        if (n == interestDates.size() && applyObservationShift) {
            return n - 1;
        }
        return n;
    }
}
