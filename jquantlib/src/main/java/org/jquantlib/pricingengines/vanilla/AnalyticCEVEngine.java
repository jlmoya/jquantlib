/*
 Copyright (C) 2018 Klaus Spanderen
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
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.distributions.GammaDistribution;
import org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Analytic pricing engine and calculator for European vanilla options under
 * the constant elasticity of variance (CEV) process with absorbing boundary
 * at {@code f = 0}.
 * <p>
 * Process:
 * <pre>  df_t = alpha * f_t^beta * dW_t</pre>
 *
 * <p>Java port of v1.42.1
 * {@code ql/pricingengines/vanilla/analyticcevengine.{hpp,cpp}}.
 *
 * <p>Reference:
 * D.R. Brecher, A.E. Lindsay, <i>Results on the CEV Process, Past and Present</i>.
 *
 * <p>The engine uses the non-central chi-squared CDF (boost on the C++ side,
 * {@link NonCentralCumulativeChiSquaredDistribution} on the Java side) for
 * the standard {@code delta < 2} and {@code delta >= 2} branches; for
 * {@code delta >= 2} the call price further requires the regularized lower
 * incomplete gamma function {@code gamma_p}, supplied by
 * {@link GammaDistribution}.
 *
 * @author Phase 5e.5b-CFC-d-112 port
 */
public class AnalyticCEVEngine extends VanillaOption.EngineImpl {

    /** Forward-only CEV calculator (no discount factor applied). */
    public static final class CEVCalculator {
        private final double f0_;
        private final double alpha_;
        private final double beta_;
        private final double delta_;
        private final double x0_;

        public CEVCalculator(final double f0, final double alpha, final double beta) {
            this.f0_    = f0;
            this.alpha_ = alpha;
            this.beta_  = beta;
            this.delta_ = (1.0 - 2.0 * beta) / (1.0 - beta);
            this.x0_    = X(f0);
        }

        public double f0()    { return f0_; }
        public double alpha() { return alpha_; }
        public double beta()  { return beta_; }

        private double X(final double f) {
            final double ab = alpha_ * (1.0 - beta_);
            return JQuantMath.pow(f, 2.0 * (1.0 - beta_)) / (ab * ab);
        }

        /**
         * Undiscounted CEV option value at maturity time {@code t} for the
         * given strike and option type. Mirrors C++
         * {@code CEVCalculator::value} (analyticcevengine.cpp lines 42-84).
         */
        public double value(final Option.Type optionType,
                            final double strike,
                            final double t) {
            final double kTilde = X(strike);

            if (optionType == Option.Type.Call) {
                if (delta_ < 2.0) {
                    return f0_ * (1.0 - new NonCentralCumulativeChiSquaredDistribution(
                                4.0 - delta_, x0_ / t).op(kTilde / t))
                         - strike * new NonCentralCumulativeChiSquaredDistribution(
                                2.0 - delta_, kTilde / t).op(x0_ / t);
                } else {
                    // C++: g = boost::math::gamma_p(0.5*delta_-1.0, x0_/(2.0*t))
                    final double g = new GammaDistribution(0.5 * delta_ - 1.0)
                            .op(x0_ / (2.0 * t));

                    return f0_ * (g - new NonCentralCumulativeChiSquaredDistribution(
                                delta_ - 2.0, kTilde / t).op(x0_ / t))
                         - strike * new NonCentralCumulativeChiSquaredDistribution(
                                delta_, x0_ / t).op(kTilde / t);
                }
            } else if (optionType == Option.Type.Put) {
                if (delta_ < 2.0) {
                    return - f0_ * new NonCentralCumulativeChiSquaredDistribution(
                                4.0 - delta_, x0_ / t).op(kTilde / t)
                           + strike * (1.0 - new NonCentralCumulativeChiSquaredDistribution(
                                2.0 - delta_, kTilde / t).op(x0_ / t));
                } else {
                    return - f0_ * new NonCentralCumulativeChiSquaredDistribution(
                                delta_ - 2.0, kTilde / t).op(x0_ / t)
                           + strike * (1.0 - new NonCentralCumulativeChiSquaredDistribution(
                                delta_, x0_ / t).op(kTilde / t));
                }
            } else {
                throw new IllegalArgumentException("unknown option type");
            }
        }
    }

    // ----------------------------------------------------------------
    // engine
    // ----------------------------------------------------------------

    private final CEVCalculator calculator_;
    private final Handle<YieldTermStructure> discountCurve_;

    private final Option.ArgumentsImpl     arguments;
    private final Instrument.ResultsImpl   results;

    public AnalyticCEVEngine(final double f0,
                             final double alpha,
                             final double beta,
                             final Handle<YieldTermStructure> discountCurve) {
        super();
        QL.require(discountCurve != null, "null discount curve");
        this.calculator_    = new CEVCalculator(f0, alpha, beta);
        this.discountCurve_ = discountCurve;

        this.arguments = (Option.ArgumentsImpl)     arguments_;
        this.results   = (Instrument.ResultsImpl)   results_;
    }

    @Override
    public void calculate() {
        QL.require(arguments.exercise.type() == Exercise.Type.European,
                "not an European option");

        QL.require(arguments.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments.payoff;

        final Date exerciseDate = arguments.exercise.lastDate();
        final YieldTermStructure rTS = discountCurve_.currentLink();

        results.value = calculator_.value(
                payoff.optionType(),
                payoff.strike(),
                rTS.timeFromReference(exerciseDate))
            * rTS.discount(exerciseDate);
    }
}
