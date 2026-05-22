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
import java.util.Map;
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
                    new TreeSet<>(Issuer.EARLIER_THAN));
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
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final var mc = new RandomDefaultLM<GaussianCopulaPolicy>(
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
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final var mc = new RandomDefaultLM<GaussianCopulaPolicy>(
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
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final var mc = new RandomDefaultLM<GaussianCopulaPolicy>(
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
        final var s1 = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(model1.copula().numFactors(), 42), model1.copula());
        final var s2 = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(model2.copula().numFactors(), 42), model2.copula());

        // Same seed → same defaults; only recovery differs.
        final var mcLowRR = new RandomDefaultLM<GaussianCopulaPolicy>(
                model1, s1, /*recoveries=*/Arrays.asList(0.0, 0.0, 0.0), 200, 1.0e-4);
        final var mcHighRR = new RandomDefaultLM<GaussianCopulaPolicy>(
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

    // ====== Phase 4m.7c WI-3 — loss-distribution / VaR tests =================

    @Test
    public void lossDistributionIsMonotoneNonDecreasing() {
        final Basket basket = buildBasket(0.10);
        final DefaultLatentModel<GaussianCopulaPolicy> model = buildModel(Math.sqrt(0.20));
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final var mc = new RandomDefaultLM<GaussianCopulaPolicy>(
                model, sampler, /*recoveries=*/null, /*nSims=*/500, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);

        final Date oneYear = today.add(new Period(1, TimeUnit.Years));
        final Map<Double, Double> dist = mc.lossDistribution(oneYear);
        assertTrue("non-empty distribution", !dist.isEmpty());

        // Cumulative-prob map must be non-decreasing in loss key, end <= 1.
        Double prevKey = null;
        Double prevVal = null;
        for (final Map.Entry<Double, Double> e : dist.entrySet()) {
            if (prevKey != null) {
                assertTrue("loss key increases: prev=" + prevKey + " cur=" + e.getKey(),
                        e.getKey() >= prevKey);
                assertTrue("cumulative prob non-decreasing: prev=" + prevVal + " cur=" + e.getValue(),
                        e.getValue() >= prevVal - 1.0e-12);
            }
            prevKey = e.getKey();
            prevVal = e.getValue();
            assertTrue("prob in [0,1]", e.getValue() >= 0.0 && e.getValue() <= 1.0 + 1.0e-12);
        }
        assertEquals("final cumulative prob ≈ 1", 1.0, prevVal, 1.0e-9);
    }

    @Test
    public void expectedShortfallExceedsPercentile() {
        final Basket basket = buildBasket(0.30);  // bigger lambda for more loss
        final DefaultLatentModel<GaussianCopulaPolicy> model = buildModel(Math.sqrt(0.20));
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final var mc = new RandomDefaultLM<GaussianCopulaPolicy>(
                model, sampler, /*recoveries=*/null, /*nSims=*/500, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);

        final Date oneYear = today.add(new Period(1, TimeUnit.Years));
        final double q90 = mc.percentile(oneYear, 0.90);
        final double es90 = mc.expectedShortfall(oneYear, 0.90);
        // ES is the conditional mean above the quantile -> >= quantile.
        assertTrue("ES90 >= q90: q90=" + q90 + " es90=" + es90,
                es90 >= q90 - 1.0e-9);
        // both are bounded above by tranche width (300).
        assertTrue("q90 <= 300", q90 <= 300.0);
        assertTrue("es90 <= 300", es90 <= 300.0);
    }

    @Test
    public void percentileMonotoneInP() {
        final Basket basket = buildBasket(0.30);
        final DefaultLatentModel<GaussianCopulaPolicy> model = buildModel(Math.sqrt(0.20));
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final var mc = new RandomDefaultLM<GaussianCopulaPolicy>(
                model, sampler, /*recoveries=*/null, /*nSims=*/500, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);

        final Date oneYear = today.add(new Period(1, TimeUnit.Years));
        final double q50 = mc.percentile(oneYear, 0.50);
        final double q75 = mc.percentile(oneYear, 0.75);
        final double q90 = mc.percentile(oneYear, 0.90);
        // monotone non-decreasing in p.
        assertTrue("q50 <= q75", q50 <= q75 + 1.0e-9);
        assertTrue("q75 <= q90", q75 <= q90 + 1.0e-9);
    }

    @Test
    public void splitVaRLevelSumsToTotalLoss() {
        final Basket basket = buildBasket(0.30);
        final DefaultLatentModel<GaussianCopulaPolicy> model = buildModel(Math.sqrt(0.20));
        final var sampler = new FactorSampler<GaussianCopulaPolicy>(
                new SobolRsg(model.copula().numFactors(), 42), model.copula());
        final var mc = new RandomDefaultLM<GaussianCopulaPolicy>(
                model, sampler, /*recoveries=*/null, /*nSims=*/500, /*accuracy=*/1.0e-4);
        mc.setBasket(basket);

        final Date oneYear = today.add(new Period(1, TimeUnit.Years));
        // Pick a moderate threshold strictly inside the loss range so >0 paths
        // exceed it (avoids the degenerate "no paths > targetLoss" case where
        // splitStats are all empty by C++ design).
        final double targetLoss = mc.percentile(oneYear, 0.50);  // median
        // Split should be a 3-element vector with non-negative entries summing
        // approximately to targetLoss (per-name relative attributions sum to 1
        // by construction within each path that exceeds the threshold; over
        // many such paths the means sum to ~1 -> times targetLoss = targetLoss).
        if (targetLoss > 0.0 && targetLoss < 290.0) {
            final double[] split = mc.splitVaRLevel(oneYear, targetLoss);
            assertEquals("splitVaRLevel returns one entry per live name",
                    basket.remainingSize(), split.length);
            double sum = 0.0;
            for (final double s : split) {
                assertTrue("each split >= 0", s >= -1.0e-9);
                sum += s;
            }
            assertEquals("sum of splits ≈ targetLoss",
                    targetLoss, sum, 0.20 * targetLoss + 1.0);
        } else {
            // Degenerate case: percentile sits at edge; just verify shape.
            final double[] split = mc.splitVaRLevel(oneYear, 50.0);
            assertEquals(basket.remainingSize(), split.length);
        }
    }
}
