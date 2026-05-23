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
 Copyright (C) 2008 Marek Glowacki
*/

package org.jquantlib.math.copulas;

import org.jquantlib.math.distributions.BivariateCumulativeNormalDistributionDr78;
import org.jquantlib.math.distributions.InverseCumulativeNormal;

/**
 * Gaussian copula.
 *
 * <p>Formula: {@code C(u, v) = Phi_rho(Phi^-1(u), Phi^-1(v))} where
 * {@code Phi_rho} is the bivariate-normal CDF with correlation {@code rho}
 * and {@code Phi^-1} is the inverse univariate-normal CDF.
 *
 * <p>Java port of v1.42.1 {@code ql/math/copulas/gaussiancopula.{hpp,cpp}}.
 * C++ uses {@code BivariateCumulativeNormalDistributionWe04DP}; Java currently
 * only has the Drezner-1978 implementation ({@code Dr78}, ~6-decimal accuracy),
 * so this port uses Dr78. Tests cross-validate against a C++ probe that also
 * uses Dr78 (not We04DP).
 */
public final class GaussianCopula implements Copula {

    private final double rho;
    private final BivariateCumulativeNormalDistributionDr78 bivariateNormalCdf;
    private final InverseCumulativeNormal invCumNormal;

    public GaussianCopula(final double rho) {
        if (rho < -1.0 || rho > 1.0) {
            throw new IllegalArgumentException(
                    "rho (" + rho + ") must be in [-1,1]");
        }
        this.rho = rho;
        this.bivariateNormalCdf = new BivariateCumulativeNormalDistributionDr78(rho);
        this.invCumNormal = new InverseCumulativeNormal();
    }

    @Override
    public double apply(final double u, final double v) {
        if (u < 0.0 || u > 1.0) {
            throw new IllegalArgumentException(
                    "1st argument (" + u + ") must be in [0,1]");
        }
        if (v < 0.0 || v > 1.0) {
            throw new IllegalArgumentException(
                    "2nd argument (" + v + ") must be in [0,1]");
        }
        return bivariateNormalCdf.op(invCumNormal.op(u), invCumNormal.op(v));
    }
}
