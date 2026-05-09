/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.termstructures.volatilities;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/swaptionvolatilitymatrix.cpp}
 * v1.42.1 (364 LOC, 2 test cases).
 *
 * <p><strong>Both cases deferred to Phase 5f.5</strong> — Java has only
 * the base {@link org.jquantlib.termstructures.SwaptionVolatilityStructure}
 * abstract class; concrete {@code SwaptionVolatilityMatrix} (constant /
 * bilinear-interpolated grid by option-tenor x swap-tenor) is not yet
 * ported.
 *
 * <ul>
 *   <li>{@code testSwaptionVolMatrixCoherence} — two-axis interpolation
 *       coherence: ATM vol read at exact grid nodes equals the input
 *       quotes; off-node reads interpolate consistently.</li>
 *   <li>{@code testSwaptionVolMatrixObservability} — matrix observers
 *       trigger recomputation when input quote handles change.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/swaptionvolatilitymatrix.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class SwaptionVolatilityMatrixTest {

    @Ignore("Phase 5f.5 — SwaptionVolatilityMatrix concrete class not ported")
    @Test
    public void testSwaptionVolMatrixCoherence() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — SwaptionVolatilityMatrix observability not ported")
    @Test
    public void testSwaptionVolMatrixObservability() { fail("not implemented"); }
}
