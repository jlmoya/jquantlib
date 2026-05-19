/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.legacy.libormarkets;

import org.jquantlib.math.matrixutilities.Array;

/**
 * Const-wrapper volatility model.
 *
 * <p>Forwards every call to a held {@link LmVolatilityModel}; its own
 * {@code arguments_} list is empty so it does not participate in calibration.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code legacy/libormarketmodels/lmconstwrappervolmodel.hpp}.
 */
public class LmConstWrapperVolatilityModel extends LmVolatilityModel {

    protected final LmVolatilityModel volaModel_;

    public LmConstWrapperVolatilityModel(final LmVolatilityModel volaModel) {
        super(volaModel.size(), 0);
        this.volaModel_ = volaModel;
    }

    @Override
    public Array volatility(final double t, final Array x) {
        return volaModel_.volatility(t, x);
    }

    @Override
    public double volatility(final int i, final double t, final Array x) {
        return volaModel_.volatility(i, t, x);
    }

    @Override
    public double integratedVariance(final int i, final int j, final double u, final Array x) {
        return volaModel_.integratedVariance(i, j, u, x);
    }

    @Override
    protected void generateArguments() {
        // no-op: this wrapper exposes no calibration parameters of its own.
    }
}
