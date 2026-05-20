/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file ports v1.42.1 test-suite/marketmodel.cpp tests (Phase 1 closure A2-D-545).

 The reference QuantLib is licensed under the QuantLib license; this Java
 port is licensed under the BSD License.
 */
package org.jquantlib.testsuite.model.marketmodels;

import static org.jquantlib.testsuite.model.marketmodels.MarketModelTestSetup.MarketModelType.ExponentialCorrelationAbcdVolatility;
import static org.jquantlib.testsuite.model.marketmodels.MarketModelTestSetup.MarketModelType.ExponentialCorrelationFlatVolatility;
import static org.jquantlib.testsuite.model.marketmodels.MarketModelTestSetup.MeasureType.MoneyMarket;
import static org.jquantlib.testsuite.model.marketmodels.MarketModelTestSetup.MeasureType.Terminal;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.legacy.libormarkets.LmExtLinearExponentialVolModel;
import org.jquantlib.math.integrals.SegmentIntegral;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.ForwardForwardMappings;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.MarketModelEvolver;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct;
import org.jquantlib.model.marketmodels.PathwiseAccountingEngine;
import org.jquantlib.model.marketmodels.SwapForwardMappings;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateEuler;
import org.jquantlib.model.marketmodels.evolvers.MarketModelVolProcess;
import org.jquantlib.model.marketmodels.evolvers.SVDDFwdRatePc;
import org.jquantlib.model.marketmodels.evolvers.volprocesses.SquareRootAndersen;
import org.jquantlib.model.marketmodels.browniangenerators.MTBrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.models.FwdPeriodAdapter;
import org.jquantlib.model.marketmodels.models.FwdToCotSwapAdapter;
import org.jquantlib.model.marketmodels.products.MultiProductComposite;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseMultiCaplet;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseMultiDeflatedCaplet;
import org.jquantlib.model.marketmodels.products.multistep.MultiProductPathwiseWrapper;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoinitialSwaps;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoterminalSwaps;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoterminalSwaptions;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepForwards;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepInverseFloater;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepOptionlets;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepSwap;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepNothing;
import org.jquantlib.model.marketmodels.products.multistep.CallSpecifiedMultiProduct;
import org.jquantlib.model.marketmodels.products.multistep.ExerciseAdapter;
import org.jquantlib.model.marketmodels.callability.SwapRateTrigger;
import org.jquantlib.model.marketmodels.callability.NothingExerciseValue;
import org.jquantlib.model.marketmodels.callability.UpperBoundEngine;
import org.jquantlib.model.marketmodels.callability.ParametricExerciseAdapter;
import org.jquantlib.model.marketmodels.callability.TriggeredSwapExercise;
import org.jquantlib.model.marketmodels.callability.CollectNodeData;
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.math.statistics.Statistics;
import org.jquantlib.math.optimization.Simplex;
import org.jquantlib.methods.montecarlo.GenericEarlyExercise;
import org.jquantlib.methods.montecarlo.GenericLongstaffSchwartzRegression;
import org.jquantlib.methods.montecarlo.NodeData;
import org.jquantlib.math.statistics.GenericSequenceStatistics;
import org.jquantlib.model.marketmodels.callability.SwapBasisSystem;
import org.jquantlib.model.marketmodels.callability.LongstaffSchwartzExerciseStrategy;
import org.jquantlib.model.marketmodels.ConstrainedEvolver;
import org.jquantlib.model.marketmodels.ProxyGreekEngine;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateEulerConstrained;
import org.jquantlib.instruments.CashOrNothingPayoff;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.SimpleDayCounter;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepPeriodCapletSwaptions;
import org.jquantlib.model.marketmodels.products.onestep.OneStepForwards;
import org.jquantlib.model.marketmodels.products.onestep.OneStepOptionlets;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseInverseFloater;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.termstructures.volatility.AbcdCalibration;
import org.jquantlib.termstructures.volatility.AbcdFunction;
import org.jquantlib.termstructures.volatility.AbcdSquared;
import org.junit.Before;
import org.junit.Test;

/**
 * Faithful Java ports of the v1.42.1 {@code test-suite/marketmodel.cpp}
 * tests that depend on {@link MarketModelTestSetup}.
 *
 * <p>Each test uses the file-scope helpers extracted in
 * {@link MarketModelTestSetup} ({@link MarketModelTestSetup#setup()},
 * {@link MarketModelTestSetup#simulate}, {@link MarketModelTestSetup#makeMarketModel},
 * {@link MarketModelTestSetup#makeMeasure},
 * {@link MarketModelTestSetup#makeMarketModelEvolver},
 * {@link MarketModelTestSetup#checkForwardsAndOptionlets} and
 * {@link MarketModelTestSetup#checkNormalForwardsAndOptionlets}).
 *
 * <p>{@code *precondition(if_speed(Slow|Fast))} tests in C++ are gated on
 * {@code -Dql.slowTests=1} on the Java side via {@link org.junit.Assume}.
 *
 * <p>Path counts default to {@link MarketModelTestSetup#paths_}={@code 127} and
 * {@link MarketModelTestSetup#trainingPaths_}={@code 31} (the C++ {@code _DEBUG}
 * setting) to keep JUnit wall-time manageable. The C++ release build uses
 * {@code 32767} / {@code 8191}.
 *
 * <p>Several tests in the C++ file are not ported here because they require
 * substantial additional infrastructure (e.g., {@code SubProductExpectedValues}
 * harness, {@code collectNodeData}-based exercise-strategy drivers,
 * {@code ProxyGreekEngine}, {@code PathwiseVegasOuterAccountingEngine}, the
 * stochastic-vol {@code SVDDFwdRatePc} MC harness). These are tracked
 * separately and reported as BLOCKED until those dependencies land:
 * {@code testAllMultiStepProducts}, {@code testCallableSwapNaif},
 * {@code testCallableSwapLS}, {@code testCallableSwapAnderson},
 * {@code testGreeks}, {@code testPathwiseGreeks}, {@code testPathwiseVegas},
 * {@code testPathwiseMarketVegas}, {@code testStochVolForwardsAndOptionlets}.
 */
public class MarketModelTest {

    public MarketModelTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Before
    public void before() {
        MarketModelTestSetup.setup();
    }

