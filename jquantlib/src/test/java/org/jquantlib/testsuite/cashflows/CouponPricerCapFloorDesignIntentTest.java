/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.cashflow.ArithmeticAveragedOvernightIndexedCouponPricer;
import org.jquantlib.cashflow.AveragingMultipleResetsPricer;
import org.jquantlib.cashflow.CompoundingMultipleResetsPricer;
import org.jquantlib.cashflow.CompoundingOvernightIndexedCouponPricer;
import org.jquantlib.cashflow.MultipleResetsPricer;
import org.jquantlib.cashflow.RangeAccrualPricer;
import org.jquantlib.lang.exceptions.LibraryException;
import org.junit.Test;

/**
 * Design-intent contract test for the four cashflow pricers whose
 * cap / floor / swapletPrice operations are intentionally unimplemented per
 * C++ QuantLib v1.42.1.
 *
 * <p>Mirrors the {@code QL_FAIL("...not implemented" / "not available")} stubs
 * in:
 * <ul>
 *   <li>{@code ql/cashflows/overnightindexedcouponpricer.hpp:113-117} —
 *       {@code CompoundingOvernightIndexedCouponPricer}</li>
 *   <li>{@code ql/cashflows/overnightindexedcouponpricer.hpp:155-159} —
 *       {@code ArithmeticAveragedOvernightIndexedCouponPricer}</li>
 *   <li>{@code ql/cashflows/multipleresetscoupon.cpp:102-120} —
 *       {@code MultipleResetsPricer}</li>
 *   <li>{@code ql/cashflows/rangeaccrual.cpp:154-168} —
 *       {@code RangeAccrualPricer}</li>
 * </ul>
 *
 * <p>The intent: these are aggregate-rate / digital-range pricers; per-fixing
 * caplet and floorlet pricing is delegated to dedicated Black-formula pricers
 * (e.g. {@link org.jquantlib.cashflow.BlackOvernightIndexedCouponPricer}).
 * This test pins the contract so future contributors can see the rationale.
 *
 * @author JQuantLib migration team (Phase 3-C)
 */
public class CouponPricerCapFloorDesignIntentTest {

    public CouponPricerCapFloorDesignIntentTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // ── CompoundingOvernightIndexedCouponPricer ──────────────────────────────

    @Test
    public void testCompoundingOvernightSwapletPriceThrows() {
        final var p = new CompoundingOvernightIndexedCouponPricer();
        assertThrowsLibrary(p::swapletPrice, "CompoundingOvernightIndexedCouponPricer", "swapletPrice");
    }

