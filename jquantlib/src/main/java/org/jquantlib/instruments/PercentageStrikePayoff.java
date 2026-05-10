/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2003, 2006 Ferdinando Ametrano
 Copyright (C) 2006 Warren Chou
 Copyright (C) 2006 StatPro Italia srl
 Copyright (C) 2006 Chiara Fornarola

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.instruments;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Payoff with strike expressed as percentage (moneyness) of the underlying spot.
 * <p>
 * Used by Cliquet (Ratchet) options where each forward-starting period uses a
 * strike set to a fixed percentage of the spot at the period's reset date.
 * <ul>
 *   <li>CALL: {@code price * max(1 - moneyness, 0)}</li>
 *   <li>PUT:  {@code price * max(moneyness - 1, 0)}</li>
 * </ul>
 *
 * Mirrors C++ QuantLib v1.42.1 {@code PercentageStrikePayoff} in
 * {@code ql/instruments/payoffs.{hpp,cpp}}.
 */
public class PercentageStrikePayoff extends StrikedTypePayoff {

    public PercentageStrikePayoff(final Option.Type type, final /*@Real*/ double moneyness) {
        super(type, moneyness);
    }

    @Override
    public String name() /* @ReadOnly */ {
        return "PercentageStrike";
    }

    @Override
    public final double get(final double price) /* @ReadOnly */ {
        if (type == Option.Type.Call) {
            return price * Math.max(1.0 - strike, 0.0);
        } else if (type == Option.Type.Put) {
            return price * Math.max(strike - 1.0, 0.0);
        } else {
            throw new LibraryException(UNKNOWN_OPTION_TYPE);
        }
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<PercentageStrikePayoff> v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
