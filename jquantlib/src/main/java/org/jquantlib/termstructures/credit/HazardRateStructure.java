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
 Copyright (C) 2008 Jose Aparicio
 Copyright (C) 2008 Chris Kenyon
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 StatPro Italia srl
 Copyright (C) 2009 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.credit;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Natural;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * Hazard-rate term structure — Java port of QuantLib v1.42.1 {@code HazardRateStructure}
 * ({@code ql/termstructures/credit/hazardratestructure.{hpp,cpp}}).
 *
 * <p>Abstract adapter on {@link DefaultProbabilityTermStructure}; subclasses
 * implement {@link #hazardRateImpl(double)} and survival probability / default density are derived.
 *
 * <p>Hazard rates are defined with annual frequency and continuous
 * compounding. The default {@link #survivalProbabilityImpl(double)} would use Gauss-Chebyshev quadrature in the C++
 * source; the JQuantLib port intentionally throws {@link UnsupportedOperationException} for the non-overridden
 * numerical-fallback path because the ported subclasses (e.g. {@code FlatHazardRate},
 * {@code InterpolatedHazardRateCurve}) all supply closed-form overrides. A future port can wire in
 * {@link org.jquantlib.math.integrals.GaussianQuadrature} if needed.
 */
public abstract class HazardRateStructure extends DefaultProbabilityTermStructure {

    public HazardRateStructure(final DayCounter dayCounter) {
        super(dayCounter);
    }

    public HazardRateStructure(final DayCounter dayCounter, final List< Handle< Quote > > jumps,
            final List< Date > jumpDates) {
        super(dayCounter, jumps, jumpDates);
    }

    public HazardRateStructure(final Date referenceDate, final Calendar cal, final DayCounter dayCounter) {
        super(referenceDate, cal, dayCounter);
    }

    public HazardRateStructure(final Date referenceDate, final Calendar cal, final DayCounter dayCounter,
            final List< Handle< Quote > > jumps, final List< Date > jumpDates) {
        super(referenceDate, cal, dayCounter, jumps, jumpDates);
    }

    public HazardRateStructure(final @Natural int settlementDays, final Calendar cal, final DayCounter dayCounter) {
        super(settlementDays, cal, dayCounter);
    }

    public HazardRateStructure(final @Natural int settlementDays, final Calendar cal, final DayCounter dayCounter,
            final List< Handle< Quote > > jumps, final List< Date > jumpDates) {
        super(settlementDays, cal, dayCounter, jumps, jumpDates);
    }

    /**
     * Derived classes must implement this. Mirrors C++ pure-virtual {@code hazardRateImpl(Time)}.
     */
    @Override
    protected double hazardRateImpl(final @Time double t) {
        throw new LibraryException("hazardRateImpl() must be implemented by a class derived from HazardRateStructure");
    }

    /**
     * Default implementation: numerical integration of {@code -h(t)} via Gauss-Chebyshev quadrature in C++. Java port
     * currently requires subclasses to override (closed-form path); the numerical fallback is deferred until a derived
     * class needs it.
     */
    @Override
    protected double survivalProbabilityImpl(final @Time double t) {
        throw new LibraryException("default numerical survivalProbabilityImpl(Time) not yet ported; "
                + "derived class must override (Gauss-Chebyshev fallback deferred)");
    }

    /**
     * Inline definition mirrors C++ {@code hazardRateImpl(t) * survivalProbabilityImpl(t)}.
     */
    @Override
    protected double defaultDensityImpl(final @Time double t) {
        return hazardRateImpl(t) * survivalProbabilityImpl(t);
    }
}
