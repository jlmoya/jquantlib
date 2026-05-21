/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 1 closure (Path A) — A1-545.

 This source code is release under the BSD License.

 This file ports v1.42.1 test-suite/marketmodel.cpp file-scope helpers.

 The reference QuantLib is licensed under the QuantLib license; this Java
 port is licensed under the BSD License.
 */
package org.jquantlib.testsuite.model.marketmodels;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.SimpleDayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.statistics.GenericSequenceStatistics;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.jquantlib.model.marketmodels.AccountingEngine;
import org.jquantlib.model.marketmodels.BrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.MarketModelEvolver;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.browniangenerators.MTBrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.correlations.TimeHomogeneousForwardCorrelation;
import org.jquantlib.model.marketmodels.evolvers.LogNormalCotSwapRatePc;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateBalland;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateIpc;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRatePc;
import org.jquantlib.model.marketmodels.evolvers.NormalFwdRatePc;
import org.jquantlib.model.marketmodels.models.AbcdVol;
import org.jquantlib.model.marketmodels.models.FlatVol;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Faithful Java extraction of v1.42.1 {@code test-suite/marketmodel.cpp:158-269}
 * fixture helpers. Provides shared setup for the 16 {@code marketmodel.cpp}
 * Monte-Carlo tests previously BLOCKED on this missing helper.
 *
 * <p>The C++ file-scope globals {@code todaysDate, dates, rateTimes, ...} are
 * mirrored here as {@code public static} fields. {@link #setup()} initializes
 * them per a v1.42.1 reproduction; each test should call {@link #setup()}
 * (in {@code @Before}) before consuming the fields.
 *
 * <p>{@link #setup()} mirrors the C++ {@code _DEBUG} branch (the small
 * {@code paths_=127, trainingPaths_=31}) by default — Java tests run the
 * "debug" path-count to keep wall-time per test manageable; for the full
 * v1.42.1 release-build sweep ({@code paths_=32767, trainingPaths_=8191})
 * callers may set {@link #paths_} and {@link #trainingPaths_} manually
 * after {@code setup()}.
 *
 * <p>This is a faithful 1:1 line-mapping; no business-logic changes.
 */
public final class MarketModelTestSetup {

    // ------------------------------------------------------------------
    // Times (cpp:128-133)
    // ------------------------------------------------------------------
    public static Date todaysDate;
    public static Date startDate;
    public static Date endDate;
    public static Schedule dates;
    public static double[] rateTimes;
    public static double[] paymentTimes;
    public static double[] accruals;
    public static Calendar calendar;
    public static SimpleDayCounter dayCounter;

    // ------------------------------------------------------------------
    // Rates and displacement (cpp:134-138)
    // ------------------------------------------------------------------
    public static double[] todaysForwards;
    public static double[] todaysCoterminalSwapRates;
    public static double meanForward;
    public static double[] coterminalAnnuity;
    public static double displacement;
    public static double[] todaysDiscounts;

    // ------------------------------------------------------------------
    // Volatilities (cpp:139-140)
    // ------------------------------------------------------------------
    public static double[] volatilities;
    public static double[] blackVols;
    public static double[] normalVols;
    public static double[] swaptionsVolatilities;
    public static double[] swaptionsBlackVols;

    // ------------------------------------------------------------------
    // Abcd parameters (cpp:141)
    // ------------------------------------------------------------------
    public static double a, b, c, d;

    // ------------------------------------------------------------------
    // Correlation / measure / MC (cpp:142-146)
    // ------------------------------------------------------------------
    public static double longTermCorrelation;
    public static double beta;
    public static int measureOffset_;
    public static long seed_;
    public static int paths_;
    public static int trainingPaths_;
    public static boolean printReport_ = false;

    private MarketModelTestSetup() {
        // utility class — no instances
    }

    // ------------------------------------------------------------------
    // setup() — cpp:158-268
    // ------------------------------------------------------------------

    /**
     * Faithful port of v1.42.1 {@code setup()} (test-suite/marketmodel.cpp:158).
     * Initializes all static fields used by the 16 marketmodel.cpp tests.
     */
    public static void setup() {

        // Times -----------------------------------------------------------
        calendar = new NullCalendar();
        todaysDate = new Settings().evaluationDate();
        endDate = todaysDate.add(new Period(5, TimeUnit.Years));
        dates = new Schedule(todaysDate, endDate, new Period(Frequency.Semiannual),
                calendar, BusinessDayConvention.Following, BusinessDayConvention.Following,
                DateGeneration.Rule.Backward, false);
        rateTimes = new double[dates.size() - 1];
        paymentTimes = new double[rateTimes.length - 1];
        accruals = new double[rateTimes.length - 1];
        dayCounter = new SimpleDayCounter();
        for (int i = 1; i < dates.size(); ++i) {
            rateTimes[i - 1] = dayCounter.yearFraction(todaysDate, dates.dates().get(i));
        }
        // std::copy(rateTimes.begin()+1, rateTimes.end(), paymentTimes.begin());
        System.arraycopy(rateTimes, 1, paymentTimes, 0, rateTimes.length - 1);
        for (int i = 1; i < rateTimes.length; ++i) {
            accruals[i - 1] = rateTimes[i] - rateTimes[i - 1];
        }

        // Rates & displacement --------------------------------------------
        todaysForwards = new double[paymentTimes.length];
        displacement = 0.0;
        meanForward = 0.0;
        for (int i = 0; i < todaysForwards.length; ++i) {
            todaysForwards[i] = 0.03 + 0.0010 * i;
            meanForward += todaysForwards[i];
        }
        meanForward /= todaysForwards.length;

        // Discounts -------------------------------------------------------
        todaysDiscounts = new double[rateTimes.length];
        todaysDiscounts[0] = 0.95;
        for (int i = 1; i < rateTimes.length; ++i) {
            todaysDiscounts[i] = todaysDiscounts[i - 1] /
                    (1.0 + todaysForwards[i - 1] * accruals[i - 1]);
        }

        // Coterminal swap rates & annuities -------------------------------
        final int N = todaysForwards.length;
        todaysCoterminalSwapRates = new double[N];
        coterminalAnnuity = new double[N];
        double floatingLeg = 0.0;
        for (int i = 1; i <= N; ++i) {
            if (i == 1) {
                coterminalAnnuity[N - 1] = accruals[N - 1] * todaysDiscounts[N];
            } else {
                coterminalAnnuity[N - i] = coterminalAnnuity[N - i + 1] +
                        accruals[N - i] * todaysDiscounts[N - i + 1];
            }
            floatingLeg = todaysDiscounts[N - i] - todaysDiscounts[N];
            todaysCoterminalSwapRates[N - i] = floatingLeg / coterminalAnnuity[N - i];
        }

        // Cap/Floor Volatilities (cpp:214-234) ----------------------------
        final double[] mktVols = {
                0.15541283,
                0.18719678,
                0.20890740,
                0.22318179,
                0.23212717,
                0.23731450,
                0.23988649,
                0.24066384,
                0.24023111,
                0.23900189,
                0.23726699,
                0.23522952,
                0.23303022,
                0.23076564,
                0.22850101,
                0.22627951,
                0.22412881,
                0.22206569,
                0.22009939
        };

        a = -0.0597;
        b = 0.1677;
        c = 0.5403;
        d = 0.1710;
        volatilities = new double[todaysForwards.length];
        blackVols = new double[todaysForwards.length];
        normalVols = new double[todaysForwards.length];
        final int lim = Math.min(mktVols.length, todaysForwards.length);
        for (int i = 0; i < lim; ++i) {
            volatilities[i] = todaysForwards[i] * mktVols[i] /
                    (todaysForwards[i] + displacement);
            blackVols[i] = mktVols[i];
            normalVols[i] = mktVols[i] * todaysForwards[i];
        }

        // Swaption volatility quick fix -----------------------------------
        swaptionsVolatilities = volatilities.clone();

        // Cap/Floor Correlation -------------------------------------------
        longTermCorrelation = 0.5;
        beta = 0.2;
        measureOffset_ = 5;

        // Monte Carlo -----------------------------------------------------
        seed_ = 42L;

        // C++ #ifdef _DEBUG branch — default to the "debug" small path-count
        // to keep Java JUnit wall-time per test manageable. Tests requiring
        // the full release-build path-count ({@code paths_=32767,
        // trainingPaths_=8191}) may overwrite these fields after {@code
        // setup()} returns. Mirrors cpp:261-267.
        paths_ = 127;
        trainingPaths_ = 31;
    }

    // ------------------------------------------------------------------
    // MarketModelType enum (cpp:124-126)
    // ------------------------------------------------------------------

    public enum MarketModelType {
        ExponentialCorrelationFlatVolatility,
        ExponentialCorrelationAbcdVolatility
        // CalibratedMM is commented out in v1.42.1
    }

    /** Port of v1.42.1 {@code marketModelTypeToString} (cpp:284-295). */
    public static String marketModelTypeToString(final MarketModelType type) {
        switch (type) {
          case ExponentialCorrelationFlatVolatility:
            return "Exp. Corr. Flat Vol.";
          case ExponentialCorrelationAbcdVolatility:
            return "Exp. Corr. Abcd Vol.";
          default:
            throw new IllegalArgumentException("unknown MarketModelEvolver type");
        }
    }

    /**
     * Port of v1.42.1 {@code makeMarketModel} (cpp:297-361). Two-argument
     * default-forwarding overload (forwardBump=0, volBump=0).
     */
    public static MarketModel makeMarketModel(final boolean logNormal,
            final EvolutionDescription evolution,
            final int numberOfFactors,
            final MarketModelType marketModelType) {
        return makeMarketModel(logNormal, evolution, numberOfFactors, marketModelType, 0.0, 0.0);
    }

    /**
     * Port of v1.42.1 {@code makeMarketModel} (cpp:297-361).
     *
     * <p>Constructs {@link FlatVol} or {@link AbcdVol} based on
     * {@link MarketModelType}, optionally bumping initial forwards and
     * vols. The v1.42.1 dead-code path {@code CalibratedMM} stays
     * commented out per upstream.
     */
    public static MarketModel makeMarketModel(final boolean logNormal,
            final EvolutionDescription evolution,
            final int numberOfFactors,
            final MarketModelType marketModelType,
            final double forwardBump,
            final double volBump) {

        // Note: the LmExtLinearExponentialVolModel + LmLinearExponentialCorrelationModel
        // construction in the v1.42.1 C++ (cpp:306-310) is computed but only
        // used by the dead-code CalibratedMM branch. Java mirrors that by
        // omitting the unused locals.

        final double[] bumpedForwards = new double[todaysForwards.length];
        for (int i = 0; i < bumpedForwards.length; ++i) {
            bumpedForwards[i] = todaysForwards[i] + forwardBump;
        }

        final double[] bumpedVols = new double[volatilities.length];
        if (logNormal) {
            for (int i = 0; i < bumpedVols.length; ++i) {
                bumpedVols[i] = volatilities[i] + volBump;
            }
        } else {
            for (int i = 0; i < bumpedVols.length; ++i) {
                bumpedVols[i] = normalVols[i] + volBump;
            }
        }

        final Matrix correlations = ExponentialForwardCorrelation.exponentialCorrelations(
                evolution.rateTimes(), longTermCorrelation, beta, 1.0, 0.0);
        final PiecewiseConstantCorrelation corr = new TimeHomogeneousForwardCorrelation(
                correlations, doubleArrayToList(evolution.rateTimes()));

        final double[] displacements = new double[bumpedForwards.length];
        for (int i = 0; i < displacements.length; ++i) {
            displacements[i] = displacement;
        }

        switch (marketModelType) {
          case ExponentialCorrelationFlatVolatility:
            return new FlatVol(bumpedVols, corr, evolution, numberOfFactors,
                    bumpedForwards, displacements);
          case ExponentialCorrelationAbcdVolatility:
            return new AbcdVol(0.0, 0.0, 1.0, 1.0, bumpedVols, corr, evolution,
                    numberOfFactors, bumpedForwards, displacements);
          default:
            throw new IllegalArgumentException("unknown MarketModel type");
        }
    }

    // ------------------------------------------------------------------
    // MeasureType enum (cpp:363-364)
    // ------------------------------------------------------------------

    public enum MeasureType {
        ProductSuggested,
        Terminal,
        MoneyMarket,
        MoneyMarketPlus
    }

    /** Port of v1.42.1 {@code measureTypeToString} (cpp:366-379). */
    public static String measureTypeToString(final MeasureType type) {
        switch (type) {
          case ProductSuggested:
            return "ProductSuggested measure";
          case Terminal:
            return "Terminal measure";
          case MoneyMarket:
            return "Money Market measure";
          case MoneyMarketPlus:
            return "Money Market Plus measure";
          default:
            throw new IllegalArgumentException("unknown measure type");
        }
    }

    /**
     * Port of v1.42.1 {@code makeMeasure} (cpp:381-418).
     *
     * <p>Returns the numeraire vector for the given product+measure type.
     * The C++ {@code BOOST_ERROR} calls become {@link IllegalStateException}
     * so failures propagate to JUnit.
     */
    public static int[] makeMeasure(final MarketModelMultiProduct product,
            final MeasureType measureType) {
        int[] result;
        final EvolutionDescription evolution = product.evolution();
        switch (measureType) {
          case ProductSuggested:
            result = product.suggestedNumeraires();
            break;
          case Terminal:
            result = EvolutionDescription.terminalMeasure(evolution);
            if (!EvolutionDescription.isInTerminalMeasure(evolution, result)) {
                throw new IllegalStateException(
                        "failure in verifying Terminal measure: " + intArrayToString(result));
            }
            break;
          case MoneyMarket:
            result = EvolutionDescription.moneyMarketMeasure(evolution);
            if (!EvolutionDescription.isInMoneyMarketMeasure(evolution, result)) {
                throw new IllegalStateException(
                        "failure in verifying MoneyMarket measure: " + intArrayToString(result));
            }
            break;
          case MoneyMarketPlus:
            result = EvolutionDescription.moneyMarketPlusMeasure(evolution, measureOffset_);
            if (!EvolutionDescription.isInMoneyMarketPlusMeasure(evolution, result, measureOffset_)) {
                throw new IllegalStateException(
                        "failure in verifying MoneyMarketPlus(" + measureOffset_
                                + ") measure: " + intArrayToString(result));
            }
            break;
          default:
            throw new IllegalArgumentException("unknown measure type");
        }
        EvolutionDescription.checkCompatibility(evolution, result);
        return result;
    }

    // ------------------------------------------------------------------
    // EvolverType enum (cpp:420)
    // ------------------------------------------------------------------

    public enum EvolverType { Ipc, Balland, Pc, NormalPc, CotSwapPc }

    /** Port of v1.42.1 {@code evolverTypeToString} (cpp:422-435). */
    public static String evolverTypeToString(final EvolverType type) {
        switch (type) {
          case Ipc:
            return "iterative predictor corrector";
          case Balland:
            return "Balland predictor corrector";
          case Pc:
            return "predictor corrector";
          case NormalPc:
            return "predictor corrector for normal case";
          case CotSwapPc:
            return "coterminal swap rate predictor corrector";
          default:
            throw new IllegalArgumentException("unknown MarketModelEvolver type");
        }
    }

    /**
     * Port of v1.42.1 {@code makeMarketModelEvolver} (cpp:437-462).
     * Default-forwarding overload with {@code initialStep=0}.
     */
    public static MarketModelEvolver makeMarketModelEvolver(final MarketModel marketModel,
            final int[] numeraires,
            final BrownianGeneratorFactory generatorFactory,
            final EvolverType evolverType) {
        return makeMarketModelEvolver(marketModel, numeraires, generatorFactory, evolverType, 0);
    }

    /**
     * Port of v1.42.1 {@code makeMarketModelEvolver} (cpp:437-462). Dispatches
     * to the four evolver implementations:
     * {@link LogNormalFwdRateIpc}, {@link LogNormalFwdRateBalland},
     * {@link LogNormalFwdRatePc}, {@link NormalFwdRatePc}.
     */
    public static MarketModelEvolver makeMarketModelEvolver(final MarketModel marketModel,
            final int[] numeraires,
            final BrownianGeneratorFactory generatorFactory,
            final EvolverType evolverType,
            final int initialStep) {
        switch (evolverType) {
          case Ipc:
            return new LogNormalFwdRateIpc(marketModel, generatorFactory, numeraires, initialStep);
          case Balland:
            return new LogNormalFwdRateBalland(marketModel, generatorFactory, numeraires, initialStep);
          case Pc:
            return new LogNormalFwdRatePc(marketModel, generatorFactory, numeraires, initialStep);
          case NormalPc:
            return new NormalFwdRatePc(marketModel, generatorFactory, numeraires, initialStep);
          case CotSwapPc:
            return new LogNormalCotSwapRatePc(marketModel, generatorFactory, numeraires, initialStep);
          default:
            throw new IllegalArgumentException("unknown MarketModelEvolver type");
        }
    }

    // ------------------------------------------------------------------
    // simulate() — cpp:270-281
    // ------------------------------------------------------------------

    /**
     * Port of v1.42.1 {@code simulate} (cpp:270-281). Runs an
     * {@link AccountingEngine} multi-path simulation and returns the
     * collected sequence statistics.
     */
    public static SequenceStatistics simulate(final MarketModelEvolver evolver,
            final MarketModelMultiProduct product) {
        final int initialNumeraire = evolver.numeraires()[0];
        final double initialNumeraireValue = todaysDiscounts[initialNumeraire];

        final AccountingEngine engine = new AccountingEngine(evolver, product, initialNumeraireValue);
        final SequenceStatistics stats = new SequenceStatistics(product.numberOfProducts());
        engine.multiplePathValues(stats, paths_);
        return stats;
    }

    // ------------------------------------------------------------------
    // checkForwardsAndOptionlets() — cpp:522-585
    // ------------------------------------------------------------------

    /**
     * Port of v1.42.1 {@code checkForwardsAndOptionlets} (cpp:522-585).
     *
     * <p>Asserts that the MC-computed forwards and optionlet (caplet) values
     * lie within {@code 2.5} standard deviations of the analytic forwards
     * and {@link BlackCalculator} caplet values, with a bias check
     * ({@code minError > 0 || maxError < 0}).
     *
     * <p>Throws {@link AssertionError} on failure so JUnit reports a
     * test failure with the full discrepancy table.
     */
    public static void checkForwardsAndOptionlets(final GenericSequenceStatistics stats,
            final double[] forwardStrikes,
            final List<StrikedTypePayoff> displacedPayoffs,
            final String config) {

        final org.jquantlib.math.matrixutilities.Array resultsArr = stats.mean();
        final org.jquantlib.math.matrixutilities.Array errorsArr = stats.errorEstimate();

        final int N = todaysForwards.length;
        final double[] expectedForwards = new double[N];
        final double[] expectedCaplets = new double[N];
        final double[] forwardStdDevs = new double[N];
        final double[] capletStdDev = new double[N];
        double minError = Double.MAX_VALUE;
        double maxError = -Double.MAX_VALUE;

        // forwards check
        for (int i = 0; i < N; ++i) {
            expectedForwards[i] = (todaysForwards[i] - forwardStrikes[i])
                    * accruals[i] * todaysDiscounts[i + 1];
            forwardStdDevs[i] = (resultsArr.get(i) - expectedForwards[i]) / errorsArr.get(i);
            if (forwardStdDevs[i] > maxError) {
                maxError = forwardStdDevs[i];
            } else if (forwardStdDevs[i] < minError) {
                minError = forwardStdDevs[i];
            }
            final double expiry = rateTimes[i];
            expectedCaplets[i] = new BlackCalculator(displacedPayoffs.get(i),
                    todaysForwards[i] + displacement,
                    volatilities[i] * Math.sqrt(expiry),
                    todaysDiscounts[i + 1] * accruals[i]).value();
            capletStdDev[i] = (resultsArr.get(i + N) - expectedCaplets[i]) / errorsArr.get(i + N);
            if (capletStdDev[i] > maxError) {
                maxError = capletStdDev[i];
            } else if (capletStdDev[i] < minError) {
                minError = capletStdDev[i];
            }
        }

        final double errorThreshold = 2.50;
        if (printReport_ || minError > 0.0 || maxError < 0.0
                || minError < -errorThreshold || maxError > errorThreshold) {
            final StringBuilder sb = new StringBuilder(config).append('\n');
            for (int i = 0; i < N; ++i) {
                sb.append(i + 1).append(" forward: ")
                  .append(resultsArr.get(i)).append("\t").append(expectedForwards[i])
                  .append("\t").append(errorsArr.get(i))
                  .append("; discrepancy = ").append(forwardStdDevs[i]).append('\n');
            }
            for (int i = 0; i < N; ++i) {
                final double e = errorsArr.get(i + N);
                final double disc = (resultsArr.get(i + N) - expectedCaplets[i]) / (e == 0.0 ? 1.0 : e);
                sb.append(i + 1).append("\t").append(resultsArr.get(i + N))
                  .append(" +- ").append(e)
                  .append("\t").append(expectedCaplets[i])
                  .append("\t").append(e)
                  .append("; discrepancy = ").append(disc).append('\n');
            }
            throw new AssertionError("checkForwardsAndOptionlets failed: " + sb);
        }
    }

    // ------------------------------------------------------------------
    // checkNormalForwardsAndOptionlets() — cpp:589-651
    // ------------------------------------------------------------------

    /**
     * Port of v1.42.1 {@code checkNormalForwardsAndOptionlets} (cpp:589-651).
     *
     * <p>Same as {@link #checkForwardsAndOptionlets} except the caplets use
     * the {@link BlackFormula#bachelierBlackFormula bachelierBlackFormula}
     * (normal model) instead of {@link BlackCalculator} (lognormal model).
     */
    public static void checkNormalForwardsAndOptionlets(final GenericSequenceStatistics stats,
            final double[] forwardStrikes,
            final List<PlainVanillaPayoff> displacedPayoffs,
            final String config) {

        final org.jquantlib.math.matrixutilities.Array resultsArr = stats.mean();
        final org.jquantlib.math.matrixutilities.Array errorsArr = stats.errorEstimate();

        final int N = todaysForwards.length;
        final double[] expectedForwards = new double[N];
        final double[] expectedCaplets = new double[N];
        final double[] forwardStdDevs = new double[N];
        final double[] capletStdDev = new double[N];
        double minError = Double.MAX_VALUE;
        double maxError = -Double.MAX_VALUE;

        for (int i = 0; i < N; ++i) {
            expectedForwards[i] = (todaysForwards[i] - forwardStrikes[i])
                    * accruals[i] * todaysDiscounts[i + 1];
            forwardStdDevs[i] = (resultsArr.get(i) - expectedForwards[i]) / errorsArr.get(i);
            if (forwardStdDevs[i] > maxError) {
                maxError = forwardStdDevs[i];
            } else if (forwardStdDevs[i] < minError) {
                minError = forwardStdDevs[i];
            }
            final double expiry = rateTimes[i];
            expectedCaplets[i] = BlackFormula.bachelierBlackFormula(displacedPayoffs.get(i),
                    todaysForwards[i] + displacement,
                    normalVols[i] * Math.sqrt(expiry),
                    todaysDiscounts[i + 1] * accruals[i]);
            capletStdDev[i] = (resultsArr.get(i + N) - expectedCaplets[i]) / errorsArr.get(i + N);
            if (capletStdDev[i] > maxError) {
                maxError = capletStdDev[i];
            } else if (capletStdDev[i] < minError) {
                minError = capletStdDev[i];
            }
        }

        final double errorThreshold = 2.50;
        if (minError > 0.0 || maxError < 0.0
                || minError < -errorThreshold || maxError > errorThreshold) {
            final StringBuilder sb = new StringBuilder(config).append('\n');
            for (int i = 0; i < N; ++i) {
                sb.append(i + 1).append(" forward: ")
                  .append(resultsArr.get(i)).append(" +- ").append(errorsArr.get(i))
                  .append("; expected: ").append(expectedForwards[i])
                  .append("; discrepancy = ").append(forwardStdDevs[i]).append('\n');
            }
            for (int i = 0; i < N; ++i) {
                final double e = errorsArr.get(i + N);
                final double disc = (resultsArr.get(i + N) - expectedCaplets[i]) / (e == 0.0 ? 1.0 : e);
                sb.append(i + 1).append(" caplet: ")
                  .append(resultsArr.get(i + N)).append(" +- ").append(e)
                  .append("; expected: ").append(expectedCaplets[i])
                  .append("; discrepancy = ").append(disc).append('\n');
            }
            throw new AssertionError("checkNormalForwardsAndOptionlets failed: " + sb);
        }
    }

    // ------------------------------------------------------------------
    // Helper utilities
    // ------------------------------------------------------------------

    /** Convenience: {@link MTBrownianGeneratorFactory} using {@link #seed_}. */
    public static BrownianGeneratorFactory mtBrownianGeneratorFactory() {
        return new MTBrownianGeneratorFactory(seed_);
    }

    private static List<Double> doubleArrayToList(final double[] arr) {
        final List<Double> list = new ArrayList<Double>(arr.length);
        for (final double x : arr) {
            list.add(x);
        }
        return list;
    }

    private static String intArrayToString(final int[] arr) {
        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(arr[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    // Suppress unused-import warning for Option (kept for downstream test
    // code that consumes Option.Type via the payoffs list).
    @SuppressWarnings("unused")
    private static final Class<?> OPTION_KEEP_IMPORT = Option.class;
}
