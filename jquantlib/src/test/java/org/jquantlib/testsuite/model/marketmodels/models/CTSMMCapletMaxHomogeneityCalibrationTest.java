/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 1.1 Round 1
 — D5-E worktree, SMM calibration closure.

 This source code is release under the BSD License.

 This file is a faithful Java port of v1.42.1
 test-suite/marketmodel_smmcaplethomocalibration.cpp::testFunction
 and ::testPeriodFunction @ 099987f0ca2c11c505dc4348cdb9ce01a598e1e5.
 {@code testSphereCylinder} is already covered by
 {@link org.jquantlib.testsuite.math.optimization.SphereCylinderOptimizerTest}.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */
package org.jquantlib.testsuite.model.marketmodels.models;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.SimpleDayCounter;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.correlations.CotSwapFromFwdCorrelation;
import org.jquantlib.model.marketmodels.correlations.ExponentialForwardCorrelation;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.models.CTSMMCapletMaxHomogeneityCalibration;
import org.jquantlib.model.marketmodels.models.CotSwapToFwdAdapter;
import org.jquantlib.model.marketmodels.models.FwdPeriodAdapter;
import org.jquantlib.model.marketmodels.models.FwdToCotSwapAdapter;
import org.jquantlib.model.marketmodels.models.PiecewiseConstantAbcdVariance;
import org.jquantlib.model.marketmodels.models.PiecewiseConstantVariance;
import org.jquantlib.model.marketmodels.models.PseudoRootFacade;
import org.jquantlib.model.marketmodels.models.VolatilityInterpolationSpecifierAbcd;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Before;
import org.junit.Test;

/**
 * Faithful Java port of v1.42.1
 * {@code test-suite/marketmodel_smmcaplethomocalibration.cpp::testFunction}
 * @ 099987f0ca2c11c505dc4348cdb9ce01a598e1e5.
 */
public class CTSMMCapletMaxHomogeneityCalibrationTest {

    public CTSMMCapletMaxHomogeneityCalibrationTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private double[] rateTimes_;
    private double[] todaysForwards_;
    private double[] todaysSwaps_;
    private int numberOfFactors_;
    private double displacement_;
    private double[] capletVols_;
    private double a_, b_, c_, d_;
    private double longTermCorrelation_;
    private double beta_;

    @Before
    public void setup() {
        // Times (cpp:99-114)
        final Calendar calendar = new NullCalendar();
        final Date todaysDate = new Settings().evaluationDate();
        final Date endDate = todaysDate.add(new Period(66, TimeUnit.Months));
        final Schedule dates = new Schedule(todaysDate, endDate,
                new Period(Frequency.Semiannual),
                calendar, BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward, false);
        final SimpleDayCounter dayCounter = new SimpleDayCounter();
        rateTimes_ = new double[dates.size() - 1];
        for (int i = 1; i < dates.size(); ++i) {
            rateTimes_[i - 1] = dayCounter.yearFraction(todaysDate, dates.dates().get(i));
        }
        final double[] accruals = new double[rateTimes_.length - 1];
        for (int i = 1; i < rateTimes_.length; ++i) {
            accruals[i - 1] = rateTimes_[i] - rateTimes_[i - 1];
        }

        // Rates & displacement (cpp:117-129)
        todaysForwards_ = new double[accruals.length];
        numberOfFactors_ = 3;
        displacement_ = 0.0;
        for (int i = 0; i < todaysForwards_.length; ++i) {
            todaysForwards_[i] = 0.03 + 0.0025 * i;
        }
        final LMMCurveState cs = new LMMCurveState(rateTimes_);
        cs.setOnForwardRates(todaysForwards_);
        todaysSwaps_ = cs.coterminalSwapRates();

        // Abcd & vols (cpp:166-195)
        a_ = 0.0;
        b_ = 0.17;
        c_ = 1.0;
        d_ = 0.10;
        final double[] mktCapletVols = {
                0.1640, 0.1740, 0.1840, 0.1940, 0.1840,
                0.1740, 0.1640, 0.1540, 0.1440, 0.1340376439125532
        };
        capletVols_ = new double[todaysSwaps_.length];
        for (int i = 0; i < todaysSwaps_.length; ++i) {
            capletVols_[i] = mktCapletVols[i];
        }

        longTermCorrelation_ = 0.5;
        beta_ = 0.2;
    }

    private static List<Double> doubleArrayToList(final double[] in) {
        final List<Double> out = new ArrayList<>(in.length);
        for (final double v : in) {
            out.add(v);
        }
        return out;
    }

