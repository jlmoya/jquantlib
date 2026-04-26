/*
Copyright (C) 2008 Praneet Tiwari

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
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * %Swaption class.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/instruments/swaption.hpp}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ {@code Swaption::arguments} multiply-inherits from
 *     {@code FixedVsFloatingSwap::arguments} and {@code Option::arguments}.
 *     Java has no multiple inheritance, and {@link VanillaSwap.ArgumentsImpl}
 *     is currently a non-static inner class (cannot be extended without an
 *     enclosing instance). This scaffold therefore extends only
 *     {@link Swap.ArgumentsImpl} (the static base) and exposes the swaption
 *     fields ({@code swap}, {@code settlementType}, {@code settlementMethod},
 *     {@code exercise}) as direct members. Swap-leg fields ({@code legs},
 *     {@code payer}) come via {@code Swap.setupArguments}; vanilla-specific
 *     fields are not yet propagated. Engines that need fixed/floating leg
 *     details should read them from the underlying {@code swap} reference
 *     held on the arguments.
 * <li>The C++ {@code FixedVsFloatingSwap} hierarchy is not yet ported; the
 *     constructor accepts a {@link VanillaSwap} (its only Java subclass at
 *     this stage), matching the existing stub signature.
 * </ul>
 *
 * @author Praneet Tiwari
 */
public class Swaption extends Option {

    //
    // private fields
    //

    private final VanillaSwap swap_;
    private final Settlement.Type settlementType_;
    private final Settlement.Method settlementMethod_;

    //
    // public constructors
    //

    /**
     * Constructs a physically-settled swaption (PhysicalOTC method by default).
     */
    public Swaption(final VanillaSwap swap, final Exercise exercise) {
        this(swap, exercise, Settlement.Type.Physical, Settlement.Method.PhysicalOTC);
    }

    /**
     * Constructs a swaption with the given settlement type, defaulting the
     * method to a value consistent with the type.
     */
    public Swaption(final VanillaSwap swap, final Exercise exercise,
            final Settlement.Type delivery) {
        this(swap, exercise, delivery,
                delivery == Settlement.Type.Physical
                        ? Settlement.Method.PhysicalOTC
                        : Settlement.Method.ParYieldCurve);
    }

    /**
     * Constructs a swaption with explicit settlement type and method.
     */
    public Swaption(final VanillaSwap swap, final Exercise exercise,
            final Settlement.Type delivery,
            final Settlement.Method settlementMethod) {
        super(null /* payoff */, exercise);
        Settlement.checkTypeAndMethodConsistency(delivery, settlementMethod);
        this.swap_ = swap;
        this.settlementType_ = delivery;
        this.settlementMethod_ = settlementMethod;
        this.swap_.addObserver(this);
    }

    //
    // public inspectors
    //

    public Settlement.Type settlementType() {
        return settlementType_;
    }

    public Settlement.Method settlementMethod() {
        return settlementMethod_;
    }

    /**
     * @return the underlying swap. Mirrors C++ {@code underlying()}.
     */
    public VanillaSwap underlying() {
        return swap_;
    }

    /**
     * Backwards-compatible alias for {@link #underlying()} (matches the
     * C++ legacy accessor {@code underlyingSwap()}).
     */
    public VanillaSwap underlyingSwap() {
        return swap_;
    }

    //
    // overrides Instrument
    //

    @Override
    public boolean isExpired() /* @ReadOnly */ {
        final Date today = new Settings().evaluationDate();
        // Mirror C++: the swaption is expired when its last exercise date has occurred.
        return exercise.lastDate().lt(today) || exercise.lastDate().eq(today);
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        // Chain to the underlying swap so that Swap-level fields are populated.
        swap_.setupArguments(args);

        QL.require(args instanceof Swaption.ArgumentsImpl, "wrong argument type");
        final Swaption.ArgumentsImpl a = (Swaption.ArgumentsImpl) args;

        a.swap = swap_;
        a.settlementType = settlementType_;
        a.settlementMethod = settlementMethod_;
        a.exercise = exercise;
        // payoff is intentionally left null (matches C++ Swaption which passes an empty Payoff).
    }

    //
    // public inner interfaces
    //

    /**
     * Marker interface for swaption arguments. In C++ this inherits from both
     * {@code FixedVsFloatingSwap::arguments} and {@code Option::arguments};
     * see the class-level note for the Java compromise.
     */
    public interface Arguments extends Swap.Arguments, Option.Arguments {
        /* marking interface */
    }

    /**
     * Marker interface for swaption results.
     */
    public interface Results extends Instrument.Results {
        /* marking interface */
    }

    //
    // public static inner classes
    //

    /**
     * %Arguments for swaption calculation.
     */
    public static class ArgumentsImpl extends Swap.ArgumentsImpl
            implements Swaption.Arguments {

        public VanillaSwap swap;
        public Settlement.Type settlementType = Settlement.Type.Physical;
        public Settlement.Method settlementMethod = Settlement.Method.PhysicalOTC;
        public Exercise exercise;

        // Option.Arguments-compatible fields. Payoff is conceptually empty for swaptions.
        public Payoff payoff;

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(swap != null, "swap not set");
            QL.require(exercise != null, "exercise not set");
            Settlement.checkTypeAndMethodConsistency(settlementType, settlementMethod);
        }
    }

    /**
     * %Results from swaption calculation. Adds no fields beyond
     * {@link Instrument.ResultsImpl}; engines may still publish extra values
     * (such as {@code vega}) via {@link Instrument.ResultsImpl#additionalResults()}.
     */
    public static class ResultsImpl extends Instrument.ResultsImpl
            implements Swaption.Results {

        @Override
        public void reset() {
            super.reset();
        }
    }

    /**
     * Base class for swaption pricing engines. Mirrors C++
     * {@code Swaption::engine = GenericEngine<Swaption::arguments, Swaption::results>}.
     */
    public abstract static class EngineImpl
            extends GenericEngine<Swaption.Arguments, Swaption.Results> {

        protected EngineImpl() {
            super(new Swaption.ArgumentsImpl(), new Swaption.ResultsImpl());
        }
    }
}
