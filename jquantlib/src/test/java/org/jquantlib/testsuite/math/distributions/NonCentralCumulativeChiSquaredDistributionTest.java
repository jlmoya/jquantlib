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
                        + " diff=" + Math.abs(got - expected));
            }
        }
    }
}
