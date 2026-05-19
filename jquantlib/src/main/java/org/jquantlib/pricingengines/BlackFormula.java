/*
 Copyright (C) 2008 Richard Gomes

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
 Copyright (C) 2007 Cristina Duminuco
 Copyright (C) 2007 Chiara Fornarola
 Copyright (C) 2003, 2004, 2005, 2006 Ferdinando Ametrano
 Copyright (C) 2006 Mark Joshi
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2006 StatPro Italia srl

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

package org.jquantlib.pricingengines;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.annotation.DiscountFactor;
import org.jquantlib.lang.annotation.NonNegative;
import org.jquantlib.lang.annotation.Real;
import org.jquantlib.lang.annotation.StdDev;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.Derivative;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.solvers1D.NewtonSafe;

/**
 *
 * Black 1976 formula
 *
 * @author Richard Gomes
 * @author Srinivas Hasti
 */
// TODO: adjust formulas (LaTeX)
public class BlackFormula {

    /** {@code phi(0) = 1/sqrt(2*pi)}; standard normal pdf at zero. */
    private static final double BACHELIER_PHI_AT_ZERO = 1.0 / Math.sqrt(2.0 * Math.PI);

    /**
     * Black 1976 formula
     *
     * @note instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormula(final Option.Type optionType, @Real final double strike,
            @Real final double forward, @StdDev final double stddev) {

        return blackFormula(optionType, strike, forward, stddev, 1.0, 0.0);
    }

    /**
     * Black 1976 formula
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormula(final Option.Type optionType, @Real final double strike,
            @Real final double forward, @StdDev final double stddev, @DiscountFactor final double discount) {

        return blackFormula(optionType, strike, forward, stddev, discount, 0.0);
    }

    // ---
    // ---
    // ---

    /**
     *
     * Black 1976 formula
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormula(final Option.Type optionType, @Real double strike, @Real double forward,
            @StdDev final double stddev, @DiscountFactor final double discount, @Real final double displacement) {

        // strike may be negative when displacement > 0; the shifted strike must be non-negative.
        // Forward may be negative when displacement > |forward|; the shifted forward must be positive.
        // Mirrors C++ QuantLib v1.42.1 blackFormula::checkParameters which checks after shifting.
        QL.require(displacement >= 0.0, "displacement must be non-negative"); // TODO: message
        QL.require(strike + displacement >= 0.0, "strike+displacement must be non-negative"); // TODO: message
        QL.require(forward + displacement > 0.0, "forward+displacement must be positive"); // TODO: message
        QL.require(stddev >= 0.0, "stddev must be non-negative"); // TODO: message
        QL.require(discount > 0.0, "discount must be positive"); // TODO: message

        // Note: zero-stdDev intrinsic uses unshifted (forward - strike) per C++ blackFormula:75
        if ( stddev == 0.0 )
            return Math.max((forward - strike) * optionType.toInteger(), (0.0d)) * discount;
        forward = forward + displacement;
        strike = strike + displacement;

        if ( strike == 0.0 ) // strike=0 iff displacement=0
            return (optionType == Option.Type.Call ? forward * discount : 0.0);

        @Real
        final double d1 = Math.log(forward / strike) / stddev + 0.5 * stddev;
        @Real
        final double d2 = d1 - stddev;

        // TODO: code review
        final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        @Real
        final double result =
                discount * optionType.toInteger() * (forward * phi.op(optionType.toInteger() * d1) - strike * phi.op(
                        optionType.toInteger() * d2));

        if ( result >= 0.0 )
            return result;
        throw new ArithmeticException("a negative value was calculated"); // TODO: message
    }

    /**
     * Black 1976 formula
     *
     * @note instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormula(final PlainVanillaPayoff payoff, @Real final double strike,
            @Real final double forward, @StdDev final double stddev) {

        return blackFormula(payoff, strike, forward, stddev, 1.0, 0.0);
    }

    /**
     * Black 1976 formula
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormula(final PlainVanillaPayoff payoff, @Real final double strike,
            @Real final double forward, @StdDev final double stddev, @DiscountFactor final double discount) {

        return blackFormula(payoff, strike, forward, stddev, discount, 0.0);
    }

    // ---
    // ---
    // ---

    /**
     *
     * Black 1976 formula
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormula(final PlainVanillaPayoff payoff, @Real final double strike,
            @Real final double forward, @StdDev final double stddev, @DiscountFactor final double discount,
            @Real final double displacement) {

        return blackFormula(payoff.optionType(), payoff.strike(), forward, stddev, discount, displacement);
    }

    /**
     * Approximated Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity).
     * <p>
     * It is calculated using Brenner and Subrahmanyan (1988) and Feinstein (1988) approximation for at-the-money
     * forward option, with the extended moneyness approximation by Corrado and Miller (1996)
     */

    public static /*@Real*/ double blackFormulaImpliedStdDevApproximation(final Option.Type optionType,
            @Real final double strike, @Real final double forward, @Real final double blackPrice) {

        return blackFormulaImpliedStdDevApproximation(optionType, strike, forward, blackPrice, 1.0, 0.0);
    }

    /**
     * Approximated Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity).
     * <p>
     * It is calculated using Brenner and Subrahmanyan (1988) and Feinstein (1988) approximation for at-the-money
     * forward option, with the extended moneyness approximation by Corrado and Miller (1996)
     */

    public static /*@Real*/ double blackFormulaImpliedStdDevApproximation(final Option.Type optionType,
            @Real final double strike, @Real final double forward, @Real final double blackPrice,
            @DiscountFactor final double discount) {

        return blackFormulaImpliedStdDevApproximation(optionType, strike, forward, blackPrice, discount, 0.0);
    }

    // ---
    // ---
    // ---

