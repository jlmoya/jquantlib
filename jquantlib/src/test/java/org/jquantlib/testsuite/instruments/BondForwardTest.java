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

/*
 Copyright (C) 2022 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.testsuite.instruments;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.instruments.Bond;
import org.jquantlib.instruments.BondForward;
import org.jquantlib.instruments.Position;
import org.jquantlib.instruments.bonds.FixedRateBond;
import org.jquantlib.pricingengines.bond.DiscountingBondEngine;
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
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Faithful port of {@code migration-harness/cpp/quantlib/test-suite/bondforward.cpp}
 * (QuantLib v1.42.1). Phase 5d.5-Bonds — un-blocks the BondForwardTest skeleton
 * landed in Phase 5d.
 *
 * <p>Per the binding rigor directive (2026-05-08) every C++
 * {@code BOOST_AUTO_TEST_CASE} is mirrored as a {@code @Test public void}
 * method with the same name. Tolerance 1e-2 (mirrors the C++ source).
 *
 * <p>Reference: {@code migration-harness/references/instruments/bond_forward.json}
 * (probe {@code bond-forward/bond_forward_probe.cpp}).
 */
public class BondForwardTest {

    /** Mirrors the C++ {@code CommonVars} struct (bondforward.cpp:34). */
    private static class CommonVars {
        final Date today;
        final Handle<YieldTermStructure> curveHandle;

        CommonVars() {
            today = new Date(7, Month.March, 2022);
            new Settings().setEvaluationDate(today);
            curveHandle = new Handle<YieldTermStructure>(
                    new FlatForward(today, 0.0004977, new Actual365Fixed()));
        }
    }

    private static Bond buildBond(final Date issue, final Date maturity, final double cpn) {
        final Schedule sch = new Schedule(issue, maturity, new Period(Frequency.Annual),
                new Target(),
                BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward, false);
        return new FixedRateBond(2, 1.0e5, sch, new double[] { cpn },
                new ActualActual(ActualActual.Convention.ISDA),
                BusinessDayConvention.Following, 100.0);
    }

    private static BondForward buildBondForward(final Bond underlying,
                                                  final Handle<YieldTermStructure> handle,
                                                  final Date delivery,
                                                  final Position type) {
        final Date valueDt = handle.currentLink().referenceDate();
        return new BondForward(valueDt, delivery, type, 0.0, 2,
                new ActualActual(ActualActual.Convention.ISDA),
                new Target(), BusinessDayConvention.Following,
                underlying, handle, handle);
    }

    @Test
    public void testFuturesPriceReplication() {
        final CommonVars vars = new CommonVars();
        final double tolerance = 1.0e-2;

        final Date issue = new Date(15, Month.August, 2015);
        final Date maturity = new Date(15, Month.August, 2046);
        final double cpn = 0.025;

        final Bond bnd = buildBond(issue, maturity, cpn);
        final DiscountingBondEngine pricer = new DiscountingBondEngine(vars.curveHandle);
        bnd.setPricingEngine(pricer);

        final Date delivery = new Date(10, Month.March, 2022);
        final double conversionFactor = 0.76871;
        final BondForward bndFwd = buildBondForward(bnd, vars.curveHandle,
                                                     delivery, Position.Long);

        final double futuresPrice = bndFwd.cleanForwardPrice() / conversionFactor;
        final double expectedFuturesPrice = 207.47;

        if (Math.abs(futuresPrice - expectedFuturesPrice) > tolerance) {
            fail("unable to replicate bond futures price"
                    + "\n    calculated: " + futuresPrice
                    + "\n    expected:   " + expectedFuturesPrice);
        }
    }

    @Test
    public void testCleanForwardPriceReplication() {
        final CommonVars vars = new CommonVars();
        final double tolerance = 1.0e-2;

        final Date issue = new Date(15, Month.August, 2015);
        final Date maturity = new Date(15, Month.August, 2046);
        final double cpn = 0.025;

        final Bond bnd = buildBond(issue, maturity, cpn);
        bnd.setPricingEngine(new DiscountingBondEngine(vars.curveHandle));

        final Date delivery = new Date(10, Month.March, 2022);
        final BondForward bndFwd = buildBondForward(bnd, vars.curveHandle,
                                                     delivery, Position.Long);

        final double fwdCleanPrice = bndFwd.cleanForwardPrice();
        final double expectedFwdCleanPrice = bndFwd.forwardValue() - bnd.accruedAmount(delivery);

        if (Math.abs(fwdCleanPrice - expectedFwdCleanPrice) > tolerance) {
            fail("unable to replicate clean forward price"
                    + "\n    calculated: " + fwdCleanPrice
                    + "\n    expected:   " + expectedFwdCleanPrice);
        }
    }

    @Test
    public void testThatForwardValueIsEqualToSpotValueIfNoIncome() {
        final CommonVars vars = new CommonVars();
        final double tolerance = 1.0e-2;

        final Date issue = new Date(15, Month.August, 2015);
        final Date maturity = new Date(15, Month.August, 2046);
        final double cpn = 0.025;

        final Bond bnd = buildBond(issue, maturity, cpn);
        bnd.setPricingEngine(new DiscountingBondEngine(vars.curveHandle));

        final Date delivery = new Date(10, Month.March, 2022);
        final BondForward bndFwd = buildBondForward(bnd, vars.curveHandle,
                                                     delivery, Position.Long);

        final double bndFwdValue = bndFwd.forwardValue();
        final double underlyingDirtyPrice = bnd.dirtyPrice();

        if (Math.abs(bndFwdValue - underlyingDirtyPrice) > tolerance) {
            fail("unable to match the dirty price"
                    + "\n    bond forward:    " + bndFwdValue
                    + "\n    underlying bond: " + underlyingDirtyPrice);
        }
    }
}
