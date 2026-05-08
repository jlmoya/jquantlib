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
 Copyright (C) 2009 Chris Kenyon

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.InflationIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.Observer;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Base inflation-coupon class.
 *
 * <p>The day counter is usually obtained from the inflation term structure
 * that the inflation index uses for forecasting. There is no gearing or
 * spread because these are relevant for YoY coupons but not zero inflation
 * coupons.
 *
 * <p>Note: inflation indices do not contain day counters or calendars.
 *
 * <p>Mirrors C++ {@code QuantLib::InflationCoupon} at v1.42.1
 * (cashflows/inflationcoupon.{hpp,cpp}). Constructor parameter order matches
 * the existing JQuantLib {@link Coupon} convention
 * (nominal/paymentDate/start/end), not the C++ order.
 *
 * @author JQuantLib migration team (Phase 2p A.2)
 */
public abstract class InflationCoupon extends Coupon implements Observer {

    //
    // protected fields
    //

    protected InflationCouponPricer pricer_;
    protected InflationIndex index_;
    protected Period observationLag_;
    protected DayCounter dayCounter_;
    protected int fixingDays_;

    /** Cached rate from the most recent {@link #performCalculations()} call. */
    protected double rate_;

    //
    // public constructors
    //

    public InflationCoupon(final double nominal,
                           final Date paymentDate,
                           final Date startDate,
                           final Date endDate,
                           final int fixingDays,
                           final InflationIndex index,
                           final Period observationLag,
                           final DayCounter dayCounter) {
        this(nominal, paymentDate, startDate, endDate, fixingDays,
             index, observationLag, dayCounter, new Date(), new Date());
    }

    public InflationCoupon(final double nominal,
                           final Date paymentDate,
                           final Date startDate,
                           final Date endDate,
                           final int fixingDays,
                           final InflationIndex index,
                           final Period observationLag,
                           final DayCounter dayCounter,
                           final Date refPeriodStart,
                           final Date refPeriodEnd) {
        // ref period is before lag — see C++ inflationcoupon.cpp constructor body
        super(nominal, paymentDate, startDate, endDate, refPeriodStart, refPeriodEnd);
        this.index_ = index;
        this.observationLag_ = observationLag;
        this.dayCounter_ = dayCounter;
        this.fixingDays_ = fixingDays;
        this.rate_ = Double.NaN;

        // C++ registerWith(index_) and registerWith(evaluationDate)
        if (index_ != null) {
            index_.addObserver(this);
        }
        new Settings().evaluationDate().addObserver(this);
    }

    //
    // public methods
    //

    /** Set the pricer. C++ {@code setPricer}. */
    public void setPricer(final InflationCouponPricer pricer) {
        QL.require(checkPricerImpl(pricer), "pricer given is wrong type");
        if (pricer_ != null) {
            pricer_.deleteObserver(this);
        }
        pricer_ = pricer;
        if (pricer_ != null) {
            pricer_.addObserver(this);
        }
        update();
    }

    public InflationCouponPricer pricer() {
        return pricer_;
    }

    public InflationIndex index() {
        return index_;
    }

    public Period observationLag() {
        return observationLag_;
    }

    public int fixingDays() {
        return fixingDays_;
    }

    public Date fixingDate() {
        // Fixing calendar is usually the null calendar for inflation indices.
        return index_.fixingCalendar().advance(
                refPeriodEnd_.sub(observationLag_),
                new Period(-fixingDays_, TimeUnit.Days),
                BusinessDayConvention.ModifiedPreceding);
    }

    /** Fixing of the underlying index, as observed by the coupon. */
    public double indexFixing() {
        return index_.fixing(fixingDate());
    }

    public double price(final Handle<YieldTermStructure> discountingCurve) {
        return amount() * discountingCurve.currentLink().discount(date());
    }

    //
    // implements CashFlow / Coupon
    //

    @Override
    public double amount() {
        return rate() * accrualPeriod() * nominal();
    }

    @Override
    public DayCounter dayCounter() {
        return dayCounter_;
    }

    @Override
    public double accruedAmount(final Date d) {
        if (d.le(accrualStartDate_) || d.gt(paymentDate_)) {
            return 0.0;
        }
        return nominal() * rate() * dayCounter().yearFraction(
                accrualStartDate_,
                Date.min(d, accrualEndDate_),
                refPeriodStart_,
                refPeriodEnd_);
    }

    @Override
    public double rate() {
        // Java port follows the existing Coupon-family convention of
        // recomputing on demand (no LazyObject membership).
        performCalculations();
        return rate_;
    }

    /**
     * Mirrors C++ {@code performCalculations()}. Updates the cached rate via
     * the configured pricer. Public to match LazyObject contract; not called
     * directly by users.
     */
    public void performCalculations() {
        QL.require(pricer_ != null, "pricer not set");
        // We know the pricer is the correct concrete type because checkPricerImpl
        // validated it on setPricer. In general pricer_ is a derived class.
        pricer_.initialize(this);
        rate_ = pricer_.swapletRate();
    }

    /**
     * Make sure the given pricer is the correct concrete type. Implemented by
     * subclasses; mirrors C++ {@code checkPricerImpl}. Can also be done via
     * the accept/visit mechanism in external pricer-setter classes.
     */
    protected abstract boolean checkPricerImpl(InflationCouponPricer pricer);

    //
    // implements Observer
    //

    @Override
    public void update() {
        notifyObservers();
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<InflationCoupon> v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
