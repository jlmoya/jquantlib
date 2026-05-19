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
import org.jquantlib.math.Quadratic;
import org.jquantlib.math.matrixutilities.BasisIncompleteOrdered;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.math.optimization.SphereCylinderOptimizer;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.model.marketmodels.SwapForwardMappings;

import java.util.ArrayList;
import java.util.List;

/**
 * Maximum-homogeneity CTSMM caplet calibration via sphere-cylinder optimization.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/capletcoterminalmaxhomogeneity.{hpp,cpp}}
 * (QuantLib v1.42.1, ~492 LOC C++).
 *
 * <p>Phase 3j B.6 (Track B). Depends on {@link SphereCylinderOptimizer} and
 * {@link BasisIncompleteOrdered} (both in math.* packages).
 */
public final class CTSMMCapletMaxHomogeneityCalibration extends CTSMMCapletCalibration {

    private final double caplet0Swaption1Priority_;
    private double totalSwaptionError_;

    public CTSMMCapletMaxHomogeneityCalibration(final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr, final List< PiecewiseConstantVariance > displacedSwapVariances,
            final double[] capletVols, final CurveState cs, final double displacement,
            final double caplet0Swaption1Priority) {
        super(evolution, corr, displacedSwapVariances, capletVols, cs, displacement);
        QL.require(caplet0Swaption1Priority >= 0.0 && caplet0Swaption1Priority <= 1.0,
                "caplet0Swaption1Priority (" + caplet0Swaption1Priority + ") must be in [0.0, 1.0]");
        this.caplet0Swaption1Priority_ = caplet0Swaption1Priority;
    }

    public CTSMMCapletMaxHomogeneityCalibration(final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr, final List< PiecewiseConstantVariance > displacedSwapVariances,
            final double[] capletVols, final CurveState cs, final double displacement) {
        this(evolution, corr, displacedSwapVariances, capletVols, cs, displacement, 1.0);
    }

