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

 JQuantLib is based on QuantLib. http://quantlib.org/
*/

/*
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.interpolations.AbstractInterpolation;
import org.jquantlib.math.interpolations.SABRInterpolation.SABRSpecs;
import org.jquantlib.math.interpolations.XABRInterpolationImpl;
import org.jquantlib.math.interpolations.XABRSpecs;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.termstructures.volatilities.Sabr;

/**
 * No-arbitrage SABR (Doust 2012) smile interpolation between discrete
 * volatility points.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/noarbsabrinterpolation.hpp}
 * ({@code detail::NoArbSabrSpecs} struct + {@code NoArbSabrInterpolation}
 * facade).
 *
 * <p>The 4 parameters are {@code (alpha, beta, nu, rho)}; they are
 * tan-mapped to/from their constrained domains using
 * {@link NoArbSabrModel.Constants} bounds. {@code alpha} is recovered from
 * the {@code sigmaI = alpha * forward^(beta-1)} reparameterization
 * (mirrors C++).
 *
 * <p>During calibration the cost function evaluates volatilities via the
 * NoArbSabrSmileSection: the model's {@code optionPrice} is deferred to
 * Phase 4f.5, so {@link NoArbSabrSmileSection#volatilityImpl(double)}
 * falls back to the Hagan SABR closed form. This gives a working
 * calibration loop that produces SABR-equivalent fits today and will
 * upgrade to true No-Arb-SABR fits once the absorption table is loaded.
 */
public class NoArbSabrInterpolation extends AbstractInterpolation {

    private final XABRInterpolationImpl<NoArbSabrSpecs> xabrImpl_;

    public NoArbSabrInterpolation(
            final Array vx, final Array vy,
            final double t, final double forward,
            final double alpha, final double beta, final double nu, final double rho,
            final boolean alphaIsFixed, final boolean betaIsFixed,
            final boolean nuIsFixed, final boolean rhoIsFixed,
            final boolean vegaWeighted,
            final EndCriteria endCriteria,
            final OptimizationMethod optMethod,
            final double errorAccept,
            final boolean useMaxError,
            final int maxGuesses,
            final double shift) {

        QL.require(shift == 0.0, "NoArbSabrInterpolation for non zero shift not implemented");

        final double[] xArr = new double[vx.size()];
        final double[] yArr = new double[vy.size()];
        for (int i = 0; i < vx.size(); ++i) xArr[i] = vx.get(i);
        for (int i = 0; i < vy.size(); ++i) yArr[i] = vy.get(i);

        final double[] params = { alpha, beta, nu, rho };
        final boolean[] paramIsFixed = { alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed };

        this.xabrImpl_ = new XABRInterpolationImpl<NoArbSabrSpecs>(
                xArr, yArr, t, forward, params, paramIsFixed,
                vegaWeighted, endCriteria, optMethod,
                errorAccept, useMaxError, maxGuesses,
                new double[0], new NoArbSabrSpecs());

        impl = new NoArbSabrInterpolationImpl(vx, vy);
    }

    public NoArbSabrInterpolation(
            final Array vx, final Array vy,
            final double t, final double forward,
            final double alpha, final double beta, final double nu, final double rho,
            final boolean alphaIsFixed, final boolean betaIsFixed,
            final boolean nuIsFixed, final boolean rhoIsFixed,
            final boolean vegaWeighted,
            final EndCriteria endCriteria,
            final OptimizationMethod optMethod) {
        this(vx, vy, t, forward, alpha, beta, nu, rho,
                alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed,
                vegaWeighted, endCriteria, optMethod,
                0.0020, false, 50, 0.0);
    }

    public double expiry()   { return xabrImpl_.t_; }
    public double forward()  { return xabrImpl_.forward_; }
    public double alpha()    { return xabrImpl_.params_[0]; }
    public double beta()     { return xabrImpl_.params_[1]; }
    public double nu()       { return xabrImpl_.params_[2]; }
    public double rho()      { return xabrImpl_.params_[3]; }
    public double rmsError() { return xabrImpl_.error_; }
    public double maxError() { return xabrImpl_.maxError_; }

    public EndCriteria.Type endCriteria() { return xabrImpl_.XABREndCriteria_; }

    public XABRInterpolationImpl<NoArbSabrSpecs> xabrImpl() { return xabrImpl_; }

