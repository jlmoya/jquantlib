/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 5e.5b-CFC-d-163.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */
package org.jquantlib.testsuite.math.randomnumbers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.randomnumbers.SobolBrownianBridgeRsg;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-163 — {@link SobolBrownianBridgeRsg} cross-validation.
 *
 * <p>Mirrors the structural and statistical contract of the C++
 * {@code SobolBrownianBridgeRsg} (v1.42.1
 * {@code ql/math/randomnumbers/sobolbrownianbridgersg.{hpp,cpp}}). Reference
 * sequence values were generated via
 * {@code migration-harness/cpp/probes/math/randomnumbers/sobol_brownian_bridge_rsg_probe.cpp}
 * and persisted to
 * {@code migration-harness/references/math/randomnumbers/sobol_brownian_bridge_rsg.json}.
 *
 * <p>Three configurations are exercised:
 * <ul>
 *   <li>{@code f=2, s=4, Diagonal, seed=0, Jaeckel}</li>
 *   <li>{@code f=2, s=4, Factors,  seed=42, Jaeckel}</li>
 *   <li>{@code f=3, s=8, Steps,    seed=12345, Jaeckel}</li>
 * </ul>
 *
 * <p>Tolerance: tight tier (1e-12 rel / 1e-14 abs). Empirically Java matches
 * C++ to ~1 ULP on every dimension of every sample; we allow {@code 1e-12}
 * absolute slack to absorb any future tweaks to the inverse-cumulative
 * approximation while still flagging genuine regressions.
 */
public class SobolBrownianBridgeRsgTest {

    /** Tight tier tolerance per CLAUDE.md (1e-12 rel / 1e-14 abs near zero). */
    private static final double TOL = 1.0e-12;

    public SobolBrownianBridgeRsgTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void dimensionEqualsFactorsTimesSteps() {
        final SobolBrownianBridgeRsg rsg = new SobolBrownianBridgeRsg(3, 8);
        assertEquals(24, rsg.dimension());
    }

    @Test
    public void lastSequenceReturnsSameBufferAsNextSequence() {
        final SobolBrownianBridgeRsg rsg = new SobolBrownianBridgeRsg(2, 4);
        final Sample<double[]> first = rsg.nextSequence();
        final Sample<double[]> last = rsg.lastSequence();
        assertSame("lastSequence must alias nextSequence buffer", first, last);
    }

    @Test
    public void allConstructorOverloadsAccepted() {
        // Smoke-test all four ctor signatures compile + run.
        assertNotNull(new SobolBrownianBridgeRsg(2, 4));
        assertNotNull(new SobolBrownianBridgeRsg(2, 4, SobolBrownianGenerator.Ordering.Factors));
        assertNotNull(new SobolBrownianBridgeRsg(2, 4, SobolBrownianGenerator.Ordering.Steps, 7L));
        assertNotNull(new SobolBrownianBridgeRsg(2, 4, SobolBrownianGenerator.Ordering.Diagonal,
                7L, SobolRsg.DirectionIntegers.Jaeckel));
    }

    @Test
    public void sequenceIsStepMajor_perCppLayout() {
        // C++ setNextSequence packs (step-major): seq[i*factors + f] = output[f]
        // for step i, factor f. Verify total length and that the buffer is
        // length factors*steps.
        final int factors = 3, steps = 5;
        final SobolBrownianBridgeRsg rsg = new SobolBrownianBridgeRsg(factors, steps);
        final Sample<double[]> s = rsg.nextSequence();
        assertEquals(factors * steps, s.value().length);
        assertEquals(1.0, s.weight(), 0.0);
    }

    /**
     * Case 1: factors=2, steps=4, Diagonal, seed=0, Jaeckel — first 5
     * sequences. Cross-validated against C++ v1.42.1 (probe
     * {@code sobol_brownian_bridge_rsg_probe} commit
     * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
     */
    @Test
    public void f2s4_diagonal_seed0_jaeckel_firstFiveSequences() {
        final double[][] expected = {
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            { 1.1514260264472251, -1.1514260264472251,  0.19755347399961987, -0.19755347399961987,
              0.47693627622380275, -0.47693627622380275, -0.47693627622380275,  0.47693627622380275 },
            {-1.1514260264472251,  1.1514260264472251, -0.19755347399961987,  0.19755347399961987,
             -0.47693627622380275,  0.47693627622380275,  0.47693627622380275, -0.47693627622380275 },
            { 0.813419847696433,    0.07892547561585317, -0.813419847696433,   -1.547914219777013,
             -1.13205921134184,     0.6411670632218066,   0.49478048405102615,  0.19054295364853935 },
            {-0.2253120547866335,   0.5091823172939464,   0.2253120547866335,   0.9598064268672134,
              1.3756614353023864, -0.3975648392612601,   0.9250373257291196,   1.2292748561316063 }
        };
        runFixture(new SobolBrownianBridgeRsg(2, 4,
                SobolBrownianGenerator.Ordering.Diagonal, 0L,
                SobolRsg.DirectionIntegers.Jaeckel), expected);
    }

