/*
 Copyright (C)
 2009 Ueli Hofstetter

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

package org.jquantlib.legacy.libormarkets;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.model.Parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for libor-market-model correlation models.
 *
 * <p>Java port of QuantLib v1.42.1 {@code legacy/libormarketmodels/lmcorrmodel.hpp}.
 */
public abstract class LmCorrelationModel {

    protected int size_;
    protected List< Parameter > arguments_;

    public LmCorrelationModel(final int size, final int nArguments) {
        this.size_ = size;
        this.arguments_ = new ArrayList< Parameter >(nArguments);
        for ( int i = 0; i < nArguments; ++i ) {
            this.arguments_.add(new Parameter());
        }
    }

    public int size() {
        return size_;
    }

    public int factors() {
        return size_;
    }

    public boolean isTimeIndependent() {
        return false;
    }

    public Matrix pseudoSqrt(final double t, final Array x) {
        return PseudoSqrt.pseudoSqrt(this.correlation(t, x), SalvagingAlgorithm.Spectral);
    }

    public Matrix pseudoSqrt(final double t) {
        return pseudoSqrt(t, new Array(0));
    }

    public double correlation(final int i, final int j, final double t, final Array x) {
        // inefficient implementation, please overload in derived classes
        return correlation(t, x).get(i, j);
    }

    public double correlation(final int i, final int j, final double t) {
        return correlation(t, new Array(0)).get(i, j);
    }

    public abstract Matrix correlation(double t, final Array x);

    public Matrix correlation(final double t) {
        return correlation(t, new Array(0));
    }

    public List< Parameter > params() {
        return arguments_;
    }

    public void setParams(final List< Parameter > arguments) {
        arguments_ = arguments;
        generateArguments();
    }

    protected abstract void generateArguments();

}
