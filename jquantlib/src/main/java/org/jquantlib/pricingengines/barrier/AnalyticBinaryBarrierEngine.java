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
 Copyright (C) 2014 Thema Consulting SA
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AssetOrNothingPayoff;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Analytic pricing engine for American binary barrier options.
 * <p>
 * Mirrors {@code QuantLib::AnalyticBinaryBarrierEngine} from
 * {@code ql/pricingengines/barrier/analyticbinarybarrierengine.cpp} (v1.42.1).
 * <p>
 * The formulas are taken from "The complete guide to option pricing formulas 2nd Ed",
 * E.G. Haug, McGraw-Hill, p.176 and following.
 *
 * @author JQuantLib migration
 */
public class AnalyticBinaryBarrierEngine extends BarrierOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;

    public AnalyticBinaryBarrierEngine(final GeneralizedBlackScholesProcess process) {
        this.process_ = process;
        this.process_.addObserver(this);
    }

    @Override
    public void calculate() {
        final BarrierOption.ArgumentsImpl a = (BarrierOption.ArgumentsImpl) arguments_;
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        QL.require(a.exercise instanceof AmericanExercise, "non-American exercise given");
        final AmericanExercise ex = (AmericanExercise) a.exercise;
        QL.require(ex.payoffAtExpiry(), "payoff must be at expiry");

        // C++: ex->dates()[0] <= process_->blackVolatility()->referenceDate()
        QL.require(ex.dates().get(0).le(process_.blackVolatility().currentLink().referenceDate()),
                "American option with window exercise not handled yet");

        QL.require(a.payoff instanceof StrikedTypePayoff, "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;

        final double spot = process_.stateVariable().currentLink().value();
        QL.require(spot > 0.0, "negative or null underlying given");

        final double variance = process_.blackVolatility().currentLink().blackVariance(
                ex.lastDate(), payoff.strike());
        final double barrier = a.barrier;
        QL.require(barrier > 0.0, "positive barrier value required");
        final BarrierType barrierType = a.barrierType;

        // KO degenerate cases
        if ((barrierType == BarrierType.DownOut && spot <= barrier)
                || (barrierType == BarrierType.UpOut && spot >= barrier)) {
            r.value = 0;
            r.greeks().delta = 0;
            r.greeks().gamma = 0;
            r.greeks().vega = 0;
            r.greeks().theta = 0;
            r.greeks().rho = 0;
            r.greeks().dividendRho = 0;
            return;
        }

        // KI degenerate cases — knocked in becomes a digital European
        if ((barrierType == BarrierType.DownIn && spot <= barrier)
                || (barrierType == BarrierType.UpIn && spot >= barrier)) {
            final Exercise euExercise = new EuropeanExercise(a.exercise.lastDate());
            final VanillaOption opt = new VanillaOption(payoff, euExercise);
            opt.setPricingEngine(new AnalyticEuropeanEngine(process_));
            r.value = opt.NPV();
            r.greeks().delta = opt.delta();
            r.greeks().gamma = opt.gamma();
            r.greeks().vega = opt.vega();
            r.greeks().theta = opt.theta();
            r.greeks().rho = opt.rho();
            r.greeks().dividendRho = opt.dividendRho();
            return;
        }

        final double riskFreeDiscount = process_.riskFreeRate().currentLink().discount(ex.lastDate());
        r.value = payoffAtExpiry(payoff, a, ex, spot, variance, riskFreeDiscount);
    }


    /**
     * Mirrors C++ {@code AnalyticBinaryBarrierEngine_helper::payoffAtExpiry}.
     * Computes the value of a binary barrier option that pays at expiry.
     */
    private double payoffAtExpiry(final StrikedTypePayoff payoff,
                                   final BarrierOption.ArgumentsImpl a,
                                   final Exercise exercise,
                                   final double spot,
                                   final double variance,
                                   final double discount) {
        final double dividendDiscount =
                process_.dividendYield().currentLink().discount(exercise.lastDate());

        QL.require(spot > 0.0, "positive spot value required");
        QL.require(discount > 0.0, "positive discount required");
        QL.require(dividendDiscount > 0.0, "positive dividend discount required");
        QL.require(variance >= 0.0, "negative variance not allowed");

        final Option.Type type = payoff.optionType();
        final double strike = payoff.strike();
        final double barrier = a.barrier;
        QL.require(barrier > 0.0, "positive barrier value required");
        final BarrierType barrierType = a.barrierType;

        final double stdDev = Math.sqrt(variance);
        double mu = Math.log(dividendDiscount / discount) / variance - 0.5;
        double K = 0;

        if (payoff instanceof CashOrNothingPayoff) {
            K = ((CashOrNothingPayoff) payoff).getCashPayoff();
        }

        if (payoff instanceof AssetOrNothingPayoff) {
            mu += 1.0;
            K = spot * dividendDiscount / discount; // forward
        }

        final double logSX = Math.log(spot / strike);
        final double logSH = Math.log(spot / barrier);
        final double logHS = Math.log(barrier / spot);
        final double logH2SX = Math.log(barrier * barrier / (spot * strike));
        final double HS2mu = Math.pow(barrier / spot, 2 * mu);

        final double eta = (barrierType == BarrierType.DownIn
                || barrierType == BarrierType.DownOut) ? 1.0 : -1.0;
        final double phi = (type == Option.Type.Call) ? 1.0 : -1.0;

        double cumX1, cumX2, cumY1, cumY2;
        if (variance >= Constants.QL_EPSILON) {
            // mu*stddev instead of (mu+1)*stddev — cash-or-nothing doesn't need it,
            // and asset-or-nothing's mu has already been bumped by +1 above.
            final double x1 = phi * (logSX / stdDev + mu * stdDev);
            final double x2 = phi * (logSH / stdDev + mu * stdDev);
            final double y1 = eta * (logH2SX / stdDev + mu * stdDev);
            final double y2 = eta * (logHS / stdDev + mu * stdDev);

            final CumulativeNormalDistribution f = new CumulativeNormalDistribution();
            cumX1 = f.op(x1);
            cumX2 = f.op(x2);
            cumY1 = f.op(y1);
            cumY2 = f.op(y2);
        } else {
            cumX1 = (logSX > 0) ? 1.0 : 0.0;
            cumX2 = (logSH > 0) ? 1.0 : 0.0;
            cumY1 = (logH2SX > 0) ? 1.0 : 0.0;
            cumY2 = (logHS > 0) ? 1.0 : 0.0;
        }

        double alpha = 0;

        switch (barrierType) {
            case DownIn:
                if (type == Option.Type.Call) {
                    if (strike >= barrier) {
                        alpha = HS2mu * cumY1; // B3
                    } else {
                        alpha = cumX1 - cumX2 + HS2mu * cumY2; // B1-B2+B4
                    }
                } else {
                    if (strike >= barrier) {
                        alpha = cumX2 + HS2mu * (-cumY1 + cumY2); // B2-B3+B4
                    } else {
                        alpha = cumX1; // B1
                    }
                }
                break;

            case UpIn:
                if (type == Option.Type.Call) {
                    if (strike >= barrier) {
                        alpha = cumX1; // B1
                    } else {
                        alpha = cumX2 + HS2mu * (-cumY1 + cumY2); // B2-B3+B4
                    }
                } else {
                    if (strike >= barrier) {
                        alpha = cumX1 - cumX2 + HS2mu * cumY2; // B1-B2+B4
                    } else {
                        alpha = HS2mu * cumY1; // B3
                    }
                }
                break;

            case DownOut:
                if (type == Option.Type.Call) {
                    if (strike >= barrier) {
                        alpha = cumX1 - HS2mu * cumY1; // B1-B3
                    } else {
                        alpha = cumX2 - HS2mu * cumY2; // B2-B4
                    }
                } else {
                    if (strike >= barrier) {
                        alpha = cumX1 - cumX2 + HS2mu * (cumY1 - cumY2); // B1-B2+B3-B4
                    } else {
                        alpha = 0;
                    }
                }
                break;

            case UpOut:
                if (type == Option.Type.Call) {
                    if (strike >= barrier) {
                        alpha = 0;
                    } else {
                        alpha = cumX1 - cumX2 + HS2mu * (cumY1 - cumY2); // B1-B2+B3-B4
                    }
                } else {
                    if (strike >= barrier) {
                        alpha = cumX2 - HS2mu * cumY2; // B2-B4
                    } else {
                        alpha = cumX1 - HS2mu * cumY1; // B1-B3
                    }
                }
                break;
            default:
                throw new LibraryException("invalid barrier type");
        }

        return discount * K * alpha;
    }
}
