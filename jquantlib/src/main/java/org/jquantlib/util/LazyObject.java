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

/*
 Copyright (C) 2003 RiskMap srl

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
 * Framework for calculation on demand and result caching.
 *
 * @author Richard Gomes
 * @see <a href="http://c2.com/cgi/wiki?LazyObject">Lazy Object Design Pattern</a>
 * @see Observer
 * @see Observable
 */
public abstract class LazyObject implements Observer, Observable {

    //
    // protected fields
    //

    /**
     * Implements multiple inheritance via delegate pattern to an inner class.
     *
     * <p>Phase 2x A.4: switched to {@link WeakReferenceObservable} so
     * that observers from completed tests don't accumulate on the lazy object's observer list and cascade on every
     * {@code Settings.setEvaluationDate}.
     *
     * @see Observable
     * @see DefaultObservable
     */
    private final Observable delegatedObservable = new WeakReferenceObservable(this);
    protected boolean calculated;
    protected boolean frozen;
    /**
     * Mirrors C++ {@code LazyObject::failed_} - set when a prior {@link #performCalculations()} invocation threw, so
     * subsequent {@link #update()} calls still forward a notification (observers must be told that the failed state is
     * invalidated by the new input).
     */
    protected boolean failed;
    private boolean alwaysForwardNotifications_;

    //
    // protected abstract methods
    //
    /**
     * Re-entrancy guard - mirrors C++ {@code LazyObject::updating_}. Set to {@code true} while {@link #update()} is
     * executing so that recursive calls from a downstream {@code notifyObservers()} chain return immediately, breaking
     * any observer-cycle that would otherwise cause a {@link StackOverflowError}.
     */
    private boolean updating_ = false;

    //
    // public constructors
    //

    /**
     * Creates a new LazyObject instance which is potentially able to perform calculations on demand every time it
     * observes a change in a {@link Observable} object. A LazyObject is an {@link Observer} and an {@link Observable}
     * at the same time.
     */
    public LazyObject() {
        this.calculated = false;
        this.frozen = false;
        this.failed = false;
        // Mirror C++ LazyObject ctor: pick up the per-session default
        // from LazyObject.Defaults. Note JQuantLib's Defaults default-default
        // is 'forward first only' (false), matching JQuantLib's historical
        // behaviour; this differs from C++ which defaults to
        // 'always forward' (true) when QL_FASTER_LAZY_OBJECTS is undefined.
        this.alwaysForwardNotifications_ = Defaults.instance().forwardsAllNotifications();
    }

    //
    // public final methods
    //

    /**
     * This method must implement any calculations which must be (re)done in order to calculate the desired results.
     *
     * @throws ArithmeticException
     *
     */
    protected abstract void performCalculations() throws ArithmeticException;

    /**
     * This method force the recalculation of any results which would otherwise be cached.
     *
     * @note Explicit invocation of this method is <b>not</b> necessary if the object registered itself as observer with
     * the structures on which such results depend. It is strongly advised to follow this policy when possible.
     */
    public final void recalculate() {
        final boolean wasFrozen = frozen;
        calculated = frozen = false;
        failed = false;
        try {
            calculate();
        } finally {
            frozen = wasFrozen;
            notifyObservers();
        }
    }

    /**
     * Returns whether a calculation has been performed and is currently cached. Mirrors C++
     * {@code LazyObject::isCalculated()}.
     * <p>Phase 5e.5b-CFC-d-62 alignment.
     * <p>Not declared {@code final} because legacy code in
     * {@link org.jquantlib.model.shortrate.onefactormodels.gaussian1d.MarkovFunctional} declares a same-named private
     * method (Java disallows shadowing a {@code final} superclass method even with {@code private}).
     */
    public boolean isCalculated() {
        return calculated;
    }

    /**
     * Force-set the {@code calculated} flag. Mirrors C++ {@code LazyObject::setCalculated(bool c)}
     * (ql/patterns/lazyobject.hpp:46). Required by {@code GlobalBootstrap::setupCostFunction()}
     * during multi-curve bootstrap to prevent the curve from being re-entered while its bootstrap
     * cost function is being evaluated (the LM iteration triggers observer chains that, without
     * this guard, reset {@code calculated} and cause infinite recursion).
     * <p>Phase 1.3 closure (D5-A-MCSpread). Public + non-final to mirror C++ availability.
     */
    public void setCalculated(final boolean c) {
        this.calculated = c;
    }

    /**
     * This method constrains the object to return the presently cached results on successive invocations, even if
     * arguments upon which they depend should change.
     */
    public final void freeze() {
        frozen = true;
    }

    /**
     * Forces the object to always forward notifications to its observers, even when already marked as needing
     * recalculation.
     *
     * <p>Mirrors C++ {@code LazyObject::alwaysForwardNotifications()}.
     * This is needed for instruments (e.g., swaptions) where the underlying asset can change the expired state without
     * triggering a recalculation, so observers must still be notified.
     */
    public final void alwaysForwardNotifications() {
        alwaysForwardNotifications_ = true;
    }

