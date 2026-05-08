/*
 Copyright (C) 2009 Andreas Gaida
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2009 Klaus Spanderen

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
 * Explicit-Euler time-stepping scheme.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/schemes/expliciteulerscheme.{hpp,cpp}}.
 * <p>
 * The scheme advances by one full step: {@code a += theta * dt * L(a)},
 * where {@code L} is the spatial operator. With {@code theta = 1} this is
 * the standard forward-Euler method; smaller {@code theta} provides an
 * explicit blend used by {@link CrankNicolsonScheme}.
 *
 * @author Phase 2l Track C.1 port
 */
public class ExplicitEulerScheme {

    /** Time step (NaN until {@link #setStep} is called). */
    protected double dt;

    protected final FdmLinearOpComposite map;
    protected final BoundaryConditionSchemeHelper bcSet;

    /** Constructor with empty boundary-condition set (mirrors C++ default arg). */
    public ExplicitEulerScheme(final FdmLinearOpComposite map) {
        this(map, new FdmBoundaryConditionSet());
    }

    public ExplicitEulerScheme(final FdmLinearOpComposite map,
                               final FdmBoundaryConditionSet bcSet) {
        this.dt = Double.NaN;
        this.map = map;
        this.bcSet = new BoundaryConditionSchemeHelper(bcSet);
    }

    /** Set the rollback step size. */
    public void setStep(final double dt) {
        this.dt = dt;
    }

    /** Default step: theta = 1.0 (full explicit Euler). */
    public void step(final Array a, final double t) {
        step(a, t, 1.0);
    }

    /**
     * Advance {@code a} from time {@code t} to {@code t-dt} in-place with
     * explicit weight {@code theta}.
     * <p>
     * Mirrors C++ {@code ExplicitEulerScheme::step(a, t, theta)}:
     * {@code a += theta * dt * map.apply(a)}.
     * <p>
     * The {@code protected} version with {@code theta} is called by
     * {@link CrankNicolsonScheme} for the explicit sub-step.
     */
    protected void step(final Array a, final double t, final double theta) {
        QL.require(t - dt > -1e-8, "a step towards negative time given");

        final double tPrev = Math.max(0.0, t - dt);
        map.setTime(tPrev, t);
        bcSet.setTime(tPrev);

        bcSet.applyBeforeApplying(map);
        // a += theta * dt * map.apply(a)
        final Array increment = map.apply(a).mulAssign(theta * dt);
        a.addAssign(increment);
        bcSet.applyAfterApplying(a);
    }
}
