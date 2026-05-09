/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2008 Roland Stamm
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Protection;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Black-formula CDS-option engine.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::BlackCdsOptionEngine}
 * ({@code ql/experimental/credit/blackcdsoptionengine.{hpp,cpp}}).
 *
 * <p>Assumes the exercise date equals the start date of the passed CDS.
 *
 * <p>Phase 4m.5 work-item 3.
 */
public class BlackCdsOptionEngine
        extends GenericEngine<CdsOption.ArgumentsImpl, CdsOption.ResultsImpl> {

    private final Handle<DefaultProbabilityTermStructure> probability;
    private final double recoveryRate;
    private final Handle<YieldTermStructure> termStructure;
    private final Handle<Quote> volatility;

    public BlackCdsOptionEngine(final Handle<DefaultProbabilityTermStructure> probability,
                                final double recoveryRate,
                                final Handle<YieldTermStructure> termStructure,
                                final Handle<Quote> volatility) {
        super(new CdsOption.ArgumentsImpl(), new CdsOption.ResultsImpl());
        this.probability = probability;
        this.recoveryRate = recoveryRate;
        this.termStructure = termStructure;
        this.volatility = volatility;

        probability.addObserver(this);
        termStructure.addObserver(this);
        volatility.addObserver(this);
    }

    @Override
    public void calculate() {
        final CashFlow firstCoupon = arguments_.leg.get(0);
        final Date maturityDate = firstCoupon.date();
        final Date exerciseDate = arguments_.exercise.date(0);
        if (maturityDate.compareTo(exerciseDate) <= 0) {
            throw new IllegalStateException("Underlying CDS should start after option maturity");
        }
        final Date settlement = termStructure.currentLink().referenceDate();

        // For atm/strike rates we need to query the underlying CDS — do this
        // through the swap reference saved in arguments.
        final CreditDefaultSwap swap = arguments_.swap;
        final double spotFwdSpread = swap.fairSpread();
        final double swapSpread = swap.runningSpread();

        final DayCounter tSDc = termStructure.currentLink().dayCounter();

        // C++: std::fabs(swap.couponLegNPV() / swapSpread)
        final double riskyAnnuity = Math.abs(swap.couponLegNPV() / swapSpread);
        results_.riskyAnnuity = riskyAnnuity;

        final double T = tSDc.yearFraction(settlement, exerciseDate);
        final double stdDev = volatility.currentLink().value() * Math.sqrt(T);
        final Option.Type callPut = (arguments_.side == Protection.Side.Buyer)
                ? Option.Type.Call : Option.Type.Put;

        results_.value = BlackFormula.blackFormula(callPut, swapSpread, spotFwdSpread,
                stdDev, riskyAnnuity);

        // If a non knock-out payer option, add front-end protection value.
        if (arguments_.side == Protection.Side.Buyer && !arguments_.knocksOut) {
            final double frontEndProtection = callPut.toInteger() * arguments_.notional
                    * (1.0 - recoveryRate)
                    * probability.currentLink().defaultProbability(exerciseDate)
                    * termStructure.currentLink().discount(exerciseDate);
            results_.value += frontEndProtection;
        }
    }

    public Handle<YieldTermStructure> termStructure() {
        return termStructure;
    }

    public Handle<Quote> volatility() {
        return volatility;
    }
}
