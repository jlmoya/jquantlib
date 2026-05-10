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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.methods.montecarlo;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/pathgenerator.cpp (Phase 5a).
 *
 * <p>2 BOOST_AUTO_TEST_CASE methods.
 *
 * <p>{@code testPathGenerator} requires
 * {@code PseudoRandom::make_sequence_generator} (Java has different API)
 * plus the cached values that depend on bit-exact {@code MersenneTwister}
 * sequence — feasible but the static helper is not yet ported as a
 * one-liner and requires a per-fixture wrapper.
 *
 * <p>{@code testMultiPathGenerator} requires
 * {@code MultiPathGenerator}, which JQuantLib does not have; only
 * {@code SobolBrownianGenerator} family is present. Phase 5a.5
 * carry-forward.
 */
public class PathGeneratorTest {

    public PathGeneratorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5a.5: PseudoRandom.makeSequenceGenerator now exists; empty test body — needs full port from C++ pathgenerator.cpp::testPathGenerator with cached MT-dependent path values.")
    @Test
    public void testPathGenerator() {
    }

    @Ignore("Phase 5a.5: MultiPathGenerator now ported (commit ebb9e3e7); empty test body — needs full port from C++ pathgenerator.cpp::testMultiPathGenerator. Note: a structural MultiPathGeneratorTest already exists (commit c9717fb1).")
    @Test
    public void testMultiPathGenerator() {
    }
}
