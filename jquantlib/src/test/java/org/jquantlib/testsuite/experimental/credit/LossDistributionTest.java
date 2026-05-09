/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.experimental.credit.BinomialProbabilityOfAtLeastNEvents;
import org.jquantlib.experimental.credit.Distribution;
import org.jquantlib.experimental.credit.LossDist;
import org.jquantlib.experimental.credit.LossDistBinomial;
import org.jquantlib.experimental.credit.LossDistBucketing;
import org.jquantlib.experimental.credit.LossDistHomogeneous;
import org.jquantlib.experimental.credit.LossDistMonteCarlo;
import org.jquantlib.experimental.credit.ProbabilityOfAtLeastNEvents;
import org.jquantlib.experimental.credit.ProbabilityOfNEvents;
import org.junit.Test;

/**
 * Phase 4m.6 tests for the {@link LossDist} family.
 *
 * <p>Cross-validation: closed-form binomial identities, sum-to-one of
 * exact-N-events probability vector, monotonicity of cumulative
 * (excess) probabilities. Reference: QuantLib v1.42.1
 * {@code ql/experimental/credit/lossdistribution.{hpp,cpp}}.
 */
public class LossDistributionTest {

    private static final double TIGHT = 1.0e-12;
    private static final double LOOSE = 1.0e-8;

    private static List<Double> probs(final double... v) {
        final List<Double> r = new ArrayList<>(v.length);
        for (final double d : v) r.add(d);
        return r;
    }

    @Test
    public void probabilityOfNEventsSumsToOne() {
        // For independent defaults, sum_{n=0..N} P(exactly n) = 1.
        final List<Double> p = probs(0.1, 0.2, 0.3, 0.4);
        final List<Double> probs = LossDist.probabilityOfNEvents(p);
        double sum = 0.0;
        for (final double d : probs) sum += d;
        assertEquals(1.0, sum, TIGHT);
    }

    @Test
    public void probabilityOfNEventsAllProbZero() {
        // If all probabilities are zero, probability of zero defaults = 1
        // and probabilities for n > 0 are 0.
        final List<Double> p = probs(0.0, 0.0, 0.0);
        final List<Double> probs = LossDist.probabilityOfNEvents(p);
        assertEquals(1.0, probs.get(0), TIGHT);
        assertEquals(0.0, probs.get(1), TIGHT);
        assertEquals(0.0, probs.get(2), TIGHT);
        assertEquals(0.0, probs.get(3), TIGHT);
    }

    @Test
    public void probabilityOfNEventsAllProbOne() {
        // If all probabilities are 1, then probability of N defaults = 1 and
        // probabilities for k < N are 0.
        final List<Double> p = probs(1.0, 1.0, 1.0);
        final List<Double> probs = LossDist.probabilityOfNEvents(p);
        assertEquals(0.0, probs.get(0), TIGHT);
        assertEquals(0.0, probs.get(1), TIGHT);
        assertEquals(0.0, probs.get(2), TIGHT);
        assertEquals(1.0, probs.get(3), TIGHT);
    }

    @Test
    public void probabilityOfAtLeastNEventsHomogeneous() {
        // For homogeneous probabilities the at-least-N probability matches the
        // binomial cumulative tail. Use p = 0.3, n = 5.
        final List<Double> p = probs(0.3, 0.3, 0.3, 0.3, 0.3);
        // P(>= 0) = 1 (always true)
        assertEquals(1.0, LossDist.probabilityOfAtLeastNEvents(0, p), TIGHT);
        // P(>= 5) = 0.3^5
        assertEquals(Math.pow(0.3, 5), LossDist.probabilityOfAtLeastNEvents(5, p), 1.0e-10);
    }

    @Test
    public void binomialMatchesGeneralForHomogeneousProbs() {
        // For homogeneous probabilities, the general algorithm and the binomial
        // closed form must agree.
        final List<Double> p = probs(0.25, 0.25, 0.25, 0.25, 0.25, 0.25);
        for (int n = 0; n <= 6; ++n) {
            final double general = LossDist.probabilityOfNEvents(n, p);
            final double binomial = LossDist.binomialProbabilityOfNEvents(n, p);
            assertEquals("n=" + n, binomial, general, 1.0e-10);
        }
    }

