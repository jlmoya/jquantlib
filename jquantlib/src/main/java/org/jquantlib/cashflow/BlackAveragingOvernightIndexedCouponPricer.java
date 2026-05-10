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

 This program is distributed in the hope that it will be useful, but
 WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 or FITNESS FOR A PARTICULAR PURPOSE. See the license for more details.
*/

package org.jquantlib.cashflow;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.Date;

/**
 * Black-formula pricer for capped/floored arithmetically-averaged
 * overnight-indexed coupons.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/cashflows/blackovernightindexedcouponpricer.{hpp,cpp}}
 * {@code BlackAveragingOvernightIndexedCouponPricer}.
 * <p>
 * <b>Phase 5e.5b-CFC-b:</b> companion to
 * {@link BlackOvernightIndexedCouponPricer} for the simple-averaging path.
 * Only {@link RateAveraging.Type#Simple Simple} averaging is supported (the
 * C++ class explicitly fails initialization if the underlying coupon has
 * Compound averaging — those should use the compounding pricer instead).
 * <p>
 * Both the global ({@link #optionletRateGlobal}) and the daily/local
 * ({@link #optionletRateLocal}) cap/floor paths are implemented:
 * <ul>
 *   <li><b>Global:</b> if the last fixing has occurred, intrinsic
 *       {@code gearing * max(±(F-K), 0)}; otherwise a single Black/Bachelier
 *       call/put on the forward simple-average rate.</li>
 *   <li><b>Local:</b> daily-cap/floor with a midpoint Black/Bachelier
 *       valuation for the future portion (Ester / Daily Spread Curve Setup
 *       in ORE).</li>
 * </ul>
 * <p>
 * The Lyashenko-Mercurio dampening of the volatility (linear weight from
 * 1 at fixing-start to 0 at fixing-end) follows the same recipe as in
 * {@link BlackOvernightIndexedCouponPricer}; see the C++ comment block
 * (overnightindexedcouponpricer.cpp:319-331) for the derivation.
 *
 * @category cashflows
 *
 * @author JQuantLib migration team
 */
