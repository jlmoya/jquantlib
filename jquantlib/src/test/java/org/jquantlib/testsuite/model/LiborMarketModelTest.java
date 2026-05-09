/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/libormarketmodel.cpp} v1.42.1
 * (465 LOC, 4 test cases).
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong> — the QL
 * {@code LiborForwardModel} (LFM, BGM-style LIBOR market model)
 * production class is not present in Java.  Java has supporting infra
 * ({@link org.jquantlib.processes.LiborForwardModelProcess}, {@link
 * org.jquantlib.processes.LfmCovarianceParameterization} and the
 * legacy {@code legacy/libormarkets/LfmCovarianceProxy}) but no
 * end-to-end calibratable {@code LiborForwardModel} class.
 *
 * <ul>
 *   <li>{@code testSimpleCovarianceModels} — covariance parameterisations
 *       (constant, exponential-decay, abcd) yield correct integrated
 *       variances.</li>
 *   <li>{@code testCapletPricing} — analytic LFM caplet pricing
 *       matches Black formula.</li>
 *   <li>{@code testCalibration} — LFM calibration to caplet vol surface
 *       converges (Levenberg-Marquardt).</li>
 *   <li>{@code testSwaptionPricing} — analytic LFM swaption pricing
 *       (Brace-Gatarek-Musiela approximation).</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/libormarketmodel.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class LiborMarketModelTest {

    @Ignore("Phase 5f.5 — LFM covariance integration not exposed")
    @Test
    public void testSimpleCovarianceModels() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — LiborForwardModel caplet pricer not ported")
    @Test
    public void testCapletPricing() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — LiborForwardModel calibration loop not ported")
    @Test
    public void testCalibration() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — LiborForwardModel BGM swaption pricer not ported")
    @Test
    public void testSwaptionPricing() { fail("not implemented"); }
}
