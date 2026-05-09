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
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.jquantlib.Settings;
import org.jquantlib.currencies.America;
import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.credit.Basket;
import org.jquantlib.experimental.credit.DefaultEvent;
import org.jquantlib.experimental.credit.DefaultLatentModel;
import org.jquantlib.experimental.credit.DefaultSimEvent;
import org.jquantlib.experimental.credit.FactorSampler;
import org.jquantlib.experimental.credit.Issuer;
import org.jquantlib.experimental.credit.Issuer.KeyCurvePair;
import org.jquantlib.experimental.credit.LatentModel;
import org.jquantlib.experimental.credit.NorthAmericaCorpDefaultKey;
import org.jquantlib.experimental.credit.Pool;
import org.jquantlib.experimental.credit.RandomDefaultLM;
import org.jquantlib.experimental.credit.Seniority;
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
 * Phase 4m.7b kernel test for {@link RandomDefaultLM}. End-to-end MC
 * simulation against a tiny basket with flat hazard-rate curves; uses Sobol
 * sequences with a fixed seed for determinism.
 *
 * <p>Tolerance tier: LOOSE (absolute 5e-2 / relative 5%) — Monte-Carlo
 * convergence with N=2000 paths gives ~1/sqrt(N)=2.2% standard error on
 * default probabilities; we test against analytical references with
 * generous slack.
 *
 * <p>Cross-validated semantically against C++ v1.42.1 ql/experimental/credit/
 * randomdefaultlatentmodel.{hpp}: probAtLeastNEvents counts paths whose
 * defaulting-name count meets the threshold; expectedTrancheLoss
 * accumulates clipped {@code [0, detach-attach]} loss per path.
 */
public class RandomDefaultLMTest {

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

    /**
     * Build a 3-name basket where each name has a flat hazard-rate of
     * {@code lambda}. Each name has notional 100 with attach=0/detach=1
     * (full equity tranche). Recovery = 0.
     *
     * <p>The issuer's KeyCurvePair must use the same DefaultProbKey instance
     * that the Pool indexes (so {@code Issuer.defaultProbability(key)} finds
     * the right curve via {@code key.equals}). We use NorthAmericaCorpDefaultKey
     * for both — its eventTypes (FailureToPay + Bankruptcy + Restructuring)
     * are populated by the constructor and {@code DefaultProbKey.equals}
     * compares on those.
     */
    private Basket buildBasket(final double lambda) {
        final Pool pool = new Pool();
        final NorthAmericaCorpDefaultKey k = new NorthAmericaCorpDefaultKey(usd, Seniority.SnrFor);

        final List<String> names = Arrays.asList("A", "B", "C");
        final List<Double> notionals = Arrays.asList(100.0, 100.0, 100.0);

        for (final String n : names) {
            final FlatHazardRate hz = new FlatHazardRate(today, lambda, dc);
            final Handle<DefaultProbabilityTermStructure> curveH =
                    new Handle<DefaultProbabilityTermStructure>(hz);
            // Construct an issuer with a single KeyCurvePair pre-built around
            // the SAME key the pool indexes — bypasses the eventTypes/
            // currencies/seniorities ctor (which would build a different key).
            final List<KeyCurvePair> probabilities = new ArrayList<>();
            probabilities.add(new KeyCurvePair(k, curveH));
            final Issuer issuer = new Issuer(probabilities,
                    new TreeSet<DefaultEvent>(Issuer.EARLIER_THAN));
            pool.add(n, issuer, k);
        }
        return new Basket(today, names, notionals, pool, 0.0, 1.0);
    }

