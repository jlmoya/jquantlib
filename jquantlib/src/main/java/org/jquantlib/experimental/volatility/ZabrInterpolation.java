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

/*
 Copyright (C) 2014 Peter Caspers
 Copyright (C) 2026 Aaditya Panikath

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.AbstractInterpolation;
import org.jquantlib.math.interpolations.XABRInterpolationImpl;
import org.jquantlib.math.interpolations.XABRSpecs;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.pricingengines.BlackFormula;

/**
 * ZABR smile interpolation between discrete volatility points.
 *
 * <p>Port of QuantLib v1.42.1
 * {@code ql/math/interpolations/zabrinterpolation.hpp}. The C++ class is a template
 * {@code ZabrInterpolation<Evaluation>}; this Java port pins the evaluation tag to
 * {@link ZabrSmileSection.Evaluation#ShortMaturityLognormal} (the only flavor exercised by the swaption-vol cube test
 * suite). Alternate kernels can be selected by constructing the cube with a different
 * {@link ZabrSmileSection.Evaluation} (the kernel only affects the smile- section evaluation, not the calibration which
 * is always Hagan-style).
 *
 * <p>Backing infrastructure: holds an
 * {@link XABRInterpolationImpl}{@code <ZabrSpecs>}; calibration semantics are identical to {@code SABRInterpolation}
 * apart from the 5th parameter {@code gamma} and the custom direct/inverse transformations defined in
 * {@link ZabrSpecs}.
 *
 * <p><b>Phase 5e.5b-CFC-d-264.</b>
 */
public class ZabrInterpolation extends AbstractInterpolation {

    private final XABRInterpolationImpl< ZabrSpecs > xabrImpl_;

    public ZabrInterpolation(final Array vx, final Array vy, final double t, final double forward, final double alpha,
            final double beta, final double nu, final double rho, final double gamma, final boolean alphaIsFixed,
            final boolean betaIsFixed, final boolean nuIsFixed, final boolean rhoIsFixed, final boolean gammaIsFixed,
            final boolean vegaWeighted, final EndCriteria endCriteria, final OptimizationMethod optMethod) {
        this(vx, vy, t, forward, alpha, beta, nu, rho, gamma, alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed,
                gammaIsFixed, vegaWeighted, endCriteria, optMethod, 0.0020 /* errorAccept (C++ default) */,
                false  /* useMaxError */, 50     /* maxGuesses */);
    }

