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
 */
package org.jquantlib.testsuite.pricingengines.swap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.instruments.ZeroCouponInflationSwap;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedZeroInflationCurve;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.junit.Test;

/**
 * Spot checks for the per-leg {@code startDiscounts[i]} / {@code endDiscounts[i]}
 * fields populated by {@code DiscountingSwapEngine} (Phase 2q L0 A.2).
 *
 * <p>Mirrors C++ v1.42.1 {@code DiscountingSwapEngine::calculate}
 * (pricingengines/swap/discountingswapengine.cpp:71-105). For each leg, the
 * start discount is the curve's discount at the leg's start date (or
 * accrualStart for Coupon legs), and the end discount is at the leg's maturity.
 *
 * <p>Tier rationale: TIGHT. Both values are direct {@code curve.discount(date)}
 * calls and the FlatForward continuous curve is closed-form
 * {@code exp(-rT)} — no solvers involved.
 */
public class SwapResultsDiscountFactorsTest {

    @Test
    public void discountingSwapEngine_populatesStartAndEndDiscounts() {
        // Reuse the ZCIIS setup from ZeroCouponInflationSwapTest (anchored on
        // Phase 2p A.3): UKRPI on a 6-pillar curve with 5% Continuous A/365F
        // FlatForward nominal discount curve.
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Date refDate = cal.adjust(evalDate, bdc);

        final Date[] nodeDates = new Date[] {
                new Date(1,  Month.May,    2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] nodeRates = new double[] {
                0.025, 0.030, 0.032, 0.034, 0.036, 0.038
        };
        final var zeroCurve = new InterpolatedZeroInflationCurve<Linear>(Linear.class,
                        refDate, nodeDates, nodeRates, freq, dc);
        zeroCurve.enableExtrapolation();

        final Handle<ZeroInflationTermStructure> ts =
                new Handle<ZeroInflationTermStructure>(zeroCurve);
        final UKRPI ukRpi = new UKRPI(Frequency.Monthly, false, false, ts);

        // Seed enough fixings for the swap to price the inflation leg.
        final Date[] fixDates = new Date[] {
                new Date(1, Month.January,   2005), new Date(1, Month.February,  2005),
                new Date(1, Month.March,     2005), new Date(1, Month.April,     2005),
                new Date(1, Month.May,       2005), new Date(1, Month.June,      2005),
                new Date(1, Month.July,      2005), new Date(1, Month.August,    2005),
                new Date(1, Month.September, 2005), new Date(1, Month.October,   2005),
                new Date(1, Month.November,  2005), new Date(1, Month.December,  2005),
                new Date(1, Month.January,   2006), new Date(1, Month.February,  2006),
                new Date(1, Month.March,     2006), new Date(1, Month.April,     2006),
                new Date(1, Month.May,       2006), new Date(1, Month.June,      2006),
                new Date(1, Month.July,      2006), new Date(1, Month.August,    2006),
                new Date(1, Month.September, 2006), new Date(1, Month.October,   2006),
                new Date(1, Month.November,  2006), new Date(1, Month.December,  2006),
                new Date(1, Month.January,   2007), new Date(1, Month.February,  2007),
                new Date(1, Month.March,     2007), new Date(1, Month.April,     2007),
                new Date(1, Month.May,       2007), new Date(1, Month.June,      2007),
                new Date(1, Month.July,      2007),
        };
        final double[] fixVals = new double[] {
                189.9, 189.9, 190.5, 191.6, 192.0, 192.2, 192.2, 192.6, 193.1, 193.3, 193.6, 194.1,
                193.4, 194.2, 195.0, 196.5, 197.7, 198.5, 198.5, 199.2, 200.1, 200.4, 201.1, 202.7,
                201.6, 203.1, 204.4, 205.4, 206.2, 207.3, 206.1
        };
        for (int i = 0; i < fixDates.length; ++i) {
            ukRpi.addFixing(fixDates[i], fixVals[i], true);
        }

        // Nominal discount curve: 5% Continuous Actual365Fixed.
        final FlatForward nominalCurve = new FlatForward(refDate, 0.05, dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> nominalTS =
                new Handle<YieldTermStructure>(nominalCurve);
        final DiscountingSwapEngine engine = new DiscountingSwapEngine(nominalTS);

        // Build a 5y Payer ZCIIS at fixed 3% with NoInterpolation observation.
        final Date maturity = new Date(13, Month.August, 2012);
        final Period obsLag3M = new Period(3, TimeUnit.Months);

        final ZeroCouponInflationSwap zcis = new ZeroCouponInflationSwap(
                ZeroCouponInflationSwap.Type.Payer,
                /*nominal*/ 1_000_000.0,
                evalDate,
                maturity,
                cal, bdc, dc,
                /*fixedRate*/ 0.03,
                ukRpi,
                obsLag3M,
                CPI.InterpolationType.AsIndex);
        zcis.setPricingEngine(engine);

        // Trigger calculation.
        zcis.NPV();

        // For ZCIIS both legs hold a single non-Coupon cashflow each (a
        // SimpleCashFlow on the fixed leg and an IndexedCashFlow on the
        // inflation leg). Per C++ CashFlows::startDate / maturityDate
        // (cashflows.cpp:38-64), both startDate and maturityDate fall back
        // to {@code i->date()} for non-Coupon cashflows — i.e. for these
        // legs startDiscounts[i] == endDiscounts[i] == curve.discount(payDate[i]).
        final Date fixedPayDate = zcis.fixedLeg().get(0).date();
        final Date inflationPayDate = zcis.inflationLeg().get(0).date();

        final double expectedFixed = nominalCurve.discount(fixedPayDate);
        final double expectedInflation = nominalCurve.discount(inflationPayDate);

        final double startDisc0 = zcis.startDiscounts(0);
        final double startDisc1 = zcis.startDiscounts(1);
        final double endDisc0 = zcis.endDiscounts(0);
        final double endDisc1 = zcis.endDiscounts(1);

        // Sanity: discount factors are in (0,1] for non-trivial forward times.
        assertTrue("startDisc[0] in (0,1]", startDisc0 > 0.0 && startDisc0 <= 1.0);
        assertTrue("endDisc[0] in (0,1]", endDisc0 > 0.0 && endDisc0 <= 1.0);
        assertTrue("startDisc[1] in (0,1]", startDisc1 > 0.0 && startDisc1 <= 1.0);
        assertTrue("endDisc[1] in (0,1]", endDisc1 > 0.0 && endDisc1 <= 1.0);

        // Bit-exact equality with curve.discount(date).
        // Tolerance: TIGHT (1e-12 abs) since both sides come from the same
        // FlatForward.discount(date) implementation under the hood.
        assertEquals("fixed-leg startDisc must match curve.discount(payDate[0])",
                expectedFixed, startDisc0, 1.0e-12);
        assertEquals("fixed-leg endDisc must match curve.discount(payDate[0])",
                expectedFixed, endDisc0, 1.0e-12);
        assertEquals("inflation-leg startDisc must match curve.discount(payDate[1])",
                expectedInflation, startDisc1, 1.0e-12);
        assertEquals("inflation-leg endDisc must match curve.discount(payDate[1])",
                expectedInflation, endDisc1, 1.0e-12);
    }
}
