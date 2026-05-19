/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2003, 2004 Roman Gitlin

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.math.interpolations;

import org.jquantlib.QL;

/**
 * N-dimensional cubic spline interpolation between discrete points.
 *
 * <p>Java port of v1.42.1
 * {@code ql/math/interpolations/multicubicspline.hpp}.
 *
 * <p>The C++ implementation uses heavy template recursion ({@code Int2Type<N>},
 * {@code n_cubic_spline<X>}, etc.) to support up to 15 dimensions with zero
 * heap allocation. The Java port instead uses a flat {@code double[]} grid
 * indexed by strides plus a recursive evaluation routine, which is functionally
 * equivalent (natural cubic spline along every axis) but does not require
 * template specialisation. Memory cost is identical: {@code prod(dim) *
 * sizeof(double)} per grid plus one second-derivative grid per dimension.
 *
 * <h3>Algorithm</h3>
 *
 * Identical to C++ {@code base_cubic_spline}/{@code base_cubic_splint} —
 * natural cubic splines (second derivatives zero at the endpoints) applied
 * along each axis in turn. The {@code build()} step precomputes the
 * second-derivative table for every fibre (row/column/.../axis) by walking
 * the grid axis-by-axis with the Thomas algorithm. The {@code op()} step
 * recursively evaluates each 1-d cubic spline along the outermost
 * dimension after reducing the remaining N-1 dimensions to scalar values
 * at the supplied query point — i.e. ordinary tensor-product evaluation.
 *
 * <h3>Limitations</h3>
 *
 * Minimum 4 grid points per axis (matches C++ {@code dim >= 3} check on
 * {@code v.size() - 1}). Extrapolation is allowed per-axis only if the
 * corresponding {@code allowExtrapolation[i]} flag is true, matching the
 * C++ {@code ae_} array.
 *
 * @author Phase 5e.5b-CFC-d-280 port
 */
public final class MultiCubicSpline {

    private final double[][] grid;
    private final boolean[] allowExtrapolation;
    private final int n;            // number of dimensions
    private final int[] dims;       // dimensions per axis
    private final int[] strides;    // row-major (last-fastest) strides
    private final int total;        // product of dims
    private final double[] y;       // function values, flat row-major
    private final double[] y2;      // second derivatives along last axis (rebuilt per fibre)

    /**
     * @param grid               per-axis grid points; {@code grid[i]} has the
     *                           knots along dimension {@code i} (size >= 4)
     * @param values             function values laid out row-major; the
     *                           innermost / last dimension is fastest, matching
     *                           C++ {@code DataTable} semantics
     * @param allowExtrapolation per-axis extrapolation flags; pass an empty
     *                           array (length 0) for the all-false default
     */
    public MultiCubicSpline(final double[][] grid,
                            final double[] values,
                            final boolean[] allowExtrapolation) {
        QL.require(grid != null && grid.length >= 1, "empty grid");
        this.n = grid.length;
        this.grid = new double[n][];
        for (int i = 0; i < n; ++i) {
            QL.require(grid[i] != null && grid[i].length >= 4,
                    "dimension " + i + ": not enough points for interpolation");
            this.grid[i] = grid[i].clone();
            // sanity: strictly increasing
            for (int k = 1; k < grid[i].length; ++k) {
                QL.require(grid[i][k] > grid[i][k - 1],
                        "dimension " + i + ": invalid data (knots not strictly increasing)");
            }
        }

        this.dims = new int[n];
        int t = 1;
        for (int i = 0; i < n; ++i) {
            dims[i] = grid[i].length;
            t *= dims[i];
        }
        this.total = t;
        this.strides = new int[n];
        strides[n - 1] = 1;
        for (int i = n - 2; i >= 0; --i) {
            strides[i] = strides[i + 1] * dims[i + 1];
        }

        QL.require(values != null && values.length == total,
                "values size mismatch with grid");
        this.y = values.clone();

        if (allowExtrapolation == null || allowExtrapolation.length == 0) {
            this.allowExtrapolation = new boolean[n];
        } else {
            QL.require(allowExtrapolation.length == n,
                    "allowExtrapolation length mismatch");
            this.allowExtrapolation = allowExtrapolation.clone();
        }

        this.y2 = new double[total];
        buildSecondDerivatives();
    }

