/*
 Copyright (C) 2026 JQuantLib migration contributors

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
*/

package org.jquantlib.testsuite.termstructures.yieldcurves;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.yieldcurves.CubicBSplinesFitting;
import org.jquantlib.termstructures.yieldcurves.ExponentialSplinesFitting;
import org.jquantlib.termstructures.yieldcurves.FittedBondDiscountCurve;
import org.jquantlib.termstructures.yieldcurves.NaturalCubicFitting;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Cross-validation tests for the Phase2-L2-B yield fitting methods ports.
 *
 * <p>Reference values come from the C++ v1.42.1 formulas in
 * {@code ql/termstructures/yield/nonlinearfittingmethods.cpp} evaluated symbolically
 * (no external probe required because all four methods are pure closed-form discount
 * functions of an {@code x[]} parameter vector).
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link ExponentialSplinesFitting} — constrained and unconstrained, free vs. fixed kappa.</li>
 *   <li>{@link CubicBSplinesFitting}      — constrained and unconstrained.</li>
 *   <li>{@link NaturalCubicFitting}       — natural-cubic-spline discount.</li>
 *   <li>Size + clone semantics.</li>
 * </ul>
 *
 * <p>Phase 2 forward closure L2-B.
 */
public class Phase2L2BYieldFittingTest {

    /** Tight tolerance for analytical discount-factor evaluation. */
    private static final double TIGHT = 1.0e-12;

    // ---------- ExponentialSplinesFitting ----------

    @Test
    public void testExponentialSplinesConstrainedFixedKappa() {
        // C++ peculiarity: with constrainAtZero=true and fixedKappa set, in discountFunction
        // local N = size() (NOT numCoeffs_). So the loop runs over [0, size()-1) iterations.
        // With numCoeffs=4, constrainAtZero=true, fixedKappa given:
        //   size() = numCoeffs - 1 = 3
        //   In discountFunction, N := size() = 3, so loop runs i in [0, 2):
        //     d(t) = x[0]*exp(-kappa*2*t) + x[1]*exp(-kappa*3*t) + (1 - x[0] - x[1])*exp(-kappa*t)
        final double kappa = 0.3;
        final ExponentialSplinesFitting fit = new ExponentialSplinesFitting(
                true, new Array(0), null, new Array(0), 0.0, Double.MAX_VALUE, 4, kappa,
                new org.jquantlib.math.optimization.NoConstraint());
        assertEquals("size with fixed kappa", 3, fit.size());

        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(20, TimeUnit.Years));

