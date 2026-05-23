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
 Copyright (C) 2007 Marco Bianchetti
 Copyright (C) 2007 Giorgio Facchinetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

/**
 * StickyMin payoff (double option).
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::StickyMinPayoff} ({@code stickyratchet.hpp:151-166}).
 * Special-case of {@link DoubleStickyRatchetPayoff} with {@code type1=+1, type2=+1}.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public final class StickyMinPayoff extends DoubleStickyRatchetPayoff {

    public StickyMinPayoff(final double gearing1, final double gearing2, final double gearing3,
            final double spread1, final double spread2, final double spread3,
            final double initialValue1, final double initialValue2,
            final double accrualFactor) {
        super(+1.0, +1.0,
                gearing1, gearing2, gearing3,
                spread1, spread2, spread3,
                initialValue1, initialValue2,
                accrualFactor);
    }

    @Override
    public String name() {
        return "StickyMin";
    }
}
