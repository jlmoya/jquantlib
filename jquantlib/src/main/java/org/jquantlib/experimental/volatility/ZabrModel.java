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

 JQuantLib is based on QuantLib. http://quantlib.org/
*/

/*
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.QL;
import org.jquantlib.experimental.finitedifferences.FdmDupire1dOp;
import org.jquantlib.experimental.finitedifferences.FdmZabrOp;
import org.jquantlib.experimental.finitedifferences.Glued1dMesher;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.interpolations.BicubicSplineInterpolation;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.ode.AdaptiveRungeKutta;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmBackwardSolver;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.termstructures.volatilities.Sabr;

import java.util.Arrays;
import java.util.Collections;

/**
 * ZABR model (Andreasen-Huge ZABR — Expansions for the Masses, 2011).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/zabr.{hpp,cpp}}.
 *
 * <p><b>Phase 4f.5b — gamma != 1 + Dupire FD price ported.</b>
 * <ul>
 *   <li>{@link #lognormalVolatility(double)} / {@link #normalVolatility(double)}
 *       fully implemented for both {@code gamma == 1} (closed-form
 *       zabr.cpp lines 332-338) and {@code gamma != 1} (adaptive Runge-Kutta
 *       integration of the {@code F(y, u)} ODE, zabr.cpp lines 339-358).</li>
 *   <li>{@link #localVolatility(double)} fully implemented (zabr.cpp lines 97-114).</li>
 *   <li>{@link #fdPrice(double)} fully implemented via {@link FdmDupire1dOp}
 *       backward solver (zabr.cpp lines 116-198).</li>
 * </ul>
 *
 * <p><b>Phase 4f.5c — full FD price ported.</b>
 * <ul>
 *   <li>{@link #fullFdPrice(double)} fully implemented via the 2-factor
 *       {@link FdmZabrOp} backward solver (Hundsdorfer scheme), with two
 *       {@link Concentrating1dMesher} pieces glued together by
 *       {@link Glued1dMesher} for the forward grid and a single
 *       {@link Concentrating1dMesher} for the alpha grid (zabr.cpp lines
 *       200-310). Solution interpolated via {@link BicubicSplineInterpolation}.</li>
 * </ul>
 */
public class ZabrModel {

    private final double expiryTime_;
    private final double forward_;
    private final double alpha_;
    private final double beta_;
    /** {@code nu_} stored after the C++ transformation {@code nu * alpha^(1-gamma)}. */
    private final double nu_;
    private final double rho_;
    private final double gamma_;

    /**
     * Constructor (zabr.cpp lines 42-56). Validates SABR parameters and the extra ZABR-specific {@code gamma >= 0}
     * bound.
     *
     * <p>Note: the stored {@code nu_} is the C++ transformed value
     * {@code nu * alpha^(1 - gamma)} (matches C++ field semantics so that downstream formulas can use {@code nu_}
     * directly).
     */
    public ZabrModel(final double expiryTime, final double forward, final double alpha, final double beta,
            final double nu, final double rho, final double gamma) {
        new Sabr().validateSabrParameters(alpha, beta, nu, rho);
        QL.require(gamma >= 0.0, "gamma must be non negative: " + gamma + " not allowed");
        QL.require(forward >= 0.0, "forward must be non negative: " + forward + " not allowed");
        QL.require(expiryTime > 0.0, "expiry time must be positive: " + expiryTime + " not allowed");

        this.expiryTime_ = expiryTime;
        this.forward_ = forward;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.nu_ = nu * Math.pow(alpha, 1.0 - gamma); // C++ transformation
        this.rho_ = rho;
        this.gamma_ = gamma;
    }

    public double forward() {
        return forward_;
    }

    public double expiryTime() {
        return expiryTime_;
    }

    public double alpha() {
        return alpha_;
    }

    public double beta() {
        return beta_;
    }

    public double nu() {
        return nu_;
    }

    public double rho() {
        return rho_;
    }

    public double gamma() {
        return gamma_;
    }

    /**
     * ZABR-equivalent Black (lognormal) volatility — mirrors C++ v1.42.1 {@code ZabrModel::lognormalVolatility(strike)}
     * → {@code lognormalVolatilityHelper}.
     */
    public double lognormalVolatility(final double strike) {
        final double xs = x(strike);
        return lognormalVolatilityHelper(strike, xs);
    }

    /**
     * Normal (Bachelier-equivalent) ZABR volatility — mirrors C++ v1.42.1 {@code ZabrModel::normalVolatility(strike)} →
     * {@code normalVolatilityHelper}.
     */
    public double normalVolatility(final double strike) {
        final double xs = x(strike);
        return normalVolatilityHelper(strike, xs);
    }

