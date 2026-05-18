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
*/

/*
 Copyright (C) 2006 Mario Pucci
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.volatilities;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.time.Date;

/**
 * SABR smile section — Java port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/sabrsmilesection.{hpp,cpp}}.
 *
 * <p>Wraps a SABR (alpha, beta, nu, rho) parameter set and exposes it as
 * a {@link SmileSection}. Mirrors C++ behavior:
 * <ul>
 *   <li>{@code minStrike() == -shift_}</li>
 *   <li>{@code maxStrike() == Double.MAX_VALUE} (QL_MAX_REAL)</li>
 *   <li>{@code atmLevel() == forward_}</li>
 *   <li>volatility/variance use {@code unsafeShiftedSabrVolatility},
 *       with strike clamped at {@code max(0.00001 - shift, strike)}.</li>
 * </ul>
 *
 * <p>Two constructor variants mirror C++ — one taking time-to-expiry and
 * day counter (no exercise date); one taking expiry date + reference date
 * + day counter.
 *
 * @author JQuantLib migration contributors
 */
public class SabrSmileSection extends SmileSection {

    /**
     * SABR volatility approximation to use when evaluating
     * {@link #volatilityImpl(double)} and {@link #varianceImpl(double)}.
     *
     * <p>Mirrors the {@code SabrSmileSection::Approximation} enum that C++
     * QuantLib v1.42.1+ uses to switch between the classical Hagan
     * (2002) closed-form and the Floc'h-Kennedy expansion
     * (sabr.{hpp,cpp} {@code sabrFlochKennedyVolatility}). Floc'h-Kennedy
     * is preferred at short expiries / near-ATM strikes where the Hagan
     * formula develops a small kink.
     */
    public enum Approximation {
        /** Hagan-Kumar-Lesniewski-Woodward 2002 closed-form (default). */
        Hagan,
        /** Floc'h-Kennedy explicit expansion (closed-form, short-expiry safe). */
        FlochKennedy
    }

    private final double alpha_;
    private final double beta_;
    private final double nu_;
    private final double rho_;
    private final double forward_;
    /** Stored shift (parent class also has it; kept here for clarity). */
    private final double shift_;
    private final Approximation approximation_;
    private final Sabr sabr_ = new Sabr();

    /**
     * Time-to-expiry constructor — mirrors C++ first ctor (sabrsmilesection.cpp lines 27-36).
     *
     * @param timeToExpiry  T (positive)
     * @param forward       forward rate
     * @param sabrParameters length-4 array {alpha, beta, nu, rho}
     * @param shift         displacement (default 0)
     * @param volatilityType ShiftedLognormal or Normal (default ShiftedLognormal)
     */
    public SabrSmileSection(
            final double timeToExpiry,
            final double forward,
            final double[] sabrParameters,
            final double shift,
            final VolatilityType volatilityType) {
        this(timeToExpiry, forward, sabrParameters, shift, volatilityType,
                Approximation.Hagan);
    }

    /**
     * Full time-to-expiry constructor accepting an {@link Approximation}
     * — mirrors C++ {@code SabrSmileSection(timeToExpiry, ..., approximation)}.
     */
    public SabrSmileSection(
            final double timeToExpiry,
            final double forward,
            final double[] sabrParameters,
            final double shift,
            final VolatilityType volatilityType,
            final Approximation approximation) {
        super(timeToExpiry, null /* DayCounter() default */, volatilityType, shift);
        this.forward_ = forward;
        this.shift_ = shift;
        this.approximation_ = approximation == null ? Approximation.Hagan : approximation;
        QL.require(sabrParameters != null && sabrParameters.length == 4,
                "sabrParameters must be length 4 (alpha, beta, nu, rho)");
        this.alpha_ = sabrParameters[0];
        this.beta_  = sabrParameters[1];
        this.nu_    = sabrParameters[2];
        this.rho_   = sabrParameters[3];

        QL.require(forward + shift > 0.0,
                "at the money forward rate + shift must be positive: forward="
                + forward + " shift=" + shift + " not allowed");
        sabr_.validateSabrParameters(alpha_, beta_, nu_, rho_);
        if (this.approximation_ == Approximation.FlochKennedy) {
            // Floc'h-Kennedy uses log(F/k) and pow(k, beta); the C++ formula
            // is undefined for non-positive forward / strike (no shift handling).
            QL.require(shift == 0.0,
                    "FlochKennedy approximation does not support shifted SABR (shift must be 0): shift="
                            + shift);
            QL.require(volatilityType != VolatilityType.Normal,
                    "FlochKennedy approximation only supports lognormal volatility");
        }
    }

    /**
     * Convenience overload: shift=0, ShiftedLognormal.
     */
    public SabrSmileSection(
            final double timeToExpiry,
            final double forward,
            final double[] sabrParameters) {
        this(timeToExpiry, forward, sabrParameters, 0.0, VolatilityType.ShiftedLognormal);
    }