    /** Faithful port of v1.42.1 {@code testFunction} (cpp:225-350). */
    @Test
    public void testFunction() {
        final int numberOfRates = todaysForwards_.length;
        final EvolutionDescription evolution = new EvolutionDescription(rateTimes_);

        final PiecewiseConstantCorrelation fwdCorr = new ExponentialForwardCorrelation(
                doubleArrayToList(rateTimes_), longTermCorrelation_, beta_);

        final LMMCurveState cs = new LMMCurveState(rateTimes_);
        cs.setOnForwardRates(todaysForwards_);

        final PiecewiseConstantCorrelation corr = new CotSwapFromFwdCorrelation(
                fwdCorr, cs, displacement_);

        final List<PiecewiseConstantVariance> swapVariances = new ArrayList<>(numberOfRates);
        for (int i = 0; i < numberOfRates; ++i) {
            swapVariances.add(new PiecewiseConstantAbcdVariance(a_, b_, c_, d_, i, rateTimes_));
        }

        final double caplet0Swaption1Priority = 1.0;

        final CTSMMCapletMaxHomogeneityCalibration calibrator =
                new CTSMMCapletMaxHomogeneityCalibration(evolution, corr, swapVariances,
                        capletVols_, cs, displacement_, caplet0Swaption1Priority);

        final int maxIterations = 10;
        final double capletTolerance = 1e-4;
        final int innerMaxIterations = 100;
        final double innerTolerance = 1e-8;

        final boolean result = calibrator.calibrate(numberOfFactors_, maxIterations,
                capletTolerance, innerMaxIterations, innerTolerance);
        assertTrue("calibration failed", result);

        final List<Matrix> swapPseudoRoots = calibrator.swapPseudoRoots();
        final double[] displacementsArr = new double[numberOfRates];
        Arrays.fill(displacementsArr, displacement_);
        final MarketModel smm = new PseudoRootFacade(swapPseudoRoots, rateTimes_,
                cs.coterminalSwapRates(), displacementsArr);
        final MarketModel flmm = new CotSwapToFwdAdapter(smm);
        final Matrix capletTotCovariance = flmm.totalCovariance(numberOfRates - 1);

        final double[] capletVols = new double[numberOfRates];
        for (int i = 0; i < numberOfRates; ++i) {
            capletVols[i] = Math.sqrt(capletTotCovariance.get(i, i) / rateTimes_[i]);
        }

        // Check perfect swaption fit (cpp:315-328)
        final double swapTolerance = 1e-14;
        Matrix swapTerminalCovariance = new Matrix(numberOfRates, numberOfRates);
        for (int i = 0; i < numberOfRates; ++i) {
            final double expSwaptionVol = swapVariances.get(i).totalVolatility(i);
            swapTerminalCovariance = swapTerminalCovariance.add(
                    swapPseudoRoots.get(i).mul(swapPseudoRoots.get(i).transpose()));
            final double swaptionVol = Math.sqrt(swapTerminalCovariance.get(i, i) / rateTimes_[i]);
            final double error = Math.abs(swaptionVol - expSwaptionVol);
            if (error > swapTolerance) {
                fail(String.format(
                        "failed to reproduce swaption %d vol: expected=%.16f realized=%.16f error=%.3e tol=%.3e",
                        i + 1, expSwaptionVol, swaptionVol, error, swapTolerance));
            }
        }

        // Check caplet fit (cpp:331-341)
        for (int i = 0; i < numberOfRates; ++i) {
            final double error = Math.abs(capletVols[i] - capletVols_[i]);
            if (error > capletTolerance) {
                fail(String.format(
                        "failed to reproduce caplet %d vol: expected=%.6f realized=%.6f pcterr=%.3e error=%.3e tol=%.3e",
                        i + 1, capletVols_[i], capletVols[i],
                        error / capletVols_[i], error, capletTolerance));
            }
        }
    }

