/*
 Copyright (C) 2007 Francois du Vignaud
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.matrixutilities;

import org.jquantlib.math.optimization.CostFunction;

/**
 * Cost function associated with the Frobenius matrix norm.
 *
 * <p>Faithful Java port of {@code QuantLib::FrobeniusCostFunction}
 * (v1.42.1 {@code ql/math/matrixutilities/tapcorrelations.{hpp,cpp}}, pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Given a target matrix and a parameterized matrix-building function
 * {@code f(x, matrixSize, rank)}, the cost is
 * {@code ||f(x)*f(x)^T - target||_F^2} (dot product of the strict lower
 * triangle of the difference). Used by tap-correlation calibration in C++.
 *
 * <p>The C++ companion {@code triangularAnglesParametrization} helpers (which
 * supply the typical {@code f} argument) are not yet ported. This class can
 * still be used with any caller-supplied matrix builder of the same shape.
 *
 * <p>Phase 2 L1-D port.
 */
public class FrobeniusCostFunction extends CostFunction {

    /** {@code f(parameters, matrixSize, rank) -> Matrix}. */
    @FunctionalInterface
    public interface MatrixBuilder {
        Matrix build(Array x, int matrixSize, int rank);
    }

    private final Matrix target_;
    private final MatrixBuilder f_;
    private final int matrixSize_;
    private final int rank_;

    public FrobeniusCostFunction(final Matrix target, final MatrixBuilder f,
            final int matrixSize, final int rank) {
        this.target_ = target;
        this.f_ = f;
        this.matrixSize_ = matrixSize;
        this.rank_ = rank;
    }

    /**
     * Returns {@code DotProduct(values(x), values(x))} matching
     * C++ {@code FrobeniusCostFunction::value}; this overrides the base
     * RMS form used by {@link CostFunction#value(Array)}.
     */
    @Override
    public double value(final Array x) {
        final Array temp = values(x);
        return temp.dotProduct(temp);
    }

    /**
     * Returns the strict lower-triangle differences between
     * {@code f(x)*f(x)^T} and {@code target}.
     */
    @Override
    public Array values(final Array x) {
        final Matrix pseudoRoot = f_.build(x, matrixSize_, rank_);
        final Matrix differences = pseudoRoot.mul(pseudoRoot.transpose()).sub(target_);
        final int rows = target_.rows();
        final Array result = new Array((rows * (target_.cols() - 1)) / 2);
        int k = 0;
        for ( int i = 0; i < rows; i++ ) {
            for ( int j = 0; j < i; j++ ) {
                result.set(k, differences.get(i, j));
                k++;
            }
        }
        return result;
    }
}
