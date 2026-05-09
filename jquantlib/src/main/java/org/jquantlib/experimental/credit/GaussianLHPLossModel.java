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
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.BivariateNormalDistribution;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.Date;

/**
 * Portfolio loss model with analytical expected tranche loss for a large
 * homogeneous pool with Gaussian one-factor copula.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::GaussianLHPLossModel}
 * ({@code ql/experimental/credit/gaussianlhplossmodel.{hpp,cpp}}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Reference: <i>"The Normal Inverse Gaussian Distribution for Synthetic
 * CDO pricing."</i>, Anna Kalemanova, Bernd Schmid, Ralf Werner, Journal
 * of Derivatives, Vol. 14, No. 3, Spring 2007, pp. 80-93.
 *
 * <p>Java vs C++:
 * <ul>
 *   <li>{@code DefaultLossModel} virtual interface methods are inherited
 *       from the {@link DefaultLossModel} base.</li>
 *   <li>The {@link LatentModel}{@code <GaussianCopulaPolicy>} multiple
 *       inheritance in C++ becomes composition: the LM is a private field.</li>
 *   <li>The {@code Handle<Quote>} reactivity for correlation/recovery quotes
 *       is approximated by {@link Quote} references — Observer wiring is
 *       deferred to a follow-up phase per Phase 4m.7 scope.</li>
 *   <li>Static {@link #expectedTrancheLossKernel} / {@link #percentilePortfolioLossFractionKernel}
 *       / {@link #probOverLossKernel} expose the analytic core for testing
 *       without a {@link Basket}.</li>
 * </ul>
 */
public class GaussianLHPLossModel extends DefaultLossModel {

    private static final CumulativeNormalDistribution PHI = new CumulativeNormalDistribution();
    private static final InverseCumulativeNormal INV_PHI = new InverseCumulativeNormal();

    /** sqrt(1 - rho); cached. */
    private double sqrt1minuscorrel_;
    /** sqrt(rho); cached. */
    private double beta_;
    /** Bivariate cumulative normal distribution with correlation -beta. */
    private BivariateNormalDistribution biphi_;

    private final Quote correl_;
    private final List<Quote> rrQuotes_;

    /**
     * Constant-recovery convenience constructor.
     *
     * @param correlation latent-model correlation in [0, 1]
     * @param recoveries  per-name constant recoveries
     */
    public GaussianLHPLossModel(final double correlation, final List<Double> recoveries) {
        QL.require(correlation >= 0.0 && correlation <= 1.0,
                "correlation out of [0,1]: " + correlation);
        QL.require(recoveries != null && !recoveries.isEmpty(),
                "recoveries must be non-empty");
        this.correl_ = new SimpleQuote(correlation);
        this.rrQuotes_ = new ArrayList<>(recoveries.size());
        for (final Double r : recoveries) {
            this.rrQuotes_.add(new RecoveryRateQuote(r));
        }
        recomputeCache();
    }

    /**
     * Quote-driven constructor with constant recoveries.
     *
     * @param correlQuote correlation quote (read once at construction; full
     *                    Observer reactivity deferred to Phase 4m.7b)
     * @param recoveries  per-name constant recoveries
     */
    public GaussianLHPLossModel(final Quote correlQuote, final List<Double> recoveries) {
        QL.require(correlQuote != null, "correl quote null");
        QL.require(recoveries != null && !recoveries.isEmpty(),
                "recoveries must be non-empty");
        this.correl_ = correlQuote;
        this.rrQuotes_ = new ArrayList<>(recoveries.size());
        for (final Double r : recoveries) {
            this.rrQuotes_.add(new RecoveryRateQuote(r));
        }
        recomputeCache();
    }

    /**
     * Quote-driven constructor with quote-driven recoveries (typically
     * {@link RecoveryRateQuote} instances).
     */
    public GaussianLHPLossModel(final Quote correlQuote, final List<Quote> rrQuotes, final boolean useQuotes) {
        // useQuotes is only there to disambiguate the erasure with the recoveries List<Double> ctor.
        QL.require(useQuotes, "use the Quote/Quote ctor only with recovery quotes");
        QL.require(correlQuote != null, "correl quote null");
        QL.require(rrQuotes != null && !rrQuotes.isEmpty(),
                "rrQuotes must be non-empty");
        this.correl_ = correlQuote;
        this.rrQuotes_ = new ArrayList<>(rrQuotes);
        recomputeCache();
    }

