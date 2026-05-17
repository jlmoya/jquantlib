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
 Copyright (C) 2011 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.math.integrals.GaussLaguerreIntegration;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FirstDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.TripleBandLinearOp;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Finite-difference operator for the Kluge OU + exp-jumps model
 * <p>
 * {@code dX_t = a*(b(t) - X_t) dt + sigma dW_t};
 * <p>
 * {@code dY_t = -beta * Y_t dt + dJ_t} where {@code J} is a compound Poisson
 * process with rate {@code lambda} and exponentially distributed jump sizes
 * with rate {@code eta}.
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdmextoujumpop.{hpp,cpp}}.
 *
 * <p>The operator splits into three contributions:</p>
 * <ul>
 *   <li>direction 0: the {@link FdmExtendedOrnsteinUhlenbeckOp} on {@code X};</li>
 *   <li>direction 1: the upwind first-derivative {@code -beta*y * d/dy} on {@code Y};</li>
 *   <li>cross / mixed: the integral operator describing the jump component, mapping
 *       {@code y -> y + xi/eta} weighted by a Gauss–Laguerre quadrature
 *       (the standard transform for the {@code exp(-x)}-weighted integral
 *       of the exponential-jump density).</li>
 * </ul>
 *
 * @author Phase 5e.5b-CFC-d-171 port
 */
public class FdmExtOUJumpOp implements FdmLinearOpComposite {

    private final FdmMesher mesher_;
    private final ExtOUWithJumpsProcess process_;
    @SuppressWarnings("unused")
    private final YieldTermStructure rTS_;
    private final FdmBoundaryConditionSet bcSet_;
    private final GaussLaguerreIntegration gaussLaguerreIntegration_;

    @SuppressWarnings("unused")
    private final Array x_;
    private final FdmExtendedOrnsteinUhlenbeckOp ouOp_;

    private final TripleBandLinearOp dyMap_;

    private final SparseMatrix integroPart_;

    public FdmExtOUJumpOp(final FdmMesher mesher,
                          final ExtOUWithJumpsProcess process,
                          final YieldTermStructure rTS,
                          final FdmBoundaryConditionSet bcSet,
                          final int integroIntegrationOrder) {
        this.mesher_ = mesher;
        this.process_ = process;
        this.rTS_ = rTS;
        this.bcSet_ = bcSet;
        this.gaussLaguerreIntegration_ =
                new GaussLaguerreIntegration(integroIntegrationOrder);
        this.x_ = mesher.locations(0);
        this.ouOp_ = new FdmExtendedOrnsteinUhlenbeckOp(
                mesher,
                process.getExtendedOrnsteinUhlenbeckProcess(),
                rTS, bcSet, 0);

        // dyMap = FirstDerivativeOp(1, mesher).mult(-beta * locations(1))
        final FirstDerivativeOp dy = new FirstDerivativeOp(1, mesher);
        final Array minusBetaY = mesher.locations(1).mul(-process.beta());
        this.dyMap_ = dy.mult(minusBetaY);

        // Build integro part (sparse) using Gauss-Laguerre transform of the
        // exponential-jump density.
        final double eta = process.eta();
        final double lambda = process.jumpIntensity();
        final int order = gaussLaguerreIntegration_.order();

        final double[] yInt = new double[order];
        final double[] weights = new double[order];
        for (int i = 0; i < order; ++i) {
            yInt[i] = gaussLaguerreIntegration_.x(i);
            weights[i] = gaussLaguerreIntegration_.weight(i);
        }

        final int n = mesher_.layout().size();
        final SparseMatrix integro = new SparseMatrix(n, n);

        // yLoc[k] = mesher.location at coord-1 == k (any pick along direction 0
        // gives the same y-value because mesher is a tensor product).
        final int dim1 = mesher_.layout().dim()[1];
        final double[] yLoc = new double[dim1];
        for (final FdmLinearOpIterator iter : mesher_.layout()) {
            yLoc[iter.coordinates()[1]] = mesher_.location(iter, 1);
        }

        for (final FdmLinearOpIterator iter : mesher_.layout()) {
            final int diag = iter.index();
            // -lambda on diagonal (compound-Poisson "loss" term).
            integro.addAt(diag, diag, -lambda);

            final double y = mesher_.location(iter, 1);
            final int yIndex = iter.coordinates()[1];

            for (int i = 0; i < order; ++i) {
                final double weight = Math.exp(-yInt[i]) * weights[i];
                final double ys = y + yInt[i] / eta;

                int l;
                if (ys > yLoc[yLoc.length - 1]) {
                    l = yLoc.length - 2;
                } else {
                    // C++: upper_bound(yLoc.begin(), yLoc.end()-1, ys) - yLoc.begin() - 1
                    // i.e. largest l such that yLoc[l] <= ys, capped at yLoc.length-2.
                    int upper = upperBound(yLoc, yLoc.length - 1, ys);
                    l = upper - 1;
                }

                final double denom = yLoc[l + 1] - yLoc[l];
                final double s = (ys - yLoc[l]) / denom;

                integro.addAt(diag,
                        mesher_.layout().neighbourhood(iter, 1, l - yIndex),
                        weight * lambda * (1.0 - s));
                integro.addAt(diag,
                        mesher_.layout().neighbourhood(iter, 1, l + 1 - yIndex),
                        weight * lambda * s);
            }
        }
        this.integroPart_ = integro;
    }

