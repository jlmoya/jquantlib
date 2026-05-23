/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
/*
 Copyright (C) 2014 Fabien Le Floc'h
*/
package org.jquantlib.pricingengines.vanilla;

/**
 * Lorig-Pagliarani-Pascucci order-3 expansion of the Heston implied volatility.
 *
 * <p>Phase 2 L3-D Java port of {@code QuantLib::LPP3HestonExpansion}
 * (v1.42.1 ql/pricingengines/vanilla/hestonexpansionengine.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The expansion expresses implied volatility as a degree-3 polynomial
 * in {@code x = log(K/F)}. The four coefficients are evaluated once per term via the Mathematica-emitted closed-form
 * helpers {@link #z0}, {@link #z1}, {@link #z2}, {@link #z3} — verbatim transliterations of the C++ closed forms
 * (cpp pow → Math.pow, sqrt → Math.sqrt).
 *
 * <p>References:
 * <ul>
 *   <li>M. Lorig, S. Pagliarani, A. Pascucci — <i>Explicit implied vols for
 *       multifactor local-stochastic vol models.</i> arXiv 1306.5447v3, 2014.</li>
 * </ul>
 *
 * @see LPP2HestonExpansion
 */
public final class LPP3HestonExpansion implements HestonExpansion {

    private final double[] coeffs = new double[4];

    /** {@code exp(kappa*t)} cached for the polynomial helpers. */
    private final double ekt;
    /** {@code exp(2*kappa*t)}. */
    private final double e2kt;
    /** {@code exp(3*kappa*t)}. */
    private final double e3kt;
    /** {@code exp(4*kappa*t)}. */
    private final double e4kt;

    /**
     * @param kappa Heston mean-reversion speed
     * @param theta long-term variance
     * @param sigma vol-of-vol
     * @param v0    initial variance
     * @param rho   spot/variance correlation
     * @param term  time horizon (year fraction)
     */
    public LPP3HestonExpansion(final double kappa, final double theta, final double sigma, final double v0,
            final double rho, final double term) {
        this.ekt = Math.exp(kappa * term);
        this.e2kt = ekt * ekt;
        this.e3kt = e2kt * ekt;
        this.e4kt = e2kt * e2kt;
        coeffs[0] = z0(term, kappa, theta, sigma, v0, rho);
        coeffs[1] = z1(term, kappa, theta, sigma, v0, rho);
        coeffs[2] = z2(term, kappa, theta, sigma, v0, rho);
        coeffs[3] = z3(term, kappa, theta, sigma, v0, rho);
    }

    @Override
    public double impliedVolatility(final double strike, final double forward) {
        final double x = Math.log(strike / forward);
        final double vol = coeffs[0] + x * (coeffs[1] + x * (coeffs[2] + x * coeffs[3]));
        return Math.max(1e-8, vol);
    }

