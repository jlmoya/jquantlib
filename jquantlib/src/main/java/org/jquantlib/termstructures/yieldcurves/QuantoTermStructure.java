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
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Quanto term structure for modelling quanto effect in option pricing.
 *
 * <p>Phase 5i.5-MGR port of {@code QuantLib::QuantoTermStructure}
 * (v1.42.1 ql/termstructures/yield/quantotermstructure.hpp).
 *
 * <p>This term structure remains linked to the underlying structures, so
 * any changes in the latter are reflected here.
 */
public class QuantoTermStructure extends ZeroYieldStructure {

    private final Handle< YieldTermStructure > underlyingDividendTS_;
    private final Handle< YieldTermStructure > riskFreeTS_;
    private final Handle< YieldTermStructure > foreignRiskFreeTS_;
    private final Handle< BlackVolTermStructure > underlyingBlackVolTS_;
    private final Handle< BlackVolTermStructure > exchRateBlackVolTS_;
    private final double underlyingExchRateCorrelation_;
    private final double strike_;
    private final double exchRateATMlevel_;

    public QuantoTermStructure(final Handle< YieldTermStructure > underlyingDividendTS,
            final Handle< YieldTermStructure > riskFreeTS, final Handle< YieldTermStructure > foreignRiskFreeTS,
            final Handle< BlackVolTermStructure > underlyingBlackVolTS, final double strike,
            final Handle< BlackVolTermStructure > exchRateBlackVolTS, final double exchRateATMlevel,
            final double underlyingExchRateCorrelation) {
        super(underlyingDividendTS.currentLink().dayCounter());
        this.underlyingDividendTS_ = underlyingDividendTS;
        this.riskFreeTS_ = riskFreeTS;
        this.foreignRiskFreeTS_ = foreignRiskFreeTS;
        this.underlyingBlackVolTS_ = underlyingBlackVolTS;
        this.exchRateBlackVolTS_ = exchRateBlackVolTS;
        this.underlyingExchRateCorrelation_ = underlyingExchRateCorrelation;
        this.strike_ = strike;
        this.exchRateATMlevel_ = exchRateATMlevel;

        this.underlyingDividendTS_.addObserver(this);
        this.riskFreeTS_.addObserver(this);
        this.foreignRiskFreeTS_.addObserver(this);
        this.underlyingBlackVolTS_.addObserver(this);
        this.exchRateBlackVolTS_.addObserver(this);
    }

    @Override
    public DayCounter dayCounter() {
        return underlyingDividendTS_.currentLink().dayCounter();
    }

    @Override
    public Calendar calendar() {
        return underlyingDividendTS_.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return underlyingDividendTS_.currentLink().settlementDays();
    }

    @Override
    public Date referenceDate() {
        return underlyingDividendTS_.currentLink().referenceDate();
    }

    @Override
    public Date maxDate() {
        Date m = underlyingDividendTS_.currentLink().maxDate();
        if ( riskFreeTS_.currentLink().maxDate().lt(m) ) {
            m = riskFreeTS_.currentLink().maxDate();
        }
        if ( foreignRiskFreeTS_.currentLink().maxDate().lt(m) ) {
            m = foreignRiskFreeTS_.currentLink().maxDate();
        }
        if ( underlyingBlackVolTS_.currentLink().maxDate().lt(m) ) {
            m = underlyingBlackVolTS_.currentLink().maxDate();
        }
        if ( exchRateBlackVolTS_.currentLink().maxDate().lt(m) ) {
            m = exchRateBlackVolTS_.currentLink().maxDate();
        }
        return m;
    }

    @Override
    protected double zeroYieldImpl(final double t) {
        // warning: assumes all TS use the same daycount.
        return underlyingDividendTS_.currentLink().zeroRate(t, Compounding.Continuous, Frequency.NoFrequency, true)
                .rate() + riskFreeTS_.currentLink().zeroRate(t, Compounding.Continuous, Frequency.NoFrequency, true)
                .rate() - foreignRiskFreeTS_.currentLink()
                .zeroRate(t, Compounding.Continuous, Frequency.NoFrequency, true).rate()
                + underlyingExchRateCorrelation_ * underlyingBlackVolTS_.currentLink().blackVol(t, strike_, true)
                * exchRateBlackVolTS_.currentLink().blackVol(t, exchRateATMlevel_, true);
    }
}
