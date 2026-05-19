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
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVarianceTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Black volatility curve modelled as variance curve, with quoted vol inputs.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/extendedblackvariancecurve.{hpp,cpp}}. Similar to
 * {@link org.jquantlib.termstructures.volatilities.BlackVarianceCurve} but extends it to use quoted volatilities
 * ({@link Quote} instances) rather than raw doubles.
 */
public class ExtendedBlackVarianceCurve extends BlackVarianceTermStructure {

    private final DayCounter dayCounter_;
    private final Date maxDate_;
    private final Quote[] volatilities_;
    private final double[] times_;
    private final double[] variances_;
    private final Interpolation.Interpolator factory_;
    private final boolean forceMonotoneVariance_;
    private Interpolation varianceCurve_;

    public ExtendedBlackVarianceCurve(final Date referenceDate, final Date[] dates, final Quote[] volatilities,
            final DayCounter dayCounter, final boolean forceMonotoneVariance) {
        super(referenceDate);
        QL.require(dates.length == volatilities.length, "size mismatch between dates and volatilities");
        QL.require(dates[0].gt(referenceDate), "cannot have dates_[0] <= referenceDate");

        this.dayCounter_ = dayCounter;
        this.maxDate_ = dates[dates.length - 1];
        this.volatilities_ = volatilities.clone();
        this.forceMonotoneVariance_ = forceMonotoneVariance;

        this.variances_ = new double[dates.length + 1];
        this.times_ = new double[dates.length + 1];
        this.times_[0] = 0.0;
        for ( int j = 1; j <= dates.length; ++j ) {
            times_[j] = timeFromReference(dates[j - 1]);
            QL.require(times_[j] > times_[j - 1], "dates must be sorted unique!");
        }

        setVariances();
        this.factory_ = new Linear();
        setInterpolation(factory_);

        // Register as observer of each quote — invalidates and recalculates on change.
        for ( final Quote q : volatilities_ ) {
            if ( q != null ) {
                q.addObserver(new Observer() {
                    @Override
                    public void update() {
                        ExtendedBlackVarianceCurve.this.update();
                    }
                });
            }
        }
    }

    public ExtendedBlackVarianceCurve(final Date referenceDate, final Date[] dates, final Quote[] volatilities,
            final DayCounter dayCounter) {
        this(referenceDate, dates, volatilities, dayCounter, true);
    }

    private void setVariances() {
        variances_[0] = 0.0;
        for ( int j = 1; j <= volatilities_.length; ++j ) {
            final double sigma = volatilities_[j - 1].value();
            variances_[j] = times_[j] * sigma * sigma;
            QL.require(variances_[j] >= variances_[j - 1] || !forceMonotoneVariance_,
                    "variance must be non-decreasing");
        }
    }

    /** Replace the variance interpolator (mirrors C++ template setInterpolation). */
    public final void setInterpolation(final Interpolation.Interpolator factory) {
        varianceCurve_ = factory.interpolate(new Array(times_), new Array(variances_));
        varianceCurve_.enableExtrapolation();
        varianceCurve_.update();
        notifyObservers();
    }

    @Override
    public void update() {
        setVariances();
        if ( varianceCurve_ != null ) {
            varianceCurve_.update();
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
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxStrike() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    protected double blackVarianceImpl(final double t, final double strike) {
        if ( t <= times_[times_.length - 1] ) {
            return varianceCurve_.op(t, true);
        } else {
            return varianceCurve_.op(times_[times_.length - 1], true) * t / times_[times_.length - 1];
        }
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< ExtendedBlackVarianceCurve > v = (pv != null) ? pv.visitor(
                this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
