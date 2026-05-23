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
 Copyright (C) 2003, 2006 Ferdinando Ametrano
 Copyright (C) 2006 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Binary supershare payoff.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::SuperSharePayoff}
 * ({@code ql/instruments/payoffs.{hpp,cpp}:232-256 / 208-224}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Pays a fixed {@code cashPayoff} when the underlying lies in {@code [strike, secondStrike)},
 * otherwise zero.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public final class SuperSharePayoff extends StrikedTypePayoff {

    private final double secondStrike;
    private final double cashPayoff;

    public SuperSharePayoff(final double strike, final double secondStrike, final double cashPayoff) {
        super(Option.Type.Call, strike);
        QL.require(secondStrike > strike,
                "second strike (" + secondStrike + ") must be higher than first strike (" + strike + ")");
        this.secondStrike = secondStrike;
        this.cashPayoff = cashPayoff;
    }

    public double secondStrike() {
        return secondStrike;
    }

    public double cashPayoff() {
        return cashPayoff;
    }

    //
    // overrides Payoff
    //

    @Override
    public String name() {
        return "SuperShare";
    }

    @Override
    public String description() {
        return super.description() + ", " + secondStrike + " second strike, " + cashPayoff + " amount";
    }

    /**
     * {@code (price>=strike && price<secondStrike) ? cashPayoff : 0.0}.
     */
    @Override
    public double get(final double price) {
        return (price >= strike && price < secondStrike) ? cashPayoff : 0.0;
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< SuperSharePayoff > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
