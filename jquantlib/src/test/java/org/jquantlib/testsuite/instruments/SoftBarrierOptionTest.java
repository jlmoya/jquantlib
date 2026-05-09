/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5k skeleton port of {@code test-suite/softbarrieroption.cpp}
 * v1.42.1 (208 LOC, 1 case).
 *
 * <p>Exercises the soft-barrier (range-barrier) option (Hart-Ross 1994):
 * a partial-knockout barrier that activates between two barrier levels
 * with linearly-decreasing notional. Cross-validated against the Haug 2007
 * reference table.
 *
 * <p><strong>The 1 case deferred to Phase 5k.5</strong> — Java has no
 * soft-barrier instrument or engine:
 * <ul>
 *   <li>No {@code SoftBarrierOption} (range-barrier) instrument;
 *   <li>No {@code SoftBarrier::Type} enum (DownIn / DownOut / UpIn / UpOut);
 *   <li>No {@code AnalyticSoftBarrierEngine} (Hart-Ross 1994 closed form).
 * </ul>
 *
 * <p>Production-code carry-forward to a future barrier-extensions phase;
 * Phase 5k.5 is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/softbarrieroption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class SoftBarrierOptionTest {

    private static final String REASON_HAUG =
            "Phase 5k.5 — requires SoftBarrierOption instrument + "
          + "AnalyticSoftBarrierEngine (Hart-Ross 1994) for the "
          + "Haug 2007 reference table";

    @Ignore(REASON_HAUG) @Test public void testSoftBarrierHaug() { fail("not implemented"); }
}
