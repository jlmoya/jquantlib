/*
 Copyright (C) 2026 JQuantLib migration contributors

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
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmBackwardSolver;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.junit.Test;

/**
 * Dispatch-coverage test for {@link FdmBackwardSolver}: verifies that all
 * Phase 5j.5 newly-wired scheme types
 * (CrankNicolson, CraigSneyd, ModifiedCraigSneyd, ExplicitEuler,
 * MethodOfLines, TrBDF2) successfully run a rollback without throwing
 * and produce a finite, non-trivial result.
 *
 * <p>Oracle: 1-D heat equation
 * {@code u_t = u_xx} on {@code [0, pi]} with Dirichlet BCs. The same
 * operator FdmSchemesTest uses; per the QuantLib FdmBackwardSolver
 * convention, the rollback solves {@code u_tau - L u = 0} with
 * {@code tau = T - t}, so the discrete answer at the end of the rollback
 * matches the C++ behaviour validated by {@link FdmSchemesTest}.
 *
 * <p>Each scheme is checked for: (1) no exception, (2) all values finite,
 * (3) some values changed from the seed (the rollback actually ran). Per-
 * scheme convergence-rate validation is in {@link FdmSchemesTest}.
 *
 * @author Phase 5j.5 — FdmBackwardSolver scheme dispatch
 */
public class FdmBackwardSolverDispatchTest {

    /** 1-D heat operator, identical to FdmSchemesTest.HeatOp1D. */
    private static final class HeatOp1D implements FdmLinearOpComposite {
        private final int n;
        private final double invH2;

        HeatOp1D(final int n) {
            this.n = n;
            final double h = Math.PI / (n + 1);
            this.invH2 = 1.0 / (h * h);
        }

        @Override public int size() { return 1; }
        @Override public void setTime(final double t1, final double t2) { }

        @Override
        public Array apply(final Array r) {
            final Array out = new Array(n).fill(0.0);
            for (int i = 0; i < n; i++) {
                final double left  = (i > 0)   ? r.get(i - 1) : 0.0;
                final double right = (i < n-1) ? r.get(i + 1) : 0.0;
                out.set(i, (left - 2.0 * r.get(i) + right) * invH2);
            }
            return out;
        }

        @Override
        public Array applyMixed(final Array r) {
            return new Array(n).fill(0.0);
        }

        @Override
        public Array applyDirection(final int direction, final Array r) {
            return apply(r);
        }

        @Override
        public Array solveSplitting(final int direction, final Array r, final double s) {
            final double sub  = s * invH2;
            final double sup_ = s * invH2;
            final double main_ = 1.0 - 2.0 * s * invH2;
            final double[] c = new double[n];
            final double[] d = new double[n];
            double m = main_;
            c[0] = sup_ / m;
            d[0] = r.get(0) / m;
            for (int i = 1; i < n; i++) {
                m = main_ - sub * c[i - 1];
                c[i] = sup_ / m;
                d[i] = (r.get(i) - sub * d[i - 1]) / m;
            }
            final Array x = new Array(n);
            x.set(n - 1, d[n - 1]);
            for (int i = n - 2; i >= 0; i--) {
                x.set(i, d[i] - c[i] * x.get(i + 1));
            }
            return x;
        }

        @Override
        public Array preconditioner(final Array r, final double dt) {
            return solveSplitting(0, r, dt);
        }

        @Override public Matrix toMatrix() { throw new UnsupportedOperationException(); }
        @Override public List<Matrix> toMatrixDecomp() { throw new UnsupportedOperationException(); }
    }

    private static Array seedAtT(final int n, final double T) {
        final double h = Math.PI / (n + 1);
        final Array u = new Array(n);
        final double decay = Math.exp(-T);
        for (int i = 0; i < n; i++) {
            u.set(i, Math.sin((i + 1) * h) * decay);
        }
        return u;
    }

    private static FdmStepConditionComposite emptyCondition() {
        return new FdmStepConditionComposite(
                new ArrayList<>(),
                new FdmStepConditionComposite.Conditions());
    }

    /** Run a rollback and return the final array (for sanity / correctness checks). */
    private static Array runDispatch(final FdmSchemeDesc schemeDesc,
                                     final int n, final int steps,
                                     final double T,
                                     final int dampingSteps) {
        final HeatOp1D op = new HeatOp1D(n);
        final FdmBackwardSolver solver = new FdmBackwardSolver(
                op, new FdmBoundaryConditionSet(), emptyCondition(), schemeDesc);
        final Array u = seedAtT(n, T);
        solver.rollback(u, T, 0.0, steps, dampingSteps);
        return u;
    }

