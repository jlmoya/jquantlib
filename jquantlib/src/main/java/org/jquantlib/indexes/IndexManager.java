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

package org.jquantlib.indexes;

import org.jquantlib.time.TimeSeries;
import org.jquantlib.util.Observable;
import org.jquantlib.util.ObservableValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IndexManager {

    private static final long serialVersionUID = -9204254124065694863L;

    private static Map< String, TimeSeries< Double > > data;
    private static Map< String, Observable > notifiers;
    private static volatile IndexManager instance;

    //
    // static public methods
    //

    private IndexManager() {
        data = new ConcurrentHashMap<>();
        notifiers = new ConcurrentHashMap<>();
    }

    //
    // private constructors
    //

    public static IndexManager getInstance() {
        if ( instance == null ) {
            synchronized ( IndexManager.class ) {
                if ( instance == null ) {
                    instance = new IndexManager();
                }
            }
        }
        return instance;
    }

    public TimeSeries< Double > getHistory(final String name) {
        return data.get(name);
    }

    public void setHistory(final String name, final TimeSeries< Double > history) {
        data.put(name, history);
    }

    public void clearHistory(final String name) {
        data.remove(name);
    }

    public void clearHistories() {
        data.clear();
    }

    /**
     * Returns the per-name notifier shared across all index instances with the same name. Mirrors C++ v1.42.1
     * ql/indexes/indexmanager.cpp:44-50, which caches notifiers in a map so that observers registered through any index
     * instance fire when {@code addFixing}/{@code clearHistory} is invoked through any other instance with the same
     * name.
     *
     * Phase 5c align: previously this returned a new {@code ObservableValue} on every call, breaking observer
     * notifications across index instances.
     */
    public Observable notifier(final String name) {
        Observable n = notifiers.get(name);
        if ( n == null ) {
            // ObservableValue<String> wraps the index name as a stable carrier;
            // what matters is that the same Observable instance is returned on
            // every call so observers registered via any instance fire on
            // notifyObservers() invoked from any other.
            n = new ObservableValue< String >(name);
            notifiers.put(name, n);
        }
        // Ensure history exists for backward compatibility.
        if ( data.get(name) == null ) {
            data.put(name, new TimeSeries< Double >(Double.class));
        }
        return n;
    }

}
