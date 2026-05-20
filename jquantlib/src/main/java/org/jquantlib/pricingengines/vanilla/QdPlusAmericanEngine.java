/*
 Copyright (C) 2022 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.distributions.Derivative;
import org.jquantlib.math.distributions.SecondDerivative;
import org.jquantlib.math.integrals.TanhSinhIntegral;
import org.jquantlib.math.interpolations.ChebyshevInterpolation;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.solvers1D.Newton;
import org.jquantlib.math.solvers1D.Ridder;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * American option engine based on the QD+ approximation to the exercise boundary.
 *
 * <p>Phase 1 closure A1 port of {@code QuantLib::QdPlusAmericanEngine}
 * (v1.42.1 ql/pricingengines/vanilla/qdplusamericanengine.{hpp,cpp}; pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The QD+ engine itself is a stand-alone analytic approximation that is
 * frequently used to seed the more accurate QD fixed-point engine
 * ({@link QdFpAmericanEngine}).
 *
 * <p>References:
 * <ul>
 *   <li>Li, M. (2009), "Analytical Approximations for the Critical Stock
 *       Prices of American Options: A Performance Comparison."
 *       https://mpra.ub.uni-muenchen.de/15018/1/MPRA_paper_15018.pdf</li>
 *   <li>Leif Andersen, Mark Lake (2021), "Fast American Option Pricing:
 *       The Double-Boundary Case."</li>
 * </ul>
 */
public class QdPlusAmericanEngine extends QdPutCallParityEngine {

    public enum SolverType {Brent, Newton, Ridder, Halley, SuperHalley}

    private final int interpolationPoints_;
    private final SolverType solverType_;
    private final double eps_;
    private final int maxIter_;

    public QdPlusAmericanEngine(final GeneralizedBlackScholesProcess process) {
        this(process, 8, SolverType.Halley, 1e-6, -1);
    }

    public QdPlusAmericanEngine(final GeneralizedBlackScholesProcess process,
                                 final int interpolationPoints) {
        this(process, interpolationPoints, SolverType.Halley, 1e-6, -1);
    }

    public QdPlusAmericanEngine(final GeneralizedBlackScholesProcess process,
                                 final int interpolationPoints,
                                 final SolverType solverType) {
        this(process, interpolationPoints, solverType, 1e-6, -1);
    }

    public QdPlusAmericanEngine(final GeneralizedBlackScholesProcess process,
                                 final int interpolationPoints,
                                 final SolverType solverType,
                                 final double eps) {
        this(process, interpolationPoints, solverType, eps, -1);
    }

    /**
     * @param interpolationPoints number of Chebyshev nodes used to interpolate the exercise boundary
     * @param solverType  solver used for the QD+ boundary at each tau
     * @param eps         solver tolerance / boundary-add-on integration tolerance
     * @param maxIter     max solver iterations; pass {@code -1} for the C++ {@code Null<Size>()} default
     */
    public QdPlusAmericanEngine(final GeneralizedBlackScholesProcess process,
                                 final int interpolationPoints,
                                 final SolverType solverType,
                                 final double eps,
                                 final int maxIter) {
        super(process);
        this.interpolationPoints_ = interpolationPoints;
        this.solverType_ = solverType;
        this.eps_ = eps;
        if (maxIter < 0) {
            this.maxIter_ = (solverType == SolverType.Newton
                    || solverType == SolverType.Brent
                    || solverType == SolverType.Ridder) ? 100 : 10;
        } else {
            this.maxIter_ = maxIter;
        }
    }

    /**
     * Reference table 2 of Andersen-Lake (2021) — admissible {@code xMax} bound.
     */
    public static double xMax(final double K, final double r, final double q) {
        if (r > 0.0 && q > 0.0) {
            return K * Math.min(1.0, r / q);
        } else if (r > 0.0 && q <= 0.0) {
            return K;
        } else if (r == 0.0 && q < 0.0) {
            return K;
        } else if (r == 0.0 && q >= 0.0) {
            return 0.0; // European case
        } else if (r < 0.0 && q >= 0.0) {
            return 0.0; // European case
        } else if (r < 0.0 && q < r) {
            return K; // double boundary
        } else if (r < 0.0 && r <= q && q < 0.0) {
            return 0.0; // European case
        } else {
            throw new IllegalStateException("internal error in QdPlusAmericanEngine.xMax");
        }
    }

