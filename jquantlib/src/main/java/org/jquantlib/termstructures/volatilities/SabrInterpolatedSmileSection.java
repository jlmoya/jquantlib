/*
 Copyright (C) 2007 Cristina Duminuco
 Copyright (C) 2006 François du Vignaud
 Copyright (C) 2015 Peter Caspers
 Copyright (C) 2026 JQuantLib migration contributors (Java port)

 This source code is released under the BSD License.

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
 Copyright (C) 2007 Cristina Duminuco
 Copyright (C) 2006 François du Vignaud
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.volatilities;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.SABRInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * SABR-interpolated smile section.
 *
 * <p>Mirrors C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/sabrinterpolatedsmilesection.{hpp,cpp}}. Fits a SABR model to a set of market
 * strike/volatility pairs via the existing {@link SABRInterpolation} optimizer and exposes the calibrated surface as a
 * {@link SmileSection}.
 *
 * <p>Java single-inheritance precludes the C++ double-inheritance from
 * {@code SmileSection} and {@code LazyObject}. Instead we maintain a {@code dirty_} flag: any setter or constructor
 * sets {@code dirty_ = true}; {@link #ensureCalculated()} runs {@link #performCalculations()} on demand before any
 * accessor is used.
 *
 * <p>Two constructor variants mirror C++:
 * <ol>
 *   <li><b>Plain values</b> — {@code Rate forward}, {@code Volatility atmVol},
 *       {@code double[] vols}: all market data supplied as raw doubles.
 *       This is the primary variant used in the MarkovFunctional SabrSmile branch
 *       and in tests.</li>
 *   <li><b>Same values, full parameter list</b> — identical behaviour but
 *       exposes all optional SABR settings (fixed-parameter flags,
 *       vegaWeighted, endCriteria, optimizationMethod, dayCounter, shift).</li>
 * </ol>
 *
 * <p>Phase 2k Track A.
 *
 * @author JQuantLib migration contributors
 */
public class SabrInterpolatedSmileSection extends SmileSection {

    // -----------------------------------------------------------------------
    // Fields (mirror C++ private members)
    // -----------------------------------------------------------------------

    /** Raw market strikes supplied at construction. */
    private final double[] strikes_;

    /**
     * Strikes actually used in calibration: when {@code hasFloatingStrikes_} these are {@code forward + strikes_[i]};
     * invalid vol handles are skipped (plain-values variant: all are always valid).
     */
    private final double[] actualStrikes_;

    /** Whether supplied strikes are floating (relative to forward). */
    private final boolean hasFloatingStrikes_;

    /** Market forward rate. */
    private final double forwardValue_;

    /** ATM volatility (used when hasFloatingStrikes_ to adjust vols). */
    private final double atmVolatility_;

    /** Market vols (after filtering valid entries). */
    private final double[] vols_;

    // SABR initial / fixed parameters
    private final double alpha0_;
    private final double beta0_;
    private final double nu0_;
    private final double rho0_;

    private final boolean isAlphaFixed_;
    private final boolean isBetaFixed_;
    private final boolean isNuFixed_;
    private final boolean isRhoFixed_;
    private final boolean vegaWeighted_;

    private final EndCriteria endCriteria_;
    private final OptimizationMethod method_;

    // -----------------------------------------------------------------------
    // Lazy state (set by performCalculations())
    // -----------------------------------------------------------------------

    /** Dirty flag: true until {@link #performCalculations()} has run. */
    private boolean dirty_ = true;

