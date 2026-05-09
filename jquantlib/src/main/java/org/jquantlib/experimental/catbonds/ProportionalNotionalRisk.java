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
import org.jquantlib.QL;

/**
 * Notional risk model that proportionally reduces the notional once cumulative
 * losses exceed an attachment point, reaching zero at an exhaustion point.
 *
 * <p>Port of {@code ql/experimental/catbonds/riskynotional.hpp}
 * {@code ProportionalNotionalRisk}.
 */
public class ProportionalNotionalRisk extends NotionalRisk {

    private final double attachement_;
    private final double exhaustion_;

    public ProportionalNotionalRisk(
            final EventPaymentOffset paymentOffset,
            final double attachement,
            final double exhaustion) {

        super(paymentOffset);
        QL.require(attachement < exhaustion,
                "exhaustion level needs to be greater than attachement");
        this.attachement_ = attachement;
        this.exhaustion_  = exhaustion;
    }

    @Override
    public void updatePath(final List<DateRealPair> events, final NotionalPath path) {
        path.reset();
        double losses = 0.0;
        double previousNotional = 1.0;
        for (final DateRealPair event : events) {
            losses += event.value;
            if (losses > attachement_ && previousNotional > 0.0) {
                previousNotional = Math.max(0.0,
                        (exhaustion_ - losses) / (exhaustion_ - attachement_));
                path.addReduction(paymentOffset_.paymentDate(event.date), previousNotional);
            }
        }
    }
}
