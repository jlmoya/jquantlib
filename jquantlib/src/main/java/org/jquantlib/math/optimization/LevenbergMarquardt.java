/*
 Copyright (C) 2009 Ueli Hofstetter
 Copyright (C) 2026 JQuantLib migration contributors

 This source code is released under the BSD License.

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
package org.jquantlib.math.optimization;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria.Type;

/**
 * Levenberg-Marquardt optimization method. Port of QuantLib v1.42.1
 * {@code QuantLib::LevenbergMarquardt}
 * (ql/math/optimization/levenbergmarquardt.{hpp,cpp}); delegates the
 * inner loop to {@link Minpack#lmdif}.
 * <p>
 * The analytic-Jacobian branch of the C++ original is not ported: the
 * Java {@link CostFunction#jacobian} uses a central-difference scheme
 * which is incompatible with the forward-difference contract MINPACK
 * expects, and the known callers of this class (Interpolation-LM,
 * SABR, OptimizerTest) all rely on the internal forward-difference
 * Jacobian. The flag can be revisited in a future pass if analytic
 * Jacobians become useful.
 *
 * @see Minpack#lmdif
 */
public class LevenbergMarquardt extends OptimizationMethod {

    private final double epsfcn_;
    private final double xtol_;
    private final double gtol_;
    private Integer info_;

    private Problem currentProblem_;
    private Array initCostValues_;

    public LevenbergMarquardt() {
        this(1.0e-8, 1.0e-8, 1.0e-8);
    }

    public LevenbergMarquardt(final double epsfcn, final double xtol, final double gtol) {
        this.epsfcn_ = epsfcn;
        this.xtol_ = xtol;
        this.gtol_ = gtol;
    }

    /** Last {@code info} value returned by MINPACK::lmdif (1-8). */
    public int getInfo() {
        return info_ == null ? 0 : info_;
    }

    @Override
    public Type minimize(final Problem P, final EndCriteria endCriteria) {
        P.reset();
        final Array initX = P.currentValue();
        currentProblem_ = P;
        initCostValues_ = P.costFunction().values(initX);

        final int m = initCostValues_.size();
        final int n = initX.size();

        final double[] xx = new double[n];
        for (int i = 0; i < n; i++) {
            xx[i] = initX.get(i);
        }

        final double[] fvec = new double[m];
        final double[] diag = new double[n];
        final int mode = 1;
        // factor=100 is the documentation-recommended value, matching
        // levenbergmarquardt.cpp.
        final double factor = 100.0;
        // lmdif evaluates the cost function n+1 times per "iteration"
        // in the MINPACK sense, so expand maxfev accordingly.
        final int maxfev = endCriteria.maxIterations_ * (n + 1);
        final int nprint = 0;
        final int[] info = { 0 };
        final int[] nfev = { 0 };
        final double[] fjac = new double[m * n];
        final int ldfjac = m;
        final int[] ipvt = new int[n];
        final double[] qtf = new double[n];
        final double[] wa1 = new double[n];
        final double[] wa2 = new double[n];
        final double[] wa3 = new double[n];
        final double[] wa4 = new double[m];

        QL.require(n > 0, "no variables given");
        QL.require(m >= n, "less functions (" + m
                + ") than available variables (" + n + ")");
        QL.require(endCriteria.functionEpsilon_ >= 0.0,
                "negative f tolerance");
        QL.require(xtol_ >= 0.0, "negative x tolerance");
        QL.require(gtol_ >= 0.0, "negative g tolerance");
        QL.require(maxfev > 0, "null number of evaluations");

        final Minpack.LmdifCostFunction lmdifCostFunction =
                (mm, nn, x, fv, iflag) -> this.fcn(mm, nn, x, fv);

        Minpack.lmdif(m, n, xx, fvec,
                endCriteria.functionEpsilon_, xtol_, gtol_, maxfev, epsfcn_,
                diag, mode, factor, nprint, info, nfev, fjac, ldfjac,
                ipvt, qtf, wa1, wa2, wa3, wa4, lmdifCostFunction, null);

        info_ = info[0];

        // check requirements & endCriteria evaluation
        QL.require(info[0] != 0, "MINPACK: improper input parameters");
        QL.require(info[0] != 7,
                "MINPACK: xtol is too small. no further improvement in the "
                        + "approximate solution x is possible.");
        QL.require(info[0] != 8,
                "MINPACK: gtol is too small. fvec is orthogonal to the "
                        + "columns of the jacobian to machine precision.");

        Type ecType;
        switch (info[0]) {
            case 1:
            case 2:
            case 3:
            case 4:
                // 2 and 3 would more precisely map to StationaryPoint, 4 to
                // a gradient-related code, but levenbergmarquardt.cpp keeps
                // StationaryFunctionValue for backwards compatibility.
                ecType = Type.StationaryFunctionValue;
                break;
            case 5:
                ecType = Type.MaxIterations;
                break;
            case 6:
                ecType = Type.FunctionEpsilonTooSmall;
                break;
            default:
                throw new IllegalStateException(
                        "unknown MINPACK info result: " + info[0]);
        }

        final Array finalX = new Array(xx);
        P.setCurrentValue(finalX);
        P.setFunctionValue(P.costFunction().value(finalX));
        return ecType;
    }

    /**
     * Residual evaluation callback invoked by MINPACK::lmdif. Returns the
     * problem's cost vector at {@code x}, or a 1e10 penalty vector when
     * the constraint is violated or the evaluation produces non-finite
     * values. Mirrors {@code LevenbergMarquardt::fcn} in
     * levenbergmarquardt.cpp.
     */
    private void fcn(final int m, final int n, final double[] x, final double[] fvec) {
        final Array xt = new Array(n);
        for (int i = 0; i < n; i++) {
            xt.set(i, x[i]);
        }
        if (currentProblem_.constraint().test(xt)) {
            final Array tmp = currentProblem_.values(xt);
            boolean valid = true;
            for (int i = 0; i < tmp.size(); i++) {
                if (!Double.isFinite(tmp.get(i))) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                for (int i = 0; i < tmp.size(); i++) {
                    fvec[i] = tmp.get(i);
                }
                return;
            }
        }
        // Constraint violated or evaluation produced non-finite values.
        // Return a uniform large penalty so the optimizer steers away.
        final int len = initCostValues_.size();
        for (int i = 0; i < len; i++) {
            fvec[i] = 1.0e10;
        }
    }
}
