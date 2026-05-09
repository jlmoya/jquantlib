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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.cashflow;

import org.jquantlib.Settings;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussKronrodAdaptive;
import org.jquantlib.math.integrals.GaussKronrodNonAdaptive;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.time.Date;

/**
 * Hagan CMS-coupon pricer via Gauss-Kronrod numerical replication.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code NumericHaganPricer} in
 * {@code ql/cashflows/conundrumpricer.{hpp,cpp}}. Replicates the CMS
 * convexity adjustment by integrating an option-payoff x F''(K)
 * integrand against vanilla swaption prices. Uses the same heuristic
 * upper-limit estimation, variable-change-cubed accelerator, and
 * fall-back to GaussKronrodAdaptive on non-convergence as the C++
 * version.
 */
public class NumericHaganPricer extends HaganPricer {

    private static final double DEFAULT_LOWER_LIMIT = 0.0;
    private static final double DEFAULT_UPPER_LIMIT = 1.0;
    private static final double DEFAULT_PRECISION = 1.0e-6;

    private double lowerLimit_;
    private double upperLimit_;
    private double stdDeviationsForLowerLimit_;
    private double stdDeviationsForUpperLimit_;
    private final double requiredStdDeviations_ = 8.0;
    private final double precision_;
    @SuppressWarnings("unused")
    private final double refiningIntegrationTolerance_ = 0.0001;
    private final double hardUpperLimit_;

    public NumericHaganPricer(final Handle<SwaptionVolatilityStructure> swaptionVol,
                              final GFunctionFactory.YieldCurveModel modelOfYieldCurve,
                              final Handle<Quote> meanReversion) {
        this(swaptionVol, modelOfYieldCurve, meanReversion,
                DEFAULT_LOWER_LIMIT, DEFAULT_UPPER_LIMIT, DEFAULT_PRECISION,
                Double.MAX_VALUE);
    }

    public NumericHaganPricer(final Handle<SwaptionVolatilityStructure> swaptionVol,
                              final GFunctionFactory.YieldCurveModel modelOfYieldCurve,
                              final Handle<Quote> meanReversion,
                              final double lowerLimit,
                              final double upperLimit,
                              final double precision,
                              final double hardUpperLimit) {
        super(swaptionVol, modelOfYieldCurve, meanReversion);
        this.lowerLimit_ = lowerLimit;
        this.upperLimit_ = upperLimit;
        this.precision_ = precision;
        this.hardUpperLimit_ = hardUpperLimit;
    }

    public double upperLimit() { return upperLimit_; }
    public double lowerLimit() { return lowerLimit_; }
    public double stdDeviations() { return stdDeviationsForUpperLimit_; }

    @Override
    protected double optionletPrice(final Option.Type optionType, final double strike) {
        final ConundrumIntegrand integrand = new ConundrumIntegrand(
                vanillaOptionPricer_, gFunction_,
                annuity_, swapRateValue_, strike, optionType);

        stdDeviationsForUpperLimit_ = requiredStdDeviations_;
        stdDeviationsForLowerLimit_ = requiredStdDeviations_;

        double a;
        double b;
        double integralValue;
        if (optionType == Option.Type.Call) {
            upperLimit_ = resetUpperLimit(stdDeviationsForUpperLimit_);
            integralValue = integrate(strike, upperLimit_, integrand);
        } else {
            lowerLimit_ = resetLowerLimit(stdDeviationsForLowerLimit_);
            a = Math.min(strike, lowerLimit_);
            b = strike;
            integralValue = integrate(a, b, integrand);
        }

        final double dFdK = integrand.firstDerivativeOfF(strike);
        final double swaptionPrice = vanillaOptionPricer_.evaluate(strike, optionType, annuity_);

        // v. Hagan, Conundrums..., formulas 2.17a, 2.18a
        return coupon_.accrualPeriod() * (discount_ / annuity_)
                * ((1.0 + dFdK) * swaptionPrice + optionType.toInteger() * integralValue);
    }

    @Override
    public double swapletPrice() {
        final Date today = new Settings().evaluationDate();
        if (fixingDate_.le(today)) {
            final double Rs = coupon_.swapIndex().fixing(fixingDate_);
            return (gearing_ * Rs + spread_) * (coupon_.accrualPeriod() * discount_);
        }
        final double atmCapletPrice = optionletPrice(Option.Type.Call, swapRateValue_);
        final double atmFloorletPrice = optionletPrice(Option.Type.Put, swapRateValue_);
        return gearing_ * (coupon_.accrualPeriod() * discount_ * swapRateValue_
                            + atmCapletPrice - atmFloorletPrice)
                + spreadLegValue_;
    }

