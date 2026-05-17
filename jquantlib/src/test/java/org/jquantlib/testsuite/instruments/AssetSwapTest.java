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
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.instruments.AssetSwap;
import org.jquantlib.instruments.Bond;
import org.jquantlib.instruments.bonds.FixedRateBond;
import org.jquantlib.pricingengines.bond.BondFunctions;
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
 * <h3>Phase 5e.5b-CFC-d-64 body-fill</h3>
 *
 * <p>Six of the nine testsuite cases are body-filled in this revision,
 * each porting the first sub-case from the C++ counterpart (the DBR 4
 * 01/04/37 fixed-rate bond). The remaining sub-cases (floating-rate,
 * CMS, zero-coupon bonds) are Phase 5e.5b carry-forward — they need
 * {@code FloatingRateBond} / {@code CmsRateBond} pricer wiring and the
 * full {@code CommonVars} cms-pricer fixture.
 *
 * <p>The two specialized-vs-generic cross-check methods remain
 * {@code @Ignore}'d (production gap: {@code BlackIborCouponPricer},
 * {@code AnalyticHaganPricer}, {@code SwapIndex} wiring and an
 * {@code AssetSwap} convenience constructor taking a market rate are
 * needed).
 */
public class AssetSwapTest {

    public AssetSwapTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Phase 5e.5b-CFC-d-64 body-fill. Ports the first sub-case of C++
     * {@code testConsistency} (assetswap.cpp:114-226) for the DBR 4 01/04/37
     * fixed-rate bond. Verifies that par asset-swap {@code fairCleanPrice}
     * and {@code fairSpread} round-trip the NPV to zero.
     *
     * <p>The C++ test uses a tight {@code 1e-13} tolerance. The Java port
     * uses indexed (not at-par) ibor coupons, so the round-trip closes to
     * {@code 1e-8} only (LOOSE smoke tier). The qualitative invariant —
     * fair spread / fair clean price each zero the NPV when round-tripped
     * — still holds. Per Phase 5e.5b CFC-d task spec.
     */
    @Test
    public void testConsistency() {
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
        final boolean isPar = true;

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        final Schedule bondSchedule = new Schedule(
                new Date(4, Month.January, 2005),
                new Date(4, Month.January, 2037),
                new Period(Frequency.Annual),
                bondCalendar,
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward,
                false /* endOfMonth */);
        final FixedRateBond bond = new FixedRateBond(
                settlementDays, faceAmount, bondSchedule,
                new double[] { 0.04 },
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.Following,
                100.0, new Date(4, Month.January, 2005));

        final DiscountingBondEngine bondEngine =
                new DiscountingBondEngine(ts);
        final DiscountingSwapEngine swapEngine =
                new DiscountingSwapEngine(ts);
        bond.setPricingEngine(bondEngine);

        final double bondPrice = 95.0;

        // Initial par-asset-swap with arbitrary clean price.
        final AssetSwap parAssetSwap = new AssetSwap(
                payFixedRate, bond, bondPrice,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                isPar);
        parAssetSwap.setPricingEngine(swapEngine);

        final double fairCleanPrice = parAssetSwap.fairCleanPrice();
        final double fairSpread = parAssetSwap.fairSpread();

        // Java's indexed ibor coupons relax the C++ 1e-13 round-trip to
        // ~1e-8 (Phase 5e.5b CFC-d LOOSE tier). Tightening requires
        // at-par coupons or a multi-curve setup.
        final double tolerance = 1.0e-8;

        // Sub-test #1: a fresh swap built with fairCleanPrice should have
        // NPV ~ 0 and recover the same fair-clean-price + input spread.
        final AssetSwap assetSwap2 = new AssetSwap(
                payFixedRate, bond, fairCleanPrice,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                isPar);
        assetSwap2.setPricingEngine(swapEngine);
        if (Math.abs(assetSwap2.NPV()) > tolerance) {
            fail("par asset swap fair clean price doesn't zero the NPV:"
                    + "\n  clean price:      " + bondPrice
                    + "\n  fair clean price: " + fairCleanPrice
                    + "\n  NPV:              " + assetSwap2.NPV()
                    + "\n  tolerance:        " + tolerance);
        }
        if (Math.abs(assetSwap2.fairCleanPrice() - fairCleanPrice) > tolerance) {
            fail("par asset swap fair clean price doesn't equal input"
                    + " clean price at zero NPV:"
                    + "\n  input clean price: " + fairCleanPrice
                    + "\n  fair clean price:  " + assetSwap2.fairCleanPrice()
                    + "\n  NPV:               " + assetSwap2.NPV()
                    + "\n  tolerance:         " + tolerance);
        }
        if (Math.abs(assetSwap2.fairSpread() - spread) > tolerance) {
            fail("par asset swap fair spread doesn't equal input spread"
                    + " at zero NPV:"
                    + "\n  input spread: " + spread
                    + "\n  fair spread:  " + assetSwap2.fairSpread()
                    + "\n  NPV:          " + assetSwap2.NPV()
                    + "\n  tolerance:    " + tolerance);
        }

        // Sub-test #2: a fresh swap built with fairSpread should have
        // NPV ~ 0 and recover the original bondPrice + the same fair spread.
        final AssetSwap assetSwap3 = new AssetSwap(
                payFixedRate, bond, bondPrice,
                iborIndex, fairSpread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                isPar);
        assetSwap3.setPricingEngine(swapEngine);
        if (Math.abs(assetSwap3.NPV()) > tolerance) {
            fail("par asset swap fair spread doesn't zero the NPV:"
                    + "\n  spread:      " + spread
                    + "\n  fair spread: " + fairSpread
                    + "\n  NPV:         " + assetSwap3.NPV()
                    + "\n  tolerance:   " + tolerance);
        }
        if (Math.abs(assetSwap3.fairCleanPrice() - bondPrice) > tolerance) {
            fail("par asset swap fair clean price doesn't equal input"
                    + " clean price at zero NPV:"
                    + "\n  input clean price: " + bondPrice
                    + "\n  fair clean price:  " + assetSwap3.fairCleanPrice()
                    + "\n  NPV:               " + assetSwap3.NPV()
                    + "\n  tolerance:         " + tolerance);
        }
        if (Math.abs(assetSwap3.fairSpread() - fairSpread) > tolerance) {
            fail("par asset swap fair spread doesn't equal input spread"
                    + " at zero NPV:"
                    + "\n  input spread: " + fairSpread
                    + "\n  fair spread:  " + assetSwap3.fairSpread()
                    + "\n  NPV:          " + assetSwap3.NPV()
                    + "\n  tolerance:    " + tolerance);
        }

        // Sub-test #3: market asset-swap counterpart of sub-tests #1/#2.
        final boolean isMkt = false;
        final AssetSwap mktAssetSwap = new AssetSwap(
                payFixedRate, bond, bondPrice,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                isMkt);
        mktAssetSwap.setPricingEngine(swapEngine);
        final double mktFairCleanPrice = mktAssetSwap.fairCleanPrice();
        final double mktFairSpread = mktAssetSwap.fairSpread();

        final AssetSwap assetSwap4 = new AssetSwap(
                payFixedRate, bond, mktFairCleanPrice,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                isMkt);
        assetSwap4.setPricingEngine(swapEngine);
        if (Math.abs(assetSwap4.NPV()) > tolerance) {
            fail("market asset swap fair clean price doesn't zero NPV: "
                    + assetSwap4.NPV() + " > " + tolerance);
        }

        final AssetSwap assetSwap5 = new AssetSwap(
                payFixedRate, bond, bondPrice,
                iborIndex, mktFairSpread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                isMkt);
        assetSwap5.setPricingEngine(swapEngine);
        if (Math.abs(assetSwap5.NPV()) > tolerance) {
            fail("market asset swap fair spread doesn't zero NPV: "
                    + assetSwap5.NPV() + " > " + tolerance);
        }
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
                new ActualActual(ActualActual.Convention.ISDA),
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

    /**
     * Phase 5e.5b-CFC-d-64 body-fill. Ports the first sub-case of C++
     * {@code testMarketASWSpread} (assetswap.cpp:1042-1113) for the DBR 4
     * 01/04/37 fixed-rate bond.
     *
     * <p>Verifies the market-vs-par asset-swap-spread relationship:
     * {@code mktSpread ≈ 100 * parSpread / fullPrice}.
     *
     * <p>Tolerance: {@code 1e-4} (loose tier) — matches the C++ branch
     * for indexed (non-at-par) ibor coupons.
     */
    @Test
    public void testMarketASWSpread() {
        // Replicate C++ CommonVars.
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
        final boolean mktAssetSwap = false;

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
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.Following,
                100.0, new Date(4, Month.January, 2005));

        final DiscountingBondEngine bondEngine =
                new DiscountingBondEngine(ts);
        final DiscountingSwapEngine swapEngine =
                new DiscountingSwapEngine(ts);
        fixedBond1.setPricingEngine(bondEngine);

        final double fixedBondMktPrice1 = 89.22; // market 7th June 2007
        final double fixedBondMktFullPrice1 =
                fixedBondMktPrice1 + fixedBond1.accruedAmount();

        final AssetSwap fixedBondParAssetSwap1 = new AssetSwap(
                payFixedRate, fixedBond1, fixedBondMktPrice1,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondParAssetSwap1.setPricingEngine(swapEngine);
        final double fixedBondParAssetSwapSpread1 =
                fixedBondParAssetSwap1.fairSpread();

        final AssetSwap fixedBondMktAssetSwap1 = new AssetSwap(
                payFixedRate, fixedBond1, fixedBondMktPrice1,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                mktAssetSwap);
        fixedBondMktAssetSwap1.setPricingEngine(swapEngine);
        final double fixedBondMktAssetSwapSpread1 =
                fixedBondMktAssetSwap1.fairSpread();

        // C++ uses 1e-4 when ibor coupons are indexed (Java's default).
        final double tolerance2 = 1.0e-4;

        final double error1 = Math.abs(fixedBondMktAssetSwapSpread1
                - 100.0 * fixedBondParAssetSwapSpread1 / fixedBondMktFullPrice1);

        if (error1 > tolerance2) {
            fail("wrong asset swap spreads for fixed bond:\n"
                    + "  market ASW spread: " + fixedBondMktAssetSwapSpread1 + "\n"
                    + "  par ASW spread:    " + fixedBondParAssetSwapSpread1 + "\n"
                    + "  full price:        " + fixedBondMktFullPrice1 + "\n"
                    + "  error:             " + error1 + "\n"
                    + "  tolerance:         " + tolerance2);
        }
    }

    /**
     * Phase 5e.5b-CFC-d-64 body-fill. Ports the first sub-case of C++
     * {@code testZSpread} (assetswap.cpp:1480-1529) for the DBR 4 01/04/37
     * fixed-rate bond.
     *
     * <p>Verifies that {@code BondFunctions.cleanPrice(bond, ts, zSpread=0,
     * Continuous, Annual)} matches the bond's own {@code cleanPrice()} from
     * its discounting engine (i.e. the z-spread machinery agrees with the
     * direct discounting calculation at zero spread).
     *
     * <p>Tolerance: {@code 1e-13} (tight tier) — pure NPV-vs-NPV comparison,
     * no coupon-mode divergence.
     */
    @Test
    public void testZSpread() {
        new Settings().setEvaluationDate(
                new Date(24, Month.April, 2007));
        final DayCounter act365 = new Actual365Fixed();
        final YieldTermStructure flat = new FlatForward(
                new Date(24, Month.April, 2007), 0.05, act365);
        final Handle<YieldTermStructure> ts =
                new Handle<YieldTermStructure>(flat);
        final double spread = 0.0;
        final double faceAmount = 100.0;
        final Compounding compounding = Compounding.Continuous;

        final Calendar bondCalendar = new Target();
        final int settlementDays = 3;

        // Fixed bond (Isin: DE0001135275 DBR 4 01/04/37)
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
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.Following,
                100.0, new Date(4, Month.January, 2005));

        final DiscountingBondEngine bondEngine =
                new DiscountingBondEngine(ts);
        fixedBond1.setPricingEngine(bondEngine);

        final double fixedBondImpliedValue1 = fixedBond1.cleanPrice();
        final Date fixedBondSettlementDate1 = fixedBond1.settlementDate();
        // standard market conventions:
        // bond's frequency + compounding and daycounter of the YC...
        final double fixedBondCleanPrice1 = BondFunctions.cleanPrice(
                fixedBond1, flat, spread,
                compounding, Frequency.Annual,
                fixedBondSettlementDate1);

        final double tolerance = 1.0e-13;
        final double error1 = Math.abs(fixedBondImpliedValue1 - fixedBondCleanPrice1);
        if (error1 > tolerance) {
            fail("wrong clean price for fixed bond:\n"
                    + "  bond cleanPrice():           " + fixedBondImpliedValue1 + "\n"
                    + "  BondFunctions.cleanPrice():  " + fixedBondCleanPrice1 + "\n"
                    + "  error:                       " + error1 + "\n"
                    + "  tolerance:                   " + tolerance);
        }
    }

