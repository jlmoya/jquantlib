/*
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

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.time.Date;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Recursive STCDO default loss model for a heterogeneous pool of names.
 *
 * <p>Java port of QuantLib v1.42.1 template
 * {@code template <class copulaPolicy> class RecursiveLossModel} (declared in
 * {@code ql/experimental/credit/recursivelossmodel.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The pool names are heterogeneous in their default probabilities, notionals
 * and recovery rates. Correlations are given by the latent model. The recursive pricing algorithm follows
 * Andersen-Sidenius-Basu, "All your hedges in one basket", Risk, November 2003 (pp. 67-72).
 *
 * <p>Notes (carried from the C++ source):
 * <ul>
 *   <li>Using copulas other than Gaussian this is only an approximation
 *       (see remark on p.68).</li>
 *   <li>The loss-unit chosen here is {@code min(lgd) / nBuckets}. This is OK
 *       for pricing but not for risk metrics — see O'Kane 18.3.2.</li>
 * </ul>
 *
 * @param <P> the {@link CopulaPolicy} bound through the underlying {@link ConstantLossLatentModel}
 */
public class RecursiveLossModel< P extends CopulaPolicy > extends DefaultLossModel {

    private final ConstantLossLatentModel< P > copula_;
    private final int nBuckets_;

    // cached basket-driven state (set in resetModel)
    private int remainingBsktSize_;
    private List< Double > notionals_;
    private double notional_;
    private double attachAmount_;
    private double detachAmount_;
    private double lossUnit_;
    private double[] wk_;

    /** Single-argument convenience constructor (one bucket). */
    public RecursiveLossModel(final ConstantLossLatentModel< P > model) {
        this(model, 1);
    }

    public RecursiveLossModel(final ConstantLossLatentModel< P > model, final int nBuckets) {
        QL.require(model != null, "model must not be null");
        QL.require(nBuckets > 0, "nBuckets must be positive");
        this.copula_ = model;
        this.nBuckets_ = nBuckets;
    }

    @Override
    protected void resetModel() {
        if ( basket == null ) {
            return;
        }
        notionals_ = basket.remainingNotionals();
        notional_ = basket.remainingNotional();
        attachAmount_ = basket.remainingAttachmentAmount();
        detachAmount_ = basket.remainingDetachmentAmount();
        remainingBsktSize_ = notionals_.size();

        copula_.resetBasket(basket);

        // Build LGD vector; drop zeros for min, then derive lossUnit and wk.
        final double[] lgds = new double[remainingBsktSize_];
        double minLgd = Double.POSITIVE_INFINITY;
        for ( int i = 0; i < remainingBsktSize_; ++i ) {
            lgds[i] = notionals_.get(i) * (1.0 - copula_.recoveries().get(i));
            if ( lgds[i] != 0.0 && lgds[i] < minLgd ) {
                minLgd = lgds[i];
            }
        }
        QL.require(minLgd != Double.POSITIVE_INFINITY, "all per-name LGDs are zero");
        lossUnit_ = minLgd / nBuckets_;
        wk_ = new double[remainingBsktSize_];
        for ( int i = 0; i < remainingBsktSize_; ++i ) {
            wk_[i] = Math.floor(lgds[i] / lossUnit_ + 0.5);
        }
    }

    /**
     * Expected tranche loss at {@code date}.
     *
     * <p>Mirrors C++ {@code RecursiveLossModel<CP>::expectedTrancheLoss}:
     * computes inverted unconditional probabilities once and integrates the conditional loss against the systemic factor
     * via the copula.
     */
    @Override
    public double expectedTrancheLoss(final Date date) {
        QL.require(basket != null, "basket not set on RecursiveLossModel");
        final List< Double > uncDefProb = basket.remainingProbabilities(date);
        final double[] invProb = new double[uncDefProb.size()];
        for ( int i = 0; i < uncDefProb.size(); ++i ) {
            invProb[i] = copula_.inverseCumulativeY(uncDefProb.get(i), i);
        }
        return copula_.integratedExpectedValue((double[] v1) -> expectedConditionalLossInvP(invProb, v1));
    }

