/*
 Copyright (C) 2009 Ueli Hofstetter

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
 Copyright (C) 2003, 2004, 2007 Ferdinando Ametrano
 Copyright (C) 2006 Yiping Chen
 Copyright (C) 2007 Neil Firth

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.math.matrixutilities;

import org.jquantlib.QL;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.optimization.ConjugateGradient;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.Problem;

/**
 * @author Ueli Hofstetter
 */
public class PseudoSqrt {

    private final static String unknown_salvaging_algorithm = "unknown salvaging algorithm";

    //! Returns the rank-reduced pseudo square root of a real symmetric matrix
    /*! The result matrix has rank<=maxRank. If maxRank>=size, then the
        specified percentage of eigenvalues out of the eigenvalues' sum is
        retained.

        If the input matrix is not positive semi definite, it can return an
        approximation of the pseudo square root using a (user selected)
        salvaging algorithm.

        \pre the given matrix must be symmetric.

        \relates Matrix
     */
    public static Matrix rankReducedSqrt(final Matrix matrix, final int maxRank, final int componentRetainedPercentage,
            final SalvagingAlgorithm sa) {

        QL.require(matrix.rows == matrix.columns(), Cells.MATRIX_MUST_BE_SQUARE); // QA:[RG]::verified
        QL.require(checkSymmetry(matrix), Cells.MATRIX_MUST_BE_SYMMETRIC); // QA:[RG]::verified
        QL.require(componentRetainedPercentage > 0.0, "no eigenvalues retained");
        QL.require(componentRetainedPercentage <= 1.0, "percentage to be retained > 100%");
        QL.require(maxRank >= 1, "max rank required < 1");

        final int size = matrix.rows;

        // spectral (a.k.a Principal Component) analysis
        SymmetricSchurDecomposition jd = new SymmetricSchurDecomposition(matrix);
        Array eigenValues = jd.eigenvalues();

        // salvaging algorithm
        switch ( sa ) {
        case None:
            // eigenvalues are sorted in decreasing order
            if ( eigenValues.get(size - 1) < -1e-16 )
                throw new IllegalArgumentException("negative eigenvalue(s) (" + eigenValues.get(size - 1) + ")");
            break;
        case Spectral:
            // negative eigenvalues set to zero
            for ( int i = 0; i < size; ++i ) {
                eigenValues.set(i, Math.max(eigenValues.get(i), 0.0));
            }
            break;
        case Higham:
            final int maxIterations = 40;
            final double tolerance = 1e-6;
            final Matrix adjustedMatrix = null;//highamImplementation(matrix, maxIterations, tolerance);
            jd = new SymmetricSchurDecomposition(adjustedMatrix);
            eigenValues = jd.eigenvalues();
            break;
        default:
            throw new LibraryException("unknown or invalid salvaging algorithm");
        }

        // factor reduction
        double enough = componentRetainedPercentage * eigenValues.accumulate();
        if ( componentRetainedPercentage == 1.0 ) {
            // numerical glitches might cause some factors to be discarded
            enough *= 1.1;
        }
        // retain at least one factor
        double components = eigenValues.first();
        int retainedFactors = 1;
        for ( int i = 1; components < enough && i < size; ++i ) {
            components += eigenValues.get(i);
            retainedFactors++;
        }
        // output is granted to have a rank<=maxRank
        retainedFactors = Math.min(retainedFactors, maxRank);

        final Matrix diagonal = new Matrix(size, retainedFactors);
        for ( int i = 0; i < retainedFactors; ++i ) {
            diagonal.set(i, i, Math.sqrt(eigenValues.get(i)));
        }
        // Phase 3j align: C++ pseudosqrt.cpp:532 is `eigenvectors() * diagonal`
        // (one multiplication). Java port previously had the duplicated factor
        // `eigenvectors().mul(eigenvectors())` which produced an unrelated matrix.
        final Matrix result = jd.eigenvectors().mul(diagonal);

        normalizePseudoRoot(matrix, result);
        return result;
    }

