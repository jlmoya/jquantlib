/*
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2009, 2014 Jose Aparicio
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.experimental.credit;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.time.Date;

/**
 * Default loss-distribution convolution for a finite homogeneous pool.
 *
 * <p>Java port of QuantLib v1.42.1 template
 * {@code template <class copulaPolicy> class HomogeneousPoolLossModel}
 * (declared in {@code ql/experimental/credit/homogeneouspooldef.hpp}).
 *
 * <p>Builds the loss distribution via {@link LossDistHomogeneous} convolution
 * inside a 1D trapezoid integration over the systemic factor.
 *
 * <p>The C++ note about bucket count (use up to attainable losses for
 * constant LGD) is left as-is — the bucket grid is set by the caller.
 *
 * @param <P> the {@link CopulaPolicy} bound through the underlying
 *            {@link ConstantLossLatentModel}
 */
public class HomogeneousPoolLossModel<P extends CopulaPolicy> extends DefaultLossModel {

    private final ConstantLossLatentModel<P> copula_;
    private final int nBuckets_;
    private final double max_;
    private final double min_;
    private final int nSteps_;
    private final double delta_;

    /** Cached basket attach/detach amounts (set in {@link #resetModel()}). */
    private double attach_;
    private double detach_;
    private double notional_;
    private double attachAmount_;
    private double detachAmount_;
    private List<Double> notionals_;

    public HomogeneousPoolLossModel(final ConstantLossLatentModel<P> copula,
                                    final int nBuckets) {
        this(copula, nBuckets, 5.0, -5.0, 50);
    }

    public HomogeneousPoolLossModel(final ConstantLossLatentModel<P> copula,
                                    final int nBuckets,
                                    final double max,
                                    final double min,
                                    final int nSteps) {
        QL.require(copula.numFactors() == 1,
                "Inhomogeneous model not implemented for multifactor");
        this.copula_ = copula;
        this.nBuckets_ = nBuckets;
        this.max_ = max;
        this.min_ = min;
        this.nSteps_ = nSteps;
        this.delta_ = (max - min) / nSteps;
    }

    @Override
    protected void resetModel() {
        if (basket == null) {
            return;
        }
        attach_ = Math.min(basket.remainingAttachmentAmount() / basket.remainingNotional(), 1.0);
        detach_ = Math.min(basket.remainingDetachmentAmount() / basket.remainingNotional(), 1.0);
        notional_ = basket.remainingNotional();
        notionals_ = basket.remainingNotionals();
        attachAmount_ = basket.remainingAttachmentAmount();
        detachAmount_ = basket.remainingDetachmentAmount();
        copula_.resetBasket(basket);
    }

    /** Build the loss distribution at the given date. */
    public Distribution lossDistrib(final Date d) {
        QL.require(basket != null, "Basket not set on HomogeneousPoolLossModel");
        final LossDistHomogeneous bucktLDistBuff = new LossDistHomogeneous(nBuckets_, detachAmount_);
        // lgd = (1 - rr) * notional, per name
        final List<Double> recoveries = copula_.recoveries();
        final List<Double> lgd = new ArrayList<>(recoveries.size());
        for (int i = 0; i < recoveries.size(); ++i) {
            lgd.add((1.0 - recoveries.get(i)) * notionals_.get(i));
        }
        final List<Double> probsList = basket.remainingProbabilities(d);
        final double[] prob = new double[probsList.size()];
        for (int i = 0; i < prob.length; ++i) {
            prob[i] = copula_.inverseCumulativeY(probsList.get(i), i);
        }
        final Distribution dist = new Distribution(nBuckets_, 0.0, detachAmount_);
        final double[] mkft = new double[]{min_ + delta_ / 2.0};
        for (int i = 0; i < nSteps_; ++i) {
            final List<Double> conditionalProbs = new ArrayList<>(notionals_.size());
            for (int iName = 0; iName < notionals_.size(); ++iName) {
                conditionalProbs.add(copula_.conditionalDefaultProbabilityInvP(prob[iName], iName, mkft));
            }
            final Distribution bld = bucktLDistBuff.op(lgd, conditionalProbs);
            final double densitydm = delta_ * copula_.density(toList(mkft));
            for (int j = 0; j < nBuckets_; ++j) {
                dist.addDensity(j, bld.density(j) * densitydm);
            }
            mkft[0] += delta_;
        }
        return dist;
    }

    @Override
    public double expectedTrancheLoss(final Date d) {
        return lossDistrib(d).cumulativeExcessProbability(attachAmount_, detachAmount_);
    }

    @Override
    public double percentile(final Date d, final double percentile) {
        final double portfLoss = lossDistrib(d).confidenceLevel(percentile);
        return Math.min(Math.max(portfLoss - attachAmount_, 0.0), detachAmount_ - attachAmount_);
    }

    @Override
    public double expectedShortfall(final Date d, final double percentile) {
        final Distribution dist = lossDistrib(d);
        dist.tranche(attachAmount_, detachAmount_);
        return dist.expectedShortfall(percentile);
    }

    private static List<Double> toList(final double[] a) {
        final List<Double> l = new ArrayList<>(a.length);
        for (final double v : a) {
            l.add(v);
        }
        return l;
    }
}
