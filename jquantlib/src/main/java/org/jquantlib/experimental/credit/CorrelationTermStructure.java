/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2014 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Natural;
import org.jquantlib.termstructures.AbstractTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Abstract correlation term structure: like a volatility TS, but with a known correlation range and no strike
 * reference.
 *
 * <p>Java port of QuantLib v1.42.1 abstract {@code QuantLib::CorrelationTermStructure}
 * ({@code ql/experimental/credit/correlationstructure.{hpp,cpp}}).
 *
 * <p>Derived correlations TS may have elements with arbitrary
 * dimensions; this base class doesn't commit to a particular layout (number, matrix). The {@link #correlationSize} is
 * supplied by the concrete subclass.
 *
 * <p>Phase 4m foundation.
 */
public abstract class CorrelationTermStructure extends AbstractTermStructure {

    private final BusinessDayConvention bdc;

    public CorrelationTermStructure(final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc) {
        super(dc);
        // Mirrors C++ TermStructure(dc) + assignment of calendar_ post-super.
        this.calendar = cal;
        this.bdc = bdc;
    }

    public CorrelationTermStructure(final Date referenceDate, final Calendar cal, final BusinessDayConvention bdc,
            final DayCounter dc) {
        super(referenceDate, cal, dc);
        this.bdc = bdc;
    }

    public CorrelationTermStructure(final @Natural int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(settlementDays, cal, dc);
        this.bdc = bdc;
    }

    public BusinessDayConvention businessDayConvention() {
        return bdc;
    }

    /** Period/date conversion. Mirrors C++ {@code dateFromTenor(const Period&)}. */
    public Date dateFromTenor(final Period p) {
        return calendar().advance(referenceDate(), p, businessDayConvention());
    }

    /** The size of the squared correlation. */
    public abstract int correlationSize();
}
