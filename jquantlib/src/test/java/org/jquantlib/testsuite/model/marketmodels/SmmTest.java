/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 1.1 Round 1
 — D5-E worktree, SMM multi-step closure.

 This source code is release under the BSD License.

 This file is a faithful Java port of v1.42.1 test-suite/marketmodel_smm.cpp
 @ 099987f0ca2c11c505dc4348cdb9ce01a598e1e5.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */
package org.jquantlib.testsuite.model.marketmodels;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.SimpleDayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.jquantlib.model.marketmodels.AccountingEngine;
import org.jquantlib.model.marketmodels.BrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.MarketModelEvolver;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.correlations.TimeHomogeneousForwardCorrelation;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.evolvers.LogNormalCotSwapRatePc;
import org.jquantlib.model.marketmodels.models.AbcdVol;
import org.jquantlib.model.marketmodels.models.FlatVol;
import org.jquantlib.model.marketmodels.products.MultiProductComposite;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoterminalSwaps;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoterminalSwaptions;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Faithful Java port of v1.42.1 {@code test-suite/marketmodel_smm.cpp}
 * @ 099987f0ca2c11c505dc4348cdb9ce01a598e1e5.
 *
 * <p>Hosts the single C++ test
 * {@code testMultiStepCoterminalSwapsAndSwaptions} which exercises
 * coterminal swap and swaption pricing in a lognormal coterminal swap
 * rate market model using a {@link LogNormalCotSwapRatePc} evolver. The
 * C++ test is {@code *precondition(if_speed(Slow))}; we mirror that by
 * gating on the {@code ql.slowTests} system property so default
 * {@code mvn test} runs do not pay the Monte-Carlo cost.
 *
 * <p>Fixture is private to this class (10y horizon, {@code displacement
 * = 0.02}, distinct vol curve) and does not share {@link
 * MarketModelTestSetup}'s 5y/0.0-displacement fixture used by the
 * other 16 marketmodel.cpp tests.
 */
public class SmmTest {

    public SmmTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // ------------------------------------------------------------------
    // File-scope fixture (cpp:61-76)
    // ------------------------------------------------------------------
    private Date todaysDate;
    private Date endDate;
    private double[] rateTimes;
    private double[] accruals;
    private Calendar calendar;
    private SimpleDayCounter dayCounter;
    private double[] todaysForwards;
    private double[] todaysSwaps;
    private double displacement;
    private double[] todaysDiscounts;
    private double[] volatilities;
    private double longTermCorrelation;
    private double beta;
    private long seed_;
    private int paths_;

    @Before
    public void setup() {
        // Times (cpp:80-93)
        calendar = new NullCalendar();
        todaysDate = new Settings().evaluationDate();
        endDate = todaysDate.add(new Period(10, TimeUnit.Years));
        final Schedule dates = new Schedule(todaysDate, endDate,
                new Period(Frequency.Semiannual),
                calendar, BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward, false);
        rateTimes = new double[dates.size() - 1];
        accruals = new double[rateTimes.length - 1];
        dayCounter = new SimpleDayCounter();
        for (int i = 1; i < dates.size(); ++i) {
            rateTimes[i - 1] = dayCounter.yearFraction(todaysDate, dates.dates().get(i));
        }
        for (int i = 1; i < rateTimes.length; ++i) {
            accruals[i - 1] = rateTimes[i] - rateTimes[i - 1];
        }

        // Rates & displacement (cpp:96-101)
        todaysForwards = new double[accruals.length];
        displacement = 0.02;
        for (int i = 0; i < todaysForwards.length; ++i) {
            todaysForwards[i] = 0.03 + 0.0010 * i;
        }
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(todaysForwards);
        todaysSwaps = cs.coterminalSwapRates();

        // Discounts (cpp:106-111)
        todaysDiscounts = new double[rateTimes.length];
        todaysDiscounts[0] = 0.95;
        for (int i = 1; i < rateTimes.length; ++i) {
            todaysDiscounts[i] = todaysDiscounts[i - 1]
                    / (1.0 + todaysForwards[i - 1] * accruals[i - 1]);
        }

        // Swaption vols (cpp:114-144)
        final double[] mktVols = {
                0.15541283, 0.18719678, 0.20890740, 0.22318179,
                0.23212717, 0.23731450, 0.23988649, 0.24066384,
                0.24023111, 0.23900189, 0.23726699, 0.23522952,
                0.23303022, 0.23076564, 0.22850101, 0.22627951,
                0.22412881, 0.22206569, 0.22009939
        };
        volatilities = new double[todaysSwaps.length];
        for (int i = 0; i < todaysSwaps.length; ++i) {
            volatilities[i] = todaysSwaps[i] * mktVols[i] / (todaysSwaps[i] + displacement);
        }

        // Correlation / MC (cpp:147-160)
        longTermCorrelation = 0.5;
        beta = 0.2;
        seed_ = 42L;
        // Java tests use the C++ _DEBUG path-count (127) by default to keep
        // wall-time manageable; full release-build count is 32767.
        paths_ = 127;
    }

