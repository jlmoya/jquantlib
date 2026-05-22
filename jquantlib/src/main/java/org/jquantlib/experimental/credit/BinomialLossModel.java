/*
 Copyright (C) 2014 Jose Aparicio
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

import java.util.*;

/**
 * Binomial Defaultable Basket Loss Model.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code template <class LLM> class BinomialLossModel} (declared in
 * {@code ql/experimental/credit/binomiallossmodel.hpp}).
 *
 * <p>Models the portfolio loss distribution by approximating it to an
 * adjusted binomial. Fits the two moments of the loss distribution through an adapted binomial approximation. Allows
 * for portfolio inhomogeneity at no excessive cost over the LHP.
 *
 * <p>References:
 * <ul>
 *   <li>Dominic O'Kane, <i>Approximating Independent Loss Distributions
 *       with an Adjusted Binomial Distribution</i>, EDHEC 2007.</li>
 *   <li>Dominic O'Kane, <i>Modelling single name and multi-name credit
 *       derivatives</i>, Wiley 2008, ch. 18.5.2.</li>
 * </ul>
 *
 * <p>Java vs C++:
 * <ul>
 *   <li>The C++ {@code typedef typename LLM::copulaType copulaType} is not
 *       reified — the underlying copula is implicit in the supplied
 *       {@link ConstantLossLatentModel}.</li>
 *   <li>The static {@link #lossProbabilityKernel(double[], double[])}
 *       exposes the core adjusted-binomial pmf for testing without a
 *       basket / latent-model wiring.</li>
 *   <li>The {@code expConditionalLgd} stochastic-recovery hook is
 *       simplified: the constant-recovery model returns {@code 1 - rr_i}
 *       directly. The "live list" is taken to mean all names of the
 *       basket (no settled defaults) per the C++ resetModel comment.</li>
 * </ul>
 *
 * @param <P> the {@link CopulaPolicy} bound through the underlying {@link ConstantLossLatentModel}
 */
public class BinomialLossModel< P extends CopulaPolicy > extends DefaultLossModel {

    /** {@code QL_EPSILON} as in C++ qldefines.hpp (machine epsilon of double). */
    private static final double QL_EPSILON = Math.ulp(1.0);
    private final ConstantLossLatentModel< P > copula_;
    /** Cached basket attach/detach amounts (set in {@link #resetModel()}). */
    private double attachAmount_;
    private double detachAmount_;

    public BinomialLossModel(final ConstantLossLatentModel< P > copula) {
        this.copula_ = copula;
    }

