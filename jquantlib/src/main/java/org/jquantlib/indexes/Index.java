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

import java.util.Iterator;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.lang.iterators.Iterables;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeSeries;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

/**
 * Purely virtual base class for indexes
 *
 * @author Srinivas Hasti
 */
//TODO: Code review and comments
public abstract class Index implements Observable {

	//
    // public abstract methods
    //

    /**
	 * @return name of the Index
	 */
	public abstract String name();

	/**
	 * @return the calendar defining valid fixing dates
	 */
	public abstract Calendar fixingCalendar();

	/**
	 *  @return TRUE if the fixing date is a valid one
	 */
	public abstract boolean isValidFixingDate(Date fixingDate);

	/**
	 * @return the fixing at the given date. The date passed as arguments must be the actual calendar date of the
	 * fixing; no settlement days must be used.
	 */
	public abstract double fixing(Date fixingDate, boolean forecastTodaysFixing);


	//
	// public methods
	//

	/**
	 * @return the fixing TimeSeries
	 */
	public TimeSeries<Double> timeSeries() {
		return IndexManager.getInstance().getHistory(name());
	}

	/**
	 * Stores the historical fixing at the given date
	 * <p>
	 * The date passed as arguments must be the actual calendar date of the
	 * fixing; no settlement days must be used.
	 */
	public void addFixing(final Date date, final double value) {
		addFixing(date, value, false);
	}
	
	/**
	 * Stores the historical fixing at the given date
	 * <p>
	 * The date passed as arguments must be the actual calendar date of the
	 * fixing; no settlement days must be used.
	 */
	public void addFixing(final Date date, final double value, final boolean forceOverwrite) {
		final String tag = name();
		boolean missingFixing;
		boolean validFixing;
		boolean noInvalidFixing = true;
		boolean noDuplicatedFixing = true;
		final TimeSeries<Double> h = IndexManager.getInstance().getHistory(tag);

        validFixing = isValidFixingDate(date);
        // Phase 2p A.2 align: null = missing (TimeSeries.get returns Double or
        // null). Short-circuit on null before any unboxing to avoid NPE.
        final Double currentValue = h.get(date);
        missingFixing = forceOverwrite
                || currentValue == null
                || Closeness.isClose(currentValue, Constants.NULL_REAL);
        if (validFixing) {
            if (missingFixing) {
                h.put(date, value);
            } else if (Closeness.isClose(currentValue, value)) {
                // Do nothing
            } else {
                noDuplicatedFixing = false;
            }
        } else {
            noInvalidFixing = false;
        }

		IndexManager.getInstance().setHistory(tag, h);

		// Phase 5c align: notify the per-name notifier so observers registered
		// with any other index instance sharing the same name fire as in C++
		// v1.42.1 (ql/indexes/indexmanager.hpp:104).
		IndexManager.getInstance().notifier(tag).notifyObservers();

		QL.ensure(noInvalidFixing , "at least one invalid fixing provided");  // TODO: message
		QL.ensure(noDuplicatedFixing , "at least one duplicated fixing provided");  // TODO: message
	}

	/**
	 * Stores historical fixings at the given dates
	 * <p>
	 * The dates passed as arguments must be the actual calendar dates of the
	 * fixings; no settlement days must be used.
	 */
	public final void addFixings(final Iterator<Date> dates, final Iterator<Double> values, final boolean forceOverwrite) {
		final String tag = name();
		boolean missingFixing;
		boolean validFixing;
		boolean noInvalidFixing = true;
		boolean noDuplicatedFixing = true;
		final TimeSeries<Double> h = IndexManager.getInstance().getHistory(tag);

		for (final Date date : Iterables.unmodifiableIterable(dates)) {
            final double value = values.next();
            validFixing = isValidFixingDate(date);
            // Phase 2p A.2 align: TimeSeries.get(date) returns Double or null
            // for missing keys. Unboxing to primitive double NPEs whenever the
            // date isn't already in the history — which is the common case for
            // a fresh index. Treat null as "missing" (mirrors the singular
            // addFixing's existing handling at line 109).
            final Double currentValue = h.get(date);
            missingFixing = forceOverwrite
                    || currentValue == null
                    || Closeness.isClose(currentValue, Constants.NULL_REAL);
            if (validFixing) {
                if (missingFixing) {
                    h.put(date, value);
                } else if (Closeness.isClose(currentValue, value)) {
                    // Do nothing
                } else {
                    noDuplicatedFixing = false;
                }
            } else {
                noInvalidFixing = false;
            }
		}

		IndexManager.getInstance().setHistory(tag, h);

		// Phase 5c align: see addFixing(date, value, forceOverwrite).
		IndexManager.getInstance().notifier(tag).notifyObservers();

		QL.ensure(noInvalidFixing , "at least one invalid fixing provided");  // TODO: message
		QL.ensure(noDuplicatedFixing , "at least one duplicated fixing provided");  // TODO: message
	}


