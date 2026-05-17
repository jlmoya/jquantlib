/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.finitedifferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.finitedifferences.FdmExtOUJumpOp;
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.ExponentialJump1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5j skeleton port of {@code test-suite/vpp.cpp} v1.42.1.
 *
 * <p><strong>Phase 5e.5b-CFC-d-171 update:</strong>
 * {@link org.jquantlib.experimental.finitedifferences.FdmExtendedOrnsteinUhlenbeckOp},
 * {@link org.jquantlib.experimental.finitedifferences.FdmExtOUJumpOp},
 * {@link org.jquantlib.experimental.finitedifferences.FdmExtOUJumpSolver}, and
 * {@link org.jquantlib.experimental.finitedifferences.FdSimpleExtOUStorageEngine}
 * are now ported (the storage engine as a skeleton until
 * {@code VanillaStorageOption} + {@code FdmSimpleStorageCondition} +
 * {@code FdmSimple2dExtOUSolver} land). A new
 * {@link #testFdmExtOUJumpOpSmoke()} sanity-checks the new operator
 * end-to-end on a small 2D mesh.
 *
 * <p><strong>Phase 5e.5b-CFC-d-164:</strong>
 * {@link org.jquantlib.experimental.finitedifferences.VanillaVPPOption},
 * {@link org.jquantlib.experimental.finitedifferences.FdKlugeExtOUSpreadEngine},
 * {@link org.jquantlib.experimental.finitedifferences.FdmSpreadPayoffInnerValue},
 * and {@link org.jquantlib.instruments.SwingExercise} are ported.
 * The remaining blockers are listed per-test below.
 *
 * <p>Java has the {@code KlugeExtOUProcess}, {@code GemanRoncoroniProcess},
 * and {@code ExtOUWithJumpsProcess} classes (in
 * {@code org.jquantlib.experimental.processes}) but the energy-option
 * engines (FdSimpleKlugeExtOUVPPEngine, DynProgVPPIntrinsicValueEngine)
 * and the supporting FD operators (FdmKlugeExtOUOp, FdmNdimSolver,
 * FdmVPPStepConditionFactory) are NOT yet ported.
 *
 * <p>The {@code testKlugeExtOUMatrixDecomposition} case additionally
 * requires the MC-based VPP intrinsic-value calculator which depends on a
 * Bermudan Longstaff-Schwartz infrastructure not yet exercised for
 * commodity processes.
 *
 * <p>Source: {@code test-suite/vpp.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class VppTest {

    private static final String REASON_STORAGE_ENGINE =
            "Phase 5j.5 — FdSimpleExtOUStorageEngine is ported as a skeleton "
          + "(Phase 5e.5b-CFC-d-171); the full pricing path still requires "
          + "VanillaStorageOption + FdmSimpleStorageCondition + FdmSimple2dExtOUSolver";

    private static final String REASON_VPP_ENGINE =
            "Phase 5j.5 — requires FdSimpleKlugeExtOUVPPEngine "
          + "(full VPP pricing path; FdmKlugeExtOUOp + FdmVPPStepCondition* "
          + "not yet ported)";

    private static final String REASON_DP_ENGINE =
            "Phase 5j.5 — VanillaVPPOption + SwingExercise are ported "
          + "(Phase 5e.5b-CFC-d-164); still requires DynProgVPPIntrinsicValueEngine "
          + "(needs FdmVPPStepCondition + FdmVPPStepConditionFactory)";

    private static final String REASON_SPREAD_ENGINE =
            "Phase 5j.5 — FdKlugeExtOUSpreadEngine + FdmSpreadPayoffInnerValue are "
          + "ported as a skeleton (Phase 5e.5b-CFC-d-164); the calculate() body "
          + "still needs FdmKlugeExtOUSolver<3> (which needs FdmNdimSolver "
          + "+ FdmKlugeExtOUOp)";

    private static final String REASON_PROCESS =
            "Phase 5j.5 — requires GemanRoncoroniProcess MC harness wiring "
          + "(process exists; calibration loop not yet exercised)";

    @Ignore(REASON_PROCESS)
    @Test
    public void testGemanRoncoroniProcess() { fail("not implemented"); }

    @Ignore(REASON_STORAGE_ENGINE)
    @Test
    public void testSimpleExtOUStorageEngine() { fail("not implemented"); }

    @Ignore(REASON_SPREAD_ENGINE)
    @Test
    public void testKlugeExtOUSpreadOption() { fail("not implemented"); }

    @Ignore(REASON_DP_ENGINE)
    @Test
    public void testVPPIntrinsicValue() { fail("not implemented"); }

    @Ignore(REASON_VPP_ENGINE)
    @Test
    public void testVPPPricing() { fail("not implemented"); }

    @Ignore("Phase 5j.5 — requires Kluge ExtOU matrix decomposition utility "
          + "(used by Bermudan VPP MC engine)")
    @Test
    public void testKlugeExtOUMatrixDecomposition() { fail("not implemented"); }

    /**
     * Phase 5e.5b-CFC-d-171: smoke test for the freshly-ported
     * {@link org.jquantlib.experimental.finitedifferences.FdmExtOUJumpOp}.
     *
     * <p>Builds the operator on a small 2D mesh ({@code X} log-spot,
     * {@code Y} jump component), calls {@code setTime}, and verifies the
     * three per-direction contributions agree with {@code apply()}:
     * {@code apply(r) == applyDirection(0, r) + applyDirection(1, r) +
     *  applyMixed(r)}.</p>
     *
     * <p>This guards against regressions in the operator's wiring, including
     * the OU drift+diffusion (direction 0), the upwind {@code -beta*y * d/dy}
     * drift (direction 1), and the Gauss–Laguerre integral approximation of
     * the jump density (mixed). The check uses a tight tolerance of
     * {@code 1e-12} because all three contributions are direct matrix-vector
     * products with no accumulation beyond a few hundred FLOPs.</p>
     */
    @Test
    public void testFdmExtOUJumpOpSmoke() {
        // Small grid — 9 x 7 = 63 cells, fast to build.
        final int xGrid = 9;
        final int yGrid = 7;

        // Driving processes — mild parameters.
        final double speed = 1.0;
        final double vol = 0.4;
        final double x0 = 3.0;
        final ExtendedOrnsteinUhlenbeckProcess ouProcess =
                new ExtendedOrnsteinUhlenbeckProcess(speed, vol, x0,
                        new Ops.DoubleOp() {
                            @Override public double op(final double t) { return x0; }
                        });

        final double beta = 5.0;
        final double jumpIntensity = 1.0;
        final double eta = 1.0 / 0.4;
        final ExtOUWithJumpsProcess process =
                new ExtOUWithJumpsProcess(ouProcess, 0.0, beta, jumpIntensity, eta);

        // Mesh: simple uniform X mesh, exponential-jump Y mesh.
        final Fdm1dMesher xMesher = new Uniform1dMesher(0.5, 5.5, xGrid);
        final Fdm1dMesher yMesher =
                new ExponentialJump1dMesher(yGrid, beta, jumpIntensity, eta);
        final FdmMesher mesher = new FdmMesherComposite(xMesher, yMesher);

        // Flat-forward risk-free curve so setTime() has a non-trivial r.
        final Date refDate = new Date(18, Month.December, 2011);
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final YieldTermStructure rTS = new FlatForward(refDate, 0.1, dc);

        final FdmExtOUJumpOp op = new FdmExtOUJumpOp(
                mesher, process, rTS, new FdmBoundaryConditionSet(), 16);
        op.setTime(0.1, 0.2);

        // Build a deterministic input vector (size == layout.size()).
        final int n = mesher.layout().size();
        assertEquals(xGrid * yGrid, n);
        final Array r = new Array(n);
        for (int i = 0; i < n; ++i) {
            r.set(i, Math.sin(0.5 * i) + 1.7);
        }

        // apply() must equal sum of per-direction + mixed contributions.
        final Array full = op.apply(r);
        final Array d0   = op.applyDirection(0, r);
        final Array d1   = op.applyDirection(1, r);
        final Array mix  = op.applyMixed(r);

        assertEquals(n, full.size());
        assertNotNull(d0);
        assertNotNull(d1);
        assertNotNull(mix);

        final double tol = 1.0e-12;
        for (int i = 0; i < n; ++i) {
            final double expected = d0.get(i) + d1.get(i) + mix.get(i);
            assertEquals("mismatch at i=" + i,
                    expected, full.get(i), tol);
        }

        // solveSplitting along an inactive direction must be identity.
        final Array splitOther = op.solveSplitting(2, r, 0.1);
        for (int i = 0; i < n; ++i) {
            assertEquals(r.get(i), splitOther.get(i), 0.0);
        }

        // preconditioner is just solveSplitting along direction 0;
        // sanity-check the call goes through and returns the right size.
        final Array pre = op.preconditioner(r, 0.01);
        assertEquals(n, pre.size());

        // size() is the number of mesh dimensions (== 2 here).
        assertTrue("size() should be 2", op.size() == 2);
    }
}
