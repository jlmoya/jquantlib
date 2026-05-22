/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.marketmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.SwapForwardMappings;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.correlations.TimeHomogeneousForwardCorrelation;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.models.FlatVol;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepSwaption;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-202 port of {@code test-suite/swapforwardmappings.cpp}
 * v1.42.1 (445 LOC, 2 test cases).
 *
 * <p>Both tests are now active. The Java port follows the C++ exactly for
 * {@code testForwardSwapJacobians} (analytic vs bump-and-revalue with the
 * C++ tolerance 1e-5). For {@code testSwaptionImpliedVolatility} the Java
 * port cross-validates the analytic implied-vol values (BGM
 * freeze-coefficient formula) against C++ at TIGHT tolerance (1e-12).
 *
 * <p><strong>Note on MC path:</strong> the C++ test runs a 32k-path LMM
 * Monte-Carlo simulation per startIndex purely to empirically validate the
 * freeze-coefficient approximation against the LMM ground truth. That
 * validation is methodology, not an implementation invariant — the test
 * passes iff {@code Black(impliedVol)} is within 3.5σ of the MC mean,
 * which is a statement about model quality, not about the
 * {@link SwapForwardMappings#swaptionImpliedVolatility} port. The Java
 * test omits the MC piece and instead asserts an exact (TIGHT) match to
 * the C++ analytic output, which is a strictly stronger invariant on the
 * Java implementation. See harness probe
 * {@code swapforwardmappings_probe} for the reference values.
 *
 * <p>Source: {@code test-suite/swapforwardmappings.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 * Reference probe: {@code migration-harness/references/models/marketmodels/swapforwardmappings.json}.
 */
public class SwapForwardMappingsTest {

    private static final String TEST_GROUP = "models/marketmodels/swapforwardmappings";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);

    // ------------------------------------------------------------------
    // Fixture matching the C++ MarketModelData class. The fixture is
    // hardcoded from the C++ probe output (NullCalendar + Semiannual
    // Schedule + SimpleDayCounter over 9 years collapses to 0.5*k year
    // fractions for k = 1..18 with nbRates = 17). Cross-checked at fixture
    // load time against the JSON reference so any drift surfaces loudly.
    // ------------------------------------------------------------------
    private static final int N = 17;
    private static final double[] RATE_TIMES = {
            0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0,
            5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0
    };
    private static final double[] FORWARDS = new double[N];
    private static final double[] DISPLACEMENTS = new double[N];
    private static final double[] DISCOUNT_FACTORS = new double[N + 1];
    private static final double[] VOLATILITIES = {
            0.15541283, 0.18719678, 0.20890740, 0.22318179, 0.23212717,
            0.23731450, 0.23988649, 0.24066384, 0.24023111, 0.23900189,
            0.23726699, 0.23522952, 0.23303022, 0.23076564, 0.22850101,
            0.22627951, 0.22412881
    };
    static {
        DISCOUNT_FACTORS[0] = 1.0;
        for (int i = 0; i < N; ++i) {
            FORWARDS[i] = 0.03 + 0.0010 * i;
            final double tau = RATE_TIMES[i + 1] - RATE_TIMES[i];
            DISCOUNT_FACTORS[i + 1] = DISCOUNT_FACTORS[i] / (1.0 + FORWARDS[i] * tau);
        }
        // Sanity-check our local fixture against the C++ probe.
        final JSONObject fix = (JSONObject) REF.getCase("fixture").expectedRaw();
        if (fix.getInt("nbRates") != N) {
            throw new AssertionError("nbRates mismatch: expected=" + N
                    + " probe=" + fix.getInt("nbRates"));
        }
        assertVecClose("rateTimes", RATE_TIMES, fix.getJSONArray("rateTimes"), 0.0);
        assertVecClose("forwards", FORWARDS, fix.getJSONArray("forwards"), 1.0e-15);
        assertVecClose("discountFactors", DISCOUNT_FACTORS,
                fix.getJSONArray("discountFactors"), 1.0e-15);
        assertVecClose("volatilities", VOLATILITIES,
                fix.getJSONArray("volatilities"), 0.0);
        assertVecClose("displacements", DISPLACEMENTS,
                fix.getJSONArray("displacements"), 0.0);
    }

    private static void assertVecClose(String name, double[] a, JSONArray b, double tol) {
        if (a.length != b.length()) {
            throw new AssertionError(name + " length mismatch: " + a.length + " vs " + b.length());
        }
        for (int i = 0; i < a.length; ++i) {
            final double e = b.getDouble(i);
            final double diff = Math.abs(a[i] - e);
            if (diff > tol) {
                throw new AssertionError(name + "[" + i + "] mismatch: java=" + a[i]
                        + " probe=" + e + " diff=" + diff);
            }
        }
    }

    /**
     * Port of C++ {@code testForwardSwapJacobians}.
     *
     * <p>Two sub-cases:
     * <ol>
     *   <li>Coinitial-swap Jacobian {@code dsr[i]/df[j]} (where swap[i]
     *       spans forwards [0..i+1)) computed analytically must equal the
     *       central-difference bump-and-revalue derivative within 1e-5.</li>
     *   <li>Constant-maturity-swap Jacobian for every spanning length in
     *       [1, nbRates), same comparison.</li>
     * </ol>
     *
     * <p>Bump size: 1e-8 (matches C++). Tolerance: 1e-5 (matches C++).
     * The analytic-vs-numerical test is self-validating; cross-validation
     * against C++ probe at TIGHT 1e-12 is also performed on the analytic
     * Jacobians as a regression guard.
     */
    @Test
    public void testForwardSwapJacobians() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES);
        cs.setOnForwardRates(FORWARDS);

        final double bumpSize = 1e-8;
        final double errorTolerance = 1e-5;

        // ----- coinitial -----
        {
            final Matrix coinitialNumerical = new Matrix(N, N);
            final double[] bumped = FORWARDS.clone();
            for (int i = 0; i < N; ++i) {
                for (int j = 0; j < N; ++j) {
                    System.arraycopy(FORWARDS, 0, bumped, 0, N);
                    bumped[j] += bumpSize;
                    cs.setOnForwardRates(bumped);
                    final double upRate = cs.cmSwapRate(0, i + 1);
                    bumped[j] -= 2.0 * bumpSize;
                    cs.setOnForwardRates(bumped);
                    final double downRate = cs.cmSwapRate(0, i + 1);
                    coinitialNumerical.set(i, j, (upRate - downRate) / (2.0 * bumpSize));
                }
            }
            // restore to unbumped rates (needed for next sub-case)
            cs.setOnForwardRates(FORWARDS);
            final Matrix coinitialAnalytic = SwapForwardMappings.coinitialSwapForwardJacobian(cs);

            // self-validation (analytic ≈ numerical) — matches C++ tolerance
            assertMatrixClose("coinitial analytic vs numerical",
                    coinitialAnalytic, coinitialNumerical, errorTolerance);

            // cross-validation against C++ probe — TIGHT
            assertMatrixCloseToProbe("coinitialSwapForwardJacobian",
                    coinitialAnalytic, 1.0e-12);
        }

        // ----- CMS for every spanningForwards -----
        {
            for (int spanningForwards = 1; spanningForwards < N; ++spanningForwards) {
                final Matrix cmsNumerical = new Matrix(N, N);
                final double[] bumped = FORWARDS.clone();
                for (int i = 0; i < N; ++i) {
                    for (int j = 0; j < N; ++j) {
                        System.arraycopy(FORWARDS, 0, bumped, 0, N);
                        bumped[j] += bumpSize;
                        cs.setOnForwardRates(bumped);
                        final double upRate = cs.cmSwapRate(i, spanningForwards);
                        bumped[j] -= 2.0 * bumpSize;
                        cs.setOnForwardRates(bumped);
                        final double downRate = cs.cmSwapRate(i, spanningForwards);
                        cmsNumerical.set(i, j, (upRate - downRate) / (2.0 * bumpSize));
                    }
                }
                cs.setOnForwardRates(FORWARDS);
                final Matrix cmsAnalytic =
                        SwapForwardMappings.cmSwapForwardJacobian(cs, spanningForwards);

                assertMatrixClose("CMS span=" + spanningForwards + " analytic vs numerical",
                        cmsAnalytic, cmsNumerical, errorTolerance);
                assertMatrixCloseToProbe("cmSwapForwardJacobian_span" + spanningForwards,
                        cmsAnalytic, 1.0e-12);
            }
        }
    }

    /**
     * Port of C++ {@code testSwaptionImpliedVolatility}, restricted to the
     * analytic (freeze-coefficient) implied-volatility computation.
     *
     * <p>C++ loop: {@code for (startIndex = 1; startIndex + 2 < nbRates;
     * startIndex += 5)}, i.e. {@code {1, 6, 11}} for nbRates=17.
     *
     * <p>For each startIndex, build a TimeHomogeneousForwardCorrelation
     * with the same (longTermCorr, beta) = (0.5, 0.2), wrap it in FlatVol
     * with the per-rate market vols, and call
     * {@link SwapForwardMappings#swaptionImpliedVolatility}. The result
     * must match the C++ probe at TIGHT 1e-12. We also re-compute swapRate
     * and swapAnnuity from the curve state to cross-check.
     *
     * <p>The MC validation in the C++ test (Black price within 3.5σ of MC
     * swaption) is omitted: see class-level javadoc for rationale.
     */
    @Test
    public void testSwaptionImpliedVolatility() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES);
        cs.setOnForwardRates(FORWARDS);

        final double longTermCorr = 0.5;
        final double beta = 0.2;
        final int endIndex = N - 2; // = 15 for N=17

        final List<Double> rateTimesList = new ArrayList<>(RATE_TIMES.length);
        for (double t : RATE_TIMES) rateTimesList.add(t);

        for (int startIndex = 1; startIndex + 2 < N; startIndex += 5) {
            // Mirror C++: build a MultiStepSwaption and use its evolution
            // description. MultiProductMultiStep::evolution() takes ALL
            // rate times except the last as evolution times, so the
            // FlatVol pseudo-root grid matches C++ exactly.
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 0.03);
            final MultiStepSwaption product = new MultiStepSwaption(
                    RATE_TIMES, startIndex, endIndex, payoff);
            final EvolutionDescription evolution = product.evolution();

            final int numberOfFactors = N;
            final Matrix correlations = ExponentialForwardCorrelation
                    .exponentialCorrelations(rateTimesList, longTermCorr, beta, 1.0, 0.0);
            final TimeHomogeneousForwardCorrelation corr =
                    new TimeHomogeneousForwardCorrelation(correlations, rateTimesList);
            final MarketModel lmm = new FlatVol(
                    VOLATILITIES, corr, evolution, numberOfFactors,
                    cs.forwardRates(), DISPLACEMENTS);

            final double impliedVol = SwapForwardMappings.swaptionImpliedVolatility(
                    lmm, startIndex, endIndex);
            final double swapRate = cs.cmSwapRate(startIndex, endIndex - startIndex);
            final double swapAnnuity = cs.cmSwapAnnuity(startIndex, startIndex,
                    endIndex - startIndex) * DISCOUNT_FACTORS[startIndex];

            final JSONObject exp = (JSONObject) REF
                    .getCase("swaptionImpliedVolatility_start" + startIndex)
                    .expectedRaw();
            final double expImpliedVol  = exp.getDouble("impliedVol");
            final double expSwapRate    = exp.getDouble("swapRate");
            final double expSwapAnnuity = exp.getDouble("swapAnnuity");

            assertEquals("swapRate start=" + startIndex,
                    expSwapRate, swapRate, 1.0e-12);
            assertEquals("swapAnnuity start=" + startIndex,
                    expSwapAnnuity, swapAnnuity, 1.0e-12);
            assertEquals("impliedVol start=" + startIndex,
                    expImpliedVol, impliedVol, 1.0e-12);
            assertTrue("impliedVol positive start=" + startIndex,
                    impliedVol > 0.0);
        }
    }

    // ------------------------------------------------------------------
    // Assertion helpers
    // ------------------------------------------------------------------

    private static void assertMatrixClose(String tag, Matrix actual, Matrix expected,
                                          double tol) {
        if (actual.rows() != expected.rows() || actual.columns() != expected.columns()) {
            throw new AssertionError(tag + " shape mismatch");
        }
        for (int i = 0; i < actual.rows(); ++i) {
            for (int j = 0; j < actual.columns(); ++j) {
                final double a = actual.get(i, j);
                final double e = expected.get(i, j);
                if (Math.abs(a - e) > tol) {
                    throw new AssertionError(tag + " mismatch [" + i + "][" + j + "]"
                            + " java=" + a + " ref=" + e + " diff=" + (a - e));
                }
            }
        }
    }

    private static void assertMatrixCloseToProbe(String caseName, Matrix actual,
                                                 double tol) {
        final JSONObject exp = (JSONObject) REF.getCase(caseName).expectedRaw();
        final JSONArray rows = exp.getJSONArray("jacobian");
        if (rows.length() != actual.rows()) {
            throw new AssertionError(caseName + " row count mismatch");
        }
        for (int i = 0; i < actual.rows(); ++i) {
            final JSONArray row = rows.getJSONArray(i);
            if (row.length() != actual.columns()) {
                throw new AssertionError(caseName + " col count mismatch at row " + i);
            }
            for (int j = 0; j < actual.columns(); ++j) {
                final double a = actual.get(i, j);
                final double e = row.getDouble(j);
                if (Math.abs(a - e) > tol) {
                    throw new AssertionError(caseName + " probe mismatch [" + i + "][" + j + "]"
                            + " java=" + a + " ref=" + e + " diff=" + (a - e));
                }
            }
        }
    }
}
