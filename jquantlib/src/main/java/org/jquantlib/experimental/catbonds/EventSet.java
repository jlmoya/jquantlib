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

import java.util.List;

/**
 * Catastrophe risk based on a historical event set.
 *
 * <p>Port of {@code ql/experimental/catbonds/catrisk.hpp/.cpp} {@code EventSet}.
 */
public class EventSet extends CatRisk {

    private final List< DateRealPair > events_;
    private final Date eventsStart_;
    private final Date eventsEnd_;

    public EventSet(final List< DateRealPair > events, final Date eventsStart, final Date eventsEnd) {
        this.events_ = events;
        this.eventsStart_ = eventsStart.clone();
        this.eventsEnd_ = eventsEnd.clone();
    }

    @Override
    public CatSimulation newSimulation(final Date start, final Date end) {
        return new EventSetSimulation(events_, eventsStart_, eventsEnd_, start, end);
    }
}
