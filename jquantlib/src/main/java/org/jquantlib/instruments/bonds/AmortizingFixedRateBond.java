/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2008 Simon Ibbotson

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/

package org.jquantlib.instruments.bonds;

import org.jquantlib.QL;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Bond;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Amortizing fixed-rate bond.
 *
 * Java port of QuantLib v1.42.1
 * {@code ql/instruments/bonds/amortizingfixedratebond.{hpp,cpp}}.
 *
 * The notional vector encodes both amortizations and (Phase 5d.5-Bonds-b)
 * draw-downs once {@link FixedRateLeg} grows {@code withPaymentCalendar} /
 * {@code withPaymentLag} / {@code withExCouponPeriod}. The current Java
 * surface mirrors the simplest constructor — the four arguments needed by
 * {@code testAmortizingFixedRateBond} (settlementDays, notionals, schedule,
 * coupons, accrualDayCounter) — and falls back to the existing
 * {@link FixedRateLeg} builder.
 *
 * Phase 5d.5-Bonds (carry-forward from 5d). The full constructor surface
 * (paymentConvention, issueDate, ex-coupon period, redemptions vector,
 * paymentLag) and the draw-down test live in Phase 5d.5-Bonds-b.
 *
 * @author Jose Moya
 */
public class AmortizingFixedRateBond extends Bond {

    protected Frequency frequency_;
    protected DayCounter dayCounter_;

    /**
     * Primary constructor — matches the simplest C++ overload exercised
     * by {@code testAmortizingFixedRateBond}.
     *
     * @param settlementDays    settlement-days lag
     * @param notionals         per-period notional vector (amortizing schedule)
     * @param schedule          coupon schedule
     * @param coupons           per-coupon rate vector (length 1 ⇒ constant rate)
     * @param accrualDayCounter accrual day counter
     */
    public AmortizingFixedRateBond(final /* @Natural */ int settlementDays,
                                    final double[] notionals,
                                    final Schedule schedule,
                                    final double[] coupons,
                                    final DayCounter accrualDayCounter) {
        this(settlementDays, notionals, schedule, coupons, accrualDayCounter,
             BusinessDayConvention.Following, new Date(),
             new double[] { 100.0 });
    }

    /**
     * Full Phase 5d.5-Bonds constructor (paymentConvention + issueDate +
     * redemptions vector). The ex-coupon period and paymentLag arguments
     * are deferred to Phase 5d.5-Bonds-b together with the corresponding
     * {@link FixedRateLeg} builder additions.
     */
    public AmortizingFixedRateBond(final /* @Natural */ int settlementDays,
                                    final double[] notionals,
                                    final Schedule schedule,
                                    final double[] coupons,
                                    final DayCounter accrualDayCounter,
                                    final BusinessDayConvention paymentConvention,
                                    final Date issueDate,
                                    final double[] redemptions) {

        super(settlementDays, schedule.calendar(), issueDate);

        frequency_ = schedule.tenor().frequency();
        dayCounter_ = accrualDayCounter;
        maturityDate_ = schedule.endDate().clone();

        cashflows_ = new FixedRateLeg(schedule, accrualDayCounter)
                        .withNotionals(notionals)
                        .withCouponRates(coupons)
                        .withPaymentAdjustment(paymentConvention)
                        .Leg();

        addRedemptionsToCashflows(redemptions);

        QL.ensure(!cashflows().isEmpty(), "bond with no cashflows!");
    }

    public Frequency frequency() {
        return frequency_;
    }

    public DayCounter dayCounter() {
        return dayCounter_;
    }

    /**
     * Returns a schedule for French amortization. Mirrors C++
     * {@code sinkingSchedule(startDate, bondLength, frequency, paymentCalendar)}.
     */
    public static Schedule sinkingSchedule(final Date startDate,
                                            final Period bondLength,
                                            final Frequency frequency,
                                            final Calendar paymentCalendar) {
        final Date maturityDate = startDate.add(bondLength);
        return new Schedule(startDate, maturityDate, new Period(frequency),
                            paymentCalendar,
                            BusinessDayConvention.Unadjusted,
                            BusinessDayConvention.Unadjusted,
                            DateGeneration.Rule.Backward, false);
    }

    /**
     * Returns a sequence of notionals for French amortization. Mirrors C++
     * {@code sinkingNotionals(bondLength, sinkingFrequency, couponRate, initialNotional)}.
     */
    public static double[] sinkingNotionals(final Period bondLength,
                                             final Frequency sinkingFrequency,
                                             final double couponRate,
                                             final double initialNotional) {
        final int[] nPeriodsRef = new int[1];
        QL.require(isSubPeriod(new Period(sinkingFrequency), bondLength, nPeriodsRef),
                   "Bond frequency is incompatible with the maturity tenor");
        final int nPeriods = nPeriodsRef[0];

        final double[] notionals = new double[nPeriods + 1];
        notionals[0] = initialNotional;
        final double coupon = couponRate / sinkingFrequency.toInteger();
        double compoundedInterest = 1.0;
        final double totalValue = Math.pow(1.0 + coupon, nPeriods);
        for (int i = 0; i < nPeriods - 1; ++i) {
            compoundedInterest *= (1.0 + coupon);
            double currentNotional;
            if (coupon < 1.0e-12) {
                currentNotional = initialNotional * (1.0 - (i + 1.0) / nPeriods);
            } else {
                currentNotional = initialNotional
                        * (compoundedInterest
                                - (compoundedInterest - 1.0) / (1.0 - 1.0 / totalValue));
            }
            notionals[i + 1] = currentNotional;
        }
        notionals[nPeriods] = 0.0;
        return notionals;
    }

    // ----- internal helpers (mirrors anonymous-namespace helpers in C++) -----

    private static int[] daysMinMax(final Period p) {
        switch (p.units()) {
            case Days:
                return new int[] { p.length(), p.length() };
            case Weeks:
                return new int[] { 7 * p.length(), 7 * p.length() };
            case Months:
                return new int[] { 28 * p.length(), 31 * p.length() };
            case Years:
                return new int[] { 365 * p.length(), 366 * p.length() };
            default:
                throw new LibraryException("unknown time unit (" + p.units() + ")");
        }
    }

    private static boolean isSubPeriod(final Period subPeriod,
                                        final Period superPeriod,
                                        final int[] numSubPeriodsOut) {
        final int[] superDays = daysMinMax(superPeriod);
        final int[] subDays = daysMinMax(subPeriod);
        final double minPeriodRatio = ((double) superDays[0]) / ((double) subDays[1]);
        final double maxPeriodRatio = ((double) superDays[1]) / ((double) subDays[0]);
        final int lowRatio = (int) Math.floor(minPeriodRatio);
        final int highRatio = (int) Math.ceil(maxPeriodRatio);
        try {
            for (int i = lowRatio; i <= highRatio; ++i) {
                final Period testPeriod = subPeriod.mul(i);
                // C++ Period::operator== is structural: !(a<b || b<a). Use the
                // same composite to handle e.g. Period(360,Months) == Period(30,Years).
                if (!(testPeriod.lt(superPeriod) || superPeriod.lt(testPeriod))) {
                    numSubPeriodsOut[0] = i;
                    return true;
                }
            }
        } catch (final RuntimeException e) {
            return false;
        }
        return false;
    }

    // unused-import suppression
    @SuppressWarnings("unused") private static final TimeUnit _t = TimeUnit.Years;
    @SuppressWarnings("unused") private static final NullCalendar _nc = new NullCalendar();
}
