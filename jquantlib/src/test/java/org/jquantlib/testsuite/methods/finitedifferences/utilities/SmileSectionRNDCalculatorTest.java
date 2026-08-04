/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.methods.finitedifferences.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.volatility.SviSmileSection;
import org.jquantlib.methods.finitedifferences.utilities.SmileSectionRNDCalculator;
import org.jquantlib.termstructures.volatilities.AtmSmileSection;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Cross-validates {@link SmileSectionRNDCalculator} — new in C++ QuantLib v1.43 — against the
 * {@code methods/v143_smilesection_rnd} probe.
 * <p>
 * The probe's cases carry the smile, the grid parameters and the abscissa, so the sweep below reconstructs each one.
 * Four smiles are covered: a flat section and three SVI shapes, including a steep one where the tails are where a
 * wrong finite-difference gap or a mishandled grid shows up first.
 * <p>
 * Two properties the class is easy to get wrong are asserted directly rather than inferred: {@code pdf}/{@code cdf}
 * must be independent of {@code nStrikes}/{@code nStd} (they never build the grid), and {@code invcdf} at
 * probabilities beyond the surviving CDF range must clamp to the retained grid endpoints rather than extrapolate.
 *
 * @author Jose Moya
 */
public class SmileSectionRNDCalculatorTest {

    /** TIGHT tier for everything that is not a finite difference. */
    private static final double REL_TOL = 1.0e-10;
    private static final double ABS_TOL = 1.0e-12;

    /**
     * {@code pdf} and {@code cdf} are finite differences of option prices, so how well they can agree across
     * implementations is bounded by cancellation, not by how faithful the port is.
     * <p>
     * Two cancellations stack. First, each Black price is itself a difference of terms of order
     * {@code max(forward, strike)}, so its absolute error is about {@code eps·max(F, K)} — for a 2.8 option struck
     * off a 100 forward that is ~35x worse than {@code eps·|C|}. Second, {@code cdf} divides a first difference by
     * {@code gap = 1e-5} and {@code pdf} divides a <em>second</em> difference by {@code gap² = 1e-8} and then scales
     * by {@code S}. The resulting floors are:
     * <pre>
     *   cdf:  2·eps·max(F, S) / gap
     *   pdf:  4·eps·max(F, S)·S / gap²
     * </pre>
     * At {@code S = 116, F = 100} that is ~1.2e-3 on a pdf of ~1.39 — and the disagreement actually observed against
     * C++ is 3.6e-5, comfortably inside it. Both sides evaluate the same expression and simply lose the same digits
     * differently.
     * <p>
     * These two are therefore asserted against a bound derived per case from the case's own forward and strike,
     * rather than a blanket loose tier. A real porting error — wrong gap, wrong sign, wrong smile — moves these by
     * percent, so nothing is given up.
     */
    private static final double CDF_GAP = 1.0e-5;
    private static final double PDF_GAP = 1.0e-4;
    private static final double EPS = Math.ulp(1.0);

    /** Absolute floor on {@code cdf} at strike {@code s} for a smile with the given forward. */
    private static double cdfBound(final double forward, final double s) {
        return 2.0 * EPS * Math.max(forward, s) / CDF_GAP;
    }

    /** Absolute floor on {@code pdf} at strike {@code s} for a smile with the given forward. */
    private static double pdfBound(final double forward, final double s) {
        return 4.0 * EPS * Math.max(forward, s) * s / (PDF_GAP * PDF_GAP);
    }

    /**
     * Absolute floor on {@code invcdf}, obtained by propagating the {@code cdf} floor through the quantile slope.
     * <p>
     * The spline is built through CDF values that each carry {@link #cdfBound}; inverting it turns a CDF error into a
     * log-strike error divided by the density there, {@code d(log K)/dp = 1 / pdf(log K)}. Using the calculator's own
     * {@code pdf} at the returned quantile is exactly that factor, which is why the bound widens automatically in the
     * tails — where the density is small and the quantile is genuinely ill-conditioned — and stays tight near the
     * middle.
     */
    private static double invcdfBound(final SmileSectionRNDCalculator rnd, final double forward,
            final double logStrike) {
        final double density = Math.abs(rnd.pdf(logStrike));
        if ( density < 1.0e-12 ) {
            // Degenerate slope: fall back to the relative tier rather than dividing by ~0.
            return Math.max(ABS_TOL, REL_TOL * Math.abs(logStrike));
        }
        return cdfBound(forward, Math.exp(logStrike)) / density;
    }

