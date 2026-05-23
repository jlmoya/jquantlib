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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.Settings;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.time.Date;

/**
 * Rate helper with date schedule relative to the global evaluation date
 *
 * <p>
 * This class takes care of rebuilding the date schedule when the global evaluation date changes
 *
 * @author Srinivas Hasti
 */
// TODO: code review :: license, class comments, comments for access modifiers, comments for @Override
public abstract class RelativeDateRateHelper extends RateHelper {

    //
    // protected fields
    //

    protected Date evaluationDate;

    /**
     * Mirrors C++ v1.42.1 {@code RelativeDateRateHelper::updateDates_} (ratehelpers.hpp:47).
     * When {@code false}, the helper uses caller-supplied absolute dates and skips
     * recomputation on each {@link #update()} call. Default {@code true} preserves
     * the historical Java behavior (recompute on evaluation-date change).
     */
    protected boolean updateDates = true;

    //
    // public constructors
    //

    public RelativeDateRateHelper(/*@Real*/ final double d) {
        this(d, true);
    }

    public RelativeDateRateHelper(final Handle< Quote > quote) {
        this(quote, true);
    }

    /**
     * Mirrors C++ v1.42.1 {@code RelativeDateRateHelper(rate, updateDates)} overload
     * (ratehelpers.hpp:42). {@code updateDates=false} is used by date-based helper
     * ctors (e.g. {@code DepositRateHelper(quote, fixingDate, ibor)}) so that
     * {@link #update()} does not overwrite the caller-supplied absolute date.
     */
    public RelativeDateRateHelper(/*@Real*/ final double d, final boolean updateDates) {
        super(d);
        this.updateDates = updateDates;
        // Register as observer of the live evaluation-date proxy, but cache a
        // *value* snapshot via clone() so the update() guard does not alias the
        // proxy. Without the clone the cached reference would mutate in
        // lock-step with Settings.setEvaluationDate(), silently skipping
        // re-initializeDates() — see C++ v1.42.1 bootstraphelper.hpp:215-218.
        final Date live = new Settings().evaluationDate();
        live.addObserver(this);
        this.evaluationDate = live.clone();
    }

    public RelativeDateRateHelper(final Handle< Quote > quote, final boolean updateDates) {
        super(quote);
        this.updateDates = updateDates;
        // See ctor (double, boolean) note: clone-snapshot for proxy aliasing.
        final Date live = new Settings().evaluationDate();
        live.addObserver(this);
        this.evaluationDate = live.clone();
    }

    //
    // protected abstract methods
    //

    protected abstract void initializeDates();

    //
    // overrides RateHelper
    //

    @Override
    public void update() {
        // Mirrors C++ v1.42.1 RelativeDateRateHelper::update() (ratehelpers.cpp).
        // When updateDates_ is false (date-based ctors), absolute caller-supplied
        // dates must not be overwritten on evaluation-date change.
        if ( this.updateDates ) {
            final Date newEvaluationDate = new Settings().evaluationDate();
            if ( !evaluationDate.equals(newEvaluationDate) ) {
                // Take a value snapshot via clone — re-assigning the live proxy
                // here would silently break the next-update guard.
                evaluationDate = newEvaluationDate.clone();
                initializeDates();
            }
        }
        super.update();
    }

}
