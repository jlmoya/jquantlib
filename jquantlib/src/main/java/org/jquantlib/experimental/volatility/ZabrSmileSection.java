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
*/

/*
 Copyright (C) 2014 Peter Caspers
 Copyright (C) 2026 Aaditya Panikath

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * ZABR smile section — Java port of QuantLib v1.42.1 {@code ql/termstructures/volatility/zabrsmilesection.{hpp,cpp}}
 * (template class with four evaluation tags).
 *
 * <p>The C++ class is a template parameterized over an evaluation tag
 * (one of {@code ZabrShortMaturityLognormal}, {@code ZabrShortMaturityNormal}, {@code ZabrLocalVolatility},
 * {@code ZabrFullFd}). This Java port collapses the four template specializations into a single class with an
 * {@link Evaluation enum} discriminator and runtime dispatch in {@link #init init} / {@link #optionPrice optionPrice} /
 * {@link #volatilityImpl volatilityImpl}.
 *
 * <h2>Phase history</h2>
 *
 * <ul>
 *   <li><b>Phase 4f.5</b> — {@link Evaluation#ShortMaturityLognormal} +
 *       {@link Evaluation#ShortMaturityNormal} flavors (closed-form vols).</li>
 *   <li><b>Phase 4f.5c</b> — {@link Evaluation#LocalVolatility} +
 *       {@link Evaluation#FullFd} flavors. The LocalVol path uses
 *       {@link ZabrModel#fdPrice(double)} (Dupire 1-D FD); the FullFd path
 *       uses {@link ZabrModel#fullFdPrice(double)} (FdmZabrOp 2-D FD).
 *       Both flavors build a strike grid (default 21-pt moneyness ×
 *       {@code fdRefinement} subgrid), evaluate FD prices on it, then
 *       interpolate via cubic spline with exponential right-tail extrapolation
 *       (matches C++ {@code init3(ZabrLocalVolatility)} / {@code init3(ZabrFullFd)}).</li>
 * </ul>
 */
public class ZabrSmileSection extends SmileSection {

    /** Default moneyness grid — mirror C++ defaultMoney[21] (zabrsmilesection.hpp lines 168-170). */
    private static final double[] DEFAULT_MONEYNESS = { 0.0, 0.01, 0.05, 0.10, 0.25, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90,
            1.0, 1.25, 1.5, 1.75, 2.0, 5.0, 7.5, 10.0, 15.0, 20.0 };
    private final ZabrModel model_;
    private final double forward_;
    private final Evaluation evaluation_;
    private final int fdRefinement_;
    // FD-flavor state — populated by initFd() for LocalVolatility / FullFd.
    private double[] strikes_;
    private double[] callPrices_;
    private CubicInterpolation callPriceFct_;
    private double a_;
    private double b_;
    /**
     * Time-based constructor — mirrors C++ first ctor (zabrsmilesection.hpp lines 117-125). Day counter defaults to
     * Actual/365 (C++ uses default {@code DayCounter()}, but since SmileSection does not access the dc when constructed
     * by time-to-expiry, the choice is irrelevant for this flavor).
     *
     * @param timeToExpiry   T (positive)
     * @param forward        forward rate
     * @param zabrParameters length-5 array {alpha, beta, nu, rho, gamma}
     * @param evaluation     one of {@link Evaluation}
     */
    public ZabrSmileSection(final double timeToExpiry, final double forward, final double[] zabrParameters,
            final Evaluation evaluation) {
        this(timeToExpiry, forward, zabrParameters, evaluation, null, 5);
    }

    /**
     * Full-args time constructor — supports LocalVolatility / FullFd flavors (zabrsmilesection.hpp lines 117-125 +
     * init).
     *
     * @param moneyness    optional moneyness grid (null/empty → default 21-pt)
     * @param fdRefinement number of sub-grid refinements between two moneyness ticks (default 5; ignored for non-FD
     *                     flavors)
     */
    public ZabrSmileSection(final double timeToExpiry, final double forward, final double[] zabrParameters,
            final Evaluation evaluation, final double[] moneyness, final int fdRefinement) {
        super(timeToExpiry, null, VolatilityType.ShiftedLognormal, 0.0);
        QL.require(zabrParameters != null && zabrParameters.length >= 5,
                "zabrParameters must be length >= 5 (alpha, beta, nu, rho, gamma)");
        this.forward_ = forward;
        this.evaluation_ = evaluation;
        this.fdRefinement_ = fdRefinement;
        this.model_ = new ZabrModel(timeToExpiry, forward, zabrParameters[0], zabrParameters[1], zabrParameters[2],
                zabrParameters[3], zabrParameters[4]);
        initFd(moneyness);
    }

