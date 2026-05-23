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
 Copyright (C) 2008, 2009 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.credit;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Default-probability term structure based on interpolation of hazard rates — Java port of QuantLib v1.42.1
 * {@code InterpolatedHazardRateCurve<Interpolator>}
 * ({@code ql/termstructures/credit/interpolatedhazardratecurve.hpp}).
 *
 * <p>The C++ class is templated on the interpolator type; the Java port uses
 * the JQuantLib {@code Class<I>} idiom (mirrors
 * {@code org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve}).
 *
 * <p>Hazard-rate extrapolation past the last node is flat
 * (mirrors C++ {@code data_.back()}). Survival probability uses the interpolation primitive:
 * {@code S(t) = exp(-integral_0^t h(tau) dtau)}.
 *
 * @param <I> interpolator type (e.g. {@link org.jquantlib.math.interpolations.factories.BackwardFlat}).
 */
@SuppressWarnings("deprecation")
public class InterpolatedHazardRateCurve< I extends Interpolator > extends HazardRateStructure {

    //
    // protected mutable fields (visible to subclasses for bootstrap)
    //

    private final Class< I > classI;
    private final Interpolator interpolator;
    protected Date[] dates;
    protected /*@Time*/ double[] times;

    //
    // private final fields
    //
    protected double[] data;
    protected Interpolation interpolation;

    //
    // public constructors
    //

    public InterpolatedHazardRateCurve(final Class< I > classI, final Date[] dates, final double[] hazardRates,
            final DayCounter dayCounter, final Calendar calendar) {
        this(classI, dates, hazardRates, dayCounter, calendar, constructInterpolator(classI));
    }

    public InterpolatedHazardRateCurve(final Class< I > classI, final Date[] dates, final double[] hazardRates,
            final DayCounter dayCounter) {
        this(classI, dates, hazardRates, dayCounter, new NullCalendar(), constructInterpolator(classI));
    }

    public InterpolatedHazardRateCurve(final Class< I > classI, final Date[] dates, final double[] hazardRates,
            final DayCounter dayCounter, final Calendar calendar, final Interpolator interpolator) {
        super(dates[0], (calendar != null) ? calendar : new NullCalendar(), dayCounter);
        QL.require(classI != null, "Generic type for Interpolation is null");
        QL.require(dates != null && hazardRates != null && dates.length == hazardRates.length,
                "dates/data count mismatch");
        this.classI = classI;
        this.interpolator = (interpolator != null) ? interpolator : constructInterpolator(classI);
        this.dates = dates.clone();
        this.data = hazardRates.clone();
        initialize();
    }

    //
    // protected constructors (bootstrap descendants)
    //

    protected InterpolatedHazardRateCurve(final Class< I > classI, final DayCounter dayCounter) {
        super(dayCounter);
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = constructInterpolator(classI);
    }

    protected InterpolatedHazardRateCurve(final Class< I > classI, final Date referenceDate,
            final DayCounter dayCounter) {
        super(referenceDate, new NullCalendar(), dayCounter);
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = constructInterpolator(classI);
    }

    protected InterpolatedHazardRateCurve(final Class< I > classI, final int settlementDays, final Calendar calendar,
            final DayCounter dayCounter) {
        super(settlementDays, calendar, dayCounter);
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = constructInterpolator(classI);
    }

    //
    // factory
    //

    static private Interpolator constructInterpolator(final Class< ? > klass) {
        if ( klass == null ) {
            throw new LibraryException("null interpolator");
        }
        if ( !Interpolator.class.isAssignableFrom(klass) ) {
            throw new LibraryException(ReflectConstants.WRONG_ARGUMENT_TYPE);
        }
        try {
            return (Interpolator) klass.newInstance();
        } catch ( final Exception e ) {
            throw new LibraryException("cannot create Interpolator", e);
        }
    }

    //
    // initialize
    //

    private void initialize() {
        QL.require(dates.length >= interpolator.requiredPoints(), "not enough input dates given");
        QL.require(data.length == dates.length, "dates/data count mismatch");
        for ( int i = 0; i < dates.length; ++i ) {
            QL.require(data[i] >= 0.0, "negative hazard rate");
        }
        setupTimes(dates, dates[0], dayCounter());
        setupInterpolation();
        interpolation.update();
    }

    //
    // TermStructure
    //

    @Override
    public Date maxDate() {
        return dates[dates.length - 1];
    }

    //
    // HazardRateStructure
    //

    @Override
    protected double hazardRateImpl(final @Time double t) {
        if ( t <= times[times.length - 1] ) {
            return interpolation.op(t, true);
        }
        // flat hazard rate extrapolation
        return data[data.length - 1];
    }

    @Override
    protected double survivalProbabilityImpl(final @Time double t) {
        if ( t == 0.0 ) {
            return 1.0;
        }
        double integral;
        final double tMax = times[times.length - 1];
        if ( t <= tMax ) {
            integral = interpolation.primitive(t, true);
        } else {
            integral = interpolation.primitive(tMax, true) + data[data.length - 1] * (t - tMax);
        }
        return Math.exp(-integral);
    }

    //
    // inspectors
    //

    public Date[] dates() {
        return dates;
    }

    public /*@Time*/ double[] times() {
        return times;
    }

    public double[] data() {
        return data;
    }

    public double[] hazardRates() {
        return data;
    }

    public List< Pair< Date, Double > > nodes() {
        final List< Pair< Date, Double > > result = new ArrayList<>(dates.length);
        for ( int i = 0; i < dates.length; ++i ) {
            result.add(new Pair<>(dates[i], data[i]));
        }
        return result;
    }

    public Interpolator interpolator() {
        return interpolator;
    }

    public Interpolation interpolation() {
        return interpolation;
    }

    public Class< I > interpolatorClass() {
        return classI;
    }

    //
    // protected helpers (for bootstrap descendants)
    //

    protected void setupTimes(final Date[] ds, final Date referenceDate, final DayCounter dc) {
        this.times = new double[ds.length];
        this.times[0] = dc.yearFraction(referenceDate, ds[0]);
        for ( int i = 1; i < ds.length; ++i ) {
            QL.require(ds[i].gt(ds[i - 1]), "dates not sorted: " + ds[i] + " passed after " + ds[i - 1]);
            this.times[i] = dc.yearFraction(referenceDate, ds[i]);
            QL.require(this.times[i] != this.times[i - 1], "two passed dates correspond to the same time");
        }
    }

    protected void setupInterpolation() {
        this.interpolation = this.interpolator.interpolate(new Array(times), new Array(data));
    }

    protected void setData(final double[] newData) {
        this.data = newData;
    }

    protected void setDates(final Date[] newDates) {
        this.dates = newDates;
    }

    protected void setTimes(final double[] newTimes) {
        this.times = newTimes;
    }

    protected void setInterpolation(final Interpolation newInterpolation) {
        this.interpolation = newInterpolation;
    }
}
