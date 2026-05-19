/*
 Copyright (C) 2017 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the license for more details.
*/

package org.jquantlib.termstructures.volatilities.equityfx;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.termstructures.BlackVarianceTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * BlackVarianceTermStructure adapter backed by an AndreasenHugeVolatilityInterpl.
 *
 * <p>Java port of v1.42.1
 * ql/termstructures/volatility/equityfx/andreasenhugevolatilityadapter.{hpp,cpp}
 *
 * @author Phase 2m Track D port
 */
public class AndreasenHugeVolatilityAdapter extends BlackVarianceTermStructure {

    private final double eps_;
    private final AndreasenHugeVolatilityInterpl volInterpl_;

    public AndreasenHugeVolatilityAdapter(final AndreasenHugeVolatilityInterpl volInterpl, final double eps) {
        this.volInterpl_ = volInterpl;
        this.eps_ = eps;
    }

    /** Convenience constructor with default eps = 1e-6. */
    public AndreasenHugeVolatilityAdapter(final AndreasenHugeVolatilityInterpl volInterpl) {
        this(volInterpl, 1e-6);
    }

    @Override
    protected double blackVarianceImpl(final double t, final double strike) {
        final double fwd = volInterpl_.fwd(t);
        final Option.Type optionType = (fwd > strike) ? Option.Type.Put : Option.Type.Call;

        final double npv = volInterpl_.optionPrice(t, strike, optionType);
        final double discount = volInterpl_.riskFreeRate().currentLink().discount(t);

        final double stdDev = BlackFormula.blackFormulaImpliedStdDevLiRS(optionType, strike, fwd, npv, discount, 0.0,
                Double.NaN, 1.0, eps_, 1000);

        return stdDev * stdDev;
    }

    @Override
    public Date maxDate() {
        return volInterpl_.maxDate();
    }

    @Override
    public double minStrike() {
        return volInterpl_.minStrike();
    }

    @Override
    public double maxStrike() {
        return volInterpl_.maxStrike();
    }

    @Override
    public Calendar calendar() {
        return volInterpl_.riskFreeRate().currentLink().calendar();
    }

    @Override
    public DayCounter dayCounter() {
        return volInterpl_.riskFreeRate().currentLink().dayCounter();
    }

    @Override
    public Date referenceDate() {
        return volInterpl_.riskFreeRate().currentLink().referenceDate();
    }

    @Override
    public int settlementDays() {
        return volInterpl_.riskFreeRate().currentLink().settlementDays();
    }
}
