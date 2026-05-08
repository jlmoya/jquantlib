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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.YoYInflationCoupon;
import org.jquantlib.cashflow.YoYInflationCouponPricer;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Date;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * Helper class to instantiate a standard YoY inflation cap or floor.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::MakeYoYInflationCapFloor}
 * ({@code ql/instruments/makeyoyinflationcapfloor.{hpp,cpp}}).
 *
 * <p>Inlines the C++ {@code yoyInflationLeg} builder for the simple
 * (non-cap/floor) coupon path; gearings/spreads default to 1.0/0.0,
 * matching the standard YoY cap/floor's leg coupon shape.
 *
 * @author JQuantLib migration team (Phase 2r C.1)
 */
public class MakeYoYInflationCapFloor {

    private final InflationCapFloor.Type capFloorType_;
    private final int length_;
    private final Calendar calendar_;
    private final YoYInflationIndex index_;
    private final Period observationLag_;
    private final CPI.InterpolationType interpolation_;

    private double strike_ = Constants.NULL_REAL;
    private boolean firstCapletExcluded_ = false;
    private boolean asOptionlet_ = false;
    private Date effectiveDate_ = null;
    private Period forwardStart_ = new Period(0, TimeUnit.Days);
    private DayCounter dayCounter_;
    private BusinessDayConvention roll_ = BusinessDayConvention.ModifiedFollowing;
    private int fixingDays_ = 0;
    private double nominal_ = 1000000.0;
    private Handle<YieldTermStructure> nominalTermStructure_ = new Handle<>();
    private PricingEngine engine_;

    public MakeYoYInflationCapFloor(final InflationCapFloor.Type capFloorType,
                                    final YoYInflationIndex index,
                                    final int length,
                                    final Calendar cal,
                                    final Period observationLag,
                                    final CPI.InterpolationType interpolation) {
        this.capFloorType_ = capFloorType;
        this.length_ = length;
        this.calendar_ = cal;
        this.index_ = index;
        this.observationLag_ = observationLag;
        this.interpolation_ = interpolation;
        this.dayCounter_ = new Thirty360(Thirty360.Convention.BondBasis);
    }

    public MakeYoYInflationCapFloor withNominal(final double n) {
        this.nominal_ = n;
        return this;
    }

    public MakeYoYInflationCapFloor withEffectiveDate(final Date effectiveDate) {
        this.effectiveDate_ = effectiveDate;
        return this;
    }

    public MakeYoYInflationCapFloor withFirstCapletExcluded() {
        this.firstCapletExcluded_ = true;
        return this;
    }

    public MakeYoYInflationCapFloor withPaymentDayCounter(final DayCounter dc) {
        this.dayCounter_ = dc;
        return this;
    }

    public MakeYoYInflationCapFloor withPaymentAdjustment(final BusinessDayConvention bdc) {
        this.roll_ = bdc;
        return this;
    }

    public MakeYoYInflationCapFloor withFixingDays(final int n) {
        this.fixingDays_ = n;
        return this;
    }

    public MakeYoYInflationCapFloor withPricingEngine(final PricingEngine engine) {
        this.engine_ = engine;
        return this;
    }

    public MakeYoYInflationCapFloor asOptionlet(final boolean b) {
        this.asOptionlet_ = b;
        return this;
    }

    public MakeYoYInflationCapFloor asOptionlet() {
        return asOptionlet(true);
    }

    public MakeYoYInflationCapFloor withStrike(final double strike) {
        QL.require(nominalTermStructure_.empty(), "ATM strike already given");
        this.strike_ = strike;
        return this;
    }

    public MakeYoYInflationCapFloor withAtmStrike(
            final Handle<YieldTermStructure> nominalTermStructure) {
        QL.require(strike_ == Constants.NULL_REAL || Double.isNaN(strike_),
                "explicit strike already given");
        this.nominalTermStructure_ = nominalTermStructure;
        return this;
    }

    public MakeYoYInflationCapFloor withForwardStart(final Period forwardStart) {
        this.forwardStart_ = forwardStart;
        return this;
    }

    /** Build and return the configured {@link InflationCapFloor}. */
    public InflationCapFloor build() {
        Date startDate;
        if (effectiveDate_ != null && !effectiveDate_.isNull()) {
            startDate = effectiveDate_;
        } else {
            final Date referenceDate = new Settings().evaluationDate();
            final Date spotDate = calendar_.advance(referenceDate,
                    new Period(fixingDays_, TimeUnit.Days),
                    BusinessDayConvention.Following);
            startDate = spotDate.add(forwardStart_);
        }

        final Date endDate = calendar_.advance(startDate,
                new Period(length_, TimeUnit.Years),
                BusinessDayConvention.Unadjusted);
        final Schedule schedule = new MakeSchedule(startDate, endDate,
                new Period(1, TimeUnit.Years), calendar_,
                BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .schedule();

        // Build YoY leg inline (mirrors C++ yoyInflationLeg::operator Leg()
        // for the simple-coupon path: gearing=1.0, spread=0.0, no caps/floors).
        final Leg leg = buildYoyLeg(schedule);

        if (firstCapletExcluded_) {
            leg.remove(0);
        }
        if (asOptionlet_ && leg.size() > 1) {
            // remove all but the last
            while (leg.size() > 1) {
                leg.remove(0);
            }
        }

        // Strike resolution
        final List<Double> strikeVector = new ArrayList<>();
        if (strike_ == Constants.NULL_REAL || Double.isNaN(strike_)) {
            // ATM on the forecasting curve
            QL.require(!nominalTermStructure_.empty(),
                    "either a strike or a nominal term structure must be supplied");
            strikeVector.add(CashFlows.getInstance().atmRate(leg, nominalTermStructure_));
        } else {
            strikeVector.add(strike_);
        }

        final InflationCapFloor capFloor = new InflationCapFloor(
                capFloorType_, leg, strikeVector);
        if (engine_ != null) {
            capFloor.setPricingEngine(engine_);
        }
        return capFloor;
    }

    /** Inline yoyInflationLeg builder (no caps/floors path). */
    private Leg buildYoyLeg(final Schedule schedule) {
        final int n = schedule.size() - 1;
        QL.require(n >= 1, "schedule must have at least 2 dates");
        QL.require(dayCounter_ != null, "no payment daycounter given");

        final Leg leg = new Leg();
        for (int i = 0; i < n; ++i) {
            final Date start = schedule.date(i);
            final Date end = schedule.date(i + 1);
            final Date paymentDate = calendar_.adjust(end, roll_);
            final YoYInflationCoupon coupon = new YoYInflationCoupon(
                    nominal_,
                    paymentDate,
                    start, end,
                    fixingDays_,
                    index_,
                    observationLag_,
                    interpolation_,
                    dayCounter_,
                    /* gearing */ 1.0,
                    /* spread  */ 0.0,
                    start, end);
            leg.add(coupon);
        }

        // Standard YoY pricer (no caps/floors at this layer)
        final YoYInflationCouponPricer pricer = new YoYInflationCouponPricer();
        for (final CashFlow cf : leg) {
            if (cf instanceof YoYInflationCoupon) {
                ((YoYInflationCoupon) cf).setPricer(pricer);
            }
        }
        return leg;
    }
}
