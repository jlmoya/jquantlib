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
 Copyright (C) 2026 contributors

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.termstructures.volatilities.equityfx;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/piecewiseblackvariancesurface.cpp (Phase 5g).
 *
 * <p>The C++ file contains 16 test cases exercising the
 * {@code PiecewiseBlackVarianceSurface} term structure: exact repricing,
 * interpolation, vol derivation, extrapolation, observer behavior, strike
 * dependence, multi-tenor smile, MakeFromGrid factory, accessors, zero-time
 * variance, single-tenor surface, ragged strike grids, single-section
 * constructor, SABR equivalence, SVI smile section, and local-vol FD
 * pricing from SABR smiles (1,111 LOC total).
 *
 * <p><b>Phase 5g.5 deferral:</b> JQuantLib does not have a
 * {@code PiecewiseBlackVarianceSurface} production class. The
 * v1.42.1 C++ class lives at
 * {@code ql/termstructures/volatility/equityfx/piecewiseblackvariancesurface.hpp}.
 * Faithful port deferred until that class is added (it depends on
 * {@code InterpolatedSmileSection} which is also missing — see
 * {@link InterpolatedSmileSectionTest}).
 */
public class PiecewiseBlackVarianceSurfaceTest {

    public PiecewiseBlackVarianceSurfaceTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    @Ignore("Phase 5g.5 — PiecewiseBlackVarianceSurface class not present in "
            + "JQuantLib (depends on InterpolatedSmileSection which also "
            + "missing). C++ piecewiseblackvariancesurface.cpp testExactRepricing.")
    public void testExactRepricing() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testInterpolation() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testBlackVolDerivation() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testExtrapolation() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testObserver() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testStrikeDependence() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testMultiTenorSmileInterpolation() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testMakeFromGrid() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testConstructorValidation() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testMakeFromGridValidation() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testAccessors() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testZeroTimeVariance() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testSingleTenorSurface() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testRaggedStrikeGrids() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testSingleSectionConstructor() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testSabrEquivalence() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testSviSmileSection() { }

    @Test
    @Ignore("Phase 5g.5 — see testExactRepricing.")
    public void testLocalVolFdPricingFromSabrSmiles() { }
}
