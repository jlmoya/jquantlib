/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.currency;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d additional skeleton port of {@code test-suite/exchangerate.cpp}
 * v1.42.1 (383 LOC, 5 cases) — gap-fill complement to
 * {@link ExchangeRateManagerTest}.
 *
 * <p>{@link ExchangeRateManagerTest} covers a different surface area
 * (manager-centric: known-rate population, custom-add, multiple-entries
 * date-range lookup, clear semantics, missing-pair error) and does not
 * mirror the C++ {@code exchangerate.cpp} test layout, which is
 * organized around {@link org.jquantlib.currencies.ExchangeRate}
 * instances and {@code ExchangeRate::Type} (Direct vs Derived).
 *
 * <p>This companion file mirrors the C++ layout's 5 cases:
 * <ul>
 *   <li>{@code testDirect} — direct-quote arithmetic (rate, source,
 *       target);
 *   <li>{@code testDerived} — derived-quote chain (transitive rate);
 *   <li>{@code testDirectLookup} — manager direct-lookup variant;
 *   <li>{@code testTriangulatedLookup} — triangulated-via-EUR lookup
 *       (legacy fixed-rate triangulation through a base currency);
 *   <li>{@code testSmartLookup} — smart-lookup that tries Direct then
 *       Derived then Triangulated.
 * </ul>
 *
 * <p><strong>All 5 cases deferred to Phase 5d.5</strong> — Java has
 * {@link org.jquantlib.currencies.ExchangeRate} and
 * {@link org.jquantlib.currencies.ExchangeRateManager}, but:
 * <ul>
 *   <li>The {@code ExchangeRate.Type.Derived} branch wiring (chain
 *       construction from two source rates) needs an audit against
 *       v1.42.1 — Java has the enum but the chain-derivation arithmetic
 *       was last verified pre-1.42.1;
 *   <li>The {@code ExchangeRateManager.lookup} triangulation path
 *       through EUR (legacy ECU / fixed-rate triangulation) needs a
 *       probe-cross-validated case;
 *   <li>{@code SmartLookup} fallback ordering needs to be validated
 *       against v1.42.1 (priority: Direct → Derived → Triangulated).
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: body these against
 * {@code migration-harness/cpp/probes/currencies/exchangerate/} probes
 * once authored — the existing {@code exchangerate_manager} probe only
 * covers the lookup surface, not the {@code Type::Derived} arithmetic.
 *
 * <p>Source: {@code test-suite/exchangerate.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class ExchangeRateAdditionalTest {

    private static final String REASON_ARITHMETIC =
            "Phase 5d.5 — requires audit of ExchangeRate Direct/Derived "
          + "arithmetic against v1.42.1 + probe reference values";

    private static final String REASON_LOOKUP =
            "Phase 5d.5 — requires probe-validated reference for "
          + "ExchangeRateManager direct lookup variant beyond what "
          + "ExchangeRateManagerTest already covers";

    private static final String REASON_TRIANGULATED =
            "Phase 5d.5 — requires audit of EUR triangulated-lookup path "
          + "(legacy ECU / fixed-rate triangulation through a base currency)";

    private static final String REASON_SMART =
            "Phase 5d.5 — requires audit of smart-lookup fallback ordering "
          + "(Direct → Derived → Triangulated) against v1.42.1";

    @Ignore(REASON_ARITHMETIC) @Test public void testDirect() { fail("not implemented"); }
    @Ignore(REASON_ARITHMETIC) @Test public void testDerived() { fail("not implemented"); }
    @Ignore(REASON_LOOKUP) @Test public void testDirectLookup() { fail("not implemented"); }
    @Ignore(REASON_TRIANGULATED) @Test public void testTriangulatedLookup() { fail("not implemented"); }
    @Ignore(REASON_SMART) @Test public void testSmartLookup() { fail("not implemented"); }
}
