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
 Copyright (C) 2020 Jack Gillett
*/

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Multi-path pricer for the Heston-driven discrete geometric-average-price Asian.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mc_discr_geom_av_price_heston.{hpp,cpp}}
 * {@code GeometricAPOHestonPathPricer} (Phase 5e.5b-CFC-d-114).
 *
 * @author JQuantLib
 */
public final class GeometricAPOHestonPathPricer extends PathPricer<MultiPath> {

    private final PlainVanillaPayoff payoff_;
    private final double discount_;
    private final int[] fixingIndices_;
    private final double runningProduct_;
    private final int pastFixings_;

    public GeometricAPOHestonPathPricer(final Option.Type type,
                                        final double strike,
                                        final double discount,
                                        final int[] fixingIndices) {
        this(type, strike, discount, fixingIndices, 1.0, 0);
    }

    public GeometricAPOHestonPathPricer(final Option.Type type,
                                        final double strike,
                                        final double discount,
                                        final int[] fixingIndices,
                                        final double runningProduct,
                                        final int pastFixings) {
        QL.require(strike >= 0.0, "strike less than zero not allowed");
        this.payoff_ = new PlainVanillaPayoff(type, strike);
        this.discount_ = discount;
        this.fixingIndices_ = fixingIndices.clone();
        this.runningProduct_ = runningProduct;
        this.pastFixings_ = pastFixings;
    }

    @Override
    public Double op(final MultiPath multiPath) {
        final Path path = multiPath.get(0);
        final int n = multiPath.pathSize();
        QL.require(n > 0, "the path cannot be empty");

        double averagePrice = 1.0;
        double product = runningProduct_;
        final int fixings = pastFixings_ + fixingIndices_.length;

        // care must be taken not to overflow product
        final double maxValue = Double.MAX_VALUE;
        for (final int fixingIndice : fixingIndices_) {
            final double price = path.get(fixingIndice);
            if (product < maxValue / price) {
                product *= price;
            } else {
                averagePrice *= Math.pow(product, 1.0 / fixings);
                product = price;
            }
        }
        averagePrice *= Math.pow(product, 1.0 / fixings);
        return discount_ * payoff_.get(averagePrice);
    }
}
