/*
 Copyright (C) 2026 Jose Moya

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
 */

/*
 Copyright (C) 2024 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.pricingengines.basket;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.Derivative;
import org.jquantlib.math.distributions.SecondDerivative;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.solvers1D.Halley;
import org.jquantlib.math.solvers1D.Newton;
import org.jquantlib.math.solvers1D.Ridder;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * Single-factor BSM basket pricing engine.
 *
 * <p>Pricing engine for baskets where all underlyings are driven by a single
 * stochastic factor. The forward basket is mapped onto a one-dimensional geometric Brownian motion, whose root strike
 * is found by a one-dimensional solver. The resulting pricing formula is a weighted sum of Black-Scholes formulae.</p>
 *
 * <p>Reference: Jaehyuk Choi, "Sum of all Black-Scholes-Merton Models: An
 * efficient Pricing Method for Spread, Basket and Asian Options", https://arxiv.org/pdf/1805.03172 (2018).</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/pricingengines/basket/singlefactorbsmbasketengine.{hpp,cpp}}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.</p>
 *
 * @author Jose Moya
 */
public class SingleFactorBsmBasketEngine extends BasketOption.Engine {

    private final double xTol_;
    private final int n_;
    private final List< GeneralizedBlackScholesProcess > processes_;
    private double lastD_ = Double.NaN;

    public SingleFactorBsmBasketEngine(final List< GeneralizedBlackScholesProcess > processes) {
        this(processes, 1e4 * Constants.QL_EPSILON);
    }

    public SingleFactorBsmBasketEngine(final List< GeneralizedBlackScholesProcess > processes, final double xTol) {
        this.xTol_ = xTol;
        this.n_ = processes.size();
        this.processes_ = processes;

        for ( final GeneralizedBlackScholesProcess p : processes_ ) {
            p.addObserver(this);
        }
    }

