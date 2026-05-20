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

import org.jquantlib.QL;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * European option pricing engine using Monte Carlo simulation.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/vanilla/mceuropeanengine.hpp} (Phase 5h.5-MC-INFRA WI-8). Specialised for
 * {@code RNG = PseudoRandom} (Mersenne-Twister +InverseCumulativeNormal) — quasi-random / low-discrepancy variants are
 * deferred to Phase 5h.5-MC-INFRA-b.
 *
 * <p>Cross-validates against {@code AnalyticEuropeanEngine} —
 * convergence to the closed-form Black-Scholes price as N→∞.
 *
 * @author JQuantLib
 */
public class MCEuropeanEngine extends MCVanillaEngine {

    public MCEuropeanEngine(final GeneralizedBlackScholesProcess process, final int timeSteps,
            final int timeStepsPerYear, final boolean brownianBridge, final boolean antitheticVariate,
            final int requiredSamples, final double requiredTolerance, final int maxSamples, final long seed) {
        super(process, timeSteps, timeStepsPerYear, brownianBridge, antitheticVariate,
                /* controlVariate=*/ false, requiredSamples, requiredTolerance, maxSamples, seed);
    }

    @Override
    protected PathPricer< Path > pathPricer() {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final PlainVanillaPayoff payoff;
        try {
            payoff = (PlainVanillaPayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-plain payoff given");
        }
        QL.require(payoff != null, "non-plain payoff given");

        final double discount = process_.riskFreeRate().currentLink().discount(timeGrid().back());
        return new EuropeanPathPricer(payoff.optionType(), payoff.strike(), discount);
    }
}