    /**
     * Adjusted-binomial conditional loss-probability density: given per-name conditional default probabilities
     * {@code condDefProb} (of length N) and per-name LGDs {@code lgdsLeft} (of length N), returns the (N+1)-element pmf
     * over loss-bucket index {@code k = 0..N} (number of defaults).
     *
     * <p>Direct port of {@code BinomialLossModel<LLM>::lossProbability} in
     * the C++ header. The two moments (mean and variance) of the loss distribution match those of the actual
     * non-homogeneous case; α is the scaling factor and ε / ε⁺ are the spike adjustments at the floor and ceiling of
     * the average.
     */
    public static double[] lossProbabilityKernel(final double[] condDefProbIn, final double[] lgdsLeftIn) {
        QL.require(condDefProbIn.length == lgdsLeftIn.length, "condDefProb and lgdsLeft must have equal length");
        final int bsktSize = condDefProbIn.length;

        // Local working copies (kernel mutates these, mirroring C++ for the
        // stat aggregates).
        final double[] condDefProb = condDefProbIn.clone();
        final double[] lgdsLeft = lgdsLeftIn.clone();

        double sumLgd = 0.0;
        for ( final double v : lgdsLeft ) {
            sumLgd += v;
        }
        final double avgLgd = sumLgd / bsktSize;

        double cdpDotLgd = 0.0;
        for ( int i = 0; i < bsktSize; ++i ) {
            cdpDotLgd += condDefProb[i] * lgdsLeft[i];
        }
        final double avgProb = avgLgd <= QL_EPSILON ? 0.0 : cdpDotLgd / (avgLgd * bsktSize);

        final double m = avgProb * bsktSize;
        final double floorAveProb = Math.min(bsktSize - 1, Math.floor(m));
        final double ceilAveProb = floorAveProb + 1.0;

        final double varianceBinom = avgProb * (1.0 - avgProb) / bsktSize;

        final double[] oneMinusDef = new double[bsktSize];
        for ( int i = 0; i < bsktSize; ++i ) {
            oneMinusDef[i] = 1.0 - condDefProb[i];
        }
        // condDefProb[i] *= oneMinusDef[i]
        for ( int i = 0; i < bsktSize; ++i ) {
            condDefProb[i] = condDefProb[i] * oneMinusDef[i];
        }
        // lgdsLeft[i] = lgdsLeft[i] * lgdsLeft[i]
        for ( int i = 0; i < bsktSize; ++i ) {
            lgdsLeft[i] = lgdsLeft[i] * lgdsLeft[i];
        }
        double variance = 0.0;
        for ( int i = 0; i < bsktSize; ++i ) {
            variance += condDefProb[i] * lgdsLeft[i];
        }
        variance = avgLgd <= QL_EPSILON ? 0.0 : variance / (((double) bsktSize) * bsktSize * avgLgd * avgLgd);

        final double sumAves =
                -Math.pow(ceilAveProb - m, 2) - (Math.pow(floorAveProb - m, 2) - Math.pow(ceilAveProb, 2.0)) * (
                        ceilAveProb - m);

        final double alpha = (variance * bsktSize + sumAves) / (varianceBinom * bsktSize + sumAves);

        final double[] lossProbDensity = new double[bsktSize + 1];
        if ( avgProb >= 1.0 - QL_EPSILON ) {
            lossProbDensity[bsktSize] = 1.0;
        } else if ( avgProb <= QL_EPSILON ) {
            lossProbDensity[0] = 1.0;
        } else {
            final double probsRatio = avgProb / (1.0 - avgProb);
            lossProbDensity[0] = Math.pow(1.0 - avgProb, bsktSize);
            for ( int i = 1; i < bsktSize + 1; ++i ) {
                lossProbDensity[i] = lossProbDensity[i - 1] * probsRatio * (((double) bsktSize) - i + 1.0) / i;
            }
            for ( int i = 0; i < bsktSize + 1; ++i ) {
                lossProbDensity[i] *= alpha;
            }
            final double epsilon = (1.0 - alpha) * (ceilAveProb - m);
            final double epsilonPlus = 1.0 - alpha - epsilon;
            lossProbDensity[(int) floorAveProb] += epsilon;
            lossProbDensity[(int) ceilAveProb] += epsilonPlus;
        }
        return lossProbDensity;
    }

    // ------------------------------------------------------------------------
    // Static analytic kernel (testable without a Basket)
    // ------------------------------------------------------------------------

    /** Suppressed-warning helper — keeps ArrayList import live for derived classes. */
    @SuppressWarnings( "unused" )
    private static < T > List< T > keepArrayList() {
        return new ArrayList<>();
    }

    // ------------------------------------------------------------------------
    // Basket-attached overrides
    // ------------------------------------------------------------------------

    @Override
    protected void resetModel() {
        if ( basket != null ) {
            attachAmount_ = basket.remainingAttachmentAmount();
            detachAmount_ = basket.remainingDetachmentAmount();
            copula_.resetBasket(basket);
        }
    }

    /** Read-only access to the underlying constant-loss latent model. */
    public ConstantLossLatentModel< P > copula() {
        return copula_;
    }

