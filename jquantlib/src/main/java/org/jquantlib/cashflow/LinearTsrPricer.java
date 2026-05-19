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
package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussKronrodNonAdaptive;
import org.jquantlib.math.integrals.Integrator;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.AtmSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * CMS-coupon pricer using the Linear Terminal Swap Rate (TSR) model.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code LinearTsrPricer} in {@code ql/cashflows/lineartsrpricer.{hpp,cpp}}. The slope
 * parameter is tied to a Gaussian short-rate model (cf. Andersen-Piterbarg ch. 16.3.2).
 *
 * <p>Integration cut-off is set by one of:
 * <ul>
 *   <li>explicit lower / upper rate bounds ({@link Settings#withRateBound}),</li>
 *   <li>strike at which a vanilla swaption has at most a given vega ratio
 *       relative to the ATM ({@link Settings#withVegaRatio}),</li>
 *   <li>strike at which the undeflated payer/receiver price falls below a
 *       threshold ({@link Settings#withPriceThreshold}),</li>
 *   <li>a Black-Scholes std-dev count under an ATM-vol benchmark
 *       ({@link Settings#withBSStdDevs}).</li>
 * </ul>
 *
 * <p>For shifted-lognormal smile sections the supplied bounds are
 * applied to {@code strike + shift}; for normal vol the lower bound is
 * adjusted to {@code min(-upperBound, lowerBound)} unless explicitly set.
 */
public class LinearTsrPricer extends CmsCouponPricer implements MeanRevertingPricer {

    public static final double DEFAULT_LOWER_BOUND = 0.0001;
    public static final double DEFAULT_UPPER_BOUND = 2.0000;
    private final Handle< YieldTermStructure > couponDiscountCurve_;
    private final Settings settings_;
    private final DayCounter volDayCounter_;
    private final Integrator integrator_;
    private double a_;
    private double b_;
    private Handle< Quote > meanReversion_;
    private Handle< YieldTermStructure > forwardCurve_;
    private Handle< YieldTermStructure > discountCurve_;
    private CmsCoupon coupon_;
    private Date today_;
    private Date paymentDate_;
    private Date fixingDate_;
    private double gearing_;
    private double spread_;
    private Period swapTenor_;
    private double spreadLegValue_;
    private double swapRateValue_;
    private double couponDiscountRatio_;
    private double discountCurvePaymentDiscount_;
    private double annuity_;
    @SuppressWarnings( "unused" )
    private SwapIndex swapIndex_;
    @SuppressWarnings( "unused" )
    private VanillaSwap swap_;
    private SmileSection smileSection_;
    private double adjustedLowerBound_;
    private double adjustedUpperBound_;
    public LinearTsrPricer(final Handle< SwaptionVolatilityStructure > swaptionVol,
            final Handle< Quote > meanReversion) {
        this(swaptionVol, meanReversion, new Handle< YieldTermStructure >(), new Settings(), null);
    }

    public LinearTsrPricer(final Handle< SwaptionVolatilityStructure > swaptionVol, final Handle< Quote > meanReversion,
            final Handle< YieldTermStructure > couponDiscountCurve, final Settings settings,
            final Integrator integrator) {
        super(swaptionVol);
        this.meanReversion_ = meanReversion;
        this.couponDiscountCurve_ = couponDiscountCurve;
        this.settings_ = settings;
        this.volDayCounter_ = swaptionVol.currentLink().dayCounter();
        this.integrator_ = (integrator != null) ? integrator : new GaussKronrodNonAdaptive(1.0e-10, 5000, 1.0e-10);

        if ( this.couponDiscountCurve_ != null && !this.couponDiscountCurve_.empty() ) {
            this.couponDiscountCurve_.addObserver(this);
        }
    }

    /**
     * SmileSection vega evaluated via blackFormulaStdDevDerivative * sqrt(T) * 0.01, mirroring C++
     * {@code SmileSection::vega} for ShiftedLognormal sections. (LinearTsrPricer only needs a vega ratio, so the 0.01
     * factor cancels; we keep it here for parity.)
     */
    private static double vega(final SmileSection section, final double strike) {
        QL.require(section.volatilityType() == VolatilityType.ShiftedLognormal,
                "vega for normal smilesection not yet implemented");
        final double atm = section.atmLevel();
        QL.require(atm != Constants.NULL_REAL, "smile section must provide atm level to compute option vega");
        final double stdDev = Math.sqrt(section.variance(strike));
        // C++ blackFormulaVolDerivative = blackFormulaStdDevDerivative * sqrt(T)
        return BlackFormula.blackFormulaStdDevDerivative(strike, atm, stdDev, 1.0, section.shift()) * Math.sqrt(
                section.exerciseTime()) * 0.01;
    }

    @Override
    public double meanReversion() {
        return meanReversion_.currentLink().value();
    }

    @Override
    public void setMeanReversion(final Handle< Quote > meanReversion) {
        if ( meanReversion_ != null ) {
            meanReversion_.deleteObserver(this);
        }
        this.meanReversion_ = meanReversion;
        if ( this.meanReversion_ != null ) {
            this.meanReversion_.addObserver(this);
        }
        update();
    }

    /**
     * GsrG(d): the integral of e^(-mr*(s - fixing)) from fixingDate_ to d, specialised so that mr -> 0 reduces to the
     * year-fraction.
     */
    private double GsrG(final Date d) {
        final double yf = volDayCounter_.yearFraction(fixingDate_, d);
        if ( Math.abs(meanReversion_.currentLink().value()) < 1.0e-4 ) {
            return yf;
        }
        final double mr = meanReversion_.currentLink().value();
        return (1.0 - Math.exp(-mr * yf)) / mr;
    }

    private double singularTerms(final Option.Type type, final double strike) {
        final double omega = (type == Option.Type.Call ? 1.0 : -1.0);
        final double s1 = Math.max(omega * (swapRateValue_ - strike), 0.0) * (a_ * swapRateValue_ + b_);
        final double s2 = (a_ * strike + b_) * smileSection_.optionPrice(strike,
                strike < swapRateValue_ ? Option.Type.Put : Option.Type.Call);
        return s1 + s2;
    }

    private double integrand(final double strike) {
        return 2.0 * a_ * smileSection_.optionPrice(strike,
                strike < swapRateValue_ ? Option.Type.Put : Option.Type.Call);
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        QL.require(coupon instanceof CmsCoupon, "CMS coupon needed");
        coupon_ = (CmsCoupon) coupon;
        gearing_ = coupon_.gearing();
        spread_ = coupon_.spread();

        fixingDate_ = coupon_.fixingDate();
        paymentDate_ = coupon_.date();
        swapIndex_ = coupon_.swapIndex();
        // Java SwapIndex unifies forwarding and discounting curves.
        forwardCurve_ = coupon_.swapIndex().termStructure();
        discountCurve_ = forwardCurve_;

        today_ = new org.jquantlib.Settings().evaluationDate();

        final double couponCurvePaymentDiscount;
        if ( couponDiscountCurve_ != null && !couponDiscountCurve_.empty() && paymentDate_.gt(
                couponDiscountCurve_.currentLink().referenceDate()) ) {
            couponCurvePaymentDiscount = couponDiscountCurve_.currentLink().discount(paymentDate_);
        } else {
            couponCurvePaymentDiscount = 1.0;
        }

        if ( paymentDate_.gt(discountCurve_.currentLink().referenceDate()) ) {
            discountCurvePaymentDiscount_ = discountCurve_.currentLink().discount(paymentDate_);
        } else {
            discountCurvePaymentDiscount_ = 1.0;
        }

        couponDiscountRatio_ = couponCurvePaymentDiscount / discountCurvePaymentDiscount_;
        spreadLegValue_ = spread_ * coupon_.accrualPeriod() * discountCurvePaymentDiscount_ * couponDiscountRatio_;

        if ( fixingDate_.gt(today_) ) {
            swapTenor_ = coupon_.swapIndex().tenor();
            swap_ = coupon_.swapIndex().underlyingSwap(fixingDate_);
            swapRateValue_ = swap_.fairRate();
            annuity_ = 1.0e4 * Math.abs(swap_.fixedLegBPS());
            final Leg swapFixedLeg = swap_.fixedLeg();

            final SmileSection sectionTmp = swaptionVolatility().currentLink()
                    .smileSection(fixingDate_, swapTenor_, false);

            adjustedLowerBound_ = settings_.lowerRateBound_;
            adjustedUpperBound_ = settings_.upperRateBound_;

            if ( sectionTmp.volatilityType() == VolatilityType.Normal ) {
                if ( settings_.defaultBounds_ ) {
                    adjustedLowerBound_ = Math.min(adjustedLowerBound_, -adjustedUpperBound_);
                }
            } else {
                adjustedLowerBound_ -= sectionTmp.shift();
                adjustedUpperBound_ -= sectionTmp.shift();
            }

            // If the section does not provide an atm level, wrap with one.
            if ( sectionTmp.atmLevel() == Constants.NULL_REAL ) {
                smileSection_ = new AtmSmileSection(sectionTmp, swapRateValue_);
            } else {
                smileSection_ = sectionTmp;
            }

            // Compute the linear model parameters a, b.
            double gx = 0.0;
            double gy = 0.0;
            for ( int i = 0; i < swapFixedLeg.size(); ++i ) {
                final Coupon c = (Coupon) swapFixedLeg.get(i);
                final double yf = c.accrualPeriod();
                final Date d = c.date();
                final double pv = yf * discountCurve_.currentLink().discount(d);
                gx += pv * GsrG(d);
                gy += pv;
            }

            final double gamma = gx / gy;
            final Date lastd = swapFixedLeg.get(swapFixedLeg.size() - 1).date();

            a_ = discountCurve_.currentLink().discount(paymentDate_) * (gamma - GsrG(paymentDate_)) / (
                    discountCurve_.currentLink().discount(lastd) * GsrG(lastd) + swapRateValue_ * gy * gamma);

            b_ = discountCurve_.currentLink().discount(paymentDate_) / gy - a_ * swapRateValue_;
        }
    }

    /** Strike at which the smile section's vega is {@code ratio} of ATM vega. */
    private double strikeFromVegaRatio(final double ratio, final Option.Type optionType, final double referenceStrike) {
        double a;
        double b;
        double min;
        double max;
        if ( optionType == Option.Type.Call ) {
            a = swapRateValue_;
            min = referenceStrike;
            max = Math.min(smileSection_.maxStrike(), adjustedUpperBound_);
            b = max;
        } else {
            min = Math.max(smileSection_.minStrike(), adjustedLowerBound_);
            a = min;
            b = swapRateValue_;
            max = referenceStrike;
        }

        final double targetVega = vega(smileSection_, swapRateValue_) * ratio;
        final Ops.DoubleOp h = new Ops.DoubleOp() {
            @Override
            public double op(final double strike) {
                return vega(smileSection_, strike) - targetVega;
            }
        };

        double k = (a + b) / 2.0;
        try {
            final Brent solver = new Brent();
            k = solver.solve(h, 1.0e-5, (a + b) / 2.0, a, b);
        } catch ( final RuntimeException ignored ) {
            // use default value
        }
        return Math.min(Math.max(k, min), max);
    }

    /** Strike at which the smile section's option price equals {@code price}. */
    private double strikeFromPrice(final double price, final Option.Type optionType, final double referenceStrike) {
        double a;
        double b;
        double min;
        double max;
        if ( optionType == Option.Type.Call ) {
            a = swapRateValue_;
            min = referenceStrike;
            max = Math.min(smileSection_.maxStrike(), adjustedUpperBound_);
            b = max;
        } else {
            min = Math.max(smileSection_.minStrike(), adjustedLowerBound_);
            a = min;
            b = swapRateValue_;
            max = referenceStrike;
        }

        final Option.Type type = optionType;
        final double targetPrice = price;
        final Ops.DoubleOp h = new Ops.DoubleOp() {
            @Override
            public double op(final double strike) {
                return smileSection_.optionPrice(strike, type) - targetPrice;
            }
        };

        double k = swapRateValue_;
        try {
            final Brent solver = new Brent();
            k = solver.solve(h, 1.0e-5, swapRateValue_, a, b);
        } catch ( final RuntimeException ignored ) {
            // use default
        }
        return Math.min(Math.max(k, min), max);
    }

    private double optionletPrice(final Option.Type optionType, final double strike) {
        if ( optionType == Option.Type.Call && strike >= adjustedUpperBound_ ) {
            return 0.0;
        }
        if ( optionType == Option.Type.Put && strike <= adjustedLowerBound_ ) {
            return 0.0;
        }

        double lower = strike;
        double upper = strike;

        switch ( settings_.strategy_ ) {
        case RateBound:
            if ( optionType == Option.Type.Call ) {
                upper = adjustedUpperBound_;
            } else {
                lower = adjustedLowerBound_;
            }
            break;
        case VegaRatio: {
            final double bound = strikeFromVegaRatio(settings_.vegaRatio_, optionType, strike);
            if ( optionType == Option.Type.Call ) {
                upper = Math.min(bound, adjustedUpperBound_);
            } else {
                lower = Math.max(bound, adjustedLowerBound_);
            }
            break;
        }
        case PriceThreshold: {
            // C++ uses settings_.vegaRatio_ as the price threshold here too
            // (an apparent typo in v1.42.1, mirrored verbatim).
            final double bound = strikeFromPrice(settings_.vegaRatio_, optionType, strike);
            if ( optionType == Option.Type.Call ) {
                upper = Math.min(bound, adjustedUpperBound_);
            } else {
                lower = Math.max(bound, adjustedLowerBound_);
            }
            break;
        }
        case BSStdDevs: {
            final double atm = smileSection_.atmLevel();
            final double atmVol = smileSection_.volatility(atm);
            final double shift = smileSection_.shift();
            final double lowerTmp;
            final double upperTmp;
            if ( smileSection_.volatilityType() == VolatilityType.ShiftedLognormal ) {
                upperTmp = (atm + shift) * Math.exp(
                        settings_.stdDevs_ * atmVol - 0.5 * atmVol * atmVol * smileSection_.exerciseTime()) - shift;
                lowerTmp = (atm + shift) * Math.exp(
                        -settings_.stdDevs_ * atmVol - 0.5 * atmVol * atmVol * smileSection_.exerciseTime()) - shift;
            } else {
                final double tmp = settings_.stdDevs_ * atmVol * Math.sqrt(smileSection_.exerciseTime());
                upperTmp = atm + tmp;
                lowerTmp = atm - tmp;
            }
            upper = Math.min(upperTmp - shift, adjustedUpperBound_);
            lower = Math.max(lowerTmp - shift, adjustedLowerBound_);
            break;
        }
        default:
            QL.error("Unknown strategy (" + settings_.strategy_ + ")");
        }

        // Compute the relevant integral.
        double result = 0.0;
        if ( upper > lower ) {
            double tmpBound = Math.min(upper, swapRateValue_);
            if ( tmpBound > lower ) {
                result += integrator_.op(this::integrand, lower, tmpBound);
            }
            tmpBound = Math.max(lower, swapRateValue_);
            if ( upper > tmpBound ) {
                result += integrator_.op(this::integrand, tmpBound, upper);
            }
            result *= (optionType == Option.Type.Call ? 1.0 : -1.0);
        }

        result += singularTerms(optionType, strike);

        return annuity_ * result * couponDiscountRatio_ * coupon_.accrualPeriod();
    }

    @Override
    public double swapletPrice() {
        if ( fixingDate_.le(today_) ) {
            final double Rs = coupon_.swapIndex().fixing(fixingDate_);
            return (gearing_ * Rs + spread_) * (coupon_.accrualPeriod() * discountCurvePaymentDiscount_
                    * couponDiscountRatio_);
        }
        final double atmCapletPrice = optionletPrice(Option.Type.Call, swapRateValue_);
        final double atmFloorletPrice = optionletPrice(Option.Type.Put, swapRateValue_);
        return gearing_ * (
                coupon_.accrualPeriod() * discountCurvePaymentDiscount_ * swapRateValue_ * couponDiscountRatio_
                        + atmCapletPrice - atmFloorletPrice) + spreadLegValue_;
    }

    @Override
    public double swapletRate() {
        return swapletPrice() / (coupon_.accrualPeriod() * discountCurvePaymentDiscount_ * couponDiscountRatio_);
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        if ( fixingDate_.le(today_) ) {
            final double Rs = Math.max(coupon_.swapIndex().fixing(fixingDate_) - effectiveCap, 0.0);
            return (gearing_ * Rs) * (coupon_.accrualPeriod() * discountCurvePaymentDiscount_ * couponDiscountRatio_);
        }
        return gearing_ * optionletPrice(Option.Type.Call, effectiveCap);
    }

    @Override
    public double capletRate(final double effectiveCap) {
        return capletPrice(effectiveCap) / (coupon_.accrualPeriod() * discountCurvePaymentDiscount_
                * couponDiscountRatio_);
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        if ( fixingDate_.le(today_) ) {
            final double Rs = Math.max(effectiveFloor - coupon_.swapIndex().fixing(fixingDate_), 0.0);
            return (gearing_ * Rs) * (coupon_.accrualPeriod() * discountCurvePaymentDiscount_ * couponDiscountRatio_);
        }
        return gearing_ * optionletPrice(Option.Type.Put, effectiveFloor);
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        return floorletPrice(effectiveFloor) / (coupon_.accrualPeriod() * discountCurvePaymentDiscount_
                * couponDiscountRatio_);
    }

    /** Tunable settings for the integration cut-off strategy. */
    public static final class Settings {

        public Strategy strategy_ = Strategy.RateBound;
        public double vegaRatio_ = 0.01;
        public double priceThreshold_ = 1.0e-8;
        public double stdDevs_ = 3.0;
        public double lowerRateBound_ = DEFAULT_LOWER_BOUND;
        public double upperRateBound_ = DEFAULT_UPPER_BOUND;
        public boolean defaultBounds_ = true;

        public Settings withRateBound(final double lowerRateBound, final double upperRateBound) {
            strategy_ = Strategy.RateBound;
            lowerRateBound_ = lowerRateBound;
            upperRateBound_ = upperRateBound;
            defaultBounds_ = false;
            return this;
        }

        public Settings withRateBound() {
            return withRateBound(DEFAULT_LOWER_BOUND, DEFAULT_UPPER_BOUND);
        }

        public Settings withVegaRatio(final double vegaRatio) {
            strategy_ = Strategy.VegaRatio;
            vegaRatio_ = vegaRatio;
            lowerRateBound_ = DEFAULT_LOWER_BOUND;
            upperRateBound_ = DEFAULT_UPPER_BOUND;
            defaultBounds_ = true;
            return this;
        }

        public Settings withVegaRatio(final double vegaRatio, final double lowerRateBound,
                final double upperRateBound) {
            strategy_ = Strategy.VegaRatio;
            vegaRatio_ = vegaRatio;
            lowerRateBound_ = lowerRateBound;
            upperRateBound_ = upperRateBound;
            defaultBounds_ = false;
            return this;
        }

        public Settings withPriceThreshold(final double priceThreshold) {
            strategy_ = Strategy.PriceThreshold;
            priceThreshold_ = priceThreshold;
            lowerRateBound_ = DEFAULT_LOWER_BOUND;
            upperRateBound_ = DEFAULT_UPPER_BOUND;
            defaultBounds_ = true;
            return this;
        }

        public Settings withPriceThreshold(final double priceThreshold, final double lowerRateBound,
                final double upperRateBound) {
            strategy_ = Strategy.PriceThreshold;
            priceThreshold_ = priceThreshold;
            lowerRateBound_ = lowerRateBound;
            upperRateBound_ = upperRateBound;
            defaultBounds_ = false;
            return this;
        }

        public Settings withBSStdDevs(final double stdDevs) {
            strategy_ = Strategy.BSStdDevs;
            stdDevs_ = stdDevs;
            lowerRateBound_ = DEFAULT_LOWER_BOUND;
            upperRateBound_ = DEFAULT_UPPER_BOUND;
            defaultBounds_ = true;
            return this;
        }

        public Settings withBSStdDevs(final double stdDevs, final double lowerRateBound, final double upperRateBound) {
            strategy_ = Strategy.BSStdDevs;
            stdDevs_ = stdDevs;
            lowerRateBound_ = lowerRateBound;
            upperRateBound_ = upperRateBound;
            defaultBounds_ = false;
            return this;
        }

        public enum Strategy {RateBound, VegaRatio, PriceThreshold, BSStdDevs}
    }
}
