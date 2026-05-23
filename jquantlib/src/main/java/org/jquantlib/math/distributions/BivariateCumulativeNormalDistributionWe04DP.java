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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.distributions;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.TabulatedGaussLegendre;

/**
 * Cumulative bivariate normal distribution (West 2004) — near double-precision.
 *
 * <p>Faithful Java port of {@code QuantLib::BivariateCumulativeNormalDistributionWe04DP}
 * (v1.42.1 {@code ql/math/distributions/bivariatenormaldistribution.{hpp,cpp}}, pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Reference: Graeme West, "Better Approximations To Cumulative Normal
 * Distributions", Wilmott Magazine 2005 (May), 70-76; algorithm based on
 * Genz (2004), "Numerical Computation of Rectangular Bivariate and Trivariate
 * Normal and t Probabilities", Statistics and Computing 14, 151-160.
 *
 * <p>Implementation uses {@link TabulatedGaussLegendre} with order selected
 * by correlation magnitude: 6 for |rho| < 0.3, 12 for |rho| < 0.75, 20 otherwise.
 *
 * <p>Phase 2 L1-D port.
 */
public class BivariateCumulativeNormalDistributionWe04DP {

    private final double correlation_;
    private final CumulativeNormalDistribution cumnorm_ = new CumulativeNormalDistribution();

    public BivariateCumulativeNormalDistributionWe04DP(final double rho) {
        QL.require(rho >= -1.0, "rho must be >= -1.0 (" + rho + " not allowed)");
        QL.require(rho <= 1.0, "rho must be <= 1.0 (" + rho + " not allowed)");
        this.correlation_ = rho;
    }

    /**
     * Evaluate P(X &lt;= x, Y &lt;= y) where (X, Y) is standard bivariate normal
     * with correlation {@code correlation_}.
     */
    public double op(final double x, final double y) {
        // Mirrors C++ operator()(Real x, Real y) in bivariatenormaldistribution.cpp:165
        final TabulatedGaussLegendre gaussLegendreQuad = new TabulatedGaussLegendre(20);
        if ( Math.abs(correlation_) < 0.3 ) {
            gaussLegendreQuad.setOrder(6);
        } else if ( Math.abs(correlation_) < 0.75 ) {
            gaussLegendreQuad.setOrder(12);
        }

        final double h = -x;
        double k = -y;
        double hk = h * k;
        double BVN = 0.0;

        if ( Math.abs(correlation_) < 0.925 ) {
            if ( Math.abs(correlation_) > 0 ) {
                final double asr = Math.asin(correlation_);
                final Eqn3 f = new Eqn3(h, k, asr);
                BVN = gaussLegendreQuad.evaluate(f);
                BVN *= asr * (0.25 / Math.PI);
            }
            BVN += cumnorm_.op(-h) * cumnorm_.op(-k);
        } else {
            if ( correlation_ < 0 ) {
                k *= -1.0;
                hk *= -1.0;
            }
            if ( Math.abs(correlation_) < 1 ) {
                final double Ass = (1.0 - correlation_) * (1.0 + correlation_);
                double a = Math.sqrt(Ass);
                final double bs = (h - k) * (h - k);
                final double c = (4.0 - hk) / 8.0;
                final double d = (12.0 - hk) / 16.0;
                final double asr = -(bs / Ass + hk) / 2.0;
                if ( asr > -100.0 ) {
                    BVN = a * Math.exp(asr)
                            * (1.0 - c * (bs - Ass) * (1.0 - d * bs / 5.0) / 3.0
                                    + c * d * Ass * Ass / 5.0);
                }
                if ( -hk < 100.0 ) {
                    final double B = Math.sqrt(bs);
                    BVN -= Math.exp(-hk / 2.0) * 2.506628274631
                            * cumnorm_.op(-B / a) * B
                            * (1.0 - c * bs * (1.0 - d * bs / 5.0) / 3.0);
                }
                a /= 2.0;
                final Eqn6 f = new Eqn6(a, c, d, bs, hk);
                BVN += gaussLegendreQuad.evaluate(f);
                BVN /= (-2.0 * Math.PI);
            }

            if ( correlation_ > 0 ) {
                BVN += cumnorm_.op(-Math.max(h, k));
            } else {
                BVN *= -1.0;
                if ( k > h ) {
                    // Evaluate cumnorm where it is most precise: lower tail
                    // because of double accuracy around 0.0 vs around 1.0.
                    if ( h >= 0 ) {
                        BVN += cumnorm_.op(-h) - cumnorm_.op(-k);
                    } else {
                        BVN += cumnorm_.op(k) - cumnorm_.op(h);
                    }
                }
            }
        }
        return BVN;
    }

    // ------------------------------------------------------------------
    // Inner-class integrands (mirroring C++ anonymous-namespace eqn3/eqn6).
    // ------------------------------------------------------------------

    private static final class Eqn3 implements Ops.DoubleOp {
        private final double hk_;
        private final double asr_;
        private final double hs_;

        Eqn3(final double h, final double k, final double asr) {
            this.hk_ = h * k;
            this.asr_ = asr;
            this.hs_ = (h * h + k * k) / 2.0;
        }

        @Override
        public double op(final double x) {
            final double sn = Math.sin(asr_ * (-x + 1.0) * 0.5);
            return Math.exp((sn * hk_ - hs_) / (1.0 - sn * sn));
        }
    }

    private static final class Eqn6 implements Ops.DoubleOp {
        private final double a_;
        private final double c_;
        private final double d_;
        private final double bs_;
        private final double hk_;

        Eqn6(final double a, final double c, final double d, final double bs, final double hk) {
            this.a_ = a;
            this.c_ = c;
            this.d_ = d;
            this.bs_ = bs;
            this.hk_ = hk;
        }

        @Override
        public double op(final double x) {
            double xs = a_ * (-x + 1.0);
            xs = Math.abs(xs * xs);
            final double rs = Math.sqrt(1.0 - xs);
            final double asr = -(bs_ / xs + hk_) / 2.0;
            if ( asr > -100.0 ) {
                return a_ * Math.exp(asr)
                        * (Math.exp(-hk_ * (1.0 - rs) / (2.0 * (1.0 + rs))) / rs
                                - (1.0 + c_ * xs * (1.0 + d_ * xs)));
            } else {
                return 0.0;
            }
        }
    }
}
