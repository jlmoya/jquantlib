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
 Copyright (C) 2009, 2011 Chris Kenyon
 Copyright (C) 2022 Quaternion Risk Management Ltd

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.instruments.Option;
import org.jquantlib.lang.annotation.Rate;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Base pricer for capped/floored CPI coupons.
 *
 * <p>This pricer can already do swaplets; to obtain volatility-dependent
 * coupons (caps/floors with Black/DD/Bachelier semantics) you would need to
 * implement a descendant that overrides {@link #optionletPriceImp}. The CPI
 * volatility surface ({@code QuantLib::CPIVolatilitySurface}) is part of
 * Phase 2r scope and is therefore not represented in this Java port — calls
 * that would require it raise {@link org.jquantlib.QL#error}.
 *
 * <p>Mirrors C++ {@code QuantLib::CPICouponPricer} at v1.42.1
 * (cashflows/cpicouponpricer.{hpp,cpp}). The {@code capletVol_} handle is
 * intentionally omitted; if/when {@code CPIVolatilitySurface} is ported, add
 * the second-form constructor + {@code setCapletVolatility} accessor without
 * breaking the existing constructor or {@code initialize} signatures.
 *
 * @author JQuantLib migration team (Phase 2q C.1)
 */
public class CPICouponPricer extends InflationCouponPricer {

    //
    // protected fields
    //

    protected Handle<YieldTermStructure> nominalTermStructure_;
    protected CPICoupon coupon_;
    protected double gearing_;
    protected double discount_;

    //
    // public constructors
    //

    public CPICouponPricer() {
        this(new Handle<YieldTermStructure>());
    }

    public CPICouponPricer(final Handle<YieldTermStructure> nominalTermStructure) {
        this.nominalTermStructure_ = nominalTermStructure;
        // C++ registerWith(nominalTermStructure_)
        if (nominalTermStructure_ != null) {
            nominalTermStructure_.addObserver(this);
        }
    }

    //
    // public methods
    //

    public Handle<YieldTermStructure> nominalTermStructure() {
        return nominalTermStructure_;
    }

    //
    // InflationCouponPricer interface
    //

    @Override
    public void initialize(final InflationCoupon coupon) {
        QL.require(coupon instanceof CPICoupon, "wrong coupon type for CPICouponPricer");
        this.coupon_ = (CPICoupon) coupon;
        this.gearing_ = coupon_.fixedRate();
        this.paymentDate_ = coupon_.date();

        // mirror C++: empty TS allows extracting rates but discount_ is invalid
        if (nominalTermStructure_ == null || nominalTermStructure_.empty()) {
            this.discount_ = Constants.NULL_REAL;
        } else {
            this.discount_ = 1.0;
            if (paymentDate_.gt(nominalTermStructure_.currentLink().referenceDate())) {
                this.discount_ = nominalTermStructure_.currentLink().discount(paymentDate_);
            }
        }
    }

    @Override
    public double swapletPrice() {
        QL.require(discount_ != Constants.NULL_REAL, "no nominal term structure provided");
        return swapletRate() * coupon_.accrualPeriod() * discount_;
    }

    @Override
    public /*@Rate*/ double swapletRate() {
        return accruedRate(coupon_.accrualEndDate());
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        return gearing_ * optionletPrice(Option.Type.Call, effectiveCap);
    }

    @Override
    public /*@Rate*/ double capletRate(final double effectiveCap) {
        return gearing_ * optionletRate(Option.Type.Call, effectiveCap);
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        return gearing_ * optionletPrice(Option.Type.Put, effectiveFloor);
    }

    @Override
    public /*@Rate*/ double floorletRate(final double effectiveFloor) {
        return gearing_ * optionletRate(Option.Type.Put, effectiveFloor);
    }

    /**
     * Mirrors C++ {@code CPICouponPricer::accruedRate(Date)}. The pricer must
     * be initialized first; the InflationCoupon base class drives initialize
     * from its {@code performCalculations}.
     */
    public /*@Rate*/ double accruedRate(final Date settlementDate) {
        return gearing_ * coupon_.indexRatio(settlementDate);
    }

    //
    // protected helpers
    //

    protected double optionletPrice(final Option.Type optionType, final double effStrike) {
        QL.require(discount_ != Constants.NULL_REAL, "no nominal term structure provided");
        return optionletRate(optionType, effStrike)
                * coupon_.accrualPeriod() * discount_;
    }

    protected double optionletRate(final Option.Type optionType, final double effStrike) {
        final Date fixingDate = coupon_.fixingDate();
        if (fixingDate.le(new Settings().evaluationDate())) {
            // amount is determined
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
        // not yet determined — would need volatility surface
        throw new org.jquantlib.lang.exceptions.LibraryException(
                "missing optionlet volatility (CPIVolatilitySurface not yet ported; "
                + "Phase 2r — port required for vol-dependent CPI rates)");
    }

    /**
     * Hook for derived classes to provide a vol-dependent price. The base
     * implementation always errors — derived classes must override.
     *
     * <p>Mirrors C++ {@code CPICouponPricer::optionletPriceImp}.
     */
    protected double optionletPriceImp(final Option.Type optionType,
                                       final double strike,
                                       final double forward,
                                       final double stdDev) {
        throw new org.jquantlib.lang.exceptions.LibraryException(
                "you must implement this to get a vol-dependent price");
    }
}
