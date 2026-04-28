/*
 Copyright (C) 2009 Andreas Gaida
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2009, 2017 Klaus Spanderen

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
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

/**
 * Implicit-Euler time-stepping scheme.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/schemes/impliciteulerscheme.{hpp,cpp}}.
 *
 * <h2>Phase 2h scope</h2>
 * The C++ scheme falls back to BiCGStab / GMRES for {@code map.size() != 1}.
 * BiCGStab and GMRES are not yet ported to Java. This implementation
 * supports the {@code map.size() == 1} fast path (used by
 * {@link org.jquantlib.methods.finitedifferences.operators.FdmHullWhiteOp})
 * and throws a clear {@link LibraryException} for higher-dimensional input.
 * That covers {@code FdHullWhiteSwaptionEngine} and the
 * {@code dampingSteps == 0} default of {@code FdG2SwaptionEngine}; multi-d
 * damping is a Phase 2h follow-up.
 *
 * @author Phase 2h WI-1 port
 */
public class ImplicitEulerScheme {

    /** Time step (set by {@link #setStep}). NaN until first {@code setStep}. */
    protected double dt;

    protected final FdmLinearOpComposite map;
    protected final BoundaryConditionSchemeHelper bcSet;

    /** Constructor with empty boundary-condition set (mirrors C++ default arg). */
    public ImplicitEulerScheme(final FdmLinearOpComposite map) {
        this(map, new FdmBoundaryConditionSet());
    }

    public ImplicitEulerScheme(final FdmLinearOpComposite map,
                               final FdmBoundaryConditionSet bcSet) {
        this.dt = Double.NaN;
        this.map = map;
        this.bcSet = new BoundaryConditionSchemeHelper(bcSet);
    }

    /** Set the rollback step size (called by the solver between steps). */
    public void setStep(final double dt) {
        this.dt = dt;
    }

    /** Default step: theta = 1.0 (full implicit). */
    public void step(final Array a, final double t) {
        step(a, t, 1.0);
    }

    /**
     * Advance {@code a} from time {@code t} to {@code t-dt} in-place with
     * implicitness weight {@code theta}.
     * <p>
     * Mirrors C++ {@code ImplicitEulerScheme::step(a, t, theta)} for the
     * {@code map.size() == 1} fast path. Higher-dimensional input throws
     * {@link LibraryException} (BiCGStab/GMRES not yet ported).
     */
    public void step(final Array a, final double t, final double theta) {
        QL.require(t - dt > -1e-8, "a step towards negative time given");

        final double tPrev = Math.max(0.0, t - dt);
        map.setTime(tPrev, t);
        bcSet.setTime(tPrev);

        bcSet.applyBeforeSolving(map, a);

        if (map.size() == 1) {
            final Array result = map.solveSplitting(0, a, -theta * dt);
            a.fill(result);
        } else {
            throw new LibraryException(
                    "ImplicitEulerScheme: BiCGStab/GMRES path not yet ported "
                  + "(map.size() = " + map.size() + "). Use dampingSteps = 0 "
                  + "with multi-dimensional operators (Phase 2h follow-up).");
        }

        bcSet.applyAfterSolving(a);
    }
}
