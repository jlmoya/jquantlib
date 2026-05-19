/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.4.

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
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.model.marketmodels.callability;

import org.jquantlib.methods.montecarlo.ParametricExercise;

/**
 * Parametric exercise strategy for callable market-model products.
 *
 * <p>Java port of {@code MarketModelParametricExercise}
 * (ql/models/marketmodels/callability/marketmodelparametricexercise.hpp v1.42.1).
 *
 * <p>The C++ class derives from both {@code MarketModelNodeDataProvider} and
 * {@code ParametricExercise}; Java uses interface multiple-inheritance with the same combination. Concrete
 * implementations supply both the node data (state-variable values) and the parametric exercise rule.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/callability/marketmodelparametricexercise.hpp" v1.42.1
 */
public interface MarketModelParametricExercise extends MarketModelNodeDataProvider, ParametricExercise {

    /**
     * Returns a newly-allocated copy of this parametric exercise. Mirrors C++
     * {@code std::unique_ptr<MarketModelParametricExercise> clone()}.
     */
    MarketModelParametricExercise clone();

    /**
     * Default implementation returns {@link ParametricExercise#numberOfVariables()}, matching C++
     * {@code numberOfData() = numberOfVariables()}.
     */
    @Override
    default int[] numberOfData() {
        return numberOfVariables();
    }
}
