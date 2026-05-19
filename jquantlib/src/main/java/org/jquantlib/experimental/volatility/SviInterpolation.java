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
import org.jquantlib.math.interpolations.XABRInterpolationImpl;
import org.jquantlib.math.interpolations.XABRSpecs;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.pricingengines.BlackFormula;

/**
 * SVI (Stochastic Volatility Inspired) smile interpolation between discrete volatility points.
 *
 * <p>Faithful port of QuantLib C++ v1.42.1
 * {@code ql/experimental/volatility/sviinterpolation.hpp}, including the {@code detail::SviSpecs} struct, the
 * {@code detail::checkSviParameters} arbitrage-free test, and the {@code detail::sviTotalVariance} closed-form total
 * variance.
 *
 * <p>The SVI parameterization (Gatheral 2004) is
 * <pre>
 *   w(k) = a + b * (rho * (k - m) + sqrt((k - m)^2 + sigma^2))
 * </pre>
 * with parameter constraints
 * <ul>
 *   <li>{@code b >= 0}</li>
 *   <li>{@code |rho| < 1}</li>
 *   <li>{@code sigma > 0}</li>
 *   <li>{@code a + b * sigma * sqrt(1 - rho^2) >= 0}</li>
 *   <li>{@code b * (1 + |rho|) <= 4}</li>
 * </ul>
 *
 * @see SviSmileSection
 */
public class SviInterpolation extends AbstractInterpolation {

    private final XABRInterpolationImpl< SviSpecs > xabrImpl_;

    public SviInterpolation(final Array vx, // strikes
            final Array vy, // volatilities
            final double t, // option expiry
            final double forward, final double a, final double b, final double sigma, final double rho, final double m,
            final boolean aIsFixed, final boolean bIsFixed, final boolean sigmaIsFixed, final boolean rhoIsFixed,
            final boolean mIsFixed, final boolean vegaWeighted, final EndCriteria endCriteria,
            final OptimizationMethod optMethod) {
        this(vx, vy, t, forward, a, b, sigma, rho, m, aIsFixed, bIsFixed, sigmaIsFixed, rhoIsFixed, mIsFixed,
                vegaWeighted, endCriteria, optMethod, 0.0020 /* errorAccept */, false /* useMaxError */,
                50 /* maxGuesses */);
    }

    /**
     * Full-arity constructor mirroring C++ {@code SviInterpolation} (sviinterpolation.hpp lines 147-169).
     */
    public SviInterpolation(final Array vx, final Array vy, final double t, final double forward, final double a,
            final double b, final double sigma, final double rho, final double m, final boolean aIsFixed,
            final boolean bIsFixed, final boolean sigmaIsFixed, final boolean rhoIsFixed, final boolean mIsFixed,
            final boolean vegaWeighted, final EndCriteria endCriteria, final OptimizationMethod optMethod,
            final double errorAccept, final boolean useMaxError, final int maxGuesses) {

        final double[] xArr = new double[vx.size()];
        final double[] yArr = new double[vy.size()];
        for ( int i = 0; i < vx.size(); ++i )
            xArr[i] = vx.get(i);
        for ( int i = 0; i < vy.size(); ++i )
            yArr[i] = vy.get(i);

        final double[] params = { a, b, sigma, rho, m };
        final boolean[] paramIsFixed = { aIsFixed, bIsFixed, sigmaIsFixed, rhoIsFixed, mIsFixed };

        this.xabrImpl_ = new XABRInterpolationImpl< SviSpecs >(xArr, yArr, t, forward, params, paramIsFixed,
                vegaWeighted, endCriteria, optMethod, errorAccept, useMaxError, maxGuesses, new double[0],
                new SviSpecs());

        impl = new SviInterpolationImpl(vx, vy);
    }

    /**
     * Validate SVI parameters per {@code detail::checkSviParameters} (sviinterpolation.hpp lines 35-47).
     *
     * @throws LibraryException with a descriptive message on first violation.
     */
    public static void checkSviParameters(final double a, final double b, final double sigma, final double rho,
            final double m, final double tte) {
        QL.require(b >= 0.0, "b (" + b + ") must be non negative");
        QL.require(Math.abs(rho) < 1.0, "rho (" + rho + ") must be in (-1,1)");
        QL.require(sigma > 0.0, "sigma (" + sigma + ") must be positive");
        QL.require(a + b * sigma * Math.sqrt(1.0 - rho * rho) >= 0.0,
                "a + b sigma sqrt(1-rho^2) (a=" + a + ", b=" + b + ", sigma=" + sigma + ", rho=" + rho
                        + ") must be non negative");
        QL.require(b * (1.0 + Math.abs(rho)) <= 4.0,
                "b(1+|rho|) must be less than or equal to 4, (b=" + b + ", rho=" + rho + ")");
    }

