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
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Everest multi-path pricer.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/experimental/exoticoptions/mceverestengine.{hpp,cpp}}::{@code
 * EverestMultiPathPricer} (Phase 4i.5 WI-4).
 *
 * <p>The Everest payoff is {@code (1 + min(yield) + guarantee) * notional},
 * where {@code yield_j = S_j(T)/S_j(0) - 1.0} for each asset {@code j}.
 *
 * @author JQuantLib
 */
public class EverestMultiPathPricer extends PathPricer<MultiPath> {

    private final double notional_;
    private final /* @Rate */ double guarantee_;
    private final /* @DiscountFactor */ double discount_;

    public EverestMultiPathPricer(final double notional,
                                  final double guarantee,
                                  final double discount) {
        this.notional_ = notional;
        this.guarantee_ = guarantee;
        this.discount_ = discount;
    }

    @Override
    public Double op(final MultiPath multiPath) {
        final int n = multiPath.pathSize();
        QL.require(n > 0, "the path cannot be empty");

        final int numAssets = multiPath.assetNumber();
        QL.require(numAssets > 0, "there must be some paths");

        // Search the yield min
        double minYield = multiPath.get(0).back() / multiPath.get(0).front() - 1.0;
        for (int j = 1; j < numAssets; ++j) {
            final double yield = multiPath.get(j).back() / multiPath.get(j).front() - 1.0;
            minYield = Math.min(minYield, yield);
        }
        return (1.0 + minYield + guarantee_) * notional_ * discount_;
    }
}
