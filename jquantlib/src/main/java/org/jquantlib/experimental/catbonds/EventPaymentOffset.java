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
 * Interface that maps a catastrophe event date to the date on which the associated payment is made.
 *
 * <p>Port of {@code ql/experimental/catbonds/riskynotional.hpp}
 * {@code EventPaymentOffset}.
 */
public interface EventPaymentOffset {

    Date paymentDate(Date eventDate);
}
