/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2006 Allen Kuo
 Copyright (C) 2022 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/

package org.jquantlib.instruments;

import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Forward contract on a bond.
 *
 * Java port of QuantLib v1.42.1 {@code ql/instruments/bondforward.{hpp,cpp}}.
 *
 * <p>Notes
 * <ol>
 *   <li>{@code valueDate} refers to the settlement date of the bond forward
 *       contract. {@code maturityDate} is the delivery (or repurchase) date
 *       for the underlying bond — not the bond's maturity date.</li>
 *   <li>Pricing formulas (P refers to a price):
 *       <ul>
 *         <li>{@code P_CleanFwd(t) = P_DirtyFwd(t) - AI(t = deliveryDate)}
 *             where {@code AI} is the accrued interest on the underlying bond.</li>
 *         <li>{@code P_DirtyFwd(t) = (P_DirtySpot(t) - SpotIncome(t)) /
 *             discountCurve.discount(t = deliveryDate)}</li>
 *         <li>{@code SpotIncome(t) = sum_i CF_i * incomeDiscountCurve.discount(t_i)}
 *             where the {@code CF_i} are the underlying bond's cashflows that fall
 *             between {@code settlementDate()} and {@code maturityDate}.</li>
 *       </ul></li>
 * </ol>
 *
 * @author Jose Moya
 */
public class BondForward extends Forward {

    protected Bond bond;

    /**
     * Primary constructor — mirrors the C++ ctor at
     * {@code bondforward.cpp:27}.
     *
     * <p>If {@code strike} is given, {@link #NPV} is the NPV of the contract.
     * To obtain the strike that makes the contract worth zero today, use
     * {@link #forwardPrice()}; in that mode the {@code strike} argument is
     * ignored.
     */
    public BondForward(final Date valueDate,
                       final Date maturityDate,
                       final Position type,
                       final /* @Real */ double strike,
                       final /* @Natural */ int settlementDays,
                       final DayCounter dayCounter,
                       final Calendar calendar,
                       final BusinessDayConvention businessDayConvention,
                       final Bond bond,
                       final Handle<YieldTermStructure> discountCurve,
                       final Handle<YieldTermStructure> incomeDiscountCurve) {
        super(dayCounter, calendar, businessDayConvention, settlementDays,
              new ForwardTypePayoff(type, strike), valueDate, maturityDate,
              discountCurve);
        this.bond = bond;
        this.incomeDiscountCurve = incomeDiscountCurve;
        if (this.incomeDiscountCurve != null) {
            this.incomeDiscountCurve.addObserver(this);
        }
        if (this.bond != null) {
            this.bond.addObserver(this);
        }
    }

    /** Convenience overload: empty income discount curve. */
    public BondForward(final Date valueDate,
                       final Date maturityDate,
                       final Position type,
                       final /* @Real */ double strike,
                       final /* @Natural */ int settlementDays,
                       final DayCounter dayCounter,
                       final Calendar calendar,
                       final BusinessDayConvention businessDayConvention,
                       final Bond bond,
                       final Handle<YieldTermStructure> discountCurve) {
        this(valueDate, maturityDate, type, strike, settlementDays, dayCounter,
             calendar, businessDayConvention, bond, discountCurve,
             new Handle<YieldTermStructure>());
    }

    /** Convenience overload: empty discount and income curves. */
    public BondForward(final Date valueDate,
                       final Date maturityDate,
                       final Position type,
                       final /* @Real */ double strike,
                       final /* @Natural */ int settlementDays,
                       final DayCounter dayCounter,
                       final Calendar calendar,
                       final BusinessDayConvention businessDayConvention,
                       final Bond bond) {
        this(valueDate, maturityDate, type, strike, settlementDays, dayCounter,
             calendar, businessDayConvention, bond,
             new Handle<YieldTermStructure>(),
             new Handle<YieldTermStructure>());
    }

    /** (dirty) forward bond price. */
    public /* @Real */ double forwardPrice() {
        return forwardValue();
    }

    /** (dirty) forward bond price minus accrued on bond at delivery. */
    public /* @Real */ double cleanForwardPrice() {
        return forwardValue() - bond.accruedAmount(maturityDate);
    }

    /**
     * NPV of bond coupons discounted using {@code incomeDiscountCurve}.
     *
     * <p>Only coupons between {@code max(evaluationDate, settlementDate())} and
     * the bond forward contract's {@code maturityDate} are considered income.
     */
    @Override
    public /* @Real */ double spotIncome(final Handle<YieldTermStructure> incomeDiscountCurve) {
        double income = 0.0;
        final Date settlement = settlementDate();
        final Leg cf = bond.cashflows();
        // Assumes:
        //  1. cashflows are in ascending order
        //  2. income = all coupons paid between settlementDate() and contract delivery/maturity date
        for (int i = 0; i < cf.size(); ++i) {
            final CashFlow flow = (CashFlow) cf.get(i);
            if (!flow.hasOccurred(settlement)) {
                if (flow.hasOccurred(maturityDate)) {
                    income += flow.amount() * incomeDiscountCurve.currentLink().discount(flow.date());
                } else {
                    break;
                }
            }
        }
        return income;
    }

    /** NPV of underlying bond. */
    @Override
    public /* @Real */ double spotValue() {
        return bond.dirtyPrice();
    }

    @Override
    public void performCalculations() {
        underlyingSpotValue = spotValue();
        underlyingIncome = spotIncome(incomeDiscountCurve);
        super.performCalculations();
    }
}
