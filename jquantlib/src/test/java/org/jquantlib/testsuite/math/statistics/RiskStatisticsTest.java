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

/*
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2003 RiskMap srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.math.statistics;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.math.statistics.GaussianStatsHolder;
import org.jquantlib.math.statistics.IncrementalStatistics;
import org.jquantlib.math.statistics.RiskStatistics;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/riskstats.cpp (Phase 2 L6-A).
 *
 * <p>Single C++ {@code BOOST_AUTO_TEST_CASE testResults} drives 5 means x
 * 3 sigmas = 15 N(mu,sigma) datasets through {@link RiskStatistics} and
 * {@link IncrementalStatistics}, verifying samples / weight sum / min / max,
 * the central moments (mean / variance / std dev / skewness / kurtosis),
 * Gaussian percentile / VaR / expected-shortfall / shortfall /
 * average-shortfall / regret / downside-variance, and the empirical
 * counterparts. Each (mu, sigma) pair generates 2^16 - 1 = 65535 Sobol draws
 * mapped through the inverse normal CDF.
 *
 * <p><b>Java mapping:</b> C++ uses {@code GenericGaussianStatistics<IncrementalStatistics>}
 * and {@code RiskStatistics = GenericRiskStatistics<GaussianStatistics>}. In
 * Java, {@link IncrementalStatistics} already extends
 * {@code GenericRiskStatistics} (which itself extends {@code GaussianStatistics}),
 * so the {@code gaussian*} entry points are available on both stat objects.
 *
 * <p>All asserts that were previously commented out in the initial L6-A port
 * are now enabled following the 2026-05-23 {@code align(math.statistics)} fix
 * to {@link org.jquantlib.math.statistics.GenericRiskStatistics}, which
 * corrected three composition bugs:
 * <ol>
 *   <li>{@code regret(target)} composed
 *       {@code Expression([Square, Bind2nd(Minus, target)])} (Square FIRST),
 *       evaluating {@code (x)^2 - target} instead of {@code (x - target)^2}.
 *       Replaced with {@code ComposedFunction(Square, Bind2nd(Minus, target))}
 *       = {@code (x - target)^2}, matching variance()/skewness()/kurtosis()
 *       composition order in {@link
 *       org.jquantlib.math.statistics.GeneralStatistics}. This also fixes
 *       {@code downsideVariance()} (which is {@code regret(0.0)}) and
 *       {@code semiVariance()} (which is {@code regret(mean())}).</li>
 *   <li>{@code averageShortfall(target)} used
 *       {@code Bind1stPredicate(target, LessThan)}, evaluating
 *       {@code LessThan.op(target, xi) = (target < xi)} -- the UPPER half --
 *       instead of {@code xi < target} (LOWER half). Replaced with
 *       {@code Bind2ndPredicate(LessThan, target)}.</li>
 *   <li>{@code shortfall(target)} used {@code Clipped(less, Constant(1.0))}
 *       which returns {@code Double.NaN} when {@code x >= target}, poisoning
 *       the sum to NaN. Replaced with a direct 1.0/0.0 indicator over the
 *       full range.</li>
 * </ol>
 *
 * <p>The C++ {@code GenericGaussianStatistics<StatsHolder>} sub-test
 * (riskstats.cpp lines 343-351) now runs against {@link
 * org.jquantlib.math.statistics.GaussianStatsHolder} -- a small concrete Java
 * class that mirrors the {@code gaussian*} risk measures on a fixed
 * {@code (mean, sigma)} pair, providing the same shape as the C++ template
 * instantiation.
 */
public class RiskStatisticsTest {

