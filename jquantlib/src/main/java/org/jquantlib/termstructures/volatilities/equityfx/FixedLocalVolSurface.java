/*
 Copyright (C) 2015 Johannes Göttker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen
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
package org.jquantlib.termstructures.volatilities.equityfx;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Local-volatility surface from a caller-supplied fixed grid of values.
 *
 * <p>Java port of v1.42.1
 * {@code ql/termstructures/volatility/equityfx/fixedlocalvolsurface.{hpp,cpp}}.
 *
 * <p>Three constructors mirror C++:
 * <ul>
 *   <li>Date-vector ctor — converts dates to time-fractions internally</li>
 *   <li>Time-vector ctor with a single shared strike vector</li>
 *   <li>Time-vector ctor with per-time strike vectors (smile-fan layout)</li>
 * </ul>
 *
 * <p>Vol is interpolated linearly across strikes per time slice; across
 * time it is linearly interpolated between adjacent slices. Below the
 * first or above the last strike the behaviour is controlled by
 * {@link Extrapolation} (constant clamp or interpolator-default).
 *
 * @author Phase 5h.5-RND-b port
 */
public class FixedLocalVolSurface extends LocalVolTermStructure {

    public enum Extrapolation { ConstantExtrapolation, InterpolatorDefaultExtrapolation }

    private final Date maxDate_;
    private final List<Double> times_;
    private final Matrix localVolMatrix_;
    /** One strike vector per time slice (may be the same shared vector). */
    private final List<double[]> strikes_;
    private final List<Interpolation> localVolInterpol_;
    private final Extrapolation lowerExtrapolation_;
    private final Extrapolation upperExtrapolation_;

