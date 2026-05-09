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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.pricingengines.forward;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.ForwardVanillaOption;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Forward (strike-resetting) performance vanilla-option engine.
 *
 * <p>Phase 5i.5-MGR Java port of C++
 * {@code ForwardPerformanceVanillaEngine<AnalyticEuropeanEngine>}
 * (v1.42.1 ql/pricingengines/forward/forwardperformanceengine.hpp).
 *
 * <p>Performance variant: NPV is the inner discounted price scaled by
 * {@code 1 / S(0)}, paying off as a percentage performance.
 */
public class ForwardPerformanceVanillaEngine extends ForwardVanillaEngine {

    public ForwardPerformanceVanillaEngine(final GeneralizedBlackScholesProcess process) {
        super(process);
    }

    @Override
    protected void getOriginalResults() {
        final ForwardVanillaOption.ArgumentsImpl args =
                (ForwardVanillaOption.ArgumentsImpl) arguments_;
        final ForwardVanillaOption.ResultsImpl r =
                (ForwardVanillaOption.ResultsImpl) results_;
        final org.jquantlib.instruments.Option.GreeksImpl rg = r.greeks();

        final DayCounter rfdc = process_.riskFreeRate().currentLink().dayCounter();
        final double resetTime = rfdc.yearFraction(
                process_.riskFreeRate().currentLink().referenceDate(), args.resetDate);
        double discR = process_.riskFreeRate().currentLink().discount(args.resetDate);

        // Performance option: divide by S(0) to make payoff a percentage.
        discR /= process_.stateVariable().currentLink().value();

        final org.jquantlib.instruments.Option.GreeksImpl ig = originalResults_.greeks();
        final double temp = originalResults_.value;
        r.value = discR * temp;
        rg.delta = 0.0;
        rg.gamma = 0.0;
        rg.theta = process_.riskFreeRate().currentLink()
                .zeroRate(args.resetDate, rfdc, Compounding.Continuous, Frequency.NoFrequency)
                .rate() * r.value;
        rg.vega = discR * ig.vega;
        rg.rho = -resetTime * r.value + discR * ig.rho;
        rg.dividendRho = discR * ig.dividendRho;
    }
}
