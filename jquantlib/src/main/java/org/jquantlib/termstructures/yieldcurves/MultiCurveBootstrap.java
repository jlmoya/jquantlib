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
 Copyright (C) 2025 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.yieldcurves;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.util.Observer;

/**
 * Coordinator that drives a single Levenberg-Marquardt problem across multiple
 * {@link MultiCurveBootstrapContributor}s — typically multiple {@link GlobalBootstrap}-bootstrapped
 * {@link PiecewiseYieldCurve}s that form a dependency cycle.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MultiCurveBootstrap}
 * ({@code ql/termstructures/globalbootstrap.{hpp,cpp}:51-67, 26-118}).
 *
 * <p>Flow:
 * <ol>
 *   <li>Each contributor's {@link MultiCurveBootstrapContributor#setupCostFunction()} is called once; the returned
 *       guess arrays are concatenated into a single global guess vector.</li>
 *   <li>The optimiser runs against a global cost function that, on every step, slices the variable vector back into
 *       per-contributor chunks, calls {@link MultiCurveBootstrapContributor#setCostFunctionArgument(Array)} for each,
 *       fires {@link Observer#update()} for every registered observer (the non-bootstrapped curves in the cycle), and
 *       then collects each contributor's residual via
 *       {@link MultiCurveBootstrapContributor#evaluateCostFunction()}.</li>
 *   <li>On success every contributor is marked valid via {@link MultiCurveBootstrapContributor#setToValid()}.</li>
 * </ol>
 */
public class MultiCurveBootstrap {

    private final OptimizationMethod optimizer;
    private final EndCriteria endCriteria;
    private final List< MultiCurveBootstrapContributor > contributors = new ArrayList<>();
    private final List< Observer > observers = new ArrayList<>();

    /** Accuracy-only ctor; mirrors C++ {@code MultiCurveBootstrap(Real accuracy)}. */
    public MultiCurveBootstrap(final double accuracy) {
        this.optimizer = new LevenbergMarquardt(accuracy, accuracy, accuracy);
        this.endCriteria = new EndCriteria(1000, 10, accuracy, accuracy, accuracy);
    }

    /**
     * Optimiser / criteria ctor. Either argument may be {@code null}; defaults to LM with accuracy {@code 1e-10}
     * and {@code EndCriteria(1000, 10, 1e-10, 1e-10, 1e-10)}, mirroring C++.
     */
    public MultiCurveBootstrap(final OptimizationMethod optimizer, final EndCriteria endCriteria) {
        final double accuracy = 1.0e-10;
        this.optimizer = optimizer != null ? optimizer
                : new LevenbergMarquardt(accuracy, accuracy, accuracy);
        this.endCriteria = endCriteria != null ? endCriteria
                : new EndCriteria(1000, 10, accuracy, accuracy, accuracy);
    }

    /** Register a contributor and wire its parent-bootstrapper hook to this coordinator. */
    public void add(final MultiCurveBootstrapContributor c) {
        contributors.add(c);
        c.setParentBootstrapper(this);
    }

    /** Register an observer to be poked on every LM step (non-bootstrapped cycle curves). */
    public void addObserver(final Observer o) {
        observers.add(o);
    }

    /**
     * Drive the global optimisation. Called from {@link GlobalBootstrap#calculate()} on the first contributor whose
     * parent-bootstrapper has been set — that delegates here, and we then drive every contributor as one
     * concatenated cost function.
     */
    public void runMultiCurveBootstrap() {

        final List< Integer > guessSizes = new ArrayList<>();
        final List< Double > globalGuess = new ArrayList<>();

        for ( final MultiCurveBootstrapContributor c : contributors ) {
            final Array guess = c.setupCostFunction();
            for ( int i = 0; i < guess.size(); ++i ) {
                globalGuess.add(guess.get(i));
            }
            guessSizes.add(guess.size());
        }

        final CostFunction cost = new CostFunction() {
            @Override
            public double value(final Array x) {
                final Array v = values(x);
                double sum = 0.0;
                for ( int i = 0; i < v.size(); ++i ) {
                    sum += v.get(i) * v.get(i);
                }
                return 0.5 * sum;
            }

            @Override
            public Array values(final Array x) {
                // slice x into per-contributor chunks and dispatch
                int offset = 0;
                for ( int c = 0; c < contributors.size(); ++c ) {
                    final int len = guessSizes.get(c);
                    final Array tmp = new Array(len);
                    for ( int i = 0; i < len; ++i ) {
                        tmp.set(i, x.get(offset + i));
                    }
                    offset += len;
                    contributors.get(c).setCostFunctionArgument(tmp);
                }

                // fire observers (non-bootstrapped cycle curves)
                for ( final Observer o : observers ) {
                    o.update();
                }

                // Re-arm the calculated flag on every contributor's curve.
                // The o.update() above can cascade back into the bootstrapped
                // curves (via MultiCurve.update() if the non-bootstrapped curve
                // is observed by the MultiCurve), flipping their calculated
                // flag to false. Without this re-arming, the next discount()
                // lookup against the curve (via the spread chain inside
                // evaluateCostFunction's swap.recalculate) re-enters
                // bootstrap.calculate → runMultiCurveBootstrap → SOE.
                // Mirrors the protective semantics of C++ globalbootstrap.hpp
                // setupCostFunction's setCalculated(true), held across the
                // entire LM iteration. Phase 1.3 closure (D5-A-MCSpread).
                for ( final MultiCurveBootstrapContributor c2 : contributors ) {
                    if ( c2 instanceof org.jquantlib.termstructures.yieldcurves.GlobalBootstrap ) {
                        final org.jquantlib.util.LazyObject lo =
                                ((org.jquantlib.termstructures.yieldcurves.GlobalBootstrap) c2).ts();
                        if ( lo != null ) {
                            lo.setCalculated(true);
                        }
                    }
                }

                // collect residuals
                final List< Array > results = new ArrayList<>(contributors.size());
                int totalLen = 0;
                for ( final MultiCurveBootstrapContributor c : contributors ) {
                    final Array r = c.evaluateCostFunction();
                    results.add(r);
                    totalLen += r.size();
                }

                final Array result = new Array(totalLen);
                int rofs = 0;
                for ( final Array r : results ) {
                    for ( int i = 0; i < r.size(); ++i ) {
                        result.set(rofs + i, r.get(i));
                    }
                    rofs += r.size();
                }
                return result;
            }
        };

        final NoConstraint noConstraint = new NoConstraint();
        final Array initial = new Array(globalGuess.size());
        for ( int i = 0; i < globalGuess.size(); ++i ) {
            initial.set(i, globalGuess.get(i));
        }
        final Problem problem = new Problem(cost, noConstraint, initial);
        final EndCriteria.Type endType = optimizer.minimize(problem, endCriteria);

        QL.require(EndCriteria.succeeded(endType),
                "global bootstrap failed to minimize to required accuracy (during multi curve bootstrap): "
                        + endType);

        for ( final MultiCurveBootstrapContributor c : contributors ) {
            c.setToValid();
        }
    }
}
