/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.math.matrixutilities.Array;

/**
 * Interface for additional variables solved alongside the curve data during
 * {@link GlobalBootstrap} optimisation.
 *
 * <p>Faithful port of the abstract base class declared inline in
 * {@code ql/termstructures/globalbootstrap.hpp:69} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}):
 *
 * <pre>{@code
 * class AdditionalBootstrapVariables {
 *   public:
 *     virtual ~AdditionalBootstrapVariables() = default;
 *     virtual Array initialize(bool validData) = 0;
 *     virtual void update(const Array& x) = 0;
 * };
 * }</pre>
 *
 * <p>Concrete implementation: {@link SimpleQuoteVariables} (mirrors C++
 * {@code ql/termstructures/globalbootstrapvars.{hpp,cpp}}).
 */
public interface AdditionalBootstrapVariables {

    /**
     * Compute initial guesses for the additional variables.
     *
     * @param validData {@code true} if the surrounding curve is already in a valid state
     *                  (re-use the underlying quotes' current values); {@code false} if
     *                  the bootstrap is starting cold (seed from the constructor-supplied
     *                  initial guesses).
     * @return guesses in optimiser-space (post-{@code transformInverse}).
     */
    Array initialize(boolean validData);

    /**
     * Push optimiser-space values back into the underlying state (via
     * {@code transformDirect}).
     *
     * @param x optimiser-space values; size equals the number of variables.
     */
    void update(Array x);
}
