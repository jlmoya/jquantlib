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
 * Abstract base for notional-risk models that translate a catastrophe-event path into a notional-reduction path.
 *
 * <p>Port of {@code ql/experimental/catbonds/riskynotional.hpp}
 * {@code NotionalRisk}.
 */
public abstract class NotionalRisk {

    protected final EventPaymentOffset paymentOffset_;

    protected NotionalRisk(final EventPaymentOffset paymentOffset) {
        this.paymentOffset_ = paymentOffset;
    }

    /**
     * Updates {@code path} to reflect the notional reductions induced by the given catastrophe events.
     */
    public abstract void updatePath(List< DateRealPair > events, NotionalPath path);
}
