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

/*
 Copyright (C) 2009 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis
*/

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

import java.util.ArrayList;
import java.util.List;

/**
 * Pricing engine for European discrete geometric average-strike Asian options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/analytic_discr_geom_av_strike.{hpp,cpp}}
 * {@code AnalyticDiscreteGeometricAverageStrikeAsianEngine} (Phase 5e.5b-CFC-d-243).
 *
 * <p>The closed-form expression follows "Asian Option", E. Levy (1997)
 * in "Exotic Options: The State of the Art", edited by L. Clewlow, C. Strickland, pp. 65-97.
 *
 * @author JQuantLib
 */
public class AnalyticDiscreteGeometricAverageStrikeAsianEngine extends DiscreteAveragingAsianOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process;
    private final DiscreteAveragingAsianOption.ArgumentsImpl a;
    private final DiscreteAveragingAsianOption.ResultsImpl r;

    public AnalyticDiscreteGeometricAverageStrikeAsianEngine(final GeneralizedBlackScholesProcess process) {
        this.process = process;
        this.a = (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;
        this.r = (DiscreteAveragingAsianOption.ResultsImpl) results_;
        process.addObserver(this);
    }

    @Override
    public void calculate() /* @ReadOnly */ {

        QL.require(a.averageType == AverageType.Geometric, "not a geometric average option");

        QL.require(a.exercise.type() == Exercise.Type.European, "not an European option");

        QL.require(a.runningAccumulator > 0.0,
                "positive running product required: " + a.runningAccumulator + " not allowed");
        final double runningLog = Math.log(a.runningAccumulator);
        final int pastFixings = a.pastFixings;
        QL.require(pastFixings == 0, "past fixings currently not managed");

        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;

        final DayCounter rfdc = process.riskFreeRate().currentLink().dayCounter();
        final DayCounter divdc = process.dividendYield().currentLink().dayCounter();
        final DayCounter voldc = process.blackVolatility().currentLink().dayCounter();

        final List< Double > fixingTimes = new ArrayList< Double >();
        for ( int i = 0; i < a.fixingDates.size(); i++ ) {
            final Date fixingDate = a.fixingDates.get(i);
            // C++ uses `fixingDate >= arguments_.fixingDates[0]` — the first
            // fixing date is the reference for the volatility year-fraction.
            if ( fixingDate.ge(a.fixingDates.get(0)) ) {
                final /* @Time */ double t = voldc.yearFraction(a.fixingDates.get(0), fixingDate);
                fixingTimes.add(Double.valueOf(t));
            }
        }

        final int remainingFixings = fixingTimes.size();
        final int numberOfFixings = pastFixings + remainingFixings;
        final double N = numberOfFixings;

        final double pastWeight = pastFixings / N;
        final double futureWeight = 1.0 - pastWeight;

        double timeSum = 0.0;
        for ( int i = 0; i < fixingTimes.size(); i++ ) {
            timeSum += fixingTimes.get(i).doubleValue();
        }

        // C++ uses fixingDates[pastFixings] (i.e. first remaining fixing) as
        // the reference for risk-free-rate residualTime to the exercise date.
        final /* @Time */ double residualTime = rfdc.yearFraction(a.fixingDates.get(pastFixings),
                a.exercise.lastDate());

        final double underlying = process.stateVariable().currentLink().value();
        QL.require(underlying > 0.0, "positive underlying value required");

        final /* @Volatility */ double volatility = process.blackVolatility().currentLink()
                .blackVol(a.exercise.lastDate(), underlying);

        final Date exDate = a.exercise.lastDate();
        final double dividendRate = process.dividendYield().currentLink()
                .zeroRate(exDate, divdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double riskFreeRate = process.riskFreeRate().currentLink()
                .zeroRate(exDate, rfdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double nu = riskFreeRate - dividendRate - 0.5 * volatility * volatility;

        double temp = 0.0;
        for ( int i = pastFixings + 1; i < numberOfFixings; i++ ) {
            temp += fixingTimes.get(i - pastFixings - 1).doubleValue() * (N - i);
        }
        final double variance = volatility * volatility / N / N * (timeSum + 2.0 * temp);
        final double covarianceTerm = volatility * volatility / N * timeSum;
        final double sigmaSum_2 = variance + volatility * volatility * residualTime - 2.0 * covarianceTerm;

        final int M = (pastFixings == 0 ? 1 : pastFixings);
        final double runningLogAverage = runningLog / M;

        final double muG = pastWeight * runningLogAverage + futureWeight * Math.log(underlying) + nu * timeSum / N;

        final CumulativeNormalDistribution f = new CumulativeNormalDistribution();

        final double y1 = (Math.log(underlying) + (riskFreeRate - dividendRate) * residualTime - muG - variance / 2.0
                + sigmaSum_2 / 2.0) / Math.sqrt(sigmaSum_2);
        final double y2 = y1 - Math.sqrt(sigmaSum_2);

        if ( payoff.optionType() == Option.Type.Call ) {
            r.value = underlying * Math.exp(-dividendRate * residualTime) * f.op(y1)
                    - Math.exp(muG + variance / 2.0 - riskFreeRate * residualTime) * f.op(y2);
        } else if ( payoff.optionType() == Option.Type.Put ) {
            r.value = -underlying * Math.exp(-dividendRate * residualTime) * f.op(-y1)
                    + Math.exp(muG + variance / 2.0 - riskFreeRate * residualTime) * f.op(-y2);
        } else {
            throw new IllegalArgumentException("invalid option type");
        }
    }
}
