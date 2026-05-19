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

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.*;
import org.jquantlib.math.randomnumbers.HaltonRsg;
import org.jquantlib.model.VolatilityType;

/**
 * Generic XABR-style interpolation implementation, parameterized by {@link XABRSpecs} for model-specific behaviour
 * (SABR, no-arbitrage SABR, ZABR, ...).
 *
 * <p>Faithful port of QuantLib C++ v1.42.1
 * {@code ql/math/interpolations/xabrinterpolation.hpp} lines 100-321 (the {@code XABRInterpolationImpl<I1, I2, Model>}
 * template).
 *
 * <p><b>Phase 2d structural port:</b> the C++ class extends
 * {@code Interpolation::templateImpl<I1, I2>} so it can be plugged directly into the {@code Interpolation} hierarchy.
 * The Java {@link AbstractInterpolation.Impl} pattern is single-class-bound (the impl is an inner of an
 * {@link AbstractInterpolation}), which doesn't allow a generic standalone class to extend it cleanly. For now this
 * class is a standalone bearer of the Halton restart loop and calibration state; the {@link Interpolation}-level
 * integration will be performed by the next chunk's {@code SABRInterpolation} refactor (Phase 2d C.6+), which will
 * delegate to an instance of this class.
 *
 * @param <S> XABR specs type
 */
public class XABRInterpolationImpl< S extends XABRSpecs > extends XABRCoeffHolder< S > {

    /** Strikes (mirrors C++ {@code xBegin_..xEnd_}). */
    protected final double[] xBegin_;

    /** Vols (mirrors C++ {@code yBegin_..yEnd_}). */
    protected final double[] yBegin_;

    protected final boolean vegaWeighted_;
    protected final double errorAccept_;
    protected final boolean useMaxError_;
    protected final int maxGuesses_;
    /**
     * Volatility type carried by the parent interpolator (mirrors C++ {@code XABRInterpolationImpl::volatilityType_}
     * field at xabrinterpolation.hpp line 320). Defaults to {@link VolatilityType#ShiftedLognormal}; passed down to
     * {@link XABRSpecs#volatility(double, double, double, double[], double[], VolatilityType)} by
     * {@link #value(double)}.
     */
    protected final VolatilityType volatilityType_;
    protected EndCriteria endCriteria_;
    protected OptimizationMethod optMethod_;

    /**
     * Backward-compatible constructor — defaults {@code volatilityType} to {@link VolatilityType#ShiftedLognormal}
     * (matching C++ default arg at xabrinterpolation.hpp line 118).
     */
    public XABRInterpolationImpl(final double[] xBegin, final double[] yBegin, final double t, final double forward,
            final double[] params, final boolean[] paramIsFixed, final boolean vegaWeighted,
            final EndCriteria endCriteria, final OptimizationMethod optMethod, final double errorAccept,
            final boolean useMaxError, final int maxGuesses, final double[] addParams, final S specs) {
        this(xBegin, yBegin, t, forward, params, paramIsFixed, vegaWeighted, endCriteria, optMethod, errorAccept,
                useMaxError, maxGuesses, addParams, specs, VolatilityType.ShiftedLognormal);
    }

    /**
     * Full-arity constructor with explicit {@link VolatilityType}. Mirrors C++ {@code XABRInterpolationImpl} ctor
     * (xabrinterpolation.hpp lines 105-135) including the {@code volatilityType} parameter (line 118).
     */
    public XABRInterpolationImpl(final double[] xBegin, final double[] yBegin, final double t, final double forward,
            final double[] params, final boolean[] paramIsFixed, final boolean vegaWeighted,
            final EndCriteria endCriteria, final OptimizationMethod optMethod, final double errorAccept,
            final boolean useMaxError, final int maxGuesses, final double[] addParams, final S specs,
            final VolatilityType volatilityType) {
        super(t, forward, params, paramIsFixed, addParams, specs);
        this.xBegin_ = xBegin.clone();
        this.yBegin_ = yBegin.clone();
        this.vegaWeighted_ = vegaWeighted;
        this.volatilityType_ = (volatilityType != null) ? volatilityType : VolatilityType.ShiftedLognormal;
        // C++ semantic (xabrinterpolation.hpp lines 125-133): the caller-
        // supplied optMethod / endCriteria are used as-is; only when null do
        // we assign defaults.
        this.optMethod_ = (optMethod != null) ? optMethod : new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
        this.endCriteria_ = (endCriteria != null) ? endCriteria : new EndCriteria(60000, 100, 1e-8, 1e-8, 1e-8);
        this.errorAccept_ = errorAccept;
        this.useMaxError_ = useMaxError;
        this.maxGuesses_ = maxGuesses;
        this.weights_ = new double[xBegin.length];
        final double w = 1.0 / xBegin.length;
        for ( int i = 0; i < xBegin.length; ++i ) {
            this.weights_[i] = w;
        }
    }

