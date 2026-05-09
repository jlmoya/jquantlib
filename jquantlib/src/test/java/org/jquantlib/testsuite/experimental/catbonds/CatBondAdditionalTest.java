/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.catbonds;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d additional skeleton port of {@code test-suite/catbonds.cpp}
 * v1.42.1 (665 LOC, 9 cases) — gap-fill for cases not in
 * {@link CatBondTest}.
 *
 * <p>{@link CatBondTest} (Phase 4 experimental port) already covers 6
 * of the 9 cases:
 * {@code testEventSetForWholeYears},
 * {@code testEventSetForIrregularPeriods},
 * {@code testEventSetForNoEvents},
 * {@code testCatBondInDoomScenario},
 * {@code testCatBondWithDoomOnceInTenYears},
 * {@code testCatBondWithProportionalNotional} (Java rename for one of
 * the proportional-notional C++ cases).
 *
 * <p>This companion file adds the 3 missing cases:
 * <ul>
 *   <li>{@code testBetaRisk} — beta-distributed catastrophe loss
 *       severity (uses {@code BetaRisk} class — present in C++,
 *       absent in Java);
 *   <li>{@code testRiskFreeAgainstFloatingRateBond} — sanity check that
 *       a risk-free CAT bond converges to a {@link
 *       org.jquantlib.instruments.bonds.FloatingRateBond} when loss
 *       probability is zero;
 *   <li>{@code testCatBondWithGeneratedEventsProportional} — generated
 *       (vs historical) event set with proportional notional risk.
 * </ul>
 *
 * <p><strong>All 3 cases deferred to Phase 5d.5</strong>:
 * <ul>
 *   <li>{@code testBetaRisk} requires the {@code BetaRisk}
 *       {@link org.jquantlib.experimental.catbonds.NotionalRisk} subclass
 *       (C++ has it; Java {@code experimental.catbonds} package only has
 *       {@code DigitalNotionalRisk} + {@code ProportionalNotionalRisk});
 *   <li>{@code testRiskFreeAgainstFloatingRateBond} needs cross-validation
 *       against a known-equivalent FRN built with the same schedule;
 *       requires probe values for both legs;
 *   <li>{@code testCatBondWithGeneratedEventsProportional} requires a
 *       parametric {@code BetaRisk}-driven event simulator (currently
 *       Java only supports the {@code EventSetSimulation} from a fixed
 *       historical {@code EventSet}).
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: port the {@code BetaRisk} class and add
 * the generated-event-set simulation harness, then body these tests.
 *
 * <p>Source: {@code test-suite/catbonds.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class CatBondAdditionalTest {

    private static final String REASON_BETA =
            "Phase 5d.5 — requires BetaRisk NotionalRisk subclass "
          + "(present in C++ experimental/catbonds; not in Java)";

    private static final String REASON_RISK_FREE =
            "Phase 5d.5 — requires probe reference for the "
          + "risk-free-CAT-bond / FloatingRateBond convergence assertion";

    private static final String REASON_GENERATED =
            "Phase 5d.5 — requires BetaRisk-driven generated-event-set "
          + "simulator + proportional notional risk wiring";

    @Ignore(REASON_BETA)
    @Test
    public void testBetaRisk() { fail("not implemented"); }

    @Ignore(REASON_RISK_FREE)
    @Test
    public void testRiskFreeAgainstFloatingRateBond() { fail("not implemented"); }

    @Ignore(REASON_GENERATED)
    @Test
    public void testCatBondWithGeneratedEventsProportional() { fail("not implemented"); }
}
