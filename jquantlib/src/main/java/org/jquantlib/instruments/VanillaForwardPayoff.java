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
 Copyright (C) 2010 Klaus Spanderen
 */
package org.jquantlib.instruments;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Forward-style {@link StrikedTypePayoff} returning a signed difference between
 * spot and strike (no max-with-zero floor).
 *
 * <p>Java port of v1.42.1
 * {@code ql/instruments/vanillaswingoption.hpp::VanillaForwardPayoff}.
 *
 * <p>For a CALL: {@code price - strike}. For a PUT: {@code strike - price}.
 * Unlike {@link PlainVanillaPayoff}, the payoff may be negative — appropriate
 * for the swing-option per-exercise cash flow where an early exercise is
 * forced to be exercised once chosen (no opt-out).
 *
 * @author Phase 5e.5b-CFC-d-170 port
 */
public class VanillaForwardPayoff extends StrikedTypePayoff {

    public VanillaForwardPayoff(final Option.Type type, final /*@Real*/ double strike) {
        super(type, strike);
    }

    @Override
    public String name() /* @ReadOnly */ {
        return "ForwardTypePayoff";
    }

    @Override
    public double get(final double price) /* @ReadOnly */ {
        if (type == Option.Type.Call) {
            return price - strike;
        } else if (type == Option.Type.Put) {
            return strike - price;
        } else {
            throw new LibraryException(UNKNOWN_OPTION_TYPE);
        }
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<VanillaForwardPayoff> v =
                (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
