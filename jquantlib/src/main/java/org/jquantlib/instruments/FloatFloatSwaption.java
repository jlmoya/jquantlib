/*
 Copyright (C) 2013, 2018 Peter Caspers

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
 Copyright (C) 2013, 2018 Peter Caspers

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

/*!
 * \file floatfloatswaption.hpp (Java port)
 * \brief Option on a {@link FloatFloatSwap}.
 */

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * FloatFloat swaption: an option on a {@link FloatFloatSwap}.
 *
 * <p>Structurally analogous to {@link NonstandardSwaption} wrapping a
 * {@link NonstandardSwap}, but the underlying here is a
 * {@link FloatFloatSwap} (two floating legs with per-coupon gearings,
 * spreads, caps, and floors).
 *
 * <p>Mirrors C++ v1.42.1 {@code ql/instruments/floatfloatswaption.hpp} /
 * {@code .cpp} (author Peter Caspers, 2013/2018).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ {@code FloatFloatSwaption::arguments} multiply-inherits from
 *     {@code FloatFloatSwap::arguments} and {@code Option::arguments}.
 *     Java has no multiple inheritance; {@link ArgumentsImpl} extends
 *     {@link FloatFloatSwap.ArgumentsImpl} and adds the swaption fields
 *     ({@code swap}, {@code exercise}, {@code settlementType},
 *     {@code settlementMethod}). This matches the Swaption / NonstandardSwaption
 *     pattern used throughout this port.
 * <li>C++ {@code calibrationBasket()} is deferred — it requires
 *     {@code BasketGeneratingEngine} which is not yet ported (B.3 / later).
 * </ul>
 *
 * <p>Phase 2j.5 Track B.2.
 */
public class FloatFloatSwaption extends Option {

    // ── private fields ────────────────────────────────────────────────────────

    private final FloatFloatSwap swap_;
    private final Settlement.Type settlementType_;
    private final Settlement.Method settlementMethod_;


    // ── constructors ──────────────────────────────────────────────────────────

    /**
     * Construct with explicit settlement type/method, defaulting to
     * {@link Settlement.Type#Physical} / {@link Settlement.Method#PhysicalOTC}.
     * Mirrors C++ {@code FloatFloatSwaption(shared_ptr<FloatFloatSwap>, Exercise)}.
     */
    public FloatFloatSwaption(final FloatFloatSwap swap,
                               final Exercise exercise) {
        this(swap, exercise,
             Settlement.Type.Physical, Settlement.Method.PhysicalOTC);
    }

    /**
     * Full constructor.
     * Mirrors C++ {@code FloatFloatSwaption(shared_ptr<FloatFloatSwap>,
     * Exercise, Settlement::Type, Settlement::Method)}.
     */
    public FloatFloatSwaption(final FloatFloatSwap swap,
                               final Exercise exercise,
                               final Settlement.Type delivery,
                               final Settlement.Method settlementMethod) {
        super(null /* payoff */, exercise);
        this.swap_             = swap;
        this.settlementType_   = delivery;
        this.settlementMethod_ = settlementMethod;
        this.swap_.addObserver(this);
        this.swap_.alwaysForwardNotifications();
    }


    // ── inspectors ────────────────────────────────────────────────────────────

    /** Mirrors C++ {@code FloatFloatSwaption::settlementType()}. */
    public Settlement.Type settlementType() {
        return settlementType_;
    }

    /** Mirrors C++ {@code FloatFloatSwaption::settlementMethod()}. */
    public Settlement.Method settlementMethod() {
        return settlementMethod_;
    }

    /**
     * Returns the underlying swap type (Payer/Receiver).
     * Mirrors C++ {@code FloatFloatSwaption::type()}.
     */
    public VanillaSwap.Type type() {
        return swap_.type();
    }

    /**
     * Returns the underlying float-float swap.
     * Mirrors C++ {@code FloatFloatSwaption::underlyingSwap()}.
     */
    public FloatFloatSwap underlyingSwap() {
        return swap_;
    }

    /**
     * Exposes the exercise. Used by engines.
     */
    public Exercise exercise() {
        return exercise;
    }


    // ── Instrument interface ──────────────────────────────────────────────────

    /**
     * Mirrors C++ {@code FloatFloatSwaption::isExpired()}.
     * The swaption is expired when the last exercise date has occurred.
     */
    @Override
    public boolean isExpired() /* @ReadOnly */ {
        final Date today = new Settings().evaluationDate();
        return exercise.lastDate().le(today);
    }

    /**
     * Mirrors C++ {@code FloatFloatSwaption::setupArguments()}.
     * Chains to the underlying swap to fill FloatFloatSwap-level fields, then
     * overlays swaption-specific fields.
     */
    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        // Populate FloatFloatSwap-level fields.
        swap_.setupArguments(args);

        QL.require(args instanceof FloatFloatSwaption.ArgumentsImpl,
                   "wrong argument type");
        final FloatFloatSwaption.ArgumentsImpl a =
                (FloatFloatSwaption.ArgumentsImpl) args;

        a.swap             = swap_;
        a.exercise         = exercise;
        a.settlementType   = settlementType_;
        a.settlementMethod = settlementMethod_;
    }


    // ── inner interfaces ──────────────────────────────────────────────────────

    /**
     * Marking interface for FloatFloatSwaption arguments.
     * Mirrors C++ {@code FloatFloatSwaption::arguments} (multiple base classes
     * collapsed to a single interface here).
     */
    public interface Arguments extends FloatFloatSwap.Arguments,
                                        Option.Arguments {
        /* marker */
    }

    /**
     * Marking interface for FloatFloatSwaption results.
     */
    public interface Results extends Instrument.Results {
        /* marker */
    }


    // ── inner classes ─────────────────────────────────────────────────────────

    /**
     * Concrete arguments for {@link FloatFloatSwaption}.
     * Mirrors C++ {@code FloatFloatSwaption::arguments} which multiply-inherits
     * from {@code FloatFloatSwap::arguments} and {@code Option::arguments}.
     */
    public static class ArgumentsImpl extends FloatFloatSwap.ArgumentsImpl
            implements FloatFloatSwaption.Arguments {

        public FloatFloatSwap swap;
        public Exercise exercise;
        public Settlement.Type settlementType = Settlement.Type.Physical;
        public Settlement.Method settlementMethod = Settlement.Method.PhysicalOTC;

        // Option.Arguments-compatible field. Payoff is conceptually empty.
        public Payoff payoff;

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(swap     != null, "underlying float-float swap not set");
            QL.require(exercise != null, "exercise not set");
            Settlement.checkTypeAndMethodConsistency(settlementType, settlementMethod);
        }
    }

    /**
     * Concrete results for {@link FloatFloatSwaption}.
     * Adds no fields beyond {@link Instrument.ResultsImpl}; engines may publish
     * extra values via {@link Instrument.ResultsImpl#additionalResults()}.
     */
    public static class ResultsImpl extends Instrument.ResultsImpl
            implements FloatFloatSwaption.Results {

        @Override
        public void reset() {
            super.reset();
        }
    }

    /**
     * Abstract engine base for {@link FloatFloatSwaption}.
     * Mirrors C++ {@code FloatFloatSwaption::engine =
     * GenericEngine<FloatFloatSwaption::arguments, FloatFloatSwaption::results>}.
     */
    public abstract static class EngineImpl
            extends GenericEngine<FloatFloatSwaption.Arguments,
                                  FloatFloatSwaption.Results> {

        protected EngineImpl() {
            super(new FloatFloatSwaption.ArgumentsImpl(),
                  new FloatFloatSwaption.ResultsImpl());
        }
    }
}
