/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Cristina Duminuco
 Copyright (C) 2007 Giorgio Facchinetti
*/

package org.jquantlib.math.interpolations;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.termstructures.volatility.AbcdCalibration;
import org.jquantlib.termstructures.volatility.AbcdFunction;

/**
 * Abcd interpolation between discrete points.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/math/interpolations/abcdinterpolation.hpp}. Fits Rebonato's
 * {@link AbcdFunction} {@code f(t) = (a + b*t) * exp(-c*t) + d} parameterization to a vector of Black volatilities,
 * delegating the non-linear fit to the existing {@link AbcdCalibration}.
 *
 * <p>The class implements the {@link Interpolation} contract so it can be plugged into surfaces / curves that take an
 * {@link Interpolation.Interpolator}. Use the {@link Abcd} factory for that purpose.
 *
 * <p>Construction synopsis (matches the C++ defaults):
 * <pre>{@code
 * AbcdInterpolation interp = new AbcdInterpolation(times, blackVols);
 * double vol = interp.op(0.75);          // interpolated Black vol
 * double a   = interp.a();               // calibrated parameter
 * double rms = interp.rmsError();        // residual
 * }</pre>
 *
 * <p>Mirrors C++ {@code detail::AbcdCoeffHolder} (parameter container with default-fill semantics) and
 * {@code detail::AbcdInterpolationImpl} (the {@code Interpolation::templateImpl} wrapper that drives the fit on
 * {@code update()}).
 *
 * <p>The {@code Null<Real>} sentinel for guesses is {@link Constants#NULL_REAL}; passing it causes the C++ default
 * starting value to be used (a=-0.06, b=0.17, c=0.54, d=0.17). When a guess is non-null, the corresponding
 * {@code *IsFixed} flag is honoured; when it IS null, the parameter is forced unfixed regardless of the flag, exactly as
 * in C++ {@code AbcdCoeffHolder::AbcdCoeffHolder} (abcdinterpolation.hpp lines 54-65).
 */
public class AbcdInterpolation extends AbstractInterpolation {

    private final AbcdInterpolationImpl abcdImpl_;

    /**
     * Convenience constructor with all defaults (matches C++ defaulted-argument constructor at
     * {@code abcdinterpolation.hpp} lines 159-185).
     */
    public AbcdInterpolation(final Array vx /* times */, final Array vy /* blackVols */) {
        this(vx, vy,
                -0.06, 0.17, 0.54, 0.17,
                false, false, false, false,
                false, null, null);
    }

    /**
     * Full-arity constructor mirroring C++ {@code AbcdInterpolation::AbcdInterpolation}
     * (abcdinterpolation.hpp lines 159-185). {@code vx} are calibration times, {@code vy} the corresponding
     * Black volatilities.
     *
     * @param vx           calibration times (length N >= 2)
     * @param vy           target Black volatilities (length N)
     * @param a            initial guess for {@code a} (use {@link Constants#NULL_REAL} for C++ default -0.06)
     * @param b            initial guess for {@code b} (use {@link Constants#NULL_REAL} for C++ default 0.17)
     * @param c            initial guess for {@code c} (use {@link Constants#NULL_REAL} for C++ default 0.54)
     * @param d            initial guess for {@code d} (use {@link Constants#NULL_REAL} for C++ default 0.17)
     * @param aIsFixed     freeze {@code a} (ignored when {@code a == NULL_REAL})
     * @param bIsFixed     freeze {@code b} (ignored when {@code b == NULL_REAL})
     * @param cIsFixed     freeze {@code c} (ignored when {@code c == NULL_REAL})
     * @param dIsFixed     freeze {@code d} (ignored when {@code d == NULL_REAL})
     * @param vegaWeighted use vega weighting in the LM cost function
     * @param endCriteria  optimisation end-criteria (null = LM defaults)
     * @param optMethod    optimisation method (null = Levenberg-Marquardt with C++ defaults)
     */
    public AbcdInterpolation(final Array vx, final Array vy,
            final double a, final double b, final double c, final double d,
            final boolean aIsFixed, final boolean bIsFixed,
            final boolean cIsFixed, final boolean dIsFixed,
            final boolean vegaWeighted,
            final EndCriteria endCriteria,
            final OptimizationMethod optMethod) {
        this.abcdImpl_ = new AbcdInterpolationImpl(vx, vy,
                a, b, c, d, aIsFixed, bIsFixed, cIsFixed, dIsFixed,
                vegaWeighted, endCriteria, optMethod);
        this.impl = abcdImpl_;
        this.impl.update();
    }

    // ---- inspectors (mirror C++ AbcdInterpolation::a/b/c/d/k/rmsError/maxError/endCriteria) ----

    public double a() {
        return abcdImpl_.a_;
    }

    public double b() {
        return abcdImpl_.b_;
    }

    public double c() {
        return abcdImpl_.c_;
    }

    public double d() {
        return abcdImpl_.d_;
    }

    /** Per-knot adjustment factors {@code k[i] = blackVols[i] / value(times[i])}. */
    public List< Double > k() {
        return abcdImpl_.k_;
    }

    /** Weighted root-mean-square calibration error. */
    public double rmsError() {
        return abcdImpl_.error_;
    }

    /** Maximum absolute calibration error. */
    public double maxError() {
        return abcdImpl_.maxError_;
    }

    /** End-criteria reached by the last calibration. */
    public EndCriteria.Type endCriteria() {
        return abcdImpl_.abcdEndCriteria_;
    }

