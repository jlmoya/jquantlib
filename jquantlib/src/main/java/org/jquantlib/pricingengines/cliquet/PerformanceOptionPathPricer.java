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
 Copyright (C) 2008 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.
*/

package org.jquantlib.pricingengines.cliquet;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Path pricer for the cliquet performance option.
 * <p>
 * Sums {@code discount[i-1] * payoff(path[i] / path[i-1])} for {@code i=2..n-1},
 * matching the C++ template form where index 0 is "today" and index 1..n-1 are
 * the reset / fixing times.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code PerformanceOptionPathPricer} declared in
 * {@code ql/pricingengines/cliquet/mcperformanceengine.{hpp,cpp}}.
 */
public final class PerformanceOptionPathPricer extends PathPricer<Path> {

    private final PlainVanillaPayoff payoff;
    private final double[] discounts;

    public PerformanceOptionPathPricer(final Option.Type type,
                                       final double strike,
                                       final double[] discounts) {
        QL.require(strike >= 0.0, "strike less than zero not allowed");
        this.payoff = new PlainVanillaPayoff(type, strike);
        this.discounts = discounts.clone();
    }

    @Override
    public Double op(final Path path) {
        final int n = path.length();
        QL.require(n == discounts.length + 1, "discounts/options mismatch");

        double sum = 0.0;
        for (int i = 2; i < n; i++) {
            sum += discounts[i - 1] * payoff.get(path.get(i) / path.get(i - 1));
        }
        return sum;
    }
}
