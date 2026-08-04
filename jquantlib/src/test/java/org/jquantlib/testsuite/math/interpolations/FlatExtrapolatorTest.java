/*
 Copyright (C) 2026 Jose Moya

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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.math.interpolations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.function.DoubleUnaryOperator;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.FlatExtrapolator;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates {@link FlatExtrapolator} — new in C++ QuantLib v1.43 — against the
 * {@code math/v143_flatextrapolation} probe.
 * <p>
 * The decorator is small, so what the probe pins is behaviour at the seams rather than arithmetic in bulk: the strict
 * (rather than inclusive) boundary test in {@code derivative} / {@code secondDerivative}, the linear — not flat —
 * extension of {@code primitive}, and the fact that the decorator owns its extrapolation flag independently of the
 * interpolation it wraps. Each of those is a plausible port bug that a value-only check would not catch.
 *
 * @author Jose Moya
 */
public class FlatExtrapolatorTest {

    /** TIGHT tier: pure interpolation arithmetic, no iteration or transcendental accumulation. */
    private static final double ABS_TOL = 1.0e-14;
    private static final double REL_TOL = 1.0e-12;

    private static final double[] X = { 0.0, 1.0, 2.0, 3.0, 4.0 };
    private static final double[] Y_CUBIC = { 5.0, 3.0, 4.0, 2.0, 1.0 };
    private static final double[] Y_LINEAR = { 1.0, 2.5, 2.0, 4.0, 3.5 };

    //
    // reference plumbing
    //

    private static ReferenceReader ref() {
        return ReferenceReader.load("math/v143_flatextrapolation");
    }

    private static JSONObject expected(final String caseName) {
        return (JSONObject) ref().getCase(caseName).expectedRaw();
    }

    private static double[] inputX(final String caseName) {
        final JSONArray a = ref().getCase(caseName).inputs().getJSONArray("x");
        final double[] xs = new double[a.length()];
        for ( int i = 0; i < xs.length; ++i ) {
            xs[i] = a.getDouble(i);
        }
        return xs;
    }

    private static void assertClose(final String what, final double expected, final double actual) {
        assertEquals(what, expected, actual, Math.max(ABS_TOL, REL_TOL * Math.abs(expected)));
    }

    /**
     * Evaluates {@code f} at each abscissa the probe used for {@code caseName} and compares against the recorded
     * {@code values} array.
     */
    private static void checkSampled(final String caseName, final DoubleUnaryOperator f) {
        final double[] xs = inputX(caseName);
        final JSONArray values = expected(caseName).getJSONArray("values");
        assertEquals(caseName + ": sample count", values.length(), xs.length);
        for ( int i = 0; i < xs.length; ++i ) {
            assertClose(caseName + " at x=" + xs[i], values.getDouble(i), f.applyAsDouble(xs[i]));
        }
    }

    //
    // interpolation builders — mirror the probe's definitions exactly
    //

