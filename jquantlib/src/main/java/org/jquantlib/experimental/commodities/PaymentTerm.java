/*
 Copyright (C) 2026 JQuantLib

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 J. Erik Radmall
*/

package org.jquantlib.experimental.commodities;

import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

import java.util.HashMap;
import java.util.Map;

/**
 * Payment term: an offset (in days, on a calendar) past either trade date or pricing date.
 * <p>
 * Java port of QuantLib v1.42.1 {@code paymentterm.{hpp,cpp}}.
 */
public class PaymentTerm {

    /** Shared registry by name, mirroring the C++ static map. */
    private static final Map< String, Data > paymentTerms_ = new HashMap<>();
    /** Pimpl-style data; null when this instance is empty. */
    protected Data data_;

    public PaymentTerm() {
        // empty
    }

    public PaymentTerm(final String name, final EventType eventType, final int offsetDays, final Calendar calendar) {
        final Data existing = paymentTerms_.get(name);
        if ( existing != null ) {
            this.data_ = existing;
        } else {
            this.data_ = new Data(name, eventType, offsetDays, calendar);
            paymentTerms_.put(name, this.data_);
        }
    }

    public final String name() {
        return data_.name;
    }

    public final EventType eventType() {
        return data_.eventType;
    }

    public final int offsetDays() {
        return data_.offsetDays;
    }

    public final Calendar calendar() {
        return data_.calendar;
    }

    public final boolean empty() {
        return data_ == null;
    }

    public final Date getPaymentDate(final Date date) {
        return data_.calendar.adjust(date.add(data_.offsetDays));
    }

    @Override
    public boolean equals(final Object obj) {
        if ( this == obj )
            return true;
        if (!(obj instanceof PaymentTerm paymentTerm))
            return false;
        final PaymentTerm other = paymentTerm;
        if ( this.empty() || other.empty() )
            return this.empty() == other.empty();
        return this.name().equals(other.name());
    }

    @Override
    public int hashCode() {
        return empty() ? 0 : name().hashCode();
    }

    @Override
    public String toString() {
        return empty() ? "null payment term type" : name();
    }

    public enum EventType {
        TradeDate, PricingDate
    }

    /** Pimpl data record. */
    protected static final class Data {
        final String name;
        final EventType eventType;
        final int offsetDays;
        final Calendar calendar;

        Data(final String name, final EventType eventType, final int offsetDays, final Calendar calendar) {
            this.name = name;
            this.eventType = eventType;
            this.offsetDays = offsetDays;
            this.calendar = calendar;
        }
    }
}
