/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

/*
 Copyright (C) 2007, 2008 Mark Joshi
*/

package org.jquantlib.math.matrixutilities;

import org.jquantlib.QL;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds an ordered basis for an incomplete subspace by Gram-Schmidt orthonormalization.
 *
 * <p>Java port of {@code ql/math/matrixutilities/basisincompleteordered.{hpp,cpp}}
 * (QuantLib v1.42.1). Phase 3j Track B align — replaces the prior stub.
 */
public class BasisIncompleteOrdered {

    private final int euclideanDimension_;
    private final List< double[] > currentBasis_ = new ArrayList<>();

    public BasisIncompleteOrdered(final int euclideanDimension) {
        this.euclideanDimension_ = euclideanDimension;
    }

    /**
     * Adds a new vector to the basis. Returns true if it was linearly independent (i.e., admitted into the basis after
     * orthonormalization).
     *
     * @param newVector1 vector of length {@link #euclideanDimension()}
     * @return true if added, false if linearly dependent on existing basis
     */
    public boolean addVector(final double[] newVector1) {
        QL.require(newVector1.length == euclideanDimension_,
                "missized vector passed to BasisIncompleteOrdered.addVector");

        if ( currentBasis_.size() == euclideanDimension_ ) {
            return false;
        }

        final double[] v = newVector1.clone();
        for ( final double[] basisVec : currentBasis_ ) {
            double innerProd = 0.0;
            for ( int k = 0; k < euclideanDimension_; ++k ) {
                innerProd += v[k] * basisVec[k];
            }
            for ( int k = 0; k < euclideanDimension_; ++k ) {
                v[k] -= innerProd * basisVec[k];
            }
        }

        double normSq = 0.0;
        for ( final double e : v )
            normSq += e * e;
        final double norm = Math.sqrt(normSq);

        if ( norm < 1e-12 ) {
            return false;
        }
        for ( int l = 0; l < euclideanDimension_; ++l ) {
            v[l] /= norm;
        }
        currentBasis_.add(v);
        return true;
    }

    /** Convenience overload: takes Array. */
    public boolean addVector(final Array newVector) {
        final double[] arr = new double[newVector.size()];
        for ( int i = 0; i < arr.length; ++i ) {
            arr[i] = newVector.get(i);
        }
        return addVector(arr);
    }

    public int basisSize() {
        return currentBasis_.size();
    }

    public int euclideanDimension() {
        return euclideanDimension_;
    }

    /** Returns the current basis as the rows of a matrix. */
    public Matrix getBasisAsRowsInMatrix() {
        final int rows = currentBasis_.size();
        final Matrix basis = new Matrix(rows, euclideanDimension_);
        for ( int i = 0; i < rows; ++i ) {
            final double[] v = currentBasis_.get(i);
            for ( int j = 0; j < euclideanDimension_; ++j ) {
                basis.set(i, j, v[j]);
            }
        }
        return basis;
    }
}
