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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Piecewise year-on-year inflation term structure — Java port of QuantLib v1.42.1
 * {@code PiecewiseYoYInflationCurve<Interpolator, Bootstrap, Traits>} with the default template arguments
 * ({@code Bootstrap = IterativeBootstrap}, {@code Traits = YoYInflationTraits}).
 *
 * <p>Sister class to {@link PiecewiseZeroInflationCurve}: same algorithm,
 * different traits ({@link YoYInflationTraits}) and different helper type ({@link YearOnYearInflationSwapHelper}).
 *
 * <p>Bootstrap is performed lazily on first access; convergence is at the
 * {@code accuracy} threshold passed at construction.
 *
 * @param <I> interpolator type
 * @see YoYInflationTraits
 * @see YearOnYearInflationSwapHelper
 */
public class PiecewiseYoYInflationCurve< I extends Interpolator > extends InterpolatedYoYInflationCurve< I > {

    //
    // private fields
    //

    private final List< YearOnYearInflationSwapHelper > instruments;
    private final YoYInflationTraits traits;
    private final double accuracy;
    private boolean validCurve;
    private boolean calculated;
    private boolean calculating;

    //
    // public constructors
    //

    public PiecewiseYoYInflationCurve(final Class< I > classI, final Date referenceDate, final Date baseDate,
            final double baseYoYRate, final Frequency frequency, final DayCounter dayCounter,
            final List< YearOnYearInflationSwapHelper > instruments) {
        this(classI, referenceDate, baseDate, baseYoYRate, frequency, dayCounter, instruments, 1.0e-12);
    }

    public PiecewiseYoYInflationCurve(final Class< I > classI, final Date referenceDate, final Date baseDate,
            final double baseYoYRate, final Frequency frequency, final DayCounter dayCounter,
            final List< YearOnYearInflationSwapHelper > instruments, final double accuracy) {
        super(classI, referenceDate, baseDate, baseYoYRate, frequency, dayCounter);
        QL.require(instruments != null && !instruments.isEmpty(),
                "no helpers provided to piecewise YoY inflation curve");
        this.instruments = new ArrayList<>(instruments);
        this.traits = new YoYInflationTraits();
        this.accuracy = accuracy;
        this.validCurve = false;
        this.calculated = false;

        for ( final YearOnYearInflationSwapHelper h : this.instruments ) {
            h.addObserver(this);
        }
    }

    //
    // calculate-on-access
    //

    private void ensureCalculated() {
        if ( calculated || calculating )
            return;
        calculating = true;
        try {
            performCalculations();
            calculated = true;
        } finally {
            calculating = false;
        }
    }

