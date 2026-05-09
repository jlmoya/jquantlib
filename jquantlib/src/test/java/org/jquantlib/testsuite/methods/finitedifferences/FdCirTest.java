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
 * Phase 5j skeleton port of {@code test-suite/fdcir.cpp} v1.42.1.
 *
 * <p><strong>Single case deferred to Phase 5j.5</strong> — the test file
 * exercises {@code FdmCIROp} (Cox-Ingersoll-Ross finite-difference
 * operator) and the corresponding {@code FdCIRVanillaEngine}.  Neither
 * class is yet ported to Java (Phase 4n.5 carry-forward).
 *
 * <ul>
 *   <li>{@code testFdmCIRConvergence} — convergence study of FD-CIR
 *       pricing as grid is refined.  Requires FdCIRVanillaEngine.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/fdcir.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class FdCirTest {

    @Ignore("Phase 5j.5 — requires FdmCIROp + FdCIRVanillaEngine (Phase 4n.5 carry-forward)")
    @Test
    public void testFdmCIRConvergence() { fail("not implemented"); }
}
