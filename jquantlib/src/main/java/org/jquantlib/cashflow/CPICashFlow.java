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
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Cash flow paying the performance of a CPI (zero-inflation) index.
 *
 * <p>It is NOT a coupon, i.e., no accruals.
 *
 * <p>Mirrors C++ {@code QuantLib::CPICashFlow} at v1.42.1
 * (cashflows/cpicoupon.{hpp,cpp}).
 *
 * @author JQuantLib migration team (Phase 2q C.1)
 */
public class CPICashFlow extends IndexedCashFlow {

    //
    // protected fields
    //

    protected double baseFixing_;
    protected Date observationDate_;
    protected Period observationLag_;
    protected CPI.InterpolationType interpolation_;
    protected Frequency frequency_;

    //
    // public constructors
    //

    public CPICashFlow(final double notional, final ZeroInflationIndex index, final Date baseDate,
            final double baseFixing, final Date observationDate, final Period observationLag,
            final CPI.InterpolationType interpolation, final Date paymentDate) {
        this(notional, index, baseDate, baseFixing, observationDate, observationLag, interpolation, paymentDate, false);
    }

    public CPICashFlow(final double notional, final ZeroInflationIndex index, final Date baseDate,
            final double baseFixing, final Date observationDate, final Period observationLag,
            final CPI.InterpolationType interpolation, final Date paymentDate, final boolean growthOnly) {
        super(notional, index, baseDate, observationDate.sub(observationLag), paymentDate, growthOnly);

        this.baseFixing_ = baseFixing;
        this.observationDate_ = observationDate.clone();
        this.observationLag_ = observationLag;
        this.interpolation_ = interpolation;
        this.frequency_ = (index != null) ? index.frequency() : Frequency.NoFrequency;

        QL.require(index != null, "no index provided");
        QL.require(!isNullCPI(baseFixing_) || !baseDate.isNull(),
                "baseCPI and baseDate can not be both null, " + "provide a valid baseCPI or baseDate");
        QL.require(isNullCPI(baseFixing_) || Math.abs(baseFixing_) > 1e-16,
                "|baseCPI_| < 1e-16, future divide-by-zero problem");
    }

    //
    // public methods
    //

    /** Sentinel test for "no base CPI passed" (mirrors C++ Null<Rate>()). */
    static boolean isNullCPI(final double v) {
        return Double.isNaN(v) || v == Constants.NULL_REAL;
    }

    /**
     * Returns the base date supplied at construction. Throws if no base date was specified (mirrors C++
     * {@code CPICashFlow::baseDate()}).
     */
    @Override
    public Date baseDate() {
        final Date base = super.baseDate();
        if ( !base.isNull() ) {
            return base;
        }
        throw new LibraryException("no base date specified");
    }

    /**
     * Value used on the base date. Does not have to agree with the index on that date. Falls back to
     * {@link CPI#laggedFixing} with zero lag if no explicit base fixing was supplied.
     */
    @Override
    public double baseFixing() {
        if ( !isNullCPI(baseFixing_) ) {
            return baseFixing_;
        }
        return CPI.laggedFixing(cpiIndex(), baseDate(), new Period(0, TimeUnit.Months), interpolation_);
    }

    @Override
    public double indexFixing() {
        return CPI.laggedFixing(cpiIndex(), observationDate_, observationLag_, interpolation_);
    }

    public Date observationDate() {
        return observationDate_.clone();
    }

    public Period observationLag() {
        return observationLag_;
    }

    /** Linear/constant/as-index interpolation of future data. */
    public CPI.InterpolationType interpolation() {
        return interpolation_;
    }

    public Frequency frequency() {
        return frequency_;
    }

    //
    // implements PolymorphicVisitable
    //

    public ZeroInflationIndex cpiIndex() {
        return (ZeroInflationIndex) index();
    }

    //
    // static helpers
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< CPICashFlow > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
