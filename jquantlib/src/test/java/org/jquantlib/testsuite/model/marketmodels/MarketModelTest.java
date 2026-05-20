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
import org.jquantlib.model.marketmodels.SwapForwardMappings;
import org.jquantlib.model.marketmodels.browniangenerators.MTBrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.models.FwdPeriodAdapter;
import org.jquantlib.model.marketmodels.models.FwdToCotSwapAdapter;
import org.jquantlib.model.marketmodels.products.MultiProductComposite;
import org.jquantlib.model.marketmodels.products.multistep.MultiProductPathwiseWrapper;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoinitialSwaps;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoterminalSwaps;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoterminalSwaptions;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepForwards;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepInverseFloater;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepOptionlets;
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
        boolean testBias = true;
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
    // Helper utilities
    // ------------------------------------------------------------------

    private static double[] filled(final int n, final double v) {
        final double[] a = new double[n];
        Arrays.fill(a, v);
        return a;
    }
}
