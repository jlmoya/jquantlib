/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of the GaussLaguerreIntegration(128) port against
 C++ QuantLib v1.42.1 reference values produced by the
 gauss_laguerre_integration_probe. See phase2f-design §C.2 (Phase 2f WI-3).
 */
package org.jquantlib.testsuite.math.integrals;

import static org.junit.Assert.fail;

import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLaguerreIntegration;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GaussLaguerreIntegrationTest {

    /**
     * Embedded n=128 nodes/weights must match C++ bit-exactly — the
     * tables are not derived in Java, they are copied verbatim from
     * the C++ probe. Bit-flip here means the table copy was tampered
     * with or got out of sync with the pinned C++ submodule.
     */
    @Test
    public void nodesWeightsMatchCppExact() {
        final ReferenceReader reader = ReferenceReader.load("math/integrals/gauss_laguerre_integration");
        final Case c = reader.getCase("nodes_weights_n128");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray nodes = exp.getJSONArray("nodes");
        final JSONArray weights = exp.getJSONArray("weights");
        final GaussLaguerreIntegration q = new GaussLaguerreIntegration(128);
        for (int i = 0; i < q.order(); i++) {
            if (!Tolerance.exact(q.x(i), nodes.getDouble(i))) {
                fail("node[" + i + "] not bit-exact: java=" + q.x(i)
                        + " cpp=" + nodes.getDouble(i));
            }
            if (!Tolerance.exact(q.weight(i), weights.getDouble(i))) {
                fail("weight[" + i + "] not bit-exact: java=" + q.weight(i)
                        + " cpp=" + weights.getDouble(i));
            }
        }
    }

    /**
     * Reference integrals at n=128 must match C++ bit-exactly when the
     * integrand is computed with arithmetic identical to C++ (constants,
     * polynomials). For transcendental integrands (cos) the result was
     * previously subject to Math.cos vs std::cos 1-ULP drift (A13).
     * Phase 2i.5 WI-3: cos integrand now uses JQuantMath.cos (correctly-
     * rounded against CORE-MATH cr_cos); tier stays TIGHT, which is
     * sufficient whether or not the cos swap eliminates all residual
     * (Gauss quadrature node accumulation may still contribute).
     */
    @Test
    public void referenceIntegralsMatchCpp() {
        final ReferenceReader reader = ReferenceReader.load("math/integrals/gauss_laguerre_integration");
        final Case c = reader.getCase("reference_integrals_n128");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray integrals = exp.getJSONArray("integrals");
        final GaussLaguerreIntegration q = new GaussLaguerreIntegration(128);
        for (int i = 0; i < integrals.length(); i++) {
            final JSONObject item = integrals.getJSONObject(i);
            final String name = item.getString("name");
            final double expected = item.getDouble("value");
            final double got;
            switch (name) {
                case "const_one":  got = q.op(constOne()); break;
                case "x":          got = q.op(identity());  break;
                case "x_squared":  got = q.op(squared());   break;
                case "cos":        got = q.op(cos());       break;
                default: throw new IllegalArgumentException("unknown integrand " + name);
            }
            if (!Tolerance.tight(got, expected)) {
                fail(name + ": expected=" + expected + " got=" + got
                        + " diff=" + Math.abs(got - expected));
            }
        }
    }

    /**
     * Phase 5h.5-Integration: arbitrary orders (144, 160) derived via the
     * Golub-Welsch algorithm in {@link org.jquantlib.math.integrals.GaussianQuadrature}
     * + {@link org.jquantlib.math.integrals.GaussLaguerrePolynomial} must
     * reproduce the analytic moments {@code ∫₀^∞ x^k e^{-x} dx = k!}.
     *
     * <p>Note: the C++ {@code GaussianQuadrature::operator()} returns the
     * raw {@code Σ wᵢ f(xᵢ)} where the {@code exp(-x)} is implicitly
     * absorbed in the weights ({@code wᵢ = μ₀ v[0][i]² / w(xᵢ)} with
     * {@code w(x) = exp(-x)} for Laguerre); to recover
     * {@code ∫₀^∞ x^k exp(-x) dx} we feed the integrand
     * {@code f(x) = x^k * exp(-x)} explicitly.
     */
    @Test
    public void derivedOrders144And160ReproduceFactorialMoments() {
        for (int n : new int[] { 144, 160 }) {
            final GaussLaguerreIntegration q = new GaussLaguerreIntegration(n);
            // ∫₀^∞ 1 * e^{-x} dx = 1 = 0!
            final double m0 = q.op(weighted(0));
            // ∫₀^∞ x * e^{-x} dx = 1 = 1!
            final double m1 = q.op(weighted(1));
            // ∫₀^∞ x² * e^{-x} dx = 2 = 2!
            final double m2 = q.op(weighted(2));
            if (Math.abs(m0 - 1.0) > 1e-9) {
                fail("n=" + n + " moment 0: expected 1.0 got " + m0);
            }
            if (Math.abs(m1 - 1.0) > 1e-9) {
                fail("n=" + n + " moment 1: expected 1.0 got " + m1);
            }
            if (Math.abs(m2 - 2.0) > 1e-9) {
                fail("n=" + n + " moment 2: expected 2.0 got " + m2);
            }
        }
    }

    /** {@code f(x) = x^k * exp(-x)} for analytic moment tests. */
    private static Ops.DoubleOp weighted(final int k) {
        return new Ops.DoubleOp() { public double op(double x) {
            return Math.pow(x, k) * Math.exp(-x);
        } };
    }

    /**
     * Phase 5h.5-Integration: order > 192 must be rejected per the C++
     * {@code QL_REQUIRE(intOrder <= 192, ...)} check in
     * {@code AnalyticHestonEngine::Integration::gaussLaguerre}.
     */
    @Test
    public void orderGreaterThan192Rejected() {
        try {
            new GaussLaguerreIntegration(193);
            fail("expected order > 192 to throw");
        } catch (final RuntimeException expected) {
            // ok
        }
    }

    private static Ops.DoubleOp constOne() { return new Ops.DoubleOp() { public double op(double x){ return 1.0; } }; }
    private static Ops.DoubleOp identity() { return new Ops.DoubleOp() { public double op(double x){ return x; } }; }
    private static Ops.DoubleOp squared()  { return new Ops.DoubleOp() { public double op(double x){ return x * x; } }; }
    // Phase 2i.5 WI-3: JQuantMath.cos (CORE-MATH cr_cos, correctly-rounded).
    private static Ops.DoubleOp cos()      { return new Ops.DoubleOp() { public double op(double x){ return JQuantMath.cos(x); } }; }
}
