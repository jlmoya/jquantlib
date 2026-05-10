/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.testsuite.math;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.CovarianceDecomposition;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/covariance.cpp (Phase 5a).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods. {@code testRankReduction} requires
 * {@code rankReducedSqrt} (not in JQuantLib — Phase 5a.5 carry-forward).
 * {@code testSalvagingMatrix} and {@code testCovariance} are portable.
 */
public class CovarianceTest {

    public CovarianceTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static double norm(final Matrix m) {
        double sum = 0.0;
        for (int i = 0; i < m.rows(); i++) {
            for (int j = 0; j < m.columns(); j++) {
                sum += m.get(i, j) * m.get(i, j);
            }
        }
        return Math.sqrt(sum);
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no rankReducedSqrt() helper "
            + "(C++ ql/math/matrixutilities/pseudosqrt.hpp). Port then enable.")
    @Test
    public void testRankReduction() {
    }

    @Test
    public void testSalvagingMatrix() {
        QL.info("Testing positive semi-definiteness salvaging algorithms...");

        final int n = 3;

        final Matrix badCorr = new Matrix(n, n);
        badCorr.set(0, 0, 1.0); badCorr.set(0, 1, 0.9); badCorr.set(0, 2, 0.7);
        badCorr.set(1, 0, 0.9); badCorr.set(1, 1, 1.0); badCorr.set(1, 2, 0.3);
        badCorr.set(2, 0, 0.7); badCorr.set(2, 1, 0.3); badCorr.set(2, 2, 1.0);

        final Matrix goodCorr = new Matrix(n, n);
        goodCorr.set(0, 0, 1.00000000000); goodCorr.set(1, 1, 1.00000000000);
        goodCorr.set(2, 2, 1.00000000000);
        goodCorr.set(0, 1, 0.894024408508599); goodCorr.set(1, 0, 0.894024408508599);
        goodCorr.set(0, 2, 0.696319066114392); goodCorr.set(2, 0, 0.696319066114392);
        goodCorr.set(1, 2, 0.300969036104592); goodCorr.set(2, 1, 0.300969036104592);

        Matrix b = PseudoSqrt.pseudoSqrt(badCorr, SalvagingAlgorithm.Spectral);
        Matrix calcCorr = b.mul(b.transpose());

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                final double expected = goodCorr.get(i, j);
                final double calculated = calcCorr.get(i, j);
                if (Math.abs(calculated - expected) > 1.0e-10) {
                    fail("SalvagingCorrelation with spectral alg "
                            + "cor[" + i + "][" + j + "]:\n"
                            + "    calculated: " + calculated
                            + "\n    expected:   " + expected);
                }
            }
        }

        final Matrix badCov = new Matrix(n, n);
        badCov.set(0, 0, 0.04000); badCov.set(0, 1, 0.03240); badCov.set(0, 2, 0.02240);
        badCov.set(1, 0, 0.03240); badCov.set(1, 1, 0.03240); badCov.set(1, 2, 0.00864);
        badCov.set(2, 0, 0.02240); badCov.set(2, 1, 0.00864); badCov.set(2, 2, 0.02560);

        b = PseudoSqrt.pseudoSqrt(badCov, SalvagingAlgorithm.Spectral);
        final Matrix goodCov = b.mul(b.transpose());

        final double error = norm(goodCov.sub(badCov));
        if (error > 4.0e-4) {
            fail(error + " error while salvaging covariance matrix with spectral alg\n"
                    + "input matrix:\n" + badCov
                    + "salvaged matrix:\n" + goodCov);
        }
    }

    @Test
    public void testCovariance() {
        QL.info("Testing covariance and correlation calculations...");

        final double[][] data = {
            { 3.0,  9.0 },
            { 2.0,  7.0 },
            { 4.0, 12.0 },
            { 5.0, 15.0 },
            { 6.0, 17.0 }
        };
        final int n = data[0].length;

        final Matrix expCor = new Matrix(n, n);
        expCor.set(0, 0, 1.0); expCor.set(0, 1, 0.9970544855015813);
        expCor.set(1, 0, 0.9970544855015813); expCor.set(1, 1, 1.0);

        final SequenceStatistics s = new SequenceStatistics(n);
        final double[] temp = new double[n];

        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < n; j++) {
                temp[j] = data[i][j];
            }
            s.add(temp);
        }

        final Array stdDev = s.standardDeviation();
        final Matrix calcCov = s.covariance();
        final Matrix calcCor = s.correlation();

        final Matrix expCov = new Matrix(n, n);
        for (int i = 0; i < n; i++) {
            expCov.set(i, i, stdDev.get(i) * stdDev.get(i));
            for (int j = 0; j < i; j++) {
                final double v = expCor.get(i, j) * stdDev.get(i) * stdDev.get(j);
                expCov.set(i, j, v);
                expCov.set(j, i, v);
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double expected = expCor.get(i, j);
                double calculated = calcCor.get(i, j);
                if (Math.abs(calculated - expected) > 1.0e-10) {
                    fail("SequenceStatistics cor[" + i + "][" + j + "]:\n"
                            + "    calculated: " + calculated
                            + "\n    expected:   " + expected);
                }
                expected = expCov.get(i, j);
                calculated = calcCov.get(i, j);
                if (Math.abs(calculated - expected) > 1.0e-10) {
                    fail("SequenceStatistics cov[" + i + "][" + j + "]:\n"
                            + "    calculated: " + calculated
                            + "\n    expected:   " + expected);
                }
            }
        }

        // CovarianceDecomposition recovers std + correlation matrix from a covariance matrix.
        final CovarianceDecomposition covDecomposition = new CovarianceDecomposition(expCov);
        final Matrix recCor = covDecomposition.correlationMatrix();
        final double[] recStd = covDecomposition.standardDeviations();

        for (int i = 0; i < n; i++) {
            if (Math.abs(recStd[i] - stdDev.get(i)) > 1.0e-16) {
                fail("CovarianceDecomposition standardDev[" + i + "]:\n"
                        + "    calculated: " + recStd[i]
                        + "\n    expected:   " + stdDev.get(i));
            }
            for (int j = 0; j < n; j++) {
                if (Math.abs(recCor.get(i, j) - expCor.get(i, j)) > 1.0e-14) {
                    fail("\nCovarianceDecomposition corr[" + i + "][" + j + "]:\n"
                            + "    calculated: " + recCor.get(i, j)
                            + "\n    expected:   " + expCor.get(i, j));
                }
            }
        }

        // C++ also tests getCovariance(std.begin(), std.end(), expCor) — Java
        // has no free-function getCovariance helper. Lower-level inverse of
        // CovarianceDecomposition is exercised above via reconstructed std/cor.
    }
}
