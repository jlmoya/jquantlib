/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2015 Ferdinando Ametrano
 Copyright (C) 2015 Maddalena Zanzi

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.instruments;

/**
 * Futures-type enumeration.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/instruments/futures.hpp} {@code Futures::Type}.
 * These conventions specify the kind of futures type used by FuturesRateHelper
 * and related bootstrap helpers.
 *
 * <ul>
 *   <li>{@link Type#IMM}: Chicago Mercantile International Money Market —
 *       third Wednesday of March, June, September, December;</li>
 *   <li>{@link Type#ASX}: Australian Security Exchange — second Friday
 *       of March, June, September, December;</li>
 *   <li>{@link Type#Custom}: other rules (caller-supplied dates).</li>
 * </ul>
 */
public final class Futures {

    private Futures() {
    }

    public enum Type {
        IMM, ASX, Custom
    }
}
