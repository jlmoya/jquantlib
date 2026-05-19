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
/*
 Copyright (C) 2014 Fabien Le Floc'h

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/
package org.jquantlib.pricingengines.vanilla;

/**
 * Lorig-Pagliarani-Pascucci order-2 expansion of the Heston implied volatility.
 *
 * <p>Phase 5h.5 port of {@code QuantLib::LPP2HestonExpansion}
 * (v1.42.1 ql/pricingengines/vanilla/hestonexpansionengine.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References:
 * <ul>
 *   <li>M. Lorig, S. Pagliarani, A. Pascucci — <i>Explicit implied vols for
 *       multifactor local-stochastic vol models.</i> arXiv 1306.5447v3, 2014.</li>
 * </ul>
 *
 * <p>The expansion expresses implied volatility as a degree-2 polynomial in
 * {@code x = log(K/F)}. The three coefficients are evaluated once per term
 * via the Mathematica-emitted closed-form helpers {@link #z0}, {@link #z1},
 * {@link #z2}. The Mathematica notebook is available from the original
 * authors' website (http://explicitsolutions.wordpress.com/) and these
 * formulas are transcribed verbatim from the C++ source.
 */
public final class LPP2HestonExpansion implements HestonExpansion {

    private final double[] coeffs = new double[3];

    /** {@code exp(kappa*t)} cached for the polynomial helpers. */
    private final double ekt;
    /** {@code exp(2*kappa*t)}. */
    private final double e2kt;
    /** {@code exp(3*kappa*t)}. */
    private final double e3kt;
    @SuppressWarnings( "unused" )
    private final double e4kt;

    /**
     * @param kappa Heston mean-reversion speed
     * @param theta long-term variance
     * @param sigma vol-of-vol
     * @param v0    initial variance
     * @param rho   spot/variance correlation
     * @param term  time horizon (year fraction)
     */
    public LPP2HestonExpansion(final double kappa, final double theta, final double sigma, final double v0,
            final double rho, final double term) {
        this.ekt = Math.exp(kappa * term);
        this.e2kt = ekt * ekt;
        this.e3kt = e2kt * ekt;
        this.e4kt = e2kt * e2kt;
        coeffs[0] = z0(term, kappa, theta, sigma, v0, rho);
        coeffs[1] = z1(term, kappa, theta, sigma, v0, rho);
        coeffs[2] = z2(term, kappa, theta, sigma, v0, rho);
    }

    @Override
    public double impliedVolatility(final double strike, final double forward) {
        final double x = Math.log(strike / forward);
        final double vol = coeffs[0] + x * (coeffs[1] + x * coeffs[2]);
        return Math.max(1e-8, vol);
    }

    /** Mathematica-emitted constant-term coefficient. Verbatim from C++. */
    private double z0(final double t, final double kappa, final double theta, final double delta, final double y,
            final double rho) {
        final double delta2 = delta * delta;
        final double rho2 = rho * rho;
        final double kappa2 = kappa * kappa;
        final double kappa3 = kappa2 * kappa;
        final double kappa5 = kappa2 * kappa3;

        final double commonA = (1.0 + ekt * (-1.0 + kappa * t)) * theta + (-1.0 + ekt) * y;
        final double commonB = (2.0 + kappa * t + ekt * (-2.0 + kappa * t)) * theta + (-1.0 + ekt - kappa * t) * y;
        final double commonC = -theta + kappa * t * theta + (theta - y) / ekt + y;
        final double bracket =
                theta - 2.0 * y + e2kt * (-5.0 * theta + 2.0 * kappa * t * theta + 2.0 * y + 8.0 * rho2 * (
                        (-3.0 + kappa * t) * theta + y)) + 4.0 * ekt * (theta + kappa * t * theta - kappa * t * y
                        + rho2 * ((6.0 + kappa * t * (4.0 + kappa * t)) * theta
                        - (2.0 + kappa * t * (2.0 + kappa * t)) * y));

        final double num = 4.0 * delta2 * kappa * (-theta - 4.0 * ekt * (theta + kappa * t * (theta - y)) + e2kt * (
                (5.0 - 2.0 * kappa * t) * theta - 2.0 * y) + 2.0 * y) * commonA
                + 128.0 * ekt * kappa3 * commonA * commonA + 32.0 * delta * ekt * kappa2 * rho * commonA * commonB
                + delta2 * ekt * rho2 * commonC * commonB * commonB
                + (48.0 * delta2 * e2kt * kappa2 * rho2 * commonB * commonB) / commonA
                - delta2 * rho2 * commonA * commonB * commonB + 2.0 * delta2 * kappa * commonA * bracket
                - (8.0 * delta2 * kappa2 * commonA * bracket) / commonC;

        final double den = 128.0 * e3kt * kappa5 * t * t * Math.pow(commonC / (kappa * t), 1.5);
        return num / den;
    }

