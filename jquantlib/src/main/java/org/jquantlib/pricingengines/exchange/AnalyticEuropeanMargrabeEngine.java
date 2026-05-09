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
package org.jquantlib.pricingengines.exchange;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.MargrabeOption;
import org.jquantlib.instruments.NullPayoff;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

/**
 * Analytic engine for European Margrabe options.
 *
 * <p>Implements formulae from W. Margrabe,
 * <em>The Value of an Option to Exchange One Asset for Another</em>,
 * Journal of Finance, 33 (March 1978), 177-186.
 *
 * <p>Phase 5i.5-MGR port of {@code QuantLib::AnalyticEuropeanMargrabeEngine}
 * (v1.42.1 ql/pricingengines/exotic/analyticeuropeanmargrabeengine.{hpp,cpp}).
 */
public class AnalyticEuropeanMargrabeEngine extends MargrabeOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process1_;
    private final GeneralizedBlackScholesProcess process2_;
    private final double rho_;

    public AnalyticEuropeanMargrabeEngine(
            final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2,
            final double correlation) {
        super();
        this.process1_ = process1;
        this.process2_ = process2;
        this.rho_      = correlation;
        this.process1_.addObserver(this);
        this.process2_.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(arguments_.exercise.type() == Exercise.Type.European,
                "not an European Option");

        QL.require(arguments_.payoff instanceof NullPayoff, "not a Null Payoff type");

        final int Q1 = arguments_.Q1;
        final int Q2 = arguments_.Q2;

        final double s1 = process1_.stateVariable().currentLink().value();
        final double s2 = process2_.stateVariable().currentLink().value();

        final Date lastDate = arguments_.exercise.lastDate();

        final double variance1 = process1_.blackVolatility().currentLink()
                                          .blackVariance(lastDate, s1);
        final double variance2 = process2_.blackVolatility().currentLink()
                                          .blackVariance(lastDate, s2);

        final double riskFreeDiscount = process1_.riskFreeRate().currentLink()
                                                  .discount(lastDate);

        final double dividendDiscount1 = process1_.dividendYield().currentLink()
                                                  .discount(lastDate);
        final double dividendDiscount2 = process2_.dividendYield().currentLink()
                                                  .discount(lastDate);

        final double forward1 = s1 * dividendDiscount1 / riskFreeDiscount;
        final double forward2 = s2 * dividendDiscount2 / riskFreeDiscount;

        final double stdDev1 = Math.sqrt(variance1);
        final double stdDev2 = Math.sqrt(variance2);
        final double variance = variance1 + variance2 - 2.0 * rho_ * stdDev1 * stdDev2;
        final double stdDev = Math.sqrt(variance);

        final double d1 = (Math.log((Q1 * forward1) / (Q2 * forward2))
                          + 0.5 * variance) / stdDev;
        final double d2 = d1 - stdDev;

        final CumulativeNormalDistribution cum = new CumulativeNormalDistribution();
        final NormalDistribution norm = new NormalDistribution();
        final double Nd1 = cum.op(d1);
        final double Nd2 = cum.op(d2);
        final double nd1 = norm.op(d1);
        final double nd2 = norm.op(d2);

        final DayCounter rfdc = process1_.riskFreeRate().currentLink().dayCounter();
        final double t = rfdc.yearFraction(
                process1_.riskFreeRate().currentLink().referenceDate(), lastDate);
        final double sqt = Math.sqrt(t);
        final double q1 = -Math.log(dividendDiscount1) / (sqt * sqt);
        final double q2 = -Math.log(dividendDiscount2) / (sqt * sqt);

        results_.value = riskFreeDiscount * (Q1 * forward1 * Nd1 - Q2 * forward2 * Nd2);

        // Greeks
        results_.delta1 = riskFreeDiscount * (Q1 * forward1 * Nd1) / s1;
        results_.delta2 = -riskFreeDiscount * (Q2 * forward2 * Nd2) / s2;
        results_.gamma1 = (riskFreeDiscount * (Q1 * forward1 * nd1) / s1) / (Q1 * s1 * stdDev);
        results_.gamma2 = (-riskFreeDiscount * (Q2 * forward2 * nd2) / s2) / (-Q2 * s2 * stdDev);
        final double vega = riskFreeDiscount * (Q1 * forward1 * nd1) * sqt;

        // Standard greeks (delta, gamma, theta) on the multi-asset base
        final org.jquantlib.instruments.Option.GreeksImpl baseGreeks = results_.greeks();
        baseGreeks.theta = -((stdDev * vega / sqt) / (2.0 * t)
                            - (q1 * Q1 * s1 * results_.delta1)
                            - (q2 * Q2 * s2 * results_.delta2));
        baseGreeks.rho = 0.0;
    }
}
