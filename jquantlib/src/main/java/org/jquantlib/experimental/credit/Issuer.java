/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2008, 2009 StatPro Italia srl
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.currencies.Currency;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Issuer (credit name) handling: maps default-prob keys to their default-probability term structures and stores the
 * history of past default events affecting this issuer.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::Issuer}
 * ({@code ql/experimental/credit/issuer.{hpp,cpp}}).
 *
 * <p>The C++ {@code DefaultEventSet} is a {@code std::set} ordered by
 * {@code earlier_than}; the Java port uses a {@link TreeSet} with a date-ordering {@link Comparator}.
 *
 * <p>Phase 4m foundation.
 */
public class Issuer {

    /** Comparator that orders DefaultEvents by their date. Mirrors C++ {@code earlier_than}. */
    public static final Comparator< DefaultEvent > EARLIER_THAN = (e1, e2) -> e1.date().compareTo(e2.date());
    private final List< KeyCurvePair > probabilities;
    private final TreeSet< DefaultEvent > events;
    public Issuer() {
        this(new ArrayList<>(), new TreeSet<>(EARLIER_THAN));
    }

    public Issuer(final List< KeyCurvePair > probabilities, final TreeSet< DefaultEvent > events) {
        this.probabilities = new ArrayList<>(probabilities);
        this.events = new TreeSet<>(EARLIER_THAN);
        if ( events != null ) {
            this.events.addAll(events);
        }
    }

    public Issuer(final List< List< DefaultType > > eventTypes, final List< Currency > currencies,
            final List< Seniority > seniorities, final List< Handle< DefaultProbabilityTermStructure > > curves,
            final TreeSet< DefaultEvent > events) {
        this.events = new TreeSet<>(EARLIER_THAN);
        if ( events != null ) {
            this.events.addAll(events);
        }
        QL.require((eventTypes.size() == curves.size()) && (curves.size() == currencies.size()) && (currencies.size()
                == seniorities.size()), "Incompatible size of Issuer parameters.");
        this.probabilities = new ArrayList<>(eventTypes.size());
        for ( int i = 0; i < eventTypes.size(); i++ ) {
            final DefaultProbKey k = new DefaultProbKey(eventTypes.get(i), currencies.get(i), seniorities.get(i));
            this.probabilities.add(new KeyCurvePair(k, curves.get(i)));
        }
    }

    private static boolean between(final DefaultEvent e, final Date start, final Date end,
            final boolean includeRefDate) {
        return !e.hasOccurred(start, includeRefDate) && e.hasOccurred(end, includeRefDate);
    }

    /** Mirrors C++ {@code defaultProbability(const DefaultProbKey&)}. */
    public Handle< DefaultProbabilityTermStructure > defaultProbability(final DefaultProbKey key) {
        for ( final KeyCurvePair p : probabilities ) {
            if ( key.equals(p.key) ) {
                return p.curve;
            }
        }
        throw new LibraryException("Probability curve not available.");
    }

    /**
     * Returns a defaulting event between {@code start} and {@code end} for the given contract key, or {@code null} if
     * none. Mirrors C++ {@code defaultedBetween} (returns empty shared_ptr).
     */
    public DefaultEvent defaultedBetween(final Date start, final Date end, final DefaultProbKey key,
            final boolean includeRefDate) {
        for ( final DefaultEvent event : events ) {
            if ( event.matchesDefaultKey(key) && between(event, start, end, includeRefDate) ) {
                return event;
            }
        }
        return null;
    }

    public DefaultEvent defaultedBetween(final Date start, final Date end, final DefaultProbKey key) {
        return defaultedBetween(start, end, key, false);
    }

    /** Mirrors C++ {@code defaultsBetween}. Returns all matching events. */
    public List< DefaultEvent > defaultsBetween(final Date start, final Date end, final DefaultProbKey contractKey,
            final boolean includeRefDate) {
        final List< DefaultEvent > defaults = new ArrayList<>();
        for ( final DefaultEvent event : events ) {
            if ( event.matchesDefaultKey(contractKey) && between(event, start, end, includeRefDate) ) {
                defaults.add(event);
            }
        }
        return defaults;
    }

    /** Pair of (DefaultProbKey, default-probability handle). */
    public static class KeyCurvePair {
        public final DefaultProbKey key;
        public final Handle< DefaultProbabilityTermStructure > curve;

        public KeyCurvePair(final DefaultProbKey key, final Handle< DefaultProbabilityTermStructure > curve) {
            this.key = key;
            this.curve = curve;
        }
    }
}
