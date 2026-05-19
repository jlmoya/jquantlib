/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2005 StatPro Italia srl

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

package org.jquantlib.termstructures;

/**
 * Pillar-date choice for a rate helper.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/termstructures/yield/ratehelpers.hpp} {@code Pillar::Choice}. Controls which
 * date is used as the curve pillar (anchor) when bootstrapping a yield curve from this helper.
 *
 * <ul>
 *   <li>{@link #MaturityDate}: maturity (delivery) date of the instrument;</li>
 *   <li>{@link #LastRelevantDate}: latest relevant date of the instrument
 *       (the last date that affects pricing — typically the same as
 *       {@code MaturityDate} or later);</li>
 *   <li>{@link #CustomDate}: user-supplied date.</li>
 * </ul>
 *
 * @author JQuantLib migration team
 */
public final class Pillar {

    private Pillar() {
    }

    public enum Choice {
        MaturityDate, LastRelevantDate, CustomDate
    }
}
