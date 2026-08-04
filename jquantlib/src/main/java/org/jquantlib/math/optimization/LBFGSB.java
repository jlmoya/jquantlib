/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.math.optimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Limited-memory BFGS with box constraints (L-BFGS-B).
 * <p>
 * Faithful implementation of Byrd, Lu, Nocedal and Zhu, "A Limited Memory Algorithm for Bound Constrained
 * Optimization", SIAM J. Sci. Comput. 16(5):1190-1208, 1995.
 * <p>
 * Coordinates whose bound is &plusmn;{@code Double.MAX_VALUE} are treated as unbounded, so with no active bounds the
 * method reduces to plain limited-memory BFGS. Explicit bounds may instead be passed to the constructor, in which case
 * they <em>override</em> — not intersect with — the problem's constraint.
 * <p>
 * Limited memory trades accuracy of the Hessian model for cost: only the last {@code memory} correction pairs are
 * stored, needing O(memory&middot;n) storage and work per step instead of the O(n&sup2;) of dense BFGS, at the price of
 * linear rather than superlinear convergence.
 * <p>
 * Ported from C++ QuantLib v1.43 {@code ql/math/optimization/lbfgsb.{hpp,cpp}} — new in that release.
 *
 * @author Jose Moya
 * @category optimizers
 */
public class LBFGSB extends OptimizationMethod {

    private static final double INF = Constants.QL_MAX_REAL;

    private final int m;
    private final double pgTol;
    private final double factr;
    private final Array lowerBound;
    private final Array upperBound;

    //
    // public constructors
    //

    public LBFGSB() {
        this(10, 1.0e-8, 1.0e7 * Constants.QL_EPSILON);
    }

    /**
     * @param memory number of stored correction pairs
     * @param pgTol  convergence tolerance on the infinity norm of the projected gradient
     * @param fTol   the iteration stops when the relative reduction of the objective falls below {@code fTol}
     */
    public LBFGSB(final int memory, final double pgTol, final double fTol) {
        QL.require(memory > 0, "memory must be positive");
        this.m = memory;
        this.pgTol = pgTol;
        // Mirrors the C++ divide-then-multiply round trip: factr_ = fTol / QL_EPSILON, and the stop test later
        // multiplies by QL_EPSILON again. Collapsing the two changes the result in the last bits.
        this.factr = fTol / Constants.QL_EPSILON;
        this.lowerBound = new Array(0);
        this.upperBound = new Array(0);
    }

    /**
     * Convenience constructor taking explicit box bounds. These override the bounds of the problem's constraint — they
     * are not intersected with them. Use &plusmn;{@code Double.MAX_VALUE} for unbounded coordinates.
     */
    public LBFGSB(final Array lowerBound, final Array upperBound) {
        this(lowerBound, upperBound, 10, 1.0e-8, 1.0e7 * Constants.QL_EPSILON);
    }

    public LBFGSB(final Array lowerBound, final Array upperBound, final int memory, final double pgTol,
            final double fTol) {
        QL.require(memory > 0, "memory must be positive");
        QL.require(lowerBound.size() == upperBound.size(), "lower and upper bound sizes are inconsistent");
        this.m = memory;
        this.pgTol = pgTol;
        this.factr = fTol / Constants.QL_EPSILON;
        this.lowerBound = lowerBound.clone();
        this.upperBound = upperBound.clone();
    }

    //
    // private static helpers
    //

    /**
     * A bound is "absent" when it equals &plusmn;{@code DBL_MAX} — the value returned by the default {@code Constraint}
     * and by {@code NoConstraint}. The 0.5 factor guards against overflow in subsequent arithmetic such as
     * {@code x - bound}.
     */
    private static boolean noUpper(final double u) {
        return u >= 0.5 * INF;
    }

    private static boolean noLower(final double l) {
        return l <= -0.5 * INF;
    }

    private static double norm2(final Array v) {
        return Math.sqrt(v.dotProduct(v));
    }

    /**
     * Compact limited-memory representation of the BFGS Hessian approximation {@code B = theta I - W M W^T} (Byrd, Lu,
     * Nocedal &amp; Zhu 1995, eq. 3.5), built from the stored correction pairs.
     */
    private static final class CompactRep {
        Matrix w; // n x 2col, [ Y | theta S ]
        Matrix mInv; // 2col x 2col, inverse of the middle matrix
        double theta = 1.0;
        int col = 0;
    }

