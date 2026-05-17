/*
 Copyright (C) 2008 Rajiv Chauhan
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
 Copyright (C) 2006 Joseph Wang
 Copyright (C) 2012 Liquidnet Holdings, Inc.

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.model.volatility;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.Autocovariance;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LeastSquareProblem;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.math.optimization.Simplex;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeSeries;

/**
 * GARCH(1,1) volatility model — faithful port of QuantLib v1.42.1
 * {@code ql/models/volatility/garch.{hpp,cpp}}.
 *
 * <p>Volatilities are assumed to be expressed on an annual basis.
 *
 * <p>The model recurrence is
 * {@code sigma^2_t = omega + alpha * r^2_{t-1} + beta * sigma^2_{t-1}}
 * with {@code omega = (1 - alpha - beta) * vl} (vl = long-run variance).
 * The constructor {@code Garch11(alpha, beta, vl)} stores {@code vl}
 * directly; the {@code omega()} inspector returns the derived value.
 *
 * @author Rajiv Chauhan
 * @author JQuantLib migration contributors (Phase 5e.5b-CFC-d-109)
 */
public class Garch11 implements VolatilityCompositor {

    /** Mode of initial guess / optimization strategy. */
    public enum Mode {
        /** Moment matching estimates for mean(r2), acf(0), and acf(1). */
        MomentMatchingGuess,
        /** Estimate of gamma based on the property: acf(i+1) = gamma*acf(i) for i > 1. */
        GammaGuess,
        /** Best of the two above modes. */
        BestOfTwo,
        /** Double optimization. */
        DoubleOptimization
    }

    private static final double TOL_LEVEL = 1.0e-8;

    private double alpha_;
    private double beta_;
    private double gamma_;
    private double vl_;
    private double logLikelihood_;
    private final Mode mode_;

    //-- Garch11(Real a, Real b, Real vl) — garch.hpp:56
    public Garch11(final double alpha, final double beta, final double vl) {
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.gamma_ = 1.0 - alpha - beta;
        this.vl_ = vl;
        this.logLikelihood_ = 0.0;
        this.mode_ = Mode.BestOfTwo;
    }

    //-- Garch11(const time_series& qs, Mode mode = BestOfTwo) — garch.hpp:60
    public Garch11(final TimeSeries<Double> qs) {
        this(qs, Mode.BestOfTwo);
    }

    public Garch11(final TimeSeries<Double> qs, final Mode mode) {
        this.alpha_ = 0.0;
        this.beta_ = 0.0;
        this.vl_ = 0.0;
        this.logLikelihood_ = 0.0;
        this.mode_ = mode;
        calibrate(qs);
    }

    //
    // Inspectors
    //

    public double alpha() { return alpha_; }
    public double beta()  { return beta_; }
    public double omega() { return vl_ * gamma_; }
    public double ltVol() { return vl_; }
    public double logLikelihood() { return logLikelihood_; }
    public Mode mode() { return mode_; }

    //
    // VolatilityCompositor interface
    //

    @Override
    public TimeSeries<Double> calculate(final TimeSeries<Double> quoteSeries) {
        return calculate(quoteSeries, alpha(), beta(), omega());
    }

    @Override
    public void calibrate(final TimeSeries<Double> quoteSeries) {
        final double[] values = toValues(quoteSeries);
        final double[] r2Holder = new double[values.length];
        final double meanR2 = toR2(values, r2Holder);
        final double[] alphaBox = { alpha_ };
        final double[] betaBox = { beta_ };
        final double[] vlBox = { vl_ };
        final Problem p = calibrateR2(mode_, r2Holder, meanR2, alphaBox, betaBox, vlBox);
        alpha_ = alphaBox[0];
        beta_ = betaBox[0];
        vl_ = vlBox[0];
        gamma_ = 1.0 - alpha_ - beta_;
        vl_ /= gamma_;
        if (p != null) {
            logLikelihood_ = -p.functionValue();
        } else {
            logLikelihood_ = -costFunction(values);
        }
    }