    /**
     * Convenience overload: defaults to ShortMaturityLognormal.
     */
    public ZabrSmileSection(final double timeToExpiry, final double forward, final double[] zabrParameters) {
        this(timeToExpiry, forward, zabrParameters, Evaluation.ShortMaturityLognormal);
    }

    /**
     * Date-based constructor — mirrors C++ second ctor (zabrsmilesection.hpp lines 127-137).
     */
    public ZabrSmileSection(final Date d, final double forward, final double[] zabrParameters, final DayCounter dc,
            final Evaluation evaluation) {
        this(d, forward, zabrParameters, dc, evaluation, null, 5);
    }

    /**
     * Full-args date constructor — supports LocalVolatility / FullFd flavors (zabrsmilesection.hpp lines 127-137 +
     * init).
     */
    public ZabrSmileSection(final Date d, final double forward, final double[] zabrParameters, final DayCounter dc,
            final Evaluation evaluation, final double[] moneyness, final int fdRefinement) {
        super(d, dc == null ? new Actual365Fixed() : dc, new Date(), VolatilityType.ShiftedLognormal, 0.0);
        QL.require(zabrParameters != null && zabrParameters.length >= 5,
                "zabrParameters must be length >= 5 (alpha, beta, nu, rho, gamma)");
        this.forward_ = forward;
        this.evaluation_ = evaluation;
        this.fdRefinement_ = fdRefinement;
        this.model_ = new ZabrModel(exerciseTime(), forward, zabrParameters[0], zabrParameters[1], zabrParameters[2],
                zabrParameters[3], zabrParameters[4]);
        initFd(moneyness);
    }

