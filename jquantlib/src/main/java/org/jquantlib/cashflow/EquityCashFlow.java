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
 Copyright (C) 2023 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.indexes.EquityIndex;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Equity-linked cash flow.
 *
 * <p>Single-period cashflow on an {@link EquityIndex}: pays
 * {@code notional * (I_T / I_0 - 1)} when {@code growthOnly=true} (default, swap-style) or {@code notional * I_T / I_0}
 * when {@code growthOnly=false} (bond-style). When a {@link EquityCashFlowPricer} is attached, the {@link #amount()}
 * delegates to {@code notional * pricer.price()}, allowing non-trivial valuations such as quanto-corrected returns.
 *
 * <p>Mirrors C++ {@code QuantLib::EquityCashFlow} at v1.42.1
 * ({@code ql/cashflows/equitycashflow.{hpp,cpp}}).
 *
 * @author JQuantLib migration team (Phase 5d.5-EQ)
 */
public class EquityCashFlow extends IndexedCashFlow {

    private final Observer notifier_ = new Observer() {
        @Override
        public void update() {
            notifyObservers();
        }
    };

    //
    // public constructors
    //
    private EquityCashFlowPricer pricer_;

    public EquityCashFlow(final double notional, final EquityIndex index, final Date baseDate, final Date fixingDate,
            final Date paymentDate) {
        this(notional, index, baseDate, fixingDate, paymentDate, true);
    }

    //
    // public methods
    //

    public EquityCashFlow(final double notional, final EquityIndex index, final Date baseDate, final Date fixingDate,
            final Date paymentDate, final boolean growthOnly) {
        super(notional, index, baseDate, fixingDate, paymentDate, growthOnly);
    }

    /**
     * Apply pricer to every {@link EquityCashFlow} in {@code leg}. Mirrors C++ {@code setCouponPricer(const Leg&, ...)}
     * at {@code ql/cashflows/equitycashflow.cpp:44-51}.
     */
    public static void setCouponPricer(final Leg leg, final EquityCashFlowPricer p) {
        for ( final CashFlow item : leg ) {
            if ( item instanceof EquityCashFlow ) {
                ((EquityCashFlow) item).setPricer(p);
            }
        }
    }

    //
    // overrides IndexedCashFlow
    //

    public void setPricer(final EquityCashFlowPricer pricer) {
        // C++ unregisterWith / registerWith — Java uses observer add/delete.
        // Hold the registration via a simple Observer wrapper so the pricer's
        // notifications propagate to this cashflow.
        if ( pricer_ != null ) {
            pricer_.deleteObserver(notifier_);
        }
        pricer_ = pricer;
        if ( pricer_ != null ) {
            pricer_.addObserver(notifier_);
        }
        notifyObservers();
    }

    //
    // overrides PolymorphicVisitable
    //

    public EquityCashFlowPricer pricer() {
        return pricer_;
    }

    //
    // private helpers
    //

    @Override
    public double amount() {
        if ( pricer_ == null ) {
            return super.amount();
        }
        pricer_.initialize(this);
        return notional() * pricer_.price();
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< EquityCashFlow > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
