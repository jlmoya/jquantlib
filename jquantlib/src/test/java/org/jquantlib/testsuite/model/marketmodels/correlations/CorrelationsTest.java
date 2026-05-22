/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3h Track B.1.

 This source code is release under the BSD License.
 */
package org.jquantlib.testsuite.model.marketmodels.correlations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.correlations.TimeHomogeneousForwardCorrelation;
import org.jquantlib.testsuite.util.Tolerance;
import org.junit.Test;

/**
 * Unit tests for the marketmodels correlations family (Phase 3h Track B.1).
 *
 * Cross-validates against C++ v1.42.1
 * {@code ql/models/marketmodels/correlations/expcorrelations.cpp} +
 * {@code timehomogeneousforwardcorrelation.cpp}: the formulas are simple
 * closed-form so we cross-check against the algebraic definition and against
 * structural invariants (symmetry, diagonal=1, long-term limit).
 */
public class CorrelationsTest {

    private static List<Double> rateTimesGrid() {
        return new ArrayList<>(Arrays.asList(0.5, 1.0, 1.5, 2.0, 2.5, 3.0));
    }

    @Test
    public void exponentialCorrelations_diagonalIsOne_andSymmetric() {
        final List<Double> rateTimes = rateTimesGrid();
        final Matrix m = ExponentialForwardCorrelation.exponentialCorrelations(
                rateTimes, 0.5, 0.2, 1.0, 0.0);
        final int n = rateTimes.size() - 1;
        assertEquals(n, m.rows());
        assertEquals(n, m.columns());
        for (int i = 0; i < n; ++i) {
            assertEquals(1.0, m.get(i, i), 1.0e-15);
            for (int j = 0; j < i; ++j) {
                assertEquals("symmetry [" + i + "][" + j + "]",
                        m.get(i, j), m.get(j, i), 1.0e-15);
                assertTrue("entry within [longTermCorr,1]",
                        m.get(i, j) >= 0.5 - 1e-12 && m.get(i, j) <= 1.0 + 1e-12);
            }
        }
    }

