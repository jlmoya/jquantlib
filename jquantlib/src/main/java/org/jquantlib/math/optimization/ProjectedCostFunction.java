/*
 Copyright (C) 2007 François du Vignaud
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2013 Peter Caspers
 Copyright (C) 2009 Ueli Hofstetter
 Copyright (C) 2026 Jose Moya (Java port update)

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.optimization;

import org.jquantlib.math.matrixutilities.Array;

/**
 * Parameterized cost function — creates a proxy {@link CostFunction} that depends
 * on any arbitrary subset of parameters (the rest being fixed).
 *
 * <p>Faithful port of QuantLib C++ v1.42.1
 * {@code ql/math/optimization/projectedcostfunction.hpp|cpp}. C++ uses multiple
 * inheritance ({@code CostFunction} + {@code Projection}); Java composes a
 * {@link Projection} instance and delegates the projection-API methods.
 */
public class ProjectedCostFunction extends CostFunction {

    private final CostFunction costFunction_;
    private final Projection projection_;

    //-- ProjectedCostFunction(const CostFunction&, const Array&, const std::vector<bool>&);
    //-- in ql/math/optimization/projectedcostfunction.cpp:26
    public ProjectedCostFunction(final CostFunction costFunction,
                                 final Array parameterValues,
                                 final boolean[] fixParameters) {
        this.costFunction_ = costFunction;
        this.projection_ = new Projection(parameterValues, fixParameters);
    }

    //-- ProjectedCostFunction(const CostFunction&, const Projection&);
    //-- in ql/math/optimization/projectedcostfunction.cpp:32
    public ProjectedCostFunction(final CostFunction costFunction, final Projection projection) {
        this.costFunction_ = costFunction;
        this.projection_ = projection;
    }

    //-- Real value(const Array& freeParameters) const override;
    //-- in ql/math/optimization/projectedcostfunction.cpp:37
    @Override
    public double value(final Array freeParameters) {
        projection_.mapFreeParameters(freeParameters);
        return costFunction_.value(projection_.actualParameters_);
    }

    //-- Array values(const Array& freeParameters) const override;
    //-- in ql/math/optimization/projectedcostfunction.cpp:42
    @Override
    public Array values(final Array freeParameters) {
        projection_.mapFreeParameters(freeParameters);
        return costFunction_.values(projection_.actualParameters_);
    }

    // ---- Projection API delegation (callers treat ProjectedCostFunction as a Projection
    // ---- in C++; Java callers use these delegating methods instead.)

    /** @see Projection#project(Array) */
    public Array project(final Array parameters) {
        return projection_.project(parameters);
    }

    /** @see Projection#include(Array) */
    public Array include(final Array projectedParameters) {
        return projection_.include(projectedParameters);
    }
}
