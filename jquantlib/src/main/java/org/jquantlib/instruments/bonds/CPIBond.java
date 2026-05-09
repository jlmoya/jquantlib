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
 Copyright (C) 2010, 2011 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments.bonds;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CPICashFlow;
import org.jquantlib.cashflow.CPICoupon;
import org.jquantlib.cashflow.CPICouponPricer;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.instruments.Bond;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

/**
 * Zero-inflation-indexed-ratio-with-base bond.
 *
 * <p>If there is only one date in the schedule it is a zero bond returning an
 * inflated notional.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::CPIBond}
 * ({@code ql/instruments/bonds/cpibond.{hpp,cpp}}).
 *
 * <p>The C++ class delegates cash-flow construction to {@code CPILeg}. JQuantLib
 * does not yet have a standalone {@code CPILeg} class (deferred — see {@code
 * CPISwap}'s inline {@code buildCpiLeg}); the bond therefore replicates the
 * subset of {@code CPILeg::operator Leg()} it needs (single fixed-rate
 * sequence, optional ex-coupon period, no caps/floors). When a generic
 * {@code CPILeg} class is added in a later phase this method should be
 * refactored to use it.
 *
 * @author JQuantLib migration team (Phase 2v Track B.1)
 */
public class CPIBond extends Bond {

    //
    // protected fields (mirrors C++ protected members)
    //

    protected Frequency frequency_;
    protected DayCounter dayCounter_;
    protected boolean growthOnly_;
    protected double baseCPI_;
    protected Period observationLag_;
    protected ZeroInflationIndex cpiIndex_;
    protected CPI.InterpolationType observationInterpolation_;

    //
    // public constructors
    //

    /**
     * Primary constructor — mirrors C++ {@code CPIBond} non-deprecated
     * overload (no {@code growthOnly} parameter; defaults to {@code false}).
     */
    public CPIBond(final /* @Natural */ int settlementDays,
                   final /* @Real */ double faceAmount,
                   final /* @Real */ double baseCPI,
                   final Period observationLag,
                   final ZeroInflationIndex cpiIndex,
                   final CPI.InterpolationType observationInterpolation,
                   final Schedule schedule,
                   final double[] coupons,
                   final DayCounter accrualDayCounter,
                   final BusinessDayConvention paymentConvention,
                   final Date issueDate,
                   final Calendar paymentCalendar,
                   final Period exCouponPeriod,
                   final Calendar exCouponCalendar,
                   final BusinessDayConvention exCouponConvention,
                   final boolean exCouponEndOfMonth) {
        this(settlementDays, faceAmount, /* growthOnly */ false, baseCPI, observationLag,
                cpiIndex, observationInterpolation, schedule, coupons, accrualDayCounter,
                paymentConvention, issueDate, paymentCalendar, exCouponPeriod,
                exCouponCalendar, exCouponConvention, exCouponEndOfMonth);
    }

    /**
     * Convenience constructor with C++ defaults: {@code paymentConvention =
     * ModifiedFollowing}, {@code issueDate = Date()}, default
     * {@code paymentCalendar} (use schedule's calendar), no ex-coupon period.
     */
    public CPIBond(final int settlementDays,
                   final double faceAmount,
                   final double baseCPI,
                   final Period observationLag,
                   final ZeroInflationIndex cpiIndex,
                   final CPI.InterpolationType observationInterpolation,
                   final Schedule schedule,
                   final double[] coupons,
                   final DayCounter accrualDayCounter,
                   final BusinessDayConvention paymentConvention) {
        this(settlementDays, faceAmount, baseCPI, observationLag, cpiIndex,
                observationInterpolation, schedule, coupons, accrualDayCounter,
                paymentConvention, /* issueDate */ new Date(),
                /* paymentCalendar */ new Calendar(),
                /* exCouponPeriod */ new Period(),
                /* exCouponCalendar */ new Calendar(),
                /* exCouponConvention */ BusinessDayConvention.Unadjusted,
                /* exCouponEndOfMonth */ false);
    }

    /**
     * Convenience constructor with C++ defaults including
     * {@code paymentConvention = ModifiedFollowing}.
     */
    public CPIBond(final int settlementDays,
                   final double faceAmount,
                   final double baseCPI,
                   final Period observationLag,
                   final ZeroInflationIndex cpiIndex,
                   final CPI.InterpolationType observationInterpolation,
                   final Schedule schedule,
                   final double[] coupons,
                   final DayCounter accrualDayCounter) {
        this(settlementDays, faceAmount, baseCPI, observationLag, cpiIndex,
                observationInterpolation, schedule, coupons, accrualDayCounter,
                BusinessDayConvention.ModifiedFollowing);
    }

