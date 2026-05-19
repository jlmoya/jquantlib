/*
 Copyright (C) 2010 Selene Makarios

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

/*
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2007 Marco Bianchetti
 Copyright (C) 2007 Francois du Vignaud
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2006 Mario Pucci
 Copyright (C) 2006 StatPro Italia srl
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.math.interpolations;

import org.jquantlib.QL;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.termstructures.volatilities.Sabr;

/**
 * SABR smile interpolation between discrete volatility points.
 *
 * <p>Phase 2d WI-3 refactor: the per-instance calibration state and the
 * Halton multi-restart loop now live in {@link XABRInterpolationImpl} via the generic {@link XABRSpecs} contract;
 * {@link SABRSpecs} (a static nested class) supplies the SABR-specific dimension, default values, guess synthesis,
 * parameter transformations, and volatility evaluation. This mirrors C++ v1.42.1
 * {@code ql/math/interpolations/sabrinterpolation.hpp} lines 41-149 (the {@code detail::SABRSpecs} struct + the
 * {@code SABRInterpolation} facade).
 *
 * <p>Java single-inheritance forces a delegation bridge: the inner
 * {@link SABRInterpolationImpl} extends {@link AbstractInterpolation.Impl} (preserving the existing
 * {@link Interpolation} pattern) and forwards {@code update()} / {@code op(x)} to a held
 * {@code XABRInterpolationImpl<SABRSpecs>}. C++ achieves the same via multiple inheritance from
 * {@code Interpolation::templateImpl} and {@code XABRCoeffHolder<Model>}.
 *
 * @author Selene Makarios
 */
public class SABRInterpolation extends AbstractInterpolation {

    private final XABRInterpolationImpl< SABRSpecs > xabrImpl_;

    public SABRInterpolation(final Array vx, // x = strikes
            final Array vy, // y = volatilities
            @Time final double t, // option expiry
            final double forward, final double alpha, final double beta, final double nu, final double rho,
            final boolean alphaIsFixed, final boolean betaIsFixed, final boolean nuIsFixed, final boolean rhoIsFixed,
            final boolean vegaWeighted, final EndCriteria endCriteria, final OptimizationMethod optMethod) {
        this(vx, vy, t, forward, alpha, beta, nu, rho, alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, vegaWeighted,
                endCriteria, optMethod, 0.0020 /* errorAccept (C++ default) */, false  /* useMaxError (C++ default) */,
                50     /* maxGuesses  (C++ default) */, 0.0    /* shift       (C++ default) */);
    }

    /**
     * Full-arity constructor mirroring C++ v1.42.1 {@code SABRInterpolation::SABRInterpolation} (sabrinterpolation.hpp
     * lines 152-181) including the {@code errorAccept}, {@code useMaxError}, {@code maxGuesses}, and {@code shift}
     * parameters that drive the Halton multi-restart loop.
     */
    public SABRInterpolation(final Array vx, final Array vy, @Time final double t, final double forward,
            final double alpha, final double beta, final double nu, final double rho, final boolean alphaIsFixed,
            final boolean betaIsFixed, final boolean nuIsFixed, final boolean rhoIsFixed, final boolean vegaWeighted,
            final EndCriteria endCriteria, final OptimizationMethod optMethod, final double errorAccept,
            final boolean useMaxError, final int maxGuesses, final double shift) {
        this(vx, vy, t, forward, alpha, beta, nu, rho, alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, vegaWeighted,
                endCriteria, optMethod, errorAccept, useMaxError, maxGuesses, shift, VolatilityType.ShiftedLognormal);
    }

