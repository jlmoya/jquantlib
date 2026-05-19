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
import org.jquantlib.cashflow.*;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Swap with a fixed leg and a multiple-resets floating leg.
 *
 * <p>The floating leg contains coupons whose rate is determined by
 * compounding or averaging {@code resetsPerCoupon} consecutive Ibor fixings during each accrual period.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::MultipleResetsSwap} in
 * {@code ql/instruments/multipleresetsswap.{hpp,cpp}}.  The Java port extends {@link Swap} directly (the Java codebase
 * does not have a separate {@code FixedVsFloatingSwap} layer; {@link VanillaSwap} follows the same pattern).
 *
 * <p>Phase 5d.5-MR.
 */
public class MultipleResetsSwap extends Swap {

    static final double basisPoint = 1.0e-4;

    private final VanillaSwap.Type type;
    private final double nominal;
    private final Schedule fixedSchedule;
    private final double fixedRate;
    private final DayCounter fixedDayCount;
    private final Schedule fullResetSchedule;
    private final IborIndex iborIndex;
    private final int resetsPerCoupon;
    private final double spread;
    private final RateAveraging.Type averagingMethod;
    private final BusinessDayConvention paymentConvention;

    // results
    private double fairRate;
    private double fairSpread;

    public MultipleResetsSwap(final VanillaSwap.Type type, final double nominal, final Schedule fixedSchedule,
            final double fixedRate, final DayCounter fixedDayCount, final Schedule fullResetSchedule,
            final IborIndex iborIndex, final int resetsPerCoupon) {
        this(type, nominal, fixedSchedule, fixedRate, fixedDayCount, fullResetSchedule, iborIndex, resetsPerCoupon, 0.0,
                RateAveraging.Type.Compound, null /* default payment convention */, 0, new NullCalendar());
    }

    public MultipleResetsSwap(final VanillaSwap.Type type, final double nominal, final Schedule fixedSchedule,
            final double fixedRate, final DayCounter fixedDayCount, final Schedule fullResetSchedule,
            final IborIndex iborIndex, final int resetsPerCoupon, final double spread,
            final RateAveraging.Type averagingMethod, final BusinessDayConvention paymentConvention,
            final int paymentLag, final Calendar paymentCalendar) {
        super(2);
        this.type = type;
        this.nominal = nominal;
        this.fixedSchedule = fixedSchedule;
        this.fixedRate = fixedRate;
        this.fixedDayCount = fixedDayCount;
        this.fullResetSchedule = fullResetSchedule;
        this.iborIndex = iborIndex;
        this.resetsPerCoupon = resetsPerCoupon;
        this.spread = spread;
        this.averagingMethod = averagingMethod;
        this.paymentConvention = (paymentConvention != null)
                ? paymentConvention
                : fixedSchedule.businessDayConvention();

        QL.require((fullResetSchedule.size() - 1) % resetsPerCoupon == 0,
                "number of reset periods (" + (fullResetSchedule.size() - 1)
                        + ") is not a multiple of resetsPerCoupon (" + resetsPerCoupon + ")");

        // fixed leg
        final Leg fixedLeg = new FixedRateLeg(fixedSchedule, fixedDayCount).withNotionals(nominal)
                .withCouponRates(fixedRate).withPaymentAdjustment(this.paymentConvention).Leg();

        // floating multiple-resets leg
        final Calendar effPaymentCalendar = paymentCalendar.empty() ? fullResetSchedule.calendar() : paymentCalendar;
        final Leg floatingLeg = new MultipleResetsLeg(fullResetSchedule, iborIndex, resetsPerCoupon).withNotionals(
                        nominal).withRateSpreads(spread).withAveragingMethod(averagingMethod)
                .withPaymentAdjustment(this.paymentConvention).withPaymentLag(paymentLag)
                .withPaymentCalendar(effPaymentCalendar).Leg();

        for ( final CashFlow cf : floatingLeg ) {
            cf.addObserver(this);
        }

        super.legs.add(fixedLeg);
        super.legs.add(floatingLeg);
        if ( type == VanillaSwap.Type.Payer ) {
            super.payer[0] = -1.0;
            super.payer[1] = +1.0;
        } else {
            super.payer[0] = +1.0;
            super.payer[1] = -1.0;
        }
    }

    public VanillaSwap.Type type() {
        return type;
    }

    public double nominal() {
        return nominal;
    }

    public Schedule fixedSchedule() {
        return fixedSchedule;
    }

    public double fixedRate() {
        return fixedRate;
    }

    public DayCounter fixedDayCount() {
        return fixedDayCount;
    }

    public Schedule fullResetSchedule() {
        return fullResetSchedule;
    }

    public IborIndex iborIndex() {
        return iborIndex;
    }

    public int resetsPerCoupon() {
        return resetsPerCoupon;
    }

    public double spread() {
        return spread;
    }

    public RateAveraging.Type averagingMethod() {
        return averagingMethod;
    }

    public BusinessDayConvention paymentConvention() {
        return paymentConvention;
    }

    public Leg fixedLeg() {
        return legs.get(0);
    }

    public Leg floatingLeg() {
        return legs.get(1);
    }

