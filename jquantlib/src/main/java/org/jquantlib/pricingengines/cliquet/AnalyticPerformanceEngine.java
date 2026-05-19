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

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.*;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

import java.util.ArrayList;
import java.util.List;

/**
 * Pricing engine for performance options using analytical formulae.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code AnalyticPerformanceEngine} in
 * {@code ql/pricingengines/cliquet/analyticperformanceengine.{hpp,cpp}}.
 *
 * @author Jose Moya
 */
public class AnalyticPerformanceEngine extends OneAssetOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final CliquetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;

    public AnalyticPerformanceEngine(final GeneralizedBlackScholesProcess process) {
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

        final List< Date > resetDates = new ArrayList< Date >(a.resetDates);
        resetDates.add(a.exercise.lastDate());

        final double underlying = process.stateVariable().currentLink().value();
        QL.require(underlying > 0.0, "negative or null underlying");

        // PlainVanillaPayoff with strike = 1.0 (performance variant)
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(moneyness.optionType(), 1.0);

        r.value = 0.0;
        greeks.delta = 0.0;
        greeks.gamma = 0.0;
        greeks.theta = 0.0;
        greeks.rho = 0.0;
        greeks.dividendRho = 0.0;
        greeks.vega = 0.0;

        for ( int i = 1; i < resetDates.size(); i++ ) {

            final double discount = process.riskFreeRate().currentLink().discount(resetDates.get(i - 1));

            final double rDiscount =
                    process.riskFreeRate().currentLink().discount(resetDates.get(i)) / process.riskFreeRate()
                            .currentLink().discount(resetDates.get(i - 1));

            final double qDiscount =
                    process.dividendYield().currentLink().discount(resetDates.get(i)) / process.dividendYield()
                            .currentLink().discount(resetDates.get(i - 1));

            final double forward = (1.0 / moneyness.strike()) * qDiscount / rDiscount;

            final double variance = process.blackVolatility().currentLink()
                    .blackForwardVariance(resetDates.get(i - 1), resetDates.get(i), underlying * moneyness.strike(),
                            false);

            final BlackCalculator black = new BlackCalculator(payoff, forward, Math.sqrt(variance), rDiscount);

            final DayCounter rfdc = process.riskFreeRate().currentLink().dayCounter();
            final DayCounter divdc = process.dividendYield().currentLink().dayCounter();
            final DayCounter voldc = process.blackVolatility().currentLink().dayCounter();

            r.value += discount * moneyness.strike() * black.value();
            // delta += 0.0; gamma += 0.0
            greeks.theta += process.riskFreeRate().currentLink()
                    .forwardRate(resetDates.get(i - 1), resetDates.get(i), rfdc, Compounding.Continuous,
                            Frequency.NoFrequency).rate() * discount * moneyness.strike() * black.value();

            double dt = rfdc.yearFraction(resetDates.get(i - 1), resetDates.get(i));
            final double t = rfdc.yearFraction(process.riskFreeRate().currentLink().referenceDate(),
                    resetDates.get(i - 1));
            greeks.rho += discount * moneyness.strike() * (black.rho(dt) - t * black.value());

            dt = divdc.yearFraction(resetDates.get(i - 1), resetDates.get(i));
            greeks.dividendRho += discount * moneyness.strike() * black.dividendRho(dt);

            dt = voldc.yearFraction(resetDates.get(i - 1), resetDates.get(i));
            greeks.vega += discount * moneyness.strike() * black.vega(dt);
        }
    }
}
