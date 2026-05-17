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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004 Ferdinando Ametrano
 Copyright (C) 2007, 2008 StatPro Italia srl
*/

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Path pricer for discrete geometric-average-price Asian payoffs.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mc_discr_geom_av_price.{hpp,cpp}}
 * {@code GeometricAPOPathPricer} (Phase 5e.5b-CFC-d-114).
 *
 * <p>Mirrors C++ {@code Real GeometricAPOPathPricer::operator()(const Path&)}:
 * computes the geometric mean of the asset values along the path
 * (skipping the t=0 point unless the time grid explicitly starts at 0),
 * applies the payoff, and discounts.
 *
 * @author JQuantLib
 */
public final class GeometricAPOPathPricer extends PathPricer<Path> {

    private final PlainVanillaPayoff payoff_;
    private final double discount_;
    private final double runningProduct_;
    private final int pastFixings_;

    public GeometricAPOPathPricer(final Option.Type type,
                                  final double strike,
                                  final double discount) {
        this(type, strike, discount, 1.0, 0);
    }

    public GeometricAPOPathPricer(final Option.Type type,
                                  final double strike,
                                  final double discount,
                                  final double runningProduct,
                                  final int pastFixings) {
        QL.require(strike >= 0.0, "negative strike given");
        this.payoff_ = new PlainVanillaPayoff(type, strike);
        this.discount_ = discount;
        this.runningProduct_ = runningProduct;
        this.pastFixings_ = pastFixings;
    }

    @Override
    public Double op(final Path path) {
        final int n = path.length() - 1;
        QL.require(n > 0, "the path cannot be empty");

        double averagePrice = 1.0;
        double product = runningProduct_;
        int fixings = n + pastFixings_;
        if (path.timeGrid().mandatoryTimes().get(0) == 0.0) {
            fixings += 1;
            product *= path.front();
        }
        // care must be taken not to overflow product
        final double maxValue = Double.MAX_VALUE;
        for (int i = 1; i < n + 1; i++) {
            final double price = path.get(i);
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
