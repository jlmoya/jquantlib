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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.SwapForwardMappings;

/**
 * Alpha-form CTSMM caplet calibration — uses AlphaFinder per rate.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/capletcoterminalalphacalibration.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Phase 3j B.5 (Track B). Depends on {@link AlphaFinder} (B.7) and
 * {@link AlphaFormLinearHyperbolic} default form (B.1).
 */
public final class CTSMMCapletAlphaFormCalibration extends CTSMMCapletCalibration {

    private final double[] alphaInitial_;
    private final double[] alphaMax_;
    private final double[] alphaMin_;
    private final boolean maximizeHomogeneity_;
    private AlphaForm parametricForm_;
    // results
    private double[] alpha_;
    private double[] a_;
    private double[] b_;

    public CTSMMCapletAlphaFormCalibration(final EvolutionDescription evolution,
                                           final PiecewiseConstantCorrelation corr,
                                           final List<PiecewiseConstantVariance> displacedSwapVariances,
                                           final double[] capletVols,
                                           final CurveState cs,
                                           final double displacement,
                                           final double[] alphaInitial,
                                           final double[] alphaMax,
                                           final double[] alphaMin,
                                           final boolean maximizeHomogeneity,
                                           final AlphaForm parametricForm) {
        super(evolution, corr, displacedSwapVariances, capletVols, cs, displacement);
        this.alphaInitial_ = alphaInitial.clone();
        this.alphaMax_ = alphaMax.clone();
        this.alphaMin_ = alphaMin.clone();
        this.maximizeHomogeneity_ = maximizeHomogeneity;
        this.parametricForm_ = parametricForm == null
                ? new AlphaFormLinearHyperbolic(evolution.rateTimes())
                : parametricForm;

        QL.require(numberOfRates_ == alphaInitial.length,
                "mismatch between number of rates (" + numberOfRates_
                        + ") and alphaInitial (" + alphaInitial.length + ")");
        QL.require(numberOfRates_ == alphaMax.length,
                "mismatch between number of rates (" + numberOfRates_
                        + ") and alphaMax (" + alphaMax.length + ")");
        QL.require(numberOfRates_ == alphaMin.length,
                "mismatch between number of rates (" + numberOfRates_
                        + ") and alphaMin (" + alphaMin.length + ")");

        this.alpha_ = new double[numberOfRates_];
        this.a_ = new double[numberOfRates_];
        this.b_ = new double[numberOfRates_];
    }

    /** Convenience constructor with default LinearHyperbolic form. */
    public CTSMMCapletAlphaFormCalibration(final EvolutionDescription evolution,
                                           final PiecewiseConstantCorrelation corr,
                                           final List<PiecewiseConstantVariance> displacedSwapVariances,
                                           final double[] capletVols,
                                           final CurveState cs,
                                           final double displacement,
                                           final double[] alphaInitial,
                                           final double[] alphaMax,
                                           final double[] alphaMin,
                                           final boolean maximizeHomogeneity) {
        this(evolution, corr, displacedSwapVariances, capletVols, cs, displacement,
                alphaInitial, alphaMax, alphaMin, maximizeHomogeneity, null);
    }

