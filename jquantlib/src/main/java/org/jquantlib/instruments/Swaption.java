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
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.Derivative;
import org.jquantlib.math.solvers1D.NewtonSafe;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swaption.BlackSwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
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
 *     fields ({@code swap}, {@code ois}, {@code settlementType},
 *     {@code settlementMethod}, {@code exercise}) as direct members.
 *     Swap-leg fields ({@code legs}, {@code payer}) come via
 *     {@code Swap.setupArguments}; vanilla-specific fields are not yet
 *     propagated. Engines that need fixed/floating leg details should read
 *     them from the underlying swap reference held on the arguments —
 *     {@code args.swap} when the underlying is a {@link VanillaSwap}, or
 *     {@code args.ois} when it is an {@link OvernightIndexedSwap}.
 * <li>The C++ {@code FixedVsFloatingSwap} hierarchy is not yet ported; rather
 *     than introducing a new parent class (which would require touching
 *     {@code VanillaSwap}, {@code OvernightIndexedSwap}, and their make-builders
 *     — all out of scope here), Java {@link Swaption} stores the underlying as
 *     {@link Swap} and polymorphically dispatches via two typed accessors
 *     ({@link #underlying()} for the {@code VanillaSwap} case,
 *     {@link #underlyingOis()} for the {@code OvernightIndexedSwap} case).
 *     This mirrors the C++ {@code Swaption} class which retained a
 *     {@code vanilla_} field for backwards-compatible {@code underlyingSwap()}.
 *     Per the C++ class warning (swaption.hpp:60-68): only
 *     {@link BlackSwaptionEngine} / {@code FdHullWhiteSwaptionEngine} /
 *     {@code FdG2SwaptionEngine} fully support OIS underlyings; other engines
 *     will treat the OIS leg as a vanilla floating leg, which is at best a
 *     decent proxy.
 * </ul>
 *
 * @author Praneet Tiwari
 */
public class Swaption extends Option {

    //
    // private fields
    //

    private final Swap swap_;
    /** Cached {@link VanillaSwap} view of {@link #swap_} ({@code null} when
     *  the underlying is an {@link OvernightIndexedSwap}). Mirrors C++
     *  {@code vanilla_} (swaption.hpp:137). */
    private final VanillaSwap vanilla_;
    /** Cached {@link OvernightIndexedSwap} view of {@link #swap_}
     *  ({@code null} when the underlying is a {@link VanillaSwap}). */
    private final OvernightIndexedSwap ois_;
    private final Settlement.Type settlementType_;
    private final Settlement.Method settlementMethod_;

    //
    // public constructors — VanillaSwap underlying
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
        this.vanilla_ = swap;
        this.ois_ = null;
        this.settlementType_ = delivery;
        this.settlementMethod_ = settlementMethod;
        this.swap_.addObserver(this);
    }

    //
    // public constructors — OvernightIndexedSwap underlying
    //

    /**
     * Constructs a physically-settled swaption on an
     * {@link OvernightIndexedSwap} (PhysicalOTC method by default).
     * <p>
     * Mirrors the C++ templated ctor
     * {@code Swaption(ext::shared_ptr<FixedVsFloatingSwap>, ...)}
     * (swaption.hpp:94-97) with the {@code OvernightIndexedSwap} subclass.
     */
    public Swaption(final OvernightIndexedSwap swap, final Exercise exercise) {
        this(swap, exercise, Settlement.Type.Physical, Settlement.Method.PhysicalOTC);
    }

    /**
     * Constructs an OIS swaption with the given settlement type, defaulting
     * the method to a value consistent with the type.
     */
    public Swaption(final OvernightIndexedSwap swap, final Exercise exercise,
            final Settlement.Type delivery) {
        this(swap, exercise, delivery,
                delivery == Settlement.Type.Physical
                        ? Settlement.Method.PhysicalOTC
                        : Settlement.Method.ParYieldCurve);
    }

    /**
     * Constructs an OIS swaption with explicit settlement type and method.
     */
    public Swaption(final OvernightIndexedSwap swap, final Exercise exercise,
            final Settlement.Type delivery,
            final Settlement.Method settlementMethod) {
        super(null /* payoff */, exercise);
        Settlement.checkTypeAndMethodConsistency(delivery, settlementMethod);
        this.swap_ = swap;
        this.vanilla_ = null;
        this.ois_ = swap;
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
     * @return the underlying swap as a {@link VanillaSwap}, or {@code null}
     *         when the underlying is an {@link OvernightIndexedSwap}. Mirrors
     *         C++ {@code underlyingSwap()} (the {@code vanilla_} branch,
     *         swaption.hpp:137 + swaption.cpp:150).
     */
    public VanillaSwap underlying() {
        return vanilla_;
    }

    /**
     * Backwards-compatible alias for {@link #underlying()} (matches the
     * C++ legacy accessor {@code underlyingSwap()}).
     */
    public VanillaSwap underlyingSwap() {
        return vanilla_;
    }

    /**
     * @return the underlying swap as an {@link OvernightIndexedSwap}, or
     *         {@code null} when the underlying is a {@link VanillaSwap}.
     */
    public OvernightIndexedSwap underlyingOis() {
        return ois_;
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
        // Both VanillaSwap and OvernightIndexedSwap inherit Swap.setupArguments
        // (which populates legs + payer); VanillaSwap further overrides it to
        // populate vanilla-leg fields.
        swap_.setupArguments(args);

        QL.require(args instanceof Swaption.ArgumentsImpl, "wrong argument type");
        final Swaption.ArgumentsImpl a = (Swaption.ArgumentsImpl) args;

        a.swap = vanilla_;
        a.ois = ois_;
        a.settlementType = settlementType_;
        a.settlementMethod = settlementMethod_;
        a.exercise = exercise;
        // payoff is intentionally left null (matches C++ Swaption which passes an empty Payoff).
    }

    //
    // implied volatility — mirrors C++ v1.42.1 swaption.cpp lines 182-205
    // plus the anonymous-namespace ImpliedSwaptionVolHelper (swaption.cpp:38-103).
    //

    /**
     * Price type for {@link #impliedVolatility}. Mirrors the C++ enum
     * {@code Swaption::PriceType} (swaption.hpp:91).
     * <ul>
     *   <li>{@link #Spot} — the target price is the swaption NPV (spot value).</li>
     *   <li>{@link #Forward} — the target price is the forward swaption price
     *       (already divided by the exercise-date discount factor); the solver
     *       converts to a spot target by multiplying by the discount factor.</li>
     * </ul>
     */
    public enum PriceType { Spot, Forward }

    /**
     * Implied volatility (full-arity overload).
     *
     * <p>Mirrors C++ {@code Swaption::impliedVolatility(targetValue, disc,
     * guess, accuracy, maxEvaluations, minVol, maxVol, type, displacement,
     * priceType)} — swaption.cpp:182-205. Constructs an internal pricing
     * engine that shares a {@link SimpleQuote} for the volatility, then runs
     * {@link NewtonSafe} on the price-target residual until the engine NPV
     * matches {@code targetValue} to within {@code accuracy}.
     *
     * <p>The {@link VolatilityType} parameter selects the formula: under
     * {@link VolatilityType#ShiftedLognormal} the helper uses
     * {@link BlackSwaptionEngine} with the supplied displacement; under
     * {@link VolatilityType#Normal} a {@code BachelierSwaptionEngine} is
     * required by C++ but is not yet ported on the Java side — passing
     * {@code Normal} therefore throws {@link UnsupportedOperationException}.
     *
     * @param targetValue target price (either spot NPV or forward price,
     *                    selected by {@code priceType})
     * @param discountCurve discount curve handle
     * @param guess initial volatility guess
     * @param accuracy solver tolerance on the price residual
     * @param maxEvaluations maximum solver evaluations
     * @param minVol lower volatility bracket
     * @param maxVol upper volatility bracket
     * @param type volatility convention (ShiftedLognormal or Normal)
     * @param displacement displacement for ShiftedLognormal (ignored for Normal)
     * @param priceType {@link PriceType#Spot} or {@link PriceType#Forward}
     * @return implied volatility solving {@code engineNPV(targetValue) == 0}
     */
    public /*@Volatility*/ double impliedVolatility(
            final /*@Real*/ double targetValue,
            final Handle<YieldTermStructure> discountCurve,
            final /*@Volatility*/ double guess,
            final /*@Real*/ double accuracy,
            final /*@NonNegative*/ int maxEvaluations,
            final /*@Volatility*/ double minVol,
            final /*@Volatility*/ double maxVol,
            final VolatilityType type,
            final /*@Real*/ double displacement,
            final PriceType priceType) {
        QL.require(!isExpired(), "instrument expired");
        QL.require(exercise.type() == Exercise.Type.European,
                "not a European option");

        // Convert forward target to spot if needed: spot = fwd * D(t_exercise).
        // Mirrors C++ swaption.cpp:196-199 verbatim.
        double effectiveTarget = targetValue;
        if (priceType == PriceType.Forward) {
            effectiveTarget *= discountCurve.currentLink().discount(
                    exercise.date(0));
        }

        final ImpliedSwaptionVolHelper f = new ImpliedSwaptionVolHelper(
                this, discountCurve, effectiveTarget, displacement, type);
        final NewtonSafe solver = new NewtonSafe();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(f, accuracy, guess, minVol, maxVol);
    }

    /** Convenience overload: defaults priceType = Spot. */
    public /*@Volatility*/ double impliedVolatility(
            final /*@Real*/ double targetValue,
            final Handle<YieldTermStructure> discountCurve,
            final /*@Volatility*/ double guess,
            final /*@Real*/ double accuracy,
            final /*@NonNegative*/ int maxEvaluations,
            final /*@Volatility*/ double minVol,
            final /*@Volatility*/ double maxVol,
            final VolatilityType type,
            final /*@Real*/ double displacement) {
        return impliedVolatility(targetValue, discountCurve, guess, accuracy,
                maxEvaluations, minVol, maxVol, type, displacement,
                PriceType.Spot);
    }

    /** Convenience overload: defaults type = ShiftedLognormal, displacement = 0,
     *  priceType = Spot. */
    public /*@Volatility*/ double impliedVolatility(
            final /*@Real*/ double targetValue,
            final Handle<YieldTermStructure> discountCurve,
            final /*@Volatility*/ double guess,
            final /*@Real*/ double accuracy,
            final /*@NonNegative*/ int maxEvaluations,
            final /*@Volatility*/ double minVol,
            final /*@Volatility*/ double maxVol) {
        return impliedVolatility(targetValue, discountCurve, guess, accuracy,
                maxEvaluations, minVol, maxVol,
                VolatilityType.ShiftedLognormal, 0.0, PriceType.Spot);
    }

    /** Convenience overload mirroring the C++ default arguments
     *  (accuracy=1e-4, maxEvaluations=100, minVol=1e-7, maxVol=4.0,
     *  type=ShiftedLognormal, displacement=0, priceType=Spot). */
    public /*@Volatility*/ double impliedVolatility(
            final /*@Real*/ double targetValue,
            final Handle<YieldTermStructure> discountCurve,
            final /*@Volatility*/ double guess) {
        return impliedVolatility(targetValue, discountCurve, guess, 1.0e-4,
                100, 1.0e-7, 4.0,
                VolatilityType.ShiftedLognormal, 0.0, PriceType.Spot);
    }

    /**
     * Functor passed to the 1D solver inside {@link #impliedVolatility}.
     *
     * <p>Mirrors the C++ anonymous-namespace {@code ImpliedSwaptionVolHelper}
     * (swaption.cpp:38-103). The helper owns a {@link SimpleQuote} shared
     * with an internal {@link BlackSwaptionEngine} (ShiftedLognormal); each
     * call to {@link #op(double)} sets the quote to the trial volatility,
     * triggers the engine, and returns {@code engineNPV - targetValue}.
     *
     * <h3>Derivative</h3>
     * <p>C++ reads the analytical {@code vega} from
     * {@code results.additionalResults["vega"]} (swaption.cpp:94-102). The
     * Java {@link BlackSwaptionEngine} populates the same key
     * (Phase 5e.5b-CFC-d-73), so we read it directly to mirror C++ verbatim.
     */
    private static final class ImpliedSwaptionVolHelper implements Derivative {
        private final PricingEngine engine_;
        private final SimpleQuote vol_;
        private final /*@Real*/ double targetValue_;
        private final Instrument.ResultsImpl results_;

        ImpliedSwaptionVolHelper(final Swaption swaption,
                                 final Handle<YieldTermStructure> discountCurve,
                                 final /*@Real*/ double targetValue,
                                 final /*@Real*/ double displacement,
                                 final VolatilityType type) {
            this.targetValue_ = targetValue;
            // Implausible starting value forces a recalculate on the first
            // op(x) call (mirrors C++ SimpleQuote(-1.0) sentinel,
            // swaption.cpp:61-64).
            this.vol_ = new SimpleQuote(-1.0);
            final Handle<Quote> h = new Handle<Quote>(vol_);

            switch (type) {
                case ShiftedLognormal:
                    // C++ uses BlackSwaptionEngine(disc, h, Actual365Fixed,
                    // displacement). Java's BlackSwaptionEngine builds an
                    // internal ConstantSwaptionVolatility with the supplied
                    // quote handle; displacement is propagated as the
                    // engine-level displacement (used when the surface's own
                    // shift is zero) — see BlackSwaptionEngine.calculate()
                    // effective-displacement branch.
                    this.engine_ = new BlackSwaptionEngine(discountCurve,
                            wrapConstantVol(h, displacement));
                    break;
                case Normal:
                    // BachelierSwaptionEngine is not yet ported on the Java
                    // side (see SwaptionAdditionalTest.testSwaptionDeltaIn
                    // BachelierModel). Mirror C++ swaption.cpp:77-79 fail
                    // path with a Java equivalent.
                    throw new UnsupportedOperationException(
                            "Normal vol implied-vol path requires"
                            + " BachelierSwaptionEngine (not yet ported)");
                default:
                    throw new LibraryException(
                            "unknown VolatilityType (" + type + ")");
            }

            // Mirrors C++ swaption.setupArguments(engine_->getArguments()).
            // Swaption.setupArguments is protected on Instrument; this is
            // a nested class of Swaption so the call is permitted.
            swaption.setupArguments(engine_.getArguments());
            engine_.getArguments().validate();
            this.results_ = (Instrument.ResultsImpl) engine_.getResults();
        }

        @Override
        public double op(final double x) {
            if (x != vol_.value()) {
                vol_.setValue(x);
                engine_.calculate();
            }
            return results_.value - targetValue_;
        }

        @Override
        public double derivative(final double x) {
            if (x != vol_.value()) {
                vol_.setValue(x);
                engine_.calculate();
            }
            // Mirrors C++ swaption.cpp:99-102: analytical vega is required.
            final Object vega = results_.additionalResults().get("vega");
            QL.require(vega instanceof Number, "vega not provided");
            return ((Number) vega).doubleValue();
        }
    }

    /**
     * Wraps a volatility quote into a {@link org.jquantlib.termstructures.SwaptionVolatilityStructure}
     * handle compatible with {@link BlackSwaptionEngine}. Mirrors the
     * implicit C++ vol-handle conversion in {@code ImpliedSwaptionVolHelper}
     * (swaption.cpp:70-71) which constructs a {@code BlackSwaptionEngine}
     * directly from {@code Handle<Quote>}.
     *
     * <p>Java's primary {@code BlackSwaptionEngine(Handle<YieldTermStructure>,
     * Handle<Quote>)} factory does not accept a per-call displacement;
     * we construct a {@link org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility}
     * here so the displacement flows in via the surface's {@code shift()}
     * accessor (matching the path the engine takes at calculate() time).
     */
    private static Handle<org.jquantlib.termstructures.SwaptionVolatilityStructure>
            wrapConstantVol(final Handle<Quote> vol, final double displacement) {
        return new Handle<org.jquantlib.termstructures.SwaptionVolatilityStructure>(
                new org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility(
                        0,
                        new org.jquantlib.time.calendars.NullCalendar(),
                        org.jquantlib.time.BusinessDayConvention.Following,
                        vol,
                        new Actual365Fixed(),
                        VolatilityType.ShiftedLognormal,
                        displacement));
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

        /** {@link VanillaSwap}-typed underlying view (null when the underlying
         *  is an {@link OvernightIndexedSwap}). */
        public VanillaSwap swap;
        /** {@link OvernightIndexedSwap}-typed underlying view (null when the
         *  underlying is a {@link VanillaSwap}). Mirrors the C++ template
         *  hierarchy via runtime dispatch — see class-level note. */
        public OvernightIndexedSwap ois;
        public Settlement.Type settlementType = Settlement.Type.Physical;
        public Settlement.Method settlementMethod = Settlement.Method.PhysicalOTC;
        public Exercise exercise;

        // Option.Arguments-compatible fields. Payoff is conceptually empty for swaptions.
        public Payoff payoff;

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(swap != null || ois != null, "swap not set");
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