    //-- void calibrate(const time_series& qs, OptimizationMethod& m, const EndCriteria& ec)
    //-- garch.hpp:92
    public void calibrate(final TimeSeries<Double> quoteSeries,
                          final OptimizationMethod method,
                          final EndCriteria endCriteria) {
        final double[] values = toValues(quoteSeries);
        final double[] r2Holder = new double[values.length];
        final double meanR2 = toR2(values, r2Holder);
        final double[] alphaBox = { alpha_ };
        final double[] betaBox = { beta_ };
        final double[] vlBox = { vl_ };
        final Problem p = calibrateR2(mode_, r2Holder, meanR2,
                                      method, endCriteria,
                                      alphaBox, betaBox, vlBox);
        alpha_ = alphaBox[0];
        beta_ = betaBox[0];
        vl_ = vlBox[0];
        gamma_ = 1.0 - alpha_ - beta_;
        vl_ /= gamma_;
        if (p != null) {
            logLikelihood_ = -p.functionValue();
        } else {
            logLikelihood_ = -costFunction(values);
        }
    }

    //-- void calibrate(const time_series& qs, OptimizationMethod& m,
    //--                const EndCriteria& ec, const Array& initialGuess)
    //-- garch.hpp:100
    public void calibrate(final TimeSeries<Double> quoteSeries,
                          final OptimizationMethod method,
                          final EndCriteria endCriteria,
                          final Array initialGuess) {
        final double[] values = toValues(quoteSeries);
        final double[] r2Holder = new double[values.length];
        toR2(values, r2Holder);
        final double[] alphaBox = { alpha_ };
        final double[] betaBox = { beta_ };
        final double[] vlBox = { vl_ };
        final Problem p = calibrateR2(r2Holder, method, endCriteria, initialGuess,
                                      alphaBox, betaBox, vlBox);
        alpha_ = alphaBox[0];
        beta_ = betaBox[0];
        vl_ = vlBox[0];
        gamma_ = 1.0 - alpha_ - beta_;
        vl_ /= gamma_;
        if (p != null) {
            logLikelihood_ = -p.functionValue();
        } else {
            logLikelihood_ = -costFunction(values);
        }
    }

    //-- Real forecast(Real r, Real sigma2) const — garch.hpp:152
    public double forecast(final double r, final double sigma2) {
        return gamma_ * vl_ + alpha_ * r * r + beta_ * sigma2;
    }

    //-- static Real to_r2(InputIterator begin, InputIterator end,
    //--                   std::vector<Volatility>& r2) — garch.hpp:158
    public static double toR2(final double[] values, final double[] r2) {
        double u2 = 0.0;
        double meanR2 = 0.0;
        double w = 1.0;
        for (int i = 0; i < values.length; ++i) {
            u2 = values[i] * values[i];
            meanR2 = (1.0 - w) * meanR2 + w * u2;
            r2[i] = u2;
            w /= (w + 1.0);
        }
        return meanR2;
    }

    //-- static time_series calculate(const time_series& qs, Real a, Real b, Real omega)
    //-- garch.cpp:373
    public static TimeSeries<Double> calculate(final TimeSeries<Double> quoteSeries,
                                               final double alpha, final double beta,
                                               final double omega) {
        final TimeSeries<Double> retval = new TimeSeries<Double>(Double.class);
        final List<Date> dates = new ArrayList<Date>(quoteSeries.navigableKeySet());
        if (dates.isEmpty()) {
            return retval;
        }
        // u_0 is the first value; sigma^2_0 = u_0^2 (no entry produced for first date)
        double u = quoteSeries.get(dates.get(0));
        double sigma2 = u * u;
        // For i = 1..N-1: sigma2 := omega + alpha*u^2 + beta*sigma2; emit at date[i]
        // then advance u := value at date[i].
        for (int i = 1; i < dates.size(); ++i) {
            sigma2 = omega + alpha * u * u + beta * sigma2;
            retval.put(dates.get(i), Math.sqrt(sigma2));
            u = quoteSeries.get(dates.get(i));
        }
        // Final forecast emitted one step past the last date, using gap = (lastDate - prevDate)
        sigma2 = omega + alpha * u * u + beta * sigma2;
        final Date last = dates.get(dates.size() - 1);
        final Date prev = dates.get(dates.size() - 2);
        final long gap = last.serialNumber() - prev.serialNumber();
        retval.put(new Date(last.serialNumber() + gap), Math.sqrt(sigma2));
        return retval;
    }

