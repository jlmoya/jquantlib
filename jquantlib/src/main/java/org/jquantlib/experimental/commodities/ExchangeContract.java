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

import org.jquantlib.time.Date;

/**
 * Exchange-traded futures contract metadata: code, expiry, and underlying delivery window.
 * <p>
 * Java port of QuantLib v1.42.1 {@code exchangecontract.hpp}.
 */
public class ExchangeContract {

    protected String code_;
    protected Date expirationDate_;
    protected Date underlyingStartDate_;
    protected Date underlyingEndDate_;

    public ExchangeContract() {
        this.code_ = "";
        this.expirationDate_ = new Date();
        this.underlyingStartDate_ = new Date();
        this.underlyingEndDate_ = new Date();
    }

    public ExchangeContract(final String code, final Date expirationDate, final Date underlyingStartDate,
            final Date underlyingEndDate) {
        this.code_ = code;
        this.expirationDate_ = expirationDate;
        this.underlyingStartDate_ = underlyingStartDate;
        this.underlyingEndDate_ = underlyingEndDate;
    }

    public String code() {
        return code_;
    }

    public Date expirationDate() {
        return expirationDate_;
    }

    public Date underlyingStartDate() {
        return underlyingStartDate_;
    }

    public Date underlyingEndDate() {
        return underlyingEndDate_;
    }
}
