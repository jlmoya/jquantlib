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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.SpreadBasketPayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.integrals.GaussHermiteIntegration;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.CholeskyDecomposition;
import org.jquantlib.math.matrixutilities.HouseholderReflection;
import org.jquantlib.math.matrixutilities.HouseholderTransformation;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SVD;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.time.Date;

/**
 * Pricing engine for basket options on multiple underlyings using the
 * Choi (2018) quadrature scheme.
 *
 * <p>This class implements the pricing formula from "Sum of all
 * Black-Scholes-Merton Models: An efficient Pricing Method for Spread,
 * Basket and Asian Options", Jaehyuk Choi, 2018,
 * https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2913048.</p>
 *
 * <p>The basket payoff is reduced to a 1-D conditional Black-Scholes
 * problem (priced via {@link SingleFactorBsmBasketEngine}) and integrated
 * over the remaining (n-1) orthogonal noise dimensions using a tensor-product
 * Gauss-Hermite quadrature.</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/pricingengines/basket/choibasketengine.{hpp,cpp}}.
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.</p>
 *
 * @author Jose Moya
 */
public class ChoiBasketEngine extends BasketOption.Engine {

    private final int n_;
    private final List<GeneralizedBlackScholesProcess> processes_;
    private final Matrix rho_;
    private final double lambda_;
    private final long maxNrIntegrationSteps_;
    private final boolean calcFwdDelta_;
    private final boolean controlVariate_;

    public ChoiBasketEngine(
            final List<GeneralizedBlackScholesProcess> processes,
            final Matrix rho,
            final double lambda) {
        this(processes, rho, lambda, Long.MAX_VALUE, false, false);
    }

    public ChoiBasketEngine(
            final List<GeneralizedBlackScholesProcess> processes,
            final Matrix rho,
            final double lambda,
            final long maxNrIntegrationSteps,
            final boolean calcFwdDelta,
            final boolean controlVariate) {
        this.n_ = processes.size();
        this.processes_ = processes;
        this.rho_ = rho;
        this.lambda_ = lambda;
        this.maxNrIntegrationSteps_ = maxNrIntegrationSteps;
        // controlVariate implies fwdDelta
        this.calcFwdDelta_ = calcFwdDelta || controlVariate;
        this.controlVariate_ = controlVariate;

        QL.require(n_ > 0, "No Black-Scholes process is given.");
        QL.require(n_ == rho_.rows() && rho_.rows() == rho_.columns(),
                "process and correlation matrix must have the same size.");
        QL.require(lambda_ > 0.0, "lambda must be positive");

        for (final GeneralizedBlackScholesProcess p : processes_) {
            p.addObserver(this);
        }
    }

