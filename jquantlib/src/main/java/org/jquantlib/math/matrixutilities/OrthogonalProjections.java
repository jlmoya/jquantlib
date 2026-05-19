/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k.5 C.9.

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

/*
 Copyright (C) 2007, 2008 Mark Joshi
*/

package org.jquantlib.math.matrixutilities;

/**
 * Given a collection of vectors w_i, find a collection of vectors x_i such that x_i is orthogonal to w_j for i != j,
 * and {@code <x_i, w_i> = <w_i, w_i>}.
 *
 * <p>The algorithm performs Gram-Schmidt on all other vectors to build an
 * orthonormal basis excluding w_j, then projects w_j onto the orthogonal complement to get x_j. If the resulting
 * multiplier exceeds {@code multiplierCutOff}, vector j is marked invalid.
 *
 * <p>Mirrors C++ {@code OrthogonalProjections} declared in
 * {@code ql/math/matrixutilities/basisincompleteordered.hpp} and implemented in the corresponding .cpp (QuantLib
 * v1.42.1).
 *
 * <p>Tested in MatricesTest::testOrthogonalProjection() in the C++ suite;
 * the Java equivalent is in {@link org.jquantlib.testsuite.math.matrixutilities.OrthogonalProjectionsTest}.
 *
 * @author Jose Moya
 */
public class OrthogonalProjections {

    // ---- inputs ----
    private final Matrix originalVectors_;
    private final double multiplierCutoff_;
    private final int numberVectors_;
    private final int dimension_;

    // ---- outputs ----
    private final boolean[] validVectors_;
    private final double[][] projectedVectors_;
    // ---- workspace ----
    private final Matrix orthoNormalizedVectors_;
    private int numberValidVectors_;

    /**
     * Constructs the orthogonal-projection set.
     *
     * @param originalVectors  matrix with one input vector per row
     * @param multiplierCutOff if |sizeMultiplier| >= this, vector is discarded
     * @param tolerance        if the Gram-Schmidt residual norm < this, vector is discarded
     */
    public OrthogonalProjections(final Matrix originalVectors, final double multiplierCutOff, final double tolerance) {
        this.originalVectors_ = new Matrix(originalVectors);
        this.multiplierCutoff_ = multiplierCutOff;
        this.numberVectors_ = originalVectors.rows();
        this.dimension_ = originalVectors.cols();
        this.validVectors_ = new boolean[numberVectors_];
        for ( int i = 0; i < numberVectors_; ++i ) {
            validVectors_[i] = true;
        }
        this.projectedVectors_ = new double[numberVectors_][dimension_];
        this.orthoNormalizedVectors_ = new Matrix(numberVectors_, dimension_);

        final double[] currentVector = new double[dimension_];

        for ( int j = 0; j < numberVectors_; ++j ) {
            if ( validVectors_[j] ) {
                // Copy all rows into orthoNormalizedVectors_ as working copies
                for ( int k = 0; k < numberVectors_; ++k ) {
                    for ( int m = 0; m < dimension_; ++m ) {
                        orthoNormalizedVectors_.set(k, m, originalVectors_.get(k, m));
                    }
                }

                // Gram-Schmidt: orthonormalize all rows except row j,
                // skip invalid rows too.
                for ( int k = 0; k < numberVectors_; ++k ) {
                    if ( k != j && validVectors_[k] ) {
                        // Subtract projections onto all already-orthonormalized valid rows l < k (l != j)
                        for ( int l = 0; l < k; ++l ) {
                            if ( validVectors_[l] && l != j ) {
                                final double dotProduct = innerProduct(orthoNormalizedVectors_, k,
                                        orthoNormalizedVectors_, l);
                                for ( int n = 0; n < dimension_; ++n ) {
                                    orthoNormalizedVectors_.set(k, n, orthoNormalizedVectors_.get(k, n)
                                            - dotProduct * orthoNormalizedVectors_.get(l, n));
                                }
                            }
                        }

                        final double normBeforeScaling = norm(orthoNormalizedVectors_, k);
                        if ( normBeforeScaling < tolerance ) {
                            validVectors_[k] = false;
                        } else {
                            final double recip = 1.0 / normBeforeScaling;
                            for ( int m = 0; m < dimension_; ++m ) {
                                orthoNormalizedVectors_.set(k, m, orthoNormalizedVectors_.get(k, m) * recip);
                            }
                        }
                    }
                }

                // We now have an o.n. basis for everything except j.
                // Compute the norm squared of the original vector j.
                final double prevNormSquared = normSquared(originalVectors_, j);

                // Project orthoNormalizedVectors_[j] onto the orthogonal complement of
                // all valid r != j.
                for ( int r = 0; r < numberVectors_; ++r ) {
                    if ( validVectors_[r] && r != j ) {
                        final double dotProduct = innerProduct(orthoNormalizedVectors_, j, orthoNormalizedVectors_, r);
                        for ( int s = 0; s < dimension_; ++s ) {
                            orthoNormalizedVectors_.set(j, s,
                                    orthoNormalizedVectors_.get(j, s) - dotProduct * orthoNormalizedVectors_.get(r, s));
                        }
                    }
                }

                final double projectionOnOriginalDirection = innerProduct(originalVectors_, j, orthoNormalizedVectors_,
                        j);
                final double sizeMultiplier = prevNormSquared / projectionOnOriginalDirection;

                if ( Math.abs(sizeMultiplier) < multiplierCutoff_ ) {
                    for ( int t = 0; t < dimension_; ++t ) {
                        currentVector[t] = orthoNormalizedVectors_.get(j, t) * sizeMultiplier;
                    }
                } else {
                    validVectors_[j] = false;
                }
            }

            projectedVectors_[j] = currentVector.clone();
        }

        // Count valid vectors
        numberValidVectors_ = 0;
        for ( int i = 0; i < numberVectors_; ++i ) {
            if ( validVectors_[i] ) {
                ++numberValidVectors_;
            }
        }
    }

    private static double normSquared(final Matrix v, final int row) {
        double x = 0.0;
        for ( int i = 0; i < v.cols(); ++i ) {
            final double e = v.get(row, i);
            x += e * e;
        }
        return x;
    }

    private static double norm(final Matrix v, final int row) {
        return Math.sqrt(normSquared(v, row));
    }

    private static double innerProduct(final Matrix v, final int row1, final Matrix w, final int row2) {
        double x = 0.0;
        for ( int i = 0; i < v.cols(); ++i ) {
            x += v.get(row1, i) * w.get(row2, i);
        }
        return x;
    }

    // ---- private helpers ----

    /** Returns the validity mask for the projected vectors. */
    public boolean[] validVectors() {
        return validVectors_;
    }

    /**
     * Returns the projected vector at the given index. Only meaningful when {@code validVectors()[index] == true}.
     */
    public double[] getVector(final int index) {
        return projectedVectors_[index];
    }

    /** Returns the number of valid (non-discarded) vectors. */
    public int numberValidVectors() {
        return numberValidVectors_;
    }
}
