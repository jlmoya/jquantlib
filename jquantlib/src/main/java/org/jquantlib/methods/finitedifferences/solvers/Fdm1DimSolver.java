/*
 Copyright (C) 2011 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.finitedifferences.solvers;

import org.jquantlib.math.interpolations.MonotonicNaturalCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmSnapshotCondition;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.util.LazyObject;

/**
 * Lazy 1D wrapper around {@link FdmBackwardSolver} that interpolates the
 * rolled-back state as a function of the underlying.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/solvers/fdm1dimsolver.{hpp,cpp}}.
 *
 * <p>The Java port uses {@link MonotonicNaturalCubicInterpolation} as a
 * direct equivalent of C++ {@code MonotonicCubicNaturalSpline} (both wrap
 * the same underlying {@code CubicInterpolation} configuration:
 * spline + natural BCs + monotonicity-preserving derivative approximation).
 *
 * @author Phase 2h WI-1 port
 */
public class Fdm1DimSolver extends LazyObject {

    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;
    private final FdmLinearOpComposite op;

    private final FdmSnapshotCondition thetaCondition;
    private final FdmStepConditionComposite conditions;

    private final Array x;
    private final Array initialValues;
    private final Array resultValues;
    private MonotonicNaturalCubicInterpolation interpolation;

    public Fdm1DimSolver(final FdmSolverDesc solverDesc,
                         final FdmSchemeDesc schemeDesc,
                         final FdmLinearOpComposite op) {
        this.solverDesc = solverDesc;
        this.schemeDesc = schemeDesc;
        this.op = op;

        // thetaCondition_ at 0.99 * min(1/365, first stopping time or maturity)
        // Mirrors C++ Fdm1DimSolver constructor.
        final double earliestStop = solverDesc.condition.stoppingTimes().isEmpty()
                ? solverDesc.maturity
                : solverDesc.condition.stoppingTimes().get(0);
        this.thetaCondition = new FdmSnapshotCondition(
                0.99 * Math.min(1.0 / 365.0, earliestStop));

        this.conditions = FdmStepConditionComposite.joinConditions(
                thetaCondition, solverDesc.condition);

        final int size = solverDesc.mesher.layout().size();
        this.x = new Array(size);
        this.initialValues = new Array(size);
        this.resultValues = new Array(size);

        for (final FdmLinearOpIterator iter : solverDesc.mesher.layout()) {
            initialValues.set(iter.index(),
                    solverDesc.calculator.avgInnerValue(iter, solverDesc.maturity));
            x.set(iter.index(), solverDesc.mesher.location(iter, 0));
        }
    }

    @Override
    protected void performCalculations() {
        final Array rhs = initialValues.clone();

        new FdmBackwardSolver(op, solverDesc.bcSet, conditions, schemeDesc)
                .rollback(rhs, solverDesc.maturity, 0.0,
                        solverDesc.timeSteps, solverDesc.dampingSteps);

        resultValues.fill(rhs);
        // Constructor calls update() — no further work needed.
        interpolation = new MonotonicNaturalCubicInterpolation(x, resultValues.clone());
    }

    /** Interpolated solver value at {@code x}. */
    public double interpolateAt(final double xq) {
        calculate();
        return interpolation.op(xq);
    }

    /**
     * Finite-difference theta estimate at {@code x}: difference between the
     * snapshot at {@link #thetaCondition}'s target time and the present
     * value, divided by the snapshot time. Returns {@link Double#NaN}
     * (matching C++ {@code Null<Real>()}) if the first stopping time is
     * exactly zero.
     */
    public double thetaAt(final double xq) {
        if (!conditions.stoppingTimes().isEmpty()
                && conditions.stoppingTimes().get(0) == 0.0) {
            return Double.NaN;
        }
        calculate();
        final Array snapshot = thetaCondition.getValues();
        final MonotonicNaturalCubicInterpolation snap =
                new MonotonicNaturalCubicInterpolation(x, snapshot);
        return (snap.op(xq) - interpolateAt(xq)) / thetaCondition.getTime();
    }

    /** First-derivative of the interpolated value at {@code x}. */
    public double derivativeX(final double xq) {
        calculate();
        return interpolation.derivative(xq);
    }

    /** Second-derivative of the interpolated value at {@code x}. */
    public double derivativeXX(final double xq) {
        calculate();
        return interpolation.secondDerivative(xq);
    }
}