    /**
     * Build a natural-cubic-spline second-derivative table along the last
     * axis ({@code n-1}). At evaluation time we recursively reduce dim
     * {@code n-1} to a scalar function of dim {@code n-2}, then reduce
     * {@code n-2}, etc., rebuilding the next-axis second derivatives on
     * the fly. Storing only the last-axis y2 keeps memory bounded.
     */
    private void buildSecondDerivatives() {
        // Build natural cubic spline second derivatives along the last axis
        // for every fibre. We walk fibres by iterating all (n-1)-dim
        // coordinates; for each, run the Thomas algorithm along the last axis.
        final int sz = dims[n - 1];
        final double[] u = new double[sz];
        // outer-coord product:
        int outerSize = 1;
        for (int i = 0; i < n - 1; ++i) outerSize *= dims[i];

        if (n == 1) {
            naturalCubicSpline(y, 0, 1, sz, grid[0], y2, 0, u);
            return;
        }
        // last-axis stride is 1, so each fibre is contiguous of length sz
        // step between fibres = strides[n-2] / dims[n-1] ... actually fibres
        // begin every `dims[n-1]` elements when last-axis stride is 1.
        // Iterate by fibre base address.
        for (int o = 0; o < outerSize; ++o) {
            final int base = o * sz;
            naturalCubicSpline(y, base, 1, sz, grid[n - 1], y2, base, u);
        }
    }

    /**
     * Standard natural cubic spline second-derivative computation (Numerical
     * Recipes section 3.3). Operates on a 1-d slice of {@code yArr} starting
     * at {@code yStart}, stride {@code yStride}, length {@code sz}, with knot
     * abscissae {@code x}. Writes second derivatives into {@code y2Arr}
     * starting at {@code y2Start} with the same stride; uses workspace
     * {@code u[]} of length >= sz.
     */
    private static void naturalCubicSpline(final double[] yArr, final int yStart, final int yStride,
                                           final int sz, final double[] x,
                                           final double[] y2Arr, final int y2Start, final double[] u) {
        // Natural boundary: y2[0] = y2[n-1] = 0
        y2Arr[y2Start] = 0.0;
        u[0] = 0.0;
        for (int i = 1; i < sz - 1; ++i) {
            final double xim1 = x[i - 1], xi = x[i], xip1 = x[i + 1];
            final double yim1 = yArr[yStart + (i - 1) * yStride];
            final double yi   = yArr[yStart + i * yStride];
            final double yip1 = yArr[yStart + (i + 1) * yStride];
            final double sig = (xi - xim1) / (xip1 - xim1);
            final double p = sig * y2Arr[y2Start + (i - 1) * yStride] + 2.0;
            y2Arr[y2Start + i * yStride] = (sig - 1.0) / p;
            u[i] = (yip1 - yi) / (xip1 - xi) - (yi - yim1) / (xi - xim1);
            u[i] = (6.0 * u[i] / (xip1 - xim1) - sig * u[i - 1]) / p;
        }
        y2Arr[y2Start + (sz - 1) * yStride] = 0.0;
        for (int k = sz - 2; k >= 0; --k) {
            y2Arr[y2Start + k * yStride] =
                    y2Arr[y2Start + k * yStride] * y2Arr[y2Start + (k + 1) * yStride] + u[k];
        }
    }

    /** Evaluate at point {@code x} (length {@code n}). */
    public double op(final double[] x) {
        QL.require(x != null && x.length == n,
                "argument size mismatch (expected " + n + ")");
        // Recursive tensor-product evaluation: reduce last axis first,
        // building (n-1)-dim grid; recurse until 0-dim scalar.
        return evaluate(y, n, x);
    }

