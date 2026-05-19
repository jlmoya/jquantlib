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
/*
 Copyright (C) 2002, 2003, 2004 Ferdinando Ametrano
 Copyright (C) 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.pricingengines.quanto;

import org.jquantlib.QL;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.QuantoBarrierOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.barrier.AnalyticBarrierEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.QuantoTermStructure;

/**
 * Quanto engine for barrier options.
 *
 * <p>Phase 5e.5b-CFC-d-102 Java specialisation of C++
 * {@code QuantoEngine<BarrierOption, AnalyticBarrierEngine>} (v1.42.1 ql/pricingengines/quanto/quantoengine.hpp).
 * Mirrors the {@link QuantoVanillaEngine} pattern but builds an inner {@link AnalyticBarrierEngine} bound to a
 * synthetic {@link BarrierOption} that carries the barrier-specific arguments.
 *
 * <p>Warning: as in the C++ source, only simple Black-Scholes
 * processes are supported (no Merton).
 */
public class QuantoBarrierEngine extends QuantoBarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final Handle< YieldTermStructure > foreignRiskFreeRate_;
    private final Handle< BlackVolTermStructure > exchangeRateVolatility_;
    private final Handle< ? extends Quote > correlation_;

    public QuantoBarrierEngine(final GeneralizedBlackScholesProcess process,
            final Handle< YieldTermStructure > foreignRiskFreeRate,
            final Handle< BlackVolTermStructure > exchangeRateVolatility, final Handle< ? extends Quote > correlation) {
        super();
        this.process_ = process;
        this.foreignRiskFreeRate_ = foreignRiskFreeRate;
        this.exchangeRateVolatility_ = exchangeRateVolatility;
        this.correlation_ = correlation;
        this.process_.addObserver(this);
        this.foreignRiskFreeRate_.addObserver(this);
        this.exchangeRateVolatility_.addObserver(this);
        this.correlation_.addObserver(this);
    }

    @Override
    public void calculate() {
        final BarrierOption.ArgumentsImpl a = (BarrierOption.ArgumentsImpl) arguments_;
        final QuantoBarrierOption.ResultsImpl r = (QuantoBarrierOption.ResultsImpl) results_;

        // ATM exchangeRate level
        final double exchangeRateATMlevel = 1.0;

        QL.require(a.payoff instanceof StrikedTypePayoff, "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        final double strike = payoff.strike();

        final Handle< ? extends Quote > spot = process_.stateVariable();
        QL.require(spot.currentLink().value() > 0.0, "negative or null underlying");

        final Handle< YieldTermStructure > riskFreeRate = process_.riskFreeRate();
        // Quanto-adjusted dividend term structure
        final QuantoTermStructure quantoTS = new QuantoTermStructure(process_.dividendYield(), process_.riskFreeRate(),
                foreignRiskFreeRate_, process_.blackVolatility(), strike, exchangeRateVolatility_, exchangeRateATMlevel,
                correlation_.currentLink().value());
        final Handle< YieldTermStructure > dividendYield = new Handle< YieldTermStructure >(quantoTS);
        final Handle< BlackVolTermStructure > blackVol = process_.blackVolatility();

        final GeneralizedBlackScholesProcess quantoProcess = new GeneralizedBlackScholesProcess(spot, dividendYield,
                riskFreeRate, blackVol);

        // Construct an inner AnalyticBarrierEngine and a synthetic
        // BarrierOption bound to it; let it calculate, then read back results.
        final AnalyticBarrierEngine inner = new AnalyticBarrierEngine(quantoProcess);
        final BarrierOption opt = new BarrierOption(a.barrierType, a.barrier, a.rebate, payoff, a.exercise);
        opt.setPricingEngine(inner);
        opt.NPV();   // forces calculate

        final BarrierOption.ResultsImpl ir = (BarrierOption.ResultsImpl) inner.getResults();
        final org.jquantlib.instruments.Option.GreeksImpl ig = ir.greeks();
        final org.jquantlib.instruments.Option.MoreGreeksImpl im = ir.moreGreeks();

        // Copy base results
        r.value = ir.value;

        final org.jquantlib.instruments.Option.GreeksImpl rg = r.greeks();
        rg.delta = ig.delta;
        rg.gamma = ig.gamma;
        rg.theta = ig.theta;
        rg.vega = ig.vega;
        if ( ig.rho != Constants.NULL_REAL && !Double.isNaN(ig.rho) && ig.dividendRho != Constants.NULL_REAL
                && !Double.isNaN(ig.dividendRho) ) {
            rg.rho = ig.rho + ig.dividendRho;
            rg.dividendRho = ig.dividendRho;
        } else {
            rg.rho = Constants.NULL_REAL;
            rg.dividendRho = Constants.NULL_REAL;
        }

        // MoreGreeks pass-through
        final org.jquantlib.instruments.Option.MoreGreeksImpl rm = r.moreGreeks();
        rm.deltaForward = im.deltaForward;
        rm.elasticity = im.elasticity;
        rm.thetaPerDay = im.thetaPerDay;
        rm.strikeSensitivity = im.strikeSensitivity;
        rm.itmCashProbability = im.itmCashProbability;

        final double exchangeRateFlatVol = exchangeRateVolatility_.currentLink()
                .blackVol(a.exercise.lastDate(), exchangeRateATMlevel);

        if ( ig.vega != Constants.NULL_REAL && !Double.isNaN(ig.vega) && ig.dividendRho != Constants.NULL_REAL
                && !Double.isNaN(ig.dividendRho) ) {
            rg.vega = ig.vega + correlation_.currentLink().value() * exchangeRateFlatVol * ig.dividendRho;
        } else {
            rg.vega = Constants.NULL_REAL;
        }

        if ( ig.dividendRho != Constants.NULL_REAL && !Double.isNaN(ig.dividendRho) ) {
            final double volatility = process_.blackVolatility().currentLink()
                    .blackVol(a.exercise.lastDate(), process_.stateVariable().currentLink().value());
            r.qvega = correlation_.currentLink().value() * volatility * ig.dividendRho;
            r.qrho = -ig.dividendRho;
            r.qlambda = exchangeRateFlatVol * volatility * ig.dividendRho;
        } else {
            r.qvega = Constants.NULL_REAL;
            r.qrho = Constants.NULL_REAL;
            r.qlambda = Constants.NULL_REAL;
        }
    }
}
