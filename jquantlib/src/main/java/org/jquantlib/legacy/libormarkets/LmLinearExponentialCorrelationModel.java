/*
 Copyright (C) 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2009 Ueli Hofstetter (Java port skeleton).
 Copyright (C) 2026 JQuantLib migration contributors (Phase 5e.5b-CFC-d-132 fix).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.legacy.libormarkets;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.math.optimization.BoundaryConstraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.ConstantParameter;

/**
 * Linear-exponential correlation model.
 *
 * <p>{@latex[ \rho_{i,j}=\rho + (1-\rho)e^{-\beta |i-j|} }
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code legacy/libormarketmodels/lmlinexpcorrmodel.{hpp,cpp}}.
 */
public class LmLinearExponentialCorrelationModel extends LmCorrelationModel {

    private Matrix corrMatrix_;
    private Matrix pseudoSqrt_;
    private final int factors_;

    public LmLinearExponentialCorrelationModel(final int size, final double rho,
                                               final double beta, final int factors) {
        super(size, 2);
        this.corrMatrix_ = new Matrix(size, size);
        this.factors_ = (factors > 0) ? factors : size;
        arguments_.set(0, new ConstantParameter(rho, new BoundaryConstraint(-1.0, 1.0)));
        arguments_.set(1, new ConstantParameter(beta, new PositiveConstraint()));
        generateArguments();
    }

    public LmLinearExponentialCorrelationModel(final int size, final double rho, final double beta) {
        this(size, rho, beta, 0);
    }

    @Override
    public Matrix correlation(final double time, final Array x) {
        return corrMatrix_.clone();
    }

    @Override
    public double correlation(final int i, final int j, final double time, final Array x) {
        return corrMatrix_.get(i, j);
    }

    @Override
    public boolean isTimeIndependent() {
        return true;
    }

    @Override
    public int factors() {
        return factors_;
    }

    @Override
    public Matrix pseudoSqrt(final double time, final Array x) {
        return pseudoSqrt_.clone();
    }

    @Override
    protected void generateArguments() {
        final double rho = arguments_.get(0).get(0.0);
        final double beta = arguments_.get(1).get(0.0);

        for (int i = 0; i < size_; ++i) {
            for (int j = i; j < size_; ++j) {
                final double value = rho + (1 - rho) * Math.exp(-beta * Math.abs(i - j));
                corrMatrix_.set(i, j, value);
                corrMatrix_.set(j, i, value);
            }
        }

        pseudoSqrt_ = PseudoSqrt.rankReducedSqrt(corrMatrix_, factors_, 1, SalvagingAlgorithm.None);
        // Note: in C++ this is `pseudoSqrt_ * transpose(pseudoSqrt_)`; preserving
        // that semantics here so a rank-reduced sqrt yields a consistent matrix.
        corrMatrix_ = pseudoSqrt_.mul(pseudoSqrt_.transpose());
    }
}
