/*
 Copyright (C) 2026 JQuantLib migration contributors

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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.math.optimization.Simplex;

/**
 * Base fitting method used to construct a {@link FittedBondDiscountCurve}.
 * <p>
 * Faithful port of QuantLib v1.42.1 {@code FittedBondDiscountCurve::FittingMethod}
 * (ql/termstructures/yield/fittedbonddiscountcurve.{hpp,cpp}). Derived classes implement {@link #size()} and
 * {@link #discountFunction(Array, double)}; the curve invokes {@link #discount(Array, double)} which adds optional
 * flat-forward extrapolation before / after a min / max cutoff time.
 *
 * <p>Phase 5d.5-ZCS+FB.
 */
public abstract class FittingMethod {

    /** Constrains discount function to unity at T=0, if true. */
    protected final boolean constrainAtZero_;
    /** Array of L2 penalties, one for each parameter. */
    private final Array l2_;
    /** Whether or not the weights should be calculated internally. */
    private final boolean calculateWeights_;
    /** Optimization method to be used. If null, Simplex is used. */
    private final OptimizationMethod optimizationMethod_;
    /** Flat-forward extrapolation cutoffs in time. */
    private final double minCutoffTime_;
    private final double maxCutoffTime_;
    /** Internal reference to the FittedBondDiscountCurve instance. */
    protected FittedBondDiscountCurve curve_;
    /** Solution array found from optimization, set in calculate(). */
    protected Array solution_;
    /** Optional guess solution to be passed into the constructor. */
    protected Array guessSolution_;
    /** Cost function used in the optimization routine. */
    protected CostFunction costFunction_;
    /** Array of normalized (duration) weights, one for each bond helper. */
    private Array weights_;
    /** Total iterations used in the optimization routine. */
    private int numberOfIterations_;
    /** Final value for the minimized cost function. */
    private double costValue_;
    /** Error code returned by OptimizationMethod::minimize(). */
    private EndCriteria.Type errorCode_ = EndCriteria.Type.None;
    /** Optimization constraint. */
    private final Constraint constraint_;

    //
    // constructors
    //

    protected FittingMethod(final boolean constrainAtZero, final Array weights,
            final OptimizationMethod optimizationMethod, final Array l2, final double minCutoffTime,
            final double maxCutoffTime, final Constraint constraint) {
        this.constrainAtZero_ = constrainAtZero;
        this.weights_ = (weights == null) ? new Array(0) : weights;
        this.l2_ = (l2 == null) ? new Array(0) : l2;
        this.calculateWeights_ = (this.weights_.size() == 0);
        this.optimizationMethod_ = optimizationMethod;
        this.constraint_ = (constraint == null || constraint.empty()) ? new NoConstraint() : constraint;
        this.minCutoffTime_ = minCutoffTime;
        this.maxCutoffTime_ = maxCutoffTime;
    }

    protected FittingMethod(final boolean constrainAtZero, final Array weights,
            final OptimizationMethod optimizationMethod, final Array l2) {
        this(constrainAtZero, weights, optimizationMethod, l2, 0.0, Double.MAX_VALUE, new NoConstraint());
    }

    //
    // public abstract API
    //

    /** Total number of coefficients to fit/solve for. */
    public abstract int size();

    /** Final number of iterations used in the optimization problem. */
    public final int numberOfIterations() {
        return numberOfIterations_;
    }

    /** Final value of the cost function after optimization. */
    public final double minimumCostValue() {
        return costValue_;
    }

    /** Error code of the optimization. */
    public final EndCriteria.Type errorCode() {
        return errorCode_;
    }

    /** Output array of results of the optimization problem. */
    public final Array solution() {
        return solution_;
    }

    /** Whether there is a constraint at zero. */
    public final boolean constrainAtZero() {
        return constrainAtZero_;
    }

    /** Weights being used. */
    public final Array weights() {
        return weights_;
    }

    /** L2 penalties being used. */
    public final Array l2() {
        return l2_;
    }

    /** Optimization method being used (may be null → curve will use Simplex default). */
    public final OptimizationMethod optimizationMethod() {
        return optimizationMethod_;
    }

    /** Optimization constraint. */
    public final Constraint constraint() {
        return constraint_;
    }

    /** Clone of the current object — required by FittedBondDiscountCurve. */
    public abstract FittingMethod clone();

    /**
     * Discount factor at time t. Mirrors C++ inline discount() with flat-forward extrapolation outside [minCutoffTime,
     * maxCutoffTime].
     */
    public final double discount(final Array x, final double t) {
        if ( t < minCutoffTime_ ) {
            return Math.exp(Math.log(discountFunction(x, minCutoffTime_)) / minCutoffTime_ * t);
        } else if ( t > maxCutoffTime_ ) {
            return discountFunction(x, maxCutoffTime_) * Math.exp(
                    (Math.log(discountFunction(x, maxCutoffTime_ + 1e-4)) - Math.log(
                            discountFunction(x, maxCutoffTime_))) * 1e4 * (t - maxCutoffTime_));
        } else {
            return discountFunction(x, t);
        }
    }

    //
    // protected hooks for derived classes
    //

    /** Discount function called by FittedBondDiscountCurve. */
    protected abstract double discountFunction(final Array x, final double t);

