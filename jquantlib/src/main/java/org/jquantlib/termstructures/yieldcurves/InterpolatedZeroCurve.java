/*
Copyright (C) 2011 Richard Gomes

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
 Copyright (C) 2002, 2003 Decillion Pty(Ltd)
 Copyright (C) 2005, 2006, 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Term structure based on interpolation of zero yields
 * <p>
 *
 * @param <I> Interpolator
 * @author Richard Gomes
 * @category yieldtermstructures
 */
@SuppressWarnings("deprecation") // legitimate internal user of deprecated InterpolatedCurve API
public class InterpolatedZeroCurve< I extends Interpolator > extends ZeroYieldStructure implements Traits.Curve {

    //
    // private fields
    //

    private final Class< I > classI;
    private final Interpolator interpolator;
    private Date[] dates;
    /**
     * Optional override of the curve's max date, normally the last node. Set by the bootstrap when an instrument's
     * latest relevant date falls after its pillar, so the curve is allowed to extrapolate that far.
     * <p>
     * Mirrors C++ v1.43 {@code InterpolatedCurve<T>::maxDate_} ({@code ql/termstructures/interpolatedcurve.hpp:137}).
     */
    private Date maxDate;
    private /*@Time*/ double[] times;

    //
    // private final fields
    //
    private Interpolation interpolation;
    private double[] data;

    //
    // public constructors
    //

    public InterpolatedZeroCurve(final Class< I > classI, final Date[] dates, final double[] yields,
            final DayCounter dc) {
        this(classI, dates, yields, dc, null, null);
    }

    public InterpolatedZeroCurve(final Class< I > classI, final Date[] dates, final double[] yields,
            final DayCounter dc, final Calendar calendar) {
        this(classI, dates, yields, dc, calendar, null);
    }

    public InterpolatedZeroCurve(final Class< I > classI, final Date[] dates, final double[] yields,
            final DayCounter dc, final Calendar calendar, final Interpolator interpolator) {
        super(dates[0], calendar == null ? new Calendar() : calendar, dc);

        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;

        QL.require(dates.length != 0, "Dates cannot be empty");
        QL.require(yields.length != 0, "yields cannot be empty");
        QL.require(dates.length == yields.length, "Dates must be the same size as yields");
        // NOTE: data are zero rates, NOT discount factors. The previous
        // `yields[0] == 1.0` and `data[0] > 0` assertions were a stale
        // copy-paste from InterpolatedDiscountCurve and have been removed
        // (Phase 2x A.1) — zero rates are arbitrary doubles per C++ v1.42.1
        // (ql/termstructures/yield/zerocurve.hpp).

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

    protected InterpolatedZeroCurve(final Class< I > classI, final Date referenceDate, final DayCounter dc) {
        this(classI, referenceDate, dc, null);
    }

    protected InterpolatedZeroCurve(final Class< I > classI, final Date referenceDate, final DayCounter dc,
            final Interpolator interpolator) {
        super(referenceDate, new Calendar(), dc);
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    protected InterpolatedZeroCurve(final Class< I > classI, final DayCounter dc) {
        this(classI, dc, null);
    }

    protected InterpolatedZeroCurve(final Class< I > classI, final DayCounter dc, final Interpolator interpolator) {
        super(dc);

        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    protected InterpolatedZeroCurve(final Class< I > classI, final /*@Natural*/ int settlementDays,
            final Calendar calendar, final DayCounter dc) {
        this(classI, settlementDays, calendar, dc, null);
    }

    protected InterpolatedZeroCurve(final Class< I > classI, final /*@Natural*/ int settlementDays,
            final Calendar calendar, final DayCounter dc, final Interpolator interpolator) {
        // Phase 3e: align to v1.42.1 — pass the supplied calendar (was
        // `new Calendar()`, which left impl==null and broke advance/adjust).
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
        // C++ v1.43 discountcurve.hpp:111-115:
        //   if (this->maxDate_ != Date()) return this->maxDate_;
        //   return dates_.back();
        if ( maxDate != null && !maxDate.isNull() )
            return maxDate;
        final int last = dates.length - 1;
        return dates[last];
    }

    @Override
    public void setMaxDate(final Date maxDate) {
        this.maxDate = maxDate;
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
     * instantaneous forward as a primitive (C++ has no analogous method on
     * {@code InterpolatedZeroCurve}); callers should go through the public
     * {@link org.jquantlib.termstructures.YieldTermStructure#forwardRate}
     * API which derives it from {@link #zeroYield(double)} numerically.
     */
    @Override
    public double forward(final double t) {
        throw new UnsupportedOperationException(
                "InterpolatedZeroCurve.forward(t): not a primitive of a zero curve — "
                        + "use forwardRate(d1,d2,...) on the parent YieldTermStructure");
    }

    @Override
    public double zeroYield(final double t) {
        return zeroYieldImpl(t);
    }

    //
    // overrides ZeroYieldStructure
    //

    @Override
    protected double zeroYieldImpl(final double t) {
        // Mirror C++ InterpolatedZeroCurve<T>::zeroYieldImpl (v1.42.1
        // ql/termstructures/yield/zerocurve.hpp:159-169): inside the
        // curve's last pillar use the interpolator directly; PAST the
        // last pillar use FLAT-FORWARD extrapolation. Cubic-extrapolating
        // the zero-rate polynomial past the last pillar produces an
        // uncontrolled tail (Phase 5e.5b-CFC-d-4): the extrapolated zero
        // is dominated by the curvature of the last segment, which can
        // be far from the locally-instantaneous forward, and discount
        // factors drift from the C++ reference at the level of 1e-7+.
        final double tMax = times[times.length - 1];
        if ( t <= tMax ) {
            return interpolation.op(t, true);
        }
        // flat fwd extrapolation
        final double zMax = data[data.length - 1];
        final double instFwdMax = zMax + tMax * interpolation.derivative(tMax, true);
        return (zMax * tMax + instFwdMax * (t - tMax)) / t;
    }

}
