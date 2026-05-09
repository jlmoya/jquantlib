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
 Copyright (C) 2007 Cristina Duminuco

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.cashflow;

/**
 * Digital-option replication strategies.
 * <p>
 * Specification of replication strategies used to price the embedded digital
 * option in a {@link DigitalCoupon}.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/replication.hpp}.
 *
 * @author Cristina Duminuco (C++ original)
 */
public final class Replication {

    public enum Type {
        /** Sub-replication (lower bound on the price). */
        Sub,
        /** Central replication (default). */
        Central,
        /** Super-replication (upper bound on the price). */
        Super
    }

    private Replication() {
        // utility namespace class
    }
}
