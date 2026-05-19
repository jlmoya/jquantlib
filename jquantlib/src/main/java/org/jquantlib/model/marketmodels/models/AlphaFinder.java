/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

/*
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.models;

import org.jquantlib.math.Quadratic;

/**
 * Alpha solver for the alpha-form caplet calibration.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/alphafinder.{hpp,cpp}}
 * (QuantLib v1.42.1, ~659 LOC C++).
 *
 * <p>The C++ API uses {@code Real&} out-parameters; Java uses length-1
 * {@code double[]} holders or a length-3 result tuple where convenient. Caller-mutable {@code ratetwovols} is a
 * {@code double[]} pre-sized to the solution length (caller responsibility).
 *
 * <p>Phase 3j B.7 (Track B). Depends on {@link AlphaForm} (B.1) and
 * {@link Quadratic} (L0.3).
 */
public final class AlphaFinder {

    private final AlphaForm parametricform_;

    private int stepindex_;
    private double[] rateonevols_;
    private double[] ratetwohomogeneousvols_;
    private double[] putativevols_;
    private double[] correlations_;
    private double w0_, w1_;
    private double constantPart_;
    private double linearPart_;
    private double quadraticPart_;
    private double totalVar_;
    private double targetVariance_;

    public AlphaFinder(final AlphaForm parametricform) {
        this.parametricform_ = parametricform;
    }

    // -- public solve() / solveWithMaxHomogeneity() ------------------------------

    /**
     * Find an alpha that brings caplet variance to {@code targetVariance}.
     *
     * @param alpha       length-1 array; out: the alpha value found
     * @param a           length-1 array; out: scaling 'a'
     * @param b           length-1 array; out: scaling 'b'
     * @param ratetwovols caller-pre-allocated array; out: the new vol vector
     * @return true on success
     */
    public boolean solve(final double alpha0, final int stepindex, final double[] rateonevols,
            final double[] ratetwohomogeneousvols, final double[] correlations, final double w0, final double w1,
            final double targetVariance, final double tolerance, final double alphaMax, final double alphaMin,
            final int steps, final double[] alpha, final double[] a, final double[] b, final double[] ratetwovols) {
        prepareState(stepindex, rateonevols, ratetwohomogeneousvols, correlations, w0, w1, targetVariance);

        // initial alpha
        double valueAtTP = valueAtTurningPoint(alpha0);

        if ( valueAtTP <= targetVariance ) {
            finalPart(alpha0, stepindex, ratetwohomogeneousvols, quadraticPart_, linearPart_, constantPart_, alpha, a,
                    b, ratetwovols);
            return true;
        }

        // bracket
        double bottomValue = valueAtTurningPoint(alphaMin);
        double bottomAlpha = alphaMin;
        double topValue = valueAtTurningPoint(alphaMax);
        double topAlpha = alphaMax;
        double bilimit = alpha0;

        if ( bottomValue > targetVariance && topValue > targetVariance ) {
            int i = 1;
            while ( i < steps && topValue > targetVariance ) {
                topAlpha = alpha0 + (alphaMax - alpha0) * (i + 0.0) / (steps + 0.0);
                topValue = valueAtTurningPoint(topAlpha);
                ++i;
            }
            if ( topValue <= targetVariance ) {
                bilimit = alpha0 + (topAlpha - alpha0) * (i - 2.0) / (steps + 0.0);
            }
        }

        if ( bottomValue > targetVariance && topValue > targetVariance ) {
            int i = 1;
            while ( i < steps && topValue > targetVariance ) {
                bottomAlpha = alpha0 + (alphaMin - alpha0) * (i + 0.0) / (steps + 0.0);
                bottomValue = valueAtTurningPoint(bottomAlpha);
                ++i;
            }
            if ( bottomValue <= targetVariance ) {
                bilimit = alpha0 + (bottomAlpha - alpha0) * (i - 2.0) / (steps + 0.0);
            }
        }

        if ( bottomValue > targetVariance && topValue > targetVariance ) {
            return false;
        }

        if ( bottomValue <= targetVariance ) {
            // increasing function
            alpha[0] = bisectionForValueAtTP(targetVariance, bottomAlpha, bilimit, tolerance);
        } else {
            // decreasing function (use minus)
            alpha[0] = bisectionForMinusValueAtTP(-targetVariance, bilimit, topAlpha, tolerance);
        }

        finalPart(alpha[0], stepindex, ratetwohomogeneousvols, quadraticPart_, linearPart_, constantPart_, alpha, a, b,
                ratetwovols);
        return true;
    }

