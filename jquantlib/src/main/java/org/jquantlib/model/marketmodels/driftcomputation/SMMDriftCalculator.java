/*
Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.driftcomputation;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.curvestates.CoterminalSwapCurveState;

/**
 * Drift computation for coterminal-swap market models.
 *
 * <p>Java port of {@code ql/models/marketmodels/driftcomputation/smmdriftcalculator.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>See Mark Joshi, Lorenzo Liesch, <i>Effective Implementation Of Generic
 * Market Models</i>.
 */
public class SMMDriftCalculator {

    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final int numeraire_;
    private final int alive_;
    private final double[] displacements_;
    private final double[] oneOverTaus_;
    private final Matrix C_;
    private final Matrix pseudo_;
    private final double[] tmp_;
    // workspace
    private final Matrix wkaj_;        // < W(k) | A(j)/P(n) >
    private final Matrix wkpj_;        // < W(k) | P(j)/P(n) >
    private final Matrix wkajshifted_;

    public SMMDriftCalculator(final Matrix pseudo,
                              final double[] displacements,
                              final double[] taus,
                              final int numeraire,
                              final int alive) {
        this.numberOfRates_ = taus.length;
        this.numberOfFactors_ = pseudo.columns();
        this.numeraire_ = numeraire;
        this.alive_ = alive;
        this.displacements_ = displacements.clone();
        this.oneOverTaus_ = new double[taus.length];
        this.pseudo_ = new Matrix(pseudo);
        this.tmp_ = new double[taus.length];
        // wkaj_ : (numFactors, numRates)         init zero
        this.wkaj_ = new Matrix(pseudo_.columns(), pseudo_.rows());
        // wkpj_ : (numFactors, numRates+1)       init zero
        this.wkpj_ = new Matrix(pseudo_.columns(), pseudo_.rows() + 1);
        // wkajshifted_ : (numFactors, numRates)  init zero
        this.wkajshifted_ = new Matrix(pseudo_.columns(), pseudo_.rows());

        QL.require(numberOfRates_ > 0, "Dim out of range");
        QL.require(displacements.length == numberOfRates_,
                "Displacements out of range");
        QL.require(pseudo.rows() == numberOfRates_,
                "pseudo.rows() not consistent with dim");
        QL.require(pseudo.columns() > 0 && pseudo.columns() <= numberOfRates_,
                "pseudo.rows() not consistent with pseudo.columns()");
        QL.require(alive < numberOfRates_, "Alive out of bounds");
        QL.require(numeraire_ <= numberOfRates_, "Numeraire larger than dim");
        QL.require(numeraire_ >= alive, "Numeraire smaller than alive");

        for (int i = 0; i < taus.length; ++i) {
            oneOverTaus_[i] = 1.0 / taus[i];
        }
        this.C_ = pseudo_.mul(pseudo_.transpose());
    }

    /** Computes the drifts using the supplied coterminal-swap curve state. */
    public void compute(final CoterminalSwapCurveState cs, final double[] drifts) {
        // Compute drifts with factor reduction, using pseudo square root.
        final double[] SR = cs.coterminalSwapRates();
        final double[] taus = cs.rateTaus();
        final double[] annuities = new double[numberOfRates_];
        for (int j = 0; j < numberOfRates_; ++j) {
            annuities[j] = cs.coterminalSwapAnnuity(numberOfRates_, j);
        }

        for (int k = 0; k < numberOfFactors_; ++k) {
            for (int j = numberOfRates_ - 2; j >= alive_ - 1; --j) {
                final double annuity = annuities[j + 1];
                final double pseudoJp1K = pseudo_.get(j + 1, k);
                final double wkpj = SR[j + 1] * (pseudoJp1K * annuity + wkaj_.get(k, j + 1))
                        + pseudoJp1K * displacements_[j + 1] * annuity;
                wkpj_.set(k, j + 1, wkpj);
                if (j >= alive_) {
                    final double w = wkpj * taus[j] + wkaj_.get(k, j + 1);
                    wkaj_.set(k, j, w);
                }
            }
        }

        final double numeraireRatio = cs.discountRatio(numberOfRates_, numeraire_);

        for (int k = 0; k < numberOfFactors_; ++k) {
            for (int j = alive_; j < numberOfRates_; ++j) {
                final double v = -wkaj_.get(k, j) / annuities[j]
                        + wkpj_.get(k, numeraire_) * numeraireRatio;
                wkajshifted_.set(k, j, v);
            }
        }

        // eq 5.3 (in log coordinates)
        for (int j = alive_; j < numberOfRates_; ++j) {
            double d = 0.0;
            for (int k = 0; k < numberOfFactors_; ++k) {
                d += wkajshifted_.get(k, j) * pseudo_.get(j, k);
            }
            drifts[j] = d;
        }
    }
}
