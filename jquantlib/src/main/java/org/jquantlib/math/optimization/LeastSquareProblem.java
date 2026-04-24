/*
 Copyright (C) 2001, 2002, 2003 Nicolas Di Césaré
 Copyright (C) 2005, 2007 StatPro Italia srl
 Copyright (C) 2009 Ueli Hofstetter
 Copyright (C) 2026 Jose Moya (Java port update)

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.optimization;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

//-- class LeastSquareProblem; in ql/math/optimization/leastsquare.hpp:38
/**
 * Abstract base class for least-square problems — a port of QuantLib C++
 * v1.42.1 {@code LeastSquareProblem}. Subclasses supply the target vector
 * and the model function (optionally with its Jacobian) for fitting.
 */
public abstract class LeastSquareProblem {

    /** Size of the problem (length of the target vector). */
    public abstract int size();

    /** Compute the target vector and the values of the function to fit. */
    public abstract void targetAndValue(Array x, Array target, Array fct2fit);

    /**
     * Compute the target vector, the values of the function to fit, and the
     * matrix of derivatives (Jacobian) of the fit function.
     */
    public abstract void targetValueAndGradient(Array x,
                                                Matrix grad_fct2fit,
                                                Array target,
                                                Array fct2fit);
}
