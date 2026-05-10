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
 Copyright (C) 2011 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.WriterExtensibleOption;
import org.jquantlib.math.distributions.BivariateNormalDistribution;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Analytic engine for writer-extensible options.
 * <p>
 * Formulas from Haug, "Option Pricing Formulas".
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticWriterExtensibleOptionEngine} in
 * {@code ql/pricingengines/exotic/analyticwriterextensibleoptionengine.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class AnalyticWriterExtensibleOptionEngine extends OneAssetOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final WriterExtensibleOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;

    public AnalyticWriterExtensibleOptionEngine(final GeneralizedBlackScholesProcess process) {
        super(new WriterExtensibleOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        this.a = (WriterExtensibleOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.process = process;
        this.process.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(a.payoff instanceof PlainVanillaPayoff, "not a plain vanilla payoff");
        final PlainVanillaPayoff payoff1 = (PlainVanillaPayoff) a.payoff;

        QL.require(a.payoff2 instanceof PlainVanillaPayoff, "not a plain vanilla payoff");
        final PlainVanillaPayoff payoff2 = (PlainVanillaPayoff) a.payoff2;

        final Exercise exercise1 = a.exercise;
        final Exercise exercise2 = a.exercise2;

        // STEP 1
        final Option.Type type = payoff1.optionType();

        // S = spot
        final double spot = process.stateVariable().currentLink().value();

        // For the B&S formulae:
        final DayCounter dividendDC = process.dividendYield().currentLink().dayCounter();
        final double dividend = process.dividendYield().currentLink().zeroRate(
                exercise1.lastDate(), dividendDC, Compounding.Continuous, Frequency.NoFrequency).rate();

        final DayCounter riskFreeDC = process.riskFreeRate().currentLink().dayCounter();
        final double riskFree = process.riskFreeRate().currentLink().zeroRate(
                exercise1.lastDate(), riskFreeDC, Compounding.Continuous, Frequency.NoFrequency).rate();

        // Time to maturity
        final double t1 = riskFreeDC.yearFraction(
                process.riskFreeRate().currentLink().referenceDate(), exercise1.lastDate());
        final double t2 = riskFreeDC.yearFraction(
                process.riskFreeRate().currentLink().referenceDate(), exercise2.lastDate());

        // b = r-q
        final double b = riskFree - dividend;

        final double forwardPrice = spot * Math.exp(b * t1);

        final double volatility = process.blackVolatility().currentLink().blackVol(
                exercise1.lastDate(), payoff1.strike());

        final double stdDev = volatility * Math.sqrt(t1);

        final double discount = Math.exp(-riskFree * t1);

        // Call the B&S method
        final double black = BlackFormula.blackFormula(type, payoff1.strike(),
                                                       forwardPrice, stdDev, discount);

        // STEP 2 — Standard bivariate normal distribution
        final double ro = Math.sqrt(t1 / t2);
        final double z1 = (Math.log(spot / payoff2.strike())
                + (b + Math.pow(volatility, 2) / 2.0) * t2)
                / (volatility * Math.sqrt(t2));
        final double z2 = (Math.log(spot / payoff1.strike())
                + (b + Math.pow(volatility, 2) / 2.0) * t1)
                / (volatility * Math.sqrt(t1));

        final BivariateNormalDistribution biv = new BivariateNormalDistribution(-ro);

        // STEP 3
        final double bivariate1;
        final double bivariate2;
        final double result;

        if (type == Option.Type.Call) {
            bivariate1 = biv.op(z1, -z2);
            bivariate2 = biv.op(z1 - volatility * Math.sqrt(t2),
                                -z2 + volatility * Math.sqrt(t1));
            result = black + spot * Math.exp((b - riskFree) * t2) * bivariate1
                    - payoff2.strike() * Math.exp(-riskFree * t2) * bivariate2;
        } else {
            bivariate1 = biv.op(-z1, z2);
            bivariate2 = biv.op(-z1 + volatility * Math.sqrt(t2),
                                z2 - volatility * Math.sqrt(t1));
            result = black - spot * Math.exp((b - riskFree) * t2) * bivariate1
                    + payoff2.strike() * Math.exp(-riskFree * t2) * bivariate2;
        }

        r.value = result;
    }
}
