/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008, 2009 Ralph Schreyer
 Copyright (C) 2008, 2009 Klaus Spanderen

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
import org.jquantlib.methods.finitedifferences.operators.FdmBlackScholesOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.util.LazyObject;

/**
 * Lazy 1-D FDM solver for the Black-Scholes PDE in log-space.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/solvers/fdmblackscholessolver.{hpp,cpp}}.
 * <p>
 * Wires {@link FdmBlackScholesOp} into a {@link Fdm1DimSolver}. The solver interpolates in log(S) space; the public API
 * ({@link #valueAt}, {@link #deltaAt}, {@link #gammaAt}, {@link #thetaAt}) accepts the raw spot price S and converts to
 * {@code ln(S)} before interpolation (matching C++ {@code std::log(s)}).
 *
 * <p>Local-vol and Quanto-helper constructor parameters are omitted (Phase 2m.5).
 *
 * @author Phase 2m Track A port
 */
public class FdmBlackScholesSolver extends LazyObject {

    private final GeneralizedBlackScholesProcess process;
    private final double strike;
    private final FdmSolverDesc solverDesc;
    private final FdmSchemeDesc schemeDesc;
    private final boolean localVol;
    private final double illegalLocalVolOverwrite;

    private Fdm1DimSolver solver;

    /**
     * Default scheme: {@link FdmSchemeDesc#Douglas()}.
     */
    public FdmBlackScholesSolver(final GeneralizedBlackScholesProcess process, final double strike,
            final FdmSolverDesc solverDesc) {
        this(process, strike, solverDesc, FdmSchemeDesc.Douglas(), false, Double.NaN);
    }

    public FdmBlackScholesSolver(final GeneralizedBlackScholesProcess process, final double strike,
            final FdmSolverDesc solverDesc, final FdmSchemeDesc schemeDesc) {
        this(process, strike, solverDesc, schemeDesc, false, Double.NaN);
    }

    /**
     * Full ctor mirroring C++
     * {@code FdmBlackScholesSolver(process, strike, solverDesc, schemeDesc, localVol, illegalLocalVolOverwrite,
     * quantoHelper)}. {@code quantoHelper} is omitted here (deferred — see {@link FdmBlackScholesSolver}'s class doc).
     */
    public FdmBlackScholesSolver(final GeneralizedBlackScholesProcess process, final double strike,
            final FdmSolverDesc solverDesc, final FdmSchemeDesc schemeDesc, final boolean localVol,
            final double illegalLocalVolOverwrite) {
        this.process = process;
        this.strike = strike;
        this.solverDesc = solverDesc;
        this.schemeDesc = schemeDesc;
        this.localVol = localVol;
        this.illegalLocalVolOverwrite = illegalLocalVolOverwrite;
        process.addObserver(this);
    }

    @Override
    protected void performCalculations() {
        final FdmBlackScholesOp op = new FdmBlackScholesOp(solverDesc.mesher, process, strike, localVol,
                illegalLocalVolOverwrite, 0);
        solver = new Fdm1DimSolver(solverDesc, schemeDesc, op);
    }

    /** Option NPV at spot {@code s}. */
    public double valueAt(final double s) {
        calculate();
        return solver.interpolateAt(JQuantMath.log(s));
    }

    /** Delta (dV/dS) at spot {@code s}. */
    public double deltaAt(final double s) {
        calculate();
        return solver.derivativeX(JQuantMath.log(s)) / s;
    }

    /**
     * Gamma (d^2V/dS^2) at spot {@code s}.
     * <p>
     * C++: {@code (derivativeXX(ln s) - derivativeX(ln s)) / s^2}.
     */
    public double gammaAt(final double s) {
        calculate();
        final double logS = JQuantMath.log(s);
        return (solver.derivativeXX(logS) - solver.derivativeX(logS)) / (s * s);
    }

    /** Theta (dV/dt per year, finite-difference estimate) at spot {@code s}. */
    public double thetaAt(final double s) {
        calculate();
        return solver.thetaAt(JQuantMath.log(s));
    }
}
