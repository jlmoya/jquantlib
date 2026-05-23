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
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2009 Andrea Odetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.mcbasket;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.GeneralLinearLeastSquares;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Longstaff-Schwartz multi-path pricer for early-exercise basket options.
 *
 * <p>Phase 4i port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/longstaffschwartzmultipathpricer.{hpp,cpp}}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References:
 * Longstaff & Schwartz (2001), <i>Valuing American Options by Simulation: A Simple Least-Squares Approach</i>, Review
 * of Financial Studies 14(1), 113-147.
 *
 * <h3>Phase 4i.5 implementation (P3-B)</h3>
 *
 * <p>The pricer holds the full LSM state machine (calibration / pricing
 * phases, regression coefficients, lower-bound tracking). {@link #op(MultiPath)} and {@link #calibrate()} now mirror
 * the C++ algorithm in {@code longstaffschwartzmultipathpricer.cpp}, using {@link MultiPath}, the multi-asset
 * {@link LsmBasisSystem#multiPathBasisSystem} and {@link GeneralLinearLeastSquares} for the per-step regression.
 */
public class LongstaffSchwartzMultiPathPricer extends PathPricer< MultiPath > {

    protected final PathPayoff payoff_;
    protected final int[] timePositions_;
    protected final List< Handle< YieldTermStructure > > forwardTermStructures_;
    protected final Array dF_;
    /** Calibration-time storage of per-path information. */
    protected final List< PathInfo > paths_ = new ArrayList<>();
    protected final int polynomialOrder_;
    protected final PolynomialType polynomialType_;
    /** Multi-asset basis functions (state-dimension = payoff_.basisSystemDimension()). */
    protected final List< Ops.ObjectToDouble< Array > > v_;
    protected boolean calibrationPhase_ = true;
    protected Array[] coeff_;
    protected double[] lowerBounds_;

    public LongstaffSchwartzMultiPathPricer(final PathPayoff payoff, final int[] timePositions,
            final List< Handle< YieldTermStructure > > forwardTermStructures, final Array discounts,
            final int polynomialOrder, final PolynomialType polynomialType) {
        this.payoff_ = payoff;
        this.timePositions_ = Arrays.copyOf(timePositions, timePositions.length);
        this.forwardTermStructures_ = forwardTermStructures;
        this.dF_ = discounts;
        this.polynomialOrder_ = polynomialOrder;
        this.polynomialType_ = polynomialType;
        this.coeff_ = new Array[timePositions.length - 1];
        this.lowerBounds_ = new double[timePositions.length];

        // Mirrors C++ runtime check.
        switch ( polynomialType ) {
        case Monomial:
        case Laguerre:
        case Hermite:
        case Hyperbolic:
        case Chebyshev2nd:
            break;
        default:
            throw new LibraryException("insufficient polynomial type");
        }

        // Construct the multi-asset basis system. Mirrors C++:
        //   v_(LsmBasisSystem::multiPathBasisSystem(
        //          payoff->basisSystemDimension(), polynomialOrder, polynomialType))
        this.v_ = LsmBasisSystem.multiPathBasisSystem(payoff.basisSystemDimension(), polynomialOrder,
                toLsmType(polynomialType));
    }

    /**
     * Map our local {@link PolynomialType} enum to the canonical
     * {@link LsmBasisSystem.PolynomialType}. The local enum is a strict subset of the canonical one (matches the C++
     * runtime guard above).
     */
    private static LsmBasisSystem.PolynomialType toLsmType(final PolynomialType type) {
        return switch ( type ) {
            case Monomial -> LsmBasisSystem.PolynomialType.Monomial;
            case Laguerre -> LsmBasisSystem.PolynomialType.Laguerre;
            case Hermite -> LsmBasisSystem.PolynomialType.Hermite;
            case Hyperbolic -> LsmBasisSystem.PolynomialType.Hyperbolic;
            case Chebyshev2nd -> LsmBasisSystem.PolynomialType.Chebyshev2nd;
        };
    }

    /**
     * Extract the relevant {@link PathInfo} from the multi-path. Mirrors C++
     * {@code LongstaffSchwartzMultiPathPricer::transformPath} lines 61-80.
     */
    protected PathInfo transformPath(final MultiPath multiPath) {
        final int numberOfAssets = multiPath.assetNumber();
        final int numberOfTimes = timePositions_.length;

        final Matrix path = new Matrix(numberOfAssets, numberOfTimes);

        for ( int i = 0; i < numberOfTimes; i++ ) {
            final int pos = timePositions_[i];
            for ( int j = 0; j < numberOfAssets; j++ ) {
                path.set(j, i, multiPath.get(j).get(pos));
            }
        }

        final PathInfo info = new PathInfo(numberOfTimes);
        payoff_.value(path, forwardTermStructures_, info.payments, info.exercises, info.states);
        return info;
    }

    /**
     * Mirrors C++ {@code Real operator()(const MultiPath&)} lines 82-153 of
     * {@code longstaffschwartzmultipathpricer.cpp}. During the calibration phase it stores the relevant per-path
     * data for {@link #calibrate()} to consume; during the pricing phase it backward-inducts using the calibrated
     * regression coefficients.
     */
    @Override
    public Double op(final MultiPath multiPath) {
        final PathInfo path = transformPath(multiPath);

        if ( calibrationPhase_ ) {
            // store paths for the calibration — only the relevant part.
            paths_.add(path);
            // result doesn't matter
            return 0.0;
        }

        // exercise at time t cancels all payment AFTER t.

        final int len = path.pathLength();
        double price = 0.0;

        // last event date
        {
            final double payoff = path.payments.get(len - 1);
            final double exercise = path.exercises.get(len - 1);
            final Array states = path.states.get(len - 1);
            final boolean canExercise = states.size() > 0;

            // at the end the continuation value is 0.0
            if ( canExercise && exercise > 0.0 ) {
                price += exercise;
            }
            price += payoff;
        }

        for ( int i = len - 2; i >= 0; i-- ) {
            price *= dF_.get(i + 1) / dF_.get(i);

            final double exercise = path.exercises.get(i);

            /*
             * coeff_[i].size()
             *   - 0           => never exercise
             *   - v_.size()   => use estimated continuation value (if > lowerBounds_[i])
             *   - v_.size()+1 => always exercise
             *
             * In any case if states is empty, no exercise is allowed.
             */
            final Array states = path.states.get(i);
            final boolean canExercise = states.size() > 0;

            if ( canExercise ) {
                if ( coeff_[i].size() == v_.size() + 1 ) {
                    // special value: always exercise
                    price = exercise;
                } else {
                    if ( coeff_[i].size() > 0 && exercise > lowerBounds_[i] ) {
                        double continuationValue = 0.0;
                        for ( int l = 0; l < v_.size(); l++ ) {
                            continuationValue += coeff_[i].get(l) * v_.get(l).op(states);
                        }
                        if ( continuationValue < exercise ) {
                            price = exercise;
                        }
                    }
                }
            }
            final double payoff = path.payments.get(i);
            price += payoff;
        }

        return price * dF_.get(0);
    }

    /**
     * Two-phase calibration: solves the per-step regression and decides always/never/optimised exercise. Mirrors C++
     * {@code LongstaffSchwartzMultiPathPricer::calibrate()} lines 155-308.
     */
    public void calibrate() {
        final int n = paths_.size(); // number of paths
        QL.require(n > 0, "no paths to calibrate over");
        final double[] prices = new double[n];
        final double[] exercise = new double[n];

        final int basisDimension = payoff_.basisSystemDimension();
        final int len = paths_.get(0).pathLength();

        /*
         * Estimate the lower bound of the continuation value so that only ITM paths contribute to the regression.
         */
        for ( int j = 0; j < n; j++ ) {
            final double payoff = paths_.get(j).payments.get(len - 1);
            final double exerciseLast = paths_.get(j).exercises.get(len - 1);
            final Array states = paths_.get(j).states.get(len - 1);
            final boolean canExercise = states.size() > 0;

            // at the end the continuation value is 0.0
            if ( canExercise && exerciseLast > 0.0 ) {
                prices[j] += exerciseLast;
            }
            prices[j] += payoff;
        }

        lowerBounds_[len - 1] = minOf(prices);

        final boolean[] lsExercise = new boolean[n];

        for ( int i = len - 2; i >= 0; i-- ) {
            final List< Double > y = new ArrayList<>();
            final List< Array > x = new ArrayList<>();

            // prices are discounted up to time i
            final double discountRatio = dF_.get(i + 1) / dF_.get(i);
            for ( int j = 0; j < n; j++ ) {
                prices[j] *= discountRatio;
            }
            lowerBounds_[i + 1] *= discountRatio;

            // roll back step
            for ( int j = 0; j < n; j++ ) {
                exercise[j] = paths_.get(j).exercises.get(i);

                // If states is empty, no exercise in this path; it will not
                // participate in the least-squares regression.
                final Array states = paths_.get(j).states.get(i);
                QL.require(states.size() == 0 || states.size() == basisDimension,
                        "Invalid size of basis system");

                // Only paths that could potentially create exercise opportunities
                // participate in the regression.
                // If exercise is lower than the minimum continuation value, there
                // is no point in considering it.
                if ( states.size() > 0 && exercise[j] > lowerBounds_[i + 1] ) {
                    x.add(states);
                    y.add(prices[j]);
                }
            }

            if ( v_.size() <= x.size() ) {
                final Array[] xArr = x.toArray(new Array[0]);
                final double[] yArr = new double[y.size()];
                for ( int k = 0; k < yArr.length; k++ ) {
                    yArr[k] = y.get(k);
                }
                coeff_[i] = new GeneralLinearLeastSquares(xArr, yArr, v_).coefficients();
            } else {
                // If number of ITM paths is smaller than the number of
                // calibration functions -> never exercise.
                coeff_[i] = new Array(0);
            }

            /*
             * Attempt to avoid static arbitrage given by always or never exercising. "Always" is absolute regardless
             * of lowerBoundContinuationValue_ (this could be changed), but it still honours "canExercise".
             */
            double sumOptimized = 0.0;
            double sumNoExercise = 0.0;
            double sumAlwaysExercise = 0.0; // always, if allowed

            for ( int j = 0, k = 0; j < n; j++ ) {
                sumNoExercise += prices[j];
                lsExercise[j] = false;

                final boolean canExercise = paths_.get(j).states.get(i).size() > 0;
                if ( canExercise ) {
                    sumAlwaysExercise += exercise[j];
                    if ( coeff_[i].size() > 0 && exercise[j] > lowerBounds_[i + 1] ) {
                        double continuationValue = 0.0;
                        for ( int l = 0; l < v_.size(); l++ ) {
                            continuationValue += coeff_[i].get(l) * v_.get(l).op(x.get(k));
                        }
                        if ( continuationValue < exercise[j] ) {
                            lsExercise[j] = true;
                        }
                        ++k;
                    }
                } else {
                    sumAlwaysExercise += prices[j];
                }

                sumOptimized += lsExercise[j] ? exercise[j] : prices[j];
            }

            sumOptimized /= n;
            sumNoExercise /= n;
            sumAlwaysExercise /= n;

            if ( sumOptimized >= sumNoExercise && sumOptimized >= sumAlwaysExercise ) {
                // Accepted LS decision.
                for ( int j = 0; j < n; j++ ) {
                    // lsExercise already contains "canExercise"
                    prices[j] = lsExercise[j] ? exercise[j] : prices[j];
                }
            } else if ( sumAlwaysExercise > sumNoExercise ) {
                // Overridden bad LS decision: ALWAYS.
                for ( int j = 0; j < n; j++ ) {
                    final boolean canExercise = paths_.get(j).states.get(i).size() > 0;
                    prices[j] = canExercise ? exercise[j] : prices[j];
                }
                // special value to indicate "always exercise"
                coeff_[i] = new Array(v_.size() + 1);
            } else {
                // Overridden bad LS decision: NEVER.
                // prices already contain the continuation value;
                // special value to indicate "never exercise".
                coeff_[i] = new Array(0);
            }

            // Then add (in any case) the payment at time t, which is made even
            // if cancellation happens at t.
            for ( int j = 0; j < n; j++ ) {
                final double payoff = paths_.get(j).payments.get(i);
                prices[j] += payoff;
            }

            lowerBounds_[i] = minOf(prices);
        }

        // remove calibration paths
        paths_.clear();
        // entering the calculation phase
        calibrationPhase_ = false;
    }

    private static double minOf(final double[] xs) {
        double m = xs[0];
        for ( int i = 1; i < xs.length; i++ ) {
            if ( xs[i] < m ) {
                m = xs[i];
            }
        }
        return m;
    }

    /**
     * Polynomial-family selector for the LSM basis. Mirrors {@code LsmBasisSystem::PolynomialType} restricted to the
     * subset accepted by the C++ runtime guard in
     * {@code LongstaffSchwartzMultiPathPricer::LongstaffSchwartzMultiPathPricer}.
     */
    public enum PolynomialType {
        Monomial, Laguerre, Hermite, Hyperbolic, Chebyshev2nd
    }

    /**
     * Cached per-path payoff/exercise/state data. Populated during the calibration phase and replayed during pricing.
     */
    public static class PathInfo {
        public Array payments;
        public Array exercises;
        public List< Array > states;

        public PathInfo(final int numberOfTimes) {
            this.payments = new Array(numberOfTimes);
            this.exercises = new Array(numberOfTimes);
            this.states = new ArrayList<>(numberOfTimes);
            for ( int i = 0; i < numberOfTimes; ++i ) {
                states.add(new Array(0));
            }
        }

        public int pathLength() {
            return states.size();
        }
    }
}