    /** Recompute beta_/sqrt1minuscorrel_/biphi_ from {@link #correl_}. */
    public final void update() {
        recomputeCache();
        if (basket != null) {
            basket.update();
        }
    }

    private void recomputeCache() {
        final double rho = correl_.value();
        this.sqrt1minuscorrel_ = Math.sqrt(1.0 - rho);
        this.beta_ = Math.sqrt(rho);
        this.biphi_ = new BivariateNormalDistribution(-beta_);
    }

    @Override
    protected void resetModel() {
        // No internal state besides what's already cached.
    }

    /** Read-only access to the per-name recovery quotes. */
    public List<Quote> recoveryQuotes() {
        return Collections.unmodifiableList(rrQuotes_);
    }

    /** Read-only access to the correlation quote. */
    public Quote correlationQuote() {
        return correl_;
    }

    // ------------------------------------------------------------------------
    // Analytic kernels (static; testable without a Basket)
    // ------------------------------------------------------------------------

    /**
     * Expected tranche loss for a homogeneous pool, given remaining notional,
     * average default probability, average recovery, and tranche
     * attachment/detachment fractions and copula correlation.
     *
     * <p>Direct port of C++ {@code GaussianLHPLossModel::expectedTrancheLossImpl}.
     */
    public static double expectedTrancheLossKernel(final double remNot,
                                                   final double prob,
                                                   final double averageRR,
                                                   final double attach,
                                                   final double detach,
                                                   final double correl) {
        if (attach >= detach) {
            return 0.0;
        }
        if (remNot == 0.0) {
            return 0.0;
        }
        final double one = 1.0 - 1.0e-12;
        final double k1 = Math.min(one, attach / (1.0 - averageRR)) + QL_EPSILON;
        final double k2 = Math.min(one, detach / (1.0 - averageRR)) + QL_EPSILON;
        if (prob > 0.0) {
            final double sqrt1mc = Math.sqrt(1.0 - correl);
            final double beta = Math.sqrt(correl);
            final BivariateNormalDistribution biphi = new BivariateNormalDistribution(-beta);
            final double ip = INV_PHI.op(prob);
            final double if1 = (ip - sqrt1mc * INV_PHI.op(k1)) / beta;
            final double if2 = (ip - sqrt1mc * INV_PHI.op(k2)) / beta;
            return remNot * (detach * PHI.op(if2) - attach * PHI.op(if1)
                    + (1.0 - averageRR) * (biphi.op(ip, -if2) - biphi.op(ip, -if1)));
        }
        return 0.0;
    }

    /**
     * Portfolio-loss percentile (loss-as-fraction) closed form.
     * Direct port of C++ {@code GaussianLHPLossModel::percentilePortfolioLossFraction}.
     */
    public static double percentilePortfolioLossFractionKernel(final double averageRR,
                                                               final double averageProb,
                                                               final double perctlIn,
                                                               final double correl) {
        QL.require(perctlIn >= 0.0 && perctlIn <= 1.0,
                "Percentile argument out of bounds: " + perctlIn);
        if (perctlIn == 0.0) {
            return 0.0;
        }
        double perctl = perctlIn;
        if (perctl == 1.0) {
            perctl = 1.0 - QL_EPSILON;
        }
        final double sqrt1mc = Math.sqrt(1.0 - correl);
        final double beta = Math.sqrt(correl);
        return (1.0 - averageRR) * PHI.op(
                (INV_PHI.op(averageProb)
                        + beta * INV_PHI.op(perctl))
                        / sqrt1mc);
    }

