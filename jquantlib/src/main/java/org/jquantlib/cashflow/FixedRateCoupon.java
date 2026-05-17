/*
 Copyright (C) 2009 Ueli Hofstetter
 Copyright (C) 2009 John Nichol

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


package org.jquantlib.cashflow;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * 
 * @author Zahid Hussain
 *
 */
// TODO: code review :: Please review this class! :S
public class FixedRateCoupon extends Coupon {

	private final InterestRate rate;
	private final DayCounter dayCounter;

	//
	// constructors
	//

	public FixedRateCoupon(final double nominal, final Date paymentDate,
			final double rate, final DayCounter dayCounter,
			final Date accrualStartDate, final Date accrualEndDate) {
		this(nominal, paymentDate, rate, dayCounter, accrualStartDate,
				accrualEndDate, new Date(), new Date(), new Date());
	}

	public FixedRateCoupon(final double nominal, final Date paymentDate,
			final double rate, final DayCounter dayCounter,
			final Date accrualStartDate, final Date accrualEndDate,
			final Date refPeriodStart, final Date refPeriodEnd) {
		this(nominal, paymentDate, rate, dayCounter, accrualStartDate,
				accrualEndDate, refPeriodStart, refPeriodEnd, new Date());
	}

	/** Mirror of C++ {@code FixedRateCoupon(paymentDate, nominal, rate,
	 *  dayCounter, accrualStartDate, accrualEndDate, refPeriodStart,
	 *  refPeriodEnd, exCouponDate)} (ql/cashflows/fixedratecoupon.cpp:32-43).
	 *  Phase 5e.5b-CFC-d-93. */
	public FixedRateCoupon(final double nominal, final Date paymentDate,
			final double rate, final DayCounter dayCounter,
			final Date accrualStartDate, final Date accrualEndDate,
			final Date refPeriodStart, final Date refPeriodEnd,
			final Date exCouponDate) {
		super(nominal, paymentDate, accrualStartDate, accrualEndDate,
				refPeriodStart, refPeriodEnd, exCouponDate);

		this.rate = new InterestRate(rate, dayCounter, Compounding.Simple);
		this.dayCounter = dayCounter;
	}

	public FixedRateCoupon(final double nominal, final Date paymentDate,
			final InterestRate interestRate, final DayCounter dayCounter,
			final Date accrualStartDate, final Date accrualEndDate) {
		this(nominal, paymentDate, interestRate, dayCounter, accrualStartDate,
				accrualEndDate, new Date(), new Date(), new Date());
	}

	public FixedRateCoupon(final double nominal, final Date paymentDate,
			final InterestRate interestRate, final DayCounter dayCounter,
			final Date accrualStartDate, final Date accrualEndDate,
			final Date refPeriodStart, final Date refPeriodEnd) {
		this(nominal, paymentDate, interestRate, dayCounter, accrualStartDate,
				accrualEndDate, refPeriodStart, refPeriodEnd, new Date());
	}

	/** Mirror of C++ {@code FixedRateCoupon(paymentDate, nominal,
	 *  interestRate, accrualStartDate, accrualEndDate, refPeriodStart,
	 *  refPeriodEnd, exCouponDate)} (ql/cashflows/fixedratecoupon.cpp:45-60).
	 *  Phase 5e.5b-CFC-d-93. */
	public FixedRateCoupon(final double nominal, final Date paymentDate,
			final InterestRate interestRate, final DayCounter dayCounter,
			final Date accrualStartDate, final Date accrualEndDate,
			final Date refPeriodStart, final Date refPeriodEnd,
			final Date exCouponDate) {
		super(nominal, paymentDate, accrualStartDate, accrualEndDate,
				refPeriodStart, refPeriodEnd, exCouponDate);

		this.rate = interestRate;
		this.dayCounter = dayCounter;
	}

	//
	// public methods
	//

	public InterestRate interestRate() {
		return rate;
	}

	//
	// Overrides Coupon
	//

	@Override
	public DayCounter dayCounter() {
		return dayCounter;
	}

	@Override
	public double rate() {
		return rate.rate();
	}

	@Override
	public double accruedAmount(final Date d) {
		// Mirror of C++ FixedRateCoupon::accruedAmount(d)
		// (ql/cashflows/fixedratecoupon.cpp:73-89). Phase 5e.5b-CFC-d-93
		// adds the ex-coupon branch: when settlement is on/after the
		// ex-coupon date the accrued amount goes negative (the buyer
		// pays the seller back the coupon they will receive but never
		// earned).
		if (d.le(accrualStartDate_) || d.gt(paymentDate_)) {
			// out of coupon range
			return 0.0;
		} else if (tradingExCoupon(d)) {
			final Date maxD = d.ge(accrualEndDate_) ? d : accrualEndDate_;
			return -nominal()
					* (rate.compoundFactor(d, maxD,
							refPeriodStart_, refPeriodEnd_) - 1.0);
		} else {
			// usual case
			final Date minD = d.le(accrualEndDate_) ? d : accrualEndDate_;
			return nominal()
					* (rate.compoundFactor(accrualStartDate_, minD,
							refPeriodStart_, refPeriodEnd_) - 1.0);
		}
	}

	//
	// Overrides CashFlow
	//

	@Override
	public double amount() {
		return nominal()
				* (rate.compoundFactor(accrualStartDate_, accrualEndDate_,
						refPeriodStart_, refPeriodEnd_) - 1.0);
	}


	//
	// implements PolymorphicVisitable
	//

	@Override
	public void accept(final PolymorphicVisitor pv) {
		final Visitor<FixedRateCoupon> v = (pv!=null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
	}
}
