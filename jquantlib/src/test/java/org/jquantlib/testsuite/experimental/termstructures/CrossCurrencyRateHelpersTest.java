/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.termstructures;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/crosscurrencyratehelpers.cpp}
 * v1.42.1 (755 LOC, 16 test cases).
 *
 * <p>Java already has the cross-currency helper class hierarchy
 * ({@link
 * org.jquantlib.experimental.termstructures.ConstNotionalCrossCurrencyBasisSwapRateHelper},
 * {@link
 * org.jquantlib.experimental.termstructures.MtMCrossCurrencyBasisSwapRateHelper},
 * {@link
 * org.jquantlib.experimental.termstructures.CrossCurrencyBasisSwapRateHelperBase}),
 * but no test-suite harness exists for them.
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong> — the C++
 * test fixture builds a complete two-currency multi-curve
 * environment (USD/EUR style) and bootstraps the foreign basis curve
 * across many collateral / basis-leg permutations.  Building the
 * fixture in Java requires a JoinedDiscountCurve helper plus
 * cross-validated bootstrap reference values.
 *
 * <ul>
 *   <li>{@code testConstNotionalBasisSwapsWithCollateralInQuoteAndBasisInBaseCcy}</li>
 *   <li>{@code testConstNotionalBasisSwapsWithCollateralInBaseAndBasisInQuoteCcy}</li>
 *   <li>{@code testConstNotionalBasisSwapsWithCollateralAndBasisInBaseCcy}</li>
 *   <li>{@code testConstNotionalBasisSwapsWithCollateralAndBasisInQuoteCcy}</li>
 *   <li>{@code testResettingBasisSwapsWithCollateralInQuoteAndBasisInBaseCcy}</li>
 *   <li>{@code testResettingBasisSwapsWithCollateralInBaseAndBasisInQuoteCcy}</li>
 *   <li>{@code testResettingBasisSwapsWithCollateralAndBasisInBaseCcy}</li>
 *   <li>{@code testResettingBasisSwapsWithCollateralAndBasisInQuoteCcy}</li>
 *   <li>{@code testResettingBasisSwapsWithArbitraryFreq}</li>
 *   <li>{@code testResettingBasisSwapsWithPaymentLag}</li>
 *   <li>{@code testResettingBasisSwapsWithOvernightIndex}</li>
 *   <li>{@code testResettingBasisSwapsWithOvernightIndexException}</li>
 *   <li>{@code testExceptionWhenInstrumentTenorShorterThanIndexFrequency}</li>
 *   <li>{@code testConstNotionalCrossCurrencySwapRateHelperRelinking}</li>
 *   <li>{@code testConstNotionalHelperCollateralOnFixedLeg}</li>
 *   <li>{@code testConstNotionalHelperCollateralOnFloatingLeg}</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/crosscurrencyratehelpers.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class CrossCurrencyRateHelpersTest {

    @Ignore("Phase 5f.5 — needs cross-validated XCcy bootstrap reference values")
    @Test
    public void testConstNotionalBasisSwapsWithCollateralInQuoteAndBasisInBaseCcy() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — needs cross-validated XCcy bootstrap reference values")
    @Test
    public void testConstNotionalBasisSwapsWithCollateralInBaseAndBasisInQuoteCcy() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — needs cross-validated XCcy bootstrap reference values")
    @Test
    public void testConstNotionalBasisSwapsWithCollateralAndBasisInBaseCcy() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — needs cross-validated XCcy bootstrap reference values")
    @Test
    public void testConstNotionalBasisSwapsWithCollateralAndBasisInQuoteCcy() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — needs MtM XCcy bootstrap reference values")
    @Test
    public void testResettingBasisSwapsWithCollateralInQuoteAndBasisInBaseCcy() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — needs MtM XCcy bootstrap reference values")
    @Test
    public void testResettingBasisSwapsWithCollateralInBaseAndBasisInQuoteCcy() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — needs MtM XCcy bootstrap reference values")
    @Test
    public void testResettingBasisSwapsWithCollateralAndBasisInBaseCcy() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — needs MtM XCcy bootstrap reference values")
    @Test
    public void testResettingBasisSwapsWithCollateralAndBasisInQuoteCcy() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — arbitrary-frequency MtM helper not ported")
    @Test
    public void testResettingBasisSwapsWithArbitraryFreq() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — payment-lag MtM helper variant not ported")
    @Test
    public void testResettingBasisSwapsWithPaymentLag() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — overnight-index MtM XCcy helper not ported")
    @Test
    public void testResettingBasisSwapsWithOvernightIndex() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — overnight-index error path not ported")
    @Test
    public void testResettingBasisSwapsWithOvernightIndexException() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — tenor < index frequency error path not ported")
    @Test
    public void testExceptionWhenInstrumentTenorShorterThanIndexFrequency() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — handle relinking observability not ported")
    @Test
    public void testConstNotionalCrossCurrencySwapRateHelperRelinking() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — collateral-on-fixed-leg variant not ported")
    @Test
    public void testConstNotionalHelperCollateralOnFixedLeg() {
        fail("not implemented");
    }

    @Ignore("Phase 5f.5 — collateral-on-floating-leg variant not ported")
    @Test
    public void testConstNotionalHelperCollateralOnFloatingLeg() {
        fail("not implemented");
    }
}
