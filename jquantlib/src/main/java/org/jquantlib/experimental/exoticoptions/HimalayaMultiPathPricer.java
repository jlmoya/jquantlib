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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Himalaya multi-path pricer.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/experimental/exoticoptions/mchimalayaengine.{hpp,cpp}}::{@code
 * HimalayaMultiPathPricer} (Phase 4i.5 WI-3).
 *
 * <p>At each fixing date, the asset with the highest yield (relative
 * to its initial value) is selected, its price is added to the
 * running average, and that asset is removed from the basket. After
 * all fixing dates have been processed the average is fed into the
 * vanilla payoff and discounted to today.
 *
 * @author JQuantLib
 */
public class HimalayaMultiPathPricer extends PathPricer<MultiPath> {

    private final Payoff payoff_;
    private final /* @DiscountFactor */ double discount_;

    public HimalayaMultiPathPricer(final Payoff payoff,
                                   final double discount) {
        this.payoff_ = payoff;
        this.discount_ = discount;
    }

    @Override
    public Double op(final MultiPath multiPath) {
        final int numAssets = multiPath.assetNumber();
        final int numNodes = multiPath.pathSize();
        QL.require(numAssets > 0, "no asset given");

        final boolean[] remainingAssets = new boolean[numAssets];
        for (int j = 0; j < numAssets; ++j) {
            remainingAssets[j] = true;
        }
        double averagePrice = 0.0;
        final int fixings = numNodes - 1;
        for (int i = 1; i < numNodes; i++) {
            double bestPrice = 0.0;
            double bestYield = -Double.MAX_VALUE; // mirrors C++ QL_MIN_REAL
            int removeAsset = 0;
            for (int j = 0; j < numAssets; j++) {
                if (remainingAssets[j]) {
                    final double price = multiPath.get(j).get(i);
                    final double yield = price / multiPath.get(j).front();
                    if (yield >= bestYield) {
                        bestPrice = price;
                        bestYield = yield;
                        removeAsset = j;
                    }
                }
            }
            remainingAssets[removeAsset] = false;
            averagePrice += bestPrice;
        }
        averagePrice /= Math.min(fixings, numAssets);

        final double payoff = payoff_.get(averagePrice);
        return payoff * discount_;
    }
}
