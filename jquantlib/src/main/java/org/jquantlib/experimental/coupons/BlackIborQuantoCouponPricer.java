/*
 Copyright (C) 2008 Toyin Akin (C++)

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

package org.jquantlib.experimental.coupons;

import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.Date;

/**
 * BlackIborCouponPricer specialization that applies a quanto adjustment to the
 * forward fixing before the standard Black/Bachelier convexity correction.
 *
 * <p>Port of {@code ql/experimental/coupons/quantocouponpricer.{hpp,cpp}} from
 * C++ QuantLib v1.42.1.
 *
 * <p>The adjustment follows Hull, 6th Edition, page 642, generalised to shifted
 * lognormal and normal caplet volatility types:
 * <ul>
 *   <li>Shifted lognormal: {@code (F + shift) * exp(sigma * sigmaFx * rho * t) - shift}</li>
 *   <li>Normal: {@code F + sigma * sigmaFx * rho * t}</li>
 * </ul>
 * where {@code sigma} is the caplet vol, {@code sigmaFx} the FX rate Black vol
 * at the fixing date and {@code rho} the correlation between underlying and FX.
 *
 * @author Jose Moya
 */
public class BlackIborQuantoCouponPricer extends BlackIborCouponPricer {

    private final Handle<BlackVolTermStructure> fxRateBlackVolatility_;
    private final Handle<Quote> underlyingFxCorrelation_;

    public BlackIborQuantoCouponPricer(final Handle<BlackVolTermStructure> fxRateBlackVolatility,
                                       final Handle<Quote> underlyingFxCorrelation,
                                       final Handle<OptionletVolatilityStructure> capletVolatility) {
        super(capletVolatility);
        this.fxRateBlackVolatility_ = fxRateBlackVolatility;
        this.underlyingFxCorrelation_ = underlyingFxCorrelation;
        if (fxRateBlackVolatility_ != null) {
            fxRateBlackVolatility_.addObserver(this);
        }
        if (underlyingFxCorrelation_ != null) {
            underlyingFxCorrelation_.addObserver(this);
        }
    }

    @Override
    public double adjustedFixing(double fixing) {
        if (fixing == Constants.NULL_REAL || Double.isNaN(fixing)) {
            fixing = coupon_.indexFixing();
        }

        // Apply the quanto adjustment first, then delegate to the parent class
        // (which performs the in-arrears / timing convexity correction).
        final Date d1 = coupon_.fixingDate();
        final OptionletVolatilityStructure vol = capletVolatility().currentLink();
        final Date referenceDate = vol.referenceDate();

        if (d1.gt(referenceDate)) {
            final double t1 = vol.timeFromReference(d1);
            final double fxsigma = fxRateBlackVolatility_.currentLink().blackVol(d1, fixing, true);
            final double sigma = vol.volatility(d1, fixing);
            final double rho = underlyingFxCorrelation_.currentLink().value();

            if (vol.volatilityType() == VolatilityType.ShiftedLognormal) {
                final double dQuantoAdj = Math.exp(sigma * fxsigma * rho * t1);
                final double shift = vol.displacement();
                fixing = (fixing + shift) * dQuantoAdj - shift;
            } else {
                final double dQuantoAdj = sigma * fxsigma * rho * t1;
                fixing += dQuantoAdj;
            }
        }

        return super.adjustedFixing(fixing);
    }
}
