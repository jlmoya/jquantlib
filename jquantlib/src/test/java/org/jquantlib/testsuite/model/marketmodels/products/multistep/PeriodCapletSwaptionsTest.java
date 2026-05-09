/*
Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.9.

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
 */

package org.jquantlib.testsuite.model.marketmodels.products.multistep;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepPeriodCapletSwaptions;
import org.junit.Test;

/**
 * Structural tests for {@link MultiStepPeriodCapletSwaptions}.
 */
public class PeriodCapletSwaptionsTest {

    public PeriodCapletSwaptionsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * 7-rate grid (rateTimes={0.5..3.5}), period=2, offset=0:
     * numberFRAs = 6, numberBigFRAs = 6/2 = 3.
     * Verifies number of products = 2 * numberBigFRAs = 6.
     */
    @Test
    public void testNumberOfProductsAndBigFRAs() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5};
        final double[] forwardOptionPaymentTimes = {1.0, 2.0, 3.0};
        final double[] swaptionPaymentTimes = {1.0, 2.0, 3.0};
        final StrikedTypePayoff[] forwardPayOffs = new StrikedTypePayoff[3];
        final StrikedTypePayoff[] swapPayOffs = new StrikedTypePayoff[3];
        for (int i = 0; i < 3; ++i) {
            forwardPayOffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.05);
            swapPayOffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.05);
        }

        final MultiStepPeriodCapletSwaptions p = new MultiStepPeriodCapletSwaptions(
                rateTimes, forwardOptionPaymentTimes, swaptionPaymentTimes,
                forwardPayOffs, swapPayOffs, 2, 0);

        assertEquals(6, p.numberOfProducts());
        assertEquals(1, p.maxNumberOfCashFlowsPerProductPerStep());
        // possibleCashFlowTimes = 3 forward + 3 swaption = 6
        assertEquals(6, p.possibleCashFlowTimes().length);
    }
}
