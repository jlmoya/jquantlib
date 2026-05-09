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

import java.util.List;

import org.jquantlib.indexes.EquityIndex;
import org.jquantlib.time.Date;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

/**
 * Abstract pricer base for {@link EquityCashFlow}.
 *
 * <p>Mirrors C++ {@code EquityCashFlowPricer} at v1.42.1
 * ({@code ql/cashflows/equitycashflow.hpp:70-87}). Concrete implementations
 * (e.g., {@link EquityQuantoCashFlowPricer}) compute the per-unit-notional
 * price; the {@link EquityCashFlow#amount()} multiplies by notional.
 *
 * @author JQuantLib migration team (Phase 5d.5-EQ)
 */
public abstract class EquityCashFlowPricer implements Observer, Observable {

    // Phase 2x A.4: WeakReferenceObservable so test runs don't accumulate
    // observers across cycles.
    private final DefaultObservable delegatedObservable =
            new org.jquantlib.util.WeakReferenceObservable(this);

    protected EquityIndex index_;
    protected Date baseDate_;
    protected Date fixingDate_;
    protected boolean growthOnlyPayoff_;

    /** Price per unit notional. */
    public abstract double price();

    /** Pull the index, base date, fixing date and payoff flag from the cashflow. */
    public abstract void initialize(EquityCashFlow cashFlow);

    //
    // implements Observer
    //

    @Override
    public void update() {
        notifyObservers();
    }

    //
    // implements Observable
    //

    @Override
    public void addObserver(final Observer observer) {
        delegatedObservable.addObserver(observer);
    }

    @Override
    public int countObservers() {
        return delegatedObservable.countObservers();
    }

    @Override
    public void deleteObserver(final Observer observer) {
        delegatedObservable.deleteObserver(observer);
    }

    @Override
    public void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    @Override
    public void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }

    @Override
    public List<Observer> getObservers() {
        return delegatedObservable.getObservers();
    }
}
