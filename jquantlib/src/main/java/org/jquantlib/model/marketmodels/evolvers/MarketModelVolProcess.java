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
 Copyright (C) 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels.evolvers;

/**
 * Vol-process abstract base for displaced-diffusion LMM with uncorrelated
 * vol process. Called "Shifted BGM" with Heston vol by Brace in
 * "Engineering BGM."
 * <p>
 * The vol process is an external input plugged into evolvers such as
 * {@link SVDDFwdRatePc}.
 *
 * @see "ql/models/marketmodels/evolvers/marketmodelvolprocess.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public abstract class MarketModelVolProcess {

    public abstract int variatesPerStep();

    public abstract int numberSteps();

    public abstract void nextPath();

    public abstract double nextstep(double[] variates);

    public abstract double stepSd();

    public abstract double[] stateVariables();

    public abstract int numberStateVariables();
}
