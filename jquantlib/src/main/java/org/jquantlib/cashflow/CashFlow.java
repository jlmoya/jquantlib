/*
 Copyright (C) 2008 Srinivas Hasti

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

import org.jquantlib.Settings;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * @author Srinivas Hasti
 */
public abstract class CashFlow extends Event implements Comparable< CashFlow > {

    //
    // public abstract methods
    //

    /**
     * @return amount of the cash flow. The amount is not discounted, i.e., it is the actual amount paid at the cash
     * flow date.
     */
    public abstract double amount();

    /**
     * Mirror of C++ {@code CashFlow::exCouponDate()} (ql/cashflow.hpp:66). Default implementation returns a
     * null/default {@link Date}, meaning "the cashflow has no ex-coupon date". Subclasses such as {@link Coupon}
     * override this. Phase 5e.5b-CFC-d-93.
     */
    public Date exCouponDate() {
        return new Date();
    }

    /**
     * Mirror of C++ {@code CashFlow::tradingExCoupon(refDate)} (ql/cashflow.cpp:51-61). Returns {@code true} iff the
     * cashflow has a non-null ex-coupon date that is on or before {@code refDate} (or the
     * {@link Settings#evaluationDate()} when {@code refDate} is null/default). Phase 5e.5b-CFC-d-93.
     */
    public boolean tradingExCoupon(final Date refDate) {
        final Date ecd = exCouponDate();
        if ( ecd == null || ecd.isNull() ) {
            return false;
        }
        final Date ref = (refDate != null && !refDate.isNull()) ? refDate : new Settings().evaluationDate();
        return ecd.le(ref);
    }

    /** Overload — uses today's evaluation date. */
    public boolean tradingExCoupon() {
        return tradingExCoupon(null);
    }

    //
    // overrides Event::hasOccurred to honor C++ semantics
    //

    /**
     * Mirrors C++ {@code CashFlow::hasOccurred(refDate, includeRefDate)} (cashflow.cpp v1.42.1 lines 27-49). Adds the
     * {@link Settings#includeTodaysCashFlows()} override to the base {@link Event#hasOccurred(Date, Boolean)}
     * behavior.
     *
     * <p>Boolean parameter mirrors C++ {@code ext::optional<bool>}:
     * {@code null} means "use {@link Settings#includeReferenceDateEvents()}".
     *
     * <p>If {@code refDate} equals the current evaluation date (or is
     * null/default), the {@code includeTodaysCashFlows} setting (when non-null) overrides the {@code includeRefDate}
     * parameter.
     */
    @Override
    public boolean hasOccurred(final Date refDate, Boolean includeRefDate) /* @ReadOnly */ {
        // Easy and quick handling of most cases (cashflow.cpp:30-37):
        // when refDate is set and unambiguously before/after the cash-flow
        // date, return without touching settings.
        if ( refDate != null && !refDate.isNull() ) {
            final Date cf = date();
            if ( refDate.lt(cf) ) {
                return false;
            }
            if ( cf.lt(refDate) ) {
                return true;
            }
        }

        // refDate equals (or is null/default-construed as) the evaluation
        // date — apply the includeTodaysCashFlows override (cashflow.cpp:39-47).
        final Settings settings = new Settings();
        if ( refDate == null || refDate.isNull() || refDate.equals(settings.evaluationDate()) ) {
            final Boolean includeToday = settings.includeTodaysCashFlows();
            if ( includeToday != null ) {
                includeRefDate = includeToday;
            }
        }
        return super.hasOccurred(refDate, includeRefDate);
    }

    /** Overload — delegates to the {@link Boolean}-typed C++-aligned form. */
    @Override
    public boolean hasOccurred(final Date refDate) /* @ReadOnly */ {
        return hasOccurred(refDate, null);
    }

    /** Overload — delegates to the {@link Boolean}-typed C++-aligned form. */
    @Override
    public boolean hasOccurred(final Date refDate, final boolean includeRefDate) /* @ReadOnly */ {
        return hasOccurred(refDate, Boolean.valueOf(includeRefDate));
    }

    //
    // implements Comparable
    //

    @Override
    public int compareTo(final CashFlow c2) {
        if ( date().lt(c2.date()) ) {
            return -1;
        }

        if ( date().equals(c2.date()) ) {
            try {
                if ( amount() < c2.amount() ) {
                    return -1;
                }
            } catch ( final Exception e ) {
                return -1;
            }
            return 0;
        }

        return 1;
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< CashFlow > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }

}
