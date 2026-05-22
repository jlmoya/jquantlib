/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.legacy.libormarkets.LfmCovarianceProxy;
import org.jquantlib.legacy.libormarkets.LfmHullWhiteParameterization;
import org.jquantlib.legacy.libormarkets.LfmSwaptionEngine;
import org.jquantlib.legacy.libormarkets.LiborForwardModel;
import org.jquantlib.legacy.libormarkets.LmCorrelationModel;
import org.jquantlib.legacy.libormarkets.LmExponentialCorrelationModel;
import org.jquantlib.legacy.libormarkets.LmExtLinearExponentialVolModel;
import org.jquantlib.legacy.libormarkets.LmFixedVolatilityModel;
import org.jquantlib.legacy.libormarkets.LmLinearExponentialCorrelationModel;
import org.jquantlib.legacy.libormarkets.LmLinearExponentialVolatilityModel;
import org.jquantlib.legacy.libormarkets.LmVolatilityModel;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.math.statistics.GeneralStatistics;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.MultiPathGenerator;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.AffineModel;
import org.jquantlib.model.BlackCalibrationHelper;
import org.jquantlib.model.CalibrationHelper;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.model.shortrate.calibrationhelpers.CapHelper;
import org.jquantlib.model.shortrate.calibrationhelpers.SwaptionHelper;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.capfloor.AnalyticCapFloorEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.processes.LiborForwardModelProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.CapletVarianceCurve;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeGrid;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Java port of {@code test-suite/libormarketmodel.cpp} v1.42.1 (465 LOC,
 * 4 test cases).
 *
 * <p>Status (Phase 5e.5b-CFC-d-138):
 * <ul>
 *   <li>{@code testSimpleCovarianceModels} — <strong>body-filled</strong>
 *       (Phase 5e.5b-CFC-d-132). Exercises the exponential correlation
 *       reconstruction, the linear-exponential vol surface against its
 *       closed-form, and the {@link LfmCovarianceProxy} diffusion ↔
 *       covariance identity.</li>
 *   <li>{@code testCapletPricing} — <strong>body-filled</strong>.
 *       Builds a 10-period {@link LiborForwardModelProcess} on Euribor6M,
 *       bootstraps lambdas via {@link LfmHullWhiteParameterization}, feeds
 *       the resulting diagonal-vol model into {@link LiborForwardModel},
 *       and prices a 4% cap through {@link AnalyticCapFloorEngine}.
 *       Compares against the C++ reference NPV 0.015853935178.</li>
 *   <li>{@code testSwaptionPricing} — <strong>body-filled</strong>.
 *       Verifies (a) {@link LiborForwardModel#S_0} matches the fair-rate of
 *       the par forward swap and (b) the {@link LfmSwaptionEngine} NPV is
 *       within 2.35 × MC-standard-error of a 5000-trial Monte-Carlo path
 *       reference over the {@link LiborForwardModelProcess} via
 *       {@link MultiPathGenerator} + PseudoRandom (Mersenne Twister + inverse
 *       cumulative normal) RSG.</li>
 *   <li>{@code testCalibration} — <strong>body-filled</strong>
 *       (Phase 5e.5b-CFC-d-229). Builds 12 {@link CapHelper}s priced via
 *       {@link AnalyticCapFloorEngine} and 49 {@link SwaptionHelper}s priced
 *       via {@link LfmSwaptionEngine} against a fixed market vol surface,
 *       then runs {@link LiborForwardModel#calibrate} with
 *       {@link LevenbergMarquardt} (1e-6 tol) + {@link EndCriteria}
 *       (2000 iter, 100 stationary, 1e-6 epsilons). Asserts the calibration
 *       residual sqrt(sum diff^2) < 8e-3, matching C++ verbatim.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/libormarketmodel.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class LiborMarketModelTest {

    /** Mirror of C++ free function {@code makeIndex(dates, rates)}
     *  (test-suite/libormarketmodel.cpp:56-74). */
    private static IborIndex makeIndex(final Date[] datesIn, final double[] rates) {
        final DayCounter dayCounter = new Actual360();
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<YieldTermStructure>();

        final IborIndex index = new Euribor6M(termStructure);

        final Date todaysDate = index.fixingCalendar().adjust(
                new Date(4, Month.September, 2005));
        new Settings().setEvaluationDate(todaysDate);

        // Mutate dates[0] to settlement date — mirrors C++ in-place edit.
        final Date[] dates = datesIn.clone();
        dates[0] = index.fixingCalendar().advance(todaysDate,
                index.fixingDays(), TimeUnit.Days);

        termStructure.linkTo(new InterpolatedZeroCurve<Linear>(Linear.class,
                dates, rates, dayCounter));
        return index;
    }

    /** Convenience overload — single-curve default index (4-Sep-2005 to
     *  4-Sep-2018, rates {0.039, 0.041}). */
    private static IborIndex makeIndex() {
        return makeIndex(new Date[] {
                new Date(4, Month.September, 2005),
                new Date(4, Month.September, 2018) },
                new double[] { 0.039, 0.041 });
    }

    /** Mirror of C++ {@code makeCapVolCurve(todaysDate)}
     *  (test-suite/libormarketmodel.cpp:85-103). The 9-element strip is the
     *  Hull-White lambda-source for testCapletPricing. */
    private static OptionletVolatilityStructure makeCapVolCurve(final Date todaysDate) {
        final double[] vols = { 14.40, 17.15, 16.81, 16.64, 16.17,
                                15.78, 15.40, 15.21, 14.86 };

        final LiborForwardModelProcess process =
                new LiborForwardModelProcess(10, makeIndex());

        final Date[] dates = new Date[9];
        final double[] capletVols = new double[9];
        for (int i = 0; i < 9; ++i) {
            capletVols[i] = vols[i] / 100.0;
            dates[i] = process.fixingDates().get(i + 1);
        }
        return new CapletVarianceCurve(todaysDate, dates, capletVols, new Actual360());
    }

    /** Mirror of C++ {@code PseudoRandom::make_sequence_generator(dimension,
     *  seed)} composing the canonical MT + InverseCumulativeNormal stack.
     *  The Java {@code PseudoRandom.makeSequenceGenerator} factory is broken
     *  (see {@link
     *  org.jquantlib.testsuite.methods.montecarlo.PathGeneratorTest} for the
     *  same workaround). */
    private static InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                        InverseCumulativeNormal>
            makePseudoRandomRsg(final int dimension, final long seed) {
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(seed);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> rsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, dimension, rng);
        return new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                        InverseCumulativeNormal>(rsg, new InverseCumulativeNormal());
    }

    @Test
    public void testSimpleCovarianceModels() {
        final int size = 10;
        final double tolerance = 1e-14;

        // ----- (i) Exponential correlation reconstructs from its pseudo-sqrt
        final LmCorrelationModel corrModel = new LmExponentialCorrelationModel(size, 0.1);

        final Matrix corr = corrModel.correlation(0.0);
        final Matrix ps = corrModel.pseudoSqrt(0.0);
        final Matrix recon = corr.sub(ps.mul(ps.transpose()));

        for (int i = 0; i < size; ++i) {
            for (int j = 0; j < size; ++j) {
                if (Math.abs(recon.get(i, j)) > tolerance) {
                    fail("Failed to reproduce correlation matrix"
                            + "\n    calculated: " + recon.get(i, j)
                            + "\n    expected:   0");
                }
            }
        }

        // ----- (ii) volatility surface against closed-form Brigo-Mercurio-Morini
        final List<Double> fixingTimes = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            fixingTimes.add(0.5 * i);
        }

        final double a = 0.2;
        final double b = 0.1;
        final double c = 2.1;
        final double d = 0.3;

        final LmVolatilityModel volaModel =
                new LmLinearExponentialVolatilityModel(fixingTimes, a, b, c, d);

        final LfmCovarianceProxy covarProxy = new LfmCovarianceProxy(volaModel, corrModel);

        for (double t = 0; t < 4.6; t += 0.31) {
            final Matrix diff = covarProxy.diffusion(t);
            final Matrix reconCov = covarProxy.covariance(t).sub(diff.mul(diff.transpose()));

            for (int i = 0; i < size; ++i) {
                for (int j = 0; j < size; ++j) {
                    if (Math.abs(reconCov.get(i, j)) > tolerance) {
                        fail("Failed to reproduce covariance/diffusion identity"
                                + "\n    t: " + t
                                + "\n    (i,j): (" + i + "," + j + ")"
                                + "\n    calculated: " + reconCov.get(i, j)
                                + "\n    expected:   0");
                    }
                }
            }

            final Array volatility = volaModel.volatility(t);
            for (int k = 0; k < size; ++k) {
                double expected = 0.0;
                if ((double) k > 2.0 * t) {
                    final double T = fixingTimes.get(k);
                    expected = (a * (T - t) + d) * Math.exp(-b * (T - t)) + c;
                }
                if (Math.abs(expected - volatility.get(k)) > tolerance) {
                    fail("Failed to reproduce volatilities"
                            + "\n    t: " + t
                            + "\n    k: " + k
                            + "\n    calculated: " + volatility.get(k)
                            + "\n    expected:   " + expected);
                }
            }
        }
        assertTrue(true);
    }

    @Test
    public void testCapletPricing() {
        // Mirror of C++ BOOST_AUTO_TEST_CASE(testCapletPricing)
        // (test-suite/libormarketmodel.cpp:180-226).
        final boolean usingAtParCoupons = IborCoupon.Settings.getInstance().usingAtParCoupons();
        final int size = 10;
        final double tolerance = usingAtParCoupons ? 1e-12 : 1e-5;

        final IborIndex index = makeIndex();
        final LiborForwardModelProcess process =
                new LiborForwardModelProcess(size, index);

        final OptionletVolatilityStructure capVolCurve =
                makeCapVolCurve(new Settings().evaluationDate());

        // Bootstrap Hull-White lambdas, then take sqrt(diag(covariance(0))).
        // Mirror C++:
        //   Array variances = LfmHullWhiteParameterization(process, capVolCurve)
        //                       .covariance(0.0).diagonal();
        final LfmHullWhiteParameterization hullWhite =
                new LfmHullWhiteParameterization(process, capVolCurve, null, 1);
        final Array variances = hullWhite.covariance(0.0).diagonal();
        final Array sqrtVariances = variances.sqrt();

        // Hand the lambdas to LmFixedVolatilityModel keyed on fixing-times.
        final List<Double> fixings = process.fixingTimes();
        final Array fixingTimesArr = new Array(fixings.size());
        for (int i = 0; i < fixings.size(); ++i) {
            fixingTimesArr.set(i, fixings.get(i));
        }
        final LmVolatilityModel volaModel = new LmFixedVolatilityModel(
                sqrtVariances, fixingTimesArr);

        final LmCorrelationModel corrModel =
                new LmExponentialCorrelationModel(size, 0.3);

        final AffineModel model = new LiborForwardModel(process, volaModel, corrModel);

        final Handle<YieldTermStructure> termStructure = index.termStructure();

        final PricingEngine engine = new AnalyticCapFloorEngine(model, termStructure);

        // Build a Cap on process.cashFlows() with strikes all 0.04.
        // C++ uses Cap(leg, strikes) — Java goes via the 5-arg CapFloor ctor.
        final List<Double> strikes = new ArrayList<>(Collections.nCopies(size, Double.valueOf(0.04)));
        final CapFloor cap1 = new CapFloor(CapFloor.Type.Cap, process.cashFlows(),
                strikes, /* termStructure */ null, engine);

        final double expected = 0.015853935178;
        final double calculated = cap1.NPV();

        if (Math.abs(expected - calculated) > tolerance) {
            fail("Failed to reproduce npv"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    diff:       " + Math.abs(expected - calculated)
                    + "\n    tolerance:  " + tolerance);
        }
    }

    @Test
    public void testCalibration() {
        // Mirror of C++ BOOST_AUTO_TEST_CASE(testCalibration)
        // (test-suite/libormarketmodel.cpp:228-328).
        final int size = 14;
        // C++ uses tolerance 8e-3 against sqrt(sum_i diff_i^2). Java keeps
        // this tier (LOOSE per CLAUDE.md: do not loosen below 1e-2).
        final double tolerance = 8e-3;

        final double[] capVols = {
                0.145708, 0.158465, 0.166248, 0.168672,
                0.169007, 0.167956, 0.166261, 0.164239,
                0.162082, 0.159923, 0.157781, 0.155745,
                0.153776, 0.151950, 0.150189, 0.148582,
                0.147034, 0.145598, 0.144248
        };

        final double[] swaptionVols = {
                0.170595, 0.166844, 0.158306, 0.147444,
                0.136930, 0.126833, 0.118135, 0.175963,
                0.166359, 0.155203, 0.143712, 0.132769,
                0.122947, 0.114310, 0.174455, 0.162265,
                0.150539, 0.138734, 0.128215, 0.118470,
                0.110540, 0.169780, 0.156860, 0.144821,
                0.133537, 0.123167, 0.114363, 0.106500,
                0.164521, 0.151223, 0.139670, 0.128632,
                0.119123, 0.110330, 0.103114, 0.158956,
                0.146036, 0.134555, 0.124393, 0.115038,
                0.106996, 0.100064
        };

        final IborIndex index = makeIndex();
        final LiborForwardModelProcess process =
                new LiborForwardModelProcess(size, index);
        final Handle<YieldTermStructure> termStructure = index.termStructure();

        // Model parameterisation: extended linear-exponential vol + linear-
        // exponential correlation, mirroring C++ exactly.
        final LmVolatilityModel volaModel =
                new LmExtLinearExponentialVolModel(process.fixingTimes(),
                        0.5, 0.6, 0.1, 0.1);
        final LmCorrelationModel corrModel =
                new LmLinearExponentialCorrelationModel(size, 0.5, 0.8);

        final LiborForwardModel model =
                new LiborForwardModel(process, volaModel, corrModel);

        final DayCounter dayCounter =
                index.termStructure().currentLink().dayCounter();

        // Build the CapHelper + SwaptionHelper list. C++ uses
        // ImpliedVolError for both; mirror the loop structure verbatim.
        final List<CalibrationHelper> calibrationHelpers =
                new ArrayList<>();
        int swapVolIndex = 0;
        for (int i = 2; i < size; ++i) {
            final Period maturity = new Period(i * index.tenor().length(),
                    index.tenor().units());
            final Handle<Quote> capVol = new Handle<Quote>(
                    new SimpleQuote(capVols[i - 2]));

            final CapHelper capHelper = new CapHelper(maturity, capVol, index,
                    Frequency.Annual, index.dayCounter(),
                    /* includeFirstSwaplet */ true, termStructure,
                    BlackCalibrationHelper.CalibrationErrorType.ImpliedVolError,
                    VolatilityType.ShiftedLognormal, 0.0);
            capHelper.setPricingEngine(
                    new AnalyticCapFloorEngine(model, termStructure));
            calibrationHelpers.add(capHelper);

            if (i <= size / 2) {
                for (int j = 1; j <= size / 2; ++j) {
                    final Period len = new Period(j * index.tenor().length(),
                            index.tenor().units());
                    final Handle<Quote> swaptionVol = new Handle<Quote>(
                            new SimpleQuote(swaptionVols[swapVolIndex++]));

                    final SwaptionHelper swaptionHelper = new SwaptionHelper(
                            maturity, len, swaptionVol, index,
                            index.tenor(), dayCounter, index.dayCounter(),
                            termStructure,
                            BlackCalibrationHelper.CalibrationErrorType.ImpliedVolError,
                            /* strike */ Constants.NULL_REAL,
                            /* nominal */ 1.0,
                            VolatilityType.ShiftedLognormal, 0.0);
                    swaptionHelper.setPricingEngine(
                            new LfmSwaptionEngine(model, termStructure));
                    calibrationHelpers.add(swaptionHelper);
                }
            }
        }

        // C++:
        //   LevenbergMarquardt om(1e-6, 1e-6, 1e-6);
        //   model->calibrate(helpers, om, EndCriteria(2000, 100, 1e-6, 1e-6, 1e-6));
        // The C++ 3-arg overload defaults additionalConstraint = NoConstraint()
        // and weights = empty. Java's only public calibrate is the 5-arg
        // overload, so mirror those defaults explicitly.
        final LevenbergMarquardt om = new LevenbergMarquardt(1e-6, 1e-6, 1e-6);
        final EndCriteria endCriteria = new EndCriteria(2000, 100,
                1e-6, 1e-6, 1e-6);
        model.calibrate(calibrationHelpers, om, endCriteria,
                new NoConstraint(), /* weights */ null);

        // Measure the calibration error: sum of squared per-helper errors,
        // then sqrt — C++ test-suite/libormarketmodel.cpp:317-327.
        double sumSq = 0.0;
        for (int i = 0; i < calibrationHelpers.size(); ++i) {
            final double diff = calibrationHelpers.get(i).calibrationError();
            sumSq += diff * diff;
        }
        final double calculated = Math.sqrt(sumSq);

        if (calculated > tolerance) {
            fail("Failed to calibrate libor forward model"
                    + "\n    calculated diff: " + calculated
                    + "\n    expected: smaller than " + tolerance);
        }
    }

    @Test
    public void testSwaptionPricing() {
        // Mirror of C++ BOOST_AUTO_TEST_CASE(testSwaptionPricing)
        // (test-suite/libormarketmodel.cpp:330-461).
        final boolean usingAtParCoupons = IborCoupon.Settings.getInstance().usingAtParCoupons();

        final int size = 10;
        final int steps = 8 * size;

        // C++: tolerance = usingAtParCoupons ? 1e-12 : 1e-6 — used only for
        // the deterministic S_0 vs swap-fair-rate comparison below. The MC
        // path uses the per-statistic error-estimate × 2.35 bound.
        final double s0Tolerance = usingAtParCoupons ? 1e-12 : 1e-6;

        final Date[] dates = {
                new Date(4, Month.September, 2005),
                new Date(4, Month.September, 2011)
        };
        final double[] rates = { 0.04, 0.08 };

        final IborIndex index = makeIndex(dates, rates);

        final LiborForwardModelProcess process =
                new LiborForwardModelProcess(size, index);

        final LmCorrelationModel corrModel =
                new LmExponentialCorrelationModel(size, 0.5);

        final LmVolatilityModel volaModel =
                new LmLinearExponentialVolatilityModel(process.fixingTimes(),
                        0.291, 1.483, 0.116, 0.00001);

        // set-up pricing-engine covariance proxy on the process
        process.setCovarParam(new LfmCovarianceProxy(volaModel, corrModel));

        // build the MC pipeline: PseudoRandom RSG -> MultiPathGenerator
        final List<Double> tmp = process.fixingTimes();
        final TimeGrid grid = new TimeGrid(tmp, steps);

        final int[] location = new int[tmp.size()];
        for (int i = 0; i < tmp.size(); ++i) {
            final double target = tmp.get(i);
            int found = -1;
            for (int gi = 0; gi < grid.size(); ++gi) {
                if (grid.get(gi) == target) {
                    found = gi;
                    break;
                }
            }
            // Fall back to nearest if no exact match (defensive — C++ uses
            // FP-equality, which works there because TimeGrid mandatoryPoints
            // ctor preserves the input anchors verbatim).
            if (found < 0) {
                double bestDelta = Double.POSITIVE_INFINITY;
                for (int gi = 0; gi < grid.size(); ++gi) {
                    final double delta = Math.abs(grid.get(gi) - target);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        found = gi;
                    }
                }
            }
            location[i] = found;
        }

        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                   InverseCumulativeNormal> rsg =
                makePseudoRandomRsg(process.factors() * (grid.size() - 1), 42L);

        final int nrTrails = 5000;
        final MultiPathGenerator<InverseCumulativeRsg<
                RandomSequenceGenerator<MersenneTwisterUniformRng>, InverseCumulativeNormal>>
                generator = new MultiPathGenerator<>(process, grid, rsg, false);

        final LiborForwardModel liborModel =
                new LiborForwardModel(process, volaModel, corrModel);

        final Calendar calendar = index.fixingCalendar();
        final DayCounter dayCounter = index.termStructure().currentLink().dayCounter();
        final BusinessDayConvention convention = index.businessDayConvention();
        final Date settlement = index.termStructure().currentLink().referenceDate();

        for (int i = 1; i < size; ++i) {
            for (int j = 1; j <= size - i; ++j) {
                final Date fwdStart = settlement.add(new Period(6 * i, TimeUnit.Months));
                final Date fwdMaturity = fwdStart.add(new Period(6 * j, TimeUnit.Months));

                final Schedule schedule = new Schedule(
                        fwdStart, fwdMaturity, index.tenor(), calendar,
                        convention, convention, DateGeneration.Rule.Forward, false);

                double swapRate = 0.0404;
                VanillaSwap forwardSwap = new VanillaSwap(
                        VanillaSwap.Type.Receiver, 1.0,
                        schedule, swapRate, dayCounter,
                        schedule, index, 0.0, index.dayCounter());
                forwardSwap.setPricingEngine(
                        new DiscountingSwapEngine(index.termStructure()));

                // check forward pricing first — S_0 must match fair-rate of
                // the par forward swap up to s0Tolerance.
                final double expectedRate = forwardSwap.fairRate();
                final double calculatedRate = liborModel.S_0(i - 1, i + j - 1);

                if (Math.abs(expectedRate - calculatedRate) > s0Tolerance) {
                    fail("Failed to reproduce fair forward swap rate"
                            + "\n    i,j:        " + i + "," + j
                            + "\n    calculated: " + calculatedRate
                            + "\n    expected:   " + expectedRate);
                }

                // Re-build the swap at the fair rate (so NPV ~= 0).
                swapRate = forwardSwap.fairRate();
                forwardSwap = new VanillaSwap(
                        VanillaSwap.Type.Receiver, 1.0,
                        schedule, swapRate, dayCounter,
                        schedule, index, 0.0, index.dayCounter());
                forwardSwap.setPricingEngine(
                        new DiscountingSwapEngine(index.termStructure()));

                // For i == j with i <= size/2, also exercise the LFM swaption
                // engine against an MC reference price.
                if (i == j && i <= size / 2) {
                    final PricingEngine swaptionEngine =
                            new LfmSwaptionEngine(liborModel, index.termStructure());
                    final EuropeanExercise exercise =
                            new EuropeanExercise(process.fixingDates().get(i));

                    final Swaption swaption = new Swaption(forwardSwap, exercise);
                    swaption.setPricingEngine(swaptionEngine);

                    final GeneralStatistics stat = new GeneralStatistics();
                    for (int n = 0; n < nrTrails; ++n) {
                        final Sample<MultiPath> path =
                                (n % 2) != 0 ? generator.antithetic() : generator.next();
                        final MultiPath mp = path.value();

                        final double[] mcRates = new double[size];
                        for (int k = 0; k < process.size(); ++k) {
                            mcRates[k] = mp.get(k).get(location[i]);
                        }
                        final double[] dis = process.discountBond(mcRates);

                        double npv = 0.0;
                        for (int m = i; m < i + j; ++m) {
                            npv += (swapRate - mcRates[m])
                                    * (process.accrualEndTimes().get(m)
                                            - process.accrualStartTimes().get(m))
                                    * dis[m];
                        }
                        stat.add(Math.max(npv, 0.0));
                    }

                    // C++ compares to within 2.35 * error_estimate; mirror.
                    final double diff = Math.abs(swaption.NPV() - stat.mean());
                    final double bound = stat.errorEstimate() * 2.35;
                    if (diff > bound) {
                        fail("Failed to reproduce swaption npv"
                                + "\n    i,j:           " + i + "," + j
                                + "\n    MC mean:       " + stat.mean()
                                + "\n    swaption NPV:  " + swaption.NPV()
                                + "\n    diff:          " + diff
                                + "\n    2.35 * stderr: " + bound);
                    }
                }
            }
        }
    }
}
