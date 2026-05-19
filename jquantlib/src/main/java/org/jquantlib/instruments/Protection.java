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
 Copyright (C) 2008, 2009 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

/**
 * Information on a default-protection contract.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::Protection}
 * ({@code ql/default.hpp}). The C++ {@code Protection} type is a struct exposing a single {@code Side} enum; in Java we
 * model it as a final non-instantiable class containing the {@code Side} enum, preserving the fully-qualified
 * {@code Protection.Side} access pattern from the C++ source.
 *
 * @category instruments
 */
public final class Protection {

    private Protection() {
        // utility class — no instances
    }

    /** Side of a credit-default-swap protection contract. */
    public enum Side {
        /** Buyer of protection (typically pays the running spread). */
        Buyer,
        /** Seller of protection (typically receives the running spread). */
        Seller
    }
}
