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

/**
 * Analytic computation of the derivative of the LMM one-step rate-update
 * map with respect to a set of pseudo-root bumps.
 *
 * <p>Implements the Giles-Glasserman page-95 formula directly: for each
 * pseudo-bump {@code pseudoBumps[i]}, returns the chain-rule combination
 * {@code B[i][j] = sum_{k,f} pseudoBumps[i][k][f] * (∂newRate_j / ∂pseudo_{kf})}.
 * Each elementary derivative is built from the working arrays {@code e_} and
 * {@code ratios_}.
 *
 * <p>Pre-condition: {@code aliveIndex == numeraire} (we work in the
 * discretely-compounding money-market measure).
 *
 * <p>Mirrors C++ {@code RatePseudoRootJacobian}
 * (ql/models/marketmodels/pathwisegreeks/ratepseudorootjacobian.{hpp,cpp}
 * v1.42.1).
 *
 * @author Jose Moya
 */
public class RatePseudoRootJacobian {

    private final Matrix pseudoRoot_;
    private final int aliveIndex_;
    private final double[] taus_;
    private final List<Matrix> pseudoBumps_;
    private final double[] displacements_;
    private final int numberBumps_;
    private final int factors_;

    // workspace
    private final List<Matrix> allDerivatives_;
    private final Matrix e_;
    private final double[] ratios_;

    public RatePseudoRootJacobian(final Matrix pseudoRoot,
                                  final int aliveIndex,
                                  final int numeraire,
                                  final double[] taus,
                                  final List<Matrix> pseudoBumps,
                                  final double[] displacements) {
        this.pseudoRoot_ = new Matrix(pseudoRoot);
        this.aliveIndex_ = aliveIndex;
        this.taus_ = taus.clone();
        this.pseudoBumps_ = new ArrayList<>(pseudoBumps.size());
        for (final Matrix m : pseudoBumps) {
            this.pseudoBumps_.add(new Matrix(m));
        }
        this.displacements_ = displacements.clone();
        this.numberBumps_ = pseudoBumps.size();
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

        for (int i = 0; i < pseudoBumps.size(); ++i) {
            QL.require(pseudoBumps.get(i).rows() == numberRates,
                    "pseudoBumps[i].rows()<> taus.size() with i =" + i);
            QL.require(pseudoBumps.get(i).columns() == factors_,
                    "pseudoBumps[i].columns()<> factors with i = " + i);
        }

        this.allDerivatives_ = new ArrayList<>(numberRates);
        for (int i = 0; i < numberRates; ++i) {
            allDerivatives_.add(new Matrix(numberRates, factors_));
        }
    }

    /**
     * Fills the Jacobian matrix B (page 95 of the Giles-Glasserman paper).
     *
     * @param oldRates       rates at the start of the step
     * @param discountRatios one-step discount ratios (P(t,t_j+1)/P(t,t_j))
     * @param newRates       rates at the end of the step
     * @param gaussians      gaussian shocks driving the step
     * @param B              output Jacobian; rows = numberBumps, cols = numberRates
     */
    public void getBumps(final double[] oldRates,
                         final double[] discountRatios,
                         final double[] newRates,
                         final double[] gaussians,
                         final Matrix B) {
        final int numberRates = taus_.length;

        QL.require(B.rows() == numberBumps_,
                "we need B.rows() which is " + B.rows()
                        + " to equal numberBumps_ which is " + numberBumps_);
        QL.require(B.columns() == numberRates,
                "we need B.columns() which is " + B.columns()
                        + " to equal numberRates which is " + numberRates);

        for (int j = aliveIndex_; j < numberRates; ++j) {
            ratios_[j] = (oldRates[j] + displacements_[j]) * discountRatios[j + 1];
        }

        for (int f = 0; f < factors_; ++f) {
            e_.set(aliveIndex_, f, 0.0);
            for (int j = aliveIndex_ + 1; j < numberRates; ++j) {
                e_.set(j, f, e_.get(j - 1, f) + ratios_[j - 1] * pseudoRoot_.get(j - 1, f));
            }
        }

        for (int f = 0; f < factors_; ++f) {
            for (int j = aliveIndex_; j < numberRates; ++j) {
                final Matrix dj = allDerivatives_.get(j);
                for (int k = aliveIndex_; k < j; ++k) {
                    dj.set(k, f, newRates[j] * ratios_[k] * taus_[k] * pseudoRoot_.get(j, f));
                }

                // GG don't seem to have the 2, this term is miniscule in any case
                double tmp = 2 * ratios_[j] * taus_[j] * pseudoRoot_.get(j, f);
                tmp -= pseudoRoot_.get(j, f);
                tmp += e_.get(j, f) * taus_[j];
                tmp += gaussians[f];
                tmp *= (newRates[j] + displacements_[j]);

                dj.set(j, f, tmp);

                for (int k = j + 1; k < numberRates; ++k) {
                    dj.set(k, f, 0.0);
                }
            }
        }

        for (int i = 0; i < numberBumps_; ++i) {
            int j = 0;
            for (; j < aliveIndex_; ++j) {
                B.set(i, j, 0.0);
            }
            for (; j < numberRates; ++j) {
                double sum = 0.0;
                final Matrix bump = pseudoBumps_.get(i);
                final Matrix dj = allDerivatives_.get(j);
                for (int k = aliveIndex_; k < numberRates; ++k) {
                    for (int f = 0; f < factors_; ++f) {
                        sum += bump.get(k, f) * dj.get(k, f);
                    }
                }
                B.set(i, j, sum);
            }
        }
    }
}