    /** Mathematica-emitted constant-term coefficient. Verbatim transliteration from C++. */
    private double z0(final double t, final double kappa, final double theta,
            final double delta, final double y, final double rho) {
        return (96*Math.pow(delta,2)*ekt*Math.pow(kappa,3)*
            (-theta - 4*ekt*(theta + kappa*t*(theta - y)) +
                e2kt*((5 - 2*kappa*t)*theta - 2*y) + 2*y)*
                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                3072*e2kt*Math.pow(kappa,5)*
                Math.pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,2) +
                96*Math.pow(delta,3)*ekt*Math.pow(kappa,2)*rho*
                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                (-2*theta - kappa*t*theta - 2*ekt*(2 + kappa*t)*
                    (2*theta + kappa*t*(theta - y)) + e2kt*((10 - 3*kappa*t)*theta - 3*y) +
                    3*y + 2*kappa*t*y) + 768*delta*e2kt*Math.pow(kappa,4)*rho*
                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                    ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                        (-1 + ekt - kappa*t)*y) +
                        6*Math.pow(delta,3)*kappa*rho*(-theta - 4*ekt*(theta + kappa*t*(theta - y)) +
                            e2kt*((5 - 2*kappa*t)*theta - 2*y) + 2*y)*
                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                            ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                (-1 + ekt - kappa*t)*y) +
                                24*Math.pow(delta,2)*e2kt*Math.pow(kappa,2)*Math.pow(rho,2)*
                                (-theta + kappa*t*theta + (theta - y)/ekt + y)*
                                Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                    (-1 + ekt - kappa*t)*y,2) +
                                    (1152*Math.pow(delta,2)*e3kt*Math.pow(kappa,4)*Math.pow(rho,2)*
                                        Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                            (-1 + ekt - kappa*t)*y,2))/
                                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) -
                                            24*Math.pow(delta,2)*ekt*Math.pow(kappa,2)*Math.pow(rho,2)*
                                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                            Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                (-1 + ekt - kappa*t)*y,2) +
                                                80*Math.pow(delta,3)*ekt*kappa*Math.pow(rho,3)*
                                                Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                    (-1 + ekt - kappa*t)*y,3) +
                                                    Math.pow(delta,3)*ekt*Math.pow(rho,3)*
                                                    (-theta + kappa*t*theta + (theta - y)/ekt + y)*
                                                    Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                        (-1 + ekt - kappa*t)*y,3) -
                                                        (1440*Math.pow(delta,3)*e3kt*Math.pow(kappa,3)*Math.pow(rho,3)*
                                                            Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                (-1 + ekt - kappa*t)*y,3))/
                                                                Math.pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,2) -
                                                                (528*Math.pow(delta,3)*e2kt*Math.pow(kappa,2)*Math.pow(rho,3)*
                                                                    Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                        (-1 + ekt - kappa*t)*y,3))/
                                                                        ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) -
                                                                        3*Math.pow(delta,3)*Math.pow(rho,3)*((1 + ekt*(-1 + kappa*t))*theta +
                                                                            (-1 + ekt)*y)*Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*
                                                                                theta + (-1 + ekt - kappa*t)*y,3) +
                                                                                384*Math.pow(delta,3)*e2kt*Math.pow(kappa,3)*rho*
                                                                                ((2 + kappa*t + 2*ekt*Math.pow(2 + kappa*t,2) +
                                                                                    e2kt*(-10 + 3*kappa*t))*theta +
                                                                                    (-3 + 3*e2kt - 2*kappa*t - 2*ekt*kappa*t*(2 + kappa*t))*y) -
                                                                                    (576*Math.pow(delta,3)*e2kt*Math.pow(kappa,3)*rho*
                                                                                        ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                            (-1 + ekt - kappa*t)*y)*
                                                                                            ((1 + e2kt*(-5 + 2*kappa*t + 4*Math.pow(rho,2)*(-3 + kappa*t)) +
                                                                                                2*ekt*(2 + 2*kappa*t +
                                                                                                    Math.pow(rho,2)*(6 + 4*kappa*t + Math.pow(kappa,2)*Math.pow(t,2))))*theta +
                                                                                                    2*(-1 + e2kt*(1 + 2*Math.pow(rho,2)) -
                                                                                                        ekt*(2*kappa*t +
                                                                                                            Math.pow(rho,2)*(2 + 2*kappa*t + Math.pow(kappa,2)*Math.pow(t,2))))*y))/
                                                                                                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                                                                                                            Math.pow(delta,3)*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                (-1 + ekt)*y)*((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                    (-1 + ekt - kappa*t)*y)*
                                                                                                                    (theta*(12*ekt*Math.pow(kappa,3)*Math.pow(rho,2)*Math.pow(t,2) +
                                                                                                                        8*Math.pow(-1 + ekt,2)*Math.pow(rho,2)*theta -
                                                                                                                        (-1 + ekt)*kappa*
                                                                                                                        (3 + 8*Math.pow(rho,2)*t*theta + ekt*(15 + 8*Math.pow(rho,2)*(9 + t*theta)))
                                                                                                                        + 2*Math.pow(kappa,2)*t*(Math.pow(rho,2)*t*theta +
                                                                                                                            2*ekt*(3 + Math.pow(rho,2)*(12 + t*theta)) +
                                                                                                                            e2kt*(3 + Math.pow(rho,2)*(12 + t*theta)))) -
                                                                                                                            2*(6*ekt*Math.pow(kappa,3)*Math.pow(rho,2)*Math.pow(t,2) +
                                                                                                                                4*Math.pow(-1 + ekt,2)*Math.pow(rho,2)*theta +
                                                                                                                                2*Math.pow(kappa,2)*t*(Math.pow(rho,2)*t*theta +
                                                                                                                                    ekt*(3 + Math.pow(rho,2)*(6 + t*theta))) -
                                                                                                                                    (-1 + ekt)*kappa*
                                                                                                                                    (3 + 6*Math.pow(rho,2)*t*theta + ekt*(3 + 2*Math.pow(rho,2)*(6 + t*theta))))*
                                                                                                                                    y + 2*Math.pow(rho,2)*Math.pow(1 - ekt + kappa*t,2)*Math.pow(y,2)) -
                                                                                                                                    (40*Math.pow(delta,3)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                        (-1 + ekt)*y)*((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                            (-1 + ekt - kappa*t)*y)*
                                                                                                                                            (theta*(12*ekt*Math.pow(kappa,3)*Math.pow(rho,2)*Math.pow(t,2) +
                                                                                                                                                8*Math.pow(-1 + ekt,2)*Math.pow(rho,2)*theta -
                                                                                                                                                (-1 + ekt)*kappa*
                                                                                                                                                (3 + 8*Math.pow(rho,2)*t*theta +
                                                                                                                                                    ekt*(15 + 8*Math.pow(rho,2)*(9 + t*theta))) +
                                                                                                                                                    2*Math.pow(kappa,2)*t*(Math.pow(rho,2)*t*theta +
                                                                                                                                                        2*ekt*(3 + Math.pow(rho,2)*(12 + t*theta)) +
                                                                                                                                                        e2kt*(3 + Math.pow(rho,2)*(12 + t*theta)))) -
                                                                                                                                                        2*(6*ekt*Math.pow(kappa,3)*Math.pow(rho,2)*Math.pow(t,2) +
                                                                                                                                                            4*Math.pow(-1 + ekt,2)*Math.pow(rho,2)*theta +
                                                                                                                                                            2*Math.pow(kappa,2)*t*(Math.pow(rho,2)*t*theta +
                                                                                                                                                                ekt*(3 + Math.pow(rho,2)*(6 + t*theta))) -
                                                                                                                                                                (-1 + ekt)*kappa*
                                                                                                                                                                (3 + 6*Math.pow(rho,2)*t*theta + ekt*(3 + 2*Math.pow(rho,2)*(6 + t*theta)))
                                                                                                                                                            )*y + 2*Math.pow(rho,2)*Math.pow(1 - ekt + kappa*t,2)*Math.pow(y,2)))/
                                                                                                                                                            (-theta + kappa*t*theta + (theta - y)/ekt + y) -
                                                                                                                                                            12*Math.pow(delta,3)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                (-1 + ekt)*y)*(2*theta + kappa*t*theta - y - kappa*t*y +
                                                                                                                                                                    ekt*((-2 + kappa*t)*theta + y))*
                                                                                                                                                                    (theta - 2*y + e2kt*
                                                                                                                                                                        (-5*theta + 2*kappa*t*theta + 2*y + 4*Math.pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                        2*ekt*(2*(theta + kappa*t*(theta - y)) +
                                                                                                                                                                            Math.pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))) +
                                                                                                                                                                            (288*Math.pow(delta,3)*Math.pow(kappa,2)*rho*
                                                                                                                                                                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                (2*theta + kappa*t*theta - y - kappa*t*y + ekt*((-2 + kappa*t)*theta + y))*
                                                                                                                                                                                (theta - 2*y + e2kt*
                                                                                                                                                                                    (-5*theta + 2*kappa*t*theta + 2*y + 4*Math.pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                    2*ekt*(2*(theta + kappa*t*(theta - y)) +
                                                                                                                                                                                        Math.pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))))
                                                                                                                                                                                        /(-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                        48*Math.pow(delta,2)*ekt*Math.pow(kappa,3)*
                                                                                                                                                                                        ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                        (theta - 2*y + e2kt*
                                                                                                                                                                                            (-5*theta + 2*kappa*t*theta + 2*y + 8*Math.pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                            4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                Math.pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))) -
                                                                                                                                                                                                (192*Math.pow(delta,2)*ekt*Math.pow(kappa,4)*
                                                                                                                                                                                                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                                    (theta - 2*y + e2kt*
                                                                                                                                                                                                        (-5*theta + 2*kappa*t*theta + 2*y + 8*Math.pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                                        4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                            Math.pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))))
                                                                                                                                                                                                            /(-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                                            3*Math.pow(delta,3)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                                                                (-1 + ekt)*y)*((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                    (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                    (theta - 2*y + e2kt*
                                                                                                                                                                                                                        (-5*theta + 2*kappa*t*theta + 2*y + 8*Math.pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                                                        4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                                            Math.pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))) -
                                                                                                                                                                                                                            (12*Math.pow(delta,3)*Math.pow(kappa,2)*rho*
                                                                                                                                                                                                                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                                                                ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                                    (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                                    (theta - 2*y + e2kt*
                                                                                                                                                                                                                                        (-5*theta + 2*kappa*t*theta + 2*y + 8*Math.pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                                                                        4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                                                            Math.pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))))
                                                                                                                                                                                                                                            /(-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                                                                            4*Math.pow(delta,3)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                                                                                                (-1 + ekt)*y)*(3*(theta - 2*y)*((2 + kappa*t)*theta - (1 + kappa*t)*y) +
                                                                                                                                                                                                                                                    3*ekt*(6*Math.pow(theta,2) + theta*y - 2*Math.pow(y,2) +
                                                                                                                                                                                                                                                        kappa*(13*t*Math.pow(theta,2) + theta*(8 - 18*t*y) + 4*y*(-3 + t*y)) +
                                                                                                                                                                                                                                                        4*Math.pow(kappa,2)*t*(theta + t*Math.pow(theta,2) - 2*t*theta*y + y*(-2 + t*y))) +
                                                                                                                                                                                                                                                        3*e3kt*(10*Math.pow(theta,2) +
                                                                                                                                                                                                                                                            2*Math.pow(kappa,2)*t*theta*(6 + 8*Math.pow(rho,2) + t*theta) - 9*theta*y + 2*Math.pow(y,2) +
                                                                                                                                                                                                                                                            kappa*(-9*t*Math.pow(theta,2) + 4*(3 + 4*Math.pow(rho,2))*y +
                                                                                                                                                                                                                                                                theta*(-40 - 64*Math.pow(rho,2) + 4*t*y))) +
                                                                                                                                                                                                                                                                e2kt*(-54*Math.pow(theta,2) +
                                                                                                                                                                                                                                                                    8*Math.pow(kappa,4)*Math.pow(rho,2)*Math.pow(t,3)*(theta - y) + 39*theta*y - 6*Math.pow(y,2) +
                                                                                                                                                                                                                                                                    24*Math.pow(kappa,3)*Math.pow(t,2)*(theta + 2*Math.pow(rho,2)*theta - (1 + Math.pow(rho,2))*y) +
                                                                                                                                                                                                                                                                    6*Math.pow(kappa,2)*t*(3*t*Math.pow(theta,2) - 8*(1 + Math.pow(rho,2))*y +
                                                                                                                                                                                                                                                                        theta*(16 + 24*Math.pow(rho,2) - 3*t*y)) -
                                                                                                                                                                                                                                                                        3*kappa*(5*t*Math.pow(theta,2) + 2*y*(8*Math.pow(rho,2) + 3*t*y) -
                                                                                                                                                                                                                                                                            theta*(32 + 64*Math.pow(rho,2) + 17*t*y)))) -
                                                                                                                                                                                                                                                                            (48*Math.pow(delta,3)*Math.pow(kappa,2)*rho*
                                                                                                                                                                                                                                                                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                                                                                                                (3*(theta - 2*y)*((2 + kappa*t)*theta - (1 + kappa*t)*y) +
                                                                                                                                                                                                                                                                                    3*ekt*(6*Math.pow(theta,2) + theta*y - 2*Math.pow(y,2) +
                                                                                                                                                                                                                                                                                        kappa*(13*t*Math.pow(theta,2) + theta*(8 - 18*t*y) + 4*y*(-3 + t*y)) +
                                                                                                                                                                                                                                                                                        4*Math.pow(kappa,2)*t*(theta + t*Math.pow(theta,2) - 2*t*theta*y + y*(-2 + t*y))) +
                                                                                                                                                                                                                                                                                        3*e3kt*(10*Math.pow(theta,2) +
                                                                                                                                                                                                                                                                                            2*Math.pow(kappa,2)*t*theta*(6 + 8*Math.pow(rho,2) + t*theta) - 9*theta*y +
                                                                                                                                                                                                                                                                                            2*Math.pow(y,2) + kappa*(-9*t*Math.pow(theta,2) + 4*(3 + 4*Math.pow(rho,2))*y +
                                                                                                                                                                                                                                                                                                theta*(-40 - 64*Math.pow(rho,2) + 4*t*y))) +
                                                                                                                                                                                                                                                                                                e2kt*(-54*Math.pow(theta,2) +
                                                                                                                                                                                                                                                                                                    8*Math.pow(kappa,4)*Math.pow(rho,2)*Math.pow(t,3)*(theta - y) + 39*theta*y - 6*Math.pow(y,2) +
                                                                                                                                                                                                                                                                                                    24*Math.pow(kappa,3)*Math.pow(t,2)*
                                                                                                                                                                                                                                                                                                    (theta + 2*Math.pow(rho,2)*theta - (1 + Math.pow(rho,2))*y) +
                                                                                                                                                                                                                                                                                                    6*Math.pow(kappa,2)*t*(3*t*Math.pow(theta,2) - 8*(1 + Math.pow(rho,2))*y +
                                                                                                                                                                                                                                                                                                        theta*(16 + 24*Math.pow(rho,2) - 3*t*y)) -
                                                                                                                                                                                                                                                                                                        3*kappa*(5*t*Math.pow(theta,2) + 2*y*(8*Math.pow(rho,2) + 3*t*y) -
                                                                                                                                                                                                                                                                                                            theta*(32 + 64*Math.pow(rho,2) + 17*t*y)))))/
                                                                                                                                                                                                                                                                                                            (-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                                                                                                                                            (240*Math.pow(delta,3)*e2kt*Math.pow(kappa,2)*rho*
                                                                                                                                                                                                                                                                                                                ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                                                                                                                    (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                                                                                                                    (12*ekt*Math.pow(kappa,3)*Math.pow(rho,2)*Math.pow(t,2)*(theta - y) +
                                                                                                                                                                                                                                                                                                                        2*Math.pow(-1 + ekt,2)*Math.pow(rho,2)*Math.pow(-2*theta + y,2) -
                                                                                                                                                                                                                                                                                                                        (-1 + ekt)*kappa*
                                                                                                                                                                                                                                                                                                                        (8*(1 + ekt)*Math.pow(rho,2)*t*Math.pow(theta,2) +
                                                                                                                                                                                                                                                                                                                            2*y*(-3 - 3*ekt*(1 + 4*Math.pow(rho,2)) + 2*Math.pow(rho,2)*t*y) +
                                                                                                                                                                                                                                                                                                                            theta*(3 - 12*Math.pow(rho,2)*t*y + ekt*(15 + Math.pow(rho,2)*(72 - 4*t*y)))
                                                                                                                                                                                                                                                                                                                            ) + 2*Math.pow(kappa,2)*t*(e2kt*theta*
                                                                                                                                                                                                                                                                                                                                (3 + Math.pow(rho,2)*(12 + t*theta)) + Math.pow(rho,2)*t*Math.pow(theta - y,2) +
                                                                                                                                                                                                                                                                                                                                2*ekt*(Math.pow(rho,2)*t*Math.pow(theta,2) - 3*(y + 2*Math.pow(rho,2)*y) +
                                                                                                                                                                                                                                                                                                                                    theta*(3 + Math.pow(rho,2)*(12 - t*y))))))/
                                                                                                                                                                                                                                                                                                                                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y))/
                                                                                                                                                                                                                                                                                                                                    (3072.*e4kt*Math.pow(kappa,7)*Math.pow(t,2)*
                                                                                                                                                                                                                                                                                                                                        Math.pow((-theta + kappa*t*theta + (theta - y)/ekt + y)/(kappa*t),1.5));
    }

    /** Mathematica-emitted x-coefficient. Verbatim transliteration from C++. */
    private double z1(final double t, final double kappa, final double theta,
            final double delta, final double y, final double rho) {
        return (delta*(768*e2kt*Math.pow(kappa,4)*rho*
            ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                (-1 + ekt - kappa*t)*y) -
                (576*delta*e2kt*Math.pow(kappa,3)*Math.pow(rho,2)*
                    Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                        (-1 + ekt - kappa*t)*y,2))/
                        ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) -
                        10*Math.pow(delta,2)*Math.pow(rho,3)*Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*
                            theta + (-1 + ekt - kappa*t)*y,3) +
                            (6*Math.pow(delta,2)*kappa*Math.pow(rho,3)*
                                Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                    (-1 + ekt - kappa*t)*y,3))/
                                    (-theta + kappa*t*theta + (theta - y)/ekt + y) -
                                    (3360*Math.pow(delta,2)*e3kt*Math.pow(kappa,3)*Math.pow(rho,3)*
                                        Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                            (-1 + ekt - kappa*t)*y,3))/
                                            Math.pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,3) -
                                            (288*Math.pow(delta,2)*e2kt*Math.pow(kappa,2)*Math.pow(rho,3)*
                                                Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                    (-1 + ekt - kappa*t)*y,3))/
                                                    Math.pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,2) +
                                                    (234*Math.pow(delta,2)*ekt*kappa*Math.pow(rho,3)*
                                                        Math.pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                            (-1 + ekt - kappa*t)*y,3))/
                                                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) -
                                                            96*delta*ekt*Math.pow(kappa,3)*
                                                            ((1 + 4*ekt*(1 + kappa*t) + e2kt*(-5 + 2*kappa*t))*theta +
                                                                2*(-1 + e2kt - 2*ekt*kappa*t)*y) -
                                                                12*Math.pow(delta,2)*kappa*rho*((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                    (-1 + ekt - kappa*t)*y)*
                                                                    ((1 + 4*ekt*(1 + kappa*t) + e2kt*(-5 + 2*kappa*t))*theta +
                                                                        2*(-1 + e2kt - 2*ekt*kappa*t)*y) -
                                                                        192*Math.pow(delta,2)*ekt*Math.pow(kappa,2)*rho*
                                                                        ((2 + kappa*t + 2*ekt*Math.pow(2 + kappa*t,2) +
                                                                            e2kt*(-10 + 3*kappa*t))*theta +
                                                                            (-3 + 3*e2kt - 2*kappa*t - 2*ekt*kappa*t*(2 + kappa*t))*y)
                                                                            - (12*Math.pow(delta,2)*ekt*Math.pow(kappa,2)*rho*
                                                                                ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                    (-1 + ekt - kappa*t)*y)*
                                                                                    ((1 + e2kt*(-5 + 2*kappa*t + 8*Math.pow(rho,2)*(-3 + kappa*t)) +
                                                                                        4*ekt*(1 + kappa*t +
                                                                                            Math.pow(rho,2)*(6 + 4*kappa*t + Math.pow(kappa,2)*Math.pow(t,2))))*theta +
                                                                                            2*(-1 + e2kt*(1 + 4*Math.pow(rho,2)) -
                                                                                                2*ekt*(kappa*t +
                                                                                                    Math.pow(rho,2)*(2 + 2*kappa*t + Math.pow(kappa,2)*Math.pow(t,2))))*y))/
                                                                                                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                                                                                                    (576*Math.pow(delta,2)*ekt*Math.pow(kappa,2)*rho*
                                                                                                        ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                            (-1 + ekt - kappa*t)*y)*
                                                                                                            ((1 + e2kt*(-5 + 2*kappa*t + 4*Math.pow(rho,2)*(-3 + kappa*t)) +
                                                                                                                2*ekt*(2 + 2*kappa*t +
                                                                                                                    Math.pow(rho,2)*(6 + 4*kappa*t + Math.pow(kappa,2)*Math.pow(t,2))))*theta +
                                                                                                                    2*(-1 + e2kt*(1 + 2*Math.pow(rho,2)) -
                                                                                                                        ekt*(2*kappa*t +
                                                                                                                            Math.pow(rho,2)*(2 + 2*kappa*t + Math.pow(kappa,2)*Math.pow(t,2))))*y))/
                                                                                                                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                                                                                                                            (5*Math.pow(delta,2)*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                (-1 + ekt)*y)*
                                                                                                                                ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                    (-1 + ekt - kappa*t)*y)*
                                                                                                                                    (theta*(12*ekt*Math.pow(kappa,3)*Math.pow(rho,2)*Math.pow(t,2) +
                                                                                                                                        8*Math.pow(-1 + ekt,2)*Math.pow(rho,2)*theta -
                                                                                                                                        (-1 + ekt)*kappa*
                                                                                                                                        (3 + 8*Math.pow(rho,2)*t*theta +
                                                                                                                                            ekt*(15 + 8*Math.pow(rho,2)*(9 + t*theta))) +
                                                                                                                                            2*Math.pow(kappa,2)*t*(Math.pow(rho,2)*t*theta +
                                                                                                                                                2*ekt*(3 + Math.pow(rho,2)*(12 + t*theta)) +
                                                                                                                                                e2kt*(3 + Math.pow(rho,2)*(12 + t*theta)))) -
                                                                                                                                                2*(6*ekt*Math.pow(kappa,3)*Math.pow(rho,2)*Math.pow(t,2) +
                                                                                                                                                    4*Math.pow(-1 + ekt,2)*Math.pow(rho,2)*theta +
                                                                                                                                                    2*Math.pow(kappa,2)*t*(Math.pow(rho,2)*t*theta +
                                                                                                                                                        ekt*(3 + Math.pow(rho,2)*(6 + t*theta))) -
                                                                                                                                                        (-1 + ekt)*kappa*
                                                                                                                                                        (3 + 6*Math.pow(rho,2)*t*theta +
                                                                                                                                                            ekt*(3 + 2*Math.pow(rho,2)*(6 + t*theta))))*y +
                                                                                                                                                            2*Math.pow(rho,2)*Math.pow(1 - ekt + kappa*t,2)*Math.pow(y,2)))/
                                                                                                                                                            (ekt*(-theta + kappa*t*theta + (theta - y)/ekt + y)) -
                                                                                                                                                            (48*Math.pow(delta,2)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                (-1 + ekt)*y)*
                                                                                                                                                                (2*theta + kappa*t*theta - y - kappa*t*y +
                                                                                                                                                                    ekt*((-2 + kappa*t)*theta + y))*
                                                                                                                                                                    (theta - 2*y + e2kt*
                                                                                                                                                                        (-5*theta + 2*kappa*t*theta + 2*y + 4*Math.pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                        2*ekt*(2*(theta + kappa*t*(theta - y)) +
                                                                                                                                                                            Math.pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))
                                                                                                                                                                        ))/(ekt*(-theta + kappa*t*theta + (theta - y)/ekt + y)) +
                                                                                                                                                                        (96*delta*Math.pow(kappa,3)*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                            (-1 + ekt)*y)*
                                                                                                                                                                            (theta - 2*y + e2kt*
                                                                                                                                                                                (-5*theta + 2*kappa*t*theta + 2*y + 8*Math.pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                    Math.pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))
                                                                                                                                                                                ))/(-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                (9*Math.pow(delta,2)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                                    (-1 + ekt)*y)*
                                                                                                                                                                                    ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                        (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                        (theta - 2*y + e2kt*
                                                                                                                                                                                            (-5*theta + 2*kappa*t*theta + 2*y + 8*Math.pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                            4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                Math.pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))
                                                                                                                                                                                            ))/(ekt*(-theta + kappa*t*theta + (theta - y)/ekt + y)) -
                                                                                                                                                                                            (48*Math.pow(delta,2)*ekt*Math.pow(kappa,2)*rho*
                                                                                                                                                                                                (3*(theta - 2*y)*((2 + kappa*t)*theta - (1 + kappa*t)*y) +
                                                                                                                                                                                                    3*ekt*(6*Math.pow(theta,2) + theta*y - 2*Math.pow(y,2) +
                                                                                                                                                                                                        kappa*(13*t*Math.pow(theta,2) + theta*(8 - 18*t*y) + 4*y*(-3 + t*y)) +
                                                                                                                                                                                                        4*Math.pow(kappa,2)*t*(theta + t*Math.pow(theta,2) - 2*t*theta*y + y*(-2 + t*y))) +
                                                                                                                                                                                                        3*e3kt*(10*Math.pow(theta,2) +
                                                                                                                                                                                                            2*Math.pow(kappa,2)*t*theta*(6 + 8*Math.pow(rho,2) + t*theta) - 9*theta*y +
                                                                                                                                                                                                            2*Math.pow(y,2) + kappa*(-9*t*Math.pow(theta,2) + 4*(3 + 4*Math.pow(rho,2))*y +
                                                                                                                                                                                                                theta*(-40 - 64*Math.pow(rho,2) + 4*t*y))) +
                                                                                                                                                                                                                e2kt*(-54*Math.pow(theta,2) +
                                                                                                                                                                                                                    8*Math.pow(kappa,4)*Math.pow(rho,2)*Math.pow(t,3)*(theta - y) + 39*theta*y -
                                                                                                                                                                                                                    6*Math.pow(y,2) + 24*Math.pow(kappa,3)*Math.pow(t,2)*
                                                                                                                                                                                                                    (theta + 2*Math.pow(rho,2)*theta - (1 + Math.pow(rho,2))*y) +
                                                                                                                                                                                                                    6*Math.pow(kappa,2)*t*(3*t*Math.pow(theta,2) - 8*(1 + Math.pow(rho,2))*y +
                                                                                                                                                                                                                        theta*(16 + 24*Math.pow(rho,2) - 3*t*y)) -
                                                                                                                                                                                                                        3*kappa*(5*t*Math.pow(theta,2) + 2*y*(8*Math.pow(rho,2) + 3*t*y) -
                                                                                                                                                                                                                            theta*(32 + 64*Math.pow(rho,2) + 17*t*y)))))/
                                                                                                                                                                                                                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                                                                                                                                                                                                                            (12*Math.pow(delta,2)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                                                                                (-1 + ekt)*y)*
                                                                                                                                                                                                                                (3*(theta - 2*y)*((2 + kappa*t)*theta - (1 + kappa*t)*y) +
                                                                                                                                                                                                                                    3*ekt*(6*Math.pow(theta,2) + theta*y - 2*Math.pow(y,2) +
                                                                                                                                                                                                                                        kappa*(13*t*Math.pow(theta,2) + theta*(8 - 18*t*y) + 4*y*(-3 + t*y)) +
                                                                                                                                                                                                                                        4*Math.pow(kappa,2)*t*(theta + t*Math.pow(theta,2) - 2*t*theta*y + y*(-2 + t*y))) +
                                                                                                                                                                                                                                        3*e3kt*(10*Math.pow(theta,2) +
                                                                                                                                                                                                                                            2*Math.pow(kappa,2)*t*theta*(6 + 8*Math.pow(rho,2) + t*theta) - 9*theta*y +
                                                                                                                                                                                                                                            2*Math.pow(y,2) + kappa*(-9*t*Math.pow(theta,2) + 4*(3 + 4*Math.pow(rho,2))*y +
                                                                                                                                                                                                                                                theta*(-40 - 64*Math.pow(rho,2) + 4*t*y))) +
                                                                                                                                                                                                                                                e2kt*(-54*Math.pow(theta,2) +
                                                                                                                                                                                                                                                    8*Math.pow(kappa,4)*Math.pow(rho,2)*Math.pow(t,3)*(theta - y) + 39*theta*y -
                                                                                                                                                                                                                                                    6*Math.pow(y,2) + 24*Math.pow(kappa,3)*Math.pow(t,2)*
                                                                                                                                                                                                                                                    (theta + 2*Math.pow(rho,2)*theta - (1 + Math.pow(rho,2))*y) +
                                                                                                                                                                                                                                                    6*Math.pow(kappa,2)*t*(3*t*Math.pow(theta,2) - 8*(1 + Math.pow(rho,2))*y +
                                                                                                                                                                                                                                                        theta*(16 + 24*Math.pow(rho,2) - 3*t*y)) -
                                                                                                                                                                                                                                                        3*kappa*(5*t*Math.pow(theta,2) + 2*y*(8*Math.pow(rho,2) + 3*t*y) -
                                                                                                                                                                                                                                                            theta*(32 + 64*Math.pow(rho,2) + 17*t*y)))))/
                                                                                                                                                                                                                                                            (ekt*(-theta + kappa*t*theta + (theta - y)/ekt + y)) +
                                                                                                                                                                                                                                                            (240*Math.pow(delta,2)*e2kt*Math.pow(kappa,2)*rho*
                                                                                                                                                                                                                                                                ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                                                                    (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                                                                    (12*ekt*Math.pow(kappa,3)*Math.pow(rho,2)*Math.pow(t,2)*(theta - y) +
                                                                                                                                                                                                                                                                        2*Math.pow(-1 + ekt,2)*Math.pow(rho,2)*Math.pow(-2*theta + y,2) -
                                                                                                                                                                                                                                                                        (-1 + ekt)*kappa*
                                                                                                                                                                                                                                                                        (8*(1 + ekt)*Math.pow(rho,2)*t*Math.pow(theta,2) +
                                                                                                                                                                                                                                                                            2*y*(-3 - 3*ekt*(1 + 4*Math.pow(rho,2)) + 2*Math.pow(rho,2)*t*y) +
                                                                                                                                                                                                                                                                            theta*(3 - 12*Math.pow(rho,2)*t*y +
                                                                                                                                                                                                                                                                                ekt*(15 + Math.pow(rho,2)*(72 - 4*t*y)))) +
                                                                                                                                                                                                                                                                                2*Math.pow(kappa,2)*t*(e2kt*theta*(3 + Math.pow(rho,2)*(12 + t*theta)) +
                                                                                                                                                                                                                                                                                    Math.pow(rho,2)*t*Math.pow(theta - y,2) +
                                                                                                                                                                                                                                                                                    2*ekt*(Math.pow(rho,2)*t*Math.pow(theta,2) - 3*(y + 2*Math.pow(rho,2)*y) +
                                                                                                                                                                                                                                                                                        theta*(3 + Math.pow(rho,2)*(12 - t*y))))))/
                                                                                                                                                                                                                                                                                        Math.pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,2) -
                                                                                                                                                                                                                                                                                        (120*Math.pow(delta,2)*ekt*kappa*rho*
                                                                                                                                                                                                                                                                                            ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                                                                                                (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                                                                                                (12*ekt*Math.pow(kappa,3)*Math.pow(rho,2)*Math.pow(t,2)*(theta - y) +
                                                                                                                                                                                                                                                                                                    2*Math.pow(-1 + ekt,2)*Math.pow(rho,2)*Math.pow(-2*theta + y,2) -
                                                                                                                                                                                                                                                                                                    (-1 + ekt)*kappa*
                                                                                                                                                                                                                                                                                                    (8*(1 + ekt)*Math.pow(rho,2)*t*Math.pow(theta,2) +
                                                                                                                                                                                                                                                                                                        2*y*(-3 - 3*ekt*(1 + 4*Math.pow(rho,2)) + 2*Math.pow(rho,2)*t*y) +
                                                                                                                                                                                                                                                                                                        theta*(3 - 12*Math.pow(rho,2)*t*y +
                                                                                                                                                                                                                                                                                                            ekt*(15 + Math.pow(rho,2)*(72 - 4*t*y)))) +
                                                                                                                                                                                                                                                                                                            2*Math.pow(kappa,2)*t*(e2kt*theta*(3 + Math.pow(rho,2)*(12 + t*theta)) +
                                                                                                                                                                                                                                                                                                                Math.pow(rho,2)*t*Math.pow(theta - y,2) +
                                                                                                                                                                                                                                                                                                                2*ekt*(Math.pow(rho,2)*t*Math.pow(theta,2) - 3*(y + 2*Math.pow(rho,2)*y) +
                                                                                                                                                                                                                                                                                                                    theta*(3 + Math.pow(rho,2)*(12 - t*y))))))/
                                                                                                                                                                                                                                                                                                                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)))/
                                                                                                                                                                                                                                                                                                                    (1536.*e3kt*Math.pow(kappa,6)*Math.pow(t,2)*
                                                                                                                                                                                                                                                                                                                        Math.pow((-theta + kappa*t*theta + (theta - y)/ekt + y)/(kappa*t),1.5));
    }

    /** Mathematica-emitted x^2-coefficient. Verbatim transliteration from C++. */
    private double z2(final double t, final double kappa, final double theta,
            final double delta, final double y, final double rho) {
        return (Math.pow(delta,2)*(8*e3kt*Math.pow(kappa,5)*Math.pow(rho,2)*Math.pow(t,4)*(2 + delta*rho*t)*
            Math.pow(theta,2)*(theta - y) - delta*Math.pow(-1 + ekt,3)*rho*
            (2*(-1 + ekt*(-5 + 24*Math.pow(rho,2)))*Math.pow(theta,3) +
                (7 + ekt*(3 + 56*Math.pow(rho,2)))*Math.pow(theta,2)*y -
                3*(1 + ekt*(-3 + 8*Math.pow(rho,2)))*theta*Math.pow(y,2) +
                2*(-1 + ekt*(-1 + 2*Math.pow(rho,2)))*Math.pow(y,3)) -
                Math.pow(-1 + ekt,2)*kappa*
                ((-4 + delta*rho*t - 8*ekt*
                    (2 - 12*Math.pow(rho,2) - 4*delta*rho*t + 25*delta*Math.pow(rho,3)*t) +
                    e2kt*(20 - 96*Math.pow(rho,2) + 3*delta*rho*t + 56*delta*Math.pow(rho,3)*t)
                    )*Math.pow(theta,3) - 2*(-8 + 2*delta*rho*t +
                        e2kt*(24 - 80*Math.pow(rho,2) - 9*delta*rho*t +
                            24*delta*Math.pow(rho,3)*t) -
                            4*ekt*(4 - 20*Math.pow(rho,2) - 10*delta*rho*t + 39*delta*Math.pow(rho,3)*t)
                        )*Math.pow(theta,2)*y + (5*(-4 + delta*rho*t) +
                            ekt*(-16 + 80*Math.pow(rho,2) + 57*delta*rho*t -
                                140*delta*Math.pow(rho,3)*t) +
                                2*e2kt*(18 - 40*Math.pow(rho,2) - 3*delta*rho*t +
                                    6*delta*Math.pow(rho,3)*t))*theta*Math.pow(y,2) +
                                    2*(4 + e2kt*(-4 + 8*Math.pow(rho,2)) - delta*rho*t +
                                        ekt*rho*(-8*rho - 7*delta*t + 14*delta*Math.pow(rho,2)*t))*Math.pow(y,3)) +
                                        ekt*(-1 + ekt)*Math.pow(kappa,2)*t*
                                        ((-24 + 128*Math.pow(rho,2) + 9*delta*rho*t - 144*delta*Math.pow(rho,3)*t -
                                            4*ekt*(6 - 8*Math.pow(rho,2) - 9*delta*rho*t + 6*delta*Math.pow(rho,3)*t) +
                                            e2kt*(48 - 160*Math.pow(rho,2) - 9*delta*rho*t +
                                                24*delta*Math.pow(rho,3)*t))*Math.pow(theta,3) -
                                                (-72 + 320*Math.pow(rho,2) + 27*delta*rho*t - 360*delta*Math.pow(rho,3)*t -
                                                    ekt*rho*(160*rho - 81*delta*t + 348*delta*Math.pow(rho,2)*t) +
                                                    2*e2kt*(36 - 80*Math.pow(rho,2) - 3*delta*rho*t +
                                                        6*delta*Math.pow(rho,3)*t))*Math.pow(theta,2)*y -
                                                        2*(32 - 128*Math.pow(rho,2) + 12*e2kt*(-1 + 2*Math.pow(rho,2)) -
                                                            15*delta*rho*t + 144*delta*Math.pow(rho,3)*t +
                                                            2*ekt*(-10 + 52*Math.pow(rho,2) - 13*delta*rho*t +
                                                                58*delta*Math.pow(rho,3)*t))*theta*Math.pow(y,2) +
                                                                4*(4 - 16*Math.pow(rho,2) - 3*delta*rho*t + 18*delta*Math.pow(rho,3)*t +
                                                                    ekt*(-4 + 16*Math.pow(rho,2) - 2*delta*rho*t + 11*delta*Math.pow(rho,3)*t))*
                                                                    Math.pow(y,3)) - 4*e2kt*Math.pow(kappa,4)*Math.pow(t,3)*theta*
                                                                    (2*e2kt*(-1 + 2*Math.pow(rho,2))*Math.pow(theta,2) +
                                                                        Math.pow(rho,2)*(4 + 13*delta*rho*t)*Math.pow(theta - y,2) +
                                                                        ekt*((-4 + 16*Math.pow(rho,2) - 2*delta*rho*t + 9*delta*Math.pow(rho,3)*t)*
                                                                            Math.pow(theta,2) + (4 - 32*Math.pow(rho,2) + 2*delta*rho*t - 19*delta*Math.pow(rho,3)*t)*
                                                                            theta*y + 4*Math.pow(rho,2)*(2 + delta*rho*t)*Math.pow(y,2))) -
                                                                            2*ekt*Math.pow(kappa,3)*Math.pow(t,2)*
                                                                            (-4*Math.pow(rho,2)*(-4 + 3*delta*rho*t)*Math.pow(theta - y,3) +
                                                                                e3kt*Math.pow(theta,2)*
                                                                                ((18 - 40*Math.pow(rho,2) - delta*rho*t + 2*delta*Math.pow(rho,3)*t)*theta +
                                                                                    12*(-1 + 2*Math.pow(rho,2))*y) +
                                                                                    2*ekt*((-9 + 36*Math.pow(rho,2) + 19*delta*Math.pow(rho,3)*t)*Math.pow(theta,3) +
                                                                                        2*(9 - 30*Math.pow(rho,2) + 7*delta*Math.pow(rho,3)*t)*Math.pow(theta,2)*y +
                                                                                        (-8 + 20*Math.pow(rho,2) + delta*rho*t - 46*delta*Math.pow(rho,3)*t)*theta*Math.pow(y,2) +
                                                                                        Math.pow(rho,2)*(4 + 13*delta*rho*t)*Math.pow(y,3)) +
                                                                                        e2kt*(8*theta*y*(-3*theta + 2*y) +
                                                                                            delta*rho*t*theta*(7*Math.pow(theta,2) - 23*theta*y + 8*Math.pow(y,2)) -
                                                                                            8*Math.pow(rho,2)*(6*Math.pow(theta,3) - 18*Math.pow(theta,2)*y + 11*theta*Math.pow(y,2) -
                                                                                                Math.pow(y,3)) + 4*delta*Math.pow(rho,3)*t*
                                                                                                (-13*Math.pow(theta,3) + 31*Math.pow(theta,2)*y - 14*theta*Math.pow(y,2) + Math.pow(y,3))))))/
                                                                                                (64.*Math.pow(kappa,2)*t*Math.sqrt((-theta + kappa*t*theta + (theta - y)/ekt + y)/
                                                                                                    (kappa*t))*Math.pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,
                                                                                                        4));
    }

    /** Mathematica-emitted x^3-coefficient. Verbatim transliteration from C++. */
    private double z3(final double t, final double kappa, final double theta,
            final double delta, final double y, final double rho) {
        return (Math.pow(delta,3)*ekt*rho*((-15*(2 + kappa*t) +
            3*e4kt*(50 - 79*kappa*t + 35*Math.pow(kappa,2)*Math.pow(t,2) -
                6*Math.pow(kappa,3)*Math.pow(t,3) +
                8*Math.pow(rho,2)*(-18 + 15*kappa*t - 6*Math.pow(kappa,2)*Math.pow(t,2) +
                    Math.pow(kappa,3)*Math.pow(t,3))) +
                    ekt*(-3*(20 + 86*kappa*t + 29*Math.pow(kappa,2)*Math.pow(t,2)) +
                        Math.pow(rho,2)*(432 + 936*kappa*t + 552*Math.pow(kappa,2)*Math.pow(t,2) +
                            92*Math.pow(kappa,3)*Math.pow(t,3))) +
                            e2kt*(360 + 324*kappa*t - 261*Math.pow(kappa,2)*Math.pow(t,2) -
                                48*Math.pow(kappa,3)*Math.pow(t,3) -
                                4*Math.pow(rho,2)*(324 + 378*kappa*t - 12*Math.pow(kappa,2)*Math.pow(t,2) -
                                    2*Math.pow(kappa,3)*Math.pow(t,3) + 23*Math.pow(kappa,4)*Math.pow(t,4))) +
                                    e3kt*(3*(-140 + 62*kappa*t + 81*Math.pow(kappa,2)*Math.pow(t,2) -
                                        38*Math.pow(kappa,3)*Math.pow(t,3) + 8*Math.pow(kappa,4)*Math.pow(t,4)) +
                                        4*Math.pow(rho,2)*(324 + 54*kappa*t - 114*Math.pow(kappa,2)*Math.pow(t,2) +
                                            77*Math.pow(kappa,3)*Math.pow(t,3) - 19*Math.pow(kappa,4)*Math.pow(t,4) +
                                            2*Math.pow(kappa,5)*Math.pow(t,5))))*Math.pow(theta,3) +
                                            (15*(7 + 4*kappa*t) + 3*e4kt*
                                                (-79 + 70*kappa*t - 18*Math.pow(kappa,2)*Math.pow(t,2) +
                                                    24*Math.pow(rho,2)*(5 - 4*kappa*t + Math.pow(kappa,2)*Math.pow(t,2))) -
                                                    3*ekt*(26 - 200*kappa*t - 87*Math.pow(kappa,2)*Math.pow(t,2) +
                                                        4*Math.pow(rho,2)*(30 + 142*kappa*t + 115*Math.pow(kappa,2)*Math.pow(t,2) +
                                                            23*Math.pow(kappa,3)*Math.pow(t,3))) +
                                                            2*e2kt*(3*(-66 - 195*kappa*t + 63*Math.pow(kappa,2)*Math.pow(t,2) +
                                                                16*Math.pow(kappa,3)*Math.pow(t,3)) +
                                                                4*Math.pow(rho,2)*(135 + 390*kappa*t - 9*Math.pow(kappa,2)*Math.pow(t,2) -
                                                                    48*Math.pow(kappa,3)*Math.pow(t,3) + 23*Math.pow(kappa,4)*Math.pow(t,4))) +
                                                                    e3kt*(606 + 300*kappa*t - 585*Math.pow(kappa,2)*Math.pow(t,2) +
                                                                        210*Math.pow(kappa,3)*Math.pow(t,3) - 24*Math.pow(kappa,4)*Math.pow(t,4) -
                                                                        4*Math.pow(rho,2)*(270 + 282*kappa*t - 345*Math.pow(kappa,2)*Math.pow(t,2) +
                                                                            153*Math.pow(kappa,3)*Math.pow(t,3) - 29*Math.pow(kappa,4)*Math.pow(t,4) +
                                                                            2*Math.pow(kappa,5)*Math.pow(t,5))))*Math.pow(theta,2)*y +
                                                                            (-93 - 75*kappa*t + 3*e4kt*
                                                                                (35 - 18*kappa*t + 24*Math.pow(rho,2)*(-2 + kappa*t)) +
                                                                                3*ekt*(58 - 123*kappa*t - 86*Math.pow(kappa,2)*Math.pow(t,2) +
                                                                                    4*Math.pow(rho,2)*(12 + 80*kappa*t + 92*Math.pow(kappa,2)*Math.pow(t,2) +
                                                                                        23*Math.pow(kappa,3)*Math.pow(t,3))) +
                                                                                        e3kt*(-3*(74 + 137*kappa*t - 100*Math.pow(kappa,2)*Math.pow(t,2) +
                                                                                            16*Math.pow(kappa,3)*Math.pow(t,3)) -
                                                                                            16*Math.pow(rho,2)*(-27 - 51*kappa*t + 45*Math.pow(kappa,2)*Math.pow(t,2) -
                                                                                                12*Math.pow(kappa,3)*Math.pow(t,3) + Math.pow(kappa,4)*Math.pow(t,4))) +
                                                                                                e2kt*(36 + 909*kappa*t - 42*Math.pow(kappa,2)*Math.pow(t,2) -
                                                                                                    60*Math.pow(kappa,3)*Math.pow(t,3) -
                                                                                                    4*Math.pow(rho,2)*(108 + 462*kappa*t + 96*Math.pow(kappa,2)*Math.pow(t,2) -
                                                                                                        117*Math.pow(kappa,3)*Math.pow(t,3) + 23*Math.pow(kappa,4)*Math.pow(t,4))))*theta*Math.pow(y,2)
                                                                                                        + 2*(9 + 3*e4kt*(-3 + 4*Math.pow(rho,2)) + 15*kappa*t +
                                                                                                            e2kt*(-3*kappa*t*(33 + 10*kappa*t) +
                                                                                                                Math.pow(rho,2)*(36 + 192*kappa*t + 96*Math.pow(kappa,2)*Math.pow(t,2) -
                                                                                                                    46*Math.pow(kappa,3)*Math.pow(t,3))) +
                                                                                                                    e3kt*(18 + 57*kappa*t - 12*Math.pow(kappa,2)*Math.pow(t,2) -
                                                                                                                        2*Math.pow(rho,2)*(18 + 48*kappa*t - 21*Math.pow(kappa,2)*Math.pow(t,2) +
                                                                                                                            2*Math.pow(kappa,3)*Math.pow(t,3))) +
                                                                                                                            ekt*(3*(-6 + 9*kappa*t + 14*Math.pow(kappa,2)*Math.pow(t,2)) -
                                                                                                                                2*Math.pow(rho,2)*(6 + 48*kappa*t + 69*Math.pow(kappa,2)*Math.pow(t,2) +
                                                                                                                                    23*Math.pow(kappa,3)*Math.pow(t,3))))*Math.pow(y,3)))/
                                                                                                                                    (96.*kappa*t*Math.sqrt((-theta + kappa*t*theta + (theta - y)/ekt + y)/(kappa*t))*
                                                                                                                                        Math.pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,5));
    }
}