    public static void normalizePseudoRoot(final Matrix matrix, final Matrix pseudo) {

        final int size = matrix.rows;

        if ( size != pseudo.rows )
            throw new IllegalArgumentException(
                    "matrix/pseudo mismatch: matrix rows are " + size + " while pseudo rows are " + pseudo.cols);

        final int pseudoCols = pseudo.cols;

        // row normalization
        // Phase 3j align: C++ pseudosqrt.cpp:62-63 sums `pseudo[i][j]*pseudo[i][j]`
        // (squared row norm). Java port previously used `pseudo[i][j]*pseudo[j][i]`
        // which is asymmetric and OOB when pseudoCols != rows.
        for ( int i = 0; i < size; ++i ) {
            double norm = 0.0;
            for ( int j = 0; j < pseudoCols; ++j ) {
                norm += pseudo.get(i, j) * pseudo.get(i, j);
            }
            if ( norm > 0.0 ) {
                final double normAdj = Math.sqrt(matrix.get(i, i) / norm);
                for ( int j = 0; j < pseudoCols; ++j ) {
                    pseudo.set(i, j, pseudo.get(i, j) * normAdj);
                }
            }
        }
    }

    /**
     * Optimisation step for the {@code Hypersphere} and {@code LowerDiagonal}
     * salvaging algorithms. Faithful port of C++
     * {@code QuantLib::detail::hypersphereOptimize}
     * (pseudosqrt.cpp:141-263) — parameterises the pseudo-root via spherical
     * angles {@code theta_i}, minimises the Frobenius residual with
     * {@link ConjugateGradient}, then reconstructs the matrix from the
     * optimal angles.
     *
     * @param targetMatrix the symmetric matrix whose pseudo-root is sought
     * @param currentRoot  an initial pseudo-root (typically derived from the
     *                     spectral salvaging step)
     * @param lowerDiagonal {@code true} for the lower-diagonal flavour
     *                                  ({@code n*(n-1)/2} angles);
     *                                  {@code false} for the full hypersphere
     *                                  ({@code n*(n-1)} angles)
     * @return the optimised pseudo-root
     */
    static Matrix hypersphereOptimize(final Matrix targetMatrix, final Matrix currentRoot, final boolean lowerDiagonal) {
        final int size = targetMatrix.rows;
        Matrix result = currentRoot.clone();
        final Array variance = new Array(size);
        for ( int i = 0; i < size; i++ ) {
            variance.set(i, Math.sqrt(targetMatrix.get(i, i)));
        }
        if ( lowerDiagonal ) {
            // C++ pseudosqrt.cpp:150-157: replace result with the Cholesky
            // factor of (result * result^T), scaled by the row-diagonal.
            final Matrix approxMatrix = result.mul(result.transpose());
            result = CholeskyDecomposition.CholeskyDecomposition(approxMatrix, true);
            for ( int i = 0; i < size; i++ ) {
                final double rowScale = Math.sqrt(approxMatrix.get(i, i));
                for ( int j = 0; j < size; j++ ) {
                    result.set(i, j, result.get(i, j) / rowScale);
                }
            }
        } else {
            // C++ pseudosqrt.cpp:158-164: divide each row by the
            // square-root-of-variance.
            for ( int i = 0; i < size; i++ ) {
                for ( int j = 0; j < size; j++ ) {
                    result.set(i, j, result.get(i, j) / variance.get(i));
                }
            }
        }

        final ConjugateGradient optimize = new ConjugateGradient();
        final EndCriteria endCriteria = new EndCriteria(100, 10, 1e-8, 1e-8, 1e-8);
        final HypersphereCostFunction costFunction = new HypersphereCostFunction(targetMatrix, variance, lowerDiagonal);
        final NoConstraint constraint = new NoConstraint();

        // hypersphere vector optimization
        final double eps = 1e-16;

        if ( lowerDiagonal ) {
            // C++ pseudosqrt.cpp:174-217 — n*(n-1)/2 angles
            final Array theta = new Array(size * (size - 1) / 2);
            for ( int i = 1; i < size; i++ ) {
                for ( int j = 0; j < i; j++ ) {
                    final int idx = i * (i - 1) / 2 + j;
                    double v = result.get(i, j);
                    v = clampToOpenUnit(v, eps);
                    for ( int k = 0; k < j; k++ ) {
                        v /= Math.sin(theta.get(i * (i - 1) / 2 + k));
                        v = clampToOpenUnit(v, eps);
                    }
                    double ang = Math.acos(v);
                    if ( j == i - 1 && result.get(i, i) < 0.0 ) {
                        ang = -ang;
                    }
                    theta.set(idx, ang);
                }
            }
            final Problem p = new Problem(costFunction, constraint, theta);
            optimize.minimize(p, endCriteria);
            final Array optTheta = p.currentValue();

            // Reconstruct result from optimised angles (C++ pseudosqrt.cpp:201-217).
            for ( int i = 0; i < size; i++ ) {
                for ( int k = 0; k < size; k++ ) {
                    result.set(i, k, 1.0);
                }
            }
            for ( int i = 0; i < size; i++ ) {
                for ( int k = 0; k < size; k++ ) {
                    if ( k > i ) {
                        result.set(i, k, 0.0);
                    } else {
                        double prod = 1.0;
                        for ( int j = 0; j <= k; j++ ) {
                            if ( j == k && k != i ) {
                                prod *= Math.cos(optTheta.get(i * (i - 1) / 2 + j));
                            } else if ( j != i ) {
                                prod *= Math.sin(optTheta.get(i * (i - 1) / 2 + j));
                            }
                        }
                        result.set(i, k, prod);
                    }
                }
            }
        } else {
            // C++ pseudosqrt.cpp:218-256 — n*(n-1) angles
            final Array theta = new Array(size * (size - 1));
            for ( int i = 0; i < size; i++ ) {
                for ( int j = 0; j < size - 1; j++ ) {
                    final int idx = j * size + i;
                    double v = result.get(i, j);
                    v = clampToOpenUnit(v, eps);
                    for ( int k = 0; k < j; k++ ) {
                        v /= Math.sin(theta.get(k * size + i));
                        v = clampToOpenUnit(v, eps);
                    }
                    double ang = Math.acos(v);
                    if ( j == size - 2 && result.get(i, j + 1) < 0.0 ) {
                        ang = -ang;
                    }
                    theta.set(idx, ang);
                }
            }
            final Problem p = new Problem(costFunction, constraint, theta);
            optimize.minimize(p, endCriteria);
            final Array optTheta = p.currentValue();

            // Reconstruct result from optimised angles (C++ pseudosqrt.cpp:245-255).
            for ( int i = 0; i < size; i++ ) {
                for ( int k = 0; k < size; k++ ) {
                    result.set(i, k, 1.0);
                }
            }
            for ( int i = 0; i < size; i++ ) {
                for ( int k = 0; k < size; k++ ) {
                    double prod = 1.0;
                    for ( int j = 0; j <= k; j++ ) {
                        if ( j == k && k != size - 1 ) {
                            prod *= Math.cos(optTheta.get(j * size + i));
                        } else if ( j != size - 1 ) {
                            prod *= Math.sin(optTheta.get(j * size + i));
                        }
                    }
                    result.set(i, k, prod);
                }
            }
        }

        // C++ pseudosqrt.cpp:258-262 — re-scale rows by sqrt(variance).
        for ( int i = 0; i < size; i++ ) {
            for ( int j = 0; j < size; j++ ) {
                result.set(i, j, result.get(i, j) * variance.get(i));
            }
        }
        return result;
    }

