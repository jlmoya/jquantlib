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
 Copyright (C) 2007, 2008, 2009 Chris Kenyon
 Copyright (C) 2009 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.util.Pair;

/**
 * Inflation term structure based on the interpolation of year-on-year rates.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code InterpolatedYoYInflationCurve<Interpolator>}
 * ({@code ql/termstructures/inflation/interpolatedyoyinflationcurve.hpp}).
 *
 * <p>Sister class to
 * {@link InterpolatedZeroInflationCurve} — same architecture, same generic
 * shape (uses {@code Class<I>} for runtime interpolator factory access).
 *
 * <h3>Note on input rates</h3>
 * The provided rates are <strong>not</strong> YoY inflation-swap quotes — they
 * are direct YoY rates at each pillar date. Bootstrap from swap quotes is the
 * job of {@link PiecewiseYoYInflationCurve}.
 *
 * @param <I> Interpolator type (e.g. {@link Linear}).
 * @see PiecewiseYoYInflationCurve
 * @see YearOnYearInflationSwapHelper
 */
public class InterpolatedYoYInflationCurve<I extends Interpolator>
        extends YoYInflationTermStructure {

    //
    // protected fields (mutable, accessible to subclasses for bootstrap)
    //

    protected Date[] dates;
    protected /*@Time*/ double[] times;
    protected double[] data;
    protected Interpolation interpolation;
    protected Date maxDateOverride;

    //
    // private final fields
    //

    private final Class<I> classI;
    private final Interpolator interpolator;

    //
    // public constructors
    //

    /**
     * Primary constructor — equivalent to the C++ first-form template
     * constructor. The first date in {@code dates} is the curve's base date.
     */
    public InterpolatedYoYInflationCurve(
            final Class<I> classI,
            final Date referenceDate,
            final Date[] dates,
            final double[] rates,
            final Frequency frequency,
            final DayCounter dayCounter) {
        this(classI, referenceDate, dates, rates, frequency, dayCounter,
             constructInterpolator(classI));
    }

    public InterpolatedYoYInflationCurve(
            final Class<I> classI,
            final Date referenceDate,
            final Date[] dates,
            final double[] rates,
            final Frequency frequency,
            final DayCounter dayCounter,
            final Interpolator interpolator) {
        super(referenceDate, new NullCalendar(), dayCounter,
              new Period((int) referenceDate.sub(dates[0]), TimeUnit.Days),
              frequency, rates[0], new Handle<YieldTermStructure>());
        QL.require(classI != null, "Generic type for Interpolation is null");
        QL.require(dates != null && dates.length > 1,
                "too few dates: " + (dates == null ? 0 : dates.length));
        QL.require(rates != null && rates.length == dates.length,
                "indices/dates count mismatch: " +
                (rates == null ? 0 : rates.length) + " vs " + dates.length);
        for (int i = 1; i < dates.length; ++i) {
            // YoY inflation data may be positive or negative but must be > -1.
            QL.require(rates[i] > -1.0, "year-on-year inflation data < -100 %");
        }

        this.classI = classI;
        this.interpolator = interpolator == null
                ? constructInterpolator(classI) : interpolator;

        this.dates = dates.clone();
        this.data = rates.clone();
        setupTimes(this.dates, referenceDate, dayCounter);
        setupInterpolation();
        this.interpolation.update();
    }

    //
    // protected constructors (used by descendants — e.g. PiecewiseYoYInflationCurve)
    //

    /**
     * Protected constructor for bootstrap descendants who don't have
     * dates/rates yet. Equivalent to C++ second-form template constructor.
     */
    protected InterpolatedYoYInflationCurve(
            final Class<I> classI,
            final Date referenceDate,
            final Date baseDate,
            final double baseYoYRate,
            final Frequency frequency,
            final DayCounter dayCounter) {
        this(classI, referenceDate, baseDate, baseYoYRate, frequency, dayCounter,
             constructInterpolator(classI));
    }

    protected InterpolatedYoYInflationCurve(
            final Class<I> classI,
            final Date referenceDate,
            final Date baseDate,
            final double baseYoYRate,
            final Frequency frequency,
            final DayCounter dayCounter,
            final Interpolator interpolator) {
        super(referenceDate, new NullCalendar(), dayCounter,
              new Period((int) referenceDate.sub(baseDate), TimeUnit.Days),
              frequency, baseYoYRate, new Handle<YieldTermStructure>());
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = interpolator == null
                ? constructInterpolator(classI) : interpolator;

        // Store baseDate as the first node — bootstrap will append more nodes later.
        this.dates = new Date[] { baseDate };
        this.data = new double[] { baseYoYRate };
        this.times = new double[] { dayCounter.yearFraction(referenceDate, baseDate) };
    }

    //
    // factories
    //

    static private Interpolator constructInterpolator(final Class<?> klass) {
        if (klass == null) {
            throw new LibraryException("null interpolator");
        }
        if (!Interpolator.class.isAssignableFrom(klass)) {
            throw new LibraryException(ReflectConstants.WRONG_ARGUMENT_TYPE);
        }
        try {
            return (Interpolator) klass.newInstance();
        } catch (final Exception e) {
            throw new LibraryException("cannot create Interpolator", e);
        }
    }

    //
    // YoYInflationTermStructure / InflationTermStructure overrides
    //

    /**
     * Override the base-class default ({@code Date(0)}) to return the first
     * node date — matches C++ semantics.
     */
    @Override
    public Date baseDate() {
        return dates[0];
    }

    @Override
    public Date maxDate() {
        if (maxDateOverride != null) {
            return maxDateOverride;
        }
        return dates[dates.length - 1];
    }

    @Override
    protected /*@Rate*/ double yoyRateImpl(final /*@Time*/ double t) {
        return interpolation.op(t, true);
    }

    /**
     * Mirror C++ {@code YoYInflationTermStructure::yoyRate(date, false)} which
     * snaps the date to its inflation-period start before sampling the curve,
     * permitting dates {@code >= baseDate} (which can predate the reference
     * date by the observation lag).
     */
    @Override
    public /*@Rate*/ double yoyRate(final Date d, final boolean extrapolate) {
        final Pair<Date, Date> dd = org.jquantlib.termstructures.InflationTermStructure
                .inflationPeriod(d, frequency());
        QL.require(dd.first().ge(baseDate()),
                "date " + dd.first() + " before base date " + baseDate());
        if (!extrapolate && !allowsExtrapolation()) {
            QL.require(dd.first().le(maxDate()),
                    "date " + dd.first() + " past max curve date " + maxDate());
        }
        return yoyRateImpl(timeFromReference(dd.first()));
    }

    @Override
    public /*@Rate*/ double yoyRate(final Date d) {
        return yoyRate(d, false);
    }

    //
    // Inspectors (mirroring C++ template inline accessors)
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

    public double[] rates() {
        return data;
    }

    public List<Pair<Date, Double>> nodes() {
        final List<Pair<Date, Double>> result = new ArrayList<>(dates.length);
        for (int i = 0; i < dates.length; ++i) {
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

    public Class<I> interpolatorClass() {
        return classI;
    }

    //
    // Protected setup helpers (for piecewise bootstrap path)
    //

    /**
     * Mirrors C++ {@code InterpolatedCurve::setupTimes}: builds {@code times_}
     * from the supplied dates against the given reference date and day counter.
     */
    protected void setupTimes(final Date[] ds, final Date referenceDate,
                              final DayCounter dc) {
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

    /** Mirrors C++ {@code InterpolatedCurve::setupInterpolation}. */
    protected void setupInterpolation() {
        this.interpolation = this.interpolator.interpolate(
                new Array(times), new Array(data));
    }

    /** Setter used by the bootstrap loop to install bootstrapped data. */
    protected void setData(final double[] newData) {
        this.data = newData;
    }

    /** Setter used by the bootstrap loop to extend the date grid. */
    protected void setDates(final Date[] newDates) {
        this.dates = newDates;
    }

    /** Setter used by the bootstrap loop to extend the time grid. */
    protected void setTimes(final double[] newTimes) {
        this.times = newTimes;
    }

    /** Setter for the running interpolation during bootstrap. */
    protected void setInterpolation(final Interpolation newInterpolation) {
        this.interpolation = newInterpolation;
    }

    /** For bootstrap: optionally override the maxDate to a value beyond dates[end]. */
    protected void setMaxDate(final Date d) {
        this.maxDateOverride = d;
    }

    /**
     * Override the base-rate slot. Used during bootstrap when the first helper
     * is solved (the curve's base value comes from the bootstrap, not from
     * construction).
     */
    protected void overrideBaseRate(final double r) {
        this.setBaseRate(r);
    }
}
