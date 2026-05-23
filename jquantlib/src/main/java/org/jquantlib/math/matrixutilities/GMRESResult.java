/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

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
package org.jquantlib.math.matrixutilities;

import java.util.List;

/**
 * Result of a {@link GMRES} solve as a JDK 25 record.
 *
 * <p>Faithful Java port of {@code QuantLib::GMRESResult}
 * (v1.42.1 {@code ql/math/matrixutilities/gmres.hpp}, pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Mirrors C++ {@code struct GMRESResult { std::list<Real> errors; Array x; }}.
 * Top-level record form for new callers; the existing {@link GMRES.Result}
 * inner class is preserved for source compatibility and exposes the same
 * components.
 *
 * <p>Phase 2 L1-D port.
 *
 * @param errors per-iteration relative-residual errors
 * @param x      solution vector
 */
public record GMRESResult(List<Double> errors, Array x) {

    /** Wrap a {@link GMRES.Result} as a {@link GMRESResult} record. */
    public static GMRESResult from(final GMRES.Result r) {
        return new GMRESResult(r.errors, r.x);
    }
}
