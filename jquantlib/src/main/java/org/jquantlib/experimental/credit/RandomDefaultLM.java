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
import org.jquantlib.Settings;
import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.statistics.IncrementalStatistics;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Default-only Latent-Model Monte-Carlo simulation with deterministic recoveries.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code RandomDefaultLM<copulaPolicy, USNG>} (declared in
 * {@code ql/experimental/credit/randomdefaultlatentmodel.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Phase 4m.7b foundation: ports the simulation kernel
 * ({@link #performSimulations}, {@link #nextSample}) and the three
 * statistics most needed by basket pricing — {@link #expectedTrancheLoss},
 * {@link #probAtLeastNEvents}, and {@link #defaultCorrelation}. The
 * remaining statistics (lossDistribution / expectedShortfall / percentile /
 * splitVaRLevel / splitVaRAndError) require a Java {@code Histogram}
 * port + heavier risk-statistics infrastructure and are deferred to Phase
 * 4m.7c.
 *
 * <p>The C++ implementation uses CRTP to avoid virtual dispatch in the
 * inner Monte-Carlo loop. Java cannot replicate CRTP cleanly; the Java
 * port collapses the two-level hierarchy ({@code RandomLM} +
 * {@code RandomDefaultLM}) into a single concrete class. Subclassing for
 * the sister {@code RandomLossLM} (stochastic recoveries) is via
 * {@link #getEventRecovery(int)} override.
 *
 * @param <P> the {@link CopulaPolicy} subtype controlling distributions
 */
public class RandomDefaultLM<P extends CopulaPolicy> {

    /** Maximum simulation horizon in days; matches C++ {@code maxHorizon_}. */
    public static final int MAX_HORIZON_DAYS = 4050; // ~11 years

    private final DefaultLatentModel<P> model_;
    private final FactorSampler<P> sampler_;
    private final List<Double> recoveries_;
    private final int nSims_;
    private final double accuracy_;

    private Basket basket_;

    /** Per-name horizon default-probability cache (set in {@link #initDates}). */
    private double[] horizonDefaultPs_;

    /** Per-simulation buffer of default events; populated by {@link #performSimulations}. */
    private final List<List<DefaultSimEvent>> simsBuffer_ = new ArrayList<>();

    /**
     * @param model       underlying default latent model (provides copula,
     *                    factorWeights, conditional probabilities)
     * @param sampler     factor sampler to draw the next path
     * @param recoveries  per-name deterministic recoveries (size must equal
     *                    basket size); pass {@code null} or empty for zero
     * @param nSims       number of Monte-Carlo paths
     * @param accuracy    accuracy for the inverse-time Brent solver
     */
    public RandomDefaultLM(final DefaultLatentModel<P> model,
                           final FactorSampler<P> sampler,
                           final List<Double> recoveries,
                           final int nSims,
                           final double accuracy) {
        QL.require(model != null, "model must not be null");
        QL.require(sampler != null, "sampler must not be null");
        QL.require(nSims > 0, "nSims must be positive");
        QL.require(accuracy > 0.0, "accuracy must be positive");
        this.model_ = model;
        this.sampler_ = sampler;
        this.recoveries_ = (recoveries == null || recoveries.isEmpty())
                ? Collections.nCopies(model.size(), 0.0)
                : new ArrayList<>(recoveries);
        this.nSims_ = nSims;
        this.accuracy_ = accuracy;
    }

    /**
     * Bind the basket whose loss the simulation projects. Triggers
     * {@link DefaultLatentModel#resetBasket} on the underlying model.
     */
    public void setBasket(final Basket basket) {
        QL.require(basket != null, "basket must not be null");
        QL.require(basket.size() == model_.size(),
                "Incompatible basket and model sizes");
        QL.require(recoveries_.size() == basket.size(),
                "Incompatible basket and recovery sizes");
        this.basket_ = basket;
        model_.resetBasket(basket);
        // Invalidate cached calculations.
        this.horizonDefaultPs_ = null;
        this.simsBuffer_.clear();
    }

    /** Underlying default latent model. */
    public DefaultLatentModel<P> model() {
        return model_;
    }

    /** Number of Monte-Carlo paths configured. */
    public int nSims() {
        return nSims_;
    }

    /**
     * Run the Monte-Carlo simulation. After this returns,
     * {@link #getSim(int)} can be used to inspect the per-path event lists.
     * Idempotent: re-runs only if the buffer is empty.
     */
    public void calculate() {
        if (!simsBuffer_.isEmpty()) {
            return;
        }
        QL.require(basket_ != null, "setBasket() must be called before calculate()");
        initDates();
        performSimulations();
    }

    /** Force a recompute on the next {@link #calculate()} call. */
    public void invalidate() {
        simsBuffer_.clear();
        horizonDefaultPs_ = null;
    }

    /**
     * Pre-compute per-name default probabilities at the maximum horizon date.
     * Mirrors C++ {@code initDates()}. Called once at the start of each
     * simulation run.
     */
    private void initDates() {
        final Date today = new Settings().evaluationDate();
        final Date maxHorizonDate = today.add(new Period(MAX_HORIZON_DAYS, TimeUnit.Days));

        final Pool pool = basket_.pool();
        final List<DefaultProbKey> dks = basket_.defaultKeys();
        final List<String> names = basket_.names();
        horizonDefaultPs_ = new double[basket_.size()];
        for (int iName = 0; iName < basket_.size(); ++iName) {
            final Handle<DefaultProbabilityTermStructure> dts =
                    pool.get(names.get(iName)).defaultProbability(dks.get(iName));
            horizonDefaultPs_[iName] = dts.currentLink().defaultProbability(maxHorizonDate, true);
        }
    }

    /**
     * Inner Monte-Carlo loop: draw {@code nSims_} paths and dispatch each to
     * {@link #nextSample}.
     */
    private void performSimulations() {
        for (int i = 0; i < nSims_; ++i) {
            final double[] sample = sampler_.nextSequence().value();
            nextSample(sample);
        }
    }

    /**
     * Process a single Monte-Carlo path: for each name, compute the latent
     * variable from the factor sample, derive the simulated default
     * probability, and (if it's within the horizon) Brent-solve for the
     * default time and emit a {@link DefaultSimEvent}.
     *
     * <p>Mirrors C++ {@code RandomDefaultLM::nextSample(values)}.
     */
    private void nextSample(final double[] sample) {
        final List<DefaultSimEvent> path = new ArrayList<>();
        final Pool pool = basket_.pool();
        final List<DefaultProbKey> dks = basket_.defaultKeys();
        final List<String> names = basket_.names();

        for (int iName = 0; iName < model_.size(); ++iName) {
            final double y = model_.latentVarValue(sample, iName);
            final double simDefaultProb = model_.cumulativeY(y, iName);
            // Only emit if the default lies within the horizon.
            if (horizonDefaultPs_[iName] >= simDefaultProb) {
                final Handle<DefaultProbabilityTermStructure> dts =
                        pool.get(names.get(iName)).defaultProbability(dks.get(iName));
                final DefaultProbabilityTermStructure curve = dts.currentLink();
                final Date curveRef = curve.referenceDate();

                // Brent-solve for the day-stride at which the curve's
                // cumulative default-prob equals simDefaultProb.
                final Ops.DoubleOp root = (final double t) -> {
                    QL.require(t >= 0.0, "t < 0 in default-time inversion");
                    return curve.defaultProbability(
                            curveRef.add(new Period((int) t, TimeUnit.Days)), true)
                            - simDefaultProb;
                };
                final double dayStride = new Brent().solve(root, accuracy_, 0.0, 1.0);
                path.add(new DefaultSimEvent(iName, (int) dayStride));
            }
        }
        simsBuffer_.add(path);
    }

    /**
     * Recovery for a defaulted name. Default implementation returns the
     * per-name deterministic recovery; subclasses (stochastic-recovery
     * models) override.
     */
    protected double getEventRecovery(final int nameIdx) {
        return recoveries_.get(nameIdx);
    }

    /** Read-only view of the {@code iSim}-th simulation's events. */
    public List<DefaultSimEvent> getSim(final int iSim) {
        return Collections.unmodifiableList(simsBuffer_.get(iSim));
    }

    // --------------------------------------------------------------------
    //  Statistics
    // --------------------------------------------------------------------

    /**
     * Probability that at least {@code n} defaults occur in the basket on or
     * before date {@code d}. Mirrors C++ {@code probAtLeastNEvents}.
     */
    public double probAtLeastNEvents(final int n, final Date d) {
        calculate();
        final Date today = new Settings().evaluationDate();
        QL.require(d.gt(today), "Date for statistic must be in the future");
        if (n == 0) {
            return 1.0;
        }
        final long val = (long) d.serialNumber() - today.serialNumber();
        double counts = 0.0;
        for (int iSim = 0; iSim < nSims_; ++iSim) {
            int simCount = 0;
            for (final DefaultSimEvent e : simsBuffer_.get(iSim)) {
                if (val > e.dayFromRef) {
                    simCount++;
                }
            }
            if (simCount >= n) {
                counts++;
            }
        }
        return counts / nSims_;
    }

    /**
     * Pearson correlation between the default indicators of names
     * {@code iName} and {@code jName} on or before date {@code d}.
     * Mirrors C++ {@code defaultCorrelation}.
     */
    public double defaultCorrelation(final Date d, final int iName, final int jName) {
        calculate();
        final Date today = new Settings().evaluationDate();
        QL.require(d.gt(today), "Date for statistic must be in the future");
        final long val = (long) d.serialNumber() - today.serialNumber();
        double E_ij = 0.0, E_i = 0.0, E_j = 0.0;
        for (int iSim = 0; iSim < nSims_; ++iSim) {
            double imatch = 0.0, jmatch = 0.0;
            for (final DefaultSimEvent e : simsBuffer_.get(iSim)) {
                if (val > e.dayFromRef) {
                    if (e.nameIdx == iName) imatch = 1.0;
                    if (e.nameIdx == jName) jmatch = 1.0;
                }
            }
            E_ij += imatch * jmatch;
            E_i += imatch;
            E_j += jmatch;
        }
        E_ij /= (nSims_ - 1.0);  // unbiased
        E_i /= nSims_;
        E_j /= nSims_;
        return (E_ij - E_i * E_j)
                / Math.sqrt(E_i * E_j * (1.0 - E_i) * (1.0 - E_j));
    }

    /**
     * Expected tranche loss at date {@code d}. Mirrors C++
     * {@code expectedTrancheLoss}, returning the {@code .first} of
     * {@link #expectedTrancheLossInterval}.
     */
    public double expectedTrancheLoss(final Date d) {
        return expectedTrancheLossInterval(d, 0.95)[0];
    }

    /**
     * Expected tranche loss + half-width of the 95%-confidence interval
     * (mean ± half-width). Mirrors C++ {@code expectedTrancheLossInterval}.
     *
     * @return {@code double[]{mean, halfWidth}}
     */
    public double[] expectedTrancheLossInterval(final Date d, final double confidencePerc) {
        calculate();
        final Date today = new Settings().evaluationDate();
        final long val = (long) d.serialNumber() - today.serialNumber();
        final double attach = basket_.attachmentAmount();
        final double detach = basket_.detachmentAmount();
        final List<String> names = basket_.names();
        final IncrementalStatistics stats = new IncrementalStatistics();
        for (int iSim = 0; iSim < nSims_; ++iSim) {
            double portfLoss = 0.0;
            for (final DefaultSimEvent e : simsBuffer_.get(iSim)) {
                if (val > e.dayFromRef) {
                    final Date eventDate = today.add(new Period(e.dayFromRef, TimeUnit.Days));
                    portfLoss += basket_.exposure(names.get(e.nameIdx), eventDate)
                            * (1.0 - getEventRecovery(e.nameIdx));
                }
            }
            stats.add(Math.min(Math.max(portfLoss - attach, 0.0), detach - attach));
        }
        // half-width via standard-normal quantile of (1+conf)/2
        final org.jquantlib.math.distributions.InverseCumulativeNormal inv =
                new org.jquantlib.math.distributions.InverseCumulativeNormal();
        final double q = inv.op(0.5 * (1.0 + confidencePerc));
        return new double[] {stats.mean(), stats.errorEstimate() * q};
    }
}
