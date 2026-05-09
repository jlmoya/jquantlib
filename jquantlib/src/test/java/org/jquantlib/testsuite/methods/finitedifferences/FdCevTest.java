/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5j skeleton port of {@code test-suite/fdcev.cpp} v1.42.1.
 *
 * <p><strong>Both cases deferred to Phase 5j.5</strong> — the test file
 * exercises {@code FdmCEVOp} (CEV finite-difference operator) and
 * {@code FdCEVVanillaEngine} (CEV vanilla engine).  Java has the
 * {@code FdmCEV1dMesher} (Phase 2m) and the {@code CEVRNDCalculator} (in
 * methods.finitedifferences.utilities) but the FD operator and engine
 * are NOT yet ported (Phase 4n.5 carry-forward).
 *
 * <ul>
 *   <li>{@code testLocalMartingale} — checks martingale property of the
 *       CEV process in the FDM solver.  Requires FdCEVVanillaEngine.</li>
 *   <li>{@code testFdmCevOp} — checks the FD operator's convergence
 *       properties.  Requires FdmCEVOp class.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/fdcev.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class FdCevTest {

    private static final String REASON =
            "Phase 5j.5 — requires FdmCEVOp + FdCEVVanillaEngine (Phase 4n.5 carry-forward)";

    @Ignore(REASON)
    @Test
    public void testLocalMartingale() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFdmCevOp() { fail("not implemented"); }
}
