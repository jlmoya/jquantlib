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
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Pricing engine for European spread options on two assets using the
 * analytic operator-splitting approximation of Chi-Fai Lo (2015) and the
 * higher-order Strang-splitting refinement.
 *
 * <p>Reference: Chi-Fai Lo, "Pricing Spread Options by the Operator
 * Splitting Method",
 * <a href="https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2429696">
 * SSRN abstract 2429696</a>.</p>
 *
 * <p>Two approximation orders are provided:
 * <ul>
 *   <li>{@link Order#First} — Kirk's value plus the first-order
 *       Lo-Hayashi-Park splitting correction;</li>
 *   <li>{@link Order#Second} — adds the second-order Strang-splitting
 *       correction (derived from the closed-form Mathematica expansion
 *       described in the C++ source).</li>
 * </ul>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/pricingengines/basket/operatorsplittingspreadengine.{hpp,cpp}}.</p>
 *
 * @author Jose Moya
 */
public class OperatorSplittingSpreadEngine extends SpreadBlackScholesVanillaEngine {

    /** Approximation order. */
    public enum Order {
        /** First-order operator-splitting correction over Kirk. */
        First,
        /** Second-order (Strang-splitting) correction over Kirk. */
        Second
    }

    private static final CumulativeNormalDistribution CDF =
            new CumulativeNormalDistribution();
    private static final NormalDistribution PDF =
            new NormalDistribution();

    private static final double M_SQRT2 = Math.sqrt(2.0);
    private static final double M_SQRTPI = Math.sqrt(Math.PI);

    private final Order order;

    public OperatorSplittingSpreadEngine(
            final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2,
            final double correlation) {
        this(process1, process2, correlation, Order.Second);
    }

    public OperatorSplittingSpreadEngine(
            final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2,
            final double correlation,
            final Order order) {
        super(process1, process2, correlation);
        this.order = order;
    }

    private static double sq(final double x) {
        return x * x;
    }

    @Override
    protected double calculateSpread(
            final double f1, final double f2, final double k,
            final Option.Type optionType,
            final double variance1, final double variance2,
            final double df) {

        final double vol1 = Math.sqrt(variance1);
        final double vol2 = Math.sqrt(variance2);
        final double sig2 = vol2 * f2 / (f2 + k);
        final double sig_m = Math.sqrt(variance1 + sig2 * (sig2 - 2.0 * rho * vol1));

        final double d1 = (Math.log(f1) - Math.log(f2 + k)) / sig_m + 0.5 * sig_m;
        final double d2 = d1 - sig_m;

        final double kirkCallNPV = df * (f1 * CDF.op(d1) - (f2 + k) * CDF.op(d2));

        final double vs = vol2 / (sig_m * sig_m);
        final double rs = sq(rho * vol1 - sig2);

        final double oPlt = -sig2 * sig2 * k * df * PDF.op(d2) * vs
                * (-d2 * rs / sig2 - 0.5 * vs * sig_m * k / (f2 + k)
                    * (rs * d1 * d2 + (1.0 - rho * rho) * variance1));

        if (order == Order.First) {
            return callPutParityPrice(kirkCallNPV + 0.5 * oPlt,
                    f1, f2, df, k, optionType);
        }

        QL.require(order == Order.Second, "unknown approximation type");

        // Second-order (Strang-splitting) correction.  Two analytic
        // branches: a Taylor expansion near rs ~ 0, and the general
        // closed-form derived from Mathematica's symbolic expansion.
        final double R2 = f2 + k;
        final double R1 = f1 / R2;
        final double vol12 = vol1 * vol1;
        final double vol22 = vol2 * vol2;
        final double vol23 = vol22 * vol2;

        final double ooPlt;
        if (rs < Math.pow(Constants.QL_EPSILON, 0.625)) {
            final double vol24 = vol22 * vol22;
            final double vol26 = vol22 * vol24;
            final double k2 = k * k;
            final double R22 = R2 * R2;
            final double R24 = R22 * R22;
            final double kmR22 = sq(k - R2);
            final double lnR1 = Math.log(R1);

            ooPlt = -0.0625 * (k2 * kmR22 * vol26
                * (-8.0 * R22 * R24 * (7.0 * k2 - 7.0 * k * R2 + R22) * vol12 * vol12
                    + kmR22 * R24 * vol12 * (-112.0 * k * R2 + 16.0 * R22
                        + k2 * (124.0 + 3.0 * vol12)) * vol22
                    - 2.0 * kmR22 * kmR22 * R22
                        * (-28.0 * k * R2 + 4.0 * R22 + k2 * (34.0 + 3.0 * vol12)) * vol24
                    + 3.0 * k2 * kmR22 * kmR22 * kmR22 * vol26
                    - 4.0 * k * (k - R2) * R24 * lnR1
                        * (-4.0 * R22 * vol12 + 4.0 * kmR22 * vol22
                            + 3.0 * k * (k - R2) * vol22 * lnR1)))
                / (Math.exp(sq(-(R22 * vol12) + kmR22 * vol22 + 2.0 * R22 * lnR1)
                        / (8.0 * R24 * vol12 - 8.0 * kmR22 * R22 * vol22))
                    * M_SQRTPI * M_SQRT2 * R22 * R24 * R2
                    * sq(R22 * vol12 - kmR22 * vol22)
                    * Math.sqrt(vol12 - (kmR22 * vol22) / R22));
        } else {
            final double F2 = f2;
            final double F22 = F2 * F2;
            final double F23 = F22 * F2;
            final double F24 = F22 * F22;

            final double iR2 = 1.0 / R2;
            final double iR22 = iR2 * iR2;
            final double iR23 = iR22 * iR2;
            final double iR24 = iR22 * iR22;
            final double a = vol12 - 2.0 * F2 * iR2 * rho * vol1 * vol2 + F22 * iR22 * vol22;
            final double a2 = a * a;
            final double b = a / 2.0 + Math.log(R1);
            final double b2 = b * b;
            final double c = Math.sqrt(a);
            final double d = b / c;
            final double e = rho * vol1 - F2 * iR2 * vol2;
            final double e2 = e * e;
            final double f = d - c;
            final double g = -2.0 * iR2 * rho * vol1 * vol2 + 2.0 * F2 * iR22 * rho * vol1 * vol2
                    + 2.0 * F2 * iR22 * vol22 - 2.0 * F22 * iR23 * vol22;
            final double h = rho * rho;
            final double j = 1.0 - h;
            final double iat = 1.0 / c;
            final double l = b * iat - c;
            final double m = f * (1.0 - (R2 * rho * vol1) / (F2 * vol2))
                    - (e * iR2 * k * (d * l + (j * vol12) / (e * e)) * vol2) / (2.0 * c);
            final double n = (iat * (1.0 - (R2 * rho * vol1) / (F2 * vol2))) / R1
                    - (e * iR2 * k * ((f * iat) / R1 + b / (a * R1)) * vol2) / (2.0 * c);
            final double o = df * Math.exp(-0.5 * f * f);
            final double p = d * l + (j * vol12) / (e * e);
            final double q = (-2.0 * j * vol12 * (-(iR2 * vol2) + F2 * iR22 * vol2)) / (e * e * e);
            final double s = q - (b2 * g) / (2.0 * a2) - (b * f * g) / (2.0 * a * c)
                    + (f * g) / (2.0 * c);
            final double u = f * (-((rho * vol1) / (F2 * vol2)) + (R2 * rho * vol1) / (F22 * vol2));
            final double v = -0.5 * (b * g * (1.0 - (R2 * rho * vol1) / (F2 * vol2))) / (a * c);
            final double w = (3.0 * g * g) / (4.0 * a2 * c)
                    - (4.0 * iR22 * rho * vol1 * vol2 - 4.0 * F2 * iR23 * rho * vol1 * vol2
                        + 2.0 * iR22 * vol22 - 8.0 * F2 * iR23 * vol22
                        + 6.0 * F22 * iR24 * vol22) / (2.0 * a * c);
            final double x = u + v + (e * g * iR2 * k * p * vol2) / (4.0 * a * c)
                    + (e * iR22 * k * p * vol2) / (2.0 * c)
                    - (e * iR2 * k * s * vol2) / (2.0 * c)
                    - (iR2 * k * p * vol2 * (-(iR2 * vol2) + F2 * iR22 * vol2)) / (2.0 * c);
            final double y = (4.0 * iR22 - 4.0 * F2 * iR23) * rho * vol1 * vol2
                    + (2.0 * iR22 - 8.0 * F2 * iR23 + 6.0 * F22 * iR24) * vol22;
            final double z = 4.0 * iR22 * rho * vol1 * vol2 - 4.0 * F2 * iR23 * rho * vol1 * vol2
                    + 2.0 * iR22 * vol22 - 8.0 * F2 * iR23 * vol22 + 6.0 * F22 * iR24 * vol22;

            ooPlt = (k * o * vol23 * (
                -2.0 * c * b2 * e2 * e * (-1.0 + f * f) * F23 * F24 * g * g * iR22 * m * vol23
                + 2.0 * b2 * e2 * e2 * F23 * F24 * g * g * iR2 * iR22 * k * vol22 * vol22
                + 2.0 * a * b * e2 * e * F23 * F22 * g * iR22 * vol2
                    * (-8.0 * e2 * F2 * iR2 * k * vol22 + 7.0 * f * F22 * g * m * vol22)
                - a * c * e2 * e * F23 * F22 * g * iR22 * vol2
                    * (4.0 * e * F2 * vol2 * (-2.0 * b * (-1.0 + f * f) * m + e * f * iR2 * k * vol2)
                        + F22 * g * (16.0 * m + e * (2.0 * f + 3.0 * b * iat) * iR2 * k * vol2) * vol22)
                - 4.0 * a2 * a * c * e2 * (
                    e2 * F22 * vol2 * (4.0 * F22 * iat * iR22 * R2 * rho * vol1
                        + 8.0 * F23 * iR22 * n * R1 * vol2
                        - 4.0 * F24 * 3.0 * iR23 * n * R1 * vol2
                        - F23 * iR22 * (4.0 * iat * rho * vol1 + F22 * iR2 * k * p * vol23 * w))
                    + 4.0 * F23 * F22 * vol22 * vol22
                        * (iR22 * (-2.0 * F2 * iR2 + 3.0 * F22 * iR22) * m
                            + F22 * (2.0 * iR2 - 3.0 * F2 * iR22) * iR23 * m
                            + F22 * iR22 * (-iR2 + F2 * iR22) * x)
                    + 2.0 * e * F22 * (
                        2.0 * F24 * F2 * iR24 * n * R1 * vol23
                        + 2.0 * f * F2 * F22 * iR22 * rho * vol1 * vol22
                        - 2.0 * f * F22 * iR22 * R2 * rho * vol1 * vol22
                        - b * F24 * iR22 * R2 * rho * vol1 * vol22 * w
                        - 2.0 * F24 * vol2 * (iR23 * n * R1 * vol22 + 4.0 * iR23 * m * vol22
                            - 2.0 * iR22 * vol22 * x)
                        + F23 * (2.0 * iR22 * m * vol23 + 6.0 * F22 * iR24 * m * vol23
                            + b * F22 * iR22 * vol23 * w - 4.0 * F22 * iR23 * vol23 * x)))
                + 2.0 * a2 * c * e2 * F23 * F22 * vol2 * (
                    8.0 * F22 * g * iR22 * (-iR2 + F2 * iR22) * m * vol23
                    + e2 * iR22 * vol2 * (8.0 * F2 * g * n * R1
                        + b * F22 * iat * iR2 * k * vol22 * (y - z))
                    + 4.0 * e * vol22 * (4.0 * F2 * g * iR22 * m
                        + F22 * (-4.0 * g * iR23 * m + 2.0 * g * iR22 * x + iR22 * m * z)))
                + 2.0 * a2 * a * F22 * (
                    -4.0 * e2 * e2 * e * f * F24 * iat * iR24 * k * vol23
                    + 8.0 * e * F2 * F24 * iR23 * (-iR22 + F2 * iR23) * j * k * vol12 * vol23 * vol22
                    + 12.0 * F2 * F24 * iR23 * sq(iR2 - F2 * iR22) * j * k * vol12 * vol23 * vol23
                    + e2 * e2 * F2 * vol22 * (
                        2.0 * F24 * iR22 * k * vol22
                            * (2.0 * (iR23 * p - iR22 * s) + b2 * iat * iR2 * w)
                        + f * (4.0 * F22 * iR22 * (4.0 * m + F22 * iat * iR2 * iR22 * k * vol22)
                            - 4.0 * F23 * (6.0 * iR23 * m + iat * iR24 * k * vol22 - 2.0 * iR22 * x)
                            + F24 * iR23 * k * vol22 * (2.0 * b * w + iat * y)))
                    - 2.0 * e2 * e * F22 * iR22 * (
                        4.0 * f * F22 * (iR2 - F2 * iR22) * m * vol23
                        + F22 * vol22 * (
                            F2 * vol2 * (2.0 * k * (F2 * iR24 * p + F2 * iR24 * p + iR22 * s
                                - iR23 * (2.0 * p + F2 * s)) * vol22 + y - z)
                            + R2 * rho * vol1 * (-y + z))))
                - 2.0 * a2 * e2 * F23 * (
                    2.0 * e2 * e * F23 * iR22 * k * (2.0 * b * iR22 + g * (-1.0 + f * iat) * iR2) * vol23
                    + 4.0 * b * f * F22 * F22 * g * iR22 * (-iR2 + F2 * iR22) * m * vol22 * vol22
                    + 2.0 * e2 * F22 * iR22 * vol2 * (
                        2.0 * b * F2 * iR2 * (iR2 - F2 * iR22) * k * vol23
                        + g * (2.0 * R2 * rho * vol1 + 2.0 * F2 * (-1.0 + 3.0 * f * m
                            + b * f * n * R1) * vol2
                            + F22 * k * (-(iR22 * p) + iR2 * s) * vol23))
                    + e * vol22 * (
                        F2 * F22 * g * iR22 * (g * R2 * rho * vol1
                            + F2 * g * (-1.0 + f * m) * vol2
                            + 2.0 * F2 * iR2 * (-iR2 + F2 * iR22) * k * p * vol23)
                        + 2.0 * b * (2.0 * F2 * F22 * g * iR22 * rho * vol1
                            - 2.0 * F22 * g * iR22 * R2 * rho * vol1
                            + 4.0 * f * F23 * g * iR22 * m * vol2
                            + f * F24 * vol2 * (-4.0 * g * iR23 * m + 2.0 * g * iR22 * x
                                + iR22 * m * z))))))
                / (16.0 * a2 * a2 * c * e2 * F23 * M_SQRT2 * M_SQRTPI * vol2);

            // suppress "unused" warning on `h`; computed for parity with C++
            // source (also documents the symbolic substitution rho^2 = h).
            assert h == rho * rho;
        }

        return callPutParityPrice(kirkCallNPV + 0.5 * oPlt + 0.125 * ooPlt,
                f1, f2, df, k, optionType);
    }

    private static double callPutParityPrice(
            final double callPrice,
            final double f1, final double f2, final double df,
            final double k, final Option.Type optionType) {
        if (optionType == Option.Type.Call) {
            return callPrice;
        }
        return callPrice - df * (f1 - f2 - k);
    }
}
