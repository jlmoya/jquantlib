/*
 Copyright (C) 2026 JQuantLib migration

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

/*
 Copyright (C) 2008 Roland Lichters
*/

package org.jquantlib.experimental.credit;

/**
 * Pair of loss time and amount, sortable by loss time.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::Loss}
 * ({@code ql/experimental/credit/loss.hpp}). The C++ class is a tiny POD with two public fields and free comparison
 * operators that order by {@code time} only. The Java port keeps the public fields, adds a {@code compareTo}-style
 * ordering helper, and equality / hash on {@code time} alone (matching C++ {@code operator==}).
 *
 * <p>Phase 4m foundation.
 */
public final class Loss implements Comparable< Loss > {

    public double time;
    public double amount;

    public Loss() {
        this(0.0, 0.0);
    }

    public Loss(final double time, final double amount) {
        this.time = time;
        this.amount = amount;
    }

    /** Mirrors C++ {@code operator<} on {@code time} only. */
    @Override
    public int compareTo(final Loss other) {
        return Double.compare(this.time, other.time);
    }

    /** Mirrors C++ {@code operator==} on {@code time} only. */
    @Override
    public boolean equals(final Object o) {
        if ( this == o ) {
            return true;
        }
        if ( !(o instanceof Loss) ) {
            return false;
        }
        final Loss other = (Loss) o;
        return Double.compare(this.time, other.time) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(time);
    }
}
