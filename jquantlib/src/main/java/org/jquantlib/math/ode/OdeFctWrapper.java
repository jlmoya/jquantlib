/*
 Copyright (C) 2012 Peter Caspers
 Copyright (C) 2026 JQuantLib migration contributors.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.ode;

import org.jquantlib.math.Complex;

/**
 * Adapter that lifts a scalar {@code y' = f(t, y)} into the vector-state
 * shape expected by {@link AdaptiveRungeKutta}.
 *
 * <p>Faithful Java port of {@code QuantLib::detail::OdeFctWrapper<T>}
 * (v1.42.1 {@code ql/math/ode/adaptiverungekutta.hpp}, pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>{@link AdaptiveRungeKutta} already inlines the equivalent of this
 * wrapper as anonymous lambdas inside its scalar {@code solve(OdeFct1d, ...)}
 * and {@code solveComplex(OdeFctC1d, ...)} overloads. This top-level class is
 * provided as a documented, named adapter for callers porting C++ code that
 * references the {@code detail::OdeFctWrapper} symbol directly.
 *
 * <p>Phase 2 L1-D port.
 */
public final class OdeFctWrapper {

    private OdeFctWrapper() {
        /* utility class */
    }

    /**
     * Wrap a scalar real ODE into the vector-state form expected by
     * {@link AdaptiveRungeKutta.OdeFct}. Mirrors C++
     * {@code detail::OdeFctWrapper<Real>::operator()}.
     */
    public static AdaptiveRungeKutta.OdeFct wrap(final AdaptiveRungeKutta.OdeFct1d ode1d) {
        return (t, y) -> new double[] { ode1d.apply(t, y[0]) };
    }

    /**
     * Wrap a scalar complex ODE into the vector-state form expected by
     * {@link AdaptiveRungeKutta.OdeFctC}. Mirrors C++
     * {@code detail::OdeFctWrapper<std::complex<Real>>::operator()}.
     */
    public static AdaptiveRungeKutta.OdeFctC wrapComplex(final AdaptiveRungeKutta.OdeFctC1d ode1d) {
        return (t, y) -> new Complex[] { ode1d.apply(t, y[0]) };
    }
}
