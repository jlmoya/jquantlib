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
package org.jquantlib.pricingengines.quanto;

import org.jquantlib.QL;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.QuantoVanillaOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.QuantoTermStructure;

/**
 * Quanto engine for vanilla options.
 *
 * <p>Phase 5i.5-MGR Java specialisation of C++
 * {@code QuantoEngine<VanillaOption, AnalyticEuropeanEngine>} (v1.42.1 ql/pricingengines/quanto/quantoengine.hpp). For
 * now only the {@code AnalyticEuropeanEngine} specialisation is provided, matching the canonical C++ test-suite path.
 * Other inner engines can be added by subclassing {@link QuantoVanillaOption.EngineImpl}.
 */
public class QuantoVanillaEngine extends QuantoVanillaOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final Handle< YieldTermStructure > foreignRiskFreeRate_;
    private final Handle< BlackVolTermStructure > exchangeRateVolatility_;
    private final Handle< ? extends Quote > correlation_;

    public QuantoVanillaEngine(final GeneralizedBlackScholesProcess process,
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
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final QuantoVanillaOption.ResultsImpl r = (QuantoVanillaOption.ResultsImpl) results_;

        // ATM exchangeRate level needed here
        final double exchangeRateATMlevel = 1.0;

        QL.require(a.payoff instanceof StrikedTypePayoff, "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        final double strike = payoff.strike();

        final Handle< ? extends Quote > spot = process_.stateVariable();
        QL.require(spot.currentLink().value() > 0.0, "negative or null underlying");

        final Handle< YieldTermStructure > riskFreeRate = process_.riskFreeRate();
        // Construct the quanto-adjusted dividend term structure
        final QuantoTermStructure quantoTS = new QuantoTermStructure(process_.dividendYield(), process_.riskFreeRate(),
                foreignRiskFreeRate_, process_.blackVolatility(), strike, exchangeRateVolatility_, exchangeRateATMlevel,
                correlation_.currentLink().value());
        final Handle< YieldTermStructure > dividendYield = new Handle< YieldTermStructure >(quantoTS);
        final Handle< BlackVolTermStructure > blackVol = process_.blackVolatility();

        final GeneralizedBlackScholesProcess quantoProcess = new GeneralizedBlackScholesProcess(spot, dividendYield,
                riskFreeRate, blackVol);

        // Construct an inner European engine and a synthetic VanillaOption
        // bound to it; let it calculate, then read back its results.
        final AnalyticEuropeanEngine inner = new AnalyticEuropeanEngine(quantoProcess);
        final VanillaOption opt = new VanillaOption(payoff, a.exercise);
        opt.setPricingEngine(inner);
        opt.NPV();   // forces calculate

        final OneAssetOption.ResultsImpl ir = (OneAssetOption.ResultsImpl) inner.getResults();
        final org.jquantlib.instruments.Option.GreeksImpl ig = ir.greeks();
        final org.jquantlib.instruments.Option.MoreGreeksImpl im = ir.moreGreeks();

        // Copy base results
        r.value = ir.value;
        // Copy greeks via base ResultsImpl's GreeksImpl
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

        // MoreGreeks copy — mirrors C++ default copy of inner results
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
