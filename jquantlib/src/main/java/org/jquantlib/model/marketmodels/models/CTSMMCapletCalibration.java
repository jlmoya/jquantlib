/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

/*
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.models;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Coterminal swap-rate market-model (CTSMM) caplet calibration abstract base.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/ctsmmcapletcalibration.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Subclasses implement {@link #calibrationImpl(int, int, double)} which
 * performs the per-iteration calibration step and populates {@link #swapCovariancePseudoRoots_}.
 *
 * <p>The {@link #calibrate(int, int, double, int, double)} method runs a fixed
 * iterative loop: each iteration calls {@code calibrationImpl}, computes model swaption / caplet vols by passing the
 * calibrated pseudo-roots through a {@link PseudoRootFacade} and {@link CotSwapToFwdAdapter}, then rescales
 * {@code usedCapletVols_} by the ratio of market-to-model caplet vol.
 *
 * <p>Phase 3j B.3 (Track B). Uses {@link PseudoRootFacade} (Phase 3j Track A)
 * and {@link CotSwapToFwdAdapter} (Phase 3j Track A).
 */
public abstract class CTSMMCapletCalibration {

    // input
    protected final EvolutionDescription evolution_;
    protected final PiecewiseConstantCorrelation corr_;
    protected final List< PiecewiseConstantVariance > displacedSwapVariances_;
    protected final CurveState cs_;
    protected final double displacement_;
    protected final int numberOfRates_;
    protected double[] mktCapletVols_;
    protected double[] mdlCapletVols_;
    protected double[] mktSwaptionVols_;
    protected double[] mdlSwaptionVols_;
    protected List< double[] > timeDependentCalibratedSwaptionVols_;
    // working
    protected double[] usedCapletVols_;
    // results
    protected boolean calibrated_;
    protected int failures_;
    protected double deformationSize_;
    protected double capletRmsError_, capletMaxError_;
    protected double swaptionRmsError_, swaptionMaxError_;
    protected List< Matrix > swapCovariancePseudoRoots_;

    public CTSMMCapletCalibration(final EvolutionDescription evolution, final PiecewiseConstantCorrelation corr,
            final List< PiecewiseConstantVariance > displacedSwapVariances, final double[] mktCapletVols,
            final CurveState cs, final double displacement) {
        this.evolution_ = evolution;
        this.corr_ = corr;
        this.displacedSwapVariances_ = new ArrayList<>(displacedSwapVariances);
        this.mktCapletVols_ = mktCapletVols.clone();
        this.numberOfRates_ = evolution.numberOfRates();
        this.mdlCapletVols_ = new double[numberOfRates_];
        this.mktSwaptionVols_ = new double[numberOfRates_];
        this.mdlSwaptionVols_ = new double[numberOfRates_];
        this.cs_ = cs;
        this.displacement_ = displacement;
        this.calibrated_ = false;
        this.swapCovariancePseudoRoots_ = new ArrayList<>();
        this.timeDependentCalibratedSwaptionVols_ = new ArrayList<>();
        performChecks(evolution_, corr_, displacedSwapVariances_, mktCapletVols_, cs_);
    }