public class BlackAveragingOvernightIndexedCouponPricer
        extends ArithmeticAveragedOvernightIndexedCouponPricer {

    private double gearing_;
    private double swapletRate_;
    private double forwardRate_;

    public BlackAveragingOvernightIndexedCouponPricer() {
        super(0.03, 0.0, false, new Handle<OptionletVolatilityStructure>(), false);
    }

    public BlackAveragingOvernightIndexedCouponPricer(final Handle<OptionletVolatilityStructure> v) {
        super(0.03, 0.0, false, v, false);
    }

    public BlackAveragingOvernightIndexedCouponPricer(final Handle<OptionletVolatilityStructure> v,
                                                      final boolean effectiveVolatilityInput) {
        super(0.03, 0.0, false, v, effectiveVolatilityInput);
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        super.initialize(coupon);

        // Mirror C++ guard: this class is only valid for simple-averaging
        // coupons. Compound coupons must use BlackOvernightIndexedCouponPricer.
        QL.require(coupon_.averagingMethod() != RateAveraging.Type.Compound,
            "Averaging method required to be simple for "
          + "BlackAveragingOvernightIndexedCouponPricer");

        gearing_ = coupon.gearing();
        // swapletRate_ is the gross rate (gearing*F + spread) per the parent.
        swapletRate_ = super.swapletRate();
        forwardRate_ = (swapletRate_ - coupon_.spread()) / coupon_.gearing();

        effectiveCapletVolatility_ = Constants.NULL_REAL;
        effectiveFloorletVolatility_ = Constants.NULL_REAL;
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
        return dailyCapFloor
                ? optionletRateLocal(Option.Type.Call, effectiveCap)
                : optionletRateGlobal(Option.Type.Call, effectiveCap);
    }

    @Override
    public double floorletRate(final double effectiveFloor, final boolean dailyCapFloor) {
        return dailyCapFloor
                ? optionletRateLocal(Option.Type.Put, effectiveFloor)
                : optionletRateGlobal(Option.Type.Put, effectiveFloor);
    }

    /**
     * Global (period-rate) cap/floor pricer.
     * Mirrors C++ {@code BlackAveragingOvernightIndexedCouponPricer::optionletRateGlobal}.
     */
    private double optionletRateGlobal(final Option.Type optionType, final double effStrike) {
        final Date lastRelevantFixingDate = coupon_.fixingDate();
        if (lastRelevantFixingDate.le(new Settings().evaluationDate())) {
            // already determined: intrinsic
            final double a;
            final double b;
            if (optionType == Option.Type.Call) {
                a = forwardRate_;
                b = effStrike;
            } else {
                a = effStrike;
                b = forwardRate_;
            }
            return gearing_ * Math.max(a - b, 0.0);
        }
        // not yet fixed: Black model
        QL.require(capletVolatility() != null && !capletVolatility().empty(),
            "BlackAveragingOvernightIndexedCouponPricer: missing optionlet volatility");
        final List<Date> fixingDates = coupon_.fixingDates();
        QL.require(!fixingDates.isEmpty(),
            "BlackAveragingOvernightIndexedCouponPricer: empty fixing dates");
        final OptionletVolatilityStructure vol = capletVolatility().currentLink();
        final boolean shiftedLn = vol.volatilityType() == VolatilityType.ShiftedLognormal;
        final double shift = vol.displacement();
        final double effectiveTime = vol.timeFromReference(fixingDates.get(fixingDates.size() - 1));

        final double stdDev;
        if (effectiveVolatilityInput()) {
            stdDev = vol.volatility(fixingDates.get(fixingDates.size() - 1), effStrike)
                    * Math.sqrt(effectiveTime);
        } else {
            // Lyashenko-Mercurio dampening
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
                ? BlackFormula.blackFormula(optionType, effStrike, forwardRate_, stdDev, 1.0, shift)
                : BlackFormula.bachelierBlackFormula(optionType, effStrike, forwardRate_, stdDev, 1.0);
        return gearing_ * fixing;
    }

    /**
     * Daily cap/floor pricer (the "local" path).
     * <p>
     * Mirrors C++
     * {@code BlackAveragingOvernightIndexedCouponPricer::optionletRateLocal}.
     * Identical structure to the compounding variant
     * ({@link BlackOvernightIndexedCouponPricer#optionletRateLocal}) but uses
     * <em>arithmetic accumulation</em> instead of compounding: the running
     * variable is {@code accumulatedRate += rate * dt} and the average rate
     * over the future portion is added directly rather than via
     * {@code (1 + r*dailyTau)^days}.
     */
    private double optionletRateLocal(final Option.Type optionType, final double effStrike) {
        QL.require(!effectiveVolatilityInput(),
            "BlackAveragingOvernightIndexedCouponPricer.optionletRateLocal() does not support "
          + "effective volatility input.");

        final double absStrike = coupon_.compoundSpreadDaily()
                ? effStrike + coupon_.spread()
                : effStrike;

        final OvernightIndex index = coupon_.overnightIndex();
        final List<Date> fixingDates = coupon_.fixingDates();
        final double[] dt = coupon_.dt();
        final List<Date> dates = coupon_.valueDates();

        final int n = dt.length;
        final int lockoutDays = coupon_.lockoutDays();
        QL.require(lockoutDays < n,
            "rate cutoff (" + lockoutDays
          + ") must be less than number of fixings in period (" + n + ")");
        final int nCutoff = n - lockoutDays;

        double accumulatedRate = 0.0;
        double accumulatedRateRaw = 0.0;

        // historical portion
        final Date today = new Settings().evaluationDate();
        int i = 0;
        while (i < n && fixingDates.get(Math.min(i, nCutoff)).lt(today)) {
            double pastFixing = index.pastFixing(fixingDates.get(Math.min(i, nCutoff)));
            QL.require(pastFixing != Constants.NULL_REAL,
                "Missing " + index.name() + " fixing for "
              + fixingDates.get(Math.min(i, nCutoff)));
            if (coupon_.compoundSpreadDaily()) {
                pastFixing += coupon_.spread();
            }
            accumulatedRate += cappedFlooredRate(pastFixing, optionType, absStrike) * dt[i];
            accumulatedRateRaw += pastFixing * dt[i];
            ++i;
        }

        // today edge case
        if (i < n && fixingDates.get(Math.min(i, nCutoff)).equals(today)) {
            try {
                double pastFixing = index.pastFixing(today);
                if (pastFixing != Constants.NULL_REAL) {
                    if (coupon_.compoundSpreadDaily()) {
                        pastFixing += coupon_.spread();
                    }
                    accumulatedRate += cappedFlooredRate(pastFixing, optionType, absStrike) * dt[i];
                    accumulatedRateRaw += pastFixing * dt[i];
                    ++i;
                }
                // else: fall through and forecast
            } catch (final Exception e) {
                // fall through and forecast
            }
        }

        // forward portion
        if (i < n) {
            final Handle<YieldTermStructure> curve = index.termStructure();
            QL.require(!curve.empty(),
                "null term structure set to this instance of " + index.name());
            final YieldTermStructure ts = curve.currentLink();

            double startDiscount = ts.discount(dates.get(i));
            double endDiscount = ts.discount(dates.get(Math.max(nCutoff, i)));

            // Lockout adjustment
            if (nCutoff < n) {
                final double discountCutoffDate = ts.discount(dates.get(nCutoff).add(1))
                                                  / ts.discount(dates.get(nCutoff));
                final long cutoffSpan = dates.get(n).sub(dates.get(nCutoff));
                endDiscount *= Math.pow(discountCutoffDate, cutoffSpan);
            }

            final double tau = coupon_.dayCounter().yearFraction(dates.get(i), dates.get(dates.size() - 1));
            double averageRate = -Math.log(endDiscount / startDiscount) / tau;

            final OptionletVolatilityStructure vol = capletVolatility().currentLink();
            final double midPoint = (vol.timeFromReference(dates.get(i))
                                   + vol.timeFromReference(dates.get(nCutoff))) / 2.0;
            final double stdDev = vol.volatility(midPoint, effStrike) * Math.sqrt(midPoint);
            final double shift = vol.displacement();
            final boolean shiftedLn = vol.volatilityType() == VolatilityType.ShiftedLognormal;
            final double cfValue = shiftedLn
                    ? BlackFormula.blackFormula(optionType, effStrike, averageRate, stdDev, 1.0, shift)
                    : BlackFormula.bachelierBlackFormula(optionType, effStrike, averageRate, stdDev, 1.0);

            final double effectiveTime = vol.timeFromReference(fixingDates.get(fixingDates.size() - 1));
            if (optionType == Option.Type.Call) {
                effectiveCapletVolatility_ = stdDev / Math.sqrt(effectiveTime);
            } else {
                effectiveFloorletVolatility_ = stdDev / Math.sqrt(effectiveTime);
            }

            if (coupon_.compoundSpreadDaily()) {
                averageRate += coupon_.spread();
            }

            final double averageRateRaw = averageRate;
            averageRate += (optionType == Option.Type.Call) ? (-cfValue) : cfValue;

            // Arithmetic accumulation (no compounding) — formula (4) ORE
            final long span = dates.get(dates.size() - 1).sub(dates.get(i));
            final double dailyTau = coupon_.dayCounter().yearFraction(
                    dates.get(i), dates.get(dates.size() - 1)) / span;
            accumulatedRate += dailyTau * averageRate * span;
            accumulatedRateRaw += dailyTau * averageRateRaw * span;
        }

        // Period accrual: in C++ averaging variant the guard uses
        // coupon_->fixingDays() == 0 (not lockoutDays==0). This mirrors
        // overnightindexedcouponpricer.cpp:470-472.
        final double tauPeriod = coupon_.fixingDays() == 0
                ? coupon_.accrualPeriod()
                : coupon_.dayCounter().yearFraction(dates.get(0), dates.get(dates.size() - 1));
        double rate = accumulatedRate / tauPeriod;
        double rawRate = accumulatedRateRaw / tauPeriod;

        rate *= coupon_.gearing();
        rawRate *= coupon_.gearing();

        if (!coupon_.compoundSpreadDaily()) {
            rate += coupon_.spread();
            rawRate += coupon_.spread();
        }

        return (optionType == Option.Type.Call ? -1.0 : 1.0) * (rate - rawRate);
    }

    private static double cappedFlooredRate(final double r, final Option.Type optionType, final double k) {
        return optionType == Option.Type.Call ? Math.min(r, k) : Math.max(r, k);
    }

    @Override
    public double swapletPrice() {
        QL.require(false, "BlackAveragingOvernightIndexedCouponPricer.swapletPrice() not provided");
        return Constants.NULL_REAL;
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        QL.require(false, "BlackAveragingOvernightIndexedCouponPricer.capletPrice() not provided");
        return Constants.NULL_REAL;
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        QL.require(false, "BlackAveragingOvernightIndexedCouponPricer.floorletPrice() not provided");
        return Constants.NULL_REAL;
    }
}
