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

package org.jquantlib.experimental.volatility;

import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * ZABR interpolation factory and traits.
 *
 * <p>Java port of C++ QuantLib v1.43
 * {@code ql/math/interpolations/zabrinterpolation.hpp::Zabr} (lines 168-207).
 * It carries a pre-parameterised ZABR fit — the five model parameters, their
 * fixed/free flags and the calibration settings — so that it can be handed to
 * anything taking an {@link Interpolation.Interpolator}, the same role
 * {@link org.jquantlib.math.interpolations.Abcd} plays for the abcd fit.
 *
 * <p>The C++ class is templated on an evaluation tag, which it forwards to
 * {@code ZabrInterpolation<Evaluation>}. There is no such parameter here
 * because {@link ZabrInterpolation} itself pins the evaluation kernel to the
 * Hagan-style lognormal one: the kernel affects only smile-section evaluation,
 * not the calibration this factory parameterises. See the class comment on
 * {@code ZabrInterpolation}.
 *
 * <p>Mirrors C++ {@code Zabr::global = true} (the fit is non-local). C++ does
 * not declare {@code requiredPoints} on {@code Zabr}; the value here is the
 * five model parameters' worth of quotes needed for a determined fit, matching
 * {@code ZabrSpecs::dimension()}.
 *
 * @author Jose Moya
 */
public class Zabr implements Interpolation.Interpolator {

    private final double t_;
    private final double forward_;
    private final double alpha_, beta_, nu_, rho_, gamma_;
    private final boolean alphaIsFixed_, betaIsFixed_, nuIsFixed_, rhoIsFixed_, gammaIsFixed_;
    private final boolean vegaWeighted_;
    private final EndCriteria endCriteria_;
    private final OptimizationMethod optMethod_;
    private final double errorAccept_;
    private final boolean useMaxError_;
    private final int maxGuesses_;

    /**
     * Full-arity constructor mirroring C++ {@code Zabr::Zabr}
     * (zabrinterpolation.hpp:171-186).
     */
    public Zabr(final double t, final double forward,
            final double alpha, final double beta, final double nu, final double rho, final double gamma,
            final boolean alphaIsFixed, final boolean betaIsFixed, final boolean nuIsFixed,
            final boolean rhoIsFixed, final boolean gammaIsFixed,
            final boolean vegaWeighted,
            final EndCriteria endCriteria,
            final OptimizationMethod optMethod,
            final double errorAccept, final boolean useMaxError, final int maxGuesses) {
        this.t_ = t;
        this.forward_ = forward;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.nu_ = nu;
        this.rho_ = rho;
        this.gamma_ = gamma;
        this.alphaIsFixed_ = alphaIsFixed;
        this.betaIsFixed_ = betaIsFixed;
        this.nuIsFixed_ = nuIsFixed;
        this.rhoIsFixed_ = rhoIsFixed;
        this.gammaIsFixed_ = gammaIsFixed;
        this.vegaWeighted_ = vegaWeighted;
        this.endCriteria_ = endCriteria;
        this.optMethod_ = optMethod;
        this.errorAccept_ = errorAccept;
        this.useMaxError_ = useMaxError;
        this.maxGuesses_ = maxGuesses;
    }

    /**
     * Convenience constructor taking the C++ defaults for the trailing
     * arguments ({@code vegaWeighted = false}, no end-criteria / method
     * override, {@code errorAccept = 0.0020}, {@code useMaxError = false},
     * {@code maxGuesses = 50}).
     */
    public Zabr(final double t, final double forward,
            final double alpha, final double beta, final double nu, final double rho, final double gamma,
            final boolean alphaIsFixed, final boolean betaIsFixed, final boolean nuIsFixed,
            final boolean rhoIsFixed, final boolean gammaIsFixed) {
        this(t, forward, alpha, beta, nu, rho, gamma,
                alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, gammaIsFixed,
                false, null, null, 0.0020, false, 50);
    }

    @Override
    public Interpolation interpolate(final Array vx, final Array vy) {
        return new ZabrInterpolation(vx, vy, t_, forward_,
                alpha_, beta_, nu_, rho_, gamma_,
                alphaIsFixed_, betaIsFixed_, nuIsFixed_, rhoIsFixed_, gammaIsFixed_,
                vegaWeighted_, endCriteria_, optMethod_,
                errorAccept_, useMaxError_, maxGuesses_);
    }

    /** C++ {@code Zabr::global = true} (zabrinterpolation.hpp:197). */
    @Override
    public boolean global() {
        return true;
    }

    /** Five model parameters, matching {@code ZabrInterpolation.ZabrSpecs.dimension()}. */
    @Override
    public int requiredPoints() {
        return 5;
    }
}
