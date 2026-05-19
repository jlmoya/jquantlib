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

/**
 * MidPoint CDO engine — mezzanine CDO tranche pricing on schedule steps.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::MidPointCDOEngine}
 * ({@code ql/experimental/credit/midpointcdoengine.{hpp,cpp}}).
 *
 * <p>Reads the basket reference from the {@code SyntheticCdo.Arguments}
 * and assumes a {@link DefaultLossModel} has been attached via {@code basket.setLossModel(...)}. Prices the protection
 * and premium legs by computing the expected tranche loss at each schedule date and applying mid-period default
 * approximations.
 *
 * <p>Phase 4m.5 work-item 6 (CDO half).
 */
public class MidPointCdoEngine extends GenericEngine< SyntheticCdo.ArgumentsImpl, SyntheticCdo.ResultsImpl > {

    private final Handle< YieldTermStructure > discountCurve;

    public MidPointCdoEngine(final Handle< YieldTermStructure > discountCurve) {
        super(new SyntheticCdo.ArgumentsImpl(), new SyntheticCdo.ResultsImpl());
        this.discountCurve = discountCurve;
        discountCurve.addObserver(this);
    }

    private static Date max(final Date a, final Date b) {
        return (a.compareTo(b) >= 0) ? a : b;
    }

    @Override
    public void calculate() {
        final Date today = new Settings().evaluationDate();

        results_.premiumValue = 0.0;
        results_.protectionValue = 0.0;
        results_.upfrontPremiumValue = 0.0;
        results_.error = 0;
        results_.expectedTrancheLoss.clear();
        // todo: should be remaining when considering realized loses
        results_.xMin = arguments_.basket.attachmentAmount();
        results_.xMax = arguments_.basket.detachmentAmount();
        results_.remainingNotional = results_.xMax - results_.xMin;
        final double inceptionTrancheNotional = arguments_.basket.trancheNotional();

        // Compute expected loss at the beginning of the first relevant period.
        double e1 = 0;
        final CashFlow firstFlow = arguments_.normalizedLeg.get(0);
        if ( !firstFlow.hasOccurred(today) ) {
            e1 = arguments_.basket.expectedTrancheLoss(((Coupon) firstFlow).accrualStartDate());
        }
        results_.expectedTrancheLoss.add(e1);

        for ( final CashFlow cf : arguments_.normalizedLeg ) {
            if ( cf.hasOccurred(today) ) {
                results_.expectedTrancheLoss.add(0.0);
                continue;
            }
            final Coupon coupon = (Coupon) cf;
            final Date paymentDate = coupon.date();
            final Date startDate = max(coupon.accrualStartDate(), discountCurve.currentLink().referenceDate());
            final Date endDate = coupon.accrualEndDate();
            // Loss within the period assumed to take place mid-period.
            final Date defaultDate = startDate.add((int) ((endDate.serialNumber() - startDate.serialNumber()) / 2));

            final double e2 = arguments_.basket.expectedTrancheLoss(endDate);
            results_.expectedTrancheLoss.add(e2);
            results_.premiumValue += ((inceptionTrancheNotional - e2) / inceptionTrancheNotional) * coupon.amount()
                    * discountCurve.currentLink().discount(paymentDate);
            // default flows
            final double discount = discountCurve.currentLink().discount(defaultDate);
            results_.protectionValue += discount * (e2 - e1);
            e1 = e2;
        }

        if ( !firstFlow.hasOccurred(today) ) {
            results_.upfrontPremiumValue =
                    inceptionTrancheNotional * arguments_.upfrontRate * discountCurve.currentLink()
                            .discount(((Coupon) firstFlow).accrualStartDate());
        }
        if ( arguments_.side == Protection.Side.Buyer ) {
            results_.protectionValue *= -1;
            results_.premiumValue *= -1;
            results_.upfrontPremiumValue *= -1;
        }
        results_.value = results_.premiumValue - results_.protectionValue + results_.upfrontPremiumValue;
        results_.errorEstimate = Constants.NULL_REAL;

        // Fair spread given the upfront
        double fairSpread = 0.0;
        if ( results_.premiumValue != 0.0 ) {
            fairSpread = -(results_.protectionValue + results_.upfrontPremiumValue) * arguments_.runningRate
                    / results_.premiumValue;
        }
        results_.additionalResults().put("fairPremium", fairSpread);
        results_.additionalResults().put("premiumLegNPV", results_.premiumValue + results_.upfrontPremiumValue);
        results_.additionalResults().put("protectionLegNPV", results_.protectionValue);
    }
}
