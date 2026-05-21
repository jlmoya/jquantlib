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
/*
 Copyright (C) 2025 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.math.matrixutilities.Array;

/**
 * Five-method protocol that lets a bootstrapper participate in a multi-curve global optimisation driven by
 * {@link MultiCurveBootstrap}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MultiCurveBootstrapContributor}
 * ({@code ql/termstructures/globalbootstrap.hpp:40-49}). The protocol splits the contributor's bootstrap into five
 * hooks so {@link MultiCurveBootstrap} can drive a single concatenated Levenberg-Marquardt problem across multiple
 * contributing curves plus their observers.
 *
 * <p>Method semantics:
 * <ul>
 *   <li>{@link #setParentBootstrapper(MultiCurveBootstrap)} — wire the contributor to a parent so that the
 *       contributor's own {@code calculate()} short-circuits and delegates to
 *       {@link MultiCurveBootstrap#runMultiCurveBootstrap()}.</li>
 *   <li>{@link #setupCostFunction()} — initialise the contributor's internal state (dates / times / interpolation)
 *       and return its initial guess vector. Mirrors {@code GlobalBootstrap::setupCostFunction()}.</li>
 *   <li>{@link #setCostFunctionArgument(Array)} — write a chunk of the global guess vector back into the
 *       contributor's curve and update its interpolation.</li>
 *   <li>{@link #evaluateCostFunction()} — compute the contributor's residual vector for the current curve state.</li>
 *   <li>{@link #setToValid()} — mark the contributor's curve as having a converged solution.</li>
 * </ul>
 *
 * @see MultiCurveBootstrap
 * @see MultiCurve
 */
public interface MultiCurveBootstrapContributor {

    void setParentBootstrapper(MultiCurveBootstrap parent);

    Array setupCostFunction();

    void setCostFunctionArgument(Array x);

    Array evaluateCostFunction();

    void setToValid();
}