    /**
     * For FD flavors (LocalVolatility / FullFd): build the strike grid, evaluate FD prices on it, then build the
     * cubic-spline interpolation + exponential right-tail extrapolation parameters {@code a_}, {@code b_}. Mirror C++
     * {@code init/init2/init3(ZabrLocalVolatility|ZabrFullFd)} (zabrsmilesection.hpp lines 154-260).
     *
     * <p>For non-FD flavors this method is a no-op.
     */
    private void initFd(final double[] moneyness) {
        if ( evaluation_ != Evaluation.LocalVolatility && evaluation_ != Evaluation.FullFd ) {
            return;
        }

        // ----- init() — strike grid (zabrsmilesection.hpp lines 154-195)
        final double[] tmp = (moneyness == null || moneyness.length == 0) ? DEFAULT_MONEYNESS : moneyness;

        final List< Double > strikesList = new ArrayList< Double >();
        double lastF = 0.0;
        boolean firstStrike = true;
        for ( final double i : tmp ) {
            final double f = i * forward_;
            if ( f > 0.0 ) {
                if ( !firstStrike ) {
                    for ( int j = 1; j <= fdRefinement_; ++j ) {
                        strikesList.add(lastF + ((double) j) * (f - lastF) / (fdRefinement_ + 1));
                    }
                }
                firstStrike = false;
                lastF = f;
                strikesList.add(f);
            }
        }

        // ----- init2() — fill callPrices_ (zabrsmilesection.hpp lines 209-221)
        final int nStrike = strikesList.size();
        final double[] cp = new double[nStrike];
        if ( evaluation_ == Evaluation.LocalVolatility ) {
            // C++ uses callPrices_ = model_->fdPrice(strikes_) which builds
            // the FD grid once then evaluates the spline at every requested
            // strike. The Java ZabrModel only exposes the scalar form, so we
            // call it per strike (slower but element-by-element identical
            // because the FD grid parameters depend only on the model + the
            // single strike argument; calling it per-strike rebuilds the grid
            // each time but produces the same value at the requested point).
            for ( int i = 0; i < nStrike; ++i ) {
                cp[i] = model_.fdPrice(strikesList.get(i));
            }
        } else { // FullFd
            // C++ uses #pragma omp parallel for; Java port runs sequentially.
            for ( int i = 0; i < nStrike; ++i ) {
                cp[i] = model_.fullFdPrice(strikesList.get(i));
            }
        }

        // ----- init3() — insert (0, forward) at front + cubic spline
        //                + exponential right-tail extrapolation
        //                (zabrsmilesection.hpp lines 230-254)
        strikes_ = new double[nStrike + 1];
        callPrices_ = new double[nStrike + 1];
        strikes_[0] = 0.0;
        callPrices_[0] = forward_;
        for ( int i = 0; i < nStrike; ++i ) {
            strikes_[i + 1] = strikesList.get(i);
            callPrices_[i + 1] = cp[i];
        }

        callPriceFct_ = new CubicInterpolation(new Array(strikes_), new Array(callPrices_),
                CubicInterpolation.DerivativeApprox.Spline, true, CubicInterpolation.BoundaryCondition.SecondDerivative,
                0.0, CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        callPriceFct_.enableExtrapolation();

        // Exponential right-tail extrapolation (matches C++ init3).
        // C++: eps = 1e-5 — gap for first derivative.
        final double eps = 1.0e-5;
        final double last = strikes_[strikes_.length - 1];
        final double c0 = callPriceFct_.op(last);
        final double c0p = (callPriceFct_.op(last - eps) - c0) / eps;

        a_ = c0p / c0;
        b_ = Math.log(c0) + a_ * last;
    }

    @Override
    public double minStrike() {
        return 0.0;
    }

    @Override
    public double maxStrike() {
        return Double.MAX_VALUE;
    }

    @Override
    public double atmLevel() {
        return forward_;
    }

    /** Underlying {@link ZabrModel}. */
    public ZabrModel model() {
        return model_;
    }

    /** Evaluation flavor of this section. */
    public Evaluation evaluation() {
        return evaluation_;
    }

    @Override
    protected double volatilityImpl(final double strikeIn) {
        switch ( evaluation_ ) {
        case ShortMaturityLognormal: {
            // Mirror C++ ZabrShortMaturityLognormal volatilityImpl
            // (zabrsmilesection.hpp lines 297-303): strike clamp at 1e-6.
            final double strike = Math.max(1.0e-6, strikeIn);
            return model_.lognormalVolatility(strike);
        }
        case ShortMaturityNormal:
        case LocalVolatility:
        case FullFd: {
            // Mirror C++ ZabrShortMaturityNormal volatilityImpl + the
            // LocalVol/FullFd specializations that delegate to it
            // (zabrsmilesection.hpp lines 305-335): implied vol via Black
            // inversion of the model's call/put price; on failure return 0.
            double impliedVol = 0.0;
            try {
                final Option.Type type = (strikeIn >= model_.forward()) ? Option.Type.Call : Option.Type.Put;
                impliedVol = BlackFormula.blackFormulaImpliedStdDev(type, strikeIn, model_.forward(),
                        optionPrice(strikeIn, type, 1.0), 1.0) / Math.sqrt(exerciseTime());
            } catch ( final Exception ignored ) {
                // C++ catches all and returns 0 — matches.
            }
            return impliedVol;
        }
        default:
            throw new UnsupportedOperationException(
                    "ZabrSmileSection volatility for flavor " + evaluation_ + " not yet ported.");
        }
    }

    /**
     * Mirrors C++ {@code optionPrice(strike, type, discount)} dispatch (zabrsmilesection.hpp lines 263-294).
     * <ul>
     *   <li>{@link Evaluation#ShortMaturityLognormal} — defers to
     *       {@link SmileSection#optionPrice(double, Option.Type, double)}
     *       which uses the Black formula on
     *       {@link #volatilityImpl(double)}.</li>
     *   <li>{@link Evaluation#ShortMaturityNormal} — Bachelier formula on
     *       {@link ZabrModel#normalVolatility(double)} (lines 269-276).</li>
     *   <li>{@link Evaluation#LocalVolatility} / {@link Evaluation#FullFd} —
     *       reads call price directly from the cubic-spline interpolation +
     *       exponential right-tail extrapolation (lines 278-288); converts
     *       to put via parity {@code P = C - (F - K)}.</li>
     * </ul>
     */
    @Override
    public double optionPrice(final double strike, final Option.Type type, final double discount) {
        switch ( evaluation_ ) {
        case ShortMaturityNormal: {
            final double k = Math.max(1.0e-6, strike);
            final double normalVol = model_.normalVolatility(k);
            return BlackFormula.bachelierBlackFormula(type, k, forward_, normalVol * Math.sqrt(exerciseTime()),
                    discount);
        }
        case LocalVolatility:
        case FullFd: {
            final double last = strikes_[strikes_.length - 1];
            final double call = (strike <= last) ? callPriceFct_.op(strike) : Math.exp(-a_ * strike + b_);
            if ( type == Option.Type.Call ) {
                return call * discount;
            }
            // Put-call parity.
            return (call - (forward_ - strike)) * discount;
        }
        case ShortMaturityLognormal:
        default:
            return super.optionPrice(strike, type, discount);
        }
    }

    /**
     * ZABR evaluation flavor — mirrors C++ tag struct types (zabrsmilesection.hpp lines 41-45).
     */
    public enum Evaluation {
        /** Short-maturity lognormal expansion (uses {@code ZabrModel.lognormalVolatility}). */
        ShortMaturityLognormal,
        /** Short-maturity normal expansion (uses {@code ZabrModel.normalVolatility}). */
        ShortMaturityNormal,
        /** Local-volatility flavor — uses {@code ZabrModel.fdPrice} (Phase 4f.5c). */
        LocalVolatility,
        /** Full 2-factor FD flavor — uses {@code ZabrModel.fullFdPrice} (Phase 4f.5c). */
        FullFd
    }
}
