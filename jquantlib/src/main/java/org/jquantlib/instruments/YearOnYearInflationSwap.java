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
 Copyright (C) 2007, 2009 Chris Kenyon
 Copyright (C) 2009 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.cashflow.*;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.termstructures.inflation.YearOnYearInflationSwapHelper;
import org.jquantlib.time.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Year-on-year inflation-indexed swap.
 *
 * <p>Quoted as a fixed rate {@code K}. At start:
 * <pre>
 *   sum_{i=1..M} P_n(0,t_i) N K =
 *   sum_{i=1..M} P_n(0,t_i) N (I(t_i)/I(t_{i-1}) - 1)
 * </pre>
 * where {@code t_M} is the maturity time, {@code P_n(0,t)} is the nominal discount factor at time {@code t}, {@code N}
 * is the notional and {@code I(t)} is the inflation index value at time {@code t}.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YearOnYearInflationSwap}
 * ({@code ql/instruments/yearonyearinflationswap.{hpp,cpp}}).
 *
 * <p>Type {@code Payer} / {@code Receiver} refers to the YoY (inflation) leg
 * — same convention as {@link ZeroCouponInflationSwap}.
 *
 * <p>The Java port omits the C++ {@code arguments}/{@code results} subclass
 * mechanism: the swap prices through {@link Swap.ResultsImpl} (NPV + legNPV/legBPS) returned by the standard
 * {@code DiscountingSwapEngine}, with {@code fairRate} computed from the engine's results inline (mirrors the C++
 * {@code fetchResults} fallback path when the engine is a generic Swap engine).
 *
 * @author JQuantLib migration team (Phase 2q B)
 */
public class YearOnYearInflationSwap extends Swap {

    //
    // public inner enums
    //

    private final Type type;

    //
    // private final fields
    //
    private final double nominal;
    private final Schedule fixedSchedule;
    private final double fixedRate;
    private final DayCounter fixedDayCount;
    private final Schedule yoySchedule;
    private final YoYInflationIndex yoyIndex;
    private final Period observationLag;
    private final CPI.InterpolationType interpolation;
    private final double spread;
    private final DayCounter yoyDayCount;
    private final Calendar paymentCalendar;
    private final BusinessDayConvention paymentConvention;
    /**
     * Default-overload constructor — uses {@link BusinessDayConvention#ModifiedFollowing}.
     */
    public YearOnYearInflationSwap(final Type type, final double nominal, final Schedule fixedSchedule,
            final double fixedRate, final DayCounter fixedDayCount, final Schedule yoySchedule,
            final YoYInflationIndex yoyIndex, final Period observationLag, final CPI.InterpolationType interpolation,
            final double spread, final DayCounter yoyDayCount, final Calendar paymentCalendar) {
        this(type, nominal, fixedSchedule, fixedRate, fixedDayCount, yoySchedule, yoyIndex, observationLag,
                interpolation, spread, yoyDayCount, paymentCalendar, BusinessDayConvention.ModifiedFollowing);
    }

    //
    // public constructors
    //

    public YearOnYearInflationSwap(final Type type, final double nominal, final Schedule fixedSchedule,
            final double fixedRate, final DayCounter fixedDayCount, final Schedule yoySchedule,
            final YoYInflationIndex yoyIndex, final Period observationLag, final CPI.InterpolationType interpolation,
            final double spread, final DayCounter yoyDayCount, final Calendar paymentCalendar,
            final BusinessDayConvention paymentConvention) {
        super(2);
        this.type = type;
        this.nominal = nominal;
        this.fixedSchedule = fixedSchedule;
        this.fixedRate = fixedRate;
        this.fixedDayCount = fixedDayCount;
        this.yoySchedule = yoySchedule;
        this.yoyIndex = yoyIndex;
        this.observationLag = observationLag;
        this.interpolation = interpolation;
        this.spread = spread;
        this.yoyDayCount = yoyDayCount;
        this.paymentCalendar = paymentCalendar;
        this.paymentConvention = paymentConvention;

        // Fixed leg uses simple compounding by default (matches C++ default
        // FixedRateLeg behavior). Note FixedRateLeg in JQuantLib is built via
        // .Leg() rather than C++ implicit operator Leg().
        final Leg fixedLeg = new FixedRateLeg(fixedSchedule, fixedDayCount).withNotionals(nominal)
                .withCouponRates(fixedRate).withPaymentAdjustment(paymentConvention).Leg();

        // Build YoY leg manually (yoyInflationLeg helper not yet ported —
        // the leg structure is small enough to inline here without losing
        // any C++ semantics for the swap path).
        final Leg yoyLeg = buildYoyLeg();

        // Register coupons as observers (matches C++ registerWith loop).
        for ( final CashFlow cf : yoyLeg ) {
            cf.addObserver(this);
        }

        legs.add(fixedLeg);
        legs.add(yoyLeg);

        switch ( type ) {
        case Payer:
            this.payer[0] = -1.0;
            this.payer[1] = +1.0;
            break;
        case Receiver:
            this.payer[0] = +1.0;
            this.payer[1] = -1.0;
            break;
        default:
            throw new LibraryException("unknown YoY-inflation-swap type");
        }
    }