    /**
     * Port of {@code makeMarketModel} (cpp:196-256). Returns {@link FlatVol}
     * for {@code ExponentialCorrelationFlatVolatility} or {@link AbcdVol}
     * for {@code ExponentialCorrelationAbcdVolatility}.
     */
    private MarketModel makeMarketModel(final EvolutionDescription evolution,
            final int numberOfFactors, final boolean abcdNotFlat) {
        final double[] bumpedRates = new double[todaysForwards.length];
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(todaysForwards);
        final double[] usedRates = cs.coterminalSwapRates();
        System.arraycopy(usedRates, 0, bumpedRates, 0, usedRates.length);
        final double[] bumpedVols = volatilities.clone();

        final Matrix correlations = ExponentialForwardCorrelation.exponentialCorrelations(
                doubleArrayToList(evolution.rateTimes()), longTermCorrelation, beta, 1.0, 0.0);
        final PiecewiseConstantCorrelation corr = new TimeHomogeneousForwardCorrelation(
                correlations, doubleArrayToList(evolution.rateTimes()));

        final double[] displacements = new double[bumpedRates.length];
        for (int i = 0; i < displacements.length; ++i) {
            displacements[i] = displacement;
        }
        if (abcdNotFlat) {
            return new AbcdVol(0.0, 0.0, 1.0, 1.0, bumpedVols, corr, evolution,
                    numberOfFactors, bumpedRates, displacements);
        }
        return new FlatVol(bumpedVols, corr, evolution, numberOfFactors, bumpedRates, displacements);
    }

    private static List<Double> doubleArrayToList(final double[] in) {
        final List<Double> out = new ArrayList<>(in.length);
        for (final double v : in) {
            out.add(v);
        }
        return out;
    }

    private SequenceStatistics simulate(final MarketModelEvolver evolver,
            final MarketModelMultiProduct product) {
        final int initialNumeraire = evolver.numeraires()[0];
        final double initialNumeraireValue = todaysDiscounts[initialNumeraire];
        final AccountingEngine engine = new AccountingEngine(evolver, product, initialNumeraireValue);
        final SequenceStatistics stats = new SequenceStatistics(product.numberOfProducts());
        engine.multiplePathValues(stats, paths_);
        return stats;
    }

    private void checkCoterminalSwapsAndSwaptions(final SequenceStatistics stats,
            final double fixedRate, final StrikedTypePayoff[] displacedPayoff, final String config) {

        final Array results = stats.mean();
        final Array errors = stats.errorEstimate();
        final int N = todaysForwards.length;
        final double[] discrepancies = new double[N];

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(todaysForwards);

        // Check swaps
        final double[] expectedNPVs = new double[todaysSwaps.length];
        double maxError = -Double.MAX_VALUE;
        for (int i = 0; i < N; ++i) {
            final double expectedNPV = cs.coterminalSwapAnnuity(i, i)
                    * (todaysSwaps[i] - fixedRate) * todaysDiscounts[i];
            expectedNPVs[i] = expectedNPV;
            discrepancies[i] = (results.get(i) - expectedNPVs[i]) / errors.get(i);
            maxError = Math.max(Math.abs(discrepancies[i]), maxError);
        }
        if (maxError > 0.5) {
            final StringBuilder msg = new StringBuilder(config).append("\n");
            for (int i = 0; i < N; ++i) {
                msg.append(String.format("  swap %d: NPV=%.6f +- %.6f, exp=%.6f, disc=%.4f stderr%n",
                        i + 1, results.get(i), errors.get(i), expectedNPVs[i], discrepancies[i]));
            }
            fail("coterminal swap NPV test failed:\n" + msg.toString());
        }

        // Check swaptions
        maxError = 0.0;
        final double[] expectedSwaptions = new double[N];
        for (int i = 0; i < N; ++i) {
            final double expectedSwaption =
                    new BlackCalculator(displacedPayoff[i],
                            todaysSwaps[i] + displacement,
                            volatilities[i] * Math.sqrt(rateTimes[i]),
                            cs.coterminalSwapAnnuity(i, i) * todaysDiscounts[i]).value();
            expectedSwaptions[i] = expectedSwaption;
            discrepancies[i] = (results.get(N + i) - expectedSwaptions[i]) / errors.get(N + i);
            maxError = Math.max(Math.abs(discrepancies[i]), maxError);
        }
        if (maxError > 2.0) {
            final StringBuilder msg = new StringBuilder(config).append("\n");
            for (int i = 1; i <= N; ++i) {
                msg.append(String.format("  swaption %d: %.6f +- %.6f, exp=%.6f, disc=%.4f stderr%n",
                        i, results.get(2 * N - i), errors.get(2 * N - i),
                        expectedSwaptions[N - i], discrepancies[N - i]));
            }
            fail("coterminal swaption test failed:\n" + msg.toString());
        }
    }

