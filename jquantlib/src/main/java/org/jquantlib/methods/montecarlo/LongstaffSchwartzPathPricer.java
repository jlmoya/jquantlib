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
 Copyright (C) 2006 Klaus Spanderen
 Copyright (C) 2015 Peter Caspers
 Copyright (C) 2015 Thema Consulting SA
*/

package org.jquantlib.methods.montecarlo;

import org.jquantlib.QL;
import org.jquantlib.math.GeneralLinearLeastSquares;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.statistics.IncrementalStatistics;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.TimeGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Longstaff-Schwartz path pricer for early-exercise options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/methods/montecarlo/longstaffschwartzpathpricer.hpp} (Phase 5h.5-MC-AME WI-3). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References: Francis Longstaff, Eduardo Schwartz, 2001.
 * <i>Valuing American Options by Simulation: A Simple Least-Squares
 * Approach</i>, Review of Financial Studies 14(1), 113-147.
 *
 * <p>The pricer operates in two phases:
 * <ul>
 *   <li><b>calibration phase</b> ({@code calibrationPhase_ = true}): every
 *       path passed to {@link #op(Object)} is stored, the call returns 0.
 *       Once the calibration sample size has been reached the caller
 *       invokes {@link #calibrate()}, which solves the per-time-step
 *       least-squares regression and stores the coefficients in
 *       {@link #coeff_}.</li>
 *   <li><b>pricing phase</b> ({@code calibrationPhase_ = false}): each
 *       path is rolled back from terminal to today; at each interior
 *       step the regressed continuation value is compared with the
 *       exercise value and the larger one is taken (early exercise).
 *       The discounted final price is returned.</li>
 * </ul>
 *
 * @param <PathType>  concrete path type ({@link Path} for single-asset, {@code MultiPath} for multi-asset).
 * @param <StateType> regression-state type ({@code Double} for single, {@code Array} for multi).
 * @author JQuantLib
 */
public class LongstaffSchwartzPathPricer< PathType, StateType > extends PathPricer< PathType > {

    //
    // protected fields (mirror C++ exactly)
    //

    protected final EarlyExercisePathPricer< PathType, StateType > pathPricer_;
    protected final IncrementalStatistics exerciseProbability_ = new IncrementalStatistics();
    protected final List< PathType > paths_ = new ArrayList< PathType >();
    protected final List< ? extends Ops.Op< StateType, Double > > v_;
    protected final int len_;
    protected boolean calibrationPhase_ = true;
    /** Regression coefficients per interior time step (size {@code len_-2}). */
    protected Array[] coeff_;
    /** Step-by-step discount factors {@code DF(t_{i+1})/DF(t_i)} (size {@code len_-1}). */
    protected double[] dF_;

    //
    // constructor
    //

    public LongstaffSchwartzPathPricer(final TimeGrid times,
            final EarlyExercisePathPricer< PathType, StateType > pathPricer, final YieldTermStructure termStructure) {
        QL.require(times != null && pathPricer != null && termStructure != null,
                "times, pathPricer and termStructure must be non-null");
        this.pathPricer_ = pathPricer;
        this.len_ = times.size();
        QL.require(len_ >= 2, "TimeGrid must have at least 2 points");

        this.coeff_ = new Array[len_ - 2 < 0 ? 0 : len_ - 2];
        this.dF_ = new double[len_ - 1];
        this.v_ = pathPricer.basisSystem();

        for ( int i = 0; i < len_ - 1; ++i ) {
            dF_[i] = termStructure.discount(times.get(i + 1)) / termStructure.discount(times.get(i));
        }
    }

    //
    // pricing-phase API
    //

    /**
     * During calibration: store path (deep-copied — the path generator recycles its output buffer), return 0. During
     * pricing: roll back from terminal exercise; compare regressed continuation value vs exercise at each step.
     */
    @Override
    public Double op(final PathType path) {
        if ( calibrationPhase_ ) {
            // PathGenerator / MultiPathGenerator both recycle their Path
            // object across draws, so the StateType passed in is a shared
            // reference. Deep-copy before stashing to prevent aliasing.
            paths_.add(deepCopyPath(path));
            return 0.0;
        }

        double price = pathPricer_.operator(path, len_ - 1);
        boolean exercised = (price > 0.0);

        for ( int i = len_ - 2; i > 0; --i ) {
            price *= dF_[i];

            final double exercise = pathPricer_.operator(path, i);
            if ( exercise > 0.0 ) {
                final StateType regValue = pathPricer_.state(path, i);

                double continuationValue = 0.0;
                for ( int l = 0; l < v_.size(); ++l ) {
                    continuationValue += coeff_[i - 1].get(l) * v_.get(l).op(regValue);
                }
                if ( continuationValue < exercise ) {
                    price = exercise;
                    exercised = true;
                }
            }
        }

        exerciseProbability_.add(exercised ? 1.0 : 0.0);
        return price * dF_[0];
    }

    //
    // calibration-phase API
    //

    /**
     * Solve the LSM regression at every interior time step using the stored calibration paths. Switches the pricer to
     * the pricing phase.
     *
     * <p>Supports both single-variate (StateType=Double, single-asset
     * Path) and multi-variate (StateType=Array, MultiPath) regression paths. The state-type is detected at
     * calibrate-time on the first non-empty ITM sample; mismatched homogeneity throws.
     */
    public void calibrate() {
        final int n = paths_.size();
        QL.require(n > 0, "no paths to calibrate against");

        final double[] prices = new double[n];
        final double[] exercise = new double[n];

        // Initialize from terminal exercise.
        for ( int j = 0; j < n; ++j ) {
            prices[j] = pathPricer_.operator(paths_.get(j), len_ - 1);
        }

        // Backward induction over interior time steps.
        for ( int i = len_ - 2; i > 0; --i ) {
            // Collect in-the-money sub-population: states (homogeneous
            // type) and discounted prices.
            final List< StateType > xList = new ArrayList< StateType >(n);
            final List< Double > yList = new ArrayList< Double >(n);

            for ( int j = 0; j < n; ++j ) {
                exercise[j] = pathPricer_.operator(paths_.get(j), i);
                if ( exercise[j] > 0.0 ) {
                    xList.add(pathPricer_.state(paths_.get(j), i));
                    yList.add(dF_[i] * prices[j]);
                }
            }

            if ( v_.size() <= xList.size() ) {
                final double[] yArr = new double[yList.size()];
                for ( int k = 0; k < yList.size(); ++k ) {
                    yArr[k] = yList.get(k);
                }
                coeff_[i - 1] = solveStepRegression(xList, yArr);
            } else {
                // Too few ITM paths — early-exercise iff exerciseValue > 0.
                coeff_[i - 1] = new Array(v_.size());
            }

            // Roll back per-path prices.
            for ( int j = 0; j < n; ++j ) {
                prices[j] *= dF_[i];
                if ( exercise[j] > 0.0 ) {
                    final StateType s = pathPricer_.state(paths_.get(j), i);
                    double continuationValue = 0.0;
                    for ( int l = 0; l < v_.size(); ++l ) {
                        continuationValue += coeff_[i - 1].get(l) * v_.get(l).op(s);
                    }
                    if ( continuationValue < exercise[j] ) {
                        prices[j] = exercise[j];
                    }
                }
            }
        }

        // Release calibration storage and enter pricing phase.
        paths_.clear();
        calibrationPhase_ = false;
    }

    /**
     * Solve one time-step's LSE regression — dispatches on the runtime state-type of the ITM samples.
     *
     * <p>Single-variate ({@code Double}) → routes through the original
     * {@code GeneralLinearLeastSquares(double[], double[], List&lt;DoubleOp&gt;)} constructor.
     *
     * <p>Multi-variate ({@code Array}) → routes through the new
     * {@code GeneralLinearLeastSquares(Array[], double[], List&lt;ObjectToDouble&lt;Array&gt;&gt;)} constructor (Phase
     * MC-extras WI-1).
     */
    @SuppressWarnings( "unchecked" )
    private Array solveStepRegression(final List< StateType > xList, final double[] yArr) {
        final int m = xList.size();
        QL.require(m > 0, "empty regression sample");
        final StateType first = xList.get(0);

        if ( first instanceof Double ) {
            final List< Ops.DoubleOp > basisDouble = new ArrayList< Ops.DoubleOp >(v_.size());
            for ( final Ops.Op< StateType, Double > bf : v_ ) {
                basisDouble.add(new Ops.DoubleOp() {
                    @Override
                    public double op(final double x) {
                        return bf.op((StateType) Double.valueOf(x));
                    }
                });
            }
            final double[] xArr = new double[m];
            for ( int k = 0; k < m; ++k ) {
                xArr[k] = (Double) xList.get(k);
            }
            return new GeneralLinearLeastSquares(xArr, yArr, basisDouble).coefficients();
        }

        if ( first instanceof Array ) {
            final List< Ops.ObjectToDouble< Array > > basisArr = new ArrayList< Ops.ObjectToDouble< Array > >(
                    v_.size());
            for ( final Ops.Op< StateType, Double > bf : v_ ) {
                basisArr.add(new Ops.ObjectToDouble< Array >() {
                    @Override
                    public double op(final Array a) {
                        return bf.op((StateType) a);
                    }
                });
            }
            final Array[] xArr = new Array[m];
            for ( int k = 0; k < m; ++k ) {
                xArr[k] = (Array) xList.get(k);
            }
            return new GeneralLinearLeastSquares(xArr, yArr, basisArr).coefficients();
        }

        throw new UnsupportedOperationException("regression state type not supported: " + first.getClass().getName()
                + " — extend solveStepRegression for this state type");
    }

    //
    // diagnostics
    //

    /** Mirrors C++ {@code exerciseProbability()}. */
    public double exerciseProbability() {
        return exerciseProbability_.mean();
    }

    //
    // path-clone helper — Path / MultiPath subclasses can override
    //

    /**
     * Deep-copy the path so the calibration-phase storage doesn't alias the path-generator's reusable output buffer.
     *
     * <p>Default supports {@link Path} and {@link MultiPath}; other path
     * types must override.
     */
    @SuppressWarnings( "unchecked" )
    protected PathType deepCopyPath(final PathType path) {
        if ( path instanceof Path ) {
            final Path src = (Path) path;
            final double[] vals = src.values().clone();
            return (PathType) new Path(src.timeGrid(), vals);
        }
        if ( path instanceof MultiPath ) {
            final MultiPath src = (MultiPath) path;
            // Snapshot every component path's values.
            final int n = src.assetNumber();
            final List< Path > copies = new ArrayList< Path >(n);
            for ( int a = 0; a < n; ++a ) {
                final Path p = src.get(a);
                copies.add(new Path(p.timeGrid(), p.values().clone()));
            }
            return (PathType) new MultiPath(copies);
        }
        throw new UnsupportedOperationException("deepCopyPath: unsupported path type " + path.getClass().getName()
                + " — override deepCopyPath in a subclass");
    }
}