    /**
     * Approximated Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity).
     * <p>
     * It is calculated using Brenner and Subrahmanyan (1988) and Feinstein (1988) approximation for at-the-money
     * forward option, with the extended moneyness approximation by Corrado and Miller (1996)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDevApproximation(final Option.Type optionType,
            @Real double strike, @Real double forward, @Real final double blackPrice,
            @DiscountFactor final double discount, @Real final double displacement) {

        // strike may be negative when displacement > 0; the shifted strike must be non-negative.
        // Mirrors C++ QuantLib v1.42.1 blackFormulaImpliedStdDevApproximation check (Phase 2o A.2).
        QL.require(strike + displacement >= 0.0, "strike+displacement must be non-negative"); // TODO: message
        QL.require(forward > 0.0, "forward must be positive"); // TODO: message
        QL.require(displacement >= 0.0, "displacement must be non-negative"); // TODO: message
        QL.require(blackPrice >= 0.0, "blackPrice must be non-negative"); // TODO: message
        QL.require(discount > 0.0, "discount must be positive"); // TODO: message

        double stddev;
        forward = forward + displacement;
        strike = strike + displacement;
        if ( Closeness.isClose(strike, forward) )
            // Brenner-Subrahmanyan (1988) and Feinstein (1988) ATM approx.
            stddev = blackPrice / discount * Math.sqrt(2.0 * Math.PI) / forward;
        else {
            // Corrado and Miller extended moneyness approximation
            final double moneynessDelta = optionType.toInteger() * (forward - strike);
            final double moneynessDelta_2 = moneynessDelta / 2.0;
            double temp = blackPrice / discount - moneynessDelta_2;
            final double moneynessDelta_PI = moneynessDelta * moneynessDelta / Math.PI;
            double temp2 = temp * temp - moneynessDelta_PI;
            if ( temp2 < 0.0 )
                // approximation breaks down, 2 alternatives:
                // 1. zero it
                temp2 = 0.0;
            // 2. Manaster-Koehler (1982) efficient Newton-Raphson seed
            // return std::fabs(std::log(forward/strike))*std::sqrt(2.0); -- commented out in original C++
            temp2 = Math.sqrt(temp2);
            temp += temp2;
            temp *= Math.sqrt(2.0 * Math.PI);
            stddev = temp / (forward + strike);
        }

        if ( stddev >= 0.0 )
            return stddev;
        throw new ArithmeticException("a negative value was calculated"); // TODO: message
    }

    /**
     * Approximated Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity).
     * <p>
     * It is calculated using Brenner and Subrahmanyan (1988) and Feinstein (1988) approximation for at-the-money
     * forward option, with the extended moneyness approximation by Corrado and Miller (1996)
     */

    public static /*@Real*/ double blackFormulaImpliedStdDevApproximation(final PlainVanillaPayoff payoff,
            @Real final double strike, @Real final double forward, @Real final double blackPrice) {

        // TODO : complete
        return blackFormulaImpliedStdDevApproximation(payoff, strike, forward, blackPrice, 1.0, 0.0);
    }

    /**
     * Approximated Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity).
     * <p>
     * It is calculated using Brenner and Subrahmanyan (1988) and Feinstein (1988) approximation for at-the-money
     * forward option, with the extended moneyness approximation by Corrado and Miller (1996)
     */

    public static /*@Real*/ double blackFormulaImpliedStdDevApproximation(final PlainVanillaPayoff payoff,
            @Real final double strike, @Real final double forward, @Real final double blackPrice,
            @DiscountFactor final double discount) {

        // TODO : complete
        return blackFormulaImpliedStdDevApproximation(payoff, strike, forward, blackPrice, discount, 0.0);
    }

    // ---
    // ---
    // ---

    /**
     * Approximated Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity).
     * <p>
     * It is calculated using Brenner and Subrahmanyan (1988) and Feinstein (1988) approximation for at-the-money
     * forward option, with the extended moneyness approximation by Corrado and Miller (1996)
     */

