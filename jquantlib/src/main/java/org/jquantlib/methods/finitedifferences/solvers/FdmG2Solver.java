/*
 Copyright (C) 2011 Klaus Spanderen

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
package org.jquantlib.methods.finitedifferences.solvers;

import org.jquantlib.methods.finitedifferences.operators.FdmG2Op;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.model.shortrate.twofactormodels.G2;
import org.jquantlib.quotes.Handle;
import org.jquantlib.util.LazyObject;

/**
 * G2++ two-factor short-rate FDM solver.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/solvers/fdmg2solver.{hpp,cpp}}.
 * Wires {@link FdmG2Op} into a {@link Fdm2DimSolver}.
 *
 * @author Phase 2h WI-1 port
 */
public class FdmG2Solver extends LazyObject {

    private final Handle<G2> model;
    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;

    private Fdm2DimSolver solver;

    /** Convenience overload matching C++ default {@code FdmSchemeDesc::Hundsdorfer()}. */
    public FdmG2Solver(final Handle<G2> model,
                       final FdmSolverDesc solverDesc) {
        this(model, solverDesc, FdmSchemeDesc.Hundsdorfer());
    }

    public FdmG2Solver(final Handle<G2> model,
                       final FdmSolverDesc solverDesc,
                       final FdmSchemeDesc schemeDesc) {
        this.model = model;
        this.solverDesc = solverDesc;
        this.schemeDesc = schemeDesc;
        // Mirrors C++ registerWith(model_): forward observable change
        // notifications from the Handle so the lazy solver invalidates.
        model.addObserver(this);
    }

    @Override
    protected void performCalculations() {
        final FdmG2Op op = new FdmG2Op(
                solverDesc.mesher, model.currentLink(), 0, 1);
        solver = new Fdm2DimSolver(solverDesc, schemeDesc, op);
    }

    /** Solver value at {@code (x, y)} of the OU state variables. */
    public double valueAt(final double x, final double y) {
        calculate();
        return solver.interpolateAt(x, y);
    }
}
