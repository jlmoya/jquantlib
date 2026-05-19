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
 Copyright (C) 2015 Thema Consulting SA
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.experimental.barrieroption.DoubleBarrierOption;
import org.jquantlib.experimental.barrieroption.DoubleBarrierType;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.time.Frequency;

/**
 * Analytic pricing engine for double barrier binary options.
 * <p>
 * Mirrors {@code QuantLib::AnalyticDoubleBarrierBinaryEngine} from
 * {@code ql/pricingengines/barrier/analyticdoublebarrierbinaryengine.cpp} (v1.42.1).
 * <p>
 * Implements C.H.Hui series ("One-Touch Double Barrier Binary Option Values", Applied Financial Economics 6/1996), as
 * described in "The complete guide to option pricing formulas 2nd Ed", E.G. Haug, McGraw-Hill, p.180.
 * <p>
 * The Knock In part of KI+KO and KO+KI options pays at hit, while the Double Knock In pays at end. This engine thus
 * requires European exercise for Double Knock options, and American exercise for KIKO/KOKI.
 *
 * @author JQuantLib migration
 */
public class AnalyticDoubleBarrierBinaryEngine extends DoubleBarrierOption.EngineImpl {

    private static final double PI = 3.14159265358979323846264338327950;

    private final GeneralizedBlackScholesProcess process_;

    public AnalyticDoubleBarrierBinaryEngine(final GeneralizedBlackScholesProcess process) {
        this.process_ = process;
        this.process_.addObserver(this);
    }

    @Override
    public void calculate() {
        final DoubleBarrierOption.ArgumentsImpl a = args();
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;
        final DoubleBarrierType barrierType = a.barrierType;

        if ( barrierType == DoubleBarrierType.KIKO || barrierType == DoubleBarrierType.KOKI ) {
            QL.require(a.exercise instanceof AmericanExercise, "KIKO/KOKI options must have American exercise");
            final AmericanExercise ex = (AmericanExercise) a.exercise;
            QL.require(ex.dates().get(0).le(process_.blackVolatility().currentLink().referenceDate()),
                    "American option with window exercise not handled yet");
        } else {
            QL.require(a.exercise instanceof EuropeanExercise, "non-European exercise given");
        }

        QL.require(a.payoff instanceof CashOrNothingPayoff, "a cash-or-nothing payoff must be given");
        final CashOrNothingPayoff payoff = (CashOrNothingPayoff) a.payoff;

        final double spot = process_.stateVariable().currentLink().value();
        QL.require(spot > 0.0, "negative or null underlying given");

        final double variance = process_.blackVolatility().currentLink()
                .blackVariance(a.exercise.lastDate(), payoff.strike());

        final double barrierLo = a.barrier_lo;
        final double barrierHi = a.barrier_hi;
        QL.require(barrierLo > 0.0, "positive low barrier value required");
        QL.require(barrierHi > 0.0, "positive high barrier value required");
        QL.require(barrierLo < barrierHi, "barrier_lo must be < barrier_hi");
        QL.require(barrierType == DoubleBarrierType.KnockIn || barrierType == DoubleBarrierType.KnockOut
                        || barrierType == DoubleBarrierType.KIKO || barrierType == DoubleBarrierType.KOKI,
                "Unsupported barrier type");

        // degenerate cases
        switch ( barrierType ) {
        case KnockOut:
            if ( spot <= barrierLo || spot >= barrierHi ) {
                setZeroResult(r);
                return;
            }
            break;
        case KnockIn:
            if ( spot <= barrierLo || spot >= barrierHi ) {
                r.value = payoff.getCashPayoff();
                r.greeks().delta = 0;
                r.greeks().gamma = 0;
                r.greeks().vega = 0;
                r.greeks().rho = 0;
                return;
            }
            break;
        case KIKO:
            if ( spot >= barrierHi ) {
                setZeroResult(r);
                return;
            } else if ( spot <= barrierLo ) {
                r.value = payoff.getCashPayoff();
                r.greeks().delta = 0;
                r.greeks().gamma = 0;
                r.greeks().vega = 0;
                r.greeks().rho = 0;
                return;
            }
            break;
        case KOKI:
            if ( spot <= barrierLo ) {
                setZeroResult(r);
                return;
            } else if ( spot >= barrierHi ) {
                r.value = payoff.getCashPayoff();
                r.greeks().delta = 0;
                r.greeks().gamma = 0;
                r.greeks().vega = 0;
                r.greeks().rho = 0;
                return;
            }
            break;
        default:
            throw new LibraryException("Unsupported barrier type");
        }

        switch ( barrierType ) {
        case KnockOut:
        case KnockIn:
            r.value = payoffAtExpiry(payoff, a, spot, variance, barrierType);
            break;
        case KIKO:
        case KOKI:
            r.value = payoffKIKO(payoff, a, spot, variance, barrierType);
            break;
        default:
            throw new LibraryException("Unsupported barrier type");
        }
    }

    private void setZeroResult(final OneAssetOption.ResultsImpl r) {
        r.value = 0;
        r.greeks().delta = 0;
        r.greeks().gamma = 0;
        r.greeks().vega = 0;
        r.greeks().rho = 0;
    }

    /**
     * Mirrors C++ {@code AnalyticDoubleBarrierBinaryEngine_helper::payoffAtExpiry}.
     */
    private double payoffAtExpiry(final CashOrNothingPayoff payoff, final DoubleBarrierOption.ArgumentsImpl a,
            final double spot, final double variance, final DoubleBarrierType barrierType) {
        return payoffAtExpiry(payoff, a, spot, variance, barrierType, 100, 1e-8);
    }

