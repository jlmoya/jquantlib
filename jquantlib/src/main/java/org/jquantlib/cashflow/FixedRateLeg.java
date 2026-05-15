package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;

// TODO: code review :: license, class comments, comments for access modifiers, comments for @Override
public class FixedRateLeg extends Leg {

    private final Schedule schedule_;
    private double[] notionals_;
    private InterestRate[] couponRates_;
    private final DayCounter paymentDayCounter_;
    private DayCounter firstPeriodDayCounter_;
    private DayCounter lastPeriodDayCounter_;
    private BusinessDayConvention paymentAdjustment_;
    /** Phase 5d.5-Bonds-b — payment calendar (defaults to schedule.calendar()). */
    private Calendar paymentCalendar_;
    /** Phase 5d.5-Bonds-b — payment-lag in business days (default 0). */
    private int paymentLag_;
    /** Phase 5d.5-Bonds-b — ex-coupon period (default empty). */
    private Period exCouponPeriod_;
    /** Phase 5d.5-Bonds-b — ex-coupon calendar (default empty). */
    private Calendar exCouponCalendar_;
    /** Phase 5d.5-Bonds-b — ex-coupon adjustment (default Following). */
    private BusinessDayConvention exCouponAdjustment_;
    /** Phase 5d.5-Bonds-b — ex-coupon end-of-month flag (default false). */
    private boolean exCouponEndOfMonth_;

    public FixedRateLeg(final Schedule schedule, final DayCounter paymentDayCounter){
        this.schedule_=(schedule);
        this.paymentDayCounter_=(paymentDayCounter);
        this.paymentAdjustment_ = BusinessDayConvention.Following;
        // Phase 5d.5-Bonds-b — default payment calendar matches C++
        // FixedRateLeg::operator Leg() which uses paymentCalendar_.advance(...)
        // and falls back to schedule.calendar() when paymentCalendar_ is empty.
        // Java has no Calendar.empty(); default to schedule.calendar() up front.
        this.paymentCalendar_ = schedule.calendar();
        this.paymentLag_ = 0;
        this.exCouponPeriod_ = new Period();
        this.exCouponCalendar_ = new Calendar();
        this.exCouponAdjustment_ = BusinessDayConvention.Following;
        this.exCouponEndOfMonth_ = false;
    }

    public FixedRateLeg withNotionals(/* Real */final double notional) {
        this.notionals_ = new double[] {notional};
        return this;
    }

    public FixedRateLeg withNotionals(final double[]/*List<Double>*/ notionals) {
        this.notionals_ = notionals; // TODO: clone() ?
        return this;
    }

    public FixedRateLeg withCouponRates(/* @Rate */final double couponRate) {
        couponRates_ = new InterestRate[]{new InterestRate(couponRate, paymentDayCounter_, Compounding.Simple)};

        //        couponRates_.clear();
        //        couponRates_.set(0, new InterestRate(couponRate, paymentDayCounter_, Compounding.SIMPLE));
        return this;
    }

    public FixedRateLeg withCouponRates(final InterestRate couponRate) {
        couponRates_ = new InterestRate[]{couponRate};
        return this;
    }

    public FixedRateLeg withCouponRates(/* @Rate */final double [] couponRates) {
        couponRates_ = new InterestRate[couponRates.length];
        for (int i = 0; i<couponRates.length; i++) {
            couponRates_[i] = new InterestRate(couponRates[i], paymentDayCounter_, Compounding.Simple);
        }
        return this;
    }

    public FixedRateLeg withCouponRates(final InterestRate [] couponRates) {
        couponRates_ = couponRates; // TODO: clone() ?
        return this;
    }