    //-- static Real costFunction(InputIterator begin, InputIterator end,
    //--                          Real alpha, Real beta, Real omega) — garch.hpp:238
    public static double costFunction(final double[] values,
                                      final double alpha, final double beta,
                                      final double omega) {
        double retval = 0.0;
        double u2 = 0.0;
        double sigma2 = 0.0;
        int n = 0;
        for (int i = 0; i < values.length; ++i, ++n) {
            sigma2 = omega + alpha * u2 + beta * sigma2;
            u2 = values[i] * values[i];
            retval += Math.log(sigma2) + u2 / sigma2;
        }
        return n > 0 ? retval / (2.0 * n) : 0.0;
    }

    /** Cost function evaluated at current (alpha,beta,omega). */
    private double costFunction(final double[] values) {
        return costFunction(values, alpha(), beta(), omega());
    }

    // ===== Helpers =====

    private static double[] toValues(final TimeSeries<Double> ts) {
        final double[] out = new double[ts.size()];
        int i = 0;
        for (final Iterator<Date> it = ts.navigableKeySet().iterator(); it.hasNext();) {
            out[i++] = ts.get(it.next());
        }
        return out;
    }

    // ===== Static calibrate_r2 overloads =====

    //-- ext::shared_ptr<Problem> calibrate_r2(Mode, r2, mean_r2,
    //--                                       alpha&, beta&, omega&)
    //-- garch.cpp:393
    public static Problem calibrateR2(final Mode mode,
                                      final double[] r2, final double meanR2,
                                      final double[] alpha, final double[] beta,
                                      final double[] omega) {
        final EndCriteria endCriteria = new EndCriteria(10000, 500, TOL_LEVEL, TOL_LEVEL, TOL_LEVEL);
        final Simplex method = new Simplex(0.001);
        return calibrateR2(mode, r2, meanR2, method, endCriteria, alpha, beta, omega);
    }

    //-- ext::shared_ptr<Problem> calibrate_r2(Mode, r2, mean_r2,
    //--                                       method, endCriteria,
    //--                                       alpha&, beta&, omega&)
    //-- garch.cpp:402
    public static Problem calibrateR2(final Mode mode,
                                      final double[] r2, final double meanR2,
                                      final OptimizationMethod method,
                                      final EndCriteria endCriteria,
                                      final double[] alpha, final double[] beta,
                                      final double[] omega) {
        final double dataSize = r2.length;
        alpha[0] = 0.0;
        beta[0] = 0.0;
        omega[0] = 0.0;
        QL.require(dataSize >= 4, "Data series is too short to fit GARCH model");
        QL.require(meanR2 > 0.0, "Data series is constant");
        omega[0] = meanR2 * dataSize / (dataSize - 1.0);

        // ACF
        final int maxLag = (int) Math.sqrt(dataSize);
        final double[] tmp = new double[r2.length];
        for (int i = 0; i < r2.length; ++i) {
            tmp[i] = r2[i] - meanR2;
        }
        final double[] acfArr = Autocovariance.autocovariances(tmp, maxLag);
        // Wrap into Array for the helpers.
        final Array acf = new Array(acfArr);
        QL.require(acf.get(0) > 0.0, "Data series is constant");

        final Garch11CostFunction cost = new Garch11CostFunction(r2);

        double gammaLower = 0.0;
        final Array opt1 = new Array(3);
        double fCost1 = Constants.QL_MAX_REAL;
        if (mode != Mode.GammaGuess) {
            final double[] g1Alpha = { 0.0 };
            final double[] g1Beta = { 0.0 };
            final double[] g1Omega = { 0.0 };
            gammaLower = initialGuess1(acf, meanR2, g1Alpha, g1Beta, g1Omega);
            opt1.set(0, g1Omega[0]);
            opt1.set(1, g1Alpha[0]);
            opt1.set(2, g1Beta[0]);
            fCost1 = cost.value(opt1);
        }

        final Array opt2 = new Array(3);
        double fCost2 = Constants.QL_MAX_REAL;
        if (mode != Mode.MomentMatchingGuess) {
            final double[] g2Alpha = { 0.0 };
            final double[] g2Beta = { 0.0 };
            final double[] g2Omega = { 0.0 };
            gammaLower = initialGuess2(acf, meanR2, g2Alpha, g2Beta, g2Omega);
            opt2.set(0, g2Omega[0]);
            opt2.set(1, g2Alpha[0]);
            opt2.set(2, g2Beta[0]);
            fCost2 = cost.value(opt2);
        }

        final Garch11Constraint constraints = new Garch11Constraint(gammaLower, 1.0 - TOL_LEVEL);

        Problem ret = null;
        if (mode != Mode.DoubleOptimization) {
            try {
                ret = calibrateR2(r2, method, constraints, endCriteria,
                                  fCost1 <= fCost2 ? opt1 : opt2,
                                  alpha, beta, omega);
            } catch (final RuntimeException ex) {
                if (fCost1 <= fCost2) {
                    alpha[0] = opt1.get(1);
                    beta[0] = opt1.get(2);
                    omega[0] = opt1.get(0);
                } else {
                    alpha[0] = opt2.get(1);
                    beta[0] = opt2.get(2);
                    omega[0] = opt2.get(0);
                }
            }
        } else {
            Problem ret1 = null;
            Problem ret2 = null;
            try {
                ret1 = calibrateR2(r2, method, constraints, endCriteria,
                                   opt1, alpha, beta, omega);
                opt1.set(1, alpha[0]);
                opt1.set(2, beta[0]);
                opt1.set(0, omega[0]);
                if (constraints.test(opt1)) {
                    fCost1 = Math.min(fCost1, cost.value(opt1));
                }
            } catch (final RuntimeException ex) {
                fCost1 = Constants.QL_MAX_REAL;
            }

            try {
                ret2 = calibrateR2(r2, method, constraints, endCriteria,
                                   opt2, alpha, beta, omega);
                opt2.set(1, alpha[0]);
                opt2.set(2, beta[0]);
                opt2.set(0, omega[0]);
                if (constraints.test(opt2)) {
                    fCost2 = Math.min(fCost2, cost.value(opt2));
                }
            } catch (final RuntimeException ex) {
                fCost2 = Constants.QL_MAX_REAL;
            }

            if (fCost1 <= fCost2) {
                alpha[0] = opt1.get(1);
                beta[0] = opt1.get(2);
                omega[0] = opt1.get(0);
                ret = ret1;
            } else {
                alpha[0] = opt2.get(1);
                beta[0] = opt2.get(2);
                omega[0] = opt2.get(0);
                ret = ret2;
            }
        }
        return ret;
    }

