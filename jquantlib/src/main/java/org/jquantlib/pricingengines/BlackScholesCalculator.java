/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2006 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.pricingengines;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;

/**
 * Black-Scholes 1973 calculator class.
 * <p>
 * Convenience wrapper over {@link BlackCalculator} that takes spot and growth
 * (dividend discount) instead of forward, and exposes spot-based Greeks.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code BlackScholesCalculator} in
 * {@code ql/pricingengines/blackscholescalculator.{hpp,cpp}}.
 */
public class BlackScholesCalculator extends BlackCalculator {

    private final double spot;
    private final double growth;

    public BlackScholesCalculator(final StrikedTypePayoff payoff,
                                  final double spot,
                                  final double growth,
                                  final double stdDev,
                                  final double discount) {
        super(payoff, spot * growth / discount, stdDev, discount);
        this.spot = spot;
        this.growth = growth;
        QL.require(spot > 0.0, "spot must be positive");
        QL.require(growth > 0.0, "growth must be positive");
    }

    public BlackScholesCalculator(final Option.Type type,
                                  final double strike,
                                  final double spot,
                                  final double growth,
                                  final double stdDev,
                                  final double discount) {
        this(new PlainVanillaPayoff(type, strike), spot, growth, stdDev, discount);
    }

    /** Sensitivity to change in the underlying spot price. */
    public double delta() {
        return delta(spot);
    }

    /** Sensitivity in percent to a percent change in the underlying spot price. */
    public double elasticity() {
        return elasticity(spot);
    }

    /** Second order derivative with respect to change in the underlying spot price. */
    public double gamma() {
        return gamma(spot);
    }

    /** Sensitivity to time to maturity. */
    public double theta(final double maturity) {
        return theta(spot, maturity);
    }

    /** Sensitivity to time to maturity per day (assuming 365 days in a year). */
    public double thetaPerDay(final double maturity) {
        return thetaPerDay(spot, maturity);
    }
}
