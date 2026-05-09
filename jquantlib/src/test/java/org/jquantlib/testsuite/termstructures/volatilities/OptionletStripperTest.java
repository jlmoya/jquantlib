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
 * Phase 5f skeleton port of {@code test-suite/optionletstripper.cpp} v1.42.1
 * (991 LOC, 8 test cases).
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong> — Java has only
 * the {@link
 * org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure}
 * abstract type and the {@link
 * org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility}
 * concrete; the stripper machinery
 * ({@code OptionletStripper1}, {@code OptionletStripper2}, {@code
 * StrippedOptionletAdapter}, {@code CapFloorTermVolCurve},
 * {@code CapFloorTermVolSurface}) is not yet ported.
 *
 * <ul>
 *   <li>{@code testFlatTermVolatilityStripping1} — flat-vol stripping</li>
 *   <li>{@code testTermVolatilityStripping1} — full surface stripping (LN)</li>
 *   <li>{@code testTermVolatilityStrippingNormalVol} — normal-vol</li>
 *   <li>{@code testTermVolatilityStrippingShiftedLogNormalVol} — SLN</li>
 *   <li>{@code testFlatTermVolatilityStripping2} — flat-vol stripper2</li>
 *   <li>{@code testTermVolatilityStripping2} — surface stripper2</li>
 *   <li>{@code testSwitchStrike} — switch-strike conversion</li>
 *   <li>{@code testTermVolatilityStripping1ON} — overnight-index variant</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/optionletstripper.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class OptionletStripperTest {

    @Ignore("Phase 5f.5 — OptionletStripper1 not ported")
    @Test
    public void testFlatTermVolatilityStripping1() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — OptionletStripper1 + CapFloorTermVolSurface not ported")
    @Test
    public void testTermVolatilityStripping1() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Normal-vol stripping not ported")
    @Test
    public void testTermVolatilityStrippingNormalVol() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Shifted-log-normal stripping not ported")
    @Test
    public void testTermVolatilityStrippingShiftedLogNormalVol() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — OptionletStripper2 not ported")
    @Test
    public void testFlatTermVolatilityStripping2() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — OptionletStripper2 + surface not ported")
    @Test
    public void testTermVolatilityStripping2() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — switch-strike conversion utility not ported")
    @Test
    public void testSwitchStrike() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — overnight-index optionlet stripping not ported")
    @Test
    public void testTermVolatilityStripping1ON() { fail("not implemented"); }
}
