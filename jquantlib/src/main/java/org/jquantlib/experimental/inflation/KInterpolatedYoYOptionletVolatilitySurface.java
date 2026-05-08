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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.experimental.inflation;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.inflation.InflationCapFloorEngine;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Pair;

/**
 * K-interpolated year-on-year inflation optionlet volatility surface.
 *
 * <p>Mirrors C++ v1.42.1
 * {@code QuantLib::KInterpolatedYoYOptionletVolatilitySurface}
 * ({@code ql/experimental/inflation/kinterpolatedyoyoptionletvolatilitysurface.hpp}).
 *
 * <p>The stripper provides curves in the T direction along each K. We don't
 * know whether this is interpolating or fitting in T. K-direction
 * interpolation is not model fitting.
 *
 * <p>Java port note: the C++ class is templated on {@code Interpolator1D}.
 * Java uses the same {@code Class<I>}-factory pattern as
 * {@link InterpolatedYoYOptionletVolatilityCurve}.
 *
 * @param <I> Interpolator type (e.g. {@code Linear})
 *
 * @author JQuantLib migration team (Phase 2s Track B)
 */
public class KInterpolatedYoYOptionletVolatilitySurface<I extends Interpolator>
        extends YoYOptionletVolatilitySurface {

    private final YoYCapFloorTermPriceSurfaceLike capFloorPrices_;
    private final InflationCapFloorEngine yoyInflationCouponPricer_;
    private final YoYOptionletStripper yoyOptionletStripper_;

    private final Interpolator factory1D_;
    private final double slope_;

    // mutable cache (mirrors C++ mutable members)
    private boolean lastDateIsSet_ = false;
    private Date lastDate_;
    private Interpolation tempKinterpolation_;
    private Pair<List<Double>, List<Double>> slice_;

    //
    // constructors
    //

    /** Mirrors C++ primary constructor. */
    public KInterpolatedYoYOptionletVolatilitySurface(
            final Class<I> classI,
            final int settlementDays,
            final Calendar cal,
            final BusinessDayConvention bdc,
            final DayCounter dc,
            final Period lag,
            final YoYCapFloorTermPriceSurfaceLike capFloorPrices,
            final InflationCapFloorEngine pricer,
            final YoYOptionletStripper yoyOptionletStripper,
            final double slope,
            final Interpolator interpolator,
            final VolatilityType volType,
            final double displacement) {
        super(settlementDays, cal, bdc, dc, lag,
                capFloorPrices.yoyIndex().frequency(),
                capFloorPrices.yoyIndex().interpolated(),
                volType, displacement);

        QL.require(classI != null, "Generic type for Interpolator is null");
        QL.require(capFloorPrices != null, "capFloorPrices must not be null");
        QL.require(pricer != null, "pricer must not be null");
        QL.require(yoyOptionletStripper != null, "stripper must not be null");

        this.capFloorPrices_ = capFloorPrices;
        this.yoyInflationCouponPricer_ = pricer;
        this.yoyOptionletStripper_ = yoyOptionletStripper;
        this.factory1D_ = interpolator == null
                ? constructInterpolator(classI) : interpolator;
        this.slope_ = slope;

        performCalculations();
    }

    /** Convenience constructor — defaults to ShiftedLognormal/displacement=0. */
    public KInterpolatedYoYOptionletVolatilitySurface(
            final Class<I> classI,
            final int settlementDays,
            final Calendar cal,
            final BusinessDayConvention bdc,
            final DayCounter dc,
            final Period lag,
            final YoYCapFloorTermPriceSurfaceLike capFloorPrices,
            final InflationCapFloorEngine pricer,
            final YoYOptionletStripper yoyOptionletStripper,
            final double slope) {
        this(classI, settlementDays, cal, bdc, dc, lag,
                capFloorPrices, pricer, yoyOptionletStripper, slope,
                /* interpolator */ null,
                VolatilityType.ShiftedLognormal, 0.0);
    }

    private static Interpolator constructInterpolator(final Class<?> klass) {
        if (klass == null) {
            throw new LibraryException("null interpolator class");
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
    // YoYOptionletVolatilitySurface overrides
    //

    @Override
    public Date maxDate() {
        final List<Period> mats = capFloorPrices_.maturities();
        return referenceDate().add(mats.get(mats.size() - 1));
    }

    @Override
    public double minStrike() {
        return capFloorPrices_.strikes().get(0);
    }

    @Override
    public double maxStrike() {
        final List<Double> strikes = capFloorPrices_.strikes();
        return strikes.get(strikes.size() - 1);
    }

    /**
     * Mirrors C++ {@code volatilityImpl(Time, Rate)}: re-derive a date from
     * (length, strike) and dispatch to the date-keyed implementation.
     */
    @Override
    protected double volatilityImpl(final double length, final double strike) {
        final int years = (int) Math.floor(length);
        final int days = (int) Math.floor((length - years) * 365.0);
        final Date d = referenceDate()
                .add(new Period(years, TimeUnit.Years))
                .add(new Period(days, TimeUnit.Days));
        return volatilityImpl(d, strike);
    }

    /**
     * Date-keyed strike interpolation. Mirrors C++
     * {@code volatilityImpl(const Date& d, Rate strike)}.
     */
    public double volatilityImpl(final Date d, final double strike) {
        updateSlice(d);
        if (this.allowsExtrapolation()) {
            this.tempKinterpolation_.enableExtrapolation();
        }
        return tempKinterpolation_.op(strike);
    }

    /**
     * Returns the (strikes, vols) slice at date {@code d} as built by the
     * stripper (cached if {@code d} matches the last call).
     */
    public Pair<List<Double>, List<Double>> Dslice(final Date d) {
        updateSlice(d);
        return slice_;
    }

    //
    // internals
    //

    /** Mirrors C++ {@code performCalculations()} → stripper.initialize(...). */
    protected void performCalculations() {
        // slope is the assumption on the initial caplet volatility change
        yoyOptionletStripper_.initialize(capFloorPrices_,
                yoyInflationCouponPricer_, slope_);
    }

    /** Mirrors C++ {@code updateSlice(d)} — caches per-date stripped slice. */
    private void updateSlice(final Date d) {
        if (!lastDateIsSet_ || !d.eq(lastDate_)) {
            slice_ = yoyOptionletStripper_.slice(d);
            // Build the temp K-interpolation across the slice's strikes/vols.
            final List<Double> ks = slice_.first();
            final List<Double> vs = slice_.second();
            final double[] x = new double[ks.size()];
            final double[] y = new double[vs.size()];
            for (int i = 0; i < ks.size(); ++i) {
                x[i] = ks.get(i);
                y[i] = vs.get(i);
            }
            tempKinterpolation_ = factory1D_.interpolate(
                    new Array(x), new Array(y));
            tempKinterpolation_.update();
            lastDateIsSet_ = true;
            lastDate_ = d;
        }
    }
}
