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
 Copyright (C) 2005 Klaus Spanderen
 Copyright (C) 2007 StatPro Italia srl
*/
package org.jquantlib.methods.montecarlo;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;

/**
 * Path pricer for European vanilla payoffs on a (multi-)path whose first sub-path carries the underlying asset
 * trajectory.
 *
 * <p>Java port of the inline {@code EuropeanHestonPathPricer} class
 * declared in QuantLib v1.42.1 {@code ql/pricingengines/vanilla/mceuropeanhestonengine.hpp} (Phase 5h.5-Bates-b).
 *
 * <p>Mirrors C++ {@code Real EuropeanHestonPathPricer::operator()(const
 * MultiPath&)}: the payoff is evaluated at the asset's terminal value (sub-path 0) and discounted by the precomputed
 * constant discount factor. Sub-path 1 (variance) is unused — it only matters for the path generation, not the payoff.
 *
 * <p>Lives in {@code methods.montecarlo} (rather than
 * {@code pricingengines.vanilla} alongside its sibling {@link org.jquantlib.pricingengines.vanilla.EuropeanPathPricer})
 * because it depends only on {@link MultiPath} and is reusable from any multi-asset MC engine (Heston, Bates, HHW,
 * ...). The pure single European pricer continues to live with the engine that popularised it.
 *
 * @author JQuantLib
 */
public class EuropeanHestonPathPricer extends PathPricer< MultiPath > {

    private final PlainVanillaPayoff payoff_;
    private final double discount_;

    public EuropeanHestonPathPricer(final Option.Type type, final double strike, final double discount) {
        QL.require(strike >= 0.0, "strike less than zero not allowed");
        this.payoff_ = new PlainVanillaPayoff(type, strike);
        this.discount_ = discount;
    }

    @Override
    public Double op(final MultiPath multiPath) {
        final Path path = multiPath.get(0);
        final int n = multiPath.pathSize();
        QL.require(n > 0, "the path cannot be empty");
        return payoff_.get(path.back()) * discount_;
    }
}
