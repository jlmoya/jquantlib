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
import org.jquantlib.math.optimization.*;

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
     * Re-run every time the instruments / referenceDate changes. Default implementation requires bond helpers to have
     * been registered with the curve via setTermStructure().
     */
    protected void init() {
        if ( curve_ == null || curve_.maxEvaluations() == 0 ) {
            return; // skip
        }
        // Full bond-fitting initialization (weights based on Bond duration) is
        // outside the Phase 5d.5-ZCS+FB scope; in this slice the parametric
        // (no-fit) branch is the supported entry point. Bond-helper-based fits
        // require BondHelper duration / yield calculations that are tracked as
        // a Phase 5d.5-ZCS+FBb carry-forward.
        QL.require(false, "FittingMethod.init(): bond-helper fitting is not yet ported; "
                + "use the parametric (no-fit) FittedBondDiscountCurve constructor");
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
     * Optimization routine entry. Parametric (no-fit) curves short-circuit: solution_ is set to the supplied guess, no
     * optimizer is invoked. Full bond-fitting via Simplex / LM is a Phase 5d.5-ZCS+FBb carry-forward.
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
        QL.require(false, "FittingMethod.calculate(): bond-helper fitting is not yet ported; "
                + "use the parametric (no-fit) FittedBondDiscountCurve constructor");
    }
}
