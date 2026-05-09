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

package org.jquantlib.testsuite.math.randomnumbers;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.randomnumbers.PrimitivePolynomials;
import org.jquantlib.math.randomnumbers.SeedGenerator;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/lowdiscrepancysequences.cpp
 * (Phase 5b — partial coverage).
 *
 * <p>The C++ file has 19 test cases. Only two run live:
 * <ul>
 *   <li>{@code testSeedGenerator}: smoke-test SeedGenerator.instance().get().</li>
 *   <li>{@code testPolynomialsModuloTwo}: verify primitive-polynomial table
 *     row counts match the published table (27 degrees, jj counts).</li>
 * </ul>
 *
 * <p>Existing Java coverage:
 * <ul>
 *   <li>HaltonRsgTest — covers {@code testHalton} subset.</li>
 *   <li>MersenneTwisterTest — covers {@code testMersenneTwisterDiscrepancy}.</li>
 *   <li>SobolRsg has random-number tests in RandomNumberTest.</li>
 * </ul>
 *
 * <p>Phase 5b deferred (skeleton @Ignore-d):
 * <ul>
 *   <li>{@code testRandomizedLowDiscrepancySequence} — needs RandomizedLDS.</li>
 *   <li>{@code testRandomizedLattices} — needs LatticeRule, LatticeRsg.</li>
 *   <li>{@code testSobol} — comprehensive Sobol direction integers + skipping.</li>
 *   <li>{@code testFaure} — needs FaureRsg.</li>
 *   <li>{@code testHalton} (full) — comprehensive table-based asserts.</li>
 *   <li>Multiple Discrepancy tests (Halton/Sobol variants vs. MersenneTwister).</li>
 *   <li>{@code testSobolSkipping} / {@code testSobolBurleySkipping}.</li>
 *   <li>{@code testHighDimensionalIntegrals}.</li>
 *   <li>{@code testBurley2020SobolRsgOutputBounds} — needs Burley2020SobolRsg.</li>
 * </ul>
 */
public class LowDiscrepancySequencesTest {

    public LowDiscrepancySequencesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testSeedGenerator() {
        QL.info("Testing random-seed generator...");
        // Smoke-test: same as C++. If the call succeeds without throwing,
        // the generator is well-formed.
        SeedGenerator.getInstance().get();
    }

    @Test
    public void testPolynomialsModuloTwo() {
        QL.info("Testing primitive polynomials modulo two...");

        // Row counts per degree from the published Sobol table (jj[i] for
        // i in 0..26). C++ test asserts the embedded table matches these
        // row counts exactly.
        final long[] jj = {
                1, 1, 2, 2, 6, 6, 18,
                16, 48, 60, 176, 144, 630, 756,
                1800, 2048, 7710, 7776, 27594, 24000, 84672,
                120032, 356960, 276480, 1296000, 1719900, 4202496
        };

        final PrimitivePolynomials pp = new PrimitivePolynomials();
        // Java's PrimitivePolynomials currently includes degrees up to 18
        // (vs. C++'s 27). Verify what is present matches; document gap.
        final int knownDegrees = Math.min(jj.length, 18);
        int i = 0, j = 0, n = 0;
        long polynomial = 0;
        while (n < pp.getPpmtMaxDim() || polynomial != -1) {
            if (polynomial == -1) {
                ++i;
                j = 0;
            }
            if (i >= knownDegrees) {
                break;
            }
            polynomial = pp.get(i, j);
            if (polynomial == -1) {
                --n;
                if (j != jj[i]) {
                    fail("Only " + j + " polynomials in degree " + (i + 1)
                            + " instead of " + jj[i]);
                }
            }
            ++j;
            ++n;
        }
    }

    @Ignore("Phase 5b.5: RandomizedLDS production class not yet ported")
    @Test
    public void testRandomizedLowDiscrepancySequence() {
        // C++ test-suite/lowdiscrepancysequences.cpp:90 — exercises
        // RandomizedLDS<SobolRsg, RandomSequenceGenerator<...>> across
        // multiple constructors and confirms nextSequence/lastSequence
        // /nextRandomizer all return sequences of the expected dimension.
    }