    /** The calibrated SABR interpolation object (recreated each calculate()). */
    private SABRInterpolation sabrInterpolation_;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Full-arity "plain values" constructor — mirrors C++ second ctor (the one without {@code Handle<Quote>}).
     *
     * @param optionDate         expiry date of this smile slice
     * @param forward            current forward rate
     * @param strikes            market strikes (absolute or floating offsets)
     * @param hasFloatingStrikes true if strikes are relative offsets from forward
     * @param atmVolatility      ATM volatility
     * @param vols               market volatilities (same length as strikes)
     * @param alpha              SABR alpha initial value
     * @param beta               SABR beta initial value
     * @param nu                 SABR nu initial value
     * @param rho                SABR rho initial value
     * @param isAlphaFixed       fix alpha during calibration
     * @param isBetaFixed        fix beta during calibration
     * @param isNuFixed          fix nu during calibration
     * @param isRhoFixed         fix rho during calibration
     * @param vegaWeighted       use vega-weighted calibration
     * @param endCriteria        calibration stopping criterion (null = default)
     * @param method             optimization method (null = default)
     * @param dc                 day counter for time conversion
     * @param shift              shift for shifted-lognormal SABR (0 = standard)
     */
    public SabrInterpolatedSmileSection(final Date optionDate, final double forward, final double[] strikes,
            final boolean hasFloatingStrikes, final double atmVolatility, final double[] vols, final double alpha,
            final double beta, final double nu, final double rho, final boolean isAlphaFixed, final boolean isBetaFixed,
            final boolean isNuFixed, final boolean isRhoFixed, final boolean vegaWeighted,
            final EndCriteria endCriteria, final OptimizationMethod method, final DayCounter dc, final double shift) {

        super(optionDate, dc == null ? new Actual365Fixed() : dc,
                new Date() /* null date → isFloating_ = true, mirrors C++ Date() */, VolatilityType.ShiftedLognormal,
                shift);

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

        // Build actualStrikes_ and vols_ (all entries valid for the plain-values variant)
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

    /**
     * Convenience constructor with default SABR initial guesses and settings. Strikes must be absolute
     * (hasFloatingStrikes=false), all parameters free, vegaWeighted=true, default EndCriteria/OptimizationMethod,
     * Actual365Fixed, shift=0.
     */
    public SabrInterpolatedSmileSection(final Date optionDate, final double forward, final double[] strikes,
            final double atmVolatility, final double[] vols) {
        this(optionDate, forward, strikes, false, atmVolatility, vols, Constants.NULL_REAL, Constants.NULL_REAL,
                Constants.NULL_REAL, Constants.NULL_REAL, false, false, false, false, true, null, null,
                new Actual365Fixed(), 0.0);
    }

    /**
     * Constructor using the same market data as C++ {@code SabrInterpolatedSmileSection} second ctor but with
     * {@code List} arguments (convenience for callers passing List&lt;Rate&gt; from MarkovFunctional's strike-grid).
     *
     * @param optionDate         expiry date
     * @param forward            current forward rate
     * @param strikes            strike list
     * @param hasFloatingStrikes whether strikes are floating offsets from forward
     * @param atmVolatility      ATM volatility
     * @param vols               market volatility list (same length as strikes)
     * @param alpha              initial SABR alpha
     * @param beta               initial SABR beta
     * @param nu                 initial SABR nu
     * @param rho                initial SABR rho
     * @param isAlphaFixed       fix alpha during calibration
     * @param isBetaFixed        fix beta during calibration
     * @param isNuFixed          fix nu during calibration
     * @param isRhoFixed         fix rho during calibration
     * @param vegaWeighted       use vega-weighted calibration
     * @param endCriteria        calibration stopping criterion (null = default)
     * @param method             optimization method (null = default)
     * @param dc                 day counter for time conversion
     * @param shift              SABR shift
     */
    public SabrInterpolatedSmileSection(final Date optionDate, final double forward, final List< Double > strikes,
            final boolean hasFloatingStrikes, final double atmVolatility, final List< Double > vols, final double alpha,
            final double beta, final double nu, final double rho, final boolean isAlphaFixed, final boolean isBetaFixed,
            final boolean isNuFixed, final boolean isRhoFixed, final boolean vegaWeighted,
            final EndCriteria endCriteria, final OptimizationMethod method, final DayCounter dc, final double shift) {
        this(optionDate, forward, toDoubleArray(strikes), hasFloatingStrikes, atmVolatility, toDoubleArray(vols), alpha,
                beta, nu, rho, isAlphaFixed, isBetaFixed, isNuFixed, isRhoFixed, vegaWeighted, endCriteria, method, dc,
                shift);
    }

    // -----------------------------------------------------------------------
    // Lazy-calculation machinery (mirrors C++ LazyObject pattern)
    // -----------------------------------------------------------------------

    private static double[] toDoubleArray(final List< Double > list) {
        final double[] arr = new double[list.size()];
        for ( int i = 0; i < arr.length; i++ ) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * Run calibration if the section is dirty. Called by every accessor before returning results.
     */
    private void ensureCalculated() {
        if ( dirty_ ) {
            performCalculations();
            dirty_ = false;
        }
    }

    /**
     * (Re-)creates and calibrates the {@link SABRInterpolation} object. Mirrors C++
     * {@code SabrInterpolatedSmileSection::performCalculations()}.
     */
    private void performCalculations() {
        createInterpolation();
        sabrInterpolation_.update();
    }

    // -----------------------------------------------------------------------
    // SmileSection interface
    // -----------------------------------------------------------------------

    /**
     * Builds a fresh {@link SABRInterpolation} from the pre-computed {@code actualStrikes_} and {@code vols_}. Mirrors
     * C++ {@code SabrInterpolatedSmileSection::createInterpolation()}.
     *
     * <p>C++ passes {@code 0.0020} for {@code errorAccept},
     * {@code false} for {@code useMaxError}, and {@code 50} for {@code maxGuesses} — this matches the defaults of the
     * full-arity {@link SABRInterpolation} constructor.
     */
    private void createInterpolation() {
        final Array vx = new Array(actualStrikes_);
        final Array vy = new Array(vols_);
        sabrInterpolation_ = new SABRInterpolation(vx, vy, exerciseTime(), forwardValue_, alpha0_, beta0_, nu0_, rho0_,
                isAlphaFixed_, isBetaFixed_, isNuFixed_, isRhoFixed_, vegaWeighted_, endCriteria_, method_, 0.0020,
                false, 50, shift());
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
        return sabrInterpolation_.op(strike, true);
    }

    // -----------------------------------------------------------------------
    // Inspectors (mirror C++ inline accessors)
    // -----------------------------------------------------------------------

    @Override
    protected double varianceImpl(final double strike) {
        ensureCalculated();
        final double v = sabrInterpolation_.op(strike, true);
        return v * v * exerciseTime();
    }

    /**
     * Calibrated SABR alpha.
     */
    public double alpha() {
        ensureCalculated();
        return sabrInterpolation_.alpha();
    }

    /**
     * Calibrated SABR beta.
     */
    public double beta() {
        ensureCalculated();
        return sabrInterpolation_.beta();
    }

    /**
     * Calibrated SABR nu (vol of vol).
     */
    public double nu() {
        ensureCalculated();
        return sabrInterpolation_.nu();
    }

    /**
     * Calibrated SABR rho (correlation).
     */
    public double rho() {
        ensureCalculated();
        return sabrInterpolation_.rho();
    }

    /**
     * Root-mean-square error of the SABR fit.
     */
    public double rmsError() {
        ensureCalculated();
        return sabrInterpolation_.rmsError();
    }

    /**
     * Maximum point-wise error of the SABR fit.
     */
    public double maxError() {
        ensureCalculated();
        return sabrInterpolation_.maxError();
    }

    // -----------------------------------------------------------------------
    // SmileSection.update() override
    // -----------------------------------------------------------------------

    /**
     * End-criteria outcome of the calibration optimizer.
     */
    public EndCriteria.Type endCriteria() {
        ensureCalculated();
        return sabrInterpolation_.endCriteria();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Marks the section as dirty so the next accessor call will re-calibrate. Mirrors C++
     * {@code SabrInterpolatedSmileSection::update()}.
     */
    @Override
    public void update() {
        dirty_ = true;
        super.update();
    }
}
