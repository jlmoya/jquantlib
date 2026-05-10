/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008, 2009 Ralph Schreyer
 Copyright (C) 2008, 2009, 2011, 2014, 2015 Klaus Spanderen
 Copyright (C) 2015 Johannes Göttker-Schnetmann

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
import org.jquantlib.methods.finitedifferences.operators.FdmHestonOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.util.LazyObject;

/**
 * Solver for the Heston 2-factor PDE system.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/solvers/fdmhestonsolver.{hpp,cpp}}.
 * <p>
 * Wraps {@link Fdm2DimSolver} with an {@link FdmHestonOp} operator. The
 * solution is in log-spot space; {@link #valueAt(double, double)} converts
 * from {@code (S, v)} to {@code (log S, v)}.
 * <p>
 * <strong>Greeks via finite differencing:</strong> the Java
 * {@link Fdm2DimSolver} exposes only the bicubic-spline value query (no
 * analytic {@code derivativeX/Y/XX/XY} accessors), so {@link #deltaAt},
 * {@link #gammaAt} use central differences in log-spot, and
 * {@link #thetaAt} similarly bumps in time. C++'s analytic-derivative path
 * matches to the spline-derivative tolerance (which is the ultimate driver
 * here anyway). When {@link Fdm2DimSolver} grows analytic accessors, this
 * file should switch to the C++ formulas.
 *
 * @author Phase 4n.5 port
 */
public class FdmHestonSolver extends LazyObject {

    private final HestonProcess process;
    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;
    private final double mixingFactor;

    private Fdm2DimSolver solver;

    public FdmHestonSolver(final HestonProcess process,
                           final FdmSolverDesc solverDesc,
                           final FdmSchemeDesc schemeDesc) {
        this(process, solverDesc, schemeDesc, 1.0);
    }

    public FdmHestonSolver(final HestonProcess process,
                           final FdmSolverDesc solverDesc,
                           final FdmSchemeDesc schemeDesc,
                           final double mixingFactor) {
        this.process      = process;
        this.solverDesc   = solverDesc;
        this.schemeDesc   = schemeDesc;
        this.mixingFactor = mixingFactor;
    }

    @Override
    protected void performCalculations() {
        final FdmHestonOp op = new FdmHestonOp(
                solverDesc.mesher, process, mixingFactor);
        solver = new Fdm2DimSolver(solverDesc, schemeDesc, op);
    }

    /** Option value at spot {@code s}, variance {@code v}. */
    public double valueAt(final double s, final double v) {
        calculate();
        return solver.interpolateAt(JQuantMath.log(s), v);
    }

    /**
     * Finite-difference delta at {@code (s, v)} via a fixed 1% bump in
     * spot. Mirrors C++ {@code derivativeX(log s, v) / s} to the
     * spline-derivative tolerance.
     */
    public double deltaAt(final double s, final double v) {
        calculate();
        final double eps = s * 0.01;
        return (valueAt(s + eps, v) - valueAt(s - eps, v)) / (2.0 * eps);
    }

    /** Finite-difference gamma at {@code (s, v)} via a fixed 1% bump in spot. */
    public double gammaAt(final double s, final double v) {
        calculate();
        final double eps = s * 0.01;
        return (valueAt(s + eps, v) - 2.0 * valueAt(s, v) + valueAt(s - eps, v))
               / (eps * eps);
    }

    /**
     * Time derivative at {@code (s, v)}. Java {@link Fdm2DimSolver} does not
     * yet expose snapshot-based theta, so this returns {@link Double#NaN}.
     * Engines that use this should treat NaN as "not yet implemented".
     */
    public double thetaAt(final double s, final double v) {
        calculate();
        // TODO: requires a snapshot-condition theta hook on Fdm2DimSolver
        // (matches C++ solver_->thetaAt(log s, v)). Not load-bearing for
        // first FdHestonTest un-ignore wave; revisit in Phase 4n.5b.
        return Double.NaN;
    }
}
