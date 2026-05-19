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
 Copyright (C) 2009 Roland Lichters
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.credit.HazardRateStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Default-probability structure with a multiplicative spread on hazard rates.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::FactorSpreadedHazardRateCurve}
 * ({@code ql/experimental/credit/factorspreadedhazardratecurve.hpp}).
 *
 * <p>This term structure remains linked to the original structure. Hazard
 * rate at time {@code t} is {@code original.hazardRate(t, true) * (1 + spread.value())}.
 *
 * <p>Phase 4m foundation.
 */
public class FactorSpreadedHazardRateCurve extends HazardRateStructure {

    private final Handle< DefaultProbabilityTermStructure > originalCurve;
    private final Handle< Quote > spread;

    public FactorSpreadedHazardRateCurve(final Handle< DefaultProbabilityTermStructure > h,
            final Handle< Quote > spread) {
        super(h.currentLink().dayCounter());
        this.originalCurve = h;
        this.spread = spread;
        this.originalCurve.addObserver(this);
        this.spread.addObserver(this);
    }

    @Override
    public DayCounter dayCounter() {
        // null-guard: super() may invoke this before the field is assigned
        return (originalCurve == null) ? super.dayCounter() : originalCurve.currentLink().dayCounter();
    }

    @Override
    public Calendar calendar() {
        return (originalCurve == null) ? super.calendar() : originalCurve.currentLink().calendar();
    }

    @Override
    public Date referenceDate() {
        return (originalCurve == null) ? super.referenceDate() : originalCurve.currentLink().referenceDate();
    }

    @Override
    public Date maxDate() {
        // null-guard for super-constructor invocation: return sentinel until originalCurve is set.
        return (originalCurve == null) ? Date.maxDate() : originalCurve.currentLink().maxDate();
    }

    @Override
    public double maxTime() {
        return (originalCurve == null) ? super.maxTime() : originalCurve.currentLink().maxTime();
    }

    @Override
    protected double hazardRateImpl(final double t) {
        return originalCurve.currentLink().hazardRate(t, true) * (1.0 + spread.currentLink().value());
    }
}
