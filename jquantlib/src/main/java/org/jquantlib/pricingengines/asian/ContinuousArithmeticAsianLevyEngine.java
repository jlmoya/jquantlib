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
 Copyright (C) 2011 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis

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

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.ContinuousAveragingAsianOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Levy engine for continuously averaged arithmetic Asian options.
 *
 * <p>Two-moment matching analytical pricing engine based on the formulas
 * given in Haug, "Option Pricing Formulas", Second Edition, p. 99-100.
 * Implements a closed-form approximation that matches the first two moments
 * of the arithmetic continuous average distribution against a lognormal.
 *
 * <p>Port of {@code ql/pricingengines/asian/continuousarithmeticasianlevyengine.{hpp,cpp}}
 * from QuantLib v1.42.1.
 *
 * @author Jose Moya
 */
public class ContinuousArithmeticAsianLevyEngine extends ContinuousAveragingAsianOption.EngineImpl {

    private static final String NOT_AN_ARITHMETIC_AVERAGE   = "not an Arithmetic average option";
    private static final String NOT_AN_EUROPEAN_OPTION      = "not an European Option";
    private static final String START_DATE_NOT_PROVIDED     = "start date not provided";
    private static final String START_DATE_AFTER_REFERENCE  =
            "start date must be earlier than or equal to reference date";
    private static final String NON_PLAIN_PAYOFF            = "non-plain payoff given";
    private static final String CURRENT_AVERAGE_REQUIRED    =
            "current average required for seasoned option";

    private final GeneralizedBlackScholesProcess process_;
    private final Handle<? extends Quote>        currentAverage_;
    private final Date                           startDate_;

    /**
     * Primary constructor — start date is taken from the option arguments.
     *
     * @param process         the underlying generalized Black-Scholes process
     * @param currentAverage  current realized average (used only for
     *                        seasoned options where averaging has already
     *                        begun)
     */
    public ContinuousArithmeticAsianLevyEngine(final GeneralizedBlackScholesProcess process,
                                               final Handle<? extends Quote> currentAverage) {
        this.process_        = process;
        this.currentAverage_ = currentAverage;
        this.startDate_      = null;
        process_.addObserver(this);
        currentAverage_.addObserver(this);
    }

    /**
     * Deprecated constructor — kept for backward compatibility with callers
     * that supplied the start date to the engine rather than the option.
     * Mirrors the C++ {@code [[deprecated]]} overload at v1.41.
     *
     * @deprecated use the constructor without a start date and pass the
     *             start date to the option instead.
     */
    @Deprecated
    public ContinuousArithmeticAsianLevyEngine(final GeneralizedBlackScholesProcess process,
                                               final Handle<? extends Quote> currentAverage,
                                               final Date startDate) {
        this.process_        = process;
        this.currentAverage_ = currentAverage;
        this.startDate_      = startDate;
        process_.addObserver(this);
        currentAverage_.addObserver(this);
    }

    @Override
    public void calculate() /* @ReadOnly */ {

        QL.require(arguments_.averageType == AverageType.Arithmetic, NOT_AN_ARITHMETIC_AVERAGE);
        QL.require(arguments_.exercise.type() == Exercise.Type.European, NOT_AN_EUROPEAN_OPTION);

        // Prefer start date from option if available, otherwise fall back to
        // the engine's constructor parameter.  At least one must be set.
        final Date argStart = arguments_.startDate;
        final Date startDate = (argStart != null && !argStart.isNull()) ? argStart : startDate_;
        QL.require(startDate != null && !startDate.isNull(), START_DATE_NOT_PROVIDED);

        final Date refDate = process_.riskFreeRate().currentLink().referenceDate();
        QL.require(startDate.le(refDate), START_DATE_AFTER_REFERENCE);

        final DayCounter rfdc  = process_.riskFreeRate().currentLink().dayCounter();
        final DayCounter divdc = process_.dividendYield().currentLink().dayCounter();
        // voldc kept for parity with C++ (unused in the Levy formula).
        @SuppressWarnings("unused")
        final DayCounter voldc = process_.blackVolatility().currentLink().dayCounter();
        final double spot = process_.stateVariable().currentLink().value();

        // payoff
        QL.require(arguments_.payoff instanceof StrikedTypePayoff, NON_PLAIN_PAYOFF);
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        // original time to maturity (contract length, from averaging start)
        final Date maturity = arguments_.exercise.lastDate();
        final double T = rfdc.yearFraction(startDate, maturity);
        // remaining time to maturity (from today)
        final double T2 = rfdc.yearFraction(refDate, maturity);

        final double strike = payoff.strike();

        final double volatility =
                process_.blackVolatility().currentLink().blackVol(maturity, strike);

        final CumulativeNormalDistribution N = new CumulativeNormalDistribution();

        final double riskFreeRate = process_.riskFreeRate().currentLink()
                .zeroRate(maturity, rfdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double dividendYield = process_.dividendYield().currentLink()
                .zeroRate(maturity, divdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final double b = riskFreeRate - dividendYield;

        final double Se;
        if (Math.abs(b) > 1000.0 * Constants.QL_EPSILON) {
            Se = (spot / (T * b)) *
                    (Math.exp((b - riskFreeRate) * T2) - Math.exp(-riskFreeRate * T2));
        } else {
            Se = spot * T2 / T * Math.exp(-riskFreeRate * T2);
        }

        final double X;
        if (T2 < T) {
            QL.require(!currentAverage_.empty() && currentAverage_.currentLink().isValid(),
                    CURRENT_AVERAGE_REQUIRED);
            X = strike - ((T - T2) / T) * currentAverage_.currentLink().value();
        } else {
            X = strike;
        }

        final double m = (Math.abs(b) > 1000.0 * Constants.QL_EPSILON)
                ? ((Math.exp(b * T2) - 1.0) / b)
                : T2;

        final double M = (2.0 * spot * spot / (b + volatility * volatility)) *
                (((Math.exp((2.0 * b + volatility * volatility) * T2) - 1.0)
                        / (2.0 * b + volatility * volatility)) - m);

        final double D = M / (T * T);

        final double V = Math.log(D) - 2.0 * (riskFreeRate * T2 + Math.log(Se));

        final double d1 = (1.0 / Math.sqrt(V)) * ((Math.log(D) / 2.0) - Math.log(X));
        final double d2 = d1 - Math.sqrt(V);

        if (payoff.optionType() == Option.Type.Call) {
            results_.value = Se * N.op(d1) - X * Math.exp(-riskFreeRate * T2) * N.op(d2);
        } else {
            results_.value = Se * N.op(d1) - X * Math.exp(-riskFreeRate * T2) * N.op(d2)
                    - Se + X * Math.exp(-riskFreeRate * T2);
        }
    }
}