    /** Maximum-homogeneity variant: brackets a feasible region then minimizes a deformation measure. */
    public boolean solveWithMaxHomogeneity(final double alpha0, final int stepindex, final double[] rateonevols,
            final double[] ratetwohomogeneousvols, final double[] correlations, final double w0, final double w1,
            final double targetVariance, final double tolerance, final double alphaMax, final double alphaMin,
            final int steps, final double[] alpha, final double[] a, final double[] b, final double[] ratetwovols) {
        prepareState(stepindex, rateonevols, ratetwohomogeneousvols, correlations, w0, w1, targetVariance);
        putativevols_ = new double[ratetwohomogeneousvols.length];

        double alpha1 = alphaMin;
        double alpha2 = alphaMax;

        boolean alpha0OK = testIfSolutionExists(alpha0);
        boolean alphaMaxOK = testIfSolutionExists(alphaMax);
        boolean alphaMinOK = testIfSolutionExists(alphaMin);

        boolean foundOKPoint = alpha0OK || alphaMaxOK || alphaMinOK;

        if ( foundOKPoint ) {
            if ( !alphaMinOK ) {
                if ( alpha0OK ) {
                    alpha1 = findLowestOK(alphaMin, alpha0, tolerance);
                } else {
                    // alphaMaxOK must be true
                    alpha1 = findLowestOK(alpha0, alphaMax, tolerance);
                }
            }
            if ( !alphaMaxOK ) {
                alpha2 = findHighestOK(alpha1, alphaMax, tolerance);
            } else {
                alpha2 = alphaMax;
            }
        } else {
            // search for an OK point in steps
            boolean foundUpOK = false;
            boolean foundDownOK = false;
            double alphaUp = alpha0, alphaDown = alpha0;
            final double stepSize = (alphaMax - alpha0) / steps;
            for ( int j = 0; j < steps && !foundUpOK && !foundDownOK; ++j ) {
                alphaUp = alpha0 + j * stepSize;
                foundUpOK = testIfSolutionExists(alphaUp);
                alphaDown = alpha0 - j * stepSize;
                foundDownOK = testIfSolutionExists(alphaDown);
            }
            foundOKPoint = foundUpOK || foundDownOK;
            if ( !foundOKPoint ) {
                return false;
            }
            if ( foundUpOK ) {
                alpha1 = alphaUp;
                alpha2 = findHighestOK(alpha1, alphaMax, tolerance);
            } else {
                alpha2 = alphaDown;
                alpha1 = findLowestOK(alphaMin, alpha2, tolerance);
            }
        }

        // we have alpha1, alpha2 such that solution exists at endpoints; minimize within
        alpha[0] = minimizeHomogeneity(alpha1, alpha2, tolerance);

        finalPart(alpha[0], stepindex, ratetwohomogeneousvols, computeQuadraticPart(alpha[0]),
                computeLinearPart(alpha[0]), constantPart_, alpha, a, b, ratetwovols);
        return true;
    }

    // -- internal -----------------------------------------------------------------

