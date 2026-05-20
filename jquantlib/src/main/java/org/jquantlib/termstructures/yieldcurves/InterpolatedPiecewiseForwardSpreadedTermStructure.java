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
 Copyright (C) 2006 Roland Lichters
 Copyright (C) 2006, 2008, 2014 StatPro Italia srl
 Copyright (C) 2010 Robert Philipp

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Term structure with an added vector of spreads on the instantaneous forward rate.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/termstructures/yield/piecewiseforwardspreadedtermstructure.hpp} (header-only
 * template). The forward-rate spread at any given date is interpolated between the input pillar dates via the
 * supplied {@link Interpolator} factory. Outside the pillar range the spread is held flat (left-most / right-most
 * value).
 * <p>
 * The zero-yield rate is recovered by adding the time-average of the spread (i.e. its primitive divided by t) to the
 * continuously-compounded zero rate of the original curve. This means the {@link #zeroYieldImpl(double)} hook returns
 * exactly what the C++ override does:
 * {@code zeroRate_orig + interpolator.primitive(t,true) / t}.
 * <p>
 * The structure remains linked to the original curve and to every spread quote: any change in any of them will be
 * reflected here as well via the observer pattern.
 *
 * @author JQuantLib migration contributors (Phase1-closure-A2-E-552)
 * @see InterpolatedPiecewiseZeroSpreadedTermStructure zero-yield-spread cousin
 */
public class InterpolatedPiecewiseForwardSpreadedTermStructure extends ZeroYieldStructure {

    //
    // private final fields
    //

    private final Handle< YieldTermStructure > originalCurve;
    private final Handle< Quote >[] spreads;
    private final Date[] dates;
    private final double[] times;
    private final double[] spreadValues;
    private final Interpolator factory;

    //
    // private fields (refreshed on update())
    //

    private Interpolation interpolator;

    //
    // public constructors
    //

    /**
     * Full constructor mirroring C++ v1.42.1 (with interpolator-factory parameter).
     */
    @SuppressWarnings( "unchecked" )
    public InterpolatedPiecewiseForwardSpreadedTermStructure(final Handle< YieldTermStructure > h,
            final Handle< Quote >[] spreads, final Date[] dates, final Interpolator factory) {
        super();
        QL.require(spreads != null && spreads.length > 0, "no spreads given");
        QL.require(spreads.length == dates.length, "spread and date vector have different sizes");
        QL.require(factory != null, "null interpolation factory");

        this.originalCurve = h;
        this.spreads = (Handle< Quote >[]) new Handle< ? >[spreads.length];
        System.arraycopy(spreads, 0, this.spreads, 0, spreads.length);
        this.dates = new Date[dates.length];
        System.arraycopy(dates, 0, this.dates, 0, dates.length);
        this.times = new double[dates.length];
        this.spreadValues = new double[dates.length];
        this.factory = factory;

        this.originalCurve.addObserver(this);
        for ( int i = 0; i < this.spreads.length; ++i ) {
            this.spreads[i].addObserver(this);
        }
        if ( !this.originalCurve.empty() ) {
            updateInterpolation();
        }
    }

    //
    // overrides ZeroYieldStructure / AbstractYieldTermStructure
    //

    @Override
    public DayCounter dayCounter() {
        return originalCurve.currentLink().dayCounter();
    }

    @Override
    public Calendar calendar() {
        return originalCurve.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return originalCurve.currentLink().settlementDays();
    }

    @Override
    public Date referenceDate() {
        return originalCurve.currentLink().referenceDate();
    }

    @Override
    public Date maxDate() {
        final Date original = originalCurve.currentLink().maxDate();
        final Date last = dates[dates.length - 1];
        return original.le(last) ? original : last;
    }

    @Override
    protected double zeroYieldImpl(final double t) {
        final double spreadPrimitive = calcSpreadPrimitive(t);
        final InterestRate zeroRate = originalCurve.currentLink().zeroRate(t, Compounding.Continuous,
                Frequency.NoFrequency, true);
        return zeroRate.rate() + spreadPrimitive;
    }

    //
    // overrides AbstractTermStructure (observer)
    //

    @Override
    public void update() {
        if ( !originalCurve.empty() ) {
            updateInterpolation();
            // Mirror C++: YieldTermStructure::update() — Java's
            // ZeroYieldStructure has no override, so we forward to the
            // AbstractTermStructure base via super.update().
            super.update();
        } else {
            // Original curve not yet linked: skip referenceDate() lookup
            // (which would NPE) and just notify observers via the base
            // AbstractTermStructure.update().
            super.update();
        }
    }

    //
    // private helpers
    //

    /**
     * C++ {@code calcSpread} — used by the zero-yield extrapolation logic outside the pillar range.
     */
    @SuppressWarnings( "unused" )
    private double calcSpread(final double t) {
        if ( t <= times[0] ) {
            return spreads[0].currentLink().value();
        } else if ( t >= times[times.length - 1] ) {
            return spreads[spreads.length - 1].currentLink().value();
        } else {
            return interpolator.op(t, true);
        }
    }

    /**
     * C++ {@code calcSpreadPrimitive} — returns the time-average of the forward-rate spread up to time t.
     * Outside the right-most pillar, extends with the flat-forward spread (i.e. the right-most spread value).
     */
    private double calcSpreadPrimitive(final double t) {
        if ( t == 0.0 ) {
            return calcSpread(0.0);
        }
        final double integral;
        if ( t <= times[times.length - 1] ) {
            integral = interpolator.primitive(t, true);
        } else {
            integral = interpolator.primitive(times[times.length - 1], true)
                    + spreads[spreads.length - 1].currentLink().value() * (t - times[times.length - 1]);
        }
        return integral / t;
    }

    private void updateInterpolation() {
        for ( int i = 0; i < dates.length; ++i ) {
            times[i] = timeFromReference(dates[i]);
            spreadValues[i] = spreads[i].currentLink().value();
        }
        interpolator = factory.interpolate(new Array(times), new Array(spreadValues));
        interpolator.update();
    }
}