    /**
     * Reduce an {@code m}-dim grid (last {@code m} axes of the original
     * layout) of {@code yArr} to an {@code (m-1)}-dim grid by evaluating the
     * natural cubic spline along axis {@code n-m} at {@code x[n-m]}, then
     * recurse on the next axis.
     */
    private double evaluate(final double[] yArr, final int m, final double[] x) {
        if (m == 0) {
            return yArr[0];
        }
        final int axis = n - m;
        final int sz = dims[axis];
        // Inner stride (size of one "row" along axis after the next axes)
        int innerSize = 1;
        for (int i = axis + 1; i < n; ++i) innerSize *= dims[i];

        // Output grid has size innerSize (one less dim).
        final double[] out = new double[innerSize];

        // Locate the bracket and the basis coefficients for axis.
        final double[] knots = grid[axis];
        final double xq = x[axis];
        final int kk;
        final double a, b, a2, b2;
        if (xq < knots[0] || xq > knots[sz - 1]) {
            QL.require(allowExtrapolation[axis],
                    "dimension " + axis + ": extrapolation is not allowed.");
            // Clamp: linear in basis weighting on the boundary segment.
            if (xq < knots[0]) {
                kk = 0;
            } else {
                kk = sz - 2;
            }
            // Build basis at the boundary (using the segment polynomial).
            final double h = knots[kk + 1] - knots[kk];
            a  = (knots[kk + 1] - xq) / h;
            b  = (xq - knots[kk]) / h;
            a2 = (a * a * a - a) * h * h / 6.0;
            b2 = (b * b * b - b) * h * h / 6.0;
        } else {
            int lo = 0, hi = sz - 1;
            while (hi - lo > 1) {
                final int mid = (hi + lo) >>> 1;
                if (knots[mid] > xq) hi = mid;
                else lo = mid;
            }
            kk = lo;
            final double h = knots[kk + 1] - knots[kk];
            a  = (knots[kk + 1] - xq) / h;
            b  = (xq - knots[kk]) / h;
            a2 = (a * a * a - a) * h * h / 6.0;
            b2 = (b * b * b - b) * h * h / 6.0;
        }

        // Build second derivatives along this axis for every (m-1)-d slice
        // of yArr; then evaluate. y2 layout matches yArr layout.
        // To avoid O(N) allocation each call we reuse temp workspace.
        // We need y2 along axis: for each fibre along this axis, run the
        // Thomas algorithm.
        // Each fibre length is sz, fibres are strided by innerSize.
        // Total fibres = yArr.length / sz.
        final int totalSize = yArr.length;
        final int fibres = totalSize / sz;
        final double[] y2axis = new double[totalSize];
        final double[] wkU = new double[sz];

        // Fibre layout: yArr indices (i*innerSize*sz + j*1 + axisIdx*innerSize)
        // — wait, the layout here is row-major *of the current sub-array*.
        // After reduction, sub-arrays use the original layout's relative
        // strides starting at this axis.  In `yArr` (size = sz * innerSize),
        // the axis is the slowest dim (stride innerSize), the inner dims
        // are below (stride 1..innerSize-1).
        // To iterate fibres: outer index = j in [0..innerSize), fibre i
        // = j + k*innerSize for k=0..sz-1.
        for (int j = 0; j < innerSize; ++j) {
            naturalCubicSpline(yArr, j, innerSize, sz, knots, y2axis, j, wkU);
        }

        // Evaluate along the axis for each inner index j.
        for (int j = 0; j < innerSize; ++j) {
            final double yk  = yArr[j + kk * innerSize];
            final double ykp = yArr[j + (kk + 1) * innerSize];
            final double y2k  = y2axis[j + kk * innerSize];
            final double y2kp = y2axis[j + (kk + 1) * innerSize];
            out[j] = a * yk + b * ykp + a2 * y2k + b2 * y2kp;
        }

        return evaluate(out, m - 1, x);
    }
}
