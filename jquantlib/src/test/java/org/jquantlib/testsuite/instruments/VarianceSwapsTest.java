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

/*
 Copyright (C) 2006 Warren Chou
 Copyright (C) 2007, 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.instruments;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/varianceswaps.cpp (Phase 5g).
 *
 * <p>The C++ file has two test cases:
 * <ol>
 *   <li>{@code testReplicatingVarianceSwap} — variance swap pricing via the
 *       replicating-portfolio engine of Derman, Kamal &amp; Zou 1999.</li>
 *   <li>{@code testMCVarianceSwap} — variance swap pricing via Monte Carlo
 *       on a Black-Scholes-Merton process with a piecewise-flat vol curve.</li>
 * </ol>
 *
 * <p><b>Phase 5g.5 deferral:</b> JQuantLib does not have:
 * <ul>
 *   <li>{@code VarianceSwap} instrument class
 *       ({@code ql/instruments/varianceswap.hpp}).</li>
 *   <li>{@code ReplicatingVarianceSwapEngine}
 *       ({@code ql/pricingengines/forward/replicatingvarianceswapengine.hpp}).</li>
 *   <li>{@code MakeMCVarianceSwapEngine}
 *       ({@code ql/pricingengines/forward/mcvarianceswapengine.hpp}).</li>
 * </ul>
 * Faithful port deferred until these production classes are added.
 */
public class VarianceSwapsTest {

    public VarianceSwapsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    @Ignore("Phase 5g.5 — VarianceSwap instrument and "
            + "ReplicatingVarianceSwapEngine not present in JQuantLib. "
            + "C++ varianceswaps.cpp testReplicatingVarianceSwap.")
    public void testReplicatingVarianceSwap() { }

    @Test
    @Ignore("Phase 5g.5 — VarianceSwap instrument and MCVarianceSwapEngine "
            + "not present in JQuantLib. "
            + "C++ varianceswaps.cpp testMCVarianceSwap.")
    public void testMCVarianceSwap() { }
}