    private static CompactRep buildCompactRep(final List< Array > s, final List< Array > y, final double theta) {
        final CompactRep rep = new CompactRep();
        rep.theta = theta;
        rep.col = s.size();
        final int col = rep.col;

        if ( col == 0 ) {
            return rep;
        }

        final int n = s.get(0).size();
        final Matrix w = new Matrix(n, 2 * col);

        for ( int j = 0; j < col; ++j ) {
            for ( int i = 0; i < n; ++i ) {
                w.set(i, j, y.get(j).get(i));
                w.set(i, col + j, theta * s.get(j).get(i));
            }
        }

        // small Gram matrices S^T S and S^T Y
        final Matrix sts = new Matrix(col, col);
        final Matrix sty = new Matrix(col, col);

        for ( int i = 0; i < col; ++i ) {
            for ( int j = 0; j < col; ++j ) {
                sts.set(i, j, s.get(i).dotProduct(s.get(j)));
                sty.set(i, j, s.get(i).dotProduct(y.get(j)));
            }
        }

        // middle matrix [ -D  L^T ; L  theta S^T S ] with D = diag(s_i.y_i) and L the strictly lower part of S^T Y.
        final Matrix mid = new Matrix(2 * col, 2 * col);
        mid.fill(0.0);

        for ( int i = 0; i < col; ++i ) {
            mid.set(i, i, -sty.get(i, i)); // -D

            for ( int j = 0; j < col; ++j ) {
                if ( i > j ) {
                    mid.set(col + i, j, sty.get(i, j)); // L
                }
                if ( j > i ) {
                    mid.set(i, col + j, sty.get(j, i)); // L^T
                }
                mid.set(col + i, col + j, theta * sts.get(i, j));
            }
        }

        rep.w = w;
        rep.mInv = mid.inverse();
        return rep;
    }

    private static Array wRow(final Matrix w, final int i) {
        final Array r = new Array(w.columns());
        for ( int k = 0; k < r.size(); ++k ) {
            r.set(k, w.get(i, k));
        }
        return r;
    }

    /**
     * Result of the generalized-Cauchy-point walk: the Cauchy point itself, the accumulated {@code c = W^T (xcp - x)}
     * needed by the subspace step, and the set of variables left free (not pinned at a bound).
     */
    private static final class CauchyPoint {
        Array xcp;
        Array c;
        boolean[] isFree;
    }

