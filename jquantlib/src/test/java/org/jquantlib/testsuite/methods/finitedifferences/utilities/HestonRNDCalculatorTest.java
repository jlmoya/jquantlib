/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences.utilities;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.methods.finitedifferences.utilities.HestonRNDCalculator;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Phase 5h.5-RND tests for {@link HestonRNDCalculator}.
 *
 * <p>Cross-validated against C++ QuantLib v1.42.1 via the migration harness;
 * reference data lives at
 * {@code migration-harness/references/methods/finitedifferences/utilities/heston_rnd_calculator.json}.
 *
 * <p>Tolerance: LOOSE 1e-5 (Fourier-inversion via GaussLobattoIntegral and
 * complex-number arithmetic accumulate ULPs differently between Boost and
 * JQuantLib's Complex; default integrationEps is 1e-6).
 *
 * @author Phase 5h.5-RND port
 */
public class HestonRNDCalculatorTest {

    private static final double TOL = 1.0e-5;

    private HestonRNDCalculator buildCalc() {
        // Match the probe: S0=100, v0=0.04, kappa=2.0, theta=0.04, sigma=0.30, rho=-0.5
        // r=0.05, q=0.02
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(15, Month.January, 2026);

        final var spot = new Handle<Quote>(new SimpleQuote(100.0));
        final var rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.05)), dc));
        final var qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.02)), dc));

        final HestonProcess proc = new HestonProcess(rTS, qTS, spot,
                0.04, 2.0, 0.04, 0.30, -0.5);
        return new HestonRNDCalculator(proc);
    }

    @Test
    public void testPdfCdfAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "methods/finitedifferences/utilities/heston_rnd_calculator");
        final HestonRNDCalculator calc = buildCalc();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case rc = ref.getCase(name);
            final JSONObject in = rc.inputs();
            if (!in.has("x")) continue;

            final double x = in.getDouble("x");
            final double t = in.getDouble("t");
            final JSONObject exp = (JSONObject) rc.expectedRaw();

            assertEquals(name + " pdf", exp.getDouble("pdf"), calc.pdf(x, t),
                    Math.max(TOL, Math.abs(exp.getDouble("pdf")) * TOL));
            assertEquals(name + " cdf", exp.getDouble("cdf"), calc.cdf(x, t),
                    Math.max(TOL, Math.abs(exp.getDouble("cdf")) * TOL));
        }
    }

    @Test
    public void testInvcdfAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "methods/finitedifferences/utilities/heston_rnd_calculator");
        final HestonRNDCalculator calc = buildCalc();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case rc = ref.getCase(name);
            final JSONObject in = rc.inputs();
            if (!in.has("q")) continue;

            final double q = in.getDouble("q");
            final double t = in.getDouble("t");
            final JSONObject exp = (JSONObject) rc.expectedRaw();
            final double invExp = exp.getDouble("invcdf");

            assertEquals(name + " invcdf", invExp, calc.invcdf(q, t),
                    Math.max(TOL, Math.abs(invExp) * TOL));
        }
    }
}
