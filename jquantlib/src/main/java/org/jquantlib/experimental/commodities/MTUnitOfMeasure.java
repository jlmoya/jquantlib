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
 * Metric tonne unit of measure (MT).
 * <p>
 * Java port of QuantLib v1.42.1 {@code MTUnitOfMeasure} from {@code petroleumunitsofmeasure.hpp}.
 */
public class MTUnitOfMeasure extends UnitOfMeasure {

    private static final Data SHARED = new Data("Metric Tonnes", "MT", Type.Mass);

    public MTUnitOfMeasure() {
        super();
        this.data_ = SHARED;
    }
}
