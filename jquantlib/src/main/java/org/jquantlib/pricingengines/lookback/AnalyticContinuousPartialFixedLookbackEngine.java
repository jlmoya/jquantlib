/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2014 Francois Botha

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
import org.jquantlib.instruments.ContinuousPartialFixedLookbackOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.BivariateNormalDistribution;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Pricing engine for European continuous partial-time fixed-strike lookback options.
 *
 * <p>Formula from "Option Pricing Formulas, Second Edition", E.G. Haug,
 * 2006, p.148 (Heynen-Kat 1994 partial-time fixed-strike lookback).
 *
 * <p>Port of QuantLib v1.42.1
 * {@code QuantLib::AnalyticContinuousPartialFixedLookbackEngine}
 * ({@code ql/pricingengines/lookback/analyticcontinuouspartialfixedlookback.{hpp,cpp}}).
 */
public class AnalyticContinuousPartialFixedLookbackEngine extends ContinuousPartialFixedLookbackOption.EngineImpl {

    private static final String NON_PLAIN_PAYOFF_GIVEN = "Non-plain payoff given";
    private static final String NEGATIVE_OR_NULL_UNDERLYING = "negative or null underlying";
    private static final String UNKNOWN_TYPE = "Unknown type";

    private final GeneralizedBlackScholesProcess process;
    private final CumulativeNormalDistribution f;

    private final ContinuousPartialFixedLookbackOption.ArgumentsImpl a;
    private final ContinuousPartialFixedLookbackOption.ResultsImpl r;

    public AnalyticContinuousPartialFixedLookbackEngine(final GeneralizedBlackScholesProcess process) {
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

        switch ( payoff.optionType() ) {
        case Call:
            QL.require(payoff.strike() >= 0.0, "Strike must be positive or null");
            r.value = A(1);
            break;
        case Put:
            QL.require(payoff.strike() > 0.0, "Strike must be positive");
            r.value = A(-1);
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

    private double lookbackPeriodStartTime() {
        return process.time(a.lookbackPeriodStart);
    }

    /**
     * Heynen-Kat partial-time fixed-strike formula. {@code eta = +1} for call, {@code -1} for put.
     */
    private double A(final double eta) {
        final boolean differentStartOfLookback = lookbackPeriodStartTime() != residualTime();
        final double carry = riskFreeRate() - dividendYield();

        final double vol = volatility();
        final double x = 2.0 * carry / (vol * vol);
        final double s = underlying() / strike();
        final double ls = Math.log(s);
        final double sd = stdDeviation();
        final double d1 = ls / sd + 0.5 * (x + 1.0) * sd;
        final double d2 = d1 - sd;

        double e1 = 0.0, e2 = 0.0;
        if ( differentStartOfLookback ) {
            final double tau = residualTime() - lookbackPeriodStartTime();
            e1 = (carry + vol * vol / 2.0) * tau / (vol * Math.sqrt(tau));
            e2 = e1 - vol * Math.sqrt(tau);
        }

        final double f1 = (ls + (carry + vol * vol / 2.0) * lookbackPeriodStartTime()) / (vol * Math.sqrt(
                lookbackPeriodStartTime()));
        final double f2 = f1 - vol * Math.sqrt(lookbackPeriodStartTime());

        final double n1 = f.op(eta * d1);
        final double n2 = f.op(eta * d2);

        BivariateNormalDistribution cnbn1 = new BivariateNormalDistribution(-1);
        BivariateNormalDistribution cnbn2 = new BivariateNormalDistribution(0);
        BivariateNormalDistribution cnbn3 = new BivariateNormalDistribution(0);
        if ( differentStartOfLookback ) {
            cnbn1 = new BivariateNormalDistribution(-Math.sqrt(lookbackPeriodStartTime() / residualTime()));
            cnbn2 = new BivariateNormalDistribution(Math.sqrt(1 - lookbackPeriodStartTime() / residualTime()));
            cnbn3 = new BivariateNormalDistribution(-Math.sqrt(1 - lookbackPeriodStartTime() / residualTime()));
        }

        final double n3 = cnbn1.op(eta * (d1 - x * sd),
                eta * (-f1 + 2.0 * carry * Math.sqrt(lookbackPeriodStartTime()) / vol));
        final double n4 = cnbn2.op(eta * e1, eta * d1);
        final double n5 = cnbn3.op(-eta * e1, eta * d1);
        final double n6 = cnbn1.op(eta * f2, -eta * d2);
        final double n7 = f.op(eta * f1);
        final double n8 = f.op(-eta * e2);

        final double pow_s = Math.pow(s, -x);
        final double carryDiscount = Math.exp(-carry * (residualTime() - lookbackPeriodStartTime()));
        return eta * (underlying() * dividendDiscount() * n1 - strike() * riskFreeDiscount() * n2
                + underlying() * riskFreeDiscount() / x * (-pow_s * n3 + dividendDiscount() / riskFreeDiscount() * n4)
                - underlying() * dividendDiscount() * n5 - strike() * riskFreeDiscount() * n6
                + carryDiscount * dividendDiscount() * (1 - 0.5 * vol * vol / carry) * underlying() * n7 * n8);
    }
}
