/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 Copyright (C) 2004 Neil Firth
 Copyright (C) 2007, 2008 StatPro Italia srl
*/

package org.jquantlib.pricingengines.basket;

import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.LowDiscrepancy;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.time.TimeGrid;

/**
 * European basket-option engine driven by a quasi-random (low-discrepancy) sequence.
 *
 * <p>Java port of the C++ instantiation {@code MCEuropeanBasketEngine<LowDiscrepancy>}
 * (v1.43 {@code ql/pricingengines/basket/mceuropeanbasketengine.hpp}). The only difference from the pseudo-random
 * {@link MCEuropeanBasketEngine} is the sequence generator: C++ selects it through the {@code RNG} traits parameter
 *
 * <pre>
 *   typename RNG::rsg_type gen =
 *       RNG::make_sequence_generator(numAssets*(grid.size()-1), seed_);
 * </pre>
 *
 * which for {@code RNG = LowDiscrepancy} resolves to a {@link SobolRsg} mapped through the inverse normal CDF. Java has
 * no traits parameter, so the specialisation becomes a subclass overriding {@link #pathGenerator()} — the same shape
 * already used by
 * {@link org.jquantlib.pricingengines.vanilla.MCEuropeanEngineLowDiscrepancy MCEuropeanEngineLowDiscrepancy}.
 *
 * <p>Note that {@code LowDiscrepancy::allowsErrorEstimate} is {@code false} in C++: the error estimate a Sobol sequence
 * reports is meaningless, so callers must not read {@code errorEstimate()} off this engine.
 *
 * @see MCEuropeanBasketEngine pseudo-random cousin
 * @see LowDiscrepancy traits class
 */
public class MCEuropeanBasketEngineLowDiscrepancy extends MCEuropeanBasketEngine {

    /**
     * Same signature as {@link MCEuropeanBasketEngine}; see that class for the meaning of the sentinel values.
     */
    public MCEuropeanBasketEngineLowDiscrepancy(final StochasticProcessArray processes, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
        super(processes, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate, requiredSamples,
                requiredTolerance, maxSamples, seed);
    }

    /**
     * Mirrors C++ {@code MCEuropeanBasketEngine<LowDiscrepancy>::pathGenerator()}: one Sobol dimension per asset per
     * time step.
     */
    @Override
    protected MonteCarloModel.PathGeneratorAdapter< MultiPath > pathGenerator() {
        final int numAssets = processes_.size();
        final TimeGrid grid = timeGrid();
        final int dimensions = numAssets * (grid.size() - 1);
        final InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal > gsg = LowDiscrepancy
                .makeSequenceGenerator(dimensions, seed_);
        final MultiPathGenerator< InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal > > gen =
                new MultiPathGenerator< InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal > >(
                        processes_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.MultiPathGeneratorAdapterImpl(gen);
    }
}
