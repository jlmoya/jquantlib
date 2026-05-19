/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

/*
 Copyright (C) 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.models;

import java.util.List;

/**
 * Volatility-interpolation specifier interface.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/volatilityinterpolationspecifier.hpp}
 * (QuantLib v1.42.1).
 *
 * <p>Specifies how to derive the volatility structure for additional synthetic
 * rates which are interleaved between the original "big" rates.
 *
 * <p>Phase 3j B.8 (Track B).
 */
public interface VolatilityInterpolationSpecifier {

    void setScalingFactors(double[] scales);

    void setLastCapletVol(double vol);

    List< PiecewiseConstantVariance > interpolatedVariances();

    List< PiecewiseConstantVariance > originalVariances();

    int getPeriod();

    int getOffset();

    int getNoBigRates();

    int getNoSmallRates();
}