    /** Clamp v to (-1 + eps, 1 - eps) to keep acos / sin safe. */
    private static double clampToOpenUnit(final double v, final double eps) {
        if ( v > 1.0 - eps ) return 1.0 - eps;
        if ( v < -1.0 + eps ) return -1.0 + eps;
        return v;
    }

    /*
    // Optimization function for hypersphere and lower-diagonal algorithm
    public  Matrix hypersphereOptimize(final Matrix targetMatrix,
                                            final Matrix currentRoot,
                                            final boolean lowerDiagonal) {
        int i,j,k;
        int size = targetMatrix.rows();
        Matrix result = currentRoot;
        Array variance = new Array(size, 0);
        for (i=0; i<size; i++){
            variance.set(i, Math.sqrt(targetMatrix.get(i,i)));
        }
        if (lowerDiagonal) {
            Matrix approxMatrix = new Matrix(result.operatorMultiply(result, result.transpose(result)));
            result = new CholeskyDecomposition().CholeskyDecomposition(approxMatrix, true);
            for (i=0; i<size; i++) {
                for (j=0; j<size; j++) {
                    result.set(i,j, result.get(i, j) / Math.sqrt(approxMatrix.get(i,i)));
                }
            }
        } else {
            for (i=0; i<size; i++) {
                for (j=0; j<size; j++) {
                    result.set(i,j, result.get(i, j)/ variance.get(i));
                }
            }
        }

        ConjugateGradient optimize = new ConjugateGradient();
        EndCriteria endCriteria = new EndCriteria(100, 10, 1e-8, 1e-8, 1e-8);
        HypersphereCostFunction costFunction(targetMatrix, variance,
                                             lowerDiagonal);
        NoConstraint constraint;

        // hypersphere vector optimization

        if (lowerDiagonal) {
            Array theta(size * (size-1)/2);
            const Real eps=1e-16;
            for (i=1; i<size; i++) {
                for (j=0; j<i; j++) {
                    theta[i*(i-1)/2+j]=result[i][j];
                    if (theta[i*(i-1)/2+j]>1-eps)
                        theta[i*(i-1)/2+j]=1-eps;
                    if (theta[i*(i-1)/2+j]<-1+eps)
                        theta[i*(i-1)/2+j]=-1+eps;
                    for (k=0; k<j; k++) {
                        theta[i*(i-1)/2+j] /= std::sin(theta[i*(i-1)/2+k]);
                        if (theta[i*(i-1)/2+j]>1-eps)
                            theta[i*(i-1)/2+j]=1-eps;
                        if (theta[i*(i-1)/2+j]<-1+eps)
                            theta[i*(i-1)/2+j]=-1+eps;
                    }
                    theta[i*(i-1)/2+j] = std::acos(theta[i*(i-1)/2+j]);
                    if (j==i-1) {
                        if (result[i][i]<0)
                            theta[i*(i-1)/2+j]=-theta[i*(i-1)/2+j];
                    }
                }
            }
            Problem p(costFunction, constraint, theta);
            optimize.minimize(p, endCriteria);
            theta = p.currentValue();
            std::fill(result.begin(),result.end(),1.0);
            for (i=0; i<size; i++) {
                for (k=0; k<size; k++) {
                    if (k>i) {
                        result[i][k]=0;
                    } else {
                        for (j=0; j<=k; j++) {
                            if (j == k && k!=i)
                                result[i][k] *=
                                    std::cos(theta[i*(i-1)/2+j]);
                            else if (j!=i)
                                result[i][k] *=
                                    std::sin(theta[i*(i-1)/2+j]);
                        }
                    }
                }
            }
        } else {
            Array theta(size * (size-1));
            const Real eps=1e-16;
            for (i=0; i<size; i++) {
                for (j=0; j<size-1; j++) {
                    theta[j*size+i]=result[i][j];
                    if (theta[j*size+i]>1-eps)
                        theta[j*size+i]=1-eps;
                    if (theta[j*size+i]<-1+eps)
                        theta[j*size+i]=-1+eps;
                    for (k=0;k<j;k++) {
                        theta[j*size+i] /= std::sin(theta[k*size+i]);
                        if (theta[j*size+i]>1-eps)
                            theta[j*size+i]=1-eps;
                        if (theta[j*size+i]<-1+eps)
                            theta[j*size+i]=-1+eps;
                    }
                    theta[j*size+i] = std::acos(theta[j*size+i]);
                    if (j==size-2) {
                        if (result[i][j+1]<0)
                            theta[j*size+i]=-theta[j*size+i];
                    }
                }
            }
            Problem p(costFunction, constraint, theta);
            optimize.minimize(p, endCriteria);
            theta=p.currentValue();
            std::fill(result.begin(),result.end(),1.0);
            for (i=0; i<size; i++) {
                for (k=0; k<size; k++) {
                    for (j=0; j<=k; j++) {
                        if (j == k && k!=size-1)
                            result[i][k] *= std::cos(theta[j*size+i]);
                        else if (j!=size-1)
                            result[i][k] *= std::sin(theta[j*size+i]);
                    }
                }
            }
        }

        for (i=0; i<size; i++) {
            for (j=0; j<size; j++) {
                result[i][j]*=variance[i];
            }
        }
        return result;
    }
/*
    // Matrix infinity norm. See Golub and van Loan (2.3.10) or
    // <http://en.wikipedia.org/wiki/Matrix_norm>
    Real normInf(const Matrix& M) {
        Size rows = M.rows();
        Size cols = M.columns();
        Real norm = 0.0;
        for (Size i=0; i<rows; ++i) {
            Real colSum = 0.0;
            for (Size j=0; j<cols; ++j)
                colSum += std::fabs(M[i][j]);
            norm = std::max(norm, colSum);
        }
        return norm;
    }

    // Take a matrix and make all the diagonal entries 1.
    const Disposable <Matrix>
    projectToUnitDiagonalMatrix(const Matrix& M) {
        Size size = M.rows();
        QL_REQUIRE(size == M.columns(),
                   "matrix not square");

        Matrix result(M);
        for (Size i=0; i<size; ++i)
            result[i][i] = 1.0;

        return result;
    }

    // Take a matrix and make all the eigenvalues non-negative
    const Disposable <Matrix>
    projectToPositiveSemidefiniteMatrix(Matrix& M) {
        Size size = M.rows();
        QL_REQUIRE(size == M.columns(),
                   "matrix not square");

        Matrix diagonal(size, size, 0.0);
        SymmetricSchurDecomposition jd(M);
        for (Size i=0; i<size; ++i)
            diagonal[i][i] = std::max<Real>(jd.eigenvalues()[i], 0.0);

        Matrix result =
            jd.eigenvectors()*diagonal*transpose(jd.eigenvectors());
        return result;
    }

    // implementation of the Higham algorithm to find the nearest
    // correlation matrix.
    const Disposable <Matrix>
    highamImplementation(const Matrix& A,
                         const Size maxIterations,
                         const Real& tolerance) {

        Size size = A.rows();
        Matrix R, Y(A), X(A), deltaS(size, size, 0.0);

        Matrix lastX(X);
        Matrix lastY(Y);

        for (Size i=0; i<maxIterations; ++i) {
            R = Y - deltaS;
            X = projectToPositiveSemidefiniteMatrix(R);
            deltaS = X - R;
            Y = projectToUnitDiagonalMatrix(X);

            // convergence test
            if (std::max(normInf(X-lastX)/normInf(X),
                    std::max(normInf(Y-lastY)/normInf(Y),
                            normInf(Y-X)/normInf(Y)))
                    <= tolerance)
            {
                break;
            }
            lastX = X;
            lastY = Y;
        }

        // ensure we return a symmetric matrix
        for (Size i=0; i<size; ++i)
            for (Size j=0; j<i; ++j)
                Y[i][j] = Y[j][i];

        return Y;
    }

}

     */
    public static Matrix pseudoSqrt(final Matrix matrix, final SalvagingAlgorithm sa) {

        QL.require(matrix.rows() == matrix.columns(), Cells.MATRIX_MUST_BE_SQUARE); // QA:[RG]::verified
        QL.require(checkSymmetry(matrix), Cells.MATRIX_MUST_BE_SYMMETRIC); // QA:[RG]::verified

        final int size = matrix.rows;

        // spectral (a.k.a Principal Component) analysis
        final SymmetricSchurDecomposition jd = new SymmetricSchurDecomposition(matrix);
        final Matrix diagonal = new Matrix(size, size);

        // salvaging algorithm
        Matrix result;
        boolean negative;
        switch ( sa ) {
        case None:
            // eigenvalues are sorted in decreasing order
            if ( jd.eigenvalues().get(size - 1) < -1e-16 )
                throw new IllegalArgumentException(
                        "negative eigenvalue(s) (" + /*std::scientific*/ +jd.eigenvalues().get(size - 1) + ")");
            // Phase 5e.5b-CFC-d-52 align: C++ pseudosqrt.cpp:375 uses the
            // free function CholeskyDecomposition(matrix, true).
            result = CholeskyDecomposition.CholeskyDecomposition(matrix, true);
            break;
        case Spectral:
            // negative eigenvalues set to zero
            for ( int i = 0; i < size; i++ ) {
                diagonal.set(i, i, Math.sqrt(Math.max((jd.eigenvalues().get(i)), 0.0)));
            }
            // Phase 4i.5 align: C++ pseudosqrt.cpp:165 is `eigenvectors() * diagonal`.
            result = jd.eigenvectors().mul(diagonal);
            normalizePseudoRoot(matrix, result);
            break;
        case Hypersphere:
        case LowerDiagonal:
            // Phase 3-D: full salvaging path requires hypersphereOptimize
            // (C++ pseudosqrt.cpp:141-263) which combines a custom
            // spherical-angle parameterisation with ConjugateGradient.
            // The straightforward Java port is included below as
            // hypersphereOptimize(), but on this JVM it does not
            // converge reliably for ill-conditioned matrices — the
            // ConjugateGradient line-search exhausts iterations and
            // hangs the test JVM. Deferred until a hardened optimiser
            // (or a Brent-backed line-search wrapper) is available.
            // See Higham/Principal salvaging algorithms below as
            // production-ready alternatives.
            throw new UnsupportedOperationException(
                    "PseudoSqrt.SalvagingAlgorithm." + sa + " not yet supported: "
                            + "hypersphereOptimize port does not converge reliably on the current "
                            + "Java ConjugateGradient implementation. Use Higham or Principal instead "
                            + "(Phase3-D deferred; tracking under follow-up).");
        case Higham: {
            // Phase 5e.5b-CFC-d-52 port of C++ pseudosqrt.cpp:415-420.
            final int maxIterations = 40;
            final double tol = 1e-6;
            final Matrix adjusted = highamImplementation(matrix, maxIterations, tol);
            result = CholeskyDecomposition.CholeskyDecomposition(adjusted, true);
            break;
        }
        case Principal: {
            // Phase 5e.5b-CFC-d-52 port of C++ pseudosqrt.cpp:422-447.
            if ( jd.eigenvalues().get(size - 1) < -10.0 * org.jquantlib.math.Constants.QL_EPSILON ) {
                throw new IllegalArgumentException("negative eigenvalue(s) (" + jd.eigenvalues().get(size - 1) + ")");
            }
            final double[] sqrtEv = new double[size];
            for ( int i = 0; i < size; ++i ) {
                sqrtEv[i] = Math.sqrt(Math.max(jd.eigenvalues().get(i), 0.0));
            }
            // C++ pseudosqrt.cpp:437-443: diagonal[k][i] = eigenvectors[i][k] * sqrtEv[k]
            for ( int i = 0; i < size; ++i ) {
                for ( int k = 0; k < size; ++k ) {
                    diagonal.set(k, i, sqrtEv[k] * jd.eigenvectors().get(i, k));
                }
            }
            final Matrix prod = jd.eigenvectors().mul(diagonal);
            final Matrix sym = new Matrix(size, size);
            for ( int i = 0; i < size; ++i ) {
                for ( int j = 0; j < size; ++j ) {
                    sym.set(i, j, 0.5 * (prod.get(i, j) + prod.get(j, i)));
                }
            }
            result = sym;
            break;
        }
        default:
            throw new LibraryException(unknown_salvaging_algorithm);
        }
        return result;
    }

