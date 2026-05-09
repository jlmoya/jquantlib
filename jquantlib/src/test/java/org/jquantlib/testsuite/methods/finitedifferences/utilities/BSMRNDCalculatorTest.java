/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences.utilities;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.methods.finitedifferences.utilities.BSMRNDCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Phase 5h.5-RND tests for {@link BSMRNDCalculator}.
 *
 * <p>Cross-validated against C++ QuantLib v1.42.1 via the migration harness;
 * reference data lives at
 * {@code migration-harness/references/methods/finitedifferences/utilities/bsm_rnd_calculator.json}.
 *
 * @author Phase 5h.5-RND port
 */
public class BSMRNDCalculatorTest {

    private static final double TOL = 1.0e-9;        // analytic — TIGHT

    private BSMRNDCalculator buildCalc() {
        // Match the probe setup exactly:
        // S0=100, r=0.05, q=0.02, vol=0.20, today=15-Jan-2026, dc=Actual365Fixed.
        final DayCounter dc   = new Actual365Fixed();
        final Date today      = new Date(15, Month.January, 2026);

        final Handle<Quote> spot = new Handle<>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.05)), dc));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.02)), dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(0.20)), dc));

        final GeneralizedBlackScholesProcess proc =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
        return new BSMRNDCalculator(proc);
    }

    @Test
    public void testPdfCdfAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "methods/finitedifferences/utilities/bsm_rnd_calculator");
        final BSMRNDCalculator calc = buildCalc();

        for (final String name : ref.caseNames()) {
            // Skip pure-invcdf cases (have no x input).
            final ReferenceReader.Case c = ref.getCase(name);
            final JSONObject in = c.inputs();
            if (!in.has("x")) continue;

            final double x = in.getDouble("x");
            final double t = in.getDouble("t");
            final JSONObject exp = (JSONObject) c.expectedRaw();

            final double pdfExp = exp.getDouble("pdf");
            final double cdfExp = exp.getDouble("cdf");

            final double pdfAct = calc.pdf(x, t);
            final double cdfAct = calc.cdf(x, t);

            assertEquals(name + " pdf",  pdfExp, pdfAct, Math.max(TOL, Math.abs(pdfExp) * TOL));
            assertEquals(name + " cdf",  cdfExp, cdfAct, Math.max(TOL, Math.abs(cdfExp) * TOL));
        }
    }

    @Test
    public void testInvcdfAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "methods/finitedifferences/utilities/bsm_rnd_calculator");
        final BSMRNDCalculator calc = buildCalc();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final JSONObject in = c.inputs();
            // Pure invcdf cases keyed by "q" (no x); handle both shapes.
            final double q;
            final double t = in.getDouble("t");
            if (in.has("q")) {
                q = in.getDouble("q");
            } else {
                q = in.getDouble("q_for_invcdf");
            }
            final JSONObject exp = (JSONObject) c.expectedRaw();
            final double invExp = exp.getDouble("invcdf");

            final double invAct = calc.invcdf(q, t);
            assertEquals(name + " invcdf", invExp, invAct,
                    Math.max(TOL, Math.abs(invExp) * TOL));
        }
    }

    @Test
    public void testCdfMonotone() {
        // Sanity: CDF must be monotone non-decreasing across log-spot grid.
        final BSMRNDCalculator calc = buildCalc();
        double prev = -1.0;
        for (double x = 3.0; x <= 6.0; x += 0.05) {
            final double v = calc.cdf(x, 1.0);
            assertTrue("cdf monotone at x=" + x + " (prev=" + prev + " v=" + v + ")",
                    v >= prev - 1e-12);
            prev = v;
        }
    }
}
