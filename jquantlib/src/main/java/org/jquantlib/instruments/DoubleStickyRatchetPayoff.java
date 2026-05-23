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

import org.jquantlib.QL;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Intermediate class for single/double sticky/ratchet payoffs.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::DoubleStickyRatchetPayoff}
 * ({@code ql/instruments/stickyratchet.{hpp,cpp}}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Used by ratchet/sticky payoff variants. {@code initialValue1}/{@code initialValue2}
 * can be a (forward) rate or a coupon/accrualFactor.
 *
 * <p>JDK 25 sealed (JEP 409) — permits the six concrete v1.42.1 sticky/ratchet payoffs.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public abstract sealed class DoubleStickyRatchetPayoff extends Payoff
        permits RatchetPayoff, StickyPayoff,
                RatchetMaxPayoff, RatchetMinPayoff,
                StickyMaxPayoff, StickyMinPayoff {

    protected final double type1_;
    protected final double type2_;
    protected final double gearing1_;
    protected final double gearing2_;
    protected final double gearing3_;
    protected final double spread1_;
    protected final double spread2_;
    protected final double spread3_;
    protected final double initialValue1_;
    protected final double initialValue2_;
    protected final double accrualFactor_;

    public DoubleStickyRatchetPayoff(final double type1, final double type2,
            final double gearing1, final double gearing2, final double gearing3,
            final double spread1, final double spread2, final double spread3,
            final double initialValue1, final double initialValue2,
            final double accrualFactor) {
        this.type1_ = type1;
        this.type2_ = type2;
        this.gearing1_ = gearing1;
        this.gearing2_ = gearing2;
        this.gearing3_ = gearing3;
        this.spread1_ = spread1;
        this.spread2_ = spread2;
        this.spread3_ = spread3;
        this.initialValue1_ = initialValue1;
        this.initialValue2_ = initialValue2;
        this.accrualFactor_ = accrualFactor;
    }

    //
    // overrides Payoff
    //

    @Override
    public String name() {
        return "DoubleStickyRatchetPayoff";
    }

    @Override
    public String description() {
        return name();
    }

    /**
     * Mirrors C++ {@code DoubleStickyRatchetPayoff::operator()(Real forward)} from {@code stickyratchet.cpp:26-38}.
     */
    @Override
    public double get(final double forward) {
        QL.require(Math.abs(type1_) == 1.0 || type1_ == 0.0,
                "unknown/illegal type1 value (only 0.0 and +/-1,0 are allowed)");
        QL.require(Math.abs(type2_) == 1.0 || type2_ == 0.0,
                "unknown/illegal type2 value (only 0.0 and +/-1,0 are allowed)");
        final double swaplet = gearing3_ * forward + spread3_;
        final double effStrike1 = gearing1_ * initialValue1_ + spread1_;
        final double effStrike2 = gearing2_ * initialValue2_ + spread2_;
        final double effStrike3 = type1_ * type2_ * Math.max(type2_ * (swaplet - effStrike2), 0.0);
        return accrualFactor_ * (swaplet -
                type1_ * Math.max(type1_ * (swaplet - effStrike1), effStrike3));
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< DoubleStickyRatchetPayoff > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
