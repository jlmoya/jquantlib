/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.testsuite.instruments.bonds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.bonds.AmortizingCmsRateBond;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Construction smoke test for {@link AmortizingCmsRateBond} (Phase 2 L3-B).
 *
 * <p>The C++ test-suite has no dedicated {@code amortizingcmsratebond}
 * cases (only the related amortizing-fixed/floating bond tests). This
 * smoke exercises the construction path of the simplest 5-arg overload
 * and asserts:
 * <ol>
 *   <li>cashflows are non-empty,</li>
 *   <li>first Coupon's nominal aligns with the supplied amortizing
 *       notionals vector,</li>
 *   <li>maturity matches the schedule end date.</li>
 * </ol>
 */
public class AmortizingCmsRateBondSmokeTest {

    @Test
    public void testConstructAndAmortizationStructure() {
        new Settings().setEvaluationDate(new Date(15, Month.January, 2020));

        final Date issue = new Date(15, Month.January, 2020);
        final Date maturity = new Date(15, Month.January, 2025);
        final Schedule sch = new Schedule(issue, maturity, new Period(Frequency.Annual), new Target(),
                BusinessDayConvention.Following, BusinessDayConvention.Following, DateGeneration.Rule.Backward, false);

        // Forecast curve so the swap-index can resolve forwards.
        final Handle< YieldTermStructure > fwd = new Handle< YieldTermStructure >(
                new FlatForward(issue, 0.02, new Actual360()));
        final SwapIndex idx = new EuriborSwapIsdaFixA(new Period(10, TimeUnit.Years), fwd);

        // 5 annual periods → 5 amortizing notionals (linear sink).
        final double[] notionals = new double[5];
        for (int i = 0; i < 5; ++i) {
            notionals[i] = 100.0 * (1.0 - i / 5.0);
        }

        final AmortizingCmsRateBond bond = new AmortizingCmsRateBond(3, notionals, sch, idx, new Actual360());

        final Leg cfs = bond.cashflows();
        assertNotNull("cashflows must be non-null", cfs);
        assertTrue("cashflows must be non-empty", !cfs.isEmpty());

        // Find the first Coupon; it must use notionals[0].
        Coupon firstCoupon = null;
        for (int j = 0; j < cfs.size(); ++j) {
            if (cfs.get(j) instanceof Coupon) {
                firstCoupon = (Coupon) cfs.get(j);
                break;
            }
        }
        assertNotNull("must find at least one Coupon", firstCoupon);
        assertEquals("first coupon nominal == notionals[0]", notionals[0], firstCoupon.nominal(), 1e-12);

        // Coupon count equals notionals length.
        int coupons = 0;
        for (int j = 0; j < cfs.size(); ++j) {
            if (cfs.get(j) instanceof Coupon) {
                coupons++;
            }
        }
        assertEquals("coupon count == notionals.length", notionals.length, coupons);

        assertEquals("maturity date matches schedule", maturity, bond.maturityDate());
    }
}
