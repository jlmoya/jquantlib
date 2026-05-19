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

import org.jquantlib.experimental.processes.KlugeExtOUProcess;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmNdimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.util.LazyObject;

/**
 * Kluge / extended Ornstein-Uhlenbeck 3D FDM solver.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/fdmklugeextousolver.hpp}
 * (header-only template). C++ templates the solver on its dimension
 * ({@code N >= 3}); the Java port uses {@link FdmNdimSolver}, whose
 * dimension is read off the mesher's layout at construction time, so the
 * dimension parameter is not needed.</p>
 *
 * @author Phase 5e.5b-CFC-d-287 port
 */
public final class FdmKlugeExtOUSolver extends LazyObject {

    private final KlugeExtOUProcess klugeOUProcess_;
    private final YieldTermStructure rTS_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;

    private FdmNdimSolver solver_;

    public FdmKlugeExtOUSolver(final KlugeExtOUProcess klugeOUProcess,
                                final YieldTermStructure rTS,
                                final FdmSolverDesc solverDesc,
                                final FdmSchemeDesc schemeDesc) {
        this.klugeOUProcess_ = klugeOUProcess;
        this.rTS_ = rTS;
        this.solverDesc_ = solverDesc;
        this.schemeDesc_ = schemeDesc;
    }

    public double valueAt(final double[] x) {
        calculate();
        return solver_.interpolateAt(x);
    }

    @Override
    protected void performCalculations() {
        final FdmKlugeExtOUOp op = new FdmKlugeExtOUOp(
                solverDesc_.mesher,
                klugeOUProcess_,
                rTS_,
                solverDesc_.bcSet,
                16);

        solver_ = new FdmNdimSolver(solverDesc_, schemeDesc_, op);
    }
}
