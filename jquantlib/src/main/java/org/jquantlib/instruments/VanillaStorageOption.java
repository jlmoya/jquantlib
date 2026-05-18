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
 Copyright (C) 2011 Klaus Spanderen
 */
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.BermudanExercise;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Vanilla gas-storage option.
 *
 * <p>Java port of v1.42.1 {@code ql/instruments/vanillastorageoption.hpp}.</p>
 *
 * <p>A storage option is a Bermudan-exercise instrument over a working-gas
 * storage facility. At each exercise date the holder may inject (buy) or
 * withdraw (sell) gas subject to a {@code capacity} ceiling and a per-step
 * {@code changeRate} cap; the {@code load} field gives the starting working
 * volume at the engine reference date.</p>
 *
 * <p>The payoff is a {@link NullPayoff} sentinel because the cash-flow logic
 * lives entirely inside the engine's step condition; the instrument carries
 * only the contract envelope (Bermudan dates + capacity/load/changeRate).</p>
 *
 * @author Phase 5e.5b-CFC-d-215 port
 */
public class VanillaStorageOption extends OneAssetOption {

    private final double capacity_;
    private final double load_;
    private final double changeRate_;

    public VanillaStorageOption(final BermudanExercise ex,
                                final double capacity,
                                final double load,
                                final double changeRate) {
        super(new NullPayoff(), ex);
        this.capacity_ = capacity;
        this.load_ = load;
        this.changeRate_ = changeRate;
    }

    @Override
    public boolean isExpired() /* @ReadOnly */ {
        // C++ uses detail::simple_event(exercise_->lastDate()).hasOccurred();
        // jquantlib OneAssetOption.isExpired uses lastDate < evaluationDate.
        // Match C++ semantics: expired iff lastDate <= evaluationDate.
        return !exercise.lastDate().gt(new Settings().evaluationDate());
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        QL.require(args instanceof VanillaStorageOption.ArgumentsImpl,
                "wrong argument type");
        final VanillaStorageOption.ArgumentsImpl a =
                (VanillaStorageOption.ArgumentsImpl) args;
        a.payoff     = this.payoff;     // NullPayoff
        a.exercise   = this.exercise;   // BermudanExercise
        a.capacity   = capacity_;
        a.load       = load_;
        a.changeRate = changeRate_;
    }

    /** Returns the working-gas capacity (volume ceiling). */
    public double capacity() { return capacity_; }

    /** Returns the initial working-gas load. */
    public double load() { return load_; }

    /** Returns the per-step injection/withdrawal cap. */
    public double changeRate() { return changeRate_; }

    /**
     * Vanilla-storage engine arguments. Mirrors C++
     * {@code VanillaStorageOption::arguments}.
     */
    public static class ArgumentsImpl extends OneAssetOption.ArgumentsImpl
            implements OneAssetOption.Arguments {

        public double capacity;
        public double load;
        public double changeRate;

        @Override
        public void validate() /*@ReadOnly*/ {
            QL.require(payoff != null, "no payoff given");
            QL.require(exercise != null, "no exercise given");
            QL.require(capacity > 0.0 && changeRate > 0.0 && load >= 0.0,
                    "positive capacity, load and change rate required");
            QL.require(load <= capacity && changeRate <= capacity,
                    "illegal values load of changeRate");
        }
    }
}