    /**
     * Generalized Cauchy point (Byrd et al. 1995, Algorithm CP). Walks the breakpoints of the projected
     * steepest-descent path {@code x(t) = P(x - t g, l, u)} and locates the first local minimizer of the quadratic
     * model along it.
     */
    private static CauchyPoint generalizedCauchyPoint(final Array x, final Array g, final Array lo, final Array hi,
            final CompactRep rep) {
        final int n = x.size();
        final int m2 = 2 * rep.col;

        final CauchyPoint out = new CauchyPoint();
        out.xcp = x.clone();
        out.isFree = new boolean[n];
        java.util.Arrays.fill(out.isFree, true);

        final double[] t = new double[n];
        final Array d = new Array(n);
        d.fill(0.0);
        final List< Integer > brk = new ArrayList<>(); // indices with a strictly positive breakpoint

        for ( int i = 0; i < n; ++i ) {
            final double ti;
            if ( g.get(i) < 0.0 ) {
                ti = noUpper(hi.get(i)) ? INF : (x.get(i) - hi.get(i)) / g.get(i);
            } else if ( g.get(i) > 0.0 ) {
                ti = noLower(lo.get(i)) ? INF : (x.get(i) - lo.get(i)) / g.get(i);
            } else {
                ti = INF;
            }
            t[i] = ti;

            if ( ti <= 0.0 ) {
                // already sitting on the bound the gradient pushes into
                d.set(i, 0.0);
                out.isFree[i] = false;
            } else {
                d.set(i, -g.get(i));
                brk.add(i);
            }
        }

        Array c = new Array(m2);
        c.fill(0.0);
        if ( brk.isEmpty() ) { // every variable is pinned
            out.c = c;
            return out;
        }

        brk.sort((a, b) -> Double.compare(t[a], t[b]));

        Array p = rep.col > 0 ? d.mul(rep.w) : new Array(0);

        double fp = -d.dotProduct(d); // first derivative of the model, m'(0)
        double fpp = -rep.theta * fp; // second derivative, theta d^T d - ...

        if ( rep.col > 0 ) {
            fpp -= p.dotProduct(rep.mInv.mul(p));
        }
        final double fppFloor = Constants.QL_EPSILON * (fpp > 0.0 ? fpp : 1.0);

        double dtMin = fpp > 0.0 ? -fp / fpp : 0.0;

        double tOld = 0.0;
        int ptr = 0;
        int b = brk.get(ptr);
        double tb = t[b];
        double dt = tb;
        boolean exhausted = false;

        while ( dtMin >= dt && tb < INF ) {
            // pin variable b at the bound it has reached
            final double xcpb = d.get(b) > 0.0 ? hi.get(b) : (d.get(b) < 0.0 ? lo.get(b) : x.get(b));
            out.xcp.set(b, xcpb);
            out.isFree[b] = false;
            final double zb = xcpb - x.get(b);
            final double gb = g.get(b);

            if ( rep.col > 0 ) {
                c = c.add(p.mul(dt));

                final Array wb = wRow(rep.w, b);
                final Array mc = rep.mInv.mul(c);
                final Array mp = rep.mInv.mul(p);
                final Array mwb = rep.mInv.mul(wb);

                fp += dt * fpp + gb * gb + rep.theta * gb * zb - gb * wb.dotProduct(mc);
                fpp += -rep.theta * gb * gb - 2.0 * gb * wb.dotProduct(mp) - gb * gb * wb.dotProduct(mwb);
                p = p.add(wb.mul(gb));
            } else {
                fp += dt * fpp + gb * gb + rep.theta * gb * zb;
                fpp += -rep.theta * gb * gb;
            }

            if ( fpp < fppFloor ) {
                fpp = fppFloor;
            }

            d.set(b, 0.0);
            dtMin = fpp > 0.0 ? -fp / fpp : 0.0;
            tOld = tb;

            if ( ++ptr >= brk.size() ) {
                exhausted = true;
                break;
            }

            b = brk.get(ptr);
            tb = t[b];
            dt = tb - tOld;
        }

        // advance the still-free variables along the final segment
        if ( exhausted ) {
            dtMin = 0.0;
        }
        dtMin = Math.max(dtMin, 0.0);
        tOld += dtMin;

        for ( int i = 0; i < n; ++i ) {
            if ( out.isFree[i] ) {
                out.xcp.set(i, x.get(i) + tOld * d.get(i));
            }
        }

        if ( rep.col > 0 ) {
            c = c.add(p.mul(dtMin));
        }
        out.c = c;
        return out;
    }