    public RiskStatisticsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testResults() {
        QL.info("Testing risk measures...");

        final IncrementalStatistics igs = new IncrementalStatistics();
        final RiskStatistics s = new RiskStatistics();

        final double[] averages = { -100.0, -1.0, 0.0, 1.0, 100.0 };
        final double[] sigmas = { 0.1, 1.0, 100.0 };
        final int N = (int) Math.pow(2.0, 16) - 1;
        final double[] data = new double[N];
        final double[] weights = new double[N];

        for (int i = 0; i < averages.length; i++) {
            for (int j = 0; j < sigmas.length; j++) {

                final NormalDistribution normal =
                        new NormalDistribution(averages[i], sigmas[j]);
                final CumulativeNormalDistribution cumulative =
                        new CumulativeNormalDistribution(averages[i], sigmas[j]);
                final InverseCumulativeNormal inverseCum =
                        new InverseCumulativeNormal(averages[i], sigmas[j]);

                final SobolRsg rng = new SobolRsg(1);
                double dataMin = Double.MAX_VALUE;
                double dataMax = -Double.MAX_VALUE;
                for (int k = 0; k < N; k++) {
                    data[k] = inverseCum.op(rng.nextSequence().value()[0]);
                    dataMin = Math.min(dataMin, data[k]);
                    dataMax = Math.max(dataMax, data[k]);
                    weights[k] = 1.0;
                }

                igs.addSequence(data, weights);
                s.addSequence(data, weights);

                final String tag = " for N(" + averages[i] + ", " + sigmas[j] + ")";

                // ---- samples ----
                assertEquals("IncrementalGaussianStatistics: wrong number of samples",
                        N, igs.samples());
                assertEquals("RiskStatistics: wrong number of samples",
                        N, s.samples());

                // ---- weightSum ----
                double tolerance = 1e-10;
                double expected = (double) N; // weights all 1.0
                assertEquals("IncrementalGaussianStatistics: wrong sum of weights",
                        expected, igs.weightSum(), tolerance);
                assertEquals("RiskStatistics: wrong sum of weights",
                        expected, s.weightSum(), tolerance);

                // ---- min ----
                tolerance = 1e-12;
                assertEquals("IncrementalGaussianStatistics: wrong minimum value",
                        dataMin, igs.min(), tolerance);
                assertEquals("RiskStatistics: wrong minimum value",
                        dataMin, s.min(), tolerance);

                // ---- max ----
                assertEquals("IncrementalGaussianStatistics: wrong maximum value",
                        dataMax, igs.max(), tolerance);
                assertEquals("RiskStatistics: wrong maximum value",
                        dataMax, s.max(), tolerance);

                // ---- mean ----
                expected = averages[i];
                tolerance = (expected == 0.0) ? 1.0e-13 : Math.abs(expected) * 1.0e-13;
                assertEquals("IncrementalGaussianStatistics: wrong mean value" + tag,
                        expected, igs.mean(), tolerance);
                assertEquals("RiskStatistics: wrong mean value" + tag,
                        expected, s.mean(), tolerance);

                // ---- variance ----
                expected = sigmas[j] * sigmas[j];
                tolerance = expected * 1.0e-1;
                assertEquals("IncrementalGaussianStatistics: wrong variance" + tag,
                        expected, igs.variance(), tolerance);
                assertEquals("RiskStatistics: wrong variance" + tag,
                        expected, s.variance(), tolerance);

                // ---- standardDeviation ----
                expected = sigmas[j];
                tolerance = expected * 1.0e-1;
                assertEquals("IncrementalGaussianStatistics: wrong stddev" + tag,
                        expected, igs.standardDeviation(), tolerance);
                assertEquals("RiskStatistics: wrong stddev" + tag,
                        expected, s.standardDeviation(), tolerance);

                // ---- skewness ----
                expected = 0.0;
                tolerance = 1.0e-4;
                assertEquals("IncrementalGaussianStatistics: wrong skewness" + tag,
                        expected, igs.skewness(), tolerance);
                assertEquals("RiskStatistics: wrong skewness" + tag,
                        expected, s.skewness(), tolerance);

                // ---- kurtosis ----
                expected = 0.0;
                tolerance = 1.0e-1;
                assertEquals("IncrementalGaussianStatistics: wrong kurtosis" + tag,
                        expected, igs.kurtosis(), tolerance);
                assertEquals("RiskStatistics: wrong kurtosis" + tag,
                        expected, s.kurtosis(), tolerance);

                // ---- percentile ----
                expected = averages[i];
                tolerance = (expected == 0.0) ? 1.0e-3 : Math.abs(expected * 1.0e-3);
                assertEquals("IncrementalGaussianStatistics: wrong gaussianPercentile" + tag,
                        expected, igs.gaussianPercentile(0.5), tolerance);
                assertEquals("RiskStatistics: wrong gaussianPercentile" + tag,
                        expected, s.gaussianPercentile(0.5), tolerance);
                assertEquals("RiskStatistics: wrong percentile" + tag,
                        expected, s.percentile(0.5), tolerance);

                // ---- potentialUpside ----
                final double upper_tail = averages[i] + 2.0 * sigmas[j];
                final double lower_tail = averages[i] - 2.0 * sigmas[j];
                final double twoSigma = cumulative.op(upper_tail);

                expected = Math.max(upper_tail, 0.0);
                tolerance = (expected == 0.0) ? 1.0e-3 : Math.abs(expected * 1.0e-3);
                assertEquals("IncrementalGaussianStatistics: wrong gaussianPotentialUpside" + tag,
                        expected, igs.gaussianPotentialUpside(twoSigma), tolerance);
                assertEquals("RiskStatistics: wrong gaussianPotentialUpside" + tag,
                        expected, s.gaussianPotentialUpside(twoSigma), tolerance);
                assertEquals("RiskStatistics: wrong potentialUpside" + tag,
                        expected, s.potentialUpside(twoSigma), tolerance);

                // ---- GenericGaussianStatistics<StatsHolder> sub-test ----
                // Mirrors v1.42.1 riskstats.cpp lines 343-351 -- "just to check
                // that GaussianStatistics<StatsHolder> does work". Uses
                // GaussianStatsHolder, the Java analogue of the C++ template
                // instantiation.
                {
                    final GaussianStatsHolder testHolder = new GaussianStatsHolder(
                            s.mean(), s.standardDeviation());
                    final double expectedHolder = s.gaussianPotentialUpside(twoSigma);
                    final double calculatedHolder = testHolder.gaussianPotentialUpside(twoSigma);
                    if (!Closeness.isClose(calculatedHolder, expectedHolder)) {
                        org.junit.Assert.fail("GaussianStatsHolder fails"
                                + " for N(" + averages[i] + ", " + sigmas[j] + ")"
                                + "\n  calculated: " + calculatedHolder
                                + "\n  expected:   " + expectedHolder);
                    }
                }

                // ---- value-at-risk ----
                expected = -Math.min(lower_tail, 0.0);
                tolerance = (expected == 0.0) ? 1.0e-3 : Math.abs(expected * 1.0e-3);
                assertEquals("IncrementalGaussianStatistics: wrong gaussianValueAtRisk" + tag,
                        expected, igs.gaussianValueAtRisk(twoSigma), tolerance);
                assertEquals("RiskStatistics: wrong gaussianValueAtRisk" + tag,
                        expected, s.gaussianValueAtRisk(twoSigma), tolerance);
                assertEquals("RiskStatistics: wrong valueAtRisk" + tag,
                        expected, s.valueAtRisk(twoSigma), tolerance);

                if (averages[i] > 0.0 && sigmas[j] < averages[i]) {
                    // no data will miss the targets: skip the rest of this iteration
                    igs.reset();
                    s.reset();
                    continue;
                }

                // ---- expected shortfall ----
                expected = -Math.min(averages[i] - sigmas[j] * sigmas[j]
                        * normal.op(lower_tail) / (1.0 - twoSigma), 0.0);
                tolerance = (expected == 0.0) ? 1.0e-4 : Math.abs(expected) * 1.0e-2;
                assertEquals("IncrementalGaussianStatistics: wrong gaussianExpectedShortfall" + tag,
                        expected, igs.gaussianExpectedShortfall(twoSigma), tolerance);
                assertEquals("RiskStatistics: wrong gaussianExpectedShortfall" + tag,
                        expected, s.gaussianExpectedShortfall(twoSigma), tolerance);
                assertEquals("RiskStatistics: wrong expectedShortfall" + tag,
                        expected, s.expectedShortfall(twoSigma), tolerance);

                // ---- shortfall ----
                expected = 0.5;
                tolerance = (expected == 0.0) ? 1.0e-3 : Math.abs(expected * 1.0e-3);
                assertEquals("IncrementalGaussianStatistics: wrong gaussianShortfall" + tag,
                        expected, igs.gaussianShortfall(averages[i]), tolerance);
                assertEquals("RiskStatistics: wrong gaussianShortfall" + tag,
                        expected, s.gaussianShortfall(averages[i]), tolerance);
                assertEquals("RiskStatistics: wrong shortfall" + tag,
                        expected, s.shortfall(averages[i]), tolerance);

                // ---- average shortfall ----
                expected = sigmas[j] / Math.sqrt(2.0 * Math.PI) * 2.0;
                tolerance = expected * 1.0e-3;
                assertEquals("IncrementalGaussianStatistics: wrong gaussianAverageShortfall" + tag,
                        expected, igs.gaussianAverageShortfall(averages[i]), tolerance);
                assertEquals("RiskStatistics: wrong gaussianAverageShortfall" + tag,
                        expected, s.gaussianAverageShortfall(averages[i]), tolerance);
                assertEquals("RiskStatistics: wrong averageShortfall" + tag,
                        expected, s.averageShortfall(averages[i]), tolerance);

                // ---- regret ----
                expected = sigmas[j] * sigmas[j];
                tolerance = expected * 1.0e-1;
                assertEquals("IncrementalGaussianStatistics: wrong gaussianRegret(" + averages[i] + ")" + tag,
                        expected, igs.gaussianRegret(averages[i]), tolerance);
                assertEquals("RiskStatistics: wrong gaussianRegret(" + averages[i] + ")" + tag,
                        expected, s.gaussianRegret(averages[i]), tolerance);
                assertEquals("RiskStatistics: wrong regret(" + averages[i] + ")" + tag,
                        expected, s.regret(averages[i]), tolerance);

                // ---- downside variance ----
                // First the C++ self-consistency check: s.downsideVariance() vs
                // igs.downsideVariance() and igs.gaussianDownsideVariance().
                expected = s.downsideVariance();
                tolerance = (expected == 0.0) ? 1.0e-3 : Math.abs(expected * 1.0e-3);
                assertEquals("IncrementalGaussianStatistics: wrong downsideVariance" + tag,
                        expected, igs.downsideVariance(), tolerance);
                assertEquals("IncrementalGaussianStatistics: wrong gaussianDownsideVariance" + tag,
                        expected, igs.gaussianDownsideVariance(), tolerance);

                // mu==0 special case -- expected sigma^2 reference.
                if (averages[i] == 0.0) {
                    expected = sigmas[j] * sigmas[j];
                    tolerance = expected * 1.0e-3;
                    assertEquals("IncrementalGaussianStatistics: wrong downsideVariance (mu=0)" + tag,
                            expected, igs.downsideVariance(), tolerance);
                    assertEquals("IncrementalGaussianStatistics: wrong gaussianDownsideVariance (mu=0)" + tag,
                            expected, igs.gaussianDownsideVariance(), tolerance);
                    assertEquals("RiskStatistics: wrong downsideVariance (mu=0)" + tag,
                            expected, s.downsideVariance(), tolerance);
                    assertEquals("RiskStatistics: wrong gaussianDownsideVariance (mu=0)" + tag,
                            expected, s.gaussianDownsideVariance(), tolerance);
                }

                igs.reset();
                s.reset();
            }
        }
    }

