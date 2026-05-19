/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.legacy.libormarkets;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Const-wrapper correlation model.
 *
 * <p>Forwards every call to a held {@link LmCorrelationModel}; its own
 * {@code arguments_} list is empty so it does not participate in calibration.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code legacy/libormarketmodels/lmconstwrappercorrmodel.hpp}.
 */
public class LmConstWrapperCorrelationModel extends LmCorrelationModel {

    protected final LmCorrelationModel corrModel_;

    public LmConstWrapperCorrelationModel(final LmCorrelationModel corrModel) {
        super(corrModel.size(), 0);
        this.corrModel_ = corrModel;
    }

    @Override
    public int factors() {
        return corrModel_.factors();
    }

    @Override
    public Matrix correlation(final double t, final Array x) {
        return corrModel_.correlation(t, x);
    }

    @Override
    public Matrix pseudoSqrt(final double t, final Array x) {
        return corrModel_.pseudoSqrt(t, x);
    }

    @Override
    public double correlation(final int i, final int j, final double t, final Array x) {
        return corrModel_.correlation(i, j, t, x);
    }

    @Override
    public boolean isTimeIndependent() {
        return corrModel_.isTimeIndependent();
    }

    @Override
    protected void generateArguments() {
        // no-op: this wrapper exposes no calibration parameters of its own.
    }
}