    /**
     * Date-based constructor — strikes shared across all dates.
     * The vol matrix is {@code rows=strikes.size()} x {@code cols=dates.size()}.
     */
    public FixedLocalVolSurface(final Date referenceDate,
                                final List<Date> dates,
                                final double[] strikes,
                                final Matrix localVolMatrix,
                                final DayCounter dayCounter,
                                final Extrapolation lowerExtrapolation,
                                final Extrapolation upperExtrapolation) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.Following, dayCounter);

        QL.require(!dates.isEmpty(), "empty date vector");
        QL.require(dates.get(0).ge(referenceDate), "cannot have dates[0] < referenceDate");

        this.maxDate_ = dates.get(dates.size() - 1);
        this.localVolMatrix_ = localVolMatrix;
        this.times_ = new ArrayList<Double>(dates.size());
        for (final Date d : dates) {
            this.times_.add(timeFromReference(d));
        }
        // Same strike vector for every date — but we need an Array per date so
        // that downstream interpolators can be indexed uniformly.
        final double[] sharedStrikes = strikes.clone();
        this.strikes_ = new ArrayList<double[]>(dates.size());
        for (int i = 0; i < dates.size(); ++i) {
            this.strikes_.add(sharedStrikes);
        }
        this.localVolInterpol_ = new ArrayList<Interpolation>(dates.size());
        for (int i = 0; i < dates.size(); ++i) {
            this.localVolInterpol_.add(null);
        }
        this.lowerExtrapolation_ = lowerExtrapolation;
        this.upperExtrapolation_ = upperExtrapolation;

        checkSurface();
        setLinearInterpolation();
    }

    /**
     * Time-based constructor — strikes shared across all times.
     * The maxDate is approximated via {@link #yearFractionToDate}.
     */
    public FixedLocalVolSurface(final Date referenceDate,
                                final double[] times,
                                final double[] strikes,
                                final Matrix localVolMatrix,
                                final DayCounter dayCounter,
                                final Extrapolation lowerExtrapolation,
                                final Extrapolation upperExtrapolation) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.Following, dayCounter);

        QL.require(times.length > 0, "empty time vector");
        QL.require(times[0] >= 0.0, "cannot have times[0] < 0");

        this.maxDate_ = yearFractionToDate(dayCounter, referenceDate, times[times.length - 1]);
        this.times_ = new ArrayList<Double>(times.length);
        for (final double t : times) {
            this.times_.add(Double.valueOf(t));
        }
        this.localVolMatrix_ = localVolMatrix;
        final double[] sharedStrikes = strikes.clone();
        this.strikes_ = new ArrayList<double[]>(times.length);
        for (int i = 0; i < times.length; ++i) {
            this.strikes_.add(sharedStrikes);
        }
        this.localVolInterpol_ = new ArrayList<Interpolation>(times.length);
        for (int i = 0; i < times.length; ++i) {
            this.localVolInterpol_.add(null);
        }
        this.lowerExtrapolation_ = lowerExtrapolation;
        this.upperExtrapolation_ = upperExtrapolation;

        checkSurface();
        setLinearInterpolation();
    }

    /**
     * Time-based constructor — per-time strike vectors (smile-fan layout).
     * {@code strikes.get(j)} holds the strike vector for time {@code times[j]}.
     */
    public FixedLocalVolSurface(final Date referenceDate,
                                final double[] times,
                                final List<double[]> strikes,
                                final Matrix localVolMatrix,
                                final DayCounter dayCounter,
                                final Extrapolation lowerExtrapolation,
                                final Extrapolation upperExtrapolation) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.Following, dayCounter);

        QL.require(times.length > 0, "empty time vector");
        QL.require(times[0] >= 0.0, "cannot have times[0] < 0");
        QL.require(times.length == strikes.size(), "need strikes for every time step");

        this.maxDate_ = yearFractionToDate(dayCounter, referenceDate, times[times.length - 1]);
        this.times_ = new ArrayList<Double>(times.length);
        for (final double t : times) {
            this.times_.add(Double.valueOf(t));
        }
        this.localVolMatrix_ = localVolMatrix;
        this.strikes_ = new ArrayList<double[]>(times.length);
        for (final double[] s : strikes) {
            this.strikes_.add(s.clone());
        }
        this.localVolInterpol_ = new ArrayList<Interpolation>(times.length);
        for (int i = 0; i < times.length; ++i) {
            this.localVolInterpol_.add(null);
        }
        this.lowerExtrapolation_ = lowerExtrapolation;
        this.upperExtrapolation_ = upperExtrapolation;

        checkSurface();
        setLinearInterpolation();
    }

    @Override
    public Date maxDate()    { return maxDate_; }

    @Override
    public double maxTime()  { return times_.get(times_.size() - 1); }

    @Override
    public double minStrike() {
        final double[] s = strikes_.get(strikes_.size() - 1);
        return s[0];
    }

    @Override
    public double maxStrike() {
        final double[] s = strikes_.get(strikes_.size() - 1);
        return s[s.length - 1];
    }

    /**
     * Reset the per-time-slice interpolation to {@link LinearInterpolation}.
     * Mirrors C++ {@code setInterpolation<Linear>()}; the template variant
     * accepting other interpolators is not (yet) ported.
     */
    public final void setLinearInterpolation() {
        for (int j = 0; j < times_.size(); ++j) {
            final double[] xs = strikes_.get(j);
            final Array xa = new Array(xs.length);
            for (int k = 0; k < xs.length; ++k) {
                xa.set(k, xs[k]);
            }
            // Pull the j-th column from the matrix
            final Array ya = new Array(xs.length);
            for (int k = 0; k < xs.length; ++k) {
                ya.set(k, localVolMatrix_.get(k, j));
            }
            final LinearInterpolation lin = new LinearInterpolation(xa, ya);
            lin.update();
            localVolInterpol_.set(j, lin);
        }
        notifyObservers();
    }

    @Override
    protected double localVolImpl(final double tIn, final double strike) {
        // Clamp time into the supported range.
        final double t = Math.min(times_.get(times_.size() - 1),
                Math.max(tIn, times_.get(0)));

        // lower_bound — smallest index with times_[idx] >= t
        int idx = lowerBound(t);
        if (idx == times_.size()) {
            idx = times_.size() - 1;
        }

        if (Closeness.isCloseEnough(t, times_.get(idx).doubleValue())) {
            final double[] sk = strikes_.get(idx);
            if (sk[0] < sk[sk.length - 1]) {
                return localVolInterpol_.get(idx).op(strike, true);
            } else {
                return localVolMatrix_.get(localVolMatrix_.rows() / 2, idx);
            }
        } else {
            // idx is the upper bracket; idx-1 is the lower.
            double earlierStrike = strike, laterStrike = strike;
            if (lowerExtrapolation_ == Extrapolation.ConstantExtrapolation) {
                final double[] skPrev = strikes_.get(idx - 1);
                final double[] skCurr = strikes_.get(idx);
                if (strike < skPrev[0]) earlierStrike = skPrev[0];
                if (strike < skCurr[0]) laterStrike   = skCurr[0];
            }
            if (upperExtrapolation_ == Extrapolation.ConstantExtrapolation) {
                final double[] skPrev = strikes_.get(idx - 1);
                final double[] skCurr = strikes_.get(idx);
                if (strike > skPrev[skPrev.length - 1]) earlierStrike = skPrev[skPrev.length - 1];
                if (strike > skCurr[skCurr.length - 1]) laterStrike   = skCurr[skCurr.length - 1];
            }

            final double[] skPrev = strikes_.get(idx - 1);
            final double earlyVol = (skPrev[0] < skPrev[skPrev.length - 1])
                    ? localVolInterpol_.get(idx - 1).op(earlierStrike, true)
                    : localVolMatrix_.get(localVolMatrix_.rows() / 2, idx - 1);
            final double laterVol = localVolInterpol_.get(idx).op(laterStrike, true);

            return earlyVol
                    + (laterVol - earlyVol) / (times_.get(idx) - times_.get(idx - 1))
                      * (t - times_.get(idx - 1));
        }
    }

    private void checkSurface() {
        QL.require(times_.size() == localVolMatrix_.cols(),
                "mismatch between date vector and vol matrix columns");
        for (final double[] strike : strikes_) {
            QL.require(strike.length == localVolMatrix_.rows(),
                    "mismatch between money-strike vector and vol matrix rows");
        }
        for (int j = 1; j < times_.size(); ++j) {
            QL.require(times_.get(j).doubleValue() > times_.get(j - 1).doubleValue(),
                    "dates must be sorted unique!");
        }
        for (final double[] strike : strikes_) {
            for (int j = 1; j < strike.length; ++j) {
                QL.require(strike[j] >= strike[j - 1], "strikes must be sorted");
            }
        }
    }

    private int lowerBound(final double t) {
        // Linear scan — times_ is short.
        for (int k = 0; k < times_.size(); ++k) {
            if (times_.get(k).doubleValue() >= t) {
                return k;
            }
        }
        return times_.size();
    }

    /**
     * Convert a year-fraction {@code t} (with respect to the supplied day
     * counter and reference date) into a {@link Date} approximation.
     *
     * <p>Java port of v1.42.1 {@code ql/time/daycounters/yearfractiontodate.cpp}.
     * Two-step refinement: initial guess at {@code +round(t * 365.25) days},
     * then refine by year/month/day until the year-fraction is within one day.
     */
    public static Date yearFractionToDate(final DayCounter dayCounter,
                                          final Date referenceDate,
                                          final double tIn) {
        Date guessDate = referenceDate.add(new Period((int) Math.round(tIn * 365.25), TimeUnit.Days));
        double guessTime = dayCounter.yearFraction(referenceDate, guessDate);

        guessDate = guessDate.add(new Period((int) Math.round((tIn - guessTime) * 365.25),
                TimeUnit.Days));
        guessTime = dayCounter.yearFraction(referenceDate, guessDate);

        if (Closeness.isCloseEnough(guessTime, tIn)) {
            return guessDate;
        }

        final int searchDirection = (tIn - guessTime) >= 0.0 ? 1 : -1;
        // Bias by ~100 epsilons in the search direction (mirrors C++ tweak).
        final double t = tIn + searchDirection * 100 * org.jquantlib.math.Constants.QL_EPSILON;

        for (final TimeUnit u : new TimeUnit[]{TimeUnit.Years, TimeUnit.Months, TimeUnit.Days}) {
            Date next;
            while (true) {
                next = guessDate.add(new Period(searchDirection, u));
                final double yf = dayCounter.yearFraction(referenceDate, next);
                if (searchDirection * (yf - t) >= 0.0) break;
                guessDate = next;
            }
        }

        guessTime = dayCounter.yearFraction(referenceDate, guessDate);
        final Date plusOne = guessDate.add(new Period(searchDirection, TimeUnit.Days));
        if (Closeness.isCloseEnough(guessTime, tIn)
                || Math.abs(dayCounter.yearFraction(referenceDate, plusOne) - tIn)
                   > Math.abs(guessTime - tIn)) {
            return guessDate;
        } else {
            return plusOne;
        }
    }
}
