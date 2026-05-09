/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.equity;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/batesmodel.cpp} v1.42.1
 * (513 LOC, 4 test cases).
 *
 * <p>The four C++ tests are:
 * <ul>
 *   <li>{@code testAnalyticVsBlack} — collapses Bates to Black-Scholes
 *       limit (zero jump intensity, vol-of-vol → 0, v0 = theta) and
 *       verifies the analytic Bates engine reproduces Black price to
 *       1e-3.  Requires {@code BatesEngine} (analytic Heston-with-jumps).</li>
 *   <li>{@code testAnalyticAndMcVsJumpDiffusion} — cross-validates
 *       {@code BatesEngine} and {@code MCEuropeanHestonEngine} with
 *       jumps against {@code JumpDiffusionEngine} on Merton76-style
 *       data.  Requires {@code BatesProcess} + MC Heston engine.</li>
 *   <li>{@code testAnalyticVsMCPricing} — compares analytic Bates engine
 *       price to MC simulation across an OTM/ITM grid. Requires
 *       {@code MCEuropeanHestonEngine}.</li>
 *   <li>{@code testDAXCalibration} — calibrates Bates to the Heston (1993)
 *       DAX dataset, verifying RMSE under 5e-3.  Requires the calibration
 *       loop (LevenbergMarquardt + BatesEngine), CPU-intensive.</li>
 * </ul>
 *
 * <p><strong>Phase 5h.5 carry-forward:</strong> the Java
 * {@link org.jquantlib.model.equity.BatesModel} class exists (Phase 4f)
 * but the corresponding production engines are <strong>not yet ported</strong>:
 * <ul>
 *   <li>{@code BatesProcess} — Heston-process subclass with a jump component;</li>
 *   <li>{@code BatesEngine} — analytic Heston-style integration with a
 *       Bates characteristic function;</li>
 *   <li>{@code BatesDetJumpEngine}, {@code BatesDoubleExpEngine},
 *       {@code BatesDoubleExpDetJumpEngine} — variant analytic engines;</li>
 *   <li>{@code MCEuropeanHestonEngine} — MC pricer for Heston with jumps.</li>
 * </ul>
 * Without these, none of the four tests can be exercised.  The
 * {@code testDAXCalibration} case must additionally be tagged as a slow
 * test (see Phase 5 META D8) once enabled.
 *
 * <p>Source: {@code test-suite/batesmodel.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class BatesModelTest {

    private static final String REASON_ENGINES =
            "Phase 5h.5 — requires BatesProcess + BatesEngine + MCEuropeanHestonEngine "
            + "(Phase 4f.5 carry-forward; only BatesModel class exists in Java).";

    private static final String REASON_CALIB =
            "Phase 5h.5 + slow — requires Bates calibration infra and @Tag(\"slow\") "
            + "(see Phase 5 META D8).";

    @Ignore(REASON_ENGINES)
    @Test
    public void testAnalyticVsBlack() { fail("not implemented"); }

    @Ignore(REASON_ENGINES)
    @Test
    public void testAnalyticAndMcVsJumpDiffusion() { fail("not implemented"); }

    @Ignore(REASON_ENGINES)
    @Test
    public void testAnalyticVsMCPricing() { fail("not implemented"); }

    @Ignore(REASON_CALIB)
    @Test
    public void testDAXCalibration() { fail("not implemented"); }
}
