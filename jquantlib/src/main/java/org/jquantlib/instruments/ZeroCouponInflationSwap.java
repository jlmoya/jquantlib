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
 Copyright (C) 2021 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.cashflow.*;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Zero-coupon inflation-indexed swap.
 *
 * <p>Quoted as a fixed rate {@code K}. At start:
 * <pre>
 *   P_n(0,T) N [(1+K)^T - 1] = P_n(0,T) N [I(T)/I(0) - 1]
 * </pre>
 * where {@code T} is the maturity time, {@code P_n(0,t)} is the nominal discount factor at time {@code t}, {@code N} is
 * the notional, and {@code I(t)} is the inflation index value at time {@code t}.
 *
 * <p>This inherits from {@link Swap} and has two very simple legs: a fixed
 * leg, from the quote ({@code K}); and an indexed leg. At maturity the two single cashflows are swapped. These are the
 * notional versus the inflation-indexed notional. Because the coupons are zero there are no accruals (and no coupons).
 *
 * <p>In this swap, the passed type ({@code Payer} or {@code Receiver}) refers
 * to the inflation leg.
 *
 * <p>Inflation is generally available on every day, including holidays and
 * weekends. Hence there is a variable to state whether the observe/fix dates for inflation are adjusted or not. The
 * default is not to adjust.
 *
 * <p>A zero inflation swap is a simple enough instrument that the standard
 * discounting pricing engine that works for a vanilla swap also works here.
 *
 * <p>Mirrors C++ {@code QuantLib::ZeroCouponInflationSwap} at v1.42.1
 * (instruments/zerocouponinflationswap.{hpp,cpp}).
 *
 * @author JQuantLib migration team (Phase 2p A.3)
 */
public class ZeroCouponInflationSwap extends Swap {

    //
    // public inner enums
    //

    private final Type type;

    //
    // protected fields
    //
    private final double nominal;
    private final Date startDate;
    private final Date maturityDate;
    private final Calendar fixCalendar;
    private final BusinessDayConvention fixConvention;
    private final double fixedRate;
    private final ZeroInflationIndex infIndex;
    private final Period observationLag;
    private final CPI.InterpolationType observationInterpolation;
    private final boolean adjustInfObsDates;
    private final Calendar infCalendar;
    private final BusinessDayConvention infConvention;
    private final DayCounter dayCounter;
    private final Date baseDate;
    private final Date obsDate;
    /**
     * Construct a zero-coupon inflation-indexed swap, with default {@code adjustInfObsDates = false} and
     * {@code infCalendar / infConvention} equal to the fixed-side calendar / convention.
     */
    public ZeroCouponInflationSwap(final Type type, final double nominal, final Date startDate, final Date maturity,
            final Calendar fixCalendar, final BusinessDayConvention fixConvention, final DayCounter dayCounter,
            final double fixedRate, final ZeroInflationIndex infIndex, final Period observationLag,
            final CPI.InterpolationType observationInterpolation) {
        this(type, nominal, startDate, maturity, fixCalendar, fixConvention, dayCounter, fixedRate, infIndex,
                observationLag, observationInterpolation, false, new NullCalendar(), null);
    }

    //
    // public constructors
    //