    /**
     * Full-arity constructor with explicit {@link VolatilityType} — mirrors C++ v1.42.1
     * {@code SABRInterpolation::SABRInterpolation} (sabrinterpolation.hpp lines 152-181) including the {@code shift}
     * and {@code volatilityType} parameters. Pass {@link VolatilityType#Normal} to calibrate against Bachelier (normal)
     * vols; the default is {@link VolatilityType#ShiftedLognormal}.
     */
    public SABRInterpolation(final Array vx, final Array vy, @Time final double t, final double forward,
            final double alpha, final double beta, final double nu, final double rho, final boolean alphaIsFixed,
            final boolean betaIsFixed, final boolean nuIsFixed, final boolean rhoIsFixed, final boolean vegaWeighted,
            final EndCriteria endCriteria, final OptimizationMethod optMethod, final double errorAccept,
            final boolean useMaxError, final int maxGuesses, final double shift, final VolatilityType volatilityType) {

        final double[] xArr = new double[vx.size()];
        final double[] yArr = new double[vy.size()];
        for ( int i = 0; i < vx.size(); ++i )
            xArr[i] = vx.get(i);
        for ( int i = 0; i < vy.size(); ++i )
            yArr[i] = vy.get(i);

        final double[] params = { alpha, beta, nu, rho };
        final boolean[] paramIsFixed = { alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed };
        final double[] addParams = { shift };

        this.xabrImpl_ = new XABRInterpolationImpl< SABRSpecs >(xArr, yArr, t, forward, params, paramIsFixed,
                vegaWeighted, endCriteria, optMethod, errorAccept, useMaxError, maxGuesses, addParams, new SABRSpecs(),
                volatilityType);

        impl = new SABRInterpolationImpl(vx, vy);
    }

    public double expiry() {
        return xabrImpl_.t_;
    }

    public double forward() {
        return xabrImpl_.forward_;
    }

    public double alpha() {
        return xabrImpl_.params_[0];
    }

    public double beta() {
        return xabrImpl_.params_[1];
    }

    public double nu() {
        return xabrImpl_.params_[2];
    }

    public double rho() {
        return xabrImpl_.params_[3];
    }

    public double rmsError() {
        return xabrImpl_.error_;
    }

    public double maxError() {
        return xabrImpl_.maxError_;
    }

    public Array interpolationWeights() {
        final Array w = new Array(xabrImpl_.weights_.length);
        for ( int i = 0; i < xabrImpl_.weights_.length; ++i ) {
            w.set(i, xabrImpl_.weights_[i]);
        }
        return w;
    }

    public EndCriteria.Type endCriteria() {
        return xabrImpl_.XABREndCriteria_;
    }

    /** Accessor for the held XABR impl (test plumbing / introspection). */
    public XABRInterpolationImpl< SABRSpecs > xabrImpl() {
        return xabrImpl_;
    }

    /**
     * SABR Model concept (mirrors C++ {@code detail::SABRSpecs} struct in
     * {@code ql/math/interpolations/sabrinterpolation.hpp} lines 65-149).
     *
     * <p>Stateless — every method takes its inputs as parameters so a single
     * instance can be shared across calls. This matches the C++ default- constructed {@code Model()} usage.
     */
    public static final class SABRSpecs implements XABRSpecs {

        private static final double EPS1 = 1e-7;
        private static final double EPS2 = 0.9999;

        @Override
        public int dimension() {
            return 4;
        }

        @Override
        public void defaultValues(final double[] params, final boolean[] paramIsFixed, final double forward,
                final double t, final double[] addParams) {
            // Mirrors C++ sabrinterpolation.hpp lines 67-82. Order is critical:
            // beta first (alpha default is forward+shift-aware in beta), then
            // alpha, then nu, then rho. Sentinel = Constants.NULL_REAL
            // (Double.MAX_VALUE), the QuantLib Null<Real>.
            final double shift = (addParams != null && addParams.length > 0) ? addParams[0] : 0.0;
            if ( params[1] == Constants.NULL_REAL ) {
                params[1] = 0.5;
            }
            if ( params[0] == Constants.NULL_REAL ) {
                // adapt alpha to beta level
                params[0] = 0.2 * ((params[1] < 0.9999) ? JQuantMath.pow(forward + shift, 1.0 - params[1]) : 1.0);
            }
            if ( params[2] == Constants.NULL_REAL ) {
                params[2] = Math.sqrt(0.4);
            }
            if ( params[3] == Constants.NULL_REAL ) {
                params[3] = 0.0;
            }
        }