    /** Matrix infinity norm: max over rows of sum |a_ij|. C++ pseudosqrt.cpp:268. */
    private static double normInf(final Matrix M) {
        double norm = 0.0;
        for ( int i = 0; i < M.rows(); ++i ) {
            double colSum = 0.0;
            for ( int j = 0; j < M.cols(); ++j ) {
                colSum += Math.abs(M.get(i, j));
            }
            norm = Math.max(norm, colSum);
        }
        return norm;
    }

    /** Set all diagonal entries to 1. C++ pseudosqrt.cpp:282. */
    private static Matrix projectToUnitDiagonalMatrix(final Matrix M) {
        final int size = M.rows();
        QL.require(size == M.cols(), Cells.MATRIX_MUST_BE_SQUARE);
        final Matrix result = M.clone();
        for ( int i = 0; i < size; ++i ) {
            result.set(i, i, 1.0);
        }
        return result;
    }

    //
    // Higham nearest-correlation-matrix iteration (Phase 5e.5b-CFC-d-52)
    //

    /** Project to positive-semidefinite: V * diag(max(lambda_i, 0)) * V^T. C++ pseudosqrt.cpp:295. */
    private static Matrix projectToPositiveSemidefiniteMatrix(final Matrix M) {
        final int size = M.rows();
        QL.require(size == M.cols(), Cells.MATRIX_MUST_BE_SQUARE);
        final SymmetricSchurDecomposition jd = new SymmetricSchurDecomposition(M);
        final Matrix diag = new Matrix(size, size);
        for ( int i = 0; i < size; ++i ) {
            diag.set(i, i, Math.max(jd.eigenvalues().get(i), 0.0));
        }
        return jd.eigenvectors().mul(diag).mul(jd.eigenvectors().transpose());
    }