    /** Calibrated alpha vector. */
    public double[] alpha() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return alpha_;
    }

    @Override
    protected int calibrationImpl(final int numberOfFactors,
                                  final int maxIterations,
                                  final double tolerance) {
        final List<Matrix> result = new ArrayList<>();
        final int failures = capletAlphaFormCalibration(evolution_, corr_,
                displacedSwapVariances_, usedCapletVols_, cs_, displacement_,
                alphaInitial_, alphaMax_, alphaMin_, maximizeHomogeneity_, parametricForm_,
                numberOfFactors, maxIterations, tolerance,
                alpha_, a_, b_, result);
        this.swapCovariancePseudoRoots_ = result;
        return failures;
    }

    /** The actual calibration function. */
    public static int capletAlphaFormCalibration(final EvolutionDescription evolution,
                                                  final PiecewiseConstantCorrelation corr,
                                                  final List<PiecewiseConstantVariance> displacedSwapVariances,
                                                  final double[] capletVols,
                                                  final CurveState cs,
                                                  final double displacement,
                                                  final double[] alphaInitial,
                                                  final double[] alphaMax,
                                                  final double[] alphaMin,
                                                  final boolean maximizeHomogeneity,
                                                  final AlphaForm parametricForm,
                                                  final int numberOfFactors,
                                                  final int maxIterations,
                                                  final double tolerance,
                                                  final double[] alpha,
                                                  final double[] a,
                                                  final double[] b,
                                                  final List<Matrix> swapCovariancePseudoRoots) {
        CTSMMCapletCalibration.performChecks(evolution, corr, displacedSwapVariances, capletVols, cs);

        final int numberOfSteps = evolution.numberOfSteps();
        final int numberOfRates = evolution.numberOfRates();
        final double[] rateTimes = evolution.rateTimes();

        QL.require(numberOfFactors <= numberOfRates,
                "number of factors (" + numberOfFactors
                        + ") cannot be greater than numberOfRates (" + numberOfRates + ")");
        QL.require(numberOfFactors > 0,
                "number of factors (" + numberOfFactors + ") must be greater than zero");

        int failures = 0;

        // ensure outputs sized
        if (alpha.length < numberOfRates) throw new IllegalArgumentException("alpha must be sized >= numberOfRates");
        if (a.length < numberOfRates) throw new IllegalArgumentException("a must be sized >= numberOfRates");
        if (b.length < numberOfRates) throw new IllegalArgumentException("b must be sized >= numberOfRates");

        // factor reduction
        final List<Matrix> corrPseudo = new ArrayList<>(corr.times().size());
        for (int i = 0; i < corr.times().size(); ++i) {
            corrPseudo.add(PseudoSqrt.rankReducedSqrt(corr.correlation(i), numberOfFactors,
                    1, SalvagingAlgorithm.None));
        }

        final Matrix zedMatrix = SwapForwardMappings.coterminalSwapZedMatrix(cs, displacement);
        final Matrix invertedZedMatrix = zedMatrix.inverse();

        // vectors for new vol
        final List<double[]> newVols = new ArrayList<>();
        final double[] theseNewVols = new double[numberOfRates];
        final double[] firstRateVols = new double[numberOfRates];
        firstRateVols[0] = Math.sqrt(displacedSwapVariances.get(0).variances()[0]);
        final double[] secondRateVols = new double[numberOfRates];
        final double[] correlations = new double[numberOfRates];
        newVols.add(firstRateVols.clone());

        alpha[0] = alphaInitial[0];
        a[0] = b[0] = 1.0;

        final AlphaFinder solver = new AlphaFinder(parametricForm);

        // final caplet and swaption are the same; skip last
        double[] firstSrc = firstRateVols;
        for (int i = 0; i < numberOfRates - 1; ++i) {
            final double[] var = displacedSwapVariances.get(i + 1).variances();
            for (int j = 0; j < i + 2; ++j) {
                secondRateVols[j] = Math.sqrt(var[j]);
            }

            for (int k = 0; k < i + 1; k++) {
                double correlation = 0.0;
                for (int l = 0; l < numberOfFactors; ++l) {
                    final double term1 = corrPseudo.get(k).get(i, l);
                    final double term2 = corrPseudo.get(k).get(i + 1, l);
                    correlation += term1 * term2;
                }
                correlations[k] = correlation;
            }

            double w0 = invertedZedMatrix.get(i, i);
            final double w1 = invertedZedMatrix.get(i, i + 1);
            // w0 adjustment
            for (int k = i + 2; k < invertedZedMatrix.columns(); ++k) {
                w0 += invertedZedMatrix.get(i, k);
            }

            final double targetVariance = capletVols[i] * capletVols[i] * rateTimes[i];

            final double[] alphaOut = new double[1];
            final double[] aOut = new double[1];
            final double[] bOut = new double[1];
            final boolean success;
            if (maximizeHomogeneity) {
                success = solver.solveWithMaxHomogeneity(alphaInitial[i + 1], i,
                        firstSrc, secondRateVols, correlations,
                        w0, w1, targetVariance, tolerance,
                        alphaMax[i + 1], alphaMin[i + 1], maxIterations,
                        alphaOut, aOut, bOut, theseNewVols);
            } else {
                success = solver.solve(alphaInitial[i + 1], i,
                        firstSrc, secondRateVols, correlations,
                        w0, w1, targetVariance, tolerance,
                        alphaMax[i + 1], alphaMin[i + 1], maxIterations,
                        alphaOut, aOut, bOut, theseNewVols);
            }

            if (!success) {
                QL.error("alpha form failure");
                throw new RuntimeException("alpha form failure");
            }
            alpha[i + 1] = alphaOut[0];
            a[i + 1] = aOut[0];
            b[i + 1] = bOut[0];

            newVols.add(theseNewVols.clone());
            firstSrc = newVols.get(newVols.size() - 1);
        }

        // build pseudoroots
        swapCovariancePseudoRoots.clear();
        for (int k = 0; k < numberOfSteps; ++k) {
            final Matrix m = new Matrix(corrPseudo.get(k));  // copy
            for (int j = 0; j < numberOfRates; ++j) {
                final double coeff = newVols.get(j)[k];
                for (int i = 0; i < numberOfFactors; ++i) {
                    m.set(j, i, m.get(j, i) * coeff);
                }
            }
            QL.require(m.rows() == numberOfRates,
                    "step " + k + " abcd vol wrong number of rows: "
                            + m.rows() + " instead of " + numberOfRates);
            QL.require(m.columns() == numberOfFactors,
                    "step " + k + " abcd vol wrong number of columns: "
                            + m.columns() + " instead of " + numberOfFactors);
            swapCovariancePseudoRoots.add(m);
        }
        return failures;
    }
}
