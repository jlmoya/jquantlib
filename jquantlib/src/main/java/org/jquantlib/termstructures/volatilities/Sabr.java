/*
 Copyright (C) 2008 Richard Gomes

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

package org.jquantlib.termstructures.volatilities;

import static org.jquantlib.math.Closeness.isClose;

import org.jquantlib.QL;
import org.jquantlib.lang.annotation.Rate;
import org.jquantlib.lang.annotation.Real;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.math.Constants;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.model.VolatilityType;


/**
 * Implements the Black equivalent volatility for the S.A.B.R. model.
 *
 * <p>Phase 4f.5 — extended to mirror the full QuantLib v1.42.1
 * {@code ql/termstructures/volatility/sabr.{hpp,cpp}} surface:
 * lognormal, shifted-lognormal, normal (Bachelier),
 * Floc'h-Kennedy expansions, and the dispatcher overloads.
 *
 * @author <Richard Gomes>
 *
 */
public class Sabr {

    /**
     * Computes the Black equivalent volatility without validating parameters
     *
     * @param strike
     * @param forward
     * @param expiryTime
     * @param alpha
     * @param beta
     * @param nu
     * @param rho
     *
     * @return Black equivalent volatility
     *
     * @see #validateSabrParameters(Real, Real, Real, Real)
     * @see #sabrVolatility(Rate, Rate, Time, Real, Real, Real, Real)
     */
    public double unsafeSabrVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho) {
        return unsafeSabrLogNormalVolatility(strike, forward, expiryTime, alpha, beta, nu, rho);
    }

    /**
     * Black equivalent (lognormal) SABR volatility — mirrors C++ v1.42.1
     * {@code unsafeSabrLogNormalVolatility} (sabr.cpp lines 37-76).
     *
     * <p>Hagan-Kumar-Lesniewski-Woodward 2002 closed-form. The Taylor branch
     * uses {@code 1.0 - 0.5*rho*z - (3.0*rho*rho-2.0)*z*z/12.0} (matches C++).
     */
    public double unsafeSabrLogNormalVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho) {

        final double oneMinusBeta = 1.0 - beta;
        final double A = JQuantMath.pow(forward * strike, oneMinusBeta);
        final double sqrtA = Math.sqrt(A);
        double logM;
        if (!isClose(forward, strike))
            logM = Math.log(forward / strike);
        else {
            final double epsilon = (forward - strike) / strike;
            logM = epsilon - .5 * epsilon * epsilon;
        }
        final double z = (nu / alpha) * sqrtA * logM;
        final double B = 1.0 - 2.0 * rho * z + z * z;
        final double C = oneMinusBeta * oneMinusBeta * logM * logM;
        final double tmp = (Math.sqrt(B) + z - rho) / (1.0 - rho);
        final double xx = Math.log(tmp);
        final double D = sqrtA * (1.0 + C / 24.0 + C * C / 1920.0);
        final double d = 1.0 + expiryTime *
                (oneMinusBeta * oneMinusBeta * alpha * alpha / (24.0 * A)
                        + 0.25 * rho * beta * nu * alpha / sqrtA
                        + (2.0 - 3.0 * rho * rho) * (nu * nu / 24.0));

        double multiplier;
        // computations become precise enough if the square of z is worth
        // slightly more than the precision machine (hence the m)
        final double m = 10;
        if (Math.abs(z * z) > Constants.QL_EPSILON * m)
            multiplier = z / xx;
        else {
            multiplier = 1.0 - 0.5 * rho * z - (3.0 * rho * rho - 2.0) * z * z / 12.0;
        }
        return (alpha / D) * multiplier * d;
    }

    /**
     * Shifted-SABR equivalent volatility — mirrors C++
     * {@code unsafeShiftedSabrVolatility} (sabr.cpp lines 78-92).
     * Dispatches to lognormal or normal underlying based on volatilityType.
     */
    public double unsafeShiftedSabrVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho,
            final double shift,
            final VolatilityType volatilityType) {
        if (volatilityType == VolatilityType.Normal) {
            return unsafeSabrNormalVolatility(strike + shift, forward + shift, expiryTime,
                    alpha, beta, nu, rho);
        } else {
            return unsafeSabrLogNormalVolatility(strike + shift, forward + shift, expiryTime,
                    alpha, beta, nu, rho);
        }
    }

    /**
     * Convenience overload: defaults volatilityType to ShiftedLognormal.
     */
    public double unsafeShiftedSabrVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho,
            final double shift) {
        return unsafeShiftedSabrVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho, shift, VolatilityType.ShiftedLognormal);
    }

    /**
     * Normal (Bachelier-equivalent) SABR volatility — mirrors C++ v1.42.1
     * {@code unsafeSabrNormalVolatility} (sabr.cpp lines 94-132).
     *
     * <p>Reference: <a href="https://www2.deloitte.com/content/dam/Deloitte/global/Documents/Financial-Services/be-aers-fsi-sabr-sensitivities.pdf">Deloitte SABR sensitivities</a>.
     */
    public double unsafeSabrNormalVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho) {
        final double oneMinusBeta = 1.0 - beta;
        final double minusBeta = -1.0 * beta;
        final double A = JQuantMath.pow(forward * strike, oneMinusBeta);
        final double sqrtA = Math.sqrt(A);
        double logM;
        if (!isClose(forward, strike))
            logM = Math.log(forward / strike);
        else {
            final double epsilon = (forward - strike) / strike;
            logM = epsilon - .5 * epsilon * epsilon;
        }
        final double z = (nu / alpha) * sqrtA * logM;
        final double B = 1.0 - 2.0 * rho * z + z * z;
        final double C = oneMinusBeta * oneMinusBeta * logM * logM;
        final double D = logM * logM;
        final double tmp = (Math.sqrt(B) + z - rho) / (1.0 - rho);
        final double xx = Math.log(tmp);
        final double E_1 = (1.0 + D / 24.0 + D * D / 1920.0);
        final double E_2 = (1.0 + C / 24.0 + C * C / 1920.0);
        final double E = E_1 / E_2;
        final double d = 1.0 + expiryTime *
                (minusBeta * (2 - beta) * alpha * alpha / (24.0 * A)
                        + 0.25 * rho * beta * nu * alpha / sqrtA
                        + (2.0 - 3.0 * rho * rho) * (nu * nu / 24.0));

        double multiplier;
        final double m = 10;
        if (Math.abs(z * z) > Constants.QL_EPSILON * m)
            multiplier = z / xx;
        else {
            multiplier = 1.0 - 0.5 * rho * z - (3.0 * rho * rho - 2.0) * z * z / 12.0;
        }
        final double F = alpha * JQuantMath.pow(forward * strike, beta / 2.0);

        return F * E * multiplier * d;
    }

    /**
     * Generic SABR volatility (lognormal or normal) — mirrors C++ v1.42.1
     * {@code unsafeSabrVolatility(strike, forward, T, alpha, beta, nu, rho, volatilityType)}.
     */
    public double unsafeSabrVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho,
            final VolatilityType volatilityType) {
        if (volatilityType == VolatilityType.Normal) {
            return unsafeSabrNormalVolatility(strike, forward, expiryTime, alpha, beta, nu, rho);
        } else {
            return unsafeSabrLogNormalVolatility(strike, forward, expiryTime, alpha, beta, nu, rho);
        }
    }

    /**
     * checks that the parameters are valid; specifically,
     * <ol>
     * <li><code>alpha</code> > 0.0</li>
     * <li><code>beta</code> >= 0.0 && <=1.0</li>
     * <li><code>nu</code> >= 0.0</li>
     * <li><code>rho*rho</code> < 1.0 </li>
     * </ol>
     * @param alpha
     * @param beta
     * @param nu
     * @param rho
     */
    public void validateSabrParameters(
            final double alpha,
            final double beta,
            final double nu,
            final double rho) {
        //FIXME don't spent time constructing string until the error is real...
        QL.require(alpha>0.0 , "alpha must be positive"); // TODO: message
        QL.require(beta>=0.0 && beta<=1.0 , "beta must be in (0.0, 1.0)"); // TODO: message
        QL.require(nu>=0.0 , "nu must be non negative"); // TODO: message
        QL.require(rho*rho<1.0 , "rho square must be less than one"); // TODO: message
    }

    /**
     *
     * Computes the S.A.B.R. volatility
     * <p>
     * Checks S.A.B.R. model parameters using {@code #validateSabrParameters(Real, Real, Real, Real)}
     * <p>
     * Checks the terms and conditions;
     * <ol>
     * <li><code>strike</code> > 0.0</li>
     * <li><code>forward</code> > 0.0</li>
     * <li><code>expiryTime</code> >= 0.0</li>
     * </ol>
     *  @param strike
     * @param forward
     * @param expiryTime
     * @param alpha
     * @param beta
     * @param nu
     * @param rho
     * @return
     *
     * @see #unsafeSabrVolatility(Rate, Rate, Time, Real, Real, Real, Real)
     * @see #validateSabrParameters(Real, Real, Real, Real)
     */
    public double sabrVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho) {
        return sabrVolatility(strike, forward, expiryTime, alpha, beta, nu, rho,
                VolatilityType.ShiftedLognormal);
    }

    /**
     * Generic SABR volatility with explicit {@code VolatilityType} —
     * mirrors C++ v1.42.1 {@code sabrVolatility(strike, ..., volatilityType)}
     * (sabr.cpp lines 163-180).
     */
    public double sabrVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho,
            final VolatilityType volatilityType) {
        QL.require(strike > 0.0, "strike must be positive: " + strike + " not allowed");
        QL.require(forward > 0.0, "at the money forward rate must be positive: " + forward + " not allowed");
        QL.require(expiryTime >= 0.0, "expiry time must be non-negative: " + expiryTime + " not allowed");
        validateSabrParameters(alpha, beta, nu, rho);
        return unsafeSabrVolatility(strike, forward, expiryTime, alpha, beta, nu, rho, volatilityType);
    }

    /**
     * Shifted SABR volatility — mirrors C++ v1.42.1 {@code shiftedSabrVolatility}
     * (sabr.cpp lines 182-200). Validates inputs and dispatches.
     */
    public double shiftedSabrVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho,
            final double shift,
            final VolatilityType volatilityType) {
        QL.require(strike + shift > 0.0,
                "strike+shift must be positive: " + strike + "+" + shift + " not allowed");
        QL.require(forward + shift > 0.0,
                "at the money forward rate + shift must be positive: " + forward + " " + shift + " not allowed");
        QL.require(expiryTime >= 0.0,
                "expiry time must be non-negative: " + expiryTime + " not allowed");
        validateSabrParameters(alpha, beta, nu, rho);
        return unsafeShiftedSabrVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho, shift, volatilityType);
    }

    /**
     * Convenience overload: shifted SABR with default ShiftedLognormal type.
     */
    public double shiftedSabrVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho,
            final double shift) {
        return shiftedSabrVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho, shift, VolatilityType.ShiftedLognormal);
    }

    /**
     * Floc'h-Kennedy SABR expansion — mirrors C++ v1.42.1
     * {@code sabrFlochKennedyVolatility} (sabr.cpp lines 202-267).
     *
     * <p>Reference: Fabien Le Floc'h and Gary Kennedy,
     * "Explicit SABR Calibration through Simple Expansions",
     * <a href="https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2467231">SSRN 2467231</a>.
     *
     * <p>Uses a Taylor expansion around {@code k=F} when {@code |F/k - 1| < 0.0025},
     * otherwise the omega0 expansion.
     */
    public double sabrFlochKennedyVolatility(
            final double strike,
            final double forward,
            final double expiryTime,
            final double alpha,
            final double beta,
            final double nu,
            final double rho) {
        return new SabrFlochKennedyVolatilityImpl(forward, alpha, beta, nu, rho, expiryTime)
                .evaluate(strike);
    }

    /**
     * Inner functor mirroring C++ anonymous namespace {@code SabrFlochKennedyVolatility}
     * (sabr.cpp lines 202-253).
     */
    private static final class SabrFlochKennedyVolatilityImpl {
        private final double F, alpha, beta, nu, rho, t;

        SabrFlochKennedyVolatilityImpl(final double F, final double alpha, final double beta,
                final double nu, final double rho, final double t) {
            this.F = F; this.alpha = alpha; this.beta = beta;
            this.nu = nu; this.rho = rho; this.t = t;
        }

        double y(final double k) {
            return -1.0 / (1.0 - beta) * (Math.pow(F, 1 - beta) - Math.pow(k, 1 - beta));
        }

        double Dint(final double k) {
            final double yk = y(k);
            return 1 / nu * Math.log(
                    (Math.sqrt(1 + 2 * rho * nu / alpha * yk + squared(nu / alpha * yk))
                            - rho - nu / alpha * yk) / (1 - rho));
        }

        double D(final double k) {
            final double yk = y(k);
            return Math.sqrt(alpha * alpha + 2 * alpha * rho * nu * yk
                    + squared(nu * yk)) * Math.pow(k, beta);
        }

        double omega0(final double k) {
            return Math.log(F / k) / Dint(k);
        }

        double evaluate(final double k) {
            final double m = F / k;
            if (m > 1.0025 || m < 0.9975) {
                final double dInt = Dint(k);
                return omega0(k) * (1 + 0.25 * rho * nu * alpha *
                        (Math.pow(k, beta) - Math.pow(F, beta)) / (k - F) * t)
                        - omega0(k) / squared(dInt) *
                        (Math.log(omega0(k)) + 0.5 * Math.log((F * k / (D(F) * D(k))))) * t;
            } else {
                return taylorExpansion(k);
            }
        }

        double taylorExpansion(final double k) {
            final double F2 = F * F;
            final double alpha2 = alpha * alpha;
            final double rho2 = rho * rho;
            final double Fbeta = Math.pow(F, beta);
            return
                    (alpha * Math.pow(F, -3 + beta) * (alpha2 * squared(-1 + beta) * Math.pow(F, 2 * beta) * t
                            + 6 * alpha * beta * nu * Math.pow(F, 1 + beta) * rho * t
                            + F2 * (24 + nu * nu * (2 - 3 * rho2) * t))) / 24.0 +
                            (3 * alpha2 * alpha * Math.pow(-1 + beta, 3) * Math.pow(F, 3 * beta) * t
                                    + 3 * alpha2 * (-1 + beta) * (-1 + 5 * beta) * nu * Math.pow(F, 1 + 2 * beta) * rho * t
                                    + nu * F2 * F * rho * (24 + nu * nu * (-4 + 3 * rho2) * t)
                                    + alpha * Math.pow(F, 2 + beta) * (24 * (-1 + beta)
                                            + nu * nu * (2 * (-1 + beta) + 3 * (1 + beta) * rho2) * t)
                            ) / (48. * F2 * F2) * (k - F) +
                            (Math.pow(F, -5 - beta) * (alpha2 * alpha2 * Math.pow(-1 + beta, 3) * (-209 + 119 * beta) * Math.pow(F, 4 * beta) * t
                                    + 30 * alpha2 * alpha * (-1 + beta) * (9 + beta * (-37 + 18 * beta)) * nu * Math.pow(F, 1 + 3 * beta) * rho * t
                                    - 30 * alpha * nu * Math.pow(F, 3 + beta) * rho * (24 + nu * nu * (-4 * (1 + beta) + 3 * (1 + 2 * beta) * rho2) * t)
                                    + 10 * alpha2 * Math.pow(F, 2 + 2 * beta) * (24 * (-4 + beta) * (-1 + beta)
                                            + nu * nu * (2 * (-1 + beta) * (-7 + 4 * beta) + 3 * (-4 + beta * (-7 + 5 * beta)) * rho2) * t)
                                    + nu * nu * F2 * F2 * (480 - 720 * rho2 + nu * nu * (-64 + 75 * rho2 * (4 - 3 * rho2)) * t))
                            ) / (2880 * alpha) * (k - F) * (k - F);
        }
    }

    private static double squared(final double x) {
        return x * x;
    }
}