    /**
     * Per-bucket loss-probability vector at {@code date} (integrated over the
     * systemic factor). Buckets are spaced at multiples of {@link #lossUnit_}.
     */
    public double[] lossProbability(final Date date) {
        QL.require(basket != null, "basket not set on RecursiveLossModel");
        final List< Double > uncDefProb = basket.remainingProbabilities(date);
        final double[] uncArr = new double[uncDefProb.size()];
        for ( int i = 0; i < uncDefProb.size(); ++i ) {
            uncArr[i] = uncDefProb.get(i);
        }
        return copula_.integratedExpectedValueV((double[] v1) -> conditionalLossProb(uncArr, v1));
    }

    /**
     * Full loss distribution (cumulative).
     *
     * <p>Mirrors C++ {@code RecursiveLossModel<CP>::lossDistribution}: builds
     * the per-bucket marginal probabilities, then returns the cumulative distribution keyed by loss amount.
     */
    @Override
    public Map< Double, Double > lossDistribution(final Date d) {
        final double[] values = lossProbability(d);
        final TreeMap< Double, Double > distrib = new TreeMap<>();
        double sum = 0.0;
        for ( int i = 0; i < values.length; ++i ) {
            sum += values[i];
            distrib.put(i * lossUnit_, sum);
        }
        return distrib;
    }

    /**
     * Loss percentile, clamped onto the tranche.
     *
     * <p>Mirrors C++ {@code RecursiveLossModel<CP>::percentile}: linearly
     * interpolates the inverse CDF between the bracketing bucket masses, then clamps {@code loss - attachAmount} to
     * {@code [0, detachAmount - attachAmount]}.
     */
    @Override
    public double percentile(final Date d, final double percentile) {
        final TreeMap< Double, Double > dist = (TreeMap< Double, Double >) lossDistribution(d);
        // degenerate cases
        if ( dist.firstEntry().getValue() >= 1.0 ) {
            return dist.firstKey();
        }
        if ( dist.size() == 1 ) {
            return dist.firstKey();
        }
        if ( percentile == 1.0 ) {
            return dist.lastEntry().getValue();
        }
        if ( percentile == 0.0 ) {
            return dist.firstEntry().getValue();
        }
        // walk to first entry with cumProb > percentile
        Map.Entry< Double, Double > prev = null;
        Map.Entry< Double, Double > curr = null;
        for ( final Map.Entry< Double, Double > e : dist.entrySet() ) {
            if ( e.getValue() > percentile ) {
                curr = e;
                break;
            }
            prev = e;
        }
        QL.require(prev != null && curr != null, "percentile bracket failed");
        final double valPlus = curr.getValue();
        final double xPlus = curr.getKey();
        final double valMin = prev.getValue();
        final double xMin = prev.getKey();
        final double portfLoss = xPlus - (xPlus - xMin) * (valPlus - percentile) / (valPlus - valMin);
        return Math.min(Math.max(portfLoss - attachAmount_, 0.0), detachAmount_ - attachAmount_);
    }

    /**
     * Expected shortfall above {@code perctl}.
     *
     * <p>Mirrors C++ {@code RecursiveLossModel<CP>::expectedShortfall} including
     * its "broken first period" linear interpolation. Returns 0 when the requested date equals the evaluation date.
     */
    @Override
    public double expectedShortfall(final Date d, final double perctl) {
        if ( d.equals(new Settings().evaluationDate()) ) {
            return 0.0;
        }
        final TreeMap< Double, Double > distrib = (TreeMap< Double, Double >) lossDistribution(d);

        Map.Entry< Double, Double > itDist = null;
        Map.Entry< Double, Double > itNxt = null;
        Map.Entry< Double, Double > prev = null;
        for ( final Map.Entry< Double, Double > e : distrib.entrySet() ) {
            if ( e.getValue() >= perctl ) {
                itNxt = e;
                itDist = prev;
                break;
            }
            prev = e;
        }
        // not found — return 0 to match C++ fallthrough
        if ( itNxt == null || itDist == null ) {
            return 0.0;
        }
        double lossNxt = clampTranche(itNxt.getKey());
        double lossHere = clampTranche(itDist.getKey());
        final double val = lossNxt - (itNxt.getValue() - perctl) * (lossNxt - lossHere) / (itNxt.getValue() - itDist.getValue());
        double suma = (itNxt.getValue() - perctl) * (lossNxt + val) * 0.5;

        // advance once
        Map.Entry< Double, Double > prevIt = itNxt;
        Map.Entry< Double, Double > nextIt = distrib.higherEntry(itNxt.getKey());
        while ( nextIt != null ) {
            lossNxt = clampTranche(nextIt.getKey());
            lossHere = clampTranche(prevIt.getKey());
            suma += 0.5 * (lossHere + lossNxt) * (nextIt.getValue() - prevIt.getValue());
            prevIt = nextIt;
            nextIt = distrib.higherEntry(nextIt.getKey());
        }
        return suma / (1.0 - perctl);
    }

