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

import java.util.List;

/**
 * Black-formula pricer for capped/floored compounded overnight-indexed coupons.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/blackovernightindexedcouponpricer.{hpp,cpp}}
 * {@code BlackCompoundingOvernightIndexedCouponPricer}.
 * <p>
 * <b>Phase 5e.5b-CFC:</b> initial port supports the <em>global</em> cap/floor
 * path (cap on the period rate).
 * <p>
 * <b>Phase 5e.5b-CFC-b:</b> adds the <em>local</em> daily cap/floor path
 * ({@link #optionletRateLocal}). The local path computes the cap/floor as the difference between the compounded rate
 * with daily-capped fixings and the compounded rate without capping; the future portion is approximated using a single
 * Black/Bachelier valuation at the midpoint of the future period (per the C++ note "Ester / Daily Spread Curve Setup in
 * ORE").
 *
 * @author JQuantLib migration team
 * @category cashflows
 */
public class BlackOvernightIndexedCouponPricer extends CompoundingOvernightIndexedCouponPricer {

    private double gearing_;
    private double swapletRate_;
    private double effectiveIndexFixing_;

    public BlackOvernightIndexedCouponPricer() {
        super();
    }

    public BlackOvernightIndexedCouponPricer(final Handle< OptionletVolatilityStructure > v) {
        super(v, false);
    }

    public BlackOvernightIndexedCouponPricer(final Handle< OptionletVolatilityStructure > v,
            final boolean effectiveVolatilityInput) {
        super(v, effectiveVolatilityInput);
    }

