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
 Copyright (C) 2009 Dimitri Reiswich

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.pricingengines;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/blackdeltacalculator.cpp (Phase 5g).
 *
 * <p>The C++ file has four test cases exercising the FX premium-adjusted /
 * unadjusted delta conventions implemented in
 * {@code BlackDeltaCalculator}: testDeltaValues, testDeltaPriceConsistency,
 * testPutCallParity, testAtmCalcs.
 *
 * <p><b>Phase 5g.5 deferral:</b> JQuantLib does not have a
 * {@code BlackDeltaCalculator} class. The C++ class lives at
 * {@code ql/pricingengines/blackdeltacalculator.hpp} and exercises the
 * spot/forward × premium-adjusted/unadjusted FX delta conventions. The
 * Java port belongs to the deferred Phase 4l (experimental/fx). Faithful
 * test port deferred to Phase 5g.5 — explicitly called out as design
 * concern D7 in the Phase 5 META design.
 */
public class BlackDeltaCalculatorTest {

    public BlackDeltaCalculatorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    @Ignore("Phase 5g.5 / Phase 4l — BlackDeltaCalculator class not present in "
            + "JQuantLib (deferred experimental/fx). Design concern D7. "
            + "C++ blackdeltacalculator.cpp testDeltaValues.")
    public void testDeltaValues() { }

    @Test
    @Ignore("Phase 5g.5 — see testDeltaValues. "
            + "C++ blackdeltacalculator.cpp testDeltaPriceConsistency.")
    public void testDeltaPriceConsistency() { }

    @Test
    @Ignore("Phase 5g.5 — see testDeltaValues. "
            + "C++ blackdeltacalculator.cpp testPutCallParity.")
    public void testPutCallParity() { }

    @Test
    @Ignore("Phase 5g.5 — see testDeltaValues. "
            + "C++ blackdeltacalculator.cpp testAtmCalcs.")
    public void testAtmCalcs() { }
}