    /**
     * Faithful port of v1.42.1 {@code test-suite/marketmodel_smm.cpp:423-503}
     * {@code BOOST_AUTO_TEST_CASE(testMultiStepCoterminalSwapsAndSwaptions,
     * *precondition(if_speed(Slow)))}.
     *
     * <p>Gated on {@code -Dql.slowTests=1} (or any non-null value) per the
     * {@code if_speed(Slow)} C++ precondition. Default JUnit runs skip.
     */
    @Test
    public void testMultiStepCoterminalSwapsAndSwaptions() {
        Assume.assumeTrue("Slow test — set -Dql.slowTests=1 to enable",
                System.getProperty("ql.slowTests") != null);

        final double fixedRate = 0.04;

        // Swaps (cpp:434-437)
        final double[] swapPaymentTimes = new double[rateTimes.length - 1];
        System.arraycopy(rateTimes, 1, swapPaymentTimes, 0, swapPaymentTimes.length);
        final MultiStepCoterminalSwaps swaps = new MultiStepCoterminalSwaps(
                rateTimes, accruals, accruals, swapPaymentTimes, fixedRate);

        // Swaptions (cpp:439-452)
        final double[] swaptionPaymentTimes = new double[rateTimes.length - 1];
        System.arraycopy(rateTimes, 0, swaptionPaymentTimes, 0, swaptionPaymentTimes.length);
        final StrikedTypePayoff[] displacedPayoff = new StrikedTypePayoff[todaysForwards.length];
        final StrikedTypePayoff[] undisplacedPayoff = new StrikedTypePayoff[todaysForwards.length];
        for (int i = 0; i < undisplacedPayoff.length; ++i) {
            displacedPayoff[i] = new PlainVanillaPayoff(Option.Type.Call, fixedRate + displacement);
            undisplacedPayoff[i] = new PlainVanillaPayoff(Option.Type.Call, fixedRate);
        }
        final MultiStepCoterminalSwaptions swaptions = new MultiStepCoterminalSwaptions(
                rateTimes, swaptionPaymentTimes, undisplacedPayoff);

        final MultiProductComposite product = new MultiProductComposite();
        product.add(swaps);
        product.add(swaptions);
        product.finalizeComposite();
        final EvolutionDescription evolution = product.evolution();

        // Loop over market models and measures (cpp:458-501)
        final boolean[] abcdFlavors = {false, true};
        final int factors = todaysForwards.length; // full factors

        for (final boolean abcd : abcdFlavors) {
            // Terminal and MoneyMarket measures (ProductSuggested = Terminal for Composite)
            final int[][] measures = {
                    EvolutionDescription.terminalMeasure(evolution),
                    EvolutionDescription.moneyMarketMeasure(evolution)
            };
            for (final int[] numeraires : measures) {
                final MarketModel marketModel = makeMarketModel(evolution, factors, abcd);
                final SobolBrownianGeneratorFactory generatorFactory = new SobolBrownianGeneratorFactory(
                        SobolBrownianGenerator.Ordering.Diagonal, seed_);
                final MarketModelEvolver evolver = new LogNormalCotSwapRatePc(
                        marketModel, generatorFactory, numeraires, 0);
                final String config = (abcd ? "Exp. Corr. Abcd Vol." : "Exp. Corr. Flat Vol.")
                        + ", " + factors + " factors, "
                        + (EvolutionDescription.isInTerminalMeasure(evolution, numeraires)
                                ? "Terminal" : "MoneyMarket")
                        + " measure, PC evolver";
                final SequenceStatistics stats = simulate(evolver, product);
                checkCoterminalSwapsAndSwaptions(stats, fixedRate, displacedPayoff, config);
            }
        }
    }
}
