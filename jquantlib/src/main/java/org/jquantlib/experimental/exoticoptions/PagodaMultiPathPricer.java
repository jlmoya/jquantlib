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

import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Pagoda multi-path pricer.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/experimental/exoticoptions/mcpagodaengine.{hpp,cpp}}::{@code
 * PagodaMultiPathPricer} (Phase 4i.5 WI-5).
 *
 * <p>The Pagoda payoff is {@code discount * fraction *
 * max(0, min(roof, averagePerformance))}, where the average
 * performance is taken over each (asset, fixing) pair via
 * {@code S(t-1) * (S(t)/S(t-1) - 1)} and divided by the number
 * of assets.
 *
 * @author JQuantLib
 */
public class PagodaMultiPathPricer extends PathPricer<MultiPath> {

    private final /* @DiscountFactor */ double discount_;
    private final double roof_;
    private final double fraction_;

    public PagodaMultiPathPricer(final double roof,
                                 final double fraction,
                                 final double discount) {
        this.discount_ = discount;
        this.roof_ = roof;
        this.fraction_ = fraction;
    }

    @Override
    public Double op(final MultiPath multiPath) {
        final int numAssets = multiPath.assetNumber();
        final int numSteps = multiPath.pathSize();

        double averagePerformance = 0.0;
        for (int i = 1; i < numSteps; i++) {
            for (int j = 0; j < numAssets; j++) {
                averagePerformance +=
                        multiPath.get(j).front()
                        * (multiPath.get(j).get(i) / multiPath.get(j).get(i - 1) - 1.0);
            }
        }
        averagePerformance /= numAssets;

        return discount_ * fraction_
                * Math.max(0.0, Math.min(roof_, averagePerformance));
    }
}
