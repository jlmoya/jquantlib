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
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2023 Klaus Spanderen

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

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Constants;
import org.jquantlib.math.ErrorFunction;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Bjerksund and Stensland approximation engine.
 * <p>
 * Java port of v1.42.1 {@code ql/pricingengines/vanilla/bjerksundstenslandengine.{hpp,cpp}}
 * — the Spanderen 2023 rewrite that adds analytical Greeks
 * (phi_S, phi_SS, phi_gamma, phi_H, phi_I, phi_rt, phi_bt, phi_v)
 * and put-call symmetry Greek correction.
 *
 * @author Richard Gomes (original)
 * @author Phase 1 closure A2-B-546 — Spanderen 2023 rewrite
 */
public class BjerksundStenslandApproximationEngine extends VanillaOption.EngineImpl {

    private static final String NOT_AN_AMERICAN_OPTION = "not an American Option";
    private static final String NON_AMERICAN_EXERCISE_GIVEN = "non-American exercise given";
    private static final String PAYOFF_AT_EXPIRY_NOT_HANDLED = "payoff at expiry not handled";
    private static final String NON_PLAIN_PAYOFF_GIVEN = "non-plain payoff given";

    // Math constants matching C++ M_SQRT2, M_SQRTPI
    private static final double M_SQRT2 = 1.41421356237309504880;
    private static final double M_SQRTPI = 1.77245385090551602792981;

    private final GeneralizedBlackScholesProcess process;
    private final OneAssetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;
    private final Option.GreeksImpl greeks;
    private final Option.MoreGreeksImpl moreGreeks;

    private final CumulativeNormalDistribution cumNormalDist = new CumulativeNormalDistribution();
    private final ErrorFunction erf = new ErrorFunction();

    /** Last computed exercise type — equivalent to C++ additionalResults["exerciseType"]. */
    private String exerciseType = "Unknown";

    public BjerksundStenslandApproximationEngine(final GeneralizedBlackScholesProcess process) {
        this.a = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;
        this.greeks = r.greeks();
        this.moreGreeks = r.moreGreeks();
        this.process = process;
        this.process.addObserver(this);
    }

