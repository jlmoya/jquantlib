/*
 Copyright (C) 2009 Ueli Hofstetter

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
package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Constants;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.Date;

/**
 * Black-formula pricer for capped/floored Ibor coupons.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code BlackIborCouponPricer} in {@code ql/cashflows/couponpricer.{hpp,cpp}}. Supports
 * <ul>
 *   <li>shifted-lognormal volatility surfaces (via {@link BlackFormula#blackFormula})</li>
 *   <li>normal volatility surfaces (via {@link BlackFormula#bachelierBlackFormula})</li>
 *   <li>two timing adjustments: {@link TimingAdjustment#Black76} and
 *       {@link TimingAdjustment#BivariateLognormal} (see Hull 4th ed., p.550 and
 *       <a href="http://ssrn.com/abstract=2170721">SSRN 2170721</a>).</li>
 * </ul>
 * <p>
 * Cross-validated against v1.42.1 in
 * {@code migration-harness/references/cashflows/black_ibor_coupon_pricer.json}
 * (Phase 5e.5).
 */
public class BlackIborCouponPricer extends IborCouponPricer {

    private final static String missing_caplet_volatility = "missing optionlet volatility";
    private final static String no_forecast_curve = "no forecast curve provided";
    private final static String no_correlation = "no correlation given";
    private final static String unknown_timing_adjustment = "unknown timing adjustment";
    private final TimingAdjustment timingAdjustment_;
    private final Handle< Quote > correlation_;
    /** Mirrors C++ {@code discount_}; {@link Constants#NULL_REAL} when no curve. */
    protected double discount_ = Constants.NULL_REAL;
    // Cached on each initialize().
    private IborCoupon coupon_;
    private double gearing_;
    private double spread_;
    private double accrualPeriod_;
    private Date fixingDate_;
    private Date fixingValueDate_;
    private Date fixingMaturityDate_;
    private double spanningTimeIndexMaturity_;
    private IborIndex iborIndex_;
    /** Default: empty caplet vol, Black76, correlation = 1.0. */
    public BlackIborCouponPricer() {
        this(new Handle< OptionletVolatilityStructure >(), TimingAdjustment.Black76,
                new Handle< Quote >(new SimpleQuote(1.0)));
    }

    //
    // Constructors
    //

    /** Vol-only convenience constructor. */
    public BlackIborCouponPricer(final Handle< OptionletVolatilityStructure > capletVol) {
        this(capletVol, TimingAdjustment.Black76, new Handle< Quote >(new SimpleQuote(1.0)));
    }

    /** Vol + timing-adjustment convenience constructor. */
    public BlackIborCouponPricer(final Handle< OptionletVolatilityStructure > capletVol,
            final TimingAdjustment timingAdjustment) {
        this(capletVol, timingAdjustment, new Handle< Quote >(new SimpleQuote(1.0)));
    }

    /** Full constructor mirroring C++ v1.42.1 signature. */
    public BlackIborCouponPricer(final Handle< OptionletVolatilityStructure > capletVol,
            final TimingAdjustment timingAdjustment, final Handle< Quote > correlation) {
        super(capletVol);
        QL.require(
                timingAdjustment == TimingAdjustment.Black76 || timingAdjustment == TimingAdjustment.BivariateLognormal,
                unknown_timing_adjustment);
        this.timingAdjustment_ = timingAdjustment;
        this.correlation_ = correlation;
        if ( correlation_ != null ) {
            correlation_.addObserver(this);
        }
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        coupon_ = (IborCoupon) coupon;
        iborIndex_ = (IborIndex) coupon_.index();
        gearing_ = coupon_.gearing();
        spread_ = coupon_.spread();
        accrualPeriod_ = coupon_.accrualPeriod();
        QL.require(accrualPeriod_ != 0.0, "null accrual period");

        fixingDate_ = coupon_.fixingDate();

        // Compute cached date helpers locally (C++ caches them on the
        // coupon via initializeCachedData(); JQL keeps the IborCoupon
        // surface unchanged and computes per-pricer-initialize).
        fixingValueDate_ = iborIndex_.fixingCalendar()
                .advance(fixingDate_, iborIndex_.fixingDays(), org.jquantlib.time.TimeUnit.Days);
        fixingMaturityDate_ = iborIndex_.maturityDate(fixingValueDate_);
        spanningTimeIndexMaturity_ = iborIndex_.dayCounter().yearFraction(fixingValueDate_, fixingMaturityDate_);

        final Handle< YieldTermStructure > rateCurve = iborIndex_.termStructure();
        if ( rateCurve == null || rateCurve.empty() ) {
            discount_ = Constants.NULL_REAL;
        } else {
            final Date paymentDate = coupon_.date();
            if ( paymentDate.gt(rateCurve.currentLink().referenceDate()) ) {
                discount_ = rateCurve.currentLink().discount(paymentDate);
            } else {
                discount_ = 1.0;
            }
        }
    }

    //
    // FloatingRateCouponPricer interface
    //

    @Override
    public double swapletPrice() {
        QL.require(discount_ != Constants.NULL_REAL, no_forecast_curve);
        return swapletRate() * accrualPeriod_ * discount_;
    }

    @Override
    public double swapletRate() {
        return gearing_ * adjustedFixing(Constants.NULL_REAL) + spread_;
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        QL.require(discount_ != Constants.NULL_REAL, no_forecast_curve);
        return capletRate(effectiveCap) * accrualPeriod_ * discount_;
    }

