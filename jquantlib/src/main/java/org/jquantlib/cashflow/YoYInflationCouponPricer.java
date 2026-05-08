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
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Base pricer for capped/floored YoY-inflation coupons (and ordinary
 * swaplets — capless/floorless YoY coupons use this pricer directly).
 *
 * <p>Java port of QuantLib v1.42.1 {@code YoYInflationCouponPricer}
 * ({@code ql/cashflows/inflationcouponpricer.{hpp,cpp}}). Phase 2q B ports
 * the swaplet path only. The optionlet path (for cap/floor pricers via
 * {@code BlackYoYInflationCouponPricer}, etc.) is deferred to Phase 2r along
 * with {@code YoYOptionletVolatilitySurface}.
 *
 * <p>The non-capped / non-floored swaplet is the only operation needed by
 * {@link YearOnYearInflationSwap}'s YoY leg. Cap/floor variants ride atop
 * the same base via {@code optionletPriceImp} which throws here (matches
 * C++ default behavior — vol-dependent overrides supply the formula).
 *
 * @author JQuantLib migration team (Phase 2q B)
 */
public class YoYInflationCouponPricer extends InflationCouponPricer {

    //
    // protected state — populated by initialize()
    //

    protected YoYInflationCoupon coupon_;
    protected double gearing_;
    protected double spread_;
    protected double discount_;

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
        this(new Handle<YieldTermStructure>());
    }

    public YoYInflationCouponPricer(final Handle<YieldTermStructure> nominalTermStructure) {
        this.nominalTermStructure_ = nominalTermStructure;
        if (this.nominalTermStructure_ != null) {
            this.nominalTermStructure_.addObserver(this);
        }
    }

    public Handle<YieldTermStructure> nominalTermStructure() {
        return nominalTermStructure_;
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
        return gearing_ * coupon_.indexFixing() + spread_;
    }

    @Override
    public double swapletPrice() {
        QL.require(discount_ != Constants.NULL_REAL,
                "no nominal term structure provided");
        return swapletRate() * coupon_.accrualPeriod() * discount_;
    }

    /**
     * Cap/floor optionlet pricers are deferred to Phase 2r — fail loudly if
     * called from a path the Java port doesn't yet exercise.
     */
    @Override
    public double capletRate(final double effectiveCap) {
        QL.error("capletRate not implemented in Phase 2q (deferred to Phase 2r " +
                "with YoYOptionletVolatilitySurface)");
        return Constants.NULL_REAL;
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        QL.error("capletPrice not implemented in Phase 2q (deferred to Phase 2r " +
                "with YoYOptionletVolatilitySurface)");
        return Constants.NULL_REAL;
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        QL.error("floorletRate not implemented in Phase 2q (deferred to Phase 2r " +
                "with YoYOptionletVolatilitySurface)");
        return Constants.NULL_REAL;
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        QL.error("floorletPrice not implemented in Phase 2q (deferred to Phase 2r " +
                "with YoYOptionletVolatilitySurface)");
        return Constants.NULL_REAL;
    }
}
