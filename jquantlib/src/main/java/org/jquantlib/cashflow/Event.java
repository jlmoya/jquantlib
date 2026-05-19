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

package org.jquantlib.cashflow;

import org.jquantlib.Settings;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;
import org.jquantlib.util.*;

import java.util.List;

/**
 * This class is the base class for all financial events.
 *
 * @author Richard Gomes
 */
public abstract class Event implements Observable, PolymorphicVisitable {

    //
    // protected constructors
    //

    /**
     * Implements multiple inheritance via delegate pattern to an inner class.
     *
     * <p>Phase 2x A.4: switched to {@link
     * org.jquantlib.util.WeakReferenceObservable} so that observers from completed tests don't accumulate on the
     * cash-flow event's observer list and cascade on every Settings.setEvaluationDate.
     *
     * @see Observable
     * @see DefaultObservable
     */
    private final DefaultObservable delegatedObservable = new org.jquantlib.util.WeakReferenceObservable(this);

    //
    // public abstract methods
    //

    protected Event() {
        // only descendent classes can instantiate
    }

    //
    // public methods
    //

    /**
     * Returns true if an event has already occurred before a date where the
     * current date may or may not be considered accordingly to defaults taken
     * from {@link Settings}
     *
     * @param d is a Date
     * @return true if an event has already occurred before a date
     *
     * @see Settings.todaysPayments
     * @see todaysPayments
     */

    /**
     * Keeps the date at which the event occurs
     */
    public abstract Date date() /* @ReadOnly */;

    /**
     * Returns true if an event has already occurred before a date
     * <p>
     * If {@link Settings#isTodaysPayments()} is true, then a payment event has not occurred if the input date is the
     * same as the event date, and so includeToday should be defaulted to true.
     * <p>
     * This should be the only place in the code that is affected directly by {@link Settings#isTodaysPayments()}
     */
    public boolean hasOccurred(final Date d) /* @ReadOnly */ {
        return hasOccurred(d, new Settings().isTodaysPayments());
    }

    /**
     * Returns true if an event has already occurred before a date where it is explicitly defined whether the current
     * date must considered.
     *
     * @param d is a Date
     * @return true if an event has already occurred before a date
     */
    public boolean hasOccurred(final Date d, final boolean includeToday) /* @ReadOnly */ {
        if ( includeToday ) {
            return date().compareTo(d) < 0;
        } else {
            return date().compareTo(d) <= 0;
        }
    }

    //
    // implements Observable
    //

    /**
     * C++-aligned overload mirroring {@code Event::hasOccurred(refDate, ext::optional<bool> includeRefDate)} (event.cpp
     * v1.42.1 lines 28-39). Java's nullable {@link Boolean} corresponds to {@code ext::optional<bool>}: when
     * {@code null}, the {@link Settings#includeReferenceDateEvents()} flag is consulted; when non-null, the parameter
     * wins.
     *
     * <p>If {@code refDate} is {@code null} or the null-date sentinel, the
     * current evaluation date is used (matching C++ {@code d != Date() ? d : Settings::instance().evaluationDate()}).
     */
    public boolean hasOccurred(final Date refDate, final Boolean includeRefDate) /* @ReadOnly */ {
        final Settings settings = new Settings();
        final Date d = (refDate == null || refDate.isNull()) ? settings.evaluationDate() : refDate;
        final boolean includeRefDateEvent =
                includeRefDate != null ? includeRefDate.booleanValue() : settings.includeReferenceDateEvents();
        if ( includeRefDateEvent ) {
            return date().compareTo(d) < 0;
        } else {
            return date().compareTo(d) <= 0;
        }
    }

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
    public List< Observer > getObservers() {
        return delegatedObservable.getObservers();
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< Event > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            throw new LibraryException("null event visitor"); // TODO: message
        }
    }

}
