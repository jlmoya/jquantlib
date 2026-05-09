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
import org.jquantlib.instruments.ContinuousPartialFloatingLookbackOption;
import org.jquantlib.instruments.FloatingTypePayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.BivariateNormalDistribution;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Pricing engine for European continuous partial-time floating-strike
 * lookback options.
 *
 * <p>Formula from "Option Pricing Formulas, Second Edition", E.G. Haug,
 * 2006, p.146 (Heynen-Kat 1994 partial-time lookback).
 *
 * <p>Port of QuantLib v1.42.1
 * {@code QuantLib::AnalyticContinuousPartialFloatingLookbackEngine}
 * ({@code ql/pricingengines/lookback/analyticcontinuouspartialfloatinglookback.{hpp,cpp}}).
 */
public class AnalyticContinuousPartialFloatingLookbackEngine
        extends ContinuousPartialFloatingLookbackOption.EngineImpl {

    private static final String NON_FLOATING_PAYOFF_GIVEN = "Non-floating payoff given";
    private static final String NEGATIVE_OR_NULL_UNDERLYING = "negative or null underlying";
    private static final String UNKNOWN_TYPE = "Unknown type";

    private final GeneralizedBlackScholesProcess process;
    private final CumulativeNormalDistribution f;

    private final ContinuousPartialFloatingLookbackOption.ArgumentsImpl a;
    private final ContinuousPartialFloatingLookbackOption.ResultsImpl   r;

    public AnalyticContinuousPartialFloatingLookbackEngine(final GeneralizedBlackScholesProcess process) {
        this.process = process;
        this.f = new CumulativeNormalDistribution();
        this.a = arguments_;
        this.r = results_;
        this.process.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(a.payoff instanceof FloatingTypePayoff, NON_FLOATING_PAYOFF_GIVEN);
        final FloatingTypePayoff payoff = (FloatingTypePayoff) a.payoff;
        QL.require(underlying() > 0.0, NEGATIVE_OR_NULL_UNDERLYING);

        switch (payoff.optionType()) {
            case Call:
                r.value = A(1);
                break;
            case Put:
                r.value = A(-1);
                break;
            default:
                throw new LibraryException(UNKNOWN_TYPE);
        }
    }

    private double underlying() {
        return process.stateVariable().currentLink().value();
    }

    private double residualTime() {
        return process.time(a.exercise.lastDate());
    }

    private double volatility() {
        return process.blackVolatility().currentLink().blackVol(residualTime(), minmax());
    }

    private double stdDeviation() {
        return volatility() * Math.sqrt(residualTime());
    }

    private double riskFreeRate() {
        return process.riskFreeRate().currentLink().zeroRate(
                residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double riskFreeDiscount() {
        return process.riskFreeRate().currentLink().discount(residualTime());
    }

    private double dividendYield() {
        return process.dividendYield().currentLink().zeroRate(
                residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double dividendDiscount() {
        return process.dividendYield().currentLink().discount(residualTime());
    }

    private double minmax() {
        return a.minmax;
    }

    private double lambda() {
        return a.lambda;
    }

    private double lookbackPeriodEndTime() {
        return process.time(a.lookbackPeriodEnd);
    }

    /**
     * Heynen-Kat partial-time floating-strike formula. {@code eta = +1} for
     * call, {@code -1} for put.
     *
     * <p>Branches on whether {@code lookbackPeriodEndTime == residualTime}
     * (full lookback period — simpler) or strictly less (partial period —
     * full bivariate-normal expansion).
     */
    private double A(final double eta) {
        final boolean fullLookbackPeriod = lookbackPeriodEndTime() == residualTime();
        final double carry = riskFreeRate() - dividendYield();
        final double vol = volatility();
        final double x = 2.0 * carry / (vol * vol);
        final double s = underlying() / minmax();

        final double ls = Math.log(s);
        final double sd = stdDeviation();
        final double d1 = ls / sd + 0.5 * (x + 1.0) * sd;
        final double d2 = d1 - sd;

        double e1 = 0.0, e2 = 0.0;
        if (!fullLookbackPeriod) {
            final double tau = residualTime() - lookbackPeriodEndTime();
            e1 = (carry + vol * vol / 2.0) * tau / (vol * Math.sqrt(tau));
            e2 = e1 - vol * Math.sqrt(tau);
        }

        final double f1 = (ls + (carry + vol * vol / 2.0) * lookbackPeriodEndTime())
                / (vol * Math.sqrt(lookbackPeriodEndTime()));
        final double f2 = f1 - vol * Math.sqrt(lookbackPeriodEndTime());

        final double l1 = Math.log(lambda()) / vol;
        final double g1 = l1 / Math.sqrt(residualTime());

        final double n1 = f.op(eta * (d1 - g1));
        final double n2 = f.op(eta * (d2 - g1));

        BivariateNormalDistribution cnbn1 = new BivariateNormalDistribution(1);
        BivariateNormalDistribution cnbn2 = new BivariateNormalDistribution(0);
        BivariateNormalDistribution cnbn3 = new BivariateNormalDistribution(-1);
        if (!fullLookbackPeriod) {
            cnbn1 = new BivariateNormalDistribution(Math.sqrt(lookbackPeriodEndTime() / residualTime()));
            cnbn2 = new BivariateNormalDistribution(-Math.sqrt(1 - lookbackPeriodEndTime() / residualTime()));
            cnbn3 = new BivariateNormalDistribution(-Math.sqrt(lookbackPeriodEndTime() / residualTime()));
        }

        final double n3 = cnbn1.op(eta * (-f1 + 2.0 * carry * Math.sqrt(lookbackPeriodEndTime()) / vol),
                                   eta * (-d1 + x * sd - g1));
        double n4 = 0.0, n5 = 0.0, n6 = 0.0, n7 = 0.0;
        if (!fullLookbackPeriod) {
            final double tau = residualTime() - lookbackPeriodEndTime();
            final double g2 = l1 / Math.sqrt(tau);
            n4 = cnbn2.op(-eta * (d1 + g1), eta * (e1 + g2));
            n5 = cnbn2.op(-eta * (d1 - g1), eta * (e1 - g2));
            n6 = cnbn3.op(eta * -f2, eta * (d2 - g1));
            n7 = f.op(eta * (e2 - g2));
        } else {
            n4 = f.op(-eta * (d1 + g1));
        }

        final double n8 = f.op(-eta * f1);
        final double pow_s = Math.pow(s, -x);
        final double pow_l = Math.pow(lambda(), x);

        if (!fullLookbackPeriod) {
            return eta * (underlying() * dividendDiscount() * n1 -
                          lambda() * minmax() * riskFreeDiscount() * n2 +
                          underlying() * riskFreeDiscount() * lambda() / x *
                          (pow_s * n3 - dividendDiscount() / riskFreeDiscount() * pow_l * n4)
                          + underlying() * dividendDiscount() * n5 +
                          riskFreeDiscount() * lambda() * minmax() * n6 -
                          Math.exp(-carry * (residualTime() - lookbackPeriodEndTime())) *
                          dividendDiscount() * (1 + 0.5 * vol * vol / carry) * lambda() *
                          underlying() * n7 * n8);
        } else {
            // Simpler calculation
            return eta * (underlying() * dividendDiscount() * n1 -
                          lambda() * minmax() * riskFreeDiscount() * n2 +
                          underlying() * riskFreeDiscount() * lambda() / x *
                          (pow_s * n3 - dividendDiscount() / riskFreeDiscount() * pow_l * n4));
        }
    }
}
