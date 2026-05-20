/*
 Copyright (C) 2009 Ueli Hofstetter

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
package org.jquantlib.currencies;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Closeness;

/**
 * Cash amount in a given currency.
 */
//FIXME: http://bugs.jquantlib.org/view.php?id=474
public class Money implements Cloneable {

    //FIXME: These static methods must be "moved" to class Settings
    public static ConversionType conversionType;
    public static Currency baseCurrency;

    // class fields
    private/* @Decimal */ double value_;
    private Currency currency_;

    // constructors
    public Money() {
        this.value_ = (0.0);
    }

    public Money(final Currency currency, /* @Decimal */final double value) {
        this.value_ = (value);
        this.currency_ = (currency);
    }

    public Money(/* @Decimal */final double value, final Currency currency) {
        this.value_ = (value);
        this.currency_ = (currency.clone());
    }

    public static Money multiple(final Currency c, /* Decimal */final double value) {
        return new Money(value, c);
    }

    public static Money multiple( /* Decimal */final double value, final Currency c) {
        return new Money(value, c);
    }

    @Override
    public Money clone() {
        final Money money = new Money();
        money.currency_ = currency_.clone();
        money.value_ = value_;
        return money;
    }

    // accessors
    public Currency currency() {
        return currency_;
    }

    // class based operators

    public/* @Decimal */double value() {
        return value_;
    }

    public Money rounded() {
        return new Money(currency_.rounding().operator(value_), currency_);
    }

    // +() //FIXME: this looks like a mistake in c++
    public Money positiveValue() {
        return new Money(currency_, value_);
    }

    // -()
    public Money negativeValue() {
        return new Money(-value_, currency_);
    }

    // *=
    public Money mulAssign(/* Decimal */final double x) {
        value_ *= x;
        return this;
    }

    // /=
    public Money divAssign(/* Decimal */final double x) {
        value_ /= x;
        return this;
    }

    // +
    public Money add(final Money money) {
        final Money tmp = clone();
        tmp.addAssign(money);
        return tmp;
    }

    // -
    public Money sub(final Money money) {
        final Money tmp = clone();
        tmp.subAssign(money);
        return tmp;
    }

    // *
    public Money mul(/* @Decimal */final double x) {
        final Money tmp = clone();
        tmp.mulAssign(x);
        return tmp;
    }

    public Money div(/* @Decimal */final double x) {
        final Money tmp = clone();
        tmp.value_ /= x;
        return tmp;
    }

    public boolean notEquals(final Money money) {
        // eating dogfood
        return !(this.equals(money));
    }

    /**
     * Returns {@code true} iff this > money (using the active conversion
     * policy when the currencies differ).
     * <p>
     * Aligns with C++ v1.42.1 {@code operator>(Money,Money)} which is
     * {@code m1.value() > m2.value()} after converting m2 to m1's currency.
     * Equivalent to {@code money.less(this)}.
     */
    public boolean greater(final Money money) {
        return money.less(this);
    }

    /**
     * Returns {@code true} iff this >= money.  Equivalent to
     * {@code money.lessEquals(this)}; mirrors C++ v1.42.1
     * {@code operator>=(Money,Money)}.
     */
    public boolean greaterEqual(final Money money) {
        return money.lessEquals(this);
    }

    // FIXME: suspicious....
    public Money operatorMultiply(/* Decimal */final double value, final Currency c) {
        return new Money(value, c);
    }

    public void convertTo(final Currency target) {
        if ( currency().ne(target) ) {
            final ExchangeRate rate = ExchangeRateManager.getInstance().lookup(currency(), target);
            // FIXME ... evt. Money should be modified in ExchangeRate directly
            final Money money = rate.exchange(this).rounded();
            this.currency_ = money.currency_;
            this.value_ = money.value_;
        }
    }