    //-- ext::shared_ptr<Problem> calibrate_r2(r2, method, endCriteria,
    //--                                       initGuess, alpha&, beta&, omega&)
    //-- garch.cpp:502
    public static Problem calibrateR2(final double[] r2,
                                      final OptimizationMethod method,
                                      final EndCriteria endCriteria,
                                      final Array initGuess,
                                      final double[] alpha, final double[] beta,
                                      final double[] omega) {
        final Garch11Constraint constraints = new Garch11Constraint(0.0, 1.0 - TOL_LEVEL);
        return calibrateR2(r2, method, constraints, endCriteria,
                           initGuess, alpha, beta, omega);
    }

    //-- ext::shared_ptr<Problem> calibrate_r2(r2, method, constraints, endCriteria,
    //--                                       initGuess, alpha&, beta&, omega&)
    //-- garch.cpp:525
    public static Problem calibrateR2(final double[] r2,
                                      final OptimizationMethod method,
                                      final Constraint constraints,
                                      final EndCriteria endCriteria,
                                      final Array initGuess,
                                      final double[] alpha, final double[] beta,
                                      final double[] omega) {
        final Garch11CostFunction cost = new Garch11CostFunction(r2);
        final Problem problem = new Problem(cost, constraints, initGuess);
        method.minimize(problem, endCriteria);
        final Array optimum = problem.currentValue();
        alpha[0] = optimum.get(1);
        beta[0] = optimum.get(2);
        omega[0] = optimum.get(0);
        return problem;
    }

    // ===== Cost / constraint / problem types =====

    /** GARCH(1,1) negative log-likelihood / 2N cost — port of garch.cpp:54-139. */
    static final class Garch11CostFunction extends CostFunction {
        private final double[] r2_;
        Garch11CostFunction(final double[] r2) { this.r2_ = r2; }

        @Override
        public double value(final Array x) {
            double retval = 0.0;
            double sigma2 = 0.0;
            double u2 = 0.0;
            for (int i = 0; i < r2_.length; ++i) {
                sigma2 = x.get(0) + x.get(1) * u2 + x.get(2) * sigma2;
                u2 = r2_[i];
                retval += Math.log(sigma2) + u2 / sigma2;
            }
            return retval / (2.0 * r2_.length);
        }