    /**
     * Subspace minimization by the direct primal method (Byrd et al. 1995, section 5.1). Minimizes the quadratic model
     * over the free variables, holding the active ones at their Cauchy-point bound, then truncates the step back into
     * the box.
     */
    private static Array subspaceMinimization(final Array x, final Array g, final Array xcp, final Array c,
            final Array lo, final Array hi, final CompactRep rep, final boolean[] isFree) {
        final int n = x.size();
        final List< Integer > freeIdx = new ArrayList<>();
        for ( int i = 0; i < n; ++i ) {
            if ( isFree[i] ) {
                freeIdx.add(i);
            }
        }
        final int nf = freeIdx.size();

        final Array xbar = xcp.clone();
        if ( nf == 0 ) {
            return xbar;
        }

        final double theta = rep.theta;
        final int m2 = 2 * rep.col;

        // reduced gradient of the model at the Cauchy point: r = [ g + theta (xcp - x) - W M c ] over free vars
        Array wmc = new Array(n);
        wmc.fill(0.0);
        if ( rep.col > 0 ) {
            wmc = rep.w.mul(rep.mInv.mul(c));
        }

        final Array r = new Array(nf);
        for ( int k = 0; k < nf; ++k ) {
            final int i = freeIdx.get(k);
            r.set(k, g.get(i) + theta * (xcp.get(i) - x.get(i)) - wmc.get(i));
        }

        final Array dhat = new Array(nf);
        if ( rep.col == 0 ) {
            for ( int k = 0; k < nf; ++k ) {
                dhat.set(k, -r.get(k) / theta);
            }
        } else {
            // v = M (W_free^T r)
            final Array wtr = new Array(m2);
            wtr.fill(0.0);
            for ( int k = 0; k < nf; ++k ) {
                final int i = freeIdx.get(k);
                for ( int j = 0; j < m2; ++j ) {
                    wtr.set(j, wtr.get(j) + rep.w.get(i, j) * r.get(k));
                }
            }

            Array v = rep.mInv.mul(wtr);

            // N = I - (1/theta) M (W_free^T W_free)
            final Matrix wftwf = new Matrix(m2, m2);
            wftwf.fill(0.0);
            for ( int k = 0; k < nf; ++k ) {
                final int i = freeIdx.get(k);
                for ( int a = 0; a < m2; ++a ) {
                    for ( int bb = 0; bb < m2; ++bb ) {
                        wftwf.set(a, bb, wftwf.get(a, bb) + rep.w.get(i, a) * rep.w.get(i, bb));
                    }
                }
            }

            final Matrix nMat = rep.mInv.mul(wftwf).mulAssign(-1.0 / theta);
            for ( int a = 0; a < m2; ++a ) {
                nMat.set(a, a, nMat.get(a, a) + 1.0);
            }
            v = nMat.inverse().mul(v);

            // dhat = -(1/theta) r - (1/theta^2) W_free v
            for ( int k = 0; k < nf; ++k ) {
                final int i = freeIdx.get(k);
                double wfv = 0.0;
                for ( int j = 0; j < m2; ++j ) {
                    wfv += rep.w.get(i, j) * v.get(j);
                }
                dhat.set(k, -r.get(k) / theta - wfv / (theta * theta));
            }
        }

        // truncate so that every free variable stays inside the box
        double alphaStar = 1.0;
        for ( int k = 0; k < nf; ++k ) {
            final int i = freeIdx.get(k);
            if ( dhat.get(k) > 0.0 && !noUpper(hi.get(i)) ) {
                alphaStar = Math.min(alphaStar, (hi.get(i) - xcp.get(i)) / dhat.get(k));
            } else if ( dhat.get(k) < 0.0 && !noLower(lo.get(i)) ) {
                alphaStar = Math.min(alphaStar, (lo.get(i) - xcp.get(i)) / dhat.get(k));
            }
        }
        alphaStar = Math.max(alphaStar, 0.0);

        for ( int k = 0; k < nf; ++k ) {
            final int i = freeIdx.get(k);
            xbar.set(i, xcp.get(i) + alphaStar * dhat.get(k));
        }
        return xbar;
    }

    /**
     * Largest feasible step length along {@code d} starting from {@code x}.
     */
    private static double maxFeasibleStep(final Array x, final Array d, final Array lo, final Array hi) {
        double stp = INF;
        for ( int i = 0; i < x.size(); ++i ) {
            if ( d.get(i) > 0.0 && !noUpper(hi.get(i)) ) {
                stp = Math.min(stp, (hi.get(i) - x.get(i)) / d.get(i));
            } else if ( d.get(i) < 0.0 && !noLower(lo.get(i)) ) {
                stp = Math.min(stp, (lo.get(i) - x.get(i)) / d.get(i));
            }
        }
        return stp;
    }

    /**
     * Mutable carrier for the line search's outputs — Java has no out-parameters.
     */
    private static final class LineSearchResult {
        double alpha;
        Array xt;
        double ft;
        Array gt;
    }

