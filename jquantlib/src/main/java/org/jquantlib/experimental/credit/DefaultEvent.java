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
import org.jquantlib.Settings;
import org.jquantlib.cashflow.Event;
import org.jquantlib.currencies.Currency;
import org.jquantlib.math.Constants;
import org.jquantlib.time.Date;

import java.util.EnumMap;
import java.util.Map;

/**
 * Credit event on a bond of a certain seniority(ies) / currency.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::DefaultEvent}
 * ({@code ql/experimental/credit/defaultevent.{hpp,cpp}}).
 *
 * <p>Represents a credit event affecting all bonds with a given
 * seniority and currency. The event is an actual realisation, not a contractual reference; as such it contains only an
 * atomic type.
 *
 * <p>Two events are equal independently of their settlement member data
 * (mirrors C++ {@code operator==}).
 *
 * <p>Phase 4m foundation.
 */
public class DefaultEvent extends Event {

    protected Currency bondsCurrency;
    protected Date defaultDate;
    protected DefaultType eventType;
    protected Seniority bondsSeniority;
    protected DefaultSettlement defSettlement;
    public DefaultEvent(final Date creditEventDate, final DefaultType atomicEvType, final Currency curr,
            final Seniority bondsSen, final Date settleDate, final Map< Seniority, Double > recoveryRates) {
        this.bondsCurrency = curr;
        this.defaultDate = creditEventDate;
        this.eventType = atomicEvType;
        this.bondsSeniority = bondsSen;
        final Map< Seniority, Double > effectiveMap = (recoveryRates == null || recoveryRates.isEmpty())
                ? RecoveryRateQuote.makeIsdaConvMap()
                : recoveryRates;
        this.defSettlement = new DefaultSettlement(settleDate, effectiveMap);
        if ( settleDate != null && !settleDate.equals(new Date()) ) {
            QL.require(settleDate.compareTo(creditEventDate) >= 0, "Settlement date should be after default date.");
            QL.require(recoveryRates != null && recoveryRates.containsKey(bondsSen),
                    "Settled events must contain the seniority of the default");
        }
    }

    public DefaultEvent(final Date creditEventDate, final DefaultType atomicEvType, final Currency curr,
            final Seniority bondsSen, final Date settleDate, final double recoveryRate) {
        this.bondsCurrency = curr;
        this.defaultDate = creditEventDate;
        this.eventType = atomicEvType;
        this.bondsSeniority = bondsSen;
        this.defSettlement = new DefaultSettlement(settleDate, bondsSen, recoveryRate);
        if ( settleDate != null && !settleDate.equals(new Date()) ) {
            QL.require(settleDate.compareTo(creditEventDate) >= 0, "Settlement date should be after default date.");
        }
    }

    /** Settings reference, used by FailureToPayEvent matching. */
    protected static Date evaluationDate() {
        return new Settings().evaluationDate();
    }

    @Override
    public Date date() {
        return defaultDate;
    }

    public boolean isRestructuring() {
        return eventType.isRestructuring();
    }

    public boolean isDefault() {
        return !isRestructuring();
    }

    public boolean hasSettled() {
        return !defSettlement.date().equals(new Date());
    }

    public DefaultSettlement settlement() {
        return defSettlement;
    }

    public DefaultType defaultType() {
        return eventType;
    }

    public Currency currency() {
        return bondsCurrency;
    }

    public Seniority eventSeniority() {
        return bondsSeniority;
    }

    /**
     * Returns the recovery rate if the event lead to a settlement for the requested seniority;
     * {@link Constants#NULL_REAL} otherwise.
     */
    public double recoveryRate(final Seniority seniority) {
        if ( hasSettled() ) {
            return defSettlement.recoveryRate(seniority);
        }
        return Constants.NULL_REAL;
    }

    /**
     * Returns true if this event would trigger a contract whose {@code contractEvType} matches the underlying atomic +
     * restructuring type. Mirrors C++ {@code matchesEventType}.
     */
    public boolean matchesEventType(final DefaultType contractEvType) {
        return contractEvType.containsRestructuringType(eventType.restructuringType())
                && contractEvType.containsDefaultType(eventType.defaultType());
    }

    /**
     * Returns true if this event would trigger a contract with the arguments characteristics. Mirrors C++
     * {@code matchesDefaultKey}.
     */
    public boolean matchesDefaultKey(final DefaultProbKey contractKey) {
        if ( !bondsCurrency.equals(contractKey.currency()) ) {
            return false;
        }
        // a contract with NoSeniority matches all events
        if ( bondsSeniority != contractKey.seniority() && contractKey.seniority() != Seniority.NoSeniority ) {
            return false;
        }
        for ( final DefaultType t : contractKey.eventTypes() ) {
            if ( this.matchesEventType(t) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mirrors C++ free {@code operator==(const DefaultEvent&, const DefaultEvent&)}: equal independently of
     * settlement.
     */
    @Override
    public boolean equals(final Object o) {
        if ( this == o ) {
            return true;
        }
        if (!(o instanceof DefaultEvent rhs)) {
            return false;
        }
        return bondsCurrency.equals(rhs.bondsCurrency) && eventType.equals(rhs.eventType) && defaultDate.equals(
                rhs.defaultDate) && bondsSeniority == rhs.bondsSeniority;
    }

    @Override
    public int hashCode() {
        int h = bondsCurrency.hashCode();
        h = 31 * h + eventType.hashCode();
        h = 31 * h + defaultDate.hashCode();
        h = 31 * h + bondsSeniority.hashCode();
        return h;
    }

    /** Default-settlement event: settlement date + per-seniority recovery. */
    public static class DefaultSettlement extends Event {
        private final Date settlementDate;
        private final Map< Seniority, Double > recoveryRates;

        public DefaultSettlement(final Date date, final Map< Seniority, Double > recoveryRates) {
            this.settlementDate = date;
            this.recoveryRates = new EnumMap<>(Seniority.class);
            this.recoveryRates.putAll(recoveryRates);
            QL.require(!recoveryRates.containsKey(Seniority.NoSeniority),
                    "NoSeniority is not a valid realized seniority.");
        }

        public DefaultSettlement() {
            this(new Date(), Seniority.NoSeniority, 0.4);
        }

        public DefaultSettlement(final Date date) {
            this(date, Seniority.NoSeniority, 0.4);
        }

        public DefaultSettlement(final Date date, final Seniority seniority, final double recoveryRate) {
            this.settlementDate = date;
            this.recoveryRates = RecoveryRateQuote.makeIsdaConvMap();
            if ( seniority == Seniority.NoSeniority ) {
                for ( final Map.Entry< Seniority, Double > e : recoveryRates.entrySet() ) {
                    e.setValue(recoveryRate);
                }
            } else {
                recoveryRates.put(seniority, recoveryRate);
            }
        }

        @Override
        public Date date() {
            return settlementDate;
        }

        /**
         * Returns the recovery rate of a default event which has already settled. Returns NULL_REAL if the seniority is
         * not present.
         */
        public double recoveryRate(final Seniority sen) {
            QL.require(sen != Seniority.NoSeniority, "NoSeniority is not valid for recovery rate request.");
            final Double r = recoveryRates.get(sen);
            return r == null ? Constants.NULL_REAL : r;
        }
    }
}
