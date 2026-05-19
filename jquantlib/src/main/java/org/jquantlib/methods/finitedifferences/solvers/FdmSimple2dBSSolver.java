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
 Copyright (C) 2009 Ralph Schreyer
 */
package org.jquantlib.methods.finitedifferences.solvers;

import org.jquantlib.methods.finitedifferences.operators.FdmBlackScholesOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.util.LazyObject;

/**
 * Two-dimensional finite-differences Black-Scholes solver wrapping a single-asset {@link FdmBlackScholesOp} on a 2D
 * mesh — used by engines (e.g. {@code FdSimpleBSSwingEngine}) whose second axis represents a discrete control variable
 * (number of exercise rights used) rather than a stochastic state, so the PDE operator only depends on the first (spot)
 * axis.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/solvers/fdmsimple2dbssolver.{hpp,cpp}}.
 *
 * <p>{@code valueAt(s, a)} interpolates the rolled-back grid at
 * {@code (ln s, ln a)}; {@code deltaAt} and {@code gammaAt} are central finite-difference approximations along the spot
 * axis at fixed {@code a}. {@code thetaAt(s, a)} delegates to {@link Fdm2DimSolver#thetaAt(double, double)}
 * (snapshot-based theta).
 *
 * @author Phase 5e.5b-CFC-d-170 port
 */
public class FdmSimple2dBSSolver extends LazyObject {

    private final GeneralizedBlackScholesProcess process_;
    private final double strike_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;

    private Fdm2DimSolver solver_;

    public FdmSimple2dBSSolver(final GeneralizedBlackScholesProcess process, final double strike,
            final FdmSolverDesc solverDesc, final FdmSchemeDesc schemeDesc) {
        this.process_ = process;
        this.strike_ = strike;
        this.solverDesc_ = solverDesc;
        this.schemeDesc_ = schemeDesc;

        // registerWith(process_) in C++; in JQuantLib LazyObject is the
        // observer, but the engine driving this solver already observes
        // the process — no additional registration needed here.
    }

    @Override
    protected void performCalculations() {
        // FdmBlackScholesOp(mesher, process, strike, direction=0)
        final FdmBlackScholesOp op = new FdmBlackScholesOp(solverDesc_.mesher, process_, strike_, 0);
        solver_ = new Fdm2DimSolver(solverDesc_, schemeDesc_, op);
    }

    public double valueAt(final double s, final double a) {
        calculate();
        return solver_.interpolateAt(Math.log(s), Math.log(a));
    }

    public double deltaAt(final double s, final double a, final double eps) {
        return (valueAt(s + eps, a) - valueAt(s - eps, a)) / (2.0 * eps);
    }

    public double gammaAt(final double s, final double a, final double eps) {
        return (valueAt(s + eps, a) + valueAt(s - eps, a) - 2.0 * valueAt(s, a)) / (eps * eps);
    }

    public double thetaAt(final double s, final double a) {
        calculate();
        return solver_.thetaAt(Math.log(s), Math.log(a));
    }
}
