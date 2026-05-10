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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Random spot-recovery latent variable Monte-Carlo simulation for an
 * arbitrary copula.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code template<class copulaPolicy, class USNG = SobolRsg> class RandomLossLM}
 * (declared in {@code ql/experimental/credit/randomlosslatentmodel.hpp}).
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Extends {@link RandomDefaultLM} by sampling a stochastic recovery
 * value per default event from a {@link SpotRecoveryLatentModel}. The
 * realised recovery is drawn from the spot-recovery copula at the
 * simulated default time.
 *
 * <p>Java vs C++:
 * <ul>
 *   <li>The C++ template uses CRTP for static dispatch on
 *       {@code nextSample}, {@code initDates}, {@code getEventRecovery}.
 *       Java uses composition: this class extends {@code RandomDefaultLM}
 *       to inherit all loss-distribution / VaR statistics, then
 *       overrides {@link #calculate()} to wire its own simulation
 *       producer (spot-recovery aware) and tracks recoveries via a
 *       per-event side map keyed on identity. The base
 *       {@link #getEventRecovery(int)} hook returns the per-name
 *       average from the side map at lookup time, so existing
 *       statistics that index by {@code nameIdx} get the realised
 *       average across paths within a single basket.</li>
 *   <li>The C++ {@code simEvent<RandomLossLM>} uses an 8-bit
 *       {@code compactRR} field with quantisation step
 *       {@code rrGranular = 1/256}. The Java port stores recovery as
 *       full {@code double} (no quantisation; gains ~7 decimal digits
 *       of precision at a cost of 8 bytes per event vs 1 in C++).</li>
 *   <li>The basket / model size relationship: {@code 2 * basket.size()
 *       == copula.size()} (default + recovery rows).</li>
 * </ul>
 *
 * <p>Phase 4m.7c WI-5 foundation.
 *
 * @param <P> the {@link CopulaPolicy} subtype controlling distributions
 */
public class RandomLossLM<P extends CopulaPolicy> extends RandomDefaultLM<P> {

    private final SpotRecoveryLatentModel<P> spotCopula_;
    private final FactorSampler<P> sampler2_;
    private final double accuracy2_;

    /**
     * Side map: simEvent identity-hash -> sampled recovery in [0,1].
     * Populated during {@link #calculate()}; read by
     * {@link #getEventRecovery(int)} at statistic time.
     *
     * <p>Implemented as a parallel {@code List<List<Double>>} aligned with the
     * base {@code simsBuffer_} so each event {@code simsBuffer_[iSim].get(iEvt)}
     * has its recovery at {@code recoveriesBuffer_[iSim].get(iEvt)}.
     */
    private final List<List<Double>> recoveriesBuffer_ = new ArrayList<>();

    /**
     * Most-recent per-name realised recovery (running average over paths).
     * Updated as each path's events are stored. Used by
     * {@link #getEventRecovery(int)} as the per-name proxy when the base
     * statistics need a single recovery per name.
     */
    private double[] avgRecoveryByName_;
    private int[] recoveryCountByName_;

    private boolean calculated2_ = false;

    /**
     * @param spotCopula  spot-recovery latent model (provides default + RR
     *                    inversions; size = 2 * basket.size())
     * @param sampler     factor sampler whose dimension matches
     *                    {@code spotCopula.numFactors()}
     * @param nSims       number of Monte-Carlo paths
     * @param accuracy    accuracy for the inverse-time Brent solver
     */
    public RandomLossLM(final SpotRecoveryLatentModel<P> spotCopula,
                        final FactorSampler<P> sampler,
                        final int nSims,
                        final double accuracy) {
        super(buildShadowDefaultModel(spotCopula),
              sampler,
              defaultRecoveries(spotCopula),
              nSims,
              accuracy);
        this.spotCopula_ = spotCopula;
        this.sampler2_ = sampler;
        this.accuracy2_ = accuracy;
    }

    private static <P extends CopulaPolicy> DefaultLatentModel<P> buildShadowDefaultModel(
            final SpotRecoveryLatentModel<P> spotCopula) {
        QL.require(spotCopula != null, "spotCopula must not be null");
        QL.require(spotCopula.size() % 2 == 0,
                "spot copula size must be even (default+RR rows)");
        final int n = spotCopula.numNames();
        final List<List<Double>> defWeights = new ArrayList<>(n);
        final List<List<Double>> all = spotCopula.factorWeights();
        for (int i = 0; i < n; ++i) {
            defWeights.add(new ArrayList<>(all.get(i)));
        }
        return new DefaultLatentModel<P>(defWeights, spotCopula.copula(),
                LatentModel.IntegrationType.GaussianQuadrature);
    }

    private static List<Double> defaultRecoveries(final SpotRecoveryLatentModel<?> spotCopula) {
        // Placeholder; overridden by getEventRecovery below.
        final int n = spotCopula.numNames();
        final List<Double> z = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) z.add(0.0);
        return z;
    }

    /** Underlying spot-recovery latent model. */
    public SpotRecoveryLatentModel<P> spotCopula() {
        return spotCopula_;
    }

    /**
     * Bind the basket. Mirrors C++ {@code resetModel()} dispatched via
     * {@code setBasket}. Validates {@code 2 * basket.size() ==
     * spotCopula.size()}.
     */
    @Override
    public void setBasket(final Basket basket) {
        QL.require(basket != null, "basket must not be null");
        QL.require(2 * basket.size() == spotCopula_.size(),
                "Incompatible basket and model sizes.");
        super.basket_ = basket;
        spotCopula_.resetBasket(basket);
        super.simsBuffer_.clear();
        this.recoveriesBuffer_.clear();
        this.calculated2_ = false;
        this.avgRecoveryByName_ = new double[basket.size()];
        this.recoveryCountByName_ = new int[basket.size()];
    }

    /** Run the Monte-Carlo simulation. Idempotent. */
    @Override
    public void calculate() {
        if (calculated2_) return;
        QL.require(super.basket_ != null, "setBasket() must be called before calculate()");
        // Pre-compute per-name horizon default probabilities.
        final Date today = new Settings().evaluationDate();
        final Date maxHorizonDate = today.add(new Period(MAX_HORIZON_DAYS, TimeUnit.Days));
        final Pool pool = super.basket_.pool();
        final List<DefaultProbKey> dks = super.basket_.defaultKeys();
        final List<String> names = super.basket_.names();
        final double[] horizonDPs = new double[super.basket_.size()];
        for (int iName = 0; iName < super.basket_.size(); ++iName) {
            final Handle<DefaultProbabilityTermStructure> dts =
                    pool.get(names.get(iName)).defaultProbability(dks.get(iName));
            horizonDPs[iName] = dts.currentLink().defaultProbability(maxHorizonDate, true);
        }

        for (int i = 0; i < super.nSims_; ++i) {
            final double[] sample = sampler2_.nextSequence().value();
            nextSample(sample, horizonDPs);
        }
        calculated2_ = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        recoveriesBuffer_.clear();
        calculated2_ = false;
        if (avgRecoveryByName_ != null) {
            java.util.Arrays.fill(avgRecoveryByName_, 0.0);
            java.util.Arrays.fill(recoveryCountByName_, 0);
        }
    }

    /**
     * Process one Monte-Carlo path. Mirrors C++ {@code RandomLossLM::nextSample}:
     * for each defaulting name within the horizon, additionally invert the
     * spot-recovery latent variable to derive the realised recovery rate.
     */
    private void nextSample(final double[] sample, final double[] horizonDPs) {
        final List<DefaultSimEvent> path = new ArrayList<>();
        final List<Double> rrs = new ArrayList<>();
        final Pool pool = super.basket_.pool();
        final List<DefaultProbKey> dks = super.basket_.defaultKeys();
        final List<String> names = super.basket_.names();
        final Date today = new Settings().evaluationDate();
        final int n = spotCopula_.numNames();

        for (int iName = 0; iName < n; ++iName) {
            final double y = spotCopula_.latentVarValue(sample, iName);
            final double simDefaultProb = spotCopula_.cumulativeY(y, iName);
            if (horizonDPs[iName] >= simDefaultProb) {
                final Handle<DefaultProbabilityTermStructure> dts =
                        pool.get(names.get(iName)).defaultProbability(dks.get(iName));
                final DefaultProbabilityTermStructure curve = dts.currentLink();
                final Date curveRef = curve.referenceDate();

                final Ops.DoubleOp root = (final double t) -> {
                    QL.require(t >= 0.0, "t < 0 in default-time inversion");
                    return curve.defaultProbability(
                            curveRef.add(new Period((int) t, TimeUnit.Days)), true)
                            - simDefaultProb;
                };
                final double dayStride = new Brent().solve(root, accuracy2_, 0.0, 1.0);

                Date eventDate = today.add(new Period((int) dayStride, TimeUnit.Days));
                if (eventDate.lt(curveRef)) eventDate = curveRef;

                final double latentRRSample = spotCopula_.latentRRVarValue(sample, iName);
                final double recovery =
                        spotCopula_.conditionalRecovery(latentRRSample, iName, eventDate);
                path.add(new DefaultSimEvent(iName, (int) dayStride));
                rrs.add(recovery);
                // Update the per-name running average.
                avgRecoveryByName_[iName] =
                        (avgRecoveryByName_[iName] * recoveryCountByName_[iName] + recovery)
                                / (recoveryCountByName_[iName] + 1);
                recoveryCountByName_[iName]++;
            }
        }
        super.simsBuffer_.add(path);
        recoveriesBuffer_.add(rrs);
    }

    /**
     * Recovery for name {@code nameIdx}. Returns the running per-name
     * average across all simulated paths (i.e., per-name expected recovery
     * realised in this run).
     *
     * <p>This is the "per-name" approximation the base statistics use; the
     * true per-event recovery is stored in {@link #recoveriesBuffer_} and
     * accessed via {@link #getRealisedRecovery(int, int)}.
     */
    @Override
    protected double getEventRecovery(final int nameIdx) {
        if (recoveryCountByName_ == null || recoveryCountByName_[nameIdx] == 0) {
            return 0.0;
        }
        return avgRecoveryByName_[nameIdx];
    }

    /**
     * Per-event realised recovery for path {@code iSim}, event index {@code iEvt}.
     */
    public double getRealisedRecovery(final int iSim, final int iEvt) {
        return recoveriesBuffer_.get(iSim).get(iEvt);
    }

    /**
     * Per-name realised average recovery across all paths. Read-only view.
     */
    public double[] averageRecoveriesByName() {
        return avgRecoveryByName_ == null ? null : avgRecoveryByName_.clone();
    }
}