	/**
	 * Clear the fixings stored for the index
	 */
	public final void clearFixings() {
		IndexManager.getInstance().clearHistory(name());
		// Phase 5c align: notify observers when fixings cleared (mirrors
		// C++ v1.42.1 ql/indexes/indexmanager.cpp:62).
		IndexManager.getInstance().notifier(name()).notifyObservers();
	}

	public double fixing(final Date fixingDate){
        return fixing(fixingDate, false);
    }

    /**
     * Returns whether a historical fixing was stored for the given date.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 inline {@code Index::hasHistoricalFixing(Date)}
     * (ql/index.hpp:125-129) which delegates to
     * {@code IndexManager::instance().hasHistoricalFixing(name(), fixingDate)}.
     * <p>
     * Phase 5e.5b-CFC-d-14: returns {@code true} iff the per-name history
     * stored by {@link IndexManager} contains a non-null fixing for
     * {@code fixingDate}. Cross-checks both {@code containsKey} and the value
     * itself to guard against stored {@code NULL_REAL} sentinels (treated as
     * "missing" — mirrors {@link #addFixing} semantics).
     *
     * @param fixingDate calendar date of the fixing (no settlement days)
     * @return {@code true} iff a real fixing is stored for {@code fixingDate}
     */
    public boolean hasHistoricalFixing(final Date fixingDate) {
        final TimeSeries<Double> h = IndexManager.getInstance().getHistory(name());
        if (h == null) {
            return false;
        }
        final Double v = h.get(fixingDate);
        if (v == null) {
            return false;
        }
        return !Closeness.isClose(v, Constants.NULL_REAL);
    }

    /**
     * Returns the historical fixing at the given date, or
     * {@link Constants#NULL_REAL} if the fixing is not stored.
     * <p>
     * Mirrors C++ QuantLib v1.42.1 inline {@code Index::pastFixing(Date)}
     * (ql/index.hpp:131-134) — used by Black ON-coupon pricers'
     * {@code optionletRateLocal} path to peek at past daily fixings without
     * triggering the throw-on-missing semantics of
     * {@link InterestRateIndex#fixing(Date,boolean)}.
     * <p>
     * Phase 5e.5b-CFC-b: returns {@code NULL_REAL} when the date has no
     * stored fixing (instead of throwing) so callers can branch on
     * "not yet fixed" cleanly.
     *
     * @param fixingDate calendar date of the fixing (no settlement days)
     * @return the stored fixing, or {@code NULL_REAL} if missing
     */
    public double pastFixing(final Date fixingDate) {
        QL.require(isValidFixingDate(fixingDate),
                fixingDate + " is not a valid fixing date");
        final Double v = timeSeries().get(fixingDate);
        return v == null ? Constants.NULL_REAL : v.doubleValue();
    }


	//
	// implements Observable
	//

	/**
	 * Implements multiple inheritance via delegate pattern to an inner class.
	 *
	 * <p>Phase 2x A.4: switched to {@link
	 * org.jquantlib.util.WeakReferenceObservable} — inflation/IBOR coupons
	 * from completed tests would otherwise pile up on the index's
	 * observer list (strong refs) and cascade on every
	 * {@code Settings.setEvaluationDate} call.
	 */
	private final Observable delegatedObservable = new org.jquantlib.util.WeakReferenceObservable(this);

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
	public List<Observer> getObservers() {
		return delegatedObservable.getObservers();
	}

}
