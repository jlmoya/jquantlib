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
 Copyright (C) 2008 Chris Kenyon
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.credit;

import java.util.List;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Natural;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Default-density term structure — Java port of QuantLib v1.42.1
 * {@code DefaultDensityStructure}
 * ({@code ql/termstructures/credit/defaultdensitystructure.{hpp,cpp}}).
 *
 * <p>Abstract adapter on {@link DefaultProbabilityTermStructure}; subclasses
 * implement {@link #defaultDensityImpl(double)} and survival probability /
 * default density are derived.
 *
 * <p>The C++ default {@code survivalProbabilityImpl(Time)} uses
 * Gauss-Chebyshev quadrature
 * {@code S(t) = max(1 - integral_{0}^{t} p(tau) dtau, 0)}; the JQuantLib
 * port currently throws on the non-overridden numerical-fallback path —
 * the ported subclasses (e.g. {@code InterpolatedDefaultDensityCurve})
 * supply closed-form overrides.
 */
public abstract class DefaultDensityStructure extends DefaultProbabilityTermStructure {

    public DefaultDensityStructure(final DayCounter dayCounter) {
        super(dayCounter);
    }

    public DefaultDensityStructure(
            final DayCounter dayCounter,
            final List<Handle<Quote>> jumps,
            final List<Date> jumpDates) {
        super(dayCounter, jumps, jumpDates);
    }

    public DefaultDensityStructure(
            final Date referenceDate,
            final Calendar cal,
            final DayCounter dayCounter) {
        super(referenceDate, cal, dayCounter);
    }

    public DefaultDensityStructure(
            final Date referenceDate,
            final Calendar cal,
            final DayCounter dayCounter,
            final List<Handle<Quote>> jumps,
            final List<Date> jumpDates) {
        super(referenceDate, cal, dayCounter, jumps, jumpDates);
    }

    public DefaultDensityStructure(
            final @Natural int settlementDays,
            final Calendar cal,
            final DayCounter dayCounter) {
        super(settlementDays, cal, dayCounter);
    }

    public DefaultDensityStructure(
            final @Natural int settlementDays,
            final Calendar cal,
            final DayCounter dayCounter,
            final List<Handle<Quote>> jumps,
            final List<Date> jumpDates) {
        super(settlementDays, cal, dayCounter, jumps, jumpDates);
    }

    /**
     * Subclasses must implement. Mirrors C++ pure-virtual override.
     */
    @Override
    protected double defaultDensityImpl(final @Time double t) {
        throw new LibraryException(
                "defaultDensityImpl() must be implemented by a class derived from DefaultDensityStructure");
    }

    /**
     * Default implementation: Gauss-Chebyshev quadrature in C++. The
     * JQuantLib port requires subclasses to override; numerical fallback
     * deferred until needed.
     */
    @Override
    protected double survivalProbabilityImpl(final @Time double t) {
        throw new LibraryException(
                "default numerical survivalProbabilityImpl(Time) not yet ported; " +
                "derived class must override (Gauss-Chebyshev fallback deferred)");
    }
}
