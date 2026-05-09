/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.termstructures.yieldcurves;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/fittedbonddiscountcurve.cpp}
 * v1.42.1 (339 LOC, 5 cases).
 *
 * <p>Exercises the {@code FittedBondDiscountCurve} family — yield curves
 * built by least-squares fitting a parametric model
 * (Nelson-Siegel, Svensson, exponential-spline, simple-polynomial) to a
 * basket of bond prices. Exercises evaluation, flat-extrapolation
 * behavior, required initial guess (some forms need it), guess-vector
 * size validation, and constraint application.
 *
 * <p><strong>All 5 cases deferred to Phase 5d.5</strong> — Java has no
 * fitted bond discount curve family:
 * <ul>
 *   <li>No {@code FittedBondDiscountCurve} class
 *       (C++ {@code ql/termstructures/yield/fittedbonddiscountcurve.hpp});
 *   <li>No fitting-method classes
 *       ({@code NelsonSiegelFitting}, {@code SvenssonFitting},
 *        {@code ExponentialSplinesFitting}, {@code SimplePolynomialFitting},
 *        {@code CubicBSplinesFitting}, {@code SpreadFittingMethod});
 *   <li>No least-squares / optimization wiring linking the fitting basis
 *       to {@code BondHelper} pricing residuals.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the parametric bond-fitting curve family
 * belongs to a future production-code phase. The required least-squares
 * minimization infrastructure exists in {@code org.jquantlib.math.optimization},
 * but the fitting-method hierarchy and bond-residual cost function are
 * unimplemented.
 *
 * <p>Source: {@code test-suite/fittedbonddiscountcurve.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class FittedBondDiscountCurveTest {

    private static final String REASON =
            "Phase 5d.5 — requires FittedBondDiscountCurve port + at least "
          + "one FittingMethod (Nelson-Siegel / Svensson / spline / polynomial); "
          + "no Java equivalent yet";

    @Ignore(REASON)
    @Test
    public void testEvaluation() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFlatExtrapolation() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testRequiredGuess() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testGuessSize() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testConstraint() { fail("not implemented"); }
}
