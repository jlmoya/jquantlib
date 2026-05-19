/*
 Copyright (C) 2012 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors

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

/*! \file sparsematrix.hpp
    \brief Java port of the boost compressed_matrix used as SparseMatrix in
           QuantLib v1.42.1.
*/

package org.jquantlib.math.matrixutilities;

import java.util.Arrays;

/**
 * Compressed Sparse Row (CSR) sparse matrix of doubles.
 *
 * <p>Java equivalent of the C++ {@code boost::numeric::ublas::compressed_matrix<Real>}
 * which is typedef'd as {@code SparseMatrix} in {@code ql/math/matrixutilities/sparsematrix.hpp}.
 *
 * <p>The CSR storage uses three internal arrays:
 * <ul>
 *   <li>{@code rowPtr} (length {@code rows + 1}): offset into {@code colIdx}/{@code values}
 *       where row {@code i} starts; row {@code i} occupies indices
 *       {@code [rowPtr[i], rowPtr[i+1])}.</li>
 *   <li>{@code colIdx} (length {@code nnz}): column index of each stored entry,
 *       sorted ascending within each row.</li>
 *   <li>{@code values} (length {@code nnz}): value of each stored entry.</li>
 * </ul>
 *
 * <p><strong>Boost-compatible behavior:</strong> assigning {@code 0.0} to a
 * previously-unset cell still allocates an entry (matching boost's
 * {@code compressed_matrix} semantics).  This is what makes the
 * {@link #nrElements()} count diverge from "number of non-zero values" — it
 * counts allocated entries.  See {@code testSparseMatrixZeroAssignment} in the
 * C++ test-suite (fdmlinearop.cpp) for the contract.
 *
 * <p>Ported from QuantLib v1.42.1 source-of-truth boost {@code compressed_matrix}
 * usage in {@code ql/math/matrixutilities/sparsematrix.hpp}.
 *
 * <p>Phase 5b.5 — JQuantLib migration.
 */
public class SparseMatrix {

    private final int rows;
    private final int cols;

    /** rowPtr[i] = start offset (inclusive) of row i; rowPtr[rows] = nnz. */
    private final int[] rowPtr;
    /** colIdx[k] = column index of entry k (sorted ascending within each row). */
    private int[] colIdx;
    /** values[k] = value of entry k. */
    private double[] values;
    /** Current number of allocated entries (length of populated colIdx/values). */
    private int nnz;

    // -----------------------------------------------------------------------
    // Constructors

    /**
     * Build a sparse matrix of {@code rows × cols} with zero allocated entries.
     *
     * @param rows number of rows ({@code >= 0})
     * @param cols number of columns ({@code >= 0})
     */
    public SparseMatrix(final int rows, final int cols) {
        if ( rows < 0 || cols < 0 ) {
            throw new IllegalArgumentException(
                    "SparseMatrix: dimensions must be non-negative (" + rows + "x" + cols + ")");
        }
        this.rows = rows;
        this.cols = cols;
        this.rowPtr = new int[rows + 1];
        this.colIdx = new int[0];
        this.values = new double[0];
        this.nnz = 0;
    }

    /**
     * Copy constructor.  Produces an independent CSR snapshot of {@code other}.
     *
     * @param other matrix to copy
     */
    public SparseMatrix(final SparseMatrix other) {
        this.rows = other.rows;
        this.cols = other.cols;
        this.rowPtr = other.rowPtr.clone();
        this.colIdx = other.nnz > 0 ? Arrays.copyOf(other.colIdx, other.nnz) : new int[0];
        this.values = other.nnz > 0 ? Arrays.copyOf(other.values, other.nnz) : new double[0];
        this.nnz = other.nnz;
    }

    // -----------------------------------------------------------------------
    // Dimensions

    /**
     * Static convenience equivalent of C++ {@code prod(const SparseMatrix& A, const Array& x)}.
     *
     * @param A sparse matrix
     * @param x input vector
     * @return {@code A * x}
     */
    public static Array prod(final SparseMatrix A, final Array x) {
        return A.mul(x);
    }

    /** @return number of rows. */
    public int rows() {
        return rows;
    }

