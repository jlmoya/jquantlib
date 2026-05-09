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
 Copyright (C) 2024 Klaus Spanderen

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
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Pricing engine for a European spread option on two futures using the
 * Bjerksund and Stensland (2014) closed-form approximation.
 *
 * <p>P. Bjerksund and G. Stensland, "Closed form spread option valuation",
 * Quantitative Finance 14 (2014), pp. 1785-1794.</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/pricingengines/basket/bjerksundstenslandspreadengine.{hpp,cpp}}.</p>
 *
 * @author Jose Moya
 */
public class BjerksundStenslandSpreadEngine extends SpreadBlackScholesVanillaEngine {

    private static final CumulativeNormalDistribution PHI =
            new CumulativeNormalDistribution();

    public BjerksundStenslandSpreadEngine(
            final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2,
            final double correlation) {
        super(process1, process2, correlation);
    }

    @Override
    protected double calculateSpread(
            final double f1, final double f2, final double k,
            final Option.Type optionType,
            final double variance1, final double variance2,
            final double df) {

        final double cp = (optionType == Option.Type.Call) ? 1.0 : -1.0;

        final double a = f2 + k;
        final double b = f2 / a;

        final double sigma1 = Math.sqrt(variance1);
        final double sigma2 = Math.sqrt(variance2);

        final double stdev = Math.sqrt(
                variance1 + b * b * variance2 - 2.0 * rho * b * sigma1 * sigma2);

        final double lfa = Math.log(f1 / a);

        final double d1 =
                (lfa + (0.5 * variance1 + 0.5 * b * b * variance2 - b * rho * sigma1 * sigma2)) / stdev;
        final double d2 =
                (lfa + (-0.5 * variance1 + variance2 * b * (0.5 * b - 1.0) + rho * sigma1 * sigma2)) / stdev;
        final double d3 =
                (lfa + (-0.5 * variance1 + 0.5 * b * b * variance2)) / stdev;

        return df * cp * (f1 * PHI.op(cp * d1) - f2 * PHI.op(cp * d2) - k * PHI.op(cp * d3));
    }
}
