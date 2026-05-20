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
 Copyright (C) 2011 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.experimental.inflation;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.solvers1D.FiniteDifferenceNewtonSafe;
import org.jquantlib.time.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Piecewise year-on-year inflation volatility term structure.
 *
 * <p>Mirrors C++ v1.42.1
 * {@code QuantLib::PiecewiseYoYOptionletVolatilityCurve}
 * ({@code ql/experimental/inflation/piecewiseyoyoptionletvolatility.hpp}).
 *
 * <p>Java port note: the C++ class is templated on {@code Interpolator},
 * {@code Bootstrap}, and {@code Traits}. We mirror the
 * {@code <Interpolator, IterativeBootstrap, YoYInflationVolatilityTraits>} specialization (the only one actually used
 * by Track B's {@code InterpolatedYoYOptionletStripper}). The bootstrap loop is inlined here because Java's existing
 * {@code IterativeBootstrap} class is yield-curve specialized.
 *
 * <p>The curve uses a flat smile for bootstrapping at constant K. Most of the
 * work is done in the parent {@link InterpolatedYoYOptionletVolatilityCurve}. Special attention is needed at the start
 * where there is usually no data, only assumptions (encoded by {@code baseYoYVolatility} = base level).
 *
 * @param <I> interpolator type (e.g. {@code Linear})
 * @author JQuantLib migration team (Phase 2s Track B)
 */
public class PiecewiseYoYOptionletVolatility< I extends Interpolator >
        extends InterpolatedYoYOptionletVolatilityCurve< I > {

    /** Default convergence accuracy (matches C++ default 1e-12). */
    public static final double DEFAULT_ACCURACY = 1.0e-12;

    /** Maximum bootstrap iterations (matches C++ {@code Traits::maxIterations} = 25). */
    private static final int MAX_ITERATIONS = 25;

    /**
     * Maximum number of bracket-widening retries per pillar per iteration.
     * <p>C++ {@code IterativeBootstrap} default is {@code maxAttempts_=1}
     * (i.e. no retries); we use a higher default here because the
     * traits' static ±0.02 bracket around the prior pillar can fail
     * to span the root when the YoY caplet pricer is highly non-linear
     * (see {@code testYoYPriceSurfaceToVol}). The retry is gated by
     * widening factors {@code minFactor_=2} / {@code maxFactor_=2}
     * exactly as in C++ {@code iterativebootstrap.hpp:283-286}.
     */
    private static final int MAX_ATTEMPTS = 5;

    /** Widening factor for the lower bracket on retry (mirrors C++ {@code minFactor_=2.0}). */
    private static final double MIN_FACTOR = 2.0;

    /** Widening factor for the upper bracket on retry (mirrors C++ {@code maxFactor_=2.0}). */
    private static final double MAX_FACTOR = 2.0;

    private final List< YoYOptionletHelper > instruments_;
    private final double accuracy_;
    private final double baseYoYVolatility_;
    private boolean calculated_;

    //
    // constructors
    //

    /**
     * Mirrors C++
     * {@code PiecewiseYoYOptionletVolatilityCurve(Natural, Calendar, BDC, DayCounter, Period, Frequency, bool, Rate,
     * Rate, Volatility, vector<Helper>, Real, Interpolator)}.
     */
    public PiecewiseYoYOptionletVolatility(final Class< I > classI, final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc, final Period lag, final Frequency frequency,
            final boolean indexIsInterpolated, final double minStrike, final double maxStrike,
            final double baseYoYVolatility, final List< YoYOptionletHelper > instruments, final double accuracy,
            final Interpolator interpolator) {
        // Bootstrap-only constructor: pillar arrays empty until calculate() runs.
        super(classI, settlementDays, cal, bdc, dc, lag, frequency, indexIsInterpolated, minStrike, maxStrike,
                baseYoYVolatility, interpolator);
        QL.require(instruments != null && !instruments.isEmpty(), "instruments list cannot be empty");
        this.instruments_ = new ArrayList<>(instruments);
        this.accuracy_ = accuracy;
        this.baseYoYVolatility_ = baseYoYVolatility;
        this.calculated_ = false;
    }

    /** Convenience constructor with default accuracy and reflectively-built interpolator. */
    public PiecewiseYoYOptionletVolatility(final Class< I > classI, final int settlementDays, final Calendar cal,
            final BusinessDayConvention bdc, final DayCounter dc, final Period lag, final Frequency frequency,
            final boolean indexIsInterpolated, final double minStrike, final double maxStrike,
            final double baseYoYVolatility, final List< YoYOptionletHelper > instruments) {
        this(classI, settlementDays, cal, bdc, dc, lag, frequency, indexIsInterpolated, minStrike, maxStrike,
                baseYoYVolatility, instruments, DEFAULT_ACCURACY,
                /* let parent build the default interpolator */ null);
    }

    //
    // public bootstrap interface
    //

    /**
     * Forces a re-bootstrap on the next access. C++'s {@code recalculate()} (inherited from {@code LazyObject}) calls
     * {@code performCalculations()} eagerly.
     */
    public void recalculate() {
        calculated_ = false;
        calculate();
    }

    /**
     * Lazy-evaluation entry point.
     *
     * <p>Mirrors C++ {@code LazyObject::calculate()}
     * ({@code ql/patterns/lazyobject.hpp:256-270}). The flag is set
     * BEFORE invoking {@code performCalculations()} (then reset in the
     * catch block on failure) to break recursion when the bootstrap
     * re-enters the curve via the pricer's
     * {@code volatilityImpl(...)} → {@code calculate()} during NPV
     * evaluation of a {@code YoYOptionletHelper}. The previous
     * AFTER-style guard (set the flag after a successful
     * {@code performCalculations()}) recursed indefinitely on the first
     * bootstrap iteration. See C++ comment
     * "{@code prevent infinite recursion in case of bootstrapping}".
     */
    public final void calculate() {
        if ( !calculated_ ) {
            calculated_ = true;  // prevent infinite recursion in case of bootstrapping
            try {
                performCalculations();
            } catch ( final RuntimeException re ) {
                calculated_ = false;
                throw re;
            }
        }
    }

    //
    // YoYOptionletVolatilitySurface overrides — go through calculate()
    //

    @Override
    public Date baseDate() {
        calculate();
        return super.baseDate();
    }

    @Override
    public Date maxDate() {
        calculate();
        return super.maxDate();
    }

    @Override
    public double[] times() {
        calculate();
        return super.times();
    }

    @Override
    public Date[] dates() {
        calculate();
        return super.dates();
    }

    @Override
    public double[] data() {
        calculate();
        return super.data();
    }

    @Override
    protected double volatilityImpl(final double t, final double strike) {
        calculate();
        return super.volatilityImpl(t, strike);
    }

    //
    // bootstrap engine — mirrors IterativeBootstrap::calculate()
    //   specialized for YoYInflationVolatilityTraits.
    //

    /**
     * Mirrors C++ {@code performCalculations()} → {@code bootstrap_.calculate()}.
     *
     * <p>Implements the same iteration as
     * {@link org.jquantlib.termstructures.IterativeBootstrap#calculate} but with {@code YoYInflationVolatilityTraits}
     * inlined:
     * <ul>
     *   <li>{@code initialDate}: {@link #baseDate()} (parent — empty pillars
     *       so we use the base date computed from observation lag, frequency,
     *       and the global eval date)</li>
     *   <li>{@code initialValue}: {@link #baseLevel()} (i.e. baseYoYVolatility)</li>
     *   <li>{@code minValueAfter(i)}: {@code max(0, data[i-1] - 0.02)}</li>
     *   <li>{@code maxValueAfter(i)}: {@code data[i-1] + 0.02}</li>
     *   <li>{@code guess}: {@code 0.005} for first pillar, else {@code 0.002}</li>
     * </ul>
     *
     * <p>Solver selection mirrors C++
     * {@code iterativebootstrap.hpp:319-323}: {@link Brent} as
     * {@code firstSolver_} when {@code !validData} (first iteration),
     * {@link FiniteDifferenceNewtonSafe} as {@code solver_} when
     * {@code validData} (subsequent iterations). A bracket-widening
     * retry loop mirrors {@code iterativebootstrap.hpp:281-344}
     * ({@code maxAttempts_} attempts with {@code min *= MIN_FACTOR} /
     * {@code max *= MAX_FACTOR} per retry).
     */
    protected void performCalculations() {
        final int n = instruments_.size();

        // Sort instruments by latestDate (mirrors BootstrapHelperSorter).
        instruments_.sort((a, b) -> a.latestDate().compareTo(b.latestDate()));

        // Check for distinct maturities.
        for ( int i = 1; i < n; ++i ) {
            QL.require(!instruments_.get(i - 1).latestDate().eq(instruments_.get(i).latestDate()),
                    "two instruments have the same maturity");
        }

        // Check valid quotes.
        for ( int i = 0; i < n; ++i ) {
            QL.require(instruments_.get(i).quoteIsValid(), "instrument " + i + " has an invalid quote");
        }

        // Bind helpers to *this* curve.
        for ( int i = 0; i < n; ++i ) {
            instruments_.get(i).setTermStructure(this);
        }

        // Build dates / times arrays of length n+1.
        final Date[] datesArr = new Date[n + 1];
        final double[] timesArr = new double[n + 1];
        // Traits::initialDate(this) = this->baseDate() (super-class lookup;
        // we must short-circuit our own override which would re-enter calculate()).
        datesArr[0] = parentBaseDate();
        timesArr[0] = timeFromReference(datesArr[0]);
        for ( int i = 0; i < n; ++i ) {
            datesArr[i + 1] = instruments_.get(i).latestDate();
            timesArr[i + 1] = timeFromReference(datesArr[i + 1]);
        }

        // Initial data guess: data[0] = baseLevel; data[i>=1] = guess (0.005 for i=1, 0.002 thereafter).
        final double[] dataArr = new double[n + 1];
        dataArr[0] = baseYoYVolatility_;
        for ( int i = 1; i < n + 1; ++i ) {
            dataArr[i] = (i == 1) ? 0.005 : 0.002;
        }

        // Pre-install pillars so super.volatilityImpl() works during bootstrapping.
        this.dates_ = datesArr.clone();
        this.times_ = timesArr.clone();
        this.data_ = dataArr.clone();
        setupInterpolation();

        // C++ iterativebootstrap.hpp:115-116:
        //   Brent firstSolver_;                 // first iteration (!validData)
        //   FiniteDifferenceNewtonSafe solver_; // subsequent iterations (validData)
        final Brent firstSolver = new Brent();
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();

        boolean validData = false;
        for ( int iteration = 0; iteration < MAX_ITERATIONS; ++iteration ) {
            final double[] previousData = dataArr.clone();

            // Per-pillar bracket cache + attempt counter
            // (mirrors C++ minValues/maxValues/attempts vectors).
            final double[] minValues = new double[n + 1];
            final double[] maxValues = new double[n + 1];
            final int[] attempts = new int[n + 1];
            for ( int i = 0; i < n + 1; ++i ) {
                minValues[i] = Double.NaN;
                maxValues[i] = Double.NaN;
                attempts[i] = 1;
            }

            for ( int i = 1; i < n + 1; ++i ) {
                final YoYOptionletHelper instrument = instruments_.get(i - 1);

                // Bracket: traits::minValueAfter / maxValueAfter on first attempt,
                // widened on retries (mirrors C++ iterativebootstrap.hpp:273-286).
                double min;
                double max;
                if ( Double.isNaN(minValues[i]) ) {
                    // First attempt on this pillar in this iteration.
                    min = Math.max(0.0, dataArr[i - 1] - 0.02);
                    max = dataArr[i - 1] + 0.02;
                } else {
                    // Retry: widen. Negative min enlarges; positive shrinks toward 0.
                    final double pm = minValues[i];
                    min = (pm < 0.0 ? pm * MIN_FACTOR : pm / MIN_FACTOR);
                    // Positive max enlarges; negative shrinks toward 0.
                    final double pM = maxValues[i];
                    max = (pM > 0.0 ? pM * MAX_FACTOR : pM / MAX_FACTOR);
                    // Clamp min at 0 (vol cannot be negative).
                    if ( min < 0.0 ) {
                        min = 0.0;
                    }
                }
                minValues[i] = min;
                maxValues[i] = max;

                // Guess: traits::guess. C++: validData → prior iteration's value;
                // else 0.005 for i==1, 0.002 thereafter.
                double guess;
                if ( validData ) {
                    guess = dataArr[i];
                } else if ( i == 1 ) {
                    guess = 0.005;
                } else {
                    guess = 0.002;
                }

                // adjust guess if out of bracket (mirrors C++ iterativebootstrap.hpp:290-293)
                if ( guess >= max ) {
                    guess = max - (max - min) / 5.0;
                } else if ( guess <= min ) {
                    guess = min + (max - min) / 5.0;
                }

                final int idx = i;
                final BootstrapErrorFn errFn = new BootstrapErrorFn(instrument, idx);
                try {
                    final double r;
                    if ( validData ) {
                        r = solver.solve(errFn, accuracy_, guess, min, max);
                    } else {
                        r = firstSolver.solve(errFn, accuracy_, guess, min, max);
                    }
                    dataArr[i] = r;
                    // updateGuess: vols[i] = level
                    this.data_[i] = r;
                    setupInterpolation();
                } catch ( final RuntimeException re ) {
                    // Retry with widened bracket if attempts remain
                    // (mirrors C++ iterativebootstrap.hpp:337-344).
                    if ( attempts[i] < MAX_ATTEMPTS ) {
                        attempts[i]++;
                        i--;  // retry same pillar with widened bracket
                        continue;
                    }
                    throw new RuntimeException(
                            "could not bootstrap pillar " + i + " (after " + attempts[i] + " attempts, bracket=[" + min
                                    + "," + max + "]): " + re.getMessage(),
                            re);
                }
            }

            // Convergence check.
            double improvement = 0.0;
            for ( int i = 1; i < n + 1; ++i ) {
                improvement = Math.max(improvement, Math.abs(dataArr[i] - previousData[i]));
            }
            if ( validData && improvement <= accuracy_ ) {
                break;
            }
            QL.require(iteration + 1 < MAX_ITERATIONS,
                    "convergence not reached after " + (iteration + 1) + " iterations; last improvement " + improvement
                            + ", required accuracy " + accuracy_);
            // Subsequent iterations have valid data to seed the FDNS solver.
            validData = true;
        }
    }

    //
    // helpers
    //

    /**
     * Direct call to the parent {@code baseDate()} that doesn't go through our overridden version (which would recurse
     * into calculate()).
     */
    private Date parentBaseDate() {
        // Replicate parent's baseDate() logic without calling it (the cast
        // would still hit our override). The parent's baseDate() in
        // YoYOptionletVolatilitySurface uses observationLag + frequency +
        // indexIsInterpolated.
        if ( indexIsInterpolated_ ) {
            return referenceDate().sub(observationLag_);
        }
        final org.jquantlib.util.Pair< Date, Date > p = org.jquantlib.termstructures.InflationTermStructure.inflationPeriod(
                referenceDate().sub(observationLag_), frequency_);
        return p.first();
    }

    /** Re-builds the parent interpolation from {@code times_/data_}. */
    @Override
    protected void setupInterpolation() {
        if ( this.times_ == null || this.times_.length < 2 ) {
            return;
        }
        super.setupInterpolation();
    }

    /**
     * Bootstrap error function: drops the i-th pillar value, sets it via {@code updateGuess}, recomputes the
     * interpolation, and returns {@code instrument.quoteError() = quote - impliedQuote}.
     *
     * <p>Mirrors C++ {@code BootstrapError<C>::operator()(Real guess) const}.
     */
    private final class BootstrapErrorFn implements org.jquantlib.math.Ops.DoubleOp {
        private final YoYOptionletHelper instrument;
        private final int i;

        BootstrapErrorFn(final YoYOptionletHelper instrument, final int i) {
            this.instrument = instrument;
            this.i = i;
        }

        @Override
        public double op(final double guess) {
            // updateGuess: vols[i] = level.
            data_[i] = guess;
            setupInterpolation();
            // Mirror C++ YoYOptionletHelper::impliedQuote() (yoyoptionlethelpers.cpp:68-71)
            // which calls yoyCapFloor_->deepUpdate() before NPV() to invalidate
            // cached pricing under the new vol surface. The Java helper does not
            // expose the cap/floor; we instead fire notifyObservers() on the
            // surface (this) to propagate the change through the Handle to the
            // engine and on to the cap/floor's calculated flag.
            notifyObservers();
            return instrument.quoteError();
        }
    }
}
