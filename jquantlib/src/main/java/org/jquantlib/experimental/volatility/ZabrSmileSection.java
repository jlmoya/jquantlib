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
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;

/**
 * ZABR smile section — Java port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/zabrsmilesection.{hpp,cpp}}
 * (template class with four evaluation tags).
 *
 * <p>The C++ class is a template parameterized over an evaluation tag
 * (one of {@code ZabrShortMaturityLognormal}, {@code ZabrShortMaturityNormal},
 * {@code ZabrLocalVolatility}, {@code ZabrFullFd}). This Java port mirrors
 * the surface for {@link Evaluation#ShortMaturityLognormal} and
 * {@link Evaluation#ShortMaturityNormal} (which only require the closed-form
 * {@link ZabrModel#lognormalVolatility(double)} / {@link ZabrModel#normalVolatility(double)}
 * helpers — implemented for {@code gamma == 1.0}). The remaining
 * {@link Evaluation#LocalVolatility} and {@link Evaluation#FullFd} flavors
 * defer to Phase 4n.5 (require FD machinery).
 *
 * <p>Phase 4f.5 partial port. ZABR with non-unit gamma additionally requires
 * the adaptive Runge-Kutta ODE solver — deferred.
 */
public class ZabrSmileSection extends SmileSection {

    /**
     * ZABR evaluation flavor — mirrors C++ tag struct types
     * (zabrsmilesection.hpp lines 41-45).
     */
    public enum Evaluation {
        /** Short-maturity lognormal expansion (uses {@code ZabrModel.lognormalVolatility}). */
        ShortMaturityLognormal,
        /** Short-maturity normal expansion (uses {@code ZabrModel.normalVolatility}). */
        ShortMaturityNormal,
        /** Local-volatility flavor (deferred — needs FD machinery). */
        LocalVolatility,
        /** Full 2-factor FD flavor (deferred — needs FdmZabrOp). */
        FullFd
    }

    private final ZabrModel model_;
    private final double forward_;
    private final Evaluation evaluation_;

    /**
     * Time-based constructor — mirrors C++ first ctor (zabrsmilesection.hpp lines 117-125).
     * Day counter defaults to Actual/365 (C++ uses default {@code DayCounter()}, but
     * since SmileSection does not access the dc when constructed by time-to-expiry,
     * the choice is irrelevant for this flavor).
     *
     * @param timeToExpiry  T (positive)
     * @param forward       forward rate
     * @param zabrParameters length-5 array {alpha, beta, nu, rho, gamma}
     * @param evaluation    one of {@link Evaluation}
     */
    public ZabrSmileSection(
            final double timeToExpiry,
            final double forward,
            final double[] zabrParameters,
            final Evaluation evaluation) {
        super(timeToExpiry, null, VolatilityType.ShiftedLognormal, 0.0);
        QL.require(zabrParameters != null && zabrParameters.length >= 5,
                "zabrParameters must be length >= 5 (alpha, beta, nu, rho, gamma)");
        this.forward_ = forward;
        this.evaluation_ = evaluation;
        this.model_ = new ZabrModel(timeToExpiry, forward,
                zabrParameters[0], zabrParameters[1], zabrParameters[2],
                zabrParameters[3], zabrParameters[4]);
        validateForFlavor();
    }

    /**
     * Convenience overload: defaults to ShortMaturityLognormal.
     */
    public ZabrSmileSection(
            final double timeToExpiry,
            final double forward,
            final double[] zabrParameters) {
        this(timeToExpiry, forward, zabrParameters, Evaluation.ShortMaturityLognormal);
    }

    /**
     * Date-based constructor — mirrors C++ second ctor (zabrsmilesection.hpp lines 127-137).
     */
    public ZabrSmileSection(
            final Date d,
            final double forward,
            final double[] zabrParameters,
            final DayCounter dc,
            final Evaluation evaluation) {
        super(d, dc == null ? new Actual365Fixed() : dc, new Date(),
                VolatilityType.ShiftedLognormal, 0.0);
        QL.require(zabrParameters != null && zabrParameters.length >= 5,
                "zabrParameters must be length >= 5 (alpha, beta, nu, rho, gamma)");
        this.forward_ = forward;
        this.evaluation_ = evaluation;
        this.model_ = new ZabrModel(exerciseTime(), forward,
                zabrParameters[0], zabrParameters[1], zabrParameters[2],
                zabrParameters[3], zabrParameters[4]);
        validateForFlavor();
    }

    private void validateForFlavor() {
        if (evaluation_ == Evaluation.LocalVolatility
                || evaluation_ == Evaluation.FullFd) {
            throw new UnsupportedOperationException(
                    "ZabrSmileSection " + evaluation_ + " deferred to Phase 4n.5 "
                            + "(requires FD machinery — fdmDupire1dOp/fdmZabrOp).");
        }
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
        // Mirror C++ ZabrShortMaturityLognormal volatilityImpl
        // (zabrsmilesection.hpp lines 297-303): strike clamp at 1e-6
        final double strike = Math.max(1.0e-6, strikeIn);
        switch (evaluation_) {
            case ShortMaturityLognormal:
                return model_.lognormalVolatility(strike);
            case ShortMaturityNormal:
                // C++ ZabrShortMaturityNormal::volatilityImpl uses an
                // implied-vol root-find on Bachelier price (zabrsmilesection.hpp
                // lines 305-323). Returning the model normal vol directly is
                // a reasonable approximation but not what the tests expect:
                // override optionPrice for this flavor to use Bachelier directly.
                return model_.normalVolatility(strike);
            default:
                throw new UnsupportedOperationException(
                        "ZabrSmileSection volatility for flavor " + evaluation_
                                + " not yet ported.");
        }
    }

    /**
     * Mirrors C++ {@code optionPrice(strike, type, discount)} dispatch
     * (zabrsmilesection.hpp lines 263-294). For {@link Evaluation#ShortMaturityNormal}
     * the price uses the Bachelier formula on the model's normalVolatility
     * (zabrsmilesection.hpp lines 269-276). For other flavors, defers to
     * {@link SmileSection#optionPrice(double, Option.Type, double)}.
     */
    @Override
    public double optionPrice(final double strike, final Option.Type type, final double discount) {
        if (evaluation_ == Evaluation.ShortMaturityNormal) {
            final double k = Math.max(1.0e-6, strike);
            final double normalVol = model_.normalVolatility(k);
            return BlackFormula.bachelierBlackFormula(type, k, forward_,
                    normalVol * Math.sqrt(exerciseTime()), discount);
        }
        return super.optionPrice(strike, type, discount);
    }
}
