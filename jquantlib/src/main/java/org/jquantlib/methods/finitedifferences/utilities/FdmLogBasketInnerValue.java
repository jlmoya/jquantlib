/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;

/**
 * Inner-value calculator for multi-asset log-space (ln S_i) grids.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/utilities/fdminnervaluecalculator.{hpp,cpp}} — specifically
 * {@code FdmLogBasketInnerValue}, which evaluates a {@link BasketPayoff} at the mesh cell whose log-coordinates are
 * {@code (x_0, ..., x_{n-1})} by exponentiating each direction and forwarding the asset vector to
 * {@link BasketPayoff#get(double[])}.
 * <p>
 * Cell-averaging is not performed here ({@link #avgInnerValue} delegates to {@link #innerValue}) — this matches the C++
 * implementation, which simply returns {@code innerValue} for the basket case.
 *
 * @author Phase 5e.5b-CFC-d port
 */
public class FdmLogBasketInnerValue implements FdmInnerValueCalculator {

    private final BasketPayoff payoff;
    private final FdmMesher mesher;

    /**
     * @param payoff basket payoff (Min / Max / Average / Spread)
     * @param mesher multi-dimensional FDM mesh; each direction is in log-space ({@code x_i = ln S_i})
     */
    public FdmLogBasketInnerValue(final BasketPayoff payoff, final FdmMesher mesher) {
        this.payoff = payoff;
        this.mesher = mesher;
    }

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        final int n = mesher.layout().dim().length;
        final double[] x = new double[n];
        for ( int i = 0; i < n; ++i ) {
            x[i] = JQuantMath.exp(mesher.location(iter, i));
        }
        return payoff.get(x);
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        return innerValue(iter, t);
    }
}
