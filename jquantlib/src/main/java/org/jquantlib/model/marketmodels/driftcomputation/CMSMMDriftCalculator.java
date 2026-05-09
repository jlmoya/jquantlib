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
 Copyright (C) 2007 François du Vignaud
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.driftcomputation;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.curvestates.CMSwapCurveState;

/**
 * Drift computation for constant-maturity-swap market models.
 *
 * <p>Java port of {@code ql/models/marketmodels/driftcomputation/cmsmmdriftcalculator.{hpp,cpp}}
 * (QuantLib v1.42.1).
 */
public class CMSMMDriftCalculator {

    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final int numeraire_;
    private final int alive_;
    private final int spanningFwds_;
    private final double[] displacements_;
    private final double[] oneOverTaus_;
    private final Matrix C_;
    private final Matrix pseudo_;
    private final double[] tmp_;
    // workspace
    private final Matrix PjPnWk_;
    private final Matrix wkaj_;
    private final Matrix wkajN_;
    @SuppressWarnings("unused")
    private final int[] downs_;
    @SuppressWarnings("unused")
    private final int[] ups_;

    public CMSMMDriftCalculator(final Matrix pseudo,
                                final double[] displacements,
                                final double[] taus,
                                final int numeraire,
                                final int alive,
                                final int spanningFwds) {
        this.numberOfRates_ = taus.length;
        this.numberOfFactors_ = pseudo.columns();
        this.numeraire_ = numeraire;
        this.alive_ = alive;
        this.spanningFwds_ = spanningFwds;
        this.displacements_ = displacements.clone();
        this.oneOverTaus_ = new double[taus.length];
        this.pseudo_ = new Matrix(pseudo);
        this.tmp_ = new double[taus.length];
        this.PjPnWk_ = new Matrix(numberOfFactors_, 1 + taus.length);
        this.wkaj_ = new Matrix(numberOfFactors_, taus.length);
        this.wkajN_ = new Matrix(numberOfFactors_, taus.length);
        this.downs_ = new int[taus.length];
        this.ups_ = new int[taus.length];

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

        for (int i = alive_; i < numberOfRates_; ++i) {
            downs_[i] = Math.min(i + 1, numeraire_);
            ups_[i] = Math.max(i + 1, numeraire_);
        }
    }

    public void compute(final CMSwapCurveState cs, final double[] drifts) {
        final double[] taus = cs.rateTaus();

        // Compute cross variations
        for (int k = 0; k < PjPnWk_.rows(); ++k) {
            PjPnWk_.set(k, numberOfRates_, 0.0);
            wkaj_.set(k, numberOfRates_ - 1, 0.0);

            for (int j = numberOfRates_ - 2; j >= alive_ - 1; --j) {
                final double sr = cs.cmSwapRate(j + 1, spanningFwds_);
                final int endIndex = Math.min(j + spanningFwds_ + 1, numberOfRates_);
                final double first = sr * wkaj_.get(k, j + 1);
                final double second = cs.cmSwapAnnuity(numberOfRates_, j + 1, spanningFwds_)
                        * (sr + displacements_[j + 1])
                        * pseudo_.get(j + 1, k);
                final double third = PjPnWk_.get(k, endIndex);
                PjPnWk_.set(k, j + 1, first + second + third);

                if (j >= alive_) {
                    double w = wkaj_.get(k, j + 1) + PjPnWk_.get(k, j + 1) * taus[j];
                    if (j + spanningFwds_ + 1 <= numberOfRates_) {
                        w -= PjPnWk_.get(k, endIndex) * taus[endIndex - 1];
                    }
                    wkaj_.set(k, j, w);
                }
            }
        }

        final double PnOverPN = cs.discountRatio(numberOfRates_, numeraire_);

        for (int j = alive_; j < numberOfRates_; ++j) {
            for (int k = 0; k < numberOfFactors_; ++k) {
                final double v = wkaj_.get(k, j) * PnOverPN
                        - PjPnWk_.get(k, numeraire_) * PnOverPN
                                * cs.cmSwapAnnuity(numeraire_, j, spanningFwds_);
                wkajN_.set(k, j, v);
            }
        }

        for (int j = alive_; j < numberOfRates_; ++j) {
            double d = 0.0;
            for (int k = 0; k < numberOfFactors_; ++k) {
                d += pseudo_.get(j, k) * wkajN_.get(k, j);
            }
            d /= -cs.cmSwapAnnuity(numeraire_, j, spanningFwds_);
            drifts[j] = d;
        }
    }
}
