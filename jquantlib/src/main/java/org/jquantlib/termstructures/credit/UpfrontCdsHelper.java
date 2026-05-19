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

import org.jquantlib.Settings;
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
 * Upfront-quoted CDS hazard-rate bootstrap helper.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::UpfrontCdsHelper}
 * ({@code ql/termstructures/credit/defaultprobabilityhelpers.hpp:157-188},
 * {@code defaultprobabilityhelpers.cpp:159-224}).
 *
 * <p>The implied quote is the CDS's fair (par) upfront fraction under the
 * currently-bound default-probability term structure. The C++ class temporarily sets
 * {@code Settings::includeTodaysCashFlows() = true} during {@code impliedQuote} so the upfront cash flow is always seen
 * by the engine; the Java port mirrors that with try/finally on {@link Settings#setTodaysPayments(boolean)}.
 *
 * @category termstructures.credit
 */
public class UpfrontCdsHelper extends CdsHelper {

    private final int upfrontSettlementDays_;
    private final double runningSpread_;
    private Date upfrontDate_;

    public UpfrontCdsHelper(final Handle< Quote > upfront, final double runningSpread, final Period tenor,
            final int settlementDays, final Calendar calendar, final Frequency frequency,
            final BusinessDayConvention paymentConvention, final DateGeneration.Rule rule, final DayCounter dayCounter,
            final double recoveryRate, final Handle< YieldTermStructure > discountCurve,
            final int upfrontSettlementDays, final boolean settlesAccrual, final boolean paysAtDefaultTime,
            final Date startDate, final DayCounter lastPeriodDayCounter, final boolean rebatesAccrual,
            final PricingModel model) {
        super(upfront, tenor, settlementDays, calendar, frequency, paymentConvention, rule, dayCounter, recoveryRate,
                discountCurve, settlesAccrual, paysAtDefaultTime, startDate, lastPeriodDayCounter, rebatesAccrual,
                model);
        this.upfrontSettlementDays_ = upfrontSettlementDays;
        this.runningSpread_ = runningSpread;
        this.upfrontDate_ = computeUpfrontDate();
    }

    /** Upfront-as-double overload. */
    public UpfrontCdsHelper(final double upfront, final double runningSpread, final Period tenor,
            final int settlementDays, final Calendar calendar, final Frequency frequency,
            final BusinessDayConvention paymentConvention, final DateGeneration.Rule rule, final DayCounter dayCounter,
            final double recoveryRate, final Handle< YieldTermStructure > discountCurve,
            final int upfrontSettlementDays, final boolean settlesAccrual, final boolean paysAtDefaultTime,
            final Date startDate, final DayCounter lastPeriodDayCounter, final boolean rebatesAccrual,
            final PricingModel model) {
        super(upfront, tenor, settlementDays, calendar, frequency, paymentConvention, rule, dayCounter, recoveryRate,
                discountCurve, settlesAccrual, paysAtDefaultTime, startDate, lastPeriodDayCounter, rebatesAccrual,
                model);
        this.upfrontSettlementDays_ = upfrontSettlementDays;
        this.runningSpread_ = runningSpread;
        this.upfrontDate_ = computeUpfrontDate();
    }

    /**
     * Convenience overload mirroring C++ default arguments ({@code upfrontSettlementDays=3}, settlesAccrual=true,
     * paysAtDefaultTime=true, startDate=null, lastPeriodDayCounter=null, rebatesAccrual=true, model=Midpoint).
     */
    public UpfrontCdsHelper(final double upfront, final double runningSpread, final Period tenor,
            final int settlementDays, final Calendar calendar, final Frequency frequency,
            final BusinessDayConvention paymentConvention, final DateGeneration.Rule rule, final DayCounter dayCounter,
            final double recoveryRate, final Handle< YieldTermStructure > discountCurve) {
        this(upfront, runningSpread, tenor, settlementDays, calendar, frequency, paymentConvention, rule, dayCounter,
                recoveryRate, discountCurve, 3, true, true, null, null, true, PricingModel.Midpoint);
    }

    private Date computeUpfrontDate() {
        return calendar_.advance(evaluationDate_, upfrontSettlementDays_, TimeUnit.Days, paymentConvention_, false);
    }

    @Override
    protected void initializeDates() {
        super.initializeDates();
        // C++ recomputes upfrontDate_ here too; the field is initialised
        // during the constructor after super(...) finishes (see ctor body).
        // initializeDates() runs from the super-ctor before our subclass
        // fields are set; guard for that.
        if ( upfrontSettlementDays_ != 0 || calendar_ != null ) {
            try {
                this.upfrontDate_ = computeUpfrontDate();
            } catch ( final NullPointerException npe ) {
                // first call from super-ctor: subclass-only fields aren't
                // initialised yet; ignore — ctor body fills upfrontDate_.
            }
        }
    }

    @Override
    public double impliedQuote() {
        // C++: SavedSettings backup; Settings::instance().includeTodaysCashFlows() = true;
        // recalculate; return swap_->fairUpfront(); destructor restores.
        final Settings s = new Settings();
        final boolean previous = s.isTodaysPayments();
        s.setTodaysPayments(true);
        try {
            swap_.update();
            swap_.recalculate();
            return swap_.fairUpfront();
        } finally {
            s.setTodaysPayments(previous);
        }
    }

    @Override
    protected void resetEngine() {
        // C++: notional 100.0, upfront 0.01 (placeholder solved by engine),
        // running spread is the helper's runningSpread_.
        swap_ = new CreditDefaultSwap(Protection.Side.Buyer, 100.0, 0.01,                  // upfront placeholder
                runningSpread_, schedule_, paymentConvention_, dayCounter_, settlesAccrual_, paysAtDefaultTime_,
                protectionStart_, upfrontDate_, null,                  // claim
                lastPeriodDC_, rebatesAccrual_, evaluationDate_, 3);

        switch ( model_ ) {
        case Midpoint:
            // C++ passes includeSettlementDateFlows=true here so the upfront
            // cash flow on settlement date counts in the engine NPV.
            swap_.setPricingEngine(new MidPointCdsEngine(probability_, recoveryRate_, discountCurve_, Boolean.TRUE));
            break;
        case ISDA:
            // Phase 3d L1: wire IsdaCdsEngine with C++ defaults (Taylor / HalfDayBias /
            // Piecewise, includeSettlementDateFlows=false).
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

    public Date upfrontDate() {
        return upfrontDate_;
    }

    public int upfrontSettlementDays() {
        return upfrontSettlementDays_;
    }
}
