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
 * Phase 5d skeleton port of {@code test-suite/bondforward.cpp} v1.42.1
 * (154 LOC, 3 cases).
 *
 * <p>Exercises the {@code BondForward} instrument — forward agreements on
 * a generic underlying bond, including bond-future replication and the
 * relationship between forward and spot value when the bond pays no
 * income over the contract's life.
 *
 * <p><strong>All 3 cases deferred to Phase 5d.5</strong> — Java has no
 * {@code BondForward} class:
 * <ul>
 *   <li>No {@code BondForward} instrument
 *       (C++ {@code ql/instruments/bondforward.hpp});
 *   <li>No {@code BondFuturesEngine} for futures-price replication;
 *   <li>No forward/repo discounting integration with the bond cashflow
 *       income stream.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the {@code BondForward} family
 * (instrument + futures-replication engine + spot-vs-forward identity
 * checks) belongs to a future production-code phase.
 *
 * <p>Source: {@code test-suite/bondforward.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class BondForwardTest {

    private static final String REASON_FUTURES =
            "Phase 5d.5 — requires BondForward + BondFuturesEngine port "
          + "(no Java equivalent for bond-future replication yet)";

    private static final String REASON_CLEAN =
            "Phase 5d.5 — requires BondForward port + clean-forward-price "
          + "replication harness (no Java equivalent yet)";

    private static final String REASON_NO_INCOME =
            "Phase 5d.5 — requires BondForward port + spot-equals-forward "
          + "identity check for income-free bonds (no Java equivalent yet)";

    @Ignore(REASON_FUTURES)
    @Test
    public void testFuturesPriceReplication() { fail("not implemented"); }

    @Ignore(REASON_CLEAN)
    @Test
    public void testCleanForwardPriceReplication() { fail("not implemented"); }

    @Ignore(REASON_NO_INCOME)
    @Test
    public void testThatForwardValueIsEqualToSpotValueIfNoIncome() {
        fail("not implemented");
    }
}
