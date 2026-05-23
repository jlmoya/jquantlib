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

package org.jquantlib.testsuite.math.statistics;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.statistics.IncrementalStatistics;
import org.junit.Test;

/**
 * Java port of test-suite/stats.cpp::testIncrementalStatistics (Phase 5b).
 *
 * <p>This is the cached-values regression test added in QuantLib 1.7 when
 * IncrementalStatistics was rewritten as a wrapper to the boost accumulator
 * library. It feeds 500k uniform samples (with weights) and asserts
 * weightSum/mean/variance/std/error/skew/kurtosis/min/max/downsideVar/
 * downsideDev all reproduce pre-cached numerical values that pinpoint the
 * boost-accumulator parity. The C++ test then runs a numerical-stability
 * test with mu=1e8, sigma=0.1 -- a path where the old non-accumulator
 * implementation loses 8 decimals of precision.
 *
 * <h2>Cross-validation result (Phase 5e.5b-CFC-d-220)</h2>
 *
 * <p>The Java {@link IncrementalStatistics} implementation reproduces ALL
 * eleven block-1 cached values <em>bit-exactly</em> against the C++ boost
 * accumulator reference (verified 2026-05-17 via a one-shot probe on
 * MersenneTwisterUniformRng(42) feeding 500000 uniform samples with weights).
 * This is expected: Java's MT is bit-exact with C++, and the moment formulas
 * use the same algebraic identities -- the boost accumulator wrapper in C++
 * QuantLib 1.7 was a refactor, not an algorithm change, for these inputs.
 *
 * <p>Confirmed Java-side values (identical to C++):
 * <pre>
 *   weightSum         = 2.5003623600676749e+05
 *   mean              = 4.9122325964293845e-01
 *   variance          = 5.0706503959683329e+05
 *   standardDeviation = 7.1208499464378076e+02
 *   errorEstimate     = 1.0070402569876076e+00
 *   skewness          = -1.7360169326720038e-03
 *   kurtosis          = -1.1990742562085395e+00
 *   min               = -1.2339945045639761e+03
 *   max               =  1.2339958308008499e+03
 *   downsideVariance  =  5.0786776146975247e+05
 *   downsideDeviation =  7.1264841364431061e+02
 * </pre>
 *
 * <h2>Status (Phase3-truly-done-E refresh)</h2>
 *
 * <p>Block 2 of the C++ test ({@code mu=1e8, sigma=0.1}) is specifically the
 * scenario where the pre-QL-1.7 naive variance formula
 * {@code Var = <x^2>/W - <x>^2} catastrophically cancels (subtraction of two
 * numbers near 1e16, losing all precision below ~1e0). C++ QuantLib 1.7 fixed
 * this by replacing the naive accumulator with boost::accumulators (Welford
 * online algorithm).
 *
 * <p>The Java {@link IncrementalStatistics} now also uses the weighted
 * Welford / Chan-Golub-Levesque / P&eacute;bay online moment recurrences
 * (porting landed under Phase 5e.5b-CFC-d-223), so the block-2 numerical-
 * stability assertion {@code variance &asymp; 1e-2} reproduces to within the
 * {@code 1e-5} test tolerance. The test is active (no {@code @Ignore})
 * and passes under the standard test-suite profile.
 */
public class IncrementalStatisticsTest {

    public IncrementalStatisticsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testIncrementalStatistics() {
        QL.info("Testing incremental statistics...");

        // Reference C++ test-suite/stats.cpp:324
        final MersenneTwisterUniformRng mt = new MersenneTwisterUniformRng(42);
        final IncrementalStatistics stat = new IncrementalStatistics();

        for (int i = 0; i < 500000; ++i) {
            final double x = 2.0 * (mt.next().value() - 0.5) * 1234.0;
            final double w = mt.next().value();
            stat.add(x, w);
        }

        // Cross-validated bit-exact against C++ boost::accumulator reference
        // (Phase 5e.5b-CFC-d-220). Tight tier 1e-12 rel / 1e-14 abs near zero.
        final double tightRel = 1.0e-12;
        final double tightAbs = 1.0e-14;
        assertEquals(500000, stat.samples());
        assertClose("weightSum",         2.5003623600676749e+05, stat.weightSum(),         tightRel, tightAbs);
        assertClose("mean",              4.9122325964293845e-01, stat.mean(),              tightRel, tightAbs);
        assertClose("variance",          5.0706503959683329e+05, stat.variance(),          tightRel, tightAbs);
        assertClose("standardDeviation", 7.1208499464378076e+02, stat.standardDeviation(), tightRel, tightAbs);
        assertClose("errorEstimate",     1.0070402569876076e+00, stat.errorEstimate(),     tightRel, tightAbs);
        assertClose("skewness",         -1.7360169326720038e-03, stat.skewness(),          tightRel, tightAbs);
        assertClose("kurtosis",         -1.1990742562085395e+00, stat.kurtosis(),          tightRel, tightAbs);
        assertClose("min",              -1.2339945045639761e+03, stat.min(),               tightRel, tightAbs);
        assertClose("max",               1.2339958308008499e+03, stat.max(),               tightRel, tightAbs);
        assertClose("downsideVariance",  5.0786776146975247e+05, stat.downsideVariance(),  tightRel, tightAbs);
        assertClose("downsideDeviation", 7.1264841364431061e+02, stat.downsideDeviation(), tightRel, tightAbs);

        // Numerical stability: mu=1e8, sigma=0.1. C++ post-1.7 passes with boost
        // accumulators; Java still uses the naive accumulator and trips a
        // 'negative variance' assertion here. See class javadoc.
        final InverseCumulativeNormal icn = new InverseCumulativeNormal();
        final IncrementalStatistics stat2 = new IncrementalStatistics();
        for (int i = 0; i < 500000; ++i) {
            final double x = icn.op(mt.next().value()) * 1e-1 + 1e8;
            stat2.add(x, 1.0);
        }
        final double tol = 1.0e-5;
        assertEquals("stat2.variance close to 1e-2", 1.0e-2, stat2.variance(), tol);
    }

    private static void assertClose(final String name, final double expected, final double actual,
                                    final double relTol, final double absTol) {
        final double diff = Math.abs(expected - actual);
        final double tol = Math.max(absTol, relTol * Math.abs(expected));
        if (diff > tol) {
            throw new AssertionError(name + ": expected=" + expected + " actual=" + actual
                    + " diff=" + diff + " tol=" + tol);
        }
    }
}
