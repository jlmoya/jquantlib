/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-RND-b — DiscreteSimpsonIntegral tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */
package org.jquantlib.testsuite.math.integrals;

import org.jquantlib.QL;
import org.jquantlib.math.integrals.DiscreteSimpsonIntegral;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tier-stratified tests for {@link DiscreteSimpsonIntegral}.
 *
 * <p>Reference values are derived analytically:
 * <ul>
 *   <li>Composite Simpson's 1/3 rule is exact for polynomials of degree
 *       &le; 3 on a uniform grid — all polynomial cases use the TIGHT
 *       tier (1e-12).</li>
 *   <li>For sin/exp the leading error is {@code O(h^4)} on a uniform
 *       grid — verified at LOOSE tier (1e-5) with adequate sampling.</li>
 * </ul>
 *
 * @author Phase 5h.5-RND-b
 */
public class DiscreteSimpsonIntegralTest {

    private static final double TIGHT = 1.0e-12;
    private static final double LOOSE = 1.0e-5;

    public DiscreteSimpsonIntegralTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    /** Constant function → integral is exactly (b - a). Tier: TIGHT. */
    @Test
    public void integratesConstantExactly() {
        final int n = 11;
        final Array x = uniformGrid(0.0, 1.0, n);
        final Array f = constArray(n, 7.0);
        final double sum = new DiscreteSimpsonIntegral().op(x, f);
        assertEquals(7.0, sum, TIGHT);
    }

    /** Linear function on uniform grid → exact. Tier: TIGHT. */
    @Test
    public void integratesLinearExactly() {
        final int n = 11;
        final Array x = uniformGrid(0.0, 2.0, n);
        final Array f = new Array(n);
        for (int i = 0; i < n; ++i) {
            f.set(i, 3.0 * x.get(i) + 1.0);
        }
        final double sum = new DiscreteSimpsonIntegral().op(x, f);
        // \int_0^2 (3x + 1) dx = 1.5 * 4 + 2 = 8
        assertEquals(8.0, sum, TIGHT);
    }

    /** Cubic function on uniform grid → exact (Simpson is exact through degree 3). */
    @Test
    public void integratesCubicExactlyOnUniformGrid() {
        final int n = 11;
        final Array x = uniformGrid(0.0, 1.0, n);
        final Array f = new Array(n);
        for (int i = 0; i < n; ++i) {
            final double xi = x.get(i);
            f.set(i, xi * xi * xi); // x^3
        }
        final double sum = new DiscreteSimpsonIntegral().op(x, f);
        // \int_0^1 x^3 dx = 1/4
        assertEquals(0.25, sum, TIGHT);
    }

    /** Polynomial sum on uniform grid → exact through degree 3. */
    @Test
    public void integratesGeneralCubicExactly() {
        final int n = 21;
        final Array x = uniformGrid(-1.0, 2.0, n);
        final Array f = new Array(n);
        for (int i = 0; i < n; ++i) {
            final double xi = x.get(i);
            f.set(i, xi * xi * xi - 2.0 * xi * xi + xi + 1.0);
        }
        final double sum = new DiscreteSimpsonIntegral().op(x, f);
        // \int_{-1}^{2} (x^3 - 2x^2 + x + 1) dx
        //   = [x^4/4 - 2x^3/3 + x^2/2 + x] from -1 to 2
        //   = (4 - 16/3 + 2 + 2) - (1/4 + 2/3 + 1/2 - 1)
        //   = 8 - 16/3 - 0.4166666666666667
        //   = 8 - 5.333... - 0.41666... = 2.25
        assertEquals(2.25, sum, TIGHT);
    }

    /** sin(x) on a fine uniform grid: O(h^4) accuracy. Tier: LOOSE. */
    @Test
    public void integratesSinAccurately() {
        final int n = 51; // odd → no trapezoid tail
        final Array x = uniformGrid(0.0, Math.PI, n);
        final Array f = new Array(n);
        for (int i = 0; i < n; ++i) {
            f.set(i, Math.sin(x.get(i)));
        }
        final double sum = new DiscreteSimpsonIntegral().op(x, f);
        // \int_0^pi sin(x) dx = 2
        assertEquals(2.0, sum, LOOSE);
    }

    /**
     * Parabola on a non-uniform grid — the C++ pair-step formula fits a
     * quadratic through three consecutive nodes regardless of spacing, so
     * a global parabolic integrand is recovered exactly. Tier: TIGHT.
     */
    @Test
    public void integratesParabolaOnNonUniformGrid() {
        // Non-uniform 7-point grid (odd → no trapezoid tail).
        final double[] xs = {0.0, 0.10, 0.25, 0.45, 0.62, 0.85, 1.0};
        final int n = xs.length;
        final Array x = new Array(n);
        for (int i = 0; i < n; ++i) {
            x.set(i, xs[i]);
        }
        final Array f = new Array(n);
        for (int i = 0; i < n; ++i) {
            final double xi = x.get(i);
            f.set(i, 3.0 * xi * xi - xi + 1.0); // 3x^2 - x + 1
        }
        final double sum = new DiscreteSimpsonIntegral().op(x, f);
        // \int_0^1 (3x^2 - x + 1) dx = 1 - 1/2 + 1 = 1.5
        assertEquals(1.5, sum, TIGHT);
    }

    /** Even n: trapezoid tail closes the unpaired last interval. Tier: LOOSE. */
    @Test
    public void evenLengthGridUsesTrapezoidTail() {
        // 6 nodes (even) → 2 Simpson pairs (covers idx 0..4) + trapezoid (4..5).
        final int n = 6;
        final Array x = uniformGrid(0.0, 1.0, n);
        final Array f = new Array(n);
        for (int i = 0; i < n; ++i) {
            f.set(i, x.get(i) * x.get(i)); // x^2 → degree 2 → Simpson exact on pairs
        }
        // The composite formula is exact for degree-2 on Simpson pairs, but
        // the trapezoidal tail introduces error proportional to the
        // curvature on the last interval. Loose tier.
        final double sum = new DiscreteSimpsonIntegral().op(x, f);
        // Reference: integrate same quadrature by hand —
        //   pair-1 (idx 0,1,2): h=0.2, f=(0, 0.04, 0.16)  → 0.2/3 * (0 + 0.16 + 0.16)
        //                       = 0.06666666666666667 * 0.32 ≈ 0.0213333...
        //   pair-2 (idx 2,3,4): h=0.2, f=(0.16, 0.36, 0.64) → 0.2/3 * (0.16 + 1.44 + 0.64)
        //                       = 0.06666... * 2.24 ≈ 0.1493333...
        //   trapezoid (4,5):    0.5*0.2*(0.64+1.0) = 0.164
        // Sum ≈ 0.3346666...
        // True integral = 1/3 ≈ 0.3333..., so error ~ 1.3e-3.
        assertEquals(0.33466666666666667, sum, TIGHT);
    }

    // --- helpers ---

    private static Array uniformGrid(final double a, final double b, final int n) {
        final Array x = new Array(n);
        final double h = (b - a) / (n - 1);
        for (int i = 0; i < n; ++i) {
            x.set(i, a + i * h);
        }
        return x;
    }

    private static Array constArray(final int n, final double v) {
        final Array a = new Array(n);
        for (int i = 0; i < n; ++i) {
            a.set(i, v);
        }
        return a;
    }
}
