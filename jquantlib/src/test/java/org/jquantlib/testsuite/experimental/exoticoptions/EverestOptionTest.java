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
 * Phase 5k skeleton port of {@code test-suite/everestoption.cpp} v1.42.1
 * (138 LOC, 1 case).
 *
 * <p>Exercises the Everest option (multi-asset; payoff is a function of
 * the worst performer over the basket plus a guarantee) via the
 * {@code MCEverestEngine} cross-validated against a cached MC reference
 * value (BSM correlated multi-asset paths under Sobol low-discrepancy
 * sequence).
 *
 * <p><strong>The 1 case deferred to Phase 5k.5</strong> — Java has the
 * {@link org.jquantlib.experimental.exoticoptions.EverestOption}
 * instrument (Phase 4h) but lacks the multi-asset MC engine:
 * <ul>
 *   <li>No {@code MCEverestEngine} (Phase 4h instrument port left the
 *       engine for a follow-up phase per Phase 4h.5);
 *   <li>The multi-asset MC engine requires {@code MultiPath} /
 *       {@code MultiPathGenerator} / {@code StochasticProcessArray} /
 *       multi-variate path-pricer wiring — none of which are present;
 *   <li>{@link org.jquantlib.experimental.exoticoptions.EverestOption}
 *       can be constructed and validated (covered by
 *       {@link MultiAssetExoticInstrumentsTest}) but cannot be priced.
 * </ul>
 *
 * <p>The MC engine is a moderate production-code carry-forward; Phase 5k.5
 * is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/everestoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class EverestOptionTest {

    private static final String REASON_CACHED =
            "Phase 5k.5: MCEverestEngine + MultiPath + MultiPathGenerator + StochasticProcessArray "
          + "now ported (commit a40b65cc et al.); test body is `fail(\"not implemented\")` — needs full port from "
          + "C++ test-suite/everestoption.cpp::testCached.";

    @Ignore(REASON_CACHED) @Test public void testCached() { fail("not implemented"); }
}
