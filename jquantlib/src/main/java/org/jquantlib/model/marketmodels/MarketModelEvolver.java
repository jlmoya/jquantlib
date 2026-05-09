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
 * Market-model evolver
 * <p>
 * Abstract base class. The evolver does the actual gritty work of evolving
 * the forward rates from one time to the next.
 *
 * @see "ql/models/marketmodels/evolver.hpp" v1.42.1
 *
 * @author Ueli Hofstetter (original stub)
 * @author Jose Moya (Phase 3h B.8: widen visibility to public per C++)
 */
// Phase 3h decision P3H-2: numeraires() returns int[] (acceptable as-is per
// existing JQuantLib int[] convention). All abstract methods widened from
// package-private to public to match C++ evolver.hpp.
public abstract class MarketModelEvolver {

    public abstract int[] numeraires();

    public abstract double startNewPath();

    public abstract double advanceStep();

    public abstract int currentStep();

    public abstract CurveState currentState();

    public abstract void setInitialState(CurveState curveState);
}
