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
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.time.Date;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.util.List;

/**
 * Claim associated to a default event.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::Claim}
 * ({@code ql/instruments/claim.{hpp,cpp}}). A {@code Claim} computes the payment due to the protection buyer when a
 * default event occurs. The abstract {@link #amount(Date, double, double)} hook is implemented by concrete subclasses
 * such as {@link FaceValueClaim}.
 *
 * <p>Implements {@link Observer}/{@link Observable} via the standard
 * delegate-pattern used elsewhere in JQuantLib (see e.g. {@link org.jquantlib.pricingengines.GenericEngine}).
 *
 * @category instruments
 */
public abstract class Claim implements Observable, Observer {

    private final Observable delegatedObservable = new DefaultObservable(this);

    //
    // implements Observer
    //

    /**
     * Returns the claim amount due to the protection buyer at the given default date.
     *
     * @param defaultDate  the date on which the default event occurs
     * @param notional     the contract notional
     * @param recoveryRate the recovery rate (a fraction in {@code [0,1]})
     * @return the claim amount
     */
    public abstract double amount(Date defaultDate, double notional, double recoveryRate);

    //
    // implements Observable via delegate pattern
    //

    @Override
    public void update() {
        notifyObservers();
    }

    @Override
    public final void addObserver(final Observer observer) {
        delegatedObservable.addObserver(observer);
    }

    @Override
    public final int countObservers() {
        return delegatedObservable.countObservers();
    }

    @Override
    public final void deleteObserver(final Observer observer) {
        delegatedObservable.deleteObserver(observer);
    }

    @Override
    public final void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    @Override
    public final void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }

    @Override
    public final void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public final List< Observer > getObservers() {
        return delegatedObservable.getObservers();
    }
}
