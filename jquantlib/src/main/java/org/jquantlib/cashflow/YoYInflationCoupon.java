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

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.instruments.YearOnYearInflationSwap;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Coupon paying a year-on-year inflation type index.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::YoYInflationCoupon}
 * ({@code ql/cashflows/yoyinflationcoupon.{hpp,cpp}}).
 *
 * <p>Argument order in the Java port follows the {@link InflationCoupon}
 * convention {@code (nominal, paymentDate, startDate, endDate, ...)} rather than the C++ order, to keep parameter
 * ordering consistent with {@link Coupon} and the rest of the JQuantLib cashflow family.
 *
 * <p>The pricer for non-cap/floor swaplets is
 * {@link YoYInflationCouponPricer}; this is the standard inflation pricer mirrored from C++ and used by
 * {@link YearOnYearInflationSwap}'s YoY leg.
 *
 * @author JQuantLib migration team (Phase 2q B)
 */
public class YoYInflationCoupon extends InflationCoupon {

    //
    // private fields
    //

    private final YoYInflationIndex yoyIndex_;
    private final CPI.InterpolationType interpolation_;

    //
    // protected fields (visible to descendants per C++ pattern)
    //

    protected double gearing_;
    protected double spread_;

    //
    // public constructors
    //

    public YoYInflationCoupon(final double nominal, final Date paymentDate, final Date startDate, final Date endDate,
            final int fixingDays, final YoYInflationIndex yoyIndex, final Period observationLag,
            final CPI.InterpolationType interpolation, final DayCounter dayCounter, final double gearing,
            final double spread, final Date refPeriodStart, final Date refPeriodEnd) {
        super(nominal, paymentDate, startDate, endDate, fixingDays, yoyIndex, observationLag, dayCounter,
                refPeriodStart, refPeriodEnd);
        this.yoyIndex_ = yoyIndex;
        this.interpolation_ = interpolation;
        this.gearing_ = gearing;
        this.spread_ = spread;
    }

    public YoYInflationCoupon(final double nominal, final Date paymentDate, final Date startDate, final Date endDate,
            final int fixingDays, final YoYInflationIndex yoyIndex, final Period observationLag,
            final CPI.InterpolationType interpolation, final DayCounter dayCounter, final double gearing,
            final double spread) {
        this(nominal, paymentDate, startDate, endDate, fixingDays, yoyIndex, observationLag, interpolation, dayCounter,
                gearing, spread, new Date(), new Date());
    }

    public YoYInflationCoupon(final double nominal, final Date paymentDate, final Date startDate, final Date endDate,
            final int fixingDays, final YoYInflationIndex yoyIndex, final Period observationLag,
            final CPI.InterpolationType interpolation, final DayCounter dayCounter) {
        this(nominal, paymentDate, startDate, endDate, fixingDays, yoyIndex, observationLag, interpolation, dayCounter,
                1.0, 0.0, new Date(), new Date());
    }

    //
    // inspectors
    //

    /** Index gearing — multiplicative coefficient applied to the index fixing. */
    public double gearing() {
        return gearing_;
    }

    /** Spread (additive) paid over the fixing of the underlying index. */
    public double spread() {
        return spread_;
    }

    public YoYInflationIndex yoyIndex() {
        return yoyIndex_;
    }

    public CPI.InterpolationType interpolation() {
        return interpolation_;
    }

    /**
     * Adjusted fixing — convenience inverse of the rate calculation: {@code (rate - spread) / gearing}. Mirrors C++
     * inline.
     */
    public double adjustedFixing() {
        return (rate() - spread()) / gearing();
    }

    //
    // overrides InflationCoupon
    //

    /**
     * Mirrors C++ {@code YoYInflationCoupon::indexFixing()} — delegates to
     * {@link CPI#laggedYoYRate(YoYInflationIndex, Date, Period, CPI.InterpolationType)} with {@code accrualEndDate} as
     * the unlagged date.
     */
    @Override
    public double indexFixing() {
        return CPI.laggedYoYRate(yoyIndex_, accrualEndDate(), observationLag(), interpolation_);
    }

    @Override
    protected boolean checkPricerImpl(final InflationCouponPricer pricer) {
        return pricer instanceof YoYInflationCouponPricer;
    }

    //
    // PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< YoYInflationCoupon > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