    @Override
    public void calculate() {
        QL.require(arguments_.payoff instanceof AverageBasketPayoff, "average basket payoff expected");
        final AverageBasketPayoff avgPayoff = (AverageBasketPayoff) arguments_.payoff;
        QL.require(avgPayoff.basePayoff() instanceof PlainVanillaPayoff, "non-plain vanilla payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) avgPayoff.basePayoff();
        final double strike = payoff.strike();

        final double[] weights = avgPayoff.weights();
        QL.require(n_ == weights.length, "wrong number of weights arguments in payoff");

        QL.require(arguments_.exercise instanceof EuropeanExercise, "not an European exercise");
        final EuropeanExercise exercise = (EuropeanExercise) arguments_.exercise;
        final Date maturityDate = exercise.lastDate();

        // Extract per-process spot / dividend / risk-free / vol arrays
        final double[] s = new double[n_];
        final double[] dq = new double[n_];
        final double[] stdDev = new double[n_];

        final double dr0 = processes_.get(0).riskFreeRate().currentLink().discount(maturityDate);
        for ( int i = 0; i < n_; ++i ) {
            final GeneralizedBlackScholesProcess p = processes_.get(i);
            s[i] = p.stateVariable().currentLink().value();
            dq[i] = p.dividendYield().currentLink().discount(maturityDate);

            // require all risk-free discount factors equal
            final double dri = p.riskFreeRate().currentLink().discount(maturityDate);
            QL.require(Closeness.isCloseEnough(dri, dr0), "interest rates need to be the same for all underlyings");

            final double t = p.blackVolatility().currentLink().timeFromReference(maturityDate);
            final double vol = p.blackVolatility().currentLink().blackVol(maturityDate, s[i]);
            stdDev[i] = vol * Math.sqrt(t);
        }

        // fwdBasket[i] = weights[i] * s[i] * dq[i] / dr0
        final double[] fwdBasket = new double[n_];
        for ( int i = 0; i < n_; ++i ) {
            fwdBasket[i] = weights[i] * s[i] * dq[i] / dr0;
        }

        // Intrinsic case: all vols are zero
        boolean allZeroVol = true;
        for ( int i = 0; i < n_; ++i ) {
            if ( !Closeness.isCloseEnough(stdDev[i], 0.0) ) {
                allZeroVol = false;
                break;
            }
        }
        if ( allZeroVol ) {
            double sumFwd = 0.0;
            for ( int i = 0; i < n_; ++i ) {
                sumFwd += fwdBasket[i];
            }
            results_.value = dr0 * payoff.get(sumFwd);
            return;
        }

        // Build the SumExponentialsRootSolver input arrays:
        //   a[i] = fwdBasket[i] * exp(-0.5 * v[i])
        //   sig[i] = stdDev[i]
        final double[] a = new double[n_];
        for ( int i = 0; i < n_; ++i ) {
            a[i] = fwdBasket[i] * Math.exp(-0.5 * stdDev[i] * stdDev[i]);
        }

        // d = -root
        final double d = -new SumExponentialsRootSolver(a, stdDev, strike).getRoot(xTol_,
                SumExponentialsRootSolver.Strategy.Brent);

        final CumulativeNormalDistribution N = new CumulativeNormalDistribution();
        final double cp = (payoff.optionType() == Option.Type.Call) ? 1.0 : -1.0;

        // value = cp * dr0 * ( -K*N(cp*d) + sum_i fwdBasket[i] * N(cp*(d + stdDev[i])) )
        double sum = -strike * N.op(cp * d);
        for ( int i = 0; i < n_; ++i ) {
            sum += fwdBasket[i] * N.op(cp * (d + stdDev[i]));
        }
        results_.value = cp * dr0 * sum;

        results_.additionalResults().put("d", Double.valueOf(d));
        this.lastD_ = d;
    }

    /**
     * Returns the value of {@code d} from the most recent {@link #calculate()} call (the solver root, sign-flipped).
     * Used as a bridge by {@link ChoiBasketEngine} (the Java port does not propagate engine additionalResults through
     * Instrument).
     */
    public double getLastD() {
        return lastD_;
    }

    /**
     * One-dimensional helper that computes the root in {@code x} of
     * <pre>
     *   F(x) = sum_i a[i] * exp(sig[i] * x) - K
     * </pre>
     *
     * <p>Java port of {@code detail::SumExponentialsRootSolver} from
     * {@code ql/pricingengines/basket/singlefactorbsmbasketengine.{hpp,cpp}}.</p>
     */
    public static final class SumExponentialsRootSolver implements SecondDerivative, Derivative {

        private final double[] a_;
        private final double[] sig_;
        private final double K_;
        private int fCtr_ = 0;
        private int fPrimeCtr_ = 0;
        private int fDoublePrimeCtr_ = 0;
        public SumExponentialsRootSolver(final double[] a, final double[] sig, final double K) {
            QL.require(a.length == sig.length, "Arrays must have the same size");
            this.a_ = a.clone();
            this.sig_ = sig.clone();
            this.K_ = K;
        }

        @Override
        public double op(final double x) {
            ++fCtr_;
            double s = 0.0;
            for ( int i = 0; i < a_.length; ++i ) {
                s += a_[i] * Math.exp(sig_[i] * x);
            }
            return s - K_;
        }

        @Override
        public double derivative(final double x) {
            ++fPrimeCtr_;
            double s = 0.0;
            for ( int i = 0; i < a_.length; ++i ) {
                s += a_[i] * sig_[i] * Math.exp(sig_[i] * x);
            }
            return s;
        }

        @Override
        public double secondDerivative(final double x) {
            ++fDoublePrimeCtr_;
            double s = 0.0;
            for ( int i = 0; i < a_.length; ++i ) {
                s += a_[i] * sig_[i] * sig_[i] * Math.exp(sig_[i] * x);
            }
            return s;
        }

        public int getFCtr() {
            return fCtr_;
        }

        public int getDerivativeCtr() {
            return fPrimeCtr_;
        }

        public int getSecondDerivativeCtr() {
            return fDoublePrimeCtr_;
        }

        public double getRoot() {
            return getRoot(1e6 * Constants.QL_EPSILON, Strategy.Brent);
        }

        public double getRoot(final double xTol, final Strategy strategy) {
            // require a*sig >= 0 element-wise
            for ( int i = 0; i < a_.length; ++i ) {
                final double v = a_[i] * sig_[i];
                QL.require(v >= 0.0, "a*sig should not be negative");
            }

            // logProb := all a_i > 0  ==> require K > 0
            boolean logProb = true;
            for ( int i = 0; i < a_.length; ++i ) {
                if ( !(a_[i] > 0.0) ) {
                    logProb = false;
                    break;
                }
            }
            QL.require(K_ > 0.0 || !logProb, "non-positive strikes only allowed for spread options");

            // linear approximation for initial guess:
            //   denom = sum(a_i * sig_i)
            //   xInit = (K - sum(a_i)) / denom  clamped to [-10, 10]   if |denom| > eps else 0
            double denom = 0.0;
            double sumA = 0.0;
            for ( int i = 0; i < a_.length; ++i ) {
                denom += a_[i] * sig_[i];
                sumA += a_[i];
            }
            final double xInit;
            if ( Math.abs(denom) > 1000.0 * Constants.QL_EPSILON ) {
                final double raw = (K_ - sumA) / denom;
                xInit = Math.min(10.0, Math.max(-10.0, raw));
            } else {
                xInit = 0.0;
            }

            switch ( strategy ) {
            case Brent:
                return new Brent().solve(this, xTol, xInit, 1.0);
            case Newton:
                return new Newton().solve(this, xTol, xInit, 1.0);
            case Ridder:
                return new Ridder().solve(this, xTol, xInit, 1.0);
            case Halley:
                return new Halley().solve(this, xTol, xInit, 1.0);
            default:
                throw new IllegalArgumentException("unknown strategy type");
            }
        }

        public enum Strategy {Ridder, Newton, Brent, Halley}
    }
}
