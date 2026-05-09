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

/**
 * Parametric form for alpha-based caplet calibration.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/alphaform.hpp}
 * (QuantLib v1.42.1).
 *
 * <p>C++ {@code Real operator()(Integer i)} maps to Java {@link #apply(int)}.
 *
 * <p>Phase 3j B.1 (Track B).
 */
public interface AlphaForm {

    /** C++ {@code operator()(Integer i)}. Returns the alpha-form value at index i. */
    double apply(int i);

    /** Sets the alpha parameter. */
    void setAlpha(double alpha);
}