    /**
     * Construct a zero-coupon inflation-indexed swap. Mirrors C++ v1.42.1 single-overload constructor (deprecated
     * overloads are not ported).
     */
    public ZeroCouponInflationSwap(final Type type, final double nominal, final Date startDate, final Date maturity,
            final Calendar fixCalendar, final BusinessDayConvention fixConvention, final DayCounter dayCounter,
            final double fixedRate, final ZeroInflationIndex infIndex, final Period observationLag,
            final CPI.InterpolationType observationInterpolation, final boolean adjustInfObsDates,
            final Calendar infCalendarIn, final BusinessDayConvention infConventionIn) {
        super(2);
        this.type = type;
        this.nominal = nominal;
        this.startDate = startDate.clone();
        this.maturityDate = maturity.clone();
        this.fixCalendar = fixCalendar;
        this.fixConvention = fixConvention;
        this.fixedRate = fixedRate;
        this.infIndex = infIndex;
        this.observationLag = observationLag;
        this.observationInterpolation = observationInterpolation;
        this.adjustInfObsDates = adjustInfObsDates;
        this.dayCounter = dayCounter;

        // Compatibility check between index and swap definitions.
        // Mirrors C++ detail::CPI::effectiveInterpolationType (AsIndex -> Flat,
        // others pass through), then checks Linear vs availability lag.
        final CPI.InterpolationType effInterp = (observationInterpolation == CPI.InterpolationType.AsIndex)
                ? CPI.InterpolationType.Flat
                : observationInterpolation;
        if ( effInterp == CPI.InterpolationType.Linear ) {
            final Period pShift = new Period(infIndex.frequency());
            QL.require(observationLag.sub(pShift).ge(infIndex.availabilityLag()),
                    "inconsistency between swap observation lag " + observationLag + ", interpolated index period "
                            + pShift + " and index availability " + infIndex.availabilityLag()
                            + ": need (obsLag-index period) >= availLag");
        } else {
            QL.require(infIndex.availabilityLag().le(observationLag),
                    "index tries to observe inflation fixings that do not yet exist: " + " availability lag "
                            + infIndex.availabilityLag() + " versus obs lag = " + observationLag);
        }

        // Default infCalendar / infConvention to fix calendar/convention if absent.
        // C++ uses default-constructed Calendar()/BusinessDayConvention() as the
        // "absent" sentinel. The Java port treats:
        //   * NullCalendar  → "absent" (same observable behavior — adjust() is no-op)
        //   * null bdc      → "absent"
        // This matches the C++ semantic for a fresh stack-default Calendar.
        this.infCalendar = (infCalendarIn instanceof NullCalendar) ? fixCalendar : infCalendarIn;
        this.infConvention = (infConventionIn == null) ? fixConvention : infConventionIn;

        final Date infPayDate = this.infCalendar.adjust(maturity, this.infConvention);
        final Date fixedPayDate = fixCalendar.adjust(maturity, fixConvention);

        final boolean growthOnly = true;

        final ZeroInflationCashFlow inflationCashFlow = new ZeroInflationCashFlow(nominal, infIndex,
                observationInterpolation, this.startDate, this.maturityDate, observationLag, infPayDate, growthOnly);

        this.baseDate = inflationCashFlow.baseDate();
        this.obsDate = inflationCashFlow.fixingDate();

        // At this point the index may not be able to forecast — i.e. we do not
        // want to force the existence of an inflation term structure before
        // allowing users to create instruments.
        final double T = dayCounter.yearFraction(this.startDate, this.maturityDate);
        // The -1.0 is because swaps only exchange growth, not notionals as well.
        final double fixedAmount = nominal * (JQuantMath.pow(1.0 + fixedRate, T) - 1.0);

        final SimpleCashFlow fixedCashFlow = new SimpleCashFlow(fixedAmount, fixedPayDate);

        final Leg fixedLeg = new Leg();
        fixedLeg.add(fixedCashFlow);
        final Leg inflationLeg = new Leg();
        inflationLeg.add(inflationCashFlow);

        this.legs.add(fixedLeg);
        this.legs.add(inflationLeg);

        // Mirror C++ registerWith(inflationCashFlow): observe the cashflow so
        // the swap re-prices when the index updates propagate through it.
        inflationCashFlow.addObserver(this);

        switch ( type ) {
        case Payer:
            this.payer[0] = +1.0;
            this.payer[1] = -1.0;
            break;
        case Receiver:
            this.payer[0] = -1.0;
            this.payer[1] = +1.0;
            break;
        default:
            throw new LibraryException("unknown zero-inflation-swap type");
        }
    }

    /** "Payer" or "Receiver" refers to the inflation leg. */
    public Type type() {
        return type;
    }

    //
    // public methods — inspectors
    //

    public double nominal() {
        return nominal;
    }

    @Override
    public Date startDate() {
        return startDate.clone();
    }

    @Override
    public Date maturityDate() {
        return maturityDate.clone();
    }

    public Calendar fixedCalendar() {
        return fixCalendar;
    }

    public BusinessDayConvention fixedConvention() {
        return fixConvention;
    }

    public DayCounter dayCounter() {
        return dayCounter;
    }

    /** {@code K} in the formula in the class docstring. */
    public double fixedRate() {
        return fixedRate;
    }

    public ZeroInflationIndex inflationIndex() {
        return infIndex;
    }

    public Period observationLag() {
        return observationLag;
    }

    public CPI.InterpolationType observationInterpolation() {
        return observationInterpolation;
    }

    public boolean adjustObservationDates() {
        return adjustInfObsDates;
    }

    public Calendar inflationCalendar() {
        return infCalendar;
    }

    public BusinessDayConvention inflationConvention() {
        return infConvention;
    }

    /** Just one cashflow (that is not a coupon) in each leg. */
    public final Leg fixedLeg() {
        return legs.get(0);
    }

    /** Just one cashflow (that is not a coupon) in each leg. */
    public final Leg inflationLeg() {
        return legs.get(1);
    }

    public double fixedLegNPV() {
        calculate();
        QL.require(legNPV[0] != Constants.NULL_REAL, "result not available");
        return legNPV[0];
    }

