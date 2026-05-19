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
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Coupon paying the performance of a CPI (zero-inflation) index.
 *
 * <p>The performance is relative to the index value on the base date. The
 * other inflation value is taken from the {@code refPeriodEnd} date with observation lag, so any roll/calendar etc.
 * will be built in by the caller. By default this is done in {@link InflationCoupon}, which uses ModifiedPreceding with
 * fixing days assumed positive (meaning earlier), i.e., always stay in the same month relative to
 * {@code referencePeriodEnd}.
 *
 * <p>This is more sophisticated than an {@link IndexedCashFlow} because it
 * does date calculations itself.
 *
 * <p>Mirrors C++ {@code QuantLib::CPICoupon} at v1.42.1
 * (cashflows/cpicoupon.{hpp,cpp}).
 *
 * @author JQuantLib migration team (Phase 2q C.1)
 */
public class CPICoupon extends InflationCoupon {

    //
    // protected fields
    //

    protected double baseCPI_;
    protected double fixedRate_;
    protected CPI.InterpolationType observationInterpolation_;
    protected Date baseDate_;

    //
    // public constructors
    //

    /**
     * Constructor taking the base CPI to be used in the calculations.
     */
    public CPICoupon(final double baseCPI, final Date paymentDate, final double nominal, final Date startDate,
            final Date endDate, final ZeroInflationIndex index, final Period observationLag,
            final CPI.InterpolationType observationInterpolation, final DayCounter dayCounter, final double fixedRate) {
        this(baseCPI, new Date(), paymentDate, nominal, startDate, endDate, index, observationLag,
                observationInterpolation, dayCounter, fixedRate, new Date(), new Date(), new Date());
    }

    public CPICoupon(final double baseCPI, final Date paymentDate, final double nominal, final Date startDate,
            final Date endDate, final ZeroInflationIndex index, final Period observationLag,
            final CPI.InterpolationType observationInterpolation, final DayCounter dayCounter, final double fixedRate,
            final Date refPeriodStart, final Date refPeriodEnd, final Date exCouponDate) {
        this(baseCPI, new Date(), paymentDate, nominal, startDate, endDate, index, observationLag,
                observationInterpolation, dayCounter, fixedRate, refPeriodStart, refPeriodEnd, exCouponDate);
    }

    /**
     * Constructor taking a base date; the coupon will use it to retrieve the base CPI to be used in the calculations.
     */
    public CPICoupon(final Date baseDate, final Date paymentDate, final double nominal, final Date startDate,
            final Date endDate, final ZeroInflationIndex index, final Period observationLag,
            final CPI.InterpolationType observationInterpolation, final DayCounter dayCounter, final double fixedRate,
            final Date refPeriodStart, final Date refPeriodEnd, final Date exCouponDate) {
        this(Constants.NULL_REAL, baseDate, paymentDate, nominal, startDate, endDate, index, observationLag,
                observationInterpolation, dayCounter, fixedRate, refPeriodStart, refPeriodEnd, exCouponDate);
    }

    /**
     * Constructor taking both a base CPI and a base date. If both are passed, the base CPI is used in the
     * calculations.
     */
    public CPICoupon(final double baseCPI, final Date baseDate, final Date paymentDate, final double nominal,
            final Date startDate, final Date endDate, final ZeroInflationIndex index, final Period observationLag,
            final CPI.InterpolationType observationInterpolation, final DayCounter dayCounter, final double fixedRate,
            final Date refPeriodStart, final Date refPeriodEnd, final Date exCouponDate) {
        super(nominal, paymentDate, startDate, endDate, 0, index, observationLag, dayCounter, refPeriodStart,
                refPeriodEnd);

        this.baseCPI_ = baseCPI;
        this.fixedRate_ = fixedRate;
        this.observationInterpolation_ = observationInterpolation;
        this.baseDate_ = baseDate.clone();

        QL.require(index != null, "no index provided");
        QL.require(!isNullCPI(baseCPI_) || !baseDate.isNull(),
                "baseCPI and baseDate can not be both null, " + "provide a valid baseCPI or baseDate");
        QL.require(isNullCPI(baseCPI_) || Math.abs(baseCPI_) > 1e-16,
                "|baseCPI_| < 1e-16, future divide-by-zero problem");

        // Note: the InflationCoupon already registered the index/evalDate; the
        // exCouponDate is currently not represented in the Java InflationCoupon
        // base class — caller queries via {@code date()} only. Phase 2q does
        // not extend the base; if needed, follow-up phases can add ex-coupon
        // tracking once a use-case demands it.
    }

    //
    // public inspectors
    //

    /** Sentinel test for "no base CPI passed" (mirrors C++ Null<Rate>()). */
    static boolean isNullCPI(final double v) {
        return Double.isNaN(v) || v == Constants.NULL_REAL;
    }

    /** Fixed rate that will be inflated by the index ratio. */
    public double fixedRate() {
        return fixedRate_;
    }

    /**
     * Base value for the CPI index. May be {@link Constants#NULL_REAL} if the coupon was constructed via the base-date
     * constructor.
     */
    public /*@Rate*/ double baseCPI() {
        return baseCPI_;
    }

    /** Base date for the base fixing of the CPI index. May be a null date. */
    public Date baseDate() {
        return baseDate_.clone();
    }

    /** Observation interpolation: as-is, flat, or linear. */
    public CPI.InterpolationType observationInterpolation() {
        return observationInterpolation_;
    }

    //
    // calculations
    //

    /** Index used (cast to ZeroInflationIndex). */
    public ZeroInflationIndex cpiIndex() {
        return (ZeroInflationIndex) index();
    }

    @Override
    public double accruedAmount(final Date d) {
        if ( d.le(accrualStartDate_) || d.gt(paymentDate_) ) {
            return 0.0;
        }
        QL.require(pricer_ instanceof CPICouponPricer, "pricer not set or of wrong type");
        final CPICouponPricer pricer = (CPICouponPricer) pricer_;
        pricer.initialize(this);
        return nominal() * pricer.accruedRate(d) * dayCounter().yearFraction(accrualStartDate_,
                Date.min(d, accrualEndDate_), refPeriodStart_, refPeriodEnd_);
    }

    /** The index value observed (with a lag) at the end date. */
    @Override
    public double indexFixing() {
        return CPI.laggedFixing(cpiIndex(), accrualEndDate(), observationLag(), observationInterpolation_);
    }

    /**
     * The ratio between the index fixing at the passed date and the base CPI. No adjustments are applied.
     */
    public /*@Rate*/ double indexRatio(final Date d) {
        double i0 = baseCPI();
        if ( isNullCPI(i0) ) {
            i0 = CPI.laggedFixing(cpiIndex(), baseDate().add(observationLag()), observationLag(),
                    observationInterpolation_);
        }
        final double i1 = CPI.laggedFixing(cpiIndex(), d, observationLag(), observationInterpolation_);
        return i1 / i0;
    }

    //
    // overrides
    //

    /**
     * The ratio between the end-index fixing and the base CPI. This may include adjustments calculated by the pricer.
     */
    public /*@Rate*/ double adjustedIndexGrowth() {
        return rate() / fixedRate();
    }

    @Override
    protected boolean checkPricerImpl(final InflationCouponPricer pricer) {
        return pricer instanceof CPICouponPricer;
    }

    //
    // static helpers
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< CPICoupon > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