    /**
     * Linear-interpolated adjustment factor at {@code t} over the held {@code xBegin..xEnd} grid. Mirrors C++
     * {@code AbcdInterpolation::k(t, xBegin, xEnd)} (abcdinterpolation.hpp lines 197-201).
     */
    public double k(final double t, final Array xBegin, final Array xEnd) {
        // The C++ overload accepts iterator pairs; the Java single-Array idiom passes xBegin/xEnd as views.
        // We use only xBegin here for the x-grid since the k_ vector aligns with it.
        final Array ky = new Array(abcdImpl_.k_.size());
        for ( int i = 0; i < ky.size(); ++i ) {
            ky.set(i, abcdImpl_.k_.get(i));
        }
        return new LinearInterpolation(xBegin, ky).op(t);
    }

    // ===========================================================================================
    // Inner Impl (mirrors C++ detail::AbcdCoeffHolder + detail::AbcdInterpolationImpl)
    // ===========================================================================================

    /**
     * Inner impl combining C++ {@code detail::AbcdCoeffHolder} (parameter container) and
     * {@code detail::AbcdInterpolationImpl} (Interpolation::templateImpl wrapper that performs the fit).
     *
     * <p>Java single inheritance forces a single class rather than the C++ split.
     */
    private final class AbcdInterpolationImpl extends AbstractInterpolation.Impl {

        // ---- coefficient holder state (C++ AbcdCoeffHolder) ----
        double a_, b_, c_, d_;
        boolean aIsFixed_, bIsFixed_, cIsFixed_, dIsFixed_;
        List< Double > k_ = new ArrayList<>();
        double error_, maxError_;
        EndCriteria.Type abcdEndCriteria_ = EndCriteria.Type.None;

        // ---- impl state (C++ AbcdInterpolationImpl) ----
        private final EndCriteria endCriteria_;
        private final OptimizationMethod optMethod_;
        private final boolean vegaWeighted_;
        private AbcdCalibration abcdCalibrator_;

        AbcdInterpolationImpl(final Array vx, final Array vy,
                final double a, final double b, final double c, final double d,
                final boolean aIsFixed, final boolean bIsFixed,
                final boolean cIsFixed, final boolean dIsFixed,
                final boolean vegaWeighted,
                final EndCriteria endCriteria,
                final OptimizationMethod optMethod) {
            // requiredPoints == 2 per C++ Interpolation::templateImpl ctor call (abcdinterpolation.hpp line 96).
            super(vx, vy, 2);

            // C++ AbcdCoeffHolder constructor body (abcdinterpolation.hpp lines 53-68):
            // if a != Null<Real>, keep guess + honour aIsFixed; else seed default -0.06, leave aIsFixed unset (false).
            this.a_ = a;
            this.b_ = b;
            this.c_ = c;
            this.d_ = d;
            this.error_ = Constants.NULL_REAL;
            this.maxError_ = Constants.NULL_REAL;
            if ( a != Constants.NULL_REAL ) {
                this.aIsFixed_ = aIsFixed;
            } else {
                this.a_ = -0.06;
            }
            if ( b != Constants.NULL_REAL ) {
                this.bIsFixed_ = bIsFixed;
            } else {
                this.b_ = 0.17;
            }
            if ( c != Constants.NULL_REAL ) {
                this.cIsFixed_ = cIsFixed;
            } else {
                this.c_ = 0.54;
            }
            if ( d != Constants.NULL_REAL ) {
                this.dIsFixed_ = dIsFixed;
            } else {
                this.d_ = 0.17;
            }
            // NB. C++ passes the *user* a,b,c,d (possibly Null<Real>) to validate; behaviour with Null<Real> is
            // a no-op because Null<Real> = max_double passes all the inequalities. Mirror by calling validate
            // with the user inputs after the default-fill - identical observable result when all are non-null.
            AbcdFunction.validate(this.a_, this.b_, this.c_, this.d_);

            this.endCriteria_ = endCriteria;
            this.optMethod_ = optMethod;
            this.vegaWeighted_ = vegaWeighted;
        }

        @Override
        public void update() {
            // Mirror C++ AbcdInterpolationImpl::update (abcdinterpolation.hpp lines 100-125).
            final List< Double > times = new ArrayList<>(vx.size());
            final List< Double > blackVols = new ArrayList<>(vy.size());
            for ( int i = 0; i < vx.size(); ++i ) {
                times.add(vx.get(i));
                blackVols.add(vy.get(i));
            }
            abcdCalibrator_ = new AbcdCalibration(times, blackVols,
                    a_, b_, c_, d_,
                    aIsFixed_, bIsFixed_, cIsFixed_, dIsFixed_,
                    vegaWeighted_, endCriteria_, optMethod_);
            abcdCalibrator_.compute();
            this.a_ = abcdCalibrator_.a();
            this.b_ = abcdCalibrator_.b();
            this.c_ = abcdCalibrator_.c();
            this.d_ = abcdCalibrator_.d();
            this.k_ = abcdCalibrator_.k(times, blackVols);
            this.error_ = abcdCalibrator_.error();
            this.maxError_ = abcdCalibrator_.maxError();
            this.abcdEndCriteria_ = abcdCalibrator_.endCriteria();
        }

        @Override
        public double op(final double x) {
            QL.require(x >= 0.0, "time must be non negative: " + x + " not allowed");
            return abcdCalibrator_.value(x);
        }

        @Override
        public double primitive(final double x) {
            throw new LibraryException("Abcd primitive not implemented");
        }

        @Override
        public double derivative(final double x) {
            throw new LibraryException("Abcd derivative not implemented");
        }

        @Override
        public double secondDerivative(final double x) {
            throw new LibraryException("Abcd secondDerivative not implemented");
        }
    }
}
