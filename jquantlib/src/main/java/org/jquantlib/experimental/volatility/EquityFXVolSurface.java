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

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Equity / FX volatility (smile) surface.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/equityfxvolsurface.{hpp,cpp}}.
 * Concrete classes provide the actual smile-section implementation;
 * this base layer adds the forward (at-the-money) volatility and variance
 * accessors used in delta-strike conventions.
 *
 * <p>It's only in absence of smile that the concept of (at-the-money)
 * forward volatility makes sense.
 */
public abstract class EquityFXVolSurface extends BlackVolSurface {

    public EquityFXVolSurface(final BusinessDayConvention bdc, final DayCounter dc) {
        super(bdc, dc);
    }

    public EquityFXVolSurface(final Date referenceDate, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(referenceDate, cal, bdc, dc);
    }

    public EquityFXVolSurface(final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
    }

    /** Forward (at-the-money) volatility from {@code date1} to {@code date2}. */
    public double atmForwardVol(final Date date1, final Date date2,
            final boolean extrapolate) {
        QL.require(date1.lt(date2), "wrong dates");
        final double t1 = timeFromReference(date1);
        final double t2 = timeFromReference(date2);
        return atmForwardVol(t1, t2, extrapolate);
    }

    /** Forward (at-the-money) volatility from {@code time1} to {@code time2}. */
    public double atmForwardVol(final double time1, final double time2,
            final boolean extrapolate) {
        final double fwdVariance = atmForwardVariance(time1, time2, extrapolate);
        final double t = time2 - time1;
        return Math.sqrt(fwdVariance / t);
    }

    /** Forward (at-the-money) variance between two dates. */
    public double atmForwardVariance(final Date date1, final Date date2,
            final boolean extrapolate) {
        QL.require(date1.lt(date2), "wrong dates");
        final double t1 = timeFromReference(date1);
        final double t2 = timeFromReference(date2);
        return atmForwardVariance(t1, t2, extrapolate);
    }

    /** Forward (at-the-money) variance between two times. */
    public double atmForwardVariance(final double time1, final double time2,
            final boolean extrapolate) {
        QL.require(time1 < time2, "wrong times");
        final double var1 = atmVariance(time1, extrapolate);
        final double var2 = atmVariance(time2, extrapolate);
        QL.ensure(var1 < var2, "non-increasing variances");
        return var2 - var1;
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<EquityFXVolSurface> v = (pv != null) ? pv.<EquityFXVolSurface>visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
