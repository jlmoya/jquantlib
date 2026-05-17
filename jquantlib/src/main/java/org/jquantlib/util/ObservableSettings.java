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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl
 Copyright (C) 2011, 2012 Ferdinando Ametrano
 Copyright (C) 2013 Chris Higgs
 Copyright (C) 2015 Klaus Spanderen
*/

package org.jquantlib.util;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Global repository for run-time observable settings.
 *
 * <p>Java port of C++ QuantLib {@code ObservableSettings}
 * (ql/patterns/observable.hpp). Mirrors the non-thread-safe branch
 * ({@code QL_ENABLE_THREAD_SAFE_OBSERVER_PATTERN} undefined); the
 * thread-safe variant is not needed in Java because the JVM provides
 * its own GC + memory model.
 *
 * <p>Two-state toggle:
 * <ul>
 *   <li>{@link #disableUpdates(boolean)} with {@code deferred=false}:
 *       observers' {@code update()} calls are dropped silently while
 *       updates are disabled.</li>
 *   <li>{@link #disableUpdates(boolean)} with {@code deferred=true}:
 *       observers' {@code update()} calls are recorded and replayed in
 *       a single batch when {@link #enableUpdates()} is called.</li>
 * </ul>
 *
 * <p>Hooked into the notification flow by {@code DefaultObservable.notifyObservers}.
 *
 * @author JQuantLib migration contributors
 */
public final class ObservableSettings {

    private static final ObservableSettings INSTANCE = new ObservableSettings();

    /** Returns the global singleton instance. */
    public static ObservableSettings instance() {
        return INSTANCE;
    }

    private boolean updatesEnabled = true;
    private boolean updatesDeferred = false;
    private boolean runningDeferredUpdates = false;

    /**
     * Linked-set preserves insertion order so deferred updates fire in
     * registration order — matches the C++ map iteration order for the
     * test {@code testObservableSettings}.
     */
    private final Set<Observer> deferredObservers = new LinkedHashSet<Observer>();

    private ObservableSettings() {
        // singleton
    }

    /**
     * Disable notification dispatch.
     *
     * @param deferred when {@code true}, observers that would have been
     *        notified are added to a deferred queue and dispatched once,
     *        in registration order, when {@link #enableUpdates()} is
     *        called. When {@code false}, notifications are dropped.
     */
    public void disableUpdates(final boolean deferred) {
        this.updatesEnabled = false;
        this.updatesDeferred = deferred;
    }

    /** Convenience: {@code disableUpdates(false)}. */
    public void disableUpdates() {
        disableUpdates(false);
    }

    /**
     * Re-enable notification dispatch. If updates were deferred and any
     * observers accumulated, fire each accumulated observer exactly
     * once (deduplicated by identity), in insertion order.
     */
    public void enableUpdates() {
        this.updatesEnabled = true;
        this.updatesDeferred = false;

        if (deferredObservers.isEmpty()) {
            return;
        }

        // Take snapshot then clear, so a re-entrant disable/enable inside
        // an observer update() doesn't re-process the in-flight set.
        final Observer[] snapshot = deferredObservers.toArray(new Observer[0]);
        deferredObservers.clear();

        runningDeferredUpdates = true;
        RuntimeException firstException = null;
        try {
            for (final Observer obs : snapshot) {
                if (obs == null) continue;
                try {
                    obs.update();
                } catch (final RuntimeException e) {
                    if (firstException == null) firstException = e;
                }
            }
        } finally {
            runningDeferredUpdates = false;
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    public boolean updatesEnabled() {
        return updatesEnabled;
    }

    public boolean updatesDeferred() {
        return updatesDeferred;
    }

    public boolean runningDeferredUpdates() {
        return runningDeferredUpdates;
    }

    /**
     * Package-private hook used by {@code DefaultObservable.notifyObservers}
     * to record an observer that would have been notified while updates
     * are deferred. No-op when not in deferred mode.
     */
    void registerDeferredObserver(final Observer observer) {
        if (updatesDeferred && observer != null) {
            deferredObservers.add(observer);
        }
    }

    /**
     * Package-private hook used by observables that drop an observer
     * while it sits in the deferred queue.
     */
    void unregisterDeferredObserver(final Observer observer) {
        if (observer != null) {
            deferredObservers.remove(observer);
        }
    }

}
