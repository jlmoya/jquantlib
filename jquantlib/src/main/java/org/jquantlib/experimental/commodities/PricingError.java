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

/**
 * Pricing-error record attached to {@link Commodity} instances.
 * <p>
 * Java port of QuantLib v1.42.1 {@code PricingError} from {@code commodity.hpp}.
 */
public class PricingError {

    public final Level errorLevel;
    public final String tradeId;
    public final String error;
    public final String detail;
    public PricingError(final Level errorLevel, final String error, final String detail) {
        this.errorLevel = errorLevel;
        this.tradeId = "";
        this.error = error;
        this.detail = detail;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        switch ( errorLevel ) {
        case Info:
            sb.append("info: ");
            break;
        case Warning:
            sb.append("warning: ");
            break;
        case Error:
            sb.append("*** error: ");
            break;
        case Fatal:
            sb.append("*** fatal: ");
            break;
        }
        sb.append(error);
        if ( !detail.isEmpty() ) {
            sb.append(": ").append(detail);
        }
        return sb.toString();
    }

    public enum Level {
        Info, Warning, Error, Fatal
    }
}
