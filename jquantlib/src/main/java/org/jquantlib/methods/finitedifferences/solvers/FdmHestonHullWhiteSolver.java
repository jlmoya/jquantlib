/*
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
package org.jquantlib.methods.finitedifferences.solvers;

import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.operators.FdmHestonHullWhiteOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.HullWhiteProcess;
import org.jquantlib.util.LazyObject;

/**
 * Solver for the Heston–Hull-White 3-factor PDE system.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/solvers/fdmhestonhullwhitesolver.{hpp,cpp}}.
 * <p>
 * Wraps {@link Fdm3DimSolver} with a {@link FdmHestonHullWhiteOp} operator.
 * The solution is in log-spot space; {@link #valueAt(double, double, double)}
 * converts from {@code (S, v, r)} to {@code (log S, v, r)}.
 *
 * @author Phase 2m Track B port
 */
public class FdmHestonHullWhiteSolver extends LazyObject {

    private final HestonProcess hestonProcess;
    private final HullWhiteProcess hwProcess;
    private final double corrEquityShortRate;
    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;

    private Fdm3DimSolver solver;

    public FdmHestonHullWhiteSolver(final HestonProcess hestonProcess,
                                      final HullWhiteProcess hwProcess,
                                      final double corrEquityShortRate,
                                      final FdmSolverDesc solverDesc,
                                      final FdmSchemeDesc schemeDesc) {
        this.hestonProcess       = hestonProcess;
        this.hwProcess           = hwProcess;
        this.corrEquityShortRate = corrEquityShortRate;
        this.solverDesc          = solverDesc;
        this.schemeDesc          = schemeDesc;
    }

    @Override
    protected void performCalculations() {
        final FdmHestonHullWhiteOp op = new FdmHestonHullWhiteOp(
            solverDesc.mesher, hestonProcess, hwProcess, corrEquityShortRate);
        solver = new Fdm3DimSolver(solverDesc, schemeDesc, op);
    }

    /** Option value at spot {@code s}, variance {@code v}, short rate {@code r}. */
    public double valueAt(final double s, final double v, final double r) {
        calculate();
        return solver.interpolateAt(JQuantMath.log(s), v, r);
    }

    /** Finite-difference delta at {@code (s, v, r)} with bump {@code eps}. */
    public double deltaAt(final double s, final double v, final double r, final double eps) {
        return (valueAt(s + eps, v, r) - valueAt(s - eps, v, r)) / (2.0 * eps);
    }

    /** Finite-difference gamma at {@code (s, v, r)} with bump {@code eps}. */
    public double gammaAt(final double s, final double v, final double r, final double eps) {
        return (valueAt(s + eps, v, r) + valueAt(s - eps, v, r) - 2.0 * valueAt(s, v, r))
               / (eps * eps);
    }

    /** Time derivative of option value at {@code (s, v, r)}. */
    public double thetaAt(final double s, final double v, final double r) {
        calculate();
        return solver.thetaAt(JQuantMath.log(s), v, r);
    }
}
