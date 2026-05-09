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
 * Phase 5d skeleton port of {@code test-suite/forwardrateagreement.cpp}
 * v1.42.1 (120 LOC, 1 case).
 *
 * <p>Exercises the {@link org.jquantlib.instruments.ForwardRateAgreement}
 * (FRA) instrument — specifically that an FRA can be constructed without
 * a discount curve provided, deferring engine binding to a later
 * {@code setPricingEngine} call.
 *
 * <p><strong>Deferred to Phase 5d.5</strong> — Java has the
 * {@code ForwardRateAgreement} class but:
 * <ul>
 *   <li>The v1.42.1 constructor signature accepts an optional
 *       {@code Handle<YieldTermStructure>} (no-curve construction
 *       branch). The Java port retained an earlier signature and the
 *       no-curve construction branch is not exposed. Aligning the
 *       Java FRA constructor to v1.42.1 (P-2j-style API alignment) is
 *       the prerequisite for this case.
 *   <li>The {@code setPricingEngine} retro-binding path also needs
 *       audit: in C++ v1.42.1 the FRA delegates to a discounting engine
 *       set after construction. Java currently couples engine and
 *       construction.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: realign {@code ForwardRateAgreement}
 * constructor / engine wiring to v1.42.1 then body this case.
 *
 * <p>Source: {@code test-suite/forwardrateagreement.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class ForwardRateAgreementTest {

    private static final String REASON =
            "Phase 5d.5 — requires alignment of ForwardRateAgreement "
          + "constructor + engine-wiring to v1.42.1 (no-curve construction "
          + "+ deferred setPricingEngine path)";

    @Ignore(REASON)
    @Test
    public void testConstructionWithoutACurve() { fail("not implemented"); }
}
