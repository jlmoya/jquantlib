/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences.utilities;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.UniformGridMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;
import org.jquantlib.methods.finitedifferences.utilities.BoundaryCondition;
import org.jquantlib.methods.finitedifferences.utilities.FdmDirichletBoundary;
import org.jquantlib.methods.finitedifferences.utilities.FdmIndicesOnBoundary;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Cross-validation tests for the gap-fdm port of {@link FdmIndicesOnBoundary}, {@link UniformGridMesher} and
 * {@link FdmDirichletBoundary} against C++ QuantLib v1.42.1.
 *
 * <p>Reference data:
 * {@code migration-harness/references/methods/finitedifferences/utilities/fdm_gap_boundary.json}.
 *
 * <p>All values are deterministic integer index sets / exact grid coordinates / boundary-value applications, so the
 * tier is EXACT.
 *
 * @author JQuantLib gap-fdm port
 */
public class FdmGapBoundaryTest {

    private static final double EXACT = 0.0;

    private static final String GROUP = "methods/finitedifferences/utilities/fdm_gap_boundary";

    public FdmGapBoundaryTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static FdmLinearOpLayout layout4x3() {
        return new FdmLinearOpLayout(new int[] { 4, 3 });
    }

    private static UniformGridMesher mesher4x3(final FdmLinearOpLayout layout) {
        final List< Pair< Double, Double > > bounds = new ArrayList<>();
        bounds.add(new Pair<>(0.0, 3.0));
        bounds.add(new Pair<>(10.0, 12.0));
        return new UniformGridMesher(layout, bounds);
    }

    private static int[] toIntArray(final JSONArray a) {
        final int[] r = new int[a.length()];
        for ( int i = 0; i < r.length; ++i ) {
            r[i] = a.getInt(i);
        }
        return r;
    }

    @Test
    public void testIndicesOnBoundary() {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final FdmLinearOpLayout layout = layout4x3();

        for ( final String name : ref.caseNames() ) {
            if (!name.startsWith("indices_")) {
                continue;
            }
            final ReferenceReader.Case rc = ref.getCase(name);
            final JSONObject in = rc.inputs();
            final int direction = in.getInt("direction");
            final BoundaryCondition.Side side = BoundaryCondition.Side.valueOf(in.getString("side"));

            final int[] expected = toIntArray(((JSONObject) rc.expectedRaw()).getJSONArray("indices"));
            final int[] actual = new FdmIndicesOnBoundary(layout, direction, side).getIndices();

            assertEquals(name + " length", expected.length, actual.length);
            for ( int i = 0; i < expected.length; ++i ) {
                assertEquals(name + " idx[" + i + "]", expected[i], actual[i]);
            }
        }
    }

    @Test
    public void testUniformGridMesher() {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final FdmLinearOpLayout layout = layout4x3();
        final UniformGridMesher mesher = mesher4x3(layout);

        final ReferenceReader.Case rc = ref.getCase("uniform_grid_4x3");
        final JSONObject exp = (JSONObject) rc.expectedRaw();

        // locations(0), locations(1)
        final JSONArray loc0 = exp.getJSONArray("locations0");
        final JSONArray loc1 = exp.getJSONArray("locations1");
        final Array a0 = mesher.locations(0);
        final Array a1 = mesher.locations(1);
        assertEquals("locations0 size", loc0.length(), a0.size());
        assertEquals("locations1 size", loc1.length(), a1.size());
        for ( int i = 0; i < loc0.length(); ++i ) {
            assertEquals("locations0[" + i + "]", loc0.getDouble(i), a0.get(i), EXACT);
            assertEquals("locations1[" + i + "]", loc1.getDouble(i), a1.get(i), EXACT);
        }

        // per-cell location + constant dx via dplus/dminus
        final JSONArray cell0 = exp.getJSONArray("cellLoc0");
        final JSONArray cell1 = exp.getJSONArray("cellLoc1");
        final double dx0 = exp.getDouble("dx0");
        final double dx1 = exp.getDouble("dx1");
        int idx = 0;
        for ( final FdmLinearOpIterator iter : layout ) {
            assertEquals("cellLoc0[" + idx + "]", cell0.getDouble(idx), mesher.location(iter, 0), EXACT);
            assertEquals("cellLoc1[" + idx + "]", cell1.getDouble(idx), mesher.location(iter, 1), EXACT);
            assertEquals("dplus0", dx0, mesher.dplus(iter, 0), EXACT);
            assertEquals("dminus0", dx0, mesher.dminus(iter, 0), EXACT);
            assertEquals("dplus1", dx1, mesher.dplus(iter, 1), EXACT);
            assertEquals("dminus1", dx1, mesher.dminus(iter, 1), EXACT);
            ++idx;
        }
        assertEquals("layout size", cell0.length(), idx);
    }

    @Test
    public void testDirichletDir0Upper() {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final FdmLinearOpLayout layout = layout4x3();
        final UniformGridMesher mesher = mesher4x3(layout);

        final ReferenceReader.Case rc = ref.getCase("dirichlet_dir0_upper_v99");
        final JSONObject exp = (JSONObject) rc.expectedRaw();

        final FdmDirichletBoundary bc =
                new FdmDirichletBoundary(mesher, 99.0, 0, BoundaryCondition.Side.Upper);

        final int n = layout.size();
        final Array a = new Array(n);
        for ( int i = 0; i < n; ++i ) {
            a.set(i, i);
        }
        bc.applyAfterApplying(a);
        final JSONArray expApply = exp.getJSONArray("afterApplying_ramp_i");
        for ( int i = 0; i < n; ++i ) {
            assertEquals("afterApplying[" + i + "]", expApply.getDouble(i), a.get(i), EXACT);
        }

        final Array b = new Array(n);
        for ( int i = 0; i < n; ++i ) {
            b.set(i, i * 10.0);
        }
        bc.applyAfterSolving(b);
        final JSONArray expSolve = exp.getJSONArray("afterSolving_ramp_10i");
        for ( int i = 0; i < n; ++i ) {
            assertEquals("afterSolving[" + i + "]", expSolve.getDouble(i), b.get(i), EXACT);
        }
    }

    @Test
    public void testDirichletDir1LowerAndScalar() {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final FdmLinearOpLayout layout = layout4x3();
        final UniformGridMesher mesher = mesher4x3(layout);

        final ReferenceReader.Case rc = ref.getCase("dirichlet_dir1_lower_vneg5");
        final JSONObject exp = (JSONObject) rc.expectedRaw();
        final JSONObject in = rc.inputs();

        final FdmDirichletBoundary bc =
                new FdmDirichletBoundary(mesher, -5.0, 1, BoundaryCondition.Side.Lower);

        final int n = layout.size();
        final Array a = new Array(n);
        for ( int i = 0; i < n; ++i ) {
            a.set(i, i);
        }
        bc.applyAfterApplying(a);
        final JSONArray expApply = exp.getJSONArray("afterApplying_ramp_i");
        for ( int i = 0; i < n; ++i ) {
            assertEquals("afterApplying[" + i + "]", expApply.getDouble(i), a.get(i), EXACT);
        }

        // scalar overload
        final JSONArray xs = in.getJSONArray("scalar_x");
        final JSONArray vs = in.getJSONArray("scalar_value");
        final JSONArray expScalar = exp.getJSONArray("scalar_applyAfterApplying");
        for ( int i = 0; i < xs.length(); ++i ) {
            final double got = bc.applyAfterApplying(xs.getDouble(i), vs.getDouble(i));
            assertEquals("scalar[" + i + "]", expScalar.getDouble(i), got, EXACT);
        }
    }
}
