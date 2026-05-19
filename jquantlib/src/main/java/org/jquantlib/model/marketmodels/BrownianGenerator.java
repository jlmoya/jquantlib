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
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

/**
 * Brownian-motion generator interface for market-model simulations.
 * <p>
 * Each call to {@link #nextStep(double[])} fills the supplied array with the Gaussian variates for one evolution step
 * and returns the path-weight contribution from this step (always 1.0 for pseudo-random generators; Sobol-bridge
 * variants may return a non-trivial weight).
 * <p>
 * {@link #nextPath()} starts a new path and returns the path's total weight (typically 1.0; non-trivial only for
 * low-discrepancy sequences).
 *
 * @author Ueli Hofstetter (original stub)
 * @author Jose Moya (Phase 3h B.7-align: signature fix to match C++ out-param)
 * @see "ql/models/marketmodels/browniangenerator.hpp" v1.42.1
 */
// Phase 3h decision P3H-1: nextStep() → nextStep(double[]) to match C++
//   virtual Real nextStep(std::vector<Real>&) = 0
public abstract class BrownianGenerator {

    public BrownianGenerator() {
    }

    /**
     * Fills the supplied array with the Gaussian variates for the next step and returns the per-step weight
     * contribution.
     */
    public abstract double nextStep(double[] output);

    /**
     * Starts a new path and returns the path's weight.
     */
    public abstract double nextPath();

    /** @return the number of factors (= length of arrays passed to nextStep) */
    public abstract int numberOfFactors();

    /** @return the number of steps in a single path */
    public abstract int numberOfSteps();
}
