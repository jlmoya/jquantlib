/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Warren Chou
 Copyright (C) 2007 StatPro Italia srl

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

package org.jquantlib.pricingengines.lookback;

import org.jquantlib.QL;
import org.jquantlib.instruments.ContinuousFixedLookbackOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Pricing engine for European continuous fixed-strike lookback options.
 *
 * <p>Formula from "Option Pricing Formulas", E.G. Haug, McGraw-Hill, 1998,
 * p.63-64 (Conze-Viswanathan 1991).
 *
 * <p>Port of QuantLib v1.42.1
 * {@code QuantLib::AnalyticContinuousFixedLookbackEngine}
 * ({@code ql/pricingengines/lookback/analyticcontinuousfixedlookback.{hpp,cpp}}).
 */
public class AnalyticContinuousFixedLookbackEngine extends ContinuousFixedLookbackOption.EngineImpl {

    private static final String NON_PLAIN_PAYOFF_GIVEN = "Non-plain payoff given";
    private static final String NEGATIVE_OR_NULL_UNDERLYING = "negative or null underlying";
    private static final String UNKNOWN_TYPE = "Unknown type";

    private final GeneralizedBlackScholesProcess process;
    private final CumulativeNormalDistribution f;

    private final ContinuousFixedLookbackOption.ArgumentsImpl a;
    private final ContinuousFixedLookbackOption.ResultsImpl r;

    public AnalyticContinuousFixedLookbackEngine(final GeneralizedBlackScholesProcess process) {
        this.process = process;
        this.f = new CumulativeNormalDistribution();
        this.a = arguments_;
        this.r = results_;
        this.process.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(a.payoff instanceof PlainVanillaPayoff, NON_PLAIN_PAYOFF_GIVEN);
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        QL.require(underlying() > 0.0, NEGATIVE_OR_NULL_UNDERLYING);

        final double strike = payoff.strike();

        switch ( payoff.optionType() ) {
        case Call:
            QL.require(strike >= 0.0, "Strike must be positive or null");
            if ( strike <= minmax() ) {
                r.value = A(1) + C(1);
            } else {
                r.value = B(1);
            }
            break;
        case Put:
            QL.require(strike > 0.0, "Strike must be positive");
            if ( strike >= minmax() ) {
                r.value = A(-1) + C(-1);
            } else {
                r.value = B(-1);
            }
            break;
        default:
            throw new LibraryException(UNKNOWN_TYPE);
        }
    }

    private double underlying() {
        return process.stateVariable().currentLink().value();
    }

    private double strike() {
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        return payoff.strike();
    }

    private double residualTime() {
        return process.time(a.exercise.lastDate());
    }

    private double volatility() {
        return process.blackVolatility().currentLink().blackVol(residualTime(), strike());
    }

    private double stdDeviation() {
        return volatility() * Math.sqrt(residualTime());
    }

    private double riskFreeRate() {
        return process.riskFreeRate().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double riskFreeDiscount() {
        return process.riskFreeRate().currentLink().discount(residualTime());
    }

    private double dividendYield() {
        return process.dividendYield().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double dividendDiscount() {
        return process.dividendYield().currentLink().discount(residualTime());
    }

    private double minmax() {
        return a.minmax;
    }

    /**
     * Conze-Viswanathan {@code A} term: applies when strike is on the right side of the running min/max.
     */
    private double A(final double eta) {
        final double vol = volatility();
        final double lambda = 2.0 * (riskFreeRate() - dividendYield()) / (vol * vol);
        final double ss = underlying() / minmax();
        final double sd = stdDeviation();
        final double d1 = Math.log(ss) / sd + 0.5 * (lambda + 1.0) * sd;
        final double n1 = f.op(eta * d1);
        final double n2 = f.op(eta * (d1 - sd));
        final double n3 = f.op(eta * (d1 - lambda * sd));
        final double n4 = f.op(eta * d1);
        final double powss = Math.pow(ss, -lambda);
        return eta * (underlying() * dividendDiscount() * n1 - minmax() * riskFreeDiscount() * n2
                - underlying() * riskFreeDiscount() * (powss * n3 - dividendDiscount() * n4 / riskFreeDiscount())
                / lambda);
    }

    /**
     * Conze-Viswanathan {@code B} term.
     */
    private double B(final double eta) {
        final double vol = volatility();
        final double lambda = 2.0 * (riskFreeRate() - dividendYield()) / (vol * vol);
        final double ss = underlying() / strike();
        final double sd = stdDeviation();
        final double d1 = Math.log(ss) / sd + 0.5 * (lambda + 1.0) * sd;
        final double n1 = f.op(eta * d1);
        final double n2 = f.op(eta * (d1 - sd));
        final double n3 = f.op(eta * (d1 - lambda * sd));
        final double n4 = f.op(eta * d1);
        final double powss = Math.pow(ss, -lambda);
        return eta * (underlying() * dividendDiscount() * n1 - strike() * riskFreeDiscount() * n2
                - underlying() * riskFreeDiscount() * (powss * n3 - dividendDiscount() * n4 / riskFreeDiscount())
                / lambda);
    }

    /**
     * Conze-Viswanathan {@code C} term — discounted intrinsic of the recorded extremum.
     */
    private double C(final double eta) {
        return eta * (riskFreeDiscount() * (minmax() - strike()));
    }
}
