/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-SLV — FdmSquareRootFwdOp cross-validation tests.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences.operators;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation of {@link FdmSquareRootFwdOp} against C++ v1.42.1
 * via {@code migration-harness/references/heston-slv/fdm_square_root_fwd_op.json}.
 *
 * <p>Tier: TIGHT (1e-9 abs / 1e-12 rel). The operator is purely analytic.
 */
public class FdmSquareRootFwdOpTest {

    private static final double TOL_ABS = 1e-9;
    private static final double TOL_REL = 1e-12;

    private static FdmMesher mesher(final double vMin, final double vMax, final int n) {
        final List<Fdm1dMesher> ms = new ArrayList<Fdm1dMesher>(1);
        ms.add(new Uniform1dMesher(vMin, vMax, n));
        return new FdmMesherComposite(ms);
    }

    private static void assertClose(final String name, final double exp, final double act) {
        final double diff = Math.abs(exp - act);
        if (diff <= TOL_ABS) return;
        final double rel = diff / Math.max(Math.abs(exp), 1e-300);
        assertEquals(name + " (rel=" + rel + ")", exp, act, Math.max(TOL_ABS, TOL_REL * Math.abs(exp)));
    }

    private void runFamily(final ReferenceReader rr,
                           final TransformationType type,
                           final String tag) {
        // 1) boundary factors
        {
            final ReferenceReader.Case c = rr.getCase(tag + "_boundary_factors");
            final JSONObject in = c.inputs();
            final double kappa = in.getDouble("kappa");
            final double theta = in.getDouble("theta");
            final double sigma = in.getDouble("sigma");
            final double vMin  = in.getDouble("vMin");
            final double vMax  = in.getDouble("vMax");
            final int n        = in.getInt("n");
            final FdmMesher m = mesher(vMin, vMax, n);
            final FdmSquareRootFwdOp op = new FdmSquareRootFwdOp(m, kappa, theta, sigma, 0, type);

            final JSONObject exp = (JSONObject) c.expectedRaw();
            assertClose(tag + ".lowerBoundaryFactor",
                    exp.getDouble("lowerBoundaryFactor"),
                    op.lowerBoundaryFactor(type));
            assertClose(tag + ".upperBoundaryFactor",
                    exp.getDouble("upperBoundaryFactor"),
                    op.upperBoundaryFactor(type));
        }

        // 2) apply on three input vectors
        {
            final ReferenceReader.Case c = rr.getCase(tag + "_apply");
            final JSONObject in = c.inputs();
            final double kappa = in.getDouble("kappa");
            final double theta = in.getDouble("theta");
            final double sigma = in.getDouble("sigma");
            final double vMin  = in.getDouble("vMin");
            final double vMax  = in.getDouble("vMax");
            final int n        = in.getInt("n");
            final FdmMesher m = mesher(vMin, vMax, n);
            final FdmSquareRootFwdOp op = new FdmSquareRootFwdOp(m, kappa, theta, sigma, 0, type);
            final Array vLoc = m.locations(0);

            final Array p1 = new Array(n).fill(1.0);
            final Array p2 = new Array(n);
            final Array p3 = new Array(n);
            final double mid = 0.5 * (vMin + vMax);
            for (int i = 0; i < n; ++i) {
                final double v = vLoc.get(i);
                p2.set(i, v);
                p3.set(i, Math.exp(-2.0 * (v - mid) * (v - mid)));
            }
            final Array r1 = op.apply(p1);
            final Array r2 = op.apply(p2);
            final Array r3 = op.apply(p3);

            final JSONObject exp = (JSONObject) c.expectedRaw();
            assertArrayClose(tag + ".apply_constant", exp.getJSONArray("apply_constant"), r1);
            assertArrayClose(tag + ".apply_linear",   exp.getJSONArray("apply_linear"),   r2);
            assertArrayClose(tag + ".apply_gauss",    exp.getJSONArray("apply_gauss"),    r3);
        }

        // 3) v ghost values
        {
            final ReferenceReader.Case c = rr.getCase(tag + "_v_ghost");
            final JSONObject in = c.inputs();
            final double kappa = in.getDouble("kappa");
            final double theta = in.getDouble("theta");
            final double sigma = in.getDouble("sigma");
            final double vMin  = in.getDouble("vMin");
            final double vMax  = in.getDouble("vMax");
            final int n        = in.getInt("n");
            final FdmMesher m = mesher(vMin, vMax, n);
            final FdmSquareRootFwdOp op = new FdmSquareRootFwdOp(m, kappa, theta, sigma, 0, type);

            final JSONObject exp = (JSONObject) c.expectedRaw();
            assertClose(tag + ".v0",   exp.getDouble("v0"),   op.v(0));
            assertClose(tag + ".vN",   exp.getDouble("vN"),   op.v(n));
            assertClose(tag + ".vNp1", exp.getDouble("vNp1"), op.v(n + 1));
        }
    }

    private static void assertArrayClose(final String name, final JSONArray exp, final Array act) {
        assertEquals(name + ".size", exp.length(), act.size());
        for (int i = 0; i < exp.length(); ++i) {
            final double e = exp.getDouble(i);
            final double a = act.get(i);
            final double diff = Math.abs(e - a);
            if (diff <= TOL_ABS) continue;
            final double rel = diff / Math.max(Math.abs(e), 1e-300);
            assertEquals(name + "[" + i + "] (rel=" + rel + ")",
                    e, a, Math.max(TOL_ABS, TOL_REL * Math.abs(e)));
        }
    }

    @Test
    public void testPlainTransformation() {
        final ReferenceReader rr = ReferenceReader.load("heston-slv/fdm_square_root_fwd_op");
        runFamily(rr, TransformationType.Plain, "plain");
    }

    @Test
    public void testPowerTransformation() {
        final ReferenceReader rr = ReferenceReader.load("heston-slv/fdm_square_root_fwd_op");
        runFamily(rr, TransformationType.Power, "power");
    }

    @Test
    public void testLogTransformation() {
        final ReferenceReader rr = ReferenceReader.load("heston-slv/fdm_square_root_fwd_op");
        runFamily(rr, TransformationType.Log, "log");
    }
}
