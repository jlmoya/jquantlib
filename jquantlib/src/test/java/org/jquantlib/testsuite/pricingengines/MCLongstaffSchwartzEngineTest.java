/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/mclongstaffschwartzengine.cpp}
 * v1.42.1 (311 LOC, 2 test cases).
 *
 * <p>The two C++ tests are:
 * <ul>
 *   <li>{@code testAmericanOption} — prices an ITM American put on a
 *       single-asset Black-Scholes process via Longstaff-Schwartz MC
 *       ({@code MCAmericanEngine}) and compares to a reference FD price
 *       from {@code FdBlackScholesVanillaEngine}.  Tolerance ~3 standard
 *       errors; uses a polynomial basis of degree 5.</li>
 *   <li>{@code testAmericanMaxOption} — prices a multi-asset American
 *       max-of-N call (the "Boyle 1989" basket) using the LS regression
 *       with a custom multi-asset path pricer
 *       ({@code AmericanMaxPathPricer}). Compares to reference values
 *       from Andersen 1999.</li>
 * </ul>
 *
 * <p><strong>Phase 5h.5 carry-forward:</strong> the Java port lacks the
 * core LS-MC infrastructure for these tests:
 * <ul>
 *   <li>{@code MCAmericanEngine} — Longstaff-Schwartz MC engine for
 *       single-asset American vanilla options.  No equivalent in Java
 *       (the experimental {@code MCAmericanPathEngine} in
 *       {@code experimental.mcbasket} is multi-asset basket-only).</li>
 *   <li>{@code MCLongstaffSchwartzEngine} (template base) — abstract MC
 *       engine that drives the regression.  Java has
 *       {@code MCLongstaffSchwartzPathEngine} in
 *       {@code experimental.mcbasket} but it does not implement the
 *       single-asset signatures expected by these test cases.</li>
 *   <li>{@code LsmBasisSystem::PolynomialType} (Monomial / Chebyshev /
 *       Laguerre / etc.) — basis-function helper.</li>
 * </ul>
 *
 * <p>Once {@code MCAmericanEngine} is ported, both tests can be enabled.
 * The {@code testAmericanMaxOption} case requires the multi-asset
 * {@code StochasticProcessArray} (already present in Java) plus a
 * faithful port of the test's bespoke {@code AmericanMaxPathPricer}
 * (defined inline in the C++ file).
 *
 * <p>Source: {@code test-suite/mclongstaffschwartzengine.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class MCLongstaffSchwartzEngineTest {

    private static final String REASON =
            "Phase 5h.5 — requires single-asset MCAmericanEngine + "
            + "MCLongstaffSchwartzEngine port (Phase 4 carry-forward).";

    @Ignore(REASON)
    @Test
    public void testAmericanOption() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testAmericanMaxOption() { fail("not implemented"); }
}