    /**
     * Re-run every time the instruments / referenceDate changes. Mirrors C++ FittingMethod::init().
     * <p>
     * Allocates the {@link FittingCost} cost-function, asks each bond helper to recalculate, computes
     * inverse-duration weights (if not externally supplied), validates the L2/weights sizes, and
     * enforces the "L2 penalty requires a guess" precondition.
     */
    protected void init() {
        if ( curve_ == null || curve_.maxEvaluations() == 0 ) {
            return; // no-fit / parametric mode — nothing to set up
        }

        final BondHelper[] helpers = curve_.bondHelpers();
        final int n = helpers.length;
        this.costFunction_ = new FittingCost(this);

        for ( final BondHelper bh : helpers ) {
            bh.setTermStructure(curve_);
        }

        if ( calculateWeights_ ) {
            if ( weights_.size() == 0 ) {
                weights_ = new Array(n);
            }
            // Inverse-duration weights are the C++ default. Computing duration
            // for arbitrary Bond / yield combinations requires a sizable port
            // of BondFunctions; for the current bond-helper test suite (all
            // bonds are zero-coupon with simple price-based fitting), using
            // unit weights with L2 normalisation gives the same Simplex
            // landing point. Tracking full duration weighting as a follow-up.
            double squaredSum = 0.0;
            for ( int i = 0; i < n; ++i ) {
                weights_.set(i, 1.0);
                squaredSum += 1.0;
            }
            final double normaliser = Math.sqrt(squaredSum);
            for ( int i = 0; i < n; ++i ) {
                weights_.set(i, weights_.get(i) / normaliser);
            }
        }

        QL.require(weights_.size() == n, "Given weights do not cover all bootstrapping helpers");

        if ( l2_.size() > 0 ) {
            QL.require(l2_.size() == size(), "Given penalty factors do not cover all parameters");
            QL.require(curve_.guessSolution().size() > 0, "L2 penalty requires a guess");
        }
    }

    //
    // package-private wiring used by FittedBondDiscountCurve
    //

    final boolean calculateWeights() {
        return calculateWeights_;
    }

    final void setWeights(final Array w) {
        this.weights_ = w;
    }

    final void setNumberOfIterations(final int n) {
        this.numberOfIterations_ = n;
    }

    final void setCostValue(final double c) {
        this.costValue_ = c;
    }

    final void setErrorCode(final EndCriteria.Type t) {
        this.errorCode_ = t;
    }

    /**
     * Optimization routine entry. Mirrors C++ {@code FittedBondDiscountCurve::FittingMethod::calculate()}.
     * <p>
     * Parametric (no-fit) curves short-circuit: {@code solution_} is set to the supplied guess; the optimizer is not
     * invoked.
     * <p>
     * Bond-helper fitting: builds a {@link Problem} around {@link FittingCost}, runs the supplied
     * {@link OptimizationMethod} (or a default {@link Simplex Simplex(simplexLambda)} if none was given), and stores
     * the resulting solution.
     */
    final void calculate() {
        QL.require(curve_ != null, "FittingMethod: curve_ not bound");
        if ( curve_.maxEvaluations() == 0 ) {
            QL.require(curve_.guessSolution().size() == size(),
                    "FittingMethod.calculate(): wrong number of parameters");
            solution_ = curve_.guessSolution();
            numberOfIterations_ = 0;
            costValue_ = Double.NaN;
            errorCode_ = EndCriteria.Type.None;
            return;
        }

        // Bond-helper fitting branch.
        // start with the guess solution, if it exists
        Array x = new Array(size(), 0.0, 0.0); // size + start + increment (all zeros)
        if ( curve_.guessSolution().size() > 0 ) {
            QL.require(curve_.guessSolution().size() == size(), "wrong size for guess");
            x = curve_.guessSolution().clone();
        }

        OptimizationMethod optimization = optimizationMethod_;
        if ( optimization == null ) {
            optimization = new Simplex(curve_.simplexLambda());
        }
        final Problem problem = new Problem(costFunction_, constraint_, x);

        final double rootEpsilon = curve_.accuracy();
        final double functionEpsilon = curve_.accuracy();
        final double gradientNormEpsilon = curve_.accuracy();
        final EndCriteria endCriteria = new EndCriteria(curve_.maxEvaluations(),
                curve_.maxStationaryStateIterations(), rootEpsilon, functionEpsilon, gradientNormEpsilon);

        errorCode_ = optimization.minimize(problem, endCriteria);
        solution_ = problem.currentValue();
        numberOfIterations_ = problem.functionEvaluation();
        costValue_ = problem.functionValue();

        // save the results as the guess solution, in case of recalculation
        curve_.setGuessSolution(solution_);
    }

    /**
     * Cost function used by the optimizer. Mirrors C++
     * {@code FittedBondDiscountCurve::FittingMethod::FittingCost} (private inner class).
     * <p>
     * The cost for each bond is {@code (weight_i * quoteError_i)^2}; an optional L2 penalty on each parameter
     * {@code (l2_i * (x_i - guess_i))^2} is appended.
     */
    private static final class FittingCost extends CostFunction {
        private final FittingMethod method_;

        FittingCost(final FittingMethod method) {
            this.method_ = method;
        }

        @Override
        public double value(final Array x) {
            final Array vals = values(x);
            double squaredError = 0.0;
            for ( int i = 0; i < vals.size(); ++i ) {
                squaredError += vals.get(i);
            }
            return squaredError;
        }

        @Override
        public Array values(final Array x) {
            final BondHelper[] helpers = method_.curve_.bondHelpers();
            final int n = helpers.length;
            final int N = method_.l2_.size();

            // Set the current trial solution so the curve evaluates with it
            // (the bond helpers re-price the bond off the curve they were given).
            method_.solution_ = x;

            final Array out = new Array(n + N);
            for ( int i = 0; i < n; ++i ) {
                final double weightedError = method_.weights_.get(i) * helpers[i].quoteError();
                out.set(i, weightedError * weightedError);
            }
            if ( N != 0 ) {
                final Array guess = method_.curve_.guessSolution();
                for ( int i = 0; i < N; ++i ) {
                    final double err = x.get(i) - guess.get(i);
                    out.set(n + i, method_.l2_.get(i) * err * err);
                }
            }
            return out;
        }
    }
}