    /**
     * NoArbSabr Model concept (mirrors C++ {@code detail::NoArbSabrSpecs}).
     */
    public static final class NoArbSabrSpecs implements XABRSpecs {

        private static final double EPS = 0.000001;

        @Override
        public int dimension() { return 4; }

        @Override
        public void defaultValues(final double[] params, final boolean[] paramIsFixed,
                final double forward, final double t, final double[] addParams) {
            // Start with the SABR defaults (mirrors C++ noarbsabrinterpolation.hpp lines 41-44).
            new SABRSpecs().defaultValues(params, paramIsFixed, forward, t, addParams);

            // Adjust alpha/beta to keep sigmaI = alpha*forward^(beta-1) in
            // [sigmaI_min, sigmaI_max] (Doust admissibility).
            final double sigmaI = params[0] * Math.pow(forward, params[1] - 1.0);
            if (sigmaI < NoArbSabrModel.Constants.SIGMA_I_MIN) {
                if (!paramIsFixed[0]) {
                    params[0] = NoArbSabrModel.Constants.SIGMA_I_MIN * (1.0 + EPS)
                            / Math.pow(forward, params[1] - 1.0);
                } else if (!paramIsFixed[1]) {
                    params[1] = 1.0 + Math.log(NoArbSabrModel.Constants.SIGMA_I_MIN
                            * (1.0 + EPS) / params[0]) / Math.log(forward);
                }
            }
            if (sigmaI > NoArbSabrModel.Constants.SIGMA_I_MAX) {
                if (!paramIsFixed[0]) {
                    params[0] = NoArbSabrModel.Constants.SIGMA_I_MAX * (1.0 - EPS)
                            / Math.pow(forward, params[1] - 1.0);
                } else if (!paramIsFixed[1]) {
                    params[1] = 1.0 + Math.log(NoArbSabrModel.Constants.SIGMA_I_MAX
                            * (1.0 - EPS) / params[0]) / Math.log(forward);
                }
            }
        }

        @Override
        public void guess(final double[] values, final boolean[] paramIsFixed,
                final double forward, final double t,
                final double[] sampleValue, final double[] addParams) {
            int j = 0;
            if (!paramIsFixed[1]) {
                values[1] = NoArbSabrModel.Constants.BETA_MIN
                        + (NoArbSabrModel.Constants.BETA_MAX - NoArbSabrModel.Constants.BETA_MIN)
                        * sampleValue[j++];
            }
            if (!paramIsFixed[0]) {
                double sigmaI = NoArbSabrModel.Constants.SIGMA_I_MIN
                        + (NoArbSabrModel.Constants.SIGMA_I_MAX - NoArbSabrModel.Constants.SIGMA_I_MIN)
                        * sampleValue[j++];
                sigmaI *= (1.0 - EPS);
                sigmaI += EPS / 2.0;
                values[0] = sigmaI / Math.pow(forward, values[1] - 1.0);
            }
            if (!paramIsFixed[2]) {
                values[2] = NoArbSabrModel.Constants.NU_MIN
                        + (NoArbSabrModel.Constants.NU_MAX - NoArbSabrModel.Constants.NU_MIN)
                        * sampleValue[j++];
            }
            if (!paramIsFixed[3]) {
                values[3] = NoArbSabrModel.Constants.RHO_MIN
                        + (NoArbSabrModel.Constants.RHO_MAX - NoArbSabrModel.Constants.RHO_MIN)
                        * sampleValue[j++];
            }
        }

        @Override
        public Array inverse(final Array y, final boolean[] paramIsFixed,
                final double[] params, final double forward) {
            final Array x = new Array(4);
            x.set(1, Math.tan((y.get(1) - NoArbSabrModel.Constants.BETA_MIN)
                    / (NoArbSabrModel.Constants.BETA_MAX - NoArbSabrModel.Constants.BETA_MIN)
                    * Math.PI + Math.PI / 2.0));
            x.set(0, Math.tan((y.get(0) * Math.pow(forward, y.get(1) - 1.0)
                    - NoArbSabrModel.Constants.SIGMA_I_MIN)
                    / (NoArbSabrModel.Constants.SIGMA_I_MAX - NoArbSabrModel.Constants.SIGMA_I_MIN)
                    * Math.PI - Math.PI / 2.0));
            x.set(2, Math.tan((y.get(2) - NoArbSabrModel.Constants.NU_MIN)
                    / (NoArbSabrModel.Constants.NU_MAX - NoArbSabrModel.Constants.NU_MIN)
                    * Math.PI + Math.PI / 2.0));
            x.set(3, Math.tan((y.get(3) - NoArbSabrModel.Constants.RHO_MIN)
                    / (NoArbSabrModel.Constants.RHO_MAX - NoArbSabrModel.Constants.RHO_MIN)
                    * Math.PI + Math.PI / 2.0));
            return x;
        }

