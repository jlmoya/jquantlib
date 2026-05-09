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
 Copyright (C) 2006 Marco Bianchetti
 Copyright (C) 2006 Silvia Frasson
 Copyright (C) 2006 Mario Pucci
 Copyright (C) 2006 StatPro Italia srl
 Copyright (C) 2006, 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.driftcomputation;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;

/**
 * Drift computation for log-normal %Libor market models.
 *
 * <p>Java port of {@code ql/models/marketmodels/driftcomputation/lmmdriftcalculator.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Returns the drift {@code mu * dt}. See Mark Joshi, <i>Rapid Computation
 * of Drifts in a Reduced Factor Libor Market Model</i>, Wilmott Magazine,
 * May 2003.
 */
public class LMMDriftCalculator {

    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final boolean isFullFactor_;
    private final int numeraire_;
    private final int alive_;
    private final double[] displacements_;
    private final double[] oneOverTaus_;
    private final Matrix C_;
    private final Matrix pseudo_;
    // workspace
    private final double[] tmp_;
    private final Matrix e_;
    private final int[] downs_;
    private final int[] ups_;

    public LMMDriftCalculator(final Matrix pseudo,
                              final double[] displacements,
                              final double[] taus,
                              final int numeraire,
                              final int alive) {
        this.numberOfRates_ = taus.length;
        this.numberOfFactors_ = pseudo.columns();
        this.isFullFactor_ = (numberOfFactors_ == numberOfRates_);
        this.numeraire_ = numeraire;
        this.alive_ = alive;
        this.displacements_ = displacements.clone();
        this.oneOverTaus_ = new double[taus.length];
        this.pseudo_ = new Matrix(pseudo);
        this.tmp_ = new double[taus.length];
        this.e_ = new Matrix(pseudo_.columns(), pseudo_.rows());
        this.downs_ = new int[taus.length];
        this.ups_ = new int[taus.length];

        // Check requirements
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

        // Precompute 1/taus
        for (int i = 0; i < taus.length; ++i) {
            oneOverTaus_[i] = 1.0 / taus[i];
        }

        // Compute covariance matrix C = pseudo * pseudo^T
        this.C_ = pseudo_.mul(pseudo_.transpose());

        // Compute lower and upper extrema
        for (int i = alive_; i < numberOfRates_; ++i) {
            downs_[i] = Math.min(i + 1, numeraire_);
            ups_[i] = Math.max(i + 1, numeraire_);
        }
    }

    /** Computes the drifts (auto-selects plain vs reduced based on factor count). */
    public void compute(final LMMCurveState cs, final double[] drifts) {
        compute(cs.forwardRates(), drifts);
    }

    public void compute(final double[] fwds, final double[] drifts) {
        if (isFullFactor_) {
            computePlain(fwds, drifts);
        } else {
            computeReduced(fwds, drifts);
        }
    }

    public void computePlain(final LMMCurveState cs, final double[] drifts) {
        computePlain(cs.forwardRates(), drifts);
    }

    /** Plain drift — uses covariance matrix directly (eq 2,4 of Joshi 2003). */
    public void computePlain(final double[] forwards, final double[] drifts) {
        // Precompute forwards factor
        for (int i = alive_; i < numberOfRates_; ++i) {
            tmp_[i] = (forwards[i] + displacements_[i]) / (oneOverTaus_[i] + forwards[i]);
        }
        // Compute drifts
        for (int i = alive_; i < numberOfRates_; ++i) {
            double sum = 0.0;
            for (int k = downs_[i]; k < ups_[i]; ++k) {
                sum += tmp_[k] * C_.get(i, k);
            }
            drifts[i] = (numeraire_ > i + 1) ? -sum : sum;
        }
    }

    public void computeReduced(final LMMCurveState cs, final double[] drifts) {
        computeReduced(cs.forwardRates(), drifts);
    }

    /**
     * Reduced drift — uses pseudo square root (eq 7 of Joshi 2003).
     */
    public void computeReduced(final double[] forwards, final double[] drifts) {
        // Precompute forwards factor
        for (int i = alive_; i < numberOfRates_; ++i) {
            tmp_[i] = (forwards[i] + displacements_[i]) / (oneOverTaus_[i] + forwards[i]);
        }

        // Enforce initialization: e_[r][max(0, numeraire_-1)] = 0
        final int initCol = Math.max(0, numeraire_ - 1);
        for (int r = 0; r < numberOfFactors_; ++r) {
            e_.set(r, initCol, 0.0);
        }

        // 1st step: drift at index numeraire_-1 is zero
        if (numeraire_ > 0) {
            drifts[numeraire_ - 1] = 0.0;
        }

        // 2nd step: backward from N-2 to alive (inclusive)
        for (int i = numeraire_ - 2; i >= alive_; --i) {
            drifts[i] = 0.0;
            for (int r = 0; r < numberOfFactors_; ++r) {
                final double e_next = e_.get(r, i + 1);
                final double pseudoNextR = pseudo_.get(i + 1, r);
                final double e_i = e_next + tmp_[i + 1] * pseudoNextR;
                e_.set(r, i, e_i);
                drifts[i] -= e_i * pseudo_.get(i, r);
            }
        }

        // 3rd step: forward from N to n-1
        for (int i = numeraire_; i < numberOfRates_; ++i) {
            drifts[i] = 0.0;
            for (int r = 0; r < numberOfFactors_; ++r) {
                final double pseudoIR = pseudo_.get(i, r);
                final double e_i;
                if (i == 0) {
                    e_i = tmp_[i] * pseudoIR;
                } else {
                    e_i = e_.get(r, i - 1) + tmp_[i] * pseudoIR;
                }
                e_.set(r, i, e_i);
                drifts[i] += e_i * pseudoIR;
            }
        }
    }
}