    @Override
    public void calculate() {
        QL.require(arguments_.exercise instanceof EuropeanExercise,
                "not an European exercise");
        final EuropeanExercise exercise = (EuropeanExercise) arguments_.exercise;
        final Date maturityDate = exercise.lastDate();

        // Extract per-process spot, dq, dr0, blackVariance
        final double[] s = new double[n_];
        final double[] dq = new double[n_];
        final double[] stdDevArr = new double[n_];

        final double dr0 = processes_.get(0).riskFreeRate().currentLink()
                .discount(maturityDate);
        for (int i = 0; i < n_; ++i) {
            final GeneralizedBlackScholesProcess p = processes_.get(i);
            s[i] = p.stateVariable().currentLink().value();
            dq[i] = p.dividendYield().currentLink().discount(maturityDate);

            final double variance = p.blackVolatility().currentLink()
                    .blackVariance(maturityDate, s[i]);
            // sqrt(max(eps^2, var)) keeps zero-vol degenerate dimensions stable
            stdDevArr[i] = Math.sqrt(Math.max(
                    Constants.QL_EPSILON * Constants.QL_EPSILON, variance));
        }

        final double[] fwd = new double[n_];
        for (int i = 0; i < n_; ++i) {
            fwd[i] = s[i] * dq[i] / dr0;
        }

        // ---- payoff normalization (Average | Spread) ----
        final AverageBasketPayoff avgPayoff;
        if (arguments_.payoff instanceof AverageBasketPayoff) {
            avgPayoff = (AverageBasketPayoff) arguments_.payoff;
        } else if (arguments_.payoff instanceof SpreadBasketPayoff) {
            final SpreadBasketPayoff sp = (SpreadBasketPayoff) arguments_.payoff;
            avgPayoff = new AverageBasketPayoff(sp.basePayoff(),
                    new double[] { 1.0, -1.0 });
        } else {
            avgPayoff = null;
        }
        QL.require(avgPayoff != null, "average or spread basket payoff expected");

        final double[] weights = avgPayoff.weights();
        QL.require(n_ == weights.length && n_ > 1,
                "wrong number of weights arguments in payoff");

        // g = weights * fwd  (componentwise),  then g /= norm2(g)
        final double[] gArr = new double[n_];
        double gNorm2 = 0.0;
        for (int i = 0; i < n_; ++i) {
            gArr[i] = weights[i] * fwd[i];
            gNorm2 += gArr[i] * gArr[i];
        }
        final double gNorm = Math.sqrt(gNorm2);
        for (int i = 0; i < n_; ++i) {
            gArr[i] /= gNorm;
        }
        final Array g = new Array(gArr);

        // Sigma = diag(stdDev) * rho * diag(stdDev)
        final Matrix Sigma = new Matrix(n_, n_);
        for (int i = 0; i < n_; ++i) {
            for (int j = 0; j < n_; ++j) {
                Sigma.set(i, j, stdDevArr[i] * stdDevArr[j] * rho_.get(i, j));
            }
        }

        // vStar1 = Sigma * g;  vStar1 /= sqrt(g . vStar1)
        Array vStar1 = Sigma.mul(g);
        final double gv = g.dotProduct(vStar1);
        vStar1 = vStar1.mul(1.0 / Math.sqrt(gv));

        final Matrix C = CholeskyDecomposition.CholeskyDecomposition(Sigma, false);

        final double eps = 100.0 * Math.sqrt(Constants.QL_EPSILON);
        final double tol = 100.0 * Math.sqrt(Constants.QL_EPSILON);

        boolean flip = false;
        final double[] vStarArr = new double[n_];
        for (int i = 0; i < n_; ++i) {
            vStarArr[i] = vStar1.get(i);
        }
        for (int i = 0; i < n_; ++i) {
            final double signG = (gArr[i] >= 0.0) ? 1.0 : -1.0;
            if (signG * vStarArr[i] < tol * stdDevArr[i]) {
                flip = true;
                vStarArr[i] = eps * signG * stdDevArr[i];
            }
        }
        if (flip) {
            vStar1 = new Array(vStarArr);
        }

        // Compute q1 = inverse(C) * vStar1 (forward substitution on lower-tri C)
        Array q1;
        if (flip) {
            final double[] q1Arr = new double[n_];
            for (int i = 0; i < n_; ++i) {
                double s2 = 0.0;
                for (int j = 0; j < i; ++j) {
                    s2 += C.get(i, j) * q1Arr[j];
                }
                q1Arr[i] = (vStar1.get(i) - s2) / C.get(i, i);
            }
            q1 = new Array(q1Arr);
            final double q1Norm = Math.sqrt(q1.dotProduct(q1));
            vStar1 = vStar1.mul(1.0 / q1Norm);
        } else {
            q1 = C.transpose().mul(g);
        }
        final double q1Norm2 = Math.sqrt(q1.dotProduct(q1));
        q1 = q1.mul(1.0 / q1Norm2);

        // e1 = (1, 0, 0, ..., 0)
        final Array e1 = new Array(n_);
        e1.set(0, 1.0);

        // R = householder( reflectionVector(e1)(q1) ).getMatrix()
        final Matrix R = new HouseholderTransformation(
                new HouseholderReflection(e1).reflectionVector(q1))
                .getMatrix();

        // R_2_n = columns 1..n-1 of R
        final Matrix R_2_n = new Matrix(n_, n_ - 1);
        for (int i = 0; i < n_; ++i) {
            for (int j = 0; j < n_ - 1; ++j) {
                R_2_n.set(i, j, R.get(i, j + 1));
            }
        }

        // svd of C * R_2_n
        final SVD svd = new SVD(C.mul(R_2_n));
        final Matrix U = svd.U();
        final Array sv = svd.singularValues();

        // v = U * diag(sv)   (only first n-1 columns of U contribute)
        final Matrix v = new Matrix(n_, n_ - 1);
        for (int i = 0; i < n_; ++i) {
            for (int j = 0; j < n_ - 1; ++j) {
                v.set(i, j, U.get(i, j) * sv.get(j));
            }
        }

        // Quadrature orders per dimension (rescale lambda if too many points)
        final int[] nIntOrder = new int[n_ - 1];
        double lambda = lambda_;
        double alpha = 0.0;
        for (int i = 0; i < n_; ++i) {
            alpha += g.get(i) * vStar1.get(i);
        }
        alpha = 1.0 / Math.abs(alpha);
        while (true) {
            final double intScale = lambda * alpha;
            for (int i = 0; i < n_ - 1; ++i) {
                nIntOrder[i] = (int) Math.round(1.0 + intScale * sv.get(i));
            }
            long product = 1L;
            for (int i = 0; i < n_ - 1; ++i) {
                product *= nIntOrder[i];
                if (product < 0L || product > maxNrIntegrationSteps_) {
                    break;
                }
            }
            if (product <= maxNrIntegrationSteps_ && product > 0L) {
                break;
            }
            lambda *= 0.9;
            QL.require(lambda / lambda_ > 1e-10,
                    "can not rescale lambda to fit max integration order");
        }

        // Build n one-asset BlackProcess-like processes whose forward = fwd[i]
        // and whose total stdDev to maturity = vStar1[i].
        final Array vStar1Final = vStar1;
        final List<SimpleQuote> quotes = new ArrayList<SimpleQuote>(n_);
        final List<GeneralizedBlackScholesProcess> p1d =
                new ArrayList<GeneralizedBlackScholesProcess>(n_);
        for (int i = 0; i < n_; ++i) {
            final SimpleQuote q = new SimpleQuote(fwd[i]);
            quotes.add(q);

            final BlackVolTermStructure bv =
                    processes_.get(i).blackVolatility().currentLink();
            final double t = bv.dayCounter()
                    .yearFraction(bv.referenceDate(), maturityDate);
            final double vol = vStar1.get(i) / Math.sqrt(t);

            final BlackConstantVol bcv = new BlackConstantVol(
                    bv.referenceDate(), bv.calendar(), vol, bv.dayCounter());

            // BlackProcess: GBS with dividend == risk-free
            final Handle<YieldTermStructure> rfH =
                    processes_.get(i).riskFreeRate();
            final BlackScholesMertonProcess pp = new BlackScholesMertonProcess(
                    new Handle<Quote>(q),
                    rfH,
                    rfH,
                    new Handle<BlackVolTermStructure>(bcv));
            p1d.add(pp);
        }

        final BasketOption option = new BasketOption(avgPayoff, exercise);
        final SingleFactorBsmBasketEngine engine =
                new SingleFactorBsmBasketEngine(p1d);
        option.setPricingEngine(engine);
        // Bridge "d" from engine.results_ (not propagated by the Java
        // BasketOption.fetchResults path) using a tiny accessor in the
        // SingleFactorBsmBasketEngine.

        // vq[i] = 0.5 * sum_j v[i,j]^2
        final double[] vq = new double[n_];
        for (int i = 0; i < n_; ++i) {
            double s2 = 0.0;
            for (int j = 0; j < n_ - 1; ++j) {
                s2 += v.get(i, j) * v.get(i, j);
            }
            vq[i] = 0.5 * s2;
        }

        // Build the tensor-product Gauss-Hermite quadrature in (n-1) dims.
        final MultiDimQuadrature ghq = new MultiDimQuadrature(nIntOrder);

        final double normFactor = Math.pow(Math.PI, -0.5 * nIntOrder.length);

        final double[] dStore = new double[ghq.size()];
        final int[] dStoreCounter = new int[] { 0 };

        // 1d pricer: for each integration node z, set forwards on the wrapped
        //  processes to f_i = exp(-sqrt(2) * (v*z)_i - vq_i) * fwd_i,
        //  run the SingleFactorBsmBasketEngine, capture "d", multiply by the
        //  Gauss-Hermite weight kernel exp(-||z||^2).
        final MultiDimQuadrature.Func bsm1dPricer = new MultiDimQuadrature.Func() {
            @Override
            public double op(final double[] z) {
                final double[] f = new double[n_];
                double zNorm2 = 0.0;
                for (int j = 0; j < z.length; ++j) {
                    zNorm2 += z[j] * z[j];
                }
                for (int i = 0; i < n_; ++i) {
                    double vz = 0.0;
                    for (int j = 0; j < n_ - 1; ++j) {
                        vz += v.get(i, j) * z[j];
                    }
                    f[i] = Math.exp(-Math.sqrt(2.0) * vz - vq[i]) * fwd[i];
                    quotes.get(i).setValue(f[i]);
                }
                final double npv = option.NPV();
                final double d = engine.getLastD();
                dStore[dStoreCounter[0]++] = d;
                return Math.exp(-zNorm2) * npv;
            }
        };

        results_.value = ghq.integrate(bsm1dPricer) * normFactor;

        if (calcFwdDelta_) {
            QL.require(avgPayoff.basePayoff() instanceof PlainVanillaPayoff,
                    "non-plain vanilla payoff given");
            final PlainVanillaPayoff payoff =
                    (PlainVanillaPayoff) avgPayoff.basePayoff();
            final double putIndicator =
                    (payoff.optionType() == Option.Type.Call) ? 0.0 : -1.0;

            final CumulativeNormalDistribution N = new CumulativeNormalDistribution();

            final double[] fwdDelta = new double[n_];
            final double[] fHat = new double[n_];

            for (int k = 0; k < n_; ++k) {
                final int kFinal = k;
                dStoreCounter[0] = 0;
                final MultiDimQuadrature.Func deltaPricer =
                        new MultiDimQuadrature.Func() {
                    @Override
                    public double op(final double[] z) {
                        final double d = dStore[dStoreCounter[0]++];
                        double vz = 0.0;
                        double zNorm2 = 0.0;
                        for (int j = 0; j < z.length; ++j) {
                            zNorm2 += z[j] * z[j];
                            vz += v.get(kFinal, j) * z[j];
                        }
                        final double f = Math.exp(-Math.sqrt(2.0) * vz - vq[kFinal]);
                        return Math.exp(-zNorm2) * f * N.op(d + vStar1Final.get(kFinal));
                    }
                };
                fwdDelta[k] = dr0 * weights[k]
                        * (ghq.integrate(deltaPricer) * normFactor + putIndicator);
                results_.additionalResults()
                        .put("forwardDelta " + k, Double.valueOf(fwdDelta[k]));
            }

            if (controlVariate_) {
                for (int k = 0; k < n_; ++k) {
                    final int kFinal = k;
                    final MultiDimQuadrature.Func fHatPricer =
                            new MultiDimQuadrature.Func() {
                        @Override
                        public double op(final double[] z) {
                            double vz = 0.0;
                            double zNorm2 = 0.0;
                            for (int j = 0; j < z.length; ++j) {
                                zNorm2 += z[j] * z[j];
                                vz += v.get(kFinal, j) * z[j];
                            }
                            final double f = Math.exp(-Math.sqrt(2.0) * vz - vq[kFinal]);
                            return Math.exp(-zNorm2) * f;
                        }
                    };
                    fHat[k] = ghq.integrate(fHatPricer) * normFactor;
                }
                double cvSum = 0.0;
                for (int k = 0; k < n_; ++k) {
                    cvSum += fwdDelta[k] * fwd[k] * (fHat[k] - 1.0);
                }
                results_.value -= cvSum;
            }
        }
    }

