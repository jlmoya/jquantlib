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
 * US gallon unit of measure (GAL).
 * <p>
 * Java port of QuantLib v1.42.1 {@code GallonUnitOfMeasure} from {@code petroleumunitsofmeasure.hpp}.
 */
public class GallonUnitOfMeasure extends UnitOfMeasure {

    private static final Data SHARED = new Data("US Gallons", "GAL", Type.Volume, new BarrelUnitOfMeasure());

    public GallonUnitOfMeasure() {
        super();
        this.data_ = SHARED;
    }
}
