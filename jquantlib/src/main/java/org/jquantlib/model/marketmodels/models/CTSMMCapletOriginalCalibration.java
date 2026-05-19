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
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.SwapForwardMappings;

import java.util.ArrayList;
import java.util.List;

/**
 * "Original" CTSMM caplet calibration — solves a quadratic per rate to fit caplet vols.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/capletcoterminalswaptioncalibration.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Phase 3j B.4 (Track B). Naming note: C++ class is
 * {@code CTSMMCapletOriginalCalibration} but file name is {@code capletcoterminalswaptioncalibration.cpp}.
 */
public final class CTSMMCapletOriginalCalibration extends CTSMMCapletCalibration {

    private final double[] alpha_;
    private final boolean lowestRoot_;
    private final boolean useFullApprox_;

    public CTSMMCapletOriginalCalibration(final EvolutionDescription evolution, final PiecewiseConstantCorrelation corr,
            final List< PiecewiseConstantVariance > displacedSwapVariances, final double[] capletVols,
            final CurveState cs, final double displacement, final double[] alpha, final boolean lowestRoot,
            final boolean useFullApprox) {
        super(evolution, corr, displacedSwapVariances, capletVols, cs, displacement);
        QL.require(numberOfRates_ == alpha.length,
                "mismatch between number of rates (" + numberOfRates_ + ") and alpha (" + alpha.length + ")");
        this.alpha_ = alpha.clone();
        this.lowestRoot_ = lowestRoot;
        this.useFullApprox_ = useFullApprox;
    }

