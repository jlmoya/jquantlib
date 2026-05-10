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
 Copyright (C) 2025 William Day
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.experimental.exoticoptions.SoftBarrierOption;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.calendars.Target;

/**
 * Pricing engine for soft barrier european options using an analytical formula.
 * <p>
 * Mirrors {@code QuantLib::AnalyticSoftBarrierEngine} from
 * {@code ql/pricingengines/barrier/analyticsoftbarrierengine.cpp} (v1.42.1).
 * <p>
 * Formulas are taken from "The complete guide to option pricing formulas 2nd Ed",
 * E.G. Haug, p.165. Implements a closed form solution for soft barrier options
 * originally introduced by Hart and Ross (1994).
 *
 * @author JQuantLib migration
 */
public class AnalyticSoftBarrierEngine extends SoftBarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final CumulativeNormalDistribution f_;

    public AnalyticSoftBarrierEngine(final GeneralizedBlackScholesProcess process) {
        this.process_ = process;
        this.f_ = new CumulativeNormalDistribution();
        this.process_.addObserver(this);
    }

    @Override
    public void calculate() {
        final SoftBarrierOption.ArgumentsImpl a = args();
        final OneAssetOption.ResultsImpl res = (OneAssetOption.ResultsImpl) results_;

        // Market data
        final double S = underlying();
        final double X = strike();
        double r = riskFreeRate();
        final double q = dividendYield();
        final double sigma = volatility();

        // Barrier parameters
        final double U = barrierHi();
        final double L = barrierLo();
        final BarrierType barrierType = a.barrierType;

        // Stability tweak for r and q
        final double epsilon = 1e-6;
        if (Math.abs(r - q) < 1e-10) {
            r = q + epsilon; // Avoids mu = 0.5 singularity
        }

        // Option parameters
        final double T = residualTime();
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) a.payoff;
        final Option.Type optionType = payoff.optionType();
        final int eta = (optionType == Option.Type.Call) ? 1 : -1;
        final double b = r - q; // cost of carry

        validateInputs(S, X, r, q, T, U, L, optionType, barrierType, sigma);

        final boolean isKnockedIn = (barrierType == BarrierType.DownIn && S <= L)
                || (barrierType == BarrierType.UpIn && S >= U);
        final boolean isKnockedOut = (barrierType == BarrierType.DownOut && S <= L)
                || (barrierType == BarrierType.UpOut && S >= U);

        final boolean isSingleBarrier = (Math.abs(U - L) < 1e-4);

        // edge case 1: fully knocked in options should be priced as vanilla
        if (isKnockedIn) {
            res.value = vanillaEquivalent();
            return;
        }

        // edge case 2: knocked out options are worthless
        if (isKnockedOut) {
            res.value = 0.0;
            return;
        }

        // edge case 3: Haug formula breaks when U=L, use single barrier option formula instead
        if (isSingleBarrier) {
            res.value = standardBarrierEquivalent();
            return;
        }

        // soft barrier pricing logic
        final double w = knockInValue(S, X, r, sigma, T, U, L, b, optionType, eta);
        res.value = (barrierType == BarrierType.DownIn || barrierType == BarrierType.UpIn)
                ? w                              // knock in price
                : vanillaEquivalent() - w;       // knock out price
    }


    /**
     * Implements the formula to calculate 'w' from the Haug textbook, used in soft barrier pricing.
     */
    private double knockInValue(final double S, final double X, final double r,
                                 final double sigma, final double T,
                                 final double U, final double L, final double b,
                                 final Option.Type optionType, final int eta) {
        // constant terms
        final double mu = (b + 0.5 * sigma * sigma) / (sigma * sigma);
        final double sqrtT = Math.sqrt(T);
        final double lambda1 = Math.exp(-0.5 * sigma * sigma * T * (mu + 0.5) * (mu - 0.5));
        final double lambda2 = Math.exp(-0.5 * sigma * sigma * T * (mu - 0.5) * (mu - 1.5));
        final double SX = S * X;
        final double logU2_SX = Math.log((U * U) / SX);
        final double logL2_SX = Math.log((L * L) / SX);

        // d and e terms
        final double d1 = logU2_SX / (sigma * sqrtT) + mu * sigma * sqrtT;
        final double d2 = d1 - (mu + 0.5) * sigma * sqrtT;
        final double d3 = logU2_SX / (sigma * sqrtT) + (mu - 1) * sigma * sqrtT;
        final double d4 = d3 - (mu - 0.5) * sigma * sqrtT;

        final double e1 = logL2_SX / (sigma * sqrtT) + mu * sigma * sqrtT;
        final double e2 = e1 - (mu + 0.5) * sigma * sqrtT;
        final double e3 = logL2_SX / (sigma * sqrtT) + (mu - 1) * sigma * sqrtT;
        final double e4 = e3 - (mu - 0.5) * sigma * sqrtT;

        final double Nd1 = f_.op(eta * d1);
        final double Nd2 = f_.op(eta * d2);
        final double Nd3 = f_.op(eta * d3);
        final double Nd4 = f_.op(eta * d4);
        final double Ne1 = f_.op(eta * e1);
        final double Ne2 = f_.op(eta * e2);
        final double Ne3 = f_.op(eta * e3);
        final double Ne4 = f_.op(eta * e4);

        // term 1
        double term1 = eta * S * Math.exp((b - r) * T) * Math.pow(S, -2.0 * mu)
                * Math.pow(SX, mu + 0.5) / (2.0 * (mu + 0.5));
        term1 *= Math.pow(U * U / SX, mu + 0.5) * Nd1 - lambda1 * Nd2
                - Math.pow(L * L / SX, mu + 0.5) * Ne1 + lambda1 * Ne2;

        // term 2
        double term2 = eta * X * Math.exp(-r * T) * Math.pow(S, -2.0 * (mu - 1))
                * Math.pow(SX, mu - 0.5) / (2.0 * (mu - 0.5));
        term2 *= Math.pow(U * U / SX, mu - 0.5) * Nd3 - lambda2 * Nd4
                - Math.pow(L * L / SX, mu - 0.5) * Ne3 + lambda2 * Ne4;

        // final result
        return (1.0 / (U - L)) * (term1 - term2);
    }


    /**
     * Helper function to check inputs are reasonable.
     */
    private void validateInputs(final double S, final double X, final double r, final double q,
                                final double T, final double U, final double L,
                                final Option.Type optionType, final BarrierType barrierType,
                                final double sigma) {
        QL.require(S > 0.0, "Spot price must be > 0");
        QL.require(X > 0.0, "Strike price must be > 0");
        QL.require(T > 0.0, "Option must have time to maturity > 0");
        QL.require(sigma > 0, "Volatility must be > 0");
        QL.require(optionType == Option.Type.Call || optionType == Option.Type.Put,
                "Invalid option type");
        QL.require(r <= 1.0 && r >= -0.05, "Interest rate must be between -5% and 100%");
        QL.require(q <= 1.0 && q >= -0.1, "Dividend yield must be between -10% and 100%");

        QL.require(barrierType == BarrierType.DownIn
                || barrierType == BarrierType.DownOut
                || barrierType == BarrierType.UpIn
                || barrierType == BarrierType.UpOut,
                "Invalid barrier type");
        QL.require(!Double.isNaN(L), "no low barrier given");
        QL.require(!Double.isNaN(U), "no high barrier given");
        QL.require(U > 0.0 && L > 0.0, "Barrier levels must be positive");
        QL.require(U >= L, "Upper barrier must be greater than or equal to lower barrier");
    }


    //
    // helpers (mirror C++ private members)
    //

    private double underlying() { return process_.x0(); }

    private double strike() {
        final SoftBarrierOption.ArgumentsImpl a = args();
        QL.require(a.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        return ((PlainVanillaPayoff) a.payoff).strike();
    }

    private double residualTime() {
        return process_.time(args().exercise.lastDate());
    }

    private double volatility() {
        return process_.blackVolatility().currentLink().blackVol(residualTime(), strike());
    }

    private double stdDeviation() {
        return volatility() * Math.sqrt(residualTime());
    }

    private double barrierLo() { return args().barrierLo; }

    private double barrierHi() { return args().barrierHi; }

    private double riskFreeRate() {
        return process_.riskFreeRate().currentLink().zeroRate(
                residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double riskFreeDiscount() {
        return process_.riskFreeRate().currentLink().discount(residualTime());
    }

    private double dividendYield() {
        return process_.dividendYield().currentLink().zeroRate(
                residualTime(), Compounding.Continuous, Frequency.NoFrequency, false).rate();
    }

    private double dividendDiscount() {
        return process_.dividendYield().currentLink().discount(residualTime());
    }


    private double vanillaEquivalent() {
        final StrikedTypePayoff payoff = (StrikedTypePayoff) args().payoff;
        final double forwardPrice = underlying() * dividendDiscount() / riskFreeDiscount();
        final BlackCalculator black = new BlackCalculator(payoff, forwardPrice, stdDeviation(), riskFreeDiscount());
        return Math.max(black.value(), 0.0);
    }

    private double standardBarrierEquivalent() {
        final SoftBarrierOption.ArgumentsImpl a = args();
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        QL.require(payoff != null, "Payoff could not be cast to StrikedTypePayoff");

        final BarrierOption tempOption = new BarrierOption(
                a.barrierType, a.barrierHi, 0.0, payoff, a.exercise);

        final double spotVal = underlying();
        final double qVal = dividendYield();
        final double rVal = riskFreeRate();
        final double volVal = volatility();

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(spotVal));
        final Handle<YieldTermStructure> qHandle = new Handle<YieldTermStructure>(
                new FlatForward(0, new Target(), qVal, new Actual360()));
        final Handle<YieldTermStructure> rHandle = new Handle<YieldTermStructure>(
                new FlatForward(0, new Target(), rVal, new Actual360()));
        final Handle<BlackVolTermStructure> volHandle = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(0, new Target(), volVal, new Actual360()));

        final GeneralizedBlackScholesProcess process =
                new GeneralizedBlackScholesProcess(spot, qHandle, rHandle, volHandle);
        tempOption.setPricingEngine(new AnalyticBarrierEngine(process));

        return Math.max(tempOption.NPV(), 0.0);
    }
}