    private void prepareState(final int stepindex, final double[] rateonevols, final double[] ratetwohomogeneousvols,
            final double[] correlations, final double w0, final double w1, final double targetVariance) {
        this.stepindex_ = stepindex;
        this.rateonevols_ = rateonevols.clone();
        this.ratetwohomogeneousvols_ = ratetwohomogeneousvols.clone();
        this.correlations_ = correlations.clone();
        this.w0_ = w0;
        this.w1_ = w1;
        this.targetVariance_ = targetVariance;

        this.totalVar_ = 0.0;
        for ( int i = 0; i <= stepindex + 1; ++i ) {
            totalVar_ += ratetwohomogeneousvols[i] * ratetwohomogeneousvols[i];
        }

        this.constantPart_ = 0.0;
        for ( int i = 0; i < stepindex + 1; ++i ) {
            constantPart_ += rateonevols[i] * rateonevols[i];
        }
        constantPart_ *= w0 * w0;
    }

    private double computeLinearPart(final double alpha) {
        double cov = 0.0;
        parametricform_.setAlpha(alpha);
        for ( int i = 0; i < stepindex_ + 1; ++i ) {
            final double vol1 = ratetwohomogeneousvols_[i] * parametricform_.apply(i);
            cov += vol1 * rateonevols_[i] * correlations_[i];
        }
        cov *= 2.0 * w0_ * w1_;
        return cov;
    }

    private double computeQuadraticPart(final double alpha) {
        double var = 0.0;
        parametricform_.setAlpha(alpha);
        for ( int i = 0; i < stepindex_ + 1; ++i ) {
            final double vol = ratetwohomogeneousvols_[i] * parametricform_.apply(i);
            var += vol * vol;
        }
        var *= w1_ * w1_;
        return var;
    }

    private double valueAtTurningPoint(final double alpha) {
        linearPart_ = computeLinearPart(alpha);
        quadraticPart_ = computeQuadraticPart(alpha);
        final Quadratic q = new Quadratic(quadraticPart_, linearPart_, constantPart_);
        return q.valueAtTurningPoint();
    }

    private double minusValueAtTurningPoint(final double alpha) {
        return -valueAtTurningPoint(alpha);
    }

    private boolean testIfSolutionExists(final double alpha) {
        if ( valueAtTurningPoint(alpha) >= targetVariance_ ) {
            return false;
        }
        final double[] dummyAlpha = new double[1];
        final double[] dummyA = new double[1];
        final double[] dummyB = new double[1];
        return finalPart(alpha, stepindex_, ratetwohomogeneousvols_, computeQuadraticPart(alpha),
                computeLinearPart(alpha), constantPart_, dummyAlpha, dummyA, dummyB, putativevols_);
    }

    private double homogeneityFailure(final double alpha) {
        final double[] dummyAlpha = new double[1];
        final double[] dummyA = new double[1];
        final double[] dummyB = new double[1];
        finalPart(alpha, stepindex_, ratetwohomogeneousvols_, computeQuadraticPart(alpha), computeLinearPart(alpha),
                constantPart_, dummyAlpha, dummyA, dummyB, putativevols_);
        double result = 0.0;
        for ( int i = 0; i <= stepindex_ + 1; ++i ) {
            final double val = putativevols_[i] - ratetwohomogeneousvols_[i];
            result += val * val;
        }
        return result;
    }

    private boolean finalPart(final double alphaFound, final int stepindex, final double[] ratetwohomogeneousvols,
            final double quadraticPart, final double linearPart, final double constantPart, final double[] alpha,
            final double[] a, final double[] b, final double[] ratetwovols) {
        alpha[0] = alphaFound;
        final Quadratic q2 = new Quadratic(quadraticPart, linearPart, constantPart - targetVariance_);
        parametricform_.setAlpha(alphaFound);
        final double[] roots = new double[2];
        q2.roots(roots);
        a[0] = roots[0];

        double varSoFar = 0.0;
        for ( int i = 0; i < stepindex + 1; ++i ) {
            ratetwovols[i] = ratetwohomogeneousvols[i] * parametricform_.apply(i) * a[0];
            varSoFar += ratetwovols[i] * ratetwovols[i];
        }

        final double varToFind = totalVar_ - varSoFar;
        if ( varToFind < 0.0 ) {
            return false;
        }
        final double requiredSd = Math.sqrt(varToFind);
        b[0] = requiredSd / (ratetwohomogeneousvols[stepindex + 1] * parametricform_.apply(stepindex));
        ratetwovols[stepindex + 1] = requiredSd;
        return true;
    }

