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
 * Phase 5k skeleton port of {@code test-suite/chooseroption.cpp} v1.42.1
 * (163 LOC, 2 cases).
 *
 * <p>Exercises the chooser (preference) option: simple chooser (same strike
 * and expiry for the call and put alternatives, Rubinstein 1991 closed
 * form) and complex chooser (Rubinstein 1991 with different strikes /
 * expiries; trivariate normal CDF).
 *
 * <p><strong>All 2 cases deferred to Phase 5k.5</strong> — Java has no
 * chooser-option subsystem:
 * <ul>
 *   <li>No {@code SimpleChooserOption} / {@code ComplexChooserOption}
 *       instrument classes;
 *   <li>No {@code AnalyticSimpleChooserEngine} (closed-form);
 *   <li>No {@code AnalyticComplexChooserEngine} (depends on
 *       trivariate-normal CDF, not present in Java);
 *   <li>The trivariate normal CDF is needed by the complex-chooser branch
 *       and is also a Phase 5k.5 carry-forward in
 *       {@code org.jquantlib.math.distributions}.
 * </ul>
 *
 * <p>Production-code carry-forward to a future preference-options phase;
 * Phase 5k.5 is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/chooseroption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class ChooserOptionTest {

    private static final String REASON_SIMPLE =
            "Phase 5k.5 — requires SimpleChooserOption + "
          + "AnalyticSimpleChooserEngine (Rubinstein 1991 closed form)";

    private static final String REASON_COMPLEX =
            "Phase 5k.5 — requires ComplexChooserOption + "
          + "AnalyticComplexChooserEngine (Rubinstein 1991; depends on "
          + "trivariate-normal CDF — also Phase 5k.5 carry-forward)";

    @Ignore(REASON_SIMPLE)  @Test public void testAnalyticSimpleChooserEngine()    { fail("not implemented"); }
    @Ignore(REASON_COMPLEX) @Test public void testAnalyticComplexChooserEngine()   { fail("not implemented"); }
}