    /**
     * Probability of the portfolio loss exceeding a given fractional level.
     * The {@code portfFract} input is the loss fraction <em>of basket
     * notional</em>, not of tranche notional. Direct port of the analytic
     * kernel inside C++ {@code GaussianLHPLossModel::probOverLoss}.
     */
    public static double probOverLossKernel(final double averageRR,
                                            final double averageProb,
                                            final double portfFract,
                                            final double correl) {
        final double sqrt1mc = Math.sqrt(1.0 - correl);
        final double beta = Math.sqrt(correl);
        final double ip = INV_PHI.op(averageProb);
        final double if1 = (ip - sqrt1mc *
                INV_PHI.op(portfFract / (1.0 - averageRR)))
                / beta;
        return PHI.op(if1);
    }

    // ------------------------------------------------------------------------
    // Basket-attached overrides (mirror DefaultLossModel virtual surface)
    // ------------------------------------------------------------------------

    @Override
    public double expectedTrancheLoss(final Date d) {
        QL.require(basket != null, "Basket not set on GaussianLHPLossModel");
        final double remainingFullNot = basket.remainingNotional(d);
        final double averageRR = averageRecovery(d);
        final double prob = averageProb(d);
        final double remainingAttachAmount = basket.remainingAttachmentAmount();
        final double remainingDetachAmount = basket.remainingDetachmentAmount();
        final double attach = remainingAttachAmount / remainingFullNot;
        final double detach = remainingDetachAmount / remainingFullNot;
        return expectedTrancheLossInstance(remainingFullNot, prob, averageRR, attach, detach);
    }

    /**
     * Instance variant that uses the cached {@link #beta_} / {@link #sqrt1minuscorrel_} /
     * {@link #biphi_} for performance — equivalent to {@link #expectedTrancheLossKernel}.
     */
    public double expectedTrancheLossInstance(final double remNot,
                                              final double prob,
                                              final double averageRR,
                                              final double attach,
                                              final double detach) {
        if (attach >= detach) {
            return 0.0;
        }
        if (remNot == 0.0) {
            return 0.0;
        }
        final double one = 1.0 - 1.0e-12;
        final double k1 = Math.min(one, attach / (1.0 - averageRR)) + QL_EPSILON;
        final double k2 = Math.min(one, detach / (1.0 - averageRR)) + QL_EPSILON;
        if (prob > 0.0) {
            final double ip = INV_PHI.op(prob);
            final double if1 = (ip - sqrt1minuscorrel_ * INV_PHI.op(k1)) / beta_;
            final double if2 = (ip - sqrt1minuscorrel_ * INV_PHI.op(k2)) / beta_;
            return remNot * (detach * PHI.op(if2) - attach * PHI.op(if1)
                    + (1.0 - averageRR) * (biphi_.op(ip, -if2) - biphi_.op(ip, -if1)));
        }
        return 0.0;
    }

    @Override
    public double probOverLoss(final Date d, final double remainingLossFraction) {
        QL.require(remainingLossFraction >= 0.0, "Incorrect loss fraction.");
        QL.require(remainingLossFraction <= 1.0, "Incorrect loss fraction.");
        QL.require(basket != null, "Basket not set on GaussianLHPLossModel");
        final double remainingAttachAmount = basket.remainingAttachmentAmount();
        final double remainingDetachAmount = basket.remainingDetachmentAmount();
        final double remainingBasktNot = basket.remainingNotional(d);
        final double attach = Math.min(remainingAttachAmount / remainingBasktNot, 1.0);
        final double detach = Math.min(remainingDetachAmount / remainingBasktNot, 1.0);
        final double portfFract = attach + remainingLossFraction * (detach - attach);
        final double averageRR = averageRecovery(d);
        final double maxAttLossFract = 1.0 - averageRR;
        if (portfFract > maxAttLossFract) {
            return 0.0;
        }
        if (portfFract <= QL_EPSILON) {
            return 1.0;
        }
        final double prob = averageProb(d);
        final double ip = INV_PHI.op(prob);
        final double invFlightK = (ip - sqrt1minuscorrel_
                * INV_PHI.op(portfFract / (1.0 - averageRR)))
                / beta_;
        return PHI.op(invFlightK);
    }