    /** Mathematica-emitted x-coefficient. Verbatim from C++. */
    private double z1(final double t, final double kappa, final double theta, final double delta, final double y,
            final double rho) {
        final double kappa2 = kappa * kappa;
        final double kappa3 = kappa2 * kappa;
        final double commonA = (1.0 + ekt * (-1.0 + kappa * t)) * theta + (-1.0 + ekt) * y;
        final double commonC = -theta + kappa * t * theta + (theta - y) / ekt + y;
        final double oneMinusEkt = -1.0 + ekt;

        final double num = delta * rho * (
                -(delta * (oneMinusEkt * oneMinusEkt) * rho * (4.0 * theta - y) * y) + 2.0 * ekt * kappa3 * (t * t)
                        * theta * ((2.0 + 2.0 * ekt + delta * rho * t) * theta - (2.0 + delta * rho * t) * y)
                        - 2.0 * oneMinusEkt * kappa * (2.0 * theta - y) * (
                        oneMinusEkt * (-2.0 + delta * rho * t) * theta + (-2.0 + 2.0 * ekt + delta * rho * t) * y)
                        + kappa2 * t * (
                        oneMinusEkt * (-4.0 + delta * rho * t + ekt * (-12.0 + delta * rho * t)) * (theta * theta)
                                + 2.0 * (-4.0 + 4.0 * e2kt + delta * rho * t + 3.0 * delta * ekt * rho * t) * theta * y
                                - (-4.0 + delta * rho * t + 2.0 * ekt * (2.0 + delta * rho * t)) * (y * y)));
        final double den = 8.0 * kappa2 * t * Math.sqrt(commonC / (kappa * t)) * (commonA * commonA);
        return num / den;
    }

    /** Mathematica-emitted x^2-coefficient. Verbatim from C++. */
    private double z2(final double t, final double kappa, final double theta, final double delta, final double y,
            final double rho) {
        final double delta2 = delta * delta;
        final double rho2 = rho * rho;
        final double commonB = (2.0 + kappa * t + ekt * (-2.0 + kappa * t)) * theta + (-1.0 + ekt - kappa * t) * y;
        final double commonC = -theta + kappa * t * theta + (theta - y) / ekt + y;
        final double bracket =
                theta - 2.0 * y + e2kt * (-5.0 * theta + 2.0 * kappa * t * theta + 2.0 * y + 8.0 * rho2 * (
                        (-3.0 + kappa * t) * theta + y)) + 4.0 * ekt * (theta + kappa * t * theta - kappa * t * y
                        + rho2 * ((6.0 + kappa * t * (4.0 + kappa * t)) * theta
                        - (2.0 + kappa * t * (2.0 + kappa * t)) * y));

        final double num =
                delta2 * Math.sqrt(commonC / (kappa * t)) * (-12.0 * rho2 * commonB * commonB + commonC * bracket);
        final double commonC4 = commonC * commonC * commonC * commonC;
        final double den = 16.0 * e2kt * commonC4;
        return num / den;
    }
}
