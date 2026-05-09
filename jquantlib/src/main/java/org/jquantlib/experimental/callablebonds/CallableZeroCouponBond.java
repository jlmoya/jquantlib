/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CallabilitySchedule;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Callable / puttable zero-coupon bond.
 * <p>
 * Port of C++ v1.42.1
 * {@code ql/experimental/callablebonds/callablebond.{hpp,cpp}}
 * (the {@code CallableZeroCouponBond} portion).
 */
public class CallableZeroCouponBond extends CallableBond {

    public CallableZeroCouponBond(final int settlementDays, final double faceAmount,
            final Calendar calendar, final Date maturityDate, final DayCounter dayCounter,
            final BusinessDayConvention paymentConvention, final double redemption,
            final Date issueDate, final CallabilitySchedule putCallSchedule) {
        super(settlementDays, maturityDate, calendar, dayCounter, faceAmount, issueDate,
                putCallSchedule);

        frequency_ = Frequency.Once;

        final Date redemptionDate = calendar_.adjust(maturityDate_, paymentConvention);
        setSingleRedemption(faceAmount, redemption, redemptionDate);
    }
}
