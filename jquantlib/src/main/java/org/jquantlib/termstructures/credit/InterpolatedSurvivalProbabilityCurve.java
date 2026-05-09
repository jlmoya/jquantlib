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

import java.util.ArrayList;
import java.util.List;

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

/**
 * Default-probability term structure based on interpolation of survival
 * probabilities — Java port of QuantLib v1.42.1
 * {@code InterpolatedSurvivalProbabilityCurve<Interpolator>}
 * ({@code ql/termstructures/credit/interpolatedsurvivalprobabilitycurve.hpp}).
 *
 * <p>Beyond the last node, extrapolation is "flat hazard rate":
 * {@code S(t) = S_max * exp(-h_max * (t - t_max))} with
 * {@code h_max = -interpolation.derivative(tMax) / sMax}.
 *
 * @param <I> interpolator type.
 */
public class InterpolatedSurvivalProbabilityCurve<I extends Interpolator>
        extends SurvivalProbabilityStructure {

    protected Date[] dates;
    protected /*@Time*/ double[] times;
    protected double[] data;
    protected Interpolation interpolation;

    private final Class<I> classI;
    private final Interpolator interpolator;

    public InterpolatedSurvivalProbabilityCurve(
            final Class<I> classI,
            final Date[] dates,
            final double[] probabilities,
            final DayCounter dayCounter,
            final Calendar calendar) {
        this(classI, dates, probabilities, dayCounter, calendar, constructInterpolator(classI));
    }

    public InterpolatedSurvivalProbabilityCurve(
            final Class<I> classI,
            final Date[] dates,
            final double[] probabilities,
            final DayCounter dayCounter) {
        this(classI, dates, probabilities, dayCounter, new NullCalendar(),
             constructInterpolator(classI));
    }

    public InterpolatedSurvivalProbabilityCurve(
            final Class<I> classI,
            final Date[] dates,
            final double[] probabilities,
            final DayCounter dayCounter,
            final Calendar calendar,
            final Interpolator interpolator) {
        super(dates[0], (calendar != null) ? calendar : new NullCalendar(), dayCounter);
        QL.require(classI != null, "Generic type for Interpolation is null");
        QL.require(dates != null && probabilities != null
                && dates.length == probabilities.length,
                "dates/data count mismatch");
        this.classI = classI;
        this.interpolator = (interpolator != null) ? interpolator : constructInterpolator(classI);
        this.dates = dates.clone();
        this.data = probabilities.clone();
        initialize();
    }

    protected InterpolatedSurvivalProbabilityCurve(
            final Class<I> classI,
            final DayCounter dayCounter) {
        super(dayCounter);
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = constructInterpolator(classI);
    }

    protected InterpolatedSurvivalProbabilityCurve(
            final Class<I> classI,
            final Date referenceDate,
            final DayCounter dayCounter) {
        super(referenceDate, new NullCalendar(), dayCounter);
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = constructInterpolator(classI);
    }

    protected InterpolatedSurvivalProbabilityCurve(
            final Class<I> classI,
            final int settlementDays,
            final Calendar calendar,
            final DayCounter dayCounter) {
        super(settlementDays, calendar, dayCounter);
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = constructInterpolator(classI);
    }

    static private Interpolator constructInterpolator(final Class<?> klass) {
        if (klass == null) throw new LibraryException("null interpolator");
        if (!Interpolator.class.isAssignableFrom(klass)) {
            throw new LibraryException(ReflectConstants.WRONG_ARGUMENT_TYPE);
        }
        try {
            return (Interpolator) klass.newInstance();
        } catch (final Exception e) {
            throw new LibraryException("cannot create Interpolator", e);
        }
    }

    private void initialize() {
        QL.require(dates.length >= interpolator.requiredPoints(),
                "not enough input dates given");
        QL.require(data.length == dates.length, "dates/data count mismatch");
        QL.require(data[0] == 1.0,
                "the first probability must be == 1.0 to flag the corresponding date as reference date");
        setupTimes(dates, dates[0], dayCounter());
        for (int i = 1; i < dates.length; ++i) {
            QL.require(data[i] > 0.0, "negative probability");
            QL.require(data[i] <= data[i - 1],
                    "negative hazard rate implied by survival probability " +
                    data[i] + " at " + dates[i] + " (t=" + times[i] +
                    ") after " + data[i - 1] + " at " + dates[i - 1] +
                    " (t=" + times[i - 1] + ")");
        }
        setupInterpolation();
        interpolation.update();
    }

    @Override
    public Date maxDate() { return dates[dates.length - 1]; }

    @Override
    protected double survivalProbabilityImpl(final @Time double t) {
        final double tMax = times[times.length - 1];
        if (t <= tMax) {
            return interpolation.op(t, true);
        }
        // flat hazard rate extrapolation
        final double sMax = data[data.length - 1];
        final double hazardMax = -interpolation.derivative(tMax) / sMax;
        return sMax * Math.exp(-hazardMax * (t - tMax));
    }

    @Override
    protected double defaultDensityImpl(final @Time double t) {
        final double tMax = times[times.length - 1];
        if (t <= tMax) {
            return -interpolation.derivative(t, true);
        }
        final double sMax = data[data.length - 1];
        final double hazardMax = -interpolation.derivative(tMax) / sMax;
        return sMax * hazardMax * Math.exp(-hazardMax * (t - tMax));
    }

    public Date[] dates() { return dates; }
    public /*@Time*/ double[] times() { return times; }
    public double[] data() { return data; }
    public double[] survivalProbabilities() { return data; }

    public List<Pair<Date, Double>> nodes() {
        final List<Pair<Date, Double>> result = new ArrayList<>(dates.length);
        for (int i = 0; i < dates.length; ++i) {
            result.add(new Pair<>(dates[i], data[i]));
        }
        return result;
    }

    public Interpolator interpolator() { return interpolator; }
    public Interpolation interpolation() { return interpolation; }
    public Class<I> interpolatorClass() { return classI; }

    protected void setupTimes(final Date[] ds, final Date referenceDate, final DayCounter dc) {
        this.times = new double[ds.length];
        this.times[0] = dc.yearFraction(referenceDate, ds[0]);
        for (int i = 1; i < ds.length; ++i) {
            QL.require(ds[i].gt(ds[i - 1]),
                    "dates not sorted: " + ds[i] + " passed after " + ds[i - 1]);
            this.times[i] = dc.yearFraction(referenceDate, ds[i]);
            QL.require(this.times[i] != this.times[i - 1],
                    "two passed dates correspond to the same time");
        }
    }

    protected void setupInterpolation() {
        this.interpolation = this.interpolator.interpolate(
                new Array(times), new Array(data));
    }

    protected void setData(final double[] newData) { this.data = newData; }
    protected void setDates(final Date[] newDates) { this.dates = newDates; }
    protected void setTimes(final double[] newTimes) { this.times = newTimes; }
    protected void setInterpolation(final Interpolation newInterpolation) {
        this.interpolation = newInterpolation;
    }
}
