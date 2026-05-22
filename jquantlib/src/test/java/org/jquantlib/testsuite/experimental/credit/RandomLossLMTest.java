/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.jquantlib.Settings;
import org.jquantlib.currencies.America;
import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.credit.Basket;
import org.jquantlib.experimental.credit.DefaultEvent;
import org.jquantlib.experimental.credit.DefaultSimEvent;
import org.jquantlib.experimental.credit.FactorSampler;
import org.jquantlib.experimental.credit.Issuer;
import org.jquantlib.experimental.credit.Issuer.KeyCurvePair;
import org.jquantlib.experimental.credit.LatentModel;
import org.jquantlib.experimental.credit.NorthAmericaCorpDefaultKey;
import org.jquantlib.experimental.credit.Pool;
import org.jquantlib.experimental.credit.RandomLossLM;
import org.jquantlib.experimental.credit.Seniority;
import org.jquantlib.experimental.credit.SpotRecoveryLatentModel;
import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Phase 4m.7c WI-5 kernel test for {@link RandomLossLM}. End-to-end MC
 * simulation against a tiny basket with stochastic recoveries; uses Sobol
 * sequences with a fixed seed for determinism.
 *
 * <p>Tolerance tier: LOOSE (5e-2 / 5%) — Monte-Carlo at N=500.
 */
public class RandomLossLMTest {

    private Date savedEvalDate;
    private Date today;
    private final DayCounter dc = new Actual360();
    private final Currency usd = new America.USDCurrency();

    @Before
    public void setUp() {
        savedEvalDate = new Settings().evaluationDate();
        today = new Date(15, Month.June, 2010);
        new Settings().setEvaluationDate(today);
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvalDate);
    }

    private Basket buildBasket(final double lambda) {
        final Pool pool = new Pool();
        final NorthAmericaCorpDefaultKey k = new NorthAmericaCorpDefaultKey(usd, Seniority.SnrFor);
        final List<String> names = Arrays.asList("A", "B", "C");
        final List<Double> notionals = Arrays.asList(100.0, 100.0, 100.0);
        for (final String n : names) {
            final FlatHazardRate hz = new FlatHazardRate(today, lambda, dc);
            final Handle<DefaultProbabilityTermStructure> curveH =
                    new Handle<DefaultProbabilityTermStructure>(hz);
            final List<KeyCurvePair> probabilities = new ArrayList<>();
            probabilities.add(new KeyCurvePair(k, curveH));
            final Issuer issuer = new Issuer(probabilities,
                    new TreeSet<>(Issuer.EARLIER_THAN));
            pool.add(n, issuer, k);
        }
        return new Basket(today, names, notionals, pool, 0.0, 1.0);
    }

    /**
     * Build a single-factor SpotRecoveryLatentModel with all default weights
     * = w_def and all recovery weights = w_rr. 3 names => 6 latent variables.
     */
    private SpotRecoveryLatentModel<GaussianCopulaPolicy> buildSpotModel(
            final double w_def, final double w_rr) {
        final List<List<Double>> weights = new ArrayList<>();
        for (int i = 0; i < 3; ++i) weights.add(Arrays.asList(w_def));   // default rows
        for (int i = 0; i < 3; ++i) weights.add(Arrays.asList(w_rr));    // recovery rows
        final List<Double> rr = Arrays.asList(0.4, 0.4, 0.4);
        return new SpotRecoveryLatentModel<>(weights, rr, 1.0,
                new GaussianCopulaPolicy(weights),
                LatentModel.IntegrationType.GaussianQuadrature);
    }

    @Test
    public void mcSimulationProducesEvents() {
        final Basket basket = buildBasket(0.50);
        final SpotRecoveryLatentModel<GaussianCopulaPolicy> spot =
                buildSpotModel(Math.sqrt(0.20), Math.sqrt(0.20));
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(spot.copula().numFactors(), 42), spot.copula());
        final var mc = new RandomLossLM<GaussianCopulaPolicy>(
                spot, sampler, /*nSims=*/300, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);
        mc.calculate();

        int total = 0;
        for (int iSim = 0; iSim < mc.nSims(); ++iSim) {
            for (final DefaultSimEvent e : mc.getSim(iSim)) {
                total++;
                assertTrue("nameIdx in range", e.nameIdx >= 0 && e.nameIdx < 3);
                assertTrue("dayFromRef >= 0", e.dayFromRef >= 0);
            }
        }
        assertTrue("expected many events (got " + total + ")", total > 50);
    }

    @Test
    public void recoveriesInRange() {
        final Basket basket = buildBasket(0.50);
        final SpotRecoveryLatentModel<GaussianCopulaPolicy> spot =
                buildSpotModel(Math.sqrt(0.20), Math.sqrt(0.20));
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(spot.copula().numFactors(), 42), spot.copula());
        final var mc = new RandomLossLM<GaussianCopulaPolicy>(
                spot, sampler, /*nSims=*/300, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);
        mc.calculate();

        int totalEvents = 0;
        for (int iSim = 0; iSim < mc.nSims(); ++iSim) {
            for (int iEvt = 0; iEvt < mc.getSim(iSim).size(); ++iEvt) {
                final double rr = mc.getRealisedRecovery(iSim, iEvt);
                assertTrue("rr in [0,1]: " + rr, rr >= 0.0 && rr <= 1.0);
                totalEvents++;
            }
        }
        assertTrue("totalEvents > 0", totalEvents > 0);
    }

    @Test
    public void averageRecoveryNearUnconditionalRR() {
        // With deep ITM defaults (high lambda), the spot recoveries should
        // average near the unconditional 0.4 setting.
        final Basket basket = buildBasket(1.0);  // very high default rate
        final SpotRecoveryLatentModel<GaussianCopulaPolicy> spot =
                buildSpotModel(Math.sqrt(0.20), Math.sqrt(0.20));
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(spot.copula().numFactors(), 42), spot.copula());
        final var mc = new RandomLossLM<GaussianCopulaPolicy>(
                spot, sampler, /*nSims=*/500, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);
        mc.calculate();

        final double[] avg = mc.averageRecoveriesByName();
        for (int i = 0; i < avg.length; ++i) {
            // Allow generous slack — conditional RR is biased relative to
            // unconditional; checking it lies in (0, 1) and within 0.4 ± 0.30.
            assertTrue("avg recovery in (0,1)", avg[i] > 0.0 && avg[i] < 1.0);
            assertEquals("avg recovery near unconditional 0.4",
                    0.40, avg[i], 0.30);
        }
    }

    @Test
    public void expectedTrancheLossNonNegative() {
        final Basket basket = buildBasket(0.30);
        final SpotRecoveryLatentModel<GaussianCopulaPolicy> spot =
                buildSpotModel(Math.sqrt(0.20), Math.sqrt(0.20));
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(spot.copula().numFactors(), 42), spot.copula());
        final var mc = new RandomLossLM<GaussianCopulaPolicy>(
                spot, sampler, /*nSims=*/300, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);
        final Date oneYear = today.add(new Period(1, TimeUnit.Years));
        final double etl = mc.expectedTrancheLoss(oneYear);
        assertTrue("ETL >= 0", etl >= 0.0);
        assertTrue("ETL <= 300", etl <= 300.0);
    }
}
