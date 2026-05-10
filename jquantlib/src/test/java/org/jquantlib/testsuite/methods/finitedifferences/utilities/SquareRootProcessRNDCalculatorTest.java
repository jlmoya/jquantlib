/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences.utilities;

import org.jquantlib.methods.finitedifferences.utilities.SquareRootProcessRNDCalculator;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Phase 5h.5-RND tests for {@link SquareRootProcessRNDCalculator}.
 *
 * <p>Cross-validated against C++ QuantLib v1.42.1 via the migration harness;
 * reference data lives at
 * {@code migration-harness/references/methods/finitedifferences/utilities/square_root_process_rnd_calculator.json}.
 *
 * <p>Tolerances:
 * <ul>
 *   <li>conditional cdf: TIGHT 1e-9 abs/rel (delegates to JQuantLib's
 *       {@code NonCentralCumulativeChiSquaredDistribution}).</li>
 *   <li>conditional pdf: LOOSE 1e-4 (CDF central-difference; JQuantLib has no
 *       native non-central chi-squared PDF / modified Bessel functions).</li>
 *   <li>conditional invcdf: LOOSE 1e-6 abs (Brent-based inverse).</li>
 *   <li>stationary pdf: TIGHT 1e-9 (closed-form gamma density).</li>
 *   <li>stationary cdf: LOOSE 1e-7 (JQuantLib's GammaDistribution is
 *       ~5e-9 off Boost in some regimes — see test failure log).</li>
 *   <li>stationary invcdf: LOOSE 1e-5 (Brent fallback for {@code gamma_p_inv}).</li>
 * </ul>
 *
 * @author Phase 5h.5-RND port
 */
public class SquareRootProcessRNDCalculatorTest {

    private static final double TOL_TIGHT      = 1.0e-9;
    private static final double TOL_LOOSE      = 1.0e-6;
    private static final double TOL_PDF_FD     = 1.0e-4;  // CDF finite-difference for PDF
    private static final double TOL_GAMMA_LOOSE = 1.0e-7; // Java GammaDistribution vs Boost

    private SquareRootProcessRNDCalculator calc() {
        // Match the probe: v0=0.04, kappa=2.0, theta=0.04, sigma=0.30
        return new SquareRootProcessRNDCalculator(0.04, 2.0, 0.04, 0.30);
    }

    @Test
    public void testConditionalPdfCdfAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "methods/finitedifferences/utilities/square_root_process_rnd_calculator");
        final SquareRootProcessRNDCalculator c = calc();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case rc = ref.getCase(name);
            final JSONObject in = rc.inputs();
            // Skip stationary + invcdf-only cases (have no t input).
            if (!in.has("t")) continue;

            final double v = in.getDouble("v");
            final double t = in.getDouble("t");
            final JSONObject exp = (JSONObject) rc.expectedRaw();

            final double pdfExp = exp.getDouble("pdf");
            final double cdfExp = exp.getDouble("cdf");

            assertEquals(name + " pdf", pdfExp, c.pdf(v, t),
                    Math.max(TOL_PDF_FD, Math.abs(pdfExp) * TOL_PDF_FD));
            assertEquals(name + " cdf", cdfExp, c.cdf(v, t),
                    Math.max(TOL_TIGHT, Math.abs(cdfExp) * TOL_TIGHT));
        }
    }

    @Test
    public void testConditionalInvcdfAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "methods/finitedifferences/utilities/square_root_process_rnd_calculator");
        final SquareRootProcessRNDCalculator c = calc();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case rc = ref.getCase(name);
            final JSONObject in = rc.inputs();
            if (!in.has("t")) continue;

            final double t = in.getDouble("t");
            final JSONObject exp = (JSONObject) rc.expectedRaw();
            // Round-trip: invcdf(cdf(v)) ≈ v
            final double cdfExp = exp.getDouble("cdf");
            final double invExp = exp.getDouble("invcdf");

            assertEquals(name + " invcdf", invExp, c.invcdf(cdfExp, t),
                    Math.max(TOL_LOOSE, Math.abs(invExp) * TOL_LOOSE));
        }
    }

    @Test
    public void testStationaryPdfCdfAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "methods/finitedifferences/utilities/square_root_process_rnd_calculator");
        final SquareRootProcessRNDCalculator c = calc();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case rc = ref.getCase(name);
            final JSONObject in = rc.inputs();
            if (!in.optBoolean("stationary", false)) continue;

            final double v = in.getDouble("v");
            final JSONObject exp = (JSONObject) rc.expectedRaw();

            assertEquals(name + " pdf", exp.getDouble("pdf"), c.stationary_pdf(v),
                    Math.max(TOL_TIGHT, Math.abs(exp.getDouble("pdf")) * TOL_TIGHT));
            assertEquals(name + " cdf", exp.getDouble("cdf"), c.stationary_cdf(v),
                    Math.max(TOL_GAMMA_LOOSE, Math.abs(exp.getDouble("cdf")) * TOL_GAMMA_LOOSE));
            assertEquals(name + " invcdf", exp.getDouble("invcdf"),
                    c.stationary_invcdf(exp.getDouble("cdf")),
                    Math.max(TOL_LOOSE, Math.abs(exp.getDouble("invcdf")) * TOL_LOOSE));
        }
    }

    @Test
    public void testStationaryInvcdfAtFixedQuantiles() {
        final ReferenceReader ref = ReferenceReader.load(
                "methods/finitedifferences/utilities/square_root_process_rnd_calculator");
        final SquareRootProcessRNDCalculator c = calc();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case rc = ref.getCase(name);
            final JSONObject in = rc.inputs();
            if (!in.optBoolean("stationary_invcdf", false)) continue;

            final double q = in.getDouble("q");
            final JSONObject exp = (JSONObject) rc.expectedRaw();
            final double invExp = exp.getDouble("invcdf");

            assertEquals(name + " stationary_invcdf", invExp, c.stationary_invcdf(q),
                    Math.max(TOL_LOOSE, Math.abs(invExp) * TOL_LOOSE));
        }
    }
}
