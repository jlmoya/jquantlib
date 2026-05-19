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
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2007, 2008 StatPro Italia srl
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Path pricer for the European vanilla payoff.
 *
 * <p>Java port of the inline {@code EuropeanPathPricer} class declared
 * in {@code QuantLib v1.42.1 ql/pricingengines/vanilla/mceuropeanengine.hpp} (Phase 5h.5-MC-INFRA WI-9).
 *
 * <p>Mirrors C++ {@code Real EuropeanPathPricer::operator()(const Path&)}:
 * the payoff is evaluated at the path's terminal value and discounted by the (precomputed) constant discount factor.
 *
 * @author JQuantLib
 */
public final class EuropeanPathPricer extends PathPricer< Path > {

    private final PlainVanillaPayoff payoff_;
    private final double discount_;

    public EuropeanPathPricer(final Option.Type type, final double strike, final double discount) {
        QL.require(strike >= 0.0, "strike less than zero not allowed");
        this.payoff_ = new PlainVanillaPayoff(type, strike);
        this.discount_ = discount;
    }

    @Override
    public Double op(final Path path) {
        QL.require(!path.empty(), "the path cannot be empty");
        return payoff_.get(path.back()) * discount_;
    }
}