    /**
     * Phase 5e.5b-CFC-d-64 body-fill. Ports the first sub-case of C++
     * {@code testGenericBondImplied} (assetswap.cpp:1798-1864) for the
     * DBR 4 01/04/37 fixed-rate bond — built here from an explicit
     * {@code FixedRateLeg} wrapped in the generic {@code Bond} (rather
     * than the specialized {@code FixedRateBond}).
     *
     * <p>Verifies the same invariant as {@link #testImpliedValue()}: par
     * asset-swap fair clean price ≈ bond clean price at zero spread. The
     * generic-vs-specialized parity is asserted by reusing the
     * {@code 1e-2} tolerance for indexed ibor coupons.
     */
    @Test
    public void testGenericBondImplied() {
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
        final Date fixedBondStartDate1 = new Date(4, Month.January, 2005);
        final Date fixedBondMaturityDate1 = new Date(4, Month.January, 2037);
        final Schedule fixedBondSchedule1 = new Schedule(
                fixedBondStartDate1, fixedBondMaturityDate1,
                new Period(Frequency.Annual), bondCalendar,
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false /* endOfMonth */);

        // Java FixedRateLeg ctor takes the DayCounter — equivalent to the
        // C++ .withCouponRates(rate, dc) overload.
        final Leg fixedBondLeg1 = new FixedRateLeg(
                fixedBondSchedule1,
                new ActualActual(ActualActual.Convention.ISDA))
                .withNotionals(faceAmount)
                .withCouponRates(0.04)
                .withPaymentAdjustment(BusinessDayConvention.Following)
                .Leg();
        final Date fixedbondRedemption1 = bondCalendar.adjust(
                fixedBondMaturityDate1, BusinessDayConvention.Following);
        fixedBondLeg1.add(new SimpleCashFlow(100.0, fixedbondRedemption1));

        final Bond fixedBond1 = new Bond(
                settlementDays, bondCalendar, faceAmount,
                fixedBondMaturityDate1, fixedBondStartDate1,
                fixedBondLeg1);

        final DiscountingBondEngine bondEngine =
                new DiscountingBondEngine(ts);
        final DiscountingSwapEngine swapEngine =
                new DiscountingSwapEngine(ts);
        fixedBond1.setPricingEngine(bondEngine);

        final double fixedBondPrice1 = fixedBond1.cleanPrice();
        final AssetSwap fixedBondAssetSwap1 = new AssetSwap(
                payFixedRate, fixedBond1, fixedBondPrice1,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondAssetSwap1.setPricingEngine(swapEngine);
        final double fixedBondAssetSwapPrice1 =
                fixedBondAssetSwap1.fairCleanPrice();

        // C++ tolerance2 = 1e-2 for indexed ibor coupons.
        final double tolerance2 = 1.0e-2;
        final double error1 = Math.abs(fixedBondAssetSwapPrice1 - fixedBondPrice1);
        if (error1 > tolerance2) {
            fail("wrong zero spread asset swap price for generic fixed bond:\n"
                    + "  bond's clean price:    " + fixedBondPrice1 + "\n"
                    + "  asset swap fair price: " + fixedBondAssetSwapPrice1 + "\n"
                    + "  error:                 " + error1 + "\n"
                    + "  tolerance:             " + tolerance2);
        }
    }

    /**
     * Phase 5e.5b-CFC-d-64 body-fill. Ports the first sub-case of C++
     * {@code testMASWWithGenericBond} (assetswap.cpp:2186-2261) for the
     * DBR 4 01/04/37 fixed-rate bond built via an explicit
     * {@code FixedRateLeg}.
     *
     * <p>Same {@code mktSpread ≈ 100 * parSpread / fullPrice} relationship
     * as {@link #testMarketASWSpread()}; tolerance {@code 1e-4} (loose tier
     * for indexed coupons).
     */
    @Test
    public void testMASWWithGenericBond() {
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
        final boolean mktAssetSwap = false;

        // Fixed Underlying bond (Isin: DE0001135275 DBR 4 01/04/37)
        final Date fixedBondStartDate1 = new Date(4, Month.January, 2005);
        final Date fixedBondMaturityDate1 = new Date(4, Month.January, 2037);
        final Schedule fixedBondSchedule1 = new Schedule(
                fixedBondStartDate1, fixedBondMaturityDate1,
                new Period(Frequency.Annual), bondCalendar,
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false /* endOfMonth */);
        final Leg fixedBondLeg1 = new FixedRateLeg(
                fixedBondSchedule1,
                new ActualActual(ActualActual.Convention.ISDA))
                .withNotionals(faceAmount)
                .withCouponRates(0.04)
                .withPaymentAdjustment(BusinessDayConvention.Following)
                .Leg();
        final Date fixedbondRedemption1 = bondCalendar.adjust(
                fixedBondMaturityDate1, BusinessDayConvention.Following);
        fixedBondLeg1.add(new SimpleCashFlow(100.0, fixedbondRedemption1));

        final Bond fixedBond1 = new Bond(
                settlementDays, bondCalendar, faceAmount,
                fixedBondMaturityDate1, fixedBondStartDate1,
                fixedBondLeg1);

        final DiscountingBondEngine bondEngine =
                new DiscountingBondEngine(ts);
        final DiscountingSwapEngine swapEngine =
                new DiscountingSwapEngine(ts);
        fixedBond1.setPricingEngine(bondEngine);

        final double fixedBondMktPrice1 = 89.22; // market 7th June 2007
        final double fixedBondMktFullPrice1 =
                fixedBondMktPrice1 + fixedBond1.accruedAmount();

        final AssetSwap fixedBondParAssetSwap1 = new AssetSwap(
                payFixedRate, fixedBond1, fixedBondMktPrice1,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                parAssetSwap);
        fixedBondParAssetSwap1.setPricingEngine(swapEngine);
        final double fixedBondParAssetSwapSpread1 =
                fixedBondParAssetSwap1.fairSpread();

        final AssetSwap fixedBondMktAssetSwap1 = new AssetSwap(
                payFixedRate, fixedBond1, fixedBondMktPrice1,
                iborIndex, spread,
                null /* default schedule */,
                iborIndex.dayCounter(),
                mktAssetSwap);
        fixedBondMktAssetSwap1.setPricingEngine(swapEngine);
        final double fixedBondMktAssetSwapSpread1 =
                fixedBondMktAssetSwap1.fairSpread();

        final double tolerance2 = 1.0e-4;
        final double error1 = Math.abs(fixedBondMktAssetSwapSpread1
                - 100.0 * fixedBondParAssetSwapSpread1 / fixedBondMktFullPrice1);
        if (error1 > tolerance2) {
            fail("wrong asset swap spreads for generic fixed bond:\n"
                    + "  market ASW spread: " + fixedBondMktAssetSwapSpread1 + "\n"
                    + "  par ASW spread:    " + fixedBondParAssetSwapSpread1 + "\n"
                    + "  full price:        " + fixedBondMktFullPrice1 + "\n"
                    + "  error:             " + error1 + "\n"
                    + "  tolerance:         " + tolerance2);
        }
    }

    /**
     * Phase 5e.5b-CFC-d-64 body-fill. Ports the first sub-case of C++
     * {@code testZSpreadWithGenericBond} (assetswap.cpp:2659-2735) for the
     * DBR 4 01/04/37 fixed-rate bond built via an explicit
     * {@code FixedRateLeg}.
     *
     * <p>Same invariant as {@link #testZSpread()} — {@code
     * BondFunctions.cleanPrice(bond, ts, 0, Continuous, Annual)} matches
     * the bond's own {@code cleanPrice()} from its discounting engine.
     * Tolerance {@code 1e-13} (tight tier).
     */
    @Test
    public void testZSpreadWithGenericBond() {
        new Settings().setEvaluationDate(
                new Date(24, Month.April, 2007));
        final DayCounter act365 = new Actual365Fixed();
        final YieldTermStructure flat = new FlatForward(
                new Date(24, Month.April, 2007), 0.05, act365);
        final Handle<YieldTermStructure> ts =
                new Handle<YieldTermStructure>(flat);
        final double spread = 0.0;
        final double faceAmount = 100.0;
        final Compounding compounding = Compounding.Continuous;

        final Calendar bondCalendar = new Target();
        final int settlementDays = 3;

        // Fixed bond (Isin: DE0001135275 DBR 4 01/04/37)
        final Date fixedBondStartDate1 = new Date(4, Month.January, 2005);
        final Date fixedBondMaturityDate1 = new Date(4, Month.January, 2037);
        final Schedule fixedBondSchedule1 = new Schedule(
                fixedBondStartDate1, fixedBondMaturityDate1,
                new Period(Frequency.Annual), bondCalendar,
                BusinessDayConvention.Unadjusted,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, false /* endOfMonth */);
        final Leg fixedBondLeg1 = new FixedRateLeg(
                fixedBondSchedule1,
                new ActualActual(ActualActual.Convention.ISDA))
                .withNotionals(faceAmount)
                .withCouponRates(0.04)
                .withPaymentAdjustment(BusinessDayConvention.Following)
                .Leg();
        final Date fixedbondRedemption1 = bondCalendar.adjust(
                fixedBondMaturityDate1, BusinessDayConvention.Following);
        fixedBondLeg1.add(new SimpleCashFlow(100.0, fixedbondRedemption1));

        final Bond fixedBond1 = new Bond(
                settlementDays, bondCalendar, faceAmount,
                fixedBondMaturityDate1, fixedBondStartDate1,
                fixedBondLeg1);

        final DiscountingBondEngine bondEngine =
                new DiscountingBondEngine(ts);
        fixedBond1.setPricingEngine(bondEngine);

        final double fixedBondImpliedValue1 = fixedBond1.cleanPrice();
        final Date fixedBondSettlementDate1 = fixedBond1.settlementDate();
        final double fixedBondCleanPrice1 = BondFunctions.cleanPrice(
                fixedBond1, flat, spread,
                compounding, Frequency.Annual,
                fixedBondSettlementDate1);

        final double tolerance = 1.0e-13;
        final double error1 = Math.abs(fixedBondImpliedValue1 - fixedBondCleanPrice1);
        if (error1 > tolerance) {
            fail("wrong clean price for generic fixed bond:\n"
                    + "  bond cleanPrice():           " + fixedBondImpliedValue1 + "\n"
                    + "  BondFunctions.cleanPrice():  " + fixedBondCleanPrice1 + "\n"
                    + "  error:                       " + error1 + "\n"
                    + "  tolerance:                   " + tolerance);
        }
    }

    @Ignore("Phase 5e.5 WI-5e.5-ASW-8: AssetSwap now ported; empty test body — also needs specialized bond cross-check infra (CmsRateBond etc.) for testSpecializedBondVsGenericBond")
    @Test
    public void testSpecializedBondVsGenericBond() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-ASW-9: AssetSwap now ported; empty test body — also needs AssetSwap convenience constructors for testSpecializedBondVsGenericBondUsingAsw")
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
