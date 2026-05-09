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
 * Inverse-linear alpha form: {@code 1 / (1 + alpha * times[i])}.
 *
 * <p>Java port of {@code AlphaFormInverseLinear} from
 * {@code ql/models/marketmodels/models/alphaformconcrete.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Phase 3j B.1 (Track B).
 */
public final class AlphaFormInverseLinear implements AlphaForm {

    private final double[] times_;
    private double alpha_;

    public AlphaFormInverseLinear(final double[] times, final double alpha) {
        this.times_ = times.clone();
        this.alpha_ = alpha;
    }

    public AlphaFormInverseLinear(final double[] times) {
        this(times, 0.0);
    }

    @Override
    public double apply(final int i) {
        return 1.0 / (1.0 + alpha_ * times_[i]);
    }

    @Override
    public void setAlpha(final double alpha) {
        this.alpha_ = alpha;
    }
}
