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
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.integrals;

import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;

/**
 * Tanh-sinh (double exponential) quadrature for holomorphic integrands.
 *
 * <p>Phase 1 closure A1 port of {@code QuantLib::TanhSinhIntegral}
 * (v1.42.1 ql/math/integrals/tanhsinhintegral.hpp; pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p><b>Background.</b> The C++ implementation is a thin wrapper around the
 * Boost {@code boost::math::quadrature::tanh_sinh<Real>} routine: the actual
 * algorithm lives in Boost, not in QuantLib. Since no equivalent Boost
 * library exists for Java, this class implements the tanh-sinh transform
 * directly. The mathematical core is the Takahasi-Mori (1974) double
 * exponential change of variables
 * <pre>
 *   x(t) = tanh( (π/2) sinh(t) )
 *   w(t) = (π/2) cosh(t) / cosh^2( (π/2) sinh(t) )
 * </pre>
 * over the canonical {@code [-1, 1]} domain. For a general {@code [a,b]}
 * integral the standard linear change of variables to {@code [-1, 1]} is
 * applied first.
 *
 * <p><b>Refinement strategy.</b> The quadrature uses a sequence of grids
 * with step size {@code h = h0 / 2^k} for {@code k = 0,…,maxRefinements}.
 * Each refinement halves {@code h} and adds the new odd-indexed nodes,
 * doubling the cost but quartering the discretisation error (the
 * double-exponential decay is exponential in {@code 1/h}). Convergence is
 * declared when the difference between two successive estimates falls
 * below {@code relTolerance * |estimate|}.
 *
 * <p><b>Constructor parameters</b> mirror Boost's:
 * <ul>
 *   <li>{@code relTolerance} — relative tolerance against the L1 error
 *       estimate; default is {@code sqrt(eps)}.</li>
 *   <li>{@code maxRefinements} — maximum number of grid-halving refinements
 *       (default 15).</li>
 *   <li>{@code minComplement} — smallest representable complement
 *       {@code 1 - |x(t)|} used to clip {@code t} to a finite range; default
 *       {@code 4 * Double.MIN_NORMAL}.</li>
 * </ul>
 *
 * <p><b>Behaviour-equivalence note.</b> Bit-exact agreement with Boost is
 * not expected — the tanh-sinh transform is provably convergent for any
 * reasonable choice of grid and abscissae, and both implementations are
 * adaptive. Convergence to within {@code relTolerance} is the contract.
 *
 * <p>References:
 * <ul>
 *   <li>H. Takahasi, M. Mori, "Double Exponential Formulas for Numerical
 *       Integration", Publ. RIMS, Kyoto Univ. 9 (1974), 721-741.</li>
 *   <li>D. H. Bailey, K. Jeyabalan, X. S. Li, "A Comparison of Three High-
 *       Precision Quadrature Schemes", Experimental Math. 14 (2005), 317.</li>
 * </ul>
 */
public class TanhSinhIntegral extends Integrator {

    /** Default {@code relTolerance = sqrt(QL_EPSILON)} mirrors C++ default. */
    public static final double DEFAULT_REL_TOLERANCE = Math.sqrt(Constants.QL_EPSILON);

    /** Default {@code maxRefinements = 15} mirrors C++ default. */
    public static final int DEFAULT_MAX_REFINEMENTS = 15;

    /** Default {@code minComplement = 4 * Double.MIN_NORMAL} mirrors C++ default. */
    public static final double DEFAULT_MIN_COMPLEMENT = 4.0 * Double.MIN_NORMAL;

    private final double relTolerance_;
    private final int maxRefinements_;
    private final double minComplement_;
    private final double tMax_;

    public TanhSinhIntegral() {
        this(DEFAULT_REL_TOLERANCE, DEFAULT_MAX_REFINEMENTS, DEFAULT_MIN_COMPLEMENT);
    }

    public TanhSinhIntegral(final double relTolerance) {
        this(relTolerance, DEFAULT_MAX_REFINEMENTS, DEFAULT_MIN_COMPLEMENT);
    }

    public TanhSinhIntegral(final double relTolerance, final int maxRefinements) {
        this(relTolerance, maxRefinements, DEFAULT_MIN_COMPLEMENT);
    }

