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

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/noarbsabr.cpp (Phase 5g).
 *
 * <p>Direct-named equivalent. The C++ file has two test cases:
 * <ol>
 *   <li>{@code testAbsorptionMatrix} — exercises the package-private C++ class
 *       {@code QuantLib::detail::D0Interpolator} which interpolates a 16-point
 *       reference absorption matrix. The Java port currently lacks a
 *       D0Interpolator equivalent (the absorption matrix is not loaded into
 *       memory), so this test is deferred to Phase 5g.5.</li>
 *   <li>{@code testConsistencyWithHagan} — compares NoArbSabrSmileSection to
 *       SabrSmileSection across vanilla price, digital price, and density.
 *       The Java {@link org.jquantlib.experimental.volatility.NoArbSabrModel}
 *       {@code optionPrice}, {@code digitalOptionPrice}, and {@code density}
 *       methods are Phase 4f stubs that throw {@code UnsupportedOperationException};
 *       this test is deferred to Phase 5g.5 once the model body lands.</li>
 * </ol>
 *
 * <p>Existing {@link NoArbSabrInterpolationTest} and
 * {@link ZabrAndNoArbSabrSmokeTest} cover the available Phase 4f scaffolding
 * (parameter validation, Hagan-fallback volatility surface).
 */
public class NoArbSabrTest {

    public NoArbSabrTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    @Ignore("Phase 5g.5 — D0Interpolator absorption-matrix loader not ported. "
            + "Requires the 16-point reference absorption matrix to be loaded "
            + "into memory. C++ noarbsabr.cpp lines 32-73.")
    public void testAbsorptionMatrix() {
        // Deferred. Faithful body would call:
        //   checkD0(sigmaI, beta, rho, nu, tau, expectedAbsorptions)
        // for the 16 corner+interior cases of Doust's absorption table,
        // verifying d() * NoArbSabrModel::nsim ≈ expectedAbsorptions to
        // tolerance 0.1 absolute.
    }

    @Test
    @Ignore("Phase 5g.5 — NoArbSabrModel.optionPrice/digitalOptionPrice/density "
            + "are Phase 4f stubs that throw. Test compares vanilla, digital, "
            + "and density vs Sabr Hagan baseline. C++ noarbsabr.cpp lines 75-121.")
    public void testConsistencyWithHagan() {
        // Deferred. Faithful body would:
        //   - Build SabrSmileSection(tau=1, f=0.0488, alpha=0.026, beta=0.5, nu=0.4, rho=-0.1)
        //   - Build NoArbSabrSmileSection with the same parameters
        //   - Verify model.absorptionProbability ∈ [0, 1e-10]
        //   - For strike ∈ [1e-4, 0.15] step 1e-4 verify
        //       |sabr.optionPrice - noarb.optionPrice| < 1e-5
        //       |sabr.digitalOptionPrice - noarb.digitalOptionPrice| < 1e-3
        //       |sabr.density - noarb.density| < 1.0
    }
}