    /** The actual calibration function. */
    public static int capletMaxHomogeneityCalibration(final EvolutionDescription evolution,
            final PiecewiseConstantCorrelation corr, final List< PiecewiseConstantVariance > displacedSwapVariances,
            final double[] capletVols, final CurveState cs, final double displacement,
            final double caplet0Swaption1Priority, final int numberOfFactors, final int maxIterations,
            final double tolerance, final double[] deformationSize, final double[] totalSwaptionError,
            final List< Matrix > swapCovariancePseudoRoots) {

        CTSMMCapletCalibration.performChecks(evolution, corr, displacedSwapVariances, capletVols, cs);

        final int numberOfSteps = evolution.numberOfSteps();
        final int numberOfRates = evolution.numberOfRates();
        final double[] rateTimes = evolution.rateTimes();

        QL.require(numberOfFactors <= numberOfRates,
                "number of factors (" + numberOfFactors + ") cannot be greater than numberOfRates (" + numberOfRates
                        + ")");
        QL.require(numberOfFactors > 0, "number of factors (" + numberOfFactors + ") must be greater than zero");

        int failures = 0;
        totalSwaptionError[0] = 0.0;
        deformationSize[0] = 0.0;

        // factor reduction
        final List< Matrix > corrPseudo = new ArrayList<>(corr.times().size());
        for ( int i = 0; i < corr.times().size(); ++i ) {
            corrPseudo.add(
                    PseudoSqrt.rankReducedSqrt(corr.correlation(i), numberOfFactors, 1, SalvagingAlgorithm.None));
        }

        final Matrix zedMatrix = SwapForwardMappings.coterminalSwapZedMatrix(cs, displacement);
        final Matrix invertedZedMatrix = zedMatrix.inverse();

        // vectors for new vols of all swap rates
        final List< double[] > newVols = new ArrayList<>();
        final double[] theseNewVols = new double[numberOfRates];
        final double[] firstRateVols = new double[numberOfRates];
        firstRateVols[0] = Math.sqrt(displacedSwapVariances.get(0).variances()[0]);
        final double[] secondRateVols = new double[numberOfRates];
        final double[] correlations = new double[numberOfRates];
        newVols.add(firstRateVols.clone());

        double[] firstSrc = firstRateVols;

        // skip last (final caplet == final swaption)
        for ( int i = 0; i < numberOfRates - 1; ++i ) {
            final double thisFinalWeight = i > 1 ? (i - 1) / 2.0 : 1.0;

            final double[] var = displacedSwapVariances.get(i + 1).variances();
            for ( int j = 0; j < i + 2; ++j ) {
                secondRateVols[j] = Math.sqrt(var[j]);
            }

            for ( int k = 0; k < i + 1; k++ ) {
                double correlation = 0.0;
                for ( int l = 0; l < numberOfFactors; ++l ) {
                    correlation += corrPseudo.get(k).get(i, l) * corrPseudo.get(k).get(i + 1, l);
                }
                correlations[k] = correlation;
            }

            double w0 = invertedZedMatrix.get(i, i);
            final double w1 = invertedZedMatrix.get(i, i + 1);
            for ( int k = i + 2; k < invertedZedMatrix.columns(); ++k ) {
                w0 += invertedZedMatrix.get(i, k);
            }

            final double targetCapletVariance = capletVols[i] * capletVols[i] * rateTimes[i];

            final double[] thisCapletErr = new double[1];
            final double[] thisSwaptionErr = new double[1];

            final boolean success = singleRateClosestPointFinder(i, secondRateVols, firstSrc, targetCapletVariance,
                    correlations, w0, w1, caplet0Swaption1Priority, maxIterations, tolerance, theseNewVols,
                    thisFinalWeight, thisSwaptionErr, thisCapletErr);

            totalSwaptionError[0] += thisSwaptionErr[0] * thisSwaptionErr[0];

            if ( !success )
                ++failures;

            for ( int j = 0; j < i + 2; ++j ) {
                deformationSize[0] += (theseNewVols[i] - secondRateVols[i]) * (theseNewVols[i] - secondRateVols[i]);
            }

            newVols.add(theseNewVols.clone());
            firstSrc = newVols.get(newVols.size() - 1);
        }

        swapCovariancePseudoRoots.clear();
        for ( int k = 0; k < numberOfSteps; ++k ) {
            final Matrix m = new Matrix(corrPseudo.get(k));  // copy
            for ( int j = 0; j < numberOfRates; ++j ) {
                final double coeff = newVols.get(j)[k];
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

    /** Mirrors the C++ namespace-level singleRateClosestPointFinder. */
    private static boolean singleRateClosestPointFinder(final int capletNumber, final double[] homogeneousSolution,
            final double[] previousRateSolution, final double capletVariance, final double[] correlations,
            final double w0, final double w1, final double capletSwaptionPriority, final int maxIterations,
            final double tolerance, final double[] solution, final double finalWeight, final double[] swaptionError,
            final double[] capletError) {

        if ( capletNumber == 0 ) {
            // single caplet special case
            final double previousSwapVariance = previousRateSolution[0] * previousRateSolution[0];
            final double thisSwapVariance =
                    homogeneousSolution[0] * homogeneousSolution[0] + homogeneousSolution[1] * homogeneousSolution[1];
            final double crossTerm = 2 * w0 * w1 * correlations[0] * previousRateSolution[0];
            final double constantTerm = w0 * w0 * previousSwapVariance - capletVariance;
            final double theta = w1 * w1;

            final Quadratic q = new Quadratic(theta, crossTerm, constantTerm);
            final double[] roots = new double[2];
            final boolean capSuccess = q.roots(roots);
            final double volminus = roots[0];
            final double residual = thisSwapVariance - volminus * volminus;
            final boolean swapSuccess = residual >= 0.0;
            final boolean success = capSuccess && swapSuccess;

            if ( success ) {
                solution[0] = volminus;
                solution[1] = Math.sqrt(residual);
                swaptionError[0] = 0.0;
                capletError[0] = 0.0;
                return true;
            }

            final boolean prioritizeCaplet = capletSwaptionPriority < 0.5;

            if ( capSuccess && prioritizeCaplet ) {
                solution[0] = volminus;
                solution[1] = 0;
                swaptionError[0] = Math.sqrt(thisSwapVariance) - volminus;
                capletError[0] = 0.0;
                return success;
            }
            if ( capSuccess ) {
                // !prioritizeCaplet
                solution[0] = Math.sqrt(thisSwapVariance);
                solution[1] = 0.0;
                swaptionError[0] = 0.0;
                capletError[0] = Math.sqrt(q.apply(solution[0]) + capletVariance) - Math.sqrt(capletVariance);
                return success;
            }

            // caplets failed
            if ( swapSuccess ) {
                solution[0] = volminus;
                solution[1] = Math.sqrt(residual);
                swaptionError[0] = 0.0;
                capletError[0] = Math.sqrt(q.apply(solution[0]) + capletVariance) - Math.sqrt(capletVariance);
                return success;
            }

            if ( prioritizeCaplet ) {
                solution[0] = volminus;
                solution[1] = 0;
                swaptionError[0] = Math.sqrt(thisSwapVariance) - volminus;
                capletError[0] = 0.0;
            } else {
                solution[0] = Math.sqrt(thisSwapVariance);
                solution[1] = 0.0;
                swaptionError[0] = 0.0;
                capletError[0] = Math.sqrt(q.apply(solution[0]) + capletVariance) - Math.sqrt(capletVariance);
            }
            return false;
        }

        // general case
        double previousSwapVariance = 0.0;
        double thisSwapVariance = 0.0;
        for ( int i2 = 0; i2 < capletNumber + 1; ++i2 ) {
            previousSwapVariance += previousRateSolution[i2] * previousRateSolution[i2];
            thisSwapVariance += homogeneousSolution[i2] * homogeneousSolution[i2];
        }
        thisSwapVariance += homogeneousSolution[capletNumber + 1] * homogeneousSolution[capletNumber + 1];

        final double theta = w1 * w1;
        final double[] bArr = new double[capletNumber + 1];
        final double[] cylinderCentre = new double[capletNumber + 1];
        final double[] targetArray = new double[capletNumber + 2];
        final double[] targetArrayRestricted = new double[capletNumber + 1];

        double bsq = 0.0;
        for ( int i = 0; i < capletNumber + 1; ++i ) {
            bArr[i] = 2 * w0 * w1 * correlations[i] * previousRateSolution[i] / theta;
            cylinderCentre[i] = -0.5 * bArr[i];
            targetArray[i] = homogeneousSolution[i];
            targetArrayRestricted[i] = targetArray[i];
            bsq += bArr[i] * bArr[i];
        }
        targetArray[capletNumber + 1] = homogeneousSolution[capletNumber + 1];

        final double A = previousSwapVariance * w0 * w0 / theta;
        final double constQuadraticTerm = A - 0.25 * bsq;
        final double S2 = capletVariance / theta - constQuadraticTerm;
        final double S = S2 > 0 ? Math.sqrt(S2) : 0.0;
        final double R = Math.sqrt(thisSwapVariance);

        // build orthonormal basis with cylinderCentre, then targetArrayRestricted, then identity
        final BasisIncompleteOrdered basis = new BasisIncompleteOrdered(capletNumber + 1);
        basis.addVector(cylinderCentre);
        basis.addVector(targetArrayRestricted);
        for ( int i = 0; i < capletNumber + 1; ++i ) {
            final double[] ei = new double[capletNumber + 1];
            ei[i] = 1.0;
            basis.addVector(ei);
        }

        final Matrix orthTransformationRestricted = basis.getBasisAsRowsInMatrix();
        final Matrix orthTransformation = new Matrix(capletNumber + 2, capletNumber + 2);
        for ( int r = 0; r < capletNumber + 2; ++r )
            for ( int c = 0; c < capletNumber + 2; ++c )
                orthTransformation.set(r, c, 0.0);
        orthTransformation.set(capletNumber + 1, capletNumber + 1, 1.0);
        for ( int k = 0; k < capletNumber + 1; ++k ) {
            for ( int l = 0; l < capletNumber + 1; ++l ) {
                orthTransformation.set(k, l, orthTransformationRestricted.get(k, l));
            }
        }

        // movedCentre = orthTransformationRestricted * cylinderCentre
        final double[] movedCentre = matVec(orthTransformationRestricted, cylinderCentre);
        final double alpha = movedCentre[0];
        final double[] movedTarget = matVec(orthTransformation, targetArray);

        double Z1 = 0.0, Z2 = 0.0, Z3 = 0.0;

        boolean success = false;

        if ( alpha <= 0.0 ) {
            // SphereCylinderOptimizer ctor requires alpha > 0; treat as infeasible
            Z1 = R * capletSwaptionPriority + (1 - capletSwaptionPriority) * (-alpha - S);
            swaptionError[0] = Z1 - R;
            capletError[0] = (-alpha - S) - Z1;
        } else {
            final SphereCylinderOptimizer optimizer = new SphereCylinderOptimizer(R, S, alpha, movedTarget[0],
                    movedTarget[1], movedTarget[movedTarget.length - 1], finalWeight);

            if ( !optimizer.isIntersectionNonEmpty() ) {
                Z1 = R * capletSwaptionPriority + (1 - capletSwaptionPriority) * (alpha - S);
                Z2 = 0.0;
                Z3 = 0.0;
                swaptionError[0] = Z1 - R;
                capletError[0] = (alpha - S) - Z1;
            } else {
                success = true;
                capletError[0] = 0.0;
                swaptionError[0] = 0.0;

                final double[] y = new double[3];
                if ( maxIterations > 0 ) {
                    optimizer.findClosest(maxIterations, tolerance, y);
                } else {
                    optimizer.findByProjection(y);
                }
                Z1 = y[0];
                Z2 = y[1];
                Z3 = y[2];
            }
        }

        final double[] rotatedSolution = new double[capletNumber + 2];
        rotatedSolution[0] = Z1;
        rotatedSolution[1] = Z2;
        rotatedSolution[capletNumber + 1] = Z3;

        final Matrix orthTransposed = orthTransformation.transpose();
        final double[] arraySolution = matVec(orthTransposed, rotatedSolution);

        int idx = 0;
        for ( ; idx < arraySolution.length; ++idx ) {
            solution[idx] = arraySolution[idx];
        }
        for ( ; idx < solution.length; ++idx ) {
            solution[idx] = 0.0;
        }
        return success;
    }

    /** Local matrix-vector product helper (avoids dependency on Matrix.mul(double[]) which may not exist). */
    private static double[] matVec(final Matrix m, final double[] v) {
        final int rows = m.rows();
        final int cols = m.columns();
        final double[] out = new double[rows];
        for ( int i = 0; i < rows; ++i ) {
            double s = 0.0;
            for ( int j = 0; j < cols && j < v.length; ++j ) {
                s += m.get(i, j) * v[j];
            }
            out[i] = s;
        }
        return out;
    }

    // -- private --------------------------------------------------------------

    @Override
    protected int calibrationImpl(final int numberOfFactors, final int maxIterations, final double tolerance) {
        final List< Matrix > result = new ArrayList<>();
        final double[] deformation = new double[1];
        final double[] swaptionErr = new double[1];
        final int failures = capletMaxHomogeneityCalibration(evolution_, corr_, displacedSwapVariances_,
                usedCapletVols_, cs_, displacement_, caplet0Swaption1Priority_, numberOfFactors, maxIterations,
                tolerance, deformation, swaptionErr, result);
        this.deformationSize_ = deformation[0];
        this.totalSwaptionError_ = swaptionErr[0];
        this.swapCovariancePseudoRoots_ = result;
        return failures;
    }

    /** Inspector for the total swaption error. */
    public double totalSwaptionError() {
        QL.require(calibrated_, "not successfully calibrated yet");
        return totalSwaptionError_;
    }
}
