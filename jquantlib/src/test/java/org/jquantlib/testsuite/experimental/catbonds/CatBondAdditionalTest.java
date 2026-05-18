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
 * <p>This companion file holds the 3 missing cases:
 * <ul>
 *   <li>{@code testBetaRisk} — beta-distributed catastrophe loss
 *       severity;
 *   <li>{@code testRiskFreeAgainstFloatingRateBond} — sanity check that
 *       a risk-free CAT bond converges to a {@link
 *       org.jquantlib.instruments.bonds.FloatingRateBond} when loss
 *       probability is zero;
 *   <li>{@code testCatBondWithGeneratedEventsProportional} — generated
 *       (vs historical) event set with proportional notional risk.
 * </ul>
 *
 * <p>Source: {@code test-suite/catbonds.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class CatBondAdditionalTest {

    private static final String REASON_BETA =
            "Phase 5e.5b-CFC-d-239 (Round-3): BetaRisk + BetaRiskSimulation are "
          + "now ported in experimental/catbonds.  However the C++ test relies "
          + "on std::mt19937 + std::exponential_distribution + std::gamma_distribution "
          + "for its compound-Poisson/Beta sample stream and asserts "
          + "QL_CHECK_CLOSE on the empirical mean/variance at 1-2% (libstdc++) "
          + "or 5-10% (libc++) percentage tolerances. Java's BetaRiskSimulation "
          + "uses java.util.Random + Marsaglia-Tsang gamma; the resulting "
          + "sample stream produces ~2-3%-relative drift on the Poisson mean "
          + "and ~10-20% drift on the compound variance at the C++ N=1e6, which "
          + "trips the C++ tolerances on roughly 60% of seed-untreated runs. "
          + "Un-ignore requires either (a) a deterministic-seed-capable "
          + "BetaRiskSimulation overload (currently the ctor takes no seed; "
          + "production-side change, out of test-only allowlist) or "
          + "(b) widening tolerances beyond the C++ libc++ tier (5%->10% "
          + "Poisson mean, 5%->20% compound variance), which violates the "
          + "CLAUDE.md \"never loosen tolerance\" rule.";

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