        @Override
        public void guess(final double[] values, final boolean[] paramIsFixed, final double forward, final double t,
                final double[] sampleValue, final double[] addParams) {
            // Mirrors C++ sabrinterpolation.hpp lines 84-99: use the next
            // Halton sample to build a fresh per-restart guess. Note: 'j' is
            // the running index into sampleValue and is incremented only when
            // the corresponding parameter is NOT fixed (matches C++ exactly).
            final double shift = (addParams != null && addParams.length > 0) ? addParams[0] : 0.0;
            int j = 0;
            if ( !paramIsFixed[1] ) {
                values[1] = (1.0 - 2e-6) * sampleValue[j++] + 1e-6;
            }
            if ( !paramIsFixed[0] ) {
                values[0] = (1.0 - 2e-6) * sampleValue[j++] + 1e-6; // lognormal vol guess
                // adapt this to beta level
                if ( values[1] < 0.999 ) {
                    values[0] *= JQuantMath.pow(forward + shift, 1.0 - values[1]);
                }
            }
            if ( !paramIsFixed[2] ) {
                values[2] = 1.5 * sampleValue[j++] + 1e-6;
            }
            if ( !paramIsFixed[3] ) {
                values[3] = (2.0 * sampleValue[j++] - 1.0) * (1.0 - 1e-6);
            }
        }

        @Override
        public Array inverse(final Array y, final boolean[] paramIsFixed, final double[] params, final double forward) {
            // Mirrors C++ sabrinterpolation.hpp lines 104-114. Branched
            // formulas guard against domain errors when the unconstrained
            // params drift outside the standard transformation domain.
            final Array x = new Array(4);
            x.set(0, y.get(0) < 25.0 + EPS1 ? Math.sqrt(y.get(0) - EPS1) : (y.get(0) - EPS1 + 25.0) / 10.0);
            x.set(1, Math.sqrt(-Math.log(y.get(1))));
            x.set(2, y.get(2) < 25.0 + EPS1 ? Math.sqrt(y.get(2) - EPS1) : (y.get(2) - EPS1 + 25.0) / 10.0);
            x.set(3, Math.asin(y.get(3) / EPS2));
            return x;
        }

        @Override
        public Array direct(final Array x, final boolean[] paramIsFixed, final double[] params, final double forward) {
            // Mirrors C++ sabrinterpolation.hpp lines 116-129.
            final Array y = new Array(4);
            y.set(0, Math.abs(x.get(0)) < 5.0 ? x.get(0) * x.get(0) + EPS1 : (10.0 * Math.abs(x.get(0)) - 25.0) + EPS1);
            y.set(1, Math.abs(x.get(1)) < Math.sqrt(-Math.log(EPS1)) ? Math.exp(-(x.get(1) * x.get(1))) : EPS1);
            y.set(2, Math.abs(x.get(2)) < 5.0 ? x.get(2) * x.get(2) + EPS1 : (10.0 * Math.abs(x.get(2)) - 25.0) + EPS1);
            y.set(3, Math.abs(x.get(3)) < 2.5 * Math.PI
                    ? EPS2 * Math.sin(x.get(3))
                    : EPS2 * (x.get(3) > 0.0 ? 1.0 : -1.0));
            return y;
        }

        @Override
        public double volatility(final double strike, final double forward, final double t, final double[] params) {
            // The C++ template routes this through SABRWrapper, which calls
            // shiftedSabrVolatility with the addParams[0] shift. The Java
            // Sabr port is the unshifted formula; the standard SABR shift is
            // not used by any current Java caller (always passed as 0.0), so
            // we route directly to sabrVolatility.
            return new Sabr().sabrVolatility(strike, forward, t, params[0], params[1], params[2], params[3]);
        }

