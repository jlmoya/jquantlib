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

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.util.Pair;

public class ExchangeRate {

    /** The pair of chained ExchangeRates. */
    public Pair< ExchangeRate, ExchangeRate > rateChain_;

    /** The source currency of this ExchangeRate. */
    private Currency source_;
    /** The target currency of this ExchangeRate. */
    private Currency target_;
    /** The rate of this exchange rate. */
    private     /*@Decimal*/ double rate_;
    /** The type of this ExchangeRate. */
    private Type type_;
    /**
     * Creates a new ExchangeRate instance.
     */
    public ExchangeRate() {
        this.rate_ = Constants.NULL_REAL;
    }

    /**
     * Creates clone of an existing ExchangeRate instance.
     *
     * @param toCopy the ExchangeRate to be cloned.
     */
    public ExchangeRate(final ExchangeRate toCopy) {
        source_ = toCopy.source_;
        target_ = toCopy.target_;
        rate_ = toCopy.rate_;
        type_ = toCopy.type_;
        // C++ default copy constructor copies all members including the
        // shared_ptr rateChain_. Java must mirror this — recursive chain
        // building (e.g. smartLookup -> chain(head, tail) where tail is
        // itself Derived) requires the inner chain to survive the copy
        // that ExchangeRate.chain() takes when wrapping its inputs.
        rateChain_ = toCopy.rateChain_;
    }

    /**
     * Creates a new ExchangeRate instance
     *
     * @param source The source currency of this ExchangeRate Currency
     * @param target The target currency of this ExchangeRate Currency
     * @param rate   The rate of this ExchangeRate
     */
    public ExchangeRate(final Currency source, final Currency target,
            /*@Decimal*/final double rate) {
        this.source_ = (source);
        this.target_ = (target);
        this.rate_ = (rate);
        this.type_ = (Type.Direct);
    }

    /**
     * Returns a new ExchangeRate with a initialized ratechain. The ratechain will be initialized by copies of r1 and
     * r1.
     *
     * @param r1 The first ExchangeRate to be used in the ratechain ExchangeRate
     * @param r2 The second ExchangeRate to be used inthe ratechain ExchangeRate
     * @return A new ExchangeRate
     */
    public static ExchangeRate chain(final ExchangeRate r1, final ExchangeRate r2) {
        final ExchangeRate result = new ExchangeRate();
        result.type_ = ExchangeRate.Type.Derived;
        result.rateChain_ = new Pair< ExchangeRate, ExchangeRate >(new ExchangeRate(r1), new ExchangeRate(r2));
        if ( r1.source_.eq(r2.source_) ) {
            result.source_ = r1.target_;
            result.target_ = r2.target_;
            result.rate_ = r2.rate_ / r1.rate_;
        } else if ( r1.source_.eq(r2.target_) ) {
            result.source_ = r1.target_;
            result.target_ = r2.source_;
            result.rate_ = 1.0 / (r1.rate_ * r2.rate_);
        } else if ( r1.target_.eq(r2.source_) ) {
            result.source_ = r1.source_;
            result.target_ = r2.target_;
            result.rate_ = r1.rate_ * r2.rate_;
        } else if ( r1.target_.eq(r2.target_) ) {
            result.source_ = r1.source_;
            result.target_ = r2.source_;
            result.rate_ = r1.rate_ / r2.rate_;
        } else {
            throw new LibraryException("exchange rates not chainable");
        }
        return result;
    }

    //accessors
    public Currency source() {
        return source_;
    }

    public Currency target() {
        return target_;
    }

    public Type type() {
        return type_;
    }

    public /*@Decimal*/ double rate() {
        return rate_;
    }

    /**
     * Apply this ExchangeRate an amount of cash. A new money instance will be returned. The input remains unchanged.
     *
     * @param amount The money instance the exchange rate should be applied to. Money
     * @return A new money instance where this ExchangeRate has been applied to. Money
     */
    public Money exchange(final Money amount) {
        switch ( type_ ) {
        case Direct:
            if ( amount.currency().eq(source_) ) {
                return new Money(amount.value() * rate_, target_);
            } else if ( amount.currency().eq(target_) ) {
                return new Money(amount.value() / rate_, source_);
            } else {
                throw new LibraryException("exchange rate not applicable");
            }
        case Derived:
            // C++ uses operator== on Currency (name-based equality). Java
            // must use .eq() — `==` is reference identity and the chain
            // wraps copies (new ExchangeRate(r1)), so identity never holds.
            if ( amount.currency().eq(rateChain_.first().source()) || amount.currency()
                    .eq(rateChain_.first().target()) ) {
                return rateChain_.second().exchange(rateChain_.first().exchange(amount));
            } else if ( amount.currency().eq(rateChain_.second().source()) || amount.currency()
                    .eq(rateChain_.second().target()) ) {
                return rateChain_.first().exchange(rateChain_.second().exchange(amount));
            } else {
                throw new LibraryException("exchange rate not applicable");
            }
        default:
            throw new LibraryException("unknown exchange-rate type");
        }
    }

    public enum Type {
        Direct,
        /** given directly by the user */
        Derived  /** derived from exchange rates between
         other currencies */
    }

}
