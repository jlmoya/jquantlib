/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 1 closure A3-D-548-lsr.

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

/*
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.methods.montecarlo;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SVD;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.jquantlib.math.statistics.Statistics;

/**
 * Generic Longstaff-Schwartz regression for Monte-Carlo callable / Bermudan
 * pricing.
 *
 * <p>Faithful Java port of C++ free function
 * {@code QuantLib::genericLongstaffSchwartzRegression}
 * (ql/methods/montecarlo/genericlsregression.{hpp,cpp} v1.42.1).
 *
 * <p>Given {@code n} exercise dates, {@code simulationData} has {@code n+1}
 * rows. Row {@code 0} carries the cumulated cash-flows up to the first
 * exercise; rows {@code i+1} for {@code i = 0..n-1} hold the per-path
 * {@link NodeData} at the {@code i}-th exercise date. The routine walks
 * backwards through the exercise dates, regressing the deflated cash-flows
 * (less the control-variate value) against the basis functions via least
 * squares (SVD), then divides the paths into exercise / continuation by
 * comparing the estimated continuation value with the deflated exercise
 * value. {@code basisCoefficients} is filled with the per-exercise
 * regression coefficients and the routine returns the biased
 * Longstaff-Schwartz estimate, i.e. the path-average cumulated cash-flow
 * at time zero.
 *
 * @author Jose Moya
 * @see "ql/methods/montecarlo/genericlsregression.cpp" v1.42.1
 */
public final class GenericLongstaffSchwartzRegression {

    private GenericLongstaffSchwartzRegression() {
        // utility class - no instantiation
    }

    /**
     * Run the backward-induction regression and return the biased
     * Longstaff-Schwartz estimate of the option value.
     *
     * @param simulationData [{@code n+1}] rows × paths; row 0 are pre-first-exercise
     *                       data, rows {@code 1..n} are the exercise-date data.
     *                       Mutated: rows {@code 0..n-1} get the post-exercise
     *                       cumulatedCashFlows added in-place.
     * @param basisCoefficients output; on return has {@code n} rows, row {@code i}
     *                          carries the regression coefficients for the
     *                          {@code (i+1)}-th exercise opportunity.
     * @return the path-average of the cumulated cash-flows on the time-0 slice
     */
    public static double evaluate(final NodeData[][] simulationData,
                                  final double[][] basisCoefficients) {

        final int steps = simulationData.length;
        // basisCoefficients sized steps-1 (one set per exercise opportunity)
        // resize emulates std::vector<>::resize: caller passes an outer array
        // we treat basisCoefficients as already steps-1 in length; the caller
        // arranges that.  If shorter, we throw; we cannot grow a Java array.
        if (basisCoefficients.length != steps - 1) {
            throw new IllegalArgumentException(
                "basisCoefficients length " + basisCoefficients.length
                + " != steps-1=" + (steps - 1));
        }

        for (int i = steps - 1; i != 0; --i) {

            final NodeData[] exerciseData = simulationData[i];

            // 1) find the covariance matrix of basis function values and
            //    deflated cash-flows
            final int N = exerciseData[0].values.length;
            final double[] temp = new double[N + 1];
            final SequenceStatistics stats = new SequenceStatistics(N + 1);

            int j;
            for (j = 0; j < exerciseData.length; ++j) {
                if (exerciseData[j].isValid) {
                    System.arraycopy(exerciseData[j].values, 0, temp, 0, N);
                    temp[N] = exerciseData[j].cumulatedCashFlows
                            - exerciseData[j].controlValue;

                    stats.add(temp);
                }
            }

            final Array means = stats.mean();
            final Matrix covariance = stats.covariance();

            final Matrix C = new Matrix(N, N);
            final Array target = new Array(N);
            for (int k = 0; k < N; ++k) {
                target.set(k, covariance.get(k, N) + means.get(k) * means.get(N));
                for (int l = 0; l <= k; ++l) {
                    final double v = covariance.get(k, l) + means.get(k) * means.get(l);
                    C.set(k, l, v);
                    C.set(l, k, v);
                }
            }

            // 2) solve for least squares regression
            final Array alphas = new SVD(C).solveFor(target);
            final double[] alphasCopy = new double[N];
            for (int k = 0; k < N; ++k) {
                alphasCopy[k] = alphas.get(k);
            }
            basisCoefficients[i - 1] = alphasCopy;

            // 3) use exercise strategy to divide paths into exercise and
            //    non-exercise domains
            for (j = 0; j < exerciseData.length; ++j) {
                if (exerciseData[j].isValid) {
                    final double exerciseValue = exerciseData[j].exerciseValue;
                    final double continuationValue =
                            exerciseData[j].cumulatedCashFlows;
                    double estimatedContinuationValue =
                            exerciseData[j].controlValue;
                    for (int k = 0; k < N; ++k) {
                        estimatedContinuationValue +=
                                exerciseData[j].values[k] * alphas.get(k);
                    }

                    // for exercise paths, add deflated rebate to
                    // deflated cash-flows at previous time frame;
                    // for non-exercise paths, add deflated cash-flows to
                    // deflated cash-flows at previous time frame
                    final double value =
                            estimatedContinuationValue <= exerciseValue
                                    ? exerciseValue : continuationValue;

                    simulationData[i - 1][j].cumulatedCashFlows += value;
                }
            }
        }

        // the value of the product can now be estimated by averaging
        // over all paths
        final Statistics estimate = new Statistics();
        final NodeData[] estimatedData = simulationData[0];
        for (final NodeData nd : estimatedData) {
            estimate.add(nd.cumulatedCashFlows);
        }

        return estimate.mean();
    }
}
