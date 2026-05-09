/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.exoticoptions;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5k skeleton port of {@code test-suite/pagodaoption.cpp} v1.42.1
 * (134 LOC, 1 case).
 *
 * <p>Exercises the Pagoda option (multi-asset, capped/floored ratchet of
 * portfolio returns) via the {@code MCPagodaEngine} cross-validated
 * against a cached MC reference value (BSM correlated multi-asset paths
 * under Sobol low-discrepancy sequence).
 *
 * <p><strong>The 1 case deferred to Phase 5k.5</strong> — Java has the
 * {@link org.jquantlib.experimental.exoticoptions.PagodaOption}
 * instrument (Phase 4h) but lacks the multi-asset MC engine:
 * <ul>
 *   <li>No {@code MCPagodaEngine} (Phase 4h instrument port left the
 *       engine for a follow-up phase per Phase 4h.5);
 *   <li>The multi-asset MC engine requires {@code MultiPath} /
 *       {@code MultiPathGenerator} / {@code StochasticProcessArray} /
 *       multi-variate path-pricer wiring — none of which are present;
 *   <li>{@link org.jquantlib.experimental.exoticoptions.PagodaOption}
 *       can be constructed and validated (covered by
 *       {@link MultiAssetExoticInstrumentsTest}) but cannot be priced.
 * </ul>
 *
 * <p>The MC engine is a moderate production-code carry-forward; Phase 5k.5
 * is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/pagodaoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class PagodaOptionTest {

    private static final String REASON_CACHED =
            "Phase 5k.5 — requires MCPagodaEngine (Phase 4h.5 carry-forward); "
          + "depends on MultiPath / MultiPathGenerator / StochasticProcessArray "
          + "infrastructure that Java lacks";

    @Ignore(REASON_CACHED) @Test public void testCached() { fail("not implemented"); }
}
