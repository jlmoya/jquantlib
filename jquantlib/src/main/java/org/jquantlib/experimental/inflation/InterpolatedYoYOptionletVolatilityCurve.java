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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.experimental.inflation;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;
import org.jquantlib.time.*;
import org.jquantlib.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Interpolated flat-smile YoY-inflation optionlet volatility curve.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/inflation/yoyinflationoptionletvolatilitystructure2.hpp}. Interpolated in T direction,
 * constant in K direction (the "smile" is flat).
 *
 * <p>The supplied {@code dates} are vol pillars — there is no lag adjustment
 * on these dates, but they are relative to a start date that is earlier than the reference date (as always for
 * inflation).
 *
 * <p>The C++ class is templated on a 1D interpolator. Java uses the same
 * "{@code Class<I>} factory" pattern that {@link org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve}
 * uses, so callers select interpolation by passing e.g. {@link Linear}.class.
 *
 * @param <I> Interpolator type (e.g. {@link Linear}).
 */
@SuppressWarnings("deprecation")
public class InterpolatedYoYOptionletVolatilityCurve< I extends Interpolator > extends YoYOptionletVolatilitySurface {

    //
    // protected fields (mutable; sized for piecewise/bootstrap descendants)
    //

    private final Class< I > classI;
    private final Interpolator interpolator;
    private final double minStrike_;
    private final double maxStrike_;
    protected Date[] dates_;
    protected double[] times_;
    protected double[] data_;
    protected Interpolation interpolation_;

    //
    // public constructors
    //

    /** Primary constructor — explicit dates + vols. */
    public InterpolatedYoYOptionletVolatilityCurve(final Class< I > classI, final int settlementDays,
            final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc, final Period lag,
            final Frequency frequency, final boolean indexIsInterpolated, final Date[] dates, final double[] vols,
            final double minStrike, final double maxStrike) {
        this(classI, settlementDays, cal, bdc, dc, lag, frequency, indexIsInterpolated, dates, vols, minStrike,
                maxStrike, constructInterpolator(classI));
    }

    /** Primary constructor with explicit interpolator instance. */
    public InterpolatedYoYOptionletVolatilityCurve(final Class< I > classI, final int settlementDays,
            final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc, final Period lag,
            final Frequency frequency, final boolean indexIsInterpolated, final Date[] dates, final double[] vols,
            final double minStrike, final double maxStrike, final Interpolator interpolator) {
        super(settlementDays, cal, bdc, dc, lag, frequency, indexIsInterpolated);
        QL.require(classI != null, "Generic type for Interpolation is null");
        QL.require(dates != null && vols != null, "dates/vols must not be null");
        QL.require(dates.length == vols.length,
                "must have same number of dates and vols: " + dates.length + " vs " + vols.length);
        QL.require(dates.length > 1, "must have at least two dates: " + dates.length);

        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
        this.minStrike_ = minStrike;
        this.maxStrike_ = maxStrike;

        this.dates_ = dates.clone();
        this.data_ = vols.clone();
        this.times_ = new double[this.dates_.length];
        for ( int j = 0; j < this.dates_.length; ++j ) {
            this.times_[j] = timeFromReference(this.dates_[j]);
        }
        setupInterpolation();
        // Set base level to the curve's own value at base time (extrapolation
        // is permitted when reading at the base date because it can predate
        // the first pillar by very little — mirror C++).
        final double baseTime = timeFromReference(baseDate());
        setBaseLevel(this.interpolation_.op(baseTime, true));
    }

    //
    // protected constructor — bootstrap / piecewise helpers
    //

    /**
     * Bootstrap-only constructor: caller has the surface base vol but no pillar data yet. Mirrors C++ second-form
     * constructor.
     */
    protected InterpolatedYoYOptionletVolatilityCurve(final Class< I > classI, final int settlementDays,
            final Calendar cal, final BusinessDayConvention bdc, final DayCounter dc, final Period lag,
            final Frequency frequency, final boolean indexIsInterpolated, final double minStrike,
            final double maxStrike, final double baseYoYVolatility, final Interpolator interpolator) {
        super(settlementDays, cal, bdc, dc, lag, frequency, indexIsInterpolated);
        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
        this.minStrike_ = minStrike;
        this.maxStrike_ = maxStrike;
        // No pillar data yet; we only have the base level (matches C++).
        this.dates_ = new Date[0];
        this.data_ = new double[0];
        this.times_ = new double[0];
        setBaseLevel(baseYoYVolatility);
    }

    //
    // factories
    //

    private static Interpolator constructInterpolator(final Class< ? > klass) {
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

    /** Mirrors C++ {@code InterpolatedCurve::setupInterpolation}. */
    protected void setupInterpolation() {
        this.interpolation_ = this.interpolator.interpolate(new Array(times_), new Array(data_));
        this.interpolation_.update();
    }

    //
    // YoYOptionletVolatilitySurface overrides
    //

    @Override
    public double minStrike() {
        return minStrike_;
    }

    @Override
    public double maxStrike() {
        return maxStrike_;
    }

    /**
     * Approximate {@code maxDate}: project the largest interpolation x (in years, ceil) forward from the reference date
     * through the calendar. Mirrors C++ {@code optionDateFromTenor(Period(ceil(xMax), Years))}.
     */
    @Override
    public Date maxDate() {
        final double xMax = times_.length == 0 ? 0.0 : times_[times_.length - 1];
        final int years = (int) Math.ceil(xMax);
        return optionDateFromTenor(new Period(years, TimeUnit.Years));
    }

    /**
     * Strike is ignored — the smile is flat by construction.
     */
    @Override
    protected double volatilityImpl(final double t, final double strike) {
        return interpolation_.op(t);
    }

    //
    // bootstrap inspectors (mirror C++ template inline accessors)
    //

    public double[] times() {
        return times_;
    }

    public Date[] dates() {
        return dates_;
    }

    public double[] data() {
        return data_;
    }

    public List< Pair< Date, Double > > nodes() {
        final List< Pair< Date, Double > > result = new ArrayList<>(dates_.length);
        for ( int i = 0; i < dates_.length; ++i ) {
            result.add(new Pair<>(dates_[i], data_[i]));
        }
        return result;
    }

    public Interpolator interpolator() {
        return interpolator;
    }

    public Interpolation interpolation() {
        return interpolation_;
    }

    public Class< I > interpolatorClass() {
        return classI;
    }
}
