/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.experimental.catbonds;

import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

import java.util.List;

/**
 * Simulation based on a historical event set.
 *
 * <p>Port of {@code ql/experimental/catbonds/catrisk.hpp/.cpp}
 * {@code EventSetSimulation}.
 */
public class EventSetSimulation extends CatSimulation {

    private final List< DateRealPair > events_;
    private final Date eventsStart_;
    private final Date eventsEnd_;

    private final int years_;         // end_.year() - start_.year()
    private Date periodStart_;
    private Date periodEnd_;
    private int i_;                   // current pointer into events_

    public EventSetSimulation(final List< DateRealPair > events, final Date eventsStart, final Date eventsEnd,
            final Date start, final Date end) {

        super(start, end);
        this.events_ = events;
        this.eventsStart_ = eventsStart.clone();
        this.eventsEnd_ = eventsEnd.clone();

        years_ = end_.year() - start_.year();

        // Determine the first period start within the events data
        if ( eventsStart_.month().value() < start_.month().value() || (
                eventsStart_.month().value() == start_.month().value()
                        && eventsStart_.dayOfMonth() <= start_.dayOfMonth()) ) {
            periodStart_ = new Date(start_.dayOfMonth(), start_.month(), eventsStart_.year());
        } else {
            periodStart_ = new Date(start_.dayOfMonth(), start_.month(), eventsStart_.year() + 1);
        }

        periodEnd_ = new Date(end_.dayOfMonth(), end_.month(), periodStart_.year() + years_);

        // Advance i_ to the first event at or after periodStart_
        i_ = 0;
        while ( i_ < events_.size() && events_.get(i_).date.lt(periodStart_) ) {
            i_++;
        }
    }

    @Override
    public boolean nextPath(final List< DateRealPair > path) {
        path.clear();

        // Ran out of event data
        if ( periodEnd_.gt(eventsEnd_) ) {
            return false;
        }

        // Skip events between previous and current period
        while ( i_ < events_.size() && events_.get(i_).date.lt(periodStart_) ) {
            i_++;
        }

        // Collect events within the current period, adjusting year
        final int yearShift = start_.year() - periodStart_.year();
        while ( i_ < events_.size() && !events_.get(i_).date.gt(periodEnd_) ) {
            final DateRealPair orig = events_.get(i_);
            // Translate the event date into the simulation period's year
            final Date adjustedDate = orig.date.add(new Period(yearShift, TimeUnit.Years));
            path.add(new DateRealPair(adjustedDate, orig.value));
            i_++;
        }

        // Advance to the next period
        if ( start_.add(new Period(years_, TimeUnit.Years)).lt(end_) ) {
            periodStart_ = periodStart_.add(new Period(years_ + 1, TimeUnit.Years));
            periodEnd_ = periodEnd_.add(new Period(years_ + 1, TimeUnit.Years));
        } else {
            periodStart_ = periodStart_.add(new Period(years_, TimeUnit.Years));
            periodEnd_ = periodEnd_.add(new Period(years_, TimeUnit.Years));
        }

        return true;
    }
}