    /** Validates inputs to a CTSMM caplet calibration. Throws on inconsistency. */
    public static void performChecks(final EvolutionDescription evolution, final PiecewiseConstantCorrelation corr,
            final List< PiecewiseConstantVariance > displacedSwapVariances, final double[] mktCapletVols,
            final CurveState cs) {
        final double[] evolutionTimes = evolution.evolutionTimes();
        final List< Double > corrTimes = corr.times();
        QL.require(evolutionTimes.length == corrTimes.size(), "evolutionTimes vs correlation times length mismatch");
        for ( int i = 0; i < evolutionTimes.length; ++i ) {
            QL.require(evolutionTimes[i] == corrTimes.get(i),
                    "evolutionTimes[" + i + "] != correlation.times[" + i + "]");
        }

        final double[] rateTimes = evolution.rateTimes();
        final double[] csRateTimes = cs.rateTimes();
        QL.require(rateTimes.length == csRateTimes.length, "rateTimes length mismatch");
        for ( int i = 0; i < rateTimes.length; ++i ) {
            QL.require(rateTimes[i] == csRateTimes[i],
                    "rateTimes[" + i + "] EvolutionDescription vs CurveState mismatch");
        }

        final int numberOfRates = evolution.numberOfRates();
        QL.require(numberOfRates == displacedSwapVariances.size(),
                "numberOfRates (" + numberOfRates + ") vs displacedSwapVariances size (" + displacedSwapVariances.size()
                        + ") mismatch");
        QL.require(numberOfRates == corr.numberOfRates(),
                "numberOfRates (" + numberOfRates + ") vs corr.numberOfRates (" + corr.numberOfRates() + ") mismatch");
        QL.require(numberOfRates == mktCapletVols.length,
                "numberOfRates (" + numberOfRates + ") vs mktCapletVols length (" + mktCapletVols.length
                        + ") mismatch");
        QL.require(numberOfRates == cs.numberOfRates(),
                "numberOfRates (" + numberOfRates + ") vs CurveState numberOfRates (" + cs.numberOfRates()
                        + ") mismatch");

        // rateTimes minus last must equal evolutionTimes
        QL.require(rateTimes.length - 1 == evolutionTimes.length,
                "rateTimes.length-1 (" + (rateTimes.length - 1) + ") != evolutionTimes.length (" + evolutionTimes.length
                        + ")");
        for ( int i = 0; i < evolutionTimes.length; ++i ) {
            QL.require(rateTimes[i] == evolutionTimes[i], "rateTimes[" + i + "] != evolutionTimes[" + i + "]");
        }

        // last caplet vol must equal last swaption vol
        final double lastSwaptionVol = displacedSwapVariances.get(numberOfRates - 1).totalVolatility(numberOfRates - 1);
        final double diff = Math.abs(lastSwaptionVol - mktCapletVols[numberOfRates - 1]);
        QL.require(diff < 1e-12,
                "last caplet vol (" + mktCapletVols[numberOfRates - 1] + ") must be equal to last swaption vol ("
                        + lastSwaptionVol + "); discrepancy is " + (lastSwaptionVol - mktCapletVols[numberOfRates
                        - 1]));
    }

    /**
     * Run the calibration loop. After completion, {@link #failures()} returns the number of solver failures from the
     * last iteration, and the various error inspectors are populated.
     */
    public boolean calibrate(final int numberOfFactors, final int maxIterations, final double capletVolTolerance,
            final int innerSolvingMaxIterations, final double innerSolvingTolerance) {
        // initialize results
        calibrated_ = false;
        failures_ = 987654321;
        deformationSize_ = 987654321;
        capletRmsError_ = swaptionRmsError_ = 987654321;
        capletMaxError_ = swaptionMaxError_ = 987654321;

        usedCapletVols_ = mktCapletVols_.clone();
        for ( int i = 0; i < numberOfRates_; ++i ) {
            mktSwaptionVols_[i] = displacedSwapVariances_.get(i).totalVolatility(i);
        }

        final double[] displacements = new double[numberOfRates_];
        Arrays.fill(displacements, displacement_);
        final double[] rateTimes = evolution_.rateTimes();
        int iterations = 0;

        do {
            failures_ = calibrationImpl(numberOfFactors, innerSolvingMaxIterations, innerSolvingTolerance);

            // Build model swap-pseudo-roots facade and convert to forward via adapter.
            // Use direct PseudoRootFacade + CotSwapToFwdAdapter (Track A classes).
            final MarketModel ctsmm = new PseudoRootFacade(swapCovariancePseudoRoots_, rateTimes,
                    cs_.coterminalSwapRates(), displacements);
            final Matrix swaptionTotCovariance = ctsmm.totalCovariance(numberOfRates_ - 1);

            final CotSwapToFwdAdapter flmm = new CotSwapToFwdAdapter(ctsmm);
            final Matrix capletTotCovariance = flmm.totalCovariance(numberOfRates_ - 1);

            // check fit
            capletRmsError_ = swaptionRmsError_ = 0.0;
            capletMaxError_ = swaptionMaxError_ = -1.0;

            for ( int i = 0; i < numberOfRates_; ++i ) {
                mdlSwaptionVols_[i] = Math.sqrt(swaptionTotCovariance.get(i, i) / rateTimes[i]);
                final double swaptionError = Math.abs(mktSwaptionVols_[i] - mdlSwaptionVols_[i]);
                swaptionRmsError_ += swaptionError * swaptionError;
                swaptionMaxError_ = Math.max(swaptionMaxError_, swaptionError);

                mdlCapletVols_[i] = Math.sqrt(capletTotCovariance.get(i, i) / rateTimes[i]);
                final double capletError = Math.abs(mktCapletVols_[i] - mdlCapletVols_[i]);
                capletRmsError_ += capletError * capletError;
                capletMaxError_ = Math.max(capletMaxError_, capletError);

                if ( i < numberOfRates_ - 1 ) {
                    usedCapletVols_[i] *= mktCapletVols_[i] / mdlCapletVols_[i];
                }
            }
            swaptionRmsError_ = Math.sqrt(swaptionRmsError_ / numberOfRates_);
            capletRmsError_ = Math.sqrt(capletRmsError_ / numberOfRates_);
            ++iterations;
        } while ( iterations < maxIterations && capletRmsError_ > capletVolTolerance );

        // build time-dependent swaption vols for inspection
        final MarketModel ctsmmFinal = new PseudoRootFacade(swapCovariancePseudoRoots_, rateTimes,
                cs_.coterminalSwapRates(), displacements);
        timeDependentCalibratedSwaptionVols_.clear();
        for ( int i = 0; i < numberOfRates_; ++i ) {
            timeDependentCalibratedSwaptionVols_.add(ctsmmFinal.timeDependentVolatility(i));
        }

        calibrated_ = true;
        return failures_ == 0;
    }

