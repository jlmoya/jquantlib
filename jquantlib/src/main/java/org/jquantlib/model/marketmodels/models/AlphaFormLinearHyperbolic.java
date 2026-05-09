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

/**
 * Linear-hyperbolic alpha form:
 * <pre>
 *   at = alpha * times[i]
 *   value = sqrt(1 + at * (atan(at) - 0.5 * pi))
 * </pre>
 *
 * <p>Java port of {@code AlphaFormLinearHyperbolic} from
 * {@code ql/models/marketmodels/models/alphaformconcrete.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Phase 3j B.1 (Track B).
 */
public final class AlphaFormLinearHyperbolic implements AlphaForm {

    private final double[] times_;
    private double alpha_;

    public AlphaFormLinearHyperbolic(final double[] times, final double alpha) {
        this.times_ = times.clone();
        this.alpha_ = alpha;
    }

    public AlphaFormLinearHyperbolic(final double[] times) {
        this(times, 0.0);
    }

    @Override
    public double apply(final int i) {
        final double at = alpha_ * times_[i];
        double res = Math.atan(at) - 0.5 * Math.PI;
        res *= at;
        res += 1.0;
        res = Math.sqrt(res);
        return res;
    }

    @Override
    public void setAlpha(final double alpha) {
        this.alpha_ = alpha;
    }
}
