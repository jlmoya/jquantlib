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
 Copyright (C) 2007 Chris Kenyon
 Copyright (C) 2007, 2008 StatPro Italia srl
 Copyright (C) 2011 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.inflation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Ops;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.solvers1D.FiniteDifferenceNewtonSafe;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Piecewise zero-inflation term structure — Java port of QuantLib v1.42.1
 * {@code PiecewiseZeroInflationCurve<Interpolator,Bootstrap,Traits>} with the
 * default template arguments
 * ({@code Bootstrap = IterativeBootstrap}, {@code Traits = ZeroInflationTraits}).
 *
 * <p>Bootstrap is performed lazily on first access: the curve nodes are placed
 * at each helper's pillar date, and the corresponding data values are solved
 * via a 1D Brent search such that each helper's
 * {@link org.jquantlib.termstructures.BootstrapHelper#impliedQuote()} matches
 * its input quote.
 *
 * <h3>Why a self-contained bootstrap loop?</h3>
 * <p>The existing JQuantLib generic bootstrap framework
 * ({@link org.jquantlib.termstructures.IterativeBootstrap},
 *  {@link org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve}) is
 * tightly tied to {@link org.jquantlib.termstructures.YieldTermStructure}
 * via concrete type parameters. Rather than restructure that framework
 * (out-of-scope for Phase 2p A.1, would risk regressing yield-curve tests),
 * we inline a focused inflation-only bootstrap loop here, modelled directly
 * after the C++ {@code IterativeBootstrap::calculate} algorithm.
 *
 * @param <I> interpolator type
 * @see InflationTraits
 * @see ZeroCouponInflationSwapHelper
 */
public class PiecewiseZeroInflationCurve<I extends Interpolator>
        extends InterpolatedZeroInflationCurve<I> {

    //
    // private fields
    //

    private final List<ZeroCouponInflationSwapHelper> instruments;
    private final InflationTraits traits;
    private final double accuracy;
    private final GlobalBootstrap globalBootstrap;
    private boolean validCurve;
    private boolean calculated;
    private boolean calculating;

    //
    // public constructors
    //

    public PiecewiseZeroInflationCurve(
            final Class<I> classI,
            final Date referenceDate,
            final Date baseDate,
            final Frequency frequency,
            final DayCounter dayCounter,
            final List<ZeroCouponInflationSwapHelper> instruments) {
        this(classI, referenceDate, baseDate, frequency, dayCounter, instruments, 1.0e-14);
    }

    public PiecewiseZeroInflationCurve(
            final Class<I> classI,
            final Date referenceDate,
            final Date baseDate,
            final Frequency frequency,
            final DayCounter dayCounter,
            final List<ZeroCouponInflationSwapHelper> instruments,
            final double accuracy) {
        this(classI, referenceDate, baseDate, frequency, dayCounter, instruments, accuracy, null);
    }

    /**
     * Constructor selecting a {@link GlobalBootstrap} strategy. When
     * {@code globalBootstrap} is non-null, the curve will solve all pillars
     * simultaneously via Levenberg-Marquardt at first calculation rather
     * than running the iterative Brent/FDNewtonSafe loop. Mirrors C++
     * {@code PiecewiseZeroInflationCurve<Linear, GlobalBootstrap>}
     * ({@code ql/termstructures/inflation/piecewisezeroinflationcurve.hpp}
     * with non-default {@code Bootstrap} template parameter).
     *
     * @param globalBootstrap non-null to use the global solver strategy;
     *                        null for the default iterative bootstrap.
     */
    public PiecewiseZeroInflationCurve(
            final Class<I> classI,
            final Date referenceDate,
            final Date baseDate,
            final Frequency frequency,
            final DayCounter dayCounter,
            final List<ZeroCouponInflationSwapHelper> instruments,
            final double accuracy,
            final GlobalBootstrap globalBootstrap) {
        super(classI, referenceDate, baseDate, frequency, dayCounter);
        QL.require(instruments != null && !instruments.isEmpty(),
                "no helpers provided to piecewise inflation curve");
        this.instruments = new ArrayList<>(instruments);
        this.traits = new InflationTraits();
        this.accuracy = accuracy;
        this.globalBootstrap = globalBootstrap;
        this.validCurve = false;
        this.calculated = false;

        // Register helpers — bootstrapping needs each helper to know about
        // the curve being built.
        for (final ZeroCouponInflationSwapHelper h : this.instruments) {
            h.addObserver(this);
        }
    }

    //
    // calculate-on-access
    //

    /**
     * Triggers bootstrap if not yet performed. All public read accessors call
     * this first. Mirrors C++ {@code LazyObject::calculate}.
     *
     * <p>Re-entry guard ({@code calculating}) prevents infinite recursion
     * when a helper's {@code impliedQuote()} triggers a back-call into the
     * curve during the bootstrap loop itself.
     */
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

    /**
     * Invalidates the bootstrap when any observed input changes (quote, index
     * fixing, seasonality). Mirrors C++ {@code LazyObject::update()} which sets
     * {@code calculated_ = false} so that the next access re-triggers
     * {@link #performCalculations()}.
     *
     * <p>The {@code calculating} guard prevents us from resetting
     * {@code calculated} while a bootstrap is already in progress (which would
     * cause immediate infinite recursion on the next helper access).
     */
    @Override
    public void update() {
        if (!calculating) {
            calculated = false;
            validCurve = false;
        }
        super.update();
    }

    @Override
    public Date maxDate() {
        ensureCalculated();
        return super.maxDate();
    }

    @Override
    public Date baseDate() {
        // baseDate is the curve anchor — it does NOT depend on the bootstrap.
        return super.baseDate();
    }

    @Override
    protected double zeroRateImpl(final double t) {
        ensureCalculated();
        return super.zeroRateImpl(t);
    }

    @Override
    public Date[] dates() { ensureCalculated(); return super.dates(); }

    @Override
    public double[] times() { ensureCalculated(); return super.times(); }

    @Override
    public double[] data() { ensureCalculated(); return super.data(); }

    @Override
    public double[] rates() { ensureCalculated(); return super.data(); }

    //
    // bootstrap loop — mirrors C++ IterativeBootstrap::calculate
    //

    private void performCalculations() {
        // Branch to GlobalBootstrap if configured — mirrors C++
        // PiecewiseZeroInflationCurve<I, GlobalBootstrap, Traits> path.
        if (globalBootstrap != null) {
            globalBootstrap.calculate(this, instruments);
            validCurve = true;
            return;
        }

        final int n = instruments.size();

        // Sort helpers by latestDate — required so curve nodes are monotonic.
        instruments.sort((a, b) -> a.latestDate().compareTo(b.latestDate()));

        // Check no two helpers share a maturity.
        for (int i = 1; i < n; ++i) {
            QL.require(!instruments.get(i - 1).latestDate().eq(instruments.get(i).latestDate()),
                    "two instruments have the same maturity");
        }

        // Check all quotes are valid.
        for (int i = 0; i < n; ++i) {
            QL.require(instruments.get(i).quoteIsValid(),
                    "instrument has an invalid quote");
        }

        // Setup pre-bootstrap dates / times / data: dates[0] = baseDate,
        // dates[i+1] = helper[i].latestDate.
        final Date[] newDates = new Date[n + 1];
        final double[] newTimes = new double[n + 1];
        final double[] newData = new double[n + 1];

        newDates[0] = traits.initialDate(this);
        newTimes[0] = timeFromReference(newDates[0]);
        newData[0] = traits.initialValue(this);

        for (int i = 0; i < n; ++i) {
            newDates[i + 1] = instruments.get(i).latestDate();
            newTimes[i + 1] = timeFromReference(newDates[i + 1]);
            newData[i + 1] = traits.guess(i + 1, newData, false);
        }

        setDates(newDates);
        setTimes(newTimes);
        setData(newData);

        // Wire each helper to this curve so its impliedQuote() reads from us.
        for (int i = 0; i < n; ++i) {
            instruments.get(i).setTermStructure(this);
        }

        // Use a maxDate override that covers the last node — needed for
        // helper.impliedQuote() which may evaluate slightly past dates[n].
        setMaxDate(newDates[n]);

        // C++ IterativeBootstrap uses two solvers:
        //   firstSolver_ (Brent)               when validData == false (first pass)
        //   solver_ (FiniteDifferenceNewtonSafe) when validData == true  (subsequent passes)
        // Mirror that split here per Phase 2r L0 A.2.
        final Brent firstSolver = new Brent();
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();
        final int maxIterations = traits.maxIterations();

        for (int iteration = 0; ; ++iteration) {
            final double[] previousData = data().clone();

            // Restart the interpolation from the previous solved data.
            setInterpolation(interpolator().interpolate(
                    new Array(times()), new Array(data())));

            for (int i = 1; i < n + 1; ++i) {
                final ZeroCouponInflationSwapHelper instrument = instruments.get(i - 1);
                final boolean validData = validCurve || iteration > 0;
                double guess;
                if (validData) {
                    guess = data()[i];
                } else if (i == 1) {
                    // First node — base value seeds from average inflation.
                    guess = traits.guess(i, data(), false);
                } else {
                    // Extrapolate from the curve bootstrapped so far.
                    guess = data()[i - 1];
                }

                final double[] curData = data();
                final double min = traits.minValueAfter(i, curData, validData);
                final double max = traits.maxValueAfter(i, curData, validData);
                if (guess <= min || guess >= max) {
                    guess = (min + max) / 2.0;
                }

                // For the first iteration, extend the interpolation one node at a time
                // (lets us probe partially-bootstrapped values).
                if (!validCurve && iteration == 0) {
                    final double[] partialTimes = Arrays.copyOf(times(), i + 1);
                    final double[] partialData = Arrays.copyOf(data(), i + 1);
                    setInterpolation(interpolator().interpolate(
                            new Array(partialTimes), new Array(partialData)));
                }
                interpolation().update();

                // For first iteration, only have data up to (i+1); for later passes,
                // use full curve. Use the larger size so the interpolation always
                // covers fixingDate.
                final int sizeForFn = validData ? n + 1 : i + 1;
                final BootstrapErrorFn error = new BootstrapErrorFn(instrument, this, i, sizeForFn);
                final double r;
                try {
                    // Mirror C++ IterativeBootstrap: use FDNewtonSafe when validData,
                    // Brent on the first (virgin-data) pass.
                    r = validData
                            ? solver.solve(error, accuracy, guess, min, max)
                            : firstSolver.solve(error, accuracy, guess, min, max);
                } catch (final RuntimeException e) {
                    validCurve = false;
                    throw new LibraryException(
                            "could not bootstrap inflation curve at instrument " + i +
                            " (latest date " + instruments.get(i - 1).latestDate() + "): " +
                            e.getMessage(), e);
                }
                // Update via traits — copies r into data[i] and (if i==1) data[0].
                traits.updateGuess(data(), r, i);
            }

            // Re-install the full interpolation now that all nodes are solved.
            setInterpolation(interpolator().interpolate(
                    new Array(times()), new Array(data())));

            // For non-global interpolators, no convergence loop is needed.
            if (!interpolator().global()) {
                break;
            } else if (!validCurve && iteration == 0) {
                continue;
            }

            // Check convergence.
            double improvement = 0.0;
            for (int i = 1; i < n + 1; ++i) {
                improvement = Math.max(improvement, Math.abs(data()[i] - previousData[i]));
            }
            if (improvement <= accuracy) {
                break;
            }

            QL.require(iteration + 1 < maxIterations,
                    "convergence not reached after " + (iteration + 1) +
                    " iterations; last improvement " + improvement +
                    ", required accuracy " + accuracy);
        }
        validCurve = true;

        // Update the base-rate slot from the bootstrapped data[0].
        overrideBaseRate(data()[0]);
    }

    //
    // private helper: 1D function adapter for Brent solver
    //

    /**
     * Adapts {@code helper.impliedQuote() - helper.quote()} as a function of
     * {@code data[i]} for the Brent solver.
     *
     * <p>Implementation note: the Java {@link Array} class copies its source
     * {@code double[]} on construction, so the interpolation built earlier
     * holds a stale snapshot. We rebuild the interpolation from the current
     * {@code data[]} on every Brent step. This adds O(n) overhead per Brent
     * iteration vs the C++ iterator-binding approach, but is correct and
     * adequate for the small number of helpers in a practical inflation curve.
     */
    private static final class BootstrapErrorFn implements Ops.DoubleOp {
        private final ZeroCouponInflationSwapHelper helper;
        private final PiecewiseZeroInflationCurve<?> curve;
        private final int idx;
        private final int size;

        BootstrapErrorFn(final ZeroCouponInflationSwapHelper helper,
                         final PiecewiseZeroInflationCurve<?> curve,
                         final int idx,
                         final int size) {
            this.helper = helper;
            this.curve = curve;
            this.idx = idx;
            this.size = size;
        }

        @Override
        public double op(final double x) {
            curve.traits.updateGuess(curve.data(), x, idx);
            // Rebuild the interpolation up to (idx+1) using the live data —
            // this picks up the just-mutated data[idx]/data[0] values.
            final double[] partialT = java.util.Arrays.copyOf(curve.times(), size);
            final double[] partialD = java.util.Arrays.copyOf(curve.data(), size);
            curve.setInterpolation(curve.interpolator().interpolate(
                    new Array(partialT), new Array(partialD)));
            return helper.quoteError();
        }
    }

    //
    // package-private helpers for {@link GlobalBootstrap}
    //

    /**
     * Install the date / time / data grids prepared by {@link GlobalBootstrap}.
     * Called once at the start of each global-bootstrap calculation; mirrors
     * the body of C++ {@code GlobalBootstrap::initialize()}.
     *
     * <p>Package-private so {@link GlobalBootstrap} can mutate the curve's
     * private grids without exposing them on the public surface.
     */
    void installGlobalBootstrapState(final Date[] newDates,
                                      final double[] newTimes,
                                      final double[] newData) {
        setDates(newDates);
        setTimes(newTimes);
        setData(newData);
        setMaxDate(newDates[newDates.length - 1]);
        setInterpolation(interpolator().interpolate(
                new Array(newTimes), new Array(newData)));
    }

    /**
     * Re-create the interpolation from the current {@code times}/{@code data}
     * grids — called from {@link GlobalBootstrap}'s cost function at every LM
     * step (after {@code data[i]} mutations).
     */
    void refreshInterpolationForGlobalBootstrap() {
        setInterpolation(interpolator().interpolate(
                new Array(times()), new Array(data())));
    }

    /**
     * Update the curve's base-rate slot — called once at the end of each
     * global-bootstrap calculation. Mirrors C++ {@code Traits::updateGuess}'s
     * propagation of {@code data[1]} to {@code data[0]} for the inflation
     * trait.
     */
    void overrideBaseRateForGlobalBootstrap(final double r) {
        overrideBaseRate(r);
    }
}