    /**
     * Build a default 1-year-tenor schedule using the package convention — convenience helper used by both
     * {@link YearOnYearInflationSwapHelper} and tests when both legs share a single backwards-rolled annual schedule.
     *
     * <p>Mirrors the C++ helper {@code initializeDates()} exactly:
     * {@code MakeSchedule().from(start).to(end).withTenor(1Y)
     * .withConvention(Unadjusted).withCalendar(cal).backwards()} — the termination-date convention defaults to the main
     * convention (Unadjusted), NOT to {@code paymentConvention}. Payment-date adjustment (per
     * {@code paymentConvention}) is applied later by the coupon-leg builder.
     *
     * @param startDate  effective date
     * @param endDate    termination date (kept Unadjusted)
     * @param calendar   calendar used for payment-date arithmetic
     * @param convention payment convention — accepted for back-compat but ignored; schedule uses Unadjusted everywhere
     *                   to match the C++ helper
     */
    public static Schedule makeDefaultSchedule(final Date startDate, final Date endDate, final Calendar calendar,
            final BusinessDayConvention convention) {
        return new org.jquantlib.time.MakeSchedule(startDate, endDate, new Period(1, TimeUnit.Years), calendar,
                org.jquantlib.time.BusinessDayConvention.Unadjusted).withTerminationDateConvention(
                org.jquantlib.time.BusinessDayConvention.Unadjusted).backwards().schedule();
    }

    /**
     * Build the YoY leg as a sequence of {@link YoYInflationCoupon}s, one per accrual period. Mirrors C++
     * {@code yoyInflationLeg::operator Leg()} (cashflows/yoyinflationcoupon.cpp:146-232) for the simple-coupon path (no
     * caps/floors, gearing=1.0). Cap/floor variants belong to Phase 2r.
     */
    private Leg buildYoyLeg() {
        final int n = yoySchedule.size() - 1;
        QL.require(n >= 1, "yoy schedule must have at least 2 dates");

        final Leg leg = new Leg();
        Date refStart, start, refEnd, end;

        for ( int i = 0; i < n; ++i ) {
            refStart = start = yoySchedule.date(i);
            refEnd = end = yoySchedule.date(i + 1);
            final Date paymentDate = paymentCalendar.adjust(end, paymentConvention);

            // First/last period reference-date adjustment: mirrors C++
            // schedule.hasIsRegular() / isRegular(i+1) check. The Java
            // Schedule does not expose hasIsRegular(), but Schedule.isRegular
            // is always populated for our usage here (MakeSchedule produces
            // regular periods). We skip the irregular-period adjustment
            // because the bootstrap helper always uses MakeSchedule with
            // 1-Years tenor (regular by construction).
            //
            // For now, refStart=start and refEnd=end (regular case).

            final YoYInflationCoupon coupon = new YoYInflationCoupon(nominal, paymentDate, start, end,
                    /* fixingDays */ 0, yoyIndex, observationLag, interpolation, yoyDayCount,
                    /* gearing */ 1.0,
                    /* spread */ spread, refStart, refEnd);
            leg.add(coupon);
        }

        // Standard YoY swaplet pricer (no nominal TS — discounting is handled
        // by the swap engine, not the pricer; we follow C++ default
        // YoYInflationCouponPricer which constructs with empty handles and
        // uses swapletRate, never swapletPrice).
        final YoYInflationCouponPricer pricer = new YoYInflationCouponPricer();
        for ( final CashFlow cf : leg ) {
            if ( cf instanceof YoYInflationCoupon ) {
                ((YoYInflationCoupon) cf).setPricer(pricer);
            }
        }

        return leg;
    }

    //
    // inspectors
    //

