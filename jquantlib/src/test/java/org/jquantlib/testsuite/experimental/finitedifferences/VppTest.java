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
 * <p><strong>All 6 cases deferred to Phase 5j.5</strong> — the test file
 * exercises Virtual Power Plant (VPP) pricing infrastructure and the
 * Geman-Roncoroni / ExtOU-with-jumps process family.  Java has the
 * {@code KlugeExtOUProcess}, {@code GemanRoncoroniProcess}, and
 * {@code ExtOUWithJumpsProcess} classes (in
 * {@code org.jquantlib.experimental.processes}) but the energy-option
 * engines (FdSimpleExtOUStorageEngine, FdSimpleKlugeExtOUVPPEngine) and
 * the supporting FD operators (FdmExtOUOp, FdmKlugeExtOUOp,
 * FdmExtOUJumpOp) are NOT yet ported (Phase 4n.5 carry-forward).
 *
 * <p>The {@code testKlugeExtOUMatrixDecomposition} case additionally
 * requires the MC-based VPP intrinsic-value calculator which depends on a
 * Bermudan Longstaff-Schwartz infrastructure not yet exercised for
 * commodity processes.
 *
 * <p>Source: {@code test-suite/vpp.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class VppTest {

    private static final String REASON_ENGINE =
            "Phase 5j.5 — requires energy-option FD engines "
          + "(FdSimpleExtOUStorageEngine, FdSimpleKlugeExtOUVPPEngine) — "
          + "Phase 4n.5 carry-forward";

    private static final String REASON_PROCESS =
            "Phase 5j.5 — requires GemanRoncoroniProcess MC harness wiring "
          + "(process exists; calibration loop not yet exercised)";

    @Ignore(REASON_PROCESS)
    @Test
    public void testGemanRoncoroniProcess() { fail("not implemented"); }

    @Ignore(REASON_ENGINE)
    @Test
    public void testSimpleExtOUStorageEngine() { fail("not implemented"); }

    @Ignore(REASON_ENGINE + " + KlugeExtOUSpreadOption infrastructure")
    @Test
    public void testKlugeExtOUSpreadOption() { fail("not implemented"); }

    @Ignore("Phase 5j.5 — requires VanillaVPPOption + intrinsic-value pricer")
    @Test
    public void testVPPIntrinsicValue() { fail("not implemented"); }

    @Ignore(REASON_ENGINE + " + VanillaVPPOption full pricing path")
    @Test
    public void testVPPPricing() { fail("not implemented"); }

    @Ignore("Phase 5j.5 — requires Kluge ExtOU matrix decomposition utility "
          + "(used by Bermudan VPP MC engine)")
    @Test
    public void testKlugeExtOUMatrixDecomposition() { fail("not implemented"); }
}
