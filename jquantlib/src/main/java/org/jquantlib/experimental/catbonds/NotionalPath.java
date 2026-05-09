/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.experimental.catbonds;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.time.Date;

/**
 * Tracks the evolution of the notional fraction of a catastrophe bond along a
 * simulated path.
 *
 * <p>Port of {@code ql/experimental/catbonds/riskynotional.hpp/.cpp}
 * {@code NotionalPath}.
 *
 * <p>The internal list always starts with entry {@code (Date(), 1.0)} (full
 * notional at time zero).  Additional entries record reductions ordered by date.
 */
public class NotionalPath {

    /** Sorted list of (Date, rate) pairs; first entry always has Date() and rate 1.0. */
    private final List<DateRealPair> notionalRate_ = new ArrayList<>();

    public NotionalPath() {
        notionalRate_.add(new DateRealPair(new Date(), 1.0));
    }

    /**
     * Returns the fraction of the original notional remaining on {@code date}.
     * The notional is taken after reductions (same convention as C++).
     */
    public double notionalRate(final Date date) {
        int i = 0;
        while (i < notionalRate_.size() && !notionalRate_.get(i).date.gt(date)) {
            i++;
        }
        // notionalRate_[i-1] is the last entry with date <= d
        return notionalRate_.get(i - 1).value;
    }

    /** Resets to the initial state (full notional). */
    public void reset() {
        // Keep only the first (sentinel) entry
        while (notionalRate_.size() > 1) {
            notionalRate_.remove(notionalRate_.size() - 1);
        }
    }

    /** Appends a notional reduction on the given date. */
    public void addReduction(final Date date, final double newRate) {
        notionalRate_.add(new DateRealPair(date, newRate));
    }

    /**
     * Returns the total loss as a fraction of the original notional
     * (i.e., 1.0 - final notional rate).
     */
    public double loss() {
        return 1.0 - notionalRate_.get(notionalRate_.size() - 1).value;
    }
}