    /**
     * Exercise type for the last computation: {@code "American"}, {@code "European"} or {@code "Immediate"}.
     * Mirrors C++ {@code additionalResults["exerciseType"]}.
     */
    public String exerciseType() {
        return exerciseType;
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static double squared(final double x) {
        return x * x;
    }

    /**
     * Complementary error function with deep-tail precision.
     *
     * <p>{@link ErrorFunction} saturates {@code erf(x)} at {@code 1 - QL_EPSILON}
     * once {@code |x| >= 6}, and starts losing precision well before that
     * (at {@code |x| ~ 4-5} the cancellation in {@code 1 - erf(x)} eats most
     * of the meaningful digits). For the Bjerksund-Stensland Greeks formulas,
     * accuracy in the moderate-to-deep tail matters because the small
     * {@code erfc} is multiplied by very large {@code pow(I/S, ...)*S} factors,
     * and the tail error bleeds back into the visible vega (observed ~6e-3
     * mismatch versus bump-FD on a deep-ITM short-dated boundary case before
     * this fix; ~3e-3 on a long-dated near-ATM low-vol case for thresholds
     * larger than ~4.5).
     *
     * <p>For {@code |x| < 4}, defers to {@code 1 - erf(x)} — cancellation is
     * at worst ~8 digits, leaving 8 useful digits in the {@code 1e-8} range,
     * which is amply sufficient. For larger {@code |x|}, uses the continued-fraction
     * representation
     * {@code erfc(x) = exp(-x^2) / (sqrt(pi) * (x + 0.5/(x + 1/(x + 1.5/(x + ...)))))}
     * (100 levels — convergence is ~1 ULP for {@code |x| >= 2}). Matches
     * the precision of C++ {@code std::erfc} in the engine's operating range.
     */
    private double erfc(final double x) {
        if (x < -4.0) {
            // erfc(-x) = 2 - erfc(x)
            return 2.0 - erfc(-x);
        }
        if (x < 4.0) {
            return 1.0 - erf.op(x);
        }
        double cf = x;
        for (int n = 100; n >= 1; --n) {
            cf = x + 0.5 * n / cf;
        }
        return Math.exp(-x * x) / (cf * M_SQRTPI);
    }

    // -----------------------------------------------------------------------
    // phi family — port of C++ phi, phi_S, phi_SS, phi_gamma, phi_H, phi_I,
    // phi_rt, phi_bt, phi_v from v1.42.1 bjerksundstenslandengine.cpp:36-110.
    // -----------------------------------------------------------------------

    private double phi(final double S, final double gamma, final double H, final double I,
            final double rT, final double bT, final double variance) {
        final double lambda = -rT + gamma * bT + 0.5 * gamma * (gamma - 1.0) * variance;
        final double d = -(Math.log(S / H) + (bT + (gamma - 0.5) * variance)) / Math.sqrt(variance);
        final double kappa = 2.0 * bT / variance + (2.0 * gamma - 1.0);
        return Math.exp(lambda) * (cumNormalDist.op(d)
                - Math.pow(I / S, kappa)
                        * cumNormalDist.op(d - 2.0 * Math.log(I / S) / Math.sqrt(variance)));
    }

    private double phi_S(final double S, final double gamma, final double H, final double I,
            final double rT, final double bT, final double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);

        return Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.0)
                * ((-(Math.pow(I / S, 2 * (gamma + bT / v))
                        / (Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8.0 * v)) * I))
                        - 1 / (Math.exp(squared(2 * bT - v + 2 * gamma * v + 2 * lsh) / (8.0 * v)) * S))
                        / (M_SQRT2 * M_SQRTPI * sv)
                        + (Math.pow(I / S, 2 * (gamma + bT / v)) * (2 * bT + (-1 + 2 * gamma) * v)
                                * erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (2.0 * M_SQRT2 * sv)))
                                / (2.0 * I * v));
    }

    private double phi_SS(final double S, final double gamma, final double H, final double I,
            final double rT, final double bT, final double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);
        final double ex = Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8.0 * v));
        final double ey = Math.exp(squared(2 * bT + (-1 + 2 * gamma) * v + 2 * lsh) / (8.0 * v));

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.0)
                * ((M_SQRT2 * I * v * sv) / ey
                        + (2 * M_SQRT2 * Math.pow(I / S, 2 * (gamma + bT / v)) * S * sv * (2 * bT + (-1 + 2 * gamma) * v))
                                / ex
                        - 2 * Math.sqrt(Math.PI) * Math.pow(I / S, 2 * (gamma + bT / v)) * S * (bT + gamma * v)
                                * (2 * bT + (-1 + 2 * gamma) * v)
                                * erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (2.0 * M_SQRT2 * sv))
                        + (M_SQRT2 * I * sv * (bT + (-0.5 + gamma) * v + lsh)) / ey
                        - (Math.pow(I / S, 2 * (gamma + bT / v)) * S * sv
                                * (2 * bT - 3 * v + 2 * gamma * v + 4 * lis + 2 * lsh)) / (M_SQRT2 * ex)))
                / (2.0 * I * M_SQRTPI * squared(S * v));
    }

    private double phi_gamma(final double S, final double gamma, final double H, final double I,
            final double rT, final double bT, final double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);

        return Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2)
                * (((-Math.exp(-squared(2 * bT - v + 2 * gamma * v + 2 * lsh) / (8 * v))
                        + Math.pow(I / S, -1 + 2 * gamma + (2 * bT) / v)
                                / Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8 * v))) * sv)
                        / (M_SQRT2 * M_SQRTPI)
                        + ((2 * bT + (-1 + 2 * gamma) * v)
                                * erfc((2 * bT + (-1 + 2 * gamma) * v + 2 * lsh) / (2.0 * M_SQRT2 * sv))) / 4.0
                        - (Math.pow(I / S, -1 + 2 * gamma + (2 * bT) / v)
                                * erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (2.0 * M_SQRT2 * sv))
                                * (2 * bT + (-1 + 2 * gamma) * v + 4 * lis)) / 4.0);
    }

    private double phi_H(final double S, final double gamma, final double H, final double I,
            final double rT, final double bT, final double v) {
        final double lsh = Math.log(S / H);

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.0)
                * (I / Math.exp(squared(2 * bT - v + 2 * gamma * v + 2 * lsh) / (8.0 * v))
                        - (Math.pow(I / S, 2 * (gamma + bT / v)) * S)
                                / Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * Math.log(I / S) + 2 * lsh)
                                        / (8.0 * v))))
                / (H * I * Math.sqrt(2 * Math.PI) * Math.sqrt(v));
    }

    private double phi_I(final double S, final double gamma, final double H, final double I,
            final double rT, final double bT, final double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.0)
                * Math.pow(I / S, 2 * (gamma + bT / v)) * S
                * ((2 * Math.sqrt(2 / Math.PI))
                        / (Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8.0 * v)) * sv)
                        + (1 - 2 * gamma - (2 * bT) / v)
                                * erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (2.0 * M_SQRT2 * sv))))
                / (2.0 * I * I);
    }

    private double phi_rt(final double S, final double gamma, final double H, final double I,
            final double rT, final double bT, final double v) {
        final double lsh = Math.log(S / H);
        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.0)
                * (-(I * erfc((2 * bT - v + 2 * gamma * v + 2 * lsh) / (2.0 * Math.sqrt(2 * v))))
                        + Math.pow(I / S, 2 * (gamma + bT / v)) * S
                                * erfc((2 * bT - v + 2 * gamma * v + 4 * Math.log(I / S) + 2 * lsh)
                                        / (2.0 * Math.sqrt(2 * v)))))
                / (2.0 * I);
    }

    private double phi_bt(final double S, final double gamma, final double H, final double I,
            final double rT, final double bT, final double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.0)
                * (M_SQRT2 * (-(I / Math.exp(squared(2 * bT - v + 2 * gamma * v + 2 * lsh) / (8.0 * v)))
                        + (Math.pow(I / S, 2 * (gamma + bT / v)) * S)
                                / Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8.0 * v)))
                        * sv
                        + gamma * I * Math.sqrt(Math.PI) * v
                                * erfc((2 * bT - v + 2 * gamma * v + 2 * lsh) / (2.0 * M_SQRT2 * sv))
                        - M_SQRTPI * Math.pow(I / S, 2 * (gamma + bT / v)) * S
                                * erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (2.0 * M_SQRT2 * sv))
                                * (gamma * v + 2 * lis)))
                / (2.0 * I * Math.sqrt(Math.PI) * v);
    }

    private double phi_v(final double S, final double gamma, final double H, final double I,
            final double rT, final double bT, final double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);
        final double er = erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (2.0 * M_SQRT2 * sv));

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.0)
                * (((-1 + gamma) * gamma
                        * (I * erfc((2 * bT - v + 2 * gamma * v + 2 * lsh) / (2.0 * M_SQRT2 * sv))
                                - Math.pow(I / S, 2 * (gamma + bT / v)) * S * er)) / (2.0 * I)
                        + (2 * bT * Math.pow(I / S, -1 + 2 * gamma + (2 * bT) / v) * er * lis) / (v * v)
                        + (2 * bT + v - 2 * gamma * v + 2 * lsh)
                                / (2.0 * Math.exp(Math.pow(2 * bT + (-1 + 2 * gamma) * v + 2 * lsh, 2) / (8.0 * v))
                                        * M_SQRT2 * M_SQRTPI * v * sv)
                        - (Math.pow(I / S, -1 + 2 * gamma + (2 * bT) / v) * (2 * bT + v - 2 * gamma * v + 4 * lis + 2 * lsh))
                                / (2.0 * Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8.0 * v))
                                        * M_SQRT2 * M_SQRTPI * v * sv)))
                / 2.0;
    }

    // -----------------------------------------------------------------------
    // Result builders mirroring the three C++ helpers.
    // -----------------------------------------------------------------------

    /** Storage struct for option results — mirrors C++ {@code OneAssetOption::results}. */
    private static final class Results {
        double value;
        double delta;
        double gamma;
        double rho;
        double dividendRho;
        double vega;
        double theta;
        double thetaPerDay;
        double strikeSensitivity;
        double strikeGamma;
        String exerciseType;
    }

    private Results europeanCallResults(final double S, final double X, final double rfD, final double dD,
            final double variance, final PlainVanillaPayoff originalPayoff) {
        final Results results = new Results();

        final double forwardPrice = S * dD / rfD;
        final BlackCalculator black = new BlackCalculator(Option.Type.Call, X, forwardPrice, Math.sqrt(variance), rfD);

        results.value = black.value();
        results.delta = black.delta(S);
        results.gamma = black.gamma(S);

        final DayCounter rfdc = process.riskFreeRate().currentLink().dayCounter();
        final DayCounter divdc = process.dividendYield().currentLink().dayCounter();
        final DayCounter voldc = process.blackVolatility().currentLink().dayCounter();

        double t = rfdc.yearFraction(process.riskFreeRate().currentLink().referenceDate(), a.exercise.lastDate());
        results.rho = black.rho(t);

        t = divdc.yearFraction(process.dividendYield().currentLink().referenceDate(), a.exercise.lastDate());
        results.dividendRho = black.dividendRho(t);

        t = voldc.yearFraction(process.blackVolatility().currentLink().referenceDate(), a.exercise.lastDate());
        results.vega = black.vega(t);
        results.theta = black.theta(S, t);
        results.thetaPerDay = black.thetaPerDay(S, t);

        results.strikeSensitivity = black.strikeSensitivity();
        results.strikeGamma = results.gamma * squared(S / X);
        results.exerciseType = "European";

        return results;
    }

    private Results immediateExercise(final double S, final double X) {
        final Results results = new Results();
        results.value = Math.max(0.0, S - X);
        results.delta = (S >= X) ? 1.0 : 0.0;
        results.gamma = 0.0;
        results.rho = 0.0;
        results.dividendRho = 0.0;
        results.vega = 0.0;
        results.theta = 0.0;
        results.thetaPerDay = 0.0;
        results.strikeSensitivity = -results.delta;
        results.strikeGamma = 0.0;
        results.exerciseType = "Immediate";
        return results;
    }

    private Results americanCallApproximation(final double S, final double X, final double rfD, final double dD,
            final double variance, final PlainVanillaPayoff originalPayoff) {

        final Results europeanResults = europeanCallResults(S, X, rfD, dD, variance, originalPayoff);

        Results results;

        final double bT = Math.log(dD / rfD);
        final double rT = Math.log(1.0 / rfD);

        final double beta = (0.5 - bT / variance)
                + Math.sqrt(squared(bT / variance - 0.5) + 2.0 * rT / variance);

        final double BInfinity = beta / (beta - 1.0) * X;
        final double B0 = (bT == rT) ? X : Math.max(X, rT / (rT - bT) * X);
        final double ht = -(bT + 2.0 * Math.sqrt(variance)) * B0 / (BInfinity - B0);

        final double I = B0 + (BInfinity - B0) * (1 - Math.exp(ht));

        final double fwd = S * dD / rfD;
        final double q = Math.log(I / fwd) / Math.sqrt(variance);

        if (S >= I) {
            results = immediateExercise(S, X);
        } else if (q > 12.5) {
            // Run-away exercise boundary — use European Greeks for accuracy.
            results = europeanResults;
        } else {
            results = new Results();

            final double phi_S_beta_I_I_rT_bT_v = phi(S, beta, I, I, rT, bT, variance);
            final double phi_S_1_I_I_rT_bT_v = phi(S, 1.0, I, I, rT, bT, variance);
            final double phi_S_1_X_I_rT_bT_V = phi(S, 1.0, X, I, rT, bT, variance);

            results.value = (I - X) * Math.pow(S / I, beta) * (1 - phi_S_beta_I_I_rT_bT_v)
                    + S * phi_S_1_I_I_rT_bT_v
                    - S * phi_S_1_X_I_rT_bT_V
                    - X * phi(S, 0.0, I, I, rT, bT, variance)
                    + X * phi(S, 0.0, X, I, rT, bT, variance);

            final double phi_S_S_beta_I_I_rT_bT_v = phi_S(S, beta, I, I, rT, bT, variance);
            final double phi_S_S_1_I_I_rT_bT_v = phi_S(S, 1.0, I, I, rT, bT, variance);
            final double phi_S_S_1_X_I_rT_bT_v = phi_S(S, 1.0, X, I, rT, bT, variance);

            results.delta = (I - X) * Math.pow(S / I, beta - 1) * beta / I * (1 - phi_S_beta_I_I_rT_bT_v)
                    - (I - X) * Math.pow(S / I, beta) * phi_S_S_beta_I_I_rT_bT_v
                    + phi_S_1_I_I_rT_bT_v
                    + S * phi_S_S_1_I_I_rT_bT_v
                    - phi_S_1_X_I_rT_bT_V
                    - S * phi_S_S_1_X_I_rT_bT_v
                    - X * phi_S(S, 0.0, I, I, rT, bT, variance)
                    + X * phi_S(S, 0.0, X, I, rT, bT, variance);

            final Date refDate = process.riskFreeRate().currentLink().referenceDate();
            final Date exerciseDate = a.exercise.lastDate();
            final DayCounter qdc = process.dividendYield().currentLink().dayCounter();
            final double tq = qdc.yearFraction(refDate, exerciseDate);

            final double betaDq = tq * (1 / variance
                    - 1 / (2 * Math.sqrt(squared(bT / variance - 0.5) + 2.0 * rT / variance))
                            * 2 * (bT / variance - 0.5) / variance);
            final double BInfinityDq = -X / squared(beta - 1.0) * betaDq;
            final double B0Dq = (dD <= rfD) ? 0.0
                    : (X * Math.log(rfD) / squared(Math.log(dD)) * tq);

            final double htDq = tq * B0 / (BInfinity - B0)
                    - (bT + 2.0 * Math.sqrt(variance))
                            * (B0Dq * (BInfinity - B0) - B0 * (BInfinityDq - B0Dq))
                            / squared(BInfinity - B0);
            final double IDq = B0Dq + (BInfinityDq - B0Dq) * (1 - Math.exp(ht))
                    - (BInfinity - B0) * Math.exp(ht) * htDq;

            final double phi_H_S_beta_I_I_rT_bT_v = phi_H(S, beta, I, I, rT, bT, variance);
            final double phi_I_S_beta_I_I_rT_bT_v = phi_I(S, beta, I, I, rT, bT, variance);
            final double phi_gamma_S_beta_I_I_rT_bT_v = phi_gamma(S, beta, I, I, rT, bT, variance);
            final double phi_bt_S_beta_I_I_rT_bT_v = phi_bt(S, beta, I, I, rT, bT, variance);
            final double phi_H_S_1_I_I_rT_bT_v = phi_H(S, 1.0, I, I, rT, bT, variance);
            final double phi_I_S_1_I_I_rT_bT_v = phi_I(S, 1.0, I, I, rT, bT, variance);
            final double phi_bt_S_1_I_I_rT_bT_v = phi_bt(S, 1.0, I, I, rT, bT, variance);
            final double phi_I_S_1_X_I_rT_bT_v = phi_I(S, 1.0, X, I, rT, bT, variance);
            final double phi_bt_S_1_X_I_rT_bT_v = phi_bt(S, 1.0, X, I, rT, bT, variance);
            final double phi_H_S_0_I_I_rT_bT_v = phi_H(S, 0.0, I, I, rT, bT, variance);
            final double phi_I_S_0_I_I_rT_bT_v = phi_I(S, 0.0, I, I, rT, bT, variance);
            final double phi_bt_S_0_I_I_rT_bT_v = phi_bt(S, 0.0, I, I, rT, bT, variance);
            final double phi_I_S_0_X_I_rT_bT_v = phi_I(S, 0.0, X, I, rT, bT, variance);
            final double phi_bt_S_0_X_I_rT_bT_v = phi_bt(S, 0.0, X, I, rT, bT, variance);

            results.dividendRho =
                    (IDq * Math.pow(S / I, beta)
                            + (I - X) * Math.pow(S / I, beta) * (betaDq * Math.log(S / I) - beta * 1 / I * IDq))
                            * (1 - phi_S_beta_I_I_rT_bT_v)
                            - (I - X) * Math.pow(S / I, beta)
                                    * (phi_H_S_beta_I_I_rT_bT_v * IDq
                                            + phi_I_S_beta_I_I_rT_bT_v * IDq
                                            + phi_gamma_S_beta_I_I_rT_bT_v * betaDq
                                            - phi_bt_S_beta_I_I_rT_bT_v * tq)
                            + S * (phi_H_S_1_I_I_rT_bT_v * IDq
                                    + phi_I_S_1_I_I_rT_bT_v * IDq
                                    - phi_bt_S_1_I_I_rT_bT_v * tq)
                            - S * (phi_I_S_1_X_I_rT_bT_v * IDq
                                    - phi_bt_S_1_X_I_rT_bT_v * tq)
                            - X * (phi_H_S_0_I_I_rT_bT_v * IDq
                                    + phi_I_S_0_I_I_rT_bT_v * IDq
                                    - phi_bt_S_0_I_I_rT_bT_v * tq)
                            + X * (phi_I_S_0_X_I_rT_bT_v * IDq
                                    - phi_bt_S_0_X_I_rT_bT_v * tq);

            final DayCounter rdc = process.riskFreeRate().currentLink().dayCounter();
            final double tr = rdc.yearFraction(refDate, exerciseDate);

            final double betaDr = tr * (-1 / variance
                    + 1 / (2 * Math.sqrt(squared(bT / variance - 0.5) + 2.0 * rT / variance))
                            * 2 * ((bT / variance - 0.5) / variance + 1 / variance));
            final double BInfinityDr = -X / squared(beta - 1.0) * betaDr;
            final double B0Dr = (dD <= rfD) ? 0.0 : (-X * tr / Math.log(dD));
            final double htDr = -tr * B0 / (BInfinity - B0)
                    - (bT + 2.0 * Math.sqrt(variance))
                            * (B0Dr * (BInfinity - B0) - B0 * (BInfinityDr - B0Dr))
                            / squared(BInfinity - B0);
            final double IDr = B0Dr + (BInfinityDr - B0Dr) * (1 - Math.exp(ht))
                    - (BInfinity - B0) * Math.exp(ht) * htDr;

            results.rho =
                    (IDr * Math.pow(S / I, beta)
                            + (I - X) * Math.pow(S / I, beta) * (betaDr * Math.log(S / I) - beta / I * IDr))
                            * (1 - phi_S_beta_I_I_rT_bT_v)
                            - (I - X) * Math.pow(S / I, beta)
                                    * (phi_H_S_beta_I_I_rT_bT_v * IDr
                                            + phi_I_S_beta_I_I_rT_bT_v * IDr
                                            + phi_gamma_S_beta_I_I_rT_bT_v * betaDr
                                            + phi_rt(S, beta, I, I, rT, bT, variance) * tr
                                            + phi_bt_S_beta_I_I_rT_bT_v * tr)
                            + S * (phi_H_S_1_I_I_rT_bT_v * IDr
                                    + phi_I_S_1_I_I_rT_bT_v * IDr
                                    + phi_rt(S, 1.0, I, I, rT, bT, variance) * tr
                                    + phi_bt_S_1_I_I_rT_bT_v * tr)
                            - S * (phi_I_S_1_X_I_rT_bT_v * IDr
                                    + phi_rt(S, 1.0, X, I, rT, bT, variance) * tr
                                    + phi_bt_S_1_X_I_rT_bT_v * tr)
                            - X * (phi_H_S_0_I_I_rT_bT_v * IDr
                                    + phi_I_S_0_I_I_rT_bT_v * IDr
                                    + phi_rt(S, 0.0, I, I, rT, bT, variance) * tr
                                    + phi_bt_S_0_I_I_rT_bT_v * tr)
                            + X * (phi_I_S_0_X_I_rT_bT_v * IDr
                                    + phi_rt(S, 0.0, X, I, rT, bT, variance) * tr
                                    + phi_bt_S_0_X_I_rT_bT_v * tr);

            // note: C++ re-declares beta here as a local — Java keeps the outer scope; the
            // values are identical since this is the same formula.

            final DayCounter vdc = process.blackVolatility().currentLink().dayCounter();
            final double tv = vdc.yearFraction(refDate, exerciseDate);
            final double varianceDv = 2 * Math.sqrt(variance * tv);

            final double betaDv = bT / squared(variance) * varianceDv
                    + -1 / (2 * Math.sqrt(squared(bT / variance - 0.5) + 2.0 * rT / variance))
                            * (2 * (bT / variance - 0.5) * bT * varianceDv / squared(variance)
                                    + 2 * rT / squared(variance) * varianceDv);
            final double BInfinityDv = -X / squared(beta - 1.0) * betaDv;
            final double htDv = -1 / Math.sqrt(variance) * varianceDv * B0 / (BInfinity - B0)
                    + (bT + 2 * Math.sqrt(variance)) * B0 / squared(BInfinity - B0) * BInfinityDv;

            final double IDv = BInfinityDv * (1 - Math.exp(ht))
                    - (BInfinity - B0) * Math.exp(ht) * htDv;

            results.vega =
                    (IDv * Math.pow(S / I, beta)
                            + (I - X) * Math.pow(S / I, beta) * (betaDv * Math.log(S / I) - beta / I * IDv))
                            * (1 - phi_S_beta_I_I_rT_bT_v)
                            - (I - X) * Math.pow(S / I, beta)
                                    * (phi_H_S_beta_I_I_rT_bT_v * IDv
                                            + phi_I_S_beta_I_I_rT_bT_v * IDv
                                            + phi_gamma_S_beta_I_I_rT_bT_v * betaDv
                                            + phi_v(S, beta, I, I, rT, bT, variance) * varianceDv)
                            + S * (phi_H_S_1_I_I_rT_bT_v * IDv
                                    + phi_I_S_1_I_I_rT_bT_v * IDv
                                    + phi_v(S, 1.0, I, I, rT, bT, variance) * varianceDv)
                            - S * (phi_I_S_1_X_I_rT_bT_v * IDv
                                    + phi_v(S, 1.0, X, I, rT, bT, variance) * varianceDv)
                            - X * (phi_H_S_0_I_I_rT_bT_v * IDv
                                    + phi_I_S_0_I_I_rT_bT_v * IDv
                                    + phi_v(S, 0.0, I, I, rT, bT, variance) * varianceDv)
                            + X * (phi_I_S_0_X_I_rT_bT_v * IDv
                                    + phi_v(S, 0.0, X, I, rT, bT, variance) * varianceDv);

            results.gamma =
                    (I - X) * Math.pow(S / I, beta - 2) * beta * (beta - 1) / squared(I)
                            * (1 - phi_S_beta_I_I_rT_bT_v)
                            - 2 * (I - X) * Math.pow(S / I, beta - 1) * beta / I * phi_S_S_beta_I_I_rT_bT_v
                            - (I - X) * Math.pow(S / I, beta) * phi_SS(S, beta, I, I, rT, bT, variance)

                            + 2 * phi_S_S_1_I_I_rT_bT_v
                            + S * phi_SS(S, 1.0, I, I, rT, bT, variance)

                            - 2 * phi_S_S_1_X_I_rT_bT_v
                            - S * phi_SS(S, 1.0, X, I, rT, bT, variance)

                            - X * phi_SS(S, 0.0, I, I, rT, bT, variance)
                            + X * phi_SS(S, 0.0, X, I, rT, bT, variance);

            final double vol = Math.sqrt(variance / tv);

            final Date tomorrow = refDate.add(new Period(1, TimeUnit.Days));
            final double dtq = qdc.yearFraction(refDate, exerciseDate)
                    - qdc.yearFraction(tomorrow, exerciseDate);
            final double dtr = rdc.yearFraction(refDate, exerciseDate)
                    - rdc.yearFraction(tomorrow, exerciseDate);
            final double dtv = vdc.yearFraction(refDate, exerciseDate)
                    - vdc.yearFraction(tomorrow, exerciseDate);

            results.thetaPerDay = -(0.5 * results.vega * vol / tv * dtv
                    + results.rho * rT / (tr * tr) * dtr
                    + results.dividendRho * (rT - bT) / (tq * tq) * dtq);
            results.theta = 365 * results.thetaPerDay;

            results.strikeSensitivity = results.value / X - S / X * results.delta;
            results.strikeGamma = results.gamma * squared(S / X);
            results.exerciseType = "American";
        }

        // check if European engine gives higher NPV
        if (results.value < europeanResults.value) {
            results = europeanResults;
        }

        return results;
    }

    // -----------------------------------------------------------------------
    // calculate()
    // -----------------------------------------------------------------------

    @Override
    public void calculate() {
        QL.require(a.exercise.type() == Exercise.Type.American, NOT_AN_AMERICAN_OPTION);
        QL.require(a.exercise instanceof AmericanExercise, NON_AMERICAN_EXERCISE_GIVEN);
        final AmericanExercise ex = (AmericanExercise) a.exercise;
        QL.require(!ex.payoffAtExpiry(), PAYOFF_AT_EXPIRY_NOT_HANDLED);
        QL.require(a.payoff instanceof PlainVanillaPayoff, NON_PLAIN_PAYOFF_GIVEN);

        final PlainVanillaPayoff originalPayoff = (PlainVanillaPayoff) a.payoff;

        double variance = process.blackVolatility().currentLink()
                .blackVariance(ex.lastDate(), originalPayoff.strike());
        double dividendDiscount = process.dividendYield().currentLink().discount(ex.lastDate());
        double riskFreeDiscount = process.riskFreeRate().currentLink().discount(ex.lastDate());
        double spot = process.stateVariable().currentLink().value();
        QL.require(spot > 0.0, "negative or null underlying given");
        double strike = originalPayoff.strike();

        if (originalPayoff.optionType() == Option.Type.Put) {
            // use put-call symmetry
            double tmp = spot;
            spot = strike;
            strike = tmp;

            tmp = riskFreeDiscount;
            riskFreeDiscount = dividendDiscount;
            dividendDiscount = tmp;
        }

        if (dividendDiscount > 1.0 && riskFreeDiscount > dividendDiscount) {
            QL.error("double-boundary case r<q<0 for a call given");
        }

        Results computed;
        if (dividendDiscount >= 1.0 && dividendDiscount >= riskFreeDiscount) {
            computed = europeanCallResults(spot, strike, riskFreeDiscount, dividendDiscount, variance,
                    originalPayoff);
        } else {
            // early exercise can be optimal - use approximation
            computed = americanCallApproximation(spot, strike, riskFreeDiscount, dividendDiscount, variance,
                    originalPayoff);
        }

        // check if immediate exercise gives higher NPV
        if (computed.value < (spot - strike) * (1 + 10 * Constants.QL_EPSILON)) {
            computed = immediateExercise(spot, strike);
        }

        // If the original payoff is a Put, apply put-call symmetry Greek correction.
        if (originalPayoff.optionType() == Option.Type.Put) {
            // swap delta and strikeSensitivity
            double tmp = computed.delta;
            computed.delta = computed.strikeSensitivity;
            computed.strikeSensitivity = tmp;

            // swap gamma and strikeGamma
            tmp = computed.gamma;
            computed.gamma = computed.strikeGamma;
            computed.strikeGamma = tmp;

            // swap rho and dividendRho
            tmp = computed.rho;
            computed.rho = computed.dividendRho;
            computed.dividendRho = tmp;

            final double tr = process.riskFreeRate().currentLink().dayCounter().yearFraction(
                    process.riskFreeRate().currentLink().referenceDate(),
                    a.exercise.lastDate());
            final double tq = process.dividendYield().currentLink().dayCounter().yearFraction(
                    process.dividendYield().currentLink().referenceDate(),
                    a.exercise.lastDate());

            computed.rho *= tr / tq;
            computed.dividendRho *= tq / tr;
        }

        // Copy computed values into engine results.
        r.value = computed.value;
        greeks.delta = computed.delta;
        greeks.gamma = computed.gamma;
        greeks.theta = computed.theta;
        greeks.vega = computed.vega;
        greeks.rho = computed.rho;
        greeks.dividendRho = computed.dividendRho;

        moreGreeks.thetaPerDay = computed.thetaPerDay;
        moreGreeks.strikeSensitivity = computed.strikeSensitivity;

        this.exerciseType = computed.exerciseType;
    }
}
