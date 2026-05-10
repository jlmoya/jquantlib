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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2002, 2003 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003 StatPro Italia srl
 Copyright (C) 2005 Gary Kennedy

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.
 */

package org.jquantlib.math.distributions;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;

/**
 * Cumulative bivariate normal distribution function (Drezner 1978 algorithm,
 * six decimal places accuracy).
 * <p>
 * For this implementation, see "Option pricing formulas", E.G. Haug, McGraw-Hill 1998.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code BivariateCumulativeNormalDistributionDr78}
 * in {@code ql/math/distributions/bivariatenormaldistribution.{hpp,cpp}}.
 */
public class BivariateCumulativeNormalDistributionDr78 implements Ops.BinaryDoubleOp {

    private static final double[] X_ = {
        0.24840615,
        0.39233107,
        0.21141819,
        0.03324666,
        0.00082485334
    };

    private static final double[] Y_ = {
        0.10024215,
        0.48281397,
        1.06094980,
        1.77972940,
        2.66976040000
    };

    private final double rho;
    private final double rho2;

    /**
     * Constructor — correlation must be in [-1.0, 1.0].
     *
     * @param rho correlation
     */
    public BivariateCumulativeNormalDistributionDr78(final double rho) {
        QL.require(rho >= -1.0, "rho must be >= -1.0");
        QL.require(rho <= 1.0, "rho must be <= 1.0");
        this.rho = rho;
        this.rho2 = rho * rho;
    }

    /**
     * Evaluates the bivariate cumulative normal CDF at {@code (a, b)}.
     */
    @Override
    public double op(final double a, final double b) {
        final CumulativeNormalDistribution cumNormalDist = new CumulativeNormalDistribution();
        final double cumNormDistA = cumNormalDist.op(a);
        final double cumNormDistB = cumNormalDist.op(b);
        final double maxCumNormDistAB = Math.max(cumNormDistA, cumNormDistB);
        final double minCumNormDistAB = Math.min(cumNormDistA, cumNormDistB);

        if (1.0 - maxCumNormDistAB < 1e-15) {
            return minCumNormDistAB;
        }

        if (minCumNormDistAB < 1e-15) {
            return minCumNormDistAB;
        }

        final double a1 = a / Math.sqrt(2.0 * (1.0 - rho2));
        final double b1 = b / Math.sqrt(2.0 * (1.0 - rho2));

        double result = -1.0;

        if (a <= 0.0 && b <= 0.0 && rho <= 0.0) {
            double sum = 0.0;
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    sum += X_[i] * X_[j]
                            * Math.exp(a1 * (2.0 * Y_[i] - a1)
                                    + b1 * (2.0 * Y_[j] - b1)
                                    + 2.0 * rho * (Y_[i] - a1) * (Y_[j] - b1));
                }
            }
            result = Math.sqrt(1.0 - rho2) / Math.PI * sum;
        } else if (a <= 0.0 && b >= 0.0 && rho >= 0.0) {
            final BivariateCumulativeNormalDistributionDr78 bivCumNormalDist =
                    new BivariateCumulativeNormalDistributionDr78(-rho);
            result = cumNormDistA - bivCumNormalDist.op(a, -b);
        } else if (a >= 0.0 && b <= 0.0 && rho >= 0.0) {
            final BivariateCumulativeNormalDistributionDr78 bivCumNormalDist =
                    new BivariateCumulativeNormalDistributionDr78(-rho);
            result = cumNormDistB - bivCumNormalDist.op(-a, b);
        } else if (a >= 0.0 && b >= 0.0 && rho <= 0.0) {
            result = cumNormDistA + cumNormDistB - 1.0 + this.op(-a, -b);
        } else if (a * b * rho > 0.0) {
            final double rho1 = (rho * a - b) * (a > 0.0 ? 1.0 : -1.0)
                    / Math.sqrt(a * a - 2.0 * rho * a * b + b * b);
            final BivariateCumulativeNormalDistributionDr78 bivCumNormalDist =
                    new BivariateCumulativeNormalDistributionDr78(rho1);

            final double rho2 = (rho * b - a) * (b > 0.0 ? 1.0 : -1.0)
                    / Math.sqrt(a * a - 2.0 * rho * a * b + b * b);
            final BivariateCumulativeNormalDistributionDr78 cbnd2 =
                    new BivariateCumulativeNormalDistributionDr78(rho2);

            final double delta = (1.0 - (a > 0.0 ? 1.0 : -1.0) * (b > 0.0 ? 1.0 : -1.0)) / 4.0;

            result = bivCumNormalDist.op(a, 0.0) + cbnd2.op(b, 0.0) - delta;
        } else {
            QL.error("case not handled");
        }

        return result;
    }
}
