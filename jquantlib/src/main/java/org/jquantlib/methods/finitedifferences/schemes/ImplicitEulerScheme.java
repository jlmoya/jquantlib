/*
 Copyright (C) 2009 Andreas Gaida
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2009, 2017 Klaus Spanderen

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
 * Implicit-Euler time-stepping scheme.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/schemes/impliciteulerscheme.{hpp,cpp}}.
 *
 * <h2>Phase 5j.5 update</h2>
 * The C++ scheme falls back to BiCGStab / GMRES for {@code map.size() != 1}. BiCGStab and GMRES are now available
 * (Phase 2l Track A); this implementation wires both solvers into the multi-dimensional fallback path, mirroring C++
 * {@code ImplicitEulerScheme::step}. The 1-D fast path (used by
 * {@link org.jquantlib.methods.finitedifferences.operators.FdmHullWhiteOp}) remains the direct tri-diagonal solve.
 *
 * @author Phase 2h WI-1 port; Phase 5j.5 multi-d damping path
 */
public class ImplicitEulerScheme {

    protected final FdmLinearOpComposite map;
    protected final BoundaryConditionSchemeHelper bcSet;
    private final double relTol;
    private final SolverType solverType;
    /** Time step (set by {@link #setStep}). NaN until first {@code setStep}. */
    protected double dt;
    private int iterations;
    /** Constructor with empty boundary-condition set + BiCGstab default (mirrors C++ default args). */
    public ImplicitEulerScheme(final FdmLinearOpComposite map) {
        this(map, new FdmBoundaryConditionSet(), 1e-8, SolverType.BiCGstab);
    }

    public ImplicitEulerScheme(final FdmLinearOpComposite map, final FdmBoundaryConditionSet bcSet) {
        this(map, bcSet, 1e-8, SolverType.BiCGstab);
    }

    public ImplicitEulerScheme(final FdmLinearOpComposite map, final FdmBoundaryConditionSet bcSet, final double relTol,
            final SolverType solverType) {
        this.dt = Double.NaN;
        this.map = map;
        this.bcSet = new BoundaryConditionSchemeHelper(bcSet);
        this.relTol = relTol;
        this.solverType = solverType;
        this.iterations = 0;
    }

    /** Set the rollback step size (called by the solver between steps). */
    public void setStep(final double dt) {
        this.dt = dt;
    }

    /** Total iterative-solver iterations consumed (0 for 1-D direct path). */
    public int numberOfIterations() {
        return iterations;
    }

    /** Apply the operator {@code (I - theta * dt * L)} to {@code r}. */
    private Array apply(final Array r, final double theta) {
        return r.sub(map.apply(r).mulAssign(theta * dt));
    }

    /** Default step: theta = 1.0 (full implicit). */
    public void step(final Array a, final double t) {
        step(a, t, 1.0);
    }

    /**
     * Advance {@code a} from time {@code t} to {@code t-dt} in-place with implicitness weight {@code theta}.
     * <p>
     * Mirrors C++ {@code ImplicitEulerScheme::step(a, t, theta)}. For {@code map.size() == 1} uses the direct
     * tri-diagonal solve. For higher-dimensional input dispatches to BiCGStab or GMRES according to
     * {@link SolverType}.
     */
    public void step(final Array a, final double t, final double theta) {
        QL.require(t - dt > -1e-8, "a step towards negative time given");

        final double tPrev = Math.max(0.0, t - dt);
        map.setTime(tPrev, t);
        bcSet.setTime(tPrev);

        bcSet.applyBeforeSolving(map, a);

        if ( map.size() == 1 ) {
            final Array result = map.solveSplitting(0, a, -theta * dt);
            a.fill(result);
        } else if ( solverType == SolverType.BiCGstab ) {
            final BiCGStab.MatrixMult applyOp = r -> apply(r, theta);
            final BiCGStab.MatrixMult precond = r -> map.preconditioner(r, -theta * dt);
            final BiCGStab solver = new BiCGStab(applyOp, Math.max(10, a.size()), relTol, precond);
            final BiCGStab.Result result = solver.solve(a, a);
            iterations += result.iterations;
            a.fill(result.x);
        } else if ( solverType == SolverType.GMRES ) {
            final GMRES.MatrixMult applyOp = r -> apply(r, theta);
            final GMRES.MatrixMult precond = r -> map.preconditioner(r, -theta * dt);
            final GMRES solver = new GMRES(applyOp, Math.max(10, a.size() / 10), relTol, precond);
            final GMRES.Result result = solver.solve(a, a);
            iterations += result.errors.size();
            a.fill(result.x);
        } else {
            throw new LibraryException("ImplicitEulerScheme: unknown solver type " + solverType);
        }

        bcSet.applyAfterSolving(a);
    }

    /** Solver type for the multi-dimensional iterative path. */
    public enum SolverType {BiCGstab, GMRES}
}