    /**
     * Causes the object to forward only the first notification received after each (re)calculation; subsequent
     * notifications are discarded until the next recalculation. Inverse of {@link #alwaysForwardNotifications()}.
     *
     * <p>Mirrors C++ {@code LazyObject::forwardFirstNotificationOnly()}.
     * Useful when the global default ({@link LazyObject.Defaults#alwaysForwardNotifications()}) has been flipped to
     * forward-all but a specific instance should opt back.
     */
    public final void forwardFirstNotificationOnly() {
        alwaysForwardNotifications_ = false;
    }

    //
    // protected methods
    //

    /**
     * This method reverts the effect of the <i><b>freeze</b></i> method, thus re-enabling recalculations.
     */
    public final void unfreeze() {
        frozen = false;
        // send notification, just in case we lost any
        notifyObservers();
    }

    //
    // implements Observer
    //

    // XXX:registerWith
    //    @Override
    //    public void registerWith(final Observable o) {
    //        o.addObserver(this);
    //    }
    //
    //    @Override
    //    public void unregisterWith(final Observable o) {
    //        o.deleteObserver(this);
    //    }

    /**
     * This method performs all needed calculations by calling the <i><b>performCalculations</b></i> method.
     * <p>
     *
     * @note Objects cache the results of the previous calculation. Such results will be returned upon later invocations
     * of <i><b>calculate</b></i>. When the results depend on arguments which could change between invocations, the lazy
     * object must register itself as observer of such objects for the calculations to be performed again when they
     * change.
     */
    protected void calculate() {
        if ( !calculated && !frozen ) {
            // prevent infinite recursion in case of bootstrapping
            calculated = true;
            try {
                performCalculations();
                // needed when calculate() is called directly after a
                // prior failure - mirrors C++ failed_ = false
                failed = false;
            } catch ( final ArithmeticException e ) {
                calculated = false;
                failed = true;
                throw e;
            } catch ( final RuntimeException e ) {
                // Java performCalculations() signature only declares
                // ArithmeticException, but in practice many subclasses
                // throw plain RuntimeException; mirror C++ catch-all so
                // failed_ semantics work for any thrown error.
                calculated = false;
                failed = true;
                throw e;
            }
        }
    }

    //
    // implements Observable
    //

    @Override
    //XXX::OBS public void update(final Observable o, final Object arg) {
    public void update() {
        // Mirrors C++ LazyObject::update() (ql/patterns/lazyobject.hpp).
        // Guard against recursive re-entry (observer cycles created during
        // inflation curve bootstrap). C++ uses an RAII UpdateChecker + updating_
        // flag; Java uses try/finally.
        if ( updating_ ) {
            // recursive call - break the cycle silently (C++ default behaviour,
            // without QL_THROW_IN_CYCLES defined)
            return;
        }
        updating_ = true;
        try {
            // forwards notifications only the first time, or always if
            // alwaysForward_, or if a prior calculation failed (so that
            // observers are told the failed state has been invalidated)
            if ( calculated || failed || alwaysForwardNotifications_ ) {
                // set to false BEFORE notifyObservers so that:
                //   1) a downstream calculate() call that re-enters update()
                //      and checks calculated_ sees false -> no double-notification
                //   2) non-lazy observers get fresh data (not stale cached values)
                calculated = false;
                failed = false;
                // observers don't expect notifications from frozen objects
                if ( !frozen )
                    //XXX::OBS notifyObservers(arg);
                    notifyObservers();
            } else {
                calculated = false;
            }
        } finally {
            updating_ = false;
        }
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

    //
    // per-session default settings
    //

    /**
     * Per-session defaults for the {@link LazyObject} class. Mirrors C++ {@code LazyObject::Defaults}
     * (ql/patterns/lazyobject.hpp).
     *
     * <p>Singleton. Lazy objects created <em>after</em> a call to one of
     * the setters pick up the new default in their constructor; lazy objects created before are unaffected (so toggling
     * the default mid-test should be paired with a {@code TearDown}-style restore).
     *
     * <p><b>Java-vs-C++ default note:</b> JQuantLib defaults to
     * {@code forwardsAllNotifications() == false} (i.e., forward only the first notification after recalculation). C++
     * defaults to {@code true} unless {@code QL_FASTER_LAZY_OBJECTS} is defined. The Java default is preserved for
     * backwards compatibility with the existing JQuantLib test suite.
     */
    public static final class Defaults {

        private static final Defaults INSTANCE = new Defaults();
        // JQuantLib historical default: 'forward first only' (false).
        private boolean forwardsAllNotifications_ = false;

        private Defaults() {
            // singleton
        }

        public static Defaults instance() {
            return INSTANCE;
        }

        /**
         * Sets the default for subsequently-created lazy objects to forward only the first notification after
         * recalculation.
         */
        public void forwardFirstNotificationOnly() {
            forwardsAllNotifications_ = false;
        }

        /**
         * Sets the default for subsequently-created lazy objects to forward every notification (no first-only filter).
         */
        public void alwaysForwardNotifications() {
            forwardsAllNotifications_ = true;
        }

        /** Returns the current default. */
        public boolean forwardsAllNotifications() {
            return forwardsAllNotifications_;
        }
    }
}
