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
        // Phase 2f WI-3 C.1 — Phase 1 design called for an EXACT-tier
        // promotion here, but the bit-faithful match is structurally
        // unattainable: the very first Math.exp(...) call before the
        // Patnaik series even begins differs from C++ libc++ std::exp
        // by 1 ULP (Math.exp's IEEE-754 1-ULP tolerance band vs
        // libc++ macOS's correctly-rounded result), and that 1-ULP
        // seed propagates into a 1-3 ULP drift on the final CDF sum.
        // Diagnosed via /tmp/full2.{cpp,Full2.java} traces (n=2 step
        // diverges by 1 ULP, accumulates to 3 ULPs at convergence on
        // df=2.5,ncp=1.5,x=3.0). This is the design's category (d)
        // "Bessel/log/exp approximation differs", but the underlying
        // Math.exp is not part of JQuantLib — it ships with the JVM.
        // Per phase2f-design A13, the deliberate tier compromise is
        // to keep the existing TIGHT tier (abs 1e-14 + rel 1e-12),
        // which the drift comfortably fits inside (max observed
        // ~1.7e-16 absolute, well under 1e-14 + rel 1e-12).
        // Extended C.1 regression grid (14 tuples) is still useful
        // as a tighter-tier check than the original Phase 2c grid.
        runFingerprint("cdf");
    }

    @Test
    public void inverseCdfRoundTripsAtCdfX() {
        runFingerprint("inv_cdf_at_cdf_x");
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
