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
     * polynomials). For transcendental integrands (cos) the result drifts
     * by a few ULPs because Math.cos vs std::cos differ by 1 ULP per
     * call (same A13 phenomenon as NCCS). Assert TIGHT tier across the
     * board to absorb the trig drift; polynomials still come through bit
     * exact in practice but TIGHT is plenty.
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

    private static Ops.DoubleOp constOne() { return new Ops.DoubleOp() { public double op(double x){ return 1.0; } }; }
    private static Ops.DoubleOp identity() { return new Ops.DoubleOp() { public double op(double x){ return x; } }; }
    private static Ops.DoubleOp squared()  { return new Ops.DoubleOp() { public double op(double x){ return x * x; } }; }
    private static Ops.DoubleOp cos()      { return new Ops.DoubleOp() { public double op(double x){ return Math.cos(x); } }; }
}
