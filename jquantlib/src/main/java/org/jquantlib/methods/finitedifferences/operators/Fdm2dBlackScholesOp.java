/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.methods.finitedifferences.operators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Two-dimensional finite-difference operator for the Black-Scholes basket /
 * spread PDE in log-space.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/operators/fdm2dblackscholesop.{hpp,cpp}}.
 * <p>
 * The PDE for {@code V(S1, S2, t)} reads (in log-coordinates
 * {@code x = ln S1, y = ln S2}):
 * <pre>
 *   dV/dt + L_x[V] + L_y[V] + rho * sigma1 * sigma2 * d^2V/dxdy + r*V_{forward} = 0
 * </pre>
 * where {@code L_x, L_y} are the per-asset 1-D Black-Scholes operators
 * delegated to two {@link FdmBlackScholesOp} instances (directions 0 and 1).
 * The cross / correlation term plus the forward-rate "carry" piece form the
 * mixed-direction operator returned by {@link #applyMixed}.
 *
 * <h3>Deviations from C++ (deferred to a future phase)</h3>
 * <ul>
 *   <li>Local-vol mode is not supported (the constructor accepts the flag
 *       for API symmetry, but only {@code localVol = false} is implemented).
 *       C++ uses {@code LocalVolTermStructure::localVol} per-cell to drive
 *       the correlation map; the Java port computes a single forward
 *       volatility per step instead.</li>
 *   <li>Quanto helper not supported.</li>
 * </ul>
 *
 * @author Phase 5e.5b-CFC-d port
 */
public class Fdm2dBlackScholesOp implements FdmLinearOpComposite {

    private final FdmMesher mesher;
    private final GeneralizedBlackScholesProcess p1;
    private final GeneralizedBlackScholesProcess p2;
    private final NinePointLinearOp corrMapTemplate;
    private final FdmBlackScholesOp opX;
    private final FdmBlackScholesOp opY;

    private NinePointLinearOp corrMapT;
    private double currentForwardRate;

    /**
     * @param mesher      2-D log-space mesh (direction 0 = ln S1, 1 = ln S2)
     * @param p1          GBS process for asset 1
     * @param p2          GBS process for asset 2
     * @param correlation correlation between {@code S1} and {@code S2}
     * @param maturity    option maturity (years) — accepted for C++ API
     *                    symmetry; unused in the non-localVol path
     */
    public Fdm2dBlackScholesOp(final FdmMesher mesher,
                               final GeneralizedBlackScholesProcess p1,
                               final GeneralizedBlackScholesProcess p2,
                               final double correlation,
                               final double maturity) {
        this(mesher, p1, p2, correlation, maturity, false, Double.NaN);
    }

    /**
     * Full constructor mirroring C++ v1.42.1.
     *
     * @param mesher                    2-D log-space mesh
     * @param p1                        GBS process for asset 1
     * @param p2                        GBS process for asset 2
     * @param correlation               asset correlation
     * @param maturity                  option maturity (years)
     * @param localVol                  must be {@code false}; local-vol mode
     *                                  is not yet ported
     * @param illegalLocalVolOverwrite  unused (placeholder for local-vol mode)
     */
    public Fdm2dBlackScholesOp(final FdmMesher mesher,
                               final GeneralizedBlackScholesProcess p1,
                               final GeneralizedBlackScholesProcess p2,
                               final double correlation,
                               final double maturity,
                               final boolean localVol,
                               final double illegalLocalVolOverwrite) {
        if (localVol) {
            throw new UnsupportedOperationException(
                    "Fdm2dBlackScholesOp: localVol path not yet ported");
        }
        this.mesher = mesher;
        this.p1 = p1;
        this.p2 = p2;

        // Per-asset 1D Black-Scholes operators. Strike defaults to the spot
        // (C++: opX_(mesher, p1, p1->x0(), ...) and opY_(mesher, p2, p2->x0(), 1)).
        this.opX = new FdmBlackScholesOp(mesher, p1, p1.x0(), 0);
        this.opY = new FdmBlackScholesOp(mesher, p2, p2.x0(), 1);

        // corrMapTemplate_ = SecondOrderMixedDerivativeOp(0,1,mesher)
        //                    .mult(Array(layout.size(), correlation))
        final int n = mesher.layout().size();
        this.corrMapTemplate = new SecondOrderMixedDerivativeOp(0, 1, mesher)
                .mult(new Array(n).fill(correlation));

        // Initial corrMapT — overwritten in setTime; safe default is a copy
        // of the template (zero volatility scaling).
        this.corrMapT = corrMapTemplate.mult(new Array(n).fill(0.0));
        this.currentForwardRate = 0.0;
    }

    // ------------------------------------------------------------------
    // FdmLinearOpComposite contract
    // ------------------------------------------------------------------

    @Override
    public int size() {
        return 2;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        opX.setTime(t1, t2);
        opY.setTime(t1, t2);

        // Non-localVol branch: scale the correlation template by the product
        // of forward Black volatilities sampled at the per-asset spot strike.
        final double vol1 = p1.blackVolatility().currentLink()
                .blackForwardVol(t1, t2, p1.x0(), true);
        final double vol2 = p2.blackVolatility().currentLink()
                .blackForwardVol(t1, t2, p2.x0(), true);

        final int n = mesher.layout().size();
        corrMapT = corrMapTemplate.mult(new Array(n).fill(vol1 * vol2));

        currentForwardRate = p1.riskFreeRate().currentLink()
                .forwardRate(t1, t2,
                        Compounding.Continuous, Frequency.NoFrequency, true)
                .rate();
    }

    @Override
    public Array apply(final Array x) {
        return opX.apply(x).add(opY.apply(x)).add(applyMixed(x));
    }

    @Override
    public Array applyMixed(final Array x) {
        return corrMapT.apply(x).add(x.mul(currentForwardRate));
    }

    @Override
    public Array applyDirection(final int direction, final Array x) {
        if (direction == 0) {
            return opX.apply(x);
        } else if (direction == 1) {
            return opY.apply(x);
        }
        throw new IllegalArgumentException("direction is too large");
    }

    @Override
    public Array solveSplitting(final int direction, final Array x, final double s) {
        if (direction == 0) {
            return opX.solveSplitting(direction, x, s);
        } else if (direction == 1) {
            return opY.solveSplitting(direction, x, s);
        }
        throw new IllegalArgumentException("direction is too large");
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(0, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        throw new UnsupportedOperationException(
                "Fdm2dBlackScholesOp.toMatrix() not implemented; "
                        + "use toMatrixDecomp()");
    }

    @Override
    public List<Matrix> toMatrixDecomp() {
        final List<Matrix> ret = new ArrayList<>(3);
        ret.add(opX.toMatrix());
        ret.add(opY.toMatrix());
        // Mixed part: correlation cross-derivative + forward-rate * I.
        // Build I*r explicitly (Matrix has no scalar diagonal helper).
        final int n = mesher.layout().size();
        final Matrix mixed = corrMapT.toMatrix();
        for (int i = 0; i < n; ++i) {
            mixed.set(i, i, mixed.get(i, i) + currentForwardRate);
        }
        ret.add(mixed);
        return Collections.unmodifiableList(ret);
    }
}
