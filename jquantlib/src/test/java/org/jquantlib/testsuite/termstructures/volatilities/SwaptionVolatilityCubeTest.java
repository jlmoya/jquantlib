/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.termstructures.volatilities;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/swaptionvolatilitycube.cpp}
 * v1.42.1 (1,054 LOC, 15 test cases).
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong> — Java
 * {@code termstructures.volatilities.swaption.SwaptionVolatilityCube} family
 * is largely absent (only base {@link
 * org.jquantlib.termstructures.SwaptionVolatilityStructure} present).
 * SABR and Z-ABR cube types are not yet ported (Phase 4f experimental
 * SABR/ZABR is a partial prereq — see META design D6).
 *
 * <ul>
 *   <li>{@code testAtmVols} — sparse ATM cube fitting</li>
 *   <li>{@code testSmile} — smile interpolation across strikes</li>
 *   <li>{@code testSabrVols} — SABR cube fit</li>
 *   <li>{@code testSabrNormalVolatility} — normal SABR cube</li>
 *   <li>{@code testSpreadedCube} — spread overlay onto base cube</li>
 *   <li>{@code testObservability} — cube observability under quote changes</li>
 *   <li>{@code testSabrParameters} — SABR alpha/beta/nu/rho exposure</li>
 *   <li>{@code testZabrVols} — ZABR cube fit (Phase 4f experimental)</li>
 *   <li>{@code testZabrSmileSection} — ZABR smile section construction</li>
 *   <li>{@code testZabrParameters} — ZABR parameter exposure</li>
 *   <li>{@code testZabrWithNonUnitGamma} — gamma != 1 ZABR variant</li>
 *   <li>{@code testZabrWithFreeGamma} — free-gamma ZABR variant</li>
 *   <li>{@code testZabrShiftedVolThrows} — shifted-vol ZABR error path</li>
 *   <li>{@code testZabrAlternativeKernel} — kernel-variant ZABR</li>
 *   <li>{@code testZabrObservability} — ZABR cube observability</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/swaptionvolatilitycube.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class SwaptionVolatilityCubeTest {

    @Ignore("Phase 5f.5 — SwaptionVolatilityCubeBySabr not ported")
    @Test
    public void testAtmVols() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — SwaptionVolatilityCubeBySabr not ported")
    @Test
    public void testSmile() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — SwaptionVolatilityCubeBySabr not ported")
    @Test
    public void testSabrVols() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Normal-vol SABR cube not ported")
    @Test
    public void testSabrNormalVolatility() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — SpreadedSwaptionVolatility cube not ported")
    @Test
    public void testSpreadedCube() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — SwaptionVolatilityCube observability not ported")
    @Test
    public void testObservability() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — SABR cube parameter exposure not ported")
    @Test
    public void testSabrParameters() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — ZABR cube (Phase 4f experimental) not ported")
    @Test
    public void testZabrVols() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — ZABR smile section not ported")
    @Test
    public void testZabrSmileSection() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — ZABR parameter exposure not ported")
    @Test
    public void testZabrParameters() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — ZABR non-unit gamma not ported")
    @Test
    public void testZabrWithNonUnitGamma() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — ZABR free gamma not ported")
    @Test
    public void testZabrWithFreeGamma() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — ZABR shifted-vol error path not ported")
    @Test
    public void testZabrShiftedVolThrows() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — ZABR alternative kernel not ported")
    @Test
    public void testZabrAlternativeKernel() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — ZABR cube observability not ported")
    @Test
    public void testZabrObservability() { fail("not implemented"); }
}