    private static String checkDispatch(final String name,
                                        final FdmSchemeDesc schemeDesc,
                                        final int n, final int steps,
                                        final double T,
                                        final Array seed) {
        try {
            final Array u = runDispatch(schemeDesc, n, steps, T, 0);
            // Sanity 1: all values finite.
            for (int i = 0; i < u.size(); i++) {
                final double v = u.get(i);
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    return name + ": non-finite value at i=" + i + " (got " + v + ")";
                }
            }
            // Sanity 2: a step actually happened (u differs from seed).
            double diff = 0.0;
            for (int i = 0; i < u.size(); i++) {
                diff += Math.abs(u.get(i) - seed.get(i));
            }
            if (diff < 1e-12) {
                return name + ": rollback produced unchanged values (no step happened)";
            }
            return null;
        } catch (final Exception e) {
            return name + " threw: " + e.getMessage();
        }
    }

    // =========================================================================
    // @Test — collect-all-failures over all 6 newly-wired scheme types.
    // =========================================================================

    @Test
    public void fdmBackwardSolver_dispatchAllSchemes() {
        final List<String> failures = new ArrayList<>();

        final int n = 50;
        final double T = 1.0;
        final Array seed = seedAtT(n, T);

        // 1-arg map (size==1) — both implicit Euler and Crank-Nicolson use
        // the direct tri-diagonal solve; iterative solvers not exercised here.
        // Tests cover dispatch only.
        addCheck(failures, "CrankNicolson",
                FdmSchemeDesc.CrankNicolson(), n, 50, T, seed);
        addCheck(failures, "CraigSneyd",
                FdmSchemeDesc.CraigSneyd(), n, 50, T, seed);
        addCheck(failures, "ModifiedCraigSneyd",
                FdmSchemeDesc.ModifiedCraigSneyd(), n, 50, T, seed);
        // Explicit Euler is conditionally stable; need T small or nSteps large.
        // h = pi/51 ~ 0.0616, h^2/2 ~ 0.0019. With T=0.1, nSteps=100, dt=0.001 < h^2/2.
        addCheck(failures, "ExplicitEuler",
                FdmSchemeDesc.ExplicitEuler(), n, 100, 0.1, seedAtT(n, 0.1));
        addCheck(failures, "MethodOfLines",
                FdmSchemeDesc.MethodOfLines(), n, 20, T, seed);
        addCheck(failures, "TrBDF2",
                FdmSchemeDesc.TrBDF2(), n, 50, T, seed);
        // Pre-Phase-5j.5 schemes — sanity check still alive.
        addCheck(failures, "ImplicitEuler",
                FdmSchemeDesc.ImplicitEuler(), n, 50, T, seed);
        addCheck(failures, "Hundsdorfer",
                FdmSchemeDesc.Hundsdorfer(), n, 50, T, seed);
        addCheck(failures, "Douglas",
                FdmSchemeDesc.Douglas(), n, 50, T, seed);
        addCheck(failures, "ModifiedHundsdorfer",
                FdmSchemeDesc.ModifiedHundsdorfer(), n, 50, T, seed);

        if (!failures.isEmpty()) {
            final StringBuilder sb = new StringBuilder(
                    failures.size() + " dispatch failure(s):\n");
            for (final String f : failures) sb.append("  ").append(f).append('\n');
            fail(sb.toString());
        }
    }

    private static void addCheck(final List<String> failures,
                                 final String name,
                                 final FdmSchemeDesc desc,
                                 final int n, final int steps,
                                 final double T,
                                 final Array seed) {
        final String err = checkDispatch(name, desc, n, steps, T, seed);
        if (err != null) failures.add(err);
    }

    @Test
    public void fdmBackwardSolver_dampingStepsWithCrankNicolson() {
        // dampingSteps>0 with non-implicit-euler main scheme should run the
        // implicit-Euler damping prefix without throwing or producing NaN.
        final int n = 30;
        final double T = 0.5;
        final Array u = runDispatch(FdmSchemeDesc.CrankNicolson(), n, 30, T, 5);
        for (int i = 0; i < u.size(); i++) {
            final double v = u.get(i);
            assertTrue("damping prefix produced non-finite value at i=" + i,
                    !Double.isNaN(v) && !Double.isInfinite(v));
        }
    }
}