    /**
     * Locate the put exercise boundary at sub-time {@code tau} (matching C++
     * {@code putExerciseBoundaryAtTau}). Returns {@code [evaluations, boundary]}.
     */
    public double[] putExerciseBoundaryAtTau(final double S, final double K, final double r,
                                             final double q, final double vol,
                                             final double T, final double tau) {
        if (tau < Constants.QL_EPSILON) {
            return new double[]{0.0, xMax(K, r, q)};
        }

        final QdPlusBoundaryEvaluator eval = new QdPlusBoundaryEvaluator(S, K, r, q, vol, tau, T);

        double x;
        switch (solverType_) {
        case Brent:
            x = buildInSolver(eval, new Brent(), S, K, maxIter_, Constants.NULL_REAL);
            break;
        case Newton:
            x = buildInSolverNewton(eval, S, K, maxIter_, Constants.NULL_REAL);
            break;
        case Ridder:
            x = buildInSolver(eval, new Ridder(), S, K, maxIter_, Constants.NULL_REAL);
            break;
        case Halley:
        case SuperHalley: {
            boolean resultCloseEnough;
            x = eval.xmax();
            double xOld;
            double fx = 0.0;
            final double xmin = eval.xmin();
            do {
                xOld = x;
                fx = eval.value(x);
                final double fPrime = eval.derivative(x);
                final double lf = fx * eval.fprime2(x) / (fPrime * fPrime);
                final double step = (solverType_ == SolverType.Halley)
                        ? (1.0 / (1.0 - 0.5 * lf) * fx / fPrime)
                        : ((1.0 + 0.5 * lf / (1.0 - lf)) * fx / fPrime);
                x = Math.max(xmin, x - step);
                resultCloseEnough = Math.abs(x - xOld) < 0.5 * eps_;
            } while (!resultCloseEnough && eval.evaluations() < maxIter_);

            if (!resultCloseEnough && !Closeness.isClose(Math.abs(fx), 0.0)) {
                x = buildInSolver(eval, new Brent(), S, K, 10 * maxIter_, x);
            }
            break;
        }
        default:
            throw new IllegalStateException("unknown solver type");
        }

        return new double[]{eval.evaluations(), x};
    }

    /**
     * Build the Chebyshev interpolation that represents the put exercise boundary as a function of
     * the squared time-to-expiry transform.
     */
    public ChebyshevInterpolation getPutExerciseBoundary(final double S, final double K,
                                                         final double r, final double q,
                                                         final double vol, final double T) {
        final double xmax = xMax(K, r, q);
        return new ChebyshevInterpolation(interpolationPoints_, new Ops.DoubleOp() {
            @Override
            public double op(final double z) {
                final double x_sq = 0.25 * T * (1.0 + z) * (1.0 + z);
                final double b = putExerciseBoundaryAtTau(S, K, r, q, vol, T, x_sq)[1];
                final double v = Math.log(b / xmax);
                return v * v;
            }
        }, ChebyshevInterpolation.PointsType.SecondKind);
    }

    @Override
    protected double calculatePut(final double S, final double K, final double r,
                                  final double q, final double vol, final double T) {
        QL.require(!(r < 0.0 && q < r), "double-boundary case q<r<0 for a put option is given");

        final ChebyshevInterpolation q_z = getPutExerciseBoundary(S, K, r, q, vol, T);
        final double xmax = xMax(K, r, q);
        final QdPlusAddOnValue aov = new QdPlusAddOnValue(T, S, K, r, q, vol, xmax, q_z);

        // TanhSinhIntegral is always available on the Java side.
        final double addOn = new TanhSinhIntegral(eps_).op(aov, 0.0, Math.sqrt(T));

        QL.require(addOn > -10.0 * eps_, "negative early exercise value " + addOn);

        final double europeanValue = Math.max(0.0,
                new BlackCalculator(Option.Type.Put, K, S * Math.exp((r - q) * T),
                        vol * Math.sqrt(T), Math.exp(-r * T)).value());

        return europeanValue + Math.max(0.0, addOn);
    }

