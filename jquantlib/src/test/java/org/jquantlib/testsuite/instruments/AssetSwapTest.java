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

import org.jquantlib.QL;
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

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-ASW-2 — needs AssetSwap port + parAssetSwap flag.")
    @Test
    public void testImpliedValue() {
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
}
