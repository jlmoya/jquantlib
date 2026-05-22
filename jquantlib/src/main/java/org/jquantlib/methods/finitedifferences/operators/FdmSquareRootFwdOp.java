/*
 Copyright (C) 2012, 2013 Klaus Spanderen
 Copyright (C) 2014 Johannes Goettker-Schnetmann

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
package org.jquantlib.methods.finitedifferences.operators;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Square-root (CIR) Fokker-Planck forward operator.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/operators/fdmsquarerootfwdop.{hpp,cpp}}.
 * <p>
 * Discretizes the forward operator {@code dp/dt = d/dv[ kappa*(v - theta)*p ] + 0.5 * sigma^2 * d^2/dv^2 [v*p]} for a
 * Cox-Ingersoll-Ross variance process. Three transformations of the unknown are supported:
 * <ul>
 *   <li>{@link TransformationType#Plain Plain}: {@code p(v)} on the v-grid.</li>
 *   <li>{@link TransformationType#Power Power}: {@code v^(2*kappa*theta/sigma^2 - 1) p(v)}.</li>
 *   <li>{@link TransformationType#Log Log}: {@code p(log(v))}.</li>
 * </ul>
 * Zero-flux boundary conditions are imposed at both ends of the v-grid.
 *
 * @author Phase 5h.5-SLV port
 */
public class FdmSquareRootFwdOp implements FdmLinearOpComposite {

    private final int direction;
    private final double kappa, theta, sigma;
    private final TransformationType transform;
    private final ModTripleBandLinearOp mapX;
    private final double[] v_;
    public FdmSquareRootFwdOp(final FdmMesher mesher, final double kappa, final double theta, final double sigma,
            final int direction) {
        this(mesher, kappa, theta, sigma, direction, TransformationType.Plain);
    }

    public FdmSquareRootFwdOp(final FdmMesher mesher, final double kappa, final double theta, final double sigma,
            final int direction, final TransformationType type) {
        this.direction = direction;
        this.kappa = kappa;
        this.theta = theta;
        this.sigma = sigma;
        this.transform = type;

        final Array vLoc = mesher.locations(direction);
        final int size = mesher.layout().size();

        // Build the underlying TripleBandLinearOp per transformation.
        final TripleBandLinearOp built;
        if ( type == TransformationType.Plain ) {
            // FirstDeriv * (kappa*(v - theta) + sigma^2)
            //   + SecondDeriv * (0.5*sigma^2*v)
            //   + diag(kappa)
            final Array drift = vLoc.add(-theta).mul(kappa).add(sigma * sigma);
            final Array diff = vLoc.mul(0.5 * sigma * sigma);
            final Array diagK = new Array(size).fill(kappa);
            built = new FirstDerivativeOp(direction, mesher).mult(drift)
                    .add(new SecondDerivativeOp(direction, mesher).mult(diff)).add(diagK);
        } else if ( type == TransformationType.Power ) {
            // SecondDeriv * (0.5*sigma^2*v)
            //   + FirstDeriv * (kappa*(v + theta))
            //   + diag(2*kappa^2*theta/sigma^2)
            final Array diff = vLoc.mul(0.5 * sigma * sigma);
            final Array drift = vLoc.add(theta).mul(kappa);
            final Array diagC = new Array(size).fill(2.0 * kappa * kappa * theta / (sigma * sigma));
            built = new SecondDerivativeOp(direction, mesher).mult(diff)
                    .add(new FirstDerivativeOp(direction, mesher).mult(drift)).add(diagC);
        } else { // Log
            // FirstDeriv * (exp(-v) * (-0.5*sigma^2 - kappa*theta) + kappa)
            //   + SecondDeriv * (0.5*sigma^2*exp(-v))
            //   + diag(kappa*theta*exp(-v))
            final Array expNegV = vLoc.mul(-1.0).exp();
            final Array drift = expNegV.mul(-0.5 * sigma * sigma - kappa * theta).add(kappa);
            final Array diff = expNegV.mul(0.5 * sigma * sigma);
            final Array diagC = expNegV.mul(kappa * theta);
            built = new FirstDerivativeOp(direction, mesher).mult(drift)
                    .add(new SecondDerivativeOp(direction, mesher).mult(diff)).add(diagC);
        }
        this.mapX = new ModTripleBandLinearOp(built);

        // Cache v-mesh values along this direction.
        final int nv = mesher.layout().dim()[direction];
        this.v_ = new double[nv];
        for ( final FdmLinearOpIterator iter : mesher.layout() ) {
            final double v = mesher.location(iter, direction);
            v_[iter.coordinates()[direction]] = v;
        }

        // zero-flux boundary conditions
        setLowerBC(mesher);
        setUpperBC(mesher);
    }