    private double buildInSolver(final QdPlusBoundaryEvaluator eval,
                                 final org.jquantlib.math.AbstractSolver1D<Ops.DoubleOp> solver,
                                 final double S, final double strike, final int maxIter,
                                 final double guessIn) {
        solver.setMaxEvaluations(maxIter);
        solver.setLowerBound(eval.xmin());

        final double fxmin = eval.value(eval.xmin());
        double xmaxLocal = Math.max(0.5 * (eval.xmax() + S), eval.xmax());
        while (eval.value(xmaxLocal) * fxmin > 0.0 && eval.evaluations() < maxIter_) {
            xmaxLocal *= 2.0;
        }

        double guess = guessIn;
        if (guess == Constants.NULL_REAL) {
            guess = 0.5 * (xmaxLocal + S);
        }

        if (guess >= xmaxLocal) {
            guess = Math.nextAfter(xmaxLocal, -1.0);
        } else if (guess <= eval.xmin()) {
            guess = Math.nextAfter(eval.xmin(), Constants.QL_MAX_REAL);
        }

        return solver.solve(eval, eps_, guess, eval.xmin(), xmaxLocal);
    }

    private double buildInSolverNewton(final QdPlusBoundaryEvaluator eval,
                                       final double S, final double strike, final int maxIter,
                                       final double guessIn) {
        final Newton solver = new Newton();
        solver.setMaxEvaluations(maxIter);
        solver.setLowerBound(eval.xmin());

        final double fxmin = eval.value(eval.xmin());
        double xmaxLocal = Math.max(0.5 * (eval.xmax() + S), eval.xmax());
        while (eval.value(xmaxLocal) * fxmin > 0.0 && eval.evaluations() < maxIter_) {
            xmaxLocal *= 2.0;
        }

        double guess = guessIn;
        if (guess == Constants.NULL_REAL) {
            guess = 0.5 * (xmaxLocal + S);
        }

        if (guess >= xmaxLocal) {
            guess = Math.nextAfter(xmaxLocal, -1.0);
        } else if (guess <= eval.xmin()) {
            guess = Math.nextAfter(eval.xmin(), Constants.QL_MAX_REAL);
        }

        return solver.solve(eval, eps_, guess, eval.xmin(), xmaxLocal);
    }

    /**
     * QD+ exercise-boundary equation. Implements {@code QuantLib::QdPlusBoundaryEvaluator}
     * (v1.42.1 qdplusamericanengine.cpp anonymous class).
     */
    static final class QdPlusBoundaryEvaluator implements SecondDerivative {

        private final CumulativeNormalDistribution Phi = new CumulativeNormalDistribution();
        private final NormalDistribution phi = new NormalDistribution();
        private final double tau;
        private final double K;
        private final double sigma;
        private final double sigma2;
        private final double v;
        private final double r;
        private final double q;
        private final double dr;
        private final double dq;
        private final double ddr;
        private final double omega;
        private final double lambda;
        private final double lambdaPrime;
        private final double alpha;
        private final double beta;
        private final double xMaxV;
        private final double xMinV;

        private int nrEvaluations = 0;
        private double sc = Constants.NULL_REAL;
        private double dp;
        private double dm;
        private double Phi_dp;
        private double Phi_dm;
        private double phi_dp;
        private double npvCache;
        private double theta;
        private double charm;

        QdPlusBoundaryEvaluator(final double S, final double strike, final double rf,
                                final double dy, final double vol, final double t, final double T) {
            this.tau = t;
            this.K = strike;
            this.sigma = vol;
            this.sigma2 = vol * vol;
            this.v = vol * Math.sqrt(t);
            this.r = rf;
            this.q = dy;
            this.dr = Math.exp(-rf * t);
            this.dq = Math.exp(-dy * t);
            this.ddr = (Math.abs(rf * t) > 1e-5)
                    ? (rf / (1.0 - dr))
                    : (1.0 / (t * (1.0 - 0.5 * rf * t * (1.0 - rf * t / 3.0))));
            this.omega = 2.0 * (rf - dy) / sigma2;
            final double omegaM1Sq = (omega - 1.0) * (omega - 1.0);
            this.lambda = 0.5 * (-(omega - 1.0) - Math.sqrt(omegaM1Sq + 8.0 * ddr / sigma2));
            this.lambdaPrime = 2.0 * ddr * ddr / (sigma2 * Math.sqrt(omegaM1Sq + 8.0 * ddr / sigma2));
            this.alpha = 2.0 * dr / (sigma2 * (2.0 * lambda + omega - 1.0));
            this.beta = alpha * (ddr + lambdaPrime / (2.0 * lambda + omega - 1.0)) - lambda;
            this.xMaxV = QdPlusAmericanEngine.xMax(strike, rf, dy);
            this.xMinV = Constants.QL_EPSILON * 1e4 * Math.min(0.5 * (strike + S), xMaxV);
        }

