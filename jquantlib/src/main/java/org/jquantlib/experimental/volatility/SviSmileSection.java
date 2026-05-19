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
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;

/**
 * Stochastic Volatility Inspired (SVI) smile section.
 *
 * <p>Faithful port of QuantLib C++ v1.42.1
 * {@code ql/experimental/volatility/svismilesection.{hpp,cpp}}. The smile is fully described by 5 parameters
 * {@code (a, b, sigma, rho, m)} and the forward.
 *
 * <p>Volatility for strike {@code K}:
 * <pre>
 *   k = log(K / forward)
 *   w(k) = a + b * (rho * (k - m) + sqrt((k - m)^2 + sigma^2))
 *   sigma_BS(K) = sqrt(max(0, w(k) / T))
 * </pre>
 */
public class SviSmileSection extends SmileSection {

    private final double forward_;
    private final double[] params_;

    /**
     * Time-to-expiry constructor (sviinterpolation.hpp / svismilesection.cpp lines 26-29).
     *
     * @param timeToExpiry  strictly positive time to expiry
     * @param forward       forward at expiry
     * @param sviParameters length-5 vector {@code (a, b, sigma, rho, m)}
     */
    public SviSmileSection(final double timeToExpiry, final double forward, final double[] sviParameters) {
        super(timeToExpiry, new DayCounter());
        this.forward_ = forward;
        this.params_ = sviParameters.clone();
        init();
    }

    /**
     * Date constructor (svismilesection.cpp lines 31-37). Day counter defaults to {@link Actual365Fixed}.
     */
    public SviSmileSection(final Date d, final double forward, final double[] sviParameters, final DayCounter dc) {
        super(d, dc, new Date());
        this.forward_ = forward;
        this.params_ = sviParameters.clone();
        init();
    }

    public SviSmileSection(final Date d, final double forward, final double[] sviParameters) {
        this(d, forward, sviParameters, new Actual365Fixed());
    }

    private void init() {
        QL.require(exerciseTime() > 0.0, "svi expects a strictly positive expiry time");
        QL.require(params_.length == 5,
                "svi expects 5 parameters (a,b,sigma,rho,m) but (" + params_.length + ") given");
        SviInterpolation.checkSviParameters(params_[0], params_[1], params_[2], params_[3], params_[4], exerciseTime());
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

    @Override
    protected double volatilityImpl(final double strike) {
        // svismilesection.cpp lines 48-55.
        final double k = Math.log(Math.max(strike, 1.0e-6) / forward_);
        final double w = SviInterpolation.sviTotalVariance(params_[0], params_[1], params_[2], params_[3], params_[4],
                k);
        return Math.sqrt(Math.max(0.0, w / exerciseTime()));
    }
}
