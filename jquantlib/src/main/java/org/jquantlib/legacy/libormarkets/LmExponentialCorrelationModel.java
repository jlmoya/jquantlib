/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.legacy.libormarkets;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.ConstantParameter;

/**
 * Exponential correlation model.
 *
 * <p>{@latex[ \rho_{i,j}=e^{-\rho |i-j|} }
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code legacy/libormarketmodels/lmexpcorrmodel.{hpp,cpp}}.
 *
 * <p>References: Brigo, Mercurio, Morini (2003) "Different Covariance
 * Parameterizations of Libor Market Model and Joint Caps/Swaptions Calibration".
 */
public class LmExponentialCorrelationModel extends LmCorrelationModel {

    private final Matrix corrMatrix_;
    private Matrix pseudoSqrt_;

    public LmExponentialCorrelationModel(final int size, final double rho) {
        super(size, 1);
        this.corrMatrix_ = new Matrix(size, size);
        this.pseudoSqrt_ = new Matrix(size, size);
        arguments_.set(0, new ConstantParameter(rho, new PositiveConstraint()));
        generateArguments();
    }

    @Override
    public Matrix correlation(final double t, final Array x) {
        return corrMatrix_.clone();
    }

    @Override
    public double correlation(final int i, final int j, final double t, final Array x) {
        return corrMatrix_.get(i, j);
    }

    @Override
    public boolean isTimeIndependent() {
        return true;
    }

    @Override
    public Matrix pseudoSqrt(final double t, final Array x) {
        return pseudoSqrt_.clone();
    }

    @Override
    protected void generateArguments() {
        final double rho = arguments_.get(0).get(0.0);

        for ( int i = 0; i < size_; ++i ) {
            for ( int j = i; j < size_; ++j ) {
                final double value = Math.exp(-rho * Math.abs(i - j));
                corrMatrix_.set(i, j, value);
                corrMatrix_.set(j, i, value);
            }
        }

        pseudoSqrt_ = PseudoSqrt.pseudoSqrt(corrMatrix_, SalvagingAlgorithm.Spectral);
    }
}
