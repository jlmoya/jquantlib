/*
 Copyright (C) 2010 Klaus Spanderen

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

import org.jquantlib.math.interpolations.BicubicSplineInterpolation;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.CubicInterpolation.BoundaryCondition;
import org.jquantlib.math.interpolations.CubicInterpolation.DerivativeApprox;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmSnapshotCondition;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.util.LazyObject;

/**
 * Lazy 2D wrapper around {@link FdmBackwardSolver} that interpolates the
 * rolled-back state as a function of two underlyings.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/solvers/fdm2dimsolver.{hpp,cpp}}.
 *
 * <p>The Java port uses {@link BicubicSplineInterpolation} as the direct
 * equivalent of C++ {@code BicubicSpline}. {@code BicubicSplineInterpolation}
 * only exposes the {@code op(x,y)} value query directly; the analytic
 * partial derivatives are reconstructed here (Phase 4n.5d) by mirroring
 * C++ {@code BicubicSplineImpl::derivativeX/secondDerivativeX} — build a
 * 1D x-section by sampling the bicubic-spline value at every x-grid point
 * for the fixed query y, then take the derivative/second-derivative of a
 * fresh {@link CubicInterpolation} along x. {@code derivativeY/YY/XY} are
 * not yet wired (no caller needs them); add them analogously when needed.
 *
 * @author Phase 2h WI-1 port; Phase 4n.5d analytic gamma/delta
 */
public class Fdm2DimSolver extends LazyObject {

    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;
    private final FdmLinearOpComposite op;

    private final FdmSnapshotCondition thetaCondition;
    private final FdmStepConditionComposite conditions;

    private final Array x;
    private final Array y;
    private final Array initialValues;
    private final Matrix resultValues;
    private BicubicSplineInterpolation interpolation;

    public Fdm2DimSolver(final FdmSolverDesc solverDesc,
                         final FdmSchemeDesc schemeDesc,
                         final FdmLinearOpComposite op) {
        this.solverDesc = solverDesc;
        this.schemeDesc = schemeDesc;
        this.op = op;

        final double earliestStop = solverDesc.condition.stoppingTimes().isEmpty()
                ? solverDesc.maturity
                : solverDesc.condition.stoppingTimes().get(0);
        this.thetaCondition = new FdmSnapshotCondition(
                0.99 * Math.min(1.0 / 365.0, earliestStop));
        this.conditions = FdmStepConditionComposite.joinConditions(
                thetaCondition, solverDesc.condition);

        final FdmLinearOpLayout layout = solverDesc.mesher.layout();
        final int[] dim = layout.dim();
        // Mirrors C++ Matrix(dim[1], dim[0]) — note rows == y-extent, cols == x-extent.
        this.resultValues = new Matrix(dim[1], dim[0]);
        this.initialValues = new Array(layout.size());
        this.x = new Array(dim[0]);
        this.y = new Array(dim[1]);

        int xCount = 0;
        int yCount = 0;
        for (final FdmLinearOpIterator iter : layout) {
            initialValues.set(iter.index(),
                    solverDesc.calculator.avgInnerValue(iter, solverDesc.maturity));
            if (iter.coordinates()[1] == 0) {
                x.set(xCount++, solverDesc.mesher.location(iter, 0));
            }
            if (iter.coordinates()[0] == 0) {
                y.set(yCount++, solverDesc.mesher.location(iter, 1));
            }
        }
    }

    @Override
    protected void performCalculations() {
        final Array rhs = initialValues.clone();

        new FdmBackwardSolver(op, solverDesc.bcSet, conditions, schemeDesc)
                .rollback(rhs, solverDesc.maturity, 0.0,
                        solverDesc.timeSteps, solverDesc.dampingSteps);

        // Reshape rhs into (dim[1] rows, dim[0] cols), column-major in C++.
        // C++ std::copy(rhs.begin(), rhs.end(), Matrix.begin()) writes
        // row-major into a Matrix that was constructed with rows = dim[1],
        // cols = dim[0]. JQuantLib Matrix::set(row, col, v) is row-major.
        // Thus iterating flat index k == j*dim[0] + i must map to
        // resultValues[j, i] — exactly the row/col split below.
        final int rows = resultValues.rows();
        final int cols = resultValues.columns();
        for (int j = 0; j < rows; ++j) {
            for (int i = 0; i < cols; ++i) {
                resultValues.set(j, i, rhs.get(j * cols + i));
            }
        }
        interpolation = new BicubicSplineInterpolation(x, y, resultValues);
    }

    /** Interpolated solver value at {@code (x, y)}. */
    public double interpolateAt(final double xq, final double yq) {
        calculate();
        return interpolation.op(xq, yq);
    }

    /**
     * Finite-difference theta estimate at {@code (x, y)}.
     * <p>
     * Mirrors C++ v1.42.1 {@code Fdm2DimSolver::thetaAt}: returns
     * {@link Double#NaN} (matching C++ {@code Null<Real>()}) if the first
     * stopping time is exactly zero. Otherwise, builds a fresh
     * {@link BicubicSplineInterpolation} from the snapshot recorded by
     * {@link #thetaCondition} and returns
     * {@code (snap(x,y) - interpolateAt(x,y)) / thetaCondition.getTime()}.
     *
     * <p>The snapshot reshape mirrors {@link #performCalculations()}: flat
     * index {@code k = j*cols + i} maps to {@code thetaValues[j, i]}.
     */
    public double thetaAt(final double xq, final double yq) {
        if (!conditions.stoppingTimes().isEmpty()
                && conditions.stoppingTimes().get(0) == 0.0) {
            return Double.NaN;
        }
        calculate();

        final Array snapshot = thetaCondition.getValues();
        if (snapshot == null) {
            // No snapshot recorded — should not happen if the snapshot time
            // lies within the rollback range, but guard anyway.
            return Double.NaN;
        }

        final int rows = resultValues.rows();
        final int cols = resultValues.columns();
        final Matrix thetaValues = new Matrix(rows, cols);
        for (int j = 0; j < rows; ++j) {
            for (int i = 0; i < cols; ++i) {
                thetaValues.set(j, i, snapshot.get(j * cols + i));
            }
        }

        final BicubicSplineInterpolation thetaInterp =
                new BicubicSplineInterpolation(x, y, thetaValues);
        return (thetaInterp.op(xq, yq) - interpolation.op(xq, yq))
               / thetaCondition.getTime();
    }