    //
    //    Assignment operations
    //
    //    opr   method     this    right    result
    //    ----- ---------- ------- -------- ------
    //    +=    addAssign  Money   Money   this
    //    -=    addAssign  Money   Money   this

    //
    //    Operations
    //
    //    opr   method     this    right    result
    //    ----- ---------- ------- -------- ------
    //    /     addAssign  Money   Money   this
    //    ==    equals     Money   Money   boolean

    public void convertToBase() {
        QL.require((!baseCurrency.empty()), "no base currency set");  // TODO: message
        convertTo(baseCurrency);
    }

    /**
     * += Operator
     *
     * @param money The money to be added.
     * @return This money instance increased by the specified amount.
     */
    public Money addAssign(final Money money) {
        if ( this.currency_.eq(money.currency_) ) {
            this.value_ += money.value_;
        } else if ( conversionType == Money.ConversionType.BaseCurrencyConversion ) {
            this.convertToBase();
            final Money tmp = money.clone();
            tmp.convertToBase();
            // recursive invocation
            this.addAssign(tmp);
        } else if ( conversionType == Money.ConversionType.AutomatedConversion ) {
            final Money tmp = money.clone();
            tmp.convertTo(currency_);
            // recursive invocation
            this.addAssign(tmp);
        } else
            throw new LibraryException("currency mismatch and no conversion specified"); // TODO: message
        return this;
    }

    /**
     * -= Operator
     *
     * @param money The money to be subtracted.
     * @return This money instance decreased by the specified amount.
     */
    public Money subAssign(final Money money) {
        if ( currency_.eq(money.currency_) ) {
            value_ -= money.value_;
        } else if ( conversionType == Money.ConversionType.BaseCurrencyConversion ) {
            this.convertToBase();
            final Money tmp = money.clone();
            tmp.convertToBase();
            // recursive ...
            this.subAssign(tmp);
        } else if ( conversionType == Money.ConversionType.AutomatedConversion ) {
            final Money tmp = money.clone();
            tmp.convertTo(currency_);
            this.subAssign(tmp);
        } else
            throw new LibraryException("currency mismatch and no conversion specified"); // TODO: message
        return this;
    }

    /**
     * Returns the the value of this instance divided by another instance.
     *
     * @param money The amount this instance should be divided to.
     * @return The amount of this divided by money.
     * @note This instance remains unchanged!
     */
    public double div(final Money money) {
        if ( currency().eq(money.currency()) )
            return value_ / money.value();
        else if ( conversionType == Money.ConversionType.BaseCurrencyConversion ) {
            final Money tmp1 = this.clone();
            tmp1.convertToBase();
            final Money tmp2 = money.clone();
            tmp2.convertToBase();
            // recurse on the converted lhs (aligns with C++ v1.42.1 money.hpp
            // operator/(Money,Money); previously recursed on `this`, which
            // could leave the lhs in its original currency and yield wrong
            // ratios).
            return tmp1.div(tmp2);
        } else if ( conversionType == Money.ConversionType.AutomatedConversion ) {
            // Convert rhs to lhs's currency (matches C++ AutomatedConversion).
            // Previously the call was tmp.convertTo(money.currency()), a
            // no-op that left the operands in different currencies.
            final Money tmp = money.clone();
            tmp.convertTo(this.currency());
            return this.div(tmp);
        } else
            throw new LibraryException("currency mismatch and no conversion specified"); // TODO: message
    }

    /**
     * Operator ==
     *
     * @param money The instance this instance should be compared to.
     * @return Whether this instance is equal to another instance
     */
    public boolean equals(final Money money) {
        if ( currency().eq(money.currency()) )
            return value() == money.value();
        else if ( conversionType == Money.ConversionType.BaseCurrencyConversion ) {
            final Money tmp1 = this.clone();
            tmp1.convertToBase();
            final Money tmp2 = money.clone();
            tmp2.convertToBase();
            // recursive...
            return tmp1.equals(tmp2);
        } else if ( conversionType == Money.ConversionType.AutomatedConversion ) {
            final Money tmp = money.clone();
            tmp.convertTo(this.currency());
            return this.equals(tmp);
        } else
            throw new LibraryException("currency mismatch and no conversion specified"); // TODO: message
    }