    private void setLowerBC(final FdmMesher mesher) {
        final int n = 1;
        final double[] coeff = getCoeff(n);
        final double f = lowerBoundaryFactor(transform);

        final double b = -(h(n - 1) + h(n)) / zeta(n);
        final double c = h(n - 1) / zetap(n);

        for ( final FdmLinearOpIterator iter : mesher.layout() ) {
            if ( iter.coordinates()[direction] == 0 ) {
                final int idx = iter.index();
                mapX.setDiag(idx, coeff[1] + f * b);
                mapX.setUpper(idx, coeff[2] + f * c);
            }
        }
    }

    private void setUpperBC(final FdmMesher mesher) {
        final int n = v_.length;
        final double[] coeff = getCoeff(n);
        final double f = upperBoundaryFactor(transform);

        final double b = (h(n) + h(n - 1)) / zeta(n);
        final double c = -h(n) / zetam(n);

        for ( final FdmLinearOpIterator iter : mesher.layout() ) {
            if ( iter.coordinates()[direction] == n - 1 ) {
                final int idx = iter.index();
                mapX.setDiag(idx, coeff[1] + f * b);
                mapX.setLower(idx, coeff[0] + f * c);
            }
        }
    }

    public double lowerBoundaryFactor(final TransformationType type) {
        switch ( type ) {
        case Plain:
            return f0Plain();
        case Power:
            return f0Power();
        case Log:
            return f0Log();
        default:
            throw new IllegalArgumentException("unknown transform");
        }
    }

    public double upperBoundaryFactor(final TransformationType type) {
        switch ( type ) {
        case Plain:
            return f1Plain();
        case Power:
            return f1Power();
        case Log:
            return f1Log();
        default:
            throw new IllegalArgumentException("unknown transform");
        }
    }

    private double f0Plain() {
        final int n = 1;
        final double a = -(2 * h(n - 1) + h(n)) / zetam(n);
        final double alpha = sigma * sigma * v(n) / zetam(n) - mu(n) * h(n) / zetam(n);
        final double nu = a * v(n - 1) + (2 * kappa * (v(n - 1) - theta) + sigma * sigma) / (sigma * sigma);
        return alpha / nu * v(n - 1);
    }

    private double f1Plain() {
        final int n = v_.length;
        final double a = (2 * h(n) + h(n - 1)) / zetap(n);
        final double gamma = sigma * sigma * v(n) / zetap(n) + mu(n) * h(n - 1) / zetap(n);
        final double nu = a * v(n + 1) + (2 * kappa * (v(n + 1) - theta) + sigma * sigma) / (sigma * sigma);
        return gamma / nu * v(n + 1);
    }

    private double f0Power() {
        final int n = 1;
        final double muN = kappa * (v(n) + theta);
        final double a = -(2 * h(n - 1) + h(n)) / zetam(n);
        final double alpha = sigma * sigma * v(n) / zetam(n) - muN * h(n) / zetam(n);
        final double nu = a * v(n - 1) + 2 * (kappa * v(n - 1) / (sigma * sigma));
        return alpha / nu * v(n - 1);
    }

    private double f1Power() {
        final int n = v_.length;
        final double muN = kappa * (v(n) + theta);
        final double a = (2 * h(n) + h(n - 1)) / zetap(n);
        final double gamma = sigma * sigma * v(n) / zetap(n) + muN * h(n - 1) / zetap(n);
        final double nu = a * v(n + 1) + 2 * (kappa * v(n + 1) / (sigma * sigma));
        return gamma / nu * v(n + 1);
    }

    private double f0Log() {
        final int n = 1;
        final double muN = ((-kappa * theta - sigma * sigma / 2.0) * Math.exp(-v(1)) + kappa);
        final double a = -(2 * h(n - 1) + h(n)) / zetam(n);
        final double alpha = sigma * sigma * Math.exp(-v(n)) / zetam(n) - muN * h(n) / zetam(n);
        final double nu = a * Math.exp(-v(n - 1)) + 2 * kappa * (1 - theta * Math.exp(-v(n - 1))) / (sigma * sigma);
        return alpha / nu * Math.exp(-v(n - 1));
    }

    private double f1Log() {
        final int n = v_.length;
        final double muN = ((-kappa * theta - sigma * sigma / 2.0) * Math.exp(-v(n)) + kappa);
        final double a = (2 * h(n) + h(n - 1)) / zetap(n);
        final double gamma = sigma * sigma * Math.exp(-v(n)) / zetap(n) + muN * h(n - 1) / zetap(n);
        final double nu = a * Math.exp(-v(n + 1)) + 2 * kappa * (1 - theta * Math.exp(-v(n + 1))) / (sigma * sigma);
        return gamma / nu * Math.exp(-v(n + 1));
    }

