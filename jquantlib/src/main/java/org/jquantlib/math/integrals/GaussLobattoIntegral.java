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

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;

/**
 * Adaptive Gauss-Lobatto integral with Kronrod refinement and a convergence-rate estimate.  Phase 2f WI-3 C.3
 * line-by-line port of {@code QuantLib::GaussLobattoIntegral} (v1.42.1
 * ql/math/integrals/gausslobattointegral.{hpp,cpp}).
 *
 * <p>Reference: W. Gander &amp; W. Gautschi, <em>Adaptive Quadrature
 * - Revisited</em>, BIT 40(1):84-101, March 2000.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>{@code maxIterations} maps to {@link Integrator#maxEvaluations()}.
 *       {@link Constants#NULL_REAL} sentinel (i.e. {@code Double.MAX_VALUE})
 *       maps to {@code Integer.MAX_VALUE} for the parent constructor —
 *       the original {@code Null<Size>()} idiom in C++.</li>
 *   <li>{@code relAccuracy} sentinel uses {@link Constants#NULL_REAL}
 *       (matching the rest of the JQuantLib codebase) instead of C++'s
 *       {@code Null<Real>()}; the test in
 *       {@link #calculateAbsTolerance(Ops.DoubleOp, double, double)} is
 *       {@code relAccuracy_ != Constants.NULL_REAL}, mirroring the C++
 *       check.</li>
 *   <li>The {@code dist == acc} 80-bit-x87 trick is preserved verbatim
 *       — Java's strict IEEE-754 64-bit semantics make it harmless but
 *       behavior-preserving.</li>
 * </ul>
 */
public class GaussLobattoIntegral extends Integrator {

    private static final double ALPHA = Math.sqrt(2.0 / 3.0);
    private static final double BETA = 1.0 / Math.sqrt(5.0);
    private static final double X1 = 0.94288241569547971906;
    private static final double X2 = 0.64185334234578130578;
    private static final double X3 = 0.23638319966214988028;

    private final double relAccuracy_;
    private final boolean useConvergenceEstimate_;

    public GaussLobattoIntegral(final int maxIterations, final double absAccuracy) {
        this(maxIterations, absAccuracy, Constants.NULL_REAL, true);
    }

    public GaussLobattoIntegral(final int maxIterations, final double absAccuracy, final double relAccuracy,
            final boolean useConvergenceEstimate) {
        super(absAccuracy, maxIterations);
        this.relAccuracy_ = relAccuracy;
        this.useConvergenceEstimate_ = useConvergenceEstimate;
    }

    @Override
    protected double integrate(final Ops.DoubleOp f, final double a, final double b) {
        setNumberOfEvaluations(0);
        final double calcAbsTolerance = calculateAbsTolerance(f, a, b);
        increaseNumberOfEvaluations(2);
        return adaptivGaussLobattoStep(f, a, b, f.op(a), f.op(b), calcAbsTolerance);
    }

    /**
     * Computes the absolute-tolerance estimate per Gander/Gautschi Eq. (3.1)-(3.3): an "exact" 13-point composite rule,
     * then a convergence-rate ratio between 4-point and 7-point Lobatto sub-rules.
     */
    protected double calculateAbsTolerance(final Ops.DoubleOp f, final double a, final double b) {
        final double relTol = Math.max(relAccuracy_, Constants.QL_EPSILON);

        final double m = (a + b) / 2;
        final double h = (b - a) / 2;
        final double y1 = f.op(a);
        final double y3 = f.op(m - ALPHA * h);
        final double y5 = f.op(m - BETA * h);
        final double y7 = f.op(m);
        final double y9 = f.op(m + BETA * h);
        final double y11 = f.op(m + ALPHA * h);
        final double y13 = f.op(b);

        final double f1 = f.op(m - X1 * h);
        final double f2 = f.op(m + X1 * h);
        final double f3 = f.op(m - X2 * h);
        final double f4 = f.op(m + X2 * h);
        final double f5 = f.op(m - X3 * h);
        final double f6 = f.op(m + X3 * h);

        double acc =
                h * (0.0158271919734801831 * (y1 + y13) + 0.0942738402188500455 * (f1 + f2) + 0.1550719873365853963 * (
                        y3 + y11) + 0.1888215739601824544 * (f3 + f4) + 0.1997734052268585268 * (y5 + y9)
                        + 0.2249264653333395270 * (f5 + f6) + 0.2426110719014077338 * y7);

        increaseNumberOfEvaluations(13);
        if ( acc == 0.0 && (f1 != 0.0 || f2 != 0.0 || f3 != 0.0 || f4 != 0.0 || f5 != 0.0 || f6 != 0.0) ) {
            QL.error("can not calculate absolute accuracy from relative accuracy");
        }

        double r = 1.0;
        if ( useConvergenceEstimate_ ) {
            final double integral2 = (h / 6) * (y1 + y13 + 5 * (y5 + y9));
            final double integral1 = (h / 1470) * (77 * (y1 + y13) + 432 * (y3 + y11) + 625 * (y5 + y9) + 672 * y7);

            if ( Math.abs(integral2 - acc) != 0.0 ) {
                r = Math.abs(integral1 - acc) / Math.abs(integral2 - acc);
            }
            if ( r == 0.0 || r > 1.0 ) {
                r = 1.0;
            }
        }

        if ( relAccuracy_ != Constants.NULL_REAL ) {
            return Math.min(absoluteAccuracy(), acc * relTol) / (r * Constants.QL_EPSILON);
        } else {
            return absoluteAccuracy() / (r * Constants.QL_EPSILON);
        }
    }

    /**
     * Recursive 4-vs-7-point adaptive Lobatto refinement step. Splits {@code [a,b]} at six interior abscissae if the
     * sub-rule estimates disagree by more than {@code acc} in 64-bit IEEE-754.
     */
    protected double adaptivGaussLobattoStep(final Ops.DoubleOp f, final double a, final double b, final double fa,
            final double fb, final double acc) {
        QL.require(numberOfEvaluations() < maxEvaluations(), "max number of iterations reached");

        final double h = (b - a) / 2;
        final double m = (a + b) / 2;

        final double mll = m - ALPHA * h;
        final double ml = m - BETA * h;
        final double mr = m + BETA * h;
        final double mrr = m + ALPHA * h;

        final double fmll = f.op(mll);
        final double fml = f.op(ml);
        final double fm = f.op(m);
        final double fmr = f.op(mr);
        final double fmrr = f.op(mrr);
        increaseNumberOfEvaluations(5);

        final double integral2 = (h / 6) * (fa + fb + 5 * (fml + fmr));
        final double integral1 = (h / 1470) * (77 * (fa + fb) + 432 * (fmll + fmrr) + 625 * (fml + fmr) + 672 * fm);

        // avoid 80-bit logic on x86 cpu — preserved from C++ even though
        // Java IEEE-754 strictly disallows wider intermediate precision.
        final double dist = acc + (integral1 - integral2);
        if ( dist == acc || mll <= a || b <= mrr ) {
            QL.require(m > a && b > m, "Interval contains no more machine number");
            return integral1;
        } else {
            return adaptivGaussLobattoStep(f, a, mll, fa, fmll, acc) + adaptivGaussLobattoStep(f, mll, ml, fmll, fml,
                    acc) + adaptivGaussLobattoStep(f, ml, m, fml, fm, acc) + adaptivGaussLobattoStep(f, m, mr, fm, fmr,
                    acc) + adaptivGaussLobattoStep(f, mr, mrr, fmr, fmrr, acc) + adaptivGaussLobattoStep(f, mrr, b,
                    fmrr, fb, acc);
        }
    }
}