    /**
     * Line search enforcing the strong Wolfe conditions (Nocedal &amp; Wright, Algorithms 3.5/3.6), capped at the
     * largest feasible step. Returns {@code null} when no acceptable step could be found at all.
     */
    private static LineSearchResult lineSearchWolfe(final Problem p, final Array x, final Array d, final double f0,
            final Array g0, final double stpMax) {
        final double c1 = 1.0e-4;
        final double c2 = 0.9;
        final int maxIter = 30;

        final double dphi0 = g0.dotProduct(d);
        if ( dphi0 >= 0.0 ) {
            return null; // not a descent direction
        }

        final LineSearchResult res = new LineSearchResult();
        res.xt = new Array(x.size());
        res.gt = new Array(x.size());

        // best sufficient decrease seen so far, used if strong Wolfe is never reached
        final double[] best = { 0.0, f0 }; // { alpha, f }
        final Array[] bestPoint = { null, null }; // { x, g }

        // evaluates phi(a) and phi'(a), tracking the best decrease seen
        final java.util.function.DoubleUnaryOperator eval = a -> {
            res.xt = x.add(d.mul(a));
            final Array gt = new Array(x.size());
            res.ft = p.valueAndGradient(gt, res.xt);
            res.gt = gt;

            if ( res.ft < best[1] ) {
                best[0] = a;
                best[1] = res.ft;
                bestPoint[0] = res.xt.clone();
                bestPoint[1] = gt.clone();
            }
            return gt.dotProduct(d);
        };

        double aLo = 0.0;
        double aHi = 0.0;
        double fLo = f0;
        boolean bracketed = false;

        double aPrev = 0.0;
        double fPrev = f0;
        double a = Math.min(1.0, stpMax);

        for ( int i = 0; i < maxIter; ++i ) {
            final double dphi = eval.applyAsDouble(a);

            if ( res.ft > f0 + c1 * a * dphi0 || (i > 0 && res.ft >= fPrev) ) {
                aLo = aPrev;
                fLo = fPrev;
                aHi = a;
                bracketed = true;
                break;
            }

            if ( Math.abs(dphi) <= -c2 * dphi0 ) {
                res.alpha = a;
                return res; // strong Wolfe satisfied
            }

            if ( dphi >= 0.0 ) {
                aLo = a;
                fLo = res.ft;
                aHi = aPrev;
                bracketed = true;
                break;
            }

            aPrev = a;
            fPrev = res.ft;

            if ( a >= stpMax ) {
                break; // cannot expand further
            }
            a = Math.min(2.0 * a, stpMax);
        }

        if ( bracketed ) {
            for ( int j = 0; j < maxIter; ++j ) {
                a = 0.5 * (aLo + aHi); // bisection
                final double dphi = eval.applyAsDouble(a);

                if ( res.ft > f0 + c1 * a * dphi0 || res.ft >= fLo ) {
                    aHi = a;
                } else {
                    if ( Math.abs(dphi) <= -c2 * dphi0 ) {
                        res.alpha = a;
                        return res;
                    }
                    if ( dphi * (aHi - aLo) >= 0.0 ) {
                        aHi = aLo;
                    }
                    aLo = a;
                    fLo = res.ft;
                }

                if ( Math.abs(aHi - aLo) < Constants.QL_EPSILON * Math.max(1.0, Math.abs(a)) ) {
                    break;
                }
            }
        }

        // strong Wolfe not reached: accept the best sufficient decrease
        if ( bestPoint[0] != null ) {
            res.alpha = best[0];
            res.xt = bestPoint[0];
            res.ft = best[1];
            res.gt = bestPoint[1];
            return res;
        }
        return null;
    }

    //
    // implements OptimizationMethod
    //

