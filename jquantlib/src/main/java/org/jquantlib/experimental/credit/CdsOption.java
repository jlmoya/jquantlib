/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2008 Roland Stamm
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.NullPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.Protection;
import org.jquantlib.lang.reflect.ReflectConstants;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Credit Default Swap option.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::CdsOption}
 * ({@code ql/experimental/credit/cdsoption.{hpp,cpp}}).
 *
 * <p>The side of the swaption is determined by the side of the
 * underlying CDS. A receiver CDS option is a right to buy an underlying
 * CDS selling protection (receiving coupon); a payer is the right to buy
 * an underlying CDS buying protection (paying coupon).
 *
 * <p><b>Java MI workaround</b>: the C++ {@code CdsOption::arguments}
 * inherits both {@code CreditDefaultSwap::arguments} and
 * {@code Option::arguments}. Java does not allow MI on classes, so the
 * port uses composition: {@link ArgumentsImpl} extends
 * {@code CreditDefaultSwap.ArgumentsImpl} and adds the option payload
 * (payoff, exercise, swap, knocksOut) as additional fields.
 *
 * <p>Phase 4m.5 work-item 2.
 */
public class CdsOption extends Option {

    private final CreditDefaultSwap swap;
    private final boolean knocksOut;

    private double riskyAnnuity = Constants.NULL_REAL;

    public CdsOption(final CreditDefaultSwap swap,
                     final Exercise exercise,
                     final boolean knocksOut) {
        super(new NullPayoff(), exercise);
        this.swap = swap;
        this.knocksOut = knocksOut;
        QL.require(swap.side() == Protection.Side.Buyer || knocksOut,
                "receiver CDS options must knock out");
        QL.require(swap.upfront() == null,
                "underlying must be running-spread only");
        swap.addObserver(this);
    }

    public CdsOption(final CreditDefaultSwap swap, final Exercise exercise) {
        this(swap, exercise, true);
    }

    @Override
    public boolean isExpired() {
        // detail::simple_event(exercise.dates().back()).hasOccurred()
        return exercise.lastDate().compareTo(new Settings().evaluationDate()) <= 0;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        riskyAnnuity = 0.0;
    }

    @Override
    protected void setupArguments(final PricingEngine.Arguments args) {
        // Step 1: have the underlying CDS populate its CDS-arguments fields
        swap.setupArguments(args);
        // Step 2: Option fields (payoff/exercise) — direct set, avoiding Option.setupArguments
        QL.require(args instanceof CdsOption.ArgumentsImpl,
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        final CdsOption.ArgumentsImpl a = (CdsOption.ArgumentsImpl) args;
        a.payoff = this.payoff;
        a.exercise = this.exercise;
        a.swap = this.swap;
        a.knocksOut = this.knocksOut;
    }

    @Override
    protected void fetchResults(final PricingEngine.Results r) {
        super.fetchResults(r);
        QL.require(r instanceof CdsOption.ResultsImpl,
                ReflectConstants.WRONG_ARGUMENT_TYPE);
        riskyAnnuity = ((CdsOption.ResultsImpl) r).riskyAnnuity;
    }

    public CreditDefaultSwap underlyingSwap() {
        return swap;
    }

    public double atmRate() {
        return swap.fairSpread();
    }

    public double riskyAnnuity() {
        calculate();
        QL.require(riskyAnnuity != Constants.NULL_REAL, "risky annuity not provided");
        return riskyAnnuity;
    }

    public double impliedVolatility(final double targetValue,
                                    final Handle<YieldTermStructure> termStructure,
                                    final Handle<DefaultProbabilityTermStructure> probability,
                                    final double recoveryRate,
                                    final double accuracy,
                                    final int maxEvaluations,
                                    final double minVol,
                                    final double maxVol) {
        calculate();
        QL.require(!isExpired(), "instrument expired");

        final double guess = 0.10;
        final ImpliedVolHelper f = new ImpliedVolHelper(this, probability, recoveryRate,
                termStructure, targetValue);
        final Brent solver = new Brent();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(f, accuracy, guess, minVol, maxVol);
    }

    public double impliedVolatility(final double targetValue,
                                    final Handle<YieldTermStructure> termStructure,
                                    final Handle<DefaultProbabilityTermStructure> probability,
                                    final double recoveryRate) {
        return impliedVolatility(targetValue, termStructure, probability, recoveryRate,
                1.0e-4, 100, 1.0e-7, 4.0);
    }

    /**
     * CDS-option arguments — composition over MI. Extends
     * {@code CreditDefaultSwap.ArgumentsImpl} and adds option fields.
     */
    public static class ArgumentsImpl extends CreditDefaultSwap.ArgumentsImpl {
        public Payoff payoff;
        public Exercise exercise;
        public CreditDefaultSwap swap;
        public boolean knocksOut;

        @Override
        public void validate() {
            super.validate();
            QL.require(swap != null, "CDS not set");
            QL.require(exercise != null, "exercise not set");
        }
    }

    /** CDS-option results — extends Instrument.ResultsImpl + adds riskyAnnuity. */
    public static class ResultsImpl extends org.jquantlib.instruments.Instrument.ResultsImpl {
        public double riskyAnnuity = Constants.NULL_REAL;

        @Override
        public void reset() {
            super.reset();
            riskyAnnuity = Constants.NULL_REAL;
        }
    }

    //
    // private — implied vol root-finder
    //

    private static final class ImpliedVolHelper implements Ops.DoubleOp {
        private final SimpleQuote vol = new SimpleQuote(0.0);
        private final BlackCdsOptionEngine engine;
        private final double targetValue;
        private final CdsOption.ResultsImpl results;

        ImpliedVolHelper(final CdsOption opt,
                         final Handle<DefaultProbabilityTermStructure> probability,
                         final double recoveryRate,
                         final Handle<YieldTermStructure> termStructure,
                         final double targetValue) {
            this.targetValue = targetValue;
            this.engine = new BlackCdsOptionEngine(probability, recoveryRate,
                    termStructure, new Handle<Quote>(vol));
            opt.setupArguments(engine.getArguments());
            this.results = (CdsOption.ResultsImpl) engine.getResults();
        }

        @Override
        public double op(final double x) {
            vol.setValue(x);
            engine.calculate();
            return results.value - targetValue;
        }
    }
}