    //
    // public methods — results
    //

    public double inflationLegNPV() {
        calculate();
        QL.require(legNPV[1] != Constants.NULL_REAL, "result not available");
        return legNPV[1];
    }

    /**
     * Analytic fixed-leg basis-point sensitivity.
     *
     * <p>{@code legBPS_[0]} (from the engine) is 0 because the fixed leg uses
     * a {@link SimpleCashFlow}; the BPS calculator assumes simple cashflows are insensitive to {@code fixedRate} and
     * that all coupons are linear in {@code fixedRate}, but the ZCIIS fixed leg uses annual compounding so neither
     * assumption holds. We compute it directly here.
     *
     * <p>Mirrors C++ v1.42.1 {@code ZeroCouponInflationSwap::fixedLegBPS}.
     */
    public double fixedLegBPS() {
        calculate();
        // The discount factor used here is the one applied to the fixed leg's
        // single cashflow during NPV computation. C++ takes it from the
        // engine's results.endDiscounts[0]; the Java DiscountingSwapEngine
        // does not currently populate endDiscounts (the Java Swap.Results does
        // not even declare such a field), so we recover it as
        //   df_signed = legNPV[0] / fixedAmount
        // where fixedAmount = nominal * ((1+r)^T - 1) and legNPV is signed by
        // payer[0]. This yields exactly the C++ value
        //   df = payer_[0] * endDiscounts_[0]
        // when fixedAmount != 0.
        final double T = dayCounter.yearFraction(startDate, maturityDate);
        final double fixedAmount = nominal * (JQuantMath.pow(1.0 + fixedRate, T) - 1.0);
        QL.require(fixedAmount != 0.0, "cannot compute fixedLegBPS when fixedAmount is zero");
        QL.require(!Double.isNaN(legNPV[0]) && legNPV[0] != Constants.NULL_REAL,
                "cannot compute fixedLegBPS when legNPV[0] is unavailable");
        final double dfSigned = legNPV[0] / fixedAmount;

        final double basisPoint = 1.0e-4;
        return dfSigned * nominal * (JQuantMath.pow(1.0 + fixedRate + basisPoint, T) - JQuantMath.pow(1.0 + fixedRate,
                T));
    }

    /**
     * Fair fixed rate. Always means that NPV is zero for this instrument if it was created with this rate (knowing the
     * time from base to obs etc).
     *
     * <p>Mirrors C++ v1.42.1 {@code ZeroCouponInflationSwap::fairRate}.
     */
    public double fairRate() {
        // Pull the inflation cashflow's amount/notional ratio and apply the
        // closed form: growth = amount/notional + 1 (because growthOnly=true
        // in the cashflow), then fairRate = growth^(1/T) - 1.
        final IndexedCashFlow icf = downcastInflationLeg();
        final double growth = icf.amount() / icf.notional() + 1.0;
        final double T = dayCounter.yearFraction(startDate, maturityDate);
        return JQuantMath.pow(growth, 1.0 / T) - 1.0;
    }

    private IndexedCashFlow downcastInflationLeg() {
        final CashFlow cf = legs.get(1).get(0);
        QL.require(cf instanceof IndexedCashFlow, "failed to downcast to IndexedCashFlow in fairRate()");
        return (IndexedCashFlow) cf;
    }

    /**
     * Payer/Receiver type. Mirrors C++ {@code Swap::Type} ({@code Receiver = -1, Payer = 1}).
     *
     * <p>The Java port follows {@link VanillaSwap.Type} convention rather
     * than reusing {@code VanillaSwap.Type} directly, to keep the C++ {@code ZeroCouponInflationSwap} signature
     * self-contained (avoiding a dependency on the unrelated VanillaSwap class).
     */
    public enum Type {
        Receiver(-1), Payer(1);

        private final int value;

        Type(final int value) {
            this.value = value;
        }

        public static Type valueOf(final int v) {
            switch ( v ) {
            case -1:
                return Receiver;
            case 1:
                return Payer;
            default:
                throw new LibraryException("value must be -1 (Receiver) or 1 (Payer)");
            }
        }

        public int toInteger() {
            return value;
        }
    }

    //
    // engine plumbing — argument carrier matching C++ ZeroCouponInflationSwap::arguments
    //

    /**
     * Argument carrier matching C++ {@code ZeroCouponInflationSwap::arguments}. Inherits the leg/payer machinery from
     * {@link Swap.ArgumentsImpl} and adds {@code fixedRate}.
     */
    public static class Arguments extends Swap.ArgumentsImpl {
        public double fixedRate;
    }
}