    /**
     * Convenience overload: shift=0, ShiftedLognormal, explicit approximation.
     */
    public SabrSmileSection(
            final double timeToExpiry,
            final double forward,
            final double[] sabrParameters,
            final Approximation approximation) {
        this(timeToExpiry, forward, sabrParameters, 0.0,
                VolatilityType.ShiftedLognormal, approximation);
    }

    /**
     * Date-based constructor — mirrors C++ second ctor (sabrsmilesection.cpp lines 38-48).
     *
     * @param d              expiry date
     * @param forward        forward rate
     * @param sabrParameters length-4 array {alpha, beta, nu, rho}
     * @param referenceDate  reference date (null/empty → uses Settings.evaluationDate)
     * @param dc             day counter (Actual/365 if null)
     * @param shift          displacement
     * @param volatilityType ShiftedLognormal or Normal
     */
    public SabrSmileSection(
            final Date d,
            final double forward,
            final double[] sabrParameters,
            final Date referenceDate,
            final DayCounter dc,
            final double shift,
            final VolatilityType volatilityType) {
        this(d, forward, sabrParameters, referenceDate, dc, shift, volatilityType,
                Approximation.Hagan);
    }

    /**
     * Full date-based constructor accepting an {@link Approximation} —
     * mirrors C++ {@code SabrSmileSection(d, ..., approximation)}.
     */
    public SabrSmileSection(
            final Date d,
            final double forward,
            final double[] sabrParameters,
            final Date referenceDate,
            final DayCounter dc,
            final double shift,
            final VolatilityType volatilityType,
            final Approximation approximation) {
        super(d, dc == null ? new Actual365Fixed() : dc,
                referenceDate == null ? new Date() : referenceDate,
                volatilityType, shift);
        this.forward_ = forward;
        this.shift_ = shift;
        this.approximation_ = approximation == null ? Approximation.Hagan : approximation;
        QL.require(sabrParameters != null && sabrParameters.length == 4,
                "sabrParameters must be length 4 (alpha, beta, nu, rho)");
        this.alpha_ = sabrParameters[0];
        this.beta_  = sabrParameters[1];
        this.nu_    = sabrParameters[2];
        this.rho_   = sabrParameters[3];

        QL.require(forward + shift > 0.0,
                "at the money forward rate + shift must be positive: forward="
                + forward + " shift=" + shift + " not allowed");
        sabr_.validateSabrParameters(alpha_, beta_, nu_, rho_);
        if (this.approximation_ == Approximation.FlochKennedy) {
            QL.require(shift == 0.0,
                    "FlochKennedy approximation does not support shifted SABR (shift must be 0): shift="
                            + shift);
            QL.require(volatilityType != VolatilityType.Normal,
                    "FlochKennedy approximation only supports lognormal volatility");
        }
    }

    /**
     * Convenience overload: dc=Actual/365, shift=0, ShiftedLognormal.
     */
    public SabrSmileSection(
            final Date d,
            final double forward,
            final double[] sabrParameters) {
        this(d, forward, sabrParameters, new Date(), new Actual365Fixed(),
                0.0, VolatilityType.ShiftedLognormal);
    }

    @Override
    public double minStrike() {
        return -shift_;
    }

    @Override
    public double maxStrike() {
        return Double.MAX_VALUE;
    }

    @Override
    public double atmLevel() {
        return forward_;
    }

    public double alpha() { return alpha_; }
    public double beta()  { return beta_; }
    public double nu()    { return nu_; }
    public double rho()   { return rho_; }

    /** Returns the SABR approximation in use (Hagan or FlochKennedy). */
    public Approximation approximation() { return approximation_; }

    @Override
    protected double volatilityImpl(final double strikeIn) {
        // Mirror C++: strike = max(0.00001 - shift, strike)
        final double strike = Math.max(0.00001 - shift(), strikeIn);
        if (approximation_ == Approximation.FlochKennedy) {
            // FlochKennedy is unshifted lognormal — guarded by ctor.
            return sabr_.sabrFlochKennedyVolatility(strike, forward_, exerciseTime(),
                    alpha_, beta_, nu_, rho_);
        }
        return sabr_.unsafeShiftedSabrVolatility(strike, forward_, exerciseTime(),
                alpha_, beta_, nu_, rho_, shift_, volatilityType());
    }

    @Override
    protected double varianceImpl(final double strikeIn) {
        final double strike = Math.max(0.00001 - shift(), strikeIn);
        final double vol;
        if (approximation_ == Approximation.FlochKennedy) {
            vol = sabr_.sabrFlochKennedyVolatility(strike, forward_, exerciseTime(),
                    alpha_, beta_, nu_, rho_);
        } else {
            vol = sabr_.unsafeShiftedSabrVolatility(strike, forward_, exerciseTime(),
                    alpha_, beta_, nu_, rho_, shift_, volatilityType());
        }
        return vol * vol * exerciseTime();
    }
}
