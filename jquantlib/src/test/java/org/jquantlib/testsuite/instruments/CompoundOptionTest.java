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
 * Phase 5k skeleton port of {@code test-suite/compoundoption.cpp} v1.42.1
 * (346 LOC, 2 cases).
 *
 * <p>Exercises the compound option (option-on-option, Geske 1979): put-call
 * parity across all four child/mother combinations
 * (call-on-call / call-on-put / put-on-call / put-on-put), and the Haug
 * 2007 / Hull 2009 analytic-engine reference values.
 *
 * <p><strong>All 2 cases deferred to Phase 5k.5</strong> — Java has no
 * compound option subsystem:
 * <ul>
 *   <li>No {@code CompoundOption} instrument class
 *       (mother and daughter exercise / payoff bundle);
 *   <li>No {@code AnalyticCompoundOptionEngine} (Geske 1979 closed form);
 *   <li>The bivariate cumulative normal needed by Geske is present in
 *       {@code org.jquantlib.math.distributions} but not wired through a
 *       compound-option engine.
 * </ul>
 *
 * <p>Production-code carry-forward to a future compound-options phase;
 * Phase 5k.5 is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/compoundoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class CompoundOptionTest {

    private static final String REASON_PARITY =
            "Phase 5k.5 — requires CompoundOption + AnalyticCompoundOptionEngine "
          + "to verify put-call parity across all four mother/daughter "
          + "type combinations";

    private static final String REASON_VALUES =
            "Phase 5k.5 — requires CompoundOption + AnalyticCompoundOptionEngine "
          + "for the Geske / Haug 2007 / Hull 2009 reference values";

    @Ignore(REASON_PARITY) @Test public void testPutCallParity()  { fail("not implemented"); }
    @Ignore(REASON_VALUES) @Test public void testValues()         { fail("not implemented"); }
}
