/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2010 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.SimpleChooserOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Analytic engine for simple chooser option (Rubinstein 1991 closed-form).
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticSimpleChooserEngine} in
 * {@code ql/pricingengines/exotic/analyticsimplechooserengine.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class AnalyticSimpleChooserEngine extends OneAssetOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final SimpleChooserOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;

    public AnalyticSimpleChooserEngine(final GeneralizedBlackScholesProcess process) {
        super(new SimpleChooserOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        this.a = (SimpleChooserOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.process = process;
        this.process.addObserver(this);
    }

    @Override
    public void calculate() {
        final Date today = new Settings().evaluationDate();
        final DayCounter rfdc = process.riskFreeRate().currentLink().dayCounter();
        final DayCounter divdc = process.dividendYield().currentLink().dayCounter();
        final DayCounter voldc = process.blackVolatility().currentLink().dayCounter();
        QL.require(rfdc.equals(divdc), "Risk-free rate and dividend yield must have the same day counter");
        QL.require(rfdc.equals(voldc), "Risk-free rate and volatility must have the same day counter");

        final double spot = process.stateVariable().currentLink().value();
        QL.require(a.payoff instanceof StrikedTypePayoff, "non-plain payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        final double strike = payoff.strike();

        final Date maturity = a.exercise.lastDate();
        final double volatility = process.blackVolatility().currentLink().blackVol(maturity, strike);
        final double timeToMaturity = rfdc.yearFraction(today, maturity);
        final double timeToChoosing = rfdc.yearFraction(today, a.choosingDate);

        final double dividendRate = process.dividendYield().currentLink()
                .zeroRate(maturity, divdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double riskFreeRate = process.riskFreeRate().currentLink()
                .zeroRate(maturity, rfdc, Compounding.Continuous, Frequency.NoFrequency).rate();

        QL.require(spot > 0.0, "negative or null spot value");
        QL.require(strike > 0.0, "negative or null strike value");
        QL.require(volatility > 0.0, "negative or null volatility");
        QL.require(timeToChoosing > 0.0, "choosing date earlier than or equal to evaluation date");

        final double sqrtT = Math.sqrt(timeToMaturity);
        final double sqrtTc = Math.sqrt(timeToChoosing);

        final double d = (Math.log(spot / strike)
                + ((riskFreeRate - dividendRate) + volatility * volatility * 0.5) * timeToMaturity) / (volatility
                * sqrtT);

        final double y = (Math.log(spot / strike) + (riskFreeRate - dividendRate) * timeToMaturity + (
                volatility * volatility * timeToChoosing / 2.0)) / (volatility * sqrtTc);

        final CumulativeNormalDistribution f = new CumulativeNormalDistribution();

        r.value = spot * Math.exp(-dividendRate * timeToMaturity) * f.op(d) - strike * Math.exp(
                -riskFreeRate * timeToMaturity) * f.op(d - volatility * sqrtT) - spot * Math.exp(
                -dividendRate * timeToMaturity) * f.op(-y) + strike * Math.exp(-riskFreeRate * timeToMaturity) * f.op(
                -y + volatility * sqrtTc);
    }
}