    /**
     * SVI total variance closed-form (sviinterpolation.hpp lines 49-53):
     * {@code w(k) = a + b*(rho*(k-m) + sqrt((k-m)^2 + sigma^2))}.
     */
    public static double sviTotalVariance(final double a, final double b, final double sigma, final double rho,
            final double m, final double k) {
        return a + b * (rho * (k - m) + Math.sqrt((k - m) * (k - m) + sigma * sigma));
    }

    public double expiry() {
        return xabrImpl_.t_;
    }

    public double forward() {
        return xabrImpl_.forward_;
    }

    public double a() {
        return xabrImpl_.params_[0];
    }

    public double b() {
        return xabrImpl_.params_[1];
    }

    public double sigma() {
        return xabrImpl_.params_[2];
    }

    public double rho() {
        return xabrImpl_.params_[3];
    }

    public double m() {
        return xabrImpl_.params_[4];
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

    public XABRInterpolationImpl< SviSpecs > xabrImpl() {
        return xabrImpl_;
    }

    /**
     * SVI Model concept (mirrors C++ {@code detail::SviSpecs} struct in sviinterpolation.hpp lines 57-140). Stateless.
     *
     * <p>5 parameters: {@code (a, b, sigma, rho, m)}.
     */
    public static final class SviSpecs implements XABRSpecs {

        private static final double EPS1_VAL = 0.000001;
        private static final double EPS2_VAL = 0.999999;

        @Override
        public int dimension() {
            return 5;
        }

        @Override
        public void defaultValues(final double[] params, final boolean[] paramIsFixed, final double forward,
                final double t, final double[] addParams) {
            // C++ sviinterpolation.hpp lines 59-79. Order matters:
            // sigma, rho, m, b, then a (which depends on b, sigma, rho, m).
            if ( params[2] == org.jquantlib.math.Constants.NULL_REAL )
                params[2] = 0.1;
            if ( params[3] == org.jquantlib.math.Constants.NULL_REAL )
                params[3] = -0.4;
            if ( params[4] == org.jquantlib.math.Constants.NULL_REAL )
                params[4] = 0.0;
            if ( params[1] == org.jquantlib.math.Constants.NULL_REAL ) {
                params[1] = 2.0 / (1.0 + Math.abs(params[3]));
            }
            if ( params[0] == org.jquantlib.math.Constants.NULL_REAL ) {
                params[0] = Math.max(0.20 * 0.20 * t - params[1] * (params[3] * (-params[4]) + Math.sqrt(
                                (-params[4]) * (-params[4]) + params[2] * params[2])),
                        -params[1] * params[2] * Math.sqrt(1.0 - params[3] * params[3]) + EPS1_VAL);
            }
        }

        @Override
        public void guess(final double[] values, final boolean[] paramIsFixed, final double forward, final double t,
                final double[] sampleValue, final double[] addParams) {
            // C++ sviinterpolation.hpp lines 81-97.
            int j = 0;
            if ( !paramIsFixed[2] )
                values[2] = sampleValue[j++] + EPS1_VAL;
            if ( !paramIsFixed[3] )
                values[3] = (2.0 * sampleValue[j++] - 1.0) * EPS2_VAL;
            if ( !paramIsFixed[4] )
                values[4] = (2.0 * sampleValue[j++] - 1.0);
            if ( !paramIsFixed[1] ) {
                values[1] = sampleValue[j++] * 4.0 / (1.0 + Math.abs(values[3])) * EPS2_VAL;
            }
            if ( !paramIsFixed[0] ) {
                values[0] = sampleValue[j++] * t - EPS2_VAL * (values[1] * values[2] * Math.sqrt(
                        1.0 - values[3] * values[3]));
            }
        }

        @Override
        public Array inverse(final Array y, final boolean[] paramIsFixed, final double[] params, final double forward) {
            // C++ sviinterpolation.hpp lines 98-109.
            final Array x = new Array(5);
            x.set(2, Math.sqrt(y.get(2) - EPS1_VAL));
            x.set(3, Math.asin(y.get(3) / EPS2_VAL));
            x.set(4, y.get(4));
            x.set(1, Math.tan(y.get(1) / 4.0 * (1.0 + Math.abs(y.get(3))) / EPS2_VAL * Math.PI - Math.PI / 2.0));
            x.set(0, Math.sqrt(y.get(0) - EPS1_VAL + y.get(1) * y.get(2) * Math.sqrt(1.0 - y.get(3) * y.get(3))));
            return x;
        }

        @Override
        public Array direct(final Array x, final boolean[] paramIsFixed, final double[] params, final double forward) {
            // C++ sviinterpolation.hpp lines 112-129.
            final Array y = new Array(5);
            y.set(2, x.get(2) * x.get(2) + EPS1_VAL);
            y.set(3, Math.sin(x.get(3)) * EPS2_VAL);
            y.set(4, x.get(4));
            if ( paramIsFixed[1] ) {
                y.set(1, params[1]);
            } else {
                y.set(1, (Math.atan(x.get(1)) + Math.PI / 2.0) / Math.PI * EPS2_VAL * 4.0 / (1.0 + Math.abs(y.get(3))));
            }
            if ( paramIsFixed[0] ) {
                y.set(0, params[0]);
            } else {
                y.set(0, EPS1_VAL + x.get(0) * x.get(0) - y.get(1) * y.get(2) * Math.sqrt(1.0 - y.get(3) * y.get(3)));
            }
            return y;
        }

        @Override
        public double volatility(final double strike, final double forward, final double t, final double[] params) {
            // SviWrapper (= SviSmileSection) routes through volatilityImpl,
            // which computes log-moneyness k and total variance, then sqrt(w/t).
            // sviinterpolation.hpp inlines this via SviWrapper / SviSmileSection.
            final double k = Math.log(Math.max(strike, 1.0e-6) / forward);
            final double w = sviTotalVariance(params[0], params[1], params[2], params[3], params[4], k);
            return Math.sqrt(Math.max(0.0, w / t));
        }

        @Override
        public Constraint constraint(final double forward) {
            return new NoConstraint();
        }

        @Override
        public double weight(final double strike, final double forward, final double stdDev, final double[] addParams) {
            // C++ sviinterpolation.hpp lines 130-133:
            //   blackFormulaStdDevDerivative(strike, forward, stdDev, 1.0)
            return BlackFormula.blackFormulaStdDevDerivative(strike, forward, stdDev, 1.0, 0.0);
        }
    }

    /**
     * SVI interpolation factory and traits (mirrors C++ {@code class Svi} in sviinterpolation.hpp lines 191-238).
     */
    public static final class Svi {
        public static final boolean global = true;
        private final double t_;
        private final double forward_;
        private final double a_, b_, sigma_, rho_, m_;
        private final boolean aIsFixed_, bIsFixed_, sigmaIsFixed_, rhoIsFixed_, mIsFixed_;
        private final boolean vegaWeighted_;
        private final EndCriteria endCriteria_;
        private final OptimizationMethod optMethod_;
        private final double errorAccept_;
        private final boolean useMaxError_;
        private final int maxGuesses_;

        public Svi(final double t, final double forward, final double a, final double b, final double sigma,
                final double rho, final double m, final boolean aIsFixed, final boolean bIsFixed,
                final boolean sigmaIsFixed, final boolean rhoIsFixed, final boolean mIsFixed,
                final boolean vegaWeighted, final EndCriteria endCriteria, final OptimizationMethod optMethod,
                final double errorAccept, final boolean useMaxError, final int maxGuesses) {
            this.t_ = t;
            this.forward_ = forward;
            this.a_ = a;
            this.b_ = b;
            this.sigma_ = sigma;
            this.rho_ = rho;
            this.m_ = m;
            this.aIsFixed_ = aIsFixed;
            this.bIsFixed_ = bIsFixed;
            this.sigmaIsFixed_ = sigmaIsFixed;
            this.rhoIsFixed_ = rhoIsFixed;
            this.mIsFixed_ = mIsFixed;
            this.vegaWeighted_ = vegaWeighted;
            this.endCriteria_ = endCriteria;
            this.optMethod_ = optMethod;
            this.errorAccept_ = errorAccept;
            this.useMaxError_ = useMaxError;
            this.maxGuesses_ = maxGuesses;
        }

        public SviInterpolation interpolate(final Array vx, final Array vy) {
            return new SviInterpolation(vx, vy, t_, forward_, a_, b_, sigma_, rho_, m_, aIsFixed_, bIsFixed_,
                    sigmaIsFixed_, rhoIsFixed_, mIsFixed_, vegaWeighted_, endCriteria_, optMethod_, errorAccept_,
                    useMaxError_, maxGuesses_);
        }
    }

    /**
     * Inner impl bridging the {@link AbstractInterpolation.Impl} contract (single-inheritance Java) to the standalone
     * {@link XABRInterpolationImpl}.
     */
    private final class SviInterpolationImpl extends AbstractInterpolation.Impl {

        SviInterpolationImpl(final Array vx, final Array vy) {
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
            throw new LibraryException("SVI primitive not implemented");
        }

        @Override
        public double derivative(final double x) {
            throw new LibraryException("SVI derivative not implemented");
        }

        @Override
        public double secondDerivative(final double x) {
            throw new LibraryException("SVI secondDerivative not implemented");
        }
    }
}
