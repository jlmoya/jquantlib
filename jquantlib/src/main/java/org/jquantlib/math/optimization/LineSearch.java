/*
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2001, 2002, 2003 Nicolas Di Césaré
 Copyright (C) 2009 Ueli Hofstetter
 Copyright (C) 2026 Jose Moya (Java port)

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

/**
 * Abstract base class for line-search strategies.
 *
 * <p>Faithful port of QuantLib C++ v1.42.1
 * {@code ql/math/optimization/linesearch.hpp|cpp}. In C++ {@code operator()} is
 * pure-virtual; Java makes the equivalent {@link #evaluate} abstract. Subclasses
 * such as {@link ArmijoLineSearch} override it.
 */
public abstract class LineSearch {

    /** current values of the search direction */
    protected Array searchDirection_;

    /** new x and its gradient */
    protected Array xtd_, gradient_;

    /** cost function value and gradient norm corresponding to xtd_ */
    protected double qt_, qpt_;

    /** whether the last linesearch succeeded */
    protected boolean succeed_ = true;

    //-- LineSearch(Real init = 0.0);
    //-- in ql/math/optimization/linesearch.hpp:41 (header-only, empty body)
    protected LineSearch() {
        this(0.0);
    }

    protected LineSearch(final double init) {
        this.qt_ = init;
        this.qpt_ = init;
    }

    /** @return last x value evaluated by the linesearch. */
    public Array lastX() {
        return xtd_;
    }

    /** @return last cost-function value evaluated. */
    public double lastFunctionValue() {
        return qt_;
    }

    /** @return last gradient. */
    public Array lastGradient() {
        return gradient_;
    }

    //-- Real lastGradientNorm2() const;
    //-- in ql/math/optimization/linesearch.hpp:52
    /** @return square norm of last gradient. */
    public double lastGradientNorm2() {
        return qpt_;
    }

    //-- bool succeed() const;
    //-- in ql/math/optimization/linesearch.hpp:54
    public boolean succeed() {
        return succeed_;
    }

    /** @return current value of the search direction. */
    public Array searchDirection() {
        return searchDirection_;
    }

    public void setSearchDirection(final Array searchDirection) {
        this.searchDirection_ = searchDirection;
    }

    //-- virtual Real operator()(Problem& P, EndCriteria::Type& ecType,
    //--                         const EndCriteria&, Real t_ini) = 0;
    //-- in ql/math/optimization/linesearch.hpp:57
    /**
     * Perform line search. Pure abstract to mirror C++'s pure-virtual
     * {@code operator()}. Subclasses implement the actual strategy.
     *
     * @param ecType  one-element array holding the end-criteria type to mutate
     *                (Java's pass-by-reference emulation for C++ {@code Type&}).
     */
    public abstract double evaluate(Problem P, EndCriteria.Type[] ecType,
                                    EndCriteria endCriteria, double t_ini);

    //-- Real update(Array& params, const Array& direction,
    //--             Real beta, const Constraint& constraint);
    //-- in ql/math/optimization/linesearch.cpp (header-only implementation; body
    //-- in the class body of the hpp for older releases, .cpp in v1.42.1)
    /**
     * Halve {@code beta} until {@code params + beta*direction} satisfies
     * {@code constraint}, then apply the step to {@code params} in place and
     * return the accepted {@code beta}.
     *
     * <p>The C++ version mutates {@code params} via {@code params += diff * direction}.
     * The Java version uses {@link Array#addAssign} for the same effect — the previous
     * Java implementation called {@code params.add(...)} (non-mutating), leaving
     * {@code params} unchanged; that was a port bug.
     */
    public double update(final Array params, final Array direction,
                         final double beta, final Constraint constraint) {
        double diff = beta;
        Array newParams = params.add(direction.mul(diff));
        boolean valid = constraint.test(newParams);
        int icount = 0;
        while (!valid) {
            QL.require(icount <= 200, "can't update linesearch");
            diff *= 0.5;
            icount++;
            newParams = params.add(direction.mul(diff));
            valid = constraint.test(newParams);
        }
        params.addAssign(direction.mul(diff));  // MUTATE params in place, matching C++.
        return diff;
    }
}