    /**
     * Faithful Java port of v1.42.1
     * {@code test-suite/marketmodel_smmcaplethomocalibration.cpp::testPeriodFunction}
     * @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
     *
     * <p>Drives the {@code capletSwaptionPeriodicCalibration} free function via
     * {@link VolatilityInterpolationSpecifierAbcd}, then verifies caplet fit
     * (tolerance {@code 1e-4}) and perfect swaption fit (tolerance {@code 2e-5}).
     */
    @Test
    public void testPeriodFunction() {
        final int numberOfRates = todaysForwards_.length;
        final int period = 2;
        final int offset = numberOfRates % period;
        final int numberBigRates = numberOfRates / period;

        final EvolutionDescription evolution = new EvolutionDescription(rateTimes_);

        final double[] bigRateTimes = new double[numberBigRates + 1];
        for (int i = 0; i <= numberBigRates; ++i) {
            bigRateTimes[i] = rateTimes_[i * period + offset];
        }

        final PiecewiseConstantCorrelation fwdCorr = new ExponentialForwardCorrelation(
                doubleArrayToList(rateTimes_), longTermCorrelation_, beta_);

        final LMMCurveState cs = new LMMCurveState(rateTimes_);
        cs.setOnForwardRates(todaysForwards_);

        final PiecewiseConstantCorrelation corr = new CotSwapFromFwdCorrelation(
                fwdCorr, cs, displacement_);

        final List<PiecewiseConstantAbcdVariance> swapVariances = new ArrayList<>(numberBigRates);
        for (int i = 0; i < numberBigRates; ++i) {
            swapVariances.add(new PiecewiseConstantAbcdVariance(a_, b_, c_, d_, i, bigRateTimes));
        }

        // varianceInterpolator: long-rate variances + small-rate time grid
        final VolatilityInterpolationSpecifierAbcd varianceInterpolator =
                new VolatilityInterpolationSpecifierAbcd(period, offset, swapVariances, rateTimes_);

        // create calibrator (cpp:394-407)
        final double caplet0Swaption1Priority = 1.0;

        // calibration controls (cpp:402-408)
        final int maxUnperiodicIterations = 10;
        final double toleranceUnperiodic = 1e-5; // i.e. 1 bp
        final int max1dIterations = 100;
        final double tolerance1d = 1e-8;
        final int maxPeriodIterations = 30;
        final double periodTolerance = 1e-5;

        // out-params
        final List<Matrix> swapPseudoRoots = new ArrayList<>();
        final double[] deformationSize = new double[1];
        final double[] totalSwaptionError = new double[1];
        final double[] finalScales = new double[numberBigRates];
        final int[] iterationsDone = new int[1];
        final double[] errorImprovement = new double[1];
        final Matrix[] modelSwaptionVolsMatrix = new Matrix[1];

        // call free function (cpp:427-450)
        CTSMMCapletMaxHomogeneityCalibration.capletSwaptionPeriodicCalibration(
                evolution,
                corr,
                varianceInterpolator,
                capletVols_,
                cs,
                displacement_,
                caplet0Swaption1Priority,
                numberOfFactors_,
                period,
                max1dIterations,
                tolerance1d,
                maxUnperiodicIterations,
                toleranceUnperiodic,
                maxPeriodIterations,
                periodTolerance,
                deformationSize,
                totalSwaptionError,
                swapPseudoRoots,
                finalScales,
                iterationsDone,
                errorImprovement,
                modelSwaptionVolsMatrix);

        // Build smm + flmm (cpp:453-459)
        final double[] displacementsAll = new double[numberOfRates];
        Arrays.fill(displacementsAll, displacement_);
        final org.jquantlib.model.marketmodels.MarketModel smm =
                new PseudoRootFacade(swapPseudoRoots, rateTimes_,
                        cs.coterminalSwapRates(), displacementsAll);
        final org.jquantlib.model.marketmodels.MarketModel flmm = new CotSwapToFwdAdapter(smm);
        final Matrix capletTotCovariance = flmm.totalCovariance(numberOfRates - 1);

        // Check caplet fit (cpp:464-482)
        final double[] capletVols = new double[numberOfRates];
        for (int i = 0; i < numberOfRates; ++i) {
            capletVols[i] = Math.sqrt(capletTotCovariance.get(i, i) / rateTimes_[i]);
        }
        final double capletTolerance = 1e-4; // i.e. 1 bp
        for (int i = 0; i < numberOfRates; ++i) {
            final double error = Math.abs(capletVols[i] - capletVols_[i]);
            if (error > capletTolerance) {
                fail(String.format(
                        "failed to reproduce caplet %d vol: expected=%.6f realized=%.6f pcterr=%.3e error=%.3e tol=%.3e",
                        i + 1, capletVols_[i], capletVols[i],
                        error / capletVols_[i], error, capletTolerance));
            }
        }

        // Check perfect swaption fit (cpp:486-509)
        final double[] adaptedDisplacements = new double[numberBigRates];
        Arrays.fill(adaptedDisplacements, displacement_);
        final org.jquantlib.model.marketmodels.MarketModel adaptedFlmm =
                new FwdPeriodAdapter(flmm, period, offset, adaptedDisplacements);
        final org.jquantlib.model.marketmodels.MarketModel adaptedSmm = new FwdToCotSwapAdapter(adaptedFlmm);

        final double swapTolerance = 2e-5;
        final Matrix swapTerminalCovariance =
                adaptedSmm.totalCovariance(adaptedSmm.numberOfSteps() - 1);

        for (int i = 0; i < numberBigRates; ++i) {
            final double expSwaptionVol = swapVariances.get(i).totalVolatility(i);
            final double time = adaptedSmm.evolution().rateTimes()[i];
            final double swaptionVol = Math.sqrt(swapTerminalCovariance.get(i, i) / time);
            final double error = Math.abs(swaptionVol - expSwaptionVol);
            if (error > swapTolerance) {
                fail(String.format(
                        "failed to reproduce swaption %d vol: expected=%.6f realized=%.6f error=%.3e tol=%.3e",
                        i, expSwaptionVol, swaptionVol, error, swapTolerance));
            }
        }
    }
}
