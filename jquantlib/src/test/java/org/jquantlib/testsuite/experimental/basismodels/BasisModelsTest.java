/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.basismodels;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/basismodels.cpp} v1.42.1
 * (402 LOC, 4 test cases).
 *
 * <p>Java has partial coverage via {@code SwaptionCashFlowsTest} and
 * {@code TenorOptionletVTSTest} (Phase 4g); the v1.42.1
 * {@code basismodels.cpp} additionally exercises continuously- and
 * simply-compounded basis-spread variants of {@link
 * org.jquantlib.experimental.basismodels.SwaptionCashFlows} and the
 * tenor-swaption-VTS overlay.
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong>:
 * <ul>
 *   <li>{@code testSwaptioncfsContCompSpread} — continuous-compounding
 *       basis spread on swaption cash flows.</li>
 *   <li>{@code testSwaptioncfsSimpleCompSpread} — simple-compounding
 *       basis spread on swaption cash flows.</li>
 *   <li>{@code testTenoroptionletvts} — tenor-optionlet VTS overlay
 *       reproduces input vols at base tenor.</li>
 *   <li>{@code testTenorswaptionvts} — tenor-swaption VTS overlay
 *       reproduces input vols at base tenor.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/basismodels.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class BasisModelsTest {

    @Ignore("Phase 5f.5 — SwaptionCashFlows cont-comp spread variant")
    @Test
    public void testSwaptioncfsContCompSpread() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — SwaptionCashFlows simple-comp spread variant")
    @Test
    public void testSwaptioncfsSimpleCompSpread() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — TenorOptionletVTS overlay reproduce-input")
    @Test
    public void testTenoroptionletvts() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — TenorSwaptionVTS overlay (not yet ported)")
    @Test
    public void testTenorswaptionvts() { fail("not implemented"); }
}
