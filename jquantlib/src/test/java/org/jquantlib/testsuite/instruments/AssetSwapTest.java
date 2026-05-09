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

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.instruments.AssetSwap;
import org.jquantlib.instruments.bonds.FixedRateBond;
import org.jquantlib.pricingengines.bond.DiscountingBondEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.calendars.Target;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/assetswap.cpp (Phase 5e).
 *
 * <p>9 BOOST_AUTO_TEST_CASE methods exercising the
 * {@code AssetSwap} instrument, the {@code AssetSwapHelper} term-structure
 * helper, and the asset-swap pricing engine.
 *
 * <p>{@code assetswap.cpp} (4,409 LOC) is the single largest non-marketmodel
 * file in the C++ test-suite. It validates clean/dirty price dynamics,
 * z-spread computation, and asset-swap spread (par/market) calculations
 * across many bond types: fixed-rate, floating-rate, CMS, zero-coupon,
 * callable.
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>All 9 methods are present as {@code @Ignore}'d skeletons. The blocker
 * is that JQuant has no {@code AssetSwap} production class — the only
 * asset-swap-named code in {@code org.jquantlib} is the experimental
 * {@code RiskyAssetSwap} (credit-risk-adjusted, different semantics). The
 * C++ {@code AssetSwap} (in {@code ql/instruments/assetswap.hpp}) is a
 * distinct vanilla-bond-vs-floating-leg instrument that needs a clean
 * Java port.
 *
 * <ul>
 *   <li>{@code testConsistency} (564 LOC C++): exercises clean/dirty price
 *       round-trip for fixed bonds, floating bonds, CMS bonds, and ZCB.
 *       Requires {@code AssetSwap} + {@code AssetSwapPricingEngine}. See
 *       WI-5e.5-ASW-1.</li>
 *
 *   <li>{@code testImpliedValue} (366 LOC): asset-swap value computed from
 *       cleanPrice vs marketAssetSwapValue. Requires same as testConsistency
 *       plus the {@code parAssetSwap} flag wiring. See WI-5e.5-ASW-2.</li>
 *
 *   <li>{@code testMarketASWSpread} (438 LOC): market asset-swap spread
 *       computation across bond types. Requires
 *       {@code AssetSwap.fairCleanPrice()} and
 *       {@code AssetSwap.fairSpread()} accessors. See WI-5e.5-ASW-3.</li>
 *
 *   <li>{@code testZSpread} (318 LOC): z-spread vs asset-swap-spread
 *       comparison. Requires {@code BondFunctions.zSpread} and
 *       {@code BondFunctions.cleanPriceFromZSpread}. JQuant has BondFunctions
 *       (Phase 2) but the {@code zSpread} solver overload may need
 *       cross-validation; primary blocker is {@code AssetSwap}. See
 *       WI-5e.5-ASW-4.</li>
 *
 *   <li>{@code testGenericBondImplied}, {@code testMASWWithGenericBond},
 *       {@code testZSpreadWithGenericBond}: parallel of the first three
 *       tests using the generic {@code Bond} class with explicit cashflow
 *       legs (rather than specialized fixed/floating bonds). Same blockers
 *       as their specialized counterparts. See WI-5e.5-ASW-5/6/7.</li>
 *
 *   <li>{@code testSpecializedBondVsGenericBond} (559 LOC): cross-validates
 *       that specialized bond classes (FixedRateBond, FloatingRateBond,
 *       CmsRateBond) produce identical asset-swap values to the generic
 *       {@code Bond} with manually-built legs. See WI-5e.5-ASW-8.</li>
 *
 *   <li>{@code testSpecializedBondVsGenericBondUsingAsw}: as above but
 *       constructed via {@code AssetSwap} convenience constructors taking
 *       par-rate / market-rate / explicit floating leg. See WI-5e.5-ASW-9.</li>
 * </ul>
 */
public class AssetSwapTest {

