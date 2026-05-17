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
 */

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.pricingengines.AmericanPayoffAtExpiry;
import org.jquantlib.pricingengines.AmericanPayoffAtHit;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Analytic pricing engine for American vanilla options with digital payoff.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/pricingengines/vanilla/analyticdigitalamericanengine.{hpp,cpp}}.
 */
public class AnalyticDigitalAmericanEngine extends VanillaOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final OneAssetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;

    public AnalyticDigitalAmericanEngine(final GeneralizedBlackScholesProcess process) {
        this.a = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.greeks = r.greeks();
        this.process = process;
        this.process.addObserver(this);
    }

    public boolean knock_in() {
        return true;
    }

    @Override
    public void calculate() {
        QL.require(a.exercise.type() == Exercise.Type.American, "non-American exercise given");
        QL.require(a.exercise instanceof AmericanExercise, "non-American exercise given");
        final AmericanExercise ex = (AmericanExercise) a.exercise;

        QL.require(ex.dates().get(0).le(
                process.blackVolatility().currentLink().referenceDate()),
                "American option with window exercise not handled yet");

        QL.require(a.payoff instanceof StrikedTypePayoff, "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;

        final double spot = process.stateVariable().currentLink().value();
        QL.require(spot > 0.0, "negative or null underlying given");

        final double variance = process.blackVolatility().currentLink()
                .blackVariance(ex.lastDate(), payoff.strike());
        final double dividendDiscount = process.dividendYield().currentLink()
                .discount(ex.lastDate());
        final double riskFreeDiscount = process.riskFreeRate().currentLink()
                .discount(ex.lastDate());

        if (ex.payoffAtExpiry()) {
            final AmericanPayoffAtExpiry pricer = new AmericanPayoffAtExpiry(
                    spot, riskFreeDiscount, dividendDiscount, variance, payoff, knock_in());
            r.value = pricer.value();
        } else {
            final AmericanPayoffAtHit pricer = new AmericanPayoffAtHit(
                    spot, riskFreeDiscount, dividendDiscount, variance, payoff);
            r.value = pricer.value();
            greeks.delta = pricer.delta();
            greeks.gamma = pricer.gamma();

            final DayCounter rfdc = process.riskFreeRate().currentLink().dayCounter();
            final double t = rfdc.yearFraction(
                    process.riskFreeRate().currentLink().referenceDate(),
                    a.exercise.lastDate());
            greeks.rho = pricer.rho(t);
        }
    }

}
