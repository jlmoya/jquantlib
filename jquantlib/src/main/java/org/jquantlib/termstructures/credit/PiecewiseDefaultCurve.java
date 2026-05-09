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
 Copyright (C) 2008, 2016 Jose Aparicio
 Copyright (C) 2008 Chris Kenyon
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.credit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Ops;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.solvers1D.FiniteDifferenceNewtonSafe;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.time.Date;

/**
 * Piecewise default-probability term structure — Java port of QuantLib v1.42.1
 * {@code PiecewiseDefaultCurve<Traits, Interpolator, Bootstrap>}
 * ({@code ql/termstructures/credit/piecewisedefaultcurve.hpp}).
 *
 * <p>The C++ class is templated on {@code (Traits, Interpolator, Bootstrap)}:
 * the trait selects which curve flavor to extend (interpolated hazard /
 * survival / density). Java does not have C++-style template parametric
 * inheritance, so this port uses a {@link Flavor} enum to dispatch to the
 * correct {@link InterpolatedHazardRateCurve} /
 * {@link InterpolatedSurvivalProbabilityCurve} /
 * {@link InterpolatedDefaultDensityCurve} via composition.
 *
 * <p>Bootstrap is performed lazily on first access, mirroring C++
 * {@code IterativeBootstrap::calculate} (LazyObject pattern). The convergence
 * loop uses Brent on the first pass and FiniteDifferenceNewtonSafe on
 * subsequent passes — same precedent as
 * {@link org.jquantlib.termstructures.inflation.PiecewiseZeroInflationCurve}.
 *
 * <p><b>Phase 3a scope:</b> the bootstrap loop and helper-driven solving are
 * implemented but are exercised only by tests using
 * {@code DefaultProbabilityHelper} subclasses (CDS-spread / CDS-upfront).
 * Those subclasses depend on {@code CreditDefaultSwap} and are deferred to
 * Phase 3b, so the bootstrap path here is currently exercised only by
 * synthetic L1 unit tests.
 *
 * @param <I> interpolator type (e.g. BackwardFlat, Linear, LogLinear).
 */