    /**
     * Invalidates the bootstrap when any observed input changes. Mirrors C++ {@code LazyObject::update()} which resets
     * {@code calculated_} to false.
     */
    @Override
    public void update() {
        if ( !calculating ) {
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
        return super.baseDate();
    }

    @Override
    protected double yoyRateImpl(final double t) {
        ensureCalculated();
        return super.yoyRateImpl(t);
    }

    @Override
    public Date[] dates() {
        ensureCalculated();
        return super.dates();
    }

    @Override
    public double[] times() {
        ensureCalculated();
        return super.times();
    }

    @Override
    public double[] data() {
        ensureCalculated();
        return super.data();
    }

    @Override
    public double[] rates() {
        ensureCalculated();
        return super.data();
    }

    //
    // bootstrap loop — mirrors C++ IterativeBootstrap::calculate
    //

    private void performCalculations() {
        final int n = instruments.size();

        instruments.sort((a, b) -> a.latestDate().compareTo(b.latestDate()));

        for ( int i = 1; i < n; ++i ) {
            QL.require(!instruments.get(i - 1).latestDate().eq(instruments.get(i).latestDate()),
                    "two instruments have the same maturity");
        }

        for ( int i = 0; i < n; ++i ) {
            QL.require(instruments.get(i).quoteIsValid(), "instrument has an invalid quote");
        }

        // Pre-bootstrap dates / times / data: dates[0] = baseDate, dates[i+1]
        // = helper[i].latestDate.
        final Date[] newDates = new Date[n + 1];
        final double[] newTimes = new double[n + 1];
        final double[] newData = new double[n + 1];

        newDates[0] = traits.initialDate(this);
        newTimes[0] = timeFromReference(newDates[0]);
        newData[0] = traits.initialValue(this);

        // C++ iterativebootstrap.hpp:216 initializes the entire data vector
        // to initialValue (the curve's baseRate for YoY); the C++
        // Traits::guess(.., validData=false, ..) returns avgInflation. The
        // initial data does not affect convergence — all values are
        // overwritten in the bootstrap loop — but matters for the first
        // iteration's interpolation extension. Match C++ exactly.
        final double iv = newData[0];
        for ( int i = 0; i < n; ++i ) {
            newDates[i + 1] = instruments.get(i).latestDate();
            newTimes[i + 1] = timeFromReference(newDates[i + 1]);
            newData[i + 1] = iv;
        }

        setDates(newDates);
        setTimes(newTimes);
        setData(newData);

        for ( int i = 0; i < n; ++i ) {
            instruments.get(i).setTermStructure(this);
        }

        setMaxDate(newDates[n]);

        // C++ IterativeBootstrap uses two solvers:
        //   firstSolver_ (Brent)                when validData == false (first pass)
        //   solver_ (FiniteDifferenceNewtonSafe) when validData == true  (subsequent passes)
        // Mirror that split here per Phase 2r L0 A.2.
        final Brent firstSolver = new Brent();
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();
        final int maxIterations = traits.maxIterations();

        for ( int iteration = 0; ; ++iteration ) {
            final double[] previousData = data().clone();

            setInterpolation(interpolator().interpolate(new Array(times()), new Array(data())));

            for ( int i = 1; i < n + 1; ++i ) {
                final YearOnYearInflationSwapHelper instrument = instruments.get(i - 1);
                // Mirror C++ Traits::guess(i, ts_, validData, firstAliveHelper_)
                // exactly: validData ? data[i] : avgInflation.
                final boolean validData = validCurve || iteration > 0;
                double guess = traits.guess(i, data(), validData);

                final double[] curData = data();
                final double min = traits.minValueAfter(i, curData, validData);
                final double max = traits.maxValueAfter(i, curData, validData);
                // Match C++ guess-bracket adjustment exactly (iterativebootstrap.hpp:289-293).
                if ( guess >= max ) {
                    guess = max - (max - min) / 5.0;
                } else if ( guess <= min ) {
                    guess = min + (max - min) / 5.0;
                }

                if ( !validCurve && iteration == 0 ) {
                    final double[] partialTimes = Arrays.copyOf(times(), i + 1);
                    final double[] partialData = Arrays.copyOf(data(), i + 1);
                    setInterpolation(interpolator().interpolate(new Array(partialTimes), new Array(partialData)));
                }
                interpolation().update();

                final int sizeForFn = validData ? n + 1 : i + 1;
                final BootstrapErrorFn error = new BootstrapErrorFn(instrument, this, i, sizeForFn);
                final double r;
                try {
                    // Mirror C++ IterativeBootstrap: use FDNewtonSafe when validData,
                    // Brent on the first (virgin-data) pass.
                    r = validData
                            ? solver.solve(error, accuracy, guess, min, max)
                            : firstSolver.solve(error, accuracy, guess, min, max);
                } catch ( final RuntimeException e ) {
                    validCurve = false;
                    throw new LibraryException(
                            "could not bootstrap YoY inflation curve at instrument " + i + " (latest date "
                                    + instruments.get(i - 1).latestDate() + "): " + e.getMessage(), e);
                }
                traits.updateGuess(data(), r, i);
            }

            setInterpolation(interpolator().interpolate(new Array(times()), new Array(data())));

            if ( !interpolator().global() ) {
                break;
            } else if ( !validCurve && iteration == 0 ) {
                continue;
            }

            double improvement = 0.0;
            for ( int i = 1; i < n + 1; ++i ) {
                improvement = Math.max(improvement, Math.abs(data()[i] - previousData[i]));
            }
            if ( improvement <= accuracy ) {
                break;
            }

            QL.require(iteration + 1 < maxIterations,
                    "convergence not reached after " + (iteration + 1) + " iterations; last improvement " + improvement
                            + ", required accuracy " + accuracy);
        }
        validCurve = true;
    }

    //
    // private helper: 1D function adapter for Brent solver
    //

    private static final class BootstrapErrorFn implements Ops.DoubleOp {
        private final YearOnYearInflationSwapHelper helper;
        private final PiecewiseYoYInflationCurve< ? > curve;
        private final int idx;
        private final int size;

        BootstrapErrorFn(final YearOnYearInflationSwapHelper helper, final PiecewiseYoYInflationCurve< ? > curve,
                final int idx, final int size) {
            this.helper = helper;
            this.curve = curve;
            this.idx = idx;
            this.size = size;
        }

        @Override
        public double op(final double x) {
            curve.traits.updateGuess(curve.data(), x, idx);
            final double[] partialT = java.util.Arrays.copyOf(curve.times(), size);
            final double[] partialD = java.util.Arrays.copyOf(curve.data(), size);
            curve.setInterpolation(curve.interpolator().interpolate(new Array(partialT), new Array(partialD)));
            return helper.quoteError();
        }
    }
}
