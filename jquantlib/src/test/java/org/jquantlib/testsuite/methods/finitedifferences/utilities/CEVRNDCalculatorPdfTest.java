/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences.utilities;

import org.jquantlib.methods.finitedifferences.utilities.CEVRNDCalculator;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Phase Production-Audit tests for {@link CEVRNDCalculator#pdf(double, double)}.
 *
 * <p>The {@code pdf} method was missing from the JQuantLib port (only
 * {@code cdf}, {@code invcdf}, and {@code massAtZero} were ported). This
 * test pins the new method against C++ QuantLib v1.42.1 reference values
 * captured at
 * {@code migration-harness/references/methods/finitedifferences/utilities/cev_rnd_calculator_pdf.json}.
 *
 * <p>Reference values were generated via a standalone Boost-only harness
 * mirroring v1.42.1 {@code cevrndcalculator.cpp} {@code pdf()} verbatim
 * (see the JSON file's {@code generated_by} field).
 *
 * <p>Tolerance: relative {@code 1e-9} (TIGHT analytic), with absolute
 * {@code 1e-12} floor near zero.
 *
 * @author Phase Production-Audit
 */
public class CEVRNDCalculatorPdfTest {

    private static final double REL_TOL = 1.0e-9;
    private static final double ABS_TOL = 1.0e-12;

    @Test
    public void testPdfAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "methods/finitedifferences/utilities/cev_rnd_calculator_pdf");

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final JSONObject in = c.inputs();
            final double f0    = in.getDouble("f0");
            final double alpha = in.getDouble("alpha");
            final double beta  = in.getDouble("beta");
            final double f     = in.getDouble("f");
            final double t     = in.getDouble("t");

            final JSONObject exp = (JSONObject) c.expectedRaw();
            final double pdfExp = exp.getDouble("pdf");

            final CEVRNDCalculator calc = new CEVRNDCalculator(f0, alpha, beta);
            final double pdfAct = calc.pdf(f, t);

            assertEquals(name + " pdf",
                    pdfExp, pdfAct,
                    Math.max(ABS_TOL, Math.abs(pdfExp) * REL_TOL));
        }
    }

    /** Sanity: pdf is non-negative everywhere on the support. */
    @Test
    public void testPdfNonNegative() {
        // Case A parameters from the probe (delta=0).
        final CEVRNDCalculator calc = new CEVRNDCalculator(100.0, 2.0, 0.5);
        for (double f = 30.0; f <= 200.0; f += 5.0) {
            final double pdf = calc.pdf(f, 1.0);
            assertTrue("pdf >= 0 at f=" + f + " got " + pdf, pdf >= -ABS_TOL);
        }
    }

    /** Sanity: pdf is non-negative for delta >= 2 case as well. */
    @Test
    public void testPdfNonNegativeDeltaGE2() {
        final CEVRNDCalculator calc = new CEVRNDCalculator(100.0, 0.05, 1.5);
        for (double f = 50.0; f <= 200.0; f += 10.0) {
            final double pdf = calc.pdf(f, 0.5);
            assertTrue("pdf >= 0 at f=" + f + " got " + pdf, pdf >= -ABS_TOL);
        }
    }
}
