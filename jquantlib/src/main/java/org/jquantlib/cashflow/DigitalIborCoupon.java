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
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.cashflow;

import org.jquantlib.instruments.Position;
import org.jquantlib.math.Constants;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Ibor-rate coupon with a digital call/put option.
 * <p>
 * Composes an {@link IborCoupon} underlying with the {@link DigitalCoupon} replication framework. The digital payoff
 * (cash-or-nothing or asset-or-nothing) is replicated through a tight call/put-spread around the strike, controlled by
 * the {@link DigitalReplication} parameters.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/digitaliborcoupon.hpp/cpp} ({@code class DigitalIborCoupon}).
 *
 * @author Cristina Duminuco (C++ original)
 * @author Giorgio Facchinetti (C++ original)
 */
public class DigitalIborCoupon extends DigitalCoupon {

    //
    // public constructors
    //

    /** Convenience: no call/put options. */
    public DigitalIborCoupon(final IborCoupon underlying) {
        this(underlying, Constants.NULL_REAL, Position.Long, false, Constants.NULL_REAL, Constants.NULL_REAL,
                Position.Long, false, Constants.NULL_REAL, null, false);
    }

    /** Full ctor (matches C++ {@code DigitalIborCoupon} ctor, digitaliborcoupon.cpp:28-42). */
    public DigitalIborCoupon(final IborCoupon underlying, final double callStrike, final Position callPosition,
            final boolean isCallATMIncluded, final double callDigitalPayoff, final double putStrike,
            final Position putPosition, final boolean isPutATMIncluded, final double putDigitalPayoff,
            final DigitalReplication replication, final boolean nakedOption) {
        super(underlying, callStrike, callPosition, isCallATMIncluded, callDigitalPayoff, putStrike, putPosition,
                isPutATMIncluded, putDigitalPayoff, replication, nakedOption);
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< DigitalIborCoupon > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }

}