    // ------------------------------------------------------------------
    // testOneStepForwardsAndOptionlets — cpp:700-783
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:700} {@code BOOST_AUTO_TEST_CASE(testOneStepForwardsAndOptionlets)}. */
    @Test
    public void testOneStepForwardsAndOptionlets() {
        final int N = MarketModelTestSetup.todaysForwards.length;
        final double[] forwardStrikes = new double[N];
        final Payoff[] optionletPayoffs = new Payoff[N];
        final List<StrikedTypePayoff> displacedPayoffs = new ArrayList<StrikedTypePayoff>(N);
        for (int i = 0; i < N; ++i) {
            forwardStrikes[i] = MarketModelTestSetup.todaysForwards[i] + 0.01;
            optionletPayoffs[i] = new PlainVanillaPayoff(Option.Type.Call, MarketModelTestSetup.todaysForwards[i]);
            displacedPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    MarketModelTestSetup.todaysForwards[i] + MarketModelTestSetup.displacement));
        }

        final OneStepForwards forwards = new OneStepForwards(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, forwardStrikes);
        final OneStepOptionlets optionlets = new OneStepOptionlets(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, optionletPayoffs);

        final MultiProductComposite product = new MultiProductComposite();
        product.add(forwards);
        product.add(optionlets);
        product.finalizeComposite();

        final EvolutionDescription evolution = product.evolution();

        final MarketModelTestSetup.MarketModelType[] marketModels = {
                ExponentialCorrelationFlatVolatility,
                ExponentialCorrelationAbcdVolatility
        };
        for (final MarketModelTestSetup.MarketModelType mmType : marketModels) {
            // one step must be always full factors
            final int[] testedFactors = { N };
            for (final int factors : testedFactors) {
                final MarketModelTestSetup.MeasureType[] measures = { MoneyMarket, Terminal };
                for (final MarketModelTestSetup.MeasureType measure : measures) {
                    final int[] numeraires = MarketModelTestSetup.makeMeasure(product, measure);
                    final boolean logNormal = true;
                    final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType);

                    final MarketModelTestSetup.EvolverType[] evolvers = {
                            MarketModelTestSetup.EvolverType.Pc,
                            MarketModelTestSetup.EvolverType.Balland,
                            MarketModelTestSetup.EvolverType.Ipc
                    };
                    final int stop = EvolutionDescription.isInTerminalMeasure(evolution, numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; ++i) {
                        final MTBrownianGeneratorFactory generatorFactory =
                                new MTBrownianGeneratorFactory(MarketModelTestSetup.seed_);
                        final MarketModelEvolver evolver = MarketModelTestSetup.makeMarketModelEvolver(
                                marketModel, numeraires, generatorFactory, evolvers[i]);
                        final String config = MarketModelTestSetup.marketModelTypeToString(mmType)
                                + ", " + factors + " (full) factors, "
                                + MarketModelTestSetup.measureTypeToString(measure)
                                + ", " + MarketModelTestSetup.evolverTypeToString(evolvers[i])
                                + ", MT BGF";
                        final SequenceStatistics stats = MarketModelTestSetup.simulate(evolver, product);
                        MarketModelTestSetup.checkForwardsAndOptionlets(stats, forwardStrikes,
                                displacedPayoffs, config);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // testOneStepNormalForwardsAndOptionlets — cpp:785-867
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:785} {@code BOOST_AUTO_TEST_CASE(testOneStepNormalForwardsAndOptionlets)}. */
    @Test
    public void testOneStepNormalForwardsAndOptionlets() {
        final int N = MarketModelTestSetup.todaysForwards.length;
        final double[] forwardStrikes = new double[N];
        final Payoff[] optionletPayoffs = new Payoff[N];
        final List<PlainVanillaPayoff> displacedPayoffs = new ArrayList<PlainVanillaPayoff>(N);
        for (int i = 0; i < N; ++i) {
            forwardStrikes[i] = MarketModelTestSetup.todaysForwards[i] + 0.01;
            optionletPayoffs[i] = new PlainVanillaPayoff(Option.Type.Call, MarketModelTestSetup.todaysForwards[i]);
            displacedPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    MarketModelTestSetup.todaysForwards[i] + MarketModelTestSetup.displacement));
        }

        final OneStepForwards forwards = new OneStepForwards(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, forwardStrikes);
        final OneStepOptionlets optionlets = new OneStepOptionlets(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, optionletPayoffs);

        final MultiProductComposite product = new MultiProductComposite();
        product.add(forwards);
        product.add(optionlets);
        product.finalizeComposite();

        final EvolutionDescription evolution = product.evolution();

        final MarketModelTestSetup.MarketModelType[] marketModels = {
                ExponentialCorrelationFlatVolatility,
                ExponentialCorrelationAbcdVolatility
        };
        for (final MarketModelTestSetup.MarketModelType mmType : marketModels) {
            final int[] testedFactors = { N };
            for (final int factors : testedFactors) {
                final MarketModelTestSetup.MeasureType[] measures = { MoneyMarket, Terminal };
                for (final MarketModelTestSetup.MeasureType measure : measures) {
                    final int[] numeraires = MarketModelTestSetup.makeMeasure(product, measure);
                    final boolean logNormal = false;
                    final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType);

                    final MarketModelTestSetup.EvolverType[] evolvers = {
                            MarketModelTestSetup.EvolverType.NormalPc
                    };
                    final int stop = EvolutionDescription.isInTerminalMeasure(evolution, numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; ++i) {
                        final MTBrownianGeneratorFactory generatorFactory =
                                new MTBrownianGeneratorFactory(MarketModelTestSetup.seed_);
                        final MarketModelEvolver evolver = MarketModelTestSetup.makeMarketModelEvolver(
                                marketModel, numeraires, generatorFactory, evolvers[i]);
                        final String config = MarketModelTestSetup.marketModelTypeToString(mmType)
                                + ", " + factors + " (full) factors, "
                                + MarketModelTestSetup.measureTypeToString(measure)
                                + ", " + MarketModelTestSetup.evolverTypeToString(evolvers[i])
                                + ", MT BGF";
                        final SequenceStatistics stats = MarketModelTestSetup.simulate(evolver, product);
                        MarketModelTestSetup.checkNormalForwardsAndOptionlets(stats, forwardStrikes,
                                displacedPayoffs, config);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // testInverseFloater — cpp:869-996
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:869} {@code BOOST_AUTO_TEST_CASE(testInverseFloater)}. */
    @Test
    public void testInverseFloater() {
        final double[] accruals = MarketModelTestSetup.accruals;
        final double[] fixedStrikes = filled(accruals.length, 0.1);
        final double[] fixedMultipliers = filled(accruals.length, 2.0);
        final double[] floatingSpreads = filled(accruals.length, 0.002);
        final double[] fixedAccruals = accruals.clone();
        final double[] floatingAccruals = accruals.clone();

        final boolean payer = true;

        final MultiStepInverseFloater product = new MultiStepInverseFloater(
                MarketModelTestSetup.rateTimes, fixedAccruals, floatingAccruals,
                fixedStrikes, fixedMultipliers, floatingSpreads,
                MarketModelTestSetup.paymentTimes, payer);

        final MarketModelPathwiseInverseFloater productPathwise = new MarketModelPathwiseInverseFloater(
                MarketModelTestSetup.rateTimes, fixedAccruals, floatingAccruals,
                fixedStrikes, fixedMultipliers, floatingSpreads,
                MarketModelTestSetup.paymentTimes, payer);

        final MultiProductPathwiseWrapper productWrapped = new MultiProductPathwiseWrapper(productPathwise);

        final MultiProductComposite productComposite = new MultiProductComposite();
        productComposite.add(product);
        productComposite.add(productWrapped);
        productComposite.finalizeComposite();

        final EvolutionDescription evolution = productComposite.evolution();

        final MarketModelTestSetup.MarketModelType[] marketModels = {
                ExponentialCorrelationFlatVolatility,
                ExponentialCorrelationAbcdVolatility
        };
        for (final MarketModelTestSetup.MarketModelType mmType : marketModels) {
            final int[] testedFactors = { Math.min(MarketModelTestSetup.todaysForwards.length, 3) };
            for (final int factors : testedFactors) {
                final MarketModelTestSetup.MeasureType[] measures = { MoneyMarket };
                for (final MarketModelTestSetup.MeasureType measure : measures) {
                    final int[] numeraires = MarketModelTestSetup.makeMeasure(product, measure);
                    final boolean logNormal = false;
                    final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType);

                    final MarketModelTestSetup.EvolverType[] evolvers = {
                            MarketModelTestSetup.EvolverType.Pc
                    };
                    for (final MarketModelTestSetup.EvolverType evType : evolvers) {
                        final MTBrownianGeneratorFactory generatorFactory =
                                new MTBrownianGeneratorFactory(MarketModelTestSetup.seed_);
                        final MarketModelEvolver evolver = MarketModelTestSetup.makeMarketModelEvolver(
                                marketModel, numeraires, generatorFactory, evType);

                        final SequenceStatistics stats =
                                MarketModelTestSetup.simulate(evolver, productComposite);

                        final double[] modelVolatilities = new double[accruals.length];
                        for (int i = 0; i < accruals.length; ++i) {
                            modelVolatilities[i] = Math.sqrt(marketModel.totalCovariance(i).get(i, i));
                        }

                        double truePrice = 0.0;
                        for (int i = 0; i < accruals.length; ++i) {
                            final double floatingCouponPV = floatingAccruals[i]
                                    * (MarketModelTestSetup.todaysForwards[i] + floatingSpreads[i])
                                    * MarketModelTestSetup.todaysDiscounts[i + 1];
                            final double inverseCouponPV = 2 * fixedAccruals[i]
                                    * MarketModelTestSetup.todaysDiscounts[i + 1]
                                    * BlackFormula.blackFormula(Option.Type.Put, fixedStrikes[i] / 2.0,
                                            MarketModelTestSetup.todaysForwards[i], modelVolatilities[i]);
                            truePrice += floatingCouponPV - inverseCouponPV;
                        }

                        final double priceMC = stats.mean().get(0);
                        final double priceError = priceMC - truePrice;
                        final double priceSD = stats.errorEstimate().get(0);
                        final double errorInSds = priceError / priceSD;
                        if (Math.abs(errorInSds) > 4.0) {
                            fail("Inverse floater product has price error equal to " + errorInSds
                                    + " sds. Price " + truePrice + " MC price " + priceMC);
                        }
                        final double numericalTolerance = 1e-12;
                        final double wrapperPrice = stats.mean().get(1);
                        if (Math.abs(priceMC - wrapperPrice) > numericalTolerance) {
                            fail("Inverse floater and wrapper pathwise inverse floater do not agree: "
                                    + priceMC + " vs " + wrapperPrice);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // testAllMultiStepProducts — cpp:1213 (if_speed(Slow))
    // ------------------------------------------------------------------

    /** Per-sub-product expected-value bookkeeping (cpp:150). */
    private static final class SubProductExpectedValues {
        final String description;
        final List<Double> values = new ArrayList<Double>();
        boolean testBias = false; // C++ struct default (cpp:154)
        double errorThreshold;

        SubProductExpectedValues(final String descr) {
            this.description = descr;
        }
    }

    /** Faithful port of {@code test-suite/marketmodel.cpp:464}
     *  {@code checkMultiProductCompositeResults}. */
    private static void checkMultiProductCompositeResults(final SequenceStatistics stats,
            final List<SubProductExpectedValues> subProductExpectedValues, final String config) {
        final org.jquantlib.math.matrixutilities.Array results = stats.mean();
        final org.jquantlib.math.matrixutilities.Array errors = stats.errorEstimate();

        int nbOfResults = 0;
        for (final SubProductExpectedValues s : subProductExpectedValues) {
            nbOfResults += s.values.size();
        }
        if (nbOfResults != results.size()) {
            fail("mismatch between the size of the result and the number of results");
        }
        int currentResultIndex = 0;
        for (final SubProductExpectedValues s : subProductExpectedValues) {
            double minError = Double.MAX_VALUE;
            double maxError = -Double.MAX_VALUE;
            final double errorThreshold = s.errorThreshold;
            for (final double value : s.values) {
                final double stdDev = (results.get(currentResultIndex) - value) / errors.get(currentResultIndex);
                if (stdDev > maxError) maxError = stdDev;
                if (stdDev < minError) minError = stdDev;
                ++currentResultIndex;
            }
            final boolean isBiased = minError > 0.0 || maxError < 0.0;
            if ((s.testBias && isBiased) || Math.max(-minError, maxError) > errorThreshold) {
                fail(config + ": failed for sub-product " + s.description
                        + "; minError=" + minError + ", maxError=" + maxError
                        + ", errorThreshold=" + errorThreshold);
            }
        }
    }

    /** Port of {@code addForwards} (cpp:1077). */
    private static void addForwards(final MultiProductComposite product,
            final List<SubProductExpectedValues> subProductExpectedValues) {
        final int n = MarketModelTestSetup.todaysForwards.length;
        final double[] forwardStrikes = new double[n];
        for (int i = 0; i < n; ++i) {
            forwardStrikes[i] = MarketModelTestSetup.todaysForwards[i] + 0.01;
        }
        final MultiStepForwards forwards = new MultiStepForwards(
                MarketModelTestSetup.rateTimes, MarketModelTestSetup.accruals,
                MarketModelTestSetup.paymentTimes, forwardStrikes);
        product.add(forwards);
        final SubProductExpectedValues s = new SubProductExpectedValues("Forward");
        s.errorThreshold = 2.50;
        for (int i = 0; i < n; ++i) {
            s.values.add((MarketModelTestSetup.todaysForwards[i] - forwardStrikes[i])
                    * MarketModelTestSetup.accruals[i] * MarketModelTestSetup.todaysDiscounts[i + 1]);
        }
        subProductExpectedValues.add(s);
    }

    /** Port of {@code addOptionLets} (cpp:1099). */
    private static void addOptionLets(final MultiProductComposite product,
            final List<SubProductExpectedValues> subProductExpectedValues) {
        final int n = MarketModelTestSetup.todaysForwards.length;
        final Payoff[] optionletPayoffs = new Payoff[n];
        final List<StrikedTypePayoff> displacedPayoffs = new ArrayList<StrikedTypePayoff>(n);
        for (int i = 0; i < n; ++i) {
            optionletPayoffs[i] = new PlainVanillaPayoff(Option.Type.Call, MarketModelTestSetup.todaysForwards[i]);
            displacedPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    MarketModelTestSetup.todaysForwards[i] + MarketModelTestSetup.displacement));
        }
        final MultiStepOptionlets optionlets = new MultiStepOptionlets(
                MarketModelTestSetup.rateTimes, MarketModelTestSetup.accruals,
                MarketModelTestSetup.paymentTimes, optionletPayoffs);
        product.add(optionlets);

        final SubProductExpectedValues s = new SubProductExpectedValues("Caplet");
        s.errorThreshold = 2.50;
        for (int i = 0; i < n; ++i) {
            s.values.add(new BlackCalculator(displacedPayoffs.get(i),
                    MarketModelTestSetup.todaysForwards[i] + MarketModelTestSetup.displacement,
                    MarketModelTestSetup.volatilities[i] * Math.sqrt(MarketModelTestSetup.rateTimes[i]),
                    MarketModelTestSetup.todaysDiscounts[i + 1] * MarketModelTestSetup.accruals[i]).value());
        }
        subProductExpectedValues.add(s);
    }

    /** Port of {@code addCoinitialSwaps} (cpp:1132). */
    private static void addCoinitialSwaps(final MultiProductComposite product,
            final List<SubProductExpectedValues> subProductExpectedValues) {
        final double fixedRate = 0.04;
        final MultiStepCoinitialSwaps multiStepCoinitialSwaps = new MultiStepCoinitialSwaps(
                MarketModelTestSetup.rateTimes, MarketModelTestSetup.accruals,
                MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, fixedRate);
        product.add(multiStepCoinitialSwaps);
        final SubProductExpectedValues s = new SubProductExpectedValues("coinitial swap");
        s.testBias = false;
        s.errorThreshold = 2.32;
        double coinitialSwapValue = 0.0;
        for (int i = 0; i < MarketModelTestSetup.todaysForwards.length; ++i) {
            coinitialSwapValue += (MarketModelTestSetup.todaysForwards[i] - fixedRate)
                    * MarketModelTestSetup.accruals[i] * MarketModelTestSetup.todaysDiscounts[i + 1];
            s.values.add(coinitialSwapValue);
        }
        subProductExpectedValues.add(s);
    }

    /** Port of {@code addCoterminalSwapsAndSwaptions} (cpp:1152). */
    private static void addCoterminalSwapsAndSwaptions(final MultiProductComposite product,
            final List<SubProductExpectedValues> subProductExpectedValues) {
        final double fixedRate = 0.04;
        final MultiStepCoterminalSwaps swaps = new MultiStepCoterminalSwaps(
                MarketModelTestSetup.rateTimes, MarketModelTestSetup.accruals,
                MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, fixedRate);

        final int n = MarketModelTestSetup.todaysForwards.length;
        final StrikedTypePayoff[] payoffs = new StrikedTypePayoff[n];
        for (int i = 0; i < n; ++i) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, MarketModelTestSetup.todaysForwards[i]);
        }
        final MultiStepCoterminalSwaptions swaptions = new MultiStepCoterminalSwaptions(
                MarketModelTestSetup.rateTimes, MarketModelTestSetup.rateTimes, payoffs);

        product.add(swaps);
        product.add(swaptions);

        final SubProductExpectedValues sSwap = new SubProductExpectedValues("coterminal swap");
        sSwap.testBias = false;
        sSwap.errorThreshold = 2.32;
        final LMMCurveState curveState = new LMMCurveState(MarketModelTestSetup.rateTimes);
        curveState.setOnForwardRates(MarketModelTestSetup.todaysForwards);
        final double[] atmRates = curveState.coterminalSwapRates();
        for (int i = 0; i < n; ++i) {
            final double expectedNPV = curveState.coterminalSwapAnnuity(0, i)
                    * (atmRates[i] - fixedRate)
                    * MarketModelTestSetup.todaysDiscounts[0];
            sSwap.values.add(expectedNPV);
        }
        subProductExpectedValues.add(sSwap);

        // clone the product to be able to finalize it and call evolution function member
        final MultiProductComposite productClone = (MultiProductComposite) product.clone();
        productClone.finalizeComposite();
        final SubProductExpectedValues sSwaption = new SubProductExpectedValues("coterminal swaption");
        sSwaption.testBias = false;
        sSwaption.errorThreshold = 2.32;
        final double localDisplacement = 0;
        final org.jquantlib.math.matrixutilities.Matrix jacobian =
                SwapForwardMappings.coterminalSwapZedMatrix(curveState, localDisplacement);
        final boolean logNormal = true;

        final EvolutionDescription evolution = productClone.evolution();
        final int factors = MarketModelTestSetup.todaysForwards.length;
        final MarketModelTestSetup.MarketModelType marketModelType = ExponentialCorrelationFlatVolatility;
        final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                logNormal, evolution, factors, marketModelType);
        for (int i = 0; i < n; ++i) {
            final org.jquantlib.math.matrixutilities.Matrix forwardsCovariance =
                    marketModel.totalCovariance(i);
            final org.jquantlib.math.matrixutilities.Matrix cotSwapsCovariance =
                    jacobian.mul(forwardsCovariance).mul(jacobian.transpose());
            final PlainVanillaPayoff pp = new PlainVanillaPayoff(Option.Type.Call,
                    MarketModelTestSetup.todaysForwards[i] + localDisplacement);
            final double expectedSwaption = new BlackCalculator(pp,
                    MarketModelTestSetup.todaysCoterminalSwapRates[i] + localDisplacement,
                    Math.sqrt(cotSwapsCovariance.get(i, i)),
                    curveState.coterminalSwapAnnuity(0, i) * MarketModelTestSetup.todaysDiscounts[0]).value();
            sSwaption.values.add(expectedSwaption);
        }
        subProductExpectedValues.add(sSwaption);
    }

    /** Port of {@code testMultiProductComposite} (cpp:998). */
    private static void runMultiProductComposite(final MultiProductComposite product,
            final List<SubProductExpectedValues> subProductExpectedValues) {
        final EvolutionDescription evolution = product.evolution();
        final MarketModelTestSetup.MarketModelType[] marketModels = {
                ExponentialCorrelationFlatVolatility,
                ExponentialCorrelationAbcdVolatility
        };
        for (final MarketModelTestSetup.MarketModelType mmType : marketModels) {
            final int[] testedFactors = { 4, 8, MarketModelTestSetup.todaysForwards.length };
            for (final int factors : testedFactors) {
                final MarketModelTestSetup.MeasureType[] measures = {
                        Terminal,
                        MarketModelTestSetup.MeasureType.MoneyMarketPlus,
                        MoneyMarket
                };
                for (final MarketModelTestSetup.MeasureType measure : measures) {
                    final int[] numeraires = MarketModelTestSetup.makeMeasure(product, measure);
                    final boolean logNormal = true;
                    final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType);

                    final MarketModelTestSetup.EvolverType[] evolvers = {
                            MarketModelTestSetup.EvolverType.Pc,
                            MarketModelTestSetup.EvolverType.Balland,
                            MarketModelTestSetup.EvolverType.Ipc
                    };
                    final int stop = EvolutionDescription.isInTerminalMeasure(evolution, numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; ++i) {
                        final SobolBrownianGeneratorFactory generatorFactory = new SobolBrownianGeneratorFactory(
                                SobolBrownianGenerator.Ordering.Diagonal, MarketModelTestSetup.seed_);
                        final MarketModelEvolver evolver = MarketModelTestSetup.makeMarketModelEvolver(
                                marketModel, numeraires, generatorFactory, evolvers[i]);
                        final String config = MarketModelTestSetup.marketModelTypeToString(mmType)
                                + ", " + factors + " factors, "
                                + MarketModelTestSetup.measureTypeToString(measure)
                                + ", " + MarketModelTestSetup.evolverTypeToString(evolvers[i])
                                + ", MT BGF";
                        final SequenceStatistics stats = MarketModelTestSetup.simulate(evolver, product);
                        checkMultiProductCompositeResults(stats, subProductExpectedValues, config);
                    }
                }
            }
        }
    }

    /** Faithful port of {@code test-suite/marketmodel.cpp:1213} {@code BOOST_AUTO_TEST_CASE(testAllMultiStepProducts, *precondition(if_speed(Slow)))}.
     *
     *  <p>C++ Slow tests are run with the release-build path-count
     *  {@code paths_=32767, trainingPaths_=8191} (cpp:264-266). When
     *  {@code -Dql.slowTests=1} is set, this test bumps the path-count
     *  to the C++ release values; the {@code _DEBUG} smaller path-count
     *  ({@code 127/31}) is too noisy for the Forward sub-product's bias
     *  check ({@code minError > 0.0 || maxError < 0.0}) to pass deterministically.
     */
    @Test
    public void testAllMultiStepProducts() {
        org.junit.Assume.assumeTrue("test gated -Dql.slowTests=1 to mirror C++ if_speed(Slow)",
                System.getProperty("ql.slowTests") != null);
        // Bump to C++ release-build paths/trainingPaths per cpp:264-266
        MarketModelTestSetup.paths_ = 32767;
        MarketModelTestSetup.trainingPaths_ = 8191;
        final MultiProductComposite product = new MultiProductComposite();
        final List<SubProductExpectedValues> subProductExpectedValues = new ArrayList<SubProductExpectedValues>();
        addForwards(product, subProductExpectedValues);
        addOptionLets(product, subProductExpectedValues);
        addCoinitialSwaps(product, subProductExpectedValues);
        addCoterminalSwapsAndSwaptions(product, subProductExpectedValues);
        product.finalizeComposite();
        runMultiProductComposite(product, subProductExpectedValues);
    }

    // ------------------------------------------------------------------
    // testPeriodAdapter — cpp:1229-1379
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:1229} {@code BOOST_AUTO_TEST_CASE(testPeriodAdapter)}. */
    @Test
    public void testPeriodAdapter() {
        final LMMCurveState cs = new LMMCurveState(MarketModelTestSetup.rateTimes);
        cs.setOnForwardRates(MarketModelTestSetup.todaysForwards);

        final int period = 2;
        final int offset = 0;

        final LMMCurveState bigRateCS = ForwardForwardMappings.restrictCurveState(cs, period, offset);

        final double[] bigRateTimes = bigRateCS.rateTimes();
        final double[] swaptionPaymentTimes = Arrays.copyOf(bigRateTimes, bigRateTimes.length - 1);
        final double[] capletPaymentTimes = swaptionPaymentTimes.clone();

        final int numberBigRates = bigRateCS.numberOfRates();

        final StrikedTypePayoff[] optionletPayoffs = new StrikedTypePayoff[numberBigRates];
        final StrikedTypePayoff[] swaptionPayoffs = new StrikedTypePayoff[numberBigRates];
        final StrikedTypePayoff[] displacedOptionletPayoffs = new StrikedTypePayoff[numberBigRates];
        final StrikedTypePayoff[] displacedSwaptionPayoffs = new StrikedTypePayoff[numberBigRates];

        for (int i = 0; i < numberBigRates; ++i) {
            optionletPayoffs[i] = new PlainVanillaPayoff(Option.Type.Call, bigRateCS.forwardRate(i));
            swaptionPayoffs[i] = new PlainVanillaPayoff(Option.Type.Call, bigRateCS.coterminalSwapRate(i));
            displacedOptionletPayoffs[i] = new PlainVanillaPayoff(Option.Type.Call,
                    bigRateCS.forwardRate(i) + MarketModelTestSetup.displacement);
            displacedSwaptionPayoffs[i] = new PlainVanillaPayoff(Option.Type.Call,
                    bigRateCS.coterminalSwapRate(i) + MarketModelTestSetup.displacement);
        }

        final MultiStepPeriodCapletSwaptions theProduct = new MultiStepPeriodCapletSwaptions(
                MarketModelTestSetup.rateTimes,
                capletPaymentTimes, swaptionPaymentTimes,
                optionletPayoffs, swaptionPayoffs,
                period, offset);

        final EvolutionDescription evolution = theProduct.evolution();

        final boolean logNormal = true;
        final int factors = 5;

        final MarketModel originalModel = MarketModelTestSetup.makeMarketModel(
                logNormal, evolution, factors, ExponentialCorrelationAbcdVolatility);

        final double[] newDisplacements = new double[0];

        final MarketModel adaptedforwardModel = new FwdPeriodAdapter(originalModel,
                period, offset, newDisplacements);
        final MarketModel adaptedSwapModel = new FwdToCotSwapAdapter(adaptedforwardModel);

        final org.jquantlib.math.matrixutilities.Matrix finalForwardCovariances =
                adaptedforwardModel.totalCovariance(adaptedforwardModel.numberOfSteps() - 1);
        final org.jquantlib.math.matrixutilities.Matrix finalSwapCovariances =
                adaptedSwapModel.totalCovariance(adaptedSwapModel.numberOfSteps() - 1);

        final double[] adaptedForwardSds = new double[adaptedforwardModel.numberOfRates()];
        final double[] adaptedSwapSds = new double[adaptedSwapModel.numberOfRates()];
        final double[] approxCapletPrices = new double[adaptedforwardModel.numberOfRates()];
        final double[] approxSwaptionPrices = new double[adaptedSwapModel.numberOfRates()];

        for (int j = 0; j < adaptedSwapModel.numberOfRates(); ++j) {
            adaptedForwardSds[j] = Math.sqrt(finalForwardCovariances.get(j, j));
            adaptedSwapSds[j] = Math.sqrt(finalSwapCovariances.get(j, j));

            final double capletAnnuity = MarketModelTestSetup.todaysDiscounts[0]
                    * bigRateCS.discountRatio(j + 1, 0) * bigRateCS.rateTaus()[j];

            approxCapletPrices[j] = new BlackCalculator(displacedOptionletPayoffs[j],
                    bigRateCS.forwardRate(j) + MarketModelTestSetup.displacement,
                    adaptedForwardSds[j], capletAnnuity).value();

            final double swaptionAnnuity = MarketModelTestSetup.todaysDiscounts[0]
                    * bigRateCS.coterminalSwapAnnuity(0, j);

            approxSwaptionPrices[j] = new BlackCalculator(displacedSwaptionPayoffs[j],
                    bigRateCS.coterminalSwapRate(j) + MarketModelTestSetup.displacement,
                    adaptedSwapSds[j], swaptionAnnuity).value();
        }
        final SobolBrownianGeneratorFactory generatorFactory = new SobolBrownianGeneratorFactory(
                SobolBrownianGenerator.Ordering.Diagonal, MarketModelTestSetup.seed_);

        final MarketModelEvolver evolver = MarketModelTestSetup.makeMarketModelEvolver(originalModel,
                theProduct.suggestedNumeraires(), generatorFactory, MarketModelTestSetup.EvolverType.Pc);

        final SequenceStatistics stats = MarketModelTestSetup.simulate(evolver, theProduct);

        final org.jquantlib.math.matrixutilities.Array results = stats.mean();
        final org.jquantlib.math.matrixutilities.Array errors = stats.errorEstimate();

        if (2 * numberBigRates != results.size()) {
            fail("mismatch between the size of the result and the number of results");
        }

        final double[] capletErrorsInSds = new double[numberBigRates];
        final double[] swaptionErrorsInSds = new double[numberBigRates];
        for (int i = 0; i < numberBigRates; ++i) {
            capletErrorsInSds[i] = (results.get(i) - approxCapletPrices[i]) / errors.get(i);
            swaptionErrorsInSds[i] =
                    (results.get(i + numberBigRates) - approxSwaptionPrices[i]) / errors.get(i + numberBigRates);
        }

        final double capletTolerance = 4;
        final double swaptionTolerance = 4;

        for (int i = 0; i < numberBigRates; ++i) {
            if (Math.abs(capletErrorsInSds[i]) > capletTolerance) {
                fail((i + 1) + "caplet , approx price " + approxCapletPrices[i]
                        + ", \t simulation price " + results.get(i)
                        + ", \t error in sds " + capletErrorsInSds[i]);
            }
        }
        for (int i = 0; i < numberBigRates; ++i) {
            if (Math.abs(swaptionErrorsInSds[i]) > swaptionTolerance) {
                fail((i + 1) + "swaption, approx price " + approxSwaptionPrices[i]
                        + ", \t simulation price " + results.get(i + numberBigRates)
                        + ", \t error in sds " + swaptionErrorsInSds[i]);
            }
        }
    }

    // ------------------------------------------------------------------
    // checkCallableSwap — cpp:655-697
    // ------------------------------------------------------------------

    /** Port of v1.42.1 {@code checkCallableSwap} (cpp:655-697). */
    private static void checkCallableSwap(final SequenceStatistics stats, final String config) {
        final double payerNPV = stats.mean().get(0);
        final double receiverNPV = stats.mean().get(1);
        final double bermudanNPV = stats.mean().get(2);
        final double callableNPV = stats.mean().get(3);
        final double tolerance = 1.1e-15;
        final double swapError = Math.abs(receiverNPV + payerNPV);
        final double callableError = Math.abs(receiverNPV + bermudanNPV - callableNPV);

        if (swapError > tolerance) {
            fail(config + ": agreement between payer and receiver swap failed:\n"
                    + "    payer swap:    " + payerNPV + "\n"
                    + "    receiver swap: " + receiverNPV + "\n"
                    + "    error:         " + swapError + "\n"
                    + "    tolerance:     " + tolerance);
        }
        if (bermudanNPV < 0.0) {
            fail(config + ": negative bermudan option value:\n"
                    + "    bermudan:          " + bermudanNPV);
        }
        if (callableNPV < receiverNPV) {
            fail(config + ": callable receiver less valuable than plain receiver:\n"
                    + "    receiver swap:     " + receiverNPV + "\n"
                    + "    callable:          " + callableNPV);
        }
        if (callableError > tolerance) {
            fail(config + ": agreement between receiver+bermudan and callable failed:\n"
                    + "    receiver swap:     " + receiverNPV + "\n"
                    + "    bermudan:          " + bermudanNPV + "\n"
                    + "    receiver+bermudan: " + (receiverNPV + bermudanNPV) + "\n"
                    + "    callable:          " + callableNPV + "\n"
                    + "    error:             " + callableError + "\n"
                    + "    tolerance:         " + tolerance);
        }
    }

    // ------------------------------------------------------------------
    // testCallableSwapNaif — cpp:1381 (if_speed(Slow))
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:1381} {@code BOOST_AUTO_TEST_CASE(testCallableSwapNaif, *precondition(if_speed(Slow)))}. */
    @Test
    public void testCallableSwapNaif() {
        org.junit.Assume.assumeTrue("test gated -Dql.slowTests=1 to mirror C++ if_speed(Slow)",
                System.getProperty("ql.slowTests") != null);
        // C++ release-build paths/trainingPaths (cpp:264-266)
        MarketModelTestSetup.paths_ = 32767;
        MarketModelTestSetup.trainingPaths_ = 8191;

        final double fixedRate = 0.04;

        // 0. payer swap
        final MultiStepSwap payerSwap = new MultiStepSwap(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.accruals,
                MarketModelTestSetup.paymentTimes, fixedRate, true);

        // 1. equivalent receiver swap
        final MultiStepSwap receiverSwap = new MultiStepSwap(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.accruals,
                MarketModelTestSetup.paymentTimes, fixedRate, false);

        // exercise schedule: drop the last rate
        final double[] exerciseTimes = Arrays.copyOf(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.rateTimes.length - 1);

        // naif strategy
        final double[] swapTriggers = filled(exerciseTimes.length, fixedRate);
        final SwapRateTrigger naifStrategy = new SwapRateTrigger(
                MarketModelTestSetup.rateTimes, swapTriggers, exerciseTimes);

        final NothingExerciseValue nullRebate = new NothingExerciseValue(MarketModelTestSetup.rateTimes);

        final CallSpecifiedMultiProduct dummyProduct = new CallSpecifiedMultiProduct(
                receiverSwap, naifStrategy, new ExerciseAdapter(nullRebate));

        final EvolutionDescription evolution = dummyProduct.evolution();

        final MarketModelTestSetup.MarketModelType[] marketModels = {
                ExponentialCorrelationFlatVolatility,
                ExponentialCorrelationAbcdVolatility
        };
        for (final MarketModelTestSetup.MarketModelType mmType : marketModels) {
            final int[] testedFactors = { 4, MarketModelTestSetup.todaysForwards.length };
            for (final int factors : testedFactors) {
                final MarketModelTestSetup.MeasureType[] measures = {
                        MarketModelTestSetup.MeasureType.MoneyMarketPlus
                };
                for (final MarketModelTestSetup.MeasureType measure : measures) {
                    final int[] numeraires = MarketModelTestSetup.makeMeasure(dummyProduct, measure);
                    final boolean logNormal = true;
                    final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType);

                    final MarketModelTestSetup.EvolverType[] evolvers = {
                            MarketModelTestSetup.EvolverType.Pc,
                            MarketModelTestSetup.EvolverType.Balland,
                            MarketModelTestSetup.EvolverType.Ipc
                    };
                    final int stop = EvolutionDescription.isInTerminalMeasure(evolution, numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; ++i) {
                        final SobolBrownianGeneratorFactory generatorFactory = new SobolBrownianGeneratorFactory(
                                SobolBrownianGenerator.Ordering.Diagonal, MarketModelTestSetup.seed_);
                        MarketModelEvolver evolver = MarketModelTestSetup.makeMarketModelEvolver(
                                marketModel, numeraires, generatorFactory, evolvers[i]);
                        final String config = MarketModelTestSetup.marketModelTypeToString(mmType)
                                + ", " + factors + " factors, "
                                + MarketModelTestSetup.measureTypeToString(measure)
                                + ", " + MarketModelTestSetup.evolverTypeToString(evolvers[i])
                                + ", MT BGF";

                        // 2. bermudan swaption to enter into the payer swap
                        final CallSpecifiedMultiProduct bermudanProduct = new CallSpecifiedMultiProduct(
                                new MultiStepNothing(evolution), naifStrategy, payerSwap);
                        // 3. callable receiver swap
                        final CallSpecifiedMultiProduct callableProduct = new CallSpecifiedMultiProduct(
                                receiverSwap, naifStrategy, new ExerciseAdapter(nullRebate));

                        // lower bound: evolve all 4 products together
                        final MultiProductComposite allProducts = new MultiProductComposite();
                        allProducts.add(payerSwap);
                        allProducts.add(receiverSwap);
                        allProducts.add(bermudanProduct);
                        allProducts.add(callableProduct);
                        allProducts.finalizeComposite();

                        final SequenceStatistics stats = MarketModelTestSetup.simulate(evolver, allProducts);
                        checkCallableSwap(stats, config);

                        // upper bound
                        final SobolBrownianGeneratorFactory uFactory = new SobolBrownianGeneratorFactory(
                                SobolBrownianGenerator.Ordering.Diagonal, MarketModelTestSetup.seed_ + 142);
                        evolver = MarketModelTestSetup.makeMarketModelEvolver(
                                marketModel, numeraires, uFactory, evolvers[i]);

                        final List<MarketModelEvolver> innerEvolvers = new ArrayList<MarketModelEvolver>();
                        final boolean[] isExerciseTime = Utilities.isInSubset(
                                evolution.evolutionTimes(), naifStrategy.exerciseTimes());
                        for (int s = 0; s < isExerciseTime.length; ++s) {
                            if (isExerciseTime[s]) {
                                final MTBrownianGeneratorFactory iFactory = new MTBrownianGeneratorFactory(
                                        MarketModelTestSetup.seed_ + s);
                                final MarketModelEvolver e = MarketModelTestSetup.makeMarketModelEvolver(
                                        marketModel, numeraires, iFactory, evolvers[i], s);
                                innerEvolvers.add(e);
                            }
                        }
                        final int initialNumeraire = evolver.numeraires()[0];
                        final double initialNumeraireValue = MarketModelTestSetup.todaysDiscounts[initialNumeraire];
                        final UpperBoundEngine uEngine = new UpperBoundEngine(evolver, innerEvolvers,
                                receiverSwap, nullRebate, receiverSwap, nullRebate,
                                naifStrategy, initialNumeraireValue);
                        final Statistics uStats = new Statistics();
                        uEngine.multiplePathValues(uStats, 255, 256);
                        // Just exercising the upper-bound engine (no assertion in C++ either)
                        // delta = uStats.mean(); deltaError = uStats.errorEstimate();
                        // values consumed only for printReport_ branch in C++
                        @SuppressWarnings("unused") final double delta = uStats.mean();
                        @SuppressWarnings("unused") final double deltaError = uStats.errorEstimate();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // testCallableSwapAnderson — cpp:1710 (BOOST_AUTO_TEST_CASE_TEMPLATE; if_speed(Slow))
    // ------------------------------------------------------------------

    /** C++ slice<> parametric pack (cpp:1700-1708). */
    private static final class AndersonSlice {
        final MarketModelTestSetup.MarketModelType marketModelType;
        final int testedFactor; // 0 means use todaysForwards.length

        AndersonSlice(final MarketModelTestSetup.MarketModelType mt, final int tf) {
            this.marketModelType = mt;
            this.testedFactor = tf;
        }
    }

    /** Faithful port of {@code test-suite/marketmodel.cpp:1710}
     *  {@code BOOST_AUTO_TEST_CASE_TEMPLATE(testCallableSwapAnderson, T, slices)}.
     *  Iterates over the 6 {@code slice<>} parametric packs inline rather than
     *  via JUnit parametric tests (Java parametric tests would require a
     *  separate runner class; the C++ test is a single suite). */
    @Test
    public void testCallableSwapAnderson() {
        org.junit.Assume.assumeTrue("test gated -Dql.slowTests=1 to mirror C++ if_speed(Slow)",
                System.getProperty("ql.slowTests") != null);
        MarketModelTestSetup.paths_ = 32767;
        MarketModelTestSetup.trainingPaths_ = 8191;

        final AndersonSlice[] slices = {
                new AndersonSlice(ExponentialCorrelationFlatVolatility, 4),
                new AndersonSlice(ExponentialCorrelationFlatVolatility, 8),
                new AndersonSlice(ExponentialCorrelationFlatVolatility, 0),
                new AndersonSlice(ExponentialCorrelationAbcdVolatility, 4),
                new AndersonSlice(ExponentialCorrelationAbcdVolatility, 8),
                new AndersonSlice(ExponentialCorrelationAbcdVolatility, 0)
        };

        for (final AndersonSlice slc : slices) {
            final MarketModelTestSetup.MarketModelType marketModelType = slc.marketModelType;
            final int testedFactor = (slc.testedFactor != 0) ? slc.testedFactor
                    : MarketModelTestSetup.todaysForwards.length;

            final double fixedRate = 0.04;

            // C++ calls setup() once at the top and once inside the loop;
            // emulate the inner setup() exactly:
            MarketModelTestSetup.setup();
            MarketModelTestSetup.paths_ = 32767;
            MarketModelTestSetup.trainingPaths_ = 8191;

            final MultiStepSwap payerSwap = new MultiStepSwap(MarketModelTestSetup.rateTimes,
                    MarketModelTestSetup.accruals, MarketModelTestSetup.accruals,
                    MarketModelTestSetup.paymentTimes, fixedRate, true);

            final MultiStepSwap receiverSwap = new MultiStepSwap(MarketModelTestSetup.rateTimes,
                    MarketModelTestSetup.accruals, MarketModelTestSetup.accruals,
                    MarketModelTestSetup.paymentTimes, fixedRate, false);

            final double[] exerciseTimes = Arrays.copyOf(MarketModelTestSetup.rateTimes,
                    MarketModelTestSetup.rateTimes.length - 1);

            final double[] swapTriggers = filled(exerciseTimes.length, fixedRate);
            final SwapRateTrigger naifStrategy = new SwapRateTrigger(
                    MarketModelTestSetup.rateTimes, swapTriggers, exerciseTimes);

            final NothingExerciseValue control = new NothingExerciseValue(MarketModelTestSetup.rateTimes);
            final NothingExerciseValue nullRebate = new NothingExerciseValue(MarketModelTestSetup.rateTimes);

            final double[] anderTriggers = filled(exerciseTimes.length, fixedRate);
            final TriggeredSwapExercise parametricForm = new TriggeredSwapExercise(
                    MarketModelTestSetup.rateTimes, exerciseTimes, anderTriggers);

            final CallSpecifiedMultiProduct dummyProduct = new CallSpecifiedMultiProduct(
                    receiverSwap, naifStrategy, new ExerciseAdapter(nullRebate));

            final EvolutionDescription evolution = dummyProduct.evolution();

            final int factors = testedFactor;
            final MarketModelTestSetup.MeasureType[] measures = { Terminal };
            for (final MarketModelTestSetup.MeasureType measure : measures) {
                final int[] numeraires = MarketModelTestSetup.makeMeasure(dummyProduct, measure);
                final boolean logNormal = true;
                final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                        logNormal, evolution, factors, marketModelType);

                final MarketModelTestSetup.EvolverType[] evolvers = {
                        MarketModelTestSetup.EvolverType.Pc,
                        MarketModelTestSetup.EvolverType.Balland,
                        MarketModelTestSetup.EvolverType.Ipc
                };
                final int stop = EvolutionDescription.isInTerminalMeasure(evolution, numeraires) ? 0 : 1;
                for (int i = 0; i < evolvers.length - stop; ++i) {
                    final SobolBrownianGeneratorFactory generatorFactory = new SobolBrownianGeneratorFactory(
                            SobolBrownianGenerator.Ordering.Diagonal, MarketModelTestSetup.seed_);
                    MarketModelEvolver evolver = MarketModelTestSetup.makeMarketModelEvolver(
                            marketModel, numeraires, generatorFactory, evolvers[i]);
                    final String config = MarketModelTestSetup.marketModelTypeToString(marketModelType)
                            + ", " + factors + " factors, "
                            + MarketModelTestSetup.measureTypeToString(measure)
                            + ", " + MarketModelTestSetup.evolverTypeToString(evolvers[i])
                            + ", MT BGF";

                    // 1. calculate the exercise strategy
                    final List<List<NodeData>> collectedData = CollectNodeData.collect(
                            evolver, receiverSwap, parametricForm, nullRebate, control,
                            MarketModelTestSetup.trainingPaths_);
                    final Simplex om = new Simplex(0.01);
                    final org.jquantlib.math.optimization.EndCriteria ec =
                            new org.jquantlib.math.optimization.EndCriteria(1000, 100, 1e-8, 1e-16, 1e-8);
                    final int initialNumeraire = evolver.numeraires()[0];
                    final double initialNumeraireValue = MarketModelTestSetup.todaysDiscounts[initialNumeraire];

                    final List<double[]> parameters = new ArrayList<double[]>();
                    GenericEarlyExercise.optimize(collectedData, parametricForm, parameters, ec, om);
                    // C++: firstPassValue * initialNumeraireValue  (only printed)

                    final ParametricExerciseAdapter exerciseStrategy = new ParametricExerciseAdapter(
                            parametricForm, parameters);

                    final CallSpecifiedMultiProduct bermudanProduct = new CallSpecifiedMultiProduct(
                            new MultiStepNothing(evolution), exerciseStrategy, payerSwap);
                    final CallSpecifiedMultiProduct callableProduct = new CallSpecifiedMultiProduct(
                            receiverSwap, exerciseStrategy, new ExerciseAdapter(nullRebate));

                    final MultiProductComposite allProducts = new MultiProductComposite();
                    allProducts.add(payerSwap);
                    allProducts.add(receiverSwap);
                    allProducts.add(bermudanProduct);
                    allProducts.add(callableProduct);
                    allProducts.finalizeComposite();

                    final SequenceStatistics stats = MarketModelTestSetup.simulate(evolver, allProducts);
                    checkCallableSwap(stats, config);

                    // upper bound
                    final SobolBrownianGeneratorFactory uFactory = new SobolBrownianGeneratorFactory(
                            SobolBrownianGenerator.Ordering.Diagonal, MarketModelTestSetup.seed_ + 142);
                    evolver = MarketModelTestSetup.makeMarketModelEvolver(
                            marketModel, numeraires, uFactory, evolvers[i]);
                    final List<MarketModelEvolver> innerEvolvers = new ArrayList<MarketModelEvolver>();
                    final boolean[] isExerciseTime = Utilities.isInSubset(
                            evolution.evolutionTimes(), exerciseStrategy.exerciseTimes());
                    for (int s = 0; s < isExerciseTime.length; ++s) {
                        if (isExerciseTime[s]) {
                            final MTBrownianGeneratorFactory iFactory = new MTBrownianGeneratorFactory(
                                    MarketModelTestSetup.seed_ + s);
                            final MarketModelEvolver e = MarketModelTestSetup.makeMarketModelEvolver(
                                    marketModel, numeraires, iFactory, evolvers[i], s);
                            innerEvolvers.add(e);
                        }
                    }
                    final UpperBoundEngine uEngine = new UpperBoundEngine(evolver, innerEvolvers,
                            receiverSwap, nullRebate, receiverSwap, nullRebate,
                            exerciseStrategy, initialNumeraireValue);
                    final Statistics uStats = new Statistics();
                    uEngine.multiplePathValues(uStats, 255, 256);
                    @SuppressWarnings("unused") final double delta = uStats.mean();
                    @SuppressWarnings("unused") final double deltaError = uStats.errorEstimate();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // testCallableSwapLS — cpp:1534 (if_speed(Slow))
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:1534}
     *  {@code BOOST_AUTO_TEST_CASE(testCallableSwapLS, *precondition(if_speed(Slow)))}.
     *  Exercises the Longstaff-Schwartz regression-based exercise strategy
     *  for a callable swap and compares lower/upper bounds. */
    @Test
    public void testCallableSwapLS() {
        org.junit.Assume.assumeTrue("test gated -Dql.slowTests=1 to mirror C++ if_speed(Slow)",
                System.getProperty("ql.slowTests") != null);
        MarketModelTestSetup.paths_ = 32767;
        MarketModelTestSetup.trainingPaths_ = 8191;

        final double fixedRate = 0.04;

        // 0. payer swap
        final MultiStepSwap payerSwap = new MultiStepSwap(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.accruals,
                MarketModelTestSetup.paymentTimes, fixedRate, true);
        // 1. equivalent receiver swap
        final MultiStepSwap receiverSwap = new MultiStepSwap(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.accruals,
                MarketModelTestSetup.paymentTimes, fixedRate, false);

        // exercise schedule: drop the last rate (cpp:1551-1552)
        final double[] exerciseTimes = Arrays.copyOf(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.rateTimes.length - 1);

        // naif strategy (cpp:1558-1559) — used only to build the dummy product
        final double[] swapTriggers = filled(exerciseTimes.length, fixedRate);
        final SwapRateTrigger naifStrategy = new SwapRateTrigger(
                MarketModelTestSetup.rateTimes, swapTriggers, exerciseTimes);

        // Longstaff-Schwartz strategy ingredients (cpp:1562-1566)
        final NothingExerciseValue control = new NothingExerciseValue(MarketModelTestSetup.rateTimes);
        final SwapBasisSystem basisSystem = new SwapBasisSystem(
                MarketModelTestSetup.rateTimes, exerciseTimes);
        final NothingExerciseValue nullRebate = new NothingExerciseValue(MarketModelTestSetup.rateTimes);

        final CallSpecifiedMultiProduct dummyProduct = new CallSpecifiedMultiProduct(
                receiverSwap, naifStrategy, new ExerciseAdapter(nullRebate));
        final EvolutionDescription evolution = dummyProduct.evolution();

        final MarketModelTestSetup.MarketModelType[] marketModels = {
                ExponentialCorrelationFlatVolatility,
                ExponentialCorrelationAbcdVolatility
        };
        for (final MarketModelTestSetup.MarketModelType mmType : marketModels) {
            final int[] testedFactors = { 4, MarketModelTestSetup.todaysForwards.length };
            for (final int factors : testedFactors) {
                final MarketModelTestSetup.MeasureType[] measures = { MoneyMarket };
                for (final MarketModelTestSetup.MeasureType measure : measures) {
                    final int[] numeraires = MarketModelTestSetup.makeMeasure(dummyProduct, measure);
                    final boolean logNormal = true;
                    final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType);

                    final MarketModelTestSetup.EvolverType[] evolvers = {
                            MarketModelTestSetup.EvolverType.Pc,
                            MarketModelTestSetup.EvolverType.Balland,
                            MarketModelTestSetup.EvolverType.Ipc
                    };
                    final int stop = EvolutionDescription.isInTerminalMeasure(evolution, numeraires) ? 0 : 1;
                    for (int i = 0; i < evolvers.length - stop; ++i) {
                        final SobolBrownianGeneratorFactory generatorFactory = new SobolBrownianGeneratorFactory(
                                SobolBrownianGenerator.Ordering.Diagonal, MarketModelTestSetup.seed_);
                        MarketModelEvolver evolver = MarketModelTestSetup.makeMarketModelEvolver(
                                marketModel, numeraires, generatorFactory, evolvers[i]);
                        final String config = MarketModelTestSetup.marketModelTypeToString(mmType)
                                + ", " + factors + " factors, "
                                + MarketModelTestSetup.measureTypeToString(measure)
                                + ", " + MarketModelTestSetup.evolverTypeToString(evolvers[i])
                                + ", MT BGF";

                        // calculate the exercise strategy (cpp:1622-1628)
                        final List<List<NodeData>> collectedData = CollectNodeData.collect(
                                evolver, receiverSwap, basisSystem, nullRebate, control,
                                MarketModelTestSetup.trainingPaths_);

                        // List<List<NodeData>> → NodeData[][] for the regression
                        final NodeData[][] simulationData = new NodeData[collectedData.size()][];
                        for (int s = 0; s < collectedData.size(); ++s) {
                            simulationData[s] = collectedData.get(s).toArray(new NodeData[0]);
                        }
                        final double[][] basisCoefficientsArr = new double[simulationData.length - 1][];
                        GenericLongstaffSchwartzRegression.evaluate(simulationData, basisCoefficientsArr);
                        final List<double[]> basisCoefficients = new ArrayList<double[]>();
                        for (final double[] c : basisCoefficientsArr) {
                            basisCoefficients.add(c);
                        }
                        final LongstaffSchwartzExerciseStrategy exerciseStrategy =
                                new LongstaffSchwartzExerciseStrategy(basisSystem, basisCoefficients,
                                        evolution, numeraires, nullRebate, control);

                        // 2. bermudan swaption to enter into the payer swap (cpp:1630-1632)
                        final CallSpecifiedMultiProduct bermudanProduct = new CallSpecifiedMultiProduct(
                                new MultiStepNothing(evolution), exerciseStrategy, payerSwap);
                        // 3. callable receiver swap (cpp:1635-1636)
                        final CallSpecifiedMultiProduct callableProduct = new CallSpecifiedMultiProduct(
                                receiverSwap, exerciseStrategy, new ExerciseAdapter(nullRebate));

                        // lower bound: evolve all 4 products together
                        final MultiProductComposite allProducts = new MultiProductComposite();
                        allProducts.add(payerSwap);
                        allProducts.add(receiverSwap);
                        allProducts.add(bermudanProduct);
                        allProducts.add(callableProduct);
                        allProducts.finalizeComposite();

                        final SequenceStatistics stats = MarketModelTestSetup.simulate(evolver, allProducts);
                        checkCallableSwap(stats, config);

                        // upper bound (cpp:1652-1685)
                        final SobolBrownianGeneratorFactory uFactory = new SobolBrownianGeneratorFactory(
                                SobolBrownianGenerator.Ordering.Diagonal, MarketModelTestSetup.seed_ + 142);
                        evolver = MarketModelTestSetup.makeMarketModelEvolver(
                                marketModel, numeraires, uFactory, evolvers[i]);

                        final List<MarketModelEvolver> innerEvolvers = new ArrayList<MarketModelEvolver>();
                        final boolean[] isExerciseTime = Utilities.isInSubset(
                                evolution.evolutionTimes(), exerciseStrategy.exerciseTimes());
                        for (int s = 0; s < isExerciseTime.length; ++s) {
                            if (isExerciseTime[s]) {
                                final MTBrownianGeneratorFactory iFactory = new MTBrownianGeneratorFactory(
                                        MarketModelTestSetup.seed_ + s);
                                final MarketModelEvolver e = MarketModelTestSetup.makeMarketModelEvolver(
                                        marketModel, numeraires, iFactory, evolvers[i], s);
                                innerEvolvers.add(e);
                            }
                        }
                        final int initialNumeraire = evolver.numeraires()[0];
                        final double initialNumeraireValue = MarketModelTestSetup.todaysDiscounts[initialNumeraire];
                        final UpperBoundEngine uEngine = new UpperBoundEngine(evolver, innerEvolvers,
                                receiverSwap, nullRebate, receiverSwap, nullRebate,
                                exerciseStrategy, initialNumeraireValue);
                        final Statistics uStats = new Statistics();
                        uEngine.multiplePathValues(uStats, 255, 256);
                        @SuppressWarnings("unused") final double delta = uStats.mean();
                        @SuppressWarnings("unused") final double deltaError = uStats.errorEstimate();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // testGreeks — cpp:1877 (if_speed(Fast))
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:1877}
     *  {@code BOOST_AUTO_TEST_CASE(testGreeks, *precondition(if_speed(Fast)))}.
     *  Exercises {@link ProxyGreekEngine} to compute caplet Greeks via
     *  partial proxy simulation. The C++ test has no numeric assertions
     *  (it only prints results when {@code printReport_} is set); the
     *  Java port mirrors that — it asserts only that the engine runs to
     *  completion and produces finite outputs. */
    @Test
    public void testGreeks() {
        org.junit.Assume.assumeTrue("test gated -Dql.slowTests=1 to mirror C++ if_speed(Fast)",
                System.getProperty("ql.slowTests") != null);

        final int N = MarketModelTestSetup.todaysForwards.length;
        final Payoff[] payoffs = new Payoff[N];
        @SuppressWarnings("unused")
        final StrikedTypePayoff[] displacedPayoffs = new StrikedTypePayoff[N];
        for (int i = 0; i < N; ++i) {
            payoffs[i] = new CashOrNothingPayoff(Option.Type.Call, MarketModelTestSetup.todaysForwards[i], 0.01);
            displacedPayoffs[i] = new CashOrNothingPayoff(Option.Type.Call,
                    MarketModelTestSetup.todaysForwards[i] + MarketModelTestSetup.displacement, 0.01);
        }

        final MultiStepOptionlets product = new MultiStepOptionlets(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, payoffs);
        final EvolutionDescription evolution = product.evolution();

        final MarketModelTestSetup.MarketModelType[] marketModels = { ExponentialCorrelationAbcdVolatility };
        for (final MarketModelTestSetup.MarketModelType mmType : marketModels) {
            final int[] testedFactors = { 4, 8, MarketModelTestSetup.todaysForwards.length };
            for (final int factors : testedFactors) {
                final MarketModelTestSetup.MeasureType[] measures = { MoneyMarket };
                for (final MarketModelTestSetup.MeasureType measure : measures) {
                    final int[] numeraires = MarketModelTestSetup.makeMeasure(product, measure);

                    final SobolBrownianGeneratorFactory generatorFactory = new SobolBrownianGeneratorFactory(
                            SobolBrownianGenerator.Ordering.Diagonal, MarketModelTestSetup.seed_);

                    final boolean logNormal = true;
                    MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType);
                    final MarketModelEvolver evolver = new LogNormalFwdRateEuler(
                            marketModel, generatorFactory, numeraires);
                    final GenericSequenceStatistics stats =
                            new GenericSequenceStatistics(product.numberOfProducts());

                    final int[] startIndexOfConstraint = new int[evolution.evolutionTimes().length];
                    final int[] endIndexOfConstraint = new int[evolution.evolutionTimes().length];
                    for (int i = 0; i < evolution.evolutionTimes().length; ++i) {
                        startIndexOfConstraint[i] = i;
                        endIndexOfConstraint[i] = i + 1;
                    }

                    // delta/gamma evolver pair + weights (cpp:1944-1969)
                    final double forwardBump = 1.0e-6;
                    marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType, -forwardBump, 0.0);
                    final LogNormalFwdRateEulerConstrained deltaMinus =
                            new LogNormalFwdRateEulerConstrained(marketModel, generatorFactory, numeraires);
                    deltaMinus.setConstraintType(startIndexOfConstraint, endIndexOfConstraint);
                    marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType, forwardBump, 0.0);
                    final LogNormalFwdRateEulerConstrained deltaPlus =
                            new LogNormalFwdRateEulerConstrained(marketModel, generatorFactory, numeraires);
                    deltaPlus.setConstraintType(startIndexOfConstraint, endIndexOfConstraint);

                    final ConstrainedEvolver[] deltaGammaEvolvers = { deltaMinus, deltaPlus };

                    final double[][] deltaGammaWeights = new double[2][3];
                    deltaGammaWeights[0][0] = 0.0;
                    deltaGammaWeights[0][1] = -1.0 / (2.0 * forwardBump);
                    deltaGammaWeights[0][2] =  1.0 / (2.0 * forwardBump);
                    deltaGammaWeights[1][0] = -2.0 / (forwardBump * forwardBump);
                    deltaGammaWeights[1][1] =  1.0 / (forwardBump * forwardBump);
                    deltaGammaWeights[1][2] =  1.0 / (forwardBump * forwardBump);

                    // vega evolver pair + weights (cpp:1972-1992)
                    final double volBump = 1.0e-4;
                    marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType, 0.0, -volBump);
                    final LogNormalFwdRateEulerConstrained vegaMinus =
                            new LogNormalFwdRateEulerConstrained(marketModel, generatorFactory, numeraires);
                    vegaMinus.setConstraintType(startIndexOfConstraint, endIndexOfConstraint);
                    marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType, 0.0, volBump);
                    final LogNormalFwdRateEulerConstrained vegaPlus =
                            new LogNormalFwdRateEulerConstrained(marketModel, generatorFactory, numeraires);
                    vegaPlus.setConstraintType(startIndexOfConstraint, endIndexOfConstraint);

                    final ConstrainedEvolver[] vegaEvolvers = { vegaMinus, vegaPlus };

                    final double[][] vegaWeights = new double[1][3];
                    vegaWeights[0][0] = 0.0;
                    vegaWeights[0][1] = -1.0 / (2.0 * volBump);
                    vegaWeights[0][2] =  1.0 / (2.0 * volBump);

                    final ConstrainedEvolver[][] constrainedEvolvers = { deltaGammaEvolvers, vegaEvolvers };
                    final double[][][] diffWeights = { deltaGammaWeights, vegaWeights };

                    final GenericSequenceStatistics[][] greekStats = new GenericSequenceStatistics[2][];
                    greekStats[0] = new GenericSequenceStatistics[2];
                    greekStats[0][0] = new GenericSequenceStatistics(product.numberOfProducts());
                    greekStats[0][1] = new GenericSequenceStatistics(product.numberOfProducts());
                    greekStats[1] = new GenericSequenceStatistics[1];
                    greekStats[1][0] = new GenericSequenceStatistics(product.numberOfProducts());

                    final int initialNumeraire = evolver.numeraires()[0];
                    final double initialNumeraireValue = MarketModelTestSetup.todaysDiscounts[initialNumeraire];

                    final ProxyGreekEngine engine = new ProxyGreekEngine(evolver, constrainedEvolvers,
                            diffWeights, startIndexOfConstraint, endIndexOfConstraint, product,
                            initialNumeraireValue);
                    engine.multiplePathValues(stats, greekStats, MarketModelTestSetup.paths_);

                    // C++ has no numerical assertions (printReport_ only). Mirror by
                    // checking outputs are finite — covers the engine's contract.
                    final org.jquantlib.math.matrixutilities.Array values = stats.mean();
                    final org.jquantlib.math.matrixutilities.Array errors = stats.errorEstimate();
                    final org.jquantlib.math.matrixutilities.Array deltas = greekStats[0][0].mean();
                    final org.jquantlib.math.matrixutilities.Array gammas = greekStats[0][1].mean();
                    final org.jquantlib.math.matrixutilities.Array vegas  = greekStats[1][0].mean();
                    for (int i = 0; i < N; ++i) {
                        assertTrue("value[" + i + "] non-finite", !Double.isNaN(values.get(i)) && !Double.isInfinite(values.get(i)));
                        assertTrue("error[" + i + "] non-finite", !Double.isNaN(errors.get(i)) && !Double.isInfinite(errors.get(i)));
                        assertTrue("delta[" + i + "] non-finite", !Double.isNaN(deltas.get(i)) && !Double.isInfinite(deltas.get(i)));
                        assertTrue("gamma[" + i + "] non-finite", !Double.isNaN(gammas.get(i)) && !Double.isInfinite(gammas.get(i)));
                        assertTrue("vega["  + i + "] non-finite", !Double.isNaN(vegas.get(i))  && !Double.isInfinite(vegas.get(i)));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // testPathwiseGreeks — cpp:2090
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:2090} {@code BOOST_AUTO_TEST_CASE(testPathwiseGreeks)}. */
    @Test
    public void testPathwiseGreeks() {
        final int N = MarketModelTestSetup.todaysForwards.length;
        final List<StrikedTypePayoff> displacedPayoffs = new ArrayList<StrikedTypePayoff>(N);
        for (int i = 0; i < N; ++i) {
            displacedPayoffs.add(new PlainVanillaPayoff(Option.Type.Call,
                    MarketModelTestSetup.todaysForwards[i] + MarketModelTestSetup.displacement));
        }

        for (int whichProduct = 0; whichProduct < 2; ++whichProduct) {
            // C++ swaps the order: whichProduct==0 → product2 (non-deflated), 1 → product1 (deflated)
            final MarketModelPathwiseMultiProduct product;
            if (whichProduct == 0) {
                product = new MarketModelPathwiseMultiCaplet(MarketModelTestSetup.rateTimes,
                        MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes,
                        MarketModelTestSetup.todaysForwards);
            } else {
                product = new MarketModelPathwiseMultiDeflatedCaplet(MarketModelTestSetup.rateTimes,
                        MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes,
                        MarketModelTestSetup.todaysForwards);
            }

            // productDummy is needed for makeMeasure (it's MarketModelMultiProduct);
            // build a parallel non-pathwise MultiStepOptionlets with PlainVanilla.
            final Payoff[] payoffs = new Payoff[N];
            for (int i = 0; i < N; ++i) {
                payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, MarketModelTestSetup.todaysForwards[i]);
            }
            final MultiStepOptionlets productDummy = new MultiStepOptionlets(MarketModelTestSetup.rateTimes,
                    MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, payoffs);

            final EvolutionDescription evolution = product.evolution();

            final MarketModelTestSetup.MarketModelType[] marketModels = {
                    ExponentialCorrelationAbcdVolatility
            };
            for (final MarketModelTestSetup.MarketModelType mmType : marketModels) {
                final int[] testedFactors = { 2 };
                for (final int factors : testedFactors) {
                    final MarketModelTestSetup.MeasureType[] measures = { MoneyMarket };
                    for (final MarketModelTestSetup.MeasureType measure : measures) {
                        final int[] numeraires = MarketModelTestSetup.makeMeasure(productDummy, measure);
                        final MTBrownianGeneratorFactory generatorFactory =
                                new MTBrownianGeneratorFactory(MarketModelTestSetup.seed_);
                        final boolean logNormal = true;
                        final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                                logNormal, evolution, factors, mmType);

                        final LogNormalFwdRateEuler evolver = new LogNormalFwdRateEuler(
                                marketModel, generatorFactory, numeraires);
                        final SequenceStatistics stats = new SequenceStatistics(
                                product.numberOfProducts() * (N + 1));

                        final double forwardBump = 1.0e-6;

                        final int initialNumeraire = evolver.numeraires()[0];
                        final double initialNumeraireValue =
                                MarketModelTestSetup.todaysDiscounts[initialNumeraire];

                        final PathwiseAccountingEngine engine = new PathwiseAccountingEngine(
                                evolver, product, marketModel, initialNumeraireValue);
                        engine.multiplePathValues(stats, MarketModelTestSetup.paths_);

                        final org.jquantlib.math.matrixutilities.Array valuesAndDeltas = stats.mean();
                        final org.jquantlib.math.matrixutilities.Array errors = stats.errorEstimate();

                        final double[] prices = new double[product.numberOfProducts()];
                        final double[] priceErrors = new double[product.numberOfProducts()];
                        final double[][] deltas =
                                new double[product.numberOfProducts()][N];
                        final double[][] deltasErrors =
                                new double[product.numberOfProducts()][N];
                        final double[] modelPrices = new double[product.numberOfProducts()];

                        for (int i = 0; i < product.numberOfProducts(); ++i) {
                            prices[i] = valuesAndDeltas.get(i);
                            priceErrors[i] = errors.get(i);
                            modelPrices[i] = new BlackCalculator(displacedPayoffs.get(i),
                                    MarketModelTestSetup.todaysForwards[i],
                                    MarketModelTestSetup.volatilities[i]
                                            * Math.sqrt(MarketModelTestSetup.rateTimes[i]),
                                    MarketModelTestSetup.todaysDiscounts[i + 1]
                                            * (MarketModelTestSetup.rateTimes[i + 1]
                                                    - MarketModelTestSetup.rateTimes[i])).value();
                            for (int j = 0; j < N; ++j) {
                                deltas[i][j] = valuesAndDeltas.get((i + 1) * product.numberOfProducts() + j);
                                deltasErrors[i][j] = errors.get((i + 1) * product.numberOfProducts() + j);
                            }
                        }

                        final double[][] modelDeltas = new double[product.numberOfProducts()][N];

                        final double[] discPlus = new double[N + 1];
                        final double[] discMinus = new double[N + 1];
                        Arrays.fill(discPlus, MarketModelTestSetup.todaysDiscounts[0]);
                        Arrays.fill(discMinus, MarketModelTestSetup.todaysDiscounts[0]);
                        final double[] fwdPlus = new double[N];
                        final double[] fwdMinus = new double[N];

                        for (int i = 0; i < N; ++i) {
                            for (int j = 0; j < N; ++j) {
                                if (i != j) {
                                    fwdPlus[j] = MarketModelTestSetup.todaysForwards[j];
                                    fwdMinus[j] = MarketModelTestSetup.todaysForwards[j];
                                } else {
                                    fwdPlus[j] = MarketModelTestSetup.todaysForwards[j] + forwardBump;
                                    fwdMinus[j] = MarketModelTestSetup.todaysForwards[j] - forwardBump;
                                }
                                final double tau =
                                        MarketModelTestSetup.rateTimes[j + 1] - MarketModelTestSetup.rateTimes[j];
                                discPlus[j + 1] = discPlus[j] / (1.0 + fwdPlus[j] * tau);
                                discMinus[j + 1] = discMinus[j] / (1.0 + fwdMinus[j] * tau);
                            }
                            for (int k = 0; k < product.numberOfProducts(); ++k) {
                                final double tau =
                                        MarketModelTestSetup.rateTimes[k + 1] - MarketModelTestSetup.rateTimes[k];
                                final double priceUp = new BlackCalculator(displacedPayoffs.get(k), fwdPlus[k],
                                        MarketModelTestSetup.volatilities[k]
                                                * Math.sqrt(MarketModelTestSetup.rateTimes[k]),
                                        discPlus[k + 1] * tau).value();
                                final double priceDown = new BlackCalculator(displacedPayoffs.get(k), fwdMinus[k],
                                        MarketModelTestSetup.volatilities[k]
                                                * Math.sqrt(MarketModelTestSetup.rateTimes[k]),
                                        discMinus[k + 1] * tau).value();
                                modelDeltas[k][i] = (priceUp - priceDown) / (2 * forwardBump);
                            }
                        }

                        int numberErrors = 0;
                        for (int i = 0; i < product.numberOfProducts(); ++i) {
                            final double thisPrice = prices[i];
                            final double thisModelPrice = modelPrices[i];
                            final double priceErrorInSds = (thisPrice - thisModelPrice) / priceErrors[i];
                            final double errorThreshold = 3.5;
                            if (Math.abs(priceErrorInSds) > errorThreshold) {
                                ++numberErrors;
                            }
                            final double threshold = 1e-10;
                            for (int j = 0; j < N; ++j) {
                                final double delta = deltas[i][j];
                                final double modelDelta = modelDeltas[i][j];
                                double deltaErrorInSds = 100;
                                if (deltasErrors[i][j] > 0.0) {
                                    deltaErrorInSds = (delta - modelDelta) / deltasErrors[i][j];
                                } else if (Math.abs(modelDelta - delta) < threshold) {
                                    deltaErrorInSds = 0.0;
                                }
                                if (Math.abs(deltaErrorInSds) > errorThreshold) {
                                    ++numberErrors;
                                }
                            }
                        }
                        if (numberErrors > 0) {
                            fail("Pathwise greeks test (whichProduct=" + whichProduct + ") has "
                                    + numberErrors + " errors");
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // testAbcdVolatilityIntegration — cpp:4133
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:4133} {@code BOOST_AUTO_TEST_CASE(testAbcdVolatilityIntegration)}. */
    @Test
    public void testAbcdVolatilityIntegration() {
        final double a = -0.0597;
        final double b =  0.1677;
        final double c =  0.5403;
        final double d =  0.1710;

        final int N = 10;
        final double precision = 1e-04;

        final AbcdFunction instVol = new AbcdFunction(a, b, c, d);
        final SegmentIntegral SI = new SegmentIntegral(20000);
        for (int i = 0; i < N; ++i) {
            final double T1 = 0.5 * (1 + i);
            for (int k = 0; k < N - i; ++k) {
                final double T2 = 0.5 * (1 + k);
                for (int j = 0; j < N; ++j) {
                    final double xMin = 0.5 * j;
                    for (int l = 0; l < N - j; ++l) {
                        final double xMax = xMin + 0.5 * l;
                        final AbcdSquared abcd2 = new AbcdSquared(a, b, c, d, T1, T2);
                        final double numerical = SI.op(new org.jquantlib.math.Ops.DoubleOp() {
                            @Override public double op(final double x) { return abcd2.op(x); }
                        }, xMin, xMax);
                        final double analytical = instVol.covariance(xMin, xMax, T1, T2);
                        if (Math.abs(analytical - numerical) > precision) {
                            fail("T1=" + T1 + ", T2=" + T2 + ", xMin=" + xMin + ", xMax=" + xMax
                                    + ", analytical: " + analytical + ", numerical: " + numerical);
                        }
                        if (T1 == T2) {
                            final double variance = instVol.variance(xMin, xMax, T1);
                            if (Math.abs(analytical - variance) > 1e-14) {
                                fail("T1=" + T1 + ", T2=" + T2 + ", xMin=" + xMin + ", xMax=" + xMax
                                        + ", variance: " + variance + ", analytical: " + analytical);
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // testAbcdVolatilityCompare — cpp:4186
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:4186} {@code BOOST_AUTO_TEST_CASE(testAbcdVolatilityCompare)}. */
    @Test
    public void testAbcdVolatilityCompare() {
        // Parameters following Rebonato / Brigo-Mercurio
        final double a = 0.0597;
        final double b = 0.1677;
        final double c = 0.5403;
        final double d = 0.1710;

        final double[] rt = MarketModelTestSetup.rateTimes;
        final List<Double> rtList = new ArrayList<Double>(rt.length);
        for (final double x : rt) {
            rtList.add(x);
        }

        // C++ LmExtLinearExponentialVolModel ctor: (fixingTimes, a_lm, b_lm, c_lm, d_lm)
        // Brigo-Mercurio mapping: abcd-d -> lm-a, abcd-a -> lm-b, abcd-b -> lm-c, abcd-c -> lm-d.
        // C++ call: LmExtLinearExponentialVolModel(rateTimes, b, c, d, a)
        // — i.e. (lm-a=b, lm-b=c, lm-c=d, lm-d=a).
        final LmExtLinearExponentialVolModel lmAbcd =
                new LmExtLinearExponentialVolModel(rtList, b, c, d, a);
        final AbcdFunction abcd = new AbcdFunction(a, b, c, d);

        for (int i1 = 0; i1 < rt.length; ++i1) {
            for (int i2 = 0; i2 < rt.length; ++i2) {
                double T = 0.0;
                do {
                    final double lmCovariance = lmAbcd.integratedVariance(i1, i2, T);
                    final double abcdCovariance = abcd.covariance(0, T, rt[i1], rt[i2]);
                    if (Math.abs(lmCovariance - abcdCovariance) > 1e-10) {
                        fail("T1=" + rt[i1] + ", T2=" + rt[i2] + ", xMin=0, xMax=" + T
                                + ", abcd: " + abcdCovariance + ", lm: " + lmCovariance);
                    }
                    T += 0.5;
                } while (T < Math.min(rt[i1], rt[i2]));
            }
        }
    }

    // ------------------------------------------------------------------
    // testAbcdVolatilityFit — cpp:4234
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:4234} {@code BOOST_AUTO_TEST_CASE(testAbcdVolatilityFit)}. */
    @Test
    public void testAbcdVolatilityFit() {
        final double[] rt = MarketModelTestSetup.rateTimes;
        final double[] bv = MarketModelTestSetup.blackVols;

        // C++: std::vector<Time>(rateTimes.begin(), rateTimes.end()-1)
        final List<Double> tList = new ArrayList<Double>(rt.length - 1);
        for (int i = 0; i < rt.length - 1; ++i) {
            tList.add(rt[i]);
        }
        final List<Double> blackVolsList = new ArrayList<Double>(bv.length);
        for (final double x : bv) {
            blackVolsList.add(x);
        }

        final AbcdCalibration instVol = new AbcdCalibration(tList, blackVolsList);
        final double a0 = instVol.a_;
        final double b0 = instVol.b_;
        final double c0 = instVol.c_;
        final double d0 = instVol.d_;
        final double error0 = instVol.error();

        instVol.compute();

        final EndCriteria.Type ec = instVol.endCriteria();
        final double a1 = instVol.a_;
        final double b1 = instVol.b_;
        final double c1 = instVol.c_;
        final double d1 = instVol.d_;
        final double error1 = instVol.error();

        if (error1 >= error0) {
            fail("Parameters:\na: " + a0 + " -> " + a1
                    + "\nb: " + b0 + " -> " + b1
                    + "\nc: " + c0 + " -> " + c1
                    + "\nd: " + d0 + " -> " + d1
                    + "\nerror: " + error0 + " -> " + error1);
        }

        final AbcdFunction abcd = new AbcdFunction(a1, b1, c1, d1);
        final List<Double> k = instVol.k(tList, blackVolsList);
        final double tol = 3.0e-4;
        for (int i = 0; i < blackVolsList.size(); ++i) {
            if (Math.abs(k.get(i) - 1.0) > tol) {
                final double modelVol = abcd.volatility(0.0, rt[i], rt[i]);
                fail("\nEndCriteria = " + ec
                        + "\nFixing Time = " + rt[i]
                        + "\nMktVol = " + bv[i]
                        + "\nModVol = " + modelVol
                        + "\nk = " + k.get(i)
                        + "\nerror = " + Math.abs(k.get(i) - 1.0)
                        + "\ntol = " + tol);
            }
        }
        // also verify ec was set
        assertNotEquals("EndCriteria must have triggered", EndCriteria.Type.None, ec);
        assertTrue("error improved", error1 < error0);
    }

    // ------------------------------------------------------------------
    // testStochVolForwardsAndOptionlets — cpp:4285
    // ------------------------------------------------------------------

    /** Faithful port of {@code test-suite/marketmodel.cpp:4285} {@code BOOST_AUTO_TEST_CASE(testStochVolForwardsAndOptionlets)}. */
    @Test
    public void testStochVolForwardsAndOptionlets() {
        final int N = MarketModelTestSetup.todaysForwards.length;
        final double[] forwardStrikes = new double[N];
        final Payoff[] optionletPayoffs = new Payoff[N];
        for (int i = 0; i < N; ++i) {
            forwardStrikes[i] = MarketModelTestSetup.todaysForwards[i] + 0.01;
            optionletPayoffs[i] = new PlainVanillaPayoff(Option.Type.Call, MarketModelTestSetup.todaysForwards[i]);
        }

        final MultiStepForwards forwards = new MultiStepForwards(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, forwardStrikes);
        final MultiStepOptionlets optionlets = new MultiStepOptionlets(MarketModelTestSetup.rateTimes,
                MarketModelTestSetup.accruals, MarketModelTestSetup.paymentTimes, optionletPayoffs);

        final MultiProductComposite product = new MultiProductComposite();
        product.add(forwards);
        product.add(optionlets);
        product.finalizeComposite();

        final EvolutionDescription evolution = product.evolution();

        final MarketModelTestSetup.MarketModelType[] marketModels = {
                ExponentialCorrelationFlatVolatility
        };

        final int firstVolatilityFactor = 2;
        final int volatilityFactorStep = 2;

        final double meanLevel = 1.0;
        final double reversionSpeed = 1.0;

        final double volVar = 1;
        final double v0 = 1.0;
        final int numberSubSteps = 8;
        final double w1 = 0.5;
        final double w2 = 0.5;
        final double cutPoint = 1.5;

        final MarketModelVolProcess volProcess = new SquareRootAndersen(
                meanLevel, reversionSpeed, volVar, v0,
                evolution.evolutionTimes(), numberSubSteps, w1, w2, cutPoint);

        final DayCounter dc = MarketModelTestSetup.dayCounter;
        final Date today = MarketModelTestSetup.todaysDate;

        for (final MarketModelTestSetup.MarketModelType mmType : marketModels) {
            final int[] testedFactors = { 1, 2, MarketModelTestSetup.todaysForwards.length };
            for (final int factors : testedFactors) {
                final MarketModelTestSetup.MeasureType[] measures = { MoneyMarket, Terminal };
                for (final MarketModelTestSetup.MeasureType measure : measures) {
                    final int[] numeraires = MarketModelTestSetup.makeMeasure(product, measure);
                    final boolean logNormal = true;
                    final MarketModel marketModel = MarketModelTestSetup.makeMarketModel(
                            logNormal, evolution, factors, mmType);

                    final MTBrownianGeneratorFactory generatorFactory =
                            new MTBrownianGeneratorFactory(MarketModelTestSetup.seed_);
                    final MarketModelEvolver evolver = new SVDDFwdRatePc(marketModel, generatorFactory,
                            volProcess, firstVolatilityFactor, volatilityFactorStep, numeraires);

                    final SequenceStatistics stats = MarketModelTestSetup.simulate(evolver, product);
                    final org.jquantlib.math.matrixutilities.Array results = stats.mean();
                    final org.jquantlib.math.matrixutilities.Array errors = stats.errorEstimate();

                    final int sz = MarketModelTestSetup.accruals.length;
                    // check forwards
                    for (int i = 0; i < sz; ++i) {
                        final double trueValue = MarketModelTestSetup.todaysDiscounts[i]
                                - MarketModelTestSetup.todaysDiscounts[i + 1]
                                        * (1 + forwardStrikes[i] * MarketModelTestSetup.accruals[i]);
                        final double error = results.get(i) - trueValue;
                        final double errorSds = error / errors.get(i);
                        if (Math.abs(errorSds) > 3.5) {
                            fail("error in sds: " + errorSds + " for forward " + i
                                    + " in SV LMM test. True value:" + trueValue
                                    + ", actual value: " + results.get(i)
                                    + ", standard error " + errors.get(i));
                        }
                    }
                    // check caplets
                    for (int i = 0; i < sz; ++i) {
                        final double volCoeff = MarketModelTestSetup.volatilities[i];
                        final double theta = volCoeff * volCoeff * meanLevel;
                        final double kappa = reversionSpeed;
                        final double sigma = volCoeff * volVar;
                        final double rho = 0.0;
                        final double v1 = v0 * volCoeff * volCoeff;

                        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call,
                                MarketModelTestSetup.todaysForwards[i] + MarketModelTestSetup.displacement);

                        final Handle<YieldTermStructure> rfHandle = new Handle<YieldTermStructure>(
                                new FlatForward(today, 0.0, dc));
                        final Handle<YieldTermStructure> divHandle = new Handle<YieldTermStructure>(
                                new FlatForward(today, 0.0, dc));
                        final Handle<Quote> s0Handle = new Handle<Quote>(new SimpleQuote(
                                MarketModelTestSetup.todaysForwards[i] + MarketModelTestSetup.displacement));

                        final HestonProcess process = new HestonProcess(rfHandle, divHandle, s0Handle,
                                v1, kappa, theta, sigma, rho);
                        final HestonModel hestonModel = new HestonModel(process);
                        final AnalyticHestonEngine engine = new AnalyticHestonEngine(hestonModel, process, 128);
                        double trueValue = engine.priceVanillaPayoff(payoff,
                                MarketModelTestSetup.rateTimes[i]);
                        trueValue *= MarketModelTestSetup.accruals[i]
                                * MarketModelTestSetup.todaysDiscounts[i + 1];

                        final double error = results.get(i + sz) - trueValue;
                        final double errorSds = error / errors.get(i);
                        if (Math.abs(errorSds) > 4) {
                            fail("error in sds: " + errorSds + " for caplet " + i
                                    + " in SV LMM test. True value:" + trueValue
                                    + ", actual value: " + results.get(i + sz)
                                    + ", standard error " + errors.get(i));
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Helper utilities
    // ------------------------------------------------------------------

    /** Discard unused-import warnings (used in JavaDoc references only). */
    @SuppressWarnings("unused")
    private static final Class<?> UNUSED_DC = SimpleDayCounter.class;

    private static double[] filled(final int n, final double v) {
        final double[] a = new double[n];
        Arrays.fill(a, v);
        return a;
    }
}
