/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.varianceoption;

import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/varianceoption.cpp} v1.42.1
 * (118 LOC, 1 test case).
 *
 * <p>The single C++ test {@code testIntegralHeston} exercises
 * {@code IntegralHestonVarianceOptionEngine} on a Heston-driven variance
 * option with a vanilla payoff.  This is <strong>already fully covered</strong>
 * by the existing Java test {@link IntegralHestonVarianceOptionEngineTest}
 * (Phase 4a A.2), which contains six cross-validated cases including the
 * value reproduced verbatim from {@code varianceoption.cpp}.
 *
 * <p>This class exists for inventory completeness (Phase 5 META requires a
 * 1:1 mapping of Java test classes to C++ files). The single placeholder
 * test below delegates to the canonical scenario in
 * {@link IntegralHestonVarianceOptionEngineTest} (the {@code qlts_call_*}
 * case is the direct port of the C++ {@code testIntegralHeston} input).
 *
 * <p>Source: {@code test-suite/varianceoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 *
 * @see IntegralHestonVarianceOptionEngineTest
 */
public class VarianceOptionTest {

    @Test
    public void testIntegralHeston() {
        // Phase 5e.5b-CFC-d-9 body-fill — delegate to the canonical
        // call-side scenario in IntegralHestonVarianceOptionEngineTest. The
        // qlts_call_v0_2_0_T_1_5_K_0_05 case is the direct port of the
        // single C++ test_suite/varianceoption.cpp input row.
        new IntegralHestonVarianceOptionEngineTest().qlts_call_v0_2_0_T_1_5_K_0_05();
    }
}
