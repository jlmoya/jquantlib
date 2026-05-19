/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.2.

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
 Copyright (C) 2006 Ferdinando Ametrano
*/

package org.jquantlib.model.marketmodels.products;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.EvolutionDescription.Range;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;

/**
 * Single-step market-model product abstract base.
 * <p>
 * Mirrors C++ {@code class MultiProductOneStep} (ql/models/marketmodels/products/multiproductonestep.{hpp,cpp}
 * v1.42.1).
 * <p>
 * This is the abstract base class that encapsulates the notion of a {@link MarketModelMultiProduct} which can be
 * evaluated in one single step (aka Rebonato's very long jump). The suggested numeraire is the terminal measure (last
 * rate index).
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/products/multiproductonestep.hpp" v1.42.1
 */
public abstract class MultiProductOneStep extends MarketModelMultiProduct {

    protected final double[] rateTimes_;
    protected final EvolutionDescription evolution_;

    /**
     * @param rateTimes the rate fixing times (must contain at least two values)
     */
    protected MultiProductOneStep(final double[] rateTimes) {
        QL.require(rateTimes != null && rateTimes.length > 1, "Rate times must contain at least two values");
        this.rateTimes_ = rateTimes.clone();
        // single evolution time at the second-to-last rate time
        final double[] evolutionTimes = { this.rateTimes_[this.rateTimes_.length - 2] };
        final Range[] relevanceRates = { new Range(0, this.rateTimes_.length - 1) };
        this.evolution_ = new EvolutionDescription(this.rateTimes_, evolutionTimes, relevanceRates);
    }

    @Override
    public final EvolutionDescription evolution() {
        return evolution_;
    }

    /** Terminal measure: a single numeraire equal to the last rate index. */
    @Override
    public final int[] suggestedNumeraires() {
        return new int[] { rateTimes_.length - 1 };
    }
}
