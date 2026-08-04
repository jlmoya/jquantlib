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
 * Term structure based on interpolation of forward rates
 * <p>
 *
 * @param <I> Interpolator
 * @author Richard Gomes
 * @category yieldtermstructures
 */

@SuppressWarnings("deprecation")
public class InterpolatedForwardCurve< I extends Interpolator > extends ForwardRateStructure implements Traits.Curve {

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

    public InterpolatedForwardCurve(final Class< I > classI, final Date[] dates, final double[] forwards,
            final DayCounter dc) {
        this(classI, dates, forwards, dc, null, null);
    }

    public InterpolatedForwardCurve(final Class< I > classI, final Date[] dates, final double[] forwards,
            final DayCounter dc, final Calendar calendar) {
        this(classI, dates, forwards, dc, calendar, null);
    }

    public InterpolatedForwardCurve(final Class< I > classI, final Date[] dates, final double[] forwards,
            final DayCounter dc, final Calendar calendar, final Interpolator interpolator) {
        super(dates[0], calendar == null ? new Calendar() : calendar, dc);

        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;

        // Phase1-closure-A7-J align: mirror v1.42.1 InterpolatedForwardCurve::initialize
        // (forwardcurve.hpp:243-254) + setupTimes (interpolatedcurve.hpp:100-115).
        // Previously the Java port carried three copy-paste errors from
        // InterpolatedDiscountCurve:
        //   1) `forwards[0] == 1.0` — a discount-factor precondition that has
        //      no meaning for forward *rates* (rates are typically <<1.0).
        //   2) `data[0] > 0` inside the loop — also discount-curve semantics
        //      ("Negative discount"); C++ has no negative-forward check.
        //   3) `Closeness.isClose(times[i], times[i-1])` — logic *inverted*
        //      vs. C++ `!close(...)` (require dates to *differ* in year
        //      fraction, not coincide).
        // See ConvertibleBondAdditionalTest class javadoc for the original
        // bug-report breadcrumb.
        QL.require(dates.length != 0, "Dates cannot be empty");
        QL.require(forwards.length != 0, "forwards cannot be empty");
        QL.require(dates.length == forwards.length, "Dates must be the same size as forwards");

        this.dates = dates;
        this.data = forwards;
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

    protected InterpolatedForwardCurve(final Class< I > classI, final DayCounter dc) {
        this(classI, dc, null);
    }

    protected InterpolatedForwardCurve(final Class< I > classI, final DayCounter dc, final Interpolator interpolator) {
        super(dc);

        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    protected InterpolatedForwardCurve(final Class< I > classI, final Date referenceDate, final DayCounter dc) {
        this(classI, referenceDate, dc, null);
    }

    protected InterpolatedForwardCurve(final Class< I > classI, final Date referenceDate, final DayCounter dc,
            final Interpolator interpolator) {
        super(referenceDate, new Calendar(), dc);

        QL.require(classI != null, "Generic type for Interpolation is null");
        this.classI = classI;
        this.interpolator = interpolator == null ? constructInterpolator(classI) : interpolator;
    }

    protected InterpolatedForwardCurve(final Class< I > classI, final /*@Natural*/ int settlementDays,
            final Calendar calendar, final DayCounter dc) {
        this(classI, settlementDays, calendar, dc, null);
    }

    protected InterpolatedForwardCurve(final Class< I > classI, final /*@Natural*/ int settlementDays,
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

    @Override
    public double forward(final double t) {
        return forwardImpl(t);
    }

    @Override
    public double zeroYield(final double t) {
        return zeroYieldImpl(t);
    }

    //
    // overrides AbstractYieldTermStructure
    //

    @Override
    public double discountImpl(final double t) {
        // Phase Bug-Fix-2 align: v1.42.1 InterpolatedForwardCurve inherits
        // from ZeroYieldStructure, which derives discount from zero yield via
        // exp(-r*t) (zeroyieldstructure.hpp:97). The Java port previously
        // threw UnsupportedOperationException, breaking
        // PiecewiseYieldCurveTest#testLinearForwardConsistency &
        // testFlatForwardConsistency. Delegating to the parent
        // ForwardRateStructure.discountImpl mirrors the C++ behaviour
        // (zero is computed from forwards via integration; discount = exp(-r*t)).
        return super.discountImpl(t);
    }

    //
    // overrides ForwardRateStructure
    //

    @Override
    public double forwardImpl(final double t) {
        return interpolation.op(t, true);
    }

    @Override
    public double zeroYieldImpl(final double t) {
        if ( t == 0.0 )
            return forwardImpl(0.0);
        else
            return interpolation.primitive(t, true) / t;
    }

}
