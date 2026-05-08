/*
 Copyright (C) 2019 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.finitedifferences.schemes;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

/**
 * Crank-Nicolson implicit-explicit time-stepping scheme.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/schemes/cranknicolsonscheme.{hpp,cpp}}.
 * <p>
 * In one dimension the Crank-Nicolson scheme is equivalent to the Douglas
 * scheme and in higher dimensions it is usually inferior to operator
 * splitting methods like Craig-Sneyd or Hundsdorfer-Verwer.
 * <p>
 * The scheme blends an explicit Euler step (weight {@code 1-theta}) with an
 * implicit Euler step (weight {@code theta}). With {@code theta = 0.5} the
 * result is the classical Crank-Nicolson (second-order in time).
 *
 * @author Phase 2l Track C.2 port
 */
public class CrankNicolsonScheme {

    /** Time step (NaN until {@link #setStep} is called). */
    protected double dt;

    protected final double theta;
    protected final ExplicitEulerScheme explicit_;
    protected final ImplicitEulerScheme implicit_;

    /** Constructor with empty boundary-condition set (mirrors C++ default arg). */
    public CrankNicolsonScheme(final double theta,
                               final FdmLinearOpComposite map) {
        this(theta, map, new FdmBoundaryConditionSet());
    }

    public CrankNicolsonScheme(final double theta,
                               final FdmLinearOpComposite map,
                               final FdmBoundaryConditionSet bcSet) {
        this.dt = Double.NaN;
        this.theta = theta;
        this.explicit_ = new ExplicitEulerScheme(map, bcSet);
        this.implicit_ = new ImplicitEulerScheme(map, bcSet);
    }

    /** Set the rollback step size. */
    public void setStep(final double dt) {
        this.dt = dt;
        explicit_.setStep(dt);
        implicit_.setStep(dt);
    }

    /**
     * Advance {@code a} from time {@code t} to {@code t-dt} in-place.
     * <p>
     * Mirrors C++ {@code CrankNicolsonScheme::step}. If {@code theta == 1}
     * only the implicit step runs; if {@code theta == 0} only the explicit
     * step runs. Mixed: explicit part applied first, then implicit.
     */
    public void step(final Array a, final double t) {
        QL.require(t - dt > -1e-8, "a step towards negative time given");

        if (theta != 1.0) {
            explicit_.step(a, t, 1.0 - theta);
        }

        if (theta != 0.0) {
            implicit_.step(a, t, theta);
        }
    }

    /** Number of iterative-solver iterations consumed by the implicit step. */
    public int numberOfIterations() {
        // ImplicitEulerScheme currently uses only the 1D (direct solve) path,
        // which has no iterative-solver iteration count. Return 0 consistently.
        return 0;
    }
}
