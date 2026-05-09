/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.termstructures.yieldcurves;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/basisswapratehelpers.cpp}
 * v1.42.1 (240 LOC, 4 test cases).
 *
 * <p>Java has the {@code experimental.termstructures} family
 * ({@link org.jquantlib.experimental.termstructures.IborIborBasisSwapRateHelper},
 * {@link org.jquantlib.experimental.termstructures.OvernightIborBasisSwapRateHelper})
 * and existing test {@code IborIborBasisSwapRateHelperTest}; the
 * v1.42.1 production {@code basisswapratehelpers.cpp} suite goes
 * further by exercising both base-curve and projected-curve
 * bootstrap permutations together with explicit / implicit discount
 * curve selection.
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong>:
 * <ul>
 *   <li>{@code testIborIborBaseCurveBootstrap} — bootstrap of the
 *       base IBOR curve with basis-swap quotes.</li>
 *   <li>{@code testIborIborOtherCurveBootstrap} — bootstrap of the
 *       other-leg IBOR curve.</li>
 *   <li>{@code testOvernightIborBootstrapWithoutDiscountCurve} —
 *       overnight-vs-IBOR basis bootstrap, single-curve.</li>
 *   <li>{@code testOvernightIborBootstrapWithDiscountCurve} — same,
 *       multi-curve with explicit discount curve handle.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/basisswapratehelpers.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class BasisSwapRateHelpersTest {

    @Ignore("Phase 5f.5 — IBOR-vs-IBOR base-curve bootstrap harness")
    @Test
    public void testIborIborBaseCurveBootstrap() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — IBOR-vs-IBOR other-curve bootstrap harness")
    @Test
    public void testIborIborOtherCurveBootstrap() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — overnight-vs-IBOR single-curve bootstrap harness")
    @Test
    public void testOvernightIborBootstrapWithoutDiscountCurve() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — overnight-vs-IBOR multi-curve bootstrap harness")
    @Test
    public void testOvernightIborBootstrapWithDiscountCurve() {
        fail("not implemented");
    }
}
