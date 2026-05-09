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
 Copyright (C) 2011 Chris Kenyon
 Copyright (C) 2022 Quaternion Risk Management Ltd

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;

/**
 * Helper class building a sequence of capped/floored CPI coupons.
 *
 * <p>Also allowing for the inflated notional at the end (especially if there
 * is only one date in the schedule). If the fixed rate is zero you get a
 * {@link FixedRateCoupon}; otherwise you get a {@link CPICoupon}. Always
 * emits a terminal {@link CPICashFlow} for the notional.
 *
 * <p>Mirrors C++ {@code QuantLib::CPILeg} at v1.42.1
 * (cashflows/cpicoupon.{hpp,cpp}). The C++ class uses an
 * {@code operator Leg() const} implicit conversion; the Java port exposes
 * {@link #Leg()} as the explicit terminal builder method (mirroring
 * {@link IborLeg#Leg()}).
 *
 * @author JQuantLib migration team (Phase 2x A.2)
 */
public class CPILeg {

    //
    // private fields
    //

    private final Schedule schedule_;
    private final ZeroInflationIndex index_;
    private final double baseCPI_;
    private final Period observationLag_;
    private double[] notionals_;
    private double[] fixedRates_;
    private DayCounter paymentDayCounter_;
    private BusinessDayConvention paymentAdjustment_;
    private Calendar paymentCalendar_;
    private CPI.InterpolationType observationInterpolation_;
    private boolean subtractInflationNominal_;
    private double[] caps_;
    private double[] floors_;
    private Period exCouponPeriod_;
    private Calendar exCouponCalendar_;
    private BusinessDayConvention exCouponAdjustment_;
    private boolean exCouponEndOfMonth_;
    private Date baseDate_;

    //
    // public constructors
    //

    public CPILeg(final Schedule schedule,
                  final ZeroInflationIndex index,
                  final double baseCPI,
                  final Period observationLag) {
        this.schedule_ = schedule;
        this.index_ = index;
        this.baseCPI_ = baseCPI;
        this.observationLag_ = observationLag;
        this.notionals_ = new double[0];
        this.fixedRates_ = new double[0];
        // C++ default: Thirty360(BondBasis); CPIBond/CPISwap typically
        // override via withPaymentDayCounter(...).
        this.paymentDayCounter_ = new Thirty360();
        this.paymentAdjustment_ = BusinessDayConvention.ModifiedFollowing;
        this.paymentCalendar_ = schedule.calendar();
        this.observationInterpolation_ = CPI.InterpolationType.AsIndex;
        this.subtractInflationNominal_ = true;
        this.caps_ = new double[0];
        this.floors_ = new double[0];
        this.exCouponPeriod_ = new Period();
        this.exCouponCalendar_ = new Calendar();
        this.exCouponAdjustment_ = BusinessDayConvention.Following;
        this.exCouponEndOfMonth_ = false;
        this.baseDate_ = new Date(); // null date
    }

    //
    // builder setters
    //

    public CPILeg withNotionals(final double notional) {
        this.notionals_ = new double[] { notional };
        return this;
    }

    public CPILeg withNotionals(final double[] notionals) {
        this.notionals_ = notionals.clone();
        return this;
    }

    public CPILeg withFixedRates(final double fixedRate) {
        this.fixedRates_ = new double[] { fixedRate };
        return this;
    }

    public CPILeg withFixedRates(final double[] fixedRates) {
        this.fixedRates_ = fixedRates.clone();
        return this;
    }

    public CPILeg withPaymentDayCounter(final DayCounter dayCounter) {
        this.paymentDayCounter_ = dayCounter;
        return this;
    }

    public CPILeg withPaymentAdjustment(final BusinessDayConvention convention) {
        this.paymentAdjustment_ = convention;
        return this;
    }

    public CPILeg withPaymentCalendar(final Calendar cal) {
        this.paymentCalendar_ = cal;
        return this;
    }

    public CPILeg withObservationInterpolation(final CPI.InterpolationType interp) {
        this.observationInterpolation_ = interp;
        return this;
    }

    public CPILeg withSubtractInflationNominal(final boolean growthOnly) {
        this.subtractInflationNominal_ = growthOnly;
        return this;
    }

    public CPILeg withCaps(final double cap) {
        this.caps_ = new double[] { cap };
        return this;
    }

    public CPILeg withCaps(final double[] caps) {
        this.caps_ = caps.clone();
        return this;
    }

    public CPILeg withFloors(final double floor) {
        this.floors_ = new double[] { floor };
        return this;
    }

    public CPILeg withFloors(final double[] floors) {
        this.floors_ = floors.clone();
        return this;
    }

    public CPILeg withExCouponPeriod(final Period period,
                                     final Calendar cal,
                                     final BusinessDayConvention convention,
                                     final boolean endOfMonth) {
        this.exCouponPeriod_ = period;
        this.exCouponCalendar_ = cal;
        this.exCouponAdjustment_ = convention;
        this.exCouponEndOfMonth_ = endOfMonth;
        return this;
    }

    public CPILeg withExCouponPeriod(final Period period,
                                     final Calendar cal,
                                     final BusinessDayConvention convention) {
        return withExCouponPeriod(period, cal, convention, false);
    }

    public CPILeg withBaseDate(final Date baseDate) {
        this.baseDate_ = baseDate;
        return this;
    }

    //
    // terminal builder
    //