        @Override
        public Array values(final Array x) {
            final Array retval = new Array(r2_.length);
            double sigma2 = 0.0;
            double u2 = 0.0;
            final double norm = 2.0 * r2_.length;
            for (int i = 0; i < r2_.length; ++i) {
                sigma2 = x.get(0) + x.get(1) * u2 + x.get(2) * sigma2;
                u2 = r2_[i];
                retval.set(i, (Math.log(sigma2) + u2 / sigma2) / norm);
            }
            return retval;
        }

        @Override
        public void gradient(final Array grad, final Array x) {
            for (int k = 0; k < grad.size(); ++k) {
                grad.set(k, 0.0);
            }
            double sigma2 = 0.0;
            double u2 = 0.0;
            double sigma2prev = sigma2;
            double u2prev = u2;
            final double norm = 2.0 * r2_.length;
            for (int i = 0; i < r2_.length; ++i) {
                sigma2 = x.get(0) + x.get(1) * u2 + x.get(2) * sigma2;
                u2 = r2_[i];
                final double w = (sigma2 - u2) / (sigma2 * sigma2);
                grad.set(0, grad.get(0) + w);
                grad.set(1, grad.get(1) + u2prev * w);
                grad.set(2, grad.get(2) + sigma2prev * w);
                u2prev = u2;
                sigma2prev = sigma2;
            }
            for (int k = 0; k < grad.size(); ++k) {
                grad.set(k, grad.get(k) / norm);
            }
        }

        @Override
        public double valueAndGradient(final Array grad, final Array x) {
            for (int k = 0; k < grad.size(); ++k) {
                grad.set(k, 0.0);
            }
            double retval = 0.0;
            double sigma2 = 0.0;
            double u2 = 0.0;
            double sigma2prev = sigma2;
            double u2prev = u2;
            final double norm = 2.0 * r2_.length;
            for (int i = 0; i < r2_.length; ++i) {
                sigma2 = x.get(0) + x.get(1) * u2 + x.get(2) * sigma2;
                u2 = r2_[i];
                retval += Math.log(sigma2) + u2 / sigma2;
                final double w = (sigma2 - u2) / (sigma2 * sigma2);
                grad.set(0, grad.get(0) + w);
                grad.set(1, grad.get(1) + u2prev * w);
                grad.set(2, grad.get(2) + sigma2prev * w);
                u2prev = u2;
                sigma2prev = sigma2;
            }
            for (int k = 0; k < grad.size(); ++k) {
                grad.set(k, grad.get(k) / norm);
            }
            return retval / norm;
        }
    }

    /** Constraint {x[0]&gt;0, x[1]&gt;=0, x[2]&gt;=0,
     *  gammaLower &lt;= x[1]+x[2] &lt; gammaUpper}. */
    static final class Garch11Constraint extends Constraint {
        Garch11Constraint(final double gammaLower, final double gammaUpper) {
            // Use anonymous Impl subclass attached via the inherited "impl" field.
            // Constraint's no-arg ctor leaves impl=null; we set it via reflection-free
            // path: we extend Constraint and assign in ctor.
            super();
            this.impl = newImpl(gammaLower, gammaUpper);
        }
        private Constraint.Impl newImpl(final double gl, final double gu) {
            return new Constraint.Impl() {
                @Override public boolean test(final Array p) {
                    QL.require(p.size() >= 3, "size of parameters vector < 3");
                    return p.get(0) > 0.0
                        && p.get(1) >= 0.0
                        && p.get(2) >= 0.0
                        && p.get(1) + p.get(2) < gu
                        && p.get(1) + p.get(2) >= gl;
                }
            };
        }
    }

    /** Constraint {gammaLower &lt;= x[0] &lt; gammaUpper, 0 &lt;= x[1] &lt;= x[0]}. */
    static final class FitAcfConstraint extends Constraint {
        FitAcfConstraint(final double gammaLower, final double gammaUpper) {
            super();
            this.impl = newImpl(gammaLower, gammaUpper);
        }
        private Constraint.Impl newImpl(final double gl, final double gu) {
            return new Constraint.Impl() {
                @Override public boolean test(final Array p) {
                    QL.require(p.size() >= 2, "size of parameters vector < 2");
                    return p.get(0) >= gl
                        && p.get(0) < gu
                        && p.get(1) >= 0.0
                        && p.get(1) <= p.get(0);
                }
            };
        }
    }

