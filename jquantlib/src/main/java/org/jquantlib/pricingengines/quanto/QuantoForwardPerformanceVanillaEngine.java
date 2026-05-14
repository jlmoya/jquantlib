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
import org.jquantlib.instruments.ForwardVanillaOption;
import org.jquantlib.instruments.QuantoForwardVanillaOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.forward.ForwardPerformanceVanillaEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.QuantoTermStructure;

/**
 * Quanto engine for forward-performance (strike-resetting, percentage-payoff)
 * vanilla options.
 *
 * <p>Phase 5h.5-MC-INFRA-c Java specialisation of C++
 * {@code QuantoEngine<ForwardVanillaOption,
 *                     ForwardPerformanceVanillaEngine<AnalyticEuropeanEngine>>}
 * (v1.42.1 ql/pricingengines/quanto/quantoengine.hpp +
 * ql/pricingengines/forward/forwardperformanceengine.hpp).
 *
 * <p>Combines quanto adjustment of dividend term structure with the forward
 * (strike-resetting) performance pricing path. The performance variant
 * differs from the standard forward engine in that the NPV is the
 * (discounted) inner price scaled by {@code 1 / S(0)}, paying off as a
 * percentage performance.
 *
 * <p>Implementation mirrors the structure of {@link QuantoForwardVanillaEngine}
 * but binds a {@link ForwardPerformanceVanillaEngine} as the inner engine.
 */
public class QuantoForwardPerformanceVanillaEngine extends QuantoForwardVanillaOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final Handle<YieldTermStructure> foreignRiskFreeRate_;
    private final Handle<BlackVolTermStructure> exchangeRateVolatility_;
    private final Handle<? extends Quote> correlation_;

    public QuantoForwardPerformanceVanillaEngine(
            final GeneralizedBlackScholesProcess process,
            final Handle<YieldTermStructure> foreignRiskFreeRate,
            final Handle<BlackVolTermStructure> exchangeRateVolatility,
            final Handle<? extends Quote> correlation) {
        super();
        this.process_                = process;
        this.foreignRiskFreeRate_    = foreignRiskFreeRate;
        this.exchangeRateVolatility_ = exchangeRateVolatility;
        this.correlation_            = correlation;
        this.process_.addObserver(this);
        this.foreignRiskFreeRate_.addObserver(this);
        this.exchangeRateVolatility_.addObserver(this);
        this.correlation_.addObserver(this);
    }

    @Override
    public void calculate() {
        final ForwardVanillaOption.ArgumentsImpl a =
                (ForwardVanillaOption.ArgumentsImpl) arguments_;
        final QuantoForwardVanillaOption.ResultsImpl r =
                (QuantoForwardVanillaOption.ResultsImpl) results_;

        final double exchangeRateATMlevel = 1.0;

        QL.require(a.payoff instanceof StrikedTypePayoff, "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        final double strike = payoff.strike();

        final Handle<? extends Quote> spot = process_.stateVariable();
        QL.require(spot.currentLink().value() > 0.0, "negative or null underlying");

        final Handle<YieldTermStructure> riskFreeRate = process_.riskFreeRate();
        final QuantoTermStructure quantoTS = new QuantoTermStructure(
                process_.dividendYield(), process_.riskFreeRate(),
                foreignRiskFreeRate_, process_.blackVolatility(),
                strike, exchangeRateVolatility_,
                exchangeRateATMlevel, correlation_.currentLink().value());
        final Handle<YieldTermStructure> dividendYield =
                new Handle<YieldTermStructure>(quantoTS);
        final Handle<BlackVolTermStructure> blackVol = process_.blackVolatility();

        final GeneralizedBlackScholesProcess quantoProcess =
                new GeneralizedBlackScholesProcess(spot, dividendYield,
                        riskFreeRate, blackVol);

        // Build inner ForwardPerformanceVanillaEngine and a synthetic
        // ForwardVanillaOption bound to it.
        final ForwardPerformanceVanillaEngine inner =
                new ForwardPerformanceVanillaEngine(quantoProcess);
        final ForwardVanillaOption opt = new ForwardVanillaOption(
                a.moneyness, a.resetDate, payoff, a.exercise);
        opt.setPricingEngine(inner);
        opt.NPV();   // forces calculate

        final ForwardVanillaOption.ResultsImpl ir =
                (ForwardVanillaOption.ResultsImpl) inner.getResults();

        // Copy base value + greeks
        r.value = ir.value;
        final org.jquantlib.instruments.Option.GreeksImpl rg = r.greeks();
        final org.jquantlib.instruments.Option.GreeksImpl ig = ir.greeks();
        rg.delta = ig.delta;
        rg.gamma = ig.gamma;
        rg.theta = ig.theta;
        if (!isNull(ig.rho) && !isNull(ig.dividendRho)) {
            rg.rho = ig.rho + ig.dividendRho;
            rg.dividendRho = ig.dividendRho;
        } else {
            rg.rho = Constants.NULL_REAL;
            rg.dividendRho = Constants.NULL_REAL;
        }
        // Copy MoreGreeks (performance variant doesn't compute most of these,
        // but copy whatever the inner produced for parity with the C++ QuantoEngine
        // forward-greeks pass-through).
        final org.jquantlib.instruments.Option.MoreGreeksImpl rm = r.moreGreeks();
        final org.jquantlib.instruments.Option.MoreGreeksImpl im = ir.moreGreeks();
        rm.deltaForward       = im.deltaForward;
        rm.elasticity         = im.elasticity;
        rm.thetaPerDay        = im.thetaPerDay;
        rm.strikeSensitivity  = im.strikeSensitivity;
        rm.itmCashProbability = im.itmCashProbability;

        final double exchangeRateFlatVol = exchangeRateVolatility_.currentLink()
                .blackVol(a.exercise.lastDate(), exchangeRateATMlevel);

        if (!isNull(ig.vega) && !isNull(ig.dividendRho)) {
            rg.vega = ig.vega + correlation_.currentLink().value()
                    * exchangeRateFlatVol * ig.dividendRho;
        } else {
            rg.vega = Constants.NULL_REAL;
        }

        if (!isNull(ig.dividendRho)) {
            final double volatility = process_.blackVolatility().currentLink()
                    .blackVol(a.exercise.lastDate(),
                              process_.stateVariable().currentLink().value());
            r.qvega = correlation_.currentLink().value()
                    * volatility * ig.dividendRho;
            r.qrho  = -ig.dividendRho;
            r.qlambda = exchangeRateFlatVol * volatility * ig.dividendRho;
        } else {
            r.qvega = Constants.NULL_REAL;
            r.qrho  = Constants.NULL_REAL;
            r.qlambda = Constants.NULL_REAL;
        }
    }

    private static boolean isNull(final double x) {
        return x == Constants.NULL_REAL || Double.isNaN(x);
    }
}
