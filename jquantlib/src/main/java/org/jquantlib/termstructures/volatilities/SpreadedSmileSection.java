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
 Copyright (C) 2006 Mario Pucci

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.Date;

/**
 * SmileSection that adds a constant additive volatility spread to an
 * underlying smile section.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/spreadedsmilesection.{hpp,cpp}}.
 *
 * <p>volatility(k) = underlying.volatility(k) + spread.value().
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *   <li>C++ inherits {@link SmileSection} and overrides every inspector
 *       (minStrike, maxStrike, atmLevel, exerciseDate, exerciseTime,
 *        dayCounter, referenceDate, volatilityType, shift) by forwarding
 *       to {@code underlyingSection_}. Java's {@link SmileSection} keeps
 *       these as fields rather than virtual hooks for several inspectors,
 *       so we mirror the forwarders for inspectors that are virtual
 *       (minStrike, maxStrike, atmLevel) and rely on the base SmileSection
 *       state for the rest. The exercise time of the spreaded section is
 *       initialised from the underlying.
 *   </li>
 *   <li>{@code update()} mirrors C++: notify observers (no recalculation
 *       — the pure additive spread requires none).</li>
 * </ul>
 */
public class SpreadedSmileSection extends SmileSection {

    private final SmileSection underlyingSection_;
    private final Handle<Quote> spread_;

    /**
     * @param underlyingSection base smile to spread; must be non-null
     * @param spread quote handle providing the additive spread (in vol units)
     */
    public SpreadedSmileSection(final SmileSection underlyingSection,
                                final Handle<Quote> spread) {
        super(spreadingExerciseTime(underlyingSection),
                inferDayCounter(underlyingSection),
                spreadingVolatilityType(underlyingSection),
                spreadingShift(underlyingSection));
        this.underlyingSection_ = underlyingSection;
        this.spread_ = spread;
        underlyingSection.addObserver(this);
        spread.addObserver(this);
    }

    private static double spreadingExerciseTime(final SmileSection s) {
        return s.exerciseTime();
    }

    private static DayCounter inferDayCounter(final SmileSection s) {
        return s.dayCounter();
    }

    private static VolatilityType spreadingVolatilityType(final SmileSection s) {
        return s.volatilityType();
    }

    private static double spreadingShift(final SmileSection s) {
        return s.shift();
    }

    //
    // SmileSection overrides — forward to underlying
    //

    @Override
    public double minStrike() {
        return underlyingSection_.minStrike();
    }

    @Override
    public double maxStrike() {
        return underlyingSection_.maxStrike();
    }

    @Override
    public double atmLevel() {
        return underlyingSection_.atmLevel();
    }

    @Override
    public Date exerciseDate() {
        return underlyingSection_.exerciseDate();
    }

    @Override
    public DayCounter dayCounter() {
        return underlyingSection_.dayCounter();
    }

    @Override
    protected double volatilityImpl(final double k) {
        return underlyingSection_.volatility(k) + spread_.currentLink().value();
    }

    /**
     * Mirrors C++ SpreadedSmileSection::update(): notify observers but
     * do NOT propagate to the base SmileSection lazy-recalculation path
     * (the spread is purely additive — no recalc needed).
     */
    @Override
    public void update() {
        notifyObservers();
    }
}