    private static CubicInterpolation naturalCubic() {
        return new CubicInterpolation(new Array(X), new Array(Y_CUBIC), CubicInterpolation.DerivativeApprox.Spline,
                false, CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
    }

    private static CubicInterpolation notAKnotCubic() {
        return new CubicInterpolation(new Array(X), new Array(Y_CUBIC), CubicInterpolation.DerivativeApprox.Spline,
                false, CubicInterpolation.BoundaryCondition.NotAKnot, 0.0,
                CubicInterpolation.BoundaryCondition.NotAKnot, 0.0);
    }

    private static LinearInterpolation linear() {
        return new LinearInterpolation(new Array(X), new Array(Y_LINEAR));
    }

    /**
     * Builds the decorator with extrapolation enabled, which is what every sampled case needs. The
     * extrapolation-flag semantics themselves are covered separately by
     * {@link #testExtrapolationIsNotAllowedByDefault()}.
     */
    private static FlatExtrapolator extrapolating(final Interpolation decorated) {
        decorated.enableExtrapolation();
        final FlatExtrapolator f = new FlatExtrapolator(decorated);
        f.enableExtrapolation();
        return f;
    }

    //
    // tests — natural cubic spline
    //

    @Test
    public void testCubicValues() {
        QL.info("Testing FlatExtrapolator values over a natural cubic spline against C++ v1.43...");
        final FlatExtrapolator f = extrapolating(naturalCubic());
        checkSampled("cubic_value_at_nodes", f::op);
        checkSampled("cubic_value_in_range_midpoints", f::op);
        checkSampled("cubic_value_below_range", f::op);
        checkSampled("cubic_value_above_range", f::op);
        checkSampled("cubic_value_at_endpoints", f::op);
    }

    @Test
    public void testCubicDerivatives() {
        QL.info("Testing FlatExtrapolator derivatives against C++ v1.43...");
        final FlatExtrapolator f = extrapolating(naturalCubic());
        checkSampled("cubic_derivative_in_range", f::derivative);
        checkSampled("cubic_derivative_at_endpoints", f::derivative);
        checkSampled("cubic_derivative_outside_range", f::derivative);
        checkSampled("cubic_second_derivative_in_range", f::secondDerivative);
        checkSampled("cubic_second_derivative_at_endpoints", f::secondDerivative);
        checkSampled("cubic_second_derivative_outside_range", f::secondDerivative);
    }

    @Test
    public void testCubicPrimitive() {
        QL.info("Testing the FlatExtrapolator primitive's linear extension against C++ v1.43...");
        final FlatExtrapolator f = extrapolating(naturalCubic());
        checkSampled("cubic_primitive_in_range", f::primitive);
        checkSampled("cubic_primitive_at_endpoints", f::primitive);
        checkSampled("cubic_primitive_below_range", f::primitive);
        checkSampled("cubic_primitive_above_range", f::primitive);
    }

    @Test
    public void testCubicAccessors() {
        QL.info("Testing FlatExtrapolator accessor delegation against C++ v1.43...");
        final FlatExtrapolator f = extrapolating(naturalCubic());
        final JSONObject e = expected("cubic_accessors");

        assertClose("xMin", e.getDouble("xMin"), f.xMin());
        assertClose("xMax", e.getDouble("xMax"), f.xMax());
        assertEquals("empty", e.getBoolean("empty"), f.empty());
        assertEquals("allowsExtrapolation", e.getBoolean("allowsExtrapolation"), f.allowsExtrapolation());
        assertEquals("isInRange(xMin)", e.getBoolean("isInRange_xMin"), f.isInRange(0.0));
        assertEquals("isInRange(xMax)", e.getBoolean("isInRange_xMax"), f.isInRange(4.0));
        assertEquals("isInRange(2)", e.getBoolean("isInRange_2"), f.isInRange(2.0));
        assertEquals("isInRange(-1)", e.getBoolean("isInRange_minus1"), f.isInRange(-1.0));
        assertEquals("isInRange(5)", e.getBoolean("isInRange_5"), f.isInRange(5.0));

        assertClose("value at 2 before update", e.getDouble("valueAt2BeforeUpdate"), f.op(2.0));
        f.update();
        assertClose("value at 2 after update", e.getDouble("valueAt2AfterUpdate"), f.op(2.0));
    }

    /**
     * The decorator carries its own extrapolation flag: enabling extrapolation on the wrapped interpolation must not
     * let out-of-range calls through the decorator. Getting this wrong turns a loud error into a silently wrong
     * number, so it is asserted directly rather than inferred.
     */
    @Test
    public void testExtrapolationIsNotAllowedByDefault() {
        QL.info("Testing that FlatExtrapolator owns its extrapolation flag, per C++ v1.43...");
        final JSONObject e = expected("cubic_extrapolation_not_allowed_by_default");

        final CubicInterpolation decorated = naturalCubic();
        decorated.enableExtrapolation();
        final FlatExtrapolator f = new FlatExtrapolator(decorated);

        assertEquals("decorated allows extrapolation", e.getBoolean("decoratedAllowsExtrapolation"),
                decorated.allowsExtrapolation());
        assertEquals("decorator allows extrapolation", e.getBoolean("decoratorAllowsExtrapolation"),
                f.allowsExtrapolation());

        assertEquals("value below throws", e.getBoolean("valueBelowThrows"), throwsFor(() -> f.op(-1.0)));
        assertEquals("value above throws", e.getBoolean("valueAboveThrows"), throwsFor(() -> f.op(5.0)));
        assertEquals("derivative below throws", e.getBoolean("derivativeBelowThrows"),
                throwsFor(() -> f.derivative(-1.0)));
        assertEquals("second derivative above throws", e.getBoolean("secondDerivativeAboveThrows"),
                throwsFor(() -> f.secondDerivative(5.0)));
        assertEquals("primitive below throws", e.getBoolean("primitiveBelowThrows"),
                throwsFor(() -> f.primitive(-1.0)));
        assertEquals("value in range throws", e.getBoolean("valueInRangeThrows"), throwsFor(() -> f.op(2.0)));

        // Once enabled on the decorator itself, the same calls succeed.
        f.enableExtrapolation();
        assertClose("value below after enabling", 5.0, f.op(-1.0));
        assertClose("value above after enabling", 1.0, f.op(5.0));
    }

    private static boolean throwsFor(final Runnable r) {
        try {
            r.run();
            return false;
        } catch ( final RuntimeException expected ) {
            return true;
        }
    }

    /**
     * The decorated interpolation itself must be unaffected — its own values, derivatives and primitive are what the
     * decorator delegates to, so a divergence here would silently shift every decorated result.
     */
    @Test
    public void testUnderlyingCubicSplineIsUnchanged() {
        QL.info("Testing the undecorated cubic spline against C++ v1.43...");
        checkUnderlying("cubic_underlying_spline_reference", naturalCubic());
        checkUnderlying("notaknot_underlying_spline_reference", notAKnotCubic());
        checkUnderlying("linear_underlying_reference", linear());
    }

    private static void checkUnderlying(final String caseName, final Interpolation raw) {
        raw.enableExtrapolation();
        final double[] xs = inputX(caseName);
        final JSONObject e = expected(caseName);
        final JSONArray value = e.getJSONArray("value");
        final JSONArray derivative = e.getJSONArray("derivative");
        final JSONArray secondDerivative = e.getJSONArray("secondDerivative");
        final JSONArray primitive = e.getJSONArray("primitive");

        for ( int i = 0; i < xs.length; ++i ) {
            assertClose(caseName + " value at " + xs[i], value.getDouble(i), raw.op(xs[i]));
            assertClose(caseName + " derivative at " + xs[i], derivative.getDouble(i), raw.derivative(xs[i]));
            assertClose(caseName + " second derivative at " + xs[i], secondDerivative.getDouble(i),
                    raw.secondDerivative(xs[i]));
            assertClose(caseName + " primitive at " + xs[i], primitive.getDouble(i), raw.primitive(xs[i]));
        }
    }

    //
    // tests — not-a-knot cubic spline
    //

    /**
     * The not-a-knot spline has a nonzero second derivative at its endpoints, unlike the natural spline. That makes it
     * the case that actually distinguishes the strict boundary test from an inclusive one: with {@code <=} the
     * endpoint would wrongly return zero.
     */
    @Test
    public void testNotAKnotBoundaryHandling() {
        QL.info("Testing the strict FlatExtrapolator boundary test against C++ v1.43...");
        final FlatExtrapolator f = extrapolating(notAKnotCubic());
        checkSampled("notaknot_value_outside_range", f::op);
        checkSampled("notaknot_derivative_at_endpoints", f::derivative);
        checkSampled("notaknot_second_derivative_at_endpoints", f::secondDerivative);
        checkSampled("notaknot_second_derivative_outside_range", f::secondDerivative);
        checkSampled("notaknot_primitive_outside_range", f::primitive);

        final JSONArray atEndpoints = expected("notaknot_second_derivative_at_endpoints").getJSONArray("values");
        assertTrue("the not-a-knot second derivative at xMin must be nonzero for this test to discriminate",
                Math.abs(atEndpoints.getDouble(0)) > 1.0);
        assertFalse("second derivative just outside the range must be flat",
                Math.abs(f.secondDerivative(4.01)) > 0.0);
    }

    //
    // tests — linear interpolation
    //

    @Test
    public void testLinearUnderlying() {
        QL.info("Testing FlatExtrapolator over a linear interpolation against C++ v1.43...");
        final FlatExtrapolator f = extrapolating(linear());
        checkSampled("linear_value_below_in_above_range", f::op);
        checkSampled("linear_derivative", f::derivative);
        checkSampled("linear_second_derivative", f::secondDerivative);
        checkSampled("linear_primitive", f::primitive);
    }

    @Test
    public void testLinearAccessors() {
        QL.info("Testing FlatExtrapolator accessors over a linear interpolation against C++ v1.43...");
        final FlatExtrapolator f = extrapolating(linear());
        final JSONObject e = expected("linear_accessors");
        assertClose("xMin", e.getDouble("xMin"), f.xMin());
        assertClose("xMax", e.getDouble("xMax"), f.xMax());
        assertEquals("empty", e.getBoolean("empty"), f.empty());
        assertEquals("isInRange(2)", e.getBoolean("isInRange_2"), f.isInRange(2.0));
    }
}
