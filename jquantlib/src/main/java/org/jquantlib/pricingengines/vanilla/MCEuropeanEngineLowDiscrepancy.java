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
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.LowDiscrepancy;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.methods.montecarlo.MonteCarloModel;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathGenerator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.TimeGrid;

/**
 * European option pricing engine using Quasi-Monte-Carlo (low-discrepancy) simulation.
 *
 * <p>Java port of the C++ specialization
 * {@code MCEuropeanEngine<LowDiscrepancy>}
 * (v1.42.1 ql/pricingengines/vanilla/mceuropeanengine.hpp) — Phase1-closure-A2-E-552.
 *
 * <p>Uses a {@link SobolRsg} low-discrepancy sequence mapped through the inverse normal CDF; this gives a deterministic
 * (no random seed) error estimate of {@code 0} but a much faster convergence rate than pseudo-random sampling for
 * smooth, low-dimensional integrands such as the European payoff.
 *
 * @see MCEuropeanEngine pseudo-random cousin
 * @see LowDiscrepancy traits class
 */
public final class MCEuropeanEngineLowDiscrepancy extends MCEuropeanEngine {

    public MCEuropeanEngineLowDiscrepancy(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
        super(process, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate, requiredSamples,
                requiredTolerance, maxSamples, seed);
    }

    @Override
    protected MonteCarloModel.PathGeneratorAdapter< Path > pathGenerator() {
        final TimeGrid grid = timeGrid();
        final int dimensions = process_.factors() * (grid.size() - 1);
        final InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal > gsg = LowDiscrepancy
                .makeSequenceGenerator(dimensions, seed_);
        final PathGenerator< InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal > > gen = new PathGenerator< InverseCumulativeRsg< SobolRsg, InverseCumulativeNormal > >(
                process_, grid, gsg, brownianBridge_);
        return new MonteCarloModel.PathGeneratorAdapterImpl(gen);
    }
}
