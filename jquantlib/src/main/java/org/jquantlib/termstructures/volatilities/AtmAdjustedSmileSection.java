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
 Copyright (C) 2013 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.volatilities;

import org.jquantlib.instruments.Option;
import org.jquantlib.math.Constants;

/**
 * Smile section that allows for alternate specification of the ATM level and
 * recenters the source volatility accordingly.
 *
 * <p>Port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/atmadjustedsmilesection.{hpp,cpp}} —
 * a thin wrapper around an underlying {@link SmileSection} that:
 * <ul>
 *   <li>overrides {@link #atmLevel()} with a caller-supplied value, and</li>
 *   <li>optionally recenters smile queries by translating the requested
 *       strike before forwarding to the source: when {@code recenterSmile}
 *       is true and both {@code atm} and {@code source.atmLevel()} are
 *       valid, queries at strike {@code k} are evaluated on the source at
 *       {@code k + (source.atmLevel() - f)}. This shifts the source's
 *       smile so that the source ATM lines up with the requested ATM.</li>
 * </ul>
 *
 * <p>Relationship to {@link AtmSmileSection}: {@code AtmSmileSection} (C++
 * {@code AtmSmileSection}) is the simpler sibling — it only overrides the
 * ATM value but does not translate strikes. {@code AtmAdjustedSmileSection}
 * adds the strike-recentering option used in calibration workflows where
 * the calibrator needs the source smile evaluated relative to an
 * alternative ATM level.
 *
 * <p>All non-ATM inspectors and price/digital/vega/density evaluations
 * forward to the underlying source so this wrapper introduces no new
 * numerical computation; it is a coordinate-translation layer only.
 *
 * <p>L2-C Phase 2 forward closure (audit ID
 * {@code AtmAdjustedSmileSection}).
 */
public class AtmAdjustedSmileSection extends SmileSection {

    private final SmileSection source_;
    /** Effective ATM level (override or source's). */
    private final double f_;
    /** Strike adjustment {@code (source.atmLevel - f_)}; zero when not recentering. */
    private final double adjustment_;

    /**
     * Convenience constructor — inherits the source ATM, no recentering.
     * Mirrors C++ default-argument behaviour
     * {@code AtmAdjustedSmileSection(source, Null<Real>(), false)}.
     */
    public AtmAdjustedSmileSection(final SmileSection source) {
        this(source, Constants.NULL_REAL, false);
    }

    /**
     * Override ATM level; no recentering (default).
     */
    public AtmAdjustedSmileSection(final SmileSection source, final double atm) {
        this(source, atm, false);
    }

    /**
     * Full constructor.
     *
     * @param source        underlying smile section; non-null
     * @param atm           ATM override; {@link Constants#NULL_REAL} or
     *                      {@link Double#NaN} ⇒ inherit {@code source.atmLevel()}
     * @param recenterSmile when true, translate strikes so that
     *                      {@code source.atmLevel()} maps to {@code atm}
     */
    public AtmAdjustedSmileSection(final SmileSection source, final double atm, final boolean recenterSmile) {
        // Mirror C++ ctor: SmileSection(*source) copies exerciseTime, dayCounter,
        // volatilityType, shift from the source.
        super(source.exerciseTime(), source.dayCounter(), source.volatilityType(), source.shift());

        this.source_ = source;
        final boolean atmNull = isNull(atm);
        final double effF = atmNull ? source_.atmLevel() : atm;
        this.f_ = effF;

        final double srcAtm = source_.atmLevel();
        if (recenterSmile && !isNull(effF) && !isNull(srcAtm)) {
            this.adjustment_ = srcAtm - effF;
        } else {
            this.adjustment_ = 0.0;
        }
    }

    private static boolean isNull(final double x) {
        // QuantLib uses Null<Real>() == max double; tolerate NaN too for
        // Java idiom (Double.NaN is sometimes used as null sentinel).
        return Double.isNaN(x) || x == Constants.NULL_REAL;
    }

    private double adjustedStrike(final double strike) {
        return strike + adjustment_;
    }

    // ------------------------------------------------------------------
    // SmileSection overrides — forward to source (with adjusted strike)
    // ------------------------------------------------------------------

    @Override
    public double minStrike() {
        return source_.minStrike();
    }

    @Override
    public double maxStrike() {
        return source_.maxStrike();
    }

    /** Returns the effective ATM level (override or source's). */
    @Override
    public double atmLevel() {
        return f_;
    }

    /**
     * Mirrors C++ {@code optionPrice(strike, type, discount)} — forwards to
     * source at the adjusted strike.
     */
    @Override
    public double optionPrice(final double strike, final Option.Type type, final double discount) {
        return source_.optionPrice(adjustedStrike(strike), type, discount);
    }

    /**
     * Mirrors C++ {@code digitalOptionPrice(strike, type, discount, gap)} —
     * forwards to source at the adjusted strike.
     */
    @Override
    public double digitalOptionPrice(final double strike, final Option.Type type, final double discount,
            final double gap) {
        return source_.digitalOptionPrice(adjustedStrike(strike), type, discount, gap);
    }

    /**
     * Forwards to source's variance at the adjusted strike (mirrors C++
     * {@code varianceImpl}).
     */
    @Override
    protected double varianceImpl(final double strike) {
        return source_.variance(adjustedStrike(strike));
    }

    /**
     * Forwards to source's volatility at the adjusted strike (mirrors C++
     * {@code volatilityImpl}).
     */
    @Override
    protected double volatilityImpl(final double strike) {
        return source_.volatility(adjustedStrike(strike));
    }

    // ------------------------------------------------------------------
    // Inspectors
    // ------------------------------------------------------------------

    /** Underlying smile section. */
    public SmileSection source() {
        return source_;
    }

    /** Strike adjustment {@code (source.atmLevel - f_)}; zero when not recentering. */
    public double adjustment() {
        return adjustment_;
    }
}
