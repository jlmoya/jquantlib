/*
 Copyright (C) 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.legacy.libormarkets;

import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.ConstantParameter;
import org.jquantlib.model.Parameter;

/**
 * Extended linear-exponential volatility model.
 *
 * <p>{@latex[ \sigma_i(t)=k_i\left((a(T_i-t)+d)e^{-b(T_i-t)}+c\right) }
 *
 * <p>Per-tenor calibration weights {@code k_i} are stored as additional
 * {@link Parameter parameters} appended after the four base
 * {@code (a,b,c,d)} parameters.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code legacy/libormarketmodels/lmextlinexpvolmodel.{hpp,cpp}}.
 */
public class LmExtLinearExponentialVolModel extends LmLinearExponentialVolatilityModel {

    public LmExtLinearExponentialVolModel(final List<Double> fixingTimes,
                                          final double a, final double b,
                                          final double c, final double d) {
        super(fixingTimes, a, b, c, d);
        // arguments_ already has 4 entries (a,b,c,d); append size_ unit weights.
        for (int i = 0; i < size_; ++i) {
            arguments_.add(new ConstantParameter(1.0, new PositiveConstraint()));
        }
    }

    @Override
    public Array volatility(final double t, final Array x) {
        final Array tmp = super.volatility(t, x);
        for (int i = 0; i < size_; ++i) {
            tmp.set(i, tmp.get(i) * arguments_.get(i + 4).get(0.0));
        }
        return tmp;
    }

    @Override
    public double volatility(final int i, final double t, final Array x) {
        return arguments_.get(i + 4).get(0.0) * super.volatility(i, t, x);
    }

    @Override
    public double integratedVariance(final int i, final int j, final double u, final Array x) {
        return arguments_.get(i + 4).get(0.0) * arguments_.get(j + 4).get(0.0)
                * super.integratedVariance(i, j, u, x);
    }
}
