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
 * equivalent of C++ {@code BicubicSpline}. Note that
 * {@code BicubicSplineInterpolation} only exposes value queries — first
 * and second partial derivatives via the underlying {@code op(x, y)} would
 * require an interpolation-engine extension. The C++ side exposes
 * {@code derivativeX/Y/XX/YY/XY}; those are accessible to engines that
 * actually use them. None of the Phase 2h WI-2 / WI-3 engines
 * ({@code FdHullWhiteSwaptionEngine}, {@code FdG2SwaptionEngine}) read
 * those derivatives — they only call {@link #interpolateAt}, so the port
 * leaves the derivative accessors out for now and surfaces them as a
 * follow-up if a future engine needs them.
 *
 * @author Phase 2h WI-1 port
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
}
