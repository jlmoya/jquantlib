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
package org.jquantlib.pricingengines.exchange;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.*;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.BjerksundStenslandApproximationEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Analytic engine for American Margrabe options.
 *
 * <p>Implements formulae from W. Margrabe,
 * <em>The Value of an American Option to Exchange One Asset for Another</em>,
 * Journal of Finance, 33, 177-86. The option is reduced to an American single-asset option on an adjusted process and
 * priced via {@link BjerksundStenslandApproximationEngine}.
 *
 * <p>Phase 5i.5-MGR port of {@code QuantLib::AnalyticAmericanMargrabeEngine}
 * (v1.42.1 ql/pricingengines/exotic/analyticamericanmargrabeengine.{hpp,cpp}).
 */
public class AnalyticAmericanMargrabeEngine extends MargrabeOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process1_;
    private final GeneralizedBlackScholesProcess process2_;
    private final double rho_;

    public AnalyticAmericanMargrabeEngine(final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2, final double correlation) {
        super();
        this.process1_ = process1;
        this.process2_ = process2;
        this.rho_ = correlation;
        this.process1_.addObserver(this);
        this.process2_.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(arguments_.exercise.type() == Exercise.Type.American, "not an American option");

        QL.require(arguments_.exercise instanceof AmericanExercise, "not an American exercise");
        final AmericanExercise exercise = (AmericanExercise) arguments_.exercise;

        QL.require(arguments_.payoff instanceof NullPayoff, "not a null payoff");

        // The option can be priced as an American single-asset option
        // with an adjusted process and payoff.

        final Date today = new Settings().evaluationDate();

        final DayCounter rfdc = process1_.riskFreeRate().currentLink().dayCounter();
        final double t = rfdc.yearFraction(process1_.riskFreeRate().currentLink().referenceDate(), exercise.lastDate());

        final double s1 = process1_.stateVariable().currentLink().value();
        final double s2 = process2_.stateVariable().currentLink().value();

        final SimpleQuote spot = new SimpleQuote(arguments_.Q1 * s1);

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, arguments_.Q2 * s2);

        final double dividendDiscount1 = process1_.dividendYield().currentLink().discount(exercise.lastDate());
        final double q1 = -Math.log(dividendDiscount1) / t;

        final double dividendDiscount2 = process2_.dividendYield().currentLink().discount(exercise.lastDate());
        final double q2 = -Math.log(dividendDiscount2) / t;

        final Handle< YieldTermStructure > qTS = new Handle< YieldTermStructure >(new FlatForward(today, q1, rfdc));

        final Handle< YieldTermStructure > rTS = new Handle< YieldTermStructure >(new FlatForward(today, q2, rfdc));

        final double variance1 = process1_.blackVolatility().currentLink().blackVariance(exercise.lastDate(), s1);
        final double variance2 = process2_.blackVolatility().currentLink().blackVariance(exercise.lastDate(), s2);
        final double variance = variance1 + variance2 - 2.0 * rho_ * Math.sqrt(variance1) * Math.sqrt(variance2);
        final double volatility = Math.sqrt(variance / t);

        final Calendar cal = new NullCalendar();
        final Handle< BlackVolTermStructure > volTS = new Handle< BlackVolTermStructure >(
                new BlackConstantVol(today, cal, volatility, rfdc));

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(new Handle< Quote >(spot), qTS,
                rTS, volTS);

        final PricingEngine engine = new BjerksundStenslandApproximationEngine(stochProcess);

        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(engine);

        results_.value = option.NPV();
    }
}
