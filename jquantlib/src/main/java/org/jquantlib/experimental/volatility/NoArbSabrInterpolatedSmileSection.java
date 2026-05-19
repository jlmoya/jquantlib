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
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;

/**
 * No-arbitrage SABR interpolated smile section.
 *
 * <p>Faithful port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/noarbsabrinterpolatedsmilesection.{hpp,cpp}}. Java single-inheritance precludes the
 * C++ {@code public SmileSection, public LazyObject} double-inheritance; the {@code dirty_} flag pattern (cf.
 * {@link org.jquantlib.termstructures.volatilities.SabrInterpolatedSmileSection}) substitutes.
 *
 * <p>While the underlying {@link NoArbSabrModel} pricing methods are
 * deferred to Phase 4f.5, the calibration and smile evaluation already function (Hagan SABR fallback inside
 * {@link NoArbSabrInterpolation.NoArbSabrSpecs#volatility(double, double, double, double[])}).
 */
public class NoArbSabrInterpolatedSmileSection extends SmileSection {

    private final double[] strikes_;
    private final double[] actualStrikes_;
    private final boolean hasFloatingStrikes_;
    private final double forwardValue_;
    private final double atmVolatility_;
    private final double[] vols_;

    private final double alpha0_, beta0_, nu0_, rho0_;
    private final boolean isAlphaFixed_, isBetaFixed_, isNuFixed_, isRhoFixed_;
    private final boolean vegaWeighted_;

    private final EndCriteria endCriteria_;
    private final OptimizationMethod method_;

    private boolean dirty_ = true;
    private NoArbSabrInterpolation interpolation_;

    public NoArbSabrInterpolatedSmileSection(final Date optionDate, final double forward, final double[] strikes,
            final boolean hasFloatingStrikes, final double atmVolatility, final double[] vols, final double alpha,
            final double beta, final double nu, final double rho, final boolean isAlphaFixed, final boolean isBetaFixed,
            final boolean isNuFixed, final boolean isRhoFixed, final boolean vegaWeighted,
            final EndCriteria endCriteria, final OptimizationMethod method, final DayCounter dc) {
        super(optionDate, dc == null ? new Actual365Fixed() : dc, new Date());

        QL.require(strikes != null && vols != null, "strikes and vols must not be null");
        QL.require(strikes.length == vols.length, "strikes (%d) and vols (%d) must have the same length",
                strikes.length, vols.length);
        QL.require(strikes.length >= 1, "at least one strike/vol pair is required");

        this.forwardValue_ = forward;
        this.atmVolatility_ = atmVolatility;
        this.hasFloatingStrikes_ = hasFloatingStrikes;
        this.strikes_ = strikes.clone();
        this.alpha0_ = alpha;
        this.beta0_ = beta;
        this.nu0_ = nu;
        this.rho0_ = rho;
        this.isAlphaFixed_ = isAlphaFixed;
        this.isBetaFixed_ = isBetaFixed;
        this.isNuFixed_ = isNuFixed;
        this.isRhoFixed_ = isRhoFixed;
        this.vegaWeighted_ = vegaWeighted;
        this.endCriteria_ = endCriteria;
        this.method_ = method;

        if ( hasFloatingStrikes ) {
            final double[] aStrikes = new double[vols.length];
            final double[] aVols = new double[vols.length];
            for ( int i = 0; i < vols.length; i++ ) {
                aStrikes[i] = forward + strikes[i];
                aVols[i] = atmVolatility + vols[i];
            }
            this.actualStrikes_ = aStrikes;
            this.vols_ = aVols;
        } else {
            this.actualStrikes_ = strikes.clone();
            this.vols_ = vols.clone();
        }
    }

    private void ensureCalculated() {
        if ( dirty_ ) {
            createInterpolation();
            interpolation_.update();
            dirty_ = false;
        }
    }

    private void createInterpolation() {
        final Array vx = new Array(actualStrikes_);
        final Array vy = new Array(vols_);
        interpolation_ = new NoArbSabrInterpolation(vx, vy, exerciseTime(), forwardValue_, alpha0_, beta0_, nu0_, rho0_,
                isAlphaFixed_, isBetaFixed_, isNuFixed_, isRhoFixed_, vegaWeighted_, endCriteria_, method_);
    }

    @Override
    public double minStrike() {
        ensureCalculated();
        return actualStrikes_[0];
    }

    @Override
    public double maxStrike() {
        ensureCalculated();
        return actualStrikes_[actualStrikes_.length - 1];
    }

    @Override
    public double atmLevel() {
        return forwardValue_;
    }

    @Override
    protected double volatilityImpl(final double strike) {
        ensureCalculated();
        return interpolation_.op(strike, true);
    }

    @Override
    protected double varianceImpl(final double strike) {
        ensureCalculated();
        final double v = interpolation_.op(strike, true);
        return v * v * exerciseTime();
    }

    public double alpha() {
        ensureCalculated();
        return interpolation_.alpha();
    }

    public double beta() {
        ensureCalculated();
        return interpolation_.beta();
    }

    public double nu() {
        ensureCalculated();
        return interpolation_.nu();
    }

    public double rho() {
        ensureCalculated();
        return interpolation_.rho();
    }

    public double rmsError() {
        ensureCalculated();
        return interpolation_.rmsError();
    }

    public double maxError() {
        ensureCalculated();
        return interpolation_.maxError();
    }

    public EndCriteria.Type endCriteria() {
        ensureCalculated();
        return interpolation_.endCriteria();
    }

    @Override
    public void update() {
        dirty_ = true;
        super.update();
    }
}
