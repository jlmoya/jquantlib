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
 * Phase 5d additional skeleton port of {@code test-suite/convertiblebonds.cpp}
 * v1.42.1 (445 LOC, 3 cases) — gap-fill for cases not in
 * {@link ConvertibleBondTest}.
 *
 * <p>{@link ConvertibleBondTest} already covers {@code testBond} and
 * {@code testOption}.
 *
 * <p>This companion file adds {@code testRegression} — a regression
 * scenario covering known issues in past
 * {@link org.jquantlib.pricingengines.BinomialConvertibleEngine}
 * versions (cash-dividend treatment / ex-coupon date interaction with
 * conversion option).
 *
 * <p><strong>Deferred to Phase 5d.5</strong> — the regression test
 * needs:
 * <ul>
 *   <li>Cross-validated reference NPV from the v1.42.1
 *       {@code BinomialConvertibleEngine} via
 *       {@code migration-harness/cpp/probes/instruments/convertiblebonds/};
 *   <li>Audit of the Java {@code BinomialConvertibleEngine}
 *       (re-implemented per design concern D4) for the cash-dividend
 *       and ex-coupon edge cases the C++ regression case exercises.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: body the regression case once the
 * BinomialConvertibleEngine probe values are captured.
 *
 * <p>Source: {@code test-suite/convertiblebonds.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class ConvertibleBondAdditionalTest {

    private static final String REASON =
            "Phase 5d.5 — requires C++ probe regression NPV from "
          + "BinomialConvertibleEngine v1.42.1 + audit of cash-dividend / "
          + "ex-coupon edge cases";

    @Ignore(REASON)
    @Test
    public void testRegression() { fail("not implemented"); }
}
