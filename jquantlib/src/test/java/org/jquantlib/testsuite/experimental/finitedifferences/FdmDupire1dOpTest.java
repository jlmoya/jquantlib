/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4n — FdmDupire1dOp smoke tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */
package org.jquantlib.testsuite.experimental.finitedifferences;

import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.finitedifferences.FdmDupire1dOp;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Smoke tests for {@link FdmDupire1dOp}.
 */
public class FdmDupire1dOpTest {

    private static final double TIGHT = 1e-12;
    private static final double LOOSE = 1e-8;

    public FdmDupire1dOpTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static FdmMesherComposite buildMesher(final int n) {
        final Uniform1dMesher m = new Uniform1dMesher(0.0, 1.0, n);
        return new FdmMesherComposite(Collections.<Fdm1dMesher>singletonList(m));
    }

    @Test
    public void sizeAndSetTimeNoOp() {
        final FdmMesherComposite mc = buildMesher(5);
        final Array localVol = new Array(5).fill(0.2);
        final FdmDupire1dOp op = new FdmDupire1dOp(mc, localVol);
        assertEquals(1, op.size());
        op.setTime(0.0, 1.0); // no-op, just verify it doesn't throw
    }

    @Test
    public void applyAndApplyDirectionAgree() {
        // Ensure apply(r) and applyDirection(0, r) match.
        final FdmMesherComposite mc = buildMesher(7);
        final Array localVol = new Array(7).fill(0.2);
        final FdmDupire1dOp op = new FdmDupire1dOp(mc, localVol);
        final Array r = new Array(new double[] {1, 2, 3, 4, 5, 6, 7});
        final Array a = op.apply(r);
        final Array b = op.applyDirection(0, r);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); ++i) {
            assertEquals(a.get(i), b.get(i), TIGHT);
        }
    }

    @Test
    public void applyMixedReturnsInputUnchanged() {
        final FdmMesherComposite mc = buildMesher(5);
        final Array localVol = new Array(5).fill(0.3);
        final FdmDupire1dOp op = new FdmDupire1dOp(mc, localVol);
        final Array r = new Array(new double[] {1, 2, 3, 4, 5});
        final Array m = op.applyMixed(r);
        for (int i = 0; i < r.size(); ++i) {
            assertEquals(r.get(i), m.get(i), TIGHT);
        }
    }

    @Test
    public void preconditionerEqualsSolveSplittingDir0() {
        final FdmMesherComposite mc = buildMesher(5);
        final Array localVol = new Array(5).fill(0.25);
        final FdmDupire1dOp op = new FdmDupire1dOp(mc, localVol);
        final Array r = new Array(new double[] {1, 1, 1, 1, 1});
        final double dt = 0.01;
        final Array p = op.preconditioner(r, dt);
        final Array s = op.solveSplitting(0, r, dt);
        for (int i = 0; i < r.size(); ++i) {
            assertEquals(p.get(i), s.get(i), TIGHT);
        }
    }

    @Test
    public void toMatrixDecompReturnsSingleMatrix() {
        final FdmMesherComposite mc = buildMesher(4);
        final Array localVol = new Array(4).fill(0.2);
        final FdmDupire1dOp op = new FdmDupire1dOp(mc, localVol);
        final List<Matrix> decomp = op.toMatrixDecomp();
        assertEquals(1, decomp.size());
        final Matrix m = decomp.get(0);
        assertNotNull(m);
        assertEquals(4, m.rows());
        assertEquals(4, m.columns());
    }
}
