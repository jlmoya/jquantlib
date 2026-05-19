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
 Copyright (C) 2003 Neil Firth
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2007 Joseph Wang

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.instruments;

import org.jquantlib.QL;

/**
 * Weighted-average basket payoff: applies the base payoff to a weighted sum of the underlying asset values at
 * exercise.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/instruments/basketoption.hpp::AverageBasketPayoff}.</p>
 *
 * @author Jose Moya
 */
public class AverageBasketPayoff extends BasketPayoff {

    private final double[] weights;

    /**
     * Construct with explicit weights.
     */
    public AverageBasketPayoff(final Payoff basePayoff, final double[] weights) {
        super(basePayoff);
        QL.require(weights != null && weights.length > 0, "empty weights array");
        this.weights = weights.clone();
    }

    /**
     * Construct with equal weights {@code 1/n} on {@code n} assets.
     */
    public AverageBasketPayoff(final Payoff basePayoff, final int n) {
        super(basePayoff);
        QL.require(n > 0, "n must be > 0");
        this.weights = new double[n];
        final double w = 1.0 / n;
        for ( int i = 0; i < n; ++i ) {
            this.weights[i] = w;
        }
    }

    public double[] weights() {
        return weights.clone();
    }

    @Override
    public double accumulate(final double[] a) {
        QL.require(a != null, "null underlying array");
        QL.require(a.length == weights.length, "underlying array size does not match weights");
        double s = 0.0;
        for ( int i = 0; i < a.length; ++i ) {
            s += weights[i] * a[i];
        }
        return s;
    }
}
