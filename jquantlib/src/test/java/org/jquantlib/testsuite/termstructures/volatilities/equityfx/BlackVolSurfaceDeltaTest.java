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
 Copyright (C) 2019 Quaternion Risk Management Ltd
 Copyright (C) 2020 Skandinaviska Enskilda Banken AB (publ)
 Copyright (C) 2025 Paolo D'Elia

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.termstructures.volatilities.equityfx;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/blackvolsurfacedelta.cpp (Phase 5g).
 *
 * <p>The C++ file has four test cases:
 * <ol>
 *   <li>{@code testBlackVolSurfaceDeltaConstantVol} — constant-vol surface
 *       across delta and tenor.</li>
 *   <li>{@code testBlackVolSurfaceDeltaNonConstantVol} — non-constant
 *       2x2 surface at corner deltas.</li>
 *   <li>{@code testTimeExtrapolation} — flat-vol time extrapolation.</li>
 *   <li>{@code testSmileInterpolation} — smile interpolation across deltas.</li>
 * </ol>
 *
 * <p><b>Phase 5g.5 deferral:</b> JQuantLib does not have a
 * {@code BlackVolatilitySurfaceDelta} production class. The
 * v1.42.1 C++ class lives at
 * {@code ql/termstructures/volatility/equityfx/blackvolsurfacedelta.hpp}
 * and accepts a (date, putDeltas, callDeltas, hasAtm, blackVolMatrix,
 * dayCounter, calendar, spotHandle, domesticTS, foreignTS) constructor.
 * Faithful port deferred until that class is added.
 *
 * <p>Cross-reference: design concern D7 (Phase 5g) calls out
 * {@code BlackDeltaCalculator} from {@code experimental/fx/} as the
 * delta-convention implementation that this surface depends on.
 */
public class BlackVolSurfaceDeltaTest {

    public BlackVolSurfaceDeltaTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    @Ignore("Phase 5g.5 — BlackVolatilitySurfaceDelta class not present in "
            + "JQuantLib. C++ blackvolsurfacedelta.cpp lines 38-74.")
    public void testBlackVolSurfaceDeltaConstantVol() { }

    @Test
    @Ignore("Phase 5g.5 — see testBlackVolSurfaceDeltaConstantVol. "
            + "C++ blackvolsurfacedelta.cpp lines 76+.")
    public void testBlackVolSurfaceDeltaNonConstantVol() { }

    @Test
    @Ignore("Phase 5g.5 — see testBlackVolSurfaceDeltaConstantVol.")
    public void testTimeExtrapolation() { }

    @Test
    @Ignore("Phase 5g.5 — see testBlackVolSurfaceDeltaConstantVol.")
    public void testSmileInterpolation() { }
}
