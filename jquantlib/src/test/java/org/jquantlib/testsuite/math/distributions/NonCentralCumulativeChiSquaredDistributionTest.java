/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 Cross-validation of the NonCentralCumulativeChiSquared* family ports
 against C++ QuantLib v1.42.1 reference values produced by the
 noncentral_chi_squared_probe. See phase2c-design §3.1 (Phase 2c WI-1).

 Note: v1.42.1 does not define a non-central chi-squared PDF distribution
 class. Only CDF (NonCentralCumulativeChiSquareDistribution) and inverse
 CDF (InverseNonCentralCumulativeChiSquareDistribution) are exercised.
 */
package org.jquantlib.testsuite.math.distributions;

import static org.junit.Assert.fail;

import org.jquantlib.math.distributions.InverseNonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NonCentralCumulativeChiSquaredDistributionTest {

    @Test
    public void cdfMatchesCpp() {
        // Phase 2i.6 WI-2 attempted EXACT after JQuantMath.log swap at line 86.
        // A19 fired: 27-ULP residual survives at sample (df=10, ncp=50, x=65)
        // even with both Math.exp and Math.log now correctly-rounded.
        // Structural source is gammaFunction_.logValue(f2+1.0) — the Lanczos
        // logGamma approximation uses Math.log internally and its accumulated
        // rounding is the dominant floor. Phase 2j+ candidate:
        // JQuantMath.lgamma CORE-MATH port. Staying TIGHT.
        runFingerprint("cdf");
    }

    @Test
    public void inverseCdfRoundTripsAtCdfX() {
        runFingerprint("inv_cdf_at_cdf_x");
    }

    @Test
    public void pdfMatchesBoost() {
        // Phase 5h.5-SLV-d: cross-validate the new exact PDF port
        // against Boost's
        //   boost::math::pdf(non_central_chi_squared_distribution<>(df, ncp), x)
        // — the routine QuantLib v1.42.1 calls under the hood (e.g. via
        // SquareRootProcessRNDCalculator::pdf in the Heston SLV path).
        // Replaces the previous CDF central-difference surrogate (~1e-4
        // slack). Loose tier (1e-8 rel) is used because the Bessel-form
        // path involves a Math.exp/Math.log/Bessel-I composition where
        // accumulated rounding can drift several ULPs beyond the tight
        // tier on the long-Poisson-series fixtures (df=1, ncp=1000).
        // Tight tier passes for nearly all samples; loose covers the
        // worst-case series-form points and matches Boost's own internal
        // policy (epsilon = ~1e-15, but downstream multiplications loosen
        // the achievable accuracy).
        final ReferenceReader reader =
                ReferenceReader.load("math/distributions/noncentral_chi_squared_pdf");
        final Case c = reader.getCase("noncentral_chi_squared_pdf_grid");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            final JSONObject s = samples.getJSONObject(i);
            final double degrees = s.getDouble("degrees");
            final double ncp = s.getDouble("ncp");
            final double x = s.getDouble("x");
            final double expected = s.getDouble("pdf");
            final double got = new NonCentralCumulativeChiSquaredDistribution(degrees, ncp).pdf(x);
            // Tight tier passes for every fixture except the
            // Poisson-series-form points (df=8 ncp=100, df=4 ncp=500,
            // ...): those agree to ~1e-10 relative which is below the
            // tight 1e-12 floor. Root cause is the long Poisson sum
            // (~50 outer terms for ncp=100) where each Math.exp /
            // gamma_p_derivative call carries 1-2 ULP rounding plus
            // the ratio recurrence accumulates them. Dominant residual
            // is the gammaFunction_.logValue(a) Lanczos approximation
            // (same A19/A20 source already documented for the CDF).
            // 1.5e-10 rel is well within the loose Phase 1 tier and
            // matches Boost's own published policy floor when the same
            // gamma_p_derivative recurrence is the dominant source.
            if (!Tolerance.loose(got, expected)) {
                fail("pdf[" + i + "] (degrees=" + degrees + ", ncp=" + ncp + ", x=" + x
                        + "): expected=" + expected + " got=" + got
                        + " diff=" + Math.abs(got - expected)
                        + " relDiff=" + Math.abs((got - expected) / expected));
            }
        }
    }

    private static void runFingerprint(final String key) {
        final ReferenceReader reader = ReferenceReader.load("math/distributions/noncentral_chi_squared");
        final Case c = reader.getCase("noncentral_chi_squared_grid");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            final JSONObject s = samples.getJSONObject(i);
            final double degrees = s.getDouble("degrees");
            final double ncp = s.getDouble("ncp");
            final double x = s.getDouble("x");
            final double expected = s.getDouble(key);
            final double got;
            switch (key) {
                case "cdf":
                    got = new NonCentralCumulativeChiSquaredDistribution(degrees, ncp).op(x);
                    break;
                case "inv_cdf_at_cdf_x":
                    final double cdfX = new NonCentralCumulativeChiSquaredDistribution(degrees, ncp).op(x);
                    // Match C++ probe: maxEvaluations=100, accuracy=1e-13.
                    // The probe uses a Brent accuracy tighter than the
                    // v1.42.1 default 1e-8 so the Java/C++ solvers
                    // converge to bit-near values and the round-trip
                    // passes at tight tier rather than the looser
                    // Brent-floor of ~1e-9.
                    got = new InverseNonCentralCumulativeChiSquaredDistribution(degrees, ncp, 100, 1.0e-13).op(cdfX);
                    break;
                default:
                    throw new IllegalArgumentException(key);
            }
            if (!Tolerance.tight(got, expected)) {
                fail(key + "[" + i + "] (degrees=" + degrees + ", ncp=" + ncp + ", x=" + x
                        + "): expected=" + expected + " got=" + got
                        + " diff=" + Math.abs(got - expected)
                        + " ulps=" + Math.abs(Double.doubleToRawLongBits(got) - Double.doubleToRawLongBits(expected)));
            }
        }
    }
}
