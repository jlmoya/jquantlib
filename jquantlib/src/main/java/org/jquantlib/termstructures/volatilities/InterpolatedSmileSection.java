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
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 François du Vignaud
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.Date;

/**
 * Interpolated smile section over a user-supplied 1-D interpolator across
 * strikes. C++ uses a template parameter ({@code InterpolatedSmileSection<Cubic>})
 * — Java single-dispatch translates to a runtime
 * {@link Interpolation.Interpolator} factory.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/interpolatedsmilesection.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>Template specialisation flattened: ctor takes
 *      {@code Interpolation.Interpolator interpolator} (default {@link
 *      org.jquantlib.math.interpolations.factories.Cubic}); the chosen
 *      factory is invoked once in {@link #performCalculations()} after vols
 *      are recomputed from the std-dev handles.</li>
 *  <li>{@code LazyObject} dual-inheritance composed inline — see
 *      {@link StrippedOptionletAdapter} for the same pattern (calculated_
 *      flag + on-demand performCalculations()).</li>
 *  <li>{@code stdDev} is internally always carried as a {@link Quote}
 *      handle; the raw-{@code double[]} ctor variant wraps each input into
 *      a {@link SimpleQuote}, exactly mirroring C++ lines 147-149.</li>
 *  <li>{@code flatStrikeExtrapolation_} controls clamp-at-bounds behaviour
 *      (mirrors C++ lines 238-244).</li>
 * </ul>
 */
public class InterpolatedSmileSection extends SmileSection {

    //
    // private fields
    //

    private final double exerciseTimeSquareRoot_;
    private final double[] strikes_;
    private final Handle<Quote>[] stdDevHandles_;
    private final Handle<Quote> atmLevel_;
    private final double[] vols_;
    private final Interpolator interpolator_;
    private final boolean flatStrikeExtrapolation_;

    /** Lazy-calculation flag (composed; Java cannot multi-inherit LazyObject). */
    private boolean calculated_;
    /** Built lazily by performCalculations(). */
    private Interpolation interpolation_;

    //
    // public constructors — handle-based
    //

    /**
     * Time-based ctor (handle-of-Quote stdDevs). Mirrors C++ lines 104-127.
     */
    public InterpolatedSmileSection(
            final double timeToExpiry,
            final double[] strikes,
            final Handle<Quote>[] stdDevHandles,
            final Handle<Quote> atmLevel,
            final Interpolator interpolator,
            final DayCounter dc,
            final VolatilityType type,
            final double shift,
            final boolean flatStrikeExtrapolation) {
        super(timeToExpiry, dc, type, shift);
        this.strikes_ = strikes.clone();
        this.stdDevHandles_ = stdDevHandles.clone();
        this.atmLevel_ = atmLevel;
        this.vols_ = new double[stdDevHandles.length];
        this.exerciseTimeSquareRoot_ = Math.sqrt(exerciseTime());
        this.interpolator_ = interpolator;
        this.flatStrikeExtrapolation_ = flatStrikeExtrapolation;
        for (final Handle<Quote> h : stdDevHandles_) {
            h.addObserver(this);
        }
        atmLevel_.addObserver(this);
        checkStrikes();
        // Defer interpolator construction to performCalculations() after vols
        // are populated from handles (C++ does both in ctor; we split because
        // the build needs vols to have valid initial values).
    }

    /**
     * Time-based ctor (raw-double stdDevs). Mirrors C++ lines 130-157 —
     * wraps each value in a SimpleQuote.
     */
    @SuppressWarnings("unchecked")
    public InterpolatedSmileSection(
            final double timeToExpiry,
            final double[] strikes,
            final double[] stdDevs,
            final double atmLevel,
            final Interpolator interpolator,
            final DayCounter dc,
            final VolatilityType type,
            final double shift,
            final boolean flatStrikeExtrapolation) {
        super(timeToExpiry, dc, type, shift);
        this.strikes_ = strikes.clone();
        this.stdDevHandles_ = (Handle<Quote>[]) new Handle[stdDevs.length];
        for (int i = 0; i < stdDevs.length; ++i) {
            this.stdDevHandles_[i] = new Handle<Quote>(new SimpleQuote(stdDevs[i]));
        }
        this.atmLevel_ = new Handle<Quote>(new SimpleQuote(atmLevel));
        this.vols_ = new double[stdDevs.length];
        this.exerciseTimeSquareRoot_ = Math.sqrt(exerciseTime());
        this.interpolator_ = interpolator;
        this.flatStrikeExtrapolation_ = flatStrikeExtrapolation;
        checkStrikes();
    }

