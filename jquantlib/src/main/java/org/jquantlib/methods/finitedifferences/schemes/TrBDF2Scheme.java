/*
 Copyright (C) 2018 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.finitedifferences.schemes;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.BiCGStab;
import org.jquantlib.math.matrixutilities.GMRES;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

/**
 * Trapezoidal BDF2 (TR-BDF2) time-stepping scheme.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/schemes/trbdf2scheme.hpp}
 * (header-only template in C++; non-generic Java class parameterised on an
 * explicit trapezoidal {@link CrankNicolsonScheme}).
 * <p>
 * The scheme runs a trapezoidal (Crank-Nicolson) predictor step over
 * {@code alpha * dt}, then a BDF2 corrector step over the remainder
 * {@code (1 - alpha) * dt} to remove the Crank-Nicolson oscillation in the
 * Heston model and similar problems.
 *
 * <h2>Multi-dimensional path</h2>
 * The C++ template uses BiCGstab / GMRES for multi-dimensional operators
 * (map.size() != 1). Both solvers are now available from Phase 2l Track A.
 * The 1D fast path uses the tri-diagonal direct solve; higher dimensions
 * fall back to BiCGstab (default) or GMRES per the {@link SolverType}.
 *
 * @author Phase 2l Track C.6 port
 */
public class TrBDF2Scheme {

    /** Solver type — kept for API parity; only 1D direct solve is active. */
    public enum SolverType { BiCGstab, GMRES }

    /** Time step (NaN until {@link #setStep} is called). */
    protected double dt;

    /** BDF2 corrector weight: {@code beta = (1 - alpha) / (2 - alpha) * dt}. */
    protected double beta;

    private final double alpha;
    private final FdmLinearOpComposite map;
    private final CrankNicolsonScheme trapezoidalScheme;
    private final BoundaryConditionSchemeHelper bcSet;
    private final double relTol;
    private final SolverType solverType;

    private int iterations;

    /** Constructor with empty BC set and BiCGstab solver type (mirrors C++ defaults). */
    public TrBDF2Scheme(final double alpha,
                        final FdmLinearOpComposite map,
                        final CrankNicolsonScheme trapezoidalScheme) {
        this(alpha, map, trapezoidalScheme, new FdmBoundaryConditionSet(), 1e-8, SolverType.BiCGstab);
    }

    public TrBDF2Scheme(final double alpha,
                        final FdmLinearOpComposite map,
                        final CrankNicolsonScheme trapezoidalScheme,
                        final FdmBoundaryConditionSet bcSet) {
        this(alpha, map, trapezoidalScheme, bcSet, 1e-8, SolverType.BiCGstab);
    }

    public TrBDF2Scheme(final double alpha,
                        final FdmLinearOpComposite map,
                        final CrankNicolsonScheme trapezoidalScheme,
                        final FdmBoundaryConditionSet bcSet,
                        final double relTol,
                        final SolverType solverType) {
        this.dt = Double.NaN;
        this.beta = Double.NaN;
        this.alpha = alpha;
        this.map = map;
        this.trapezoidalScheme = trapezoidalScheme;
        this.bcSet = new BoundaryConditionSchemeHelper(bcSet);
        this.relTol = relTol;
        this.solverType = solverType;
        this.iterations = 0;
    }

    /** Set the rollback step size and compute {@code beta}. */
    public void setStep(final double dt) {
        this.dt = dt;
        this.beta = (1.0 - alpha) / (2.0 - alpha) * dt;
    }

    /** Total iterative-solver iterations consumed (0 for 1D direct path). */
    public int numberOfIterations() {
        return iterations;
    }

    /**
     * Apply {@code (I - beta * L)} to {@code r}.
     * Used as the linear operator for the BDF2 corrector solve.
     */
    private Array applyOp(final Array r) {
        return r.sub(map.apply(r).mulAssign(beta));
    }

    /**
     * Advance {@code fn} from time {@code t} to {@code t-dt} in-place.
     * <p>
     * Mirrors C++ {@code TrBDF2Scheme<TrapezoidalScheme>::step}.
     */
    public void step(final Array fn, final double t) {
        QL.require(t - dt > -1e-8, "a step towards negative time given");

        // Predictor: trapezoidal sub-step of size alpha * dt
        final double intermediateTimeStep = dt * alpha;

        final Array fStar = fn.clone();
        trapezoidalScheme.setStep(intermediateTimeStep);
        trapezoidalScheme.step(fStar, t);

        final double tPrev = Math.max(0.0, t - dt);
        bcSet.setTime(tPrev);
        bcSet.applyBeforeSolving(map, fn);

        // BDF2 corrector right-hand side:
        // f = (1/alpha * fStar - (1-alpha)^2/alpha * fn) / (2 - alpha)
        final double oneOverAlpha = 1.0 / alpha;
        final double c1 = oneOverAlpha;
        final double c2 = -(1.0 - alpha) * (1.0 - alpha) * oneOverAlpha;
        final Array f = fStar.mul(c1).addAssign(fn.mul(c2)).mulAssign(1.0 / (2.0 - alpha));

        // Corrector solve: (I - beta * L) * fn_new = f
        if (map.size() == 1) {
            fn.fill(map.solveSplitting(0, f, -beta));
        } else if (solverType == SolverType.BiCGstab) {
            final BiCGStab.MatrixMult applyOp   = r -> applyOp(r);
            final BiCGStab.MatrixMult precond    = r -> map.preconditioner(r, -beta);
            final BiCGStab solver = new BiCGStab(applyOp,
                    Math.max(10, fn.size()), relTol, precond);
            final BiCGStab.Result result = solver.solve(f, f);
            iterations += result.iterations;
            fn.fill(result.x);
        } else if (solverType == SolverType.GMRES) {
            final GMRES.MatrixMult applyOp  = r -> applyOp(r);
            final GMRES.MatrixMult precond  = r -> map.preconditioner(r, -beta);
            final GMRES solver = new GMRES(applyOp,
                    Math.max(10, fn.size() / 10), relTol, precond);
            final GMRES.Result result = solver.solve(f, f);
            iterations += result.errors.size();
            fn.fill(result.x);
        } else {
            throw new LibraryException("TrBDF2Scheme: unknown solver type");
        }

        bcSet.applyAfterSolving(fn);
    }
}