    /** The actual calibration function. */
    public static int calibrationFunction(final EvolutionDescription evolution, final PiecewiseConstantCorrelation corr,
            final List< PiecewiseConstantVariance > displacedSwapVariances, final double[] capletVols,
            final CurveState cs, final double displacement, final double[] alpha, final boolean lowestRoot,
            final boolean useFullApprox, final int numberOfFactors, final List< Matrix > swapCovariancePseudoRoots) {
        CTSMMCapletCalibration.performChecks(evolution, corr, displacedSwapVariances, capletVols, cs);

        final int numberOfSteps = evolution.numberOfSteps();
        final int numberOfRates = evolution.numberOfRates();
        final double[] rateTimes = evolution.rateTimes();

        QL.require(numberOfFactors <= numberOfRates,
                "number of factors (" + numberOfFactors + ") cannot be greater than numberOfRates (" + numberOfRates
                        + ")");
        QL.require(numberOfFactors > 0, "number of factors (" + numberOfFactors + ") must be greater than zero");

        int failures = 0;
        final double extraMultiplier = useFullApprox ? 1.0 : 0.0;

        // factor reduction
        final List< Matrix > corrPseudo = new ArrayList<>(corr.times().size());
        for ( int i = 0; i < corr.times().size(); ++i ) {
            corrPseudo.add(
                    PseudoSqrt.rankReducedSqrt(corr.correlation(i), numberOfFactors, 1, SalvagingAlgorithm.None));
        }

        final Matrix zedMatrix = SwapForwardMappings.coterminalSwapZedMatrix(cs, displacement);
        final Matrix invertedZedMatrix = zedMatrix.inverse();

        // do alpha part: scale variances by alpha, then renormalize so total is unchanged
        final Matrix swapTimeInhomogeneousVariances = new Matrix(numberOfSteps, numberOfRates);
        for ( int i = 0; i < numberOfSteps; ++i )
            for ( int j = 0; j < numberOfRates; ++j )
                swapTimeInhomogeneousVariances.set(i, j, 0.0);
        final double[] originalVariances = new double[numberOfRates];
        final double[] modifiedVariances = new double[numberOfRates];
        final double[] evolutionTimes = evolution.evolutionTimes();
        for ( int i = 0; i < numberOfSteps; ++i ) {
            final double s = (i == 0) ? 0.0 : evolutionTimes[i - 1];
            for ( int j = i; j < numberOfRates; ++j ) {
                final double[] var = displacedSwapVariances.get(j).variances();
                originalVariances[j] += var[i];
                final double scale = (1.0 + alpha[j] * s) * (1.0 + alpha[j] * s);
                final double v = var[i] / scale;
                swapTimeInhomogeneousVariances.set(i, j, v);
                modifiedVariances[j] += v;
            }
        }
        for ( int i = 0; i < numberOfSteps; ++i ) {
            for ( int j = i; j < numberOfRates; ++j ) {
                final double v =
                        swapTimeInhomogeneousVariances.get(i, j) * (originalVariances[j] / modifiedVariances[j]);
                swapTimeInhomogeneousVariances.set(i, j, v);
            }
        }

        // compute swap covariances for caplet approximation formula
        // without taking into account A and B
        final List< Matrix > CovarianceSwapPseudos = new ArrayList<>();
        final List< Matrix > CovarianceSwapCovs = new ArrayList<>();
        final List< Matrix > CovarianceSwapMarginalCovs = new ArrayList<>();

        for ( int i = 0; i < numberOfSteps; ++i ) {
            final Matrix base = new Matrix(corrPseudo.get(i));  // copy
            for ( int j = 0; j < numberOfRates; ++j ) {
                for ( int k = 0; k < base.columns(); ++k ) {
                    base.set(j, k, base.get(j, k) * Math.sqrt(swapTimeInhomogeneousVariances.get(i, j)));
                }
            }
            CovarianceSwapPseudos.add(base);

            final Matrix marg = base.mul(base.transpose());
            CovarianceSwapMarginalCovs.add(marg);

            final Matrix cov;
            if ( i == 0 ) {
                cov = new Matrix(marg);
            } else {
                cov = CovarianceSwapCovs.get(i - 1).add(marg);
            }
            CovarianceSwapCovs.add(cov);
        }

        // partial variances and covariances
        final double[] totVariance = new double[numberOfRates];
        final double[] almostTotVariance = new double[numberOfRates];
        final double[] almostTotCovariance = new double[numberOfRates];
        final double[] leftCovariance = new double[numberOfRates];
        for ( int i = 0; i < numberOfRates; ++i ) {
            for ( int jj = 0; jj <= i; ++jj ) {
                totVariance[i] += displacedSwapVariances.get(i).variances()[jj];
            }
            for ( int j = 0; j <= i - 1; ++j ) {
                almostTotVariance[i] += swapTimeInhomogeneousVariances.get(j, i);
            }
            for ( int j = 0; j <= i - 2; ++j ) {
                final Matrix thisPseudo = corrPseudo.get(j);
                double correlation = 0.0;
                for ( int k = 0; k < numberOfFactors; ++k ) {
                    correlation += thisPseudo.get(i - 1, k) * thisPseudo.get(i, k);
                }
                almostTotCovariance[i] += correlation * Math.sqrt(
                        swapTimeInhomogeneousVariances.get(j, i) * swapTimeInhomogeneousVariances.get(j, i - 1));
            }
            // C++ has: if (i>0) { ... j is the last value from for(j; j<=i-2)... loop =
            // i-1 actually because at the end of the loop j = i-1 (Integer signed loop)
            // }
            // Java: the C++ Integer j stayed at the last value after the loop;
            // pythonically: after the loop body executes for j=i-2, j becomes i-1 at increment
            // and we read it. So the corresponding j = i-1.
            if ( i > 0 ) {
                // C++: j is i-1 (after loop terminates with j > i-2)
                final int j = i - 1;
                final Matrix thisPseudo = corrPseudo.get(j);
                double correlation = 0.0;
                for ( int k = 0; k < numberOfFactors; ++k ) {
                    correlation += thisPseudo.get(i - 1, k) * thisPseudo.get(i, k);
                }
                leftCovariance[i] = correlation * Math.sqrt(
                        swapTimeInhomogeneousVariances.get(j, i) * swapTimeInhomogeneousVariances.get(j, i - 1));
            }
        }

        // a/b multipliers
        final double[] a = new double[numberOfSteps];
        for ( int i = 0; i < numberOfSteps; ++i )
            a[i] = 1.0;
        final double[] b = new double[numberOfSteps];

        b[0] = displacedSwapVariances.get(0).variances()[0] / swapTimeInhomogeneousVariances.get(0, 0);

        for ( int i = 1; i < numberOfSteps; ++i ) {
            for ( int j = 0; j <= i - 2; ++j ) {
                swapTimeInhomogeneousVariances.set(j, i - 1,
                        swapTimeInhomogeneousVariances.get(j, i - 1) * a[i - 1] * a[i - 1]);
            }
            // After loop: in C++ j is i-1 (the increment ran once past i-2)
            final int jPrime = i - 1;
            swapTimeInhomogeneousVariances.set(jPrime, i - 1,
                    swapTimeInhomogeneousVariances.get(jPrime, i - 1) * b[i - 1] * b[i - 1]);

            final double w0 = invertedZedMatrix.get(i - 1, i - 1);
            final double w1 = -invertedZedMatrix.get(i - 1, i);
            final double v1t1 = capletVols[i - 1] * capletVols[i - 1] * rateTimes[i - 1];

            // contribution from lower right corner
            double extraConstantPart = 0.0;
            for ( int k = i + 1; k < numberOfSteps; ++k ) {
                for ( int l = i + 1; l < numberOfSteps; ++l ) {
                    extraConstantPart += invertedZedMatrix.get(i - 1, k) * CovarianceSwapCovs.get(i - 1).get(k, l)
                            * invertedZedMatrix.get(i - 1, l);
                }
            }

            for ( int k = i + 1; k < numberOfSteps; ++k ) {
                if ( i > 1 ) {
                    extraConstantPart +=
                            invertedZedMatrix.get(i - 1, i - 1) * CovarianceSwapCovs.get(i - 2).get(i - 1, k)
                                    * invertedZedMatrix.get(i - 1, k) * a[i - 1];
                    extraConstantPart += invertedZedMatrix.get(i - 1, k) * CovarianceSwapCovs.get(i - 2).get(k, i - 1)
                            * invertedZedMatrix.get(i - 1, i - 1) * a[i - 1];
                }
                extraConstantPart +=
                        invertedZedMatrix.get(i - 1, i - 1) * CovarianceSwapMarginalCovs.get(i - 1).get(i - 1, k)
                                * invertedZedMatrix.get(i - 1, k) * b[i - 1];
                extraConstantPart += invertedZedMatrix.get(i - 1, k) * CovarianceSwapCovs.get(i - 1).get(k, i - 1)
                        * invertedZedMatrix.get(i - 1, i - 1) * b[i - 1];
            }

            // extra linear part
            double extraLinearPart = 0.0;
            for ( int k = i + 1; k < numberOfSteps; ++k ) {
                extraLinearPart += invertedZedMatrix.get(i - 1, k) * CovarianceSwapCovs.get(i - 1).get(k, i)
                        * invertedZedMatrix.get(i - 1, i);
                extraLinearPart += invertedZedMatrix.get(i - 1, i) * CovarianceSwapCovs.get(i - 1).get(i, k)
                        * invertedZedMatrix.get(i - 1, k);
            }

            final double constantPart = w0 * w0 * totVariance[i - 1] + extraConstantPart * extraMultiplier - v1t1;
            final double linearPart = -2 * w0 * w1 * (a[i - 1] * almostTotCovariance[i] + b[i - 1] * leftCovariance[i])
                    + extraLinearPart * extraMultiplier;
            final double quadraticPart = w1 * w1 * almostTotVariance[i];
            final double disc = linearPart * linearPart - 4.0 * constantPart * quadraticPart;

            double root;
            final double minimum = -linearPart / (2.0 * quadraticPart);
            boolean rightUsed = false;
            if ( disc < 0.0 ) {
                ++failures;
                root = minimum;
            } else if ( lowestRoot ) {
                root = (-linearPart - Math.sqrt(disc)) / (2.0 * quadraticPart);
            } else {
                if ( minimum > 1.0 ) {
                    root = (-linearPart - Math.sqrt(disc)) / (2.0 * quadraticPart);
                } else {
                    rightUsed = true;
                    root = (-linearPart + Math.sqrt(disc)) / (2.0 * quadraticPart);
                }
            }

            double varianceFound = root * root * almostTotVariance[i];
            double varianceToFind = totVariance[i] - varianceFound;
            double mult = varianceToFind / swapTimeInhomogeneousVariances.get(i, i);
            if ( mult <= 0.0 && rightUsed ) {
                root = (-linearPart - Math.sqrt(disc)) / (2.0 * quadraticPart);
                varianceFound = root * root * almostTotVariance[i];
                varianceToFind = totVariance[i] - varianceFound;
                mult = varianceToFind / swapTimeInhomogeneousVariances.get(i, i);
            }

            if ( mult < 0.0 ) {
                ++failures;
                a[i] = root;
                b[i] = 0.0;
            } else {
                a[i] = root;
                b[i] = Math.sqrt(mult);
            }

            QL.require(root >= 0.0, "negative root -- it should have not happened");
        }

        // Final tail: apply a/b to last variance
        {
            final int i = numberOfSteps;
            for ( int j = 0; j <= i - 2; ++j ) {
                swapTimeInhomogeneousVariances.set(j, i - 1,
                        swapTimeInhomogeneousVariances.get(j, i - 1) * a[i - 1] * a[i - 1]);
            }
            final int jPrime = i - 1;
            swapTimeInhomogeneousVariances.set(jPrime, i - 1,
                    swapTimeInhomogeneousVariances.get(jPrime, i - 1) * b[i - 1] * b[i - 1]);
        }

        // compute the results
        swapCovariancePseudoRoots.clear();
        for ( int k = 0; k < numberOfSteps; ++k ) {
            final Matrix m = new Matrix(corrPseudo.get(k));
            for ( int j = 0; j < numberOfRates; ++j ) {
                final double coeff = Math.sqrt(swapTimeInhomogeneousVariances.get(k, j));
                for ( int i = 0; i < numberOfFactors; ++i ) {
                    m.set(j, i, m.get(j, i) * coeff);
                }
            }
            QL.require(m.rows() == numberOfRates,
                    "step " + k + " abcd vol wrong number of rows: " + m.rows() + " instead of " + numberOfRates);
            QL.require(m.columns() == numberOfFactors,
                    "step " + k + " abcd vol wrong number of columns: " + m.columns() + " instead of "
                            + numberOfFactors);
            swapCovariancePseudoRoots.add(m);
        }
        return failures;
    }

    @Override
    protected int calibrationImpl(final int numberOfFactors, final int innerMaxIterations,
            final double innerTolerance) {
        // Run the static function and update swapCovariancePseudoRoots_
        final List< Matrix > result = new ArrayList<>();
        final int failures = calibrationFunction(evolution_, corr_, displacedSwapVariances_, usedCapletVols_, cs_,
                displacement_, alpha_, lowestRoot_, useFullApprox_, numberOfFactors, result);
        this.swapCovariancePseudoRoots_ = result;
        return failures;
    }
}
