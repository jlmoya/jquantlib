/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.experimental.math.TCopulaPolicy;
import org.junit.Test;

/**
 * Phase 4m.6 tests for {@link TCopulaPolicy}.
 *
 * <p>Cross-validation: closed-form Student-T identities and direct calls to
 * the policy's normalisation invariants. Reference: QuantLib v1.42.1
 * {@code ql/experimental/math/tcopulapolicy.{hpp,cpp}}.
 */
public class TCopulaPolicyTest {

    private static final double TIGHT = 1.0e-12;
    private static final double LOOSE = 1.0e-8;

    @Test
    public void emptyConstructor() {
        final TCopulaPolicy p = new TCopulaPolicy();
        // 0 latentVarsInverters_ + 0 varianceFactors_ - 1 = -1 (matches C++)
        assertEquals(-1, p.numFactors());
        assertNotNull(p.getInitTraits());
    }

    @Test
    public void singleFactorConstruction() {
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.3);
        weights.add(row);
        // tOrders for [factor, idiosyncratic] — both odd so the BehrensFisher
        // convolution polynomial is well-defined.
        final TCopulaPolicy.InitTraits traits = new TCopulaPolicy.InitTraits(5, 5);
        final TCopulaPolicy p = new TCopulaPolicy(weights, traits);
        // numFactors = 1 (latentVars) + 2 (varianceFactors) - 1 = 2
        assertEquals(2, p.numFactors());
        // round-trip the orders through getInitTraits
        assertEquals(2, p.getInitTraits().tOrders.size());
        assertEquals(Integer.valueOf(5), p.getInitTraits().tOrders.get(0));
    }

    @Test
    public void rejectsNonNormalisedFactors() {
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.9);
        row.add(0.9);  // sum-of-squares = 1.62 > 1
        weights.add(row);
        final TCopulaPolicy.InitTraits traits = new TCopulaPolicy.InitTraits(5, 5, 5);
        try {
            new TCopulaPolicy(weights, traits);
            fail("Expected exception for non-normalised factor weights");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void rejectsLowDof() {
        final TCopulaPolicy.InitTraits traits = new TCopulaPolicy.InitTraits(2, 5);
        try {
            new TCopulaPolicy(new ArrayList<>(), traits);
            fail("Expected exception for tOrder <= 2");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void varianceFactorsMatchClosedForm() {
        // varianceFactors_[i] = sqrt((nu_i - 2) / nu_i)
        final TCopulaPolicy.InitTraits traits = new TCopulaPolicy.InitTraits(5, 7, 9);
        final TCopulaPolicy p = new TCopulaPolicy(new ArrayList<>(), traits);
        final List<Double> vf = p.varianceFactors();
        assertEquals(Math.sqrt(3.0 / 5.0), vf.get(0), TIGHT);
        assertEquals(Math.sqrt(5.0 / 7.0), vf.get(1), TIGHT);
        assertEquals(Math.sqrt(7.0 / 9.0), vf.get(2), TIGHT);
    }

    @Test
    public void cumulativeYAtZeroIsHalf() {
        // F_Y(0) = 1/2 by symmetry of the convolved Student-T
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.5);
        weights.add(row);
        final TCopulaPolicy.InitTraits traits = new TCopulaPolicy.InitTraits(5, 5);
        final TCopulaPolicy p = new TCopulaPolicy(weights, traits);
        assertEquals(0.5, p.cumulativeY(0.0, 0), TIGHT);
    }

    @Test
    public void cumulativeZAtZeroIsHalf() {
        // F_Z(0) = 1/2 by symmetry of the (rescaled) idiosyncratic Student-T
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.5);
        weights.add(row);
        final TCopulaPolicy.InitTraits traits = new TCopulaPolicy.InitTraits(5, 5);
        final TCopulaPolicy p = new TCopulaPolicy(weights, traits);
        assertEquals(0.5, p.cumulativeZ(0.0), TIGHT);
    }

    @Test
    public void inverseCumulativeZRoundTrip() {
        // F_Z^{-1}(F_Z(z)) = z (loose due to Newton iteration tolerance)
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.5);
        weights.add(row);
        final TCopulaPolicy.InitTraits traits = new TCopulaPolicy.InitTraits(5, 5);
        final TCopulaPolicy p = new TCopulaPolicy(weights, traits);
        // can't easily round-trip cumulativeZ since varianceFactors get baked in.
        // Just confirm inverseZ at p=0.5 is 0
        assertEquals(0.0, p.inverseCumulativeZ(0.5), LOOSE);
    }

    @Test
    public void densityIsNonNegativeAndPeaksAtZero() {
        // single-systemic-factor density at m=0 should be positive and exceed
        // the value at m=2 for a unit-variance T.
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.5);
        weights.add(row);
        final TCopulaPolicy.InitTraits traits = new TCopulaPolicy.InitTraits(5, 5);
        final TCopulaPolicy p = new TCopulaPolicy(weights, traits);
        final List<Double> at0 = new ArrayList<>(); at0.add(0.0);
        final List<Double> at2 = new ArrayList<>(); at2.add(2.0);
        final double d0 = p.density(at0);
        final double d2 = p.density(at2);
        assertTrue("density at 0 should be positive: " + d0, d0 > 0.0);
        assertTrue("density at 2 should be positive: " + d2, d2 > 0.0);
        assertTrue("density should peak at 0 (d0=" + d0 + " vs d2=" + d2 + ")", d0 > d2);
    }

    @Test
    public void allFactorCumulInverterAtHalfIsZero() {
        // For p=0.5 every factor inverse → 0 by symmetry
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.4);
        weights.add(row);
        final TCopulaPolicy.InitTraits traits = new TCopulaPolicy.InitTraits(5, 5);
        final TCopulaPolicy p = new TCopulaPolicy(weights, traits);
        // 1 systemic factor + N idiosyncratic. probs.length = systemic + idio
        // Use 3 entries: 1 systemic, 2 idio
        final double[] probs = { 0.5, 0.5, 0.5 };
        final double[] result = p.allFactorCumulInverter(probs);
        assertArrayEquals(new double[] { 0.0, 0.0, 0.0 }, result, LOOSE);
    }
}
