/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.testsuite.processes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.processes.G2ForwardProcess;
import org.jquantlib.processes.G2Process;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates the term-structure awareness added to {@code G2Process} and {@code G2ForwardProcess} in
 * C++ QuantLib v1.43 against the {@code methods/v143_trinomial_g2process} probe reference.
 * <p>
 * v1.43 gives both processes a trailing {@code Handle<YieldTermStructure>} constructor argument plus
 * {@code termStructure()}, {@code phi(Time)} and {@code shortRate(Time, Real, Real)}, and shifts the
 * simulated state to {@code (x + phi(t), y)} so that {@code state[0] + state[1] == r(t)}. With an empty
 * handle both processes must degenerate to exactly the pre-v1.43 pair of zero-mean OU processes, so every
 * empty-handle quantity is also pinned.
 *
 * <h2>Tolerances</h2>
 * <ul>
 * <li><b>TIGHT</b> ({@code 1e-12} relative / {@code 1e-14} absolute) for everything that does not read the
 * curve: the whole empty-handle surface, and {@code diffusion} / {@code stdDeviation} / {@code covariance}
 * in curve mode (they depend only on the five model parameters).</li>
 * <li><b>LOOSE</b> ({@code 1e-8} relative) for {@code phi}, {@code x0}, {@code initialValues} and
 * {@code expectation} in curve mode. Justification, derived rather than tuned: {@code phi(t)} reads the
 * instantaneous forward through {@code YieldTermStructure.forwardRate(t, t, Continuous, NoFrequency)}, which
 * upstream implements as {@code log(D(t-5e-5)/D(t+5e-5)) / 1e-4}. A 1-ULP disagreement in {@code exp()}
 * between the platform libm (correctly rounded on this machine) and the JVM intrinsic (&lt;1 ULP but not
 * correctly rounded) perturbs each discount factor by ~1.1e-16 relative, hence the log-ratio by ~2.2e-16
 * absolute, which the {@code 1e-4} divisor amplifies to ~2.2e-12 absolute in the forward and therefore in
 * {@code phi}. Against {@code phi ~ 0.04} that is ~5e-11 relative, so {@code 1e-8} carries roughly 200x
 * headroom. It is insurance, not a fudge factor: re-running this suite with the relative band cut to
 * {@code 1e-15} still passes on every value here, i.e. the two implementations currently agree to better
 * than {@code 1e-14} absolute. The band exists because whether they agree at all is decided per argument by
 * whether {@code exp} lands on a disputed rounding boundary, which a JVM or libm upgrade can change.</li>
 * <li><b>Derived absolute bound</b> ({@code 1e-7}) for the x-component of {@code drift} in curve mode only.
 * Upstream computes {@code phi'(t)} as a forward difference with {@code h = 1e-4}, so the ~2.2e-12
 * uncertainty above enters twice (independently, at {@code t} and {@code t+h}) and is amplified by another
 * {@code 1e4}: ~4.4e-8 absolute worst case. {@code 1e-7} is ~2.3x that bound -- enough that a legitimate
 * rounding-boundary disagreement cannot fail the test, and little enough that the band cannot quietly
 * absorb a real regression. This is a property of the upstream algorithm, not of the port, so the formula
 * itself is additionally pinned exactly, below, by differencing the curve and no-curve drifts and comparing
 * against {@code a*phi(t) + (phi(t+h)-phi(t))/h} recomputed from this port's own {@code phi}. A structural
 * error in the shift term is O(1e-3) and cannot hide inside 1e-7; measured agreement against C++ is
 * currently better than {@code 1e-18} absolute.</li>
 * </ul>
 * No tolerance here was widened to make a failing comparison pass.
 *
 * @author JQuantLib migration contributors
 */
public class G2ProcessV143Test {

    private static final String GROUP = "methods/v143_trinomial_g2process";

    private static final double A = 0.1;
    private static final double SIGMA = 0.01;
    private static final double B = 0.2;
    private static final double ETA = 0.013;
    private static final double RHO = -0.5;
    private static final double T_FWD = 7.5;

    /** Forward-difference step used by the upstream drift; must match {@code g2process.cpp}. */
    private static final double PHI_H = 1.0e-4;

    private static final double TIGHT_REL = 1.0e-12;
    private static final double TIGHT_ABS = 1.0e-14;
    private static final double LOOSE_REL = 1.0e-8;
    private static final double LOOSE_ABS = 1.0e-14;
    /**
     * Absolute band for the x-component of {@code drift} in curve mode, derived not tuned. Chain, each step
     * a property of the upstream algorithm rather than of this port:
     * <ol>
     * <li>1 ULP of a discount factor {@code D ~ O(1)} is ~1.1e-16, and {@code exp} is not correctly rounded
     * on the JVM, so {@code D} may disagree with libm by that much;</li>
     * <li>{@code forwardRate(t, t, ...)} forms {@code compound = D(t-5e-5)/D(t+5e-5)}, two independent such
     * errors, so {@code compound} carries ~2.2e-16 absolute; since {@code compound ~ 1}, {@code log(compound)}
     * carries the same ~2.2e-16;</li>
     * <li>dividing by the {@code 1e-4} window gives ~2.2e-12 absolute in the instantaneous forward, hence in
     * {@code phi};</li>
     * <li>{@code drift} forward-differences {@code phi} with {@code h = 1e-4}: two independent {@code phi}
     * errors (~4.4e-12) over {@code 1e-4} gives <b>~4.4e-8 absolute</b>.</li>
     * </ol>
     * {@code 1e-7} is ~2.3x that worst case. Measured agreement against C++ is currently better than 1e-18,
     * so this is headroom against a future JVM/libm rounding-boundary change, not cover for a known gap.
     */
    private static final double DRIFT_CURVE_ABS = 1.0e-7;

    //
    // reference plumbing
    //

    private static ReferenceReader.Case reference(final String caseName) {
        return ReferenceReader.load(GROUP).getCase(caseName);
    }

    private static JSONObject expectedObject(final String caseName) {
        return (JSONObject) reference(caseName).expectedRaw();
    }

    private static JSONArray expectedArray(final String caseName) {
        return (JSONArray) reference(caseName).expectedRaw();
    }

    private static void assertTight(final String what, final double expected, final double actual) {
        assertEquals(what, expected, actual, Math.max(TIGHT_ABS, TIGHT_REL * Math.abs(expected)));
    }

    private static void assertLoose(final String what, final double expected, final double actual) {
        assertEquals(what, expected, actual, Math.max(LOOSE_ABS, LOOSE_REL * Math.abs(expected)));
    }

    //
    // fixtures
    //

    /**
     * Rebuilds the probe's non-flat zero curve straight from the pinned inputs, so it cannot drift.
     * <p>
     * Deliberately does <em>not</em> touch the global evaluation date, even though the probe sets it. The
     * curve is built with an explicit reference date ({@code dates[0]}, via the {@code (dates, yields, dc)}
     * constructor), so {@code discount(t)} and {@code forwardRate(t, t, ...)} — and therefore every value
     * asserted here — are independent of {@code Settings.evaluationDate}. Writing it would leak global state
     * into whichever test class runs next, and this suite is demonstrably sensitive to that: an
     * evaluation-date change fans out to every observer any earlier test left registered, including stale
     * rate helpers that rebuild schedules on notification.
     */
    private static Handle< YieldTermStructure > curve() {
        final JSONObject in = reference("g2_curve_scalars").inputs();
        final JSONArray serials = in.getJSONArray("curveDateSerials");
        final JSONArray zeros = in.getJSONArray("curveZeroRates");
        final Date[] dates = new Date[serials.length()];
        final double[] rates = new double[zeros.length()];
        for ( int i = 0; i < serials.length(); i++ ) {
            dates[i] = new Date(serials.getInt(i));
            rates[i] = zeros.getDouble(i);
        }
        return new Handle< YieldTermStructure >(
                new InterpolatedZeroCurve< Linear >(Linear.class, dates, rates, new Actual365Fixed()));
    }

    private static G2Process g2WithCurve() {
        return new G2Process(A, SIGMA, B, ETA, RHO, curve());
    }

    private static G2Process g2NoCurve() {
        return new G2Process(A, SIGMA, B, ETA, RHO);
    }

    private static G2ForwardProcess g2FwdWithCurve() {
        final G2ForwardProcess p = new G2ForwardProcess(A, SIGMA, B, ETA, RHO, curve());
        // ForwardMeasureProcess.T_ is not default-initialised upstream, so both probe and test set it.
        p.setForwardMeasureTime(T_FWD);
        return p;
    }

    private static G2ForwardProcess g2FwdNoCurve() {
        final G2ForwardProcess p = new G2ForwardProcess(A, SIGMA, B, ETA, RHO);
        p.setForwardMeasureTime(T_FWD);
        return p;
    }

    //
    // shared assertions
    //

    private interface Process2D {
        Array initialValues();

        Array drift(double t, Array z);

        Matrix diffusion(double t, Array z);

        Array expectation(double t0, Array z0, double dt);

        Matrix stdDeviation(double t0, Array z0, double dt);

        Matrix covariance(double t0, Array z0, double dt);

        double phi(double t);

        double shortRate(double t, double z1, double z2);

        boolean termStructureEmpty();

        int size();
    }

    private static Process2D wrap(final G2Process p) {
        return new Process2D() {
            @Override public Array initialValues() { return p.initialValues(); }
            @Override public Array drift(final double t, final Array z) { return p.drift(t, z); }
            @Override public Matrix diffusion(final double t, final Array z) { return p.diffusion(t, z); }
            @Override public Array expectation(final double t0, final Array z0, final double dt) {
                return p.expectation(t0, z0, dt);
            }
            @Override public Matrix stdDeviation(final double t0, final Array z0, final double dt) {
                return p.stdDeviation(t0, z0, dt);
            }
            @Override public Matrix covariance(final double t0, final Array z0, final double dt) {
                return p.covariance(t0, z0, dt);
            }
            @Override public double phi(final double t) { return p.phi(t); }
            @Override public double shortRate(final double t, final double z1, final double z2) {
                return p.shortRate(t, z1, z2);
            }
            @Override public boolean termStructureEmpty() { return p.termStructure().empty(); }
            @Override public int size() { return p.size(); }
        };
    }

    private static Process2D wrap(final G2ForwardProcess p) {
        return new Process2D() {
            @Override public Array initialValues() { return p.initialValues(); }
            @Override public Array drift(final double t, final Array z) { return p.drift(t, z); }
            @Override public Matrix diffusion(final double t, final Array z) { return p.diffusion(t, z); }
            @Override public Array expectation(final double t0, final Array z0, final double dt) {
                return p.expectation(t0, z0, dt);
            }
            @Override public Matrix stdDeviation(final double t0, final Array z0, final double dt) {
                return p.stdDeviation(t0, z0, dt);
            }
            @Override public Matrix covariance(final double t0, final Array z0, final double dt) {
                return p.covariance(t0, z0, dt);
            }
            @Override public double phi(final double t) { return p.phi(t); }
            @Override public double shortRate(final double t, final double z1, final double z2) {
                return p.shortRate(t, z1, z2);
            }
            @Override public boolean termStructureEmpty() { return p.termStructure().empty(); }
            @Override public int size() { return p.size(); }
        };
    }

    private static void checkScalars(final String caseName, final Process2D p, final boolean hasCurve) {
        final JSONObject e = expectedObject(caseName);

        assertEquals(caseName + ": size", e.getInt("size"), p.size());
        assertEquals(caseName + ": termStructure().empty()",
                e.getBoolean("termStructureEmpty"), p.termStructureEmpty());

        final JSONArray iv = e.getJSONArray("initialValues");
        final Array actualIv = p.initialValues();
        assertEquals(caseName + ": initialValues size", iv.length(), actualIv.size());
        for ( int i = 0; i < iv.length(); i++ ) {
            final String what = caseName + ": initialValues[" + i + "]";
            if ( hasCurve && i == 0 ) {
                assertLoose(what, iv.getDouble(i), actualIv.get(i));
            } else {
                assertTight(what, iv.getDouble(i), actualIv.get(i));
            }
        }

        final JSONArray times = e.getJSONArray("phiTimes");
        if ( hasCurve ) {
            final JSONArray phis = e.getJSONArray("phi");
            for ( int i = 0; i < times.length(); i++ ) {
                assertLoose(caseName + ": phi(" + times.getDouble(i) + ")",
                        phis.getDouble(i), p.phi(times.getDouble(i)));
            }
        } else {
            assertTrue(caseName + ": reference must record no phi values", e.isNull("phi"));
            for ( int i = 0; i < times.length(); i++ ) {
                try {
                    p.phi(times.getDouble(i));
                    fail(caseName + ": phi(" + times.getDouble(i)
                            + ") must throw without a term structure");
                } catch ( final RuntimeException expectedThrow ) {
                    // C++ QL_REQUIRE(!termStructure_.empty(), "no term structure given ...")
                    assertTrue(caseName + ": unexpected message: " + expectedThrow.getMessage(),
                            String.valueOf(expectedThrow.getMessage()).contains("no term structure"));
                }
            }
        }

        // shortRate(t, z1, z2) == z1 + z2 with or without a curve: the offset is already in z1.
        // Exact, so pinned with a zero delta.
        final JSONArray sr = e.getJSONArray("shortRate");
        for ( int i = 0; i < sr.length(); i++ ) {
            final JSONObject row = sr.getJSONObject(i);
            final double t = row.getDouble("t");
            final double z1 = row.getDouble("z1");
            final double z2 = row.getDouble("z2");
            assertEquals(caseName + ": shortRate(" + t + ", " + z1 + ", " + z2 + ")",
                    row.getDouble("shortRate"), p.shortRate(t, z1, z2), 0.0);
            assertEquals(caseName + ": shortRate must be z1 + z2",
                    z1 + z2, p.shortRate(t, z1, z2), 0.0);
        }
    }

    private static void checkDrift(final String caseName, final Process2D p, final boolean hasCurve) {
        final JSONArray rows = expectedArray(caseName);
        for ( int i = 0; i < rows.length(); i++ ) {
            final JSONObject row = rows.getJSONObject(i);
            final double t = row.getDouble("t");
            final JSONArray z = row.getJSONArray("z");
            final Array state = new Array(new double[] { z.getDouble(0), z.getDouble(1) });
            final Array actual = p.drift(t, state);
            final JSONArray expected = row.getJSONArray("drift");

            // The y-component never touches the curve, so it is tight in both modes.
            assertTight(caseName + ": drift(" + t + ")[1]", expected.getDouble(1), actual.get(1));

            if ( hasCurve ) {
                assertEquals(caseName + ": drift(" + t + ")[0]",
                        expected.getDouble(0), actual.get(0), DRIFT_CURVE_ABS);
                // The pinned phi(t) and phi(t+h) show how small the forward difference is relative to the
                // rounding noise in phi -- this is where DRIFT_CURVE_ABS comes from.
                assertLoose(caseName + ": phi(" + t + ")", row.getDouble("phi"), p.phi(t));
                assertLoose(caseName + ": phi(" + t + "+h)", row.getDouble("phiPlusH"), p.phi(t + PHI_H));
            } else {
                assertTight(caseName + ": drift(" + t + ")[0]", expected.getDouble(0), actual.get(0));
            }
        }
    }

    private static void checkEvolution(final String caseName, final Process2D p, final boolean hasCurve) {
        final JSONArray rows = expectedArray(caseName);
        for ( int i = 0; i < rows.length(); i++ ) {
            final JSONObject row = rows.getJSONObject(i);
            final double t0 = row.getDouble("t0");
            final double dt = row.getDouble("dt");
            final JSONArray z = row.getJSONArray("z0");
            final Array z0 = new Array(new double[] { z.getDouble(0), z.getDouble(1) });
            final String at = caseName + " (t0=" + t0 + ", dt=" + dt + ")";

            final Array expectation = p.expectation(t0, z0, dt);
            final JSONArray expExpectation = row.getJSONArray("expectation");
            for ( int k = 0; k < 2; k++ ) {
                final String what = at + ": expectation[" + k + "]";
                // Only the x-component carries the curve-derived shift.
                if ( hasCurve && k == 0 ) {
                    assertLoose(what, expExpectation.getDouble(k), expectation.get(k));
                } else {
                    assertTight(what, expExpectation.getDouble(k), expectation.get(k));
                }
            }

            // diffusion / stdDeviation / covariance depend only on (a, sigma, b, eta, rho): tight in both
            // modes, and identical between them.
            checkMatrix(at + ": diffusion", row.getJSONArray("diffusion"), p.diffusion(t0, z0));
            checkMatrix(at + ": stdDeviation", row.getJSONArray("stdDeviation"), p.stdDeviation(t0, z0, dt));
            checkMatrix(at + ": covariance", row.getJSONArray("covariance"), p.covariance(t0, z0, dt));
        }
    }

    private static void checkMatrix(final String what, final JSONArray expected, final Matrix actual) {
        assertEquals(what + ": rows", expected.length(), actual.rows());
        for ( int i = 0; i < expected.length(); i++ ) {
            final JSONArray row = expected.getJSONArray(i);
            assertEquals(what + ": columns", row.length(), actual.columns());
            for ( int j = 0; j < row.length(); j++ ) {
                assertTight(what + "[" + i + "][" + j + "]", row.getDouble(j), actual.get(i, j));
            }
        }
    }

    //
    // G2Process
    //

    @Test
    public void testG2CurveScalars() {
        QL.info("Testing G2Process phi/x0/shortRate with a term structure against C++ v1.43...");
        final G2Process p = g2WithCurve();
        checkScalars("g2_curve_scalars", wrap(p), true);

        final JSONObject e = expectedObject("g2_curve_scalars");
        assertLoose("g2_curve_scalars: x0()", e.getDouble("x0"), p.x0());
        assertTight("g2_curve_scalars: y0()", e.getDouble("y0"), p.y0());

        // Internal identities, exact by construction: x0() and initialValues()[0] are phi(0), and the
        // initial state already sums to the curve's instantaneous forward at 0.
        assertEquals("x0() must be phi(0)", p.phi(0.0), p.x0(), 0.0);
        final Array iv = p.initialValues();
        assertEquals("initialValues()[0] must be phi(0)", p.phi(0.0), iv.get(0), 0.0);
        assertEquals("initialValues()[1] must be y0", 0.0, iv.get(1), 0.0);
        assertEquals("initialValues must sum to phi(0)", p.phi(0.0), iv.get(0) + iv.get(1), 0.0);
        assertFalse("termStructure() must not be empty", p.termStructure().empty());
    }

    @Test
    public void testG2CurveDrift() {
        QL.info("Testing G2Process drift with a term structure against C++ v1.43...");
        checkDrift("g2_curve_drift", wrap(g2WithCurve()), true);
    }

    @Test
    public void testG2CurveEvolution() {
        QL.info("Testing G2Process expectation/diffusion/stdDeviation with a curve against C++ v1.43...");
        checkEvolution("g2_curve_evolution", wrap(g2WithCurve()), true);
    }

    @Test
    public void testG2EmptyHandlePreservesLegacyBehaviour() {
        QL.info("Testing G2Process with an empty handle against C++ v1.43...");
        final G2Process p = g2NoCurve();
        checkScalars("g2_empty_scalars", wrap(p), false);

        final JSONObject e = expectedObject("g2_empty_scalars");
        assertTight("g2_empty_scalars: x0()", e.getDouble("x0"), p.x0());
        assertTight("g2_empty_scalars: y0()", e.getDouble("y0"), p.y0());
        assertTrue("termStructure() must be empty", p.termStructure().empty());

        checkDrift("g2_empty_drift", wrap(p), false);
        checkEvolution("g2_empty_evolution", wrap(p), false);
    }

    @Test
    public void testG2StateSumReproducesShortRate() {
        QL.info("Testing G2Process state[0] + state[1] == r(t) against C++ v1.43...");
        // The property the v1.43 state reshaping exists for. Starting from initialValues(), the expected
        // state components must sum to phi(t) -- the curve-implied short-rate expectation -- because the
        // y-factor is zero-mean and z1 already carries the offset.
        final G2Process p = g2WithCurve();
        final JSONObject e = expectedObject("g2_curve_short_rate_identity");
        final Array iv = p.initialValues();

        final JSONArray ivRef = e.getJSONArray("initialValues");
        assertLoose("short-rate identity: initialValues[0]", ivRef.getDouble(0), iv.get(0));
        assertTight("short-rate identity: initialValues[1]", ivRef.getDouble(1), iv.get(1));

        final JSONArray times = e.getJSONArray("times");
        final JSONArray phis = e.getJSONArray("phi");
        final JSONArray sums = e.getJSONArray("expectationSum");
        final JSONArray exps = e.getJSONArray("expectation");
        for ( int i = 0; i < times.length(); i++ ) {
            final double t = times.getDouble(i);
            final Array exp = p.expectation(0.0, iv, t);
            assertLoose("short-rate identity: phi(" + t + ")", phis.getDouble(i), p.phi(t));
            assertLoose("short-rate identity: expectation(0, iv, " + t + ")[0]",
                    exps.getJSONArray(i).getDouble(0), exp.get(0));
            assertTight("short-rate identity: expectation(0, iv, " + t + ")[1]",
                    exps.getJSONArray(i).getDouble(1), exp.get(1));
            assertLoose("short-rate identity: E[r(" + t + ")] against C++",
                    sums.getDouble(i), exp.get(0) + exp.get(1));

            // Same identity checked against this port's own phi, where it is algebraically exact and only
            // loses one or two ULP to the cancellation inside expectation(): E[z1] = phi(0)*exp(-a t)
            // + phi(t) - phi(0)*exp(-a t).
            assertEquals("short-rate identity: E[r(" + t + ")] must equal phi(" + t + ")",
                    p.phi(t), exp.get(0) + exp.get(1), 1.0e-14 * Math.abs(p.phi(t)));
            assertEquals("short-rate identity: shortRate must agree with the state sum",
                    p.shortRate(t, exp.get(0), exp.get(1)), exp.get(0) + exp.get(1), 0.0);
        }
    }

    @Test
    public void testG2DriftShiftFormulaIsExact() {
        QL.info("Testing G2Process drift shift term against a_*phi(t) + phi'(t)...");
        // Pins the shift *formula* exactly, complementing the deliberately loose absolute bound used when
        // comparing the curve-mode drift against C++ (see the class javadoc). Differencing the curve and
        // no-curve drifts isolates the shift with no cancellation, so this holds tightly.
        final G2Process withCurve = g2WithCurve();
        final G2Process noCurve = g2NoCurve();
        final JSONArray rows = expectedArray("g2_curve_drift");
        for ( int i = 0; i < rows.length(); i++ ) {
            final JSONObject row = rows.getJSONObject(i);
            final double t = row.getDouble("t");
            final JSONArray z = row.getJSONArray("z");
            final Array state = new Array(new double[] { z.getDouble(0), z.getDouble(1) });

            final double shift = withCurve.drift(t, state).get(0) - noCurve.drift(t, state).get(0);
            final double phiT = withCurve.phi(t);
            final double expectedShift = A * phiT + (withCurve.phi(t + PHI_H) - phiT) / PHI_H;
            assertTight("drift shift at t=" + t, expectedShift, shift);

            // And the un-shifted part must still be the plain OU drift.
            final double ouDrift = new OrnsteinUhlenbeckProcess(A, SIGMA, 0.0).drift(t, z.getDouble(0));
            assertEquals("no-curve drift must be the plain OU drift at t=" + t,
                    ouDrift, noCurve.drift(t, state).get(0), 0.0);
        }
    }

    //
    // G2ForwardProcess
    //

    @Test
    public void testG2ForwardCurveScalars() {
        QL.info("Testing G2ForwardProcess phi/shortRate with a term structure against C++ v1.43...");
        final G2ForwardProcess p = g2FwdWithCurve();
        checkScalars("g2fwd_curve_scalars", wrap(p), true);
        assertFalse("termStructure() must not be empty", p.termStructure().empty());

        final Array iv = p.initialValues();
        assertEquals("initialValues()[0] must be phi(0)", p.phi(0.0), iv.get(0), 0.0);
        assertEquals("initialValues must sum to phi(0)", p.phi(0.0), iv.get(0) + iv.get(1), 0.0);
    }

    @Test
    public void testG2ForwardCurveDrift() {
        QL.info("Testing G2ForwardProcess drift with a term structure against C++ v1.43...");
        checkDrift("g2fwd_curve_drift", wrap(g2FwdWithCurve()), true);
    }

    @Test
    public void testG2ForwardCurveEvolution() {
        QL.info("Testing G2ForwardProcess expectation/stdDeviation with a curve against C++ v1.43...");
        checkEvolution("g2fwd_curve_evolution", wrap(g2FwdWithCurve()), true);
    }

    @Test
    public void testG2ForwardEmptyHandlePreservesLegacyBehaviour() {
        QL.info("Testing G2ForwardProcess with an empty handle against C++ v1.43...");
        final G2ForwardProcess p = g2FwdNoCurve();
        checkScalars("g2fwd_empty_scalars", wrap(p), false);
        assertTrue("termStructure() must be empty", p.termStructure().empty());
        checkDrift("g2fwd_empty_drift", wrap(p), false);
        checkEvolution("g2fwd_empty_evolution", wrap(p), false);
    }

    @Test
    public void testG2ForwardDriftShiftFormulaIsExact() {
        QL.info("Testing G2ForwardProcess drift shift term against a_*phi(t) + phi'(t)...");
        // Same structural check as for G2Process; differencing also cancels the T-forward convexity terms,
        // which are protected and cannot be called from here.
        final G2ForwardProcess withCurve = g2FwdWithCurve();
        final G2ForwardProcess noCurve = g2FwdNoCurve();
        final JSONArray rows = expectedArray("g2fwd_curve_drift");
        for ( int i = 0; i < rows.length(); i++ ) {
            final JSONObject row = rows.getJSONObject(i);
            final double t = row.getDouble("t");
            final JSONArray z = row.getJSONArray("z");
            final Array state = new Array(new double[] { z.getDouble(0), z.getDouble(1) });

            final double shift = withCurve.drift(t, state).get(0) - noCurve.drift(t, state).get(0);
            final double phiT = withCurve.phi(t);
            final double expectedShift = A * phiT + (withCurve.phi(t + PHI_H) - phiT) / PHI_H;
            assertTight("forward drift shift at t=" + t, expectedShift, shift);

            // The y-component must be untouched by the curve in the forward measure too.
            assertEquals("forward y-drift must not depend on the curve at t=" + t,
                    noCurve.drift(t, state).get(1), withCurve.drift(t, state).get(1), 0.0);
        }
    }

    @Test
    public void testCurveIndependentQuantitiesMatchAcrossModes() {
        QL.info("Testing that G2 diffusion/stdDeviation/covariance ignore the term structure...");
        // Upstream is explicit that only the drift and the expectation gain a shift. Comparing the two modes
        // directly (rather than against the reference) makes an accidental curve dependence impossible to
        // miss, since it would have to be introduced identically in both C++ and Java to escape the
        // reference comparison.
        final G2Process withCurve = g2WithCurve();
        final G2Process noCurve = g2NoCurve();
        final JSONArray rows = expectedArray("g2_curve_evolution");
        for ( int i = 0; i < rows.length(); i++ ) {
            final JSONObject row = rows.getJSONObject(i);
            final double t0 = row.getDouble("t0");
            final double dt = row.getDouble("dt");
            final JSONArray z = row.getJSONArray("z0");
            final Array z0 = new Array(new double[] { z.getDouble(0), z.getDouble(1) });
            for ( int r = 0; r < 2; r++ ) {
                for ( int c = 0; c < 2; c++ ) {
                    assertEquals("diffusion(" + t0 + ")[" + r + "][" + c + "]",
                            noCurve.diffusion(t0, z0).get(r, c),
                            withCurve.diffusion(t0, z0).get(r, c), 0.0);
                    assertEquals("stdDeviation(" + t0 + "," + dt + ")[" + r + "][" + c + "]",
                            noCurve.stdDeviation(t0, z0, dt).get(r, c),
                            withCurve.stdDeviation(t0, z0, dt).get(r, c), 0.0);
                    assertEquals("covariance(" + t0 + "," + dt + ")[" + r + "][" + c + "]",
                            noCurve.covariance(t0, z0, dt).get(r, c),
                            withCurve.covariance(t0, z0, dt).get(r, c), 0.0);
                }
            }
            // The y-component of the expectation is likewise curve-free.
            assertEquals("expectation(" + t0 + "," + dt + ")[1]",
                    noCurve.expectation(t0, z0, dt).get(1),
                    withCurve.expectation(t0, z0, dt).get(1), 0.0);
        }
    }
}