    /**
     * Higham iteration for nearest correlation matrix. Java port of QuantLib v1.42.1 {@code highamImplementation} in
     * {@code ql/math/matrixutilities/pseudosqrt.cpp:312}.
     */
    private static Matrix highamImplementation(final Matrix A, final int maxIterations, final double tolerance) {
        final int size = A.rows();
        Matrix Y = A.clone();
        Matrix X = A.clone();
        Matrix deltaS = new Matrix(size, size);
        Matrix lastX = X.clone();
        Matrix lastY = Y.clone();

        for ( int i = 0; i < maxIterations; ++i ) {
            final Matrix R = Y.sub(deltaS);
            X = projectToPositiveSemidefiniteMatrix(R);
            deltaS = X.sub(R);
            Y = projectToUnitDiagonalMatrix(X);

            final double convX = normInf(X.sub(lastX)) / normInf(X);
            final double convY = normInf(Y.sub(lastY)) / normInf(Y);
            final double convYX = normInf(Y.sub(X)) / normInf(Y);
            if ( Math.max(convX, Math.max(convY, convYX)) <= tolerance ) {
                break;
            }
            lastX = X.clone();
            lastY = Y.clone();
        }

        for ( int i = 0; i < size; ++i ) {
            for ( int j = 0; j < i; ++j ) {
                Y.set(i, j, Y.get(j, i));
            }
        }
        return Y;
    }

