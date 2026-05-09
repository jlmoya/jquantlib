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
 * Phase 5k skeleton port of {@code test-suite/twoassetbarrieroption.cpp}
 * v1.42.1 (144 LOC, 1 case).
 *
 * <p>Exercises the two-asset barrier option (Heynen-Kat 1994; the payoff
 * depends on one asset, the barrier is monitored on the other) cross-
 * validated against the Haug 2007 reference table for the four
 * {@code Barrier::Type} variants (DownIn / DownOut / UpIn / UpOut).
 *
 * <p><strong>The 1 case deferred to Phase 5k.5</strong> — Java has no
 * two-asset barrier subsystem:
 * <ul>
 *   <li>No {@code TwoAssetBarrierOption} instrument class;
 *   <li>No {@code AnalyticTwoAssetBarrierEngine} (Heynen-Kat 1994 closed
 *       form; depends on bivariate-normal CDF);
 *   <li>The bivariate normal CDF used by the formula is present in
 *       {@code org.jquantlib.math.distributions} but is not wired
 *       through a two-asset barrier engine.
 * </ul>
 *
 * <p>Production-code carry-forward to a future multi-asset barrier
 * extensions phase; Phase 5k.5 is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/twoassetbarrieroption.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class TwoAssetBarrierOptionTest {

    private static final String REASON_HAUG =
            "Phase 5k.5 — requires TwoAssetBarrierOption instrument + "
          + "AnalyticTwoAssetBarrierEngine (Heynen-Kat 1994) for the "
          + "Haug 2007 reference table";

    @Ignore(REASON_HAUG) @Test public void testHaugValues() { fail("not implemented"); }
}
