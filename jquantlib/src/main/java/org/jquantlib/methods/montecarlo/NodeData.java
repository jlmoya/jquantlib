/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k L0.2.

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

package org.jquantlib.methods.montecarlo;

/**
 * Data accumulated at a simulation node for Longstaff-Schwartz / parametric exercise strategies.
 * <p>
 * Mirrors C++ {@code struct NodeData} (ql/methods/montecarlo/nodedata.hpp v1.42.1). Each field directly corresponds to
 * its C++ counterpart:
 * <ul>
 *   <li>{@code exerciseValue} — discounted exercise value at this node</li>
 *   <li>{@code cumulatedCashFlows} — sum of future discounted cash flows if
 *       not exercising at this node</li>
 *   <li>{@code values} — basis-function or parametric-variable values used by
 *       the regression / parametric optimisation; length is
 *       {@code numberOfFunctions()} or {@code numberOfVariables()}</li>
 *   <li>{@code controlValue} — control-variate payoff value (for variance
 *       reduction)</li>
 *   <li>{@code isValid} — {@code false} on paths that are already dead (e.g.
 *       previously exercised) and should be excluded from the regression</li>
 * </ul>
 *
 * @author Jose Moya
 * @see "ql/methods/montecarlo/nodedata.hpp" v1.42.1
 */
public final class NodeData {

    /** Discounted exercise value at this node. */
    public double exerciseValue;

    /** Cumulated discounted future cash flows (no-exercise continuation value). */
    public double cumulatedCashFlows;

    /**
     * Basis-function evaluations (for Longstaff-Schwartz) or parametric variables (for parametric exercise
     * optimisation). Mirrors C++ {@code std::vector<Real> values}.
     */
    public double[] values;

    /** Control-variate value at this node. */
    public double controlValue;

    /**
     * {@code false} if this path is invalid at this exercise date (e.g. already exercised at an earlier date).
     */
    public boolean isValid;

    /**
     * Default constructor — all numerics to zero, {@code isValid = false}, {@code values} is an empty array.
     */
    public NodeData() {
        this.exerciseValue = 0.0;
        this.cumulatedCashFlows = 0.0;
        this.values = new double[0];
        this.controlValue = 0.0;
        this.isValid = false;
    }
}