    public FixedRateLeg withPaymentAdjustment(final BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public FixedRateLeg withFirstPeriodDayCounter(final DayCounter dayCounter) {
        firstPeriodDayCounter_ = dayCounter;
        return this;
    }

    /** Mirror of C++ {@code FixedRateLeg::withLastPeriodDayCounter}
     *  (ql/cashflows/fixedratecoupon.cpp:148-152). The provided day counter
     *  overrides the schedule's last-coupon day counter. Phase 3d L0 A.2
     *  switches this from accept-but-ignore to actually use the parameter
     *  in {@link #Leg()} construction. */
    public FixedRateLeg withLastPeriodDayCounter(final DayCounter dayCounter) {
        lastPeriodDayCounter_ = dayCounter;
        return this;
    }

    /** Mirror of C++ {@code FixedRateLeg::withPaymentCalendar}
     *  (ql/cashflows/fixedratecoupon.cpp:154-157). Overrides the calendar
     *  used to advance to the payment date. */
    public FixedRateLeg withPaymentCalendar(final Calendar calendar) {
        this.paymentCalendar_ = calendar;
        return this;
    }

    /** Mirror of C++ {@code FixedRateLeg::withPaymentLag}
     *  (ql/cashflows/fixedratecoupon.cpp:159-162). Number of business days
     *  to advance the period-end date when computing the payment date. */
    public FixedRateLeg withPaymentLag(final int lag) {
        this.paymentLag_ = lag;
        return this;
    }

    /** Mirror of C++ {@code FixedRateLeg::withExCouponPeriod}
     *  (ql/cashflows/fixedratecoupon.cpp:164-174). Records ex-coupon
     *  parameters for downstream coupon construction.
     *
     *  <p>NOTE: Java {@link FixedRateCoupon} does not yet expose an
     *  {@code exCouponDate} parameter (mirrors the gap noted in
     *  {@code CPICoupon} comments). The values are recorded and the
     *  {@code exCouponDate} is computed inside {@link #Leg()} for future
     *  threading; for now the date is computed but discarded. Tracked as
     *  Phase 5d.5-Bonds-c carry-forward (FixedRateCoupon ex-coupon
     *  parameter + Coupon.exCouponDate accessor). */
    public FixedRateLeg withExCouponPeriod(final Period period,
                                           final Calendar cal,
                                           final BusinessDayConvention convention,
                                           final boolean endOfMonth) {
        this.exCouponPeriod_ = period;
        this.exCouponCalendar_ = cal;
        this.exCouponAdjustment_ = convention;
        this.exCouponEndOfMonth_ = endOfMonth;
        return this;
    }

    public FixedRateLeg withExCouponPeriod(final Period period,
                                           final Calendar cal,
                                           final BusinessDayConvention convention) {
        return withExCouponPeriod(period, cal, convention, false);
    }


    public Leg Leg() {
        QL.require(couponRates_ != null && couponRates_.length>0 , "coupon rates not specified"); // TODO: message
        QL.require(notionals_   != null && notionals_.length>0 , "nominals not specified"); // TODO: message

        final Leg leg = new Leg();

        // the following is not always correct (for ref-date adjustments)
        final Calendar calendar = schedule_.calendar();
        // Phase 5d.5-Bonds-b — payment dates advance via paymentCalendar_
        // (defaults to schedule_.calendar()) by paymentLag_ business days
        // before applying paymentAdjustment_. Mirrors C++
        // ql/cashflows/fixedratecoupon.cpp:186 et al.
        final boolean hasExCoupon = exCouponPeriod_ != null && exCouponPeriod_.length() != 0;

        // first period might be short or long
        Date start = schedule_.date(0), end = schedule_.date(1);
        Date paymentDate = paymentCalendar_.advance(end, paymentLag_, TimeUnit.Days, paymentAdjustment_, false);
        // exCouponDate (computed for future threading; FixedRateCoupon ctor
        // does not yet accept it — see Phase 5d.5-Bonds-c carry-forward)
        @SuppressWarnings("unused")
        Date exCouponDate = computeExCouponDate(paymentDate, hasExCoupon);
        InterestRate rate = couponRates_[0];
        /*@Real*/ double nominal = notionals_[0];
        // Mirrors C++ ql/cashflows/fixedratecoupon.cpp:198-204 —
        // when the schedule lacks tenor/isRegular meta-info (date-vector
        // ctor, hasTenor()=false), or the first stub is regular, we use
        // start as the reference date; otherwise we back-walk from end
        // by one tenor.
        final boolean firstStubIsShortOrLong =
                schedule_.hasTenor() && schedule_.hasIsRegular()
                && !schedule_.isRegular(1);
        if (!firstStubIsShortOrLong) {
            QL.require(firstPeriodDayCounter_==null || !firstPeriodDayCounter_.equals(paymentDayCounter_) , "regular first coupon does not allow a first-period day count"); // TODO: message
            leg.add(new FixedRateCoupon(nominal, paymentDate, rate, paymentDayCounter_, start, end, start, end));
        } else {
            Date ref = end.sub(schedule_.tenor());
            ref = calendar.adjust(ref, schedule_.businessDayConvention());
            // FIXME: empty() method on dayCounter missing --> substituted by == null (probably incorrect)
            final DayCounter dc = (firstPeriodDayCounter_ == null) ? paymentDayCounter_ : firstPeriodDayCounter_;
            leg.add(new FixedRateCoupon(nominal, paymentDate, rate, dc, start, end, ref, end));
        }
        // regular periods
        for (int i = 2; i < schedule_.size() - 1; ++i) {
            start = end;
            end = schedule_.date(i);
            paymentDate = paymentCalendar_.advance(end, paymentLag_, TimeUnit.Days, paymentAdjustment_, false);
            exCouponDate = computeExCouponDate(paymentDate, hasExCoupon);
            if ((i - 1) < couponRates_.length) {
                rate = couponRates_[i - 1];
            } else {
                rate = couponRates_[couponRates_.length - 1];
            }
            if ((i - 1) < notionals_.length) {
                nominal = notionals_[i - 1];
            } else {
                nominal = notionals_[notionals_.length - 1];
            }
            leg.add(new FixedRateCoupon(nominal, paymentDate, rate, paymentDayCounter_, start, end, start, end));
        }

        if (schedule_.size() > 2) {
            // last period might be short or long
            final int N = schedule_.size();
            start = end;
            end = schedule_.date(N - 1);
            paymentDate = paymentCalendar_.advance(end, paymentLag_, TimeUnit.Days, paymentAdjustment_, false);
            exCouponDate = computeExCouponDate(paymentDate, hasExCoupon);
            if ((N - 2) < couponRates_.length) {
                rate = couponRates_[N - 2];
            } else {
                rate = couponRates_[couponRates_.length - 1];
            }
            if ((N - 2) < notionals_.length) {
                nominal = notionals_[N - 2];
            } else {
                nominal = notionals_[notionals_.length - 1];
            }
            // Phase 3d L0 A.2 — wire withLastPeriodDayCounter (mirrors C++
            // ql/cashflows/fixedratecoupon.cpp:255-272). When non-null this
            // day counter replaces paymentDayCounter for the last coupon.
            final DayCounter lastDc =
                    (lastPeriodDayCounter_ == null) ? paymentDayCounter_ : lastPeriodDayCounter_;
            final InterestRate lastRate = (lastPeriodDayCounter_ == null)
                    ? rate
                    : new InterestRate(rate.rate(), lastDc, rate.compounding());
            // Mirrors C++ ql/cashflows/fixedratecoupon.cpp:258-272 —
            // when the schedule lacks tenor (date-vector ctor) or the
            // last stub is regular, use the regular branch (refStart=start,
            // refEnd=end); otherwise compute a forward reference from
            // start by one tenor.
            final boolean lastIsRegularOrNoTenor =
                    !schedule_.hasTenor()
                    || (schedule_.hasIsRegular() && schedule_.isRegular(N - 1));
            if (lastIsRegularOrNoTenor) {
                leg.add(new FixedRateCoupon(nominal, paymentDate, lastRate, lastDc, start, end, start, end));
            } else {
                Date ref = start.add(schedule_.tenor());
                ref = calendar.adjust(ref, schedule_.businessDayConvention());
                leg.add(new FixedRateCoupon(nominal, paymentDate, lastRate, lastDc, start, end, start, ref));
            }
        }
        return leg;
    }

    /** Mirrors C++ {@code FixedRateLeg::operator Leg()} ex-coupon block
     *  (ql/cashflows/fixedratecoupon.cpp:191-196 and similar). Computes
     *  the ex-coupon date as {@code exCouponCalendar.advance(paymentDate,
     *  -exCouponPeriod, exCouponAdjustment, exCouponEndOfMonth)} when an
     *  ex-coupon period is configured; returns {@code Date()} otherwise. */
    private Date computeExCouponDate(final Date paymentDate, final boolean hasExCoupon) {
        if (!hasExCoupon) {
            return new Date();
        }
        return exCouponCalendar_.advance(
                paymentDate,
                exCouponPeriod_.negative(),
                exCouponAdjustment_,
                exCouponEndOfMonth_);
    }
}
