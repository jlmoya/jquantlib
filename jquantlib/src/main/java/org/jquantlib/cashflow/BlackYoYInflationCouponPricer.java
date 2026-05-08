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

import org.jquantlib.instruments.Option;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;

/**
 * Black-formula pricer for capped/floored YoY inflation coupons.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::BlackYoYInflationCouponPricer}
 * ({@code ql/cashflows/inflationcouponpricer.{hpp,cpp}}).
 *
 * <p>The optionlet rate uses the standard Black formula on
 * {@code (forward, strike, stdDev)}. The discounting is applied externally
 * by the base class via {@link #optionletPrice(org.jquantlib.instruments.Option.Type, double)}.
 *
 * @author JQuantLib migration team (Phase 2r C.3)
 */
public class BlackYoYInflationCouponPricer extends YoYInflationCouponPricer {

    public BlackYoYInflationCouponPricer() {
        super();
    }

    public BlackYoYInflationCouponPricer(
            final Handle<YieldTermStructure> nominalTermStructure) {
        super(nominalTermStructure);
    }

    public BlackYoYInflationCouponPricer(
            final Handle<YoYOptionletVolatilitySurface> capletVol,
            final Handle<YieldTermStructure> nominalTermStructure) {
        super(capletVol, nominalTermStructure);
    }

    @Override
    protected double optionletPriceImp(final Option.Type optionType,
                                       final double effStrike,
                                       final double forward,
                                       final double stdDev) {
        return BlackFormula.blackFormula(optionType, effStrike, forward, stdDev);
    }
}