    private static final Date TODAY = new Date(1, Month.March, 2025);
    private static final Date MATURITY = new Date(1, Month.March, 2026);

    private Date savedEvaluationDate;

    @Before
    public void setUp() {
        savedEvaluationDate = new Settings().evaluationDate();
        new Settings().setEvaluationDate(TODAY);
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvaluationDate);
    }

    private static DayCounter dayCounter() {
        return new Actual365Fixed();
    }

    private static ReferenceReader ref() {
        return ReferenceReader.load("methods/v143_smilesection_rnd");
    }

    private static void assertClose(final String what, final double expected, final double actual) {
        assertEquals(what, expected, actual, Math.max(ABS_TOL, REL_TOL * Math.abs(expected)));
    }

    //
    // smiles — mirror the probe's definitions exactly
    //

    private static SmileSection flatSmile(final double atm) {
        return new FlatSmileSection(MATURITY, 0.20, dayCounter(), TODAY, atm);
    }

    private static SmileSection flatSmileWithoutAtm() {
        return new FlatSmileSection(MATURITY, 0.20, dayCounter(), TODAY);
    }

    private static SmileSection sviSmile(final double forward, final double[] params) {
        final double t = dayCounter().yearFraction(TODAY, MATURITY);
        return new SviSmileSection(t, forward, params);
    }

    private static SmileSection smileFor(final String name) {
        switch ( name ) {
        case "flat":
            return flatSmile(100.0);
        case "svi1":
            return sviSmile(100.0, new double[] { 0.04, 0.10, 0.30, -0.40, 0.0 });
        case "svi2":
            return sviSmile(96.0, new double[] { 0.02, 0.08, 0.25, -0.30, 0.0 });
        case "svi_steep":
            return sviSmile(100.0, new double[] { 0.03, 0.25, 0.15, -0.75, -0.10 });
        default:
            throw new IllegalArgumentException("unknown smile: " + name);
        }
    }

    private static SmileSectionRNDCalculator calculatorFor(final JSONObject in) {
        return new SmileSectionRNDCalculator(smileFor(in.getString("smile")), in.getInt("n_strikes"),
                in.getDouble("n_std"));
    }

    //
    // tests
    //

    /**
     * Density and cumulative probability across the strike ladder, on every smile.
     */
    @Test
    public void testPdfAndCdf() {
        QL.info("Testing SmileSectionRNDCalculator pdf/cdf against C++ v1.43...");
        int checked = 0;
        for ( final String name : ref().caseNames() ) {
            if ( !name.contains("_pdf_cdf_") ) {
                continue;
            }
            final JSONObject in = ref().getCase(name).inputs();
            final JSONObject out = (JSONObject) ref().getCase(name).expectedRaw();
            final SmileSectionRNDCalculator rnd = calculatorFor(in);

            final double x = in.getDouble("x");
            final double t = in.getDouble("t");
            final double fwd = in.getDouble("forward");
            final double s = Math.exp(x);

            assertEquals(name + ": pdf", out.getDouble("pdf"), rnd.pdf(x, t),
                    Math.max(ABS_TOL, pdfBound(fwd, s)));
            assertEquals(name + ": cdf", out.getDouble("cdf"), rnd.cdf(x, t),
                    Math.max(ABS_TOL, cdfBound(fwd, s)));
            ++checked;
        }
        assertTrue("expected pdf/cdf cases in the reference", checked >= 40);
    }

    /**
     * The quantile function across the probability ladder, including the extreme tails where the grid clamp bites.
     */
    @Test
    public void testInvCdf() {
        QL.info("Testing SmileSectionRNDCalculator invcdf against C++ v1.43...");
        int checked = 0;
        for ( final String name : ref().caseNames() ) {
            if ( !name.contains("_invcdf_") || name.contains("rejects") ) {
                continue;
            }
            final JSONObject in = ref().getCase(name).inputs();
            final JSONObject out = (JSONObject) ref().getCase(name).expectedRaw();
            final SmileSectionRNDCalculator rnd = calculatorFor(in);

            final double q = rnd.invcdf(in.getDouble("p"), in.getDouble("t"));
            final double fwd = in.getDouble("forward");
            final double tol = Math.max(ABS_TOL, invcdfBound(rnd, fwd, q));
            assertEquals(name + ": invcdf", out.getDouble("invcdf"), q, tol);
            if ( out.has("strike") ) {
                assertEquals(name + ": strike", out.getDouble("strike"), Math.exp(q), Math.exp(q) * tol);
            }
            ++checked;
        }
        assertTrue("expected invcdf cases in the reference", checked >= 40);
    }

    /**
     * The grid endpoints. Because the CDF grid is made monotone and then deduplicated, {@code invcdf} at the extreme
     * probabilities returns exactly the surviving first and last strike — the sharpest available pin on how the grid
     * was built, since the grid itself is private.
     */
    @Test
    public void testGridEndpoints() {
        QL.info("Testing the SmileSectionRNDCalculator grid endpoints against C++ v1.43...");
        int checked = 0;
        for ( final String name : ref().caseNames() ) {
            if ( !name.endsWith("_grid_endpoints") ) {
                continue;
            }
            final JSONObject in = ref().getCase(name).inputs();
            final JSONObject out = (JSONObject) ref().getCase(name).expectedRaw();
            final SmileSection smile = smileFor(in.getString("smile"));
            final SmileSectionRNDCalculator rnd = calculatorFor(in);

            assertClose(name + ": atm level", out.getDouble("atm_level"), smile.atmLevel());
            assertClose(name + ": exercise time", out.getDouble("exercise_time"), smile.exerciseTime());
            assertClose(name + ": sigma atm", out.getDouble("sigma_atm"), smile.volatility(smile.atmLevel()));

            final double tiny = 1.0e-12;
            assertClose(name + ": invcdf at p min", out.getDouble("invcdf_at_p_min"), rnd.invcdf(tiny));
            assertClose(name + ": invcdf at p max", out.getDouble("invcdf_at_p_max"), rnd.invcdf(1.0 - tiny));
            assertClose(name + ": strike at p min", out.getDouble("strike_at_p_min"), Math.exp(rnd.invcdf(tiny)));
            assertClose(name + ": strike at p max", out.getDouble("strike_at_p_max"),
                    Math.exp(rnd.invcdf(1.0 - tiny)));
            ++checked;
        }
        assertTrue("expected grid-endpoint cases in the reference", checked >= 4);
    }

    /**
     * The one-argument overloads must agree with the two-argument ones evaluated at the smile's own exercise time.
     */
    @Test
    public void testOverloadsAgree() {
        QL.info("Testing SmileSectionRNDCalculator overload agreement against C++ v1.43...");
        for ( final String name : ref().caseNames() ) {
            if ( !name.endsWith("_overloads_agree") ) {
                continue;
            }
            final JSONObject in = ref().getCase(name).inputs();
            final JSONObject out = (JSONObject) ref().getCase(name).expectedRaw();
            final SmileSectionRNDCalculator rnd = calculatorFor(in);

            final double x = in.getDouble("x");
            final double p = in.getDouble("p");
            final double t = in.getDouble("t");

            final double fwd = in.getDouble("forward");
            final double s = Math.exp(x);
            final double pdfTol = Math.max(ABS_TOL, pdfBound(fwd, s));
            final double cdfTol = Math.max(ABS_TOL, cdfBound(fwd, s));

            assertEquals(name + ": pdf 1-arg", out.getDouble("pdf_1arg"), rnd.pdf(x), pdfTol);
            assertEquals(name + ": pdf 2-arg", out.getDouble("pdf_2arg"), rnd.pdf(x, t), pdfTol);
            assertEquals(name + ": cdf 1-arg", out.getDouble("cdf_1arg"), rnd.cdf(x), cdfTol);
            assertEquals(name + ": cdf 2-arg", out.getDouble("cdf_2arg"), rnd.cdf(x, t), cdfTol);
            final double invTol = Math.max(ABS_TOL, invcdfBound(rnd, fwd, rnd.invcdf(p)));
            assertEquals(name + ": invcdf 1-arg", out.getDouble("invcdf_1arg"), rnd.invcdf(p), invTol);
            assertEquals(name + ": invcdf 2-arg", out.getDouble("invcdf_2arg"), rnd.invcdf(p, t), invTol);

            // The two overloads must agree with each other exactly — that is the actual claim of this case, and it is
            // unaffected by how well either agrees with C++.
            assertEquals(name + ": overloads must agree bit for bit", rnd.pdf(x), rnd.pdf(x, t), 0.0);
            assertEquals(name + ": overloads must agree bit for bit", rnd.cdf(x), rnd.cdf(x, t), 0.0);
            assertEquals(name + ": overloads must agree bit for bit", rnd.invcdf(p), rnd.invcdf(p, t), 0.0);
        }
    }

    /**
     * {@code pdf} and {@code cdf} read the smile directly and never build the quantile grid, so wildly different
     * {@code nStrikes}/{@code nStd} must give identical answers. A port that eagerly initializes and then interpolates
     * would fail here while passing every other case.
     */
    @Test
    public void testPdfAndCdfAreGridIndependent() {
        QL.info("Testing that SmileSectionRNDCalculator pdf/cdf ignore the grid, per C++ v1.43...");
        for ( final String name : ref().caseNames() ) {
            if ( !name.endsWith("_grid_independent_pdf_cdf") ) {
                continue;
            }
            final JSONObject in = ref().getCase(name).inputs();
            final JSONObject out = (JSONObject) ref().getCase(name).expectedRaw();
            final JSONObject ga = in.getJSONObject("grid_a");
            final JSONObject gb = in.getJSONObject("grid_b");
            final double x = in.getDouble("x");
            final double t = in.getDouble("t");

            final SmileSectionRNDCalculator a = new SmileSectionRNDCalculator(smileFor(in.getString("smile")),
                    ga.getInt("n_strikes"), ga.getDouble("n_std"));
            final SmileSectionRNDCalculator b = new SmileSectionRNDCalculator(smileFor(in.getString("smile")),
                    gb.getInt("n_strikes"), gb.getDouble("n_std"));

            final double fwd = in.getDouble("forward");
            final double s = Math.exp(x);
            final double pdfTol = Math.max(ABS_TOL, pdfBound(fwd, s));
            final double cdfTol = Math.max(ABS_TOL, cdfBound(fwd, s));

            assertEquals(name + ": pdf grid a", out.getDouble("pdf_grid_a"), a.pdf(x, t), pdfTol);
            assertEquals(name + ": pdf grid b", out.getDouble("pdf_grid_b"), b.pdf(x, t), pdfTol);
            assertEquals(name + ": cdf grid a", out.getDouble("cdf_grid_a"), a.cdf(x, t), cdfTol);
            assertEquals(name + ": cdf grid b", out.getDouble("cdf_grid_b"), b.cdf(x, t), cdfTol);

            // The real claim of this case: the grid parameters must not touch pdf/cdf at all. Asserted exactly,
            // between two calculators built with wildly different grids.
            assertEquals(name + ": pdf must not depend on the grid", a.pdf(x, t), b.pdf(x, t), 0.0);
            assertEquals(name + ": cdf must not depend on the grid", a.cdf(x, t), b.cdf(x, t), 0.0);
        }
    }

    /**
     * Wrapping an ATM-less smile in {@code AtmSmileSection} must reproduce the directly-built one exactly — that is
     * the documented way to satisfy the calculator's ATM requirement.
     */
    @Test
    public void testAtmSmileSectionWrappingMatchesDirect() {
        QL.info("Testing AtmSmileSection wrapping against C++ v1.43...");
        final String name = "flat_via_atmsmilesection_matches_direct";
        final JSONObject in = ref().getCase(name).inputs();
        final JSONObject out = (JSONObject) ref().getCase(name).expectedRaw();

        final SmileSectionRNDCalculator wrapped = new SmileSectionRNDCalculator(
                new AtmSmileSection(flatSmileWithoutAtm(), 100.0), in.getInt("n_strikes"), in.getDouble("n_std"));
        final SmileSectionRNDCalculator direct = new SmileSectionRNDCalculator(flatSmile(100.0),
                in.getInt("n_strikes"), in.getDouble("n_std"));

        for ( final String key : out.keySet() ) {
            if ( !(out.get(key) instanceof Number) ) {
                continue;
            }
            // The probe records paired wrapped/direct values under matching keys; comparing the two calculators
            // directly is the assertion that matters, and the reference pins the shared value.
            final double fwd = 100.0; // the ATM level the section is wrapped at
            if ( key.startsWith("invcdf") ) {
                final double p = in.getDouble("p");
                final double tol = Math.max(ABS_TOL, invcdfBound(direct, fwd, direct.invcdf(p)));
                assertEquals(name + ": " + key + " (wrapped)", out.getDouble(key), wrapped.invcdf(p), tol);
                assertEquals(name + ": " + key + " (direct)", out.getDouble(key), direct.invcdf(p), tol);
            } else if ( key.startsWith("cdf") ) {
                final double x = in.getDouble("x");
                final double tol = Math.max(ABS_TOL, cdfBound(fwd, Math.exp(x)));
                assertEquals(name + ": " + key + " (wrapped)", out.getDouble(key), wrapped.cdf(x), tol);
                assertEquals(name + ": " + key + " (direct)", out.getDouble(key), direct.cdf(x), tol);
            } else if ( key.startsWith("pdf") ) {
                final double x = in.getDouble("x");
                final double tol = Math.max(ABS_TOL, pdfBound(fwd, Math.exp(x)));
                assertEquals(name + ": " + key + " (wrapped)", out.getDouble(key), wrapped.pdf(x), tol);
                assertEquals(name + ": " + key + " (direct)", out.getDouble(key), direct.pdf(x), tol);
            }

            // Whatever the agreement with C++, wrapping must reproduce the direct construction exactly — that is the
            // claim this case actually makes.
            if ( key.startsWith("invcdf") ) {
                assertEquals(name + ": wrapping must be exact", direct.invcdf(in.getDouble("p")),
                        wrapped.invcdf(in.getDouble("p")), 0.0);
            }
        }
    }

    /**
     * Constructor and argument guards, including the order in which {@code invcdf} validates: the missing ATM level
     * is reported before the out-of-range probability, because the grid is built first.
     */
    @Test
    public void testGuards() {
        QL.info("Testing SmileSectionRNDCalculator argument validation against C++ v1.43...");

        assertThrowsPerReference("ctor_rejects_null_smile", () -> new SmileSectionRNDCalculator(null, 200, 5.0));
        assertThrowsPerReference("ctor_rejects_n_strikes_3",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0), 3, 5.0));
        assertThrowsPerReference("ctor_accepts_n_strikes_4",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0), 4, 5.0));
        assertThrowsPerReference("ctor_rejects_n_std_zero",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0), 200, 0.0));
        assertThrowsPerReference("ctor_rejects_n_std_negative",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0), 200, -1.0));

        assertThrowsPerReference("invcdf_rejects_p_zero",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0)).invcdf(0.0));
        assertThrowsPerReference("invcdf_rejects_p_one",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0)).invcdf(1.0));
        assertThrowsPerReference("invcdf_rejects_p_negative",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0)).invcdf(-1.0));

        assertThrowsPerReference("pdf_rejects_time_mismatch",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0)).pdf(4.6, 2.0));
        assertThrowsPerReference("cdf_rejects_time_mismatch",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0)).cdf(4.6, 2.0));
        assertThrowsPerReference("invcdf_rejects_time_mismatch",
                () -> new SmileSectionRNDCalculator(flatSmile(100.0)).invcdf(0.5, 2.0));

        assertThrowsPerReference("invcdf_rejects_missing_atm_level",
                () -> new SmileSectionRNDCalculator(flatSmileWithoutAtm()).invcdf(0.5));
        assertThrowsPerReference("cdf_rejects_missing_atm_level",
                () -> new SmileSectionRNDCalculator(flatSmileWithoutAtm()).cdf(4.6));
        assertThrowsPerReference("pdf_rejects_missing_atm_level",
                () -> new SmileSectionRNDCalculator(flatSmileWithoutAtm()).pdf(4.6));

        assertThrowsPerReference("invcdf_atm_check_precedes_p_check",
                () -> new SmileSectionRNDCalculator(flatSmileWithoutAtm()).invcdf(-1.0));
    }

    private static void assertThrowsPerReference(final String caseName, final Runnable r) {
        final JSONObject out = (JSONObject) ref().getCase(caseName).expectedRaw();
        final boolean expected = out.getBoolean("throws");
        boolean actual;
        try {
            r.run();
            actual = false;
        } catch ( final RuntimeException e ) {
            actual = true;
        }
        assertEquals(caseName, expected, actual);
    }
}
