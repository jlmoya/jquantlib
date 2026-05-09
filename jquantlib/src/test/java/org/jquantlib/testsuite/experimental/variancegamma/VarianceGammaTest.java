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
 *       table of European call/put values.</li>
 *   <li>{@code testSingularityAtZero} — verifies the analytic VG engine
 *       handles the integrable singularity at strike == forward.</li>
 * </ul>
 *
 * <p>Both tests are <strong>already covered</strong> by the existing Java
 * class {@code org.jquantlib.testsuite.experimental.variancegamma
 * .VarianceGammaEngineTest} (Phase 4c, methods
 * {@code testVarianceGammaAllCases} and {@code testSingularityAtZero}),
 * which reproduces the same Madan-Carr-Chang reference table and the
 * singularity probe.
 *
 * <p>FFT engine ({@code FFTVarianceGammaEngine}) is <strong>not yet
 * ported</strong> — tracked as Phase 5h.5 carry-forward.
 *
 * <p>This class exists for 1:1 inventory mapping with the C++ test-suite
 * file list (Phase 5 META section 6 coverage report).
 *
 * <p>Source: {@code test-suite/variancegamma.cpp} v1.42.1 @ {@code 099987f0ca}.
 *
 * @see org.jquantlib.testsuite.experimental.variancegamma.VarianceGammaEngineTest
 */
public class VarianceGammaTest {

    @Ignore("Phase 5h — analytic engine portion already covered by "
            + "VarianceGammaEngineTest#testVarianceGammaAllCases (Phase 4c). "
            + "FFTVarianceGammaEngine port deferred to Phase 5h.5.")
    @Test
    public void testVarianceGamma() { fail("not implemented; see VarianceGammaEngineTest"); }

    @Ignore("Phase 5h — already covered by "
            + "VarianceGammaEngineTest#testSingularityAtZero (Phase 4c).")
    @Test
    public void testSingularityAtZero() { fail("not implemented; see VarianceGammaEngineTest"); }
}
