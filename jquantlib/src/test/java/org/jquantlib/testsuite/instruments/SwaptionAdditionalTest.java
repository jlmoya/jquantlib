/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/swaption.cpp} v1.42.1
 * (1,197 LOC, 12 test cases).
 *
 * <p>Java already has direct Swaption-engine coverage in
 * {@code testsuite.pricingengines.swaption.*} and the
 * {@code instruments.NonstandardSwaptionTest} / {@code FloatFloatSwaptionTest}
 * classes; the C++ {@code swaption.cpp} suite cross-cuts engines and
 * the {@link org.jquantlib.instruments.Swaption} instrument itself
 * (caching, vega, cash-settled, implied vol, delta, MakeSwaption builder).
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong> — these tests
 * exercise:
 * <ul>
 *   <li>{@code MakeSwaption} fluent builder (not yet ported)</li>
 *   <li>OIS swaption engine (Java OIS infra is partial — Phase 5e dep)</li>
 *   <li>Cash-settled / collateralised swaption variants
 *       (Settlement::CashSettled methods)</li>
 *   <li>Bachelier / normal-vol swaption delta (Phase 5g vol-infra prereq)</li>
 * </ul>
 *
 * <p>Cases (mirroring C++ {@code BOOST_AUTO_TEST_CASE} order):
 * <ul>
 *   <li>{@code testStrikeDependency}</li>
 *   <li>{@code testSpreadDependency}</li>
 *   <li>{@code testSpreadTreatment}</li>
 *   <li>{@code testCachedValue}</li>
 *   <li>{@code testVega}</li>
 *   <li>{@code testCashSettledSwaptions}</li>
 *   <li>{@code testImpliedVolatility}</li>
 *   <li>{@code testImpliedVolatilityOis}</li>
 *   <li>{@code testSwaptionDeltaInBlackModel}</li>
 *   <li>{@code testSwaptionDeltaInBachelierModel}</li>
 *   <li>{@code testMakeSwaptionWithExerciseCalendar}</li>
 *   <li>{@code testBlackEngineCaching}</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/swaption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class SwaptionAdditionalTest {

    @Ignore("Phase 5f.5 — MakeSwaption builder + Settlement variants not ported")
    @Test
    public void testStrikeDependency() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — MakeSwaption builder + Settlement variants not ported")
    @Test
    public void testSpreadDependency() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — MakeSwaption builder + Settlement variants not ported")
    @Test
    public void testSpreadTreatment() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — MakeSwaption builder + cached engine values not aligned")
    @Test
    public void testCachedValue() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Vega NPV-derivative not aligned across MakeSwaption")
    @Test
    public void testVega() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Cash-settled swaption pricer (CashSettled) not ported")
    @Test
    public void testCashSettledSwaptions() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Swaption.impliedVolatility convenience not aligned")
    @Test
    public void testImpliedVolatility() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — OIS swaption (Phase 5e overnight-swap dep)")
    @Test
    public void testImpliedVolatilityOis() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Black swaption delta (Phase 5g BlackCalculator dep)")
    @Test
    public void testSwaptionDeltaInBlackModel() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Bachelier swaption delta (Phase 5g vol-infra dep)")
    @Test
    public void testSwaptionDeltaInBachelierModel() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — MakeSwaption.withExerciseDateCalendar not ported")
    @Test
    public void testMakeSwaptionWithExerciseCalendar() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — BlackSwaptionEngine caching semantics not aligned")
    @Test
    public void testBlackEngineCaching() { fail("not implemented"); }
}
