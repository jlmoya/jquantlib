/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.math.interpolations;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.optimization.EndCriteria;

/**
 * Holder of XABR-family calibration coefficients and per-instance state.
 *
 * <p>Faithful port of QuantLib C++ v1.42.1
 * {@code ql/math/interpolations/xabrinterpolation.hpp} lines 51-98
 * (the {@code XABRCoeffHolder<Model>} template). The C++ template parameter
 * {@code Model} is replaced by a generic type {@code S} that extends
 * {@link XABRSpecs}; an instance of {@code S} is held in {@link #specs_}
 * (rather than being default-constructed on demand as the C++ code does via
 * {@code Model()}).
 *
 * <p>Sentinel handling mirrors the C++ contract: a parameter equal to
 * {@link Constants#NULL_REAL} (i.e. {@code Double.MAX_VALUE}, the QuantLib
 * "Null&lt;Real&gt;") is treated as "not supplied", which means the
 * corresponding {@code paramIsFixed} flag is forced to {@code false} so that
 * {@link XABRSpecs#defaultValues(double[], boolean[], double, double, double[])}
 * can populate it.
 *
 * @param <S> XABR specs type (e.g. SABR, no-arbitrage SABR, ZABR)
 */
public abstract class XABRCoeffHolder<S extends XABRSpecs> {

    /** Expiry. */
    public final double t_;

    /** Forward rate. */
    public final double forward_;

    /** Calibrated parameters. */
    public final double[] params_;

    /** Fixed-parameter flags (matching {@link #params_} length). */
    public final boolean[] paramIsFixed_;

    /** Per-strike weights (filled by the impl). */
    public double[] weights_;

    /** Calibration RMS error. */
    public double error_;

    /** Calibration max absolute error. */
    public double maxError_;

    /** Optimizer end criteria. */
    public EndCriteria.Type XABREndCriteria_ = EndCriteria.Type.None;

    /** Additional model-specific parameters. */
    public final double[] addParams_;

    /** Model-specific specs (replaces C++ {@code Model()} default-construction). */
    protected final S specs_;

    protected XABRCoeffHolder(final double t, final double forward,
            final double[] params, final boolean[] paramIsFixed,
            final double[] addParams, final S specs) {
        QL.require(t > 0.0, "expiry time must be positive: " + t + " not allowed");
        QL.require(params.length == specs.dimension(),
                "wrong number of parameters (" + params.length
                        + "), should be " + specs.dimension());
        QL.require(paramIsFixed.length == specs.dimension(),
                "wrong number of fixed parameters flags ("
                        + paramIsFixed.length + "), should be "
                        + specs.dimension());

        this.t_ = t;
        this.forward_ = forward;
        this.params_ = params.clone();
        this.paramIsFixed_ = new boolean[specs.dimension()];
        // C++ sets paramIsFixed_[i] only when params[i] != Null<Real>
        // (xabrinterpolation.hpp lines 71-74). The Null<Real> sentinel is
        // QuantLib's Double.MAX_VALUE, exposed here as Constants.NULL_REAL.
        for (int i = 0; i < params.length; ++i) {
            if (params[i] != Constants.NULL_REAL) {
                this.paramIsFixed_[i] = paramIsFixed[i];
            }
        }
        this.addParams_ = (addParams != null) ? addParams.clone() : new double[0];
        this.specs_ = specs;
        this.error_ = Constants.NULL_REAL;
        this.maxError_ = Constants.NULL_REAL;
        specs_.defaultValues(this.params_, this.paramIsFixed_,
                this.forward_, this.t_, this.addParams_);
    }

    /** Specs accessor (the Java analogue of C++ {@code Model()}). */
    public S specs() {
        return specs_;
    }
}
