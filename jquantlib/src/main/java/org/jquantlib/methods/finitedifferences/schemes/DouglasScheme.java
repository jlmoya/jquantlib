/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008 Klaus Spanderen

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
 * Douglas-Rachford ADI operator-splitting scheme.
 * <p>
 * Java port of v1.42.1 ql/methods/finitedifferences/schemes/douglasscheme.hpp
 * + .cpp.
 * <p>
 * Predictor (explicit Euler) followed by an implicit per-direction sweep;
 * no corrector pass. With {@code theta = 0.5} this coincides with
 * Crank-Nicolson on a 1D problem.
 *
 * @author Phase 2h WI-1 port
 */
public class DouglasScheme {

    /** Time step (set by {@link #setStep}). NaN until first {@code setStep}. */
    protected double dt;

    protected final double theta;

    protected final FdmLinearOpComposite map;
    protected final BoundaryConditionSchemeHelper bcSet;

    /** Constructor with empty boundary-condition set (mirrors C++ default arg). */
    public DouglasScheme(final double theta, final FdmLinearOpComposite map) {
        this(theta, map, new FdmBoundaryConditionSet());
    }

    public DouglasScheme(final double theta,
                         final FdmLinearOpComposite map,
                         final FdmBoundaryConditionSet bcSet) {
        this.dt = Double.NaN;
        this.theta = theta;
        this.map = map;
        this.bcSet = new BoundaryConditionSchemeHelper(bcSet);
    }

    /** Set the rollback step size (called by the solver between steps). */
    public void setStep(final double dt) {
        this.dt = dt;
    }

    /**
     * Advance {@code a} from time {@code t} to {@code t-dt} in-place.
     * Mirrors C++ {@code DouglasScheme::step}.
     */
    public void step(final Array a, final double t) {
        QL.require(t - dt > -1e-8, "a step towards negative time given");

        final double tPrev = Math.max(0.0, t - dt);
        map.setTime(tPrev, t);
        bcSet.setTime(tPrev);

        // Predictor: y = a + dt * map(a)
        bcSet.applyBeforeApplying(map);
        Array y = a.add(map.apply(a).mulAssign(dt));
        bcSet.applyAfterApplying(y);

        // Implicit per-direction sweep
        for (int i = 0; i < map.size(); ++i) {
            final Array rhs = y.sub(map.applyDirection(i, a).mulAssign(theta * dt));
            y = map.solveSplitting(i, rhs, -theta * dt);
        }
        bcSet.applyAfterSolving(y);

        // Write back into a (in-place, like C++ a = y)
        a.fill(y);
    }
}