    public TanhSinhIntegral(final double relTolerance, final int maxRefinements, final double minComplement) {
        // C++: Integrator(QL_MAX_REAL, Null<Size>())
        super(Constants.QL_MAX_REAL, Integer.MAX_VALUE);
        this.relTolerance_ = relTolerance;
        this.maxRefinements_ = maxRefinements;
        this.minComplement_ = minComplement;

        // tMax is the value of t at which 1 - tanh((π/2) sinh(t)) drops below
        // minComplement. Solving:
        //   1 - tanh(u) ≈ 2 e^{-2u}      (large u)
        //   ⇒ u = -0.5 ln(minComplement/2) = 0.5 ln(2/minComplement)
        //   ⇒ (π/2) sinh(tMax) = u
        //   ⇒ tMax = asinh( (2/π) u )
        final double u = 0.5 * Math.log(2.0 / minComplement);
        this.tMax_ = arcsinh((2.0 / Math.PI) * u);
    }

    /**
     * Inverse hyperbolic sine, {@code asinh(x) = ln(x + sqrt(x^2 + 1))}.
     * Provided locally because Java's {@link Math} does not expose it.
     */
    private static double arcsinh(final double x) {
        return Math.log(x + Math.sqrt(x * x + 1.0));
    }

    /**
     * Compute the tanh-sinh node {@code x(t) = tanh((π/2) sinh(t))} and weight
     * {@code w(t) = (π/2) cosh(t) / cosh^2((π/2) sinh(t))}.
     */
    private static double[] nodeWeight(final double t) {
        final double piHalf = 0.5 * Math.PI;
        final double sinhT = Math.sinh(t);
        final double coshT = Math.cosh(t);
        final double arg = piHalf * sinhT;
        final double x = Math.tanh(arg);
        final double coshArg = Math.cosh(arg);
        final double w = piHalf * coshT / (coshArg * coshArg);
        return new double[] { x, w };
    }

    @Override
    protected double integrate(final Ops.DoubleOp f, final double a, final double b) {
        setNumberOfEvaluations(0);

        // Linear change of variables [a,b] → [-1,1]: u = c1*t + c2
        final double c1 = 0.5 * (b - a);
        final double c2 = 0.5 * (a + b);

        // Refinement level 0: step h0 = 1. Grid is t = 0, ±1, ±2, …, ±jMax0
        // (the integer-multiples of h0 inside [-tMax, tMax]).
        double h = 1.0;
        // Center node contributes f(c2) with weight π/2.
        double sum = 0.5 * Math.PI * f.op(c2);
        increaseNumberOfEvaluations(1);
        // Add the remaining integer-spaced nodes for level 0 (both ±t).
        // These are j = 1, 2, …, jMax0 — and at subsequent refinement passes
        // they correspond to even-j nodes (j_k = 2^k · j_0) of the finer grid.
        final int jMax0 = (int) Math.ceil(tMax_ / h);
        for (int j = 1; j <= jMax0; ++j) {
            final double t = j * h;
            final double[] xw = nodeWeight(t);
            final double x = xw[0];
            final double w = xw[1];
            final double up = c1 * x + c2;
            final double um = -c1 * x + c2;
            sum += w * (f.op(up) + f.op(um));
            increaseNumberOfEvaluations(2);
        }

        double prevEstimate = sum * h * c1;

        // Refinement loop: each pass halves h and adds new odd-multiple nodes.
        // Pass k uses step h_k = 1/2^k and node positions t = j * h_k for odd
        // j ∈ [1, tMax/h_k]. Even-j nodes were accumulated by previous passes.
        for (int k = 1; k <= maxRefinements_; ++k) {
            h *= 0.5;
            double newSum = 0.0;
            final int jMax = (int) Math.ceil(tMax_ / h);
            for (int j = 1; j <= jMax; j += 2) {
                final double t = j * h;
                final double[] xw = nodeWeight(t);
                final double x = xw[0];
                final double w = xw[1];
                // Symmetric pair: t and -t give x and -x, same weight.
                final double up = c1 * x + c2;
                final double um = -c1 * x + c2;
                newSum += w * (f.op(up) + f.op(um));
                increaseNumberOfEvaluations(2);
            }
            // Accumulate: the new estimate is (h * (sum + newSum)) * c1.
            sum += newSum;
            final double estimate = sum * h * c1;
            final double diff = Math.abs(estimate - prevEstimate);
            final double absEst = Math.abs(estimate);

            // Convergence: diff < relTol * |estimate|, with a floor of relTol
            // itself to handle near-zero results.
            setAbsoluteError(diff);
            if (diff <= relTolerance_ * Math.max(absEst, 1.0)) {
                return estimate;
            }
            prevEstimate = estimate;
        }

        // Reached maxRefinements: return best estimate; error already recorded.
        return prevEstimate;
    }
}
