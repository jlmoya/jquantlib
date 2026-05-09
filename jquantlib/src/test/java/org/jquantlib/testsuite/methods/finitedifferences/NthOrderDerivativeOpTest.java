/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5j skeleton port of {@code test-suite/nthorderderivativeop.cpp}
 * v1.42.1.
 *
 * <p><strong>All 15 cases deferred to Phase 5j.5</strong> — the test file
 * exercises {@code NthOrderDerivativeOp} which is NOT yet ported to Java
 * (Phase 4n.5 carry-forward).  The class implements high-order
 * (5/7/9-point) finite-difference operators on uniform and non-uniform
 * grids and is used for Richardson-extrapolated Heston pricing
 * (testHigherOrderHestonOptionPricing).
 *
 * <p>This skeleton documents the C++ test surface so that Phase 5j.5 can
 * pick it up directly — each {@code @Ignore} method matches the C++
 * {@code BOOST_AUTO_TEST_CASE} name verbatim.
 *
 * <p>Source: {@code test-suite/nthorderderivativeop.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class NthOrderDerivativeOpTest {

    private static final String REASON =
            "Phase 5j.5 — requires NthOrderDerivativeOp (Phase 4n.5 carry-forward, not yet ported)";

    @Ignore(REASON)
    @Test
    public void testSparseMatrixApply() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFirstOrder2PointsApply() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFirstOrder3PointsOnUniformGrid() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFirstOrder5PointsOnUniformGrid() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFirstOrder2PointsOnUniformGrid() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFirstOrder4PointsOnUniformGrid() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFirstOrder2PointsOn2DimUniformGrid() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testSecondOrder3PointsNonUniformGrid() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testSecondOrder4PointsNonUniformGrid() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testThirdOrder4PointsUniformGrid() { fail("not implemented"); }

    @Ignore(REASON + " + needs FdHestonVanillaEngine + Richardson extrapolation")
    @Test
    public void testHigherOrderHestonOptionPricing() { fail("not implemented"); }

    @Ignore(REASON + " + needs FdHestonVanillaEngine + Richardson extrapolation")
    @Test
    public void testHigherOrderAndRichardsonExtrapolation() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testCompareFirstDerivativeOpNonUniformGrid() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testCompareFirstDerivativeOp2dUniformGrid() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testMixedSecondOrder9PointsOnUniformGrid() { fail("not implemented"); }
}