    @Override
    public double percentile(final Date d, final double perctl) {
        QL.require(basket != null, "Basket not set on GaussianLHPLossModel");
        final double remainingNot = basket.remainingNotional(d);
        final double remainingAttachAmount = basket.remainingAttachmentAmount();
        final double remainingDetachAmount = basket.remainingDetachmentAmount();
        final double attach = Math.min(remainingAttachAmount / remainingNot, 1.0);
        final double detach = Math.min(remainingDetachAmount / remainingNot, 1.0);
        return remainingNot
                * Math.min(Math.max(percentilePortfolioLossFraction(d, perctl) - attach, 0.0),
                        detach - attach);
    }

    /** Helper used by {@link #percentile(Date, double)} and {@link #expectedShortfall(Date, double)}. */
    public double percentilePortfolioLossFraction(final Date d, final double perctl) {
        QL.require(perctl >= 0.0 && perctl <= 1.0,
                "Percentile argument out of bounds: " + perctl);
        if (perctl == 0.0) {
            return 0.0;
        }
        double q = perctl;
        if (q == 1.0) {
            q = 1.0 - QL_EPSILON;
        }
        return (1.0 - averageRecovery(d)) * PHI.op(
                (INV_PHI.op(averageProb(d))
                        + beta_ * INV_PHI.op(q))
                        / sqrt1minuscorrel_);
    }

    @Override
    public double expectedShortfall(final Date d, final double perctl) {
        QL.require(basket != null, "Basket not set on GaussianLHPLossModel");
        final double ptflLossPerc = percentilePortfolioLossFraction(d, perctl);
        final double remainingAttachAmount = basket.remainingAttachmentAmount();
        final double remainingDetachAmount = basket.remainingDetachmentAmount();
        final double remainingNot = basket.remainingNotional(d);
        final double attach = Math.min(remainingAttachAmount / remainingNot, 1.0);
        final double detach = Math.min(remainingDetachAmount / remainingNot, 1.0);
        if (ptflLossPerc >= detach - QL_EPSILON) {
            return remainingNot * (detach - attach);
        }
        final double maxLossLevel = Math.max(attach, ptflLossPerc);
        final double prob = averageProb(d);
        final double averageRR = averageRecovery(d);
        final double valA = expectedTrancheLossInstance(remainingNot, prob, averageRR, maxLossLevel, detach);
        final double valB = probOverLoss(d,
                Math.min(Math.max((maxLossLevel - attach) / (detach - attach), 0.0), 1.0));
        return (valA + (maxLossLevel - attach) * remainingNot * valB) / (1.0 - perctl);
    }

    @Override
    public double expectedRecovery(final Date d, final int iName, final DefaultProbKey ik) {
        return rrQuotes_.get(iName).value();
    }

    /**
     * Notional-and-survival-probability-weighted average default probability.
     */
    public double averageProb(final Date d) {
        final List<Double> probs = basket.remainingProbabilities(d);
        final List<Double> remainingNots = basket.remainingNotionals(d);
        double dot = 0.0;
        for (int i = 0; i < probs.size(); ++i) {
            dot += probs.get(i) * remainingNots.get(i);
        }
        return dot / basket.remainingNotional(d);
    }

    /**
     * Notional-and-default-probability-weighted average recovery.
     */
    public double averageRecovery(final Date d) {
        final List<Double> probs = basket.remainingProbabilities(d);
        final int n = basket.remainingSize();
        final List<Double> recoveries = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            recoveries.add(rrQuotes_.get(i).value());
        }
        final List<Double> notionals = basket.remainingNotionals(d);
        double denom = 0.0;
        for (int i = 0; i < notionals.size(); ++i) {
            denom += notionals.get(i) * probs.get(i);
        }
        if (denom == 0.0) {
            return 0.0;
        }
        // numerator: ∑ recoveries[i] * notionals[i] * probs[i]
        double num = 0.0;
        for (int i = 0; i < notionals.size(); ++i) {
            num += recoveries.get(i) * notionals.get(i) * probs.get(i);
        }
        return num / denom;
    }

    /** {@code QL_EPSILON} as in the C++ qldefines.hpp (machine epsilon of double). */
    private static final double QL_EPSILON = Math.ulp(1.0);
}
