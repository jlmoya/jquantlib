/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.finitedifferences;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5j skeleton port of {@code test-suite/vpp.cpp} v1.42.1.
 *
 * <p><strong>Phase 5e.5b-CFC-d-164 update:</strong>
 * {@link org.jquantlib.experimental.finitedifferences.VanillaVPPOption},
 * {@link org.jquantlib.experimental.finitedifferences.FdKlugeExtOUSpreadEngine},
 * {@link org.jquantlib.experimental.finitedifferences.FdmSpreadPayoffInnerValue},
 * and {@link org.jquantlib.instruments.SwingExercise} are now ported.
 * The remaining blockers are listed per-test below.
 *
 * <p>Java has the {@code KlugeExtOUProcess}, {@code GemanRoncoroniProcess},
 * and {@code ExtOUWithJumpsProcess} classes (in
 * {@code org.jquantlib.experimental.processes}) but the energy-option
 * engines (FdSimpleExtOUStorageEngine, FdSimpleKlugeExtOUVPPEngine,
 * DynProgVPPIntrinsicValueEngine) and the supporting FD operators
 * (FdmExtOUOp, FdmKlugeExtOUOp, FdmExtOUJumpOp, FdmNdimSolver,
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
            "Phase 5j.5 — requires FdSimpleExtOUStorageEngine + FdmExtOUJumpOp "
          + "(storage-engine FD operator family not yet ported)";

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
}