    /**
     * Mirrors C++ {@code CPILeg::operator Leg() const} (cpicoupon.cpp:272-352).
     */
    public Leg Leg() {
        QL.require(notionals_ != null && notionals_.length > 0, "no notional given");

        final int n = schedule_.size() - 1;
        final Leg leg = new Leg();
        // n+1: the +1 is for the terminal notional CPICashFlow

        Date baseDate = baseDate_;
        // BaseDate and baseCPI are not given: use schedule.date(0) -
        // observationLag and let the CPICashFlow / pricer resolve via the
        // inflation index.
        if (n > 0) {
            QL.require(fixedRates_ != null && fixedRates_.length > 0,
                    "no fixedRates given");

            if (baseDate_.isNull() && isNullCPI(baseCPI_)) {
                baseDate = schedule_.date(0).sub(observationLag_);
            }

            for (int i = 0; i < n; ++i) {
                Date refStart = schedule_.date(i);
                Date start = refStart;
                Date refEnd = schedule_.date(i + 1);
                Date end = refEnd;
                final Date paymentDate = paymentCalendar_.adjust(end, paymentAdjustment_);

                Date exCouponDate = new Date();
                if (exCouponPeriod_ != null && exCouponPeriod_.length() != 0) {
                    exCouponDate = exCouponCalendar_.advance(
                            paymentDate,
                            exCouponPeriod_.negative(),
                            exCouponAdjustment_,
                            exCouponEndOfMonth_);
                }

                // Short-stub adjustment for irregular first/last periods
                // (mirrors cpicoupon.cpp:306-313). JQuantLib Schedule
                // exposes isRegular(i+1) — when fullInterface_ is false
                // isRegular() returns true for all periods, so this branch
                // is a no-op (matches C++ when hasIsRegular()==false).
                if (i == 0 && schedule_.size() > 1 && !schedule_.isRegular(i + 1)) {
                    final BusinessDayConvention bdc = schedule_.businessDayConvention();
                    refStart = schedule_.calendar().adjust(end.sub(schedule_.tenor()), bdc);
                }
                if (i == n - 1 && schedule_.size() > 1 && !schedule_.isRegular(i + 1)) {
                    final BusinessDayConvention bdc = schedule_.businessDayConvention();
                    refEnd = schedule_.calendar().adjust(start.add(schedule_.tenor()), bdc);
                }

                if (get(fixedRates_, i, 1.0) == 0.0) {
                    // Zero-rate optimization: emit a FixedRateCoupon at the
                    // (possibly capped/floored) effective rate.
                    leg.add(new FixedRateCoupon(
                            get(notionals_, i, 0.0), paymentDate,
                            effectiveFixedRate(new double[0], caps_, floors_, i),
                            paymentDayCounter_,
                            start, end, refStart, refEnd));
                } else {
                    if (noOption(caps_, floors_, i)) {
                        leg.add(new CPICoupon(
                                baseCPI_, baseDate,
                                paymentDate,
                                get(notionals_, i, 0.0),
                                start, end,
                                index_, observationLag_,
                                observationInterpolation_,
                                paymentDayCounter_,
                                get(fixedRates_, i, 0.0),
                                refStart, refEnd, exCouponDate));
                    } else {
                        throw new LibraryException(
                                "caps/floors on CPI coupons not implemented.");
                    }
                }
            }
        }

        // Terminal notional cash flow — always present in CPI legs.
        final Date payDate = paymentCalendar_.adjust(schedule_.date(n), paymentAdjustment_);
        leg.add(new CPICashFlow(
                get(notionals_, n, 0.0), index_,
                baseDate, baseCPI_,
                schedule_.date(n),
                observationLag_,
                observationInterpolation_,
                payDate,
                subtractInflationNominal_));

        // Attach a default CPICouponPricer to every CPICoupon in the leg
        // (mirrors C++ setCouponPricer(leg, make_shared<CPICouponPricer>())).
        final CPICouponPricer pricer = new CPICouponPricer();
        for (final CashFlow cf : leg) {
            if (cf instanceof CPICoupon) {
                ((CPICoupon) cf).setPricer(pricer);
            }
        }
        return leg;
    }

    //
    // detail helpers (mirrors QuantLib::detail::get / noOption / effectiveFixedRate)
    //

    private static double get(final double[] v, final int i, final double defaultValue) {
        if (v == null || v.length == 0) {
            return defaultValue;
        } else if (i < v.length) {
            return v[i];
        } else {
            return v[v.length - 1];
        }
    }

    private static boolean noOption(final double[] caps, final double[] floors, final int i) {
        return isNullRate(get(caps, i, Constants.NULL_REAL))
                && isNullRate(get(floors, i, Constants.NULL_REAL));
    }

    private static double effectiveFixedRate(final double[] spreads,
                                             final double[] caps,
                                             final double[] floors,
                                             final int i) {
        double result = get(spreads, i, 0.0);
        final double floor = get(floors, i, Constants.NULL_REAL);
        if (!isNullRate(floor)) {
            result = Math.max(floor, result);
        }
        final double cap = get(caps, i, Constants.NULL_REAL);
        if (!isNullRate(cap)) {
            result = Math.min(cap, result);
        }
        return result;
    }

    private static boolean isNullCPI(final double v) {
        return Double.isNaN(v) || v == Constants.NULL_REAL;
    }

    private static boolean isNullRate(final double v) {
        return Double.isNaN(v) || v == Constants.NULL_REAL;
    }
}