    // -- bisection helpers (mimic C++ template Bisection / FindHighestOK / FindLowestOK / Minimize) --

    private double bisectionForValueAtTP(final double target, final double low0, final double high0,
            final double tolerance) {
        double low = low0, high = high0;
        double x = 0.5 * (low + high);
        double y = valueAtTurningPoint(x);
        do {
            if ( y < target )
                low = x;
            else if ( y > target )
                high = x;
            x = 0.5 * (low + high);
            y = valueAtTurningPoint(x);
        } while ( Math.abs(high - low) > tolerance );
        return x;
    }

    private double bisectionForMinusValueAtTP(final double target, final double low0, final double high0,
            final double tolerance) {
        double low = low0, high = high0;
        double x = 0.5 * (low + high);
        double y = minusValueAtTurningPoint(x);
        do {
            if ( y < target )
                low = x;
            else if ( y > target )
                high = x;
            x = 0.5 * (low + high);
            y = minusValueAtTurningPoint(x);
        } while ( Math.abs(high - low) > tolerance );
        return x;
    }

    private double findHighestOK(final double low0, final double high0, final double tolerance) {
        double low = low0, high = high0;
        double x = 0.5 * (low + high);
        boolean ok = testIfSolutionExists(x);
        do {
            if ( ok )
                low = x;
            else
                high = x;
            x = 0.5 * (low + high);
            ok = testIfSolutionExists(x);
        } while ( Math.abs(high - low) > tolerance );
        return x;
    }

    private double findLowestOK(final double low0, final double high0, final double tolerance) {
        double low = low0, high = high0;
        double x = 0.5 * (low + high);
        boolean ok = testIfSolutionExists(x);
        do {
            if ( ok )
                high = x;
            else
                low = x;
            x = 0.5 * (low + high);
            ok = testIfSolutionExists(x);
        } while ( Math.abs(high - low) > tolerance );
        return x;
    }

    private double minimizeHomogeneity(final double low0, final double high0, final double tolerance) {
        // Golden-section-style minimization of homogeneityFailure subject to testIfSolutionExists
        double low = low0, high = high0;
        double leftValue = homogeneityFailure(low);
        double rightValue = homogeneityFailure(high);
        final double W = 0.5 * (3.0 - Math.sqrt(5.0));
        double x = W * low + (1 - W) * high;
        double midValue = homogeneityFailure(x);

        while ( high - low > tolerance ) {
            if ( x - low > high - x ) {
                // left bigger
                final double tentativeNewMid = W * low + (1 - W) * x;
                final double tentativeNewMidValue = homogeneityFailure(tentativeNewMid);
                final boolean conditioner = testIfSolutionExists(tentativeNewMidValue);
                if ( !conditioner ) {
                    if ( testIfSolutionExists(x) )
                        return x;
                    return leftValue < rightValue ? low : high;
                }
                if ( tentativeNewMidValue < midValue ) {
                    high = x;
                    rightValue = midValue;
                    x = tentativeNewMid;
                    midValue = tentativeNewMidValue;
                } else {
                    low = tentativeNewMid;
                    leftValue = tentativeNewMidValue;
                }
            } else {
                final double tentativeNewMid = W * x + (1 - W) * high;
                final double tentativeNewMidValue = homogeneityFailure(tentativeNewMid);
                final boolean conditioner = testIfSolutionExists(tentativeNewMidValue);
                if ( !conditioner ) {
                    if ( testIfSolutionExists(x) )
                        return x;
                    return leftValue < rightValue ? low : high;
                }
                if ( tentativeNewMidValue < midValue ) {
                    low = x;
                    leftValue = midValue;
                    x = tentativeNewMid;
                    midValue = tentativeNewMidValue;
                } else {
                    high = tentativeNewMid;
                    rightValue = tentativeNewMidValue;
                }
            }
        }
        return x;
    }
}