        @Override
        public Array direct(final Array x, final boolean[] paramIsFixed,
                final double[] params, final double forward) {
            final Array y = new Array(4);
            if (paramIsFixed[1]) {
                y.set(1, params[1]);
            } else {
                y.set(1, NoArbSabrModel.Constants.BETA_MIN
                        + (NoArbSabrModel.Constants.BETA_MAX - NoArbSabrModel.Constants.BETA_MIN)
                        * (Math.atan(x.get(1)) + Math.PI / 2.0) / Math.PI);
            }
            if (paramIsFixed[0]) {
                y.set(0, params[0]);
                final double sigmaI = y.get(0) * Math.pow(forward, y.get(1) - 1.0);
                if (sigmaI < NoArbSabrModel.Constants.SIGMA_I_MIN) {
                    y.set(1, 1.0 + Math.log(NoArbSabrModel.Constants.SIGMA_I_MIN
                            * (1.0 + EPS) / y.get(0)) / Math.log(forward));
                }
                if (sigmaI > NoArbSabrModel.Constants.SIGMA_I_MAX) {
                    y.set(1, 1.0 + Math.log(NoArbSabrModel.Constants.SIGMA_I_MAX
                            * (1.0 - EPS) / y.get(0)) / Math.log(forward));
                }
            } else {
                final double sigmaI = NoArbSabrModel.Constants.SIGMA_I_MIN
                        + (NoArbSabrModel.Constants.SIGMA_I_MAX - NoArbSabrModel.Constants.SIGMA_I_MIN)
                        * (Math.atan(x.get(0)) + Math.PI / 2.0) / Math.PI;
                y.set(0, sigmaI / Math.pow(forward, y.get(1) - 1.0));
            }
            if (paramIsFixed[2]) {
                y.set(2, params[2]);
            } else {
                y.set(2, NoArbSabrModel.Constants.NU_MIN
                        + (NoArbSabrModel.Constants.NU_MAX - NoArbSabrModel.Constants.NU_MIN)
                        * (Math.atan(x.get(2)) + Math.PI / 2.0) / Math.PI);
            }
            if (paramIsFixed[3]) {
                y.set(3, params[3]);
            } else {
                y.set(3, NoArbSabrModel.Constants.RHO_MIN
                        + (NoArbSabrModel.Constants.RHO_MAX - NoArbSabrModel.Constants.RHO_MIN)
                        * (Math.atan(x.get(3)) + Math.PI / 2.0) / Math.PI);
            }
            return y;
        }

        @Override
        public double volatility(final double strike, final double forward,
                final double t, final double[] params) {
            // The C++ template routes this through NoArbSabrWrapper (=
            // NoArbSabrSmileSection), which tries the model first then falls
            // back to Hagan SABR. While Phase 4f.5 dependencies (absorption
            // table) are deferred, the model throws and Hagan is used —
            // calibration thus produces SABR-equivalent fits.
            return new Sabr().unsafeSabrVolatility(strike, forward, t,
                    params[0], params[1], params[2], params[3]);
        }

        @Override
        public Constraint constraint(final double forward) {
            return new NoConstraint();
        }

        @Override
        public double weight(final double strike, final double forward,
                final double stdDev, final double[] addParams) {
            return BlackFormula.blackFormulaStdDevDerivative(strike, forward, stdDev, 1.0, 0.0);
        }
    }

    /**
     * Inner impl bridging the {@link AbstractInterpolation.Impl} contract.
     */
    private final class NoArbSabrInterpolationImpl extends AbstractInterpolation.Impl {

        NoArbSabrInterpolationImpl(final Array vx, final Array vy) {
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
            throw new LibraryException("NoArbSabr primitive not implemented");
        }

        @Override
        public double derivative(final double x) {
            throw new LibraryException("NoArbSabr derivative not implemented");
        }

        @Override
        public double secondDerivative(final double x) {
            throw new LibraryException("NoArbSabr secondDerivative not implemented");
        }
    }
}
