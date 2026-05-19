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
 Copyright (C) 2008 Roland Lichters
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.instruments.Protection;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Integral N-th to default engine.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::IntegralNtdEngine}
 * ({@code ql/experimental/credit/integralntdengine.{hpp,cpp}}).
 *
 * <p>Allows for varying recoveries and heterogeneous notionals (the
 * homogeneous fast path uses {@code probAtLeastNEvents}; the heterogeneous fall-back uses {@code probsBeingNthEvent}).
 *
 * <p>Phase 4m.5 work-item 6 (NTD half).
 */
public class IntegralNtdEngine extends GenericEngine< NthToDefault.ArgumentsImpl, NthToDefault.ResultsImpl > {

    private final Handle< YieldTermStructure > discountCurve;
    private final Period integrationStepSize;

    public IntegralNtdEngine(final Period integrationStep, final Handle< YieldTermStructure > discountCurve) {
        super(new NthToDefault.ArgumentsImpl(), new NthToDefault.ResultsImpl());
        this.discountCurve = discountCurve;
        this.integrationStepSize = integrationStep;
        discountCurve.addObserver(this);
    }

    private static double sum(final List< Double > xs) {
        double s = 0.0;
        for ( final double x : xs ) {
            s += x;
        }
        return s;
    }

    @Override
    public void calculate() {
        final Date today = new Settings().evaluationDate();

        results_.ntdErrorEstimate = Constants.NULL_REAL;
        results_.value = 0.0;
        results_.premiumValue = 0.0;
        results_.upfrontPremiumValue = 0.0;
        double accrualValue = 0.0;
        double claimValue = 0.0;

        // Hard-coded — homogeneous fast path. The C++ port has the same comment.
        final boolean basketIsHomogeneous = true;

        for ( final CashFlow cf : arguments_.premiumLeg ) {
            final FixedRateCoupon coupon = (FixedRateCoupon) cf;
            Date d = cf.date();
            if ( d.compareTo(discountCurve.currentLink().referenceDate()) > 0 ) {
                final double probNonTriggered = 1.0 - arguments_.basket.probAtLeastNEvents(arguments_.ntdOrder, d);
                results_.premiumValue += cf.amount() * discountCurve.currentLink().discount(d) * probNonTriggered;

                d = (coupon.accrualStartDate().compareTo(discountCurve.currentLink().referenceDate()) >= 0)
                        ? coupon.accrualStartDate()
                        : discountCurve.currentLink().referenceDate();

                Date d0 = d;
                Period stepSize = integrationStepSize;
                double defProb0 = arguments_.basket.probAtLeastNEvents(arguments_.ntdOrder, d0);
                List< Double > probsTriggering = new ArrayList<>();
                List< Double > probsTriggering1;
                do {
                    final double disc = discountCurve.currentLink().discount(d);
                    final double defProb1;
                    if ( basketIsHomogeneous ) {
                        defProb1 = arguments_.basket.probAtLeastNEvents(arguments_.ntdOrder, d);
                        claimValue -= (defProb1 - defProb0) * arguments_.basket.claim()
                                .amount(d, arguments_.notional, arguments_.basket.recoveryRate(d, 0)) * disc;
                    } else {
                        probsTriggering1 = arguments_.basket.probsBeingNthEvent(arguments_.ntdOrder, d);
                        defProb1 = sum(probsTriggering1);
                        for ( int iName = 0; iName < arguments_.basket.remainingSize(); iName++ ) {
                            claimValue -= (probsTriggering1.get(iName) - (probsTriggering.size() > iName
                                    ? probsTriggering.get(iName)
                                    : 0.0)) * arguments_.basket.claim()
                                    .amount(d, arguments_.notional, arguments_.basket.recoveryRate(d, iName)) * disc;
                        }
                        probsTriggering = probsTriggering1;
                    }
                    final double dcfdd = defProb1 - defProb0;
                    defProb0 = defProb1;

                    if ( arguments_.settlePremiumAccrual ) {
                        accrualValue += coupon.accruedAmount(d) * disc * dcfdd;
                    }
                    d0 = d;
                    d = d0.add(stepSize);
                    if ( !stepSize.equals(new Period(1, TimeUnit.Days)) && d.compareTo(coupon.accrualEndDate()) > 0 ) {
                        stepSize = new Period(1, TimeUnit.Days);
                        d = d0.add(stepSize);
                    }
                } while ( d.compareTo(coupon.accrualEndDate()) <= 0 );
            }
        }

        final CashFlow firstFlow = arguments_.premiumLeg.get(0);
        if ( !firstFlow.hasOccurred(today) ) {
            results_.upfrontPremiumValue =
                    arguments_.basket.remainingNotional() * arguments_.upfrontRate * discountCurve.currentLink()
                            .discount(((FixedRateCoupon) firstFlow).accrualStartDate());
        }

        if ( arguments_.side == Protection.Side.Buyer ) {
            results_.premiumValue *= -1;
            accrualValue *= -1;
            claimValue *= -1;
            results_.upfrontPremiumValue *= -1;
        }

        results_.value = results_.premiumValue + accrualValue + claimValue + results_.upfrontPremiumValue;
        results_.fairPremium = -arguments_.premiumRate * claimValue / (results_.premiumValue + accrualValue);
        results_.protectionValue = claimValue;

        results_.additionalResults().put("fairPremium", results_.fairPremium);
        results_.additionalResults().put("premiumLegNPV", results_.premiumValue + results_.upfrontPremiumValue);
        results_.additionalResults().put("protectionLegNPV", results_.protectionValue);
    }
}
