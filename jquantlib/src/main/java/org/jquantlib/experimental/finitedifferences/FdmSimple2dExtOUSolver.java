/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
/*
 Copyright (C) 2011 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm2DimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.util.LazyObject;

/**
 * Lazy 2D solver for the extended Ornstein-Uhlenbeck PDE built around
 * {@link FdmExtendedOrnsteinUhlenbeckOp} and a {@link Fdm2DimSolver}.
 *
 * <p>Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdmsimple2dextousolver.hpp}
 * (header-only in C++).</p>
 *
 * <p>The C++ default scheme is {@link FdmSchemeDesc#Hundsdorfer()}; the
 * storage engine overrides this with {@link FdmSchemeDesc#Douglas()} via the
 * full constructor.</p>
 *
 * @author Phase 5e.5b-CFC-d-215 port
 */
public class FdmSimple2dExtOUSolver extends LazyObject {

    private final ExtendedOrnsteinUhlenbeckProcess process_;
    private final YieldTermStructure rTS_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;

    private Fdm2DimSolver solver_;

    public FdmSimple2dExtOUSolver(final ExtendedOrnsteinUhlenbeckProcess process,
                                  final YieldTermStructure rTS,
                                  final FdmSolverDesc solverDesc) {
        this(process, rTS, solverDesc, FdmSchemeDesc.Hundsdorfer());
    }

    public FdmSimple2dExtOUSolver(final ExtendedOrnsteinUhlenbeckProcess process,
                                  final YieldTermStructure rTS,
                                  final FdmSolverDesc solverDesc,
                                  final FdmSchemeDesc schemeDesc) {
        this.process_ = process;
        this.rTS_ = rTS;
        this.solverDesc_ = solverDesc;
        this.schemeDesc_ = schemeDesc;
        // C++ calls registerWith(process); jquantlib's LazyObject doesn't
        // expose a public registerWith hook from a non-handle observable, so
        // the dependency is captured at construction (process held by ref).
    }

    @Override
    protected void performCalculations() {
        final FdmExtendedOrnsteinUhlenbeckOp op =
                new FdmExtendedOrnsteinUhlenbeckOp(
                        solverDesc_.mesher, process_, rTS_, solverDesc_.bcSet);
        solver_ = new Fdm2DimSolver(solverDesc_, schemeDesc_, op);
    }

    /** Interpolated solver value at {@code (x, y)}. */
    public double valueAt(final double x, final double y) {
        calculate();
        return solver_.interpolateAt(x, y);
    }
}
