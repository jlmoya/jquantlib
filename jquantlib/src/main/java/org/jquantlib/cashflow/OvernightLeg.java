/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009 Roland Lichters
 Copyright (C) 2009 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.time.*;
import org.jquantlib.time.calendars.WeekendsOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class building a sequence of overnight-indexed coupons, fluent Java translation of C++ {@code OvernightLeg}.
 * <p>
 * Port of C++ QuantLib v1.43 {@code ql/cashflows/overnightindexedcoupon.hpp/cpp} {@code OvernightLeg}.
 *
 * @author JQuantLib migration team
 * @category cashflows
 */
public class OvernightLeg {

    private final Schedule schedule_;
    private final OvernightIndex overnightIndex_;
    private List< Double > notionals_ = new ArrayList<>();
    private DayCounter paymentDayCounter_ = new DayCounter();
    private Calendar paymentCalendar_;
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.Following;
    private int paymentLag_ = 0;
    private List< Double > gearings_ = new ArrayList<>();
    private List< Double > spreads_ = new ArrayList<>();
    private boolean telescopicValueDates_ = false;
    private RateAveraging.Type averagingMethod_ = RateAveraging.Type.Compound;
    private int lookbackDays_ = Constants.NULL_NATURAL;
    private int lockoutDays_ = 0;
    private boolean applyObservationShift_ = false;
    private boolean compoundSpreadDaily_ = false;
    /** {@code null} = no rate rounding; mirrors C++ {@code ext::optional<Integer> roundingPrecision_}. */
    private Integer roundingPrecision_ = null;
    private List< Double > caps_ = new ArrayList<>();
    private List< Double > floors_ = new ArrayList<>();
    private boolean nakedOption_ = false;
    private boolean dailyCapFloor_ = false;
    private OvernightIndexedCouponPricer couponPricer_ = null;

    public OvernightLeg(final Schedule schedule, final OvernightIndex overnightIndex) {
        QL.require(overnightIndex != null, "no index provided");
        this.schedule_ = schedule;
        this.overnightIndex_ = overnightIndex;
        // C++ leaves paymentCalendar_ default-constructed (empty) and resolves it in
        // operator Leg() — see leg(). Seeding it from the schedule here would be
        // equivalent for a schedule that carries a calendar but would lose the
        // WeekendsOnly fallback for one that does not.
        this.paymentCalendar_ = new Calendar();
    }

    private static double pickValue(final List< Double > vec, final int index) {
        if ( vec.isEmpty() ) {
            throw new org.jquantlib.lang.exceptions.LibraryException("no value provided");
        }
        return vec.get(index >= vec.size() ? vec.size() - 1 : index);
    }

    private static double pickValueOrDefault(final List< Double > vec, final int index, final double dflt) {
        if ( vec.isEmpty() ) {
            return dflt;
        }
        return vec.get(index >= vec.size() ? vec.size() - 1 : index);
    }

    public OvernightLeg withNotionals(final double notional) {
        notionals_ = new ArrayList<>();
        notionals_.add(notional);
        return this;
    }

    public OvernightLeg withNotionals(final List< Double > notionals) {
        notionals_ = new ArrayList<>(notionals);
        return this;
    }

    public OvernightLeg withPaymentDayCounter(final DayCounter dc) {
        paymentDayCounter_ = dc;
        return this;
    }

