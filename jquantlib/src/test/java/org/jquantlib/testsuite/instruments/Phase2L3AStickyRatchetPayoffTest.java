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
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;

import org.jquantlib.instruments.DoubleStickyRatchetPayoff;
import org.jquantlib.instruments.RatchetMaxPayoff;
import org.jquantlib.instruments.RatchetMinPayoff;
import org.jquantlib.instruments.RatchetPayoff;
import org.jquantlib.instruments.StickyMaxPayoff;
import org.jquantlib.instruments.StickyMinPayoff;
import org.jquantlib.instruments.StickyPayoff;
import org.junit.Test;

/**
 * Smoke tests for the v1.42.1 {@code DoubleStickyRatchetPayoff} family.
 *
 * <p>Reference values were computed by hand-evaluating the v1.42.1
 * {@code DoubleStickyRatchetPayoff::operator()} formula
 * ({@code ql/instruments/stickyratchet.cpp:26-38}):
 *
 * <pre>{@code
 *   swaplet    = gearing3*forward + spread3
 *   effStrike1 = gearing1*initialValue1 + spread1
 *   effStrike2 = gearing2*initialValue2 + spread2
 *   effStrike3 = type1*type2 * max(type2*(swaplet-effStrike2), 0)
 *   price      = accrualFactor * (swaplet
 *                  - type1 * max(type1*(swaplet-effStrike1), effStrike3))
 * }</pre>
 *
 * <p>Tolerance: exact (1e-14 relative) — closed-form arithmetic, no iterative root-finding.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public class Phase2L3AStickyRatchetPayoffTest {

    private static final double TOL = 1.0e-14;

    /**
     * Ratchet single-option payoff. Mirrors {@code RatchetPayoff} constructor: {@code type1=-1, type2=0, gearing1=g1,
     * gearing2=0, gearing3=g2, spread1=s1, spread2=0, spread3=s2}. So:
     * <ul>
     *   <li>swaplet     = g2*f + s2
     *   <li>effStrike1  = g1*initial + s1
     *   <li>type2*type1 = 0 → effStrike3 = 0
     *   <li>price       = accr*(swaplet - (-1)*max(-(swaplet-effStrike1), 0))
     *                   = accr*(swaplet - max(effStrike1 - swaplet, 0))
     * </ul>
     */
    @Test
    public void testRatchetPayoff() {
        final double g1 = 1.0;
        final double g2 = 1.0;
        final double s1 = 0.001;
        final double s2 = 0.0;
        final double initial = 0.04;
        final double accr = 0.25;

        final RatchetPayoff payoff = new RatchetPayoff(g1, g2, s1, s2, initial, accr);
        assertEquals("Ratchet", payoff.name());

        // forward = 0.045: swaplet=0.045, effStrike1=0.041,
        // type1*max(-1*(0.045-0.041),0)= -max(-0.004,0)=0; price=accr*0.045
        assertEquals(accr * 0.045, payoff.get(0.045), TOL);

        // forward = 0.038: swaplet=0.038, effStrike1=0.041,
        // -max(-(-0.003),0) = -0.003; price = accr*(0.038 - (-1)*max(-1*(-0.003),0))
        //                                  = accr*(0.038 - (-1)*0.003) = accr*0.041
        assertEquals(accr * 0.041, payoff.get(0.038), TOL);
    }

    /** Sticky single-option payoff: type1=+1, type2=0. */
    @Test
    public void testStickyPayoff() {
        final double g1 = 1.0;
        final double g2 = 1.0;
        final double s1 = 0.001;
        final double s2 = 0.0;
        final double initial = 0.04;
        final double accr = 0.25;

        final StickyPayoff payoff = new StickyPayoff(g1, g2, s1, s2, initial, accr);
        assertEquals("Sticky", payoff.name());

        // forward=0.045: swaplet=0.045, effStrike1=0.041,
        // price=accr*(0.045 - max(0.045-0.041,0)) = accr*0.041
        assertEquals(accr * 0.041, payoff.get(0.045), TOL);

        // forward=0.038: max(0.038-0.041, 0)=0 → price = accr*0.038
        assertEquals(accr * 0.038, payoff.get(0.038), TOL);
    }

    /** Double sticky/ratchet base formula sanity. */
    @Test
    public void testDoubleStickyRatchetGeneralFormula() {
        // Use generic non-degenerate values to exercise effStrike3 path.
        final DoubleStickyRatchetPayoff payoff = new RatchetMaxPayoff(
                1.0, 1.0, 1.0,         // gearings
                0.001, 0.002, 0.0,     // spreads
                0.04, 0.045,           // initialValues
                0.5);                   // accrualFactor

        // type1=-1, type2=-1: forward=0.05
        // swaplet = 1.0*0.05 + 0 = 0.05
        // effStrike1 = 0.04 + 0.001 = 0.041
        // effStrike2 = 0.045 + 0.002 = 0.047
        // effStrike3 = (-1)*(-1)*max(-1*(0.05-0.047), 0) = max(-0.003,0) = 0
        // price = 0.5 * (0.05 - (-1)*max(-1*(0.05-0.041), 0))
        //       = 0.5 * (0.05 - (-1)*max(-0.009,0))
        //       = 0.5 * (0.05 - (-1)*0) = 0.5 * 0.05 = 0.025
        assertEquals(0.025, payoff.get(0.05), TOL);
        assertEquals("RatchetMax", payoff.name());
    }

    /** Constructors and name() coverage for all four double-option subclasses. */
    @Test
    public void testDoubleOptionSubclassNames() {
        final DoubleStickyRatchetPayoff rmax = new RatchetMaxPayoff(
                1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.04, 0.04, 1.0);
        assertEquals("RatchetMax", rmax.name());

        final DoubleStickyRatchetPayoff rmin = new RatchetMinPayoff(
                1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.04, 0.04, 1.0);
        assertEquals("RatchetMin", rmin.name());

        final DoubleStickyRatchetPayoff smax = new StickyMaxPayoff(
                1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.04, 0.04, 1.0);
        assertEquals("StickyMax", smax.name());

        final DoubleStickyRatchetPayoff smin = new StickyMinPayoff(
                1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.04, 0.04, 1.0);
        assertEquals("StickyMin", smin.name());
    }
}
