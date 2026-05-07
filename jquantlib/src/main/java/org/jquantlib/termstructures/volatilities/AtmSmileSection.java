/*
 Copyright (C) 2013 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/
/*
 Java port: JQuantLib migration contributors (Phase 2j.5 Track C.2).
*/

package org.jquantlib.termstructures.volatilities;

/**
 * Smile section that allows for explicit / alternate specification of the ATM level.
 *
 * <p>Thin wrapper around an arbitrary {@link SmileSection} that overrides
 * {@link #atmLevel()} with a caller-supplied value while delegating all
 * other queries ({@code volatility}, {@code variance}, {@code minStrike},
 * {@code maxStrike}, etc.) to the underlying source section.
 *
 * <p>Mirrors C++ QuantLib v1.42.1 {@code AtmSmileSection}
 * (atmsmilesection.hpp/.cpp).  Used by {@code MarkovFunctional::updateSmiles()}.
 *
 * <p>Phase 2j.5 Track C.2.
 *
 * @author JQuantLib migration contributors
 */
public class AtmSmileSection extends SmileSection {

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    private final SmileSection source_;
    private final double f_;          // effective ATM level

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /**
     * Wraps {@code source}; uses source's own {@link SmileSection#atmLevel()}
     * as the effective ATM.
     *
     * <p>Mirrors {@code AtmSmileSection(source, Null<Real>())}.
     *
     * @param source underlying smile section; must not be {@code null}
     */
    public AtmSmileSection(final SmileSection source) {
        this(source, Double.NaN);
    }

    /**
     * Wraps {@code source}; uses {@code atm} as the effective ATM level when
     * it is not {@link Double#NaN}, otherwise falls back to
     * {@code source.atmLevel()}.
     *
     * <p>Mirrors {@code AtmSmileSection(source, atm)} where {@code Null<Real>()}
     * maps to {@code Double.NaN} in Java.
     *
     * @param source underlying smile section; must not be {@code null}
     * @param atm    explicit ATM override, or {@code Double.NaN} to inherit
     */
    public AtmSmileSection(final SmileSection source, final double atm) {
        // Initialise base class from source's time-based fields.
        // C++: SmileSection(*source) — copies exerciseTime, dayCounter, type, shift.
        super(source.exerciseTime(),
              source.dayCounter(),
              source.volatilityType(),
              source.shift());

        source_ = source;
        f_ = Double.isNaN(atm) ? source_.atmLevel() : atm;
    }

    // ------------------------------------------------------------------
    // SmileSection overrides — delegate to source except for atmLevel
    // ------------------------------------------------------------------

    /** Returns the effective ATM level (override or source's). */
    @Override
    public double atmLevel() {
        return f_;
    }

    /** Delegates to source. */
    @Override
    public double minStrike() {
        return source_.minStrike();
    }

    /** Delegates to source. */
    @Override
    public double maxStrike() {
        return source_.maxStrike();
    }

    /**
     * Delegates to source.
     *
     * <p>C++: {@code volatilityImpl(strike)} calls {@code source_->volatility(strike)},
     * which (unlike {@code volatilityImpl}) handles {@code Null<Real>()} by substituting
     * {@code atmLevel()}.  In Java, {@link SmileSection#volatility(double)} does the same
     * substitution before calling {@link #volatilityImpl(double)}, so mirroring via
     * {@code source_.volatility(strike)} is correct.
     */
    @Override
    protected double volatilityImpl(final double strike) {
        return source_.volatility(strike);
    }

    /**
     * Delegates to source.
     *
     * <p>C++ {@code varianceImpl(strike)} calls {@code source_->variance(strike)}.
     * Java: mirrors the same delegation.
     */
    @Override
    protected double varianceImpl(final double strike) {
        return source_.variance(strike);
    }
}