    public boolean less(final Money money) {
        if ( this.currency().eq(money.currency()) )
            return value() < money.value();
        else if ( conversionType == Money.ConversionType.BaseCurrencyConversion ) {
            // Clone both sides to avoid mutating the caller-supplied operand
            // (previous code re-used `money` directly, mutating it via
            // convertToBase).
            final Money tmp1 = this.clone();
            tmp1.convertToBase();
            final Money tmp2 = money.clone();
            tmp2.convertToBase();
            return tmp1.less(tmp2);
        } else if ( conversionType == Money.ConversionType.AutomatedConversion ) {
            final Money tmp = money.clone();
            tmp.convertTo(currency());
            return this.less(tmp);
        } else
            throw new LibraryException("currency mismatch and no conversion specified"); // TODO: message
    }

    public boolean lessEquals(final Money money) {
        if ( currency().eq(money.currency()) )
            return value() <= money.value();
        else if ( conversionType == Money.ConversionType.BaseCurrencyConversion ) {
            // Clone the rhs and recurse via lessEquals (not less) to honor the
            // C++ v1.42.1 semantics of operator<=.
            final Money tmp1 = this.clone();
            tmp1.convertToBase();
            final Money tmp2 = money.clone();
            tmp2.convertToBase();
            return tmp1.lessEquals(tmp2);
        } else if ( conversionType == Money.ConversionType.AutomatedConversion ) {
            final Money tmp = money.clone();
            tmp.convertTo(this.currency());
            return this.lessEquals(tmp);
        } else
            throw new LibraryException("currency mismatch and no conversion specified"); // TODO: message
    }

    public boolean close(final Money money, /* Size */final int n) {
        if ( currency().eq(money.currency()) )
            return Closeness.isClose(value(), money.value(), n);
        else if ( conversionType == Money.ConversionType.BaseCurrencyConversion ) {
            final Money tmp1 = this.clone();
            tmp1.convertToBase();
            final Money tmp2 = money.clone();
            tmp2.convertToBase();
            return tmp1.close(tmp2, n);
        } else if ( conversionType == Money.ConversionType.AutomatedConversion ) {
            final Money tmp = money.clone();
            tmp.convertTo(this.currency());
            return this.close(tmp, n);
        } else
            throw new LibraryException("currency mismatch and no conversion specified"); // TODO: message
    }

    public boolean close_enough(final Money money, /* Size */final int n) {
        if ( currency().eq(money.currency()) )
            return Closeness.isCloseEnough(value(), money.value(), n);
        else if ( conversionType == Money.ConversionType.BaseCurrencyConversion ) {
            // Clone the rhs (previous code mutated the caller's Money).
            final Money tmp1 = this.clone();
            tmp1.convertToBase();
            final Money tmp2 = money.clone();
            tmp2.convertToBase();
            return tmp1.close_enough(tmp2, n);
        } else if ( conversionType == Money.ConversionType.AutomatedConversion ) {
            final Money tmp = money.clone();
            tmp.convertTo(currency());
            return this.close_enough(tmp, n);
        } else
            throw new LibraryException("currency mismatch and no conversion specified"); // TODO: message
    }

    //
    // Overrides Object
    //

    @Override
    public String toString() {
        final Currency currency = currency();
        return String.format(currency.format(), rounded().value_, currency.code(), currency.symbol());
    }

    //
    // inner public enums
    //

    // enums
    public enum ConversionType {

        /**
         * Do not perform conversions
         */
        NoConversion,

        /**
         * Convert both operands to the base currency before converting
         */
        BaseCurrencyConversion,

        /**
         * Return the result in the currency of the first operand
         */
        AutomatedConversion
    }

}
