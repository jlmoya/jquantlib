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

package org.jquantlib.termstructures.volatilities;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.volatility.ZabrInterpolation;
import org.jquantlib.experimental.volatility.ZabrSmileSection;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.time.Date;

/**
 * ZABR-interpolated smile section — calibrates a ZABR model to a set of
 * market strike/volatility pairs via the existing {@link ZabrInterpolation}
 * optimizer and exposes the calibrated surface as a {@link SmileSection}.
 *
 * <p>Port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/zabrinterpolatedsmilesection.hpp} —
 * a {@code class ZabrInterpolatedSmileSection<Evaluation>}.
 *
 * <h2>Java port deviations</h2>
 * <ul>
 *   <li><b>Template parameter</b> — the C++ class is templated over an
 *       evaluation tag (one of {@code ZabrShortMaturityLognormal},
 *       {@code ZabrShortMaturityNormal}, {@code ZabrLocalVolatility},
 *       {@code ZabrFullFd}). The Java port carries the evaluation tag as a
 *       constructor argument of type
 *       {@link ZabrSmileSection.Evaluation}; the {@link ZabrInterpolation}
 *       calibration is always done in the lognormal flavor (matches C++
 *       template specialisation for the calibration kernel) and the
 *       evaluation tag is propagated through the inspectors that surface
 *       it (e.g. when callers re-use the calibrated parameters to build a
 *       {@link ZabrSmileSection} for evaluation).</li>
 *   <li><b>LazyObject</b> — C++ inherits {@code SmileSection} and
 *       {@code LazyObject}; Java single inheritance forces a dirty-flag
 *       pattern (same approach as {@link SabrInterpolatedSmileSection}).
 *       {@link #update()} marks dirty; any accessor runs
 *       {@link #performCalculations()} via {@link #ensureCalculated()}.</li>
 *   <li><b>Quote handles</b> — C++ has two ctors, one taking
 *       {@code Handle<Quote>} for live quotes, one taking plain
 *       {@code Volatility} values. The Java port keeps only the plain-values
 *       variant (matches {@link SabrInterpolatedSmileSection} which made
 *       the same choice; the live-quote variant is added later if a
 *       caller needs it).</li>
 * </ul>
 *
 * <p>L2-C Phase 2 forward closure (audit ID
 * {@code ZabrInterpolatedSmileSection}).
 */
public class ZabrInterpolatedSmileSection extends SmileSection {

    // ------------------------------------------------------------------
    // Fields (mirror C++ private members)
    // ------------------------------------------------------------------

    /** Evaluation flavor — propagated through to consumer code. */
    private final ZabrSmileSection.Evaluation evaluation_;

    /** Raw market strikes supplied at construction. */
    private final double[] strikes_;

    /** Strikes actually used in calibration (forward+strikes_ if floating). */
    private final double[] actualStrikes_;

    /** Whether supplied strikes are floating (relative to forward). */
    private final boolean hasFloatingStrikes_;

    /** Market forward rate. */
    private final double forwardValue_;

    /** ATM volatility (used when hasFloatingStrikes_ to recenter vols). */
    private final double atmVolatility_;

    /** Market vols (after recenter when hasFloatingStrikes_). */
    private final double[] vols_;

    // ZABR initial / fixed parameters (5 = {alpha, beta, nu, rho, gamma}).
    private final double alpha0_;
    private final double beta0_;
    private final double nu0_;
    private final double rho0_;
    private final double gamma0_;

    private final boolean isAlphaFixed_;
    private final boolean isBetaFixed_;
    private final boolean isNuFixed_;
    private final boolean isRhoFixed_;
    private final boolean isGammaFixed_;
    private final boolean vegaWeighted_;

    private final EndCriteria endCriteria_;
    private final OptimizationMethod method_;

    // ------------------------------------------------------------------
    // Lazy state
    // ------------------------------------------------------------------

    /** Dirty flag — true until {@link #performCalculations()} has run. */
    private boolean dirty_ = true;

