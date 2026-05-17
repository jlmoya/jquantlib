/*
 Copyright (C) 2015, 2024 Peter Caspers
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.experimental.math;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.BiCGStab;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpLayout;
import org.jquantlib.methods.finitedifferences.operators.SecondDerivativeOp;

/**
 * Laplace interpolation of missing values.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/laplaceinterpolation.hpp/.cpp}.
 *
 * <p>Reconstruction of missing values using Laplace interpolation. We support
 * an arbitrary number of dimensions {@code n >= 1} and non-equidistant grids.
 * For {@code n = 1} the method is identical to linear interpolation with flat
 * extrapolation.  Reference: <i>Numerical Recipes, 3rd edition, ch. 3.8</i>.
 *
 * <p>Missing values are encoded as {@link Constants#NULL_REAL} (matches the
 * C++ {@code Null<Real>()} sentinel).
 *
 * <p>Phase 5e.5b-CFC-d-96.
 */
public class LaplaceInterpolation {

    /** Y functor: maps an N-d coordinate to either a value or NULL_REAL. */
    public interface YFunction extends Function<int[], Double> {
        @Override
        Double apply(int[] coordinates);
    }

    private final YFunction y_;
    private final double[][] x_;
    private final double relTol_;
    private final int maxIterMultiplier_;

    private final boolean[] coordinateIncluded_;
    private final int numberOfCoordinatesIncluded_;

    private final FdmLinearOpLayout layout_;
    private final Array interpolatedValues_;

    /** Convenience constructor with default {@code relTol = 1e-6}, {@code maxIterMultiplier = 10}. */
    public LaplaceInterpolation(final YFunction y, final double[][] x) {
        this(y, x, 1e-6, 10);
    }

    /**
     * Build the interpolation.
     *
     * @param y  N-d functor returning a real value or {@link Constants#NULL_REAL}
     *           for missing samples.
     * @param x  per-dimension grids (length-1 dimensions are projected out).
     * @param relTol             relative tolerance for the BiCGStab solver.
     * @param maxIterMultiplier  maximum-iterations cap is {@code maxIterMultiplier * N}
     *                            where {@code N} is the total grid size.
     */
    public LaplaceInterpolation(final YFunction y, final double[][] x,
                                final double relTol, final int maxIterMultiplier) {
        this.y_ = y;
        this.x_ = cloneJagged(x);
        this.relTol_ = relTol;
        this.maxIterMultiplier_ = maxIterMultiplier;

        // Decide which dimensions are non-trivial (size > 1).
        this.coordinateIncluded_ = new boolean[x_.length];
        final List<Integer> dimList = new ArrayList<>();
        for (int i = 0; i < x_.length; ++i) {
            coordinateIncluded_[i] = x_[i].length > 1;
            if (coordinateIncluded_[i]) {
                dimList.add(x_[i].length);
            }
        }
        this.numberOfCoordinatesIncluded_ = dimList.size();

        if (numberOfCoordinatesIncluded_ == 0) {
            // No work to do: operator() returns y(...) directly (or 0 if missing).
            this.layout_ = null;
            this.interpolatedValues_ = null;
            return;
        }

        final int[] dim = new int[dimList.size()];
        for (int i = 0; i < dim.length; ++i) {
            dim[i] = dimList.get(i);
        }
        this.layout_ = new FdmLinearOpLayout(dim);

        final List<Fdm1dMesher> meshers = new ArrayList<>();
        for (final double[] xi : x_) {
            if (xi.length > 1) {
                meshers.add(new Predefined1dMesher(xi.clone()));
            }
        }
        final FdmMesherComposite mesher = new FdmMesherComposite(layout_, meshers);

        // Build the Laplace operator as a dense Matrix (sum of per-direction
        // SecondDerivativeOp matrices). Then convert to SparseMatrix g (and
        // adjust rows for corners / known values) below.
        final int n = layout_.size();
        final Matrix op = new Matrix(n, n);
        for (int d = 0; d < dim.length; ++d) {
            if (dim[d] > 1) {
                final Matrix m = new SecondDerivativeOp(d, mesher).toMatrix();
                for (int i = 0; i < n; ++i) {
                    for (int j = 0; j < n; ++j) {
                        final double v = m.get(i, j);
                        if (v != 0.0) {
                            op.set(i, j, op.get(i, j) + v);
                        }
                    }
                }
            }
        }

        // Set up the linear system to solve.
        final SparseMatrix g = new SparseMatrix(n, n);
        final Array rhs = new Array(n);
        final Array guess = new Array(n);
        double guessTmp = 0.0;

        final double[] cornerH = new double[dim.length];
        final int[] cornerNeighbourIndex = new int[dim.length];

        int count = 0;
        for (final FdmLinearOpIterator pos : layout_) {
            final int[] coord = pos.coordinates();
            final int[] yCoord = (numberOfCoordinatesIncluded_ == x_.length)
                    ? coord : fullCoordinates(coord);
            final double val = y_.apply(yCoord);

            if (val == Constants.NULL_REAL) {
                // Decide whether this is a "corner" (all dimensions on boundary).
                boolean isCorner = true;
                for (int dd = 0; dd < dim.length && isCorner; ++dd) {
                    if (coord[dd] == 0) {
                        cornerH[dd] = meshers.get(dd).dplus(0);
                        cornerNeighbourIndex[dd] = 1;
                    } else if (coord[dd] == layout_.dim()[dd] - 1) {
                        cornerH[dd] = meshers.get(dd).dminus(dim[dd] - 1);
                        cornerNeighbourIndex[dd] = dim[dd] - 2;
                    } else {
                        isCorner = false;
                    }
                }
                if (isCorner) {
                    // All second derivatives are zero at the corners; build
                    // the row from the Numerical Recipes generalization, eq 3.8.6.
                    double sumCornerH = 0.0;
                    for (final double h : cornerH) {
                        sumCornerH += h;
                    }
                    for (int j = 0; j < dim.length; ++j) {
                        final int[] coordJ = coord.clone();
                        coordJ[j] = cornerNeighbourIndex[j];
                        double weight = 0.0;
                        for (int i = 0; i < dim.length; ++i) {
                            if (i != j) {
                                weight += cornerH[i];
                            }
                        }
                        weight = (dim.length == 1) ? 1.0 : (weight / sumCornerH);
                        g.set(count, layout_.index(coordJ), -weight);
                    }
                    g.set(count, count, 1.0);
                } else {
                    // Interior point: copy the row from the Laplace operator.
                    for (int j = 0; j < n; ++j) {
                        final double v = op.get(count, j);
                        if (v != 0.0) {
                            g.set(count, j, v);
                        }
                    }
                }
                rhs.set(count, 0.0);
                guess.set(count, guessTmp);
            } else {
                // Known value: identity row.
                g.set(count, count, 1.0);
                rhs.set(count, val);
                guessTmp = val;
                guess.set(count, guessTmp);
            }
            ++count;
        }

        final SparseMatrix gFinal = g;
        final BiCGStab.MatrixMult fA = arr -> SparseMatrix.prod(gFinal, arr);
        this.interpolatedValues_ = new BiCGStab(fA, maxIterMultiplier_ * n, relTol_)
                .solve(rhs, guess).x;
    }

