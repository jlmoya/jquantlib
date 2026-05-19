/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.experimental.catbonds;

import java.util.List;

/**
 * Notional risk model that sets the notional to zero whenever a loss event exceeds a threshold.
 *
 * <p>Port of {@code ql/experimental/catbonds/riskynotional.hpp}
 * {@code DigitalNotionalRisk}.
 */
public class DigitalNotionalRisk extends NotionalRisk {

    private final double threshold_;

    public DigitalNotionalRisk(final EventPaymentOffset paymentOffset, final double threshold) {
        super(paymentOffset);
        this.threshold_ = threshold;
    }

    @Override
    public void updatePath(final List< DateRealPair > events, final NotionalPath path) {
        path.reset();
        for ( final DateRealPair event : events ) {
            if ( event.value >= threshold_ ) {
                path.addReduction(paymentOffset_.paymentDate(event.date), 0.0);
            }
        }
    }
}
