/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2020 Lew Wei Hao

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.experimental.barrieroption;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Path-pricer for a continuously monitored double-barrier option simulated via Monte Carlo.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/experimental/barrieroption/mcdoublebarrierengine.cpp::DoubleBarrierPathPricer} (Phase 5e.5b-CFC-d-278).
 *
 * <p>For a {@link DoubleBarrierType#KnockOut} option the contract pays the
 * vanilla payoff against the terminal price discounted at maturity, unless the path crosses either barrier at some
 * intermediate node — in which case the rebate is paid (discounted at the knock node). For a
 * {@link DoubleBarrierType#KnockIn} option the contract becomes active only after the path crosses a barrier; otherwise
 * the rebate is paid at maturity. Knock-in/knock-out hybrids ({@code KIKO}, {@code KOKI}) are rejected.
 */
final class DoubleBarrierPathPricer extends PathPricer< Path > {

    private final DoubleBarrierType barrierType_;
    private final double barrierLow_;
    private final double barrierHigh_;
    private final double rebate_;
    private final PlainVanillaPayoff payoff_;
    private final double[] discounts_;

    DoubleBarrierPathPricer(final DoubleBarrierType barrierType, final double barrierLow, final double barrierHigh,
            final double rebate, final Option.Type type, final double strike, final double[] discounts) {
        QL.require(strike >= 0.0, "strike less than zero not allowed");
        QL.require(barrierLow > 0.0, "low barrier less/equal zero not allowed");
        QL.require(barrierHigh > 0.0, "high barrier less/equal zero not allowed");
        this.barrierType_ = barrierType;
        this.barrierLow_ = barrierLow;
        this.barrierHigh_ = barrierHigh;
        this.rebate_ = rebate;
        this.payoff_ = new PlainVanillaPayoff(type, strike);
        this.discounts_ = discounts;
    }

    @Override
    public Double op(final Path path) {
        final int n = path.length();
        QL.require(n > 1, "the path cannot be empty");

        boolean isOptionActive;
        int knockNode = -1; // Null<Size>() sentinel.
        final double terminalPrice = path.back();
        final double[] v = path.values();

        switch ( barrierType_ ) {
        case KnockOut:
            isOptionActive = true;
            for ( int i = 0; i < n - 1; i++ ) {
                final double newAssetPrice = v[i + 1];
                if ( newAssetPrice >= barrierHigh_ || newAssetPrice <= barrierLow_ ) {
                    isOptionActive = false;
                    if ( knockNode == -1 ) {
                        knockNode = i + 1;
                    }
                    break;
                }
            }
            break;
        case KnockIn:
            isOptionActive = false;
            for ( int i = 0; i < n - 1; i++ ) {
                final double newAssetPrice = v[i + 1];
                if ( newAssetPrice >= barrierHigh_ || newAssetPrice <= barrierLow_ ) {
                    isOptionActive = true;
                    if ( knockNode == -1 ) {
                        knockNode = i + 1;
                    }
                    break;
                }
            }
            break;
        default:
            throw new LibraryException("unknown barrier type");
        }

        if ( isOptionActive ) {
            return payoff_.get(terminalPrice) * discounts_[discounts_.length - 1];
        } else {
            return switch (barrierType_) {
                case KnockOut -> rebate_ * discounts_[knockNode];
                case KnockIn -> rebate_ * discounts_[discounts_.length - 1];
                default -> throw new LibraryException("unknown barrier type");
            };
        }
    }
}