    /**
     * Deprecated overload with explicit {@code growthOnly} parameter — mirrors
     * C++ {@code CPIBond} deprecated ctor (deprecated in QuantLib 1.40 in
     * favor of the overload without {@code growthOnly}).
     *
     * @deprecated Use the overload without the {@code growthOnly} parameter.
     */
    @Deprecated
    public CPIBond(final /* @Natural */ int settlementDays,
                   final /* @Real */ double faceAmount,
                   final boolean growthOnly,
                   final /* @Real */ double baseCPI,
                   final Period observationLag,
                   final ZeroInflationIndex cpiIndex,
                   final CPI.InterpolationType observationInterpolation,
                   final Schedule schedule,
                   final double[] coupons,
                   final DayCounter accrualDayCounter,
                   final BusinessDayConvention paymentConvention,
                   final Date issueDate,
                   final Calendar paymentCalendar,
                   final Period exCouponPeriod,
                   final Calendar exCouponCalendar,
                   final BusinessDayConvention exCouponConvention,
                   final boolean exCouponEndOfMonth) {
        // C++ Bond(settlementDays,
        //         paymentCalendar == Calendar() ? schedule.calendar() : paymentCalendar,
        //         issueDate)
        super(settlementDays,
              (paymentCalendar == null || paymentCalendar.empty())
                      ? schedule.calendar() : paymentCalendar,
              issueDate);

        this.frequency_ = schedule.tenor().frequency();
        this.dayCounter_ = accrualDayCounter;
        this.growthOnly_ = growthOnly;
        this.baseCPI_ = baseCPI;
        this.observationLag_ = observationLag;
        this.cpiIndex_ = cpiIndex;
        this.observationInterpolation_ = observationInterpolation;

        this.maturityDate_ = schedule.endDate().clone();

        // Build cashflows mirroring C++ CPILeg(schedule, cpiIndex, baseCPI,
        // observationLag).withNotionals(faceAmount).withFixedRates(fixedRate)
        //   .withPaymentDayCounter(accrualDayCounter)
        //   .withPaymentAdjustment(paymentConvention)
        //   .withPaymentCalendar(calendar_)
        //   .withObservationInterpolation(observationInterpolation_)
        //   .withSubtractInflationNominal(growthOnly_)
        //   .withExCouponPeriod(...)
        // (cpibond.cpp:85-97)
        this.cashflows_ = buildCpiLeg(
                schedule, faceAmount, coupons, accrualDayCounter,
                paymentConvention, calendar_,
                exCouponPeriod, exCouponCalendar,
                exCouponConvention, exCouponEndOfMonth);

        // mirrors C++ calculateNotionalsFromCashflows()
        calculateNotionalsFromCashflows();

        // mirrors C++ redemptions_.push_back(cashflows_.back())
        redemptions_.add(cashflows_.last());

        // mirrors C++ registerWith(cpiIndex_) + per-cashflow registerWith
        if (cpiIndex_ != null) {
            cpiIndex_.addObserver(this);
        }
        for (final CashFlow cf : cashflows_) {
            cf.addObserver(this);
        }
    }

    //
    // public inspectors (mirrors C++ inline accessors)
    //

    public Frequency frequency() {
        return frequency_;
    }

    public DayCounter dayCounter() {
        return dayCounter_;
    }

    public boolean growthOnly() {
        return growthOnly_;
    }

    public double baseCPI() {
        return baseCPI_;
    }

    public Period observationLag() {
        return observationLag_;
    }

    public ZeroInflationIndex cpiIndex() {
        return cpiIndex_;
    }

    public CPI.InterpolationType observationInterpolation() {
        return observationInterpolation_;
    }

    //
    // private helpers
    //