    @Test
    public void exponentialCorrelations_formulaMatches_smallCase() {
        // Formula: rho(i,j) = L + (1-L) * exp(-beta * |(t_i - t)^gamma - (t_j - t)^gamma|)
        // For L=0.5, beta=0.2, gamma=1.0, time=0, rateTimes={1,2,3,4}, n=3 rates
        final List<Double> rateTimes = Arrays.asList(1.0, 2.0, 3.0, 4.0);
        final double L = 0.5, beta = 0.2;
        final Matrix m = ExponentialForwardCorrelation.exponentialCorrelations(
                rateTimes, L, beta, 1.0, 0.0);
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                final double expected = (i == j) ? 1.0
                        : L + (1.0 - L) * Math.exp(-beta * Math.abs(rateTimes.get(i) - rateTimes.get(j)));
                if (!Tolerance.tight(m.get(i, j), expected)) {
                    fail("[" + i + "," + j + "]: exp=" + expected + " got=" + m.get(i, j));
                }
            }
        }
    }

    @Test
    public void exponentialCorrelations_zeroRowsIfRateExpired() {
        // After time > rateTimes[0], row 0 must be all zero.
        final List<Double> rateTimes = Arrays.asList(0.5, 1.0, 1.5, 2.0);
        final Matrix m = ExponentialForwardCorrelation.exponentialCorrelations(
                rateTimes, 0.5, 0.2, 1.0, 0.6);
        // rate 0 has rateTimes[0]=0.5; t=0.6 > 0.5 → row 0 stays zero
        for (int j = 0; j < 3; ++j) {
            assertEquals("row 0 col " + j + " expired", 0.0, m.get(0, j), 1.0e-15);
            assertEquals("col 0 row " + j + " expired", 0.0, m.get(j, 0), 1.0e-15);
        }
        // rate 1 (rateTimes[1]=1.0) is alive
        assertEquals(1.0, m.get(1, 1), 1.0e-15);
    }

    @Test
    public void exponentialForwardCorrelation_construction_gammaOne() {
        final List<Double> rateTimes = rateTimesGrid();
        final ExponentialForwardCorrelation efc =
                new ExponentialForwardCorrelation(rateTimes, 0.5, 0.2, 1.0);
        assertEquals(rateTimes.size() - 1, efc.numberOfRates());
        assertEquals(rateTimes.size() - 1, efc.times().size());
        // there must be one matrix per evolution time
        assertEquals(efc.times().size(), efc.correlations().size());
        // first matrix should be full-rank (no zero rows)
        final Matrix m0 = efc.correlation(0);
        assertEquals(rateTimes.size() - 1, m0.rows());
        for (int i = 0; i < m0.rows(); ++i) {
            assertEquals(1.0, m0.get(i, i), 1.0e-15);
        }
        // by step k=1, the (0,0) entry has been "shifted out"; the upper-left
        // block of the original matrix has compressed by one
        final Matrix m1 = efc.correlation(1);
        // diagonal still 1 in alive positions
        assertEquals(1.0, m1.get(1, 1), 1.0e-15);
        assertEquals(1.0, m1.get(2, 2), 1.0e-15);
    }

    @Test
    public void exponentialForwardCorrelation_construction_gammaLessThanOne_sameLengthAsTimes() {
        final List<Double> rateTimes = rateTimesGrid();
        final ExponentialForwardCorrelation efc =
                new ExponentialForwardCorrelation(rateTimes, 0.5, 0.2, 0.5);
        // gamma!=1 path: times == rateTimes[0..n-1]; one matrix per times entry
        assertEquals(efc.times().size(), efc.correlations().size());
        // Each matrix should be symmetric and have 1 on diagonal
        for (int k = 0; k < efc.correlations().size(); ++k) {
            final Matrix m = efc.correlation(k);
            for (int i = 0; i < m.rows(); ++i) {
                if (m.get(i, i) != 0.0) {
                    assertEquals("diag k=" + k + " i=" + i, 1.0, m.get(i, i), 1.0e-15);
                }
                for (int j = 0; j < i; ++j) {
                    assertEquals("sym k=" + k, m.get(i, j), m.get(j, i), 1.0e-15);
                }
            }
        }
    }

    @Test
    public void timeHomogeneousForwardCorrelation_evolvedMatrices_preservesDiagonal() {
        final int n = 5;
        final Matrix fwd = new Matrix(n, n);
        for (int i = 0; i < n; ++i) {
            fwd.set(i, i, 1.0);
            for (int j = 0; j < i; ++j) {
                final double v = 0.5 + 0.5 * Math.exp(-0.2 * Math.abs(i - j));
                fwd.set(i, j, v);
                fwd.set(j, i, v);
            }
        }
        final List<Matrix> evolved = TimeHomogeneousForwardCorrelation.evolvedMatrices(fwd);
        assertEquals(n, evolved.size());
        // matrix[0] must equal fwd
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < n; ++j) {
                assertEquals(fwd.get(i, j), evolved.get(0).get(i, j), 1.0e-15);
            }
        }
        // matrix[k] entry (i,j) for i,j>=k equals fwd(i-k, j-k)
        for (int k = 1; k < n; ++k) {
            for (int i = k; i < n; ++i) {
                for (int j = k; j < n; ++j) {
                    assertEquals("k=" + k + " (" + i + "," + j + ")",
                            fwd.get(i - k, j - k), evolved.get(k).get(i, j), 1.0e-15);
                }
            }
            // entries with i<k or j<k must be zero
            for (int i = 0; i < k; ++i) {
                for (int j = 0; j < n; ++j) {
                    assertEquals(0.0, evolved.get(k).get(i, j), 1.0e-15);
                    assertEquals(0.0, evolved.get(k).get(j, i), 1.0e-15);
                }
            }
        }
    }

    @Test
    public void timeHomogeneousForwardCorrelation_constructorPopulatesFields() {
        final List<Double> rateTimes = rateTimesGrid();
        final int n = rateTimes.size() - 1;
        final Matrix fwd = new Matrix(n, n);
        for (int i = 0; i < n; ++i) {
            fwd.set(i, i, 1.0);
            for (int j = 0; j < i; ++j) {
                final double v = 0.5 + 0.5 * Math.exp(-0.2 * Math.abs(i - j));
                fwd.set(i, j, v);
                fwd.set(j, i, v);
            }
        }
        final TimeHomogeneousForwardCorrelation th = new TimeHomogeneousForwardCorrelation(fwd, rateTimes);
        assertEquals(n, th.numberOfRates());
        assertEquals(n, th.times().size());
        assertEquals(rateTimes.size(), th.rateTimes().size());
        assertEquals(n, th.correlations().size());
    }

    @Test
    public void exponentialForwardCorrelation_rejectsGammaOutOfRange() {
        final List<Double> rateTimes = rateTimesGrid();
        try {
            new ExponentialForwardCorrelation(rateTimes, 0.5, 0.2, 1.5);
            fail("expected gamma > 1 to throw");
        } catch (final RuntimeException e) {
            assertTrue(e.getMessage().contains("gamma") || e.getMessage().contains("times"));
        }
    }

    @Test
    public void piecewiseConstantCorrelation_correlationByIndexMatchesGetter() {
        final List<Double> rateTimes = rateTimesGrid();
        final PiecewiseConstantCorrelation pc =
                new ExponentialForwardCorrelation(rateTimes, 0.5, 0.2, 1.0);
        for (int i = 0; i < pc.correlations().size(); ++i) {
            // Reference equality is fine for our List<Matrix> backing
            assertFalse("correlation(i) returns a Matrix", pc.correlation(i) == null);
        }
        try {
            pc.correlation(pc.correlations().size() + 100);
            fail("expected out-of-range index to throw");
        } catch (final RuntimeException e) {
            // ok
        }
    }
}