    private double clampTranche(final double loss) {
        return Math.min(Math.max(loss - attachAmount_, 0.0), detachAmount_ - attachAmount_);
    }

    // ----- per-market-factor conditional kernels -------------------------------

    /**
     * Conditional discrete loss distribution; uses inverse-cumulative
     * probabilities. Mirrors C++ {@code conditionalLossDistribInvP}.
     */
    private Map< Double, Double > conditionalLossDistribInvP(final double[] invpDefDate, final double[] mktFactor) {
        // Use LinkedHashMap to preserve insertion order — bucket keys are
        // integer multiples of wk_[*], no float-equality lookup issues.
        Map< Double, Double > pIndepDistrib = new LinkedHashMap<>();
        pIndepDistrib.put(0.0, 1.0);
        for ( int iName = 0; iName < remainingBsktSize_; ++iName ) {
            final double pDef = copula_.conditionalDefaultProbabilityInvP(invpDefDate[iName], iName, mktFactor);
            final Map< Double, Double > pDistTemp = new LinkedHashMap<>();
            for ( final Map.Entry< Double, Double > e : pIndepDistrib.entrySet() ) {
                final double k = e.getKey();
                final double v = e.getValue();
                pDistTemp.merge(k, v * (1.0 - pDef), Double::sum);
                pDistTemp.merge(k + wk_[iName], v * pDef, Double::sum);
            }
            pIndepDistrib = pDistTemp;
        }
        return pIndepDistrib;
    }

    /**
     * Conditional discrete loss distribution; uses uninverted unconditional
     * probabilities. Mirrors C++ {@code conditionalLossDistrib} (the slower variant — kept for parity).
     */
    private Map< Double, Double > conditionalLossDistrib(final double[] pDefDate, final double[] mktFactor) {
        Map< Double, Double > pIndepDistrib = new LinkedHashMap<>();
        pIndepDistrib.put(0.0, 1.0);
        for ( int iName = 0; iName < remainingBsktSize_; ++iName ) {
            final double pDef = copula_.conditionalDefaultProbability(pDefDate[iName], iName, mktFactor);
            final Map< Double, Double > pDistTemp = new LinkedHashMap<>();
            for ( final Map.Entry< Double, Double > e : pIndepDistrib.entrySet() ) {
                final double k = e.getKey();
                final double v = e.getValue();
                pDistTemp.merge(k, v * (1.0 - pDef), Double::sum);
                pDistTemp.merge(k + wk_[iName], v * pDef, Double::sum);
            }
            pIndepDistrib = pDistTemp;
        }
        return pIndepDistrib;
    }

    /** Conditional expected loss (inverted-P variant). Mirrors C++ {@code expectedConditionalLossInvP}. */
    private double expectedConditionalLossInvP(final double[] invPDefDate, final double[] mktFactor) {
        final Map< Double, Double > pIndepDistrib = conditionalLossDistribInvP(invPDefDate, mktFactor);
        double expLoss = 0.0;
        for ( final Map.Entry< Double, Double > e : pIndepDistrib.entrySet() ) {
            final double loss = clampTranche(e.getKey() * lossUnit_);
            expLoss += loss * e.getValue();
        }
        return expLoss;
    }

    /** Per-bucket conditional probability vector. Mirrors C++ {@code conditionalLossProb}. */
    private double[] conditionalLossProb(final double[] pDefDate, final double[] mktFactor) {
        final Map< Double, Double > pIndepDistrib = conditionalLossDistrib(pDefDate, mktFactor);
        final double[] results = new double[pIndepDistrib.size()];
        int i = 0;
        for ( final Map.Entry< Double, Double > e : pIndepDistrib.entrySet() ) {
            results[i++] = e.getValue();
        }
        return results;
    }
}
