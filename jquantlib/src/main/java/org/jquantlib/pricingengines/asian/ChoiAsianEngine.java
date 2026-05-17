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
 */

/*
 Copyright (C) 2025 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.pricingengines.asian;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.basket.ChoiBasketEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;

/**
 * Pricing engine for arithmetic Asian options using Choi (2018) quadrature.
 *
 * <p>This class replicates an arithmetic Asian option using a basket option.
 * The pricing of an arithmetic Asian option is substituted with the pricing
 * of a basket option, evaluated by {@link ChoiBasketEngine}.
 *
 * <p>References:
 * "Sum of all Black-Scholes-Merton Models: An efficient Pricing Method for
 * Spread, Basket and Asian Options", Jaehyuk Choi, 2018,
 * https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2913048.
 *
 * <p>Port of {@code ql/pricingengines/asian/choiasianengine.{hpp,cpp}}
 * from QuantLib v1.42.1 (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * @author Jose Moya
 */
public class ChoiAsianEngine extends DiscreteAveragingAsianOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final double lambda_;
    private final long maxNrIntegrationSteps_;

    private final DiscreteAveragingAsianOption.ArgumentsImpl a;
    private final DiscreteAveragingAsianOption.ResultsImpl r;

    /** Defaults: lambda=15, maxNrIntegrationSteps = 2 << 21 = 4194304. */
    public ChoiAsianEngine(final GeneralizedBlackScholesProcess process) {
        this(process, 15.0, 2L << 21);
    }

    public ChoiAsianEngine(
            final GeneralizedBlackScholesProcess process,
            final double lambda,
            final long maxNrIntegrationSteps) {
        this.process_ = process;
        this.lambda_ = lambda;
        this.maxNrIntegrationSteps_ = maxNrIntegrationSteps;
        this.a = (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;
        this.r = (DiscreteAveragingAsianOption.ResultsImpl) results_;
        process_.addObserver(this);
    }

    @Override
    public void calculate() /*@ReadOnly*/ {
        QL.require(a.averageType == AverageType.Arithmetic,
                "must be Average::Type Arithmetic ");
        QL.require(a.exercise.type() == Exercise.Type.European,
                "not a European Option");

        QL.require(a.payoff instanceof PlainVanillaPayoff,
                "non plain vanilla payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;

        // local working copy of the fixing dates (sorted ascending)
        final List<Date> fixingDates = new ArrayList<Date>(a.fixingDates);
        Collections.sort(fixingDates);

        int futureFixings = fixingDates.size();
        int pastFixings = a.pastFixings;
        double runningAccumulator = a.runningAccumulator;

        final Date exerciseDate = a.exercise.lastDate();
        final Handle<YieldTermStructure> rTS = process_.riskFreeRate();

        // Special case: a fixing date equal to today gets pushed to past
        if (futureFixings > 0
                && process_.time(fixingDates.get(0)) == 0.0) {
            fixingDates.remove(0);
            futureFixings--;
            pastFixings++;
            runningAccumulator += process_.x0();
        }

        if (futureFixings == 0) {
            QL.require(pastFixings > 0, "no past fixings given");
            r.value = payoff.get(runningAccumulator / pastFixings)
                    * rTS.currentLink().discount(exerciseDate);
            return;
        }

        // Validations on the remaining future schedule
        QL.require(fixingDates.get(fixingDates.size() - 1).le(exerciseDate),
                "last fixing date must be before exercise date");
        QL.require(process_.time(fixingDates.get(0)) >= 0.0,
                "first fixing date is in the past");
        for (int i = 1; i < fixingDates.size(); ++i) {
            QL.require(!fixingDates.get(i).eq(fixingDates.get(i - 1)),
                    "two fixing dates are the same");
        }

        final double accruedAverage = (pastFixings != 0)
                ? runningAccumulator / (pastFixings + futureFixings)
                : 0.0;

        final double strike = payoff.strike() - accruedAverage;
        QL.require(strike >= 0.0, "effective strike should to be positive");

        final Handle<YieldTermStructure> qTS = process_.dividendYield();
        final Handle<BlackVolTermStructure> volTS = process_.blackVolatility();
        final BlackVolTermStructure volTSLink = volTS.currentLink();
        final Date volRefDate = volTSLink.referenceDate();
        final DayCounter volDc = volTSLink.dayCounter();

        if (futureFixings > 1) {
            // Build per-fixing time/variance arrays
            final double[] fixingTimes = new double[futureFixings];
            final double[] variances = new double[futureFixings];
            for (int i = 0; i < futureFixings; ++i) {
                final Date fixingDate = fixingDates.get(i);
                fixingTimes[i] = volDc.yearFraction(volRefDate, fixingDate);
                variances[i] = volTSLink.blackVariance(fixingDate, strike);
            }

            // rho[i][j] = variances[min(i,j)] / sqrt(variances[i] * variances[j])
            final Matrix rho = new Matrix(futureFixings, futureFixings);
            for (int i = 0; i < futureFixings; ++i) {
                for (int j = i; j < futureFixings; ++j) {
                    final double rij = variances[Math.min(i, j)]
                            / Math.sqrt(variances[i] * variances[j]);
                    rho.set(i, j, rij);
                    rho.set(j, i, rij);
                }
            }

            // Zero-rate term structure for the per-leg processes
            final Date rRefDate = rTS.currentLink().referenceDate();
            final DayCounter rDc = rTS.currentLink().dayCounter();
            final Handle<YieldTermStructure> zeroTS =
                    new Handle<YieldTermStructure>(
                            new FlatForward(rRefDate, 0.0, rDc));

            // Build one Generalized-BS process per future fixing
            final List<GeneralizedBlackScholesProcess> processes =
                    new ArrayList<GeneralizedBlackScholesProcess>(futureFixings);
            final double tLast = fixingTimes[futureFixings - 1];
            for (int i = 0; i < futureFixings; ++i) {
                final Date fixingDate = fixingDates.get(i);
                final double sig = volTSLink.blackVol(fixingDate, payoff.strike())
                        * Math.sqrt(fixingTimes[i] / tLast);

                final double spot = process_.x0()
                        * qTS.currentLink().discount(fixingDate)
                        / rTS.currentLink().discount(fixingDate);

                final BlackConstantVol bcv = new BlackConstantVol(
                        volRefDate, volTSLink.calendar(),
                        new Handle<Quote>(new SimpleQuote(sig)),
                        volDc);

                final BlackScholesMertonProcess p = new BlackScholesMertonProcess(
                        new Handle<Quote>(new SimpleQuote(spot)),
                        zeroTS, zeroTS,
                        new Handle<BlackVolTermStructure>(bcv));
                processes.add(p);
            }

            // weights = 1 / (futureFixings + pastFixings) per leg
            final double[] weights = new double[futureFixings];
            final double w = 1.0 / (futureFixings + pastFixings);
            for (int i = 0; i < futureFixings; ++i) {
                weights[i] = w;
            }

            final AverageBasketPayoff basketPayoff = new AverageBasketPayoff(
                    new PlainVanillaPayoff(payoff.optionType(), strike),
                    weights);
            final EuropeanExercise basketExercise =
                    new EuropeanExercise(fixingDates.get(fixingDates.size() - 1));
            final BasketOption basketOption =
                    new BasketOption(basketPayoff, basketExercise);
            basketOption.setPricingEngine(
                    new ChoiBasketEngine(processes, rho, lambda_,
                            maxNrIntegrationSteps_, false, false));

            r.value = basketOption.NPV()
                    * rTS.currentLink().discount(exerciseDate);

        } else { // futureFixings == 1
            final Date fixingDate = fixingDates.get(0);
            final double fwd = process_.x0() / (pastFixings + futureFixings)
                    * qTS.currentLink().discount(fixingDate)
                    / rTS.currentLink().discount(fixingDate);
            final double stdDev =
                    Math.sqrt(volTSLink.blackVariance(fixingDate, strike));
            r.value = BlackFormula.blackFormula(
                    payoff.optionType(), strike, fwd, stdDev,
                    rTS.currentLink().discount(exerciseDate));
        }
    }
}
