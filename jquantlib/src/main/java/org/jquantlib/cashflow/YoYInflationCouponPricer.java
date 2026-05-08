/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Base pricer for capped/floored YoY-inflation coupons (and ordinary
 * swaplets — capless/floorless YoY coupons use this pricer directly).
 *
 * <p>Java port of QuantLib v1.42.1 {@code YoYInflationCouponPricer}
 * ({@code ql/cashflows/inflationcouponpricer.{hpp,cpp}}).
 *
 * <p>Phase 2r C.3 fills out the optionlet path: {@link #optionletRate},
 * {@link #optionletPrice}, {@link #capletRate} / {@link #capletPrice} /
 * {@link #floorletRate} / {@link #floorletPrice}, plus the constructor
 * variant taking a {@link YoYOptionletVolatilitySurface} handle. Concrete
 * vol-dependent subclasses
 * ({@link BlackYoYInflationCouponPricer},
 *  {@link UnitDisplacedBlackYoYInflationCouponPricer},
 *  {@link BachelierYoYInflationCouponPricer}) supply
 * {@link #optionletPriceImp}.
 *
 * <p>The {@link YoYOptionletVolatilitySurface} interface is forward-declared
 * in this package; Track B's full vol-surface family lives in
 * {@code org.jquantlib.termstructures.volatility.inflation} and implements
 * this interface (or an adapter is added when Track B lands).
 *
 * @author JQuantLib migration team (Phase 2q B + 2r C.3)
 */
public class YoYInflationCouponPricer extends InflationCouponPricer {

    //
    // protected state — populated by initialize()
    //

    protected YoYInflationCoupon coupon_;
    protected double gearing_;
    protected double spread_;
    protected double discount_;

    /** YoY optionlet vol surface (Phase 2r C.3) — may be {@code null}. */
    protected Handle<YoYOptionletVolatilitySurface> capletVol_;
    private final Handle<YieldTermStructure> nominalTermStructure_;

    //
    // public constructors
    //

    /**
     * Construct a pricer with no nominal term structure. {@link #swapletPrice}
     * will be unable to discount and will mark {@code discount_} as
     * {@link Constants#NULL_REAL}; {@link #swapletRate} still works.
     */
    public YoYInflationCouponPricer() {
        this(new Handle<YoYOptionletVolatilitySurface>(),
                new Handle<YieldTermStructure>());
    }

    public YoYInflationCouponPricer(final Handle<YieldTermStructure> nominalTermStructure) {
        this(new Handle<YoYOptionletVolatilitySurface>(), nominalTermStructure);
    }

    public YoYInflationCouponPricer(
            final Handle<YoYOptionletVolatilitySurface> capletVol,
            final Handle<YieldTermStructure> nominalTermStructure) {
        this.capletVol_ = capletVol;
        this.nominalTermStructure_ = nominalTermStructure;
        if (this.capletVol_ != null) {
            this.capletVol_.addObserver(this);
        }
        if (this.nominalTermStructure_ != null) {
            this.nominalTermStructure_.addObserver(this);
        }
    }

    public Handle<YieldTermStructure> nominalTermStructure() {
        return nominalTermStructure_;
    }

    public Handle<YoYOptionletVolatilitySurface> capletVolatility() {
        return capletVol_;
    }

    /**
     * Replace the caplet volatility surface (mirrors C++
     * {@code setCapletVolatility}). The supplied handle must be non-empty.
     */
    public void setCapletVolatility(
            final Handle<YoYOptionletVolatilitySurface> capletVol) {
        QL.require(capletVol != null && !capletVol.empty(),
                "empty capletVol handle");
        this.capletVol_ = capletVol;
        this.capletVol_.addObserver(this);
    }

    //
    // InflationCouponPricer interface
    //

    @Override
    public void initialize(final InflationCoupon coupon) {
        QL.require(coupon instanceof YoYInflationCoupon,
                "year-on-year inflation coupon needed");
        this.coupon_ = (YoYInflationCoupon) coupon;
        this.gearing_ = coupon_.gearing();
        this.spread_ = coupon_.spread();
        this.paymentDate_ = coupon_.date();

        this.discount_ = 1.0;
        if (nominalTermStructure_ == null || nominalTermStructure_.empty()) {
            // allow rate access but mark discount invalid for prices
            this.discount_ = Constants.NULL_REAL;
        } else {
            final YieldTermStructure curve = nominalTermStructure_.currentLink();
            if (paymentDate_.gt(curve.referenceDate())) {
                this.discount_ = curve.discount(paymentDate_);
            }
        }
    }

    @Override
    public double swapletRate() {
        // Identity adjustment matches C++ default
        // YoYInflationCouponPricer::adjustedFixing(Null<>): no transformation.
        return gearing_ * adjustedFixing(Double.NaN) + spread_;
    }

    @Override
    public double swapletPrice() {
        QL.require(discount_ != Constants.NULL_REAL,
                "no nominal term structure provided");
        return swapletRate() * coupon_.accrualPeriod() * discount_;
    }

    @Override
    public double capletRate(final double effectiveCap) {
        return gearing_ * optionletRate(Option.Type.Call, effectiveCap);
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        final double capletPrice = optionletPrice(Option.Type.Call, effectiveCap);
        return gearing_ * capletPrice;
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        return gearing_ * optionletRate(Option.Type.Put, effectiveFloor);
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        final double floorletPrice = optionletPrice(Option.Type.Put, effectiveFloor);
        return gearing_ * floorletPrice;
    }

    //
    // protected helpers — mirror C++ optionletPrice / optionletRate /
    // optionletPriceImp / adjustedFixing
    //

    protected double optionletPrice(final Option.Type optionType, final double effStrike) {
        QL.require(discount_ != Constants.NULL_REAL,
                "no nominal term structure provided");
        return optionletRate(optionType, effStrike)
                * coupon_.accrualPeriod() * discount_;
    }

    protected double optionletRate(final Option.Type optionType, final double effStrike) {
        final Date fixingDate = coupon_.fixingDate();
        if (capletVol_ == null || capletVol_.empty()
                || fixingDate.le(capletVol_.currentLink().baseDate())) {
            // amount is determined; the surface need not be present.
            final double a, b;
            if (optionType == Option.Type.Call) {
                a = coupon_.indexFixing();
                b = effStrike;
            } else {
                a = effStrike;
                b = coupon_.indexFixing();
            }
            return Math.max(a - b, 0.0);
        }
        // not yet determined — Black/DD/Bachelier from optionletPriceImp
        QL.require(!capletVol_.empty(), "missing optionlet volatility");

        final double stdDev = Math.sqrt(
                capletVol_.currentLink().totalVariance(
                        fixingDate, effStrike, new Period(0, TimeUnit.Days), false));
        return optionletPriceImp(optionType, effStrike,
                adjustedFixing(Double.NaN), stdDev);
    }

    /**
     * Hook for derived classes (Black/DD/Bachelier) to provide the
     * vol-dependent rate. The base implementation always errors —
     * subclasses must override.
     */
    protected double optionletPriceImp(final Option.Type optionType,
                                       final double strike,
                                       final double forward,
                                       final double stdDev) {
        throw new LibraryException(
                "you must implement this to get a vol-dependent price");
    }

    /**
     * Adjusted fixing — returns the index fixing if {@code fixing} is
     * {@code NaN} (Java sentinel for C++ {@code Null<Rate>()}), otherwise
     * returns the supplied value. The base class applies no adjustment;
     * subclasses can override.
     */
    protected double adjustedFixing(final double fixing) {
        if (Double.isNaN(fixing)) {
            return coupon_.indexFixing();
        }
        return fixing;
    }
}
