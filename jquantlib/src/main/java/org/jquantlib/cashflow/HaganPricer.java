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
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;

/**
 * Base class for CMS-coupon pricing via Hagan static replication.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code HaganPricer} in
 * {@code ql/cashflows/conundrumpricer.{hpp,cpp}}. Concrete subclasses
 * supply the {@code optionletPrice(Type, strike)} integral:
 * <ul>
 *   <li>{@link AnalyticHaganPricer} — Hagan eq. 3.5b/3.5c closed form.</li>
 *   <li>{@link NumericHaganPricer} — Gauss-Kronrod numerical replication.</li>
 * </ul>
 *
 * <p>{@code initialize(coupon)} pins the swapRate / annuity / discount /
 * G-function to the supplied CMS coupon's underlying swap. After that,
 * {@code swapletPrice}, {@code capletPrice}, {@code floorletPrice} are
 * driven by Hagan's replication formulas.
 */
public abstract class HaganPricer extends CmsCouponPricer implements MeanRevertingPricer {

    protected YieldTermStructure rateCurve_;
    protected GFunctionFactory.YieldCurveModel modelOfYieldCurve_;
    protected GFunction gFunction_;
    protected CmsCoupon coupon_;
    protected Date paymentDate_;
    protected Date fixingDate_;
    protected double swapRateValue_;
    protected double discount_;
    protected double annuity_;
    protected double gearing_;
    protected double spread_;
    protected double spreadLegValue_;
    protected double cutoffForCaplet_ = 2.0;
    protected double cutoffForFloorlet_ = 0.0;
    protected Handle<Quote> meanReversion_;
    protected Period swapTenor_;
    protected VanillaOptionPricer vanillaOptionPricer_;

    protected HaganPricer(final Handle<SwaptionVolatilityStructure> swaptionVol,
                          final GFunctionFactory.YieldCurveModel modelOfYieldCurve,
                          final Handle<Quote> meanReversion) {
        super(swaptionVol);
        this.modelOfYieldCurve_ = modelOfYieldCurve;
        this.meanReversion_ = meanReversion;
        if (this.meanReversion_ != null) {
            this.meanReversion_.addObserver(this);
        }
    }

    @Override
    public abstract double swapletPrice();

    /** Concrete subclasses implement the inner option price. */
    protected abstract double optionletPrice(Option.Type optionType, double strike);

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        QL.require(coupon instanceof CmsCoupon, "CMS coupon needed");
        coupon_ = (CmsCoupon) coupon;
        gearing_ = coupon_.gearing();
        spread_ = coupon_.spread();
        final double accrualPeriod = coupon_.accrualPeriod();
        QL.require(accrualPeriod != 0.0, "null accrual period");

        fixingDate_ = coupon_.fixingDate();
        paymentDate_ = coupon_.date();
        final SwapIndex swapIndex = coupon_.swapIndex();
        // Java SwapIndex exposes a single termStructure() (no separate
        // forwarding/discounting split). Use that as the rate curve.
        rateCurve_ = swapIndex.termStructure().currentLink();

        final Date today = new Settings().evaluationDate();

        if (paymentDate_.gt(today)) {
            discount_ = rateCurve_.discount(paymentDate_);
        } else {
            discount_ = 1.0;
        }

        spreadLegValue_ = spread_ * accrualPeriod * discount_;

