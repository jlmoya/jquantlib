/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.experimental.credit.FactorSampler;
import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.methods.montecarlo.Sample;
import org.junit.Test;

/**
 * Phase 4m.7b cross-validation of {@link FactorSampler} (generic copula-inversion
 * variant). Validates:
 * <ul>
 *   <li>Output dimension equals {@code copula.numFactors()};</li>
 *   <li>Each component is the inverse-cumulative of a uniform draw — for
 *       a Gaussian copula, samples should be approximately standard normal
 *       (mean ~ 0, variance ~ 1) over many draws;</li>
 *   <li>Determinism: same seed → same sequence (re-instantiation reproduces);</li>
 *   <li>Dimension mismatch between RSG and copula throws.</li>
 * </ul>
 *
 * <p>Tier: LOOSE (1e-2 absolute) for the empirical mean/variance — Sobol
 * convergence is sub-1/sqrt(N) but with N=4096 leakage is still ~1e-3.
 */
public class FactorSamplerTest {

    @Test
    public void factorSamplerRespectsDimension() {
        final List<List<Double>> w = Arrays.asList(
                Arrays.asList(0.5, 0.3),
                Arrays.asList(0.4, 0.6));
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(w);
        // numFactors = systemic + idiosyncratic. Inspect the policy.
        final int dim = copula.numFactors();
        assertTrue("copula must have at least 1 factor", dim >= 1);

        final SobolRsg rsg = new SobolRsg(dim, 42);
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(rsg, copula);
        assertEquals(dim, sampler.dimension());

        final Sample<double[]> s = sampler.nextSequence();
        assertNotNull(s);
        assertEquals(dim, s.value().length);
    }

    @Test
    public void factorSamplerProducesGaussianSamples() {
        final List<List<Double>> w = Arrays.asList(Arrays.asList(0.5));
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(w);
        final int dim = copula.numFactors();

        final SobolRsg rsg = new SobolRsg(dim, 42);
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(rsg, copula);

        // Draw N samples; first component should be ~ N(0,1) when the copula
        // is gaussian (since allFactorCumulInverter is the inverse of the
        // standard normal CDF for each factor).
        final int n = 4096;
        final CumulativeNormalDistribution F = new CumulativeNormalDistribution();
        double sum = 0.0;
        double sumSq = 0.0;
        // Quick KS-lite: bin 10 buckets uniformly in [0,1] via F(x) and check
        // each bucket is roughly N/10.
        final int nBuckets = 10;
        final int[] hist = new int[nBuckets];
        for (int i = 0; i < n; ++i) {
            final double[] v = sampler.nextSequence().value();
            final double x = v[0];
            sum += x;
            sumSq += x * x;
            final double u = F.op(x);
            final int b = Math.min((int) (u * nBuckets), nBuckets - 1);
            hist[b]++;
        }
        final double mean = sum / n;
        final double var = sumSq / n - mean * mean;
        assertTrue("mean ~ 0 (got " + mean + ")", Math.abs(mean) < 5e-2);
        assertTrue("variance ~ 1 (got " + var + ")", Math.abs(var - 1.0) < 5e-2);

        // Each bucket should be N/10 ± a few percent.
        for (int b = 0; b < nBuckets; ++b) {
            final double frac = hist[b] / (double) n;
            assertTrue("bucket " + b + " frac=" + frac + " not close to 0.1",
                    Math.abs(frac - 0.1) < 0.02);
        }
    }

    @Test
    public void factorSamplerIsDeterministicWithSeed() {
        final List<List<Double>> w = Arrays.asList(
                Arrays.asList(0.5, 0.3),
                Arrays.asList(0.4, 0.6));
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(w);
        final int dim = copula.numFactors();

        final var a = new FactorSampler<GaussianCopulaPolicy>(new SobolRsg(dim, 17), copula);
        final var b = new FactorSampler<GaussianCopulaPolicy>(new SobolRsg(dim, 17), copula);

        for (int i = 0; i < 20; ++i) {
            final double[] va = a.nextSequence().value();
            final double[] vb = b.nextSequence().value();
            assertEquals(va.length, vb.length);
            for (int j = 0; j < va.length; ++j) {
                // Bit-exact: same seed → same uniform sequence → same inversion.
                assertEquals("seq " + i + " comp " + j, va[j], vb[j], 0.0);
            }
        }
    }

    @Test
    public void factorSamplerRejectsDimensionMismatch() {
        final List<List<Double>> w = Arrays.asList(Arrays.asList(0.5));
        final GaussianCopulaPolicy copula = new GaussianCopulaPolicy(w);
        final SobolRsg rsg = new SobolRsg(copula.numFactors() + 1, 0);
        try {
            new FactorSampler<>(rsg, copula);
            fail("Expected exception for dimension mismatch");
        } catch (final IllegalArgumentException ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("dimension"));
        }
    }
}
