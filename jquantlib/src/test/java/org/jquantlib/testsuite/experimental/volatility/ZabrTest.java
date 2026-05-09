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
 Copyright (C) 2014 Peter Caspers
 Copyright (C) 2026 Aaditya Panikath

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/zabr.cpp (Phase 5g).
 *
 * <p>Direct-named equivalent. The C++ file has a single test
 * {@code testConsistency} which compares ZabrSmileSection across four
 * evaluation modes ({@code ZabrShortMaturityLognormal},
 * {@code ZabrShortMaturityNormal}, {@code ZabrLocalVolatility},
 * {@code ZabrFullFd}) against SabrSmileSection at gamma=1 across strikes
 * in [1e-4, 0.7] step 1e-4.
 *
 * <p><b>Phase 5g.5 deferral:</b> the Java
 * {@link org.jquantlib.experimental.volatility.ZabrModel} {@code lognormalVolatility},
 * {@code normalVolatility}, {@code localVolatility}, {@code fdPrice}, and
 * {@code fullFdPrice} methods are Phase 4f / 4n stubs that throw
 * {@code UnsupportedOperationException}. There is also no
 * {@code ZabrSmileSection} class. Faithful port deferred to Phase 5g.5.
 *
 * <p>Existing {@link ZabrAndNoArbSabrSmokeTest} verifies the Phase 4f
 * scaffolding (constructor / parameter validation).
 */
public class ZabrTest {

    public ZabrTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    @Ignore("Phase 5g.5 — ZabrSmileSection class not present and ZabrModel "
            + "evaluation methods are Phase 4f/4n stubs that throw. "
            + "C++ zabr.cpp lines 33-92.")
    public void testConsistency() {
        // Deferred. Faithful body would:
        //   tol = 1e-4
        //   alpha=0.08, beta=0.70, nu=0.20, rho=-0.30, tau=5.0, forward=0.03
        //   build SabrSmileSection sabr and four ZabrSmileSection variants
        //     ZabrShortMaturityLognormal zabr0 (gamma=1.0)
        //     ZabrShortMaturityNormal    zabr1 (gamma=1.0)
        //     ZabrLocalVolatility        zabr2 (gamma=1.0)
        //     ZabrFullFd                 zabr3 (gamma=1.0, sub-grid 2)
        //   for k ∈ [1e-4, 0.7] step 1e-4:
        //     verify |zabr*.optionPrice(k) - sabr.optionPrice(k)| < tol
    }
}