    /**
     * Replicates C++ anonymous-namespace {@code cappedFlooredRate} helper: for Call (cap) returns {@code min(r, k)};
     * for Put (floor) returns {@code max(r, k)}.
     */
    private static double cappedFlooredRate(final double r, final Option.Type optionType, final double k) {
        return optionType == Option.Type.Call ? Math.min(r, k) : Math.max(r, k);
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
     * Replicates C++ {@code CompoundingOvernightIndexedCouponPricer::compute} minus the spread split (we just need the
     * swaplet rate). This duplicates a small slice of {@link CompoundingOvernightIndexedCouponPricer#computeRate}
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
     * Daily cap/floor pricer (the "local" path).
     * <p>
     * Mirrors C++ {@code BlackCompoundingOvernightIndexedCouponPricer::optionletRateLocal}. Computes a {@code rate}
     * (with daily-capped/floored fixings) and a {@code rawRate} (without capping/flooring), and returns the option
     * component
     * <code>(optionType==Call ? -1 : +1) * (rate - rawRate)</code>.
     * <p>
     * The future portion is approximated by pricing a single cap/floor at the midpoint of the future period (one
     * Black/Bachelier evaluation), then assuming that effective rate applies daily across the future sub-period
     * (formula (4) in "Ester / Daily Spread Curve Setup in ORE").
     */
    private double optionletRateLocal(final Option.Type optionType, final double effStrike) {
        QL.require(!effectiveVolatilityInput(),
                "BlackOvernightIndexedCouponPricer.optionletRateLocal() does not support "
                        + "effective volatility input.");

        // Back out the absolute strike on the daily fixing (cf. C++ comment
        // block at overnightindexedcouponpricer.cpp:117).
        final double absStrike = coupon_.compoundSpreadDaily() ? effStrike + coupon_.spread() : effStrike;

        final OvernightIndex index = coupon_.overnightIndex();
        final List< Date > fixingDates = coupon_.fixingDates();
        final double[] dt = coupon_.dt();
        final List< Date > dates = coupon_.valueDates();

        final int n = dt.length;
        final int lockoutDays = coupon_.lockoutDays();
        QL.require(lockoutDays < n,
                "rate cutoff (" + lockoutDays + ") must be less than number of fixings in period (" + n + ")");
        final int nCutoff = n - lockoutDays;

        double compoundFactor = 1.0;
        double compoundFactorRaw = 1.0;

        // historical portion: fixingDates[min(i, nCutoff)] < today
        final Date today = new Settings().evaluationDate();
        int i = 0;
        while ( i < n && fixingDates.get(Math.min(i, nCutoff)).lt(today) ) {
            double pastFixing = index.pastFixing(fixingDates.get(Math.min(i, nCutoff)));
            QL.require(pastFixing != Constants.NULL_REAL,
                    "Missing " + index.name() + " fixing for " + fixingDates.get(Math.min(i, nCutoff)));
            if ( coupon_.compoundSpreadDaily() ) {
                pastFixing += coupon_.spread();
            }
            compoundFactor *= 1.0 + cappedFlooredRate(pastFixing, optionType, absStrike) * dt[i];
            compoundFactorRaw *= 1.0 + pastFixing * dt[i];
            ++i;
        }

        // today edge case: fixingDates[min(i, nCutoff)] == today
        if ( i < n && fixingDates.get(Math.min(i, nCutoff)).equals(today) ) {
            try {
                double pastFixing = index.pastFixing(today);
                if ( pastFixing != Constants.NULL_REAL ) {
                    if ( coupon_.compoundSpreadDaily() ) {
                        pastFixing += coupon_.spread();
                    }
                    compoundFactor *= 1.0 + cappedFlooredRate(pastFixing, optionType, absStrike) * dt[i];
                    compoundFactorRaw *= 1.0 + pastFixing * dt[i];
                    ++i;
                }
                // else: fall through and forecast
            } catch ( final Exception e ) {
                // fall through and forecast
            }
        }

        // forward portion: single Black/Bachelier in the middle of the future period
        if ( i < n ) {
            final Handle< YieldTermStructure > curve = index.termStructure();
            QL.require(!curve.empty(), "null term structure set to this instance of " + index.name());
            final YieldTermStructure ts = curve.currentLink();

            double startDiscount = ts.discount(dates.get(i));
            double endDiscount = ts.discount(dates.get(Math.max(nCutoff, i)));

            // Lockout adjustment: keep the forward discount factor constant
            // during the cutoff period (only applies if nCutoff < n).
            if ( nCutoff < n ) {
                final double discountCutoffDate =
                        ts.discount(dates.get(nCutoff).add(1)) / ts.discount(dates.get(nCutoff));
                final long cutoffSpan = dates.get(n).sub(dates.get(nCutoff));
                endDiscount *= Math.pow(discountCutoffDate, cutoffSpan);
            }

            // Average daily rate over the future period (continuously compounded approx)
            final double tau = coupon_.dayCounter().yearFraction(dates.get(i), dates.get(dates.size() - 1));
            double averageRate = -Math.log(endDiscount / startDiscount) / tau;

            // Cap/floor at the midpoint of the future period (accounting for cutoff)
            final OptionletVolatilityStructure vol = capletVolatility().currentLink();
            final double midPoint =
                    (vol.timeFromReference(dates.get(i)) + vol.timeFromReference(dates.get(nCutoff))) / 2.0;
            final double stdDev = vol.volatility(midPoint, effStrike) * Math.sqrt(midPoint);
            final double shift = vol.displacement();
            final boolean shiftedLn = vol.volatilityType() == VolatilityType.ShiftedLognormal;
            final double cfValue = shiftedLn ? BlackFormula.blackFormula(optionType, effStrike, averageRate, stdDev,
                    1.0, shift) : BlackFormula.bachelierBlackFormula(optionType, effStrike, averageRate, stdDev, 1.0);

            final double effectiveTime = vol.timeFromReference(fixingDates.get(fixingDates.size() - 1));
            if ( optionType == Option.Type.Call ) {
                effectiveCapletVolatility_ = stdDev / Math.sqrt(effectiveTime);
            } else {
                effectiveFloorletVolatility_ = stdDev / Math.sqrt(effectiveTime);
            }

            // Add spread to average rate (compoundSpreadDaily case)
            if ( coupon_.compoundSpreadDaily() ) {
                averageRate += coupon_.spread();
            }

            // Incorporate cap/floor into average rate
            final double averageRateRaw = averageRate;
            averageRate += (optionType == Option.Type.Call) ? (-cfValue) : cfValue;

            // Treat averageRate as the effective rate over the future sub-period
            // (formula (4) from the ORE paper). dailyTau converts year fractions
            // to daily increments using the actual day-count.
            final long span = dates.get(dates.size() - 1).sub(dates.get(i));
            final double dailyTau = coupon_.dayCounter().yearFraction(dates.get(i), dates.get(dates.size() - 1)) / span;
            compoundFactor *= Math.pow(1.0 + dailyTau * averageRate, span);
            compoundFactorRaw *= Math.pow(1.0 + dailyTau * averageRateRaw, span);
        }

        // Period accrual: lockout splits accrual end-date logic
        final double tauPeriod = coupon_.lockoutDays() == 0
                ? coupon_.accrualPeriod()
                : coupon_.dayCounter().yearFraction(dates.get(0), dates.get(dates.size() - 1));
        double rate = (compoundFactor - 1.0) / tauPeriod;
        double rawRate = (compoundFactorRaw - 1.0) / tauPeriod;

        rate *= coupon_.gearing();
        rawRate *= coupon_.gearing();

        if ( !coupon_.compoundSpreadDaily() ) {
            rate += coupon_.spread();
            rawRate += coupon_.spread();
        }

        // optionletRate = (Call ? -1 : +1) * (rate - rawRate)
        return (optionType == Option.Type.Call ? -1.0 : 1.0) * (rate - rawRate);
    }

    /**
     * Global (period-rate) cap/floor pricer. Mirrors C++
     * {@code BlackCompoundingOvernightIndexedCouponPricer::optionletRateGlobal}.
     */
    private double optionletRateGlobal(final Option.Type optionType, final double effStrike) {
        final Date lastRelevantFixingDate = coupon_.fixingDate();
        if ( lastRelevantFixingDate.le(new Settings().evaluationDate()) ) {
            // already determined: intrinsic
            final double a;
            final double b;
            if ( optionType == Option.Type.Call ) {
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
        final List< Date > fixingDates = coupon_.fixingDates();
        QL.require(!fixingDates.isEmpty(), "BlackOvernightIndexedCouponPricer: empty fixing dates");
        final OptionletVolatilityStructure vol = capletVolatility().currentLink();
        final boolean shiftedLn = vol.volatilityType() == VolatilityType.ShiftedLognormal;
        final double shift = vol.displacement();
        final double effectiveTime = vol.timeFromReference(fixingDates.get(fixingDates.size() - 1));

        final double stdDev;
        if ( effectiveVolatilityInput() ) {
            stdDev = vol.volatility(fixingDates.get(fixingDates.size() - 1), effStrike) * Math.sqrt(effectiveTime);
        } else {
            // Lyashenko-Mercurio dampening (see C++ for derivation)
            final double fixingStartTime = vol.timeFromReference(fixingDates.get(0));
            final double fixingEndTime = vol.timeFromReference(fixingDates.get(fixingDates.size() - 1));
            final Date refDateP1 = vol.referenceDate().add(1);
            final Date sigmaDate = fixingDates.get(0).gt(refDateP1) ? fixingDates.get(0) : refDateP1;
            final double sigma = vol.volatility(sigmaDate, effStrike);
            double T = Math.max(fixingStartTime, 0.0);
            if ( !Closeness.isCloseEnough(fixingEndTime, T) ) {
                final double diff = fixingEndTime - T;
                final double span = fixingEndTime - fixingStartTime;
                T += Math.pow(diff, 3.0) / Math.pow(span, 2.0) / 3.0;
            }
            stdDev = sigma * Math.sqrt(T);
        }

        if ( optionType == Option.Type.Call ) {
            effectiveCapletVolatility_ = stdDev / Math.sqrt(effectiveTime);
        } else {
            effectiveFloorletVolatility_ = stdDev / Math.sqrt(effectiveTime);
        }

        final double fixing = shiftedLn
                ? BlackFormula.blackFormula(optionType, effStrike, effectiveIndexFixing_, stdDev, 1.0, shift)
                : BlackFormula.bachelierBlackFormula(optionType, effStrike, effectiveIndexFixing_, stdDev, 1.0);
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