    public static /*@Real*/ double blackFormulaImpliedStdDevApproximation(final PlainVanillaPayoff payoff,
            @Real final double strike, @Real final double forward, @Real final double blackPrice,
            @DiscountFactor final double discount, @Real final double displacement) {

        return blackFormulaImpliedStdDevApproximation(payoff.optionType(), payoff.strike(), forward, blackPrice,
                discount, displacement);
    }

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final Option.Type optionType, @Real final double strike,
            @Real final double forward, @Real final double blackPrice) {

        return blackFormulaImpliedStdDev(optionType, strike, forward, blackPrice, 1.0, Double.NaN, 1.0e-6, 0.0);

    }

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final Option.Type optionType, @Real final double strike,
            @Real final double forward, @Real final double blackPrice, @DiscountFactor final double discount) {

        return blackFormulaImpliedStdDev(optionType, strike, forward, blackPrice, discount, Double.NaN, 1.0e-6, 0.0);

    }

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final Option.Type optionType, @Real final double strike,
            @Real final double forward, @Real final double blackPrice, @DiscountFactor final double discount,
            @Real final double guess) {

        return blackFormulaImpliedStdDev(optionType, strike, forward, blackPrice, discount, guess, 1.0e-6, 0.0);

    }

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final Option.Type optionType, @Real final double strike,
            @Real final double forward, @Real final double blackPrice, @DiscountFactor final double discount,
            @Real final double guess, @Real final double accuracy) {

        return blackFormulaImpliedStdDev(optionType, strike, forward, blackPrice, discount, guess, accuracy, 0.0);

    }

    // ---
    // ---
    // ---

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final Option.Type optionType, @Real double strike,
            @Real double forward, @Real final double blackPrice, @DiscountFactor final double discount,
            @Real double guess, @Real final double accuracy, @Real final double displacement) {

        //---
        // TODO: This block of code was removed because there's no option to pass maxIterations in the original C++ code
        //---
        //		return blackFormulaImpliedStdDev(optionType, strike, forward, blackPrice, discount, guess, accuracy, displacement, 1);
        //	}
        //
        //	/**
        //	 * Black 1976 implied standard deviation, i.e.
        //	 * volatility*sqrt(timeToMaturity)
        //	 */
        //	// TODO: Move the code
        //	public static /*@Real*/ double blackFormulaImpliedStdDev(
        //			final Option.Type optionType,
        //			@Real double strike,
        //			@Real double forward,
        //			@Real final double blackPrice,
        //			@DiscountFactor final doublediscount,
        //			@Real double guess,
        //			@Real final double accuracy,
        //			@Real final double displacement,
        //			final int maxIterations) {
        //---
        //TODO: The original C++ code does not have this line and calls to solver.setMaxIterations(100)
        final int maxIterations = 100;
        //---

        // strike may be negative when displacement > 0; the shifted strike must be non-negative.
        // Mirrors C++ QuantLib v1.42.1 blackFormulaImpliedStdDev check (Phase 2o A.2).
        QL.require(strike + displacement >= 0.0, "strike+displacement must be non-negative"); // TODO: message
        QL.require(forward > 0.0, "forward must be positive"); // TODO: message
        QL.require(displacement >= 0.0, "displacement must be non-negative"); // TODO: message
        QL.require(blackPrice >= 0.0, "blackPrice must be non-negative"); // TODO: message
        QL.require(discount > 0.0, "discount must be positive"); // TODO: message

        strike = strike + displacement;
        forward = forward + displacement;
        if ( Double.isNaN(guess) )
            guess = blackFormulaImpliedStdDevApproximation(optionType, strike, forward, blackPrice, discount,
                    displacement);
        else if ( guess < 0.0 )
            throw new IllegalArgumentException("stddev guess (" + guess + ") must be non-negative");

        final BlackImpliedStdDevHelper f = new BlackImpliedStdDevHelper(optionType, strike, forward,
                blackPrice / discount);
        final NewtonSafe solver = new NewtonSafe();
        solver.setMaxEvaluations(maxIterations);
        final double minSdtDev = 0.0, maxstddev = 3.0;
        final double stddev = solver.solve(f, accuracy, guess, minSdtDev, maxstddev);

        if ( stddev >= 0.0 )
            return stddev;
        throw new ArithmeticException("a negative value was calculated"); // TODO: add more logging
    }

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final PlainVanillaPayoff payoff, @Real final double strike,
            @Real final double forward, @Real final double blackPrice) {

        return blackFormulaImpliedStdDev(payoff, strike, forward, blackPrice, 1.0, Double.NaN, 1.0e-6, 0.0);
    }

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final PlainVanillaPayoff payoff, @Real final double strike,
            @Real final double forward, @Real final double blackPrice, @DiscountFactor final double discount) {

        return blackFormulaImpliedStdDev(payoff, strike, forward, blackPrice, discount, Double.NaN, 1.0e-6, 0.0);
    }

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final PlainVanillaPayoff payoff, @Real final double strike,
            @Real final double forward, @Real final double blackPrice, @DiscountFactor final double discount,
            @Real final double guess) {

        return blackFormulaImpliedStdDev(payoff, strike, forward, blackPrice, discount, guess, 1.0e-6, 0.0);
    }

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final PlainVanillaPayoff payoff, @Real final double strike,
            @Real final double forward, @Real final double blackPrice, @DiscountFactor final double discount,
            @Real final double guess, @Real final double accuracy) {

        return blackFormulaImpliedStdDev(payoff.optionType(), strike, forward, blackPrice, discount, guess, accuracy,
                0.0);
    }

    // -----------------------------------------------------------------------
    // LiRS (Li-Radovic-Stehlik) implied std-dev solver
    // Port of C++ blackFormulaImpliedStdDevLiRS (v1.42.1 blackformula.cpp).
    // Uses a fixed-point iteration based on the F/G functions from the paper.
    // -----------------------------------------------------------------------

    /**
     * Black 1976 implied standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaImpliedStdDev(final PlainVanillaPayoff payoff, @Real final double strike,
            @Real final double forward, @Real final double blackPrice, @DiscountFactor final double discount,
            @Real final double guess, @Real final double accuracy, @Real final double displacement) {

        return blackFormulaImpliedStdDev(payoff.optionType(), strike, forward, blackPrice, discount, guess, accuracy,
                displacement);
    }

    // --- LiRS helpers ---------------------------------------------------------

    /**
     * Implied Black standard deviation using the LiRS fixed-point solver.
     *
     * <p>Port of C++ {@code blackFormulaImpliedStdDevLiRS} (v1.42.1).
     *
     * @param optionType    Put or Call
     * @param strike        option strike
     * @param forward       underlying forward
     * @param blackPrice    market option price (discounted)
     * @param discount      discount factor
     * @param displacement  shift (usually 0)
     * @param guess         initial guess (NaN → use approximation)
     * @param w             blending weight ∈ [0,1]; w=1 → pure put/call, w=0 → straddle-like weighting
     * @param accuracy      convergence tolerance on stdDev
     * @param maxIterations maximum iterations
     * @return implied standard deviation
     */
    public static double blackFormulaImpliedStdDevLiRS(final Option.Type optionType, final double strike,
            final double forward, final double blackPrice, final double discount, final double displacement,
            double guess, final double w, final double accuracy, final int maxIterations) {

        QL.require(discount > 0.0, "discount must be positive");
        QL.require(blackPrice >= 0.0, "option price must be non-negative");

        if ( Double.isNaN(guess) ) {
            guess = blackFormulaImpliedStdDevApproximation(optionType, strike, forward, blackPrice, discount,
                    displacement);
        } else {
            QL.require(guess >= 0.0, "stdDev guess must be non-negative");
        }

        // Apply displacement after computing the initial guess
        final double sk = strike + displacement;
        final double fwd = forward + displacement;

        final double x = Math.log(fwd / sk);
        double cs;
        if ( optionType == Option.Type.Call ) {
            cs = blackPrice / (fwd * discount);
        } else {
            cs = blackPrice / (fwd * discount) + 1.0 - sk / fwd;
        }
        QL.require(cs >= 0.0, "normalized call price must be non-negative");

        // If x > 0 apply in-out duality to ensure x <= 0
        double xAdj = x;
        double csAdj = cs;
        if ( xAdj > 0.0 ) {
            csAdj = fwd / sk * csAdj + 1.0 - fwd / sk;
            QL.require(csAdj >= 0.0, "negative option price from in-out duality");
            xAdj = -xAdj;
        }

        final CumulativeNormalDistribution N = new CumulativeNormalDistribution();
        final InverseCumulativeNormal invN = new InverseCumulativeNormal();

        int nIter = 0;
        double vk, vkp1 = guess, dv;

        do {
            vk = vkp1;
            final double phiK = lirsPhi(xAdj, vk);
            final double alphaK = (1.0 + w) / (1.0 + phiK);
            vkp1 = alphaK * lirsG(vk, xAdj, csAdj, w, N, invN) + (1.0 - alphaK) * vk;
            dv = Math.abs(vkp1 - vk);
        } while ( dv > accuracy && ++nIter < maxIterations );

        QL.require(dv <= accuracy, "max iterations exceeded in LiRS solver");
        QL.require(vk >= 0.0, "stdDev must be non-negative");

        return vk;
    }

    private static double lirsPhi(final double x, final double v) {
        final double ax = 2.0 * Math.abs(x);
        final double v2 = v * v;
        return (v2 - ax) / (v2 + ax);
    }

    private static double lirsNp(final double x, final double v, final CumulativeNormalDistribution N) {
        return N.op(x / v + 0.5 * v);
    }

    private static double lirsNm(final double x, final double v, final CumulativeNormalDistribution N) {
        return Math.exp(-x) * N.op(x / v - 0.5 * v);
    }

    private static double lirsF(final double v, final double x, final double cs, final double w,
            final CumulativeNormalDistribution N) {
        return cs + lirsNm(x, v, N) + w * lirsNp(x, v, N);
    }

    // ---
    // ---
    // ---

    private static double lirsG(final double v, final double x, final double cs, final double w,
            final CumulativeNormalDistribution N, final InverseCumulativeNormal invN) {
        final double q = lirsF(v, x, cs, w, N) / (1.0 + w);
        // Acklam's inverse — same accuracy tier as C++ Maddock for this use
        final double k = invN.op(q);
        return k + Math.sqrt(k * k + 2.0 * Math.abs(x));
    }

    /**
     * Black 1976 probability of being in the money (in the bond martingale measure), i.e. N(d2). It is a risk-neutral
     * probability, not the real world one.
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaCashItmProbability(final Option.Type optionType,
            @Real final double strike, @Real final double forward, @StdDev final double stddev) {

        return blackFormulaCashItmProbability(optionType, strike, forward, stddev, 0.0);
    }

    // ---
    // ---
    // ---

    /**
     * Black 1976 probability of being in the money (in the bond martingale measure), i.e. N(d2). It is a risk-neutral
     * probability, not the real world one.
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaCashItmProbability(final Option.Type optionType,
            @Real final double strike, @Real final double forward, @StdDev final double stddev,
            @Real final double displacement) {

        if ( stddev == 0.0 )
            return (forward * optionType.toInteger() > strike * optionType.toInteger() ? 1.0 : 0.0);
        if ( strike == 0.0 )
            return (optionType == Option.Type.Call ? 1.0 : 0.0);
        final double d1 = Math.log((forward + displacement) / (strike + displacement)) / stddev + 0.5 * stddev;
        final double d2 = d1 - stddev;

        // TODO: code review
        final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        return phi.op(optionType.toInteger() * d2);
    }

    // ---
    // ---
    // ---

    /**
     * Black 1976 probability of being in the money (in the bond martingale measure), i.e. N(d2). It is a risk-neutral
     * probability, not the real world one.
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaCashItmProbability(final PlainVanillaPayoff payoff,
            @Real final double strike, @Real final double forward, @StdDev final double stddev,
            @Real final double displacement) {

        return blackFormulaCashItmProbability(payoff.optionType(), strike, forward, stddev, displacement);
    }

    /**
     * Black 1976 probability of being in the money (in the asset martingale measure), i.e. N(d1). This is the analytic
     * forward delta for a cash-or-nothing / asset-or-nothing payoff under the displaced-diffusion model.
     *
     * <p>Mirrors C++ v1.42.1 {@code blackFormulaAssetItmProbability}
     * (ql/pricingengines/blackformula.cpp:593-613).
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double blackFormulaAssetItmProbability(final Option.Type optionType,
            @Real final double strike, @Real final double forward, @StdDev final double stddev) {

        return blackFormulaAssetItmProbability(optionType, strike, forward, stddev, 0.0);
    }

    /**
     * Black 1976 probability of being in the money (in the asset martingale measure), i.e. N(d1), with displacement.
     *
     * <p>Mirrors C++ v1.42.1 {@code blackFormulaAssetItmProbability}
     * (ql/pricingengines/blackformula.cpp:593-613).
     */
    public static /*@Real*/ double blackFormulaAssetItmProbability(final Option.Type optionType,
            @Real final double strike, @Real final double forward, @StdDev final double stddev,
            @Real final double displacement) {

        // C++ calls checkParameters(strike, forward, displacement) here;
        // mirror the documented invariants (forward and strike+displacement
        // must be non-negative) inline.
        QL.require(strike + displacement >= 0.0, "strike + displacement must be non-negative");
        QL.require(forward + displacement > 0.0, "forward + displacement must be positive");
        QL.require(stddev >= 0.0, "stddev must be non-negative");

        final int sign = optionType.toInteger();

        if ( stddev == 0.0 ) {
            return (forward * sign < strike * sign) ? 1.0 : 0.0;
        }

        final double fShifted = forward + displacement;
        final double kShifted = strike + displacement;
        if ( kShifted == 0.0 ) {
            return (optionType == Option.Type.Call) ? 1.0 : 0.0;
        }
        final double d1 = Math.log(fShifted / kShifted) / stddev + 0.5 * stddev;
        final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        return phi.op(sign * d1);
    }

    // ---
    // ---
    // ---

    /**
     * Black 1976 N(d1) for a PlainVanillaPayoff (asset martingale measure), with displacement. Mirrors C++ v1.42.1
     * {@code blackFormulaAssetItmProbability(PlainVanillaPayoff,...)}.
     */
    public static /*@Real*/ double blackFormulaAssetItmProbability(final PlainVanillaPayoff payoff,
            @Real final double forward, @StdDev final double stddev, @Real final double displacement) {

        return blackFormulaAssetItmProbability(payoff.optionType(), payoff.strike(), forward, stddev, displacement);
    }

    /**
     * Black 1976 formula for standard deviation derivative
     * <p>
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatilitysqrt(timeToMaturity), and it returns the
     * derivative with respect to the standard deviation. If T is the time to maturity Black vega would be
     * blackstddevDerivative(strike, forward, stddev)sqrt(T)
     */
    public static /*@Real*/ double blackFormulaStdDevDerivative(@Real final double strike, @Real final double forward,
            @StdDev final double stddev) {

        return blackFormulaStdDevDerivative(strike, forward, stddev, 1.0, 0.0);
    }

    /**
     * Black 1976 formula for standard deviation derivative
     * <p>
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatilitysqrt(timeToMaturity), and it returns the
     * derivative with respect to the standard deviation. If T is the time to maturity Black vega would be
     * blackstddevDerivative(strike, forward, stddev)sqrt(T)
     */
    public static /*@Real*/ double blackFormulaStdDevDerivative(@Real final double strike, @Real final double forward,
            @StdDev final double stddev, @DiscountFactor final double discount) {

        return blackFormulaStdDevDerivative(strike, forward, stddev, discount, 0.0);
    }

    // ---
    // ---
    // ---

    /**
     * Black 1976 formula for standard deviation derivative
     * <p>
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatilitysqrt(timeToMaturity), and it returns the
     * derivative with respect to the standard deviation. If T is the time to maturity Black vega would be
     * blackstddevDerivative(strike, forward, stddev)sqrt(T)
     */
    public static /*@Real*/ double blackFormulaStdDevDerivative(@Real double strike, @Real double forward,
            @StdDev final double stddev, @DiscountFactor final double discount, @Real final double displacement) {

        // strike may be negative when displacement > 0; the shifted strike must be non-negative.
        // Mirrors C++ QuantLib v1.42.1 blackFormulaStdDevDerivative check (Phase 2o A.2).
        QL.require(strike + displacement >= 0.0, "strike+displacement must be non-negative"); // TODO: message
        QL.require(forward > 0.0, "forward must be positive"); // TODO: message
        QL.require(stddev >= 0.0, "blackPrice must be non-negative"); // TODO: message
        QL.require(discount > 0.0, "discount must be positive"); // TODO: message
        QL.require(displacement >= 0.0, "displacement must be non-negative"); // TODO: message

        forward = forward + displacement;
        strike = strike + displacement;
        final double d1 = Math.log(forward / strike) / stddev + .5 * stddev;

        // TODO: code review
        final CumulativeNormalDistribution cdf = new CumulativeNormalDistribution();
        return discount * forward * cdf.derivative(d1);
    }

    /**
     * Black 1976 formula for standard deviation derivative
     * <p>
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatilitysqrt(timeToMaturity), and it returns the
     * derivative with respect to the standard deviation. If T is the time to maturity Black vega would be
     * blackstddevDerivative(strike, forward, stddev)sqrt(T)
     */
    public static /*@Real*/ double blackFormulastddevDerivative(final PlainVanillaPayoff payoff,
            @Real final double forward, @StdDev final double stddev) {

        return blackFormulaStdDevDerivative(payoff, forward, stddev, 1.0, 0.0);
    }

    /**
     * Black 1976 formula for standard deviation derivative
     * <p>
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatilitysqrt(timeToMaturity), and it returns the
     * derivative with respect to the standard deviation. If T is the time to maturity Black vega would be
     * blackstddevDerivative(strike, forward, stddev)sqrt(T)
     */
    public static /*@Real*/ double blackFormulastddevDerivative(final PlainVanillaPayoff payoff,
            @Real final double forward, @StdDev final double stddev, @DiscountFactor final double discount) {

        return blackFormulaStdDevDerivative(payoff, forward, stddev, discount, 0.0);
    }

    // ---
    // ---
    // ---

    /**
     * Black 1976 formula for standard deviation derivative
     * <p>
     *
     * @note Instead of volatility it uses standard deviation, i.e. volatilitysqrt(timeToMaturity), and it returns the
     * derivative with respect to the standard deviation. If T is the time to maturity Black vega would be
     * blackstddevDerivative(strike, forward, stddev)sqrt(T)
     */
    public static /*@Real*/ double blackFormulaStdDevDerivative(final PlainVanillaPayoff payoff,
            @Real final double forward, @StdDev final double stddev, @DiscountFactor final double discount,
            @Real final double displacement) {

        return blackFormulaStdDevDerivative(payoff.strike(), forward, stddev, discount, displacement);
    }

    /**
     * Black style formula when forward is normal rather than log-normal. This is essentially the model of Bachelier.
     *
     * @note Bachelier model needs absolute volatility, not percentage volatility. Standard deviation is
     * absoluteVolatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double bachelierBlackFormula(final PlainVanillaPayoff payoff, @Real final double forward,
            @StdDev final double stddev, @Real final double discount) {

        return bachelierBlackFormula(payoff.optionType(), payoff.strike(), forward, stddev, discount);
    }

    // ---
    // ---
    // ---

    /**
     * Black style formula when forward is normal rather than log-normal. This is essentially the model of Bachelier.
     *
     * @note Bachelier model needs absolute volatility, not percentage volatility. Standard deviation is
     * absoluteVolatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double bachelierBlackFormula(final Option.Type optionType, @Real final double strike,
            @Real final double forward, @StdDev final double stddev, final @DiscountFactor double discount) {

        QL.require(stddev >= 0.0, "stdDev must be non-negative");
        QL.require(discount > 0.0, "discount must be positive");

        // Mirrors C++ v1.42.1 blackformula.cpp lines 705-727.
        // optionType.toInteger() returns ±1 (Call=+1, Put=-1); the
        // pre-existing optionType.ordinal() form returned 0 for Call
        // and 1 for Put, which broke both the call and put branches.
        final double d = (forward - strike) * optionType.toInteger();
        if ( stddev == 0.0 )
            return discount * Math.max(d, 0.0);
        final double h = d / stddev;

        final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        // C++: result = discount * (stdDev * phi.derivative(h) + d * phi(h));
        // Pre-existing Java was missing the outer parens, applying
        // discount only to the derivative term.
        final double result = discount * (stddev * phi.derivative(h) + d * phi.op(h));
        QL.ensure(result >= 0.0,
                "negative value (" + result + ") for " + stddev + " stdDev, " + optionType + " option, " + strike
                        + " strike, " + forward + " forward");
        return result;
    }

    /**
     * Black style formula when forward is normal rather than log-normal. This is essentially the model of Bachelier.
     *
     * @note Bachelier model needs absolute volatility, not percentage volatility. Standard deviation is
     * absoluteVolatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double bachelierBlackFormula(final Option.Type optionType, @Real final double strike,
            @Real final double forward, @StdDev final double stddev) {

        return bachelierBlackFormula(optionType, strike, forward, stddev, 1.0);
    }

    /**
     * Black style formula when forward is normal rather than log-normal. This is essentially the model of Bachelier.
     *
     * @note Bachelier model needs absolute volatility, not percentage volatility. Standard deviation is
     * absoluteVolatility*sqrt(timeToMaturity)
     */
    public static /*@Real*/ double bachelierBlackFormula(final PlainVanillaPayoff payoff, @Real final double forward,
            @StdDev final double stddev) {

        return bachelierBlackFormula(payoff, forward, stddev, 1.0);
    }

    /**
     * Bachelier (normal) implied vol from a Bachelier-formula price.
     *
     * <p>Port of C++ QuantLib v1.42.1
     * {@code QuantLib::bachelierBlackFormulaImpliedVol} (Jäckel inverse-PhiTilde closed-form approximation;
     * <em>not</em> the Choi rational h(eta) form). Inputs/outputs follow C++ exactly:
     * <pre>
     *   bachelierPrice = discount * E[max(theta*(F_T - K), 0)]
     *   sigma          = bachelierBlackFormulaImpliedVol(...)  // absolute vol
     *   stdDev         = sigma * sqrt(tte)
     * </pre>
     *
     * <p>Closed-form path branches:
     * <ul>
     *   <li><b>strike == forward</b> (close enough): {@code sigma = price / (sqrt(tte) * phi(0))}.</li>
     *   <li><b>strike != forward</b>: invert {@code PhiTilde(x) = Phi(x) + phi(x)/x}
     *       at {@code -|timeValue/(strike-forward)|} via Jäckel's two-region
     *       rational approximation followed by a single Newton-Houseolder
     *       refinement step.</li>
     * </ul>
     *
     * @param optionType     Call or Put
     * @param strike         strike rate
     * @param forward        forward rate
     * @param tte            time to expiry (positive)
     * @param bachelierPrice option price under Bachelier model
     * @param discount       discount factor (positive); price is divided by it internally
     * @return absolute (normal) volatility
     */
    public static /*@Real*/ double bachelierBlackFormulaImpliedVol(final Option.Type optionType,
            @Real final double strike, @Real final double forward, @Real final double tte,
            @Real final double bachelierPrice, @Real final double discount) {

        QL.require(tte > 0.0, "tte must be positive");
        QL.require(discount > 0.0, "discount must be positive");

        final double theta = (optionType == Option.Type.Call) ? 1.0 : -1.0;

        // compound bachelierPrice so that effectively discount = 1
        final double price = bachelierPrice / discount;

        // handle case strike == forward (closed form)
        if ( Closeness.isCloseEnough(strike, forward) ) {
            return price / (Math.sqrt(tte) * BACHELIER_PHI_AT_ZERO);
        }

        final double timeValue = price - Math.max(theta * (forward - strike), 0.0);

        if ( Closeness.isCloseEnough(timeValue, 0.0) ) {
            return 0.0;
        }

        QL.require(timeValue > 0.0,
                "bachelierBlackFormulaImpliedVolExact(theta=" + theta + ",strike=" + strike + ",forward=" + forward
                        + ",tte=" + tte + ",price=" + bachelierPrice + "): option price implies negative time value ("
                        + timeValue + ")");

        final double phiTildeStar = -Math.abs(timeValue / (strike - forward));
        final double xstar = inversePhiTilde(phiTildeStar);
        return Math.abs((strike - forward) / (xstar * Math.sqrt(tte)));
    }

    /** Standard normal pdf {@code phi(x)}. */
    private static double phi(final double x) {
        return Math.exp(-0.5 * x * x) * BACHELIER_PHI_AT_ZERO;
    }

    /** Standard normal cdf {@code Phi(x)}. */
    private static double Phi(final double x) {
        return new CumulativeNormalDistribution().op(x);
    }

    /** {@code PhiTilde(x) = Phi(x) + phi(x)/x}. */
    private static double phiTilde(final double x) {
        return Phi(x) + phi(x) / x;
    }

    /**
     * Jäckel's inverse of {@code PhiTilde} at {@code y < 0} via two-region rational approximation + a single Newton
     * step. Mirrors C++ {@code inversePhiTilde} (anonymous namespace in blackformula.cpp).
     */
    private static double inversePhiTilde(final double phiTildeStar) {
        QL.require(phiTildeStar < 0.0, "inversePhiTilde(" + phiTildeStar + "): negative argument required");
        final double xbar;
        if ( phiTildeStar < -0.001882039271 ) {
            final double g = 1.0 / (phiTildeStar - 0.5);
            final double g2 = g * g;
            final double xibar =
                    (0.032114372355 - g2 * (0.016969777977 - g2 * (2.6207332461E-3 - 9.6066952861E-5 * g2))) / (1.0
                            - g2 * (0.6635646938 - g2 * (0.14528712196 - 0.010472855461 * g2)));
            xbar = g * (0.3989422804014326 + xibar * g2);
        } else {
            final double h = Math.sqrt(-Math.log(-phiTildeStar));
            xbar = (9.4883409779 - h * (9.6320903635 - h * (0.58556997323 + 2.1464093351 * h))) / (1.0 - h * (
                    0.65174820867 + h * (1.5120247828 + 6.6437847132E-5 * h)));
        }
        final double q = (phiTilde(xbar) - phiTildeStar) / phi(xbar);
        // Householder-style refinement (C++ inversePhiTilde):
        //   xstar = xbar + 3*q*xbar^2 * (2 - q*xbar*(2 + xbar^2))
        //                  / (6 + q*xbar*(-12 + xbar*(6q + xbar*(-6 + q*xbar*(3 + xbar^2)))))
        final double xb2 = xbar * xbar;
        final double num = 3.0 * q * xb2 * (2.0 - q * xbar * (2.0 + xb2));
        final double den = 6.0 + q * xbar * (-12.0 + xbar * (6.0 * q + xbar * (-6.0 + q * xbar * (3.0 + xb2))));
        return xbar + num / den;
    }

    //
    // private inner classes
    //

    /**
     * Second derivative w.r.t. standard deviation of {@code blackFormula}. Mirrors C++
     * {@code blackFormulaStdDevSecondDerivative} (blackformula.cpp:671-693).
     */
    public static double blackFormulaStdDevSecondDerivative(final double strike, final double forward,
            final double stdDev, final double discount, final double displacement) {
        QL.require(strike + displacement >= 0.0, "strike+displacement must be non-negative");
        QL.require(forward > 0.0, "forward must be positive");
        QL.require(stdDev >= 0.0, "stdDev must be non-negative");
        QL.require(discount > 0.0, "discount must be positive");
        QL.require(displacement >= 0.0, "displacement must be non-negative");

        final double f = forward + displacement;
        final double k = strike + displacement;
        if ( stdDev == 0.0 || k == 0.0 ) {
            return 0.0;
        }
        final double d1 = Math.log(f / k) / stdDev + 0.5 * stdDev;
        final double d1p = -Math.log(f / k) / (stdDev * stdDev) + 0.5;
        return discount * f * new NormalDistribution().derivative(d1) * d1p;
    }

    //
    // Phase 5e.5b-CFC-d-3 ports of v1.42.1 BlackFormula extensions
    //

    /**
     * Black/Bachelier-style derivative of price w.r.t. forward. Mirrors C++ {@code blackFormulaForwardDerivative}
     * (blackformula.cpp:109-136).
     */
    public static double blackFormulaForwardDerivative(final Option.Type optionType, final double strikeIn,
            final double forwardIn, final double stdDev, final double discount, final double displacement) {
        QL.require(strikeIn + displacement >= 0.0, "strike+displacement must be non-negative");
        QL.require(forwardIn > 0.0, "forward must be positive");
        QL.require(stdDev >= 0.0, "stdDev must be non-negative");
        QL.require(discount > 0.0, "discount must be positive");
        QL.require(displacement >= 0.0, "displacement must be non-negative");

        final int sign = (optionType == Option.Type.Call) ? 1 : -1;
        if ( stdDev == 0.0 ) {
            // sign * max(sign(F - K) * sign, 0) * discount
            final double diff = (forwardIn - strikeIn) * sign;
            final double s = (diff > 0.0) ? 1.0 : (diff < 0.0 ? -1.0 : 0.0);
            return sign * Math.max(s, 0.0) * discount;
        }
        final double f = forwardIn + displacement;
        final double k = strikeIn + displacement;
        if ( k == 0.0 ) {
            return (optionType == Option.Type.Call) ? discount : 0.0;
        }
        final double d1 = Math.log(f / k) / stdDev + 0.5 * stdDev;
        final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        return sign * phi.op(sign * d1) * discount;
    }

    /** PlainVanillaPayoff overload of {@link #blackFormulaForwardDerivative}. */
    public static double blackFormulaForwardDerivative(final PlainVanillaPayoff payoff, final double forward,
            final double stdDev, final double discount, final double displacement) {
        return blackFormulaForwardDerivative(payoff.optionType(), payoff.strike(), forward, stdDev, discount,
                displacement);
    }

    /**
     * Bachelier-style derivative of price w.r.t. forward. Mirrors C++ {@code bachelierBlackFormulaForwardDerivative}
     * (blackformula.cpp:738-751).
     */
    public static double bachelierBlackFormulaForwardDerivative(final Option.Type optionType, final double strike,
            final double forward, final double stdDev, final double discount) {
        QL.require(stdDev >= 0.0, "stdDev must be non-negative");
        QL.require(discount > 0.0, "discount must be positive");
        final int sign = (optionType == Option.Type.Call) ? 1 : -1;
        if ( stdDev == 0.0 ) {
            final double diff = (forward - strike) * sign;
            final double s = (diff > 0.0) ? 1.0 : (diff < 0.0 ? -1.0 : 0.0);
            return sign * Math.max(s, 0.0) * discount;
        }
        final double d = (forward - strike) * sign;
        final double h = d / stdDev;
        final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        return sign * phi.op(h) * discount;
    }

    /** PlainVanillaPayoff overload of {@link #bachelierBlackFormulaForwardDerivative}. */
    public static double bachelierBlackFormulaForwardDerivative(final PlainVanillaPayoff payoff, final double forward,
            final double stdDev, final double discount) {
        return bachelierBlackFormulaForwardDerivative(payoff.optionType(), payoff.strike(), forward, stdDev, discount);
    }

    /**
     * Implied stdev approximation by Radoicic-Stefanica (RS) closed-form inversion. Mirrors C++
     * {@code blackFormulaImpliedStdDevApproximationRS} (blackformula.cpp:269-318).
     */
    public static double blackFormulaImpliedStdDevApproximationRS(final Option.Type type, final double kIn,
            final double fIn, final double marketValue, final double df, final double displacement) {
        QL.require(displacement >= 0.0, "displacement must be non-negative");
        QL.require(kIn + displacement >= 0.0, "strike+displacement must be non-negative");
        QL.require(fIn + displacement > 0.0, "forward+displacement must be positive");
        QL.require(marketValue >= 0.0, "marketValue must be non-negative");
        QL.require(df > 0.0, "discount must be positive");

        final double F = fIn + displacement;
        final double K = kIn + displacement;
        final double ey = F / K;
        final double ey2 = ey * ey;
        final double y = Math.log(ey);
        final double alpha = marketValue / (K * df);
        final double R = 2.0 * alpha + ((type == Option.Type.Call) ? -ey + 1.0 : ey - 1.0);
        final double R2 = R * R;

        final double TWO_OVER_PI = 2.0 / Math.PI;
        final double a = Math.exp((1.0 - TWO_OVER_PI) * y);
        final double aDiff = a - 1.0 / a;
        final double A = aDiff * aDiff;
        final double b = Math.exp(TWO_OVER_PI * y);
        final double yPlus1 = ey + 1.0;
        final double yMinus1 = ey - 1.0;
        final double B = 4.0 * (b + 1.0 / b) - 2.0 * K / F * (a + 1.0 / a) * (ey2 + 1.0 - R2);
        final double C = (R2 - yMinus1 * yMinus1) * (yPlus1 * yPlus1 - R2) / ey2;

        final double beta = 2.0 * C / (B + Math.sqrt(B * B + 4.0 * A * C));
        final double gamma = -Math.PI / 2.0 * Math.log(beta);

        if ( y >= 0.0 ) {
            final double M0 = K * df * ((type == Option.Type.Call)
                    ? ey * af(Math.sqrt(2.0 * y)) - 0.5
                    : 0.5 - ey * af(-Math.sqrt(2.0 * y)));
            return (marketValue <= M0)
                    ? Math.sqrt(gamma + y) - Math.sqrt(gamma - y)
                    : Math.sqrt(gamma + y) + Math.sqrt(gamma - y);
        } else {
            final double M0 = K * df * ((type == Option.Type.Call)
                    ? 0.5 * ey - af(-Math.sqrt(-2.0 * y))
                    : af(Math.sqrt(-2.0 * y)) - 0.5 * ey);
            return (marketValue <= M0)
                    ? Math.sqrt(gamma - y) - Math.sqrt(gamma + y)
                    : Math.sqrt(gamma + y) + Math.sqrt(gamma - y);
        }
    }

    /** PlainVanillaPayoff overload of {@link #blackFormulaImpliedStdDevApproximationRS}. */
    public static double blackFormulaImpliedStdDevApproximationRS(final PlainVanillaPayoff payoff, final double F,
            final double marketValue, final double df, final double displacement) {
        return blackFormulaImpliedStdDevApproximationRS(payoff.optionType(), payoff.strike(), F, marketValue, df,
                displacement);
    }

    /** Internal helper Af(x) used by {@link #blackFormulaImpliedStdDevApproximationRS}. */
    private static double af(final double x) {
        final double sign = (x > 0.0) ? 1.0 : (x < 0.0 ? -1.0 : 0.0);
        return 0.5 * (1.0 + sign * Math.sqrt(1.0 - Math.exp(-2.0 / Math.PI * x * x)));
    }

    /**
     * Implied stdev approximation by Chambers — second-order Taylor expansion around the at-the-money implied vol via
     * the Brenner-Subrahmanyam guess. Mirrors C++ {@code blackFormulaImpliedStdDevChambers}
     * (blackformula.cpp:199-248).
     */
    public static double blackFormulaImpliedStdDevChambers(final Option.Type optionType, final double strikeIn,
            final double forwardIn, final double blackPriceIn, final double blackAtmPriceIn, final double discount,
            final double displacement) {
        QL.require(displacement >= 0.0, "displacement must be non-negative");
        QL.require(strikeIn + displacement >= 0.0, "strike+displacement must be non-negative");
        QL.require(forwardIn + displacement > 0.0, "forward+displacement must be positive");
        QL.require(blackPriceIn >= 0.0, "blackPrice must be non-negative");
        QL.require(blackAtmPriceIn >= 0.0, "blackAtmPrice must be non-negative");
        QL.require(discount > 0.0, "discount must be positive");

        final double forward = forwardIn + displacement;
        final double strike = strikeIn + displacement;
        final double blackPrice = blackPriceIn / discount;
        final double blackAtmPrice = blackAtmPriceIn / discount;

        // Brenner-Subrahmanyam initial guess
        final double s0 = Math.sqrt(2.0 * Math.PI) * blackAtmPrice / forward;
        final double priceAtmVol = blackFormula(optionType, strike, forward, s0, 1.0, 0.0);
        final double dc = blackPrice - priceAtmVol;

        final double stdDev;
        if ( Closeness.isClose(dc, 0.0) ) {
            stdDev = s0;
        } else {
            final double d1 = blackFormulaStdDevDerivative(strike, forward, s0, 1.0, 0.0);
            final double d2 = blackFormulaStdDevSecondDerivative(strike, forward, s0, 1.0, 0.0);
            double ds = 0.0;
            final double tmp = d1 * d1 + 2.0 * d2 * dc;
            if ( Math.abs(d2) > 1e-10 && tmp >= 0.0 ) {
                ds = (-d1 + Math.sqrt(tmp)) / d2;
            } else if ( Math.abs(d1) > 1e-10 ) {
                ds = dc / d1;
            }
            stdDev = s0 + ds;
        }
        QL.ensure(stdDev >= 0.0, "stdDev must be non-negative");
        return stdDev;
    }

    /** PlainVanillaPayoff overload of {@link #blackFormulaImpliedStdDevChambers}. */
    public static double blackFormulaImpliedStdDevChambers(final PlainVanillaPayoff payoff, final double forward,
            final double blackPrice, final double blackAtmPrice, final double discount, final double displacement) {
        return blackFormulaImpliedStdDevChambers(payoff.optionType(), payoff.strike(), forward, blackPrice,
                blackAtmPrice, discount, displacement);
    }

    private static class BlackImpliedStdDevHelper implements Derivative {

        private final double halfOptionType_;
        private final double signedStrike_, signedForward_;
        private final double undiscountedBlackPrice_, signedMoneyness_;
        private final CumulativeNormalDistribution N_;

        public BlackImpliedStdDevHelper(final Option.Type optionType, final double strike, final double forward,
                final double undiscountedBlackPrice) {
            this(optionType, strike, forward, undiscountedBlackPrice, 0.0d);
        }

        public BlackImpliedStdDevHelper(final Option.Type optionType, final double strike, final double forward,
                final double undiscountedBlackPrice, final double displacement) {

            QL.require(strike >= 0.0, "strike must be non-negative"); // TODO: message
            QL.require(forward > 0.0, "forward must be positive"); // TODO: message
            QL.require(displacement >= 0.0, "displacement must be non-negative"); // TODO: message
            QL.require(undiscountedBlackPrice >= 0.0, "undiscounted Black price must be non-negative"); // TODO: message

            this.halfOptionType_ = (0.5 * optionType.toInteger());
            this.signedStrike_ = (optionType.toInteger() * (strike + displacement));
            this.signedForward_ = (optionType.toInteger() * (forward + displacement));
            this.undiscountedBlackPrice_ = (undiscountedBlackPrice);
            signedMoneyness_ = optionType.toInteger() * Math.log((forward + displacement) / (strike + displacement));

            // TODO: code review
            this.N_ = new CumulativeNormalDistribution();
        }

        public double op(@NonNegative final double stddev) {
            QL.require(stddev >= 0.0, "stddev must be non-negative"); // TODO: message
            if ( stddev == 0.0 )
                return Math.max(signedForward_ - signedStrike_, 0.0d) - undiscountedBlackPrice_;

            final double temp = halfOptionType_ * stddev;
            final double d = signedMoneyness_ / stddev;
            final double signedD1 = d + temp;
            final double signedD2 = d - temp;
            final double result = signedForward_ * N_.op(signedD1) - signedStrike_ * N_.op(signedD2);
            // numerical inaccuracies can yield a negative answer
            return Math.max(0.0, result) - undiscountedBlackPrice_;
        }

        public double derivative(@NonNegative final double stddev) {
            QL.require(stddev >= 0.0, "stddev must be non-negative"); // TODO: message

            final double signedD1 = signedMoneyness_ / stddev + halfOptionType_ * stddev;
            return signedForward_ * N_.derivative(signedD1);
        }

    }

}
