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
 * Constrained market-model evolver.
 * <p>
 * Abstract base class. Requires extra methods above that of
 * {@link MarketModelEvolver} to let you fix rates via importance sampling.
 * <p>
 * The evolver does the actual gritty work of evolving the forward rates from
 * one time to the next.
 * <p>
 * This is intended to be used for the Fries-Joshi proxy simulation approach
 * to Greeks.
 *
 * @see "ql/models/marketmodels/constrainedevolver.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public abstract class ConstrainedEvolver extends MarketModelEvolver {

    /** Call once to specify which forward / swap rates are eligible for constraint. */
    public abstract void setConstraintType(int[] startIndexOfSwapRate, int[] endIndexOfSwapRate);

    /** Call before each path to set the active constraints and their values. */
    public abstract void setThisConstraint(double[] rateConstraints, boolean[] isConstraintActive);
}
