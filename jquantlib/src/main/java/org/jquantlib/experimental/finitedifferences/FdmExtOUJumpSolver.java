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

import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm2DimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.util.LazyObject;

/**
 * Lazy 2D solver for the Kluge OU + exp-jumps model, built around the
 * {@link FdmExtOUJumpOp} composite operator and a {@link Fdm2DimSolver}.
 * <p>
 * Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdmextoujumpsolver.{hpp,cpp}}.
 *
 * <p>The C++ default scheme is {@link FdmSchemeDesc#Hundsdorfer()}; the
 * default Gauss–Laguerre integration order for the jump-density transform
 * is 32 — both values are preserved in the Java port.</p>
 *
 * @author Phase 5e.5b-CFC-d-171 port
 */
public class FdmExtOUJumpSolver extends LazyObject {

    private static final int GAUSS_LAGUERRE_ORDER = 32;

    private final ExtOUWithJumpsProcess process_;
    private final YieldTermStructure rTS_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;

    private Fdm2DimSolver solver_;

    public FdmExtOUJumpSolver(final ExtOUWithJumpsProcess process,
                              final YieldTermStructure rTS,
                              final FdmSolverDesc solverDesc) {
        this(process, rTS, solverDesc, FdmSchemeDesc.Hundsdorfer());
    }

    public FdmExtOUJumpSolver(final ExtOUWithJumpsProcess process,
                              final YieldTermStructure rTS,
                              final FdmSolverDesc solverDesc,
                              final FdmSchemeDesc schemeDesc) {
        this.process_ = process;
        this.rTS_ = rTS;
        this.solverDesc_ = solverDesc;
        this.schemeDesc_ = schemeDesc;
        // The C++ version calls registerWith(process_); jquantlib's LazyObject
        // does not currently expose a public registerWith hook from a non-handle
        // observable, so the dependency is captured at construction time (the
        // process is held as a strong reference and treated as immutable for
        // the solver's lifetime — sufficient for the cached engine usage).
    }

    @Override
    protected void performCalculations() {
        final FdmExtOUJumpOp op = new FdmExtOUJumpOp(
                solverDesc_.mesher, process_, rTS_,
                solverDesc_.bcSet, GAUSS_LAGUERRE_ORDER);
        solver_ = new Fdm2DimSolver(solverDesc_, schemeDesc_, op);
    }

    /** Interpolated value at {@code (x, y)} after the lazy rollback. */
    public double valueAt(final double x, final double y) {
        calculate();
        return solver_.interpolateAt(x, y);
    }
}