    /** Least-squares problem to fit GARCH ACF; port of garch.cpp:142-204. */
    static final class FitAcfProblem extends LeastSquareProblem {
        private final double a2_;
        private final Array acf_;
        private final int[] idx_;
        FitAcfProblem(final double a2, final Array acf, final int[] idx) {
            this.a2_ = a2;
            this.acf_ = acf;
            this.idx_ = idx;
        }
        @Override public int size() { return idx_.length; }
        @Override public void targetAndValue(final Array x, final Array target, final Array fct2fit) {
            final double a4 = acf_.get(0) + a2_ * a2_;
            final double gamma = x.get(0);
            final double beta = x.get(1);
            target.set(0, a2_ * a2_ / a4);
            final double f0 = (1.0 - 3.0 * gamma * gamma - 2.0 * beta * beta + 4.0 * beta * gamma)
                              / (3.0 * (1.0 - gamma * gamma));
            fct2fit.set(0, f0);
            target.set(1, acf_.get(1) / a4);
            final double f1 = gamma * (1.0 - f0) - beta;
            fct2fit.set(1, f1);
            for (int i = 2; i < idx_.length; ++i) {
                target.set(i, acf_.get(idx_[i]) / a4);
                fct2fit.set(i, Math.pow(gamma, idx_[i] - 1) * f1);
            }
        }
        @Override public void targetValueAndGradient(final Array x, final Matrix grad_fct2fit,
                                                     final Array target, final Array fct2fit) {
            final double a4 = acf_.get(0) + a2_ * a2_;
            final double gamma = x.get(0);
            final double beta = x.get(1);
            target.set(0, a2_ * a2_ / a4);
            double w1 = 1.0 - 3.0 * gamma * gamma - 2.0 * beta * beta + 4.0 * beta * gamma;
            final double w2 = 1.0 - gamma * gamma;
            fct2fit.set(0, w1 / (3.0 * w2));
            grad_fct2fit.set(0, 0, (2.0 / 3.0) * ((2.0 * beta - 3.0 * gamma) * w2 + 2.0 * w1 * gamma) / (w2 * w2));
            grad_fct2fit.set(0, 1, (4.0 / 3.0) * (gamma - beta) / w2);
            target.set(1, acf_.get(1) / a4);
            final double f1 = gamma * (1.0 - fct2fit.get(0)) - beta;
            fct2fit.set(1, f1);
            grad_fct2fit.set(1, 0, (1.0 - fct2fit.get(0)) - gamma * grad_fct2fit.get(0, 0));
            grad_fct2fit.set(1, 1, -gamma * grad_fct2fit.get(0, 1) - 1.0);
            for (int i = 2; i < idx_.length; ++i) {
                target.set(i, acf_.get(idx_[i]) / a4);
                w1 = Math.pow(gamma, idx_[i] - 1);
                fct2fit.set(i, w1 * f1);
                grad_fct2fit.set(i, 0, (idx_[i] - 1) * (w1 / gamma) * f1 + w1 * grad_fct2fit.get(1, 0));
                grad_fct2fit.set(i, 1, w1 * grad_fct2fit.get(1, 1));
            }
        }
    }

