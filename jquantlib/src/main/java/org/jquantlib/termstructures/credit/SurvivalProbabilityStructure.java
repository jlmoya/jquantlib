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
 * Survival-probability term structure — Java port of QuantLib v1.42.1 {@code SurvivalProbabilityStructure}
 * ({@code ql/termstructures/credit/survivalprobabilitystructure.{hpp,cpp}}).
 *
 * <p>Abstract adapter on {@link DefaultProbabilityTermStructure}; subclasses
 * implement {@link #survivalProbabilityImpl(double)} and the default density is derived via numerical differentiation
 * ({@code (S(t-dt) - S(t+dt)) / (2 dt)} with {@code dt = 1e-4}).
 */
public abstract class SurvivalProbabilityStructure extends DefaultProbabilityTermStructure {

    public SurvivalProbabilityStructure(final DayCounter dayCounter) {
        super(dayCounter);
    }

    public SurvivalProbabilityStructure(final DayCounter dayCounter, final List< Handle< Quote > > jumps,
            final List< Date > jumpDates) {
        super(dayCounter, jumps, jumpDates);
    }

    public SurvivalProbabilityStructure(final Date referenceDate, final Calendar cal, final DayCounter dayCounter) {
        super(referenceDate, cal, dayCounter);
    }

    public SurvivalProbabilityStructure(final Date referenceDate, final Calendar cal, final DayCounter dayCounter,
            final List< Handle< Quote > > jumps, final List< Date > jumpDates) {
        super(referenceDate, cal, dayCounter, jumps, jumpDates);
    }

    public SurvivalProbabilityStructure(final @Natural int settlementDays, final Calendar cal,
            final DayCounter dayCounter) {
        super(settlementDays, cal, dayCounter);
    }

    public SurvivalProbabilityStructure(final @Natural int settlementDays, final Calendar cal,
            final DayCounter dayCounter, final List< Handle< Quote > > jumps, final List< Date > jumpDates) {
        super(settlementDays, cal, dayCounter, jumps, jumpDates);
    }

    /**
     * Subclasses must implement. Mirrors C++ pure-virtual override (declared {@code = 0} in
     * {@code DefaultProbabilityTermStructure}).
     */
    @Override
    protected double survivalProbabilityImpl(final @Time double t) {
        throw new LibraryException(
                "survivalProbabilityImpl() must be implemented by a class derived from SurvivalProbabilityStructure");
    }

    /**
     * Numerical differentiation mirrors C++ {@code (p1 - p2) / (t2 - t1)} with {@code dt = 1e-4}.
     */
    @Override
    protected double defaultDensityImpl(final @Time double t) {
        final double dt = 1.0e-4;
        final double t1 = Math.max(t - dt, 0.0);
        final double t2 = t + dt;
        final double p1 = survivalProbabilityImpl(t1);
        final double p2 = survivalProbabilityImpl(t2);
        return (p1 - p2) / (t2 - t1);
    }
}
