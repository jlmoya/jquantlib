/*
 Copyright (C) 2007 Richard Gomes

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

package org.jquantlib.util;

import java.lang.ref.WeakReference;

/**
 * Implementation of Observable that holds references to Observers as WeakReferences.
 *
 * @author Martin Fischer (original author)
 * @author Richard Gomes
 * @author Srinivas Hasti
 * @note This implementation notifies the observers in a synchronous fashion. Note that this can cause trouble if you
 * notify observers while in a transactional context because the notification is then done also in the transaction.
 *
 * <p>
 * This class is based on the work done by Martin Fischer. See references below.
 * @see <a href="http://www.jroller.com/martin_fischer/entry/a_generic_java_observer_pattern"> Martin Fischer: Observer
 * and Observable interfaces</a>
 * @see <a href="http://jdj.sys-con.com/read/35878.htm">Improved Observer/Observable</a>
 * @see Observable
 * @see Observer
 * @see DefaultObservable
 */
public class WeakReferenceObservable extends DefaultObservable {

    public WeakReferenceObservable(final Observable observable) {
        super(observable);
    }

    @Override
    public void addObserver(final Observer referent) {
        super.addObserver(new WeakReferenceObserver(referent));
    }

    /**
     * This method deletes the Observer passed as argument but also discards those Observers which where reclaimed by
     * gc
     */
    @Override
    public void deleteObserver(final Observer observer) {
        for ( final Observer weakObserver : getObservers() ) {
            final WeakReferenceObserver weakReference = (WeakReferenceObserver) weakObserver;
            final Observer referent = weakReference.get();
            if ( referent == null || referent.equals(observer) )
                deleteWeakReference(weakReference);
        }
    }

    /**
     * Phase 2x A.4: notification-time compaction. Before fanning out the notification, sweep the observer list and
     * remove any {@link WeakReferenceObserver} whose referent has been GC'd. Without this sweep a hot notify loop
     * (e.g., {@code Settings.setEvaluationDate} called many times per inner iteration) would visit every dead weak ref
     * on every cycle — O(notify-cycles * dead-observer-count). The C++ ObservableSettings model has analogous lazy
     * compaction.
     */
    @Override
    public void notifyObservers() {
        compact();
        super.notifyObservers();
    }

    @Override
    public void notifyObservers(final Object arg) {
        compact();
        super.notifyObservers(arg);
    }

    /**
     * Removes any wrapped {@link WeakReferenceObserver} whose underlying referent has already been GC'd. Idempotent and
     * cheap when the list is small or has no dead entries.
     */
    public void compact() {
        for ( final Observer weakObserver : getObservers() ) {
            if (weakObserver instanceof WeakReferenceObserver wro) {
                if ( wro.get() == null ) {
                    deleteWeakReference(wro);
                }
            }
        }
    }

    private void deleteWeakReference(final WeakReferenceObserver observer) {
        super.deleteObserver(observer);
    }

    /**
     * Override of {@link DefaultObservable#unwrap(Observer)} so that deferred-update registration
     * ({@link ObservableSettings}) stores the actual user observer rather than the wrapping
     * {@link WeakReferenceObserver}. If the referent has been GC'd, returns {@code null} so the dead reference is
     * skipped.
     */
    @Override
    protected Observer unwrap(final Observer observer) {
        if (observer instanceof WeakReferenceObserver wro) {
            return wro.get();
        }
        return observer;
    }

    //
    // inner classes
    //

    private class WeakReferenceObserver extends WeakReference< Observer > implements Observer {

        public WeakReferenceObserver(final Observer referent) {
            super(referent);
        }

        @Override
        //XXX::OBS public void update(final Observable o, final Object arg) {
        public void update() {
            final Observer referent = get();
            if ( referent != null )
                //XXX::OBS referent.update(o, arg);
                referent.update();
            else
                deleteWeakReference(this);
        }
    }

}
