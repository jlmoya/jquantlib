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

import org.jquantlib.Settings;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.time.Date;

/**
 * Closed-form Hagan CMS-coupon pricer (eq. 3.5b/3.5c, 3.4c).
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticHaganPricer} in
 * {@code ql/cashflows/conundrumpricer.{hpp,cpp}}. Implements the
 * second-order static-replication formulas using only G' at the
 * forward swap rate.
 */
public class AnalyticHaganPricer extends HaganPricer {

    public AnalyticHaganPricer(final Handle<SwaptionVolatilityStructure> swaptionVol,
                               final GFunctionFactory.YieldCurveModel modelOfYieldCurve,
                               final Handle<Quote> meanReversion) {
        super(swaptionVol, modelOfYieldCurve, meanReversion);
    }

    /** Hagan, eqs. 3.5b (shifted-lognormal) and 3.5c (normal). */
    @Override
    protected double optionletPrice(final Option.Type optionType, final double strike) {
        final double variance = swaptionVolatility().currentLink()
                .blackVariance(fixingDate_, swapTenor_, swapRateValue_, false);
        final double firstDerivativeOfGAtForwardValue = gFunction_.firstDerivative(swapRateValue_);
        double price = 0.0;

        final double CK = vanillaOptionPricer_.evaluate(strike, optionType, annuity_);
        price += (discount_ / annuity_) * CK;

        if (swaptionVolatility().currentLink().volatilityType() == VolatilityType.ShiftedLognormal) {
            final double sqrtSigma2T = Math.sqrt(variance);
            final double lnRoverK = Math.log(swapRateValue_ / strike);
            final double d32 = (lnRoverK + 1.5 * variance) / sqrtSigma2T;
            final double d12 = (lnRoverK + 0.5 * variance) / sqrtSigma2T;
            final double dminus12 = (lnRoverK - 0.5 * variance) / sqrtSigma2T;

            final CumulativeNormalDistribution cumulativeOfNormal = new CumulativeNormalDistribution();
            final int sign = optionType.toInteger();
            final double N32 = cumulativeOfNormal.op(sign * d32);
            final double N12 = cumulativeOfNormal.op(sign * d12);
            final double Nminus12 = cumulativeOfNormal.op(sign * dminus12);

            price += sign * firstDerivativeOfGAtForwardValue * annuity_ * swapRateValue_
                     * (swapRateValue_ * Math.exp(variance) * N32
                        - (swapRateValue_ + strike) * N12
                        + strike * Nminus12);
        } else {
            final double sqrtSigma2T = Math.sqrt(variance);
            final double d = (swapRateValue_ - strike) / sqrtSigma2T;

            final CumulativeNormalDistribution cumulativeOfNormal = new CumulativeNormalDistribution();
            final int sign = optionType.toInteger();
            final double N = cumulativeOfNormal.op(sign * d);
            price += sign * firstDerivativeOfGAtForwardValue * annuity_ * variance * N;
        }

        price *= coupon_.accrualPeriod();
        return price;
    }

    /** Hagan eq. 3.4c. */
    @Override
    public double swapletPrice() {
        final Date today = new Settings().evaluationDate();
        if (fixingDate_.le(today)) {
            // the fixing is determined
            final double Rs = coupon_.swapIndex().fixing(fixingDate_);
            return (gearing_ * Rs + spread_) * (coupon_.accrualPeriod() * discount_);
        }

        final double variance = swaptionVolatility().currentLink()
                .blackVariance(fixingDate_, swapTenor_, swapRateValue_, false);
        final double firstDerivativeOfGAtForwardValue = gFunction_.firstDerivative(swapRateValue_);
        double price = 0.0;
        price += discount_ * swapRateValue_;
        if (swaptionVolatility().currentLink().volatilityType() == VolatilityType.ShiftedLognormal) {
            price += firstDerivativeOfGAtForwardValue * annuity_ * swapRateValue_
                     * swapRateValue_ * (Math.exp(variance) - 1.0);
        } else {
            price += firstDerivativeOfGAtForwardValue * annuity_ * variance;
        }
        return (gearing_ * price + spread_ * discount_) * coupon_.accrualPeriod();
    }
}
