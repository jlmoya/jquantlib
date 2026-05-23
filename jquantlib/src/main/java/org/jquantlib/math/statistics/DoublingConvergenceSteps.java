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
package org.jquantlib.math.statistics;

/**
 * Default convergence-table sampling rule for {@link ConvergenceStatistics}:
 * doubling sample sizes (1, 3, 7, 15, ...; i.e., {@code 2*current + 1}).
 *
 * <p>Faithful Java port of {@code QuantLib::DoublingConvergenceSteps}
 * (v1.42.1 {@code ql/math/statistics/convergencestatistics.hpp}, pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Previously embedded as a private inner class of {@link ConvergenceStatistics};
 * promoted to a top-level public class so the canonical C++ symbol is available
 * for callers and tests.
 *
 * <p>Phase 2 L1-D port.
 */
public class DoublingConvergenceSteps {

    public int initialSamples() {
        return 1;
    }

    public int nextSamples(final int current) {
        return 2 * current + 1;
    }
}
