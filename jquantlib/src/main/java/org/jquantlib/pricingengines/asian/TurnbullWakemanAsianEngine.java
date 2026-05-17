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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2021 Skandinaviska Enskilda Banken AB (publ)

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.pricingengines.asian;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

/**
 * Turnbull-Wakeman two moment-matching analytical pricing engine for
 * discrete-time arithmetic average-rate Asian options.
 *
 * <p>References:
 * <ul>
 *   <li>"Commodity Option Pricing", Iain Clark, Wiley, section 2.7.4.</li>
 *   <li>"Option Pricing Formulas", Second Edition, E.G. Haug, 2006, pp. 192-202.</li>
 * </ul>
 *
 * <p>Some parts of the implementation were modelled after calculations from the
 * {@code CommodityAveragePriceOptionAnalyticalEngine} class in Open Source Risk
 * Engine (https://github.com/OpenSourceRisk/Engine).
 *
 * <p>Port of {@code ql/pricingengines/asian/turnbullwakemanasianengine.{hpp,cpp}}
 * from QuantLib v1.42.1.
 */
public class TurnbullWakemanAsianEngine extends DiscreteAveragingAsianOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final DiscreteAveragingAsianOption.ArgumentsImpl a;
    private final DiscreteAveragingAsianOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;

    public TurnbullWakemanAsianEngine(final GeneralizedBlackScholesProcess process) {
        this.process = process;
        this.a = (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;
        this.r = (DiscreteAveragingAsianOption.ResultsImpl) results_;
        this.greeks = r.greeks();
        process.addObserver(this);
    }

    @Override
    public void calculate() /*@ReadOnly*/ {

        // Enforce required preconditions
        QL.require(a.exercise.type() == Exercise.Type.European, "not a European Option");
        QL.require(a.averageType == AverageType.Arithmetic,
                "must be Arithmetic AverageType");

        // Calculate the accrued portion
        final int pastFixings = a.pastFixings;
        final int futureFixings = a.fixingDates.size();
        double accruedAverage = 0.0;
        if (pastFixings != 0) {
            accruedAverage = a.runningAccumulator / (pastFixings + futureFixings);
        }

        final double discount = process.riskFreeRate().currentLink().discount(a.exercise.lastDate());

        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;

        // We will read the volatility off the surface at the effective strike
        final double effectiveStrike = payoff.strike() - accruedAverage;

        // If the effective strike is negative, exercise resp. permanent OTM is
        // guaranteed and the valuation is made easy
        final int m = futureFixings + pastFixings;
        if (effectiveStrike <= 0.0) {
            // For a reference, see "Option Pricing Formulas", Haug, 2nd ed, p. 193
            if (payoff.optionType() == Option.Type.Call) {
                final double spot = process.stateVariable().currentLink().value();
                double S_A_hat = accruedAverage;
                for (final Date fd : a.fixingDates) {
                    S_A_hat += (spot * process.dividendYield().currentLink().discount(fd) /
                                process.riskFreeRate().currentLink().discount(fd)) / m;
                }
                r.value = discount * (S_A_hat - payoff.strike());
                greeks.delta = discount * (S_A_hat - accruedAverage) / spot;
            } else if (payoff.optionType() == Option.Type.Put) {
                r.value = 0.0;
                greeks.delta = 0.0;
            }
            greeks.gamma = 0.0;
            return;
        }

        // We should only get this far when the effectiveStrike > 0 but will check anyway
        QL.require(effectiveStrike > 0.0, "expected effectiveStrike to be positive");

        // Expected value of the non-accrued portion of the average prices
        // In general, m will equal n below if there is no accrued. If accrued, m > n.
        double EA = 0.0;
        final List<Double> forwards = new ArrayList<Double>();
        final List<Double> times = new ArrayList<Double>();
        final List<Double> spotVars = new ArrayList<Double>();
        final double spot = process.stateVariable().currentLink().value();

        for (final Date fd : a.fixingDates) {
            final double dividendDiscount = process.dividendYield().currentLink().discount(fd);
            final double riskFreeDiscountForFwdEstimation = process.riskFreeRate().currentLink().discount(fd);

            final double fwd = spot * dividendDiscount / riskFreeDiscountForFwdEstimation;
            forwards.add(fwd);
            final double t = process.blackVolatility().currentLink().timeFromReference(fd);
            times.add(t);
            spotVars.add(process.blackVolatility().currentLink().blackVariance(t, effectiveStrike));

            EA += fwd;
        }
        EA /= m;

        // Expected value of A^2.
        double EA2 = 0.0;
        final int n = forwards.size();

        for (int i = 0; i < n; ++i) {
            final double fi = forwards.get(i);
            EA2 += fi * fi * Math.exp(spotVars.get(i));
            for (int j = 0; j < i; ++j) {
                EA2 += 2.0 * fi * forwards.get(j) * Math.exp(spotVars.get(j));
            }
        }

        EA2 /= (double) m * (double) m;

        // Calculate value
        final double tn = times.get(times.size() - 1);
        final double sigma = Math.sqrt(Math.log(EA2 / (EA * EA)) / tn);

        // Populate results
        final BlackCalculator black = new BlackCalculator(
                payoff.optionType(), effectiveStrike, EA, sigma * Math.sqrt(tn), discount);

        r.value = black.value();
        greeks.delta = black.delta(spot);
        greeks.gamma = black.gamma(spot);
    }
}
