/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.methods.finitedifferences.solvers;

import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.operators.Fdm2dBlackScholesOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.util.LazyObject;

/**
 * Lazy 2-D FDM solver for the two-asset Black-Scholes PDE in log-space.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/solvers/fdm2dblackscholessolver.{hpp,cpp}}.
 * <p>
 * Wires {@link Fdm2dBlackScholesOp} into a {@link Fdm2DimSolver}. The solver
 * interpolates in {@code (ln S1, ln S2)} space; the public API
 * ({@link #valueAt}, {@link #deltaXat}, {@link #deltaYat}, {@link #gammaXat},
 * {@link #gammaYat}, {@link #gammaXYat}, {@link #thetaAt}) accepts the raw
 * spot pair {@code (u, v)} and converts to {@code (ln u, ln v)} before
 * interpolation, matching C++ {@code std::log(u)} / {@code std::log(v)}.
 *
 * <h3>Deviations from C++</h3>
 * <ul>
 *   <li>Local-vol mode is not yet wired through
 *       {@link Fdm2dBlackScholesOp}; only {@code localVol = false} is
 *       implemented (a {@code true} flag triggers an
 *       {@link UnsupportedOperationException} in the operator).</li>
 * </ul>
 *
 * @author Phase 5e.5b-CFC-d port
 */
public class Fdm2dBlackScholesSolver extends LazyObject {

    private final GeneralizedBlackScholesProcess p1;
    private final GeneralizedBlackScholesProcess p2;
    private final double correlation;
    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;
    private final boolean localVol;
    private final double illegalLocalVolOverwrite;

    private Fdm2DimSolver solver;

    public Fdm2dBlackScholesSolver(final GeneralizedBlackScholesProcess p1,
                                   final GeneralizedBlackScholesProcess p2,
                                   final double correlation,
                                   final FdmSolverDesc solverDesc,
                                   final FdmSchemeDesc schemeDesc) {
        this(p1, p2, correlation, solverDesc, schemeDesc, false, Double.NaN);
    }

    public Fdm2dBlackScholesSolver(final GeneralizedBlackScholesProcess p1,
                                   final GeneralizedBlackScholesProcess p2,
                                   final double correlation,
                                   final FdmSolverDesc solverDesc,
                                   final FdmSchemeDesc schemeDesc,
                                   final boolean localVol,
                                   final double illegalLocalVolOverwrite) {
        this.p1 = p1;
        this.p2 = p2;
        this.correlation = correlation;
        this.solverDesc = solverDesc;
        this.schemeDesc = schemeDesc;
        this.localVol = localVol;
        this.illegalLocalVolOverwrite = illegalLocalVolOverwrite;

        p1.addObserver(this);
        p2.addObserver(this);
    }

    @Override
    protected void performCalculations() {
        final Fdm2dBlackScholesOp op = new Fdm2dBlackScholesOp(
                solverDesc.mesher, p1, p2, correlation,
                solverDesc.maturity, localVol, illegalLocalVolOverwrite);
        solver = new Fdm2DimSolver(solverDesc, schemeDesc, op);
    }

    /** Option NPV at spot pair {@code (u, v)}. */
    public double valueAt(final double u, final double v) {
        calculate();
        return solver.interpolateAt(JQuantMath.log(u), JQuantMath.log(v));
    }

    /** Theta (dV/dt per year, finite-difference estimate) at {@code (u, v)}. */
    public double thetaAt(final double u, final double v) {
        calculate();
        return solver.thetaAt(JQuantMath.log(u), JQuantMath.log(v));
    }

    /** Delta dV/dS1 at {@code (u, v)}. */
    public double deltaXat(final double u, final double v) {
        calculate();
        return solver.derivativeX(JQuantMath.log(u), JQuantMath.log(v)) / u;
    }

    /** Delta dV/dS2 at {@code (u, v)}. */
    public double deltaYat(final double u, final double v) {
        calculate();
        return solver.derivativeY(JQuantMath.log(u), JQuantMath.log(v)) / v;
    }

    /** Gamma d^2V/dS1^2 at {@code (u, v)}. */
    public double gammaXat(final double u, final double v) {
        calculate();
        final double x = JQuantMath.log(u);
        final double y = JQuantMath.log(v);
        return (solver.derivativeXX(x, y) - solver.derivativeX(x, y))
                / (u * u);
    }

    /** Gamma d^2V/dS2^2 at {@code (u, v)}. */
    public double gammaYat(final double u, final double v) {
        calculate();
        final double x = JQuantMath.log(u);
        final double y = JQuantMath.log(v);
        return (solver.derivativeYY(x, y) - solver.derivativeY(x, y))
                / (v * v);
    }

    /** Cross-gamma d^2V/dS1dS2 at {@code (u, v)}. */
    public double gammaXYat(final double u, final double v) {
        calculate();
        final double x = JQuantMath.log(u);
        final double y = JQuantMath.log(v);
        return solver.derivativeXY(x, y) / (u * v);
    }
}
