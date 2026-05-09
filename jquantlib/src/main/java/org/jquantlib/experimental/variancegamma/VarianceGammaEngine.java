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
 Copyright (C) 2010 Adrian O' Neill

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

package org.jquantlib.experimental.variancegamma;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.integrals.GaussKronrodNonAdaptive;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.time.Date;

/**
 * Analytic Variance Gamma engine for European vanilla options.
 *
 * <p>Phase 4c port of {@code QuantLib::VarianceGammaEngine}
 * (v1.42.1 ql/experimental/variancegamma/analyticvariancegammaengine.{hpp,cpp}).
 *
 * <p>Prices European vanilla options under the Variance Gamma process via
 * an integral representation. The integration is split at x=0.1 to handle
 * occasional singularities at zero, using a Gauss-Kronrod non-adaptive
 * quadrature on [0, 0.1] and a Gauss-Lobatto adaptive on [0.1, infinity].
 *
 * <p>{@code BlackScholesCalculator} is inlined here as
 * {@code forward = s0_adj * dividendDiscount / riskFreeDiscount} —
 * jquantlib's {@link BlackCalculator} takes a forward, while the C++
 * counterpart's {@code BlackScholesCalculator} accepts spot directly and
 * derives the forward internally.
 *
 * @category pricingengines
 */
public class VarianceGammaEngine extends VanillaOption.EngineImpl {

    private final VarianceGammaProcess process_;
    private final double absErr_;
    private final OneAssetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;

    public VarianceGammaEngine(final VarianceGammaProcess process) {
        this(process, 1.0e-5);
    }

    public VarianceGammaEngine(final VarianceGammaProcess process, final double absoluteError) {
        super();
        QL.require(absoluteError > 0.0, "absolute error must be positive");
        this.process_ = process;
        this.absErr_ = absoluteError;
        this.a = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        process_.addObserver(this);
    }

    @Override
    public void calculate() /*@ReadOnly*/ {

        QL.require(a.exercise.type() == Exercise.Type.European,
                "not an European Option");

        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        QL.require(payoff != null, "non-striked payoff given");

        final Date lastDate = a.exercise.lastDate();
        final double dividendDiscount = process_.dividendYield().currentLink().discount(lastDate);
        final double riskFreeDiscount = process_.riskFreeRate().currentLink().discount(lastDate);

        final DayCounter rfdc = process_.riskFreeRate().currentLink().dayCounter();
        final /*@Time*/ double t = rfdc.yearFraction(
                process_.riskFreeRate().currentLink().referenceDate(), lastDate);

        final Integrand f = new Integrand(payoff,
                process_.x0(),
                t, riskFreeDiscount, dividendDiscount,
                process_.sigma(), process_.nu(), process_.theta());

        // Find an upper integration bound large enough so that f(infinity) is
        // below the target tolerance.
        double infinity = 15.0 * Math.sqrt(process_.nu() * t);
        final double target = absErr_ * 1e-4;
        double val = f.op(infinity);
        while (Math.abs(val) > target) {
            infinity *= 1.5;
            val = f.op(infinity);
        }

        // The integration is split at 0.1 due to occasional singularities at 0.
        final double split = 0.1;
        final GaussKronrodNonAdaptive integrator1 =
                new GaussKronrodNonAdaptive(absErr_, 1000, 0.0);
        final double pvA = integrator1.op(f, 0.0, split);
        final GaussLobattoIntegral integrator2 = new GaussLobattoIntegral(2000, absErr_);
        final double pvB = integrator2.op(f, split, infinity);
        r.value = pvA + pvB;
    }

    /**
     * Integrand: BS price weighted by Gamma(t/nu, nu) PDF in subordinator x.
     *
     * <p>For each x:
     * <ul>
     *   <li>s0_adj = s0 * exp(theta*x + omega*t + 0.5*sigma^2*x)
     *   <li>vol_adj = sigma * sqrt(x/t) * sqrt(t) = sigma * sqrt(x)
     *   <li>BS-price * Gamma-PDF(x; t/nu, nu)
     * </ul>
     */
    private static final class Integrand implements Ops.DoubleOp {

        private final StrikedTypePayoff payoff_;
        private final double s0_;
        private final double t_;
        private final double riskFreeDiscount_;
        private final double dividendDiscount_;
        private final double sigma_;
        private final double nu_;
        private final double theta_;
        private final double omega_;
        private final double gammaDenom_;
        private final double shape_;

        Integrand(final StrikedTypePayoff payoff,
                  final double s0, final double t,
                  final double riskFreeDiscount, final double dividendDiscount,
                  final double sigma, final double nu, final double theta) {
            this.payoff_ = payoff;
            this.s0_ = s0;
            this.t_ = t;
            this.riskFreeDiscount_ = riskFreeDiscount;
            this.dividendDiscount_ = dividendDiscount;
            this.sigma_ = sigma;
            this.nu_ = nu;
            this.theta_ = theta;
            this.omega_ = Math.log(1.0 - theta * nu - (sigma * sigma * nu) / 2.0) / nu;
            // Precompute the denominator of the Gamma PDF (does not depend on x).
            // shape = t/nu, scale = nu.
            this.shape_ = t / nu;
            final GammaFunction gf = new GammaFunction();
            this.gammaDenom_ = Math.exp(gf.logValue(shape_)) * Math.pow(nu, shape_);
        }

        @Override
        public double op(final double x) {
            // Compute adjusted Black-Scholes price.
            final double s0_adj =
                    s0_ * Math.exp(theta_ * x + omega_ * t_ + (sigma_ * sigma_ * x) / 2.0);
            // C++: vol_adj = sigma * sqrt(x/t); vol_adj *= sqrt(t);
            //         => sigma * sqrt(x) (a stdDev when multiplied by 1, see below)
            // Note: C++ passes vol_adj (a vol) and t to BlackScholesCalculator,
            // which forms stdDev = vol_adj * sqrt(t). After the *= sqrt(t),
            // vol_adj already equals stdDev = sigma * sqrt(x).
            final double stdDev = sigma_ * Math.sqrt(x);

            // BlackScholesCalculator(payoff, spot, divDiscount, stdDev, riskFreeDiscount)
            //   forward = spot * divDiscount / riskFreeDiscount.
            // jquantlib's BlackCalculator wants forward+stdDev+discount.
            final double forward = s0_adj * dividendDiscount_ / riskFreeDiscount_;
            final BlackCalculator bs =
                    new BlackCalculator(payoff_, forward, stdDev, riskFreeDiscount_);
            final double bsprice = bs.value();

            // Multiply by Gamma distribution PDF.
            final double gamp =
                    (Math.pow(x, shape_ - 1.0) * Math.exp(-x / nu_)) / gammaDenom_;
            return bsprice * gamp;
        }
    }
}
