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
 * <strong>Greeks (Phase 4n.5d):</strong> {@link #deltaAt} and
 * {@link #gammaAt} use the analytic monotonic-cubic spline derivatives
 * exposed by {@link Fdm2DimSolver#derivativeX}/{@link Fdm2DimSolver#derivativeXX}
 * — matching C++ {@code FdmHestonSolver::deltaAt}/{@code gammaAt}
 * verbatim (the spline is in log-spot space, hence the {@code 1/s} and
 * {@code (d2 - d1)/(s*s)} chain-rule terms). {@link #thetaAt} delegates
 * to {@link Fdm2DimSolver#thetaAt}.
 *
 * @author Phase 4n.5 port; Phase 4n.5d analytic gamma/delta wiring
 */
public class FdmHestonSolver extends LazyObject {

    private final HestonProcess process;
    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;
    private final double mixingFactor;

    private Fdm2DimSolver solver;

    /**
     * Two-argument convenience constructor.
     * <p>
     * Mirrors C++ v1.42.1 {@code FdmHestonSolver(Handle<HestonProcess>,
     * FdmSolverDesc)} which defaults {@code schemeDesc} to
     * {@link FdmSchemeDesc#Hundsdorfer()} and {@code mixingFactor} to
     * {@code 1.0}.
     */
    public FdmHestonSolver(final HestonProcess process,
                           final FdmSolverDesc solverDesc) {
        this(process, solverDesc, FdmSchemeDesc.Hundsdorfer(), 1.0);
    }

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
     * Analytic delta at {@code (s, v)}.
     * <p>
     * Mirrors C++ v1.42.1 {@code FdmHestonSolver::deltaAt}:
     * {@code derivativeX(log s, v) / s} where the spline is in log-spot
     * space. The {@code 1/s} factor is the chain-rule jacobian
     * {@code dlog(s)/ds = 1/s}.
     */
    public double deltaAt(final double s, final double v) {
        calculate();
        return solver.derivativeX(JQuantMath.log(s), v) / s;
    }

    /**
     * Analytic gamma at {@code (s, v)}.
     * <p>
     * Mirrors C++ v1.42.1 {@code FdmHestonSolver::gammaAt}:
     * {@code (derivativeXX(x, v) - derivativeX(x, v)) / (s * s)} with
     * {@code x = log s}. The chain rule for d²/ds² of f(log s) yields
     * {@code (f''(log s) - f'(log s)) / s²}.
     */
    public double gammaAt(final double s, final double v) {
        calculate();
        final double x = JQuantMath.log(s);
        return (solver.derivativeXX(x, v) - solver.derivativeX(x, v)) / (s * s);
    }

    /**
     * Time derivative (theta) at {@code (s, v)}.
     * <p>
     * Mirrors C++ v1.42.1 {@code FdmHestonSolver::thetaAt}: delegates to
     * {@link Fdm2DimSolver#thetaAt} in log-spot space.
     */
    public double thetaAt(final double s, final double v) {
        calculate();
        return solver.thetaAt(JQuantMath.log(s), v);
    }

    /**
     * Mean-variance delta at {@code (s, v)} — delta with respect to the
     * mean-reverting variance factor.
     * <p>
     * Mirrors C++ v1.42.1 {@code FdmHestonSolver::meanVarianceDeltaAt}:
     * {@code deltaAt(s, v) + alpha * derivativeY(log s, v)} with
     * {@code alpha = rho * sigma / s}. Accounts for the instantaneous
     * correlation between the equity log-return and the variance factor
     * in the Heston SDE — the chain rule adds a {@code rho * sigma / s}
     * cross-term coming from the variance-direction spline derivative.
     */
    public double meanVarianceDeltaAt(final double s, final double v) {
        calculate();
        final double rho   = process.rho().currentLink().value();
        final double sigma = process.sigma().currentLink().value();
        final double alpha = rho * sigma / s;
        return deltaAt(s, v) + alpha * solver.derivativeY(JQuantMath.log(s), v);
    }

    /**
     * Mean-variance gamma at {@code (s, v)} — second derivative wrt spot
     * with the variance cross-term included.
     * <p>
     * Mirrors C++ v1.42.1 {@code FdmHestonSolver::meanVarianceGammaAt}:
     * {@code gammaAt(s, v) + derivativeYY(x, v) * alpha^2
     *        + 2 * derivativeXY(x, v) * alpha / s} with
     * {@code x = log s} and {@code alpha = rho * sigma / s}.
     */
    public double meanVarianceGammaAt(final double s, final double v) {
        calculate();
        final double x     = JQuantMath.log(s);
        final double rho   = process.rho().currentLink().value();
        final double sigma = process.sigma().currentLink().value();
        final double alpha = rho * sigma / s;
        return gammaAt(s, v)
                + solver.derivativeYY(x, v) * alpha * alpha
                + 2.0 * solver.derivativeXY(x, v) * alpha / s;
    }
}