    /**
     * Case 2: factors=2, steps=4, Factors, seed=42, Jaeckel — first 3 sequences.
     */
    @Test
    public void f2s4_factors_seed42_jaeckel_firstThreeSequences() {
        final double[][] expected = {
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            { 0.47693627622380275,  0.47693627622380275, -0.47693627622380275, -0.47693627622380275,
              0.19755347399961976,  0.19755347399961976,  1.1514260264472251,   1.1514260264472251 },
            {-0.47693627622380275, -0.47693627622380275,  0.47693627622380275,  0.47693627622380275,
             -0.19755347399961976, -0.19755347399961976, -1.1514260264472251,  -1.1514260264472251 }
        };
        runFixture(new SobolBrownianBridgeRsg(2, 4,
                SobolBrownianGenerator.Ordering.Factors, 42L,
                SobolRsg.DirectionIntegers.Jaeckel), expected);
    }

    /**
     * Case 3: factors=3, steps=8, Steps, seed=12345, Jaeckel — first 3 sequences.
     * 24-dimensional output exercises a larger Sobol dimensionality.
     */
    @Test
    public void f3s8_steps_seed12345_jaeckel_firstThreeSequences() {
        final double[][] expected = {
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
             0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
             0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 },
            {-0.1396914011120915,  0.1396914011120915,  0.1396914011120915,  0.8141811513355139,
             -0.8141811513355139, -0.8141811513355139, -0.8141811513355139,  0.8141811513355139,
              0.8141811513355139,  0.1396914011120915, -0.1396914011120915, -0.1396914011120915,
              0.3372448751117112, -0.6166276773358942, -0.33724487511171125, 1.2911174275593167,
              0.3372448751117112,  0.6166276773358943, -0.3372448751117112, -1.2911174275593167,
              1.2911174275593167,  0.6166276773358943, -0.3372448751117112,  0.3372448751117112 },
            { 0.1396914011120915, -0.1396914011120915, -0.1396914011120915, -0.8141811513355139,
              0.8141811513355139,  0.8141811513355139,  0.8141811513355139, -0.8141811513355139,
             -0.8141811513355139, -0.1396914011120915,  0.1396914011120915,  0.1396914011120915,
             -0.3372448751117112,  0.6166276773358942,  0.33724487511171125, -1.2911174275593167,
             -0.3372448751117112, -0.6166276773358943,  0.3372448751117112,  1.2911174275593167,
             -1.2911174275593167, -0.6166276773358943,  0.3372448751117112, -0.3372448751117112 }
        };
        runFixture(new SobolBrownianBridgeRsg(3, 8,
                SobolBrownianGenerator.Ordering.Steps, 12345L,
                SobolRsg.DirectionIntegers.Jaeckel), expected);
    }

    /**
     * Statistical sanity: after many sequences the per-dimension mean must
     * lie near zero and variance near one (since the bridged variates are
     * unit-variance Gaussians). Sobol convergence is fast, so a few thousand
     * sequences yield very tight moments.
     */
    @Test
    public void statisticalSanity_meanNearZero_varianceNearOne() {
        final int factors = 2, steps = 4, sequences = 8192;
        final SobolBrownianBridgeRsg rsg = new SobolBrownianBridgeRsg(factors, steps,
                SobolBrownianGenerator.Ordering.Diagonal, 0L,
                SobolRsg.DirectionIntegers.Jaeckel);
        final int dim = factors * steps;
        long n = 0;
        double sum = 0.0, sumSq = 0.0;
        for (int p = 0; p < sequences; ++p) {
            final double[] v = rsg.nextSequence().value();
            for (int i = 0; i < dim; ++i) {
                sum += v[i];
                sumSq += v[i] * v[i];
                ++n;
            }
        }
        final double mean = sum / n;
        final double variance = sumSq / n - mean * mean;
        assertEquals("sample mean ≈ 0", 0.0, mean, 0.05);
        assertEquals("sample variance ≈ 1", 1.0, variance, 0.10);
    }

    private static void runFixture(final SobolBrownianBridgeRsg rsg, final double[][] expected) {
        if (expected.length == 0) {
            fail("empty expected fixture");
        }
        final int expectedDim = expected[0].length;
        assertEquals("dimension matches fixture", expectedDim, rsg.dimension());
        for (int k = 0; k < expected.length; ++k) {
            final Sample<double[]> s = rsg.nextSequence();
            assertEquals("weight is 1.0 on sequence " + k, 1.0, s.weight(), 0.0);
            final double[] got = s.value();
            assertEquals("sequence " + k + " length", expectedDim, got.length);
            for (int i = 0; i < expectedDim; ++i) {
                assertEquals("seq[" + k + "][" + i + "]", expected[k][i], got[i], TOL);
            }
        }
    }
}
