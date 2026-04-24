/*
 Copyright (C) 2001, 2002, 2003 Nicolas Di Césaré
 Copyright (C) 2005 StatPro Italia srl
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

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;

//-- class LeastSquareFunction; in ql/math/optimization/leastsquare.hpp:60
/**
 * Cost function adapter for least-square problems — a port of QuantLib C++
 * v1.42.1 {@code LeastSquareFunction}. Wraps a {@link LeastSquareProblem}
 * and exposes it through the {@link CostFunction} interface so that the
 * generic optimization framework can minimize it.
 *
 * <p>Value is the squared L2 norm of the residual vector {@code target - f(x)};
 * gradient is {@code -2 * J^T * residual}, where J is the Jacobian of the
 * fit function.
 */
public class LeastSquareFunction extends CostFunction {

    //! least square problem
    protected final LeastSquareProblem lsp_;

    //-- LeastSquareFunction(LeastSquareProblem& lsp) : lsp_(lsp) {}
    //-- in ql/math/optimization/leastsquare.hpp:63
    public LeastSquareFunction(final LeastSquareProblem lsp) {
        this.lsp_ = lsp;
    }

    //-- Real LeastSquareFunction::value(const Array& x) const;
    //-- in ql/math/optimization/leastsquare.cpp:28
    @Override
    public double value(final Array x) {
        final Array target = new Array(lsp_.size());
        final Array fct2fit = new Array(lsp_.size());
        lsp_.targetAndValue(x, target, fct2fit);
        final Array diff = target.sub(fct2fit);
        return diff.dotProduct(diff);
    }

    //-- Array LeastSquareFunction::values(const Array& x) const;
    //-- in ql/math/optimization/leastsquare.cpp:39
    @Override
    public Array values(final Array x) {
        final Array target = new Array(lsp_.size());
        final Array fct2fit = new Array(lsp_.size());
        lsp_.targetAndValue(x, target, fct2fit);
        final Array diff = target.sub(fct2fit);
        return diff.mul(diff);  // element-wise square, matches C++ 'diff*diff'
    }

    //-- void LeastSquareFunction::gradient(Array& grad_f, const Array& x) const;
    //-- in ql/math/optimization/leastsquare.cpp:49
    @Override
    public void gradient(final Array grad_f, final Array x) {
        final Array target = new Array(lsp_.size());
        final Array fct2fit = new Array(lsp_.size());
        final Matrix grad_fct2fit = new Matrix(lsp_.size(), x.size());
        lsp_.targetValueAndGradient(x, grad_fct2fit, target, fct2fit);
        final Array diff = target.sub(fct2fit);
        // grad_f = -2 * (transpose(grad_fct2fit) * diff)
        final Array computed = grad_fct2fit.transpose().mul(diff).mul(-2.0);
        copyInto(grad_f, computed);
    }

    //-- Real LeastSquareFunction::valueAndGradient(Array& grad_f, const Array& x) const;
    //-- in ql/math/optimization/leastsquare.cpp:63
    @Override
    public double valueAndGradient(final Array grad_f, final Array x) {
        final Array target = new Array(lsp_.size());
        final Array fct2fit = new Array(lsp_.size());
        final Matrix grad_fct2fit = new Matrix(lsp_.size(), x.size());
        lsp_.targetValueAndGradient(x, grad_fct2fit, target, fct2fit);
        final Array diff = target.sub(fct2fit);
        final Array computed = grad_fct2fit.transpose().mul(diff).mul(-2.0);
        copyInto(grad_f, computed);
        return diff.dotProduct(diff);
    }

    /**
     * Copy {@code src} element-wise into {@code dst}. Java lacks C++'s
     * {@code Array operator=(Array)} semantic — the idiomatic Java way to
     * convey "fill this array with those values" is an explicit loop.
     */
    private static void copyInto(final Array dst, final Array src) {
        QL.require(dst.size() == src.size(),
                "gradient destination size (" + dst.size()
                        + ") != computed size (" + src.size() + ")");
        for (int i = 0; i < src.size(); i++) {
            dst.set(i, src.get(i));
        }
    }
}