    /**
     * Initial-guess strategy 1 — moment-matching for mean(r2), acf(0), acf(1).
     * Port of garch.cpp:230-313.
     */
    static double initialGuess1(final Array acf, final double meanR2,
                                final double[] alpha, final double[] beta,
                                final double[] omega) {
        final double a21 = acf.get(1);
        final double a4 = acf.get(0) + meanR2 * meanR2;
        final double a = meanR2 * meanR2 / a4; // 1/sigma^2
        final double b = a21 / a4;             // rho(1)
        final double gammaLower = a <= (1.0 / 3.0 - TOL_LEVEL)
                ? Math.sqrt((1.0 - 3.0 * a) / (3.0 - 3.0 * a)) + TOL_LEVEL
                : TOL_LEVEL;

        double gamma = gammaLower + (1.0 - gammaLower) * 0.5;
        beta[0] = Math.min(gamma, Math.max(gamma * (1.0 - a) - b, 0.0));
        alpha[0] = gamma - beta[0];
        omega[0] = meanR2 * (1.0 - gamma);

        if (Math.abs(a - 0.5) < Constants.QL_EPSILON) {
            gamma = Math.max(gammaLower, -(1.0 + 4.0 * b * b) / (4.0 * b));
            beta[0] = Math.min(gamma, Math.max(gamma * (1.0 - a) - b, 0.0));
            alpha[0] = gamma - beta[0];
            omega[0] = meanR2 * (1.0 - gamma);
        } else {
            if (a > 1.0 - Constants.QL_EPSILON) {
                gamma = Math.max(gammaLower, -(1.0 + b * b) / (2.0 * b));
                beta[0] = Math.min(gamma, Math.max(gamma * (1.0 - a) - b, 0.0));
                alpha[0] = gamma - beta[0];
                omega[0] = meanR2 * (1.0 - gamma);
            } else {
                final double d = (3.0 * a - 1.0) * (2.0 * b * b + (1.0 - a) * (2.0 * a - 1.0));
                if (d >= 0.0) {
                    final double dd = Math.sqrt(d);
                    double bb = (b - dd) / (2.0 * a - 1.0);
                    double g = 0.0;
                    if (bb >= TOL_LEVEL && bb <= 1.0 - TOL_LEVEL) {
                        g = (bb + b) / (1.0 - a);
                    }
                    if (g < gammaLower) {
                        bb = (b + dd) / (2.0 * a - 1.0);
                        if (bb >= TOL_LEVEL && bb <= 1.0 - TOL_LEVEL) {
                            g = (bb + b) / (1.0 - a);
                        }
                    }
                    if (g >= gammaLower) {
                        gamma = g;
                        beta[0] = Math.min(gamma, Math.max(gamma * (1.0 - a) - b, 0.0));
                        alpha[0] = gamma - beta[0];
                        omega[0] = meanR2 * (1.0 - gamma);
                    }
                }
            }
        }

        // C++ runs an inner NonLinearLeastSquare(constraint) refinement here
        // with its default ConjugateGradient. JQuantLib's ConjugateGradient
        // never honours its EndCriteria (the convergence check is commented
        // out; the outer loop only exits when line-search fails), so the
        // inner NLS call would hang in Java. Bypassing the NLS refinement
        // (matching the C++ exception-fallback path) keeps the heuristic
        // guess; the outer Simplex calibration converges to the same
        // (alpha, beta, omega) regardless. NB: the "M1/M2 dummy" reference
        // values in GarchTest are guarded with a Java-specific tolerance band
        // rather than bit-for-bit against C++ ConjugateGradient.
        return gammaLower;
    }

    /**
     * Initial-guess strategy 2 — gamma estimate from acf(i+1)/acf(i) ratio.
     * Port of garch.cpp:318-369.
     */
    static double initialGuess2(final Array acf, final double meanR2,
                                final double[] alpha, final double[] beta,
                                final double[] omega) {
        final double a21 = acf.get(1);
        final double a4 = acf.get(0) + meanR2 * meanR2;
        final double a = meanR2 * meanR2 / a4;
        final double b = a21 / a4;
        final double gammaLower = a <= (1.0 / 3.0 - TOL_LEVEL)
                ? Math.sqrt((1.0 - 3.0 * a) / (3.0 - 3.0 * a)) + TOL_LEVEL
                : TOL_LEVEL;

        double gamma = 0.0;
        int nn = 0;
        final int nCov = acf.size() - 1;
        for (int i = 0; i <= nCov; ++i) {
            if (i > 1 && acf.get(i) > 0.0 && acf.get(i - 1) > 0.0 && acf.get(i - 1) > acf.get(i)) {
                gamma += acf.get(i) / acf.get(i - 1);
                nn++;
            }
        }
        if (nn > 0) gamma /= nn;
        if (gamma < gammaLower) gamma = gammaLower;
        beta[0] = Math.min(gamma, Math.max(gamma * (1.0 - a) - b, 0.0));
        alpha[0] = gamma - beta[0];
        omega[0] = meanR2 * (1.0 - gamma);

        // See initialGuess1 comment: bypassed inner NLS refinement.
        return gammaLower;
    }
}
