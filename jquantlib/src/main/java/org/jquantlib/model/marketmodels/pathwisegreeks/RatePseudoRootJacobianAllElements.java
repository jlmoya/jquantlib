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

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Analytic Jacobian for all pseudo-root elements: returns one matrix per
 * rate, whose entries are the derivatives of that rate with respect to each
 * pseudo-root element.
 *
 * <p>Mirrors C++ {@code RatePseudoRootJacobianAllElements}
 * (ql/models/marketmodels/pathwisegreeks/ratepseudorootjacobian.{hpp,cpp}
 * v1.42.1).
 *
 * @author Jose Moya
 */
public class RatePseudoRootJacobianAllElements {

    private final Matrix pseudoRoot_;
    private final int aliveIndex_;
    private final double[] taus_;
    private final double[] displacements_;
    private final int factors_;

    // workspace
    private final Matrix e_;
    private final double[] ratios_;

    public RatePseudoRootJacobianAllElements(final Matrix pseudoRoot,
                                             final int aliveIndex,
                                             final int numeraire,
                                             final double[] taus,
                                             final double[] displacements) {
        this.pseudoRoot_ = new Matrix(pseudoRoot);
        this.aliveIndex_ = aliveIndex;
        this.taus_ = taus.clone();
        this.displacements_ = displacements.clone();
        this.factors_ = pseudoRoot.columns();
        this.e_ = new Matrix(pseudoRoot.rows(), pseudoRoot.columns());
        this.ratios_ = new double[taus.length];

        final int numberRates = taus.length;
        QL.require(aliveIndex == numeraire,
                "we can do only do discretely compounding MM acount so aliveIndex must equal numeraire");
        QL.require(pseudoRoot_.rows() == numberRates,
                "pseudoRoot_.rows()<> taus.size()");
        QL.require(displacements_.length == numberRates,
                "displacements_.size()<> taus.size()");
    }

    /**
     * Fills B (a list of matrices, one per rate). For rate {@code j},
     * {@code B[j][k][f]} is the derivative of {@code newRate_j} with respect
     * to {@code pseudoRoot[k][f]}.
     */
    public void getBumps(final double[] oldRates,
                         final double[] discountRatios,
                         final double[] newRates,
                         final double[] gaussians,
                         final List<Matrix> B) {
        final int numberRates = taus_.length;

        QL.require(B.size() == numberRates,
                "we need B.size() which is " + B.size()
                        + " to equal numberRates which is " + numberRates);
        for (final Matrix bj : B) {
            QL.require(bj.columns() == factors_ && bj.rows() == numberRates,
                    "we need B[j].rows() to equal numberRates and B[j].columns() to equal factors");
        }

        for (int j = aliveIndex_; j < numberRates; ++j) {
            ratios_[j] = (oldRates[j] + displacements_[j]) * discountRatios[j + 1];
        }

        for (int f = 0; f < factors_; ++f) {
            e_.set(aliveIndex_, f, 0.0);
            for (int j = aliveIndex_ + 1; j < numberRates; ++j) {
                e_.set(j, f, e_.get(j - 1, f) + ratios_[j - 1] * pseudoRoot_.get(j - 1, f));
            }
        }

        // nullify B for rates that have already reset
        for (int j = 0; j < aliveIndex_; ++j) {
            final Matrix bj = B.get(j);
            for (int k = 0; k < numberRates; ++k) {
                for (int f = 0; f < factors_; ++f) {
                    bj.set(k, f, 0.0);
                }
            }
        }

        for (int f = 0; f < factors_; ++f) {
            for (int j = aliveIndex_; j < numberRates; ++j) {
                final Matrix bj = B.get(j);
                for (int k = aliveIndex_; k < j; ++k) {
                    bj.set(k, f, newRates[j] * ratios_[k] * taus_[k] * pseudoRoot_.get(j, f));
                }

                double tmp = 2 * ratios_[j] * taus_[j] * pseudoRoot_.get(j, f);
                tmp -= pseudoRoot_.get(j, f);
                tmp += e_.get(j, f) * taus_[j];
                tmp += gaussians[f];
                tmp *= (newRates[j] + displacements_[j]);

                bj.set(j, f, tmp);

                for (int k = 0; k < aliveIndex_; ++k) {
                    bj.set(k, f, 0.0);
                }
                for (int k = j + 1; k < numberRates; ++k) {
                    bj.set(k, f, 0.0);
                }
            }
        }
    }
}
