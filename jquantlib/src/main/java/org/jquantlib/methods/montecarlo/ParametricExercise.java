/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k L0.3.

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

package org.jquantlib.methods.montecarlo;

/**
 * Parametric exercise strategy interface.
 * <p>
 * Mirrors C++ {@code class ParametricExercise}
 * (ql/methods/montecarlo/parametricexercise.hpp v1.42.1).
 * <p>
 * A parametric exercise strategy decides whether to exercise by evaluating a
 * function of the state variables with a set of optimisable parameters. The
 * parameters are calibrated offline via
 * {@link GenericEarlyExercise#optimize}, then used online during pricing.
 * <p>
 * There is one set of parameters per exercise opportunity; each exercise may
 * have a different number of variables and parameters.
 *
 * @see GenericEarlyExercise
 * @see "ql/methods/montecarlo/parametricexercise.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public interface ParametricExercise {

    /**
     * Returns the number of state variables at each exercise opportunity.
     * <p>
     * {@code numberOfVariables()[i]} is the length of the {@code variables}
     * array passed to {@link #exercise} for exercise {@code i}.
     * Mirrors C++ {@code std::vector<Size> numberOfVariables() const}.
     */
    int[] numberOfVariables();

    /**
     * Returns the number of optimisable parameters at each exercise
     * opportunity.
     * <p>
     * {@code numberOfParameters()[i]} is the length of the {@code parameters}
     * array passed to {@link #exercise} for exercise {@code i}.
     * Mirrors C++ {@code std::vector<Size> numberOfParameters() const}.
     */
    int[] numberOfParameters();

    /**
     * Returns {@code true} if the strategy recommends exercising at exercise
     * opportunity {@code exerciseNumber} given the current parameter vector
     * and state variables.
     * Mirrors C++ {@code bool exercise(Size, const std::vector<Real>&,
     * const std::vector<Real>&) const}.
     *
     * @param exerciseNumber index of the exercise opportunity (0-based)
     * @param parameters     parameter vector of length
     *                       {@code numberOfParameters()[exerciseNumber]}
     * @param variables      state-variable vector of length
     *                       {@code numberOfVariables()[exerciseNumber]}
     * @return {@code true} to exercise, {@code false} to continue
     */
    boolean exercise(int exerciseNumber, double[] parameters, double[] variables);

    /**
     * Fills {@code parameters} with an initial guess for exercise opportunity
     * {@code exerciseNumber}, used as the starting point for the optimiser.
     * Mirrors C++ {@code void guess(Size, std::vector<Real>&) const}.
     *
     * @param exerciseNumber index of the exercise opportunity (0-based)
     * @param parameters     output array of length
     *                       {@code numberOfParameters()[exerciseNumber]}
     *                       — filled in-place
     */
    void guess(int exerciseNumber, double[] parameters);
}
