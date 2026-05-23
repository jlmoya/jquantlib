/*
 Copyright (C) 2026 JQuantLib migration contributors

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
 Copyright (C) 2005 Toyin Akin
 Copyright (C) 2008, 2009 Ferdinando Ametrano
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.instruments.bonds.CPIBond;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;

/**
 * CPI bond helper for curve bootstrap.
 * <p>
 * Wraps a {@link CPIBond} as a {@link BondHelper} so the bond can participate
 * in a yield-curve bootstrap. The helper computes the implied quote by
 * re-pricing the bond off the term structure it has been given.
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code CPIBondHelper}
 * (ql/termstructures/yield/bondhelpers.{hpp,cpp}).
 *
 * <p>Phase 2 forward closure L2-B.
 */
public class CPIBondHelper extends BondHelper {

    /**
     * Primary constructor — mirrors C++ {@code CPIBondHelper} non-deprecated overload
     * (no {@code growthOnly} parameter; defaults to {@code false}).
     */
    public CPIBondHelper(final Handle< Quote > price, final int settlementDays, final double faceAmount,
            final double baseCPI, final Period observationLag, final ZeroInflationIndex cpiIndex,
            final CPI.InterpolationType observationInterpolation, final Schedule schedule, final double[] fixedRate,
            final DayCounter accrualDayCounter, final BusinessDayConvention paymentConvention, final Date issueDate,
            final Calendar paymentCalendar, final Period exCouponPeriod, final Calendar exCouponCalendar,
            final BusinessDayConvention exCouponConvention, final boolean exCouponEndOfMonth, final PriceType priceType) {
        // C++ delegates to the deprecated growthOnly overload with growthOnly=false.
        this(price, settlementDays, faceAmount, /* growthOnly */ false, baseCPI, observationLag, cpiIndex,
                observationInterpolation, schedule, fixedRate, accrualDayCounter, paymentConvention, issueDate,
                paymentCalendar, exCouponPeriod, exCouponCalendar, exCouponConvention, exCouponEndOfMonth, priceType);
    }

    /**
     * Deprecated overload with explicit {@code growthOnly} parameter — mirrors C++ {@code CPIBondHelper}
     * deprecated ctor (deprecated in QuantLib 1.40 in favor of the overload without {@code growthOnly}).
     *
     * @deprecated Use the overload without the {@code growthOnly} parameter.
     */
    @Deprecated
    public CPIBondHelper(final Handle< Quote > price, final int settlementDays, final double faceAmount,
            final boolean growthOnly, final double baseCPI, final Period observationLag,
            final ZeroInflationIndex cpiIndex, final CPI.InterpolationType observationInterpolation,
            final Schedule schedule, final double[] fixedRate, final DayCounter accrualDayCounter,
            final BusinessDayConvention paymentConvention, final Date issueDate, final Calendar paymentCalendar,
            final Period exCouponPeriod, final Calendar exCouponCalendar,
            final BusinessDayConvention exCouponConvention, final boolean exCouponEndOfMonth, final PriceType priceType) {
        super(price, new CPIBond(settlementDays, faceAmount, growthOnly, baseCPI, observationLag, cpiIndex,
                observationInterpolation, schedule, fixedRate, accrualDayCounter, paymentConvention, issueDate,
                paymentCalendar, exCouponPeriod, exCouponCalendar, exCouponConvention, exCouponEndOfMonth), priceType);
    }

    /** Convenience overload mirroring the C++ default {@code priceType=Bond::Price::Clean}. */
    public CPIBondHelper(final Handle< Quote > price, final int settlementDays, final double faceAmount,
            final double baseCPI, final Period observationLag, final ZeroInflationIndex cpiIndex,
            final CPI.InterpolationType observationInterpolation, final Schedule schedule, final double[] fixedRate,
            final DayCounter accrualDayCounter, final BusinessDayConvention paymentConvention, final Date issueDate,
            final Calendar paymentCalendar, final Period exCouponPeriod, final Calendar exCouponCalendar,
            final BusinessDayConvention exCouponConvention, final boolean exCouponEndOfMonth) {
        this(price, settlementDays, faceAmount, baseCPI, observationLag, cpiIndex, observationInterpolation, schedule,
                fixedRate, accrualDayCounter, paymentConvention, issueDate, paymentCalendar, exCouponPeriod,
                exCouponCalendar, exCouponConvention, exCouponEndOfMonth, PriceType.Clean);
    }
}
