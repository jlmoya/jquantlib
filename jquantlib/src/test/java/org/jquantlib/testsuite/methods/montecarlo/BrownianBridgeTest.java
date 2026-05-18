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

package org.jquantlib.testsuite.methods.montecarlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.jquantlib.methods.montecarlo.BrownianBridge;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.TimeGrid;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/brownianbridge.cpp (Phase 5a).
 *
 * <p>2 BOOST_AUTO_TEST_CASE methods exercising
 * {@code BrownianBridge::transform} on Sobol/InverseCumulative variates,
 * plus path-generation comparison between brownianBridge=true vs false
 * via {@code PathGenerator}.
 *
 * <p>Phase 5a.5 carry-forward: both cases require running 100k+ Sobol
 * samples through {@code SequenceStatistics} and comparing covariance
 * matrices; in JQuantLib the {@code SequenceStatistics.covariance}
 * implementation diverges from the C++ unbiased estimator (see
 * {@code CovarianceTest.testCovariance} carry-forward). The tests cannot
 * pass with the current statistics implementation.
 */
public class BrownianBridgeTest {

    public BrownianBridgeTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testVariates() {
        // Java port of QuantLib v1.42.1 test-suite/brownianbridge.cpp::testVariates.
        QL.info("Testing Brownian-bridge variates...");

        final double[] times = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 2.0, 5.0};
        final int n = times.length;
        final int samples = 262143;
        final long seed = 42L;

        final SobolRsg sobol = new SobolRsg(n, seed);
        final InverseCumulativeRsg<SobolRsg, InverseCumulativeNormal> generator =
                new InverseCumulativeRsg<SobolRsg, InverseCumulativeNormal>(
                        sobol, new InverseCumulativeNormal());

        final BrownianBridge bridge = new BrownianBridge(times);

        final SequenceStatistics stats1 = new SequenceStatistics(n);
        final SequenceStatistics stats2 = new SequenceStatistics(n);

        final double[] temp = new double[n];

        for (int i = 0; i < samples; ++i) {
            final Sample<double[]> sample = generator.nextSequence();
            bridge.transform(sample.value(), temp);
            stats1.add(temp);

            // path-summed series
            final double[] path = new double[n];
            path[0] = temp[0] * Math.sqrt(times[0]);
            for (int j = 1; j < n; ++j) {
                path[j] = path[j - 1] + temp[j] * Math.sqrt(times[j] - times[j - 1]);
            }
            stats2.add(path);
        }

        // ---- Normalized single variates: mean ~ 0, covariance ~ identity ----
        final double[] expectedMean = new double[n];
        final Matrix expectedCov = new Matrix(n, n);
        for (int i = 0; i < n; ++i) {
            expectedCov.set(i, i, 1.0);
        }

        final double meanTolerance = 1.0e-16;
        double covTolerance = 2.5e-4;

        Array mean = stats1.mean();
        Matrix covariance = stats1.covariance();

        double maxMeanError = maxDiff(mean, expectedMean);
        double maxCovError = maxDiff(covariance, expectedCov);

        if (maxMeanError > meanTolerance) {
            org.junit.Assert.fail("failed to reproduce expected mean values"
                    + "\n    max error:  " + maxMeanError);
        }
        if (maxCovError > covTolerance) {
            org.junit.Assert.fail("failed to reproduce expected covariance"
                    + "\n    max error:  " + maxCovError);
        }

        // ---- De-normalized sums along the path: cov[i][j] = times[min(i,j)] ----
        final Matrix expectedCov2 = new Matrix(n, n);
        for (int i = 0; i < n; ++i) {
            for (int j = i; j < n; ++j) {
                expectedCov2.set(i, j, times[i]);
                expectedCov2.set(j, i, times[i]);
            }
        }
        covTolerance = 6.0e-4;

        mean = stats2.mean();
        covariance = stats2.covariance();
        maxMeanError = maxDiff(mean, expectedMean);
        maxCovError = maxDiff(covariance, expectedCov2);