    private double integrate(final double a, double b, final ConundrumIntegrand integrand) {
        double result;

        // semi-infinite interval
        if (a > 0) {
            // estimate the actual upper boundary by testing integrand values
            double upperBoundary = 2 * a;
            while (integrand.value(upperBoundary) > precision_) {
                upperBoundary *= 2.0;
            }
            // sometimes b < a because of a wrong stdev-based estimation of b
            if (b > a) {
                upperBoundary = Math.min(upperBoundary, b);
            }

            final GaussKronrodNonAdaptive gaussKronrodNonAdaptive =
                    new GaussKronrodNonAdaptive(precision_, 1000000, 1.0);

            upperBoundary = Math.max(a, Math.min(upperBoundary, hardUpperLimit_));
            if (upperBoundary > 2 * a) {
                final int k = 3;
                final VariableChange variableChange = new VariableChange(integrand, a, upperBoundary, k);
                result = gaussKronrodNonAdaptive.op(new Ops.DoubleOp() {
                    @Override
                    public double op(final double x) {
                        return variableChange.value(x);
                    }
                }, 0.0, 1.0);
            } else {
                result = gaussKronrodNonAdaptive.op(integrand, a, upperBoundary);
            }

            if (!gaussKronrodNonAdaptive.isIntegrationSuccess()) {
                final GaussKronrodAdaptive integrator = new GaussKronrodAdaptive(precision_, 100000);
                b = Math.max(a, Math.min(b, hardUpperLimit_));
                result = integrator.op(integrand, a, b);
            }
        } else {
            // a <= 0: original algorithm
            b = Math.max(a, Math.min(b, hardUpperLimit_));
            if (swaptionVolatility().currentLink().volatilityType() == VolatilityType.ShiftedLognormal) {
                final GaussKronrodAdaptive integrator = new GaussKronrodAdaptive(precision_, 100000);
                result = integrator.op(integrand, a, b);
            } else {
                // Normal vol + floorlet -> use non-adaptive (adaptive overruns evals
                // when integrating across a negative strike)
                final GaussKronrodNonAdaptive integrator =
                        new GaussKronrodNonAdaptive(precision_, 100000, 1.0);
                result = integrator.op(integrand, a, b);
            }
        }
        return result;
    }

    private double resetUpperLimit(final double stdDeviationsForUpperLimit) {
        final double variance = swaptionVolatility().currentLink()
                .blackVariance(fixingDate_, swapTenor_, swapRateValue_, false);
        if (swaptionVolatility().currentLink().volatilityType() == VolatilityType.ShiftedLognormal) {
            return swapRateValue_ * Math.exp(stdDeviationsForUpperLimit * Math.sqrt(variance));
        }
        return swapRateValue_ + stdDeviationsForUpperLimit * Math.sqrt(variance);
    }

    private double resetLowerLimit(final double stdDeviationsForUpperLimit) {
        final double variance = swaptionVolatility().currentLink()
                .blackVariance(fixingDate_, swapTenor_, swapRateValue_, false);
        if (swaptionVolatility().currentLink().volatilityType() == VolatilityType.ShiftedLognormal) {
            return lowerLimit_;
        }
        return swapRateValue_ - stdDeviationsForUpperLimit * Math.sqrt(variance);
    }

    //========================================================================
    //                           Helper integrand
    //========================================================================
    /**
     * Variable change x -> a + (b-a)*(t/(b-a))^k for the semi-infinite tail
     * accelerator. Mirrors C++ anonymous-namespace {@code VariableChange}.
     */
    private static final class VariableChange {
        private final double a_;
        private final double width_;
        private final ConundrumIntegrand f_;
        private final int k_;

        VariableChange(final ConundrumIntegrand f, final double a, final double b, final int k) {
            this.a_ = a;
            this.width_ = b - a;
            this.f_ = f;
            this.k_ = k;
        }

        double value(final double x) {
            double temp = width_;
            for (int i = 1; i < k_; ++i) {
                temp *= x;
            }
            final double newVar = a_ + x * temp;
            return f_.value(newVar) * k_ * temp;
        }
    }

    /**
     * Conundrum-replication integrand: option(K) * F''(K). Mirrors C++
     * {@code NumericHaganPricer::ConundrumIntegrand}.
     */
    public static final class ConundrumIntegrand implements Ops.DoubleOp {
        private final VanillaOptionPricer vanillaOptionPricer_;
        private final double forwardValue_;
        private final double annuity_;
        private double strike_;
        private final Option.Type optionType_;
        private final GFunction gFunction_;

        public ConundrumIntegrand(final VanillaOptionPricer o,
                                  final GFunction gFunction,
                                  final double annuity,
                                  final double forwardValue,
                                  final double strike,
                                  final Option.Type optionType) {
            this.vanillaOptionPricer_ = o;
            this.forwardValue_ = forwardValue;
            this.annuity_ = annuity;
            this.strike_ = strike;
            this.optionType_ = optionType;
            this.gFunction_ = gFunction;
        }

        public double strike() { return strike_; }
        public double annuity() { return annuity_; }
        public void setStrike(final double strike) { this.strike_ = strike; }

        public double functionF(final double x) {
            final double Gx = gFunction_.evaluate(x);
            final double GR = gFunction_.evaluate(forwardValue_);
            return (x - strike_) * (Gx / GR - 1.0);
        }

        public double firstDerivativeOfF(final double x) {
            final double Gx = gFunction_.evaluate(x);
            final double GR = gFunction_.evaluate(forwardValue_);
            final double G1 = gFunction_.firstDerivative(x);
            return (Gx / GR - 1.0) + G1 / GR * (x - strike_);
        }

        public double secondDerivativeOfF(final double x) {
            final double GR = gFunction_.evaluate(forwardValue_);
            final double G1 = gFunction_.firstDerivative(x);
            final double G2 = gFunction_.secondDerivative(x);
            return 2.0 * G1 / GR + (x - strike_) * G2 / GR;
        }

        public double value(final double x) {
            return op(x);
        }

        @Override
        public double op(final double x) {
            final double option = vanillaOptionPricer_.evaluate(x, optionType_, annuity_);
            return option * secondDerivativeOfF(x);
        }
    }
}