    @Test
    public void functorsForwardToStaticMethods() {
        final List<Double> p = probs(0.3, 0.3, 0.3, 0.3);
        for (int n = 0; n <= 4; ++n) {
            assertEquals(LossDist.probabilityOfNEvents(n, p),
                    new ProbabilityOfNEvents(n).op(p), TIGHT);
            assertEquals(LossDist.probabilityOfAtLeastNEvents(n, p),
                    new ProbabilityOfAtLeastNEvents(n).op(p), TIGHT);
        }
        // Binomial-at-least functor calls 1 - CumulativeBinomial(n-1); for n=0
        // this hits a degenerate Beta-function arg and is naturally undefined.
        // Skip n=0 for this functor (matches QuantLib's caller-side discipline).
        for (int n = 1; n <= 4; ++n) {
            assertEquals(LossDist.binomialProbabilityOfAtLeastNEvents(n, p),
                    new BinomialProbabilityOfAtLeastNEvents(n).op(p), TIGHT);
        }
    }

    @Test
    public void lossDistBinomialBasic() {
        // 5 names, each volume 1, default prob 0.5
        // Expected loss = N * v * p = 5 * 1 * 0.5 = 2.5
        final LossDistBinomial ld = new LossDistBinomial(20, 10.0);
        final List<Double> nominals = probs(1.0, 1.0, 1.0, 1.0, 1.0);
        final List<Double> probabilities = probs(0.5, 0.5, 0.5, 0.5, 0.5);
        final Distribution dist = ld.op(nominals, probabilities);
        assertEquals(20, dist.size());
        // probability vector should sum to 1
        double sum = 0.0;
        for (final double v : ld.probability()) sum += v;
        assertEquals(1.0, sum, LOOSE);
        // excess probability is monotonically decreasing
        final List<Double> ex = ld.excessProbability();
        for (int i = 1; i < ex.size(); ++i) {
            assertTrue("excess not monotonically decreasing at i=" + i,
                    ex.get(i) <= ex.get(i - 1));
        }
        // excess at 0 = 1
        assertEquals(1.0, ex.get(0), LOOSE);
    }

    @Test
    public void lossDistHomogeneousMatchesBinomialForUniformProbs() {
        // Equal-probability inputs reduce LossDistHomogeneous to binomial.
        // Compare probability vectors.
        final LossDistBinomial bin = new LossDistBinomial(20, 10.0);
        final LossDistHomogeneous hom = new LossDistHomogeneous(20, 10.0);
        final List<Double> nominals = probs(1.0, 1.0, 1.0, 1.0, 1.0);
        final List<Double> probabilities = probs(0.4, 0.4, 0.4, 0.4, 0.4);
        bin.op(nominals, probabilities);
        hom.op(nominals, probabilities);
        for (int i = 0; i < 6; ++i) {
            assertEquals("i=" + i, bin.probability().get(i), hom.probability().get(i), 1.0e-10);
        }
    }

    @Test
    public void lossDistBucketingPreservesTotalProbability() {
        final LossDistBucketing ld = new LossDistBucketing(50, 10.0);
        final List<Double> nominals = probs(1.0, 2.0, 1.5, 0.5, 1.0);
        final List<Double> probabilities = probs(0.1, 0.2, 0.15, 0.05, 0.1);
        final Distribution dist = ld.op(nominals, probabilities);
        // sum_{i} density(i) * dx(i) = 1
        double sum = 0.0;
        for (int i = 0; i < dist.size(); ++i) {
            sum += dist.density(i) * dist.dx(i);
        }
        assertEquals(1.0, sum, 1.0e-6);
    }

    @Test
    public void lossDistMonteCarloApproximatesBucketingForBigSample() {
        // With many simulations, MC should approximate the analytical bucketing.
        // Compare expected loss; tight enough but not overly so.
        final List<Double> nominals = probs(1.0, 1.0, 1.0, 1.0, 1.0);
        final List<Double> probabilities = probs(0.3, 0.3, 0.3, 0.3, 0.3);

        final LossDistBucketing analytical = new LossDistBucketing(20, 10.0);
        final LossDistMonteCarlo mc = new LossDistMonteCarlo(20, 10.0, 50000, 42L, 0.0);

        final Distribution dA = analytical.op(nominals, probabilities);
        final Distribution dM = mc.op(nominals, probabilities);

        double eA = 0.0;
        double eM = 0.0;
        for (int i = 0; i < dA.size(); ++i) {
            eA += dA.average(i) * dA.density(i) * dA.dx(i);
            eM += dM.average(i) * dM.density(i) * dM.dx(i);
        }
        // Both should be close to 5*0.3 = 1.5
        assertEquals("analytical EL", 1.5, eA, 1.0e-2);
        assertEquals("MC EL", 1.5, eM, 5.0e-2);
    }
}
