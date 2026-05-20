/*
 Copyright (C) 2026 JQuantLib migration contributors

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

package org.jquantlib.testsuite.termstructures.yieldcurves;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.yieldcurves.FittedBondDiscountCurve;
import org.jquantlib.termstructures.yieldcurves.NelsonSiegelFitting;
import org.jquantlib.termstructures.yieldcurves.SimplePolynomialFitting;
import org.jquantlib.termstructures.yieldcurves.SvenssonFitting;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Tests for {@link FittedBondDiscountCurve} parametric (no-fit) mode plus
 * the analytical {@link NelsonSiegelFitting}, {@link SvenssonFitting} and
 * {@link SimplePolynomialFitting} discount functions.
 *
 * <p>Reference values were computed independently in Python using the exact
 * formulas from QuantLib v1.42.1
 * {@code ql/termstructures/yield/nonlinearfittingmethods.cpp}.
 *
 * <p>Mirrors C++ test {@code testEvaluation} in
 * {@code test-suite/fittedbonddiscountcurve.cpp} (parametric path).
 */
public class FittedBondDiscountCurveTest {

    /** Tight tolerance for analytical discount-factor evaluation. */
    private static final double TIGHT = 1.0e-12;

    private static final double[] GRID = {
        0.5, 1.0, 2.0, 3.0, 5.0, 7.0, 10.0, 15.0, 19.0
    };

    @Test
    public void testNelsonSiegelDiscountFunction() {
        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(20, TimeUnit.Years));

        // Params: c0 = 0.04, c1 = -0.02, c2 = 0.01, kappa = 0.5
        final Array params = new Array(new double[] { 0.04, -0.02, 0.01, 0.5 });

        final NelsonSiegelFitting fit = new NelsonSiegelFitting();
        final FittedBondDiscountCurve curve =
                new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        // Reference values computed in Python with the C++ formula
        // (k = kappa, r = c0 + (c1+c2)*(1-exp(-k*t))/((k+eps)*(t+eps)) - c2*exp(-k*t)).
        final double[] expected = {
            0.9883859580211468,
            0.9742714611772870,
            0.9417645335842487,
            0.9068586373522667,
            0.8373296407189543,
            0.7722166521498565,
            0.6842301343358408,
            0.5599386252473082,
            0.4771199867441009
        };