    private double payoffAtExpiry(final CashOrNothingPayoff payoff, final DoubleBarrierOption.ArgumentsImpl a,
            final double spot, final double variance, final DoubleBarrierType barrierType, final int maxIteration,
            final double requiredConvergence) {
        QL.require(spot > 0.0, "positive spot value required");
        QL.require(variance >= 0.0, "negative variance not allowed");

        final double residualTime = process_.time(a.exercise.lastDate());
        QL.require(residualTime > 0.0, "expiration time must be > 0");

        final double cash = payoff.getCashPayoff();
        final double barrierLo = a.barrier_lo;
        final double barrierHi = a.barrier_hi;

        final double sigmaq = variance / residualTime;
        final double r = process_.riskFreeRate().currentLink()
                .zeroRate(residualTime, Compounding.Continuous, Frequency.NoFrequency, false).rate();
        final double q = process_.dividendYield().currentLink()
                .zeroRate(residualTime, Compounding.Continuous, Frequency.NoFrequency, false).rate();
        final double b = r - q;

        final double alpha = -0.5 * (2 * b / sigmaq - 1);
        final double beta = -0.25 * Math.pow(2 * b / sigmaq - 1, 2) - 2 * r / sigmaq;
        final double Z = Math.log(barrierHi / barrierLo);
        final double factor = (2 * PI * cash) / (Z * Z); // common factor
        final double loAlpha = Math.pow(spot / barrierLo, alpha);
        final double hiAlpha = Math.pow(spot / barrierHi, alpha);

        double tot = 0;
        double term = 0;
        for ( int i = 1; i < maxIteration; i++ ) {
            final double term1 = (loAlpha - Math.pow(-1.0, i) * hiAlpha) / (alpha * alpha + Math.pow(i * PI / Z, 2));
            final double term2 = Math.sin(i * PI / Z * Math.log(spot / barrierLo));
            final double term3 = Math.exp(-0.5 * (Math.pow(i * PI / Z, 2) - beta) * variance);
            term = factor * i * term1 * term2 * term3;
            tot += term;
        }

        QL.require(Math.abs(term) < requiredConvergence, "serie did not converge sufficiently fast");

        if ( barrierType == DoubleBarrierType.KnockOut ) {
            return Math.max(tot, 0.0); // KO
        } else {
            final double discount = process_.riskFreeRate().currentLink().discount(a.exercise.lastDate());
            QL.require(discount > 0.0, "positive discount required");
            return Math.max(cash * discount - tot, 0.0); // KI
        }
    }

    /**
     * Mirrors C++ {@code AnalyticDoubleBarrierBinaryEngine_helper::payoffKIKO}.
     */
    private double payoffKIKO(final CashOrNothingPayoff payoff, final DoubleBarrierOption.ArgumentsImpl a,
            final double spot, final double variance, final DoubleBarrierType barrierType) {
        return payoffKIKO(payoff, a, spot, variance, barrierType, 1000, 1e-8);
    }

    private double payoffKIKO(final CashOrNothingPayoff payoff, final DoubleBarrierOption.ArgumentsImpl a,
            final double spot, final double variance, final DoubleBarrierType barrierType, final int maxIteration,
            final double requiredConvergence) {
        QL.require(spot > 0.0, "positive spot value required");
        QL.require(variance >= 0.0, "negative variance not allowed");

        final double residualTime = process_.time(a.exercise.lastDate());
        QL.require(residualTime > 0.0, "expiration time must be > 0");

        final double cash = payoff.getCashPayoff();
        double barrierLo = a.barrier_lo;
        double barrierHi = a.barrier_hi;
        if ( barrierType == DoubleBarrierType.KOKI ) {
            // swap
            final double tmp = barrierLo;
            barrierLo = barrierHi;
            barrierHi = tmp;
        }

        final double sigmaq = variance / residualTime;
        final double r = process_.riskFreeRate().currentLink()
                .zeroRate(residualTime, Compounding.Continuous, Frequency.NoFrequency, false).rate();
        final double q = process_.dividendYield().currentLink()
                .zeroRate(residualTime, Compounding.Continuous, Frequency.NoFrequency, false).rate();
        final double b = r - q;

        final double alpha = -0.5 * (2 * b / sigmaq - 1);
        final double beta = -0.25 * Math.pow(2 * b / sigmaq - 1, 2) - 2 * r / sigmaq;
        final double Z = Math.log(barrierHi / barrierLo);
        final double logSL = Math.log(spot / barrierLo);

        double tot = 0;
        double term = 0;
        for ( int i = 1; i < maxIteration; i++ ) {
            final double factor = Math.pow(i * PI / Z, 2) - beta;
            final double term1 = (beta - Math.pow(i * PI / Z, 2) * Math.exp(-0.5 * factor * variance)) / factor;
            final double term2 = Math.sin(i * PI / Z * logSL);
            term = (2.0 / (i * PI)) * term1 * term2;
            tot += term;
        }
        tot += 1 - logSL / Z;
        tot *= cash * Math.pow(spot / barrierLo, alpha);

        QL.require(Math.abs(term) < requiredConvergence, "serie did not converge sufficiently fast");

        return Math.max(tot, 0.0);
    }
}
