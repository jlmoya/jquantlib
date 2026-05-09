/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/

package org.jquantlib.experimental.catbonds;

import org.jquantlib.time.Date;

/**
 * Immutable pair of (Date, double) used in catastrophe event paths.
 *
 * <p>Java replacement for {@code std::pair<Date, Real>} in the catbonds
 * C++ implementation.
 */
public final class DateRealPair {

    public final Date date;
    public final double value;

    public DateRealPair(final Date date, final double value) {
        this.date = date.clone();
        this.value = value;
    }
}
