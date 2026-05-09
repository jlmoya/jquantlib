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

/**
 * Event payment offset that returns the event date unchanged.
 *
 * <p>Port of {@code ql/experimental/catbonds/riskynotional.hpp} {@code NoOffset}.
 */
public class NoOffset implements EventPaymentOffset {

    @Override
    public Date paymentDate(final Date eventDate) {
        return eventDate;
    }
}