    /** Default-tolerance overload matching C++ default values. */
    public boolean calibrate(final int numberOfFactors, final int maxIterations, final double capletVolTolerance) {
        return calibrate(numberOfFactors, maxIterations, capletVolTolerance, 100, 1e-8);
    }

    // -- inspectors -------------------------------------------------------

    /** Subclass calibration step. Populates {@link #swapCovariancePseudoRoots_}. Returns number of failures. */
    protected abstract int calibrationImpl(int numberOfFactors, int innerMaxIterations, double innerTolerance);

    public double[] mktCapletVols() {
        return mktCapletVols_;
    }

    public double[] mdlCapletVols() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return mdlCapletVols_;
    }

    public double[] mktSwaptionVols() {
        return mktSwaptionVols_;
    }

    public double[] mdlSwaptionVols() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return mdlSwaptionVols_;
    }

    public int failures() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return failures_;
    }

    public double deformationSize() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return deformationSize_;
    }

    public double capletRmsError() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return capletRmsError_;
    }

    public double capletMaxError() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return capletMaxError_;
    }

    public double swaptionRmsError() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return swaptionRmsError_;
    }

    public double swaptionMaxError() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return swaptionMaxError_;
    }

    public List< Matrix > swapPseudoRoots() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return swapCovariancePseudoRoots_;
    }

    public Matrix swapPseudoRoot(final int i) {
        QL.require(calibrated_, "not successfully calibrated yet");
        QL.require(i < swapCovariancePseudoRoots_.size(),
                i + " is an invalid index, must be less than " + swapCovariancePseudoRoots_.size());
        return swapCovariancePseudoRoots_.get(i);
    }

    public CurveState curveState() {
        return cs_;
    }

    public double[] displacements() {
        final double[] out = new double[numberOfRates_];
        Arrays.fill(out, displacement_);
        return out;
    }

    public double[] timeDependentCalibratedSwaptionVols(final int i) {
        QL.require(i < numberOfRates_, "index (" + i + ") must less than number of rates (" + numberOfRates_ + ")");
        return timeDependentCalibratedSwaptionVols_.get(i);
    }

    // -- static performChecks --------------------------------------------

    public double[] timeDependentUnCalibratedSwaptionVols(final int i) {
        QL.require(i < numberOfRates_, "index (" + i + ") must less than number of rates (" + numberOfRates_ + ")");
        return displacedSwapVariances_.get(i).volatilities();
    }
}