    /**
     * Halton multi-restart calibration loop. Mirrors C++ {@code XABRInterpolationImpl::update()} (xabrinterpolation.hpp
     * lines 138-236). Java exposes it as {@code calculate()} since this class is not yet integrated into the
     * {@link Interpolation} hierarchy.
     */
    public void calculate() {
        // 1) Update vega weights if vegaWeighted_ (C++ lines 144-160).
        if ( vegaWeighted_ ) {
            this.weights_ = new double[xBegin_.length];
            double weightsSum = 0.0;
            for ( int i = 0; i < xBegin_.length; ++i ) {
                final double y = yBegin_[i];
                final double stdDev = Math.sqrt(y * y * t_);
                weights_[i] = specs_.weight(xBegin_[i], forward_, stdDev, addParams_);
                weightsSum += weights_[i];
            }
            for ( int i = 0; i < weights_.length; ++i ) {
                weights_[i] /= weightsSum;
            }
        }

        // 2) "Nothing to optimize" shortcut (C++ lines 162-169) — when every
        //    parameter is fixed.
        boolean allFixed = true;
        for ( final boolean f : paramIsFixed_ ) {
            if ( !f ) {
                allFixed = false;
                break;
            }
        }
        if ( allFixed ) {
            error_ = interpolationError();
            maxError_ = interpolationMaxError();
            XABREndCriteria_ = EndCriteria.Type.None;
            return;
        }

        // 3) Halton multi-restart loop (C++ lines 170-235).
        final XABRError costFunction = new XABRError(this);

        final Array guess = new Array(specs_.dimension());
        for ( int i = 0; i < specs_.dimension(); ++i ) {
            guess.set(i, params_[i]);
        }

        int iterations = 0;
        int freeParameters = 0;
        for ( final boolean f : paramIsFixed_ ) {
            if ( !f )
                ++freeParameters;
        }
        double bestError = Double.POSITIVE_INFINITY;
        Array bestParameters = null;
        final HaltonRsg halton = new HaltonRsg(freeParameters, 42L, true, false);
        EndCriteria.Type tmpEndCriteria;
        double tmpInterpolationError;

        do {
            // C++ lines 188-196: only the second (and later) iterations
            // refresh the guess via Model().guess(...) using the next Halton
            // sample. The first iteration uses the params populated by
            // Model().defaultValues(...) in XABRCoeffHolder's ctor.
            if ( iterations > 0 ) {
                final HaltonRsg.Sample s = halton.nextSequence();
                final double[] guessArr = new double[specs_.dimension()];
                for ( int i = 0; i < specs_.dimension(); ++i ) {
                    guessArr[i] = guess.get(i);
                }
                specs_.guess(guessArr, paramIsFixed_, forward_, t_, s.value, addParams_);
                for ( int i = 0; i < paramIsFixed_.length; ++i ) {
                    if ( paramIsFixed_[i] ) {
                        guessArr[i] = params_[i];
                    }
                }
                for ( int i = 0; i < specs_.dimension(); ++i ) {
                    guess.set(i, guessArr[i]);
                }
            }

            final Array inversedTransformatedGuess = specs_.inverse(guess, paramIsFixed_, params_, forward_);

            final ProjectedCostFunction constrainedXABRError = new ProjectedCostFunction(costFunction,
                    inversedTransformatedGuess, paramIsFixed_);

            final Array projectedGuess = constrainedXABRError.project(inversedTransformatedGuess);

            final Constraint constraint = new NoConstraint();
            final Problem problem = new Problem(constrainedXABRError, constraint, projectedGuess);
            tmpEndCriteria = optMethod_.minimize(problem, endCriteria_);
            final Array projectedResult = problem.currentValue();
            final Array transfResult = constrainedXABRError.include(projectedResult);

            final Array result = specs_.direct(transfResult, paramIsFixed_, params_, forward_);
            tmpInterpolationError = useMaxError_ ? interpolationMaxError() : interpolationError();

            if ( tmpInterpolationError < bestError ) {
                bestError = tmpInterpolationError;
                bestParameters = result;
                XABREndCriteria_ = tmpEndCriteria;
            }
        } while ( ++iterations < maxGuesses_ && tmpInterpolationError > errorAccept_ );

        if ( bestParameters != null ) {
            for ( int i = 0; i < bestParameters.size(); ++i ) {
                params_[i] = bestParameters.get(i);
            }
        }
        error_ = interpolationError();
        maxError_ = interpolationMaxError();
    }