        /**
         * Vol-type-aware volatility dispatch. Mirrors C++ {@code SABRWrapper::volatility(x, volatilityType)}
         * (sabrinterpolation.hpp lines 53-56), which calls
         * {@code shiftedSabrVolatility(x, forward_, t_, alpha, beta, nu, rho, shift_, volatilityType)}.
         *
         * <p>The caller ({@link XABRInterpolationImpl#value(double)}) has already
         * pre-shifted {@code strike} and {@code forward} by {@code addParams[0]}, so here we route to the appropriate
         * unshifted formula based on the volatility type — {@link Sabr#unsafeSabrLogNormalVolatility} for
         * {@link VolatilityType#ShiftedLognormal}, {@link Sabr#unsafeSabrNormalVolatility} for
         * {@link VolatilityType#Normal}.
         */
        @Override
        public double volatility(final double strike, final double forward, final double t, final double[] params,
                final double[] addParams, final VolatilityType vt) {
            final Sabr sabr = new Sabr();
            if ( vt == VolatilityType.Normal ) {
                return sabr.unsafeSabrNormalVolatility(strike, forward, t, params[0], params[1], params[2], params[3]);
            }
            return sabr.unsafeSabrLogNormalVolatility(strike, forward, t, params[0], params[1], params[2], params[3]);
        }

        @Override
        public Constraint constraint(final double forward) {
            // C++ uses NoConstraint (xabrinterpolation.hpp line 208); the
            // SABR transformation handles parameter feasibility via direct().
            return new NoConstraint();
        }

        @Override
        public double weight(final double strike, final double forward, final double stdDev, final double[] addParams) {
            // C++ uses blackFormulaStdDevDerivative(..., 1.0, addParams[0]);
            // the Java overload with shift takes (strike, forward, stddev,
            // discount, displacement). Pass shift via the Java displacement
            // parameter to match.
            final double shift = (addParams != null && addParams.length > 0) ? addParams[0] : 0.0;
            return BlackFormula.blackFormulaStdDevDerivative(strike, forward, stdDev, 1.0, shift);
        }
    }

    /**
     * Inner impl bridging {@link AbstractInterpolation.Impl} (which the Java {@link Interpolation} contract requires)
     * to the standalone {@link XABRInterpolationImpl} that holds the calibration state. Java single inheritance
     * prevents the C++-style multi-inheritance directly.
     */
    private final class SABRInterpolationImpl extends AbstractInterpolation.Impl {

        SABRInterpolationImpl(final Array vx, final Array vy) {
            super(vx, vy);
        }

        @Override
        public void update() {
            QL.require(xabrImpl_.forward_ > 0.0,
                    "at the money forward rate must be positive: " + xabrImpl_.forward_ + " not allowed");
            xabrImpl_.calculate();
        }

        @Override
        public double op(final double x) {
            // For shifted SABR (addParams_[0] > 0), raw strike may be negative as long as
            // strike + shift > 0. Mirrors C++ xabrinterpolation.hpp (no explicit positivity
            // guard; the shifted formula requires strike+shift > 0 for log-normal SABR).
            // Phase 2o A.2: relax from x > 0 to x + shift > 0 to support negative raw strikes.
            final double shift = (xabrImpl_.addParams_.length > 0) ? xabrImpl_.addParams_[0] : 0.0;
            QL.require(x + shift > 0.0,
                    "shifted strike (strike+shift) must be positive: " + x + " + " + shift + " not allowed");
            return xabrImpl_.value(x);
        }

        @Override
        public double primitive(final double x) {
            throw new LibraryException("SABR primitive not implemented");
        }

        @Override
        public double derivative(final double x) {
            throw new LibraryException("SABR derivative not implemented");
        }

        @Override
        public double secondDerivative(final double x) {
            throw new LibraryException("SABR secondDerivative not implemented");
        }
    }
}
