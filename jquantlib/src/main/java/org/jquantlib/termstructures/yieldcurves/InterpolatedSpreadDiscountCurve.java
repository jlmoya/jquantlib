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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.AbstractYieldTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Yield curve based on interpolation of discount factors applied as a multiplicative spread to a base
 * {@link YieldTermStructure}.
 * <p>
 * Java port of QuantLib v1.42.1 {@code InterpolatedSpreadDiscountCurve}
 * (see {@code ql/termstructures/yield/spreaddiscountcurve.hpp}).
 * <p>
 * The discount-factor spread at any given date is interpolated between the input data. The composed discount factor
 * is {@code baseCurve.discount(t) * spread(t)} where {@code spread(t)} is the interpolated curve.
 * <p>
 * Outside the pillar range we extrapolate using a flat instantaneous forward rate derived from the spread
 * interpolation's derivative at the last pillar — mirroring the C++ {@code calcSpread} helper.
 * <p>
 * The curve registers as an observer of the base curve, so changes propagate transparently.
 *
 * @param <I> interpolator factory type
 * @author JQuantLib migration contributors (Phase 1.4 closure)
 */
public class InterpolatedSpreadDiscountCurve< I extends Interpolator > extends AbstractYieldTermStructure
        implements Traits.Curve {

    //
    // private final fields
    //

    private final Class< I > classI;
    private final Interpolator interpolator;
    private final Handle< YieldTermStructure > baseCurve;

    //
    // private fields (refreshed on bootstrap setData / setDates / setTimes)
    //

    private Date[] dates;
    /**
     * Optional override of the local max date, normally the last node. Set by the bootstrap when an instrument's
     * latest relevant date falls after its pillar.
     * <p>
     * Mirrors C++ v1.43 {@code InterpolatedCurve<T>::maxDate_} ({@code ql/termstructures/interpolatedcurve.hpp:137}).
     */
    private Date maxDate;
    private /*@Time*/ double[] times;
    private double[] data;
    private Interpolation interpolation;
    private DayCounter prevDayCount;

    //
    // public constructors
    //

    /**
     * Build a spread discount curve from explicit (dates, discount-factor spreads) pillars.
     * Mirrors the C++ public constructor at {@code spreaddiscountcurve.hpp:30}.
     */
    public InterpolatedSpreadDiscountCurve(final Class< I > classI, final Handle< YieldTermStructure > baseCurve,
            final Date[] dates, final double[] dfs, final Interpolator interpolator) {
        super();

        QL.require(classI != null, "Generic type for Interpolation is null");
        QL.require(baseCurve != null, "base curve handle must not be null");
        QL.require(dates != null && dates.length > 0, "no input dates given");
        QL.require(dfs != null && dfs.length == dates.length, "dates/data count mismatch");
        QL.require(dfs[0] == 1.0,
                "the first discount must be == 1.0 to flag the corresponding date as reference date");
        for ( int i = 1; i < dates.length; ++i ) {
            QL.require(dfs[i] > 0.0, "negative discount");
        }

        this.classI = classI;
        this.baseCurve = baseCurve;
        this.interpolator = interpolator;

        this.dates = dates.clone();
        this.data = dfs.clone();
        this.times = new double[dates.length];

        // observer wiring: any change in the base curve invalidates the spread curve
        if ( !baseCurve.empty() ) {
            baseCurve.addObserver(this);
            updateInterpolation();
        }
    }

    //
    // protected constructors (used by PiecewiseYieldCurve bootstrap factory)
    //

    /**
     * Bootstrap-friendly constructor. Mirrors C++ protected ctor at {@code spreaddiscountcurve.hpp:53}.
     * The dates / data arrays are filled later by the bootstrapper via {@link #setDates(Date[])} and
     * {@link #setData(double[])}.
     */
    protected InterpolatedSpreadDiscountCurve(final Class< I > classI, final Handle< YieldTermStructure > baseCurve,
            final Interpolator interpolator) {
        super();

        QL.require(classI != null, "Generic type for Interpolation is null");
        QL.require(baseCurve != null, "base curve handle must not be null");

        this.classI = classI;
        this.baseCurve = baseCurve;
        this.interpolator = interpolator;

        if ( !baseCurve.empty() ) {
            baseCurve.addObserver(this);
        }
    }

    //
    // implements YieldTermStructure (delegated to baseCurve)
    //

    @Override
    public DayCounter dayCounter() {
        return baseCurve.currentLink().dayCounter();
    }

    @Override
    public Calendar calendar() {
        return baseCurve.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return baseCurve.currentLink().settlementDays();
    }

    @Override
    public Date referenceDate() {
        return baseCurve.currentLink().referenceDate();
    }

    @Override
    public Date maxDate() {
        // C++ v1.43 spreaddiscountcurve.hpp:142-145:
        //   Date maxDate = this->maxDate_ != Date() ? this->maxDate_ : dates_.back();
        //   return std::min(baseCurve_->maxDate(), maxDate);
        final Date baseMax = baseCurve.currentLink().maxDate();
        final Date localMax;
        if ( maxDate != null && !maxDate.isNull() ) {
            localMax = maxDate;
        } else if ( dates == null || dates.length == 0 ) {
            return baseMax;
        } else {
            localMax = dates[dates.length - 1];
        }
        return baseMax.le(localMax) ? baseMax : localMax;
    }

    @Override
    public void setMaxDate(final Date maxDate) {
        this.maxDate = maxDate;
    }

    //
    // accessor
    //

    public Handle< YieldTermStructure > baseCurve() {
        return baseCurve;
    }

    //
    // implement Traits.Curve
    //

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
        final List< Pair< Date, Double > > result = new ArrayList<>();
        if ( dates == null ) {
            return result;
        }
        for ( int i = 0; i < dates.length; ++i ) {
            result.add(new Pair< Date, Double >(dates[i], data[i]));
        }
        return result;
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
     * Trait-interface accessor. Spread discount curves do not expose the
     * instantaneous forward as a primitive — use the parent's
     * {@code forwardRate(...)} family which derives it numerically from
     * {@link #discount(double)}.
     */
    @Override
    public double forward(final double t) {
        throw new UnsupportedOperationException(
                "InterpolatedSpreadDiscountCurve.forward(t): not a primitive of a discount curve — "
                        + "use forwardRate(d1,d2,...) on the parent YieldTermStructure");
    }

    /**
     * Trait-interface accessor. Spread discount curves do not expose the
     * zero yield as a primitive — use the parent's {@code zeroRate(...)}
     * family which derives it from {@link #discount(double)}.
     */
    @Override
    public double zeroYield(final double t) {
        throw new UnsupportedOperationException(
                "InterpolatedSpreadDiscountCurve.zeroYield(t): not a primitive of a discount curve — "
                        + "use zeroRate(d,...) on the parent YieldTermStructure");
    }

    //
    // overrides AbstractYieldTermStructure
    //

    @Override
    protected double discountImpl(final double t) {
        // C++ v1.42.1: baseCurve_->discount(t) * calcSpread(t)
        return baseCurve.currentLink().discount(t, true) * calcSpread(t);
    }

    /**
     * Compute the spread factor at time {@code t}, mirroring C++ {@code calcSpread}
     * ({@code spreaddiscountcurve.hpp:184}).
     */
    private double calcSpread(final double t) {
        QL.require(interpolation != null, "spread interpolation not initialised");
        QL.require(times != null && times.length > 0, "spread curve times not set");
        final double tMax = times[times.length - 1];
        if ( t <= tMax ) {
            return interpolation.op(t, true);
        }
        // flat fwd extrapolation
        final double dMax = data[data.length - 1];
        final double instFwdMax = -interpolation.derivative(tMax, true) / dMax;
        return dMax * Math.exp(-instFwdMax * (t - tMax));
    }

    //
    // observer callback
    //

    @Override
    public void update() {
        // C++ v1.42.1 InterpolatedSpreadDiscountCurve::update — only rebuild interpolation when
        // the base curve is set, otherwise propagate the base-class TermStructure update.
        if ( !baseCurve.empty() ) {
            if ( dates != null && dates.length > 0 ) {
                updateInterpolation();
            }
        }
        super.update();
    }

    /**
     * Rebuild the spread interpolation, mirroring C++ {@code updateInterpolation}
     * ({@code spreaddiscountcurve.hpp:213}).
     */
    private void updateInterpolation() {
        QL.require(dates != null && dates.length > 0, "no dates to interpolate");
        QL.require(dates[0].equals(referenceDate()),
                "the first date should be the same as in the original curve");
        final DayCounter dc = dayCounter();
        if ( prevDayCount == null || !prevDayCount.equals(dc) ) {
            // Recompute the times via the (possibly new) day counter.
            times = new double[dates.length];
            times[0] = 0.0;
            for ( int i = 1; i < dates.length; ++i ) {
                times[i] = dc.yearFraction(dates[0], dates[i]);
                QL.require(!Closeness.isClose(times[i], times[i - 1]),
                        "two dates correspond to the same time under this curve's day count convention");
            }
            this.interpolation = interpolator.interpolate(new Array(times), new Array(data));
            this.interpolation.update();
            this.prevDayCount = dc;
        }
    }

}
