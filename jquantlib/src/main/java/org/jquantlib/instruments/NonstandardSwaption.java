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
 * \file nonstandardswaption.hpp
 * \brief nonstandard swap option class
 */

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

/**
 * Non-standard swaption: an option on a {@link NonstandardSwap}.
 *
 * <p>Structurally analogous to {@link Swaption} wrapping a {@link VanillaSwap}.
 * Mirrors C++ v1.42.1 {@code ql/instruments/nonstandardswaption.hpp} /
 * {@code .cpp} (author Peter Caspers, 2013/2018).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ {@code NonstandardSwaption::arguments} multiply-inherits from
 *     {@code NonstandardSwap::arguments} and {@code Option::arguments}.
 *     Java has no multiple inheritance; {@link ArgumentsImpl} extends
 *     {@link NonstandardSwap.ArgumentsImpl} and adds the swaption fields
 *     ({@code swap}, {@code exercise}, {@code settlementType},
 *     {@code settlementMethod}). This matches the Swaption pattern.
 * <li>C++ {@code calibrationBasket()} is deferred — it requires
 *     {@code BasketGeneratingEngine} which is not yet ported (A.3 / later).
 * </ul>
 *
 * <p>Phase 2j.5 Track A.2.
 */
public class NonstandardSwaption extends Option {

    // ── private fields ────────────────────────────────────────────────────────

    private final NonstandardSwap swap_;
    private final Settlement.Type settlementType_;
    private final Settlement.Method settlementMethod_;


    // ── constructors ──────────────────────────────────────────────────────────

    /**
     * Construct from an existing {@link Swaption}.
     * Converts the underlying {@link VanillaSwap} to a {@link NonstandardSwap}
     * and copies the exercise and settlement fields.
     * Mirrors C++ {@code NonstandardSwaption(const Swaption&)}.
     */
    public NonstandardSwaption(final Swaption fromSwaption) {
        super(null /* payoff */, fromSwaption.exercise);
        this.swap_           = new NonstandardSwap(fromSwaption.underlying());
        this.settlementType_ = fromSwaption.settlementType();
        this.settlementMethod_ = fromSwaption.settlementMethod();
        this.swap_.addObserver(this);
        this.swap_.alwaysForwardNotifications();
    }

    /**
     * Construct with explicit settlement type/method, defaulting to
     * {@link Settlement.Type#Physical} / {@link Settlement.Method#PhysicalOTC}.
     */
    public NonstandardSwaption(final NonstandardSwap swap,
                               final Exercise exercise) {
        this(swap, exercise,
             Settlement.Type.Physical, Settlement.Method.PhysicalOTC);
    }

    /**
     * Full constructor.
     * Mirrors C++ {@code NonstandardSwaption(shared_ptr<NonstandardSwap>, Exercise, Type, Method)}.
     */
    public NonstandardSwaption(final NonstandardSwap swap,
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

    /** Mirrors C++ {@code NonstandardSwaption::settlementType()}. */
    public Settlement.Type settlementType() {
        return settlementType_;
    }

    /** Mirrors C++ {@code NonstandardSwaption::settlementMethod()}. */
    public Settlement.Method settlementMethod() {
        return settlementMethod_;
    }

    /**
     * Returns the underlying swap type (Payer/Receiver).
     * Mirrors C++ {@code NonstandardSwaption::type()}.
     */
    public VanillaSwap.Type type() {
        return swap_.type();
    }

    /**
     * Returns the underlying non-standard swap.
     * Mirrors C++ {@code NonstandardSwaption::underlyingSwap()}.
     */
    public NonstandardSwap underlyingSwap() {
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
     * Mirrors C++ {@code NonstandardSwaption::isExpired()}.
     * The swaption is expired when the last exercise date has occurred
     * (i.e. is less than or equal to today's evaluation date).
     */
    @Override
    public boolean isExpired() /* @ReadOnly */ {
        final Date today = new Settings().evaluationDate();
        return exercise.lastDate().le(today);
    }

    /**
     * Mirrors C++ {@code NonstandardSwaption::setupArguments()}.
     * Chains to the underlying swap to fill Swap-level fields, then
     * overlays swaption-specific fields.
     */
    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        // Populate NonstandardSwap-level fields (legs, payer, type, nominals, …).
        swap_.setupArguments(args);

        QL.require(args instanceof NonstandardSwaption.ArgumentsImpl,
                   "wrong argument type");
        final NonstandardSwaption.ArgumentsImpl a =
                (NonstandardSwaption.ArgumentsImpl) args;

        a.swap             = swap_;
        a.exercise         = exercise;
        a.settlementType   = settlementType_;
        a.settlementMethod = settlementMethod_;
    }


    // ── inner interfaces ──────────────────────────────────────────────────────

    /**
     * Marking interface for NonstandardSwaption arguments.
     * Mirrors C++ {@code NonstandardSwaption::arguments} (multiple base classes
     * collapsed to a single interface here).
     */
    public interface Arguments extends NonstandardSwap.Arguments,
                                        Option.Arguments {
        /* marker */
    }

    /**
     * Marking interface for NonstandardSwaption results.
     */
    public interface Results extends Instrument.Results {
        /* marker */
    }


    // ── inner classes ─────────────────────────────────────────────────────────

    /**
     * Concrete arguments for {@link NonstandardSwaption}.
     * Mirrors C++ {@code NonstandardSwaption::arguments} which multiply-inherits
     * from {@code NonstandardSwap::arguments} and {@code Option::arguments}.
     */
    public static class ArgumentsImpl extends NonstandardSwap.ArgumentsImpl
            implements NonstandardSwaption.Arguments {

        public NonstandardSwap swap;
        public Exercise exercise;
        public Settlement.Type settlementType = Settlement.Type.Physical;
        public Settlement.Method settlementMethod = Settlement.Method.PhysicalOTC;

        // Option.Arguments-compatible field. Payoff is conceptually empty.
        public Payoff payoff;

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(swap     != null, "underlying non standard swap not set");
            QL.require(exercise != null, "exercise not set");
            Settlement.checkTypeAndMethodConsistency(settlementType, settlementMethod);
        }
    }

    /**
     * Concrete results for {@link NonstandardSwaption}.
     * Adds no fields beyond {@link Instrument.ResultsImpl}; engines may publish
     * extra values via {@link Instrument.ResultsImpl#additionalResults()}.
     */
    public static class ResultsImpl extends Instrument.ResultsImpl
            implements NonstandardSwaption.Results {

        @Override
        public void reset() {
            super.reset();
        }
    }

    /**
     * Abstract engine base for {@link NonstandardSwaption}.
     * Mirrors C++ {@code NonstandardSwaption::engine =
     * GenericEngine<NonstandardSwaption::arguments, NonstandardSwaption::results>}.
     */
    public abstract static class EngineImpl
            extends GenericEngine<NonstandardSwaption.Arguments,
                                  NonstandardSwaption.Results> {

        protected EngineImpl() {
            super(new NonstandardSwaption.ArgumentsImpl(),
                  new NonstandardSwaption.ResultsImpl());
        }
    }
}
