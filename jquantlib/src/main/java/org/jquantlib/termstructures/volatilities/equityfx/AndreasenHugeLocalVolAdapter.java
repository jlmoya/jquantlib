/*
 Copyright (C) 2017, 2018 Klaus Spanderen

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
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * LocalVolTermStructure adapter backed by an AndreasenHugeVolatilityInterpl.
 *
 * <p>Java port of v1.42.1
 * ql/termstructures/volatility/equityfx/andreasenhugelocalvoladapter.{hpp,cpp}
 *
 * @author Phase 2m Track D port
 */
public class AndreasenHugeLocalVolAdapter extends LocalVolTermStructure {

    private final AndreasenHugeVolatilityInterpl localVol_;

    public AndreasenHugeLocalVolAdapter(
            final AndreasenHugeVolatilityInterpl localVol) {
        this.localVol_ = localVol;
    }

    @Override
    protected double localVolImpl(final double t, final double strike) {
        return localVol_.localVol(t,
                Math.min(localVol_.maxStrike(),
                         Math.max(localVol_.minStrike(), strike)));
    }

    @Override
    public Date maxDate() {
        return localVol_.maxDate();
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
    public Calendar calendar() {
        return localVol_.riskFreeRate().currentLink().calendar();
    }

    @Override
    public DayCounter dayCounter() {
        return localVol_.riskFreeRate().currentLink().dayCounter();
    }

    @Override
    public Date referenceDate() {
        return localVol_.riskFreeRate().currentLink().referenceDate();
    }

    @Override
    public int settlementDays() {
        return localVol_.riskFreeRate().currentLink().settlementDays();
    }
}
