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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003 Neil Firth
 Copyright (C) 2003 Ferdinando Ametrano
*/

package org.jquantlib.pricingengines.basket;

import org.jquantlib.QL;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * European-basket multi-path pricer.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/basket/mceuropeanbasketengine.{hpp,cpp}}::{@code
 * EuropeanMultiPathPricer} (Phase 4i.5 WI-1).
 *
 * <p>Computes {@code basketPayoff(finalPrices) * discount} where
 * {@code finalPrices[j] = multiPath[j].back()} for each asset {@code j}.
 *
 * @author JQuantLib
 */
public class EuropeanMultiPathPricer extends PathPricer<MultiPath> {

    private final BasketPayoff payoff_;
    private final /* @DiscountFactor */ double discount_;

    public EuropeanMultiPathPricer(final BasketPayoff payoff,
                                   final double discount) {
        this.payoff_ = payoff;
        this.discount_ = discount;
    }

    @Override
    public Double op(final MultiPath multiPath) {
        final int n = multiPath.pathSize();
        QL.require(n > 0, "the path cannot be empty");

        final int numAssets = multiPath.assetNumber();
        QL.require(numAssets > 0, "there must be some paths");

        // calculate the final price of each asset
        final double[] finalPrice = new double[numAssets];
        for (int j = 0; j < numAssets; j++) {
            finalPrice[j] = multiPath.get(j).back();
        }
        return payoff_.get(finalPrice) * discount_;
    }
}