    /** Evaluate the interpolation at the given N-d coordinate. */
    public double op(final int[] coordinates) {
        if (coordinates.length != x_.length) {
            throw new IllegalArgumentException(
                    "LaplaceInterpolation.op: expected " + x_.length
                            + " coordinates, got " + coordinates.length);
        }
        if (numberOfCoordinatesIncluded_ == 0) {
            final double val = y_.apply(coordinates);
            return (val == Constants.NULL_REAL) ? 0.0 : val;
        }
        final int[] projected = (numberOfCoordinatesIncluded_ == x_.length)
                ? coordinates : projectedCoordinates(coordinates);
        return interpolatedValues_.get(layout_.index(projected));
    }

    private int[] projectedCoordinates(final int[] coordinates) {
        final List<Integer> tmp = new ArrayList<>();
        for (int i = 0; i < coordinates.length; ++i) {
            if (coordinateIncluded_[i]) {
                tmp.add(coordinates[i]);
            }
        }
        final int[] out = new int[tmp.size()];
        for (int i = 0; i < out.length; ++i) {
            out[i] = tmp.get(i);
        }
        return out;
    }

    private int[] fullCoordinates(final int[] projectedCoordinates) {
        final int[] tmp = new int[coordinateIncluded_.length];
        int count = 0;
        for (int i = 0; i < coordinateIncluded_.length; ++i) {
            if (coordinateIncluded_[i]) {
                tmp[i] = projectedCoordinates[count++];
            }
        }
        return tmp;
    }

    // ------------------------------------------------------------------
    // Static convenience: in-place Matrix interpolation
    // ------------------------------------------------------------------

    /**
     * Convenience function that Laplace-interpolates {@link Constants#NULL_REAL}
     * values in the given matrix in place.  An equidistant grid is used for
     * any axis whose grid is left empty.
     */
    public static void laplaceInterpolation(final Matrix A) {
        laplaceInterpolation(A, new double[0], new double[0], 1e-6, 10);
    }

    /** As above, with the given x and y grids; {@code maxIterMultiplier = 10}. */
    public static void laplaceInterpolation(final Matrix A,
                                            final double[] x,
                                            final double[] y) {
        laplaceInterpolation(A, x, y, 1e-6, 10);
    }

    /**
     * As above, fully parameterised.  The C++ convention is that the first
     * grid in the 2-D coordinate vector is the y-axis (rows) and the second
     * is the x-axis (columns).
     */
    public static void laplaceInterpolation(final Matrix A,
                                            final double[] x,
                                            final double[] y,
                                            final double relTol,
                                            final int maxIterMultiplier) {
        final double[][] tmp = new double[2][];
        tmp[0] = (y == null || y.length == 0) ? null : y.clone();
        tmp[1] = (x == null || x.length == 0) ? null : x.clone();

        if (tmp[0] == null) {
            tmp[0] = new double[A.rows()];
            for (int i = 0; i < A.rows(); ++i) {
                tmp[0][i] = i;
            }
        }
        if (tmp[1] == null) {
            tmp[1] = new double[A.columns()];
            for (int j = 0; j < A.columns(); ++j) {
                tmp[1][j] = j;
            }
        }

        final LaplaceInterpolation interp = new LaplaceInterpolation(
                coord -> A.get(coord[0], coord[1]),
                tmp,
                relTol, maxIterMultiplier);

        for (int i = 0; i < A.rows(); ++i) {
            for (int j = 0; j < A.columns(); ++j) {
                if (A.get(i, j) == Constants.NULL_REAL) {
                    A.set(i, j, interp.op(new int[]{i, j}));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static double[][] cloneJagged(final double[][] x) {
        final double[][] out = new double[x.length][];
        for (int i = 0; i < x.length; ++i) {
            out[i] = (x[i] == null) ? new double[0] : x[i].clone();
        }
        return out;
    }
}