    // -------------------------------------------------------------------
    // Tensor-product multi-dim Gauss-Hermite quadrature (in n-1 dims).
    // Java port of QuantLib::MultiDimGaussianIntegration (v1.42.1
    // ql/math/integrals/gaussianquadratures.{hpp,cpp}) restricted to the
    // Gauss-Hermite case used by the Choi engine.
    // -------------------------------------------------------------------
    private static final class MultiDimQuadrature {

        public interface Func {
            double op(double[] z);
        }

        private final double[] weights;
        private final double[][] xs;

        MultiDimQuadrature(final int[] ns) {
            final int m = ns.length;
            int n = 1;
            for (int i = 0; i < m; ++i) {
                n *= ns[i];
            }

            this.weights = new double[n];
            Arrays.fill(this.weights, 1.0);
            this.xs = new double[n][m];

            final int[] spacing = new int[m];
            if (m > 0) {
                spacing[0] = 1;
                for (int j = 1; j < m; ++j) {
                    spacing[j] = spacing[j - 1] * ns[j - 1];
                }
            }

            // cache (order -> abscissae/weights)
            final Map<Integer, double[]> n2x = new HashMap<Integer, double[]>();
            final Map<Integer, double[]> n2w = new HashMap<Integer, double[]>();
            for (int j = 0; j < m; ++j) {
                final Integer key = Integer.valueOf(ns[j]);
                if (!n2x.containsKey(key)) {
                    final GaussHermiteIntegration q =
                            new GaussHermiteIntegration(ns[j]);
                    final double[] xa = new double[ns[j]];
                    final double[] wa = new double[ns[j]];
                    for (int i = 0; i < ns[j]; ++i) {
                        xa[i] = q.x(i);
                        wa[i] = q.weight(i);
                    }
                    n2x.put(key, xa);
                    n2w.put(key, wa);
                }
            }

            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < m; ++j) {
                    final int order = ns[j];
                    final int nx = (i / spacing[j]) % order;
                    final Integer key = Integer.valueOf(order);
                    weights[i] *= n2w.get(key)[nx];
                    xs[i][j] = n2x.get(key)[nx];
                }
            }
        }

        public int size() {
            return weights.length;
        }

        public double integrate(final Func f) {
            double s = 0.0;
            for (int i = 0; i < weights.length; ++i) {
                s += weights[i] * f.op(xs[i]);
            }
            return s;
        }
    }
}
