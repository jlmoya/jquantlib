/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.shortrate;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/shortratemodels.cpp} v1.42.1
 * (445 LOC, 6 test cases).
 *
 * <p>Java already has per-model calibration tests
 * ({@code HullWhiteCalibrationTest}, {@code BlackKarasinskiCalibrationTest},
 * {@code CoxIngersollRossCalibrationTest}, {@code VasicekCalibrationTest},
 * {@code G2Test}); the C++ {@code shortratemodels.cpp} additionally
 * exercises cached pricing values, fixed-reversion calibration, swap
 * pricing through HW, futures convexity bias, and ECIR discount factors.
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong>:
 * <ul>
 *   <li>{@code testCachedHullWhite} — cached HW pricing values</li>
 *   <li>{@code testCachedHullWhiteFixedReversion} — fixed-reversion HW</li>
 *   <li>{@code testCachedHullWhite2} — second cached HW reference set</li>
 *   <li>{@code testSwaps} — swap pricing through HW model</li>
 *   <li>{@code testFuturesConvexityBias} — futures-vs-FRA convexity</li>
 *   <li>{@code testExtendedCoxIngersollRossDiscountFactor} — ECIR DF</li>
 * </ul>
 *
 * <p>Reference values must be regenerated via {@code migration-harness/}
 * probes against C++ v1.42.1 — Phase 5f.5 task.
 *
 * <p>Source: {@code test-suite/shortratemodels.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class ShortRateModelsTest {

    @Ignore("Phase 5f.5 — needs cross-validated HW reference values")
    @Test
    public void testCachedHullWhite() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — fixed-reversion HW calibration plumbing")
    @Test
    public void testCachedHullWhiteFixedReversion() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — needs cross-validated HW reference values (set 2)")
    @Test
    public void testCachedHullWhite2() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — needs HW swap-pricing reference values")
    @Test
    public void testSwaps() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — futures-convexity-bias adjustment harness")
    @Test
    public void testFuturesConvexityBias() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — ECIR discount factor cross-validation")
    @Test
    public void testExtendedCoxIngersollRossDiscountFactor() { fail("not implemented"); }
}
