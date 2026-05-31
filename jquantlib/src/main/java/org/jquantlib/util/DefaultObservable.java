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

import org.jquantlib.QL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

// --------------------------------------------------------
// This class is based on the work done by Martin Fischer.
// See references in JavaDoc
//--------------------------------------------------------

/**
 * Default implementation of an {@link Observable}.
 * <p>
 * This implementation notifies the observers in a synchronous fashion. Note that this can cause trouble if you notify
 * the observers while in a transactional context because once the notification is done it cannot be rolled back.
 *
 * <p>Thread-safety: every mutation and read of the observer list is synchronized on this instance, and
 * {@link #notifyObservers(Object)} dispatches over a one-shot snapshot taken under that lock. This makes the observer
 * registry safe under concurrent access (e.g. a shared {@link org.jquantlib.quotes.Quote} updated from several threads)
 * while avoiding the per-mutation array copy of the {@link java.util.concurrent.CopyOnWriteArrayList} it formerly used —
 * that copy dominated allocation during instrument pricing, where every {@code LazyObject.calculate()} re-registers
 * observers.
 *
 * @author Richard Gomes
 * @author Srinivas Hasti
 * @see <a href="http://www.jroller.com/martin_fischer/entry/a_generic_java_observer_pattern"> Martin Fischer: Observer
 * and Observable interfaces</a>
 * @see <a href="http://jdj.sys-con.com/read/35878.htm">Improved Observer/Observable</a>
 * @see Observable
 * @see Observer
 * @see WeakReferenceObservable
 */
public class DefaultObservable implements Observable {

    final private static String OBSERVABLE_IS_NULL = "observable is null";
    final private static String CANNOT_NOTIFY_OBSERVERS = "could not notify one or more observers";
    private static final Logger logger = LoggerFactory.getLogger(DefaultObservable.class);

    //
    // private final fields
    //

    private final List< Observer > observers;
    private final Observable observable;

    //
    // public constructors
    //

    public DefaultObservable(final Observable observable) {
        QL.require(observable != null, DefaultObservable.OBSERVABLE_IS_NULL);
        // Plain ArrayList guarded by this instance's monitor (see methods below).
        // The former CopyOnWriteArrayList copied its whole backing array on every
        // addObserver/deleteObserver; iteration safety during dispatch is instead
        // provided by snapshotting once per notifyObservers under the lock.
        this.observers = new ArrayList<>();
        this.observable = observable;
    }

    //
    // public methods
    //

    @Override
    public synchronized void addObserver(final Observer observer) {
        observers.add(observer);
    }

    @Override
    public synchronized int countObservers() {
        return observers.size();
    }

    @Override
    public synchronized List< Observer > getObservers() {
        // Defensive snapshot copy: callers (and an observer's own update()) may
        // iterate this while the list is mutated concurrently, so hand back a
        // stable copy — matching the snapshot semantics CopyOnWriteArrayList gave.
        return Collections.unmodifiableList(new ArrayList<>(this.observers));
    }

    @Override
    public synchronized void deleteObserver(final Observer observer) {
        observers.remove(observer);
    }

    @Override
    public synchronized void deleteObservers() {
        observers.clear();
    }

    /**
     * Removes every observer matching {@code filter} in a single in-place pass under this instance's lock. Subclasses
     * (e.g. {@link WeakReferenceObservable}) use this to drop GC'd weak references without iterating-and-mutating a live
     * view (which a plain {@code ArrayList} rejects with {@link java.util.ConcurrentModificationException}) and without
     * the per-call snapshot copy that {@link #getObservers()} makes.
     *
     * @return {@code true} if at least one observer was removed.
     */
    protected synchronized boolean removeObserversIf(final Predicate<? super Observer> filter) {
        return observers.removeIf(filter);
    }

    @Override
    public void notifyObservers() {
        notifyObservers(null);
    }

    @Override
    public void notifyObservers(final Object arg) {
        // Honor the global ObservableSettings toggle (C++ ObservableSettings::
        // updatesEnabled/updatesDeferred). When disabled, dispatch is
        // suppressed; when deferred, register the observers for replay on
        // the subsequent enableUpdates() call.
        final ObservableSettings settings = ObservableSettings.instance();
        final Object[] snapshot;
        synchronized ( this ) {
            if ( observers.isEmpty() ) {
                return;
            }
            // One snapshot, taken under the lock, so a concurrent mutation — or an
            // observer that (de)registers inside its own update() — cannot perturb
            // this dispatch pass. Same guarantee CopyOnWriteArrayList's iterator
            // gave, but the backing array is no longer copied on every add/remove.
            snapshot = observers.toArray();
        }
        // Dispatch OUTSIDE the lock: update() runs arbitrary user code and may
        // re-enter this observable; holding the monitor across it would risk
        // deadlock and needlessly serialise unrelated work.
        if ( !settings.updatesEnabled() ) {
            if ( settings.updatesDeferred() ) {
                for ( final Object o : snapshot ) {
                    final Observer target = unwrap((Observer) o);
                    if ( target != null ) {
                        settings.registerDeferredObserver(target);
                    }
                }
            }
            return;
        }
        Exception exception = null;
        for ( final Object o : snapshot ) {
            try {
                wrappedNotify((Observer) o, observable, arg);
            } catch ( final Exception e ) {
                // Quite a dilemma. If we don't catch the exception,
                // other observers will not receive the notification
                // and might be left in an incorrect state. If we do
                // catch it and continue the loop (as we do here) we
                // lose the exception. The least evil might be to try
                // and notify all observers, while raising an
                // exception if something bad happened.
                exception = e;
            }
        }
        if ( exception != null ) {
            // Route through SLF4J at WARN level so test environments can
            // silence the noise via logger config. Original code dumped to
            // stderr via QL.error which polluted every test run with the
            // intentional fast-path/slow-path observer failures (e.g. when
            // a Basket re-evaluates ahead of inception before all dependent
            // observables have caught up). The observer loop above already
            // catches + continues; this log is informational, not fatal.
            logger.warn(CANNOT_NOTIFY_OBSERVERS + ": " + exception.getMessage());
        }
    }

    //
    // protected methods
    //

    /**
     * Hook used by {@link #notifyObservers(Object)} when updates are deferred: subclasses that wrap observers (e.g.
     * {@link WeakReferenceObservable} using {@link java.lang.ref.WeakReference}) override this to return the underlying
     * observer. Default returns the argument unchanged. Returning {@code null} signals the wrapped observer is no
     * longer reachable and should not be registered as a deferred observer.
     */
    protected Observer unwrap(final Observer observer) {
        return observer;
    }

    /**
     * This method is intended to encapsulate the notification semantics, in order to let extended classes to implement
     * their own version. Possible implementations are:
     * <li>remote notification;</li>
     * <li>notification via SwingUtilities.invokeLater</li>
     * <li>others...</li>
     *
     * <p>
     * The default notification simply does
     * <pre>
     * observer.update(observable, arg);
     * </pre>
     *
     * @param observer
     * @param observable
     * @param arg
     */
    protected void wrappedNotify(final Observer observer, final Observable observable, final Object arg) {
        observer.update();
    }

}
