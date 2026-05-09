/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/riskneutraldensitycalculator.cpp}
 * v1.42.1 (783 LOC, 7 test cases).
 *
 * <p>The seven C++ tests exercise the family of risk-neutral-density
 * (RND) helper classes used by {@code FdBlackScholesVanillaEngine},
 * {@code FdHestonVanillaEngine}, {@code FdSabrVanillaEngine}, etc.:
 * <ul>
 *   <li>{@code testDensityAgainstOptionPrices} — verifies that
 *       {@code BSMRNDCalculator} reproduces the put / call breakeven
 *       density implied by Black-Scholes prices to {@code 10*sqrt(eps)}
 *       relative tolerance.</li>
 *   <li>{@code testBSMagainstHestonRND} — checks that
 *       {@code HestonRNDCalculator} converges to the BSM density when
 *       Heston parameters degenerate (vol-of-vol → 0, v0 = theta,
 *       rho = 0).</li>
 *   <li>{@code testLocalVolatilityRND} — exercises
 *       {@code LocalVolRNDCalculator} on a Heston-implied local vol
 *       surface and verifies CDF integrates to 1.</li>
 *   <li>{@code testSquareRootProcessRND} — analytic non-central χ² PDF
 *       /CDF/inverse-CDF for the CIR/Heston square-root variance
 *       process via {@code SquareRootProcessRNDCalculator}.</li>
 *   <li>{@code testBlackScholesWithSkew} — large strike-range BSM RND
 *       check with an implied-vol skew.</li>
 *   <li>{@code testMassAtZeroCEVProcessRND} — verifies the Δ-mass at
 *       zero for absorbing CEV when β &lt; 1.</li>
 *   <li>{@code testCEVCDF} — analytic CEV CDF in three regimes
 *       (β &lt; 1 absorbing, β = 1 lognormal, β &gt; 1 reflecting).</li>
 * </ul>
 *
 * <p><strong>Phase 5h.5 carry-forward:</strong> Java has only two of the
 * five C++ RND calculators ported:
 * <ul>
 *   <li>{@code GBSMRNDCalculator} — present (Phase 2m).</li>
 *   <li>{@code CEVRNDCalculator} — present (Phase 2m); enables the
 *       {@code testMassAtZeroCEVProcessRND} and {@code testCEVCDF}
 *       tests in principle.</li>
 *   <li>{@code BSMRNDCalculator} — <strong>missing</strong>.</li>
 *   <li>{@code HestonRNDCalculator} — <strong>missing</strong>.</li>
 *   <li>{@code LocalVolRNDCalculator} — <strong>missing</strong>.</li>
 *   <li>{@code SquareRootProcessRNDCalculator} — <strong>missing</strong>.</li>
 * </ul>
 * The two CEV tests could be implemented standalone, but porting them
 * piecemeal without the rest of the family would inflate the diff with
 * helper code that is best added as part of a unified Phase 5h.5 RND
 * cluster commit.  For Phase 5h all seven cases are deferred together.
 *
 * <p>Source: {@code test-suite/riskneutraldensitycalculator.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class RiskNeutralDensityCalculatorTest {

    private static final String REASON_MISSING =
            "Phase 5h.5 — requires BSMRNDCalculator + HestonRNDCalculator + "
            + "LocalVolRNDCalculator + SquareRootProcessRNDCalculator port "
            + "(Phase 2m carry-forward; only GBSMRND + CEVRND exist in Java).";

    private static final String REASON_CEV =
            "Phase 5h.5 — defer alongside the other RND calculators for a "
            + "unified RND cluster commit (CEVRNDCalculator exists but the "
            + "test fixture shares helpers with the missing classes).";

    @Ignore(REASON_MISSING)
    @Test
    public void testDensityAgainstOptionPrices() { fail("not implemented"); }

    @Ignore(REASON_MISSING)
    @Test
    public void testBSMagainstHestonRND() { fail("not implemented"); }

    @Ignore(REASON_MISSING)
    @Test
    public void testLocalVolatilityRND() { fail("not implemented"); }

    @Ignore(REASON_MISSING)
    @Test
    public void testSquareRootProcessRND() { fail("not implemented"); }

    @Ignore(REASON_MISSING)
    @Test
    public void testBlackScholesWithSkew() { fail("not implemented"); }

    @Ignore(REASON_CEV)
    @Test
    public void testMassAtZeroCEVProcessRND() { fail("not implemented"); }

    @Ignore(REASON_CEV)
    @Test
    public void testCEVCDF() { fail("not implemented"); }
}
