/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Smoke test for the Phase 4a.5 A.5.1 port of GaussLegendreIntegration
 (and its dependency GaussJacobiPolynomial) against textbook analytical
 values. Coverage:
   1. Polynomial exactness — Gauss-Legendre with n nodes integrates
      polynomials of degree <= 2n-1 exactly on [-1, 1].
   2. Smooth analytical integrands (cos, exp) at modest order n=16.
 */
package org.jquantlib.testsuite.math.integrals;

import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Phase 4a.5 A.5.1 — GaussLegendreIntegration smoke tests.
 *
 * <p>Verifies the runtime Golub-Welsch eigendecomposition path through
 * {@link org.jquantlib.math.integrals.GaussJacobiPolynomial} (alpha=beta=0)
 * computes correct nodes/weights for the Gauss-Legendre quadrature on
 * {@code [-1, 1]} with {@code w(x)=1}.
 *
 * <p>Tolerance tier per phase1-design §4.2 is LOOSE (1e-8) for numerical
 * integration. For polynomial exactness we tighten to 1e-12 since the rule
 * should be exact up to round-off.
 */
public class GaussLegendreIntegrationTest {

    @Test
    public void polynomialExactness() {
        // Order n exactly integrates polynomials of degree <= 2n-1.
        // Test x^k for k = 0..7 with n = 4 (exact through deg 7).
        final GaussLegendreIntegration q = new GaussLegendreIntegration(4);
        // ∫_{-1}^{1} 1 dx = 2
        assertEquals("∫1 dx", 2.0, q.op(constOne()), 1.0e-12);
        // ∫_{-1}^{1} x dx = 0
        assertEquals("∫x dx", 0.0, q.op(identity()), 1.0e-12);
        // ∫_{-1}^{1} x^2 dx = 2/3
        assertEquals("∫x^2 dx", 2.0 / 3.0, q.op(power(2)), 1.0e-12);
        // ∫_{-1}^{1} x^3 dx = 0
        assertEquals("∫x^3 dx", 0.0, q.op(power(3)), 1.0e-12);
        // ∫_{-1}^{1} x^4 dx = 2/5
        assertEquals("∫x^4 dx", 2.0 / 5.0, q.op(power(4)), 1.0e-12);
        // ∫_{-1}^{1} x^5 dx = 0
        assertEquals("∫x^5 dx", 0.0, q.op(power(5)), 1.0e-12);
        // ∫_{-1}^{1} x^6 dx = 2/7
        assertEquals("∫x^6 dx", 2.0 / 7.0, q.op(power(6)), 1.0e-12);
        // ∫_{-1}^{1} x^7 dx = 0
        assertEquals("∫x^7 dx", 0.0, q.op(power(7)), 1.0e-12);
    }

    @Test
    public void cosineIntegral() {
        // ∫_{-1}^{1} cos(x) dx = 2 sin(1)
        final GaussLegendreIntegration q = new GaussLegendreIntegration(16);
        final double expected = 2.0 * Math.sin(1.0);
        final double got = q.op(new Ops.DoubleOp() {
            @Override public double op(final double x) { return Math.cos(x); }
        });
        assertEquals("∫cos(x) dx, [-1,1]", expected, got, 1.0e-12);
    }

    @Test
    public void exponentialIntegral() {
        // ∫_{-1}^{1} exp(x) dx = e - 1/e
        final GaussLegendreIntegration q = new GaussLegendreIntegration(16);
        final double expected = Math.exp(1.0) - Math.exp(-1.0);
        final double got = q.op(new Ops.DoubleOp() {
            @Override public double op(final double x) { return Math.exp(x); }
        });
        assertEquals("∫exp(x) dx, [-1,1]", expected, got, 1.0e-12);
    }

    @Test
    public void higherOrderConvergence() {
        // Verify that higher orders give same or better accuracy.
        // ∫_{-1}^{1} 1/(1+x^2) dx = 2 arctan(1) = pi/2
        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override public double op(final double x) { return 1.0 / (1.0 + x * x); }
        };
        final double expected = Math.PI / 2.0;
        final double v8  = new GaussLegendreIntegration(8).op(f);
        final double v32 = new GaussLegendreIntegration(32).op(f);
        // 1/(1+x^2) has poles at +/-i so convergence is slow at low order.
        // n=32 should be high-precision; n=8 just shows reasonable progress.
        assertTrue("n=8 reasonable accuracy",  Math.abs(v8  - expected) < 1.0e-3);
        assertTrue("n=32 high accuracy",       Math.abs(v32 - expected) < 1.0e-12);
        // And n=32 must be strictly better than n=8.
        assertTrue("convergence: n=32 better than n=8",
                   Math.abs(v32 - expected) < Math.abs(v8 - expected));
    }

    @Test
    public void orderAndAccessors() {
        final GaussLegendreIntegration q = new GaussLegendreIntegration(8);
        assertEquals(8, q.order());
        // Symmetry: nodes should be symmetric around 0, weights symmetric too.
        // Sum of weights = 2 (∫_{-1}^{1} 1 dx).
        double wsum = 0.0;
        for (int i = 0; i < q.order(); i++) {
            wsum += q.weight(i);
        }
        assertEquals("Σ weights == 2", 2.0, wsum, 1.0e-13);
    }

    // --- helpers ---
    private static Ops.DoubleOp constOne() {
        return new Ops.DoubleOp() { @Override public double op(final double x) { return 1.0; } };
    }
    private static Ops.DoubleOp identity() {
        return new Ops.DoubleOp() { @Override public double op(final double x) { return x; } };
    }
    private static Ops.DoubleOp power(final int k) {
        return new Ops.DoubleOp() {
            @Override
            public double op(final double x) {
                double v = 1.0;
                for (int i = 0; i < k; i++) v *= x;
                return v;
            }
        };
    }
}
