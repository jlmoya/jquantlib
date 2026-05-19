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

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;

/**
 * Date interval defined by two dates.
 * <p>
 * Java port of QuantLib v1.42.1 {@code dateinterval.{hpp,cpp}}.
 */
public class DateInterval {

    protected Date startDate_;
    protected Date endDate_;

    public DateInterval() {
        this.startDate_ = new Date();
        this.endDate_ = new Date();
    }

    public DateInterval(final Date startDate, final Date endDate) {
        if ( endDate.lt(startDate) ) {
            throw new LibraryException("end date must be >= start date");
        }
        this.startDate_ = startDate;
        this.endDate_ = endDate;
    }

    public Date startDate() {
        return startDate_;
    }

    public Date endDate() {
        return endDate_;
    }

    public boolean isDateBetween(final Date date) {
        return isDateBetween(date, true, true);
    }

    public boolean isDateBetween(final Date date, final boolean includeFirst, final boolean includeLast) {
        // Mirrors C++ semantics from dateinterval.hpp
        if ( includeFirst && !date.ge(startDate_) )
            return false;
        else if ( !date.gt(startDate_) )
            return false;
        if ( includeLast && !date.le(endDate_) )
            return false;
        else
            return date.lt(endDate_);
    }

    public DateInterval intersection(final DateInterval di) {
        if ( (startDate_.lt(di.startDate_) && endDate_.lt(di.startDate_)) || (startDate_.gt(di.endDate_) && endDate_.gt(
                di.endDate_)) ) {
            return new DateInterval();
        }
        final Date start = startDate_.gt(di.startDate_) ? startDate_ : di.startDate_;
        final Date end = endDate_.lt(di.endDate_) ? endDate_ : di.endDate_;
        return new DateInterval(start, end);
    }

    @Override
    public boolean equals(final Object obj) {
        if ( this == obj )
            return true;
        if ( !(obj instanceof DateInterval) )
            return false;
        final DateInterval other = (DateInterval) obj;
        return startDate_.equals(other.startDate_) && endDate_.equals(other.endDate_);
    }

    @Override
    public int hashCode() {
        return 31 * startDate_.hashCode() + endDate_.hashCode();
    }

    @Override
    public String toString() {
        if ( startDate_.equals(new Date()) || endDate_.equals(new Date()) ) {
            return "Null<DateInterval>()";
        }
        return startDate_ + " to " + endDate_;
    }
}