    /**
     * Date-based ctor (handle-of-Quote stdDevs). Mirrors C++ lines 159-184.
     */
    public InterpolatedSmileSection(
            final Date d,
            final double[] strikes,
            final Handle<Quote>[] stdDevHandles,
            final Handle<Quote> atmLevel,
            final DayCounter dc,
            final Interpolator interpolator,
            final Date referenceDate,
            final VolatilityType type,
            final double shift,
            final boolean flatStrikeExtrapolation) {
        super(d, dc, referenceDate, type, shift);
        this.strikes_ = strikes.clone();
        this.stdDevHandles_ = stdDevHandles.clone();
        this.atmLevel_ = atmLevel;
        this.vols_ = new double[stdDevHandles.length];
        this.exerciseTimeSquareRoot_ = Math.sqrt(exerciseTime());
        this.interpolator_ = interpolator;
        this.flatStrikeExtrapolation_ = flatStrikeExtrapolation;
        for (final Handle<Quote> h : stdDevHandles_) {
            h.addObserver(this);
        }
        atmLevel_.addObserver(this);
        checkStrikes();
    }

    /**
     * Date-based ctor (raw-double stdDevs). Mirrors C++ lines 186-215.
     */
    @SuppressWarnings("unchecked")
    public InterpolatedSmileSection(
            final Date d,
            final double[] strikes,
            final double[] stdDevs,
            final double atmLevel,
            final DayCounter dc,
            final Interpolator interpolator,
            final Date referenceDate,
            final VolatilityType type,
            final double shift,
            final boolean flatStrikeExtrapolation) {
        super(d, dc, referenceDate, type, shift);
        this.strikes_ = strikes.clone();
        this.stdDevHandles_ = (Handle<Quote>[]) new Handle[stdDevs.length];
        for (int i = 0; i < stdDevs.length; ++i) {
            this.stdDevHandles_[i] = new Handle<Quote>(new SimpleQuote(stdDevs[i]));
        }
        this.atmLevel_ = new Handle<Quote>(new SimpleQuote(atmLevel));
        this.vols_ = new double[stdDevs.length];
        this.exerciseTimeSquareRoot_ = Math.sqrt(exerciseTime());
        this.interpolator_ = interpolator;
        this.flatStrikeExtrapolation_ = flatStrikeExtrapolation;
        checkStrikes();
    }


    //
    // SmileSection overrides
    //

    @Override
    public double minStrike() {
        return strikes_[0];
    }

    @Override
    public double maxStrike() {
        return strikes_[strikes_.length - 1];
    }

    @Override
    public double atmLevel() {
        return atmLevel_.currentLink().value();
    }

    @Override
    protected double varianceImpl(final double strike) {
        calculate();
        final double v = volatilityImpl(strike);
        return v * v * exerciseTime();
    }

    @Override
    protected double volatilityImpl(final double strike) {
        calculate();
        if (flatStrikeExtrapolation_) {
            if (strike < minStrike()) {
                return interpolation_.op(minStrike(), true);
            } else if (strike > maxStrike()) {
                return interpolation_.op(maxStrike(), true);
            }
        }
        return Math.max(interpolation_.op(strike, true), 0.0);
    }

    //
    // Observer override (lazy invalidate on quote update)
    //

    @Override
    public void update() {
        super.update();
        // Phase 5e.5b-CFC-d-158: align to C++ LazyObject::update(), which
        // forwards observer notifications when the cached state is
        // invalidated. Without this, downstream observables (e.g.
        // PiecewiseBlackVarianceSurface) never propagate quote changes
        // sourced from this smile section, breaking observer chains.
        final boolean wasCalculated = calculated_;
        calculated_ = false;
        if (wasCalculated) {
            notifyObservers();
        }
    }

    //
    // composed lazy-object plumbing
    //

    private void calculate() {
        if (!calculated_) {
            calculated_ = true;
            try {
                performCalculations();
            } catch (final RuntimeException e) {
                calculated_ = false;
                throw e;
            }
        }
    }

    /**
     * Mirrors C++ {@code performCalculations()}: refresh vols from handles,
     * then (re)build the interpolation. The C++ code rebuilds a single
     * Interpolation object per query; we keep one and recompute on update.
     */
    private void performCalculations() {
        for (int i = 0; i < stdDevHandles_.length; ++i) {
            vols_[i] = stdDevHandles_[i].currentLink().value() / exerciseTimeSquareRoot_;
        }
        // Build (or rebuild) the interpolator. In C++ Interpolation.update()
        // is called against the existing wrapper; in Java the simplest
        // equivalent is to construct a fresh Interpolation each refresh.
        interpolation_ = interpolator_.interpolate(new Array(strikes_), new Array(vols_));
    }

    private void checkStrikes() {
        for (int i = 1; i < strikes_.length; ++i) {
            QL.require(strikes_[i] >= strikes_[i - 1],
                    "Strikes have to be sorted in ascending order");
        }
    }
}
