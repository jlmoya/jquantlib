/*
 Copyright (C) 2008 Srinivas Hasti
 Copyright (C) 2010 Ricahrd Gomes

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

package org.jquantlib.time;

import java.util.Map;

/**
 * Container for historical data
 * <p>
 * This class acts as a generic repository for a set of historical data. Any single datum can be accessed through its
 * date, while sets of consecutive data can be accessed through iterators.
 * <p>
 * This class is intended to keep closest compatibility with QuantLib/C++ as possible, being nothing more than a
 * shortcut to class {@link Series}, where the first parametric type is fixed as {@link Date}.
 *
 * @author Richard Gomes
 * @see Series
 */
public class TimeSeries< V > extends Series< Date, V > {

    public TimeSeries(final Class< V > classV) {
        super(Date.class, classV);
    }

    /**
     * Constructs a {@link TimeSeries} pre-populated from an arbitrary
     * {@link Map} of date/value pairs. Mirrors C++ v1.42.1
     * {@code ql/timeseries.hpp}'s parametric custom-container constructor
     * ({@code TimeSeries<T, boost::unordered_map<Date, T>>}), which accepts
     * any map-like container of (Date, value) entries.
     * <p>
     * Entries are copied into the internal sorted {@link java.util.TreeMap}
     * delegate, so iteration order remains ascending by date regardless of
     * the input container's iteration order. Accepts any {@link Map}
     * implementation (e.g., {@link java.util.HashMap}, {@link java.util.LinkedHashMap}).
     *
     * @param classV runtime class token for the value type
     * @param data   source map of date-keyed entries; null is treated as
     *               empty
     */
    public TimeSeries(final Class< V > classV, final Map< Date, V > data) {
        super(Date.class, classV);
        if (data != null) {
            for (final Map.Entry< Date, V > e : data.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        }
    }

}
