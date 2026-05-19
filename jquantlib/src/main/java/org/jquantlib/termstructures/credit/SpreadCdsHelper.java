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
 When applicable, the original copyright notice follows this notice.
*/

/*
 Copyright (C) 2008, 2009 Jose Aparicio
 Copyright (C) 2008 Chris Kenyon
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.credit;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.CreditDefaultSwap.PricingModel;
import org.jquantlib.instruments.Protection;
import org.jquantlib.pricingengines.credit.MidPointCdsEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;

/**
 * Spread-quoted CDS hazard-rate bootstrap helper.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::SpreadCdsHelper}
 * ({@code ql/termstructures/credit/defaultprobabilityhelpers.hpp:131-154},
 * {@code defaultprobabilityhelpers.cpp:110-157}).
 *
 * <p>The implied quote is the CDS's fair (par) running spread under the
 * currently-bound default-probability term structure.
 *
 * @category termstructures.credit
 */
public class SpreadCdsHelper extends CdsHelper {

    public SpreadCdsHelper(final Handle< Quote > runningSpread, final Period tenor, final int settlementDays,
            final Calendar calendar, final Frequency frequency, final BusinessDayConvention paymentConvention,
            final DateGeneration.Rule rule, final DayCounter dayCounter, final double recoveryRate,
            final Handle< YieldTermStructure > discountCurve, final boolean settlesAccrual,
            final boolean paysAtDefaultTime, final Date startDate, final DayCounter lastPeriodDayCounter,
            final boolean rebatesAccrual, final PricingModel model) {
        super(runningSpread, tenor, settlementDays, calendar, frequency, paymentConvention, rule, dayCounter,
                recoveryRate, discountCurve, settlesAccrual, paysAtDefaultTime, startDate, lastPeriodDayCounter,
                rebatesAccrual, model);
    }

    /** Spread-as-double overload. */
    public SpreadCdsHelper(final double runningSpread, final Period tenor, final int settlementDays,
            final Calendar calendar, final Frequency frequency, final BusinessDayConvention paymentConvention,
            final DateGeneration.Rule rule, final DayCounter dayCounter, final double recoveryRate,
            final Handle< YieldTermStructure > discountCurve, final boolean settlesAccrual,
            final boolean paysAtDefaultTime, final Date startDate, final DayCounter lastPeriodDayCounter,
            final boolean rebatesAccrual, final PricingModel model) {
        super(runningSpread, tenor, settlementDays, calendar, frequency, paymentConvention, rule, dayCounter,
                recoveryRate, discountCurve, settlesAccrual, paysAtDefaultTime, startDate, lastPeriodDayCounter,
                rebatesAccrual, model);
    }

    /**
     * Convenience overload defaulting settlesAccrual=true, paysAtDefaultTime=true, startDate=null,
     * lastPeriodDayCounter=null, rebatesAccrual=true, model=Midpoint — matches C++ default arguments.
     */
    public SpreadCdsHelper(final double runningSpread, final Period tenor, final int settlementDays,
            final Calendar calendar, final Frequency frequency, final BusinessDayConvention paymentConvention,
            final DateGeneration.Rule rule, final DayCounter dayCounter, final double recoveryRate,
            final Handle< YieldTermStructure > discountCurve) {
        this(runningSpread, tenor, settlementDays, calendar, frequency, paymentConvention, rule, dayCounter,
                recoveryRate, discountCurve, true, true, null, null, true, PricingModel.Midpoint);
    }

    @Override
    public double impliedQuote() {
        swap_.update();
        // C++ swap_->recalculate() — Java uses Instrument.recalculate()
        // which is the standard LazyObject pattern. Ensure NPV pipeline runs.
        swap_.recalculate();
        return swap_.fairSpread();
    }

    @Override
    protected void resetEngine() {
        // C++ uses Protection::Buyer with notional 100.0 and a temporary
        // 1% spread; the engine then solves for the actual fair spread.
        swap_ = new CreditDefaultSwap(Protection.Side.Buyer, 100.0, 0.01, schedule_, paymentConvention_, dayCounter_,
                settlesAccrual_, paysAtDefaultTime_, protectionStart_,
                null,                  // claim — defaults to FaceValueClaim
                lastPeriodDC_, rebatesAccrual_, evaluationDate_, 3);

        switch ( model_ ) {
        case Midpoint:
            swap_.setPricingEngine(new MidPointCdsEngine(probability_, recoveryRate_, discountCurve_));
            break;
        case ISDA:
            // Phase 3d L1: wire IsdaCdsEngine with the C++ default settings
            // (Taylor / HalfDayBias / Piecewise, includeSettlementDateFlows=false).
            swap_.setPricingEngine(
                    new org.jquantlib.pricingengines.credit.IsdaCdsEngine(probability_, recoveryRate_, discountCurve_,
                            Boolean.FALSE, org.jquantlib.pricingengines.credit.IsdaCdsEngine.NumericalFix.Taylor,
                            org.jquantlib.pricingengines.credit.IsdaCdsEngine.AccrualBias.HalfDayBias,
                            org.jquantlib.pricingengines.credit.IsdaCdsEngine.ForwardsInCouponPeriod.Piecewise));
            break;
        default:
            throw new IllegalArgumentException("unknown CDS pricing model: " + model_);
        }
    }
}