    /**
     * Analytic first partial derivative of the bicubic spline along x at
     * {@code (xq, yq)}.
     * <p>
     * Mirrors C++ v1.42.1 {@code BicubicSplineImpl::derivativeX}
     * (lines 81–93 of {@code bicubicsplineinterpolation.hpp}): build a
     * 1D x-section by evaluating the bicubic spline at every x-grid node
     * for the fixed query y, then return the first derivative of a fresh
     * cubic spline through that section evaluated at xq.
     */
    public double derivativeX(final double xq, final double yq) {
        calculate();
        final CubicInterpolation section = xSectionAt(yq);
        return section.derivative(xq);
    }

    /**
     * Analytic second partial derivative of the bicubic spline along x at
     * {@code (xq, yq)}.
     * <p>
     * Mirrors C++ v1.42.1 {@code BicubicSplineImpl::secondDerivativeX}
     * (lines 95–108 of {@code bicubicsplineinterpolation.hpp}): same
     * x-section construction as {@link #derivativeX}, returning the
     * second derivative of the section spline at xq.
     */
    public double derivativeXX(final double xq, final double yq) {
        calculate();
        final CubicInterpolation section = xSectionAt(yq);
        return section.secondDerivative(xq);
    }

    /**
     * Build a 1D cubic spline along x by evaluating the 2D bicubic-spline
     * value at every x-grid point with y fixed at {@code yq}. Mirrors the
     * C++ section vector inside {@code derivativeX}/{@code secondDerivativeX}
     * — boundary conditions and spline configuration are kept identical
     * (Spline + SecondDerivative=0 on both ends).
     */
    private CubicInterpolation xSectionAt(final double yq) {
        final int nx = x.size();
        final double[] section = new double[nx];
        for (int i = 0; i < nx; ++i) {
            section[i] = interpolation.op(x.get(i), yq);
        }
        return new CubicInterpolation(
                x, new Array(section),
                DerivativeApprox.Spline, false,
                BoundaryCondition.SecondDerivative, 0.0,
                BoundaryCondition.SecondDerivative, 0.0);
    }

    /**
     * Analytic first partial derivative of the bicubic spline along y at
     * {@code (xq, yq)}.
     * <p>
     * Mirrors C++ v1.42.1 {@code BicubicSplineImpl::derivativeY}
     * (lines 110–121 of {@code bicubicsplineinterpolation.hpp}). Same idea
     * as {@link #derivativeX} but along the y-axis: build a 1D cubic spline
     * by sampling the bicubic value at every y-grid node for the fixed
     * query x, then return the first derivative of that section at yq.
     */
    public double derivativeY(final double xq, final double yq) {
        calculate();
        final CubicInterpolation section = ySectionAt(xq);
        return section.derivative(yq);
    }

    /**
     * Analytic second partial derivative of the bicubic spline along y at
     * {@code (xq, yq)}.
     * <p>
     * Mirrors C++ v1.42.1 {@code BicubicSplineImpl::secondDerivativeY}
     * (lines 123–135 of {@code bicubicsplineinterpolation.hpp}).
     */
    public double derivativeYY(final double xq, final double yq) {
        calculate();
        final CubicInterpolation section = ySectionAt(xq);
        return section.secondDerivative(yq);
    }

    /**
     * Mixed partial derivative {@code d^2/dxdy} of the bicubic spline at
     * {@code (xq, yq)}.
     * <p>
     * Mirrors C++ v1.42.1 {@code BicubicSplineImpl::derivativeXY}
     * (lines 137–149 of {@code bicubicsplineinterpolation.hpp}): build a
     * 1D section in x by evaluating the y-derivative at every x-grid node
     * for the fixed query y, then return the x-derivative of a fresh cubic
     * spline through that section.
     */
    public double derivativeXY(final double xq, final double yq) {
        calculate();
        final int nx = x.size();
        final double[] section = new double[nx];
        for (int i = 0; i < nx; ++i) {
            section[i] = derivativeY(x.get(i), yq);
        }
        final CubicInterpolation sectionInterp = new CubicInterpolation(
                x, new Array(section),
                DerivativeApprox.Spline, false,
                BoundaryCondition.SecondDerivative, 0.0,
                BoundaryCondition.SecondDerivative, 0.0);
        return sectionInterp.derivative(xq);
    }

    /**
     * Build a 1D cubic spline along y by evaluating the 2D bicubic-spline
     * value at every y-grid point with x fixed at {@code xq}. Symmetric
     * counterpart to {@link #xSectionAt(double)}.
     */
    private CubicInterpolation ySectionAt(final double xq) {
        final int ny = y.size();
        final double[] section = new double[ny];
        for (int i = 0; i < ny; ++i) {
            section[i] = interpolation.op(xq, y.get(i));
        }
        return new CubicInterpolation(
                y, new Array(section),
                DerivativeApprox.Spline, false,
                BoundaryCondition.SecondDerivative, 0.0,
                BoundaryCondition.SecondDerivative, 0.0);
    }
}