    /**
     * Replicates the C++ {@code CPILeg::operator Leg()} algorithm
     * ({@code cashflows/cpicoupon.cpp:272-352}) for the subset used by
     * {@code CPIBond}: single fixed-rate sequence (or zero-rate path
     * producing {@code FixedRateCoupon}s), optional ex-coupon period, no
     * caps/floors. Always emits a terminal {@link CPICashFlow} for the
     * notional.
     *
     * <p>Default day-counter and calendar handling mirrors C++ — paymentCalendar
     * defaults to schedule.calendar() inside {@code CPILeg} ctor (already
     * resolved in our caller), payment day-counter defaults to
     * {@code Thirty360(BondBasis)} but C++ {@code CPIBond} always overrides via
     * {@code .withPaymentDayCounter(accrualDayCounter)}, so we use
     * {@code accrualDayCounter} directly.
     */
    private Leg buildCpiLeg(final Schedule schedule,
                            final double faceAmount,
                            final double[] fixedRates,
                            final DayCounter paymentDayCounter,
                            final BusinessDayConvention paymentConvention,
                            final Calendar paymentCalendar,
                            final Period exCouponPeriod,
                            final Calendar exCouponCalendar,
                            final BusinessDayConvention exCouponConvention,
                            final boolean exCouponEndOfMonth) {
        QL.require(faceAmount != 0.0 || schedule.size() == 1,
                "no notional given");

        final int n = schedule.size() - 1; // number of coupon periods
        final Leg leg = new Leg();

        // C++: baseDate may be defaulted from schedule.date(0) - observationLag
        // when both baseDate and baseCPI are unset; CPIBond always supplies
        // baseCPI_, so we keep baseDate as null (default-constructed) — the
        // CPICoupon / CPICashFlow will resolve via the supplied baseCPI_.
        final Date baseDate = new Date();

        if (n > 0) {
            QL.require(fixedRates != null && fixedRates.length > 0,
                    "no fixedRates given");

            for (int i = 0; i < n; ++i) {
                Date refStart = schedule.date(i);
                Date start = refStart;
                Date refEnd = schedule.date(i + 1);
                Date end = refEnd;
                final Date paymentDate = paymentCalendar.adjust(end, paymentConvention);

                Date exCouponDate = new Date();
                if (exCouponPeriod != null && exCouponPeriod.length() != 0) {
                    exCouponDate = exCouponCalendar.advance(
                            paymentDate,
                            exCouponPeriod.negative(),
                            exCouponConvention,
                            exCouponEndOfMonth);
                }

                // C++ short/long-stub adjustment for irregular first/last
                // periods (cpicoupon.cpp:306-313). JQuantLib Schedule
                // exposes hasIsRegular semantics through isRegular(i+1) /
                // size() / fullInterface() — when fullInterface_ is false
                // isRegular() returns true for all periods, so the branch
                // is a no-op; matches C++ when hasIsRegular()==false.
                if (i == 0 && schedule.size() > 1 && !schedule.isRegular(i + 1)) {
                    final BusinessDayConvention bdc = schedule.businessDayConvention();
                    refStart = schedule.calendar().adjust(end.sub(schedule.tenor()), bdc);
                }
                if (i == n - 1 && schedule.size() > 1 && !schedule.isRegular(i + 1)) {
                    final BusinessDayConvention bdc = schedule.businessDayConvention();
                    refEnd = schedule.calendar().adjust(start.add(schedule.tenor()), bdc);
                }

                final double rateI = (i < fixedRates.length)
                        ? fixedRates[i] : fixedRates[fixedRates.length - 1];
                final double notionalI = faceAmount; // CPIBond uses single notional

                if (rateI == 0.0) {
                    // Zero-rate optimization: emit a FixedRateCoupon at
                    // the (possibly capped/floored) effective rate. CPIBond
                    // does not pass caps/floors so the effective rate is 0.0.
                    leg.add(new FixedRateCoupon(notionalI, paymentDate,
                            0.0, paymentDayCounter,
                            start, end, refStart, refEnd));
                } else {
                    // Simple swaplet path — CPICoupon. Caps/floors not
                    // supported on CPIBond; mirror C++ which would QL_FAIL.
                    leg.add(new CPICoupon(
                            baseCPI_, baseDate,
                            paymentDate,
                            notionalI,
                            start, end,
                            cpiIndex_,
                            observationLag_,
                            observationInterpolation_,
                            paymentDayCounter,
                            rateI,
                            refStart, refEnd, exCouponDate));
                }
            }
        }

        // Always emit a terminal CPICashFlow notional flow (mirrors C++
        // cpicoupon.cpp:341-346).
        final Date payDate = paymentCalendar.adjust(schedule.date(n), paymentConvention);
        final CPICashFlow notionalFlow = new CPICashFlow(
                faceAmount, cpiIndex_,
                baseDate, baseCPI_,
                schedule.date(n),
                observationLag_,
                observationInterpolation_,
                payDate,
                growthOnly_);
        leg.add(notionalFlow);

        // Attach a default CPICouponPricer to every CPICoupon (mirrors C++
        // setCouponPricer(leg, make_shared<CPICouponPricer>())). The pricer
        // does not require a nominal term-structure for swaplet-rate
        // computations needed by Bond.dirtyPrice / accruedAmount via the
        // DiscountingBondEngine; the engine supplies its own discount curve.
        final CPICouponPricer pricer = new CPICouponPricer();
        for (final CashFlow cf : leg) {
            if (cf instanceof CPICoupon) {
                ((CPICoupon) cf).setPricer(pricer);
            }
        }
        return leg;
    }

    // suppress unused-import warnings for TimeUnit (kept for readability of
    // the buildCpiLeg algorithm where periods are used)
    @SuppressWarnings("unused")
    private static final TimeUnit __FORCE_TIMEUNIT_IMPORT = TimeUnit.Days;
}