        final double x0 = 0.3, x1 = 0.2, x2 = 0.1;
        final Array params = new Array(new double[] { x0, x1, x2 });
        final FittedBondDiscountCurve curve = new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        // Reference (mirroring C++ literally): N = size() = 3, loop i in [0,2).
        //   d(t) = sum_{i=0..1} x[i]*exp(-kappa*(i+2)*t) + coeff * exp(-kappa*t)
        //   coeff = 1 - sum_{i=0..1} x[i]
        final double[] times = { 0.5, 1.0, 2.0, 5.0, 10.0 };
        for ( final double t : times ) {
            double expected = 0.0;
            double coeff = 0.0;
            for ( int i = 0; i < 2; ++i ) {
                final double xi = (i == 0) ? x0 : x1;
                expected += xi * Math.exp(-kappa * (i + 2) * t);
                coeff += xi;
            }
            coeff = 1.0 - coeff;
            expected += coeff * Math.exp(-kappa * t);
            assertEquals("Exp splines (constrained, fixed kappa) at t=" + t, expected, curve.discount(t), TIGHT);
        }
    }

    @Test
    public void testExponentialSplinesUnconstrainedFreeKappa() {
        // constrainAtZero=false, kappa free (NaN), numCoeffs=2 → size = 3 (= 2+1, no kappa fixed)
        // Last x is kappa, first 2 are coefficients.
        // d(t) = x[0]*exp(-kappa*1*t) + x[1]*exp(-kappa*2*t)
        final ExponentialSplinesFitting fit = new ExponentialSplinesFitting(
                false, new Array(0), null, new Array(0), 0.0, Double.MAX_VALUE, 2, Double.NaN,
                new org.jquantlib.math.optimization.NoConstraint());
        assertEquals("size unconstrained free kappa", 3, fit.size());

        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(20, TimeUnit.Years));

        final double x0 = 0.4, x1 = 0.5, kappa = 0.25;
        final Array params = new Array(new double[] { x0, x1, kappa });
        final FittedBondDiscountCurve curve = new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        final double[] times = { 0.5, 1.0, 2.0, 5.0, 10.0 };
        for ( final double t : times ) {
            final double expected = x0 * Math.exp(-kappa * t) + x1 * Math.exp(-2.0 * kappa * t);
            assertEquals("Exp splines (unconstrained, free kappa) at t=" + t, expected, curve.discount(t), TIGHT);
        }
    }

    @Test
    public void testExponentialSplinesClone() {
        final ExponentialSplinesFitting fit = new ExponentialSplinesFitting(true);
        final ExponentialSplinesFitting clone = fit.clone();
        assertEquals("clone size matches", fit.size(), clone.size());
        assertEquals("clone constrainAtZero matches", fit.constrainAtZero(), clone.constrainAtZero());
    }

    // ---------- CubicBSplinesFitting ----------

    /** Knot vector taken from C++ Examples/FittedBondCurve/FittedBondCurve.cpp:212-214. */
    private static final double[] CPP_EXAMPLE_KNOTS = {
            -30.0, -20.0, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0
    };

    @Test
    public void testCubicBSplinesUnconstrained() {
        // 11 knots → n = 11-5 = 6 (BSpline internal n), basisFunctions = 11-4 = 7. size = 7 unconstrained.
        final CubicBSplinesFitting fit = new CubicBSplinesFitting(CPP_EXAMPLE_KNOTS, false);
        assertEquals("size unconstrained", 7, fit.size());

        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(15, TimeUnit.Years));

        // Coefficients with a gentle decay so curve stays positive.
        final Array params = new Array(new double[] { 1.0, 0.98, 0.95, 0.9, 0.8, 0.7, 0.6 });
        final FittedBondDiscountCurve curve = new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        // Reference: d(t) = sum x[i] * N_{i,3}(t)
        final double[] times = { 0.5, 1.0, 5.0, 10.0 };
        for ( final double t : times ) {
            double expected = 0.0;
            for ( int i = 0; i < 7; ++i ) {
                expected += params.get(i) * fit.basisFunction(i, t);
            }
            assertEquals("Cubic B-splines unconstrained at t=" + t, expected, curve.discount(t), TIGHT);
        }
    }

    @Test
    public void testCubicBSplinesConstrained() {
        // Constrained: d(0) = 1, basisFunctions = 7, size = 7-1 = 6. N_=1.
        final CubicBSplinesFitting fit = new CubicBSplinesFitting(CPP_EXAMPLE_KNOTS, true);
        assertEquals("size constrained", 6, fit.size());

        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(15, TimeUnit.Years));

        final Array params = new Array(new double[] { 0.98, 0.95, 0.9, 0.8, 0.7, 0.6 });
        final FittedBondDiscountCurve curve = new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        // d(0) must equal 1.0 (the constraint).
        assertEquals("d(0)=1", 1.0, curve.discount(0.0), TIGHT);
    }

    // ---------- NaturalCubicFitting ----------

    @Test
    public void testNaturalCubicFittingDiscountFunction() {
        // Knot times {1, 5, 10, 20}. After prepending 0 and dedup-sort: {0, 1, 5, 10, 20}.
        // n = 5, size = n - 1 = 4 free nodal values (y[0]=1 is fixed).
        final double[] knots = { 1.0, 5.0, 10.0, 20.0 };
        final NaturalCubicFitting fit = new NaturalCubicFitting(knots);
        assertEquals("size = knots+1-1", 4, fit.size());

        final Date today = new Date(15, Month.July, 2019);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final Date maxDate = today.add(new Period(20, TimeUnit.Years));

        // Free nodal values at t = 1, 5, 10, 20 (discounts).
        final Array params = new Array(new double[] { 0.97, 0.85, 0.7, 0.5 });
        final FittedBondDiscountCurve curve = new FittedBondDiscountCurve(today, fit, params, maxDate, dc);

        // At a knot the spline must take the nodal value exactly (interpolation property).
        assertEquals("d(1) at knot",  0.97, curve.discount(1.0),  TIGHT);
        assertEquals("d(5) at knot",  0.85, curve.discount(5.0),  TIGHT);
        assertEquals("d(10) at knot", 0.70, curve.discount(10.0), TIGHT);
        // d(0) is fixed to 1.0 (constraint).
        assertEquals("d(0)=1", 1.0, curve.discount(0.0), TIGHT);
        // Between-knot value must be in the [endpoint] range.
        assertTrue("d(2) in (0.85, 0.97)",
                curve.discount(2.0) > 0.85 && curve.discount(2.0) < 0.97);
    }

    @Test
    public void testNaturalCubicClampingAtBoundary() {
        final double[] knots = { 1.0, 5.0 };
        final NaturalCubicFitting fit = new NaturalCubicFitting(knots);
        // Expect 2 free nodal values after prepending 0 → knots {0, 1, 5}, size=2.
        assertEquals("size after prepend+dedup", 2, fit.size());
    }

    // ---------- Clone roundtrip ----------

    @Test
    public void testCubicBSplinesClone() {
        final CubicBSplinesFitting fit = new CubicBSplinesFitting(CPP_EXAMPLE_KNOTS, true);
        final CubicBSplinesFitting clone = fit.clone();
        assertEquals("clone size matches", fit.size(), clone.size());
        // basis-function agreement at a probe point
        for ( int i = 0; i < fit.size(); ++i ) {
            assertEquals("clone basis[" + i + "] match", fit.basisFunction(i, 3.5), clone.basisFunction(i, 3.5),
                    TIGHT);
        }
    }

    @Test
    public void testNaturalCubicClone() {
        final double[] knots = { 1.0, 5.0, 10.0 };
        final NaturalCubicFitting fit = new NaturalCubicFitting(knots);
        final NaturalCubicFitting clone = fit.clone();
        assertEquals("clone size matches", fit.size(), clone.size());
        assertEquals("clone constrainAtZero matches", fit.constrainAtZero(), clone.constrainAtZero());
    }
}