    /**
     * Per-name conditional LGD = 1 - conditional recovery (constant-recovery model returns the constant LGD).
     */
    private double[] expConditionalLgd(final Date d, final double[] mktFactors) {
        final List< Integer > live = basket.liveList();
        final double[] out = new double[live.size()];
        for ( int i = 0; i < live.size(); ++i ) {
            out[i] = 1.0 - copula_.conditionalRecovery(d, live.get(i), mktFactors);
        }
        return out;
    }

    /** Average loss per credit at the given date and conditional on m. */
    private double averageLoss(final Date d, final List< Double > remNots, final double[] mktFactors) {
        final int bsktSize = basket.remainingSize();
        final double[] fractionalEL = expConditionalLgd(d, mktFactors);
        double notBskt = 0.0;
        for ( final double v : remNots ) {
            notBskt += v;
        }
        double sum = 0.0;
        for ( int i = 0; i < fractionalEL.length; ++i ) {
            sum += fractionalEL[i] * remNots.get(i);
        }
        return sum / (bsktSize * notBskt);
    }

    /** Conditional version of {@link #lossProbabilityKernel}: applies live LGDs first. */
    private double[] lossProbability(final Date date, final List< Double > bsktNots, final double[] uncondDefProbInv,
            final double[] mktFactors) {
        final double[] fractionalEL = expConditionalLgd(date, mktFactors);
        final double[] lgdsLeft = new double[fractionalEL.length];
        for ( int i = 0; i < fractionalEL.length; ++i ) {
            lgdsLeft[i] = fractionalEL[i] * bsktNots.get(i);
        }
        final int bsktSize = basket.remainingSize();
        final double[] condDefProb = new double[bsktSize];
        for ( int j = 0; j < bsktSize; ++j ) {
            condDefProb[j] = copula_.conditionalDefaultProbabilityInvP(uncondDefProbInv[j], j, mktFactors);
        }
        return lossProbabilityKernel(condDefProb, lgdsLeft);
    }

    /** Loss points the model provides — N+1 evenly-spaced loss values. */
    private double[] lossPoints(final Date d) {
        final List< Double > notionals = basket.remainingNotionals(d);
        final double aveLossFrct = copula_.integratedExpectedValue((double[] v1) -> averageLoss(d, notionals, v1));
        final int dataSize = basket.remainingSize() + 1;
        final double outsNot = basket.remainingNotional(d);
        final double[] data = new double[dataSize];
        for ( int i = 0; i < dataSize; ++i ) {
            data[i] = i * aveLossFrct * outsNot;
        }
        return data;
    }

    /** Expected (integrated over m) loss-distribution density. */
    private double[] expectedDistribution(final Date date) {
        final List< Double > notionals = basket.remainingNotionals(date);
        final List< Double > probsList = basket.remainingProbabilities(date);
        final double[] invProbs = new double[probsList.size()];
        for ( int i = 0; i < probsList.size(); ++i ) {
            invProbs[i] = copula_.inverseCumulativeY(probsList.get(i), i);
        }
        return copula_.integratedExpectedValueV((double[] v1) -> lossProbability(date, notionals, invProbs, v1));
    }

    @Override
    public double expectedTrancheLoss(final Date d) {
        QL.require(basket != null, "Basket not set on BinomialLossModel");
        final double[] lossVals = lossPoints(d);
        final List< Double > notionals = basket.remainingNotionals(d);
        final List< Double > probsList = basket.remainingProbabilities(d);
        final double[] invProbs = new double[probsList.size()];
        for ( int i = 0; i < probsList.size(); ++i ) {
            invProbs[i] = copula_.inverseCumulativeY(probsList.get(i), i);
        }
        return copula_.integratedExpectedValue((double[] v1) -> {
            final double[] condLProb = lossProbability(d, notionals, invProbs, v1);
            double suma = 0.0;
            for ( int i = 0; i < lossVals.length; ++i ) {
                suma += condLProb[i] * Math.min(Math.max(lossVals[i] - attachAmount_, 0.0),
                        detachAmount_ - attachAmount_);
            }
            return suma;
        });
    }

