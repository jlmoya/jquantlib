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
 Copyright (C) 2009 StatPro Italia srl
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.currencies.Currency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Used to index market-implied credit-curve probabilities.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::DefaultProbKey}
 * ({@code ql/experimental/credit/defaultprobabilitykey.{hpp,cpp}}).
 *
 * <p>Acts as a proxy to the defaultable bond (or class of bonds) which
 * determines the credit-contract conditions. Aggregates atomic default types in a group defining the contract
 * conditions and indexes the probability curves calibrated to the market.
 *
 * <p>Phase 4m foundation.
 */
public class DefaultProbKey {

    /** Aggregation of event types for which the contract is sensitive. */
    protected List< DefaultType > eventTypes;
    /** Currency of the bond and protection-leg payment. */
    protected Currency obligationCurrency;
    /** Reference bond seniority. */
    protected Seniority seniorityField;

    public DefaultProbKey() {
        this.eventTypes = new ArrayList<>();
        this.obligationCurrency = new Currency();
        this.seniorityField = Seniority.NoSeniority;
    }

    public DefaultProbKey(final List< DefaultType > eventTypes, final Currency cur, final Seniority sen) {
        this.eventTypes = new ArrayList<>(eventTypes);
        this.obligationCurrency = cur;
        this.seniorityField = sen;
        // Forbid duplicated atomic types.
        final EnumSet< AtomicDefault.Type > buffer = EnumSet.noneOf(AtomicDefault.Type.class);
        for ( final DefaultType t : this.eventTypes ) {
            buffer.add(t.defaultType());
        }
        QL.require(buffer.size() == this.eventTypes.size(), "Duplicated event type in contract definition");
    }

    public Currency currency() {
        return obligationCurrency;
    }

    public Seniority seniority() {
        return seniorityField;
    }

    public List< DefaultType > eventTypes() {
        return Collections.unmodifiableList(eventTypes);
    }

    public int size() {
        return eventTypes.size();
    }

    /**
     * Mirrors C++ free {@code operator==(const DefaultProbKey&, const DefaultProbKey&)}. Equal iff seniority + currency
     * + same set of event types.
     */
    @Override
    public boolean equals(final Object o) {
        if ( this == o ) {
            return true;
        }
        if ( !(o instanceof DefaultProbKey) ) {
            return false;
        }
        final DefaultProbKey rhs = (DefaultProbKey) o;
        if ( this.seniorityField != rhs.seniorityField ) {
            return false;
        }
        if ( !this.obligationCurrency.equals(rhs.obligationCurrency) ) {
            return false;
        }
        if ( this.eventTypes.size() != rhs.eventTypes.size() ) {
            return false;
        }
        // Each rhs event must be findable in lhs (set equality).
        for ( final DefaultType rhEvent : rhs.eventTypes ) {
            boolean found = false;
            for ( final DefaultType lhEvent : this.eventTypes ) {
                if ( lhEvent.equals(rhEvent) ) {
                    found = true;
                    break;
                }
            }
            if ( !found ) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = (seniorityField != null ? seniorityField.hashCode() : 0);
        h = 31 * h + (obligationCurrency != null ? obligationCurrency.hashCode() : 0);
        // event-types: order-independent hash, mirroring set semantics.
        int eventsHash = 0;
        for ( final DefaultType t : eventTypes ) {
            eventsHash += t.hashCode();
        }
        return 31 * h + eventsHash;
    }
}