    /**
     * Volatility evaluation at a single strike (Java analogue of C++ {@code value(x)}).
     *
     * <p>For shifted XABR models (addParams_[0] = shift > 0), C++ routes through
     * {@code shiftedSabrVolatility} which internally calls {@code sabrVolatility(strike+shift, forward+shift, ...)}.
     * Java pre-applies the shift here so {@link XABRSpecs#volatility} always receives the shifted (positive) strike,
     * matching C++ semantics (Phase 2o A.2).
     */
    public double value(final double x) {
        final double shift = (addParams_ != null && addParams_.length > 0) ? addParams_[0] : 0.0;
        // Phase 5e.5b-CFC-d-262: route volatilityType_ to the specs so SABR
        // (and other vol-type-aware specs) can dispatch to the right formula
        // (lognormal vs Normal). Default impl in XABRSpecs delegates to the
        // 4-arg overload, so non-aware specs are unaffected.
        return specs_.volatility(x + shift, forward_ + shift, t_, params_, addParams_, volatilityType_);
    }

    /** Total weighted squared error (C++ {@code interpolationSquaredError()} lines 245-255). */
    public double interpolationSquaredError() {
        double totalError = 0.0;
        for ( int i = 0; i < xBegin_.length; ++i ) {
            final double diff = value(xBegin_[i]) - yBegin_[i];
            totalError += diff * diff * weights_[i];
        }
        return totalError;
    }

    /** Per-strike weighted residual vector (C++ {@code interpolationErrors()} lines 258-268). */
    public Array interpolationErrors() {
        final Array results = new Array(xBegin_.length);
        for ( int i = 0; i < xBegin_.length; ++i ) {
            results.set(i, (value(xBegin_[i]) - yBegin_[i]) * Math.sqrt(weights_[i]));
        }
        return results;
    }

    /** RMS error (C++ {@code interpolationError()} lines 270-274). */
    public double interpolationError() {
        final int n = xBegin_.length;
        final double squaredError = interpolationSquaredError();
        return Math.sqrt(n * squaredError / (n == 1 ? 1 : (n - 1)));
    }

    /** Max absolute error (C++ {@code interpolationMaxError()} lines 276-285). */
    public double interpolationMaxError() {
        double maxError = -Double.MAX_VALUE;
        for ( int i = 0; i < xBegin_.length; ++i ) {
            final double error = Math.abs(value(xBegin_[i]) - yBegin_[i]);
            if ( error > maxError )
                maxError = error;
        }
        return maxError;
    }

    /**
     * Internal cost function: applies {@link XABRSpecs#direct} to the unconstrained parameter vector, writes back to
     * {@code params_}, and returns either the squared error (scalar API) or per-strike residuals (least-squares API).
     * Mirrors C++ inner class {@code XABRError} (xabrinterpolation.hpp lines 288-312).
     */
    private static final class XABRError extends CostFunction {
        private final XABRInterpolationImpl< ? > xabr_;

        XABRError(final XABRInterpolationImpl< ? > xabr) {
            this.xabr_ = xabr;
        }

        @Override
        public double value(final Array x) {
            applyDirect(x);
            return xabr_.interpolationSquaredError();
        }

        @Override
        public Array values(final Array x) {
            applyDirect(x);
            return xabr_.interpolationErrors();
        }

        @SuppressWarnings( { "unchecked", "rawtypes" } )
        private void applyDirect(final Array x) {
            // Erased-generic dance: we know specs is XABRSpecs, but the
            // wildcard ? prevents direct method use. Cast through raw type.
            final XABRSpecs specs = xabr_.specs_;
            final Array y = specs.direct(x, xabr_.paramIsFixed_, xabr_.params_, xabr_.forward_);
            for ( int i = 0; i < xabr_.params_.length; ++i ) {
                xabr_.params_[i] = y.get(i);
            }
        }
    }
}
