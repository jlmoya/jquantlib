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
 Copyright (C) 2004, 2007 StatPro Italia srl

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

package org.jquantlib.pricingengines.cliquet;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.CliquetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PercentageStrikePayoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Pricing engine for Cliquet options using analytical formulae.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticCliquetEngine} in
 * {@code ql/pricingengines/cliquet/analyticcliquetengine.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class AnalyticCliquetEngine extends OneAssetOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final CliquetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;

    public AnalyticCliquetEngine(final GeneralizedBlackScholesProcess process) {
        super(new CliquetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        this.a = (CliquetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.greeks = r.greeks();
        this.process = process;
        this.process.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(a.accruedCoupon == Constants.NULL_REAL && a.lastFixing == Constants.NULL_REAL,
                   "this engine cannot price options already started");
        QL.require(a.localCap == Constants.NULL_REAL && a.localFloor == Constants.NULL_REAL
                   && a.globalCap == Constants.NULL_REAL && a.globalFloor == Constants.NULL_REAL,
                   "this engine cannot price capped/floored options");
        QL.require(a.exercise.type() == Exercise.Type.European, "not an European option");

        QL.require(a.payoff instanceof PercentageStrikePayoff, "wrong payoff given");
        final PercentageStrikePayoff moneyness = (PercentageStrikePayoff) a.payoff;

        final List<Date> resetDates = new ArrayList<Date>(a.resetDates);
        resetDates.add(a.exercise.lastDate());

        final double underlying = process.stateVariable().currentLink().value();
        QL.require(underlying > 0.0, "negative or null underlying");

        final double strike = underlying * moneyness.strike();
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(moneyness.optionType(), strike);

        r.value = 0.0;
        greeks.delta = 0.0;
        greeks.gamma = 0.0;
        greeks.theta = 0.0;
        greeks.rho = 0.0;
        greeks.dividendRho = 0.0;
        greeks.vega = 0.0;

        for (int i = 1; i < resetDates.size(); i++) {

            final double weight = process.dividendYield().currentLink().discount(resetDates.get(i - 1));

            final double discount =
                process.riskFreeRate().currentLink().discount(resetDates.get(i))
                / process.riskFreeRate().currentLink().discount(resetDates.get(i - 1));

            final double qDiscount =
                process.dividendYield().currentLink().discount(resetDates.get(i))
                / process.dividendYield().currentLink().discount(resetDates.get(i - 1));

            final double forward = underlying * qDiscount / discount;

            final double variance = process.blackVolatility().currentLink().blackForwardVariance(
                    resetDates.get(i - 1), resetDates.get(i), strike, false);

            final BlackCalculator black = new BlackCalculator(payoff, forward, Math.sqrt(variance), discount);

            final DayCounter rfdc = process.riskFreeRate().currentLink().dayCounter();
            final DayCounter divdc = process.dividendYield().currentLink().dayCounter();
            final DayCounter voldc = process.blackVolatility().currentLink().dayCounter();

            r.value += weight * black.value();
            greeks.delta += weight * (black.delta(underlying)
                    + moneyness.strike() * discount * black.beta());
            // gamma += 0.0
            greeks.theta += process.dividendYield().currentLink().forwardRate(
                    resetDates.get(i - 1), resetDates.get(i), rfdc,
                    Compounding.Continuous, Frequency.NoFrequency).rate()
                    * weight * black.value();

            double dt = rfdc.yearFraction(resetDates.get(i - 1), resetDates.get(i));
            greeks.rho += weight * black.rho(dt);

            final double t = divdc.yearFraction(
                    process.dividendYield().currentLink().referenceDate(),
                    resetDates.get(i - 1));
            dt = divdc.yearFraction(resetDates.get(i - 1), resetDates.get(i));
            greeks.dividendRho += weight * (black.dividendRho(dt) - t * black.value());

            dt = voldc.yearFraction(resetDates.get(i - 1), resetDates.get(i));
            greeks.vega += weight * black.vega(dt);
        }
    }
}
