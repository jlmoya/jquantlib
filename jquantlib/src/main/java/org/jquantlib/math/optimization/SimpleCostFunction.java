/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.math.optimization;

import java.util.function.Function;

import org.jquantlib.math.matrixutilities.Array;

/**
 * Convenience cost-function wrapper around a function that maps {@code Array → Array}.
 * <p>
 * Faithful Java port of QuantLib v1.42.1 {@code SimpleCostFunction} template
 * (in {@code ql/math/optimization/costfunction.hpp}).
 * The C++ template parameter is a callable; here we use {@link Function} as the
 * idiomatic Java equivalent.
 *
 * @author Jose Moya
 */
public final class SimpleCostFunction extends CostFunction {

    private final Function< Array, Array > values_;

    public SimpleCostFunction(final Function< Array, Array > values) {
        this.values_ = values;
    }

    @Override
    public Array values(final Array x) /* @ReadOnly */ {
        return values_.apply(x);
    }
}