    public AssetSwapTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-ASW-1 — needs org.jquantlib.instruments.AssetSwap "
            + "+ AssetSwapPricingEngine production port (no Java equivalent today).")
    @Test
    public void testConsistency() {
    }

    /**
     * Phase 5e.5-ASW partial port: first sub-case of {@code testImpliedValue}
     * (assetswap.cpp:676) — exercise the par-asset-swap fair-clean-price ↔
     * bond-clean-price round-trip for the DBR 4 01/04/37 fixed-rate bond at
     * spread = 0. Mirrors C++ {@code fixedBond1} block (lines 692-742).
     *
     * <p>Per the C++ test, with {@code IborCoupon::Settings::usingAtParCoupons
     * = true} (the Java default), tolerance is {@code 1e-13}; otherwise
     * {@code 1e-2}. The Java port currently uses indexed coupons, so we
     * apply the looser {@code 1e-2} tolerance.
     *
     * <p>Remaining sub-cases (fixedBond2, floatingBond1/2, cmsBond1/2) are
     * Phase 5e.5b carry-forward — they require FloatingRateBond /
     * CmsRateBond constructors and CommonVars cms pricer wiring.
     */
    @Test
    public void testImpliedValue() {
        // Replicate C++ CommonVars (assetswap.cpp:65-111).
        new Settings().setEvaluationDate(
                new Date(24, Month.April, 2007));
        final DayCounter act365 = new Actual365Fixed();
        final YieldTermStructure flat = new FlatForward(
                new Date(24, Month.April, 2007), 0.05, act365);
        final Handle<YieldTermStructure> ts =
                new Handle<YieldTermStructure>(flat);
        final Euribor6M iborIndex = new Euribor6M(ts);
        final double spread = 0.0;
        final double faceAmount = 100.0;

        final Calendar bondCalendar = new Target();
        final int settlementDays = 3;
        final boolean payFixedRate = true;
        final boolean parAssetSwap = true;

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        final Schedule fixedBondSchedule1 = new Schedule(
                new Date(4, Month.January, 2005),
                new Date(4, Month.January, 2037),
                new Period(Frequency.Annual),
                bondCalendar,
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward,
                false /* endOfMonth */);
        final FixedRateBond fixedBond1 = new FixedRateBond(
                settlementDays, faceAmount, fixedBondSchedule1,
                new double[] { 0.04 },
                new org.jquantlib.daycounters.ActualActual(
                        org.jquantlib.daycounters.ActualActual.Convention.ISDA),
                BusinessDayConvention.Following,
                100.0, new Date(4, Month.January, 2005));

        final DiscountingBondEngine bondEngine =
                new DiscountingBondEngine(ts);
        final DiscountingSwapEngine swapEngine =
                new DiscountingSwapEngine(ts);
        fixedBond1.setPricingEngine(bondEngine);

        final double fixedBondPrice1 = fixedBond1.cleanPrice();
        final AssetSwap fixedBondAssetSwap1 = new AssetSwap(
                payFixedRate,
                fixedBond1, fixedBondPrice1,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondAssetSwap1.setPricingEngine(swapEngine);
        final double fixedBondAssetSwapPrice1 =
                fixedBondAssetSwap1.fairCleanPrice();

        // C++ tolerance: 1e-2 when not using at-par coupons (Java's
        // default — IborCoupon uses indexed forward fixings).
        final double tolerance2 = 1.0e-2;
        final double error1 = Math.abs(fixedBondAssetSwapPrice1 - fixedBondPrice1);

        if (error1 > tolerance2) {
            fail("wrong zero spread asset swap price for fixed bond:\n"
                    + "  bond's clean price:    " + fixedBondPrice1 + "\n"
                    + "  asset swap fair price: " + fixedBondAssetSwapPrice1 + "\n"
                    + "  error:                 " + error1 + "\n"
                    + "  tolerance:             " + tolerance2);
        }
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-ASW-3 — needs AssetSwap.fairCleanPrice / fairSpread.")
    @Test
    public void testMarketASWSpread() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-ASW-4 — needs AssetSwap port; "
            + "BondFunctions.zSpread solver also needs cross-validation against C++.")
    @Test
    public void testZSpread() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-ASW-5 — needs AssetSwap port.")
    @Test
    public void testGenericBondImplied() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-ASW-6 — needs AssetSwap port.")
    @Test
    public void testMASWWithGenericBond() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-ASW-7 — needs AssetSwap port.")
    @Test
    public void testZSpreadWithGenericBond() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-ASW-8 — needs AssetSwap + specialized bond cross-check infra.")
    @Test
    public void testSpecializedBondVsGenericBond() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-ASW-9 — needs AssetSwap convenience constructors.")
    @Test
    public void testSpecializedBondVsGenericBondUsingAsw() {
    }


    // ─────────────────────────────────────────────────────────────────
    // Phase 5e.5-ASW smoke tests
    // ─────────────────────────────────────────────────────────────────
    //
    // The 9 testsuite cases above remain @Ignore'd pending the broader
    // CommonVars fixture port (Phase 5e.5b). The smoke tests below
    // exercise the freshly ported AssetSwap directly — verifying it
    // constructs, prices, and that the par-swap fair-spread roundtrip
    // is internally consistent.

    private static final Date EVAL = new Date(15, Month.January, 2026);

    @Before
    public void setUp() {
        new Settings().setEvaluationDate(EVAL);
    }

    /** Build a synthetic 6y annual fixed-rate bond (issue 2025, mat 2031). */
    private FixedRateBond makeBond(final double couponRate) {
        final Calendar cal = new Target();
        final Date issue = new Date(15, Month.January, 2025);
        final Date maturity = new Date(15, Month.January, 2031);
        final Schedule bondSchedule = new Schedule(
                issue, maturity,
                new Period(Frequency.Annual),
                cal,
                BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward,
                false /* endOfMonth */);
        return new FixedRateBond(
                3 /* settlementDays */,
                100.0 /* faceAmount */,
                bondSchedule,
                new double[] { couponRate },
                new Thirty360(Thirty360.Convention.BondBasis),
                BusinessDayConvention.Following,
                100.0 /* redemption */,
                issue);
    }

    /** Construct a flat-forward yield curve. */
    private Handle<YieldTermStructure> makeFlatCurve(final double rate) {
        final DayCounter dc = new Actual365Fixed();
        final YieldTermStructure flat = new FlatForward(
                EVAL, rate, dc, Compounding.Continuous, Frequency.Annual);
        return new Handle<YieldTermStructure>(flat);
    }

    @Test
    public void smokeTest_parAssetSwap_constructsAndPrices() {
        final Handle<YieldTermStructure> ts = makeFlatCurve(0.04);
        final FixedRateBond bond = makeBond(0.05);

        // Wire the bond engine so accruedAmount() / cashflows() work.
        bond.setPricingEngine(new DiscountingBondEngine(ts));

        final Euribor6M idx = new Euribor6M(ts);
        final double bondCleanPrice = 100.50;
        final double spread = 0.0025;

        final AssetSwap swap = new AssetSwap(
                true /* payBondCoupon */,
                bond,
                bondCleanPrice,
                idx,
                spread);

        swap.setPricingEngine(new DiscountingSwapEngine(ts));

        // ── Structural assertions ────────────────────────────────────
        final Leg bondLeg = swap.bondLeg();
        final Leg floatLeg = swap.floatingLeg();
        assertTrue("bondLeg should not be empty", !bondLeg.isEmpty());
        assertTrue("floatingLeg should not be empty", !floatLeg.isEmpty());

        // Par swap: floating leg = upfront + IborCoupons + backpayment
        // (size >= 3, with at least 12 semi-annual ibors over ~5y).
        assertTrue("par-swap floating leg should have >=3 cashflows "
                + "(upfront + ibors + backpayment), saw " + floatLeg.size(),
                floatLeg.size() >= 3);

        // ── Pricing assertions ───────────────────────────────────────
        final double npv = swap.NPV();
        final double fairSpread = swap.fairSpread();
        final double floatingNPV = swap.floatingLegNPV();
        final double floatingBPS = swap.floatingLegBPS();

        assertTrue("NPV must be finite, got " + npv, Double.isFinite(npv));
        assertTrue("fairSpread must be finite, got " + fairSpread,
                Double.isFinite(fairSpread));
        assertTrue("floatingLegNPV must be finite, got " + floatingNPV,
                Double.isFinite(floatingNPV));
        assertTrue("floatingLegBPS must be finite and non-zero, got "
                + floatingBPS,
                Double.isFinite(floatingBPS) && floatingBPS != 0.0);

        // ── Inspector roundtrip ──────────────────────────────────────
        assertTrue("parSwap inspector should report true", swap.parSwap());
        assertTrue("payBondCoupon inspector should report true",
                swap.payBondCoupon());
        if (Math.abs(swap.spread() - spread) > 1e-15) {
            fail("spread inspector roundtrip failed: " + swap.spread()
                    + " vs " + spread);
        }
        if (Math.abs(swap.cleanPrice() - bondCleanPrice) > 1e-15) {
            fail("cleanPrice inspector roundtrip failed: "
                    + swap.cleanPrice() + " vs " + bondCleanPrice);
        }
    }

    @Test
    public void smokeTest_marketAssetSwap_constructsAndPrices() {
        final Handle<YieldTermStructure> ts = makeFlatCurve(0.04);
        final FixedRateBond bond = makeBond(0.05);
        bond.setPricingEngine(new DiscountingBondEngine(ts));

        final Euribor6M idx = new Euribor6M(ts);
        final double bondCleanPrice = 102.0;
        final double spread = 0.0025;
        final DayCounter floatingDc = new Actual360();

        final AssetSwap swap = new AssetSwap(
                false /* payBondCoupon = receive bond */,
                bond,
                bondCleanPrice,
                idx,
                spread,
                null /* floatSchedule (auto-generate) */,
                floatingDc,
                false /* parAssetSwap = market */,
                1.0 /* gearing */,
                org.jquantlib.math.Constants.NULL_REAL,
                new Date());

        swap.setPricingEngine(new DiscountingSwapEngine(ts));

        // Market swap: floating leg = IborCoupons + final notional exchange
        // (one SimpleCashFlow). No upfront/backpayment.
        final Leg floatLeg = swap.floatingLeg();
        assertTrue("market-swap floating leg should have >=2 cashflows "
                + "(ibors + final exchange), saw " + floatLeg.size(),
                floatLeg.size() >= 2);

        final double npv = swap.NPV();
        final double fairCleanPrice = swap.fairCleanPrice();
        assertTrue("NPV must be finite, got " + npv, Double.isFinite(npv));
        assertTrue("fairCleanPrice must be finite and positive, got "
                + fairCleanPrice,
                Double.isFinite(fairCleanPrice) && fairCleanPrice > 0.0);

        assertTrue("parSwap should report false for market swap",
                !swap.parSwap());
        assertTrue("payBondCoupon should report false (we receive bond)",
                !swap.payBondCoupon());
    }

    @Test
    public void smokeTest_fairSpread_recoversZeroNpv() {
        // Build par swap with arbitrary spread, get the fair spread, build
        // a second swap with the fair spread, verify NPV is now ~ 0
        // (definition of fair-spread).
        final Handle<YieldTermStructure> ts = makeFlatCurve(0.035);
        final FixedRateBond bond = makeBond(0.06);
        bond.setPricingEngine(new DiscountingBondEngine(ts));

        final Euribor6M idx = new Euribor6M(ts);
        final double bondCleanPrice = 100.0;
        final double initialSpread = 0.001;

        final AssetSwap probe = new AssetSwap(true, bond, bondCleanPrice,
                idx, initialSpread);
        probe.setPricingEngine(new DiscountingSwapEngine(ts));
        final double fairSpread = probe.fairSpread();
        assertTrue("fairSpread must be finite, got " + fairSpread,
                Double.isFinite(fairSpread));

        // Rebuild with fairSpread (fresh bond/idx instances to avoid stale
        // observer chains).
        final FixedRateBond bond2 = makeBond(0.06);
        bond2.setPricingEngine(new DiscountingBondEngine(ts));
        final Euribor6M idx2 = new Euribor6M(ts);
        final AssetSwap fair = new AssetSwap(true, bond2, bondCleanPrice,
                idx2, fairSpread);
        fair.setPricingEngine(new DiscountingSwapEngine(ts));

        final double residualNPV = fair.NPV();
        // Notional 100; we expect machine-precision-zero. LOOSE smoke
        // tier: 1e-6 absolute. Phase 5e.5b cross-validation against C++
        // will tighten this.
        if (Math.abs(residualNPV) > 1e-6) {
            fail("fairSpread did not recover NPV ~ 0; residualNPV = "
                    + residualNPV + " (fairSpread=" + fairSpread + ")");
        }
    }
}
