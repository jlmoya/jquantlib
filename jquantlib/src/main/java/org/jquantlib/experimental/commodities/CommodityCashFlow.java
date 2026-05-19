/*
 Copyright (C) 2026 JQuantLib

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 J. Erik Radmall
*/

package org.jquantlib.experimental.commodities;

import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Money;
import org.jquantlib.time.Date;

/**
 * Cash-flow used by commodity pricing engines, carrying both discounted and undiscounted amounts and the discount
 * factors used to obtain them.
 * <p>
 * Java port of QuantLib v1.42.1 {@code commoditycashflow.{hpp,cpp}}.
 */
public class CommodityCashFlow extends CashFlow {

    private final Date date_;
    private final Money discountedAmount_;
    private final Money undiscountedAmount_;
    private final Money discountedPaymentAmount_;
    private final Money undiscountedPaymentAmount_;
    private final double discountFactor_;
    private final double paymentDiscountFactor_;
    private final boolean finalized_;

    public CommodityCashFlow(final Date date, final Money discountedAmount, final Money undiscountedAmount,
            final Money discountedPaymentAmount, final Money undiscountedPaymentAmount, final double discountFactor,
            final double paymentDiscountFactor, final boolean finalized) {
        this.date_ = date;
        this.discountedAmount_ = discountedAmount;
        this.undiscountedAmount_ = undiscountedAmount;
        this.discountedPaymentAmount_ = discountedPaymentAmount;
        this.undiscountedPaymentAmount_ = undiscountedPaymentAmount;
        this.discountFactor_ = discountFactor;
        this.paymentDiscountFactor_ = paymentDiscountFactor;
        this.finalized_ = finalized;
    }

    @Override
    public Date date() {
        return date_;
    }

    @Override
    public double amount() {
        return discountedAmount_.value();
    }

    public Currency currency() {
        return discountedAmount_.currency();
    }

    public Money discountedAmount() {
        return discountedAmount_;
    }

    public Money undiscountedAmount() {
        return undiscountedAmount_;
    }

    public Money discountedPaymentAmount() {
        return discountedPaymentAmount_;
    }

    public Money undiscountedPaymentAmount() {
        return undiscountedPaymentAmount_;
    }

    public double discountFactor() {
        return discountFactor_;
    }

    public double paymentDiscountFactor() {
        return paymentDiscountFactor_;
    }

    public boolean finalized() {
        return finalized_;
    }
}
