/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.instruments;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/capfloor.cpp (Phase 5e).
 *
 * <p>10 BOOST_AUTO_TEST_CASE methods exercising the
 * {@link org.jquantlib.instruments.CapFloor} instrument with the
 * {@code BlackCapFloorEngine} and {@code BachelierCapFloorEngine}
 * pricing engines.
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>JQuant has {@link org.jquantlib.instruments.CapFloor} (Phase 1
 * stub-finishing) and the {@code BlackCapFloorEngine} (Phase 2j) but
 * lacks the {@code MakeCapFloor} fluent helper that the C++ {@code
 * CommonVars} fixture relies on. Each test builds a vanilla swap, extracts
 * its floating leg, and wraps it in a Cap/Floor — JQuant has the lower-level
 * pieces but no convenience builder.
 *
 * <p>Specifically missing:
 * <ul>
 *   <li>{@code MakeCapFloor} fluent builder — would streamline the C++
 *       {@code makeCapFloor(type, leg, strike, vol)} pattern. See
 *       WI-5e.5-CF-1.</li>
 *   <li>{@code BachelierCapFloorEngine} (used by
 *       {@code testBachelierOptionLetsDelta}) — JQuant has
 *       {@code BachelierBlackFormula} (Phase 5g territory) but no
 *       Bachelier-mode CapFloor engine. See WI-5e.5-CF-2.</li>
 *   <li>{@code CapFloor.optionletsPrice() / optionletsBPS() /
 *       optionletsDelta()} accessors — JQuant has {@code result}-map
 *       but the named accessors that exercise the {@code optionLetsXxx}
 *       results from C++ are unwired. See WI-5e.5-CF-3.</li>
 * </ul>
 *
 * <p>All 10 methods are present as faithful skeleton {@code @Test} stubs
 * mirroring the C++ test names; bodies pending Phase 5e.5 production
 * fills.
 */
public class CapFloorTest {

    public CapFloorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5e.5 WI-5e.5-CF-1: MakeCapFloor now ported (commit c1e9cb84); empty test body — needs full port from C++ capfloor.cpp::testVega.")
    @Test
    public void testVega() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-CF-1: MakeCapFloor now ported (commit c1e9cb84); empty test body — needs full port from C++ capfloor.cpp.")
    @Test
    public void testStrikeDependency() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-CF-1: MakeCapFloor now ported (commit c1e9cb84); empty test body — needs full port from C++ capfloor.cpp.")
    @Test
    public void testConsistency() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-CF-1: MakeCapFloor now ported (commit c1e9cb84); empty test body — needs full port from C++ capfloor.cpp.")
    @Test
    public void testParity() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-CF-1: MakeCapFloor now ported (commit c1e9cb84); empty test body — needs full port from C++ capfloor.cpp.")
    @Test
    public void testATMRate() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-CF-1: MakeCapFloor now ported (commit c1e9cb84); empty test body — also needs implied-volatility solver wiring on CapFloor.")
    @Test
    public void testImpliedVolatility() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-CF-1: MakeCapFloor now ported (commit c1e9cb84); empty test body — also needs cached NPVs regenerated from C++ v1.42.1.")
    @Test
    public void testCachedValue() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CF-3 — needs CapFloor.optionletsPrice()/"
            + "optionletsBPS() result-map accessors.")
    @Test
    public void testCachedValueFromOptionLets() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CF-3 — needs CapFloor.optionletsDelta() accessor "
            + "+ Black-mode capfloor engine delta computation.")
    @Test
    public void testOptionLetsDelta() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CF-2 + WI-5e.5-CF-3 — needs Bachelier-mode "
            + "CapFloor engine + optionletsDelta accessor.")
    @Test
    public void testBachelierOptionLetsDelta() {
    }
}
