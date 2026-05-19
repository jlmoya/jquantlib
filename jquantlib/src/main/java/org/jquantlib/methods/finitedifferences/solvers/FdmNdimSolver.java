/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2011 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.methods.finitedifferences.solvers;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.MultiCubicSpline;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmSnapshotCondition;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.util.LazyObject;

/**
 * N-dimensional finite-difference solver with multi-cubic-spline output.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/solvers/fdmndimsolver.hpp} (header-only).
 *
 * <p>C++ dispatches on {@code N} at compile time via templates
 * ({@code FdmNdimSolver<N>}); the Java port stores the dimension as a runtime field and uses {@link MultiCubicSpline}'s
 * flat-array implementation, which is functionally identical (natural cubic spline along every axis).
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Compute the initial-condition vector by sampling the inner-value
 *       calculator over every mesh cell.</li>
 *   <li>Roll back via {@link FdmBackwardSolver} to {@code t = 0}.</li>
 *   <li>Re-shape the resulting flat vector into an N-d grid (matching the
 *       layout's iteration order) and build a {@link MultiCubicSpline}.</li>
 *   <li>{@link #interpolateAt(double[])} evaluates the spline at an
 *       arbitrary point in the N-d state space.</li>
 * </ol>
 *
 * @author Phase 5e.5b-CFC-d-280 port
 */
public final class FdmNdimSolver extends LazyObject {

    private final int n;                            // dimensions
    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;
    private final FdmLinearOpComposite op;

    private final FdmSnapshotCondition thetaCondition;
    private final FdmStepConditionComposite conditions;

    private final double[][] x;                     // per-axis knots
    private final double[] initialValues;
    private final boolean[] extrapolation;

    private double[] f;                              // flat grid result
    private MultiCubicSpline interp;

    public FdmNdimSolver(final FdmSolverDesc solverDesc, final FdmSchemeDesc schemeDesc,
            final FdmLinearOpComposite op) {
        this.solverDesc = solverDesc;
        this.schemeDesc = schemeDesc;
        this.op = op;

        final double earliestStop = solverDesc.condition.stoppingTimes().isEmpty()
                ? solverDesc.maturity
                : solverDesc.condition.stoppingTimes().get(0);
        this.thetaCondition = new FdmSnapshotCondition(0.99 * Math.min(1.0 / 365.0, earliestStop));
        this.conditions = FdmStepConditionComposite.joinConditions(thetaCondition, solverDesc.condition);

        final FdmLinearOpLayout layout = solverDesc.mesher.layout();
        final int[] dim = layout.dim();
        this.n = dim.length;
        QL.require(n >= 1, "solver dim must be >= 1");

        this.x = new double[n][];
        for ( int i = 0; i < n; ++i ) {
            this.x[i] = new double[dim[i]];
        }
        this.initialValues = new double[layout.size()];
        this.extrapolation = new boolean[n];

        // Walk the layout: for each iter, capture inner value and (when
        // the other coordinates are all zero) also capture the axis knot.
        // This mirrors C++ which uses `std::accumulate(c.begin(), c.end(), 0UL) - c[i] == 0U`.
        final int[] axisCursor = new int[n];
        for ( final FdmLinearOpIterator iter : layout ) {
            initialValues[iter.index()] = solverDesc.calculator.avgInnerValue(iter, solverDesc.maturity);
            final int[] c = iter.coordinates();
            int sum = 0;
            for ( final int ci : c )
                sum += ci;
            for ( int i = 0; i < n; ++i ) {
                if ( sum - c[i] == 0 ) {
                    x[i][axisCursor[i]++] = solverDesc.mesher.location(iter, i);
                }
            }
        }
    }

    @Override
    protected void performCalculations() {
        final Array rhs = new Array(initialValues.length);
        for ( int i = 0; i < initialValues.length; ++i )
            rhs.set(i, initialValues[i]);

        new FdmBackwardSolver(op, solverDesc.bcSet, conditions, schemeDesc).rollback(rhs, solverDesc.maturity, 0.0,
                solverDesc.timeSteps, solverDesc.dampingSteps);

        f = layoutToGrid(rhs);
        interp = new MultiCubicSpline(x, f, extrapolation);
    }

    /**
     * Re-shape the layout-ordered flat vector into a grid whose flat layout matches {@link MultiCubicSpline}'s
     * row-major (last-axis-fastest) convention. The {@link FdmLinearOpLayout} walks coordinates with the first axis
     * fastest (C++ column-major), so we transpose here.
     */
    private double[] layoutToGrid(final Array rhs) {
        final FdmLinearOpLayout layout = solverDesc.mesher.layout();
        final int[] dim = layout.dim();
        final int total = layout.size();
        final double[] g = new double[total];
        // strides for the MultiCubicSpline ordering: stride[n-1]=1, stride[i]=stride[i+1]*dim[i+1]
        final int[] stride = new int[n];
        stride[n - 1] = 1;
        for ( int i = n - 2; i >= 0; --i )
            stride[i] = stride[i + 1] * dim[i + 1];

        for ( final FdmLinearOpIterator iter : layout ) {
            final int[] c = iter.coordinates();
            int flat = 0;
            for ( int i = 0; i < n; ++i )
                flat += c[i] * stride[i];
            g[flat] = rhs.get(iter.index());
        }
        return g;
    }

    /** Interpolate the solution at the supplied N-d point. */
    public double interpolateAt(final double[] x) {
        calculate();
        return interp.op(x);
    }

    /** Theta estimate at the supplied N-d point. */
    public double thetaAt(final double[] x) {
        if ( conditions.stoppingTimes().get(0) == 0.0 ) {
            return Double.NaN;
        }
        calculate();
        final Array rhs = thetaCondition.getValues();
        final double[] snap = layoutToGrid(rhs);
        final MultiCubicSpline snapInterp = new MultiCubicSpline(this.x, snap, extrapolation);
        return (snapInterp.op(x) - interpolateAt(x)) / thetaCondition.getTime();
    }
}