    /**
     * Local volatility — mirrors C++ {@code ZabrModel::localVolatility(f)} (zabr.cpp lines 97-114).
     * <p>
     * {@code sigma_loc(f) = alpha * |f|^beta / F(y(f), alpha^{gamma-1} * x(f))}
     */
    public double localVolatility(final double f) {
        final double xs = x(f);
        return localVolatilityHelper(f, xs);
    }

    /**
     * FD price under the Dupire local-vol PDE — mirrors C++ {@code ZabrModel::fdPrice(strike)} (zabr.cpp lines
     * 116-198).
     * <p>
     * Builds a Concentrating1dMesher around the forward, evaluates local-vol at each node, integrates the Dupire
     * backward PDE via {@link FdmBackwardSolver} (Douglas scheme), and interpolates the solution at the requested
     * strike via cubic spline with second-derivative boundary conditions (matches C++).
     */
    public double fdPrice(final double strike) {
        // Grid parameters mirror C++ zabr.cpp lines 124-132 exactly.
        final double start = Math.min(0.00001, strike * 0.5);
        final double end = Math.max(0.10, strike * 1.5);
        final int size = 500;
        final double density = 0.1;
        final int steps = (int) Math.ceil(expiryTime_ * 24);
        final int dampingSteps = 5;

        // Mesher (concentrating around forward)
        final Fdm1dMesher m1 = new Concentrating1dMesher(start, end, size, forward_, density, true);
        final FdmMesherComposite mesher = new FdmMesherComposite(Collections.singletonList(m1));

        // Boundary conditions — empty (matches C++)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // Layout / size
        final FdmLinearOpLayout layout = mesher.layout();
        final int n = layout.size();

        // Initial values: max(forward - k, 0)
        final Array rhs = new Array(n);
        for ( final FdmLinearOpIterator it : layout ) {
            final double k = mesher.location(it, 0);
            rhs.set(it.index(), Math.max(forward_ - k, 0.0));
        }

        // Local vols at each node
        final Array kArr = mesher.locations(0);
        final Array locVol = new Array(n);
        for ( int i = 0; i < n; ++i ) {
            locVol.set(i, localVolatility(kArr.get(i)));
        }

        // Backward solver — Douglas scheme (matches C++)
        final FdmDupire1dOp op = new FdmDupire1dOp(mesher, locVol);
        final FdmBackwardSolver solver = new FdmBackwardSolver(op, boundaries, null, FdmSchemeDesc.Douglas());
        solver.rollback(rhs, expiryTime_, 0.0, steps, dampingSteps);

        // Cubic spline interpolation with second-derivative boundary conditions.
        // CubicInterpolation accepts Array directly; rhs already holds the
        // rolled-back values.
        final CubicInterpolation interp = new CubicInterpolation(kArr, rhs, CubicInterpolation.DerivativeApprox.Spline,
                true, CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        // C++: interpolation->disableExtrapolation(); — strike must be inside grid.
        QL.require(strike >= kArr.get(0) && strike <= kArr.get(n - 1),
                "strike (" + strike + ") outside FD grid [" + kArr.get(0) + "," + kArr.get(n - 1) + "]");
        return interp.op(strike);
    }

    /**
     * Full FD price under the ZABR 2-factor PDE — mirrors C++ {@code ZabrModel::fullFdPrice(strike)} (zabr.cpp lines
     * 200-310).
     * <p>
     * Builds a 2-D grid: forward direction uses two {@link Concentrating1dMesher} pieces (concentrating around
     * {@code min(forward,strike)} and {@code max(forward,strike)}) glued by {@link Glued1dMesher}; alpha direction uses
     * a single {@link Concentrating1dMesher} concentrating around the initial alpha. Backward integration uses
     * Hundsdorfer scheme via {@link FdmZabrOp}. Solution at {@code (forward, alpha)} is read back through a
     * {@link BicubicSplineInterpolation}.
     */
    public double fullFdPrice(final double strike) {
        // Grid parameter constants — mirror C++ zabr.cpp lines 203-225.
        final double eps = 0.01;
        final double scaleFactor = 1.5;
        final double normInvEps = new InverseCumulativeNormal().op(1.0 - eps);
        // alphaI used for forward-grid scaling — see zabr.cpp line 206.
        final double alphaI = alpha_ * Math.pow(forward_, beta_ - 1.0);
        // nu_ has already been transformed in the constructor (== nu * alpha^(1-gamma)).
        final double sqrtT = Math.sqrt(expiryTime_);
        double v0 = alpha_ * Math.exp(-scaleFactor * normInvEps * sqrtT * nu_);
        double v1 = alpha_ * Math.exp(scaleFactor * normInvEps * sqrtT * nu_);
        double f0 = forward_ * Math.exp(-scaleFactor * normInvEps * sqrtT * alphaI);
        double f1 = forward_ * Math.exp(scaleFactor * normInvEps * sqrtT * alphaI);
        v1 = Math.min(v1, 2.0);
        f0 = Math.min(strike / 2.0, f0);
        f1 = Math.max(strike * 1.5, Math.min(f1, Math.max(2.0, strike * 1.5)));

        final int sizef = 100;
        final int sizev = 100;
        final int steps = (int) (24 * expiryTime_ + 1);
        final int dampingSteps = 5;
        final double densityf = 0.1;
        final double densityv = 0.1;

        QL.require(strike >= f0 && strike <= f1,
                "strike (" + strike + ") must be inside pde grid [" + f0 + ";" + f1 + "]");

        // Forward mesher — two concentrating pieces glued at midpoint
        // x0 = min(forward, strike), x1 = max(forward, strike).
        // Mirror zabr.cpp lines 240-252.
        final double x0 = Math.min(forward_, strike);
        final double x1 = Math.max(forward_, strike);
        final int sizefa = Math.max(4, (int) Math.ceil(((x0 + x1) / 2.0 - f0) / (f1 - f0) * (double) sizef));
        // common point so we can spend one more on the right half (matches C++)
        final int sizefb = sizef - sizefa + 1;

        final Fdm1dMesher mfa = new Concentrating1dMesher(f0, (x0 + x1) / 2.0, sizefa, x0, densityf, true);
        final Fdm1dMesher mfb = new Concentrating1dMesher((x0 + x1) / 2.0, f1, sizefb, x1, densityf, true);
        final Fdm1dMesher mf = new Glued1dMesher(mfa, mfb);

        // Alpha (volatility) mesher concentrating around alpha_.
        final Fdm1dMesher mv = new Concentrating1dMesher(v0, v1, sizev, alpha_, densityv, true);

        final FdmMesherComposite mesher = new FdmMesherComposite(Arrays.asList(mf, mv));
        final FdmLinearOpLayout layout = mesher.layout();
        final int n = layout.size();

        // Initial values (call payoff): max(f - strike, 0). Also collect
        // unique f and v locations (the mesh is a tensor product, so f-locs
        // come from rows where coord[1]==0 and v-locs from coord[0]==0).
        final Array rhs = new Array(n);
        final double[] f_ = new double[mf.size()];
        final double[] v_ = new double[mv.size()];
        for ( final FdmLinearOpIterator iter : layout ) {
            final double f = mesher.location(iter, 0);
            rhs.set(iter.index(), Math.max(f - strike, 0.0));
            if ( iter.coordinates()[1] == 0 ) {
                f_[iter.coordinates()[0]] = mesher.location(iter, 0);
            }
            if ( iter.coordinates()[0] == 0 ) {
                v_[iter.coordinates()[1]] = mesher.location(iter, 1);
            }
        }

        // Boundary conditions — empty (matches C++).
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // Backward solver — Hundsdorfer scheme (matches C++).
        final FdmZabrOp op = new FdmZabrOp(mesher, beta_, nu_, rho_, gamma_);
        final FdmBackwardSolver solver = new FdmBackwardSolver(op, boundaries, null, FdmSchemeDesc.Hundsdorfer());
        solver.rollback(rhs, expiryTime_, 0.0, steps, dampingSteps);

        // Reshape rhs into a Matrix(f_size, v_size) for bicubic interpolation.
        // C++ stores: result(j,i) = rhs[j*sizef+i] for i in f, j in v
        // i.e. row j is the i-th f-value at the j-th v-value.
        // BicubicSplineInterpolation expects vx (length M), vy (length N), Matrix(N,M)
        // with mz(j,i) = z(vx[i], vy[j]). So columns indexed by x (=f), rows by y (=v).
        final int sizeF = f_.length;
        final int sizeV = v_.length;
        final Matrix mz = new Matrix(sizeV, sizeF);
        for ( int j = 0; j < sizeV; ++j ) {
            for ( int i = 0; i < sizeF; ++i ) {
                // rhs flat index: layout uses spacing [1, sizeF],
                // so flat = i*1 + j*sizeF.
                mz.set(j, i, rhs.get(j * sizeF + i));
            }
        }
        final BicubicSplineInterpolation interp = new BicubicSplineInterpolation(new Array(f_), new Array(v_), mz);
        // C++: interpolation->disableExtrapolation(); — read at (forward_, alpha_).
        QL.require(forward_ >= f_[0] && forward_ <= f_[sizeF - 1],
                "forward " + forward_ + " outside f-grid [" + f_[0] + "," + f_[sizeF - 1] + "]");
        QL.require(alpha_ >= v_[0] && alpha_ <= v_[sizeV - 1],
                "alpha " + alpha_ + " outside v-grid [" + v_[0] + "," + v_[sizeV - 1] + "]");
        return interp.op(forward_, alpha_);
    }

    // ------------------------------------------------------------------
    // Private helpers (mirror C++ ZabrModel:: helpers)
    // ------------------------------------------------------------------

    /**
     * Mirrors C++ {@code lognormalVolatilityHelper} (zabr.cpp lines 58-64).
     */
    private double lognormalVolatilityHelper(final double strike, final double x) {
        if ( Closeness.isClose(strike, forward_) ) {
            return Math.pow(forward_, beta_ - 1.0) * alpha_;
        }
        return Math.log(forward_ / strike) / x;
    }

    /**
     * Mirrors C++ {@code normalVolatilityHelper} (zabr.cpp lines 78-83).
     */
    private double normalVolatilityHelper(final double strike, final double x) {
        if ( Closeness.isClose(strike, forward_) ) {
            return Math.pow(forward_, beta_) * alpha_;
        }
        return (forward_ - strike) / x;
    }

    /**
     * Mirrors C++ {@code localVolatilityHelper} (zabr.cpp lines 97-102).
     */
    private double localVolatilityHelper(final double f, final double x) {
        return alpha_ * Math.pow(Math.abs(f), beta_) / F(y(f), Math.pow(alpha_, gamma_ - 1.0) * x);
    }

    /**
     * Mirrors C++ {@code ZabrModel::x(strike)} (zabr.cpp lines 312-361, scalar dispatch through the vector form). For
     * {@code gamma == 1} uses the closed form (zabr.cpp lines 332-338); otherwise integrates the {@code F(y, u)} ODE
     * with {@link AdaptiveRungeKutta} (zabr.cpp lines 339-358).
     */
    private double x(final double strike) {
        if ( beta_ >= 1.0 ) {
            QL.require(strike > 0.0, "strike must be positive (" + strike + ") if beta = 1");
        }
        final double yy = y(strike);
        if ( Closeness.isClose(gamma_, 1.0) ) {
            // Closed form — zabr.cpp lines 333-338
            final double J = Math.sqrt(1.0 + nu_ * nu_ * yy * yy - 2.0 * rho_ * nu_ * yy);
            return Math.log((J + nu_ * yy - rho_) / (1.0 - rho_)) / nu_;
        }
        // gamma != 1 — integrate F(y, u) ODE from y0=0, u0=0 to y=yy via RK.
        // Mirrors zabr.cpp lines 339-358 for a single strike (scalar path).
        final AdaptiveRungeKutta rk = new AdaptiveRungeKutta(1.0e-8, 1.0e-5, 0.0);
        final AdaptiveRungeKutta.OdeFct ode = (final double yArg, final double[] uArr) -> new double[] {
                F(yArg, uArr[0]) };
        final double[] initial = { 0.0 };
        final double[] result = rk.solve(ode, initial, 0.0, yy);
        // C++ multiplies by alpha^{1-gamma} (zabr.cpp line 353).
        return result[0] * Math.pow(alpha_, 1.0 - gamma_);
    }

    /**
     * Mirrors C++ {@code ZabrModel::y(strike)} (zabr.cpp lines 363-375).
     */
    private double y(final double strike) {
        if ( Closeness.isClose(beta_, 1.0) ) {
            return Math.log(forward_ / strike) * Math.pow(alpha_, gamma_ - 2.0);
        }
        final double term = (strike < 0.0)
                ? Math.pow(forward_, 1.0 - beta_) + Math.pow(-strike, 1.0 - beta_)
                : Math.pow(forward_, 1.0 - beta_) - Math.pow(strike, 1.0 - beta_);
        return term * Math.pow(alpha_, gamma_ - 2.0) / (1.0 - beta_);
    }

    /**
     * Mirrors C++ {@code ZabrModel::F(y, u)} (zabr.cpp lines 377-385). RHS of the ODE driving {@code u(y)} for the
     * gamma != 1 case.
     */
    private double F(final double y, final double u) {
        final double A =
                1.0 + (gamma_ - 2.0) * (gamma_ - 2.0) * nu_ * nu_ * y * y + 2.0 * rho_ * (gamma_ - 2.0) * nu_ * y;
        final double B = 2.0 * rho_ * (1.0 - gamma_) * nu_ + 2.0 * (1.0 - gamma_) * (gamma_ - 2.0) * nu_ * nu_ * y;
        final double C = (1.0 - gamma_) * (1.0 - gamma_) * nu_ * nu_;
        return (-B * u + Math.sqrt(B * B * u * u - 4.0 * A * (C * u * u - 1.0))) / (2.0 * A);
    }
}
