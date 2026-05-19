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
 * Forde et al. small-time expansion of the Heston implied volatility.
 *
 * <p>Phase 5h.5 port of {@code QuantLib::FordeHestonExpansion}
 * (v1.42.1 ql/pricingengines/vanilla/hestonexpansionengine.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References:
 * <ul>
 *   <li>M. Forde, A. Jacquier, R. Lee — <i>The small-time smile and term
 *       structure of implied volatility under the Heston model.</i> SIAM
 *       Journal on Financial Mathematics, 2012.</li>
 * </ul>
 *
 * <p>The expansion expresses implied <i>variance</i> as a degree-4 polynomial
 * in {@code x = log(K/F)}, then takes its square root. Coefficients are
 * computed once per term in the constructor and then reused for any number of
 * strike queries via {@link #impliedVolatility(double, double)}.
 */
public final class FordeHestonExpansion implements HestonExpansion {

    private final double[] coeffs = new double[5];

    /**
     * @param kappa Heston mean-reversion speed of variance
     * @param theta long-term variance level
     * @param sigma vol-of-vol
     * @param v0    initial variance
     * @param rho   spot/variance correlation (in [-1, 1])
     * @param term  time horizon (year fraction)
     */
    public FordeHestonExpansion(final double kappa, final double theta, final double sigma, final double v0,
            final double rho, final double term) {
        final double v0Sqrt = Math.sqrt(v0);
        final double rhoBarSquare = 1.0 - rho * rho;

        final double sigma00 = v0Sqrt;
        final double sigma01 = v0Sqrt * (rho * sigma / (4.0 * v0));                                   // term in x
        final double sigma02 = v0Sqrt * ((1.0 - 5.0 * rho * rho / 2.0) / 24.0 * sigma * sigma / (v0
                * v0));                              // term in x*x

        final double a00 =
                -sigma * sigma / 12.0 * (1.0 - rho * rho / 4.0) + v0 * rho * sigma / 4.0 + kappa / 2.0 * (theta - v0);

        final double a01 = rho * sigma / (24.0 * v0) * (sigma * sigma * rhoBarSquare - 2.0 * kappa * (theta + v0)
                + v0 * rho * sigma);                                                              // term in x

        final double a02 = (176.0 * sigma * sigma - 480.0 * kappa * theta - 712.0 * rho * rho * sigma * sigma
                + 521.0 * rho * rho * rho * rho * sigma * sigma + 40.0 * sigma * rho * rho * rho * v0
                + 1040.0 * kappa * theta * rho * rho - 80.0 * v0 * kappa * rho * rho) * sigma * sigma / (v0 * v0
                * 7680.0);

        coeffs[0] = sigma00 * sigma00 + a00 * term;
        coeffs[1] = sigma00 * sigma01 * 2.0 + a01 * term;
        coeffs[2] = sigma00 * sigma02 * 2.0 + sigma01 * sigma01 + a02 * term;
        coeffs[3] = sigma01 * sigma02 * 2.0;
        coeffs[4] = sigma02 * sigma02;
    }

    @Override
    public double impliedVolatility(final double strike, final double forward) {
        final double x = Math.log(strike / forward);
        double var = coeffs[0] + x * (coeffs[1] + x * (coeffs[2] + x * (coeffs[3] + x * coeffs[4])));
        var = Math.max(1e-8, var);
        return Math.sqrt(var);
    }
}