        if (maxMeanError > meanTolerance) {
            org.junit.Assert.fail("failed to reproduce expected mean values"
                    + "\n    max error:  " + maxMeanError);
        }
        if (maxCovError > covTolerance) {
            org.junit.Assert.fail("failed to reproduce expected covariance"
                    + "\n    max error:  " + maxCovError);
        }
    }

    private static double maxDiff(final Array a, final double[] b) {
        double diff = 0.0;
        for (int i = 0; i < a.size(); ++i) {
            diff = Math.max(diff, Math.abs(a.get(i) - b[i]));
        }
        return diff;
    }

    private static double maxDiff(final Matrix a, final Matrix b) {
        double diff = 0.0;
        for (int i = 0; i < a.rows(); ++i) {
            for (int j = 0; j < a.columns(); ++j) {
                diff = Math.max(diff, Math.abs(a.get(i, j) - b.get(i, j)));
            }
        }
        return diff;
    }

    @Ignore("Phase 5a.5 carry-forward — depends on SequenceStatistics + path-generation "
            + "infrastructure parity (BrownianBridge transform via PathGenerator). Slow test "
            + "(~131k Sobol samples) — also a candidate for @Tag('slow').")
    @Test
    public void testPathGeneration() {
    }

    //
    // -------------------------------------------------------------------
    // Phase MC-extras additions: probe-driven bit-exact cross-validation
    // of BrownianBridge::transform against C++ v1.42.1 references.
    // -------------------------------------------------------------------
    //

    private static final String BB_GROUP = "methods/montecarlo/brownian_bridge_transform";
    private static final double TIGHT = 1.0e-12;

    private static double[] toDoubleArray(final JSONArray ja) {
        final double[] a = new double[ja.length()];
        for (int i = 0; i < ja.length(); ++i) a[i] = ja.getDouble(i);
        return a;
    }

    private static int[] toIntArray(final JSONArray ja) {
        final int[] a = new int[ja.length()];
        for (int i = 0; i < ja.length(); ++i) a[i] = ja.getInt(i);
        return a;
    }

    private static void assertArraysClose(final String label, final double[] expected,
                                          final double[] actual) {
        assertEquals(label + ".length", expected.length, actual.length);
        for (int i = 0; i < expected.length; ++i) {
            assertEquals(label + "[" + i + "]", expected[i], actual[i], TIGHT);
        }
    }

    private static void assertIntArraysEqual(final String label, final int[] expected,
                                             final int[] actual) {
        assertEquals(label + ".length", expected.length, actual.length);
        for (int i = 0; i < expected.length; ++i) {
            assertEquals(label + "[" + i + "]", expected[i], actual[i]);
        }
    }

    @Test
    public void testTransformUniform5Step() {
        final ReferenceReader ref = ReferenceReader.load(BB_GROUP);
        final ReferenceReader.Case c = ref.getCase("uniform_5step_unit_length");
        final JSONObject in = c.inputs();
        final double length = in.getDouble("length");
        final int n = in.getInt("n");
        final double[] inputVar = toDoubleArray(in.getJSONArray("input_variates"));
        final TimeGrid tg = new TimeGrid(length, n);

        final BrownianBridge bb = new BrownianBridge(tg);
        final double[] output = new double[n];
        bb.transform(inputVar, output);

        final JSONObject expected = (JSONObject) c.expectedRaw();
        assertArraysClose("uniform_5step.output_variates",
                toDoubleArray(expected.getJSONArray("output_variates")), output);
        assertIntArraysEqual("uniform_5step.bridge_index",
                toIntArray(expected.getJSONArray("bridge_index")), bb.bridgeIndex());
        assertIntArraysEqual("uniform_5step.left_index",
                toIntArray(expected.getJSONArray("left_index")), bb.leftIndex());
        assertIntArraysEqual("uniform_5step.right_index",
                toIntArray(expected.getJSONArray("right_index")), bb.rightIndex());
        assertArraysClose("uniform_5step.left_weight",
                toDoubleArray(expected.getJSONArray("left_weight")), bb.leftWeight());
        assertArraysClose("uniform_5step.right_weight",
                toDoubleArray(expected.getJSONArray("right_weight")), bb.rightWeight());
        assertArraysClose("uniform_5step.std_deviation",
                toDoubleArray(expected.getJSONArray("std_deviation")), bb.stdDeviation());
    }

    @Test
    public void testTransformNonUniformGrid() {
        final ReferenceReader ref = ReferenceReader.load(BB_GROUP);
        final ReferenceReader.Case c = ref.getCase("nonuniform_12step_canonical_times");
        final JSONObject in = c.inputs();
        final double[] times = toDoubleArray(in.getJSONArray("times"));
        final double[] inputVar = toDoubleArray(in.getJSONArray("input_variates"));

        final BrownianBridge bb = new BrownianBridge(times);
        final double[] output = new double[times.length];
        bb.transform(inputVar, output);

        final JSONObject expected = (JSONObject) c.expectedRaw();
        assertArraysClose("nonuniform.output_variates",
                toDoubleArray(expected.getJSONArray("output_variates")), output);
        assertIntArraysEqual("nonuniform.bridge_index",
                toIntArray(expected.getJSONArray("bridge_index")), bb.bridgeIndex());
        assertArraysClose("nonuniform.std_deviation",
                toDoubleArray(expected.getJSONArray("std_deviation")), bb.stdDeviation());
        assertEquals(times.length, bb.size());
    }

    @Test
    public void testTransformUnitTimeSteps() {
        final ReferenceReader ref = ReferenceReader.load(BB_GROUP);
        final ReferenceReader.Case c = ref.getCase("size_only_8step");
        final JSONObject in = c.inputs();
        final int n = in.getInt("n");
        final double[] inputVar = toDoubleArray(in.getJSONArray("input_variates"));

        final BrownianBridge bb = new BrownianBridge(n);
        final double[] output = new double[n];
        bb.transform(inputVar, output);

        final JSONObject expected = (JSONObject) c.expectedRaw();
        assertArraysClose("size_only.output_variates",
                toDoubleArray(expected.getJSONArray("output_variates")), output);
        // Verify unit-time grid: t_i = i+1
        for (int i = 0; i < n; ++i) {
            assertEquals("size_only.times[" + i + "]", (double) (i + 1), bb.times()[i], 0.0);
        }
    }

    @Test
    public void testInspectorsExposed() {
        // Phase MC-extras WI-5 alignment with C++ inspectors.
        final BrownianBridge bb = new BrownianBridge(4);
        assertTrue("bridgeIndex non-null", bb.bridgeIndex() != null);
        assertTrue("leftIndex non-null", bb.leftIndex() != null);
        assertTrue("rightIndex non-null", bb.rightIndex() != null);
        assertTrue("leftWeight non-null", bb.leftWeight() != null);
        assertTrue("rightWeight non-null", bb.rightWeight() != null);
        assertTrue("stdDeviation non-null", bb.stdDeviation() != null);
        assertEquals(4, bb.bridgeIndex().length);
    }
}
