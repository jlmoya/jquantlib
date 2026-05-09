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
 * Petroleum barrel unit of measure (BBL).
 * <p>
 * Java port of QuantLib v1.42.1 {@code BarrelUnitOfMeasure} from
 * {@code petroleumunitsofmeasure.hpp}.
 */
public class BarrelUnitOfMeasure extends UnitOfMeasure {

    private static final Data SHARED = new Data("Barrels", "BBL", Type.Volume);

    public BarrelUnitOfMeasure() {
        super();
        this.data_ = SHARED;
    }
}
