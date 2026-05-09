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
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Black volatility (smile) surface.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/blackvolsurface.{hpp,cpp}}. Abstract
 * base for volatility surfaces; concrete subclasses implement
 * {@link #smileSectionImpl(double)} and the surface returns smile sections
 * along the time axis.
 *
 * <p>Volatilities are expressed on an annual basis.
 */
public abstract class BlackVolSurface extends BlackAtmVolCurve {

    public BlackVolSurface(final BusinessDayConvention bdc, final DayCounter dc) {
        super(bdc, dc);
    }

    public BlackVolSurface(final Date referenceDate, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(referenceDate, cal, bdc, dc);
    }

    public BlackVolSurface(final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
    }

    // -------------------------------------------------------------------
    // Smile section accessors
    // -------------------------------------------------------------------

    /** Smile for a given option tenor. */
    public SmileSection smileSection(final Period p, final boolean extrapolate) {
        return smileSection(optionDateFromTenor(p), extrapolate);
    }

    /** Smile for a given option date. */
    public SmileSection smileSection(final Date d, final boolean extrapolate) {
        return smileSection(timeFromReference(d), extrapolate);
    }

    /** Smile for a given time. */
    public SmileSection smileSection(final double t, final boolean extrapolate) {
        checkRange(t, extrapolate);
        return smileSectionImpl(t);
    }

    // -------------------------------------------------------------------
    // BlackAtmVolCurve interface
    // -------------------------------------------------------------------

    @Override
    protected double atmVarianceImpl(final double t) {
        final SmileSection s = smileSectionImpl(t);
        return s.variance(s.atmLevel());
    }

    @Override
    protected double atmVolImpl(final double t) {
        final SmileSection s = smileSectionImpl(t);
        return s.volatility(s.atmLevel());
    }

    // -------------------------------------------------------------------
    // Visitability
    // -------------------------------------------------------------------

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<BlackVolSurface> v = (pv != null) ? pv.<BlackVolSurface>visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }

    // -------------------------------------------------------------------
    // Calculations (subclass implements)
    // -------------------------------------------------------------------

    /**
     * Smile section calculation. Called after range check, so the
     * implementation must assume time-extrapolation is allowed.
     */
    protected abstract SmileSection smileSectionImpl(double t);
}
