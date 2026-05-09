/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.indexes;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/equityindex.cpp} v1.42.1
 * (283 LOC, 12 cases).
 *
 * <p>Exercises the {@code EquityIndex} class — observed equity prices used
 * as the underlying for equity-linked products (TRS, equity coupons,
 * dividend-paying single-stock options). Tests today's fixing semantics,
 * forecasting (with/without dividend curve, with/without spot, with/without
 * historical fixing), spot-change observability, and validation against
 * invalid / missing fixings or interest curves.
 *
 * <p><strong>All 12 cases deferred to Phase 5d.5</strong> — Java has no
 * {@code EquityIndex} class:
 * <ul>
 *   <li>No {@code EquityIndex} class
 *       (C++ {@code ql/indexes/equityindex.hpp});
 *   <li>No spot-as-proxy fallback for today's fixing;
 *   <li>No forecast formula
 *       {@code S_T = S_0 * P_div(T) / (P_eq(T) * P_irr(T))} wired through
 *       a {@link org.jquantlib.indexes.Index} subclass;
 *   <li>No observability harness specific to spot quote changes.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the {@code EquityIndex} family belongs to
 * a future production-code phase. Required by
 * {@link org.jquantlib.testsuite.cashflows.EquityCashFlowTest} and
 * {@link org.jquantlib.testsuite.instruments.EquityTotalReturnSwapTest}.
 *
 * <p>Source: {@code test-suite/equityindex.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class EquityIndexTest {

    private static final String REASON_FIXING =
            "Phase 5d.5 — requires EquityIndex port + today's fixing semantics "
          + "(no Java equivalent yet)";

    private static final String REASON_SPOT_PROXY =
            "Phase 5d.5 — requires EquityIndex port + spot-as-proxy fallback "
          + "for today's fixing (no Java equivalent yet)";

    private static final String REASON_FORECAST =
            "Phase 5d.5 — requires EquityIndex port + forward forecast formula "
          + "(dividend / interest / equity term-structure handles; no Java equivalent yet)";

    private static final String REASON_VALIDATION =
            "Phase 5d.5 — requires EquityIndex port + invalid-fixing-date / "
          + "missing-fixing / missing-curve validation (no Java equivalent yet)";

    private static final String REASON_OBSERVABILITY =
            "Phase 5d.5 — requires EquityIndex port + spot-quote observability "
          + "harness (no Java equivalent yet)";

    @Ignore(REASON_FIXING)
    @Test
    public void testTodaysFixing() { fail("not implemented"); }

    @Ignore(REASON_SPOT_PROXY)
    @Test
    public void testTodaysFixingWithSpotAsProxy() { fail("not implemented"); }

    @Ignore(REASON_FORECAST)
    @Test
    public void testFixingForecast() { fail("not implemented"); }

    @Ignore(REASON_FORECAST)
    @Test
    public void testFixingForecastWithoutDividend() { fail("not implemented"); }

    @Ignore(REASON_FORECAST)
    @Test
    public void testFixingForecastWithoutSpot() { fail("not implemented"); }

    @Ignore(REASON_FORECAST)
    @Test
    public void testFixingForecastWithoutSpotAndHistoricalFixing() {
        fail("not implemented");
    }

    @Ignore(REASON_OBSERVABILITY)
    @Test
    public void testSpotChange() { fail("not implemented"); }

    @Ignore(REASON_VALIDATION)
    @Test
    public void testErrorWhenInvalidFixingDate() { fail("not implemented"); }

    @Ignore(REASON_VALIDATION)
    @Test
    public void testErrorWhenFixingMissing() { fail("not implemented"); }

    @Ignore(REASON_VALIDATION)
    @Test
    public void testErrorWhenInterestHandleMissing() { fail("not implemented"); }

    @Ignore(REASON_OBSERVABILITY)
    @Test
    public void testFixingObservability() { fail("not implemented"); }

    @Ignore(REASON_FIXING)
    @Test
    public void testNoErrorIfTodayIsNotBusinessDay() { fail("not implemented"); }
}