    /** Calibrated ZABR interpolation; recreated each calculate(). */
    private ZabrInterpolation zabrInterpolation_;

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    /**
     * Full-arity constructor — mirrors C++ second ctor of
     * {@code ZabrInterpolatedSmileSection} (the one without
     * {@code Handle<Quote>}).
     *
     * @param optionDate         expiry date of this smile slice
     * @param forward            current forward rate
     * @param strikes            market strikes (absolute or floating offsets)
     * @param hasFloatingStrikes true if strikes are relative offsets from forward
     * @param atmVolatility      ATM volatility
     * @param vols               market volatilities (same length as strikes)
     * @param alpha              ZABR alpha initial value
     * @param beta               ZABR beta initial value
     * @param nu                 ZABR nu initial value
     * @param rho                ZABR rho initial value
     * @param gamma              ZABR gamma initial value
     * @param isAlphaFixed       fix alpha during calibration
     * @param isBetaFixed        fix beta during calibration
     * @param isNuFixed          fix nu during calibration
     * @param isRhoFixed         fix rho during calibration
     * @param isGammaFixed       fix gamma during calibration
     * @param vegaWeighted       use vega-weighted calibration
     * @param endCriteria        calibration stopping criterion (null = default)
     * @param method             optimization method (null = default)
     * @param dc                 day counter for time conversion
     */
    public ZabrInterpolatedSmileSection(final Date optionDate, final double forward, final double[] strikes,
            final boolean hasFloatingStrikes, final double atmVolatility, final double[] vols, final double alpha,
            final double beta, final double nu, final double rho, final double gamma, final boolean isAlphaFixed,
            final boolean isBetaFixed, final boolean isNuFixed, final boolean isRhoFixed, final boolean isGammaFixed,
            final boolean vegaWeighted, final EndCriteria endCriteria, final OptimizationMethod method,
            final DayCounter dc) {
        this(optionDate, forward, strikes, hasFloatingStrikes, atmVolatility, vols, alpha, beta, nu, rho, gamma,
                isAlphaFixed, isBetaFixed, isNuFixed, isRhoFixed, isGammaFixed, vegaWeighted, endCriteria, method, dc,
                ZabrSmileSection.Evaluation.ShortMaturityLognormal);
    }

