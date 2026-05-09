/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.1.

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
 * Multiple-step market-model product abstract base.
 * <p>
 * Mirrors C++ {@code class MultiProductMultiStep}
 * (ql/models/marketmodels/products/multiproductmultistep.{hpp,cpp} v1.42.1).
 * <p>
 * This is the abstract base class that encapsulates the notion of a
 * {@link MarketModelMultiProduct} which can be evaluated in more than one
 * step (aka Rebonato's long jump). Each evolution step corresponds to one
 * rate fixing time; the suggested numeraire is MoneyMarketPlus(1).
 *
 * @see "ql/models/marketmodels/products/multiproductmultistep.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public abstract class MultiProductMultiStep extends MarketModelMultiProduct {

    protected final double[] rateTimes_;
    protected final EvolutionDescription evolution_;

    /**
     * @param rateTimes the rate fixing times (must contain at least two values)
     */
    protected MultiProductMultiStep(final double[] rateTimes) {
        QL.require(rateTimes != null && rateTimes.length > 1,
                "Rate times must contain at least two values");
        this.rateTimes_ = rateTimes.clone();
        final int n = this.rateTimes_.length - 1;
        final double[] evolutionTimes = new double[n];
        final Range[] relevanceRates = new Range[n];
        for (int i = 0; i < n; ++i) {
            evolutionTimes[i] = this.rateTimes_[i];
            relevanceRates[i] = new Range(i, i + 1);
        }
        this.evolution_ = new EvolutionDescription(this.rateTimes_, evolutionTimes, relevanceRates);
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    /** MoneyMarketPlus(1) numeraires: {@code numeraires[i] = i + 1}. */
    @Override
    public int[] suggestedNumeraires() {
        final int n = rateTimes_.length - 1;
        final int[] numeraires = new int[n];
        for (int i = 0; i < n; ++i) {
            numeraires[i] = i + 1;
        }
        return numeraires;
    }
}
