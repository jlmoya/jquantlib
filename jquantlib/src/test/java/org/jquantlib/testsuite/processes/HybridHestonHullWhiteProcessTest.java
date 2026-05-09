/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.processes;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/hybridhestonhullwhiteprocess.cpp}
 * v1.42.1 (1,418 LOC, 13 test cases).
 *
 * <p>The thirteen C++ tests exercise the equity / interest-rate hybrid
 * model {@code HybridHestonHullWhiteProcess} and its associated pricing
 * engines:
 * <ul>
 *   <li>{@code testBsmHullWhiteEngine} — collapses Heston→BSM by setting
 *       vol-of-vol to zero, then prices a vanilla call under
 *       {@code AnalyticBSMHullWhiteEngine} and compares to a calibrated
 *       Hull-White short-rate model with deterministic equity vol.</li>
 *   <li>{@code testCompareBsmHWandHestonHW} — confirms
 *       {@code AnalyticHestonHullWhiteEngine} ≈
 *       {@code AnalyticBSMHullWhiteEngine} in the BSM limit.</li>
 *   <li>{@code testZeroBondPricing} — MC simulation of zero-coupon bond
 *       prices via the hybrid process with Andersen QE discretization.</li>
 *   <li>{@code testMcVanillaPricing} — MC vanilla-call pricing under the
 *       full hybrid process; cross-checks against analytic engine.</li>
 *   <li>{@code testMcPureHestonPricing} — degenerate hybrid (HW vol = 0)
 *       reduces to pure Heston; MC matches {@code AnalyticHestonEngine}.</li>
 *   <li>{@code testAnalyticHestonHullWhitePricing} — analytic engine
 *       cross-validation against MC.</li>
 *   <li>{@code testCallableEquityPricing} — prices a callable equity
 *       structure on the hybrid model.</li>
 *   <li>{@code testDiscretizationError} — tracks MC error vs. step size
 *       to confirm Andersen QE strong-order-1 convergence.</li>
 *   <li>{@code testFdmHestonHullWhiteEngine} — exercises
 *       {@code FdHestonHullWhiteVanillaEngine} (the only HHW engine
 *       already ported to Java; cf. Phase 2m).</li>
 *   <li>{@code testBsmHullWhitePricing} — BSM-HW analytic engine
 *       reference pricing.</li>
 *   <li>{@code testSpatialDiscretizatinError} — FD spatial-grid
 *       convergence study.</li>
 *   <li>{@code testHestonHullWhiteCalibration} — calibrate Heston piece
 *       of the hybrid model to vanilla quotes; CPU-intensive
 *       (Phase 5 META D8 — slow tag).</li>
 *   <li>{@code testH1HWPricingEngine} — Andersen / Piterbarg H1-HW
 *       expansion engine sanity check.</li>
 * </ul>
 *
 * <p><strong>Phase 5h.5 carry-forward:</strong> Java has only the
 * <em>finite-difference</em> Heston-HullWhite stack ported (Phase 2m):
 * {@code FdmHestonHullWhiteOp}, {@code FdmHestonHullWhiteSolver},
 * {@code FdHestonHullWhiteVanillaEngine}.
 *
 * <p>Missing classes that block all 13 tests:
 * <ul>
 *   <li>{@code HybridHestonHullWhiteProcess} — the joint stochastic
 *       process (3D: equity, variance, short rate);</li>
 *   <li>{@code AnalyticBSMHullWhiteEngine} — closed-form pricing under
 *       BSM equity + Hull-White rates;</li>
 *   <li>{@code AnalyticHestonHullWhiteEngine} — semi-analytic Heston +
 *       Hull-White pricing;</li>
 *   <li>{@code AnalyticH1HWEngine} — Andersen-Piterbarg H1-HW
 *       expansion;</li>
 *   <li>{@code MCHestonHullWhiteEngine} — Monte-Carlo hybrid pricer;</li>
 *   <li>HW-Heston correlation calibration helper;</li>
 *   <li>{@code MCVanillaEngine}-style callable-equity pricer.</li>
 * </ul>
 *
 * <p>Once {@code FdHestonHullWhiteVanillaEngine}-only coverage is
 * needed independently, {@code testFdmHestonHullWhiteEngine} could be
 * implemented; however its cross-validation in the C++ file relies on
 * an analytic-engine reference, so it is also deferred until the
 * analytic engine is ported.
 *
 * <p>Source: {@code test-suite/hybridhestonhullwhiteprocess.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class HybridHestonHullWhiteProcessTest {

    private static final String REASON =
            "Phase 5h.5 — requires HybridHestonHullWhiteProcess + analytic / MC HHW engines "
            + "(Phase 2m / 4n carry-forward; only FdHestonHullWhite-stack exists in Java).";

    private static final String REASON_SLOW =
            "Phase 5h.5 + slow — requires HHW calibration loop and @Tag(\"slow\") "
            + "(see Phase 5 META D8).";

    @Ignore(REASON)
    @Test
    public void testBsmHullWhiteEngine() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testCompareBsmHWandHestonHW() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testZeroBondPricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testMcVanillaPricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testMcPureHestonPricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testAnalyticHestonHullWhitePricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testCallableEquityPricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testDiscretizationError() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFdmHestonHullWhiteEngine() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testBsmHullWhitePricing() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testSpatialDiscretizatinError() { fail("not implemented"); }

    @Ignore(REASON_SLOW)
    @Test
    public void testHestonHullWhiteCalibration() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testH1HWPricingEngine() { fail("not implemented"); }
}
