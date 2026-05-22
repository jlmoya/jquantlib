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
 Copyright (C) 2014 Master IMAFA - Polytech'Nice Sophia - Université de Nice Sophia Antipolis
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.experimental.exoticoptions.PartialBarrier;
import org.jquantlib.experimental.exoticoptions.PartialTimeBarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.distributions.BivariateNormalDistribution;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Analytic engine for partial-time barrier options.
 * <p>
 * Mirrors {@code QuantLib::AnalyticPartialTimeBarrierOptionEngine} from
 * {@code ql/pricingengines/barrier/analyticpartialtimebarrieroptionengine.cpp} (v1.42.1).
 * <p>
 * Formulas from Haug, "Option Pricing Formulas".
 * <p>
 * Currently does not cover the case of knock-in partial-time end options.
 *
 * @author JQuantLib migration
 */
public class AnalyticPartialTimeBarrierOptionEngine extends PartialTimeBarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;

    // mutable state during a single calculate() call (so helper methods can access it)
    private PartialTimeBarrierOption.ArgumentsImpl currentArgs;
    /** Process used during the current calculate() call (process_ for calls, swapped-rates for puts). */
    private GeneralizedBlackScholesProcess currentProcess;

    public AnalyticPartialTimeBarrierOptionEngine(final GeneralizedBlackScholesProcess process) {
        this.process_ = process;
        this.process_.addObserver(this);
    }

    private static BarrierType getSymmetricBarrierType(final BarrierType bt) {
        return switch (bt) {
            case UpIn -> BarrierType.DownIn;
            case DownIn -> BarrierType.UpIn;
            case UpOut -> BarrierType.DownOut;
            case DownOut -> BarrierType.UpOut;
            default -> throw new LibraryException("unknown barrier type");
        };
    }

    @Override
    public void calculate() {
        final PartialTimeBarrierOption.ArgumentsImpl a = args();
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        QL.require(payoff.strike() > 0.0, "strike must be positive");

        final double spot = process_.x0();
        QL.require(spot > 0.0, "negative or null underlying given");

        // Symmetric barrier-type swap for puts (per C++)
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;
        // Save originals
        final BarrierType origBarrierType = a.barrierType;
        final double origBarrier = a.barrier;
        final org.jquantlib.instruments.Payoff origPayoff = a.payoff;

        try {
            if ( payoff.optionType() == org.jquantlib.instruments.Option.Type.Put ) {
                final double spotSq = spot * spot;
                final double callStrike = spotSq / payoff.strike();
                final PlainVanillaPayoff callPayoff = new PlainVanillaPayoff(org.jquantlib.instruments.Option.Type.Call,
                        callStrike);
                a.barrierType = getSymmetricBarrierType(origBarrierType);
                a.barrier = spotSq / origBarrier;
                a.payoff = callPayoff;
                // Put-call symmetry: build a process with risk-free and dividend swapped.
                // (Note: GeneralizedBlackScholesProcess constructor signature is
                // (stateVariable, dividendTS, riskFreeTS, blackVolTS) — we pass
                // process_.riskFreeRate() in the dividend slot and vice-versa.)
                @SuppressWarnings( "unchecked" )
                final Handle< ? extends Quote > stateVar = process_.stateVariable();
                final GeneralizedBlackScholesProcess callProcess = new GeneralizedBlackScholesProcess(stateVar,
                        process_.riskFreeRate(),       // in dividendYield slot
                        process_.dividendYield(),      // in riskFreeRate slot
                        process_.blackVolatility());
                this.currentArgs = a;
                this.currentProcess = callProcess;
                final double callValue = calculateInternal(callPayoff);
                r.value = (payoff.strike() / spot) * callValue;
            } else {
                this.currentArgs = a;
                this.currentProcess = process_;
                r.value = calculateInternal(payoff);
            }
        } finally {
            // Restore originals so further reads of arguments_ see what was set up
            a.barrierType = origBarrierType;
            a.barrier = origBarrier;
            a.payoff = origPayoff;
            this.currentArgs = null;
            this.currentProcess = null;
        }
    }

    private double calculateInternal(final PlainVanillaPayoff payoff) {
        final BarrierType barrierType = currentArgs.barrierType;
        final PartialBarrier barrierRange = currentArgs.barrierRange;
        final double r = riskFreeRateZero();
        final double q = dividendYieldZero();
        final double barrier = currentArgs.barrier;
        final double strike = payoff.strike();

        switch ( barrierType ) {
        case DownOut:
            switch ( barrierRange ) {
            case Start:
                return CA(1, barrier, strike, r, q);
            case EndB1:
                return CoB1(barrier, strike, r, q);
            case EndB2:
                return CoB2(BarrierType.DownOut, barrier, strike, r, q);
            default:
                throw new LibraryException("invalid barrier range");
            }
        case DownIn:
            switch ( barrierRange ) {
            case Start:
                return CIA(1, barrier, strike, r, q);
            case EndB1:
            case EndB2:
                throw new LibraryException("Down-and-in partial-time end barrier is not implemented");
            default:
                throw new LibraryException("invalid barrier range");
            }
        case UpOut:
            switch ( barrierRange ) {
            case Start:
                return CA(-1, barrier, strike, r, q);
            case EndB1:
                return CoB1(barrier, strike, r, q);
            case EndB2:
                return CoB2(BarrierType.UpOut, barrier, strike, r, q);
            default:
                throw new LibraryException("invalid barrier range");
            }
        case UpIn:
            switch ( barrierRange ) {
            case Start:
                return CIA(-1, barrier, strike, r, q);
            case EndB1:
            case EndB2:
                throw new LibraryException("Up-and-in partial-time end barrier is not implemented");
            default:
                throw new LibraryException("invalid barrier range");
            }
        default:
            throw new LibraryException("unknown barrier type");
        }
    }

    private double CoB2(final BarrierType barrierType, final double barrier, final double strike, final double r,
            final double q) {
        final double b = r - q;
        final double T = residualTime();
        final double S = underlying();
        final double mu_ = mu(strike, b);
        final double g1_ = g1(barrier, strike, b);
        final double g2_ = g2(barrier, strike, b);
        final double g3_ = g3(barrier, strike, b);
        final double g4_ = g4(barrier, strike, b);
        final double e1_ = e1(barrier, strike, b);
        final double e2_ = e2(barrier, strike, b);
        final double e3_ = e3(barrier, strike, b);
        final double e4_ = e4(barrier, strike, b);
        final double rho_ = rho();
        final double HSMu = HS(S, barrier, 2 * mu_);
        final double HSMu1 = HS(S, barrier, 2 * (mu_ + 1));
        final double X1 = strike * Math.exp(-r * T);

        if ( strike < barrier ) {
            switch ( barrierType ) {
            case DownOut: {
                double result = S * Math.exp((b - r) * T);
                result *= (M(g1_, e1_, rho_) - HSMu1 * M(g3_, -e3_, -rho_));
                result -= X1 * (M(g2_, e2_, rho_) - HSMu * M(g4_, -e4_, -rho_));
                return result;
            }
            case UpOut: {
                double result = S * Math.exp((b - r) * T);
                result *= (M(-g1_, -e1_, rho_) - HSMu1 * M(-g3_, e3_, -rho_));
                result -= X1 * (M(-g2_, -e2_, rho_) - HSMu * M(-g4_, e4_, -rho_));
                result -= S * Math.exp((b - r) * T) * (M(-d1(strike, b), -e1_, rho_) - HSMu1 * M(e3_,
                        -f1(barrier, strike, b), -rho_));
                result += X1 * (M(-d2(strike, b), -e2_, rho_) - HSMu * M(e4_, -f2(barrier, strike, b), -rho_));
                return result;
            }
            default:
                throw new LibraryException("invalid barrier type");
            }
        } else {
            throw new LibraryException("case of strike>barrier is not implemented for OutEnd B2 type");
        }
    }

    private double CoB1(final double barrier, final double strike, final double r, final double q) {
        final double b = r - q;
        final double T = residualTime();
        final double S = underlying();
        final double mu_ = mu(strike, b);
        final double g1_ = g1(barrier, strike, b);
        final double g2_ = g2(barrier, strike, b);
        final double g3_ = g3(barrier, strike, b);
        final double g4_ = g4(barrier, strike, b);
        final double e1_ = e1(barrier, strike, b);
        final double e2_ = e2(barrier, strike, b);
        final double e3_ = e3(barrier, strike, b);
        final double e4_ = e4(barrier, strike, b);
        final double rho_ = rho();
        final double HSMu = HS(S, barrier, 2 * mu_);
        final double HSMu1 = HS(S, barrier, 2 * (mu_ + 1));
        final double X1 = strike * Math.exp(-r * T);

        if ( strike > barrier ) {
            double result = S * Math.exp((b - r) * T);
            result *= (M(d1(strike, b), e1_, rho_) - HSMu1 * M(f1(barrier, strike, b), -e3_, -rho_));
            result -= X1 * (M(d2(strike, b), e2_, rho_) - HSMu * M(f2(barrier, strike, b), -e4_, -rho_));
            return result;
        } else {
            final double S1 = S * Math.exp((b - r) * T);
            double result = S1;
            result *= (M(-g1_, -e1_, rho_) - HSMu1 * M(-g3_, e3_, -rho_));
            result -= X1 * (M(-g2_, -e2_, rho_) - HSMu * M(-g4_, e4_, -rho_));
            result -= S1 * (M(-d1(strike, b), -e1_, rho_) - HSMu1 * M(-f1(barrier, strike, b), e3_, -rho_));
            result += X1 * (M(-d2(strike, b), -e2_, rho_) - HSMu * M(-f2(barrier, strike, b), e4_, -rho_));
            result += S1 * (M(g1_, e1_, rho_) - HSMu1 * M(g3_, -e3_, -rho_));
            result -= X1 * (M(g2_, e2_, rho_) - HSMu * M(g4_, -e4_, -rho_));
            return result;
        }
    }

    /**
     * eta = -1: Up-and-In Call; eta = 1: Down-and-In Call.
     */
    private double CIA(final int eta, final double barrier, final double strike, final double r, final double q) {
        final EuropeanExercise exercise = (EuropeanExercise) currentArgs.exercise;
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) currentArgs.payoff;
        final VanillaOption europeanOption = new VanillaOption(payoff, exercise);
        europeanOption.setPricingEngine(new AnalyticEuropeanEngine(currentProcess));
        return europeanOption.NPV() - CA(eta, barrier, strike, r, q);
    }

    private double CA(final int eta, final double barrier, final double strike, final double r, final double q) {
        // Partial-Time-Start- OUT  Call Option calculation
        final double b = r - q;
        final double rho_ = rho();
        final double T = residualTime();
        final double S = underlying();
        final double mu_ = mu(strike, b);
        final double e1_ = e1(barrier, strike, b);
        final double e2_ = e2(barrier, strike, b);
        final double e3_ = e3(barrier, strike, b);
        final double e4_ = e4(barrier, strike, b);
        final double HSMu = HS(S, barrier, 2 * mu_);
        final double HSMu1 = HS(S, barrier, 2 * (mu_ + 1));

        double result = S * Math.exp((b - r) * T);
        result *= (M(d1(strike, b), eta * e1_, eta * rho_) - HSMu1 * M(f1(barrier, strike, b), eta * e3_, eta * rho_));
        result -= (strike * Math.exp(-r * T) * (M(d2(strike, b), eta * e2_, eta * rho_) - HSMu * M(
                f2(barrier, strike, b), eta * e4_, eta * rho_)));
        return result;
    }

    //
    // helpers (mirror C++ private members)
    //

    private double underlying() {
        return currentProcess.x0();
    }

    private double residualTime() {
        return currentProcess.time(currentArgs.exercise.lastDate());
    }

    private double coverEventTime() {
        return currentProcess.time(currentArgs.coverEventDate);
    }

    private double volatility(final double t, final double strike) {
        return currentProcess.blackVolatility().currentLink().blackVol(t, strike);
    }

    private double riskFreeRateZero() {
        return currentProcess.riskFreeRate().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double dividendYieldZero() {
        return currentProcess.dividendYield().currentLink()
                .zeroRate(residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double f1(final double barrier, final double strike, final double b) {
        final double S = underlying();
        final double T = residualTime();
        final double sigma = volatility(T, strike);
        return (Math.log(S / strike) + 2 * Math.log(barrier / S) + (b + (sigma * sigma) / 2) * T) / (sigma * Math.sqrt(
                T));
    }

    private double f2(final double barrier, final double strike, final double b) {
        final double T = residualTime();
        return f1(barrier, strike, b) - volatility(T, strike) * Math.sqrt(T);
    }

    private double M(final double a, final double b, final double rho) {
        final BivariateNormalDistribution cmlNormDist = new BivariateNormalDistribution(rho);
        return cmlNormDist.op(a, b);
    }

    private double rho() {
        return Math.sqrt(coverEventTime() / residualTime());
    }

    private double mu(final double strike, final double b) {
        final double vol = volatility(coverEventTime(), strike);
        return (b - (vol * vol) / 2) / (vol * vol);
    }

    private double d1(final double strike, final double b) {
        final double T2 = residualTime();
        final double vol = volatility(T2, strike);
        return (Math.log(underlying() / strike) + (b + vol * vol / 2) * T2) / (Math.sqrt(T2) * vol);
    }

    private double d2(final double strike, final double b) {
        final double T2 = residualTime();
        final double vol = volatility(T2, strike);
        return d1(strike, b) - vol * Math.sqrt(T2);
    }

    private double e1(final double barrier, final double strike, final double b) {
        final double T1 = coverEventTime();
        final double vol = volatility(T1, strike);
        return (Math.log(underlying() / barrier) + (b + vol * vol / 2) * T1) / (Math.sqrt(T1) * vol);
    }

    private double e2(final double barrier, final double strike, final double b) {
        final double T1 = coverEventTime();
        final double vol = volatility(T1, strike);
        return e1(barrier, strike, b) - vol * Math.sqrt(T1);
    }

    private double e3(final double barrier, final double strike, final double b) {
        final double T1 = coverEventTime();
        final double vol = volatility(T1, strike);
        return e1(barrier, strike, b) + (2 * Math.log(barrier / underlying()) / (vol * Math.sqrt(T1)));
    }

    private double e4(final double barrier, final double strike, final double b) {
        final double t = coverEventTime();
        return e3(barrier, strike, b) - volatility(t, strike) * Math.sqrt(t);
    }

    private double g1(final double barrier, final double strike, final double b) {
        final double T2 = residualTime();
        final double vol = volatility(T2, strike);
        return (Math.log(underlying() / barrier) + (b + vol * vol / 2) * T2) / (Math.sqrt(T2) * vol);
    }

    private double g2(final double barrier, final double strike, final double b) {
        final double T2 = residualTime();
        final double vol = volatility(T2, strike);
        return g1(barrier, strike, b) - vol * Math.sqrt(T2);
    }

    private double g3(final double barrier, final double strike, final double b) {
        final double T2 = residualTime();
        final double vol = volatility(T2, strike);
        return g1(barrier, strike, b) + (2 * Math.log(barrier / underlying()) / (vol * Math.sqrt(T2)));
    }

    private double g4(final double barrier, final double strike, final double b) {
        final double T2 = residualTime();
        final double vol = volatility(T2, strike);
        return g3(barrier, strike, b) - vol * Math.sqrt(T2);
    }

    private double HS(final double S, final double H, final double power) {
        return Math.pow(H / S, power);
    }
}
