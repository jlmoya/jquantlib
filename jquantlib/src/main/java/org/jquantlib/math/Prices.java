/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006 Katiuscia Manzoni
 Copyright (C) 2006 Joseph Wang

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.math;

import org.jquantlib.QL;

/**
 * Price helper free functions ported from C++ QuantLib v1.42.1 {@code ql/prices.hpp} + {@code ql/prices.cpp}.
 *
 * <p>Provides {@link #midEquivalent(double, double, double, double)} and
 * {@link #midSafe(double, double)}, plus the {@link PriceType} enum.
 *
 * <p>C++ uses {@code Null<Real>()} as the sentinel for "missing" prices.
 * Following the C++ implementation literally, a price is treated as valid iff it is not {@link Constants#NULL_REAL}
 * <em>and</em> strictly positive. Callers may therefore pass {@code 0.0} to indicate "not available", which is the
 * convention used by the C++ test-suite ({@code test-suite/prices.cpp}).
 */
public final class Prices {

    private Prices() {
        // utility class — no instances
    }

    private static boolean isValid(final double price) {
        return price != Constants.NULL_REAL && price > 0.0;
    }

    /**
     * Return the MidEquivalent price: the mid if available, or a suitable substitute if the proper mid is not
     * available.
     * <p>
     * Java port of {@code QuantLib::midEquivalent} in {@code ql/prices.cpp}.
     *
     * @param bid   bid price (use {@code 0.0} or {@link Constants#NULL_REAL} for missing)
     * @param ask   ask price (use {@code 0.0} or {@link Constants#NULL_REAL} for missing)
     * @param last  last price (use {@code 0.0} or {@link Constants#NULL_REAL} for missing)
     * @param close close price (use {@code 0.0} or {@link Constants#NULL_REAL} for missing)
     * @return the mid-equivalent price
     * @throws RuntimeException if all four input prices are invalid
     */
    public static double midEquivalent(final double bid, final double ask, final double last, final double close) {
        if ( isValid(bid) ) {
            if ( isValid(ask) ) {
                return (bid + ask) / 2.0;
            } else {
                return bid;
            }
        } else {
            if ( isValid(ask) ) {
                return ask;
            } else if ( isValid(last) ) {
                return last;
            } else {
                QL.require(isValid(close), "all input prices are invalid");
                return close;
            }
        }
    }

    /**
     * Return the MidSafe price: the mid only if both bid and ask prices are available.
     * <p>
     * Java port of {@code QuantLib::midSafe} in {@code ql/prices.cpp}.
     *
     * @param bid bid price (must be valid)
     * @param ask ask price (must be valid)
     * @return {@code (bid + ask) / 2.0}
     * @throws RuntimeException if either bid or ask is invalid
     */
    public static double midSafe(final double bid, final double ask) {
        QL.require(isValid(bid), "invalid bid price");
        QL.require(isValid(ask), "invalid ask price");
        return (bid + ask) / 2.0;
    }

    /**
     * Price types — mirror of {@code QuantLib::PriceType} in {@code ql/prices.hpp}.
     */
    public enum PriceType {
        /** Bid price. */
        Bid,
        /** Ask price. */
        Ask,
        /** Last price. */
        Last,
        /** Close price. */
        Close,
        /** Mid price, arithmetic average of bid and ask. */
        Mid,
        /**
         * Mid equivalent price: arithmetic average of bid and ask when both are available; either bid or ask if only
         * one is available; else last; else close.
         */
        MidEquivalent,
        /** Safe Mid price: mid only if both bid and ask are available. */
        MidSafe
    }
}