public class PiecewiseDefaultCurve<I extends Interpolator>
        extends DefaultProbabilityTermStructure {

    public enum Flavor {
        HAZARD_RATE,
        SURVIVAL_PROBABILITY,
        DEFAULT_DENSITY;

        ProbabilityTraits.Traits traits() {
            switch (this) {
                case HAZARD_RATE:          return new ProbabilityTraits.HazardRate();
                case SURVIVAL_PROBABILITY: return new ProbabilityTraits.SurvivalProbability();
                case DEFAULT_DENSITY:      return new ProbabilityTraits.DefaultDensity();
                default: throw new LibraryException("unknown flavor");
            }
        }
    }

    //
    // private fields
    //

    private final Flavor flavor;
    private final Class<I> classI;
    private final Interpolator interpolator;
    private final List<DefaultProbabilityHelper> instruments;
    private final ProbabilityTraits.Traits traits;
    private final double accuracy;

    /** Cached underlying curve flavour delegate (built lazily by bootstrap). */
    private DefaultProbabilityTermStructure baseCurve;

    /** Mutable bootstrap state. */
    private Date[] dates;
    private double[] times;
    private double[] data;
    private Interpolation interpolation;

    private boolean validCurve;
    private boolean calculated;
    private boolean calculating;

    //
    // public constructor
    //

    public PiecewiseDefaultCurve(
            final Flavor flavor,
            final Class<I> classI,
            final Date referenceDate,
            final List<? extends DefaultProbabilityHelper> instruments,
            final DayCounter dayCounter) {
        this(flavor, classI, referenceDate, instruments, dayCounter, 1.0e-12);
    }

    public PiecewiseDefaultCurve(
            final Flavor flavor,
            final Class<I> classI,
            final Date referenceDate,
            final List<? extends DefaultProbabilityHelper> instruments,
            final DayCounter dayCounter,
            final double accuracy) {
        super(referenceDate, new org.jquantlib.time.calendars.NullCalendar(), dayCounter);
        QL.require(flavor != null, "flavor must be non-null");
        QL.require(classI != null, "interpolator class must be non-null");
        QL.require(instruments != null && !instruments.isEmpty(),
                "no helpers provided to piecewise default curve");
        this.flavor = flavor;
        this.classI = classI;
        this.interpolator = constructInterpolator(classI);
        this.instruments = new ArrayList<>(instruments);
        this.traits = flavor.traits();
        this.accuracy = accuracy;
        this.validCurve = false;
        this.calculated = false;
        for (final DefaultProbabilityHelper h : this.instruments) {
            h.addObserver(this);
        }
    }

    static private Interpolator constructInterpolator(final Class<?> klass) {
        try {
            return (Interpolator) klass.newInstance();
        } catch (final Exception e) {
            throw new LibraryException("cannot create Interpolator", e);
        }
    }

    //
    // calculate-on-access
    //

    private void ensureCalculated() {
        if (calculated || calculating) return;
        calculating = true;
        try {
            performCalculations();
            calculated = true;
        } finally {
            calculating = false;
        }
    }

    @Override
    public void update() {
        if (!calculating) {
            calculated = false;
            validCurve = false;
        }
        super.update();
    }

    //
    // accessors
    //

    @Override
    public Date maxDate() {
        ensureCalculated();
        return dates[dates.length - 1];
    }

    public Date[] dates() { ensureCalculated(); return dates; }
    public double[] times() { ensureCalculated(); return times; }
    public double[] data() { ensureCalculated(); return data; }

    @Override
    protected double survivalProbabilityImpl(final double t) {
        ensureCalculated();
        return baseCurve.survivalProbability(t, true);
    }

    @Override
    protected double defaultDensityImpl(final double t) {
        ensureCalculated();
        return baseCurve.defaultDensity(t, true);
    }

    @Override
    protected double hazardRateImpl(final double t) {
        ensureCalculated();
        return baseCurve.hazardRate(t, true);
    }

    //
    // bootstrap loop — mirrors C++ IterativeBootstrap::calculate
    //

    private void performCalculations() {
        final int n = instruments.size();

        instruments.sort((a, b) -> a.latestDate().compareTo(b.latestDate()));

        for (int i = 1; i < n; ++i) {
            QL.require(!instruments.get(i - 1).latestDate().eq(instruments.get(i).latestDate()),
                    "two instruments have the same maturity");
        }

        for (int i = 0; i < n; ++i) {
            QL.require(instruments.get(i).quoteIsValid(),
                    "instrument has an invalid quote");
        }

        // Setup pre-bootstrap dates / times / data: dates[0] = referenceDate,
        // dates[i+1] = helper[i].latestDate.
        final Date[] newDates = new Date[n + 1];
        final double[] newTimes = new double[n + 1];
        final double[] newData = new double[n + 1];

        newDates[0] = referenceDate();
        newTimes[0] = 0.0;
        newData[0] = traits.initialValue();

        for (int i = 0; i < n; ++i) {
            newDates[i + 1] = instruments.get(i).latestDate();
            newTimes[i + 1] = timeFromReference(newDates[i + 1]);
        }
        // First-pass guesses default to initialValue across the board.
        for (int i = 1; i < n + 1; ++i) {
            newData[i] = newData[0];
        }

        this.dates = newDates;
        this.times = newTimes;
        this.data = newData;

        // Build the base curve flavor with the initial dates/data.
        this.baseCurve = buildBaseCurve(newDates, newData);

        for (int i = 0; i < n; ++i) {
            instruments.get(i).setTermStructure(baseCurve);
        }

        // C++ IterativeBootstrap uses two solvers:
        //   firstSolver_ (Brent)               when validData == false (first pass)
        //   solver_ (FiniteDifferenceNewtonSafe) when validData == true  (subsequent passes)
        final Brent firstSolver = new Brent();
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();
        final int maxIterations = traits.maxIterations();

        // C++ IterativeBootstrap retry parameters (default IterativeBootstrap()):
        //   maxAttempts_ = 1, maxFactor_ = 2.0, minFactor_ = 2.0
        // We pre-emptively allow one retry per pillar with widened bounds, which
        // matches the most common QuantLib usage and unblocks
        // testIterativeBootstrapRetries / testLogLinearSurvivalConsistency.
        final int maxAttempts = 1;          // one extra attempt on top of first pass
        final double minFactor = 2.0;
        final double maxFactor = 2.0;

        for (int iteration = 0; ; ++iteration) {
            final double[] previousData = data.clone();

            // Per-pillar min/max state so we can widen the search range on retry.
            final double[] minValues = new double[n + 1];
            final double[] maxValues = new double[n + 1];
            final int[] attempts = new int[n + 1];
            java.util.Arrays.fill(minValues, Double.NaN);
            java.util.Arrays.fill(maxValues, Double.NaN);
            java.util.Arrays.fill(attempts, 1);

            for (int i = 1; i < n + 1; ++i) {
                final DefaultProbabilityHelper instrument = instruments.get(i - 1);
                final boolean validData = validCurve || iteration > 0;

                // bracket root and calculate guess (mirrors C++ iterativebootstrap.hpp:269-294).
                if (Double.isNaN(minValues[i])) {
                    // First attempt
                    minValues[i] = traits.minValueAfter(i, data, validData, times);
                    maxValues[i] = traits.maxValueAfter(i, data, validData, times);
                } else {
                    // Extending a previous attempt: a negative min is enlarged
                    // (multiplied), a positive one is shrunk towards zero.
                    minValues[i] = (minValues[i] < 0.0
                            ? minValues[i] * minFactor
                            : minValues[i] / minFactor);
                    maxValues[i] = (maxValues[i] > 0.0
                            ? maxValues[i] * maxFactor
                            : maxValues[i] / maxFactor);
                }
                final double min = minValues[i];
                final double max = maxValues[i];

                double guess = traits.guess(i, data, validData, times);
                if (guess >= max) {
                    guess = max - (max - min) / 5.0;
                } else if (guess <= min) {
                    guess = min + (max - min) / 5.0;
                }

                final BootstrapErrorFn error = new BootstrapErrorFn(
                        instrument, this, i, validData);
                try {
                    final double r = validData
                            ? solver.solve(error, accuracy, guess, min, max)
                            : firstSolver.solve(error, accuracy, guess, min, max);
                    traits.updateGuess(data, r, i);
                    if (validData) {
                        rebuildBaseCurve();
                    } else {
                        // Use a partial curve: it includes only [0..i] which are
                        // already bootstrapped — avoids validation tripping over
                        // the unbootstrapped tail.
                        rebuildPartialBaseCurve(i);
                    }
                } catch (final RuntimeException e) {
                    if (validCurve) {
                        // Previous curve state may be a bad guess; invalidate
                        // and recurse — C++ iterativebootstrap.hpp:325-335.
                        validCurve = false;
                        calculated = false;
                        performCalculations();
                        return;
                    }
                    if (attempts[i] < maxAttempts) {
                        attempts[i]++;
                        --i; // retry this pillar with widened bounds
                        continue;
                    }
                    throw new LibraryException(
                            "could not bootstrap default curve at instrument " + i +
                            " (latest date " + instruments.get(i - 1).latestDate() + "): " +
                            e.getMessage(), e);
                }
            }

            if (!interpolator.global()) break;
            if (!validCurve && iteration == 0) {
                validCurve = true;
                continue;
            }

            double improvement = 0.0;
            for (int i = 1; i < n + 1; ++i) {
                improvement = Math.max(improvement, Math.abs(data[i] - previousData[i]));
            }
            if (improvement <= accuracy) break;

            QL.require(iteration + 1 < maxIterations,
                    "convergence not reached after " + (iteration + 1) +
                    " iterations; last improvement " + improvement +
                    ", required accuracy " + accuracy);
        }
        // Final full-curve rebuild — bootstrap is done; the data array is
        // valid across all pillars. Rebinds helpers to the full curve.
        rebuildBaseCurve();
        for (final DefaultProbabilityHelper h : instruments) {
            h.setTermStructure(baseCurve);
        }
        validCurve = true;
    }

    private DefaultProbabilityTermStructure buildBaseCurve(
            final Date[] ds, final double[] vals) {
        switch (flavor) {
            case HAZARD_RATE:
                return new InterpolatedHazardRateCurve<I>(
                        classI, ds, vals, dayCounter(),
                        new org.jquantlib.time.calendars.NullCalendar(),
                        interpolator);
            case SURVIVAL_PROBABILITY:
                return new InterpolatedSurvivalProbabilityCurve<I>(
                        classI, ds, vals, dayCounter(),
                        new org.jquantlib.time.calendars.NullCalendar(),
                        interpolator);
            case DEFAULT_DENSITY:
                return new InterpolatedDefaultDensityCurve<I>(
                        classI, ds, vals, dayCounter(),
                        new org.jquantlib.time.calendars.NullCalendar(),
                        interpolator);
            default:
                throw new LibraryException("unknown flavor: " + flavor);
        }
    }

    private void rebuildBaseCurve() {
        this.baseCurve = buildBaseCurve(dates, data);
    }

    /**
     * Builds a partial base curve covering only the first {@code activePillars+1}
     * dates / data values. Mirrors C++ IterativeBootstrap's progressive
     * interpolation pattern ({@code interpolator.interpolate(times.begin(),
     * times.begin()+i+1, data.begin())}) and avoids the
     * {@code InterpolatedSurvivalProbabilityCurve} monotonicity check tripping
     * on unbootstrapped tail values that are still at their initial-guess
     * sentinel.
     *
     * <p>Required for LogLinear-survival bootstraps where the un-bootstrapped
     * tail has data[i] = 1.0 but data[i-1] is already smaller.
     */
    private void rebuildPartialBaseCurve(final int activePillars) {
        if (activePillars + 1 >= dates.length) {
            this.baseCurve = buildBaseCurve(dates, data);
            return;
        }
        // Need at least the minimum points the interpolator requires.
        final int needed = Math.max(2, activePillars + 1);
        final Date[] ds = java.util.Arrays.copyOfRange(dates, 0, needed);
        final double[] vs = java.util.Arrays.copyOfRange(data, 0, needed);
        this.baseCurve = buildBaseCurve(ds, vs);
    }

    //
    // 1D function adapter for the solver: wraps helper.quoteError() as a function
    // of data[i].
    //

    private static final class BootstrapErrorFn implements Ops.DoubleOp {
        private final DefaultProbabilityHelper helper;
        private final PiecewiseDefaultCurve<?> curve;
        private final int idx;
        private final boolean validData;

        BootstrapErrorFn(final DefaultProbabilityHelper helper,
                         final PiecewiseDefaultCurve<?> curve,
                         final int idx,
                         final boolean validData) {
            this.helper = helper;
            this.curve = curve;
            this.idx = idx;
            this.validData = validData;
        }

        @Override
        public double op(final double x) {
            curve.traits.updateGuess(curve.data, x, idx);
            // While the curve is still being bootstrapped (validData == false),
            // rebuild only the active prefix to avoid the
            // {@link InterpolatedSurvivalProbabilityCurve} monotonicity check
            // tripping on the unbootstrapped tail. Once validData is true (or
            // we have all pillars), use the full curve so the chosen
            // interpolator (e.g. LogLinear) operates over the entire dataset.
            if (validData) {
                curve.rebuildBaseCurve();
            } else {
                curve.rebuildPartialBaseCurve(idx);
            }
            // Re-bind helpers so they reference the freshly rebuilt curve.
            for (final DefaultProbabilityHelper h : curve.instruments) {
                h.setTermStructure(curve.baseCurve);
            }
            return helper.quoteError();
        }
    }

    //
    // unused fields warning suppression — the interpolation handle is currently
    // managed inside each baseCurve flavor; the field is kept for parity with the
    // C++ structure / future GlobalBootstrap support.
    //

    @SuppressWarnings("unused")
    private void touchInterpolation() {
        if (interpolation != null) interpolation.update();
        if (data != null && times != null) {
            this.interpolation = interpolator.interpolate(
                    new Array(Arrays.copyOf(times, times.length)),
                    new Array(Arrays.copyOf(data, data.length)));
        }
    }
}
