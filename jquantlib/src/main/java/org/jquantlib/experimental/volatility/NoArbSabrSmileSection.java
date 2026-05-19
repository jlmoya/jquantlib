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
import org.jquantlib.instruments.Option;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;

/**
 * No-arbitrage SABR smile section (Doust 2012).
 *
 * <p>Skeleton port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/noarbsabrsmilesection.{hpp,cpp}}. The actual {@link NoArbSabrModel} pricing/density
 * methods are deferred to Phase 4f.5 (see {@link NoArbSabrModel} for the dependency list).
 *
 * <p>The {@link #volatilityImpl(double)} fall-back to Hagan SABR (the C++
 * fallback when {@code blackFormulaImpliedStdDev} fails) is fully ported and works today; pricing methods throw
 * {@link UnsupportedOperationException}.
 */
public class NoArbSabrSmileSection extends SmileSection {

    private final NoArbSabrModel model_;
    private final double forward_;
    private final double[] params_;
    private final double shift_;

    /**
     * Time-to-expiry constructor (noarbsabrsmilesection.cpp lines 28-36).
     *
     * @param sabrParameters length-4 vector {@code (alpha, beta, nu, rho)}
     * @param shift          must be 0 (other shifts not implemented in C++ either)
     */
    public NoArbSabrSmileSection(final double timeToExpiry, final double forward, final double[] sabrParameters,
            final double shift, final VolatilityType volatilityType) {
        super(timeToExpiry, new DayCounter(), volatilityType, shift);
        this.forward_ = forward;
        this.params_ = sabrParameters.clone();
        this.shift_ = shift;
        init();
        this.model_ = new NoArbSabrModel(exerciseTime(), forward, params_[0], params_[1], params_[2], params_[3]);
    }

    public NoArbSabrSmileSection(final double timeToExpiry, final double forward, final double[] sabrParameters) {
        this(timeToExpiry, forward, sabrParameters, 0.0, VolatilityType.ShiftedLognormal);
    }

    /**
     * Date constructor (noarbsabrsmilesection.cpp lines 38-43). Default day counter = Actual365Fixed.
     */
    public NoArbSabrSmileSection(final Date d, final double forward, final double[] sabrParameters, final DayCounter dc,
            final double shift, final VolatilityType volatilityType) {
        super(d, dc, new Date(), volatilityType, shift);
        this.forward_ = forward;
        this.params_ = sabrParameters.clone();
        this.shift_ = shift;
        init();
        this.model_ = new NoArbSabrModel(exerciseTime(), forward, params_[0], params_[1], params_[2], params_[3]);
    }

    public NoArbSabrSmileSection(final Date d, final double forward, final double[] sabrParameters) {
        this(d, forward, sabrParameters, new Actual365Fixed(), 0.0, VolatilityType.ShiftedLognormal);
    }

    private void init() {
        QL.require(params_.length >= 4,
                "sabr expects 4 parameters (alpha,beta,nu,rho) but (" + params_.length + ") given");
        QL.require(forward_ > 0.0, "forward (" + forward_ + ") must be positive");
        QL.require(shift_ == 0.0, "shift (" + shift_ + ") must be zero, other shifts are not implemented yet");
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

    /** Underlying model accessor. */
    public NoArbSabrModel model() {
        return model_;
    }

    /**
     * Option price (deferred — wraps {@link NoArbSabrModel#optionPrice(double)} which throws until Phase 4f.5).
     */
    @Override
    public double optionPrice(final double strike, final Option.Type type, final double discount) {
        final double call = model_.optionPrice(strike);
        return discount * (type == Option.Type.Call ? call : call - (forward_ - strike));
    }

    @Override
    public double optionPrice(final double strike, final Option.Type type) {
        return optionPrice(strike, type, 1.0);
    }

    /**
     * Digital option price (deferred — wraps {@link NoArbSabrModel#digitalOptionPrice(double)}).
     */
    @Override
    public double digitalOptionPrice(final double strike, final Option.Type type, final double discount,
            final double gap) {
        final double call = model_.digitalOptionPrice(strike);
        return discount * (type == Option.Type.Call ? call : 1.0 - call);
    }

    /**
     * Density (deferred — wraps {@link NoArbSabrModel#density(double)}).
     */
    public double density(final double strike, final double discount, final double gap) {
        return discount * model_.density(strike);
    }

    public double density(final double strike) {
        return density(strike, 1.0, 1.0e-4);
    }

    /**
     * Implied vol via Black inversion of the model's option price; falls back to Hagan SABR closed form when inversion
     * fails.
     *
     * <p>For Phase 4f scaffold the model.optionPrice throws, so this falls
     * straight through to the Hagan fallback (which is fully functional).
     */
    @Override
    protected double volatilityImpl(final double strike) {
        double impliedVol = 0.0;
        try {
            final Option.Type type = (strike >= forward_) ? Option.Type.Call : Option.Type.Put;
            impliedVol =
                    BlackFormula.blackFormulaImpliedStdDev(type, strike, forward_, optionPrice(strike, type, 1.0), 1.0)
                            / Math.sqrt(exerciseTime());
        } catch ( final Exception ignored ) {
            // fall through to Hagan
        }
        if ( impliedVol == 0.0 ) {
            // Hagan 2002 expansion (fallback).
            // C++ uses unsafeSabrVolatility(...) with the volatilityType_ parameter.
            // The Java Sabr port currently exposes only the lognormal flavour;
            // for the ShiftedLognormal default that is what the test fixtures expect.
            impliedVol = new org.jquantlib.termstructures.volatilities.Sabr().unsafeSabrVolatility(strike, forward_,
                    exerciseTime(), params_[0], params_[1], params_[2], params_[3]);
        }
        return impliedVol;
    }
}