    @Override
    public double capletRate(final double effectiveCap) {
        return gearing_ * optionletRate(Option.Type.Call, effectiveCap);
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        QL.require(discount_ != Constants.NULL_REAL, no_forecast_curve);
        return floorletRate(effectiveFloor) * accrualPeriod_ * discount_;
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        return gearing_ * optionletRate(Option.Type.Put, effectiveFloor);
    }

    /**
     * Mirrors C++ {@code BlackIborCouponPricer::optionletPrice}. Multiplies {@link #optionletRate} by accrual period
     * and discount.
     */
    public double optionletPrice(final Option.Type optionType, final double effStrike) {
        QL.require(discount_ != Constants.NULL_REAL, no_forecast_curve);
        return optionletRate(optionType, effStrike) * accrualPeriod_ * discount_;
    }

    //
    // Black-formula core
    //

    /**
     * Mirrors C++ {@code BlackIborCouponPricer::optionletRate}.
     * <p>
     * If the fixing has occurred (fixing date &le; evaluation date), returns intrinsic max(a-b,0). Otherwise dispatches
     * to {@link BlackFormula#blackFormula} (shifted-lognormal vol type) or {@link BlackFormula#bachelierBlackFormula}
     * (normal vol type).
     */
    public double optionletRate(final Option.Type optionType, final double effStrike) {
        if ( fixingDate_.le(new Settings().evaluationDate()) ) {
            // determined fixing -> intrinsic
            double a;
            double b;
            if ( optionType == Option.Type.Call ) {
                a = coupon_.indexFixing();
                b = effStrike;
            } else {
                a = effStrike;
                b = coupon_.indexFixing();
            }
            return Math.max(a - b, 0.0);
        }
        // not yet fixed: Black model
        QL.require(capletVolatility() != null && !capletVolatility().empty(), missing_caplet_volatility);
        final OptionletVolatilityStructure vol = capletVolatility().currentLink();
        final double stdDev = Math.sqrt(vol.blackVariance(fixingDate_, effStrike));
        final double shift = vol.displacement();
        final boolean shiftedLn = vol.volatilityType() == VolatilityType.ShiftedLognormal;
        if ( shiftedLn ) {
            return BlackFormula.blackFormula(optionType, effStrike, adjustedFixing(Constants.NULL_REAL), stdDev, 1.0,
                    shift);
        }
        return BlackFormula.bachelierBlackFormula(optionType, effStrike, adjustedFixing(Constants.NULL_REAL), stdDev,
                1.0);
    }

    /**
     * Mirrors C++ {@code BlackIborCouponPricer::adjustedFixing(Rate)}.
     * <p>
     * Pass {@link Constants#NULL_REAL} to use the coupon's intrinsic fixing.
     */
    public double adjustedFixing(double fixing) {
        if ( fixing == Constants.NULL_REAL || Double.isNaN(fixing) ) {
            fixing = coupon_.indexFixing();
        }
        // No convexity if the pay date equals the index estimation end date,
        // or if the coupon is not in-arrears under Black76.
        if ( !coupon_.isInArrears() && timingAdjustment_ == TimingAdjustment.Black76 ) {
            return fixing;
        }
        final Date d1 = fixingDate_;
        final Date d2 = fixingValueDate_;
        final Date d3 = fixingMaturityDate_;
        if ( coupon_.date().equals(d3) ) {
            return fixing;
        }
        QL.require(capletVolatility() != null && !capletVolatility().empty(), missing_caplet_volatility);
        final OptionletVolatilityStructure vol = capletVolatility().currentLink();
        final Date referenceDate = vol.referenceDate();
        // No accumulated variance -> zero convexity.
        if ( d1.le(referenceDate) ) {
            return fixing;
        }
        final double tau = spanningTimeIndexMaturity_;
        final double variance = vol.blackVariance(d1, fixing);
        final double shift = vol.displacement();
        final boolean shiftedLn = vol.volatilityType() == VolatilityType.ShiftedLognormal;

        double adjustment = shiftedLn
                ? (fixing + shift) * (fixing + shift) * variance * tau / (1.0 + fixing * tau)
                : variance * tau / (1.0 + fixing * tau);

        if ( timingAdjustment_ == TimingAdjustment.BivariateLognormal ) {
            QL.require(correlation_ != null && !correlation_.empty(), no_correlation);
            final Date d4 = coupon_.date();
            final Date d5 = d4.ge(d3) ? d3 : d2;
            final double tau2 = iborIndex_.dayCounter().yearFraction(d5, d4);
            if ( d4.ge(d3) ) {
                adjustment = 0.0;
            }
            // If d4 < d2 (payment before index start), apply Black76 in-arrears.
            if ( tau2 > 0.0 ) {
                final double disc1 = iborIndex_.termStructure().currentLink().discount(d5);
                final double disc2 = iborIndex_.termStructure().currentLink().discount(d4);
                final double fixing2 = (disc1 / disc2 - 1.0) / tau2;
                adjustment -= shiftedLn
                        ? correlation_.currentLink().value() * tau2 * variance * (fixing + shift) * (fixing2 + shift)
                          / (1.0 + fixing2 * tau2)
                        : correlation_.currentLink().value() * tau2 * variance / (1.0 + fixing2 * tau2);
            }
        }
        return fixing + adjustment;
    }

    /** Convenience overload mirroring the C++ default-argument version. */
    public double adjustedFixing() {
        return adjustedFixing(Constants.NULL_REAL);
    }

    public TimingAdjustment timingAdjustment() {
        return timingAdjustment_;
    }

    //
    // Accessors
    //

    public Handle< Quote > correlation() {
        return correlation_;
    }

    /** Mirrors C++ {@code BlackIborCouponPricer::TimingAdjustment}. */
    public enum TimingAdjustment {
        Black76, BivariateLognormal
    }

}
