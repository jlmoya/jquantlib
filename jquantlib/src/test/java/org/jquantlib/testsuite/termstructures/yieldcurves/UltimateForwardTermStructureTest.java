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
 * Phase 5f skeleton port of {@code test-suite/ultimateforwardtermstructure.cpp}
 * v1.42.1 (340 LOC, 7 test cases).
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong> — the regulatory
 * Solvency-II {@code UltimateForwardTermStructure} (UFR) curve overlay
 * (Smith-Wilson interpolation between a base liquid yield curve and
 * an ultimate forward rate beyond the last liquid point) is not yet
 * ported to Java.
 *
 * <ul>
 *   <li>{@code testDutchCentralBankRates} — replicates DNB-published
 *       UFR-curve outputs at known dates.</li>
 *   <li>{@code testDutchCentralBankRatesWithRounding} — DNB outputs
 *       with their published rounding convention.</li>
 *   <li>{@code testDutchCentralBankRatesWithRoundingAndContinuousCompounding}
 *       — same with continuous compounding.</li>
 *   <li>{@code testExtrapolatedForward} — extrapolation past last
 *       smoothing point converges to the UFR.</li>
 *   <li>{@code testZeroRateAtFirstSmoothingPoint} — zero rate at
 *       FSP equals base curve's zero rate.</li>
 *   <li>{@code testThatInspectorsEqualToBaseCurve} — inspectors
 *       (referenceDate, calendar, etc.) match base curve.</li>
 *   <li>{@code testExceptionWhenFspLessOrEqualZero} — error path
 *       when first-smoothing-point time is non-positive.</li>
 *   <li>{@code testObservability} — UFR curve observability under
 *       base-curve and FSP-quote changes.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/ultimateforwardtermstructure.cpp}
 * v1.42.1 @ {@code 099987f0ca}.
 */
public class UltimateForwardTermStructureTest {

    @Ignore("Phase 5f.5 — UltimateForwardTermStructure not ported")
    @Test
    public void testDutchCentralBankRates() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — UltimateForwardTermStructure not ported")
    @Test
    public void testDutchCentralBankRatesWithRounding() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — UltimateForwardTermStructure not ported")
    @Test
    public void testDutchCentralBankRatesWithRoundingAndContinuousCompounding() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — UltimateForwardTermStructure not ported")
    @Test
    public void testExtrapolatedForward() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — UltimateForwardTermStructure not ported")
    @Test
    public void testZeroRateAtFirstSmoothingPoint() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — UltimateForwardTermStructure not ported")
    @Test
    public void testThatInspectorsEqualToBaseCurve() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — UltimateForwardTermStructure not ported")
    @Test
    public void testExceptionWhenFspLessOrEqualZero() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — UltimateForwardTermStructure observability not ported")
    @Test
    public void testObservability() { fail("not implemented"); }
}
