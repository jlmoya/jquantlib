/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.7.

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
 Copyright (C) 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels.pathwisegreeks;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.driftcomputation.LMMDriftCalculator;

/**
 * Numerical (finite-difference) computation of the derivative of the LMM
 * one-step rate-update map with respect to a set of pseudo-root bumps.
 *
 * <p>For each pseudo-bump {@code pseudoBumps[i]}, computes the bumped rates
 * {@code bumpedRates_j} via the same log-Euler step as the unbumped evolver,
 * then sets {@code B[i][j] = bumpedRates_j - newRates_j}. Used as the
 * cross-validation reference for the analytic
 * {@link RatePseudoRootJacobian}.
 *
 * <p>Mirrors C++ {@code RatePseudoRootJacobianNumerical}
 * (ql/models/marketmodels/pathwisegreeks/ratepseudorootjacobian.{hpp,cpp}
 * v1.42.1). Tested in {@code testPathwiseVegas} against the analytic version.
 *
 * @author Jose Moya
 */
public class RatePseudoRootJacobianNumerical {

    private final Matrix pseudoRoot_;
    private final int aliveIndex_;
    private final double[] taus_;
    private final List<Matrix> pseudoBumped_;
    private final double[] displacements_;
    private final int numberBumps_;
    private final List<LMMDriftCalculator> driftsComputers_;
    private final int factors_;

    // workspace
    private final double[] drifts_;
    private final double[] bumpedRates_;

    public RatePseudoRootJacobianNumerical(final Matrix pseudoRoot,
                                           final int aliveIndex,
                                           final int numeraire,
                                           final double[] taus,
                                           final List<Matrix> pseudoBumps,
                                           final double[] displacements) {
        this.pseudoRoot_ = new Matrix(pseudoRoot);
        this.aliveIndex_ = aliveIndex;
        this.taus_ = taus.clone();
        this.displacements_ = displacements.clone();
        this.numberBumps_ = pseudoBumps.size();
        this.factors_ = pseudoRoot.columns();
        this.drifts_ = new double[taus.length];
        this.bumpedRates_ = new double[taus.length];
        this.pseudoBumped_ = new ArrayList<>(numberBumps_);
        this.driftsComputers_ = new ArrayList<>(numberBumps_);

        final int numberRates = taus.length;
        QL.require(pseudoRoot_.rows() == numberRates,
                "pseudoRoot_.rows()<> taus.size()");
        QL.require(displacements_.length == numberRates,
                "displacements_.size()<> taus.size()");
        QL.require(drifts_.length == numberRates,
                "drifts_.size()<> taus.size()");

        for (int i = 0; i < numberBumps_; ++i) {
            final Matrix bump = pseudoBumps.get(i);
            QL.require(bump.rows() == numberRates,
                    "pseudoBumps[i].rows()<> taus.size() with i =" + i);
            QL.require(bump.columns() == factors_,
                    "pseudoBumps[i].columns()<> factors with i = " + i);

            // pseudo = pseudoRoot + bump
            final Matrix pseudo = new Matrix(pseudoRoot_);
            for (int r = 0; r < numberRates; ++r) {
                for (int f = 0; f < factors_; ++f) {
                    pseudo.set(r, f, pseudo.get(r, f) + bump.get(r, f));
                }
            }
            pseudoBumped_.add(pseudo);
            driftsComputers_.add(new LMMDriftCalculator(
                    pseudo, displacements, taus, numeraire, aliveIndex));
        }
    }

    /**
     * Fills the Jacobian matrix B (page 95 of the Giles-Glasserman paper).
     *
     * @param oldRates    rates at the start of the step
     * @param oneStepDFs  not used in the numerical implementation (preserved
     *                    for API parity)
     * @param newRates    rates at the end of the step (un-bumped)
     * @param gaussians   gaussian shocks driving the step
     * @param B           output Jacobian; rows = numberBumps, cols = numberRates
     */
    public void getBumps(final double[] oldRates,
                         final double[] oneStepDFs,
                         final double[] newRates,
                         final double[] gaussians,
                         final Matrix B) {
        final int numberRates = taus_.length;

        QL.require(B.rows() == numberBumps_,
                "B.rows()<> numberBumps_");
        QL.require(B.columns() == taus_.length,
                "B.columns()<> number of rates");

        for (int i = 0; i < numberBumps_; ++i) {
            final Matrix pseudo = pseudoBumped_.get(i);
            driftsComputers_.get(i).compute(oldRates, drifts_);

            for (int j = 0; j < aliveIndex_; ++j) {
                B.set(i, j, 0.0);
            }

            for (int j = aliveIndex_; j < numberRates; ++j) {
                bumpedRates_[j] = Math.log(oldRates[j] + displacements_[j]);

                for (int k = 0; k < factors_; ++k) {
                    bumpedRates_[j] += -0.5 * pseudo.get(j, k) * pseudo.get(j, k);
                }

                bumpedRates_[j] += drifts_[j];

                for (int k = 0; k < factors_; ++k) {
                    bumpedRates_[j] += pseudo.get(j, k) * gaussians[k];
                }

                bumpedRates_[j] = Math.exp(bumpedRates_[j]);
                bumpedRates_[j] -= displacements_[j];
                final double tmp = bumpedRates_[j] - newRates[j];

                B.set(i, j, tmp);
            }
        }
    }
}
