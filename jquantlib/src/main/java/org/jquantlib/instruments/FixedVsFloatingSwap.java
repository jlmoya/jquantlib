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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006, 2007 StatPro Italia srl
 Copyright (C) 2007 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

/**
 * Fixed-rate vs floating-rate swap.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::FixedVsFloatingSwap}
 * ({@code ql/instruments/fixedvsfloatingswap.{hpp,cpp}}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The fixed leg is built by this base class; the floating leg must be built
 * by concrete subclasses (see {@link #setupFloatingArguments(ArgumentsImpl)}).
 *
 * <p>This is a standalone port — the existing {@link VanillaSwap}/{@link OvernightIndexedSwap}
 * implementations still extend {@link Swap} directly to avoid refactoring their
 * many call-sites; refactoring them onto this base class is deferred to a later phase.
 *
 * <p>Warning: per the C++ docstring, if {@code Settings.includeReferenceDateCashFlows()}
 * is {@code true}, payments occurring at the swap's settlement date may be included in
 * the NPV and therefore affect fair-rate and fair-spread calculations.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public abstract class FixedVsFloatingSwap extends Swap {

    //
    // protected fields
    //

    protected final VanillaSwap.Type type_;
    protected final List< Double > fixedNominals_;
    protected final Schedule fixedSchedule_;
    protected final double fixedRate_;
    protected DayCounter fixedDayCount_;
    protected final List< Double > floatingNominals_;
    protected final Schedule floatingSchedule_;
    protected final IborIndex iborIndex_;
    protected final double spread_;
    protected final DayCounter floatingDayCount_;
    protected BusinessDayConvention paymentConvention_;

    // results
    protected double fairRate_ = Constants.NULL_REAL;
    protected double fairSpread_ = Constants.NULL_REAL;

    protected boolean constantNominals_ = false;
    protected boolean sameNominals_ = false;

    //
    // protected constructors (always invoked by subclasses)
    //

    /**
     * Full constructor. Mirrors v1.42.1 {@code FixedVsFloatingSwap::FixedVsFloatingSwap} signature.
     *
     * @param type             receiver vs payer w.r.t. the fixed leg
     * @param fixedNominals    per-coupon fixed nominals
     * @param fixedSchedule    fixed-leg schedule
     * @param fixedRate        fixed rate
     * @param fixedDayCount    fixed-leg day counter (if {@code null} uses {@code iborIndex.dayCounter()})
     * @param floatingNominals per-coupon floating nominals
     * @param floatingSchedule floating-leg schedule
     * @param iborIndex        floating index (must be non-null)
     * @param spread           floating-leg spread
     * @param floatingDayCount floating-leg day counter
     * @param paymentConvention payment business-day-convention; if {@code null} uses {@code floatingSchedule.businessDayConvention()}
     * @param paymentLag       business-day payment lag
     * @param paymentCalendar  calendar used for payment-lag application (if empty, the fixed schedule's calendar is used)
     */
    protected FixedVsFloatingSwap(final VanillaSwap.Type type,
            final List< Double > fixedNominals, final Schedule fixedSchedule,
            final double fixedRate, final DayCounter fixedDayCount,
            final List< Double > floatingNominals, final Schedule floatingSchedule,
            final IborIndex iborIndex, final double spread, final DayCounter floatingDayCount,
            final BusinessDayConvention paymentConvention, final int paymentLag,
            final Calendar paymentCalendar) {
        super(2);
        this.type_ = type;
        this.fixedNominals_ = fixedNominals;
        this.fixedSchedule_ = fixedSchedule;
        this.fixedRate_ = fixedRate;
        this.fixedDayCount_ = fixedDayCount;
        this.floatingNominals_ = floatingNominals;
        this.floatingSchedule_ = floatingSchedule;
        this.iborIndex_ = iborIndex;
        this.spread_ = spread;
        this.floatingDayCount_ = floatingDayCount;

        QL.require(iborIndex_ != null, "null floating index provided");

        if ( this.fixedDayCount_ == null || this.fixedDayCount_.empty() ) {
            this.fixedDayCount_ = iborIndex_.dayCounter();
        }

        this.paymentConvention_ = (paymentConvention != null)
                ? paymentConvention
                : floatingSchedule_.businessDayConvention();

        // Fixed leg
        final FixedRateLeg fixedLegBuilder = new FixedRateLeg(fixedSchedule_, fixedDayCount_)
                .withNotionals(toDoubleArray(fixedNominals_))
                .withCouponRates(fixedRate_)
                .withPaymentAdjustment(paymentConvention_)
                .withPaymentLag(paymentLag)
                .withPaymentCalendar(
                        (paymentCalendar == null || paymentCalendar.empty())
                                ? fixedSchedule_.calendar()
                                : paymentCalendar);
        this.legs.add(fixedLegBuilder);
        this.legs.add(new Leg()); // placeholder; subclass populates

        // type signs
        switch ( type_ ) {
            case Payer -> {
                this.payer[0] = -1.0;
                this.payer[1] = +1.0;
            }
            case Receiver -> {
                this.payer[0] = +1.0;
                this.payer[1] = -1.0;
            }
            default -> throw new org.jquantlib.lang.exceptions.LibraryException("Unknown vanilla-swap type");
        }

        // Compute sameNominals_ / constantNominals_ flags (mirrors C++ ctor tail).
        sameNominals_ = listsEqual(fixedNominals_, floatingNominals_);
        if ( !sameNominals_ ) {
            constantNominals_ = false;
        } else {
            constantNominals_ = true;
            final double front = fixedNominals_.isEmpty() ? 0.0 : fixedNominals_.get(0);
            for ( final double x : fixedNominals_ ) {
                if ( x != front ) {
                    constantNominals_ = false;
                    break;
                }
            }
        }
    }

    private static double[] toDoubleArray(final List< Double > xs) {
        final double[] out = new double[xs.size()];
        for ( int i = 0; i < out.length; i++ ) {
            out[i] = xs.get(i);
        }
        return out;
    }

    private static boolean listsEqual(final List< Double > a, final List< Double > b) {
        if ( a.size() != b.size() ) {
            return false;
        }
        for ( int i = 0; i < a.size(); i++ ) {
            if ( a.get(i).doubleValue() != b.get(i).doubleValue() ) {
                return false;
            }
        }
        return true;
    }

    //
    // inspectors (mirror C++ inline definitions)
    //

    public VanillaSwap.Type type() {
        return type_;
    }

    /** Throws if the nominal is not constant across coupons. */
    public double nominal() {
        QL.require(constantNominals_, "nominal is not constant");
        return fixedNominals_.get(0);
    }

    /** Throws if the nominals differ between the two legs. */
    public List< Double > nominals() {
        QL.require(sameNominals_, "different nominals on fixed and floating leg");
        return fixedNominals_;
    }

    public List< Double > fixedNominals() {
        return fixedNominals_;
    }

    public Schedule fixedSchedule() {
        return fixedSchedule_;
    }

    public double fixedRate() {
        return fixedRate_;
    }

    public DayCounter fixedDayCount() {
        return fixedDayCount_;
    }

    public List< Double > floatingNominals() {
        return floatingNominals_;
    }

    public Schedule floatingSchedule() {
        return floatingSchedule_;
    }

    public IborIndex iborIndex() {
        return iborIndex_;
    }

    public double spread() {
        return spread_;
    }

    public DayCounter floatingDayCount() {
        return floatingDayCount_;
    }

    public BusinessDayConvention paymentConvention() {
        return paymentConvention_;
    }

    public Leg fixedLeg() {
        return legs.get(0);
    }

    public Leg floatingLeg() {
        return legs.get(1);
    }

    //
    // results (mirror C++ result accessors)
    //

    public double fairRate() {
        calculate();
        QL.require(fairRate_ != Constants.NULL_REAL, "result not available");
        return fairRate_;
    }

    public double fairSpread() {
        calculate();
        QL.require(fairSpread_ != Constants.NULL_REAL, "result not available");
        return fairSpread_;
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

    //
    // overrides Swap
    //

    @Override
    protected void setupExpired() {
        super.setupExpired();
        legBPS[0] = legBPS[1] = 0.0;
        fairRate_ = Constants.NULL_REAL;
        fairSpread_ = Constants.NULL_REAL;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);

        if ( !(args instanceof FixedVsFloatingSwap.ArgumentsImpl arguments) ) {
            // it's a swap engine — fine, nothing more to populate
            return;
        }

        arguments.type = type_;
        arguments.nominal = constantNominals_ ? nominal() : Constants.NULL_REAL;

        final Leg fixedCoupons = fixedLeg();
        final int n = fixedCoupons.size();

        arguments.fixedResetDates = new Date[n];
        arguments.fixedPayDates = new Date[n];
        arguments.fixedNominals = new double[n];
        arguments.fixedCoupons = new double[n];

        for ( int i = 0; i < n; i++ ) {
            final CashFlow cf = fixedCoupons.get(i);
            QL.require(cf instanceof FixedRateCoupon, "non-FixedRateCoupon in fixed leg");
            final FixedRateCoupon coupon = (FixedRateCoupon) cf;
            arguments.fixedPayDates[i] = coupon.date();
            arguments.fixedResetDates[i] = coupon.accrualStartDate();
            arguments.fixedCoupons[i] = coupon.amount();
            arguments.fixedNominals[i] = coupon.nominal();
        }

        setupFloatingArguments(arguments);
    }

    /**
     * Populates the floating-leg portion of the arguments. Mirrors C++ pure-virtual
     * {@code FixedVsFloatingSwap::setupFloatingArguments(arguments*)}.
     */
    protected abstract void setupFloatingArguments(ArgumentsImpl args);

    @Override
    public void fetchResults(final PricingEngine.Results r) {
        final double basisPoint = 1.0e-4;
        super.fetchResults(r);

        if ( r instanceof FixedVsFloatingSwap.ResultsImpl results ) {
            fairRate_ = results.fairRate;
            fairSpread_ = results.fairSpread;
        } else {
            // might be a generic Swap engine; nothing to do here beyond defaulting
            fairRate_ = Constants.NULL_REAL;
            fairSpread_ = Constants.NULL_REAL;
        }

        if ( fairRate_ == Constants.NULL_REAL ) {
            if ( !Double.isNaN(legBPS[0]) ) {
                fairRate_ = fixedRate_ - NPV / (legBPS[0] / basisPoint);
            }
        }
        if ( fairSpread_ == Constants.NULL_REAL ) {
            if ( !Double.isNaN(legBPS[1]) ) {
                fairSpread_ = spread_ - NPV / (legBPS[1] / basisPoint);
            }
        }
    }

    //
    // inner types
    //

    /** Marker. */
    public interface Arguments extends Swap.Arguments { /* marker */
    }

    /** Marker. */
    public interface Results extends Swap.Results { /* marker */
    }

    /**
     * Arguments DTO for FixedVsFloatingSwap. Mirrors C++
     * {@code FixedVsFloatingSwap::arguments}.
     */
    public static class ArgumentsImpl extends Swap.ArgumentsImpl implements FixedVsFloatingSwap.Arguments {
        public VanillaSwap.Type type = VanillaSwap.Type.Receiver;
        public double nominal = Constants.NULL_REAL;

        public double[] fixedNominals;
        public Date[] fixedResetDates;
        public Date[] fixedPayDates;
        public double[] floatingNominals;
        public double[] floatingAccrualTimes;
        public Date[] floatingResetDates;
        public Date[] floatingFixingDates;
        public Date[] floatingPayDates;

        public double[] fixedCoupons;
        public double[] floatingSpreads;
        public double[] floatingCoupons;

        @Override
        public void validate() {
            super.validate();
            QL.require(fixedNominals.length == fixedPayDates.length,
                    "number of fixed nominals different from number of fixed payment dates");
            QL.require(fixedResetDates.length == fixedPayDates.length,
                    "number of fixed start dates different from number of fixed payment dates");
            QL.require(fixedPayDates.length == fixedCoupons.length,
                    "number of fixed payment dates different from number of fixed coupon amounts");
            QL.require(floatingNominals.length == floatingPayDates.length,
                    "number of floating nominals different from number of floating payment dates");
            QL.require(floatingResetDates.length == floatingPayDates.length,
                    "number of floating start dates different from number of floating payment dates");
            QL.require(floatingFixingDates.length == floatingPayDates.length,
                    "number of floating fixing dates different from number of floating payment dates");
            QL.require(floatingAccrualTimes.length == floatingPayDates.length,
                    "number of floating accrual Times different from number of floating payment dates");
            QL.require(floatingSpreads.length == floatingPayDates.length,
                    "number of floating spreads different from number of floating payment dates");
            QL.require(floatingPayDates.length == floatingCoupons.length,
                    "number of floating payment dates different from number of floating coupon amounts");
        }
    }

    /**
     * Results DTO. Mirrors C++ {@code FixedVsFloatingSwap::results}.
     */
    public static class ResultsImpl extends Swap.ResultsImpl implements FixedVsFloatingSwap.Results {
        public double fairRate = Constants.NULL_REAL;
        public double fairSpread = Constants.NULL_REAL;

        @Override
        public void reset() {
            super.reset();
            fairRate = Constants.NULL_REAL;
            fairSpread = Constants.NULL_REAL;
        }
    }
}
