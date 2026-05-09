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

 JQuantLib is based on QuantLib. http://quantlib.org/
*/

/*
 Copyright (C) 2007 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Interest rate volatility (smile) surface.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/interestratevolsurface.{hpp,cpp}}.
 * Concrete subclasses implement {@link BlackVolSurface#smileSectionImpl(double)}
 * for the actual SABR / smile representation.
 */
public abstract class InterestRateVolSurface extends BlackVolSurface {

    protected final InterestRateIndex index_;

    public InterestRateVolSurface(final InterestRateIndex index,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(bdc, dc);
        this.index_ = index;
    }

    public InterestRateVolSurface(final InterestRateIndex index,
            final Date referenceDate, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(referenceDate, cal, bdc, dc);
        this.index_ = index;
    }

    public InterestRateVolSurface(final InterestRateIndex index,
            final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
        this.index_ = index;
    }

    /** Optionlet-style date conversion using the index's fixing calendar. */
    @Override
    public Date optionDateFromTenor(final Period p) {
        // Optionlet-style (mirrors C++ interestratevolsurface.cpp lines 44-51).
        final Date refDate = index_.fixingCalendar().adjust(referenceDate(),
                BusinessDayConvention.Following);
        final Date settlement = index_.valueDate(refDate);
        final Date start = settlement.add(p);
        return index_.fixingDate(start);
    }

    /** Underlying interest rate index. */
    public InterestRateIndex index() {
        return index_;
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<InterestRateVolSurface> v = (pv != null) ? pv.<InterestRateVolSurface>visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
