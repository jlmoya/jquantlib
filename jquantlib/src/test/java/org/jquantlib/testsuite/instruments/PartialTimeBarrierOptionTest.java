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
 * Phase 5k skeleton port of {@code test-suite/partialtimebarrieroption.cpp}
 * v1.42.1 (328 LOC, 3 cases).
 *
 * <p>Exercises the partial-time barrier option (Heynen-Kat 1994):
 * call analytic engine values, put analytic engine values, and put-call
 * symmetry across barrier types ({@code PartialBarrier::Type::Start} /
 * {@code End} / {@code B1} / {@code B2}).
 *
 * <p><strong>All 3 cases deferred to Phase 5k.5</strong> — Java has the
 * {@link org.jquantlib.instruments.BarrierOption} (Phase 4e) but lacks
 * the partial-time variant:
 * <ul>
 *   <li>No {@code PartialTimeBarrierOption} instrument class;
 *   <li>No {@code PartialBarrier::Type} enum (Start / End / B1 / B2);
 *   <li>No {@code AnalyticPartialTimeBarrierOptionEngine}
 *       (Heynen-Kat 1994 closed-form);
 *   <li>The trivariate normal CDF needed by some Heynen-Kat branches
 *       is not present in {@code org.jquantlib.math.distributions}.
 * </ul>
 *
 * <p>The partial-time barrier family is a production-code carry-forward
 * to a future barrier-extensions phase; Phase 5k.5 is the test-only
 * carry-forward tag.
 *
 * <p>Source: {@code test-suite/partialtimebarrieroption.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class PartialTimeBarrierOptionTest {

    private static final String REASON_ANALYTIC_CALL =
            "Phase 5k.5 — requires PartialTimeBarrierOption instrument + "
          + "AnalyticPartialTimeBarrierOptionEngine (Heynen-Kat call branch)";

    private static final String REASON_ANALYTIC_PUT =
            "Phase 5k.5 — requires PartialTimeBarrierOption instrument + "
          + "AnalyticPartialTimeBarrierOptionEngine (Heynen-Kat put branch)";

    private static final String REASON_SYMMETRY =
            "Phase 5k.5 — requires the full partial-time barrier engine to "
          + "verify put-call symmetry across PartialBarrier::Type values";

    @Ignore(REASON_ANALYTIC_CALL) @Test public void testAnalyticEngine()           { fail("not implemented"); }
    @Ignore(REASON_ANALYTIC_PUT)  @Test public void testAnalyticEnginePutOption()  { fail("not implemented"); }
    @Ignore(REASON_SYMMETRY)      @Test public void testPutCallSymmetry()          { fail("not implemented"); }
}