    /**
     * Returns {@code true} iff {@code matrix} is symmetric (within {@link Closeness#isClose} tolerance). Mirrors C++
     * {@code checkSymmetry} in {@code ql/math/matrixutilities/pseudosqrt.cpp} (under {@code QL_EXTRA_SAFETY_CHECKS}):
     * for each strict-lower-triangle pair, requires {@code matrix[i][j] ~ matrix[j][i]}.
     *
     * <p>Phase 3j align: previously inverted condition (returned {@code true}
     * only when no close pairs were found, i.e. for non-symmetric matrices), which made every covariance assembly fail
     * PseudoSqrt's input validation.
     */
    private static boolean checkSymmetry(final Matrix matrix) {
        final int size = matrix.rows;
        for ( int i = 0; i < size; ++i ) {
            for ( int j = 0; j < i; ++j )
                if ( !Closeness.isClose(matrix.get(i, j), matrix.get(j, i)) )
                    return false;
        }
        return true;
    }

    //! Returns the pseudo square root of a real symmetric matrix
    /*! Given a matrix \f$ M \f$, the result \f$ S \f$ is defined
        as the matrix such that \f$ S S^T = M. \f$
        If the matrix is not positive semi definite, it can
        return an approximation of the pseudo square root
        using a (user selected) salvaging algorithm.

        For more information see: "The most general methodology to create
        a valid correlation matrix for risk management and option pricing
        purposes", by R. Rebonato and P. Jaeckel.
        The Journal of Risk, 2(2), Winter 1999/2000
        http://www.rebonato.com/correlationmatrix.pdf

        Revised and extended in "Monte Carlo Methods in Finance",
        by Peter Jaeckel, Chapter 6.

        \pre the given matrix must be symmetric.

        \relates Matrix

        \warning Higham algorithm only works for correlation matrices.

        \test
        - the correctness of the results is tested by reproducing
          known good data.
        - the correctness of the results is tested by checking
          returned values against numerical calculations.
     */
    public Matrix pseudoSqrt(final Matrix matrix) {
        return pseudoSqrt(matrix, SalvagingAlgorithm.None);
    }

