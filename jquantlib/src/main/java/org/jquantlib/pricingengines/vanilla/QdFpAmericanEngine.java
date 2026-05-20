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
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.integrals.GaussLegendreIntegrator;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.jquantlib.math.integrals.Integrator;
import org.jquantlib.math.interpolations.ChebyshevInterpolation;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.pricingengines.vanilla.qdfp.QdFpIterationScheme;
import org.jquantlib.pricingengines.vanilla.qdfp.QdFpLegendreScheme;
import org.jquantlib.pricingengines.vanilla.qdfp.QdFpLegendreTanhSinhScheme;
import org.jquantlib.pricingengines.vanilla.qdfp.QdFpTanhSinhIterationScheme;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * High performance / high precision American option engine based on a
 * fixed-point iteration over the exercise boundary.
 *
 * <p>Phase 1 closure A1 port of {@code QuantLib::QdFpAmericanEngine}
 * (v1.42.1 ql/pricingengines/vanilla/qdfpamericanengine.{hpp,cpp}; pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>References:
 * <ul>
 *   <li>Leif Andersen, Mark Lake, Dimitri Offengenden (2015), "High Performance
 *       American Option Pricing", SSRN abstract id 2547027.</li>
 *   <li>Leif Andersen, Mark Lake (2021), "Fast American Option Pricing:
 *       The Double-Boundary Case", Wilmott.</li>
 * </ul>
 */
public class QdFpAmericanEngine extends QdPutCallParityEngine {

    public enum FixedPointEquation { FP_A, FP_B, Auto }

    private final QdFpIterationScheme iterationScheme_;
    private final FixedPointEquation fpEquation_;

    public QdFpAmericanEngine(final GeneralizedBlackScholesProcess bsProcess) {
        this(bsProcess, accurateScheme(), FixedPointEquation.Auto);
    }

    public QdFpAmericanEngine(final GeneralizedBlackScholesProcess bsProcess,
                              final QdFpIterationScheme iterationScheme) {
        this(bsProcess, iterationScheme, FixedPointEquation.Auto);
    }

    public QdFpAmericanEngine(final GeneralizedBlackScholesProcess bsProcess,
                              final QdFpIterationScheme iterationScheme,
                              final FixedPointEquation fpEquation) {
        super(bsProcess);
        this.iterationScheme_ = iterationScheme;
        this.fpEquation_ = fpEquation;
    }

    /** Andersen-Lake fast scheme: Legendre-(7, 2, 7)-27. */
    public static QdFpIterationScheme fastScheme() {
        return FAST_SCHEME;
    }

    /** Andersen-Lake accurate scheme: Legendre-Tanh-Sinh-(25, 5, 13)-1e-8. */
    public static QdFpIterationScheme accurateScheme() {
        return ACCURATE_SCHEME;
    }

    /** Andersen-Lake high-precision scheme: Tanh-Sinh-(10, 30)-1e-10. */
    public static QdFpIterationScheme highPrecisionScheme() {
        return HIGH_PRECISION_SCHEME;
    }

    private static final QdFpIterationScheme FAST_SCHEME =
            new QdFpLegendreScheme(7, 2, 7, 27);
    private static final QdFpIterationScheme ACCURATE_SCHEME =
            new QdFpLegendreTanhSinhScheme(25, 5, 13, 1e-8);
    private static final QdFpIterationScheme HIGH_PRECISION_SCHEME =
            new QdFpTanhSinhIterationScheme(10, 30, 1e-10);

    @Override
    protected double calculatePut(final double S, final double K, final double r,
                                  final double q, final double vol, final double T) {
        QL.require(!(r < 0.0 && q < r), "double-boundary case q<r<0 for a put option is given");

        final double xmax = QdPlusAmericanEngine.xMax(K, r, q);
        final int n = iterationScheme_.getNumberOfChebyshevInterpolationNodes();

        final ChebyshevInterpolation interp = new QdPlusAmericanEngine(
                process_, n + 1, QdPlusAmericanEngine.SolverType.Halley, 1e-8)
                .getPutExerciseBoundary(S, K, r, q, vol, T);

        final double[] z = interp.nodes();
        final double[] x = new double[z.length];
        for (int i = 0; i < z.length; ++i) {
            x[i] = 0.5 * Math.sqrt(T) * (1.0 + z[i]);
        }

        // B(tau) callback: exercise boundary from current Chebyshev interp.
        final BoundaryFn B = new BoundaryFn() {
            @Override
            public double op(final double tau) {
                final double zz = 2.0 * Math.sqrt(Math.abs(tau) / T) - 1.0;
                return xmax * Math.exp(-Math.sqrt(Math.max(0.0, interp.op(zz, true))));
            }
        };

        final Ops.DoubleOp h = new Ops.DoubleOp() {
            @Override
            public double op(final double fv) {
                final double v = Math.log(fv / xmax);
                return v * v;
            }
        };

        final DqFpEquation eqn = (fpEquation_ == FixedPointEquation.FP_A
                || (fpEquation_ == FixedPointEquation.Auto && Math.abs(r - q) < 0.001))
                ? new DqFpEquation_A(K, r, q, vol, B,
                        iterationScheme_.getFixedPointIntegrator())
                : new DqFpEquation_B(K, r, q, vol, B,
                        iterationScheme_.getFixedPointIntegrator());

        final double[] y = new double[x.length];
        y[0] = 0.0;

        final int nNewton = iterationScheme_.getNumberOfJacobiNewtonFixedPointSteps();
        for (int k = 0; k < nNewton; ++k) {
            for (int i = 1; i < x.length; ++i) {
                final double tau = x[i] * x[i];
                final double b = B.op(tau);
                final double[] f = eqn.f(tau, b);
                final double N = f[0];
                final double D = f[1];
                final double fv = f[2];
                if (tau < Constants.QL_EPSILON) {
                    y[i] = h.op(fv);
                } else {
                    final double[] nd = eqn.NDd(tau, b);
                    final double Nd = nd[0];
                    final double Dd = nd[1];
                    final double fd = K * Math.exp(-(r - q) * tau)
                            * (Nd / D - Dd * N / (D * D));
                    y[i] = h.op(b - (fv - b) / (fd - 1.0));
                }
            }
            interp.updateY(y);
        }

        final int nFp = iterationScheme_.getNumberOfNaiveFixedPointSteps();
        for (int k = 0; k < nFp; ++k) {
            for (int i = 1; i < x.length; ++i) {
                final double tau = x[i] * x[i];
                final double[] f = eqn.f(tau, B.op(tau));
                y[i] = h.op(f[2]);
            }
            interp.updateY(y);
        }

        final QdPlusAmericanEngine.QdPlusAddOnValue aov =
                new QdPlusAmericanEngine.QdPlusAddOnValue(T, S, K, r, q, vol, xmax, interp);
        final double addOn = iterationScheme_.getExerciseBoundaryToPriceIntegrator()
                .op(aov, 0.0, Math.sqrt(T));

        final double europeanValue = new BlackCalculator(Option.Type.Put, K,
                S * Math.exp((r - q) * T), vol * Math.sqrt(T), Math.exp(-r * T)).value();

        return Math.max(europeanValue, 0.0) + Math.max(0.0, addOn);
    }

    /** Boundary function {@code B(tau)} closure used by the DqFp equations. */
    interface BoundaryFn {
        double op(double tau);
    }

    /**
     * Common implementation of the QD fixed-point equation. Mirrors
     * {@code QuantLib::DqFpEquation} (v1.42.1 qdfpamericanengine.cpp).
     */
    abstract static class DqFpEquation {
        protected final double r;
        protected final double q;
        protected final double vol;
        protected final BoundaryFn B;
        protected final Integrator integrator;
        protected final double[] x_i;
        protected final double[] w_i;
        protected final NormalDistribution phi = new NormalDistribution();
        protected final CumulativeNormalDistribution Phi = new CumulativeNormalDistribution();

        DqFpEquation(final double r, final double q, final double vol,
                     final BoundaryFn B, final Integrator integrator) {
            this.r = r;
            this.q = q;
            this.vol = vol;
            this.B = B;
            this.integrator = integrator;

            if (integrator instanceof GaussLegendreIntegrator) {
                final GaussLegendreIntegration gli = ((GaussLegendreIntegrator) integrator).getIntegration();
                final int n = gli.order();
                this.x_i = new double[n];
                this.w_i = new double[n];
                for (int i = 0; i < n; ++i) {
                    x_i[i] = gli.x(i);
                    w_i[i] = gli.weight(i);
                }
            } else {
                this.x_i = null;
                this.w_i = null;
            }
        }

        /** {@code (N, D, fv)} triple matching C++ {@code f()}. */
        abstract double[] f(double tau, double b);

        /** {@code (Nd, Dd)} pair matching C++ {@code NDd()}. */
        abstract double[] NDd(double tau, double b);

        protected double[] d(final double t, final double z) {
            final double v = vol * Math.sqrt(t);
            final double m = (Math.log(z) + (r - q) * t) / v + 0.5 * v;
            return new double[]{m, m - v};
        }
    }

    /**
     * QD fixed-point equation B (Andersen-Lake-Offengenden eqn. B).
     */
    static final class DqFpEquation_B extends DqFpEquation {
        private final double K;

        DqFpEquation_B(final double K, final double r, final double q, final double vol,
                       final BoundaryFn B, final Integrator integrator) {
            super(r, q, vol, B, integrator);
            this.K = K;
        }

        @Override
        double[] f(final double tau, final double b) {
            double N;
            double D;
            if (tau < Constants.QL_EPSILON * Constants.QL_EPSILON) {
                if (Closeness.isCloseEnough(b, K)) {
                    N = 0.5;
                    D = 0.5;
                } else if (b < K) {
                    N = 0.0;
                    D = 0.0;
                } else {
                    N = 1.0;
                    D = 1.0;
                }
            } else {
                double ni;
                double di;
                if (x_i != null) {
                    final double c = 0.5 * tau;
                    ni = 0.0;
                    di = 0.0;
                    for (int i = x_i.length - 1; i >= 0; --i) {
                        final double u = c * x_i[i] + c;
                        final double[] dpm = d(tau - u, b / B.op(u));
                        ni += w_i[i] * Math.exp(r * u) * Phi.op(dpm[1]);
                        di += w_i[i] * Math.exp(q * u) * Phi.op(dpm[0]);
                    }
                    ni *= c;
                    di *= c;
                } else {
                    final double bF = b;
                    ni = integrator.op(new Ops.DoubleOp() {
                        @Override
                        public double op(final double u) {
                            final double df = Math.exp(r * u);
                            if (u >= tau * (1.0 - 5.0 * Constants.QL_EPSILON)) {
                                if (Closeness.isCloseEnough(bF, B.op(u))) {
                                    return 0.5 * df;
                                } else {
                                    return df * (bF < B.op(u) ? 0.0 : 1.0);
                                }
                            } else {
                                return df * Phi.op(d(tau - u, bF / B.op(u))[1]);
                            }
                        }
                    }, 0.0, tau);
                    di = integrator.op(new Ops.DoubleOp() {
                        @Override
                        public double op(final double u) {
                            final double df = Math.exp(q * u);
                            if (u >= tau * (1.0 - 5.0 * Constants.QL_EPSILON)) {
                                if (Closeness.isCloseEnough(bF, B.op(u))) {
                                    return 0.5 * df;
                                } else {
                                    return df * (bF < B.op(u) ? 0.0 : 1.0);
                                }
                            } else {
                                return df * Phi.op(d(tau - u, bF / B.op(u))[0]);
                            }
                        }
                    }, 0.0, tau);
                }
                final double[] dpm = d(tau, b / K);
                N = Phi.op(dpm[1]) + r * ni;
                D = Phi.op(dpm[0]) + q * di;
            }

            final double alpha = K * Math.exp(-(r - q) * tau);
            double fv;
            if (tau < Constants.QL_EPSILON * Constants.QL_EPSILON) {
                if (Closeness.isCloseEnough(b, K) || b > K) {
                    fv = alpha;
                } else {
                    if (Closeness.isCloseEnough(q, 0.0)) {
                        fv = alpha * r * ((q < 0.0) ? -1.0 : 1.0) / Constants.QL_EPSILON;
                    } else {
                        fv = alpha * r / q;
                    }
                }
            } else {
                fv = alpha * N / D;
            }
            return new double[]{N, D, fv};
        }

        @Override
        double[] NDd(final double tau, final double b) {
            final double[] dpm = d(tau, b / K);
            return new double[]{
                    phi.op(dpm[1]) / (b * vol * Math.sqrt(tau)),
                    phi.op(dpm[0]) / (b * vol * Math.sqrt(tau))
            };
        }
    }

    /**
     * QD fixed-point equation A (Andersen-Lake-Offengenden eqn. A).
     */
    static final class DqFpEquation_A extends DqFpEquation {
        private final double K;

        DqFpEquation_A(final double K, final double r, final double q, final double vol,
                       final BoundaryFn B, final Integrator integrator) {
            super(r, q, vol, B, integrator);
            this.K = K;
        }

        @Override
        double[] f(final double tau, final double b) {
            final double v = vol * Math.sqrt(tau);
            double N;
            double D;
            if (tau < Constants.QL_EPSILON * Constants.QL_EPSILON) {
                if (Closeness.isCloseEnough(b, K)) {
                    N = 1.0 / (M_SQRT2 * M_SQRTPI * v);
                    D = N + 0.5;
                } else {
                    N = 0.0;
                    D = (b > K) ? 1.0 : 0.0;
                }
            } else {
                final double stv = Math.sqrt(tau) / vol;
                double K12;
                double K3;
                if (x_i != null) {
                    K12 = 0.0;
                    K3 = 0.0;
                    for (int i = x_i.length - 1; i >= 0; --i) {
                        final double y = x_i[i];
                        final double m = 0.25 * tau * (1.0 + y) * (1.0 + y);
                        final double[] dpm = d(m, b / B.op(tau - m));
                        K12 += w_i[i] * Math.exp(q * tau - q * m)
                                * (0.5 * tau * (y + 1.0) * Phi.op(dpm[0]) + stv * phi.op(dpm[0]));
                        K3 += w_i[i] * stv * Math.exp(r * tau - r * m) * phi.op(dpm[1]);
                    }
                } else {
                    final double bF = b;
                    K12 = integrator.op(new Ops.DoubleOp() {
                        @Override
                        public double op(final double y) {
                            final double m = 0.25 * tau * (1.0 + y) * (1.0 + y);
                            final double df = Math.exp(q * tau - q * m);
                            if (y <= 5.0 * Constants.QL_EPSILON - 1.0) {
                                if (Closeness.isCloseEnough(bF, B.op(tau - m))) {
                                    return df * stv / (M_SQRT2 * M_SQRTPI);
                                } else {
                                    return 0.0;
                                }
                            } else {
                                final double dp = d(m, bF / B.op(tau - m))[0];
                                return df * (0.5 * tau * (y + 1.0) * Phi.op(dp) + stv * phi.op(dp));
                            }
                        }
                    }, -1.0, 1.0);
                    K3 = integrator.op(new Ops.DoubleOp() {
                        @Override
                        public double op(final double y) {
                            final double m = 0.25 * tau * (1.0 + y) * (1.0 + y);
                            final double df = Math.exp(r * tau - r * m);
                            if (y <= 5.0 * Constants.QL_EPSILON - 1.0) {
                                if (Closeness.isCloseEnough(bF, B.op(tau - m))) {
                                    return df * stv / (M_SQRT2 * M_SQRTPI);
                                } else {
                                    return 0.0;
                                }
                            } else {
                                return df * stv * phi.op(d(m, bF / B.op(tau - m))[1]);
                            }
                        }
                    }, -1.0, 1.0);
                }
                final double[] dpm = d(tau, b / K);
                N = phi.op(dpm[1]) / v + r * K3;
                D = phi.op(dpm[0]) / v + Phi.op(dpm[0]) + q * K12;
            }

            final double alpha = K * Math.exp(-(r - q) * tau);
            double fv;
            if (tau < Constants.QL_EPSILON * Constants.QL_EPSILON) {
                if (Closeness.isCloseEnough(b, K)) {
                    fv = alpha;
                } else if (b > K) {
                    fv = 0.0;
                } else {
                    if (Closeness.isCloseEnough(q, 0.0)) {
                        fv = alpha * r * ((q < 0.0) ? -1.0 : 1.0) / Constants.QL_EPSILON;
                    } else {
                        fv = alpha * r / q;
                    }
                }
            } else {
                fv = alpha * N / D;
            }

            return new double[]{N, D, fv};
        }

        @Override
        double[] NDd(final double tau, final double b) {
            double Dd;
            double Nd;
            if (tau < Constants.QL_EPSILON * Constants.QL_EPSILON) {
                if (Closeness.isCloseEnough(b, K)) {
                    final double sqTau = Math.sqrt(tau);
                    final double vol2 = vol * vol;
                    Dd = M_1_SQRTPI * M_SQRT_2 * (
                            -(0.5 * vol2 + r - q) / (b * vol * vol2 * sqTau) + 1.0 / (b * vol * sqTau));
                    Nd = M_1_SQRTPI * M_SQRT_2 * (-0.5 * vol2 + r - q) / (b * vol * vol2 * sqTau);
                } else {
                    Dd = 0.0;
                    Nd = 0.0;
                }
            } else {
                final double[] dpm = d(tau, b / K);
                Dd = -phi.op(dpm[0]) * dpm[0] / (b * vol * vol * tau)
                        + phi.op(dpm[0]) / (b * vol * Math.sqrt(tau));
                Nd = -phi.op(dpm[1]) * dpm[1] / (b * vol * vol * tau);
            }
            return new double[]{Nd, Dd};
        }
    }

    // C++ M_SQRT2  = sqrt(2)
    // C++ M_SQRTPI = sqrt(pi)
    // C++ M_1_SQRTPI = 1/sqrt(pi)
    // C++ M_SQRT_2 = 1/sqrt(2)
    private static final double M_SQRT2 = Math.sqrt(2.0);
    private static final double M_SQRTPI = Math.sqrt(Math.PI);
    private static final double M_1_SQRTPI = 1.0 / Math.sqrt(Math.PI);
    private static final double M_SQRT_2 = 1.0 / Math.sqrt(2.0);
}
