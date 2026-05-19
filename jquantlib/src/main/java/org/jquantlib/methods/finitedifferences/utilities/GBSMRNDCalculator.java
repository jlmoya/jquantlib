/*
 Copyright (C) 2017 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Risk-neutral terminal density calculator for the Generalized Black-Scholes-Merton model with strike-dependent (skew)
 * volatility.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/methods/finitedifferences/utilities/gbsmrndcalculator.{hpp,cpp}}.
 *
 * <p>The CDF uses the Breeden-Litzenberger formula:
 * {@code CDF(k) = dC/dK} (resp. put equivalent) adjusted for vega * dvol/dk. The inverse CDF brackets around a
 * lognormal guess then refines with Brent.
 *
 * @author Phase 4j port
 */
public class GBSMRNDCalculator {

    private final GeneralizedBlackScholesProcess process_;

    public GBSMRNDCalculator(final GeneralizedBlackScholesProcess process) {
        QL.require(process != null, "process must not be null");
        this.process_ = process;
    }

    /**
     * Probability density function at {@code k} and time {@code t}. Approximated by finite differences of the CDF.
     */
    public double pdf(final double k, final double t) {
        final double dk = 1e-3 * k;
        return (cdf(k + dk, t) - cdf(k - dk, t)) / (2.0 * dk);
    }

    /**
     * Cumulative distribution function P(S_T <= k) at time {@code t}. Uses the Breeden-Litzenberger formula via
     * BlackCalculator.
     */
    public double cdf(final double k, final double t) {
        final BlackVolTermStructure volTS = process_.blackVolatility().currentLink();

        final double dk = 1e-3 * k;
        final double dvol_dk = (volTS.blackVol(t, k + dk) - volTS.blackVol(t, k - dk)) / (2.0 * dk);

        final YieldTermStructure rfTS = process_.riskFreeRate().currentLink();
        final YieldTermStructure divTS = process_.dividendYield().currentLink();

        final double dR = rfTS.discount(t, true);
        final double dD = divTS.discount(t, true);

        final double forward = process_.x0() * dD / dR;
        final double stdDev = Math.sqrt(volTS.blackVariance(t, k, true));

        if ( forward <= k ) {
            final BlackCalculator calc = new BlackCalculator(new PlainVanillaPayoff(Option.Type.Call, k), forward,
                    stdDev, dR);
            return 1.0 + (calc.strikeSensitivity() + calc.vega(t) * dvol_dk) / dR;
        } else {
            final BlackCalculator calc = new BlackCalculator(new PlainVanillaPayoff(Option.Type.Put, k), forward,
                    stdDev, dR);
            return (calc.strikeSensitivity() + calc.vega(t) * dvol_dk) / dR;
        }
    }

    /**
     * Inverse CDF: returns {@code k} such that {@code P(S_T <= k) = q}. Brackets around a lognormal guess, then solves
     * with Brent.
     */
    public double invcdf(final double q, final double t) {
        final double dR = process_.riskFreeRate().currentLink().discount(t, true);
        final double dD = process_.dividendYield().currentLink().discount(t, true);
        final double fwd = process_.x0() * dD / dR;

        final double atmVariance = Math.sqrt(process_.blackVolatility().currentLink().blackVariance(t, fwd, true));

        final double atmX = new InverseCumulativeNormal().op(q);
        final double guess = fwd * Math.exp(atmVariance * atmX);

        double lower = guess;
        while ( guess / lower < 65535.0 && cdf(lower, t) > q ) {
            lower *= 0.5;
        }

        double upper = guess;
        while ( upper / guess < 65535.0 && cdf(upper, t) < q ) {
            upper *= 2.0;
        }

        QL.require(guess / lower < 65535.0 && upper / guess < 65535.0,
                "GBSMRNDCalculator: could not find start interval for invcdf: (" + lower + ", " + upper + ")");

        final double lo = lower, hi = upper;
        final Brent brent = new Brent();
        brent.setMaxEvaluations(200);
        return brent.solve(k -> cdf(k, t) - q, 1e-10, 0.5 * (lo + hi), lo, hi);
    }
}
