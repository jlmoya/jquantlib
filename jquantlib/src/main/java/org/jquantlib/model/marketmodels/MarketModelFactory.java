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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006, 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

/**
 * Base interface for market-model factories.
 *
 * <p>Java port of {@code MarketModelFactory} from
 * {@code ql/models/marketmodels/marketmodel.hpp} (QuantLib v1.42.1).
 *
 * <p>C++ {@code MarketModelFactory} extends {@code Observable}; in Java
 * implementations may compose a {@link org.jquantlib.util.DefaultObservable} if observation is required (e.g.
 * {@code FlatVolFactory}).
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/marketmodel.hpp" v1.42.1
 */
public interface MarketModelFactory {

    /**
     * Builds a {@link MarketModel} for the given evolution and number of factors.
     *
     * @param evolution       the evolution description
     * @param numberOfFactors number of stochastic factors driving the model
     * @return the constructed market model
     */
    MarketModel create(final EvolutionDescription evolution, final int numberOfFactors);
}
