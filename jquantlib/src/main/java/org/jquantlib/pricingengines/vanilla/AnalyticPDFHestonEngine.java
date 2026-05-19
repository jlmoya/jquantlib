/*
 Copyright (C) 2014, 2015 Klaus Spanderen
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

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.methods.finitedifferences.utilities.HestonRNDCalculator;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.processes.HestonProcess;

/**
 * Analytic engine for arbitrary European payoffs under the Heston model.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/pricingengines/vanilla/analyticpdfhestonengine.{hpp,cpp}}.
 *
 * <p>Reference: A. Dragulescu, V. Yakovenko, 2002. "Probability distribution
 * of returns in the Heston model with stochastic volatility."
 * <a href="http://arxiv.org/pdf/cond-mat/0203046.pdf">arXiv:cond-mat/0203046</a>.
 *
 * <p>Prices an arbitrary {@link Payoff} by integrating
 * {@code payoff(exp(x)) * pdf(x; t) * D_r(t)} over the log-spot grid using Gauss-Lobatto quadrature; pdf comes from
 * {@link HestonRNDCalculator}.
 *
 * <p>Departure from C++: like {@link AnalyticHestonEngine} this Java port takes
 * the {@link HestonProcess} explicitly because Java {@link HestonModel} doesn't expose a {@code process()} accessor.
 *
 * @author Phase 5h.5-RND port
 */
public class AnalyticPDFHestonEngine
        extends GenericModelEngine< HestonModel, OneAssetOption.Arguments, OneAssetOption.Results > {

    private final HestonProcess process_;
    private final double integrationEps_;
    private final int maxIntegrationIterations_;

    /** Convenience constructor with C++ defaults: eps=1e-6, maxIter=10000. */
    public AnalyticPDFHestonEngine(final HestonModel model, final HestonProcess process) {
        this(model, process, 1.0e-6, 10000);
    }

    public AnalyticPDFHestonEngine(final HestonModel model, final HestonProcess process, final double integrationEps,
            final int maxIntegrationIterations) {
        super(model, new OneAssetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        QL.require(process != null, "process must not be null");
        this.process_ = process;
        this.integrationEps_ = integrationEps;
        this.maxIntegrationIterations_ = maxIntegrationIterations;
    }

    @Override
    public void calculate() {
        final OneAssetOption.ArgumentsImpl args = (OneAssetOption.ArgumentsImpl) arguments_;

        QL.require(args.exercise.type() == Exercise.Type.European, "not an European option");

        final double t = process_.time(args.exercise.lastDate());

        // 8-sigma grid in expected variance.
        final double xMax = 8.0 * Math.sqrt(process_.theta().currentLink().value() * t +
                (process_.v0().currentLink().value() - process_.theta().currentLink().value()) * (1.0 - Math.exp(
                        -process_.kappa().currentLink().value() * t)) / process_.kappa().currentLink().value());

        final double x0 = Math.log(process_.s0().currentLink().value());
        final double rD = process_.riskFreeRate().currentLink().discount(t);
        final double qD = process_.dividendYield().currentLink().discount(t);
        final double drift = x0 + Math.log(rD / qD);

        final Payoff payoff = args.payoff;
        final Ops.DoubleOp integrand = new Ops.DoubleOp() {
            @Override
            public double op(final double x) {
                return weightedPayoff(payoff, x, t);
            }
        };

        final double value = new GaussLobattoIntegral(maxIntegrationIterations_, integrationEps_).op(integrand,
                -xMax + drift, xMax + drift);

        ((OneAssetOption.ResultsImpl) results_).value = value;
    }

    /** Probability density at log-spot {@code x_t} and time {@code t}. */
    public double Pv(final double x_t, final double t) {
        return new HestonRNDCalculator(process_, integrationEps_, maxIntegrationIterations_).pdf(x_t, t);
    }

    /** Cumulative distribution {@code Pr(S_T < S)} at time {@code t}. */
    public double cdf(final double s, final double t) {
        return new HestonRNDCalculator(process_, integrationEps_, maxIntegrationIterations_).cdf(Math.log(s), t);
    }

    private double weightedPayoff(final Payoff payoff, final double x_t, final double t) {
        final double rD = process_.riskFreeRate().currentLink().discount(t);
        final double s_t = Math.exp(x_t);
        final double payoffVal = payoff.get(s_t);
        return (payoffVal != 0.0) ? payoffVal * Pv(x_t, t) * rD : 0.0;
    }
}