    /**
     * Ghost-cell extrapolation for index {@code 0} and {@code v_.length+1} (1-based to mirror C++ convention).
     */
    public double v(final int i) {
        if ( i > 0 && i <= v_.length ) {
            return v_[i - 1];
        } else if ( i == 0 ) {
            if ( transform == TransformationType.Log ) {
                return 2 * v_[0] - v_[1];
            } else {
                return Math.max(0.5 * v_[0], v_[0] - 0.01 * (v_[1] - v_[0]));
            }
        } else if ( i == v_.length + 1 ) {
            return v_[v_.length - 1] + (v_[v_.length - 1] - v_[v_.length - 2]);
        } else {
            throw new IllegalArgumentException("unknown index");
        }
    }

    private double h(final int i) {
        return v(i + 1) - v(i);
    }

    private double mu(final int i) {
        return kappa * (v(i) - theta) + sigma * sigma;
    }

    private double zetam(final int i) {
        return h(i - 1) * (h(i - 1) + h(i));
    }

    private double zeta(final int i) {
        return h(i - 1) * h(i);
    }

    private double zetap(final int i) {
        return h(i) * (h(i - 1) + h(i));
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public void setTime(final double t1, final double t2) {
        // no-op (time-independent)
    }

    /** Returns {alpha, beta, gamma}. */
    private double[] getCoeff(final int n) {
        switch ( transform ) {
        case Plain:
            return getCoeffPlain(n);
        case Power:
            return getCoeffPower(n);
        case Log:
            return getCoeffLog(n);
        default:
            throw new IllegalArgumentException("unknown transform");
        }
    }

    private double[] getCoeffPlain(final int n) {
        final double alpha = sigma * sigma * v(n) / zetam(n) - mu(n) * h(n) / zetam(n);
        final double beta = -sigma * sigma * v(n) / zeta(n) + mu(n) * (h(n) - h(n - 1)) / zeta(n) + kappa;
        final double gamma = sigma * sigma * v(n) / zetap(n) + mu(n) * h(n - 1) / zetap(n);
        return new double[] { alpha, beta, gamma };
    }

    private double[] getCoeffLog(final int n) {
        final double muN = ((-kappa * theta - sigma * sigma / 2.0) * Math.exp(-v(n)) + kappa);
        final double alpha = sigma * sigma * Math.exp(-v(n)) / zetam(n) - muN * h(n) / zetam(n);
        final double beta = -sigma * sigma * Math.exp(-v(n)) / zeta(n) + muN * (h(n) - h(n - 1)) / zeta(n)
                + kappa * theta * Math.exp(-v(n));
        final double gamma = sigma * sigma * Math.exp(-v(n)) / zetap(n) + muN * h(n - 1) / zetap(n);
        return new double[] { alpha, beta, gamma };
    }

    private double[] getCoeffPower(final int n) {
        final double muN = kappa * (theta + v(n));
        final double alpha = (sigma * sigma * v(n) - muN * h(n)) / zetam(n);
        final double beta =
                (-sigma * sigma * v(n) + muN * (h(n) - h(n - 1))) / zeta(n) + 2 * kappa * kappa * theta / (sigma
                        * sigma);
        final double gamma = (sigma * sigma * v(n) + muN * h(n - 1)) / zetap(n);
        return new double[] { alpha, beta, gamma };
    }

    @Override
    public Array apply(final Array p) {
        return mapX.apply(p);
    }

    @Override
    public Array applyMixed(final Array r) {
        return new Array(r.size());
    }

    @Override
    public Array applyDirection(final int d, final Array r) {
        if ( d == direction ) {
            return mapX.apply(r);
        } else {
            return new Array(r.size());
        }
    }

    @Override
    public Array solveSplitting(final int d, final Array r, final double dt) {
        if ( d == direction ) {
            return mapX.solveSplitting(r, dt, 1.0);
        } else {
            return r;
        }
    }

    @Override
    public Array preconditioner(final Array r, final double dt) {
        return solveSplitting(direction, r, dt);
    }

    @Override
    public Matrix toMatrix() {
        return mapX.toMatrix();
    }

    @Override
    public List< Matrix > toMatrixDecomp() {
        final List< Matrix > ret = new ArrayList<>(1);
        ret.add(mapX.toMatrix());
        return Collections.unmodifiableList(ret);
    }

    /** Package-private accessor used by {@link FdmHestonFwdOp}. */
    ModTripleBandLinearOp mapX() {
        return mapX;
    }

    public enum TransformationType {Plain, Power, Log}
}