    public double fixedLegBPS() {
        calculate();
        QL.require(!Double.isNaN(legBPS[0]), "result not available");
        return legBPS[0];
    }

    public double floatingLegBPS() {
        calculate();
        QL.require(!Double.isNaN(legBPS[1]), "result not available");
        return legBPS[1];
    }

    public double fixedLegNPV() {
        calculate();
        QL.require(!Double.isNaN(legNPV[0]), "result not available");
        return legNPV[0];
    }

    public double floatingLegNPV() {
        calculate();
        QL.require(!Double.isNaN(legNPV[1]), "result not available");
        return legNPV[1];
    }

    public double fairRate() {
        calculate();
        QL.require(!Double.isNaN(fairRate), "result not available");
        return fairRate;
    }

    public double fairSpread() {
        calculate();
        QL.require(!Double.isNaN(fairSpread), "result not available");
        return fairSpread;
    }

    @Override
    public void setupExpired() {
        super.setupExpired();
        legBPS[0] = 0.0;
        legBPS[1] = 0.0;
        fairRate = Constants.NULL_REAL;
        fairSpread = Constants.NULL_REAL;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments arguments) {
        super.setupArguments(arguments);
        // C++ MultipleResetsSwap inherits FixedVsFloatingSwap, which
        // populates VanillaSwap.Arguments-style fields. For consistency
        // with VanillaSwap (the Java equivalent of FixedVsFloatingSwap)
        // we mirror the same payload — engines such as
        // DiscountingSwapEngine only need the per-leg cashflows, which
        // come from Swap.setupArguments above; downstream Vanilla-style
        // engines can also use this swap.
        if ( arguments instanceof VanillaSwap.Arguments ) {
            final VanillaSwap.ArgumentsImpl a = (VanillaSwap.ArgumentsImpl) arguments;
            a.type = type;
            a.nominal = nominal;

            final Leg fixedCoupons = fixedLeg();
            final int nFixed = fixedCoupons.size();
            a.fixedResetDates = new ArrayList< Date >(Collections.nCopies(nFixed, null));
            a.fixedPayDates = new ArrayList< Date >(Collections.nCopies(nFixed, null));
            a.fixedCoupons = new ArrayList< Double >(Collections.nCopies(nFixed, null));
            for ( int i = 0; i < nFixed; i++ ) {
                final FixedRateCoupon coupon = (FixedRateCoupon) fixedCoupons.get(i);
                a.fixedPayDates.set(i, coupon.date());
                a.fixedResetDates.set(i, coupon.accrualStartDate());
                a.fixedCoupons.set(i, coupon.amount());
            }

            final Leg floatingCoupons = floatingLeg();
            final int nFloat = floatingCoupons.size();
            a.floatingResetDates = new ArrayList< Date >(Collections.nCopies(nFloat, null));
            a.floatingPayDates = new ArrayList< Date >(Collections.nCopies(nFloat, null));
            a.floatingFixingDates = new ArrayList< Date >(Collections.nCopies(nFloat, null));
            a.floatingAccrualTimes = new ArrayList< Double >(Collections.nCopies(nFloat, null));
            a.floatingSpreads = new ArrayList< Double >(Collections.nCopies(nFloat, null));
            a.floatingCoupons = new ArrayList< Double >(Collections.nCopies(nFloat, null));
            for ( int i = 0; i < nFloat; i++ ) {
                final MultipleResetsCoupon coupon = (MultipleResetsCoupon) floatingCoupons.get(i);
                a.floatingResetDates.set(i, coupon.accrualStartDate());
                a.floatingPayDates.set(i, coupon.date());
                a.floatingFixingDates.set(i, coupon.fixingDate());
                a.floatingAccrualTimes.set(i, coupon.accrualPeriod());
                a.floatingSpreads.set(i, coupon.spread());
                try {
                    a.floatingCoupons.set(i, coupon.amount());
                } catch ( final Exception e ) {
                    a.floatingCoupons.set(i, Constants.NULL_REAL);
                }
            }
        }
    }

    @Override
    public void fetchResults(final PricingEngine.Results results) {
        super.fetchResults(results);
        if ( results instanceof VanillaSwap.Results ) {
            final VanillaSwap.ResultsImpl r = (VanillaSwap.ResultsImpl) results;
            fairRate = r.fairRate;
            fairSpread = r.fairSpread;
        } else {
            fairRate = Constants.NULL_REAL;
            fairSpread = Constants.NULL_REAL;
        }

        if ( fairRate == Constants.NULL_REAL || Double.isNaN(fairRate) ) {
            if ( legBPS[0] != Constants.NULL_REAL && !Double.isNaN(legBPS[0]) ) {
                fairRate = fixedRate - NPV / (legBPS[0] / basisPoint);
            }
        }
        if ( fairSpread == Constants.NULL_REAL || Double.isNaN(fairSpread) ) {
            if ( legBPS[1] != Constants.NULL_REAL && !Double.isNaN(legBPS[1]) ) {
                fairSpread = spread - NPV / (legBPS[1] / basisPoint);
            }
        }
    }

    @Override
    public String toString() {
        return type.toString();
    }
}
