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
 Copyright (C) 2002, 2003 Ferdinando Ametrano
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.termstructures.volatilities.VolatilityTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Black at-the-money (no-smile) volatility curve.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/blackatmvolcurve.{hpp,cpp}}. Abstract base for ATM-only curves; concrete subclasses
 * implement {@link #atmVolImpl(double)} and {@link #atmVarianceImpl(double)}.
 *
 * <p>Volatilities are assumed to be expressed on an annual basis.
 */
public abstract class BlackAtmVolCurve extends VolatilityTermStructure {

    /** Default constructor — subclass must override {@code referenceDate()}. */
    public BlackAtmVolCurve(final BusinessDayConvention bdc, final DayCounter dc) {
        super(new Calendar(), bdc, dc);
    }

    /** Fixed reference date. */
    public BlackAtmVolCurve(final Date referenceDate, final Calendar cal, final BusinessDayConvention bdc,
            final DayCounter dc) {
        super(referenceDate, cal, bdc, dc);
    }

    /** Floating reference date based on settlement days from evaluation date. */
    public BlackAtmVolCurve(final int settlementDays, final Calendar cal, final BusinessDayConvention bdc,
            final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
    }

    // -------------------------------------------------------------------
    // Black at-the-money spot volatility (mirrors C++ public API)
    // -------------------------------------------------------------------

    /** Spot ATM volatility for an option tenor. */
    public double atmVol(final Period optionTenor, final boolean extrapolate) {
        return atmVol(optionDateFromTenor(optionTenor), extrapolate);
    }

    /** Spot ATM volatility for an option date. */
    public double atmVol(final Date d, final boolean extrapolate) {
        return atmVol(timeFromReference(d), extrapolate);
    }

    /** Spot ATM volatility for a time. */
    public double atmVol(final double t, final boolean extrapolate) {
        checkRange(t, extrapolate);
        return atmVolImpl(t);
    }

    /** Spot ATM variance for an option tenor. */
    public double atmVariance(final Period optionTenor, final boolean extrapolate) {
        return atmVariance(optionDateFromTenor(optionTenor), extrapolate);
    }

    /** Spot ATM variance for an option date. */
    public double atmVariance(final Date d, final boolean extrapolate) {
        return atmVariance(timeFromReference(d), extrapolate);
    }

    /** Spot ATM variance for a time. */
    public double atmVariance(final double t, final boolean extrapolate) {
        checkRange(t, extrapolate);
        return atmVarianceImpl(t);
    }

    // -------------------------------------------------------------------
    // Visitability
    // -------------------------------------------------------------------

    /**
     * Polymorphic visitor entry point. Mirrors C++ {@code accept(AcyclicVisitor&)}.
     */
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< BlackAtmVolCurve > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        }
    }

    // -------------------------------------------------------------------
    // Calculations (subclass implements)
    // -------------------------------------------------------------------

    /** Spot at-the-money variance calculation (called after range check). */
    protected abstract double atmVarianceImpl(double t);

    /** Spot at-the-money volatility calculation (called after range check). */
    protected abstract double atmVolImpl(double t);
}