        if (fixingDate_.gt(today)) {
            swapTenor_ = swapIndex.tenor();
            final VanillaSwap swap = swapIndex.underlyingSwap(fixingDate_);

            swapRateValue_ = swap.fairRate();

            final double bp = 1.0e-4;
            annuity_ = Math.abs(swap.fixedLegBPS() / bp);

            final int q = swapIndex.fixedLegTenor().frequency().toInteger();
            final Schedule schedule = swap.fixedSchedule();
            final DayCounter dc = swapIndex.dayCounter();
            final double startTime = dc.yearFraction(rateCurve_.referenceDate(), swap.startDate());
            final double swapFirstPaymentTime = dc.yearFraction(rateCurve_.referenceDate(), schedule.date(1));
            final double paymentTime = dc.yearFraction(rateCurve_.referenceDate(), paymentDate_);
            final double delta = (paymentTime - startTime) / (swapFirstPaymentTime - startTime);

            switch (modelOfYieldCurve_) {
                case Standard:
                    gFunction_ = GFunctionFactory.newGFunctionStandard(q, delta, swapTenor_.length());
                    break;
                case ExactYield:
                    gFunction_ = GFunctionFactory.newGFunctionExactYield(coupon_);
                    break;
                case ParallelShifts: {
                    final Handle<Quote> nullMeanReversionQuote =
                            new Handle<Quote>(new SimpleQuote(0.0));
                    gFunction_ = GFunctionFactory.newGFunctionWithShifts(coupon_, nullMeanReversionQuote);
                    break;
                }
                case NonParallelShifts:
                    gFunction_ = GFunctionFactory.newGFunctionWithShifts(coupon_, meanReversion_);
                    break;
                default:
                    QL.error("unknown/illegal gFunction type");
            }

            vanillaOptionPricer_ = new MarketQuotedOptionPricer(
                    swapRateValue_, fixingDate_, swapTenor_,
                    swaptionVolatility().currentLink());
        }
    }

    @Override
    public double meanReversion() {
        return meanReversion_.currentLink().value();
    }

    @Override
    public void setMeanReversion(final Handle<Quote> meanReversion) {
        if (meanReversion_ != null) {
            meanReversion_.deleteObserver(this);
        }
        this.meanReversion_ = meanReversion;
        if (this.meanReversion_ != null) {
            this.meanReversion_.addObserver(this);
        }
        update();
    }

    @Override
    public double swapletRate() {
        return swapletPrice() / (coupon_.accrualPeriod() * discount_);
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        // caplet is equivalent to a call option on the fixing
        final Date today = new Settings().evaluationDate();
        if (fixingDate_.le(today)) {
            // the fixing is determined
            final double Rs = Math.max(coupon_.swapIndex().fixing(fixingDate_) - effectiveCap, 0.0);
            return (gearing_ * Rs) * (coupon_.accrualPeriod() * discount_);
        }
        double capletPx = 0.0;
        if (swaptionVolatility().currentLink().volatilityType() == VolatilityType.ShiftedLognormal) {
            final double cutoffNearZero = 1.0e-10;
            if (effectiveCap < cutoffForCaplet_) {
                final double effectiveStrikeForMax = Math.max(effectiveCap, cutoffNearZero);
                capletPx = optionletPrice(Option.Type.Call, effectiveStrikeForMax);
            }
        } else {
            capletPx = optionletPrice(Option.Type.Call, effectiveCap);
        }
        return gearing_ * capletPx;
    }

    @Override
    public double capletRate(final double effectiveCap) {
        return capletPrice(effectiveCap) / (coupon_.accrualPeriod() * discount_);
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        // floorlet is equivalent to a put option on the fixing
        final Date today = new Settings().evaluationDate();
        if (fixingDate_.le(today)) {
            final double Rs = Math.max(effectiveFloor - coupon_.swapIndex().fixing(fixingDate_), 0.0);
            return (gearing_ * Rs) * (coupon_.accrualPeriod() * discount_);
        }
        double floorletPx = 0.0;
        if (swaptionVolatility().currentLink().volatilityType() == VolatilityType.ShiftedLognormal) {
            final double cutoffNearZero = 1.0e-10;
            if (effectiveFloor > cutoffForFloorlet_) {
                final double effectiveStrikeForMin = Math.max(effectiveFloor, cutoffNearZero);
                floorletPx = optionletPrice(Option.Type.Put, effectiveStrikeForMin);
            }
        } else {
            floorletPx = optionletPrice(Option.Type.Put, effectiveFloor);
        }
        return gearing_ * floorletPx;
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        return floorletPrice(effectiveFloor) / (coupon_.accrualPeriod() * discount_);
    }
}
