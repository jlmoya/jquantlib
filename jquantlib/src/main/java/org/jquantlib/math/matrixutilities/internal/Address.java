/*
JQuantLib is Copyright (c) 2007, Richard Gomes

All rights reserved.

This source code is release under the BSD License.

JQuantLib includes code taken from QuantLib.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

    Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

    Neither the names of the copyright holders nor the names of the QuantLib
    Group and its contributors may be used to endorse or promote products
    derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/
package org.jquantlib.math.matrixutilities.internal;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

import java.util.ListIterator;
import java.util.Set;

/**
 * This is the main interface responsible for {@link Matrix} and {@link Array} accessors
 *
 * @author Richard Gomes
 */
public interface Address {

    String INVALID_BACKWARD_INDEXING = "invalid backward indexing";
    String INVALID_ROW_INDEX = "invalid row index";
    String INVALID_COLUMN_INDEX = "invalid column index";
    String GAP_INDEX_FOUND = "gap index found";

    /**
     * @return the number of rows mapped
     */
    int rows();

    /**
     * @return the number of columns mapped
     */
    int cols();

    /**
     * @return the base address (inclusive)
     */
    int base();

    /**
     * @return the last address (exclusive)
     */
    int last();

    /**
     * <code>row0</code> is the row offset of the upmost row of a Matrix.
     * <p>
     * Example: Matrix B, being a sub-matrix of A, has <code>row0</code> like shown below:
     * <pre>
     *     Matrix A = new Matrix(5, 8);
     *     int row0 = 1; int row1 = 4;
     *     int col0 = 5; int col1 = 8;
     *     Matrix B = A.range(row0, row1, col0, col1);
     * </pre>
     *
     * @return
     */
    int row0();

    /**
     * <code>col0</code> is the row offset of the leftmost column of a Matrix.
     * <p>
     * Example: Matrix B, being a sub-matrix of A, has <code>col0</code> like shown below:
     * <pre>
     *     Matrix A = new Matrix(5, 8);
     *     int row0 = 1; int row1 = 4;
     *     int col0 = 5; int col1 = 8;
     *     Matrix B = A.range(row0, row1, col0, col1);
     * </pre>
     *
     * @return
     */
    int col0();

    /**
     * Tells if the underlying memory storage can be accessed in a continuous way.
     * <p>
     * When <code>contiguous</code> is <code>true</code>, certain operations are benefited by bulk operations.
     */
    boolean isContiguous();

    /**
     * This is a convenience method intended to return {@link Flags#FORTRAN}
     *
     * @return Flags#FORTRAN
     */
    boolean isFortran();

    /**
     * @return a set of flags in effect on this {@link Address} object.
     */
    Set< Address.Flags > flags();

    //
    // public inner enumerations
    //

    enum Flags {

        /**
         * Tells if this {@link Address} is intended to Fortran 1-based indexing.
         * <p>
         * In FORTRAN language, access to vectors and matrices are 1-based, like this:
         * <pre>
         *     for (i = 1; i <= n; i++)
         * </pre>
         * rather than what you can see in Java, C, C++, etc:
         * <pre>
         *     for (i = 0; i < n; i++)
         * </pre>
         */
        FORTRAN,

        //        /**
        //         * Tells if rows and columns must be transposed.
        //         * <p>
        //         * This feature is particularly important in 2 situations:
        //         * <li>Implement Matrix transposition in constant time by simply changing the address mapping as opposed
        //         * to performing an actual transposition of all elements, possibly employing very expensive copy of elements;</li>
        //         * <li>Increase performance when a Matrix is mostly used as a set of column arrays. As columns are mapped
        //         * internally as rows, the processor will show better performance due to memory caching of adjacent elements</li>
        //         * <p>
        //         * <b>NOT IMPLEMENTED YET</b>
        //         */
        //        TRANSPOSE
    }

    //
    // public inner interfaces
    //

    /**
     * This is the main interface responsible for generation of Iterators associated to classes {@link Matrix} and
     * {@link Array}
     *
     * @see ListIterator
     */
    interface Offset {
        int op();
    }

    /**
     * This interface defines {@link Array} accessors
     *
     * @author Richard Gomes
     */
    interface ArrayAddress extends Cloneable, Address {

        ArrayAddress clone();

        int op(int index);

        ArrayAddress toFortran();

        ArrayAddress toJava();

        ArrayOffset offset();

        ArrayOffset offset(int index);

        /**
         * This interface defines Iterators associated to class {@link Array}
         *
         * @see ListIterator
         */
        interface ArrayOffset extends Offset, ListIterator< Double > {
            void setIndex(final int index);
        }
    }

    /**
     * This interface defines {@link Matrix} accessors
     *
     * @author Richard Gomes
     */
    interface MatrixAddress extends Cloneable, Address {

        MatrixAddress clone();

        int op(int row, int col);

        MatrixAddress toFortran();

        MatrixAddress toJava();

        MatrixOffset offset();

        MatrixOffset offset(final int row, final int col);

        /**
         * This interface defines Iterators associated to class {@link Matrix}
         *
         * @see ListIterator
         */
        interface MatrixOffset extends Offset {
            void setRow(final int row);

            void setCol(final int col);

            void nextRow();

            void prevRow();

            void nextCol();

            void prevCol();
        }

    }

}
