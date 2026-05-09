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
 Copyright (C) 2025 Paolo D'Elia

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.termstructures.volatilities;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/interpolatedsmilesection.cpp (Phase 5g).
 *
 * <p>The C++ file has five test cases exercising
 * {@code InterpolatedSmileSection<Linear>}:
 * <ol>
 *   <li>{@code testInterpolationAndVariance} — vol/variance at interior strike.</li>
 *   <li>{@code testExtrapolationWhenAllowed} — linear extrapolation outside [min,max].</li>
 *   <li>{@code testHandlesUpdatePropagates} — quote-handle observability.</li>
 *   <li>{@code testFlatStrikeExtrapolation} — flat (constant) extrapolation.</li>
 *   <li>{@code testErrorThrowingWhenNonSortedStrikes} — sortedness validation.</li>
 * </ol>
 *
 * <p><b>Phase 5g.5 deferral:</b> JQuantLib has
 * {@link org.jquantlib.termstructures.volatilities.SabrInterpolatedSmileSection}
 * but no generic {@code InterpolatedSmileSection<Interpolation>} class.
 * Faithful port deferred until that class is added (it is a prerequisite
 * for Phase 5f swaption vol cube tests as well — design concern D6).
 */
public class InterpolatedSmileSectionTest {

    public InterpolatedSmileSectionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    @Ignore("Phase 5g.5 — generic InterpolatedSmileSection<Linear> class not "
            + "present in JQuantLib (only SabrInterpolatedSmileSection exists). "
            + "C++ interpolatedsmilesection.cpp lines 43-73.")
    public void testInterpolationAndVariance() { }

    @Test
    @Ignore("Phase 5g.5 — see testInterpolationAndVariance. "
            + "C++ interpolatedsmilesection.cpp lines 75-107.")
    public void testExtrapolationWhenAllowed() { }

    @Test
    @Ignore("Phase 5g.5 — see testInterpolationAndVariance. "
            + "C++ interpolatedsmilesection.cpp lines 109-147.")
    public void testHandlesUpdatePropagates() { }

    @Test
    @Ignore("Phase 5g.5 — see testInterpolationAndVariance. "
            + "C++ interpolatedsmilesection.cpp lines 149-190.")
    public void testFlatStrikeExtrapolation() { }

    @Test
    @Ignore("Phase 5g.5 — see testInterpolationAndVariance. "
            + "C++ interpolatedsmilesection.cpp lines 192-211.")
    public void testErrorThrowingWhenNonSortedStrikes() { }
}
