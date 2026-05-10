/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2020 Quaternion Risk Management Ltd

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

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.Date;

/**
 * Black-formula pricer for capped/floored compounded overnight-indexed coupons.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/cashflows/blackovernightindexedcouponpricer.{hpp,cpp}}
 * {@code BlackCompoundingOvernightIndexedCouponPricer}.
 * <p>
 * <b>Phase 5e.5b-CFC:</b> initial port supports the <em>global</em> cap/floor
 * path (cap on the period rate). The <em>local</em> daily-cap/floor path is
 * deferred — invoking {@link #capletRate(double, boolean)} or
 * {@link #floorletRate(double, boolean)} with {@code dailyCapFloor=true}
 * raises an exception.
 *
 * @category cashflows
 *
 * @author JQuantLib migration team
 */
public class BlackOvernightIndexedCouponPricer extends CompoundingOvernightIndexedCouponPricer {

    private double gearing_;
    private double swapletRate_;
    private double effectiveIndexFixing_;

    public BlackOvernightIndexedCouponPricer() {
        super();
    }

    public BlackOvernightIndexedCouponPricer(final Handle<OptionletVolatilityStructure> v) {
        super(v, false);
    }

    public BlackOvernightIndexedCouponPricer(final Handle<OptionletVolatilityStructure> v,
                                             final boolean effectiveVolatilityInput) {
        super(v, effectiveVolatilityInput);
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        super.initialize(coupon);
        gearing_ = coupon.gearing();
        // Compute the swaplet rate (and effectiveIndexFixing/spread) up-front
        // by delegating to CompoundingOvernightIndexedCouponPricer.
        swapletRate_ = computeSwapletRate(coupon_.accrualEndDate());
        effectiveIndexFixing_ = (swapletRate_ - coupon_.spread()) / coupon_.gearing();
        effectiveCapletVolatility_ = Constants.NULL_REAL;
        effectiveFloorletVolatility_ = Constants.NULL_REAL;
    }

    /**
     * Replicates C++ {@code CompoundingOvernightIndexedCouponPricer::compute}
     * minus the spread split (we just need the swaplet rate). This duplicates
     * a small slice of {@link CompoundingOvernightIndexedCouponPricer#computeRate}
     * because the parent method is private.
     */
    private double computeSwapletRate(final Date date) {
        // Delegate via averageRate which is the public entry point:
        return averageRate(date);
    }

    @Override
    public double swapletRate() {
        return swapletRate_;
    }

    @Override
    public double capletRate(final double effectiveCap) {
        return capletRate(effectiveCap, false);
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        return floorletRate(effectiveFloor, false);
    }

    @Override
    public double capletRate(final double effectiveCap, final boolean dailyCapFloor) {
        if (dailyCapFloor) {
            QL.require(false,
                "BlackOvernightIndexedCouponPricer: dailyCapFloor=true (local cap/floor) "
              + "is not yet ported (Phase 5e.5b-CFC carry-forward).");
        }
        return optionletRateGlobal(Option.Type.Call, effectiveCap);
    }

    @Override
    public double floorletRate(final double effectiveFloor, final boolean dailyCapFloor) {
        if (dailyCapFloor) {
            QL.require(false,
                "BlackOvernightIndexedCouponPricer: dailyCapFloor=true (local cap/floor) "
              + "is not yet ported (Phase 5e.5b-CFC carry-forward).");
        }
        return optionletRateGlobal(Option.Type.Put, effectiveFloor);
    }

    /**
     * Global (period-rate) cap/floor pricer.
     * Mirrors C++ {@code BlackCompoundingOvernightIndexedCouponPricer::optionletRateGlobal}.
     */
    private double optionletRateGlobal(final Option.Type optionType, final double effStrike) {
        final Date lastRelevantFixingDate = coupon_.fixingDate();
        if (lastRelevantFixingDate.le(new Settings().evaluationDate())) {
            // already determined: intrinsic
            final double a;
            final double b;
            if (optionType == Option.Type.Call) {
                a = effectiveIndexFixing_;
                b = effStrike;
            } else {
                a = effStrike;
                b = effectiveIndexFixing_;
            }
            return gearing_ * Math.max(a - b, 0.0);
        }
        // not yet fixed: Black model
        QL.require(capletVolatility() != null && !capletVolatility().empty(),
            "BlackOvernightIndexedCouponPricer: missing optionlet volatility");
        final List<Date> fixingDates = coupon_.fixingDates();
        QL.require(!fixingDates.isEmpty(),
            "BlackOvernightIndexedCouponPricer: empty fixing dates");
        final OptionletVolatilityStructure vol = capletVolatility().currentLink();
        final boolean shiftedLn = vol.volatilityType() == VolatilityType.ShiftedLognormal;
        final double shift = vol.displacement();
        final double effectiveTime = vol.timeFromReference(fixingDates.get(fixingDates.size() - 1));

        final double stdDev;
        if (effectiveVolatilityInput()) {
            stdDev = vol.volatility(fixingDates.get(fixingDates.size() - 1), effStrike)
                    * Math.sqrt(effectiveTime);
        } else {
            // Lyashenko-Mercurio dampening (see C++ for derivation)
            final double fixingStartTime = vol.timeFromReference(fixingDates.get(0));
            final double fixingEndTime = vol.timeFromReference(fixingDates.get(fixingDates.size() - 1));
            final Date refDateP1 = vol.referenceDate().add(1);
            final Date sigmaDate = fixingDates.get(0).gt(refDateP1) ? fixingDates.get(0) : refDateP1;
            final double sigma = vol.volatility(sigmaDate, effStrike);
            double T = Math.max(fixingStartTime, 0.0);
            if (!Closeness.isCloseEnough(fixingEndTime, T)) {
                final double diff = fixingEndTime - T;
                final double span = fixingEndTime - fixingStartTime;
                T += Math.pow(diff, 3.0) / Math.pow(span, 2.0) / 3.0;
            }
            stdDev = sigma * Math.sqrt(T);
        }

        if (optionType == Option.Type.Call) {
            effectiveCapletVolatility_ = stdDev / Math.sqrt(effectiveTime);
        } else {
            effectiveFloorletVolatility_ = stdDev / Math.sqrt(effectiveTime);
        }

        final double fixing = shiftedLn
                ? BlackFormula.blackFormula(optionType, effStrike, effectiveIndexFixing_,
                                            stdDev, 1.0, shift)
                : BlackFormula.bachelierBlackFormula(optionType, effStrike,
                                                     effectiveIndexFixing_, stdDev, 1.0);
        return gearing_ * fixing;
    }

    @Override
    public double swapletPrice() {
        QL.require(false, "BlackOvernightIndexedCouponPricer.swapletPrice() not provided");
        return Constants.NULL_REAL;
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        QL.require(false, "BlackOvernightIndexedCouponPricer.capletPrice() not provided");
        return Constants.NULL_REAL;
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        QL.require(false, "BlackOvernightIndexedCouponPricer.floorletPrice() not provided");
        return Constants.NULL_REAL;
    }
}
