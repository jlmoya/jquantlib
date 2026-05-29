/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.BoundaryCondition;
import org.jquantlib.methods.finitedifferences.TRBDF2;
import org.jquantlib.methods.finitedifferences.TridiagonalOperator;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Structural cross-validation for the gap-fdm port of the legacy {@link TRBDF2} template scheme against C++ QuantLib
 * v1.42.1.
 *
 * <p>Reference data:
 * {@code migration-harness/references/methods/finitedifferences/schemes/trbdf2_legacy.json}.
 *
 * <p>The legacy {@code TRBDF2<Operator>} has zero instantiations in v1.42.1 (dead upstream); it is ported for
 * legacy-family completeness. We instantiate it on a trivial time-constant {@link TridiagonalOperator} (a scaled
 * discrete second-difference, the heat-equation stencil) with an empty boundary-condition set and verify the two-stage
 * TR-BDF2 stepped vector matches C++ for the same operator / dt / initial vector.
 *
 * <p>Tier: TIGHT 1e-12 relative. Both ports run the identical {@code applyTo} / {@code solveFor} (Thomas-algorithm)
 * sequence, so the result is FP-identical up to accumulation order.
 *
 * @author JQuantLib gap-fdm port
 */
public class TRBDF2LegacyTest {

    private static final double TIGHT = 1.0e-12;

    private static final String GROUP = "methods/finitedifferences/schemes/trbdf2_legacy";

    public TRBDF2LegacyTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    /** Build the size-n scaled discrete second-difference operator (matches the probe's makeOp). */
    private static TridiagonalOperator makeOp(final int n, final double c) {
        final TridiagonalOperator L = new TridiagonalOperator(n);
        L.setFirstRow(-2.0 * c, 1.0 * c);
        L.setMidRows(1.0 * c, -2.0 * c, 1.0 * c);
        L.setLastRow(1.0 * c, -2.0 * c);
        return L;
    }

    private static Array ramp(final int n) {
        final Array a = new Array(n);
        for ( int i = 0; i < n; ++i ) {
            a.set(i, i + 1); // {1..n}
        }
        return a;
    }

    private static void assertStepped(final String name, final Array got) {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final ReferenceReader.Case rc = ref.getCase(name);
        final JSONArray expected = ((JSONObject) rc.expectedRaw()).getJSONArray("stepped");
        assertEquals(name + " size", expected.length(), got.size());
        for ( int i = 0; i < expected.length(); ++i ) {
            final double e = expected.getDouble(i);
            assertEquals(name + " stepped[" + i + "]", e, got.get(i),
                    Math.max(TIGHT, Math.abs(e) * TIGHT));
        }
    }

    @Test
    public void testSingleStep() {
        final int n = 5;
        final double c = 0.5;
        final double dt = 0.1;

        final TridiagonalOperator L = makeOp(n, c);
        final List< BoundaryCondition< TridiagonalOperator > > bcs = new ArrayList<>();

        final TRBDF2< TridiagonalOperator > scheme = new TRBDF2<>(L, bcs);
        scheme.setStep(dt);

        final Array a = scheme.step(ramp(n), 1.0);
        assertStepped("single_step_n5_c0.5_dt0.1", a);
    }

    @Test
    public void testThreeSteps() {
        final int n = 5;
        final double c = 0.5;
        final double dt = 0.1;

        final TridiagonalOperator L = makeOp(n, c);
        final List< BoundaryCondition< TridiagonalOperator > > bcs = new ArrayList<>();

        final TRBDF2< TridiagonalOperator > scheme = new TRBDF2<>(L, bcs);
        scheme.setStep(dt);

        Array a = ramp(n);
        double t = 1.0;
        for ( int s = 0; s < 3; ++s, t -= dt ) {
            a = scheme.step(a, t);
        }
        assertStepped("three_steps_n5_c0.5_dt0.1", a);
    }
}