    /**
     * Focused regression test for the pre-2026-05-23 regret-composition bug.
     *
     * <p>For samples drawn from N(-100, 0.1), {@code s.regret(-100)} must be
     * approximately {@code sigma^2 = 0.01} (within 10% per the
     * {@link #testResults} tolerance schedule). The buggy implementation
     * returned ~10116 because it evaluated {@code (x)^2 - target} rather than
     * {@code (x - target)^2} via {@code Expression([Square, Bind2nd(Minus,
     * target)])} (Square applied FIRST, not LAST).
     *
     * <p>This test is intentionally self-contained -- single (mu, sigma) cell,
     * deterministic Sobol seed -- so the regression manifests as a 6-order-
     * of-magnitude difference, not a tolerance breach, making future
     * accidental regressions impossible to miss.
     */
    @Test
    public void testRegretAtNegativeHundred() {
        QL.info("Testing regret(-100) at N(-100, 0.1) -- regret composition fix...");

        final RiskStatistics s = new RiskStatistics();
        final InverseCumulativeNormal inverseCum = new InverseCumulativeNormal(-100.0, 0.1);
        final SobolRsg rng = new SobolRsg(1);

        final int N = (int) Math.pow(2.0, 16) - 1;
        final double[] data = new double[N];
        final double[] weights = new double[N];
        for (int k = 0; k < N; k++) {
            data[k] = inverseCum.op(rng.nextSequence().value()[0]);
            weights[k] = 1.0;
        }
        s.addSequence(data, weights);

        final double sigma2 = 0.1 * 0.1;
        final double regret = s.regret(-100.0);
        assertEquals(
                "regret(-100) for N(-100, 0.1) should be ~sigma^2 = 0.01"
                        + " -- got " + regret
                        + " (pre-fix value was ~10116, off by 6 orders of magnitude)",
                sigma2, regret, sigma2 * 1.0e-1);

        // downsideVariance() is regret(0.0). For x ~ N(-100, 0.1) every sample
        // is below 0, so downsideVariance approximates variance + mean^2 - 0 =
        // sigma^2 + mu^2 (=> ~10000.01). Sanity-check it is finite and
        // positive (the NaN-poisoning shortfall bug used to surface here too).
        final double dv = s.downsideVariance();
        org.junit.Assert.assertTrue("downsideVariance must be finite", Double.isFinite(dv));
        org.junit.Assert.assertTrue("downsideVariance must be positive: " + dv, dv > 0.0);
    }
}
