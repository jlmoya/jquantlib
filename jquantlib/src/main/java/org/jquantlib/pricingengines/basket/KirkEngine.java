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
 */

/*
 Copyright (C) 2010 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.pricingengines.basket;

import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Pricing engine for a European spread option on two futures using Kirk's 1995 approximation.
 *
 * <p>Kirk, E. "Correlation in the Energy Markets", in <i>Managing Energy
 * Price Risk</i>, London: Risk Publications and Enron, pp. 71-78.</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/pricingengines/basket/kirkengine.{hpp,cpp}}.</p>
 *
 * @author Jose Moya
 */
public class KirkEngine extends SpreadBlackScholesVanillaEngine {

    public KirkEngine(final GeneralizedBlackScholesProcess process1, final GeneralizedBlackScholesProcess process2,
            final double correlation) {
        super(process1, process2, correlation);
    }

    @Override
    protected double calculateSpread(final double f1, final double f2, final double strike,
            final Option.Type optionType, final double variance1, final double variance2, final double df) {

        // Kirk's approximation: model spread as a single Black-process
        // with adjusted "moneyness" forward and effective volatility.
        final double f = f1 / (f2 + strike);
        final double ratio = f2 / (f2 + strike);
        final double v = Math.sqrt(
                variance1 + variance2 * ratio * ratio - 2.0 * rho * Math.sqrt(variance1 * variance2) * ratio);

        // BlackCalculator with strike=1.0, forward=f, stdDev=v, discount=df.
        final BlackCalculator black = new BlackCalculator(new PlainVanillaPayoff(optionType, 1.0), f, v, df);

        return (f2 + strike) * black.value();
    }
}
