/*
 Copyright (C) 2008 Richard Gomes

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
 Copyright (C) 2002, 2003, 2004 Ferdinando Ametrano
 Copyright (C) 2003, 2004 StatPro Italia srl

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

package org.jquantlib.termstructures.volatilities;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.interpolations.factories.Bilinear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.termstructures.BlackVarianceTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * This class calculates time/strike dependent Black volatilities using as input a matrix of Black volatilities observed
 * in the market.
 *
 * <p>The calculation is performed interpolating on the variance surface. Bilinear
 * interpolation is used as default; this can be changed by the
 * {@link #setInterpolation(Interpolation2D.Interpolator2D)} method.
 *
 * <p>Mirrors C++ v1.42.1
 * {@code ql/termstructures/volatility/equityfx/blackvariancesurface.{hpp,cpp}}. The ctor pads the time axis with a
 * leading {@code t=0.0} sentinel so the variance surface interpolates correctly down to the reference date. The
 * variance matrix is padded along the time (column) axis with a leading column of zeros; the strike (row) axis is NOT
 * padded.
 *
 * @author Richard Gomes
 */
public class BlackVarianceSurface extends BlackVarianceTermStructure {

    private final DayCounter dayCounter;

    //
    // private fields
    //
    private final Date maxDate;
    private final /* @Time */ Array times;
    private final /* @Real */ Array strikes;
    private final /* @Variance */ Matrix variances;
    private final Extrapolation lowerExtrapolation;
    private final Extrapolation upperExtrapolation;
    private Interpolation2D varianceSurface;
    public BlackVarianceSurface(final Date referenceDate, final Date[] dates, final/* @Real */ Array strikes,
            final/* @Volatility */ Matrix blackVolMatrix, final DayCounter dayCounter) {

        this(referenceDate, dates, strikes, blackVolMatrix, dayCounter, Extrapolation.InterpolatorDefaultExtrapolation,
                Extrapolation.InterpolatorDefaultExtrapolation);
    }

    //
    // public constructors
    //

    public BlackVarianceSurface(final Date referenceDate, final Date[] dates, final/* @Real */ Array strikes,
            final/* @Volatility */ Matrix blackVolMatrix, final DayCounter dayCounter,
            final Extrapolation lowerExtrapolation, final Extrapolation upperExtrapolation) {

        super(referenceDate);
        QL.require(dates.length == blackVolMatrix.columns(), "mismatch between date vector and vol matrix columns");
        QL.require(strikes.size() == blackVolMatrix.rows(), "mismatch between money-strike vector and vol matrix rows");
        // Mirror C++: QL_REQUIRE(dates[0] >= referenceDate, "cannot have dates[0] < referenceDate")
        QL.require(dates[0].ge(referenceDate), "cannot have dates[0] < referenceDate");

        this.dayCounter = dayCounter;
        this.maxDate = dates[dates.length - 1];
        this.strikes = strikes.clone();
        this.lowerExtrapolation = lowerExtrapolation;
        this.upperExtrapolation = upperExtrapolation;

        // C++ pads only the time axis:
        //   times_ = vector<Time>(dates.size()+1); times_[0] = 0.0;
        //   variances_ = Matrix(strikes.size(), dates.size()+1);
        //   variances_[i][0] = 0.0 for all i;
        // No padding of the strike axis -- prior Java padded {@code this.strikes}
        // to {@code strikes.size()+1} without padding the variance matrix, which
        // produced an off-by-one strike/variance mapping. Bug fix mirrors C++
        // exactly.
        this.times = new Array(dates.length + 1);
        this.times.set(0, 0.0);
        this.variances = new Matrix(this.strikes.size(), dates.length + 1);
        for ( int i = 0; i < blackVolMatrix.rows(); i++ ) {
            this.variances.set(i, 0, 0.0);
        }

        for ( int j = 1; j <= blackVolMatrix.columns(); j++ ) {
            times.set(j, timeFromReference(dates[j - 1]));
            QL.require(times.get(j) > times.get(j - 1), "dates must be sorted unique!");
            for ( int i = 0; i < blackVolMatrix.rows(); i++ ) {
                final double vol = blackVolMatrix.get(i, j - 1);
                final double variance = times.get(j) * vol * vol;
                variances.set(i, j, variance);
            }
        }

        // default: bilinear interpolation (mirrors C++ setInterpolation<Bilinear>()).
        setInterpolation(new Bilinear());
    }

    /**
     * Re-interpolate the variance surface using a 2-D interpolation factory. Mirrors C++ template
     * {@code setInterpolation<Interpolator>()}.
     *
     * @param i 2-D interpolator factory; if {@code null}, falls back to {@link Bilinear} (preserves the prior Java
     *          null-safe contract).
     */
    public void setInterpolation(final Interpolation2D.Interpolator2D i) {
        final Interpolation2D.Interpolator2D factory = (i != null) ? i : new Bilinear();
        varianceSurface = factory.interpolate(times, strikes, variances);
        varianceSurface.update();
        notifyObservers();
    }

    //
    // public methods
    //

    /**
     * Deprecated overload kept for backward compatibility with callers that pass the 1-D
     * {@link Interpolation.Interpolator} type (e.g. legacy samples). The argument is ignored; the surface falls back to
     * {@link Bilinear}. Real callers should use {@link #setInterpolation(Interpolation2D.Interpolator2D)}.
     *
     * @deprecated 2-D surfaces require a 2-D factory; this overload only accepts {@code null} or a 1-D dummy and
     * silently falls back to Bilinear. Migrate to {@link #setInterpolation(Interpolation2D.Interpolator2D)}.
     */
    @Deprecated
    public void setInterpolation(final Interpolation.Interpolator ignored) {
        setInterpolation((Interpolation2D.Interpolator2D) null);
    }

    @Override
    public final DayCounter dayCounter() {
        return dayCounter;
    }

    //
    // Overrides TermStructure
    //

    @Override
    public final Date maxDate() {
        return maxDate;
    }

    @Override
    public final /* @Real */ double minStrike() {
        return strikes.first();
    }

    //
    // Overrides BlackVolTermStructure
    //

    @Override
    public final /* @Real */ double maxStrike() {
        return strikes.last();
    }

    @Override
    protected final/* @Variance */double blackVarianceImpl(/* @Time */final double t, /* @Real */
            double strike) /* @ReadOnly */ {

        if ( t == 0.0 ) {
            return 0.0;
        }

        // enforce constant extrapolation when required
        if ( strike < strikes.first() && lowerExtrapolation == Extrapolation.ConstantExtrapolation ) {
            strike = strikes.first();
        }
        if ( strike > strikes.last() && upperExtrapolation == Extrapolation.ConstantExtrapolation ) {
            strike = strikes.last();
        }

        if ( t <= times.last() ) {
            return varianceSurface.op(t, strike, true);
        } else {
            // t>times_.back() || extrapolate
            /* @Time */
            final double lastTime = times.last();
            return varianceSurface.op(lastTime, strike, true) * t / lastTime;
        }
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< BlackVarianceSurface > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }

    //
    // implements PolymorphicVisitable
    //

    public enum Extrapolation {
        ConstantExtrapolation, InterpolatorDefaultExtrapolation
    }

}