    @Override
    public Map< Double, Double > lossDistribution(final Date d) {
        final var dist = new TreeMap< Double, Double >();
        final double[] lossPts = lossPoints(d);
        final double[] values = expectedDistribution(d);
        double sum = 0.0;
        for ( int i = 0; i < lossPts.length; ++i ) {
            dist.put(lossPts[i], Math.min(sum + values[i], 1.0));
            sum += values[i];
        }
        return dist;
    }

    @Override
    public double percentile(final Date d, final double perc) {
        final var dist = new TreeMap< Double, Double >(lossDistribution(d));
        final Map.Entry< Double, Double > first = dist.firstEntry();
        if ( first.getValue() >= perc ) {
            return first.getKey();
        }
        if ( dist.size() == 1 ) {
            return first.getKey();
        }
        if ( perc == 1.0 ) {
            return dist.lastEntry().getKey();
        }
        if ( perc == 0.0 ) {
            return first.getKey();
        }
        final Iterator< Map.Entry< Double, Double > > it = dist.entrySet().iterator();
        Map.Entry< Double, Double > prev = it.next();
        Map.Entry< Double, Double > cur = prev;
        while ( cur.getValue() <= perc && it.hasNext() ) {
            prev = cur;
            cur = it.next();
        }
        final double valPlus = cur.getValue();
        final double xPlus = cur.getKey();
        final double valMin = prev.getValue();
        final double xMin = prev.getKey();
        final double portfLoss = xPlus - (xPlus - xMin) * (valPlus - perc) / (valPlus - valMin);
        return Math.min(Math.max(portfLoss - attachAmount_, 0.0), detachAmount_ - attachAmount_);
    }

    @Override
    public double expectedShortfall(final Date d, final double perctl) {
        if ( d.equals(new Settings().evaluationDate()) ) {
            return 0.0;
        }
        final var dist = new TreeMap< Double, Double >(lossDistribution(d));
        final Iterator< Map.Entry< Double, Double > > it = dist.entrySet().iterator();
        Map.Entry< Double, Double > cur = null;
        Map.Entry< Double, Double > prev = null;
        while ( it.hasNext() ) {
            prev = cur;
            cur = it.next();
            if ( cur.getValue() >= perctl ) {
                break;
            }
        }
        if ( cur == null || cur.getValue() < perctl ) {
            QL.require(false, "Binomial model fails to calculate ESF.");
        }
        if ( it.hasNext() ) {
            Map.Entry< Double, Double > nxt = cur;
            Map.Entry< Double, Double > here = prev;
            double lossNxt = Math.min(Math.max(nxt.getKey() - attachAmount_, 0.0), detachAmount_ - attachAmount_);
            double lossHere = Math.min(Math.max(here.getKey() - attachAmount_, 0.0), detachAmount_ - attachAmount_);
            final double val =
                    lossNxt - (nxt.getValue() - perctl) * (lossNxt - lossHere) / (nxt.getValue() - here.getValue());
            double suma = (nxt.getValue() - perctl) * (lossNxt + val) * 0.5;
            here = nxt;
            nxt = it.hasNext() ? it.next() : null;
            while ( nxt != null ) {
                lossNxt = Math.min(Math.max(nxt.getKey() - attachAmount_, 0.0), detachAmount_ - attachAmount_);
                lossHere = Math.min(Math.max(here.getKey() - attachAmount_, 0.0), detachAmount_ - attachAmount_);
                suma += 0.5 * (lossHere + lossNxt) * (nxt.getValue() - here.getValue());
                here = nxt;
                nxt = it.hasNext() ? it.next() : null;
            }
            return suma / (1.0 - perctl);
        }
        QL.require(false, "Binomial model fails to calculate ESF.");
        return Double.NaN; // unreachable
    }
}