    /**
     * Full-arity constructor with explicit ZABR evaluation flavor.
     *
     * @param evaluation evaluation flavor; carried through to inspectors
     *                   so callers can build a {@link ZabrSmileSection} with
     *                   the matching tag after calibration completes
     */
    public ZabrInterpolatedSmileSection(final Date optionDate, final double forward, final double[] strikes,
            final boolean hasFloatingStrikes, final double atmVolatility, final double[] vols, final double alpha,
            final double beta, final double nu, final double rho, final double gamma, final boolean isAlphaFixed,
            final boolean isBetaFixed, final boolean isNuFixed, final boolean isRhoFixed, final boolean isGammaFixed,
            final boolean vegaWeighted, final EndCriteria endCriteria, final OptimizationMethod method,
            final DayCounter dc, final ZabrSmileSection.Evaluation evaluation) {

        super(optionDate, dc == null ? new Actual365Fixed() : dc,
                new Date() /* null date → isFloating_ = true, mirrors C++ Date() */,
                VolatilityType.ShiftedLognormal, 0.0);

        QL.require(strikes != null && vols != null, "strikes and vols must not be null");
        QL.require(strikes.length == vols.length, "strikes (%d) and vols (%d) must have the same length", strikes.length,
                vols.length);
        QL.require(strikes.length >= 1, "at least one strike/vol pair is required");
        QL.require(evaluation != null, "evaluation must not be null");

        this.evaluation_ = evaluation;
        this.forwardValue_ = forward;
        this.atmVolatility_ = atmVolatility;
        this.hasFloatingStrikes_ = hasFloatingStrikes;
        this.strikes_ = strikes.clone();
        this.alpha0_ = alpha;
        this.beta0_ = beta;
        this.nu0_ = nu;
        this.rho0_ = rho;
        this.gamma0_ = gamma;
        this.isAlphaFixed_ = isAlphaFixed;
        this.isBetaFixed_ = isBetaFixed;
        this.isNuFixed_ = isNuFixed;
        this.isRhoFixed_ = isRhoFixed;
        this.isGammaFixed_ = isGammaFixed;
        this.vegaWeighted_ = vegaWeighted;
        this.endCriteria_ = endCriteria;
        this.method_ = method;

        // Mirror C++ performCalculations(): build actualStrikes_/vols_ filtered by valid quotes.
        // Plain-values variant has all entries valid by construction.
        if (hasFloatingStrikes) {
            final double[] aStrikes = new double[vols.length];
            final double[] aVols = new double[vols.length];
            for (int i = 0; i < vols.length; i++) {
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

    /**
     * Convenience constructor — default ZABR initial guesses (NULL_REAL),
     * all parameters free, vegaWeighted=true, default EndCriteria /
     * OptimizationMethod, Actual365Fixed, lognormal evaluation.
     */
    public ZabrInterpolatedSmileSection(final Date optionDate, final double forward, final double[] strikes,
            final double atmVolatility, final double[] vols) {
        this(optionDate, forward, strikes, false, atmVolatility, vols, Constants.NULL_REAL, Constants.NULL_REAL,
                Constants.NULL_REAL, Constants.NULL_REAL, Constants.NULL_REAL, false, false, false, false, false, true,
                null, null, new Actual365Fixed(), ZabrSmileSection.Evaluation.ShortMaturityLognormal);
    }

    // ------------------------------------------------------------------
    // Lazy-calculation machinery
    // ------------------------------------------------------------------

    private void ensureCalculated() {
        if (dirty_) {
            performCalculations();
            dirty_ = false;
        }
    }

    /** Mirrors C++ {@code performCalculations()}. */
    private void performCalculations() {
        createInterpolation();
        zabrInterpolation_.update();
    }

    /**
     * Builds a fresh {@link ZabrInterpolation} from {@code actualStrikes_}
     * and {@code vols_}. Mirrors C++ {@code createInterpolation()}.
     */
    private void createInterpolation() {
        final Array vx = new Array(actualStrikes_);
        final Array vy = new Array(vols_);
        zabrInterpolation_ = new ZabrInterpolation(vx, vy, exerciseTime(), forwardValue_, alpha0_, beta0_, nu0_, rho0_,
                gamma0_, isAlphaFixed_, isBetaFixed_, isNuFixed_, isRhoFixed_, isGammaFixed_, vegaWeighted_,
                endCriteria_, method_);
    }

    // ------------------------------------------------------------------
    // SmileSection interface
    // ------------------------------------------------------------------

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
        return zabrInterpolation_.op(strike, true);
    }

    @Override
    protected double varianceImpl(final double strike) {
        ensureCalculated();
        final double v = zabrInterpolation_.op(strike, true);
        return v * v * exerciseTime();
    }

    // ------------------------------------------------------------------
    // Inspectors (mirror C++ inline accessors)
    // ------------------------------------------------------------------

    /** Calibrated ZABR alpha. */
    public double alpha() {
        ensureCalculated();
        return zabrInterpolation_.alpha();
    }

    /** Calibrated ZABR beta. */
    public double beta() {
        ensureCalculated();
        return zabrInterpolation_.beta();
    }

    /** Calibrated ZABR nu. */
    public double nu() {
        ensureCalculated();
        return zabrInterpolation_.nu();
    }

    /** Calibrated ZABR rho. */
    public double rho() {
        ensureCalculated();
        return zabrInterpolation_.rho();
    }

    /** Calibrated ZABR gamma. */
    public double gamma() {
        ensureCalculated();
        return zabrInterpolation_.gamma();
    }

    /** RMS error of the calibration. */
    public double rmsError() {
        ensureCalculated();
        return zabrInterpolation_.rmsError();
    }

    /** Max point-wise error of the calibration. */
    public double maxError() {
        ensureCalculated();
        return zabrInterpolation_.maxError();
    }

    /** End-criteria outcome of the calibration optimizer. */
    public EndCriteria.Type endCriteria() {
        ensureCalculated();
        return zabrInterpolation_.endCriteria();
    }

    /** Evaluation flavor (matches the C++ template parameter). */
    public ZabrSmileSection.Evaluation evaluation() {
        return evaluation_;
    }

    // ------------------------------------------------------------------
    // SmileSection.update()
    // ------------------------------------------------------------------

    /**
     * Marks the section as dirty so the next accessor call will re-calibrate.
     * Mirrors C++ {@code ZabrInterpolatedSmileSection::update()}.
     */
    @Override
    public void update() {
        dirty_ = true;
        super.update();
    }
}
