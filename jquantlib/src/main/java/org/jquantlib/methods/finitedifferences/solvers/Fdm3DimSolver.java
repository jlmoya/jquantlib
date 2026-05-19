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

import org.jquantlib.math.interpolations.BicubicSplineInterpolation;
import org.jquantlib.math.interpolations.MonotonicNaturalCubicInterpolation;
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
 * Lazy 3-D PDE solver with bicubic + monotonic-cubic interpolation.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/solvers/fdm3dimsolver.{hpp,cpp}}.
 * <p>
 * Rolls back the initial condition (= payoff) from maturity to time 0 using {@link FdmBackwardSolver}, then represents
 * the result as a 3D grid of {@link BicubicSplineInterpolation} (over x,y for each z-slice) composed with a
 * {@link MonotonicNaturalCubicInterpolation} over z.
 *
 * @author Phase 2m Track B port
 */
public class Fdm3DimSolver extends LazyObject {

    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;
    private final FdmLinearOpComposite op;

    private final FdmSnapshotCondition thetaCondition;
    private final FdmStepConditionComposite conditions;

    private final double[] x, y, z;
    private final double[] initialValues;

    // Results populated in performCalculations
    private Matrix[] resultValues;   // z.size matrices of shape (y.size, x.size)
    private BicubicSplineInterpolation[] interpolation;

    public Fdm3DimSolver(final FdmSolverDesc solverDesc, final FdmSchemeDesc schemeDesc,
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

        // Build initial values and per-direction coordinate arrays
        this.initialValues = new double[layout.size()];

        // Count unique x, y, z positions
        int xCount = 0, yCount = 0, zCount = 0;
        for ( final FdmLinearOpIterator iter : layout ) {
            initialValues[iter.index()] = solverDesc.calculator.avgInnerValue(iter, solverDesc.maturity);
            if ( iter.coordinates()[1] == 0 && iter.coordinates()[2] == 0 )
                xCount++;
            if ( iter.coordinates()[0] == 0 && iter.coordinates()[2] == 0 )
                yCount++;
            if ( iter.coordinates()[0] == 0 && iter.coordinates()[1] == 0 )
                zCount++;
        }
        this.x = new double[xCount];
        this.y = new double[yCount];
        this.z = new double[zCount];
        int xi = 0, yi = 0, zi = 0;
        for ( final FdmLinearOpIterator iter : layout ) {
            if ( iter.coordinates()[1] == 0 && iter.coordinates()[2] == 0 ) {
                x[xi++] = solverDesc.mesher.location(iter, 0);
            }
            if ( iter.coordinates()[0] == 0 && iter.coordinates()[2] == 0 ) {
                y[yi++] = solverDesc.mesher.location(iter, 1);
            }
            if ( iter.coordinates()[0] == 0 && iter.coordinates()[1] == 0 ) {
                z[zi++] = solverDesc.mesher.location(iter, 2);
            }
        }
    }

    private static Array arrayOf(final double[] d) {
        final Array a = new Array(d.length);
        for ( int i = 0; i < d.length; ++i )
            a.set(i, d[i]);
        return a;
    }

    @Override
    protected void performCalculations() {
        final int xn = x.length, yn = y.length, zn = z.length;
        final Array rhs = new Array(initialValues.length);
        for ( int i = 0; i < initialValues.length; ++i )
            rhs.set(i, initialValues[i]);

        new FdmBackwardSolver(op, solverDesc.bcSet, conditions, schemeDesc).rollback(rhs, solverDesc.maturity, 0.0,
                solverDesc.timeSteps, solverDesc.dampingSteps);

        resultValues = new Matrix[zn];
        interpolation = new BicubicSplineInterpolation[zn];

        final Array xArr = arrayOf(x);
        final Array yArr = arrayOf(y);

        for ( int k = 0; k < zn; ++k ) {
            resultValues[k] = new Matrix(yn, xn);
            // C++: copy rhs[k*yn*xn .. (k+1)*yn*xn) row-major into Matrix(yn,xn)
            for ( int j = 0; j < yn; ++j ) {
                for ( int i = 0; i < xn; ++i ) {
                    resultValues[k].set(j, i, rhs.get(k * yn * xn + j * xn + i));
                }
            }
            interpolation[k] = new BicubicSplineInterpolation(xArr, yArr, resultValues[k]);
        }
    }

    /**
     * Interpolate the solution at (x=log(S), y=v, z=r).
     */
    public double interpolateAt(final double xq, final double yq, final double zq) {
        calculate();
        final Array zVals = new Array(z.length);
        for ( int k = 0; k < z.length; ++k ) {
            zVals.set(k, interpolation[k].op(xq, yq));
        }
        return new MonotonicNaturalCubicInterpolation(arrayOf(z), zVals).op(zq);
    }

    // ---- helpers ----

    /**
     * Theta estimate: time-derivative of option value at (x, y, z). Returns NaN if the snapshot time equals 0.
     */
    public double thetaAt(final double xq, final double yq, final double zq) {
        if ( conditions.stoppingTimes().get(0) == 0.0 ) {
            return Double.NaN;
        }
        calculate();

        final Array rhs = thetaCondition.getValues();
        final int xn = x.length, yn = y.length, zn = z.length;
        final Array xArr = arrayOf(x);
        final Array yArr = arrayOf(y);
        final Array zVals = new Array(zn);

        for ( int k = 0; k < zn; ++k ) {
            final Matrix thetaMat = new Matrix(yn, xn);
            for ( int j = 0; j < yn; ++j ) {
                for ( int i = 0; i < xn; ++i ) {
                    thetaMat.set(j, i, rhs.get(k * yn * xn + j * xn + i));
                }
            }
            zVals.set(k, new BicubicSplineInterpolation(xArr, yArr, thetaMat).op(xq, yq));
        }

        final double thetaVal = new MonotonicNaturalCubicInterpolation(arrayOf(z), zVals).op(zq);
        return (thetaVal - interpolateAt(xq, yq, zq)) / thetaCondition.getTime();
    }
}
