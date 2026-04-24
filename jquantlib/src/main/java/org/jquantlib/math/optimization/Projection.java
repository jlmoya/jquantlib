/*
 Copyright (C) 2007 François du Vignaud
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2013 Peter Caspers
 Copyright (C) 2026 Jose Moya (Java port)

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.math.optimization;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Parameter projection — splits a full parameter vector into "fixed" and
 * "free" slots and provides the plumbing to round-trip between the full and
 * projected views.
 *
 * <p>Faithful port of QuantLib C++ v1.42.1
 * {@code ql/math/optimization/projection.hpp|cpp}. In C++, {@code ProjectedCostFunction}
 * multiply-inherits from both {@code CostFunction} and {@code Projection}. Java lacks
 * multiple inheritance, so {@link ProjectedCostFunction} composes a {@code Projection}
 * instance and delegates the public projection API.
 */
public class Projection {

    protected int numberOfFreeParameters_ = 0;
    protected final Array fixedParameters_;
    protected final Array actualParameters_;
    protected final boolean[] fixParameters_;

    //-- Projection(const Array& parameterValues,
    //--            std::vector<bool> fixParameters = std::vector<bool>());
    //-- in ql/math/optimization/projection.cpp:27
    /**
     * @param parameterValues initial values for all parameters.
     * @param fixParameters   same length as {@code parameterValues}; {@code true}
     *                        marks that slot as fixed, {@code false} as free. May be
     *                        {@code null} or empty to default all slots to "free".
     */
    public Projection(final Array parameterValues, final boolean[] fixParameters) {
        this.fixedParameters_ = parameterValues.clone();
        this.actualParameters_ = parameterValues.clone();
        // C++ default: empty fixParameters vector expands to all-false of size parameterValues.size()
        final boolean[] fixArg;
        if (fixParameters == null || fixParameters.length == 0) {
            fixArg = new boolean[parameterValues.size()];  // Java bool default is false → all free
        } else {
            fixArg = fixParameters.clone();
        }
        this.fixParameters_ = fixArg;

        QL.require(fixedParameters_.size() == fixParameters_.length,
                "fixedParameters_.size()!=parametersFreedoms_.size()");
        for (int i = 0; i < fixParameters_.length; i++) {
            if (!fixParameters_[i]) {
                numberOfFreeParameters_++;
            }
        }
        QL.require(numberOfFreeParameters_ > 0, "numberOfFreeParameters==0");
    }

    /** Convenience constructor mirroring the C++ default-vector form. */
    public Projection(final Array parameterValues) {
        this(parameterValues, null);
    }

    //-- void mapFreeParameters(const Array& parameterValues) const;
    //-- in ql/math/optimization/projection.cpp:43
    //
    // Despite being `const` in C++, this method mutates the `mutable` actualParameters_
    // member. Java mirrors that by making it a regular instance method that updates
    // the (non-final-referenced) array contents.
    protected void mapFreeParameters(final Array parameterValues) {
        QL.require(parameterValues.size() == numberOfFreeParameters_,
                "parameterValues.size()!=numberOfFreeParameters");
        int i = 0;
        for (int j = 0; j < actualParameters_.size(); j++) {
            if (!fixParameters_[j]) {
                actualParameters_.set(j, parameterValues.get(i++));
            }
        }
    }

    //-- virtual Array project(const Array& parameters) const;
    //-- in ql/math/optimization/projection.cpp:54
    public Array project(final Array parameters) {
        QL.require(parameters.size() == fixParameters_.length,
                "parameters.size()!=parametersFreedoms_.size()");
        final Array projectedParameters = new Array(numberOfFreeParameters_);
        int i = 0;
        for (int j = 0; j < fixParameters_.length; j++) {
            if (!fixParameters_[j]) {
                projectedParameters.set(i++, parameters.get(j));
            }
        }
        return projectedParameters;
    }

    //-- virtual Array include(const Array& projectedParameters) const;
    //-- in ql/math/optimization/projection.cpp:67
    public Array include(final Array projectedParameters) {
        QL.require(projectedParameters.size() == numberOfFreeParameters_,
                "projectedParameters.size()!=numberOfFreeParameters");
        final Array y = fixedParameters_.clone();
        int i = 0;
        for (int j = 0; j < y.size(); j++) {
            if (!fixParameters_[j]) {
                y.set(j, projectedParameters.get(i++));
            }
        }
        return y;
    }
}