    @Override
    public EndCriteria.Type minimize(final Problem p, final EndCriteria endCriteria) {
        final EndCriteria.Type[] ecType = { EndCriteria.Type.None };
        p.reset();

        Array x = p.currentValue().clone();
        final int n = x.size();

        final Array lo = lowerBound.empty() ? p.constraint().lowerBound(x) : lowerBound;
        final Array hi = upperBound.empty() ? p.constraint().upperBound(x) : upperBound;

        QL.require(lo.size() == n && hi.size() == n, "bounds size does not match the number of variables");

        // start from a feasible point
        for ( int i = 0; i < n; ++i ) {
            x.set(i, Math.min(Math.max(x.get(i), lo.get(i)), hi.get(i)));
        }

        Array g = new Array(n);
        double f = p.valueAndGradient(g, x);
        p.setCurrentValue(x);
        p.setFunctionValue(f);

        final Deque< Array > s = new ArrayDeque<>();
        final Deque< Array > y = new ArrayDeque<>();
        double theta = 1.0;
        int iter = 0;
        double pgInf = 0.0; // projected gradient infinity norm; persists for final reporting

        while ( true ) {
            pgInf = 0.0;
            for ( int i = 0; i < n; ++i ) {
                final double proj = Math.min(Math.max(x.get(i) - g.get(i), lo.get(i)), hi.get(i)) - x.get(i);
                pgInf = Math.max(pgInf, Math.abs(proj));
            }

            p.setGradientNormValue(pgInf * pgInf);

            if ( pgInf < pgTol ) {
                ecType[0] = EndCriteria.Type.ZeroGradientNorm;
                break;
            }

            // note: EndCriteria receives pgInf, not pgInf squared
            if ( endCriteria.checkZeroGradientNorm(pgInf, ecType) ) {
                break;
            }

            if ( endCriteria.checkMaxIterations(iter, ecType) ) {
                break;
            }

            // compact representation; drop the oldest pairs if the middle matrix turns out numerically singular
            CompactRep rep;
            while ( true ) {
                try {
                    rep = buildCompactRep(new ArrayList<>(s), new ArrayList<>(y), theta);
                    break;
                } catch ( final RuntimeException e ) {
                    if ( s.isEmpty() ) {
                        rep = new CompactRep();
                        rep.theta = theta;
                        break;
                    }
                    s.removeFirst();
                    y.removeFirst();
                }
            }

            final CauchyPoint cp = generalizedCauchyPoint(x, g, lo, hi, rep);

            Array xbar;
            try {
                xbar = subspaceMinimization(x, g, cp.xcp, cp.c, lo, hi, rep, cp.isFree);
            } catch ( final RuntimeException e ) {
                xbar = cp.xcp; // fall back to the Cauchy point
            }

            Array d = xbar.sub(x);
            double dphi0 = g.dotProduct(d);

            // fall back to the (always-descent) Cauchy direction, then to the projected gradient, if the
            // subspace step is not downhill
            if ( norm2(d) < Constants.QL_EPSILON || dphi0 >= 0.0 ) {
                d = cp.xcp.sub(x);
                dphi0 = g.dotProduct(d);
            }

            if ( norm2(d) < Constants.QL_EPSILON ) {
                ecType[0] = EndCriteria.Type.StationaryPoint;
                break;
            }

            if ( dphi0 >= 0.0 ) {
                for ( int i = 0; i < n; ++i ) {
                    d.set(i, Math.min(Math.max(x.get(i) - g.get(i), lo.get(i)), hi.get(i)) - x.get(i));
                }
                dphi0 = g.dotProduct(d);
                if ( dphi0 >= 0.0 || norm2(d) < Constants.QL_EPSILON ) {
                    ecType[0] = EndCriteria.Type.StationaryPoint;
                    break;
                }
            }

            final double stpMax = maxFeasibleStep(x, d, lo, hi);
            if ( stpMax < Constants.QL_EPSILON ) {
                ecType[0] = EndCriteria.Type.StationaryPoint;
                break;
            }

            final LineSearchResult ls = lineSearchWolfe(p, x, d, f, g, stpMax);
            if ( ls == null ) {
                ecType[0] = EndCriteria.Type.StationaryFunctionValue;
                break;
            }

            // limited-memory update with the curvature safeguard
            final Array sNew = ls.xt.sub(x);
            final Array yNew = ls.gt.sub(g);
            final double sy = sNew.dotProduct(yNew);
            final double yy = yNew.dotProduct(yNew);

            if ( yy > 0.0 && sy > Constants.QL_EPSILON * yy ) {
                s.addLast(sNew);
                y.addLast(yNew);
                if ( s.size() > m ) {
                    s.removeFirst();
                    y.removeFirst();
                }
                theta = yy / sy;
            }

            final double fOld = f;
            x = ls.xt;
            f = ls.ft;
            g = ls.gt;
            ++iter;

            p.setCurrentValue(x);
            p.setFunctionValue(f);

            // relative function-reduction stop (SciPy's factr criterion)
            final double denom = Math.max(Math.max(Math.abs(fOld), Math.abs(f)), 1.0);
            if ( (fOld - f) <= factr * Constants.QL_EPSILON * denom ) {
                ecType[0] = EndCriteria.Type.StationaryFunctionValue;
                break;
            }
        }

        p.setCurrentValue(x);
        p.setFunctionValue(f);
        p.setGradientNormValue(pgInf * pgInf);
        return ecType[0];
    }
}