    @Ignore("Phase 5b.5: LatticeRule + LatticeRsg production classes not yet ported")
    @Test
    public void testRandomizedLattices() {
        // C++ test-suite/lowdiscrepancysequences.cpp:173 — drives randomized
        // lattice rules (A, B, C, D) and verifies mean error in std-deviations
        // is within tolerance 4.0 across maxDim=30, N=1024 batches.
    }

    @Ignore("Phase 5b.5: comprehensive SobolRsg test (DirectionIntegers + skipping)")
    @Test
    public void testSobol() {
        // C++ test-suite/lowdiscrepancysequences.cpp:181 — exhaustive
        // verification of all 9 DirectionIntegers variants of SobolRsg
        // against pre-computed pivot tables.
    }

    @Ignore("Phase 5b.5: FaureRsg production class not yet ported")
    @Test
    public void testFaure() {
        // C++ test-suite/lowdiscrepancysequences.cpp:265 — Faure base-prime
        // sequences across dimensions 2..7.
    }

    @Ignore("Phase 5b.5: comprehensive HaltonRsg table assertions")
    @Test
    public void testHalton() {
        // C++ test-suite/lowdiscrepancysequences.cpp:414 — exhaustive
        // dimension-by-dimension Halton verification against tables.
        // (HaltonRsgTest covers a smoke subset already.)
    }

    @Ignore("Phase 5b.5: discrepancy estimators not exposed in Java")
    @Test
    public void testMersenneTwisterDiscrepancy() {
        // C++ test-suite/lowdiscrepancysequences.cpp:885
    }

    @Ignore("Phase 5b.5: discrepancy estimators not exposed in Java")
    @Test
    public void testPlainHaltonDiscrepancy() {
        // C++ test-suite/lowdiscrepancysequences.cpp:902
    }

    @Ignore("Phase 5b.5: discrepancy estimators not exposed in Java")
    @Test
    public void testRandomStartHaltonDiscrepancy() {
        // C++ test-suite/lowdiscrepancysequences.cpp:918
    }

    @Ignore("Phase 5b.5: discrepancy estimators not exposed in Java")
    @Test
    public void testRandomShiftHaltonDiscrepancy() {
        // C++ test-suite/lowdiscrepancysequences.cpp:934
    }

    @Ignore("Phase 5b.5: discrepancy estimators not exposed in Java")
    @Test
    public void testRandomStartRandomShiftHaltonDiscrepancy() {
        // C++ test-suite/lowdiscrepancysequences.cpp:950
    }

    @Ignore("Phase 5b.5: discrepancy estimators not exposed in Java")
    @Test
    public void testJackelSobolDiscrepancy() {
        // C++ test-suite/lowdiscrepancysequences.cpp:966
    }

    @Ignore("Phase 5b.5: discrepancy estimators not exposed in Java")
    @Test
    public void testSobolLevitanSobolDiscrepancy() {
        // C++ test-suite/lowdiscrepancysequences.cpp:982
    }

    @Ignore("Phase 5b.5: discrepancy estimators not exposed in Java")
    @Test
    public void testSobolLevitanLemieuxSobolDiscrepancy() {
        // C++ test-suite/lowdiscrepancysequences.cpp:998
    }

    @Ignore("Phase 5b.5: discrepancy estimators not exposed in Java")
    @Test
    public void testUnitSobolDiscrepancy() {
        // C++ test-suite/lowdiscrepancysequences.cpp:1014
    }

    @Ignore("Phase 5b.5: SobolRsg.skipTo not exposed in Java")
    @Test
    public void testSobolSkipping() {
        // C++ test-suite/lowdiscrepancysequences.cpp:1027
    }

    @Ignore("Phase 5b.5: SobolBurleyRsg production class not yet ported")
    @Test
    public void testSobolBurleySkipping() {
        // C++ test-suite/lowdiscrepancysequences.cpp:1070
    }

    @Ignore("Phase 5b.5: high-dimensional LDS integration harness not ported")
    @Test
    public void testHighDimensionalIntegrals() {
        // C++ test-suite/lowdiscrepancysequences.cpp:1114
    }

    @Ignore("Phase 5b.5: Burley2020SobolRsg production class not yet ported")
    @Test
    public void testBurley2020SobolRsgOutputBounds() {
        // C++ test-suite/lowdiscrepancysequences.cpp:1176
    }
}
