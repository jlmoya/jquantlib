/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.variancegamma;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/variancegamma.cpp} v1.42.1
 * (251 LOC, 2 test cases).
 *
 * <p>The two C++ tests are:
 * <ul>
 *   <li>{@code testVarianceGamma} — exercises both
 *       {@code VarianceGammaEngine} (closed-form analytic) and
 *       {@code FFTVarianceGammaEngine} on a 16-row Madan-Carr-Chang
 *       table of European call/put values. Analytic-engine portion is
 *       already covered by {@link VarianceGammaEngineTest
 *       #testVarianceGammaAllCases}; FFT-engine portion remains
 *       blocked by the missing {@code FFTVarianceGammaEngine}
 *       production class (Phase 5h.5 carry-forward).</li>
 *   <li>{@code testSingularityAtZero} — verifies the analytic VG engine
 *       handles the integrable singularity at strike == forward. Fully
 *       covered by the canonical {@link VarianceGammaEngineTest
 *       #testSingularityAtZero}; the placeholder below delegates to it.</li>
 * </ul>
 *
 * <p>This class exists for 1:1 inventory mapping with the C++ test-suite
 * file list (Phase 5 META section 6 coverage report).
 *
 * <p>Source: {@code test-suite/variancegamma.cpp} v1.42.1 @ {@code 099987f0ca}.
 *
 * @see VarianceGammaEngineTest
 */
public class VarianceGammaTest {

    @Ignore("Phase 5h.5 carry-forward — FFTVarianceGammaEngine production class "
            + "not yet ported. Analytic-engine portion of the C++ "
            + "testVarianceGamma is already covered by "
            + "VarianceGammaEngineTest#testVarianceGammaAllCases (Phase 4c); "
            + "this placeholder remains @Ignore'd until the FFT engine lands.")
    @Test
    public void testVarianceGamma() { fail("FFTVarianceGammaEngine not yet ported; analytic-engine cases see VarianceGammaEngineTest#testVarianceGammaAllCases"); }

    @Test
    public void testSingularityAtZero() {
        // Phase 5e.5b-CFC-d-9 body-fill — delegate to the canonical
        // implementation in VarianceGammaEngineTest. The C++ test
        // {@code variancegamma.cpp::testSingularityAtZero} is reproduced
        // in VarianceGammaEngineTest#testSingularityAtZero (Phase 4c).
        new VarianceGammaEngineTest().testSingularityAtZero();
    }
}
