/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-RND-b — FdmLocalVolFwdOp tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLocalVolFwdOp;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.LocalConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link FdmLocalVolFwdOp}.
 *
 * <p>For a constant local-volatility {@code sigma} the operator reduces to
 * the (1D) Black-Scholes Fokker-Planck operator on log-spot. On an
 * interior cell of a uniform mesh with spacing {@code h}:
 * <pre>
 *   v       = sigma^2
 *   conv    = -r + q + 0.5*v
 *   diff    = 0.5*v
 *   diag[i] = -2*diff/h^2 + 0          = -v/h^2
 *   lower[i]=  diff/h^2 - conv/(2h)
 *   upper[i]=  diff/h^2 + conv/(2h)
 * </pre>
 *
 * @author Phase 5h.5-RND-b
 */
public class FdmLocalVolFwdOpTest {

    private static final double TIGHT = 1e-12;
    private static final double LOOSE = 1e-8;

    public FdmLocalVolFwdOpTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void interiorCoefficientsMatchAnalyticalForConstantVol() {
        // Build a uniform log-spot mesh.
        final double sigma = 0.20;
        final double r     = 0.05;
        final double q     = 0.02;
        final double xMin  = -1.0;
        final double xMax  =  1.0;
        final int    n     = 11;
        final double h     = (xMax - xMin) / (n - 1);

        final double[] xs = new double[n];
        for (int i = 0; i < n; ++i) {
            xs[i] = xMin + i * h;
        }

        final Predefined1dMesher m1 = new Predefined1dMesher(xs);
        final FdmMesherComposite mesher = new FdmMesherComposite(m1);

        // Date / DC infrastructure.
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final Quote spot = new SimpleQuote(1.0);
        final YieldTermStructure rTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(r)), dc);
        final YieldTermStructure qTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(q)), dc);
        final LocalVolTermStructure lv = new LocalConstantVol(today, sigma, dc);

        final FdmLocalVolFwdOp op = new FdmLocalVolFwdOp(mesher, spot, rTS, qTS, lv);
        op.setTime(0.0, 1.0);  // 1y window — flat curves so r,q are constant

        final Matrix M = op.toMatrix();
        // Interior cell at index n/2 = 5
        final int i = 5;

        final double v        = sigma * sigma;
        final double diff     = 0.5 * v;
        final double conv     = -r + q + 0.5 * v;
        final double expDiag  = -v / (h * h);
        final double expLower = diff / (h * h) - conv / (2.0 * h);
        final double expUpper = diff / (h * h) + conv / (2.0 * h);

        assertEquals("diag",  expDiag,  M.get(i, i),     LOOSE);
        assertEquals("lower", expLower, M.get(i, i - 1), LOOSE);
        assertEquals("upper", expUpper, M.get(i, i + 1), LOOSE);
    }

    @Test
    public void applyOnZeroDensityYieldsZero() {
        final double sigma = 0.30;
        final int n = 21;
        final double[] xs = new double[n];
        for (int i = 0; i < n; ++i) {
            xs[i] = -2.0 + i * 0.2;
        }
        final FdmMesherComposite mesher = new FdmMesherComposite(new Predefined1dMesher(xs));

        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final Quote spot = new SimpleQuote(1.0);
        final YieldTermStructure rTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.0)), dc);
        final YieldTermStructure qTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.0)), dc);
        final LocalVolTermStructure lv = new LocalConstantVol(today, sigma, dc);

        final FdmLocalVolFwdOp op = new FdmLocalVolFwdOp(mesher, spot, rTS, qTS, lv);
        op.setTime(0.0, 0.5);

        final Array zero = new Array(n).fill(0.0);
        final Array out  = op.apply(zero);
        for (int i = 0; i < n; ++i) {
            assertEquals(0.0, out.get(i), TIGHT);
        }
    }

    @Test
    public void inactiveDirectionApplyReturnsZero() {
        final int n = 5;
        final double[] xs = {-1.0, -0.5, 0.0, 0.5, 1.0};
        final FdmMesherComposite mesher = new FdmMesherComposite(new Predefined1dMesher(xs));

        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final Quote spot = new SimpleQuote(1.0);
        final YieldTermStructure rTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.05)), dc);
        final YieldTermStructure qTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.02)), dc);
        final LocalVolTermStructure lv = new LocalConstantVol(today, 0.20, dc);

        final FdmLocalVolFwdOp op = new FdmLocalVolFwdOp(mesher, spot, rTS, qTS, lv);
        op.setTime(0.0, 0.25);

        final Array r = new Array(n).fill(1.0);
        // direction 1 (inactive — only direction 0 is the local-vol direction)
        final Array out = op.applyDirection(1, r);
        for (int i = 0; i < n; ++i) {
            assertEquals(0.0, out.get(i), TIGHT);
        }
        assertTrue(op.size() == 1);
    }
}