    public ZabrInterpolation(final Array vx, final Array vy, final double t, final double forward, final double alpha,
            final double beta, final double nu, final double rho, final double gamma, final boolean alphaIsFixed,
            final boolean betaIsFixed, final boolean nuIsFixed, final boolean rhoIsFixed, final boolean gammaIsFixed,
            final boolean vegaWeighted, final EndCriteria endCriteria, final OptimizationMethod optMethod,
            final double errorAccept, final boolean useMaxError, final int maxGuesses) {

        final double[] xArr = new double[vx.size()];
        final double[] yArr = new double[vy.size()];
        for ( int i = 0; i < vx.size(); ++i )
            xArr[i] = vx.get(i);
        for ( int i = 0; i < vy.size(); ++i )
            yArr[i] = vy.get(i);

        final double[] params = { alpha, beta, nu, rho, gamma };
        final boolean[] paramIsFixed = { alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, gammaIsFixed };

        this.xabrImpl_ = new XABRInterpolationImpl< ZabrSpecs >(xArr, yArr, t, forward, params, paramIsFixed,
                vegaWeighted, endCriteria, optMethod, errorAccept, useMaxError, maxGuesses, new double[0],
                new ZabrSpecs());

        impl = new ZabrInterpolationImpl(vx, vy);
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

    public double gamma() {
        return xabrImpl_.params_[4];
    }

    public double rmsError() {
        return xabrImpl_.error_;
    }

    public double maxError() {
        return xabrImpl_.maxError_;
    }

    public EndCriteria.Type endCriteria() {
        return xabrImpl_.XABREndCriteria_;
    }

    public XABRInterpolationImpl< ZabrSpecs > xabrImpl() {
        return xabrImpl_;
    }

    /**
     * ZABR Model concept (mirrors C++ {@code detail::ZabrSpecs}). 5 parameters: {alpha, beta, nu, rho, gamma}.
     */
    public static final class ZabrSpecs implements XABRSpecs {

        private static final double EPS1 = 1e-7;
        private static final double EPS2 = 0.9999;

        @Override
        public int dimension() {
            return 5;
        }

        @Override
        public void defaultValues(final double[] params, final boolean[] paramIsFixed, final double forward,
                final double t, final double[] addParams) {
            // Mirrors C++ zabrinterpolation.hpp lines 39-55.
            if ( params[1] == Constants.NULL_REAL ) {
                params[1] = 0.5;
            }
            if ( params[0] == Constants.NULL_REAL ) {
                // adapt alpha to beta level
                params[0] = 0.2 * (params[1] < 0.9999 ? Math.pow(forward, 1.0 - params[1]) : 1.0);
            }
            if ( params[2] == Constants.NULL_REAL ) {
                params[2] = Math.sqrt(0.4);
            }
            if ( params[3] == Constants.NULL_REAL ) {
                params[3] = 0.0;
            }
            if ( params[4] == Constants.NULL_REAL ) {
                params[4] = 1.0;
            }
        }

        @Override
        public void guess(final double[] values, final boolean[] paramIsFixed, final double forward, final double t,
                final double[] sampleValue, final double[] addParams) {
            // Mirrors C++ zabrinterpolation.hpp lines 56-74.
            int j = 0;
            if ( !paramIsFixed[1] ) {
                values[1] = (1.0 - 2e-6) * sampleValue[j++] + 1e-6;
            }
            if ( !paramIsFixed[0] ) {
                values[0] = (1.0 - 2e-6) * sampleValue[j++] + 1e-6; // lognormal vol guess
                if ( values[1] < 0.999 ) {
                    values[0] *= Math.pow(forward, 1.0 - values[1]);
                }
            }
            if ( !paramIsFixed[2] ) {
                values[2] = 1.5 * sampleValue[j++] + 1e-6;
            }
            if ( !paramIsFixed[3] ) {
                values[3] = (2.0 * sampleValue[j++] - 1.0) * (1.0 - 1e-6);
            }
            if ( !paramIsFixed[4] ) {
                values[4] = sampleValue[j++] * 2.0;
            }
        }

        @Override
        public Array inverse(final Array y, final boolean[] paramIsFixed, final double[] params, final double forward) {
            // Mirrors C++ zabrinterpolation.hpp lines 78-88.
            final Array x = new Array(5);
            x.set(0, y.get(0) < 25.0 + EPS1 ? Math.sqrt(y.get(0) - EPS1) : (y.get(0) - EPS1 + 25.0) / 10.0);
            x.set(1, Math.sqrt(-Math.log(y.get(1))));
            x.set(2, Math.tan(Math.PI * (y.get(2) / 5.0 - 0.5)));
            x.set(3, Math.asin(y.get(3) / EPS2));
            x.set(4, Math.tan(Math.PI * (y.get(4) / 1.9 - 0.5)));
            return x;
        }

        @Override
        public Array direct(final Array x, final boolean[] paramIsFixed, final double[] params, final double forward) {
            // Mirrors C++ zabrinterpolation.hpp lines 89-105.
            final Array y = new Array(5);
            y.set(0, Math.abs(x.get(0)) < 5.0 ? x.get(0) * x.get(0) + EPS1 : (10.0 * Math.abs(x.get(0)) - 25.0) + EPS1);
            y.set(1, Math.abs(x.get(1)) < Math.sqrt(-Math.log(EPS1)) ? Math.exp(-(x.get(1) * x.get(1))) : EPS1);
            // limit nu to 5.00
            y.set(2, (Math.atan(x.get(2)) / Math.PI + 0.5) * 5.0);
            y.set(3, Math.abs(x.get(3)) < 2.5 * Math.PI
                    ? EPS2 * Math.sin(x.get(3))
                    : EPS2 * (x.get(3) > 0.0 ? 1.0 : -1.0));
            // limit gamma to 1.9
            y.set(4, (Math.atan(x.get(4)) / Math.PI + 0.5) * 1.9);
            return y;
        }

        @Override
        public double volatility(final double strike, final double forward, final double t, final double[] params) {
            // C++ template routes this through ZabrSmileSection<Eval>::type
            // (= ZabrModel::lognormalVolatility for the lognormal evaluation).
            // For calibration we always use the lognormal flavor; the cube's
            // smile section is constructed separately with the user-selected
            // evaluation kernel after calibration.
            final ZabrModel m = new ZabrModel(t, forward, params[0], params[1], params[2], params[3], params[4]);
            return m.lognormalVolatility(Math.max(1e-6, strike));
        }

        @Override
        public Constraint constraint(final double forward) {
            return new NoConstraint();
        }

        @Override
        public double weight(final double strike, final double forward, final double stdDev, final double[] addParams) {
            return BlackFormula.blackFormulaStdDevDerivative(strike, forward, stdDev, 1.0, 0.0);
        }
    }

    /**
     * Inner impl bridging {@link AbstractInterpolation.Impl} contract.
     */
    private final class ZabrInterpolationImpl extends AbstractInterpolation.Impl {

        ZabrInterpolationImpl(final Array vx, final Array vy) {
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
            return xabrImpl_.value(x);
        }

        @Override
        public double primitive(final double x) {
            throw new LibraryException("Zabr primitive not implemented");
        }

        @Override
        public double derivative(final double x) {
            throw new LibraryException("Zabr derivative not implemented");
        }

        @Override
        public double secondDerivative(final double x) {
            throw new LibraryException("Zabr secondDerivative not implemented");
        }
    }
}
