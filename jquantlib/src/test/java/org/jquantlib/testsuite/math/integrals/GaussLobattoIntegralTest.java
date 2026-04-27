/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of the GaussLobattoIntegral port against C++ QuantLib
 v1.42.1 reference values produced by the gauss_lobatto_integral_probe.
 See phase2f-design §C.3 (Phase 2f WI-3).
 */
package org.jquantlib.testsuite.math.integrals;

import static org.junit.Assert.fail;

import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GaussLobattoIntegralTest {

    @Test
    public void integralsMatchCpp() {
        final ReferenceReader reader =
                ReferenceReader.load("math/integrals/gauss_lobatto_integral");
        final Case c = reader.getCase("reference_integrals");
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray integrals = exp.getJSONArray("integrals");

        // Tight instance reproduces the probe's "tight" GaussLobattoIntegral
        // (maxIters=100000, absAcc=1e-12). The Heston-style instance below
        // matches the C++ default ctor used in HestonProcess BroadieKaya:
        //   GaussLobattoIntegral(Null<Size>(), 1e-4)
        // mapped here to (Integer.MAX_VALUE, 1e-4).
        final GaussLobattoIntegral tight = new GaussLobattoIntegral(100000, 1e-12);
        final GaussLobattoIntegral hestonStyle =
                new GaussLobattoIntegral(Integer.MAX_VALUE, 1e-4);

        for (int i = 0; i < integrals.length(); i++) {
            final JSONObject item = integrals.getJSONObject(i);
            final String name = item.getString("name");
            final double expected = item.getDouble("value");
            final double got;
            switch (name) {
                case "poly_0_2":          got = tight.op(poly(),       0.0,  2.0); break;
                case "sin_0_pi":          got = tight.op(sin(),        0.0,  Math.PI); break;
                case "exp_neg_xsq_-2_2":  got = tight.op(expNegXsq(), -2.0,  2.0); break;
                case "runge_-1_1":        got = tight.op(runge(),     -1.0,  1.0); break;
                case "sqrt_0_1":          got = tight.op(sqrtFn(),     0.0,  1.0); break;
                case "heston_runge_eps1e-4":
                    got = hestonStyle.op(runge(), -1.0, 1.0); break;
                default: throw new IllegalArgumentException(name);
            }
            // Adaptive recursion shares the same branching logic between
            // C++ and Java provided the dist == acc test is deterministic
            // (it is — Java disallows wider intermediate precision per
            // strictfp semantics). Smooth-integrand integrals usually
            // come back bit-exact, but transcendental integrands drift
            // a few ULPs through Math.{exp,sin,cos} (A13 phenomenon).
            // Tight tier is sufficient.
            if (!Tolerance.tight(got, expected)) {
                fail(name + ": expected=" + expected + " got=" + got
                        + " diff=" + Math.abs(got - expected));
            }
        }
    }

    /**
     * Sanity smoke test: the "convergence estimate disabled" branch of
     * calculateAbsTolerance is only exercised when the
     * useConvergenceEstimate flag is false. Verify the constructor
     * variant reaches that branch and still produces a finite value.
     */
    @Test
    public void convergenceEstimateDisabled() {
        final GaussLobattoIntegral g = new GaussLobattoIntegral(
                100000, 1e-10, Constants.NULL_REAL, /*useConvergenceEstimate=*/false);
        final double v = g.op(sin(), 0.0, Math.PI);
        if (Math.abs(v - 2.0) > 1e-6) {
            fail("sin integral with disabled convergence estimate: got=" + v);
        }
    }

    private static Ops.DoubleOp poly()      { return new Ops.DoubleOp() { public double op(double x){ return x*x*x - 2.0*x*x + x + 1.0; } }; }
    private static Ops.DoubleOp sin()       { return new Ops.DoubleOp() { public double op(double x){ return Math.sin(x); } }; }
    private static Ops.DoubleOp expNegXsq() { return new Ops.DoubleOp() { public double op(double x){ return Math.exp(-x*x); } }; }
    private static Ops.DoubleOp runge()     { return new Ops.DoubleOp() { public double op(double x){ return 1.0 / (1.0 + 25.0 * x * x); } }; }
    private static Ops.DoubleOp sqrtFn()    { return new Ops.DoubleOp() { public double op(double x){ return Math.sqrt(x); } }; }
}
