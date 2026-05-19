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
 * SVI-interpolated smile section.
 *
 * <p>Mirrors C++ QuantLib v1.42.1
 * {@code ql/experimental/volatility/sviinterpolatedsmilesection.{hpp,cpp}}. Calibrates an {@link SviInterpolation} to a
 * set of market strike/volatility pairs and exposes the calibrated surface as a {@link SmileSection}.
 *
 * <p>Java single-inheritance precludes the C++ double-inheritance from
 * {@code SmileSection} and {@code LazyObject}; same dirty-flag pattern as
 * {@link org.jquantlib.termstructures.volatilities.SabrInterpolatedSmileSection}.
 *
 * <p>Phase 4f WI-1.
 */
public class SviInterpolatedSmileSection extends SmileSection {

    /** Raw market strikes supplied at construction. */
    private final double[] strikes_;

    /** Strikes actually used in calibration. */
    private final double[] actualStrikes_;

    /** Whether supplied strikes are floating (relative to forward). */
    private final boolean hasFloatingStrikes_;

    /** Market forward rate. */
    private final double forwardValue_;

    /** ATM volatility (used when hasFloatingStrikes_ to adjust vols). */
    private final double atmVolatility_;

    /** Market vols (after merging atm offsets if floating). */
    private final double[] vols_;

    // SVI initial / fixed parameters
    private final double a0_, b0_, sigma0_, rho0_, m0_;

    private final boolean isAFixed_, isBFixed_, isSigmaFixed_, isRhoFixed_, isMFixed_;
    private final boolean vegaWeighted_;

    private final EndCriteria endCriteria_;
    private final OptimizationMethod method_;

    /** Dirty flag: true until {@link #performCalculations()} has run. */
    private boolean dirty_ = true;

    /** The calibrated SVI interpolation (recreated each calculate()). */
    private SviInterpolation sviInterpolation_;

    /**
     * Plain-values constructor — mirrors C++ second ctor (no Handle&lt;Quote&gt;).
     */
    public SviInterpolatedSmileSection(final Date optionDate, final double forward, final double[] strikes,
            final boolean hasFloatingStrikes, final double atmVolatility, final double[] vols, final double a,
            final double b, final double sigma, final double rho, final double m, final boolean isAFixed,
            final boolean isBFixed, final boolean isSigmaFixed, final boolean isRhoFixed, final boolean isMFixed,
            final boolean vegaWeighted, final EndCriteria endCriteria, final OptimizationMethod method,
            final DayCounter dc) {
        super(optionDate, dc == null ? new Actual365Fixed() : dc, new Date());

        QL.require(strikes != null && vols != null, "strikes and vols must not be null");
        QL.require(strikes.length == vols.length, "strikes (%d) and vols (%d) must have the same length",
                strikes.length, vols.length);
        QL.require(strikes.length >= 1, "at least one strike/vol pair is required");

        this.forwardValue_ = forward;
        this.atmVolatility_ = atmVolatility;
        this.hasFloatingStrikes_ = hasFloatingStrikes;
        this.strikes_ = strikes.clone();
        this.a0_ = a;
        this.b0_ = b;
        this.sigma0_ = sigma;
        this.rho0_ = rho;
        this.m0_ = m;
        this.isAFixed_ = isAFixed;
        this.isBFixed_ = isBFixed;
        this.isSigmaFixed_ = isSigmaFixed;
        this.isRhoFixed_ = isRhoFixed;
        this.isMFixed_ = isMFixed;
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
            performCalculations();
            dirty_ = false;
        }
    }

    private void performCalculations() {
        createInterpolation();
        sviInterpolation_.update();
    }

    private void createInterpolation() {
        final Array vx = new Array(actualStrikes_);
        final Array vy = new Array(vols_);
        sviInterpolation_ = new SviInterpolation(vx, vy, exerciseTime(), forwardValue_, a0_, b0_, sigma0_, rho0_, m0_,
                isAFixed_, isBFixed_, isSigmaFixed_, isRhoFixed_, isMFixed_, vegaWeighted_, endCriteria_, method_);
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
        return sviInterpolation_.op(strike, true);
    }

    @Override
    protected double varianceImpl(final double strike) {
        ensureCalculated();
        final double v = sviInterpolation_.op(strike, true);
        return v * v * exerciseTime();
    }

    public double a() {
        ensureCalculated();
        return sviInterpolation_.a();
    }

    public double b() {
        ensureCalculated();
        return sviInterpolation_.b();
    }

    public double sigma() {
        ensureCalculated();
        return sviInterpolation_.sigma();
    }

    public double rho() {
        ensureCalculated();
        return sviInterpolation_.rho();
    }

    public double m() {
        ensureCalculated();
        return sviInterpolation_.m();
    }

    public double rmsError() {
        ensureCalculated();
        return sviInterpolation_.rmsError();
    }

    public double maxError() {
        ensureCalculated();
        return sviInterpolation_.maxError();
    }

    public EndCriteria.Type endCriteria() {
        ensureCalculated();
        return sviInterpolation_.endCriteria();
    }

    @Override
    public void update() {
        dirty_ = true;
        super.update();
    }
}