    /** @return number of columns. */
    public int columns() {
        return cols;
    }

    /** @return number of rows (boost {@code size1()}). */
    public int size1() {
        return rows;
    }

    /** @return number of columns (boost {@code size2()}). */
    public int size2() {
        return cols;
    }

    /**
     * Number of allocated CSR entries (boost {@code nnz()}).
     *
     * <p><strong>Note:</strong> matches the C++ helper
     * {@code nrElementsOfSparseMatrix(m)} (used in {@code testSparseMatrixZeroAssignment}) — counts entries, including
     * those whose value happens to be zero.
     *
     * @return entry count
     */
    public int nrElements() {
        return nnz;
    }

    /**
     * Number of "filled" rows + 1 (boost {@code filled1()}); equal to {@code rows + 1}.  Provided for parity with
     * boost-style iteration in the {@code prod(SparseMatrix, Array)} helper below.
     *
     * @return rows + 1
     */
    public int filled1() {
        return rows + 1;
    }

    /**
     * Reference to the row-pointer array of length {@code filled1()}. Boost-compat name: {@code index1_data()}.  Index
     * {@code i} gives the start offset of row {@code i} in {@code valueData()}/{@code index2Data()}.
     *
     * <p>The returned array is the live internal buffer; do not mutate.
     *
     * @return internal row-pointer array
     */
    public int[] index1Data() {
        return rowPtr;
    }

    /**
     * Reference to the column-index array of length {@code nrElements()}. Boost-compat name: {@code index2_data()}.
     *
     * <p>The returned array's first {@code nrElements()} positions are valid;
     * trailing positions (capacity overhead) are unused.
     *
     * @return internal column-index array
     */
    public int[] index2Data() {
        return colIdx;
    }

    // -----------------------------------------------------------------------
    // Element access

    /**
     * Reference to the value array of length {@code nrElements()}. Boost-compat name: {@code value_data()}.
     *
     * @return internal value array
     */
    public double[] valueData() {
        return values;
    }

    /**
     * Return the value at {@code (row, col)}, or {@code 0.0} if unset.
     *
     * @param row row index ({@code 0 <= row < rows})
     * @param col column index ({@code 0 <= col < cols})
     * @return stored value or {@code 0.0}
     */
    public double get(final int row, final int col) {
        checkBounds(row, col);
        final int k = findEntry(row, col);
        return (k >= 0) ? values[k] : 0.0;
    }

    /**
     * Set the value at {@code (row, col)} to {@code v}.
     *
     * <p>Boost {@code compressed_matrix} semantics: even {@code v == 0.0}
     * allocates an entry (so {@link #nrElements()} grows).
     *
     * @param row row index
     * @param col column index
     * @param v   value
     */
    public void set(final int row, final int col, final double v) {
        checkBounds(row, col);
        final int k = findEntry(row, col);
        if ( k >= 0 ) {
            values[k] = v;
        } else {
            insertEntry(row, col, v);
        }
    }

    /**
     * In-place: {@code this(row,col) += v}.  Allocates an entry if needed (even if {@code v + existing == 0.0}, the
     * entry remains allocated).
     *
     * @param row row index
     * @param col column index
     * @param v   delta to add
     */
    public void addAt(final int row, final int col, final double v) {
        checkBounds(row, col);
        final int k = findEntry(row, col);
        if ( k >= 0 ) {
            values[k] += v;
        } else {
            insertEntry(row, col, v);
        }
    }

    /**
     * In-place addition: {@code this += other}.  Both matrices must have the same shape.  Equivalent to summing entries
     * of {@code other} into {@code this} via {@link #addAt(int, int, double)}.
     *
     * @param other addend
     * @return {@code this}
     */
    public SparseMatrix addAssign(final SparseMatrix other) {
        if ( rows != other.rows || cols != other.cols ) {
            throw new IllegalArgumentException(
                    "SparseMatrix: shape mismatch in addAssign (" + rows + "x" + cols + " vs " + other.rows + "x"
                            + other.cols + ")");
        }
        for ( int i = 0; i < other.rows; i++ ) {
            final int beg = other.rowPtr[i];
            final int end = other.rowPtr[i + 1];
            for ( int k = beg; k < end; k++ ) {
                addAt(i, other.colIdx[k], other.values[k]);
            }
        }
        return this;
    }

