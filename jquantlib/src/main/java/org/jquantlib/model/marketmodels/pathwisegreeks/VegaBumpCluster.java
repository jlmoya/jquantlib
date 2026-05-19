/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.10.

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

package org.jquantlib.model.marketmodels.pathwisegreeks;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.MarketModel;

/**
 * A "cluster" of pseudo-root elements that get bumped together when computing vegas. When bumping vols, bumping every
 * pseudo-root element individually seems excessive, so we couple some together.
 *
 * <p>A cluster is a tensor-product of three half-open ranges:
 * {@code [factorBegin, factorEnd) × [rateBegin, rateEnd) × [stepBegin, stepEnd)}.
 *
 * <p>Mirrors C++ {@code VegaBumpCluster}
 * (ql/models/marketmodels/pathwisegreeks/vegabumpcluster.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class VegaBumpCluster {

    private final int factorBegin_;
    private final int factorEnd_;
    private final int rateBegin_;
    private final int rateEnd_;
    private final int stepBegin_;
    private final int stepEnd_;

    public VegaBumpCluster(final int factorBegin, final int factorEnd, final int rateBegin, final int rateEnd,
            final int stepBegin, final int stepEnd) {
        QL.require(factorBegin < factorEnd, "must have factorBegin_ < factorEnd_ in VegaBumpCluster");
        QL.require(rateBegin < rateEnd, "must have rateBegin_ < rateEnd_ in VegaBumpCluster");
        QL.require(stepBegin < stepEnd, "must have stepBegin_ < stepEnd_ in VegaBumpCluster");
        this.factorBegin_ = factorBegin;
        this.factorEnd_ = factorEnd;
        this.rateBegin_ = rateBegin;
        this.rateEnd_ = rateEnd;
        this.stepBegin_ = stepBegin;
        this.stepEnd_ = stepEnd;
    }

    /** Tests whether this cluster overlaps any element in {@code comparee}. */
    public boolean doesIntersect(final VegaBumpCluster comparee) {
        if ( factorEnd_ <= comparee.factorBegin_ )
            return false;
        if ( rateEnd_ <= comparee.rateBegin_ )
            return false;
        if ( stepEnd_ <= comparee.stepBegin_ )
            return false;

        if ( comparee.factorEnd_ <= factorBegin_ )
            return false;
        if ( comparee.rateEnd_ <= rateBegin_ )
            return false;
        return comparee.stepEnd_ > stepBegin_;
    }

    /**
     * Tests whether this cluster fits within the dimensions of the supplied volatility structure (and references only
     * alive rates).
     */
    public boolean isCompatible(final MarketModel volStructure) {
        if ( rateEnd_ > volStructure.numberOfRates() )
            return false;
        if ( stepEnd_ > volStructure.numberOfSteps() )
            return false;
        if ( factorEnd_ > volStructure.numberOfFactors() )
            return false;

        final int firstAliveRate = volStructure.evolution().firstAliveRate()[stepEnd_ - 1];
        return rateBegin_ >= firstAliveRate;
    }

    public int factorBegin() {
        return factorBegin_;
    }

    public int factorEnd() {
        return factorEnd_;
    }

    public int rateBegin() {
        return rateBegin_;
    }

    public int rateEnd() {
        return rateEnd_;
    }

    public int stepBegin() {
        return stepBegin_;
    }

    public int stepEnd() {
        return stepEnd_;
    }
}
