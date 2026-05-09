/*
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2009, 2014 Jose Aparicio
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.experimental.credit;

/**
 * Compact default-event record used by {@link RandomDefaultLM} to store a
 * Monte-Carlo simulation outcome (one event per defaulting name in one path).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code simEvent<RandomDefaultLM<copulaPolicy, USNG> >} (declared in
 * {@code ql/experimental/credit/randomdefaultlatentmodel.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ struct uses 16-bit bitfields ({@code unsigned int : 16}) for
 * memory packing; in Java we use {@code int} for both {@code nameIdx} and
 * {@code dayFromRef} since the JVM does not support packed bitfields and
 * {@code short} would require sign-extension juggling for indices > 32k.
 * Memory footprint is ~2x C++ (16 bytes vs 4) which is acceptable given
 * Java object overhead already dwarfs the difference.
 *
 * <p>Implements {@code Comparable} so that {@link java.util.Collections#sort}
 * orders events by {@code dayFromRef} (ascending), matching C++
 * {@code operator<} on the inner struct.
 */
public final class DefaultSimEvent implements Comparable<DefaultSimEvent> {

    /** Index of the defaulting name within the basket's pool ordering. */
    public final int nameIdx;
    /** Days from the curve reference date to the simulated default date. */
    public final int dayFromRef;

    public DefaultSimEvent(final int nameIdx, final int dayFromRef) {
        this.nameIdx = nameIdx;
        this.dayFromRef = dayFromRef;
    }

    @Override
    public int compareTo(final DefaultSimEvent other) {
        return Integer.compare(this.dayFromRef, other.dayFromRef);
    }

    @Override
    public String toString() {
        return "DefaultSimEvent[name=" + nameIdx + ", day=" + dayFromRef + "]";
    }
}
