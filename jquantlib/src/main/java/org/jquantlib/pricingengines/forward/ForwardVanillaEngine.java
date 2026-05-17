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
package org.jquantlib.pricingengines.forward;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.ForwardVanillaOption;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.ImpliedVolTermStructure;
import org.jquantlib.termstructures.yieldcurves.ImpliedTermStructure;
import org.jquantlib.time.Frequency;

/**
 * Forward (strike-resetting) vanilla option engine using analytic
 * Black-Scholes pricing on the implied (post-reset) GBS process.
 *
 * <p>Phase 5i.5-MGR Java specialisation of C++
 * {@code ForwardVanillaEngine<AnalyticEuropeanEngine>} (v1.42.1
 * ql/pricingengines/forward/forwardengine.hpp).
 *
 * <p>The full C++ form is a template over the inner engine type. Java's
 * default still binds the canonical {@link AnalyticEuropeanEngine}
 * specialisation, but the inner engine can be swapped via the protected
 * {@link #buildInnerEngine(GeneralizedBlackScholesProcess)} factory hook
 * (Phase 5e.5b-CFC-d-58) — used by the binomial-inner-engine variant in
 * {@code ForwardOptionTest.testGreeksInitialization}.
 */
public class ForwardVanillaEngine extends ForwardVanillaOption.EngineImpl {

    protected final GeneralizedBlackScholesProcess process_;
    protected OneAssetOption.EngineImpl originalEngine_;
    protected OneAssetOption.ArgumentsImpl originalArguments_;
    protected OneAssetOption.ResultsImpl   originalResults_;

    public ForwardVanillaEngine(final GeneralizedBlackScholesProcess process) {
        super();
        this.process_ = process;
        this.process_.addObserver(this);
    }

    /** Build inner engine and set up its arguments from {@link #arguments_}. */
    protected void setup() {
        final ForwardVanillaOption.ArgumentsImpl args =
                (ForwardVanillaOption.ArgumentsImpl) arguments_;

        QL.require(args.payoff instanceof StrikedTypePayoff, "wrong payoff given");
        final StrikedTypePayoff argPayoff = (StrikedTypePayoff) args.payoff;

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(
                argPayoff.optionType(),
                args.moneyness * process_.x0());

        final Handle<? extends Quote> spot = process_.stateVariable();
        QL.require(spot.currentLink().value() > 0.0, "negative or null underlying given");

        final Handle<YieldTermStructure> dividendYield = new Handle<YieldTermStructure>(
                new ImpliedTermStructure<YieldTermStructure>(
                        process_.dividendYield(), args.resetDate));
        final Handle<YieldTermStructure> riskFreeRate = new Handle<YieldTermStructure>(
                new ImpliedTermStructure<YieldTermStructure>(
                        process_.riskFreeRate(), args.resetDate));
        final Handle<BlackVolTermStructure> blackVolatility = new Handle<BlackVolTermStructure>(
                new ImpliedVolTermStructure(process_.blackVolatility(), args.resetDate));

        final GeneralizedBlackScholesProcess fwdProcess =
                new GeneralizedBlackScholesProcess(spot, dividendYield,
                        riskFreeRate, blackVolatility);

        originalEngine_ = buildInnerEngine(fwdProcess);
        originalArguments_ = (OneAssetOption.ArgumentsImpl) originalEngine_.getArguments();
        originalResults_   = (OneAssetOption.ResultsImpl)   originalEngine_.getResults();

        originalArguments_.payoff = payoff;
        originalArguments_.exercise = args.exercise;
        originalArguments_.validate();
    }

    /**
     * Factory hook for the inner pricing engine. Defaults to
     * {@link AnalyticEuropeanEngine} (the C++ canonical
     * {@code ForwardVanillaEngine<AnalyticEuropeanEngine>} specialisation).
     *
     * <p>Subclasses can override to substitute any
     * {@link OneAssetOption.EngineImpl} (e.g. a binomial engine),
     * matching the C++ template parameter {@code Engine}.
     */
    protected OneAssetOption.EngineImpl buildInnerEngine(
            final GeneralizedBlackScholesProcess fwdProcess) {
        return new AnalyticEuropeanEngine(fwdProcess);
    }

    @Override
    public void calculate() {
        setup();
        // Run the inner engine via a synthetic VanillaOption to drive its
        // calculate() path (matches C++ originalEngine_->calculate()).
        final VanillaOption opt = new VanillaOption(
                (StrikedTypePayoff) originalArguments_.payoff,
                originalArguments_.exercise);
        opt.setPricingEngine(originalEngine_);
        opt.NPV();   // forces calculate
        getOriginalResults();
    }

    protected void getOriginalResults() {
        final ForwardVanillaOption.ArgumentsImpl args =
                (ForwardVanillaOption.ArgumentsImpl) arguments_;
        final ForwardVanillaOption.ResultsImpl r =
                (ForwardVanillaOption.ResultsImpl) results_;
        final org.jquantlib.instruments.Option.GreeksImpl rg = r.greeks();

        final DayCounter rfdc  = process_.riskFreeRate().currentLink().dayCounter();
        final DayCounter divdc = process_.dividendYield().currentLink().dayCounter();
        final double resetTime = rfdc.yearFraction(
                process_.riskFreeRate().currentLink().referenceDate(), args.resetDate);
        final double discQ = process_.dividendYield().currentLink().discount(args.resetDate);

        final org.jquantlib.instruments.Option.GreeksImpl ig = originalResults_.greeks();
        final org.jquantlib.instruments.Option.MoreGreeksImpl im = originalResults_.moreGreeks();

        r.value = discQ * originalResults_.value;

        if (!isNull(ig.delta) && !isNull(im.strikeSensitivity)) {
            rg.delta = discQ * (ig.delta + args.moneyness * im.strikeSensitivity);
        } else {
            rg.delta = Constants.NULL_REAL;
        }
        rg.gamma = 0.0;
        rg.theta = process_.dividendYield().currentLink()
                .zeroRate(args.resetDate, divdc, Compounding.Continuous, Frequency.NoFrequency)
                .rate() * r.value;
        if (!isNull(ig.vega)) {
            rg.vega = discQ * ig.vega;
        }
        if (!isNull(ig.rho)) {
            rg.rho = discQ * ig.rho;
        }
        if (!isNull(ig.dividendRho)) {
            rg.dividendRho = -resetTime * r.value + discQ * ig.dividendRho;
        }
    }

    protected static boolean isNull(final double x) {
        return x == Constants.NULL_REAL || Double.isNaN(x);
    }
}
