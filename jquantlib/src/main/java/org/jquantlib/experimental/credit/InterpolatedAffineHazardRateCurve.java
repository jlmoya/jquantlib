/*
 Copyright (C) 2015 Jose Aparicio
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.shortrate.onefactormodels.OneFactorAffineModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default-probability term structure based on interpolation of a deterministic hazard-rate component plus a stochastic
 * one-factor rate.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::InterpolatedAffineHazardRateCurve}
 * ({@code ql/experimental/credit/interpolatedaffinehazardratecurve.hpp}).
 *
 * <p>The hazard rate here refers to the deterministic term structure added
 * on top of the affine model intensity (matching the market implied probabilities). Total probabilities are those of
 * the affine model. Example: CIR++ in credit.
 *
 * <p>The C++ class multiply-inherits from
 * {@code OneFactorAffineSurvivalStructure} and a templated {@code InterpolatedCurve<Interpolator>} mixin. The Java port
 * follows the existing JQuantLib pattern (see {@link org.jquantlib.termstructures.credit.InterpolatedHazardRateCurve})
 * by inlining the times/data/interpolation fields and using the {@code Class<I>} idiom for the interpolator.
 *
 * @param <I> interpolator type (e.g. {@code BackwardFlat}, {@code Linear}).
 */
@SuppressWarnings("deprecation")
public class InterpolatedAffineHazardRateCurve< I extends Interpolator > extends OneFactorAffineSurvivalStructure {

    private final Class< I > classI;
    private final Interpolator interpolator;
    protected Date[] dates;
    protected /*@Time*/ double[] times;
    protected double[] data;
    protected Interpolation interpolation;

    public InterpolatedAffineHazardRateCurve(final Class< I > classI, final Date[] dates, final double[] hazardRates,
            final DayCounter dayCounter, final OneFactorAffineModel model, final Calendar calendar,
            final List< Handle< Quote > > jumps, final List< Date > jumpDates) {
        super(model, dates[0], (calendar != null) ? calendar : new NullCalendar(), dayCounter, jumps, jumpDates);
        QL.require(classI != null, "Generic type for Interpolation is null");
        QL.require(dates != null && hazardRates != null && dates.length == hazardRates.length,
                "dates/data count mismatch");
        this.classI = classI;
        this.interpolator = constructInterpolator(classI);
        this.dates = dates.clone();
        this.data = hazardRates.clone();
        initialize();
    }

    public InterpolatedAffineHazardRateCurve(final Class< I > classI, final Date[] dates, final double[] hazardRates,
            final DayCounter dayCounter, final OneFactorAffineModel model, final Calendar calendar) {
        this(classI, dates, hazardRates, dayCounter, model, calendar, Collections.emptyList(),
                Collections.emptyList());
    }

    public InterpolatedAffineHazardRateCurve(final Class< I > classI, final Date[] dates, final double[] hazardRates,
            final DayCounter dayCounter, final OneFactorAffineModel model) {
        this(classI, dates, hazardRates, dayCounter, model, new NullCalendar(),
                Collections.emptyList(), Collections.emptyList());
    }

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

    private void initialize() {
        QL.require(dates.length >= interpolator.requiredPoints(), "not enough input dates given");
        QL.require(data.length == dates.length, "dates/data count mismatch");
        setupTimes(dates, dates[0], dayCounter());
        setupInterpolation();
        interpolation.update();
    }

    @Override
    public Date maxDate() {
        return dates[dates.length - 1];
    }

    /**
     * Returns the deterministic hazard-rate component (NOT the full {@code E[lambda]}). The full hazard rate is given
     * by the affine model on top.
     */
    @Override
    protected double hazardRateImpl(final @Time double t) {
        if ( t <= times[times.length - 1] ) {
            return interpolation.op(t, true);
        }
        // deterministic flat hazard-rate extrapolation
        return data[data.length - 1];
    }

    @Override
    protected double survivalProbabilityImpl(final @Time double t) {
        // The C++ uses pow(model_->dynamics()->process()->x0(), 2). Use the
        // OneFactorAffineSurvivalStructure pattern (dynamics().shortRate(...))
        // for parity with the existing port: for CIR this equals x0^2.
        final double initValHR = model.dynamics().shortRate(0.0, model.dynamics().process().x0());
        if ( t == 0.0 ) {
            return model.discountBond(0.0, t, initValHR);
        }
        final double tMax = times[times.length - 1];
        final double integral;
        if ( t <= tMax ) {
            integral = interpolation.primitive(t, true);
        } else {
            integral = interpolation.primitive(tMax, true) + data[data.length - 1] * (t - tMax);
        }
        return Math.exp(-integral) * model.discountBond(0.0, t, initValHR);
    }

    @Override
    protected double conditionalSurvivalProbabilityImpl(final @Time double tFwd, final @Time double tTarget,
            final double yVal) {
        QL.require(tFwd <= tTarget, "Probability time in the past.");
        if ( tFwd == 0.0 ) {
            return survivalProbabilityImpl(tTarget);
        }
        if ( tTarget - tFwd == 0.0 ) {
            return 1.0;
        }

        final double tMax = times[times.length - 1];
        final double integralTFwd;
        if ( tFwd <= tMax ) {
            integralTFwd = interpolation.primitive(tFwd, true);
        } else {
            integralTFwd = interpolation.primitive(tMax, true) + data[data.length - 1] * (tFwd - tMax);
        }
        final double integralTP;
        if ( tTarget <= tMax ) {
            integralTP = interpolation.primitive(tTarget, true);
        } else {
            integralTP = interpolation.primitive(tMax, true) + data[data.length - 1] * (tTarget - tMax);
        }

        return Math.exp(-(integralTP - integralTFwd)) * model.discountBond(tFwd, tTarget, yVal);
    }

    public Date[] dates() {
        return dates;
    }

    public double[] times() {
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
}
