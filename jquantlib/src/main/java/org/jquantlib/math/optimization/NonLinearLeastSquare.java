/*
 Copyright (C) 2001, 2002, 2003 Nicolas Di Césaré
 Copyright (C) 2005 StatPro Italia srl
 Copyright (C) 2009 Ueli Hofstetter
 Copyright (C) 2026 Jose Moya (Java port update)

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.

 JQuantLib is based on QuantLib. http://quantlib.org/
 */
package org.jquantlib.math.optimization;

import org.jquantlib.math.matrixutilities.Array;

//-- class NonLinearLeastSquare; in ql/math/optimization/leastsquare.hpp:97
/**
 * Non-linear least-square method. Given an optimization algorithm (default
 * is {@link ConjugateGradient}), solves {@code min { r(x) : x in R^n }} where
 * {@code r(x) = |f(x)|^2} is the Euclidean norm of the residual.
 *
 * <p>Port of QuantLib C++ v1.42.1 {@code NonLinearLeastSquare}.
 */
public class NonLinearLeastSquare {

    //! solution vector
    private Array results_;
    private Array initialValue_;
    //! least square residual norm
    private double resnorm_;
    //! Exit flag of the optimization process
    private int exitFlag_;
    //! required accuracy of the solver
    private final double accuracy_;
    private double bestAccuracy_;
    //! maximum and real number of iterations
    private final int maxIterations_;
    private int nbIterations_;
    //! Optimization method
    private final OptimizationMethod om_;
    //! constraint
    private final Constraint c_;

    //-- NonLinearLeastSquare(Constraint& c, Real accuracy = 1e-4, Size maxiter = 100);
    //-- in ql/math/optimization/leastsquare.hpp:100 / .cpp:79
    public NonLinearLeastSquare(final Constraint c, final double accuracy, final int maxiter) {
        this.exitFlag_ = -1;
        this.accuracy_ = accuracy;
        this.maxIterations_ = maxiter;
        this.om_ = new ConjugateGradient(null);
        this.c_ = c;
    }

    public NonLinearLeastSquare(final Constraint c) {
        this(c, 1e-4, 100);
    }

    //-- NonLinearLeastSquare(Constraint& c, Real accuracy, Size maxiter,
    //--                      ext::shared_ptr<OptimizationMethod> om);
    //-- in ql/math/optimization/leastsquare.hpp:104 / .cpp:87
    public NonLinearLeastSquare(final Constraint c, final double accuracy,
                                final int maxiter, final OptimizationMethod om) {
        this.exitFlag_ = -1;
        this.accuracy_ = accuracy;
        this.maxIterations_ = maxiter;
        this.om_ = om;
        this.c_ = c;
    }

    //-- Array& NonLinearLeastSquare::perform(LeastSquareProblem& lsProblem);
    //-- in ql/math/optimization/leastsquare.cpp:93
    public Array perform(final LeastSquareProblem lsProblem) {
        final double eps = accuracy_;
        final LeastSquareFunction lsf = new LeastSquareFunction(lsProblem);
        final Problem P = new Problem(lsf, c_, initialValue_);
        final EndCriteria ec = new EndCriteria(
                maxIterations_,
                Math.min(maxIterations_ / 2, 100),
                eps, eps, eps);
        exitFlag_ = om_.minimize(P, ec).ordinal();

        results_ = P.currentValue();
        resnorm_ = P.functionValue();
        bestAccuracy_ = P.functionValue();
        return results_;
    }

    public void setInitialValue(final Array initialValue) {
        this.initialValue_ = initialValue;
    }

    /** @return the results vector. */
    public Array results() { return results_; }

    /** @return the least-square residual norm. */
    public double residualNorm() { return resnorm_; }

    /** @return last function value (best accuracy seen). */
    public double lastValue() { return bestAccuracy_; }

    /** @return exit flag of the optimization process. */
    public int exitFlag() { return exitFlag_; }

    /** @return the performed number of iterations. */
    public int iterationsNumber() { return nbIterations_; }
}
