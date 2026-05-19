/*
 Copyright (C) 2015 Johannes Göttker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen
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
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Risk-neutral terminal density calculator for the Black-Scholes-Merton model with possibly strike-dependent
 * volatility.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/methods/finitedifferences/utilities/bsmrndcalculator.{hpp,cpp}}.
 *
 * <p>The terminal log-spot {@code x = ln(S_T)} under the risk-neutral measure
 * is normally distributed with
 * <pre>
 *   mean   = ln(S_0) - 0.5 * stdDev^2 + ln(D_q(t)/D_r(t))
 *   stdDev = sigma(t, exp(x)) * sqrt(t)
 * </pre>
 * where {@code D_q}, {@code D_r} are dividend / risk-free discount factors and {@code sigma(t, K)} is the Black
 * volatility at the implied strike.
 *
 * @author Phase 5h.5-RND port
 */
public class BSMRNDCalculator extends RiskNeutralDensityCalculator {

    private final GeneralizedBlackScholesProcess process_;

    public BSMRNDCalculator(final GeneralizedBlackScholesProcess process) {
        QL.require(process != null, "process must not be null");
        this.process_ = process;
    }

    @Override
    public double pdf(final double x, final double t) {
        final double[] p = distributionParams(x, t);
        return new NormalDistribution(p[0], p[1]).op(x);
    }

    @Override
    public double cdf(final double x, final double t) {
        final double[] p = distributionParams(x, t);
        return new CumulativeNormalDistribution(p[0], p[1]).op(x);
    }

    @Override
    public double invcdf(final double q, final double t) {
        final double[] p = distributionParams(q, t);
        return new InverseCumulativeNormal(p[0], p[1]).op(q);
    }

    /**
     * Returns {@code [mean, stdDev]} for the log-spot density at {@code (x, t)}. Mirrors the C++
     * {@code distributionParams} private helper.
     */
    private double[] distributionParams(final double x, final double t) {
        final double stdDev = process_.blackVolatility().currentLink().blackVol(t, Math.exp(x)) * Math.sqrt(t);
        final double mean = Math.log(process_.x0()) - 0.5 * stdDev * stdDev + Math.log(
                process_.dividendYield().currentLink().discount(t) / process_.riskFreeRate().currentLink().discount(t));
        return new double[] { mean, stdDev };
    }
}
