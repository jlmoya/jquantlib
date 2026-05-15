/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines;

import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/jumpdiffusion.cpp} v1.42.1
 * (524 LOC, 2 test cases).
 *
 * <p>The two C++ tests are:
 * <ul>
 *   <li>{@code testMerton76} — exercises {@code Merton76Process} +
 *       {@code JumpDiffusionEngine} on the Haug "Option pricing
 *       formulas" reference table (108 rows: gamma in {0.25, 0.5, 0.75},
 *       strike in {80, 90, 100, 110, 120}, intensity in {1, 5, 10},
 *       maturity in {0.10, 0.25, 0.50}). Tolerance 1e-2.</li>
 *   <li>{@code testGreeks} — exercises Greeks (delta, gamma, theta, rho,
 *       divRho, vega, strikeSensitivity) of jump-diffusion European
 *       options across a parameter grid. Tolerances 1e-4 to 1.1e-4.</li>
 * </ul>
 *
 * <p>Both tests are <strong>already covered</strong> by the existing Java
 * class {@link JumpDiffusionEngineTest}, which contains both
 * {@code testMerton76} (full Haug table) and {@code testGreeks} (parametric
 * grid). To keep the 1:1 inventory mapping with the C++ test-suite file
 * list (Phase 5 META section 6 coverage report) while still surfacing the
 * coverage in JUnit reports, both methods here delegate to the canonical
 * implementation in {@link JumpDiffusionEngineTest}.
 *
 * <p>Source: {@code test-suite/jumpdiffusion.cpp} v1.42.1 @ {@code 099987f0ca}.
 *
 * @see JumpDiffusionEngineTest
 */
public class JumpDiffusionTest {

    @Test
    public void testMerton76() {
        // Phase 5e.5b-CFC-d-9 body-fill — delegate to the canonical
        // implementation in JumpDiffusionEngineTest. The C++ test
        // {@code jumpdiffusion.cpp::testMerton76} is reproduced verbatim
        // (Haug 108-row table) in JumpDiffusionEngineTest#testMerton76.
        new JumpDiffusionEngineTest().testMerton76();
    }

    @Test
    public void testGreeks() {
        // Phase 5e.5b-CFC-d-9 body-fill — delegate to the canonical
        // implementation in JumpDiffusionEngineTest. The C++ test
        // {@code jumpdiffusion.cpp::testGreeks} (parametric grid +
        // numerical-derivative perturbations) is reproduced in
        // JumpDiffusionEngineTest#testGreeks.
        new JumpDiffusionEngineTest().testGreeks();
    }
}
