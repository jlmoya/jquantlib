/*
 Copyright (C) 2026 JQuantLib team

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
 Copyright (C) 2026 Zain Mughal

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.*;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Helper class to instantiate {@link MultipleResetsSwap} with sensible defaults (mirrors C++ v1.42.1
 * {@code QuantLib::MakeMultipleResetsSwap} in {@code ql/instruments/makemultipleresetsswap.{hpp,cpp}}).
 *
 * <p>Phase 5d.5-MR.
 */
public class MakeMultipleResetsSwap {

    private final IborIndex iborIndex_;
    private final int resetsPerCoupon_;
    private Period tenor_;
    private double fixedRate_ = Constants.NULL_REAL;
    private Period forwardStart_ = new Period();

    private int settlementDays_ = Constants.NULL_NATURAL;
    private Date effectiveDate_ = new Date();
    private Date terminationDate_ = new Date();
    private VanillaSwap.Type type_ = VanillaSwap.Type.Payer;
    private double nominal_ = 1.0;
    private Frequency fixedFrequency_ = Frequency.NoFrequency;
    private DayCounter fixedDayCount_;
    private BusinessDayConvention fixedConvention_ = BusinessDayConvention.ModifiedFollowing;
    private double spread_ = 0.0;
    private RateAveraging.Type averagingMethod_ = RateAveraging.Type.Compound;
    private PricingEngine engine_;

    public MakeMultipleResetsSwap(final Period tenor, final IborIndex iborIndex, final int resetsPerCoupon) {
        this.tenor_ = tenor;
        this.iborIndex_ = iborIndex;
        this.resetsPerCoupon_ = resetsPerCoupon;
        this.fixedDayCount_ = iborIndex.dayCounter();
    }

    public MultipleResetsSwap value() {
        final Calendar cal = iborIndex_.fixingCalendar();
        final BusinessDayConvention bdc = iborIndex_.businessDayConvention();

        QL.require(effectiveDate_.isNull() || settlementDays_ == Constants.NULL_NATURAL,
                "withEffectiveDate and withSettlementDays are mutually exclusive");

        Date startDate;
        if ( !effectiveDate_.isNull() ) {
            startDate = effectiveDate_;
        } else {
            final int settlDays = (settlementDays_ != Constants.NULL_NATURAL)
                    ? settlementDays_
                    : iborIndex_.fixingDays();
            final Date refDate = new Settings().evaluationDate();
            startDate = cal.advance(cal.adjust(refDate), settlDays, TimeUnit.Days);
            startDate = cal.advance(startDate, forwardStart_,
                    forwardStart_.length() < 0 ? BusinessDayConvention.Preceding : BusinessDayConvention.Following);
        }

        final Date endDate = !terminationDate_.isNull() ? terminationDate_ : cal.advance(startDate, tenor_, bdc);

        final Period resetTenor = iborIndex_.tenor();
        Frequency fixedFreq = fixedFrequency_;
        if ( fixedFreq == Frequency.NoFrequency ) {
            final Period couponTenor = new Period(resetsPerCoupon_ * resetTenor.length(), resetTenor.units());
            fixedFreq = couponTenor.frequency();
        }

        final Schedule fixedSchedule = new Schedule(startDate, endDate, new Period(fixedFreq), cal, fixedConvention_,
                fixedConvention_, DateGeneration.Rule.Backward, false);

        final Schedule fullResetSchedule = new Schedule(startDate, endDate, resetTenor, cal, bdc, bdc,
                DateGeneration.Rule.Backward, false);

        double usedFixedRate = fixedRate_;
        if ( fixedRate_ == Constants.NULL_REAL || Double.isNaN(fixedRate_) ) {
            // dry-run swap with rate=0 to compute the fair rate
            final MultipleResetsSwap temp = new MultipleResetsSwap(type_, nominal_, fixedSchedule, 0.0, fixedDayCount_,
                    fullResetSchedule, iborIndex_, resetsPerCoupon_, spread_, averagingMethod_, null, 0,
                    new NullCalendar());
            if ( engine_ == null ) {
                final Handle< YieldTermStructure > disc = iborIndex_.termStructure();
                QL.require(!disc.empty(), "null term structure set to this instance of " + iborIndex_.name());
                temp.setPricingEngine(new DiscountingSwapEngine(disc));
            } else {
                temp.setPricingEngine(engine_);
            }
            usedFixedRate = temp.fairRate();
        }

        final MultipleResetsSwap swap = new MultipleResetsSwap(type_, nominal_, fixedSchedule, usedFixedRate,
                fixedDayCount_, fullResetSchedule, iborIndex_, resetsPerCoupon_, spread_, averagingMethod_, null, 0,
                new NullCalendar());

        if ( engine_ == null ) {
            final Handle< YieldTermStructure > disc = iborIndex_.termStructure();
            if ( !disc.empty() ) {
                swap.setPricingEngine(new DiscountingSwapEngine(disc));
            }
        } else {
            swap.setPricingEngine(engine_);
        }

        return swap;
    }

    public MakeMultipleResetsSwap receiveFixed(final boolean flag) {
        this.type_ = flag ? VanillaSwap.Type.Receiver : VanillaSwap.Type.Payer;
        return this;
    }

    public MakeMultipleResetsSwap withType(final VanillaSwap.Type type) {
        this.type_ = type;
        return this;
    }

    public MakeMultipleResetsSwap withNominal(final double n) {
        this.nominal_ = n;
        return this;
    }

    public MakeMultipleResetsSwap withFixedRate(final double fixedRate) {
        this.fixedRate_ = fixedRate;
        return this;
    }

    public MakeMultipleResetsSwap withSettlementDays(final int settlementDays) {
        this.settlementDays_ = settlementDays;
        return this;
    }

    public MakeMultipleResetsSwap withEffectiveDate(final Date d) {
        this.effectiveDate_ = d;
        return this;
    }

    public MakeMultipleResetsSwap withTerminationDate(final Date d) {
        this.terminationDate_ = d;
        if ( !d.isNull() )
            this.tenor_ = new Period();
        return this;
    }

    public MakeMultipleResetsSwap withForwardStart(final Period fwdStart) {
        this.forwardStart_ = fwdStart;
        return this;
    }

    public MakeMultipleResetsSwap withFixedLegFrequency(final Frequency f) {
        this.fixedFrequency_ = f;
        return this;
    }

    public MakeMultipleResetsSwap withFixedLegDayCount(final DayCounter dc) {
        this.fixedDayCount_ = dc;
        return this;
    }

    public MakeMultipleResetsSwap withFixedLegConvention(final BusinessDayConvention bdc) {
        this.fixedConvention_ = bdc;
        return this;
    }

    public MakeMultipleResetsSwap withFloatingLegSpread(final double sp) {
        this.spread_ = sp;
        return this;
    }

    public MakeMultipleResetsSwap withAveragingMethod(final RateAveraging.Type m) {
        this.averagingMethod_ = m;
        return this;
    }

    public MakeMultipleResetsSwap withDiscountingTermStructure(final Handle< YieldTermStructure > d) {
        this.engine_ = new DiscountingSwapEngine(d);
        return this;
    }

    public MakeMultipleResetsSwap withPricingEngine(final PricingEngine engine) {
        this.engine_ = engine;
        return this;
    }
}
