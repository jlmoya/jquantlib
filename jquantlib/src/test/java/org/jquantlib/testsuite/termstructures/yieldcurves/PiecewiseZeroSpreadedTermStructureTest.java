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

package org.jquantlib.testsuite.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/piecewisezerospreadedtermstructure.cpp
 * (Phase 5e).
 *
 * <p>10 BOOST_AUTO_TEST_CASE methods exercising
 * {@code InterpolatedPiecewiseZeroSpreadedTermStructure} (a yield curve
 * built from a base curve plus a vector of spread quotes interpolated
 * across pillar dates).
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>JQuant has only the simpler
 * {@link org.jquantlib.termstructures.yieldcurves.ZeroSpreadedTermStructure}
 * (a single-quote uniform-shift curve), not the piecewise version. The
 * piecewise variant — {@code InterpolatedPiecewiseZeroSpreadedTermStructure}
 * (in {@code ql/termstructures/yield/piecewisezerospreadedtermstructure.hpp}) —
 * needs to be ported with all 5 interpolation traits exercised by the
 * tests:
 *
 * <ul>
 *   <li>{@code testFlatInterpolationLeft},
 *       {@code testFlatInterpolationRight}: flat-left / flat-right
 *       extrapolation behavior outside the pillar range.</li>
 *
 *   <li>{@code testLinearInterpolationMultipleSpreads},
 *       {@code testLinearInterpolation}: Linear interpolation across
 *       spread quotes.</li>
 *
 *   <li>{@code testForwardFlatInterpolation},
 *       {@code testBackwardFlatInterpolation}: ForwardFlat /
 *       BackwardFlat interpolators.</li>
 *
 *   <li>{@code testDefaultInterpolation},
 *       {@code testSetInterpolationFactory}: default-trait wiring
 *       and explicit interpolation-factory injection.</li>
 *
 *   <li>{@code testMaxDate}: maxDate from the base curve carries through.</li>
 *
 *   <li>{@code testQuoteChanging}: observer-pattern integration —
 *       changing a {@code SimpleQuote} spread re-fires the curve's
 *       discount values.</li>
 * </ul>
 *
 * <p>Production prereqs:
 * <ul>
 *   <li>{@code InterpolatedPiecewiseZeroSpreadedTermStructure<Linear>}
 *       and friends. See WI-5e.5-PZS-1.</li>
 *   <li>{@code SpreadedLinearZeroInterpolatedTermStructure} convenience
 *       typedef (most-common variant). See WI-5e.5-PZS-2.</li>
 * </ul>
 */
public class PiecewiseZeroSpreadedTermStructureTest {

    public PiecewiseZeroSpreadedTermStructureTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 — needs InterpolatedPiecewiseZeroSpreadedTermStructure port.")
    @Test
    public void testFlatInterpolationLeft() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 — needs InterpolatedPiecewiseZeroSpreadedTermStructure port.")
    @Test
    public void testFlatInterpolationRight() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 + WI-5e.5-PZS-2.")
    @Test
    public void testLinearInterpolationMultipleSpreads() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 + WI-5e.5-PZS-2.")
    @Test
    public void testLinearInterpolation() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 — needs ForwardFlat-trait variant.")
    @Test
    public void testForwardFlatInterpolation() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 — needs BackwardFlat-trait variant.")
    @Test
    public void testBackwardFlatInterpolation() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 — needs default-trait wiring.")
    @Test
    public void testDefaultInterpolation() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 — needs explicit interpolation-factory injection API.")
    @Test
    public void testSetInterpolationFactory() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 — needs maxDate pass-through verification.")
    @Test
    public void testMaxDate() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-PZS-1 — needs SimpleQuote -> spread observer wiring.")
    @Test
    public void testQuoteChanging() {
    }
}