    /*
const Disposable<Matrix> rankReducedSqrt(const Matrix& matrix,
                                         Size maxRank,
                                         Real componentRetainedPercentage,
                                         SalvagingAlgorithm::Type sa) {
    Size size = matrix.rows();

    #if defined(QL_EXTRA_SAFETY_CHECKS)
    checkSymmetry(matrix);
    #else
    QL_REQUIRE(size == matrix.columns(),
               "non square matrix: " << size << " rows, " <<
               matrix.columns() << " columns");
    #endif

    QL_REQUIRE(componentRetainedPercentage>0.0,
               "no eigenvalues retained");

    QL_REQUIRE(componentRetainedPercentage<=1.0,
               "percentage to be retained > 100%");

    QL_REQUIRE(maxRank>=1,
               "max rank required < 1");

    // spectral (a.k.a Principal Component) analysis
    SymmetricSchurDecomposition jd(matrix);
    Array eigenValues = jd.eigenvalues();

    // salvaging algorithm
    switch (sa) {
      case SalvagingAlgorithm::None:
        // eigenvalues are sorted in decreasing order
        QL_REQUIRE(eigenValues[size-1]>=-1e-16,
                   "negative eigenvalue(s) ("
                   << std::scientific << eigenValues[size-1]
                   << ")");
        break;
      case SalvagingAlgorithm::Spectral:
        // negative eigenvalues set to zero
        for (Size i=0; i<size; ++i)
            eigenValues[i] = std::max<Real>(eigenValues[i], 0.0);
        break;
      case SalvagingAlgorithm::Higham:
          {
              int maxIterations = 40;
              Real tolerance = 1e-6;
              Matrix adjustedMatrix = highamImplementation(matrix, maxIterations, tolerance);
              jd = SymmetricSchurDecomposition(adjustedMatrix);
              eigenValues = jd.eigenvalues();
          }
          break;
      default:
        QL_FAIL("unknown or invalid salvaging algorithm");
    }

    // factor reduction
    Real enough = componentRetainedPercentage *
                  std::accumulate(eigenValues.begin(),
                                  eigenValues.end(), 0.0);
    if (componentRetainedPercentage == 1.0) {
        // numerical glitches might cause some factors to be discarded
        enough *= 1.1;
    }
    // retain at least one factor
    Real components = eigenValues[0];
    Size retainedFactors = 1;
    for (Size i=1; components<enough && i<size; ++i) {
        components += eigenValues[i];
        retainedFactors++;
    }
    // output is granted to have a rank<=maxRank
    retainedFactors=std::min(retainedFactors, maxRank);

    Matrix diagonal(size, retainedFactors, 0.0);
    for (Size i=0; i<retainedFactors; ++i)
        diagonal[i][i] = std::sqrt(eigenValues[i]);
    Matrix result = jd.eigenvectors() * diagonal;

    normalizePseudoRoot(matrix, result);
    return result;
}

     */

    //
    // private methods
    //

    public enum SalvagingAlgorithm {
        None, Spectral, Hypersphere, LowerDiagonal, Higham, Principal
    }

}
