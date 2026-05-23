/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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
 Copyright (C) 2003-2008 StatPro Italia srl
 Copyright (C) 2009 Ferdinando Ametrano
 Copyright (C) 2019 SoftSolutions! S.r.l.

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.yieldcurves;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.AbstractYieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.util.Pair;

/**
 * Yield term structure based on interpolation of simply-compounded zero rates.
 *
 * <p>Java port of v1.42.1 {@code ql/termstructures/yield/interpolatedsimplezerocurve.hpp}.
 *
 * <p>Discount factor: {@code 1 / (1 + R(t) * t)} where R(t) is the interpolated simply-compounded zero rate.
 * Extrapolation past the last pillar uses flat-forward in the simply-compounded sense, mirroring C++ behaviour.
 *
 * @param <I> Interpolator
 * @see SimpleZeroYield
 */
public class InterpolatedSimpleZeroCurve< I extends Interpolator > extends AbstractYieldTermStructure
        implements Traits.Curve {

    //
    // private fields
    //

    private final Class< I > classI;
    private final Interpolator interpolator;
    private Date[] dates;
    private /*@Time*/ double[] times;

    private Interpolation interpolation;
    private double[] data;

    //
    // public constructors
    //

    public InterpolatedSimpleZeroCurve(final Class< I > classI, final Date[] dates, final double[] yields,
            final DayCounter dc) {
        this(classI, dates, yields, dc, null, null);
    }

    public InterpolatedSimpleZeroCurve(final Class< I > classI, final Date[] dates, final double[] yields,
            final DayCounter dc, final Calendar calendar) {
        this(classI, dates, yields, dc, calendar, null);
    }

    public InterpolatedSimpleZeroCurve(final Class< I > classI, final Date[] dates, final double[] yields,
            final DayCounter dc, final Calendar calendar, final Interpolator interpolator) {
        super(dates[0], calendar == null ? new Calendar() : calendar, dc);

        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;

        QL.require(dates.length != 0, "Dates cannot be empty");
        QL.require(yields.length != 0, "yields cannot be empty");
        QL.require(dates.length == yields.length, "Dates must be the same size as yields");

        this.dates = dates;
        this.data = yields;
        this.times = new double[dates.length];
        times[0] = 0.0;

        for ( int i = 1; i < dates.length; ++i ) {
            QL.require(dates[i].gt(dates[i - 1]), "Dates must be in ascending order");
            times[i] = dc.yearFraction(dates[0], dates[i]);
            QL.require(!Closeness.isClose(times[i], times[i - 1]),
                    "two dates correspond to the same time under this curve's day count convention");
        }

        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
        this.interpolation = this.interpolator.interpolate(new Array(times), new Array(data));
        this.interpolation.update();
    }

    //
    // protected constructors
    //

    protected InterpolatedSimpleZeroCurve(final Class< I > classI, final Date referenceDate, final DayCounter dc) {
        this(classI, referenceDate, dc, null);
    }

    protected InterpolatedSimpleZeroCurve(final Class< I > classI, final Date referenceDate, final DayCounter dc,
            final Interpolator interpolator) {
        super(referenceDate, new Calendar(), dc);
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    protected InterpolatedSimpleZeroCurve(final Class< I > classI, final DayCounter dc) {
        this(classI, dc, null);
    }

    protected InterpolatedSimpleZeroCurve(final Class< I > classI, final DayCounter dc, final Interpolator interpolator) {
        super(dc);
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    protected InterpolatedSimpleZeroCurve(final Class< I > classI, final /*@Natural*/ int settlementDays,
            final Calendar calendar, final DayCounter dc) {
        this(classI, settlementDays, calendar, dc, null);
    }

    protected InterpolatedSimpleZeroCurve(final Class< I > classI, final /*@Natural*/ int settlementDays,
            final Calendar calendar, final DayCounter dc, final Interpolator interpolator) {
        super(settlementDays, calendar, dc);
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    //
    // static private methods
    //

    static private Interpolator constructInterpolator(final Class< ? > klass) {
        if ( klass == null )
            throw new LibraryException("null interpolator");
        if ( !Interpolator.class.isAssignableFrom(klass) )
            throw new LibraryException(ReflectConstants.WRONG_ARGUMENT_TYPE);
        try {
            return (Interpolator) klass.newInstance();
        } catch ( final Exception e ) {
            throw new LibraryException("cannot create Interpolator", e);
        }
    }

    //
    // implements Traits.Curve
    //

    @Override
    public Date maxDate() {
        final int last = dates.length - 1;
        return dates[last];
    }

    @Override
    public Date[] dates() {
        return dates;
    }

    @Override
    public double[] times() {
        return times;
    }

    @Override
    public List< Pair< Date, Double > > nodes() {
        final List< Pair< Date, Double > > nodes = new ArrayList<>();
        for ( int i = 0; i < dates.length; ++i ) {
            nodes.add(new Pair< Date, Double >(dates[i], data[i]));
        }
        return nodes;
    }

    @Override
    public double[] data() {
        return data;
    }

    @Override
    public Interpolator interpolator() {
        return interpolator;
    }

    @Override
    public Interpolation interpolation() {
        return interpolation;
    }

    @Override
    public void setInterpolation(final Interpolation interpolation) {
        this.interpolation = interpolation;
    }

    @Override
    public void setDates(final Date[] dates) {
        this.dates = dates;
    }

    @Override
    public void setTimes(final double[] times) {
        this.times = times;
    }

    @Override
    public void setData(final double[] data) {
        this.data = data;
    }

    @Override
    public double discount(final double t) {
        return discountImpl(t);
    }

    /**
     * Trait-interface accessor. Zero-rate curves do not expose the
     * instantaneous forward as a primitive — use the parent's
     * {@code forwardRate(...)} family instead.
     */
    @Override
    public double forward(final double t) {
        throw new UnsupportedOperationException(
                "InterpolatedSimpleZeroCurve.forward(t): not a primitive of a zero curve — "
                        + "use forwardRate(d1,d2,...) on the parent YieldTermStructure");
    }

    @Override
    public double zeroYield(final double t) {
        // Returns the simply-compounded zero rate at time t (same shape as the underlying interpolation),
        // applying flat-forward extrapolation past the last pillar as per C++ discountImpl logic.
        final double tMax = times[times.length - 1];
        if ( t <= tMax ) {
            return interpolation.op(t, true);
        }
        final double zMax = data[data.length - 1];
        final double instFwdMax = zMax + tMax * interpolation.derivative(tMax, true);
        return (zMax * tMax + instFwdMax * (t - tMax)) / t;
    }

    //
    // overrides AbstractYieldTermStructure — simply-compounded discount
    //

    @Override
    protected double discountImpl(final double t) {
        // C++ v1.42.1 InterpolatedSimpleZeroCurve<T>::discountImpl
        // (interpolatedsimplezerocurve.hpp:114-128).
        double r;
        final double tMax = times[times.length - 1];
        if ( t <= tMax ) {
            r = interpolation.op(t, true);
        } else {
            // flat fwd extrapolation past last pillar in the simply-compounded sense
            final double zMax = data[data.length - 1];
            final double instFwdMax = zMax + tMax * interpolation.derivative(tMax, true);
            r = (zMax * tMax + instFwdMax * (t - tMax)) / t;
        }
        return 1.0 / (1.0 + r * t);
    }

}
