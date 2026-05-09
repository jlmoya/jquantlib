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
 * Phase 5i skeleton port of {@code test-suite/lookbackoptions.cpp} v1.42.1
 * (662 LOC, 5 cases).
 *
 * <p>Exercises continuous floating-strike, continuous fixed-strike,
 * continuous partial-floating, continuous partial-fixed lookback options
 * (Goldman-Sosin-Gatto / Conze-Viswanathan / Heynen-Kat formulas), plus
 * the MC lookback engine.
 *
 * <p><strong>All 5 cases deferred to Phase 5i.5</strong> — Java has no
 * lookback option family at all:
 * <ul>
 *   <li>No {@code ContinuousFloatingLookbackOption} /
 *       {@code ContinuousFixedLookbackOption} /
 *       {@code ContinuousPartialFloatingLookbackOption} /
 *       {@code ContinuousPartialFixedLookbackOption} instruments;
 *   <li>No floating-strike / fixed-strike payoff classes for lookback;
 *   <li>No analytic engines ({@code AnalyticContinuousFloatingLookbackEngine},
 *       {@code AnalyticContinuousFixedLookbackEngine},
 *       {@code AnalyticContinuousPartialFloatingLookbackEngine},
 *       {@code AnalyticContinuousPartialFixedLookbackEngine});
 *   <li>No {@code MCLookbackEngine} (MC continuous-lookback engine).
 * </ul>
 *
 * <p>This is a substantial production-code carry-forward: the entire
 * lookback subsystem (instruments + payoffs + engines) belongs to a
 * future production-code phase; Phase 5i.5 is the test-only carry-forward
 * tag.
 *
 * <p>Source: {@code test-suite/lookbackoptions.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class LookbackOptionTest {

    private static final String REASON_FLOATING =
            "Phase 5i.5 — requires ContinuousFloatingLookbackOption + "
          + "AnalyticContinuousFloatingLookbackEngine port "
          + "(no Java equivalent for the lookback family yet)";

    private static final String REASON_FIXED =
            "Phase 5i.5 — requires ContinuousFixedLookbackOption + "
          + "AnalyticContinuousFixedLookbackEngine port "
          + "(no Java equivalent for the lookback family yet)";

    private static final String REASON_PARTIAL_FLOATING =
            "Phase 5i.5 — requires ContinuousPartialFloatingLookbackOption + "
          + "AnalyticContinuousPartialFloatingLookbackEngine port "
          + "(Heynen-Kat partial-time lookback)";

    private static final String REASON_PARTIAL_FIXED =
            "Phase 5i.5 — requires ContinuousPartialFixedLookbackOption + "
          + "AnalyticContinuousPartialFixedLookbackEngine port "
          + "(Heynen-Kat partial-time fixed-strike lookback)";

    private static final String REASON_MC =
            "Phase 5i.5 — requires MCLookbackEngine port (MC continuous "
          + "lookback engine; depends on the lookback instrument port)";

    @Ignore(REASON_FLOATING)
    @Test
    public void testAnalyticContinuousFloatingLookback() { fail("not implemented"); }

    @Ignore(REASON_FIXED)
    @Test
    public void testAnalyticContinuousFixedLookback() { fail("not implemented"); }

    @Ignore(REASON_PARTIAL_FLOATING)
    @Test
    public void testAnalyticContinuousPartialFloatingLookback() { fail("not implemented"); }

    @Ignore(REASON_PARTIAL_FIXED)
    @Test
    public void testAnalyticContinuousPartialFixedLookback() { fail("not implemented"); }

    @Ignore(REASON_MC)
    @Test
    public void testMonteCarloLookback() { fail("not implemented"); }
}
