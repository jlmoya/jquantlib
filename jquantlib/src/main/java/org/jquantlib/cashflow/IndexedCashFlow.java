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
 Copyright (C) 2022 Quaternion Risk Management Ltd

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.indexes.Index;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Cash flow dependent on an index ratio.
 *
 * <p>This cash flow is not a coupon, i.e., there's no accrual.  The amount is
 * either {@code i(T)/i(0)} or {@code i(T)/i(0) - 1}, depending on the {@code growthOnly} parameter.
 *
 * <p>We expect this to be used inside an instrument that does all the date
 * adjustment etc., so this takes just dates and does not change them. {@code growthOnly = false} means
 * {@code i(T)/i(0)}, which is a bond-type setting. {@code growthOnly = true} means {@code i(T)/i(0) - 1}, which is a
 * swap-type setting.
 *
 * <p>Mirrors C++ {@code QuantLib::IndexedCashFlow} at v1.42.1
 * (cashflows/indexedcashflow.{hpp,cpp}).
 *
 * @author JQuantLib migration team (Phase 2p A.2)
 */
public class IndexedCashFlow extends CashFlow {

    //
    // protected fields
    //

    private final double notional_;
    private final Index index_;
    private final Date baseDate_;
    private final Date fixingDate_;
    private final Date paymentDate_;
    private final boolean growthOnly_;

    //
    // public constructors
    //

    public IndexedCashFlow(final double notional, final Index index, final Date baseDate, final Date fixingDate,
            final Date paymentDate) {
        this(notional, index, baseDate, fixingDate, paymentDate, false);
    }

    public IndexedCashFlow(final double notional, final Index index, final Date baseDate, final Date fixingDate,
            final Date paymentDate, final boolean growthOnly) {
        QL.require(index != null, "no index provided");
        this.notional_ = notional;
        this.index_ = index;
        this.baseDate_ = baseDate.clone();
        this.fixingDate_ = fixingDate.clone();
        this.paymentDate_ = paymentDate.clone();
        this.growthOnly_ = growthOnly;
        // Java port mirrors C++ registerWith via the standard observer
        // attachment used by other cashflows (see e.g. FloatingRateCoupon).
        index_.addObserver(new org.jquantlib.util.Observer() {
            @Override
            public void update() {
                notifyObservers();
            }
        });
    }

    //
    // public methods
    //

    public double notional() {
        return notional_;
    }

    public Date baseDate() {
        return baseDate_.clone();
    }

    public Date fixingDate() {
        return fixingDate_.clone();
    }

    public Index index() {
        return index_;
    }

    public boolean growthOnly() {
        return growthOnly_;
    }

    public double baseFixing() {
        return index_.fixing(baseDate());
    }

    public double indexFixing() {
        return index_.fixing(fixingDate_);
    }

    //
    // implements CashFlow
    //

    @Override
    public double amount() {
        // C++ stores `amount_` lazily via LazyObject.calculate(), but the
        // Java port follows the existing Coupon-family convention of
        // recomputing on demand (no LazyObject membership).
        final double i0 = baseFixing();
        final double i1 = indexFixing();
        if ( growthOnly_ ) {
            return notional() * (i1 / i0 - 1.0);
        }
        return notional() * (i1 / i0);
    }

    //
    // implements Event
    //

    @Override
    public Date date() {
        return paymentDate_.clone();
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< IndexedCashFlow > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