    @Test
    public void testCompoundingOvernightCapletPriceThrows() {
        final var p = new CompoundingOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.capletPrice(0.01), "CompoundingOvernightIndexedCouponPricer", "capletPrice");
    }

    @Test
    public void testCompoundingOvernightCapletRateThrows() {
        final var p = new CompoundingOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.capletRate(0.01), "CompoundingOvernightIndexedCouponPricer", "capletRate");
    }

    @Test
    public void testCompoundingOvernightFloorletPriceThrows() {
        final var p = new CompoundingOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.floorletPrice(0.01), "CompoundingOvernightIndexedCouponPricer", "floorletPrice");
    }

    @Test
    public void testCompoundingOvernightFloorletRateThrows() {
        final var p = new CompoundingOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.floorletRate(0.01), "CompoundingOvernightIndexedCouponPricer", "floorletRate");
    }

    @Test
    public void testCompoundingOvernightCapletRateBoolThrows() {
        final var p = new CompoundingOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.capletRate(0.01, true), "CompoundingOvernightIndexedCouponPricer", "capletRate");
    }

    @Test
    public void testCompoundingOvernightFloorletRateBoolThrows() {
        final var p = new CompoundingOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.floorletRate(0.01, true), "CompoundingOvernightIndexedCouponPricer", "floorletRate");
    }

    // ── ArithmeticAveragedOvernightIndexedCouponPricer ───────────────────────

    @Test
    public void testArithmeticAveragedSwapletPriceThrows() {
        final var p = new ArithmeticAveragedOvernightIndexedCouponPricer();
        assertThrowsLibrary(p::swapletPrice, "ArithmeticAveragedOvernightIndexedCouponPricer", "swapletPrice");
    }

    @Test
    public void testArithmeticAveragedCapletPriceThrows() {
        final var p = new ArithmeticAveragedOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.capletPrice(0.01), "ArithmeticAveragedOvernightIndexedCouponPricer",
                "capletPrice");
    }

    @Test
    public void testArithmeticAveragedCapletRateThrows() {
        final var p = new ArithmeticAveragedOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.capletRate(0.01), "ArithmeticAveragedOvernightIndexedCouponPricer", "capletRate");
    }

    @Test
    public void testArithmeticAveragedFloorletPriceThrows() {
        final var p = new ArithmeticAveragedOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.floorletPrice(0.01), "ArithmeticAveragedOvernightIndexedCouponPricer",
                "floorletPrice");
    }

    @Test
    public void testArithmeticAveragedFloorletRateThrows() {
        final var p = new ArithmeticAveragedOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.floorletRate(0.01), "ArithmeticAveragedOvernightIndexedCouponPricer",
                "floorletRate");
    }

    @Test
    public void testArithmeticAveragedCapletRateBoolThrows() {
        final var p = new ArithmeticAveragedOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.capletRate(0.01, false),
                "ArithmeticAveragedOvernightIndexedCouponPricer", "capletRate");
    }

    @Test
    public void testArithmeticAveragedFloorletRateBoolThrows() {
        final var p = new ArithmeticAveragedOvernightIndexedCouponPricer();
        assertThrowsLibrary(() -> p.floorletRate(0.01, false),
                "ArithmeticAveragedOvernightIndexedCouponPricer", "floorletRate");
    }

    // ── MultipleResetsPricer (via concrete subclasses) ───────────────────────

    @Test
    public void testAveragingMultipleResetsSwapletPriceThrows() {
        final MultipleResetsPricer p = new AveragingMultipleResetsPricer();
        assertThrowsLibrary(p::swapletPrice, "MultipleResetsPricer", "swapletPrice");
    }

    @Test
    public void testAveragingMultipleResetsCapletPriceThrows() {
        final MultipleResetsPricer p = new AveragingMultipleResetsPricer();
        assertThrowsLibrary(() -> p.capletPrice(0.01), "MultipleResetsPricer", "capletPrice");
    }

    @Test
    public void testAveragingMultipleResetsCapletRateThrows() {
        final MultipleResetsPricer p = new AveragingMultipleResetsPricer();
        assertThrowsLibrary(() -> p.capletRate(0.01), "MultipleResetsPricer", "capletRate");
    }

    @Test
    public void testAveragingMultipleResetsFloorletPriceThrows() {
        final MultipleResetsPricer p = new AveragingMultipleResetsPricer();
        assertThrowsLibrary(() -> p.floorletPrice(0.01), "MultipleResetsPricer", "floorletPrice");
    }

    @Test
    public void testAveragingMultipleResetsFloorletRateThrows() {
        final MultipleResetsPricer p = new AveragingMultipleResetsPricer();
        assertThrowsLibrary(() -> p.floorletRate(0.01), "MultipleResetsPricer", "floorletRate");
    }

    @Test
    public void testCompoundingMultipleResetsSwapletPriceThrows() {
        final MultipleResetsPricer p = new CompoundingMultipleResetsPricer();
        assertThrowsLibrary(p::swapletPrice, "MultipleResetsPricer", "swapletPrice");
    }

    @Test
    public void testCompoundingMultipleResetsCapletRateThrows() {
        final MultipleResetsPricer p = new CompoundingMultipleResetsPricer();
        assertThrowsLibrary(() -> p.capletRate(0.01), "MultipleResetsPricer", "capletRate");
    }

    // ── RangeAccrualPricer ───────────────────────────────────────────────────

    @Test
    public void testRangeAccrualCapletPriceThrows() {
        final RangeAccrualPricer p = makeRangeAccrualPricerStub();
        assertThrowsLibrary(() -> p.capletPrice(0.01), "RangeAccrualPricer", "capletPrice");
    }

    @Test
    public void testRangeAccrualCapletRateThrows() {
        final RangeAccrualPricer p = makeRangeAccrualPricerStub();
        assertThrowsLibrary(() -> p.capletRate(0.01), "RangeAccrualPricer", "capletRate");
    }

    @Test
    public void testRangeAccrualFloorletPriceThrows() {
        final RangeAccrualPricer p = makeRangeAccrualPricerStub();
        assertThrowsLibrary(() -> p.floorletPrice(0.01), "RangeAccrualPricer", "floorletPrice");
    }

    @Test
    public void testRangeAccrualFloorletRateThrows() {
        final RangeAccrualPricer p = makeRangeAccrualPricerStub();
        assertThrowsLibrary(() -> p.floorletRate(0.01), "RangeAccrualPricer", "floorletRate");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Anonymous concrete RangeAccrualPricer — swapletPrice is abstract on base. */
    private static RangeAccrualPricer makeRangeAccrualPricerStub() {
        final RangeAccrualPricer p = new RangeAccrualPricer() {
            @Override
            public double swapletPrice() {
                return 0.0;
            }
        };
        assertNotNull(p);
        return p;
    }

    private static void assertThrowsLibrary(final Runnable action, final String className,
            final String methodName) {
        try {
            action.run();
            fail("expected LibraryException from " + className + "::" + methodName);
        } catch ( final LibraryException ex ) {
            // Rationale must reference the class + method (so future readers see
            // why the throw exists, not just that one exists).
            final String msg = ex.getMessage();
            assertNotNull("LibraryException must carry a rationale message", msg);
            assertTrue(className + "::" + methodName + " rationale missing classname; got: " + msg,
                    msg.contains(className));
            assertTrue(className + "::" + methodName + " rationale missing methodname; got: " + msg,
                    msg.contains(methodName));
        }
    }
}
