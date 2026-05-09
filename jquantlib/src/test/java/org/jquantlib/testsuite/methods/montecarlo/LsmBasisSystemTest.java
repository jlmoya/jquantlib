/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of the {@code LsmBasisSystem} port against C++ QuantLib
 v1.42.1 reference values produced by the {@code lsm_basis_system_probe}.
 See Phase 5h.5-MC.
 */
package org.jquantlib.testsuite.methods.montecarlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.methods.montecarlo.LsmBasisSystem.PolynomialType;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Verifies that {@link LsmBasisSystem#pathBasisSystem(int, PolynomialType)}
 * and {@link LsmBasisSystem#multiPathBasisSystem(int, int, PolynomialType)}
 * produce values matching C++ {@code QuantLib::LsmBasisSystem} for every
 * supported polynomial type and order/dimension combination probed by
 * {@code lsm_basis_system_probe.cpp}.
 *
 * <p>Tier: TIGHT for the orthogonal-polynomial families (math.distributions
 * GammaFunction + JQuantMath.exp/pow contribute &lt;1e-14 abs drift). Monomial
 * uses iterative product so it is bit-exact at scales tested.
 */
public class LsmBasisSystemTest {

    private static final String GROUP = "methods/montecarlo/lsm_basis_system";

    @Test
    public void pathBasisMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(GROUP);
        for (final PolynomialType type : PolynomialType.values()) {
            for (int order = 2; order <= 4; ++order) {
                final String caseName = "path_" + type.name() + "_order" + order;
                final Case c = reader.getCase(caseName);
                final JSONObject exp = (JSONObject) c.expectedRaw();
                final int expectedSize = exp.getInt("size");
                final JSONArray rows = exp.getJSONArray("rows");

                final List<Ops.DoubleOp> basis =
                        LsmBasisSystem.pathBasisSystem(order, type);
                assertEquals(caseName + ": basis size", expectedSize, basis.size());

                for (int r = 0; r < rows.length(); ++r) {
                    final JSONObject row = rows.getJSONObject(r);
                    final double x = row.getDouble("x");
                    final JSONArray expVals = row.getJSONArray("values");
                    for (int i = 0; i < basis.size(); ++i) {
                        final double got = basis.get(i).op(x);
                        final double want = expVals.getDouble(i);
                        if (!Tolerance.tight(got, want)) {
                            fail(caseName + ": x=" + x + " i=" + i
                                    + " got=" + got + " want=" + want
                                    + " absdiff=" + Math.abs(got - want));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void multiPathBasisMonomialDim2Order2() {
        verifyMultiCase("multi_Monomial_dim2_order2", PolynomialType.Monomial, 2, 2);
    }

    @Test
    public void multiPathBasisHermiteDim2Order2() {
        verifyMultiCase("multi_Hermite_dim2_order2", PolynomialType.Hermite, 2, 2);
    }

    @Test
    public void multiPathBasisMonomialDim3Order2() {
        verifyMultiCase("multi_Monomial_dim3_order2", PolynomialType.Monomial, 3, 2);
    }

    @Test
    public void zeroDimensionThrows() {
        try {
            LsmBasisSystem.multiPathBasisSystem(0, 2, PolynomialType.Monomial);
            fail("expected exception for dim=0");
        } catch (final RuntimeException expected) {
            // ok
        }
    }

    private void verifyMultiCase(final String caseName, final PolynomialType type,
                                 final int dim, final int order) {
        final ReferenceReader reader = ReferenceReader.load(GROUP);
        final Case c = reader.getCase(caseName);
        final JSONObject exp = (JSONObject) c.expectedRaw();
        final int expectedSize = exp.getInt("size");
        final JSONArray rows = exp.getJSONArray("rows");

        final List<Ops.ObjectToDouble<Array>> basis =
                LsmBasisSystem.multiPathBasisSystem(dim, order, type);
        assertEquals(caseName + ": basis size", expectedSize, basis.size());

        for (int r = 0; r < rows.length(); ++r) {
            final JSONObject row = rows.getJSONObject(r);
            final JSONArray xs = row.getJSONArray("x");
            final double[] arr = new double[xs.length()];
            for (int j = 0; j < xs.length(); ++j) arr[j] = xs.getDouble(j);
            final Array a = new Array(arr);
            final JSONArray expVals = row.getJSONArray("values");
            for (int i = 0; i < basis.size(); ++i) {
                final double got = basis.get(i).op(a);
                final double want = expVals.getDouble(i);
                if (!Tolerance.tight(got, want)) {
                    fail(caseName + ": row=" + r + " i=" + i
                            + " got=" + got + " want=" + want
                            + " absdiff=" + Math.abs(got - want));
                }
            }
        }
    }
}
