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

 JQuantLib is based on QuantLib. http://quantlib.org/
*/

/*
 Copyright (C) 2008 Frank Hövermann

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.interpolations.factories.Bilinear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVarianceTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

import java.util.List;

/**
 * Black volatility surface modelled as variance surface, with quoted vol inputs.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/extendedblackvariancesurface.{hpp,cpp}}.
 * Similar to {@link org.jquantlib.termstructures.volatilities.BlackVarianceSurface} but uses {@link Quote} instances
 * instead of raw doubles for the input volatilities.
 *
 * <p>The variance surface is interpolated bilinearly across (time, strike) by default. As with the C++ implementation,
 * the time axis is padded with a leading {@code t=0.0} sentinel and a leading column of zero variances; strike axis is
 * not padded. {@link Extrapolation#ConstantExtrapolation} clamps queries outside {@code [minStrike, maxStrike]} to the
 * boundary strikes; the default {@link Extrapolation#InterpolatorDefaultExtrapolation} delegates to the underlying 2-D
 * interpolator (which, for {@link Bilinear}, performs extrapolation on the boundary cell).
 */
public class ExtendedBlackVarianceSurface extends BlackVarianceTermStructure {

    public enum Extrapolation {
        ConstantExtrapolation, InterpolatorDefaultExtrapolation
    }

    private final DayCounter dayCounter_;
    private final Date maxDate_;
    private final List< Handle< Quote > > volatilities_;
    private final Array strikes_;
    private final Array times_;
    private final Matrix variances_;
    private final Extrapolation lowerExtrapolation_;
    private final Extrapolation upperExtrapolation_;
    private Interpolation2D varianceSurface_;

    public ExtendedBlackVarianceSurface(final Date referenceDate, final Calendar calendar, final Date[] dates,
            final double[] strikes, final List< Handle< Quote > > volatilities, final DayCounter dayCounter) {
        this(referenceDate, calendar, dates, strikes, volatilities, dayCounter,
                Extrapolation.InterpolatorDefaultExtrapolation, Extrapolation.InterpolatorDefaultExtrapolation);
    }

    public ExtendedBlackVarianceSurface(final Date referenceDate, final Calendar calendar, final Date[] dates,
            final double[] strikes, final List< Handle< Quote > > volatilities, final DayCounter dayCounter,
            final Extrapolation lowerExtrapolation, final Extrapolation upperExtrapolation) {
        super(referenceDate, calendar);

        QL.require(dates.length * strikes.length == volatilities.size(),
                "size mismatch between date vector and vol matrix columns and/or between money-strike vector and vol matrix rows");
        QL.require(dates[0].gt(referenceDate), "cannot have dates_[0] <= referenceDate_");

        this.dayCounter_ = dayCounter;
        this.maxDate_ = dates[dates.length - 1];
        this.volatilities_ = volatilities;
        this.strikes_ = new Array(strikes);
        this.lowerExtrapolation_ = lowerExtrapolation;
        this.upperExtrapolation_ = upperExtrapolation;

        // Time axis: pad with a leading t=0.0 sentinel (mirrors C++).
        this.times_ = new Array(dates.length + 1);
        this.times_.set(0, 0.0);
        for ( int j = 1; j <= dates.length; ++j ) {
            times_.set(j, timeFromReference(dates[j - 1]));
            QL.require(times_.get(j) > times_.get(j - 1), "dates must be sorted unique");
        }

        this.variances_ = new Matrix(this.strikes_.size(), dates.length + 1);
        setVariances();
        setInterpolation(new Bilinear());

        // Register as observer of every quote.
        for ( final Handle< Quote > h : volatilities_ ) {
            if ( h != null && h.currentLink() != null ) {
                h.currentLink().addObserver(new Observer() {
                    @Override
                    public void update() {
                        ExtendedBlackVarianceSurface.this.update();
                    }
                });
            }
        }
    }

    /** Mirrors C++ template {@code setInterpolation<Interpolator>()}. */
    public final void setInterpolation(final Interpolation2D.Interpolator2D factory) {
        final Interpolation2D.Interpolator2D f = (factory != null) ? factory : new Bilinear();
        this.varianceSurface_ = f.interpolate(times_, strikes_, variances_);
        this.varianceSurface_.update();
        notifyObservers();
    }

    private void setVariances() {
        // C++ variances_[0][i] = 0.0 for all i — pad t=0 column.
        for ( int i = 0; i < strikes_.size(); ++i ) {
            variances_.set(i, 0, 0.0);
        }
        // C++ iterates j in [1, times_.size()] but with times_.size() ==
        // dates.size()+1, that's an off-by-one in the upper bound (j ==
        // dates.size()+1 would index past the volatilities_ vector). We
        // iterate the valid range [1, dates.size()] which is the same
        // population the C++ code actually fills.
        final int nDates = times_.size() - 1;
        for ( int j = 1; j <= nDates; ++j ) {
            for ( int i = 0; i < strikes_.size(); ++i ) {
                final double sigma = volatilities_.get(i * nDates + j - 1).currentLink().value();
                final double v = times_.get(j) * sigma * sigma;
                variances_.set(i, j, v);
                QL.require(variances_.get(i, j) >= variances_.get(i, j - 1), "variance must be non-decreasing");
            }
        }
    }

    @Override
    public void update() {
        setVariances();
        if ( varianceSurface_ != null ) {
            varianceSurface_.update();
        }
        notifyObservers();
    }

    @Override
    public DayCounter dayCounter() {
        return dayCounter_;
    }

    @Override
    public Date maxDate() {
        return maxDate_;
    }

    @Override
    public double minStrike() {
        return strikes_.first();
    }

    @Override
    public double maxStrike() {
        return strikes_.last();
    }

    @Override
    protected double blackVarianceImpl(final double t, double strike) {
        if ( t == 0.0 ) {
            return 0.0;
        }
        if ( strike < strikes_.first() && lowerExtrapolation_ == Extrapolation.ConstantExtrapolation ) {
            strike = strikes_.first();
        }
        if ( strike > strikes_.last() && upperExtrapolation_ == Extrapolation.ConstantExtrapolation ) {
            strike = strikes_.last();
        }
        if ( t <= times_.last() ) {
            return varianceSurface_.op(t, strike, true);
        }
        // t > times_.back(): linear-in-time extrapolation through the origin.
        final double lastT = times_.last();
        return varianceSurface_.op(lastT, strike, true) * t / lastT;
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< ExtendedBlackVarianceSurface > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
