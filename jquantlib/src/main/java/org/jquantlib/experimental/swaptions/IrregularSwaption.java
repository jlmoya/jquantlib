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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2006 Cristina Duminuco
 Copyright (C) 2006 Marco Bianchetti
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2010 Andre Miemiec

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.swaptions;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Irregular Swaption: a European/Bermudan/American option to enter into an
 * {@link IrregularSwap}.
 *
 * <p>Phase 4i port of C++ QuantLib v1.42.1
 * {@code ql/experimental/swaptions/irregularswaption.{hpp,cpp}}.
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *   <li>{@link #impliedVolatility(double, Handle, double, double, int, double, double)}
 *       is currently a scaffold: the C++ method runs an internal
 *       {@code BlackSwaptionEngine} and a {@code NewtonSafe} root-finder via
 *       a private helper, but the Java {@code BlackSwaptionEngine} expects a
 *       {@link VanillaSwap}, not an {@link IrregularSwap}. Throws
 *       {@link UnsupportedOperationException} pending a Phase 4i.5 port of
 *       {@code IrregularImpliedVolHelper}.</li>
 *   <li>{@link IrregularSettlement} is exposed as a top-level class with a
 *       nested {@link IrregularSettlement.Type} enum, mirroring the C++
 *       struct layout.</li>
 * </ul>
 */
public class IrregularSwaption extends Option {

    private final IrregularSwap swap_;
    private final IrregularSettlement.Type settlementType_;

    public IrregularSwaption(final IrregularSwap swap, final Exercise exercise) {
        this(swap, exercise, IrregularSettlement.Type.Physical);
    }

    public IrregularSwaption(final IrregularSwap swap, final Exercise exercise,
            final IrregularSettlement.Type delivery) {
        super(null /* payoff */, exercise);
        this.swap_ = swap;
        this.settlementType_ = delivery;
        this.swap_.addObserver(this);
    }

    //
    // public inspectors
    //

    public IrregularSettlement.Type settlementType() {
        return settlementType_;
    }

    public VanillaSwap.Type type() {
        return swap_.type();
    }

    public IrregularSwap underlyingSwap() {
        return swap_;
    }

    //
    // overrides Instrument
    //

    @Override
    public boolean isExpired() /* @ReadOnly */ {
        final Date today = new Settings().evaluationDate();
        return exercise.lastDate().lt(today) || exercise.lastDate().eq(today);
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        // Chain through the underlying swap's setupArguments (handles legs etc.)
        swap_.setupArguments(args);

        QL.require(args instanceof IrregularSwaption.ArgumentsImpl, "wrong argument type");
        final IrregularSwaption.ArgumentsImpl arguments = (IrregularSwaption.ArgumentsImpl) args;

        arguments.swap = swap_;
        arguments.settlementType = settlementType_;
        arguments.exercise = exercise;
    }

    /**
     * Implied volatility scaffold. Mirrors the C++ signature but throws
     * {@link UnsupportedOperationException}: the helper class
     * {@code IrregularImpliedVolHelper} requires running a
     * {@code BlackSwaptionEngine} on an {@link IrregularSwap} (not yet
     * supported in Java because the engine is keyed on
     * {@link VanillaSwap}).
     */
    public double impliedVolatility(
            final double targetValue,
            final Handle<YieldTermStructure> discountCurve,
            final double guess,
            final double accuracy,
            final int maxEvaluations,
            final double minVol,
            final double maxVol) {
        calculate();
        QL.require(!isExpired(), "instrument expired");
        // TODO Phase 4i.5: port IrregularImpliedVolHelper once
        //      BlackSwaptionEngine accepts an arbitrary swap base type.
        throw new LibraryException(
                "IrregularSwaption.impliedVolatility not yet implemented "
              + "(see Phase 4i.5 carry-forward)");
    }

    public double impliedVolatility(
            final double targetValue,
            final Handle<YieldTermStructure> discountCurve,
            final double guess) {
        return impliedVolatility(targetValue, discountCurve, guess,
                1.0e-4, 100, 1.0e-7, 4.0);
    }

    //
    // public inner interfaces
    //

    /**
     * Marker interface for irregular-swaption arguments. Mirrors the C++
     * multiple-inheritance from {@code IrregularSwap::arguments} and
     * {@code Option::arguments}; Java exposes the union as direct fields on
     * {@link ArgumentsImpl}.
     */
    public interface Arguments extends IrregularSwap.Arguments, Option.Arguments {
        /* marker */
    }

    public interface Results extends Instrument.Results { /* marker */ }

    //
    // public inner classes
    //

    public static class ArgumentsImpl extends IrregularSwap.ArgumentsImpl
            implements IrregularSwaption.Arguments {

        public IrregularSwap swap;
        public IrregularSettlement.Type settlementType = IrregularSettlement.Type.Physical;
        public Exercise exercise;
        public Payoff payoff; // intentionally null for swaptions

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(swap != null, "Irregular swap not set");
            QL.require(exercise != null, "exercise not set");
        }
    }

    public static class ResultsImpl extends Instrument.ResultsImpl
            implements IrregularSwaption.Results {
        @Override
        public void reset() {
            super.reset();
        }
    }

    public abstract static class EngineImpl
            extends GenericEngine<IrregularSwaption.Arguments, IrregularSwaption.Results> {

        protected EngineImpl() {
            super(new IrregularSwaption.ArgumentsImpl(), new IrregularSwaption.ResultsImpl());
        }
    }
}