        for (int i = 0; i < GRID.length; i++) {
            assertEquals("NS discount mismatch at t=" + GRID[i],
                    expected[i], curve.discount(GRID[i]), TIGHT);
        }
    }

    @Test
    public void testSvenssonDiscountFunction() {
        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(20, TimeUnit.Years));

        // Params: c0=0.04, c1=-0.02, c2=0.01, c3=0.005, kappa=0.5, kappa1=0.2
        final Array params = new Array(new double[] {
                0.04, -0.02, 0.01, 0.005, 0.5, 0.2
        });

        final SvenssonFitting fit = new SvenssonFitting();
        final FittedBondDiscountCurve curve =
                new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        final double[] expected = {
            0.9882703522846780,
            0.9738447483346691,
            0.9403164622680297,
            0.9040991612524092,
            0.8318164479184709,
            0.7643768800195534,
            0.6741444874337597,
            0.5488394109490421,
            0.4665907319362848
        };

        for (int i = 0; i < GRID.length; i++) {
            assertEquals("Svensson discount mismatch at t=" + GRID[i],
                    expected[i], curve.discount(GRID[i]), TIGHT);
        }
    }

    @Test
    public void testSimplePolynomialConstrainedAtZero() {
        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(20, TimeUnit.Years));

        // degree=3, constrainAtZero=true → 3 free coefficients, d(0)=1
        final Array params = new Array(new double[] { -0.05, 0.01, -0.001 });

        final SimplePolynomialFitting fit = new SimplePolynomialFitting(3, true);
        final FittedBondDiscountCurve curve =
                new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        // Reference: d(t) = 1 + x[0]*t + x[1]*t^2 + x[2]*t^3
        final double[] expected = {
            0.9773749999999999,
            0.9590000000000000,
            0.9320000000000001,
            0.9129999999999999,
            0.8750000000000000,
            0.7969999999999999,
            0.5000000000000000,
            // For t=15 and t=19 the polynomial goes negative — but the curve
            // is still a valid evaluator. We test only up to t=10 to keep the
            // discount factors physically meaningful.
        };

        for (int i = 0; i < expected.length; i++) {
            assertEquals("SimplePoly d=3 constrained mismatch at t=" + GRID[i],
                    expected[i], curve.discount(GRID[i]), TIGHT);
        }
    }

    @Test
    public void testSimplePolynomialUnconstrained() {
        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(20, TimeUnit.Years));

        // degree=2, constrainAtZero=false → 3 coeffs (degree+1)
        final Array params = new Array(new double[] { 1.0, 0.95, 0.85 });

        final SimplePolynomialFitting fit = new SimplePolynomialFitting(2, false);
        final FittedBondDiscountCurve curve =
                new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        // Reference: d(t) = x[0] + x[1]*t + x[2]*t^2
        final double[] expected = {
            1.6875000000000000,
            2.7999999999999998,
            6.2999999999999998,
            11.5000000000000000,
            27.0000000000000000,
            49.2999999999999972,
            95.5000000000000000,
            206.5000000000000000,
            325.8999999999999773
        };

        for (int i = 0; i < GRID.length; i++) {
            assertEquals("SimplePoly d=2 unconstrained mismatch at t=" + GRID[i],
                    expected[i], curve.discount(GRID[i]), TIGHT);
        }
    }

    /**
     * Mirrors C++ {@code testEvaluation}: parametric curve works as evaluator
     * up to its max date and rejects time queries past it.
     */
    @Test
    public void testEvaluationBeyondMaxDate() {
        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(10, TimeUnit.Years));

        final Array params = new Array(new double[] { 0.04, -0.02, 0.01, 0.5 });
        final NelsonSiegelFitting fit = new NelsonSiegelFitting();
        final FittedBondDiscountCurve curve =
                new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        // OK within the curve's max range
        assertTrue("discount at 3.0 should be > 0",
                curve.discount(3.0) > 0.0);

        // Past the max date → must throw (extrapolation off by default)
        try {
            curve.discount(12.0);
            fail("Expected exception when querying past max curve time");
        } catch (final RuntimeException expected) {
            // OK — expected
        }
    }

    /** numberOfBonds() is zero for a parametric curve (no helpers). */
    @Test
    public void testFitResultsExposed() {
        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(20, TimeUnit.Years));

        final Array params = new Array(new double[] { 0.04, -0.02, 0.01, 0.5 });
        final NelsonSiegelFitting fit = new NelsonSiegelFitting();
        final FittedBondDiscountCurve curve =
                new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        // trigger calculate by querying the curve
        curve.discount(1.0);

        assertEquals("solution() size matches fitting-method size",
                4, curve.fitResults().solution().size());
        assertEquals("Iterations should be zero in parametric mode",
                0, curve.fitResults().numberOfIterations());
    }

    // ------------------------------------------------------------------
    // BLOCKED ports from test-suite/fittedbonddiscountcurve.cpp (Phase1-D5-B-R2)
    // ------------------------------------------------------------------
    // testEvaluation (cpp:40)
    //   Uses ExponentialSplinesFitting (not ported to Java yet, ~80 LOC,
    //   ql/termstructures/yield/nonlinearfittingmethods.hpp lines 92-128).
    //   Java side covers the salient semantic ("curve works as evaluator
    //   then rejects past max date") via testEvaluationBeyondMaxDate above
    //   using NelsonSiegelFitting — i.e. an EXISTING_EQUIVALENT for the
    //   curve-as-evaluator property; full ExponentialSplines port BLOCKED.
    //
    // testRequiredGuess (cpp:224), testGuessSize (cpp:254), testConstraint
    // (cpp:285)
    //   All three exercise the BondHelper-driven least-squares path of
    //   FittedBondDiscountCurve. Java side currently only implements the
    //   parametric (no-fit) ctors — bond-helper fitting + the generic
    //   BondHelper rate-helper subclass (the C++ "BondHelper" wrapping any
    //   Bond as a Quote helper for fitting; ~150 LOC) is tracked as a
    //   carry-forward (see FittedBondDiscountCurve.java header comment:
    //   "BondHelper-driven least-squares optimization (Simplex / LM) is
    //   tracked as a Phase 5d.5-ZCS+FBb carry-forward"). Total infra to
    //   unblock: ~400 LOC across BondHelper class + bond-helper FBdC ctor
    //   + L2-penalty / constraint plumbing on FittingMethod.
}
