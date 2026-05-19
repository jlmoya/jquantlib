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
 * Hundsdorfer-Verwer ADI operator-splitting scheme.
 * <p>
 * Java port of v1.42.1 ql/methods/finitedifferences/schemes/hundsdorferscheme.hpp + .cpp.
 * <p>
 * The algorithm is a predictor-corrector ADI: an explicit Euler predictor followed by an implicit per-direction sweep,
 * then an explicit corrector using {@code mu} and a second per-direction sweep.
 *
 * @author Phase 2h WI-1 port
 */
public class HundsdorferScheme {

    protected final double theta;
    protected final double mu;
    protected final FdmLinearOpComposite map;
    protected final BoundaryConditionSchemeHelper bcSet;
    /** Time step (set by {@link #setStep}). NaN until first {@code setStep}. */
    protected double dt;

    /** Constructor with empty boundary-condition set (mirrors C++ default arg). */
    public HundsdorferScheme(final double theta, final double mu, final FdmLinearOpComposite map) {
        this(theta, mu, map, new FdmBoundaryConditionSet());
    }

    public HundsdorferScheme(final double theta, final double mu, final FdmLinearOpComposite map,
            final FdmBoundaryConditionSet bcSet) {
        this.dt = Double.NaN;
        this.theta = theta;
        this.mu = mu;
        this.map = map;
        this.bcSet = new BoundaryConditionSchemeHelper(bcSet);
    }

    /** Set the rollback step size (called by the solver between steps). */
    public void setStep(final double dt) {
        this.dt = dt;
    }

    /**
     * Advance {@code a} from time {@code t} to {@code t-dt} in-place. Mirrors C++ {@code HundsdorferScheme::step}.
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

        final Array y0 = y.clone();

        // Implicit per-direction sweep on the predictor
        for ( int i = 0; i < map.size(); ++i ) {
            final Array rhs = y.sub(map.applyDirection(i, a).mulAssign(theta * dt));
            y = map.solveSplitting(i, rhs, -theta * dt);
        }

        // Corrector: yt = y0 + mu*dt * map(y - a)
        bcSet.applyBeforeApplying(map);
        Array yt = y0.add(map.apply(y.sub(a)).mulAssign(mu * dt));
        bcSet.applyAfterApplying(yt);

        // Implicit per-direction sweep on the corrector
        for ( int i = 0; i < map.size(); ++i ) {
            final Array rhs = yt.sub(map.applyDirection(i, y).mulAssign(theta * dt));
            yt = map.solveSplitting(i, rhs, -theta * dt);
        }
        bcSet.applyAfterSolving(yt);

        // Write back into a (in-place, like C++ a = yt)
        a.fill(yt);
    }
}