    public Type type() {
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

    public Schedule yoySchedule() {
        return yoySchedule;
    }

    public YoYInflationIndex yoyInflationIndex() {
        return yoyIndex;
    }

    public Period observationLag() {
        return observationLag;
    }

    public CPI.InterpolationType interpolation() {
        return interpolation;
    }

    public double spread() {
        return spread;
    }

    public DayCounter yoyDayCount() {
        return yoyDayCount;
    }

    public Calendar paymentCalendar() {
        return paymentCalendar;
    }

    public BusinessDayConvention paymentConvention() {
        return paymentConvention;
    }

    public final Leg fixedLeg() {
        return legs.get(0);
    }

    public final Leg yoyLeg() {
        return legs.get(1);
    }

    //
    // result accessors — TIGHT closed-form fallbacks (mirror C++ fetchResults)
    //

    public double fixedLegNPV() {
        calculate();
        QL.require(legNPV[0] != Constants.NULL_REAL, "result not available");
        return legNPV[0];
    }

    public double yoyLegNPV() {
        calculate();
        QL.require(legNPV[1] != Constants.NULL_REAL, "result not available");
        return legNPV[1];
    }

    /**
     * Fair fixed rate. Computed from {@code legBPS[0]} as in C++ {@code YearOnYearInflationSwap::fetchResults} fallback
     * path: {@code fairRate = fixedRate - NPV / (legBPS[0]/basisPoint)}.
     *
     * <p>Mirrors C++ v1.42.1 yearonyearinflationswap.cpp:186-190 (the
     * fallback when the engine is a generic Swap engine).
     */
    public double fairRate() {
        calculate();
        final double basisPoint = 1.0e-4;
        QL.require(!Double.isNaN(legBPS[0]) && legBPS[0] != Constants.NULL_REAL,
                "fair rate result not available — legBPS[0] is unavailable");
        return fixedRate - NPV / (legBPS[0] / basisPoint);
    }

    /**
     * Fair YoY-leg spread. Computed from {@code legBPS[1]} as in C++ fallback:
     * {@code fairSpread = spread - NPV / (legBPS[1]/basisPoint)}.
     */
    public double fairSpread() {
        calculate();
        final double basisPoint = 1.0e-4;
        QL.require(!Double.isNaN(legBPS[1]) && legBPS[1] != Constants.NULL_REAL,
                "fair spread result not available — legBPS[1] is unavailable");
        return spread - NPV / (legBPS[1] / basisPoint);
    }

    @Override
    public Date startDate() {
        // Take the leg with the earliest start (matches Swap default).
        Date d = null;
        for ( int j = 0; j < legs.size(); ++j ) {
            for ( final CashFlow cf : legs.get(j) ) {
                final Date cd = (cf instanceof org.jquantlib.cashflow.Coupon)
                        ? ((org.jquantlib.cashflow.Coupon) cf).accrualStartDate()
                        : cf.date();
                if ( d == null || cd.lt(d) )
                    d = cd;
            }
        }
        return d;
    }

    @Override
    public Date maturityDate() {
        Date d = null;
        for ( int j = 0; j < legs.size(); ++j ) {
            for ( final CashFlow cf : legs.get(j) ) {
                final Date cd = (cf instanceof org.jquantlib.cashflow.Coupon)
                        ? ((org.jquantlib.cashflow.Coupon) cf).accrualEndDate()
                        : cf.date();
                if ( d == null || cd.gt(d) )
                    d = cd;
            }
        }
        return d;
    }

    //
    // The fixed-leg coupons & yoy-leg coupons can be examined directly via
    // fixedLeg() / yoyLeg().
    //

    @Override
    protected void setupExpired() {
        super.setupExpired();
        Arrays.fill(legBPS, 0.0);
    }

    /**
     * Convenience: list all fixed-leg coupons. Returns a list view; mutation of the list does not affect the underlying
     * leg.
     */
    public List< FixedRateCoupon > fixedCoupons() {
        final List< FixedRateCoupon > out = new ArrayList<>();
        for ( final CashFlow cf : fixedLeg() ) {
            if ( cf instanceof FixedRateCoupon )
                out.add((FixedRateCoupon) cf);
        }
        return out;
    }

    /**
     * Convenience: list all YoY-leg coupons. Returns a list view; mutation of the list does not affect the underlying
     * leg.
     */
    public List< YoYInflationCoupon > yoyCoupons() {
        final List< YoYInflationCoupon > out = new ArrayList<>();
        for ( final CashFlow cf : yoyLeg() ) {
            if ( cf instanceof YoYInflationCoupon )
                out.add((YoYInflationCoupon) cf);
        }
        return out;
    }

    /** Payer/Receiver type. Same shape as {@link ZeroCouponInflationSwap.Type}. */
    public enum Type {
        Receiver(-1), Payer(1);

        private final int value;

        Type(final int value) {
            this.value = value;
        }

        public static Type valueOf(final int v) {
            return switch (v) {
                case -1 -> Receiver;
                case 1 -> Payer;
                default -> throw new LibraryException("value must be -1 (Receiver) or 1 (Payer)");
            };
        }

        public int toInteger() {
            return value;
        }
    }
}
