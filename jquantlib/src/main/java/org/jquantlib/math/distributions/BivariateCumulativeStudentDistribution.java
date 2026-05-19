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
 Copyright (C) 2014 Michal Kaut

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.math.distributions;

import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;

/**
 * Bivariate cumulative Student t distribution.
 * <p>
 * Java port of {@code QuantLib::BivariateCumulativeStudentDistribution} (v1.42.1,
 * {@code ql/math/distributions/bivariatestudenttdistribution.{hpp,cpp}}). Implemented following the formulas from
 * Dunnett, C.W. and Sobel, M. (1954). "A bivariate generalization of Student t-distribution with tables for certain
 * special cases." Biometrika 41, 153-169.
 * <p>
 * Constructed with degrees of freedom {@code n} (positive integer) and correlation {@code rho} in {@code [-1, 1]};
 * evaluating at a pair {@code (x, y)} returns {@code P(X <= x, Y <= y)} for the bivariate t-distribution with the given
 * parameters.
 */
public class BivariateCumulativeStudentDistribution implements Ops.BinaryDoubleOp {

    private static final double EPSILON = 1.0e-8;

    private final int n_;
    private final double rho_;

    /**
     * @param n   degrees of freedom (positive)
     * @param rho correlation in {@code [-1, 1]}
     */
    public BivariateCumulativeStudentDistribution(final int n, final double rho) {
        this.n_ = n;
        this.rho_ = rho;
    }

    // sign function returning -1 / 0 / +1
    private static double sign(final double val) {
        if ( val == 0.0 ) {
            return 0.0;
        }
        return val < 0.0 ? -1.0 : 1.0;
    }

    /*
     * Unlike the atan2 function in C++ that gives results in [-pi, pi], this
     * returns a value in [0, 2*pi].
     */
    private static double arctan(final double x, final double y) {
        final double res = Math.atan2(x, y);
        return res >= 0.0 ? res : res + 2.0 * Constants.M_PI;
    }

    // function x(m, h, k) defined on top of page 155
    private static double fX(final double m, final double h, final double k, final double rho) {
        final double unCor = 1.0 - rho * rho;
        final double sub = (h - rho * k) * (h - rho * k);
        final double denom = sub + unCor * (m + k * k);
        if ( denom < EPSILON ) {
            return 0.0; // limit case for rho = +/-1.0
        }
        return sub / (sub + unCor * (m + k * k));
    }

    // calculates the CDF
    private static double pN(final double h, final double k, final int n, final double rho) {
        final double unCor = 1.0 - rho * rho;

        final double div = 4.0 * Math.sqrt(n * Constants.M_PI);
        final double xHK = fX(n, h, k, rho);
        final double xKH = fX(n, k, h, rho);
        final double divH = 1.0 + h * h / n;
        final double divK = 1.0 + k * k / n;
        final double sgnHK = sign(h - rho * k);
        final double sgnKH = sign(k - rho * h);

        if ( n % 2 == 0 ) {
            // n even, equation (10)

            // first line of (10)
            double res = arctan(Math.sqrt(unCor), -rho) / Constants.M_TWOPI;

            // second line of (10): contribution involving k / div
            double dgM = 2.0 * (1.0 - xHK);   // multiplier for dgj
            double gjM = sgnHK * 2.0 / Constants.M_PI; // multiplier for g_j
            // initializations for j = 1:
            double fJ = Math.sqrt(Constants.M_PI / divK);
            double gJ = 1.0 + gjM * arctan(Math.sqrt(xHK), Math.sqrt(1.0 - xHK));
            double sum = fJ * gJ;
            if ( n >= 4 ) {
                // different formulas for j = 2:
                fJ *= 0.5 / divK; // (2 - 1.5) / (j - 1) / divK
                double dgj = gjM * Math.sqrt(xHK * (1.0 - xHK));
                gJ += dgj;
                sum += fJ * gJ;
                // and then the loop for the rest of the j's:
                for ( int j = 3; j <= n / 2; ++j ) {
                    fJ *= (j - 1.5) / (j - 1) / divK;
                    dgj *= ((double) (j - 2)) / (2 * j - 3) * dgM;
                    gJ += dgj;
                    sum += fJ * gJ;
                }
            }
            res += k / div * sum;

            // third line of (10): symmetric contribution involving h / div
            dgM = 2.0 * (1.0 - xKH);
            gjM = sgnKH * 2.0 / Constants.M_PI;
            fJ = Math.sqrt(Constants.M_PI / divH);
            gJ = 1.0 + gjM * arctan(Math.sqrt(xKH), Math.sqrt(1.0 - xKH));
            sum = fJ * gJ;
            if ( n >= 4 ) {
                fJ *= 0.5 / divH;
                double dgj = gjM * Math.sqrt(xKH * (1.0 - xKH));
                gJ += dgj;
                sum += fJ * gJ;
                for ( int j = 3; j <= n / 2; ++j ) {
                    fJ *= (j - 1.5) / (j - 1) / divH;
                    dgj *= ((double) (j - 2)) / (2 * j - 3) * dgM;
                    gJ += dgj;
                    sum += fJ * gJ;
                }
            }
            res += h / div * sum;
            return res;

        } else {
            // n odd, equation (11)

            // first line of (11)
            final double hk = h * k;
            final double hkcn = hk + rho * n;
            final double sqrtExpr = Math.sqrt(h * h - 2.0 * rho * hk + k * k + n * unCor);
            double res = arctan(Math.sqrt(n) * (-(h + k) * hkcn - (hk - n) * sqrtExpr),
                    (hk - n) * hkcn - n * (h + k) * sqrtExpr) / Constants.M_TWOPI;

            if ( n > 1 ) {
                // second line of (11): contribution involving k / div
                double mult = (1.0 - xHK) / 2.0;
                double fJ = 2.0 / Math.sqrt(Constants.M_PI) / divK;
                double dgj = sgnHK * Math.sqrt(xHK);
                double gJ = 1.0 + dgj;
                double sum = fJ * gJ;
                for ( int j = 2; j <= (n - 1) / 2; ++j ) {
                    fJ *= ((double) (j - 1)) / (j - 0.5) / divK;
                    dgj *= ((double) (2 * j - 3)) / (j - 1) * mult;
                    gJ += dgj;
                    sum += fJ * gJ;
                }
                res += k / div * sum;

                // third line of (11): symmetric contribution involving h / div
                mult = (1.0 - xKH) / 2.0;
                fJ = 2.0 / Math.sqrt(Constants.M_PI) / divH;
                dgj = sgnKH * Math.sqrt(xKH);
                gJ = 1.0 + dgj;
                sum = fJ * gJ;
                for ( int j = 2; j <= (n - 1) / 2; ++j ) {
                    fJ *= ((double) (j - 1)) / (j - 0.5) / divH;
                    dgj *= ((double) (2 * j - 3)) / (j - 1) * mult;
                    gJ += dgj;
                    sum += fJ * gJ;
                }
                res += h / div * sum;
            }
            return res;
        }
    }

    @Override
    public double op(final double x, final double y) {
        return pN(x, y, n_, rho_);
    }
}
