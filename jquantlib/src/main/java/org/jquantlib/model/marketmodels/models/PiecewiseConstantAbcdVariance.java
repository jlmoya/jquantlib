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
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.termstructures.volatility.AbcdFunction;

/**
 * Piecewise-constant ABCD-form variance for caplet calibration.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/piecewiseconstantabcdvariance.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Variance over each piecewise interval [rateTimes[i-1], rateTimes[i]] is
 * computed via {@link AbcdFunction#variance(double, double, double)} of
 * the T-fixing rate at {@code rateTimes[resetIndex]}.
 *
 * <p>Phase 3j B.2 (Track B).
 */
public class PiecewiseConstantAbcdVariance extends PiecewiseConstantVariance {

    private final double[] variances_;
    private final double[] volatilities_;
    private final double[] rateTimes_;
    private final double a_, b_, c_, d_;

    public PiecewiseConstantAbcdVariance(final double a, final double b,
                                         final double c, final double d,
                                         final int resetIndex,
                                         final double[] rateTimes) {
        Utilities.checkIncreasingTimes(rateTimes);
        QL.require(rateTimes.length > 1, "Rate times must contain at least two values");
        QL.require(resetIndex < rateTimes.length - 1,
                "resetIndex (" + resetIndex + ") must be less than rateTimes.size()-1 ("
                        + (rateTimes.length - 1) + ")");

        this.rateTimes_ = rateTimes.clone();
        this.a_ = a;
        this.b_ = b;
        this.c_ = c;
        this.d_ = d;
        this.variances_ = new double[rateTimes.length - 1];
        this.volatilities_ = new double[rateTimes.length - 1];

        final AbcdFunction abcd = new AbcdFunction(a, b, c, d);
        for (int i = 0; i <= resetIndex; ++i) {
            final double startTime = (i == 0) ? 0.0 : rateTimes_[i - 1];
            variances_[i] = abcd.variance(startTime, rateTimes_[i], rateTimes_[resetIndex]);
            final double totTime = rateTimes_[i] - startTime;
            volatilities_[i] = Math.sqrt(variances_[i] / totTime);
        }
    }

    /** Copy constructor — used by VolatilityInterpolationSpecifierAbcd. */
    public PiecewiseConstantAbcdVariance(final PiecewiseConstantAbcdVariance other) {
        this.a_ = other.a_;
        this.b_ = other.b_;
        this.c_ = other.c_;
        this.d_ = other.d_;
        this.rateTimes_ = other.rateTimes_.clone();
        this.variances_ = other.variances_.clone();
        this.volatilities_ = other.volatilities_.clone();
    }

    @Override public double[] variances() { return variances_; }
    @Override public double[] volatilities() { return volatilities_; }
    @Override public double[] rateTimes() { return rateTimes_; }

    /** Returns the (a,b,c,d) parameters into a length-4 output array. */
    public void getABCD(final double[] out) {
        QL.require(out != null && out.length == 4, "out must be double[4]");
        out[0] = a_;
        out[1] = b_;
        out[2] = c_;
        out[3] = d_;
    }
}
