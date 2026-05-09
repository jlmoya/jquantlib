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
 Copyright (C) 2009, 2014 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.instruments.Protection;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Integral CDO engine — performs intra-coupon-period integration of
 * the protection / premium legs in {@code stepSize} sub-intervals.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::IntegralCDOEngine}
 * ({@code ql/experimental/credit/integralcdoengine.{hpp,cpp}}).
 *
 * <p>Phase 4m.5 work-item 6 (CDO integral half).
 */
public class IntegralCdoEngine
        extends GenericEngine<SyntheticCdo.ArgumentsImpl, SyntheticCdo.ResultsImpl> {

    private final Period stepSize;
    private final Handle<YieldTermStructure> discountCurve;

    public IntegralCdoEngine(final Handle<YieldTermStructure> discountCurve,
                             final Period stepSize) {
        super(new SyntheticCdo.ArgumentsImpl(), new SyntheticCdo.ResultsImpl());
        this.discountCurve = discountCurve;
        this.stepSize = stepSize;
        discountCurve.addObserver(this);
    }

    public IntegralCdoEngine(final Handle<YieldTermStructure> discountCurve) {
        this(discountCurve, new Period(3, TimeUnit.Months));
    }

    @Override
    public void calculate() {
        final Date today = new Settings().evaluationDate();
        results_.protectionValue = 0.0;
        results_.premiumValue = 0.0;
        results_.upfrontPremiumValue = 0.0;
        results_.error = 0;
        results_.expectedTrancheLoss.clear();

        results_.xMin = arguments_.basket.attachmentAmount();
        results_.xMax = arguments_.basket.detachmentAmount();
        results_.remainingNotional = results_.xMax - results_.xMin;
        final double inceptionTrancheNotional = arguments_.basket.trancheNotional();

        double e1 = 0.0;
        final CashFlow firstFlow = arguments_.normalizedLeg.get(0);
        if (!firstFlow.hasOccurred(today)) {
            e1 = arguments_.basket.expectedTrancheLoss(
                    ((Coupon) firstFlow).accrualStartDate());
        }
        results_.expectedTrancheLoss.add(e1);

        final NullCalendar calendar = new NullCalendar();

        for (final CashFlow cf : arguments_.normalizedLeg) {
            if (cf.hasOccurred(today)) {
                results_.expectedTrancheLoss.add(0.0);
                continue;
            }
            final Coupon coupon = (Coupon) cf;
            final Date d1 = coupon.accrualStartDate();
            final Date d2 = coupon.date();
            Date d;
            Date d0 = d1;
            double e2 = 0.0;
            do {
                final Date base = (d0.compareTo(today) > 0) ? d0 : today;
                d = calendar.advance(base, stepSize);
                if (d.compareTo(d2) > 0) {
                    d = d2;
                }
                e2 = arguments_.basket.expectedTrancheLoss(d);
                results_.premiumValue += (inceptionTrancheNotional - e2)
                        * arguments_.runningRate
                        * arguments_.dayCounter.yearFraction(d0, d)
                        * discountCurve.currentLink().discount(d);
                if (e2 < e1) {
                    results_.error++;
                }
                results_.protectionValue += (e2 - e1) * discountCurve.currentLink().discount(d);
                d0 = d;
                e1 = e2;
            } while (d.compareTo(d2) < 0);
            results_.expectedTrancheLoss.add(e2);
        }

        if (!firstFlow.hasOccurred(today)) {
            results_.upfrontPremiumValue = inceptionTrancheNotional * arguments_.upfrontRate
                    * discountCurve.currentLink().discount(
                            ((Coupon) firstFlow).accrualStartDate());
        }
        if (arguments_.side == Protection.Side.Buyer) {
            results_.protectionValue *= -1;
            results_.premiumValue *= -1;
            results_.upfrontPremiumValue *= -1;
        }
        results_.value = results_.premiumValue - results_.protectionValue
                + results_.upfrontPremiumValue;
        results_.errorEstimate = Constants.NULL_REAL;

        double fairSpread = 0.0;
        if (results_.premiumValue != 0.0) {
            fairSpread = -(results_.protectionValue + results_.upfrontPremiumValue)
                    * arguments_.runningRate / results_.premiumValue;
        }
        results_.additionalResults().put("fairPremium", fairSpread);
        results_.additionalResults().put("premiumLegNPV",
                results_.premiumValue + results_.upfrontPremiumValue);
        results_.additionalResults().put("protectionLegNPV", results_.protectionValue);
    }
}