    public OvernightLeg withPaymentAdjustment(final BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public OvernightLeg withPaymentCalendar(final Calendar cal) {
        paymentCalendar_ = cal;
        return this;
    }

    public OvernightLeg withPaymentLag(final int lag) {
        paymentLag_ = lag;
        return this;
    }

    public OvernightLeg withGearings(final double gearing) {
        gearings_ = new ArrayList<>();
        gearings_.add(gearing);
        return this;
    }

    public OvernightLeg withGearings(final List< Double > gearings) {
        gearings_ = new ArrayList<>(gearings);
        return this;
    }

    public OvernightLeg withSpreads(final double spread) {
        spreads_ = new ArrayList<>();
        spreads_.add(spread);
        return this;
    }

    public OvernightLeg withSpreads(final List< Double > spreads) {
        spreads_ = new ArrayList<>(spreads);
        return this;
    }

    public OvernightLeg withTelescopicValueDates(final boolean v) {
        telescopicValueDates_ = v;
        return this;
    }

    public OvernightLeg withAveragingMethod(final RateAveraging.Type avg) {
        averagingMethod_ = avg;
        return this;
    }

    public OvernightLeg withLookbackDays(final int lookbackDays) {
        lookbackDays_ = lookbackDays;
        return this;
    }

    public OvernightLeg withLockoutDays(final int lockoutDays) {
        lockoutDays_ = lockoutDays;
        return this;
    }

    /**
     * Compound the spread daily along with the overnight rate, rather than adding it to the compounded rate at the end
     * of the accrual period. Mirrors C++ {@code OvernightLeg::compoundingSpreadDaily(bool)}
     * ({@code ql/cashflows/overnightindexedcoupon.hpp:229}).
     */
    public OvernightLeg compoundingSpreadDaily(final boolean compoundSpreadDaily) {
        compoundSpreadDaily_ = compoundSpreadDaily;
        return this;
    }

    public OvernightLeg withObservationShift(final boolean shift) {
        applyObservationShift_ = shift;
        return this;
    }

    /**
     * Rounds the coupon rate to {@code roundingPrecision} decimal places before computing the coupon amount.
     * <p>
     * Mirror of C++ {@code OvernightLeg::withRoundingPrecision(Integer)} (overnightindexedcoupon.cpp:494-497), new in
     * v1.43.
     */
    public OvernightLeg withRoundingPrecision(final int roundingPrecision) {
        roundingPrecision_ = Integer.valueOf(roundingPrecision);
        return this;
    }

    public OvernightLeg withCaps(final double cap) {
        caps_ = new ArrayList<>();
        caps_.add(cap);
        return this;
    }

    public OvernightLeg withCaps(final List< Double > caps) {
        caps_ = new ArrayList<>(caps);
        return this;
    }

    public OvernightLeg withFloors(final double floor) {
        floors_ = new ArrayList<>();
        floors_.add(floor);
        return this;
    }

    public OvernightLeg withFloors(final List< Double > floors) {
        floors_ = new ArrayList<>(floors);
        return this;
    }

    public OvernightLeg withNakedOption(final boolean naked) {
        nakedOption_ = naked;
        return this;
    }

    public OvernightLeg withDailyCapFloor(final boolean dailyCapFloor) {
        dailyCapFloor_ = dailyCapFloor;
        return this;
    }

    public OvernightLeg withCouponPricer(final OvernightIndexedCouponPricer couponPricer) {
        couponPricer_ = couponPricer;
        return this;
    }

    /**
     * Build the leg.
     */
    public Leg leg() {
        QL.require(!notionals_.isEmpty(), "no notional given");
        final List< Date > dates = schedule_.dates();
        final Leg cashflows = new Leg();
        final DayCounter dc = paymentDayCounter_.empty() ? overnightIndex_.dayCounter() : paymentDayCounter_;

        // Resolve the payment calendar the way C++ does (overnightindexedcoupon.cpp:578-588): schedule calendar
        // first, then the explicitly-set payment calendar, then WeekendsOnly.
        //
        // The WeekendsOnly rung is near-unreachable in practice, in C++ as much as here: Schedule's date-list
        // constructor defaults to NullCalendar (which is not empty), so only a Schedule built with an explicitly
        // empty Calendar reaches it. It is ported anyway because C++ has it, and because the rung that DOES matter
        // is the last one — the port previously seeded paymentCalendar_ from the schedule in the constructor, which
        // is not what C++ does and loses the distinction between "no payment calendar given" and "the schedule's".
        Calendar calendar = schedule_.calendar();
        Calendar paymentCalendar = paymentCalendar_;
        if ( calendar.empty() ) {
            calendar = paymentCalendar;
        }
        if ( calendar.empty() ) {
            calendar = new WeekendsOnly();
        }
        if ( paymentCalendar.empty() ) {
            paymentCalendar = calendar;
        }

        for ( int i = 1; i < dates.size(); ++i ) {
            final Date startDate = dates.get(i - 1);
            final Date endDate = dates.get(i);
            final Date paymentDate = paymentCalendar.advance(endDate, paymentLag_, TimeUnit.Days,
                    paymentAdjustment_, false);
            final double nominal = pickValue(notionals_, i - 1);
            final double gearing = pickValueOrDefault(gearings_, i - 1, 1.0);
            final double spread = pickValueOrDefault(spreads_, i - 1, 0.0);

            final OvernightIndexedCoupon coupon = new OvernightIndexedCoupon(paymentDate, nominal, startDate, endDate,
                    overnightIndex_, gearing, spread, new Date(), new Date(), dc, telescopicValueDates_,
                    averagingMethod_, lookbackDays_, lockoutDays_, applyObservationShift_,
                    compoundSpreadDaily_, new Date() /* rateComputationStartDate */,
                    new Date() /* rateComputationEndDate */, new Date() /* exCouponDate */, roundingPrecision_);
            if ( couponPricer_ != null ) {
                coupon.setPricer(couponPricer_);
            }
            // Apply cap/floor wrapper if either is provided.
            final double cap = pickValueOrDefault(caps_, i - 1, Constants.NULL_REAL);
            final double floor = pickValueOrDefault(floors_, i - 1, Constants.NULL_REAL);
            if ( cap == Constants.NULL_REAL && floor == Constants.NULL_REAL ) {
                cashflows.add(coupon);
            } else {
                final CappedFlooredOvernightIndexedCoupon cfCpn = new CappedFlooredOvernightIndexedCoupon(coupon, cap,
                        floor, nakedOption_, dailyCapFloor_);
                if ( couponPricer_ != null ) {
                    cfCpn.setPricer(couponPricer_);
                }
                cashflows.add(cfCpn);
            }
        }
        return cashflows;
    }
}
