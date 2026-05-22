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
/*
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2021 Lew Wei Hao

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.pricingengines.bond;

import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.instruments.Bond;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Risky pricing engine for bonds — Java port of QuantLib v1.42.1 {@code RiskyBondEngine}
 * ({@code ql/pricingengines/bond/riskybondengine.{hpp,cpp}}).
 *
 * <p>The value of each cashflow is contingent on issuer survival; default
 * is assumed to occur in the middle of each coupon period and the recovery payment is the time-{@code T_mid} notional
 * fraction.
 *
 * <p>For each coupon period {@code [T_{i-1}, T_i]}:
 * <ul>
 *   <li>Survival leg: {@code CF_i * P(t,T_i) * S(T_i)}, where
 *       {@code P} is the discount factor and {@code S} the survival
 *       probability;</li>
 *   <li>Recovery leg: {@code N(T_mid) * Rec * (S(T_{i-1}) - S(T_i))
 *       * P(t,T_mid)}.</li>
 * </ul>
 *
 * <p>The {@code results.settlementValue} is the NPV of the cash flows
 * with payment date strictly later than the settlement date, normalised
 * by {@code P(t, settlement)}. {@code results.valuationDate} is the
 * yield curve's reference date.
 *
 * <p>Phase 5e.5b-CFC-d-205.
 */
public class RiskyBondEngine extends Bond.EngineImpl {

    private final Handle< DefaultProbabilityTermStructure > defaultTS;
    private final double recoveryRate;
    private final Handle< YieldTermStructure > yieldTS;

    public RiskyBondEngine(final Handle< DefaultProbabilityTermStructure > defaultTS, final double recoveryRate,
            final Handle< YieldTermStructure > yieldTS) {
        this.defaultTS = defaultTS;
        this.recoveryRate = recoveryRate;
        this.yieldTS = yieldTS;
        this.defaultTS.addObserver(this);
        this.yieldTS.addObserver(this);
    }

    public Handle< DefaultProbabilityTermStructure > defaultTS() {
        return defaultTS;
    }

    public double recoveryRate() {
        return recoveryRate;
    }

    public Handle< YieldTermStructure > yieldTS() {
        return yieldTS;
    }

    @Override
    public void calculate() {
        final Bond.ArgumentsImpl a = (Bond.ArgumentsImpl) arguments_;
        final Bond.ResultsImpl r = (Bond.ResultsImpl) results_;

        final Date npvDate = yieldTS.currentLink().referenceDate();
        final Date settlementDate = a.settlementDate;
        final Leg cashflows = a.cashflows;
        // C++ uses CashFlows::startDate(leg) which, for non-Coupon flows
        // (e.g. SimpleCashFlow redemption), falls back to {@code i->date()};
        // the Java {@code CashFlows.startDate} casts blindly to {@link Coupon}
        // and throws ClassCastException on a bond's redemption flow, so we
        // inline the C++-faithful traversal here. Mirrors
        // {@code ql/cashflows/cashflows.cpp:38-50}.
        Date startDate = Date.maxDate();
        for ( int i = 0; i < cashflows.size(); ++i ) {
            final CashFlow ci = cashflows.get(i);
            final Date di = (ci instanceof Coupon) ? ((Coupon) ci).accrualStartDate() : ci.date();
            startDate = Date.min(startDate, di);
        }

        // d1 tracks the previous accrual boundary used for recovery
        // (midpoint of [d1, d2]). Mirrors C++
        // ql/pricingengines/bond/riskybondengine.cpp:43.
        Date d1 = npvDate.gt(startDate) ? npvDate.clone() : startDate.clone();

        double NPV = 0.0;
        double settlementValue = 0.0;

        for ( int i = 0; i < cashflows.size(); ++i ) {
            final CashFlow cf = cashflows.get(i);
            final Date d2 = cf.date();
            if ( d2.gt(npvDate) ) {
                final double weightedCouponAmount = cf.amount() * defaultTS.currentLink().survivalProbability(d2);
                final double discToD2 = yieldTS.currentLink().discount(d2);
                NPV += weightedCouponAmount * discToD2;
                if ( d2.gt(settlementDate) ) {
                    settlementValue += weightedCouponAmount * discToD2;
                }

                if (cf instanceof Coupon coupon) {
                    // C++: Date defaultDate = d1 + (d2 - d1) / 2.
                    final long span = d2.sub(d1);
                    final Date defaultDate = d1.add((int) (span / 2));

                    final double survivalDiff =
                            defaultTS.currentLink().survivalProbability(d1) - defaultTS.currentLink()
                                    .survivalProbability(d2);
                    final double weightedRecovery = coupon.nominal() * recoveryRate * survivalDiff;
                    final double discToMid = yieldTS.currentLink().discount(defaultDate);
                    NPV += weightedRecovery * discToMid;
                    if ( d2.gt(settlementDate) ) {
                        settlementValue += weightedRecovery * discToMid;
                    }
                    d1 = d2.clone();
                }
            }
        }

        r.value = NPV;
        r.settlementValue = settlementValue / yieldTS.currentLink().discount(settlementDate);
        // Note: C++ also sets results_.valuationDate = npvDate, but Java's
        // Bond.ResultsImpl does not currently expose that field; cleanPrice()
        // derives its value from settlementValue / notional(settlementDate())
        // and does not consume valuationDate.
    }
}
