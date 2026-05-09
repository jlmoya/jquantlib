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
import org.jquantlib.time.Date;

/**
 * Abstract base class for catastrophe simulations.
 *
 * <p>Port of {@code ql/experimental/catbonds/catrisk.hpp} {@code CatSimulation}.
 */
public abstract class CatSimulation {

    protected final Date start_;
    protected final Date end_;

    protected CatSimulation(final Date start, final Date end) {
        this.start_ = start.clone();
        this.end_ = end.clone();
    }

    /**
     * Fills {@code path} with the next simulated catastrophe event sequence
     * and returns {@code true}, or returns {@code false} when no more paths
     * are available.
     */
    public abstract boolean nextPath(List<DateRealPair> path);
}