    @Override
    public int size() {
        return mesher_.layout().dim().length;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        ouOp_.setTime(t1, t2);
    }

    @Override
    public Array apply(final Array r) {
        return ouOp_.apply(r).add(dyMap_.apply(r)).add(integro(r));
    }

    @Override
    public Array applyMixed(final Array r) {
        return integro(r);
    }

    @Override
    public Array applyDirection(final int direction, final Array r) {
        if (direction == 0) {
            return ouOp_.applyDirection(direction, r);
        } else if (direction == 1) {
            return dyMap_.apply(r);
        } else {
            return new Array(r.size()).fill(0.0);
        }
    }

    @Override
    public Array solveSplitting(final int direction, final Array r, final double a) {
        if (direction == 0) {
            return ouOp_.solveSplitting(direction, r, a);
        } else if (direction == 1) {
            return dyMap_.solveSplitting(r, a, 1.0);
        } else {
            return r.clone();
        }
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return ouOp_.solveSplitting(0, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        // Sum the three contributions (ou, dy, integro) as dense matrices.
        final Matrix ou = ouOp_.toMatrix();
        final Matrix dy = dyMap_.toMatrix();
        final int rows = ou.rows();
        final int cols = ou.cols();
        final Matrix out = new Matrix(rows, cols);
        for (int i = 0; i < rows; ++i) {
            for (int j = 0; j < cols; ++j) {
                out.set(i, j, ou.get(i, j) + dy.get(i, j) + integroPart_.get(i, j));
            }
        }
        return out;
    }

    @Override
    public List<Matrix> toMatrixDecomp() {
        QL.require(bcSet_ == null || bcSet_.size() == 0,
                "boundary conditions are not supported");

        final List<Matrix> ret = new ArrayList<Matrix>(3);
        ret.add(ouOp_.toMatrixDecomp().get(0));
        ret.add(dyMap_.toMatrix());
        // Convert sparse integro to dense to satisfy the dense-decomp contract.
        final int n = integroPart_.rows();
        final Matrix integroDense = new Matrix(n, n);
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < n; ++j) {
                final double v = integroPart_.get(i, j);
                if (v != 0.0) {
                    integroDense.set(i, j, v);
                }
            }
        }
        ret.add(integroDense);
        return Collections.unmodifiableList(ret);
    }

    private Array integro(final Array r) {
        return integroPart_.mul(r);
    }

    /**
     * Equivalent of {@code std::upper_bound(a, a+hi, v)}: returns the
     * smallest index {@code i in [0, hi]} such that {@code a[i] > v}, or
     * {@code hi} if no such index exists. Mirrors the C++ behaviour used
     * for the jump-density interpolation.
     */
    private static int upperBound(final double[] a, final int hi, final double v) {
        int lo = 0;
        int high = hi;
        while (lo < high) {
            final int mid = (lo + high) >>> 1;
            if (a[mid] <= v) {
                lo = mid + 1;
            } else {
                high = mid;
            }
        }
        return lo;
    }
}