    // -----------------------------------------------------------------------
    // Matrix-vector product:  y = this * x

    /**
     * Out-of-place addition: returns a new matrix equal to {@code this + other}.
     *
     * @param other addend
     * @return new sparse matrix
     */
    public SparseMatrix add(final SparseMatrix other) {
        return new SparseMatrix(this).addAssign(other);
    }

    /**
     * Compute {@code y = this * x}.  Mirrors the {@code prod(SparseMatrix, Array)} helper in
     * {@code ql/math/matrixutilities/sparsematrix.hpp}.
     *
     * @param x input vector of length {@code columns()}
     * @return new array of length {@code rows()}
     */
    public Array mul(final Array x) {
        if ( x.size() != cols ) {
            throw new IllegalArgumentException(
                    "SparseMatrix.mul: vector size " + x.size() + " != matrix columns " + cols);
        }
        final double[] y = new double[rows];
        for ( int i = 0; i < rows; i++ ) {
            double s = 0.0;
            final int beg = rowPtr[i];
            final int end = rowPtr[i + 1];
            for ( int k = beg; k < end; k++ ) {
                s += values[k] * x.get(colIdx[k]);
            }
            y[i] = s;
        }
        return new Array(y, y.length);
    }

    // -----------------------------------------------------------------------
    // Internal helpers

    private void checkBounds(final int row, final int col) {
        if ( row < 0 || row >= rows || col < 0 || col >= cols ) {
            throw new IndexOutOfBoundsException(
                    "SparseMatrix: index (" + row + "," + col + ") out of " + rows + "x" + cols + " bounds");
        }
    }

    /**
     * Binary search within the row's column-index range.
     *
     * @return index into {@code values}/{@code colIdx} if entry exists, else {@code -1}
     */
    private int findEntry(final int row, final int col) {
        final int lo = rowPtr[row];
        final int hi = rowPtr[row + 1];
        // Linear search is fine for small rows — typical FDM operators have
        // bandwidth O(1)..O(few).  Binary would still be acceptable but not
        // measurably faster for n<=10.
        for ( int k = lo; k < hi; k++ ) {
            final int c = colIdx[k];
            if ( c == col ) {
                return k;
            }
            if ( c > col ) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Insert a new entry at the proper sorted position within row {@code row}, shifting subsequent entries right and
     * bumping all later row pointers.
     */
    private void insertEntry(final int row, final int col, final double v) {
        // Find insertion point within row [rowPtr[row], rowPtr[row+1])
        final int beg = rowPtr[row];
        final int end = rowPtr[row + 1];
        int pos = end;
        for ( int k = beg; k < end; k++ ) {
            if ( colIdx[k] > col ) {
                pos = k;
                break;
            }
        }

        // Grow capacity if needed.
        if ( nnz == colIdx.length ) {
            final int newCap = Math.max(8, colIdx.length * 2);
            colIdx = Arrays.copyOf(colIdx, newCap);
            values = Arrays.copyOf(values, newCap);
        }

        // Shift entries at [pos, nnz) one position right.
        if ( pos < nnz ) {
            System.arraycopy(colIdx, pos, colIdx, pos + 1, nnz - pos);
            System.arraycopy(values, pos, values, pos + 1, nnz - pos);
        }

        colIdx[pos] = col;
        values[pos] = v;
        nnz++;

        // Bump rowPtr for all rows after `row`.
        for ( int r = row + 1; r <= rows; r++ ) {
            rowPtr[r]++;
        }
    }

    // -----------------------------------------------------------------------
    // Diagnostics

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SparseMatrix(").append(rows).append("x").append(cols).append(", nnz=").append(nnz).append(") {");
        for ( int i = 0; i < rows; i++ ) {
            final int beg = rowPtr[i];
            final int end = rowPtr[i + 1];
            for ( int k = beg; k < end; k++ ) {
                if ( sb.charAt(sb.length() - 1) != '{' )
                    sb.append(", ");
                sb.append("(").append(i).append(",").append(colIdx[k]).append(")=").append(values[k]);
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
