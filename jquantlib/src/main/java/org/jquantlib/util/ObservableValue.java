/*
 Copyright (C) 2008 Richard Gomes

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
 Copyright (C) 2005, 2006 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.util;

import java.util.List;

/**
 * Observable and assignable proxy to concrete value
 * <p>
 * Observers can be registered with instances of this class so that they are notified when a different value is assigned
 * to such instances. Client code can copy the contained value or pass it to functions via implicit conversion.
 *
 * @author Srinivas Hasti
 * @note it is not possible to call non-const method on the returned value. This is by design, as this possibility would
 * necessarily bypass the notification code; client code should modify the value via re-assignment instead.
 */

public class ObservableValue< T > implements Observable {

    //
    // private fields
    //

    /**
     * Implements multiple inheritance via delegate pattern to an inner class.
     *
     * <p>Phase 2x A.4: switched to {@link WeakReferenceObservable} so
     * that observers from completed tests don't accumulate on the observable value's observer list and cascade on every
     * {@code Settings.setEvaluationDate}.
     */
    private final Observable delegatedObservable = new WeakReferenceObservable(this);

    //
    // public constructors
    //
    private T value;

    public ObservableValue(final T value) {
        this.value = value;
    }

    //
    // public methods
    //

    public ObservableValue(final ObservableValue< T > observable) {
        this.value = observable.value;
    }

    public void assign(final T value) {
        this.value = value;
        delegatedObservable.notifyObservers();
    }

    public void assign(final ObservableValue< T > observable) {
        this.value = observable.value;
        delegatedObservable.notifyObservers();
    }

    //
    // implements Observable
    //

    public T value() {
        return value;
    }

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
    public void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    @Override
    public void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }

    @Override
    public void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public List< Observer > getObservers() {
        return delegatedObservable.getObservers();
    }

}