    /**
     * Build a single-factor Gaussian DefaultLatentModel with all weights
     * equal to {@code w} (correlation rho = w^2). 3 names.
     */
    private DefaultLatentModel<GaussianCopulaPolicy> buildModel(final double w) {
        final List<List<Double>> weights = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            weights.add(new ArrayList<>(Collections.singletonList(w)));
        }
        return new DefaultLatentModel<>(weights, new GaussianCopulaPolicy(weights),
                LatentModel.IntegrationType.GaussianQuadrature);
    }

    @Test
    public void mcSimulationProducesEventsForHighDefaultProbability() {
        // Very high hazard rate → most paths produce defaults within horizon.
        final Basket basket = buildBasket(0.50);  // lambda = 50% / year
        final DefaultLatentModel<GaussianCopulaPolicy> model = buildModel(Math.sqrt(0.20));
        final FactorSampler<GaussianCopulaPolicy> sampler = new FactorSampler<>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final RandomDefaultLM<GaussianCopulaPolicy> mc = new RandomDefaultLM<>(
                model, sampler, /*recoveries=*/null, /*nSims=*/500, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);
        mc.calculate();

        // After 500 sims, count total events. With lambda=0.5 and 3 names, the
        // 5y horizon default-prob is 1 - exp(-0.5*5) = 91.8% per name, so
        // expected events per sim ≈ 2.7 ⇒ total events ≈ 1350.
        int total = 0;
        for (int i = 0; i < mc.nSims(); ++i) {
            for (final DefaultSimEvent e : mc.getSim(i)) {
                total++;
                assertTrue("nameIdx in range", e.nameIdx >= 0 && e.nameIdx < 3);
                assertTrue("dayFromRef >= 0", e.dayFromRef >= 0);
            }
        }
        assertTrue("expected many events (got " + total + ")", total > 100);
    }

    @Test
    public void probAtLeastNEventsConvergesToAnalytical() {
        // Independent (zero-correlation) names → P(>=k defaults) = binomial.
        // With p_i = P(default by 1y) and rho=0:
        //   P(>=1 default) = 1 - (1-p)^3
        // Use a very small lambda for analytical comparison.
        final double lambda = 0.10;  // 10% / year
        final Basket basket = buildBasket(lambda);
        // Build a zero-correlation model: w = 0 → all idiosyncratic.
        final DefaultLatentModel<GaussianCopulaPolicy> model = buildModel(0.0);
        final FactorSampler<GaussianCopulaPolicy> sampler = new FactorSampler<>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final RandomDefaultLM<GaussianCopulaPolicy> mc = new RandomDefaultLM<>(
                model, sampler, /*recoveries=*/null, /*nSims=*/2000, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);

        // Probability of default by 1y for FlatHazardRate: 1 - exp(-lambda * t),
        // where t uses Actual360. 1y = 365 days actual ⇒ t = 365/360 ≈ 1.014.
        final Date oneYear = today.add(new Period(1, TimeUnit.Years));
        final double tYear = dc.yearFraction(today, oneYear);
        final double p = 1.0 - Math.exp(-lambda * tYear);

        // Analytical P(>=1 default among 3 independent names)
        final double analyticalP1 = 1.0 - Math.pow(1.0 - p, 3);

        final double mcP1 = mc.probAtLeastNEvents(1, oneYear);
        // Expected SE ≈ sqrt(p(1-p)/N) ≈ sqrt(0.27*0.73/2000) ≈ 0.01;
        // allow 4σ slack ≈ 0.04.
        assertEquals("P(>=1 default by 1y)", analyticalP1, mcP1, 0.05);
        // Sanity: P(>=0) == 1.0 unconditionally.
        assertEquals(1.0, mc.probAtLeastNEvents(0, oneYear), 0.0);
    }

    @Test
    public void expectedTrancheLossNonNegativeAndBounded() {
        final Basket basket = buildBasket(0.10);
        final DefaultLatentModel<GaussianCopulaPolicy> model = buildModel(Math.sqrt(0.20));
        final FactorSampler<GaussianCopulaPolicy> sampler = new FactorSampler<>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final RandomDefaultLM<GaussianCopulaPolicy> mc = new RandomDefaultLM<>(
                model, sampler, /*recoveries=*/null, /*nSims=*/500, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);

        final Date oneYear = today.add(new Period(1, TimeUnit.Years));
        final double etl = mc.expectedTrancheLoss(oneYear);
        // Tranche width = detach-attach = 300 (full basket).
        assertTrue("ETL >= 0 (got " + etl + ")", etl >= 0.0);
        assertTrue("ETL <= 300 (got " + etl + ")", etl <= 300.0);
        // ETL with confidence band — should match the [0] of the interval.
        final double[] band = mc.expectedTrancheLossInterval(oneYear, 0.95);
        assertEquals("ETL == band[0]", etl, band[0], 1.0e-12);
        assertTrue("half-width >= 0", band[1] >= 0.0);
    }

    @Test
    public void recoveryAffectsExpectedLoss() {
        final Basket basket1 = buildBasket(0.10);
        final Basket basket2 = buildBasket(0.10);
        final DefaultLatentModel<GaussianCopulaPolicy> model1 = buildModel(Math.sqrt(0.20));
        final DefaultLatentModel<GaussianCopulaPolicy> model2 = buildModel(Math.sqrt(0.20));
        final FactorSampler<GaussianCopulaPolicy> s1 = new FactorSampler<>(
                new SobolRsg(model1.copula().numFactors(), 42), model1.copula());
        final FactorSampler<GaussianCopulaPolicy> s2 = new FactorSampler<>(
                new SobolRsg(model2.copula().numFactors(), 42), model2.copula());

        // Same seed → same defaults; only recovery differs.
        final RandomDefaultLM<GaussianCopulaPolicy> mcLowRR = new RandomDefaultLM<>(
                model1, s1, /*recoveries=*/Arrays.asList(0.0, 0.0, 0.0), 200, 1.0e-4);
        final RandomDefaultLM<GaussianCopulaPolicy> mcHighRR = new RandomDefaultLM<>(
                model2, s2, /*recoveries=*/Arrays.asList(0.6, 0.6, 0.6), 200, 1.0e-4);
        mcLowRR.setBasket(basket1);
        mcHighRR.setBasket(basket2);

        final Date oneYear = today.add(new Period(1, TimeUnit.Years));
        final double etlLow = mcLowRR.expectedTrancheLoss(oneYear);
        final double etlHigh = mcHighRR.expectedTrancheLoss(oneYear);
        // Higher recovery → lower loss.
        assertTrue("higher recovery should lower ETL: low=" + etlLow + " high=" + etlHigh,
                etlHigh < etlLow);
        // Specifically, scale: high-RR loss should be ~40% of low-RR loss.
        if (etlLow > 1e-3) {
            assertEquals("ratio ~ (1-0.6)/(1-0.0) = 0.4",
                    0.4, etlHigh / etlLow, 0.05);
        }
    }
}
