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
 Copyright (C) 2003 Decillion Pty(Ltd)
 Copyright (C) 2003 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.cashflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jquantlib.QL;
import org.jquantlib.time.Date;

/**
 * Distribution over a number of dates.
 * <p>
 * Wraps a {@code Date -> Real} map (kept in ascending date order) and provides the redistribution ("rebin") algorithm
 * used to spread cashflow amounts over a different time grid.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/timebasket.hpp/cpp} ({@code class TimeBasket}). The C++ class
 * privately inherits {@code std::map<Date,Real>}; this Java port composes a {@link TreeMap} keyed by {@link Date} (which
 * is naturally ordered) and re-exposes the relevant map operations.
 *
 * @author Decillion Pty (C++ original)
 * @author StatPro Italia (C++ original)
 */
public class TimeBasket {

    //
    // private fields
    //

    /** Backing store; {@link Date} sorts ascending so iteration order matches C++ {@code std::map<Date,Real>}. */
    private final TreeMap< Date, Double > map_ = new TreeMap< Date, Double >();

    //
    // public constructors
    //

    /** Empty basket. Mirror of C++ {@code TimeBasket() = default}. */
    public TimeBasket() {
        // no-op
    }

    /**
     * Build a basket from parallel {@code dates}/{@code values} vectors. Mirror of C++
     * {@code TimeBasket(const std::vector<Date>&, const std::vector<Real>&)} (timebasket.cpp:27-34).
     */
    public TimeBasket(final List< Date > dates, final List< Double > values) {
        QL.require(dates.size() == values.size(), "number of dates differs from number of values");
        for ( int i = 0; i < dates.size(); i++ ) {
            map_.put(dates.get(i), values.get(i));
        }
    }

    //
    // map interface (mirrors the `using super::...` re-exports in timebasket.hpp:43-60)
    //

    /** Number of entries. Mirror of C++ {@code using super::size}. */
    public int size() {
        return map_.size();
    }

    /** {@code true} iff there are no entries. */
    public boolean isEmpty() {
        return map_.isEmpty();
    }

    /**
     * Element access. Mirror of C++ {@code using super::operator[]} read side: returns the stored value, or {@code 0.0}
     * if the date is absent (C++ {@code std::map::operator[]} value-initialises {@code Real} to 0.0).
     */
    public double get(final Date d) {
        final Double v = map_.get(d);
        return (v == null) ? 0.0 : v.doubleValue();
    }

    /** Element assignment. Mirror of C++ {@code using super::operator[]} write side. */
    public void set(final Date d, final double value) {
        map_.put(d, value);
    }

    /** Membership. Mirror of C++ {@code TimeBasket::hasDate(const Date&)} (timebasket.hpp:76-79). */
    public boolean hasDate(final Date d) {
        return map_.containsKey(d);
    }

    /**
     * Live view of the underlying {@code Date -> Real} entries in ascending date order. Mirror of C++ {@code begin()} /
     * {@code end()} iteration.
     */
    public Iterable< Map.Entry< Date, Double > > entries() {
        return map_.entrySet();
    }

    /**
     * Reverse (descending date order) view of the underlying entries. Mirror of C++ {@code rbegin()} / {@code rend()}.
     */
    public Iterable< Map.Entry< Date, Double > > reverseEntries() {
        return map_.descendingMap().entrySet();
    }

    //
    // algebra (timebasket.hpp:81-93)
    //

    /** {@code this += other}; per-date accumulation. Mirror of C++ {@code TimeBasket::operator+=}. */
    public TimeBasket addAssign(final TimeBasket other) {
        for ( final Map.Entry< Date, Double > j : other.map_.entrySet() ) {
            map_.merge(j.getKey(), j.getValue(), Double::sum);
        }
        return this;
    }

    /** {@code this -= other}; per-date accumulation. Mirror of C++ {@code TimeBasket::operator-=}. */
    public TimeBasket subtractAssign(final TimeBasket other) {
        for ( final Map.Entry< Date, Double > j : other.map_.entrySet() ) {
            map_.merge(j.getKey(), -j.getValue(), Double::sum);
        }
        return this;
    }

    //
    // other methods
    //

    /**
     * Redistribute the entries over the given dates.
     * <p>
     * Faithful port of C++ {@code TimeBasket::rebin(const std::vector<Date>&)} (timebasket.cpp:36-74). Each entry's
     * value is allocated to the bucket on or after its date (and, when it falls strictly between two buckets, split
     * linearly by day-count distance between the bracketing buckets).
     */
    public TimeBasket rebin(final List< Date > buckets) {
        QL.require(!buckets.isEmpty(), "empty bucket structure");

        final List< Date > sbuckets = new ArrayList< Date >(buckets);
        Collections.sort(sbuckets);

        final TimeBasket result = new TimeBasket();

        for ( final Date sbucket : sbuckets ) {
            result.map_.put(sbucket, 0.0);
        }

        for ( final Map.Entry< Date, Double > j : map_.entrySet() ) {
            final Date date = j.getKey();
            final double value = j.getValue();
            Date pDate;
            Date nDate = null; // C++ Date() (null date)

            // lower_bound: index of first bucket whose date is >= `date`.
            final int bi = lowerBound(sbuckets, date);

            if ( bi == sbuckets.size() ) {
                pDate = sbuckets.get(sbuckets.size() - 1);
            } else {
                pDate = sbuckets.get(bi);
            }

            if ( bi != 0 && bi != sbuckets.size() ) {
                nDate = sbuckets.get(bi - 1);
            }

            if ( pDate.eq(date) || nDate == null ) {
                result.map_.merge(pDate, value, Double::sum);
            } else {
                final double pDays = pDate.sub(date);
                final double nDays = date.sub(nDate);
                final double tDays = pDate.sub(nDate);
                result.map_.merge(pDate, value * (nDays / tDays), Double::sum);
                result.map_.merge(nDate, value * (pDays / tDays), Double::sum);
            }
        }
        return result;
    }

    //
    // private helpers
    //

    /**
     * Index of the first element in the ascending-sorted {@code sorted} list whose date is not less than {@code key}
     * (i.e. {@code >= key}); returns {@code sorted.size()} if none. Mirror of C++ {@code std::lower_bound}.
     */
    private static int lowerBound(final List< Date > sorted, final Date key) {
        int lo = 0;
        int hi = sorted.size();
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( sorted.get(mid).lt(key) ) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

}
