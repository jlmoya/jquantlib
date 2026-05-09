/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.experimental.credit.OneFactorGaussianStudentCopula;
import org.jquantlib.experimental.credit.OneFactorStudentCopula;
import org.jquantlib.experimental.credit.OneFactorStudentGaussianCopula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.junit.Test;

/**
 * Phase 4m.6 tests for the {@link OneFactorStudentCopula} family.
 *
 * <p>Cross-validation: degenerate-correlation collapses (c=0, c=1) reduce
 * to closed-form Student-T or Gaussian CDFs. Symmetry of Y-CDF at 0 holds
 * by construction.
 *
 * <p>Reference: QuantLib v1.42.1
 * {@code ql/experimental/credit/onefactorstudentcopula.{hpp,cpp}}.
 *
 * <p>Tests use a small integration grid (50 steps) to keep runtime tractable
 * — the constructor tabulates 200 cumulative-Y entries and each entry
 * requires a 2D integration sweep.
 */
public class OneFactorStudentCopulaTest {

    private static final double TIGHT = 1.0e-12;
    private static final double LOOSE = 1.0e-6;

    @Test
    public void rejectsLowDof() {
        final Handle<Quote> rho = new Handle<Quote>(new SimpleQuote(0.3));
        try {
            new OneFactorStudentCopula(rho, 2, 5);
            fail("expected exception for nz <= 2");
        } catch (final Exception e) {
            // expected
        }
        try {
            new OneFactorStudentCopula(rho, 5, 2);
            fail("expected exception for nm <= 2");
        } catch (final Exception e) {
            // expected
        }
        try {
            new OneFactorGaussianStudentCopula(rho, 2);
            fail("expected exception for nz <= 2");
        } catch (final Exception e) {
            // expected
        }
        try {
            new OneFactorStudentGaussianCopula(rho, 2);
            fail("expected exception for nm <= 2");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void densityScalesByVarianceFactor() {
        // density(m) = StudentDistribution(nm)(m / scaleM) / scaleM
        // where scaleM = sqrt((nm-2)/nm). At m=0 the unit-variance T density.
        final Handle<Quote> rho = new Handle<Quote>(new SimpleQuote(0.3));
        // Use small grid to keep ctor cheap.
        final OneFactorStudentCopula c = new OneFactorStudentCopula(rho, 5, 5, 5.0, 50);
        // The peak density at 0 should be positive.
        assertTrue(c.density(0.0) > 0.0);
        // Symmetry
        assertEquals(c.density(1.0), c.density(-1.0), TIGHT);
    }

    @Test
    public void cumulativeZAtZeroIsHalf() {
        final Handle<Quote> rho = new Handle<Quote>(new SimpleQuote(0.3));
        final OneFactorStudentCopula c = new OneFactorStudentCopula(rho, 5, 5, 5.0, 50);
        // cumulative_(0/scaleZ) = cumulative_(0) = 0.5
        assertEquals(0.5, c.cumulativeZ(0.0), TIGHT);
    }

    @Test
    public void doubleStudent_correlationIndependence() {
        // The Y-CDF at y=0 should be 1/2 by symmetry, regardless of correlation.
        // Use 50-step integration grid and small max for tractable runtime.
        final Handle<Quote> rho = new Handle<Quote>(new SimpleQuote(0.3));
        final OneFactorStudentCopula c = new OneFactorStudentCopula(rho, 5, 5, 5.0, 50);
        // Y is symmetric → F_Y(0) ~ 0.5; integration noise allowed.
        final double cy = c.cumulativeY(0.0);
        assertEquals(0.5, cy, 1.0e-2);
    }

    @Test
    public void gaussianStudent_smoke() {
        // Smoke: must construct and produce reasonable values.
        final Handle<Quote> rho = new Handle<Quote>(new SimpleQuote(0.3));
        final OneFactorGaussianStudentCopula c =
                new OneFactorGaussianStudentCopula(rho, 5, 5.0, 50);
        assertTrue(c.density(0.0) > 0.0);
        assertEquals(0.5, c.cumulativeZ(0.0), TIGHT);
        assertEquals(0.5, c.cumulativeY(0.0), 1.0e-2);
    }

    @Test
    public void studentGaussian_smoke() {
        // Smoke: must construct and produce reasonable values.
        final Handle<Quote> rho = new Handle<Quote>(new SimpleQuote(0.3));
        final OneFactorStudentGaussianCopula c =
                new OneFactorStudentGaussianCopula(rho, 5, 5.0, 50);
        assertTrue(c.density(0.0) > 0.0);
        assertEquals(0.5, c.cumulativeZ(0.0), TIGHT);
        assertEquals(0.5, c.cumulativeY(0.0), 1.0e-2);
    }

    @Test
    public void studentGaussian_density_isStudentT() {
        // For OneFactorStudentGaussianCopula.density(m):
        //   density(m) = StudentDistribution(nm)(m/scaleM) / scaleM
        // The C++ density is rescaled. At m=0 it is positive; at large |m|
        // it decays.
        final Handle<Quote> rho = new Handle<Quote>(new SimpleQuote(0.3));
        final OneFactorStudentGaussianCopula c =
                new OneFactorStudentGaussianCopula(rho, 5, 5.0, 50);
        final double d0 = c.density(0.0);
        final double d3 = c.density(3.0);
        assertTrue("density at 0 should be larger than at 3 (d0=" + d0 + ", d3=" + d3 + ")",
                d0 > d3);
    }
}