        double xmin() { return xMinV; }
        double xmax() { return xMaxV; }
        int evaluations() { return nrEvaluations; }

        @Override
        public double op(final double S) { return value(S); }

        public double value(final double S) {
            ++nrEvaluations;
            if (S != sc) {
                preCalculate(S);
            }
            if (Closeness.isCloseEnough(K - S, npvCache)) {
                return (1.0 - dq * Phi_dp) * S + alpha * theta / dr;
            } else {
                final double c0 = -beta - lambda + alpha * theta / (dr * (K - S - npvCache));
                return (1.0 - dq * Phi_dp) * S + (lambda + c0) * (K - S - npvCache);
            }
        }

        @Override
        public double derivative(final double S) {
            if (S != sc) {
                preCalculate(S);
            }
            return 1.0 - dq * Phi_dp + dq / v * phi_dp + beta * (1.0 - dq * Phi_dp) + alpha / dr * charm;
        }

        @Override
        public double secondDerivative(final double S) {
            return fprime2(S);
        }

        public double fprime2(final double S) {
            if (S != sc) {
                preCalculate(S);
            }
            final double gamma = phi_dp * dq / (v * S);
            final double colour = gamma * (q + (r - q) * dp / v + (1.0 - dp * dm) / (2.0 * tau));
            return dq * (phi_dp / (S * v) - phi_dp * dp / (S * v * v)) + beta * gamma + alpha / dr * colour;
        }

        private void preCalculate(double S) {
            S = Math.max(Constants.QL_EPSILON, S);
            sc = S;
            dp = Math.log(S * dq / (K * dr)) / v + 0.5 * v;
            dm = dp - v;
            Phi_dp = Phi.op(-dp);
            Phi_dm = Phi.op(-dm);
            phi_dp = phi.op(dp);
            npvCache = dr * K * Phi_dm - S * dq * Phi_dp;
            theta = r * K * dr * Phi_dm - q * S * dq * Phi_dp - sigma2 * S / (2.0 * v) * dq * phi_dp;
            charm = -dq * (phi_dp * ((r - q) / v - dm / (2.0 * tau)) + q * Phi_dp);
        }
    }

    /**
     * QD+ early-exercise add-on value {@code A(z)}. Implements
     * {@code QuantLib::detail::QdPlusAddOnValue}.
     */
    static final class QdPlusAddOnValue implements Ops.DoubleOp {
        private final double T_;
        private final double S_;
        private final double K_;
        private final double xmax_;
        private final double r_;
        private final double q_;
        private final double vol_;
        private final ChebyshevInterpolation q_z_;
        private final CumulativeNormalDistribution Phi_ = new CumulativeNormalDistribution();

        QdPlusAddOnValue(final double T, final double S, final double K, final double r,
                         final double q, final double vol, final double xmax,
                         final ChebyshevInterpolation q_z) {
            this.T_ = T;
            this.S_ = S;
            this.K_ = K;
            this.xmax_ = xmax;
            this.r_ = r;
            this.q_ = q;
            this.vol_ = vol;
            this.q_z_ = q_z;
        }

        @Override
        public double op(final double z) {
            final double t = z * z;
            final double q = q_z_.op(2.0 * Math.sqrt(Math.max(0.0, T_ - t) / T_) - 1.0, true);
            final double b_t = xmax_ * Math.exp(-Math.sqrt(Math.max(0.0, q)));

            final double dr = Math.exp(-r_ * t);
            final double dq = Math.exp(-q_ * t);
            final double v = vol_ * Math.sqrt(t);

            double rv;
            if (v >= Constants.QL_EPSILON) {
                if (b_t > Constants.QL_EPSILON) {
                    final double dp = Math.log(S_ * dq / (b_t * dr)) / v + 0.5 * v;
                    rv = 2.0 * z * (r_ * K_ * dr * Phi_.op(-dp + v) - q_ * S_ * dq * Phi_.op(-dp));
                } else {
                    rv = 0.0;
                }
            } else if (Closeness.isCloseEnough(S_ * dq, b_t * dr)) {
                rv = z * (r_ * K_ * dr - q_ * S_ * dq);
            } else if (b_t * dr > S_ * dq) {
                rv = 2.0 * z * (r_ * K_ * dr - q_ * S_ * dq);
            } else {
                rv = 0.0;
            }
            return rv;
        }
    }
}
