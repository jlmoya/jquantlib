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
 Copyright (C) 2011 Chris Kenyon
 Copyright (C) 2012 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.testsuite.instruments.bonds;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CPILeg;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.instruments.bonds.CPIBond;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.bond.DiscountingBondEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.inflation.PiecewiseZeroInflationCurve;
import org.jquantlib.termstructures.inflation.ZeroCouponInflationSwapHelper;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Faithful port of {@code migration-harness/cpp/quantlib/test-suite/inflationcpibond.cpp}
 * (QuantLib v1.42.1, 296 LOC). Phase 2v B.2 — exercises the freshly ported
 * {@link CPIBond} (Phase 2v B.1) against the C++ stored values.
 *
 * <p>Per the binding rigor directive (2026-05-08) every C++
 * {@code BOOST_AUTO_TEST_CASE} is mirrored as a {@code @Test public void}
 * method with the same name. Tests that exercise classes, constructors or
 * production paths the Java port does not yet provide are marked
 * {@code @Ignore} with a documented Phase 2x follow-up reason.
 *
 * <p>The C++ {@code CommonVars} struct (inflationcpibond.cpp:76-159) is
 * replicated as the static {@link CommonVars} inner class — same evaluation
 * date (25-Nov-2009), UK calendar, ModifiedFollowing convention,
 * Actual/Actual ISDA day counter, 27-pillar UKRPI fixings table
 * (2007-07..2009-09), and 17-pillar ZCIIS quotes table (2010-2059). The
 * {@code makeHelpers} template is replaced by an inline list-builder
 * (mirrors the convention established in {@code CPISwapTest}).
 *
 * <h3>Observer-cycle workaround</h3>
 * <p>The C++ test uses a {@code RelinkableHandle<ZeroInflationTermStructure>}
 * (initially empty) to construct the index, then {@code linkTo}s the
 * bootstrapped curve afterwards. In Java's WeakReferenceObservable model
 * this triggers an unbounded observer cascade
 * (helper -> curve -> handle -> index -> helper -> ...). Following the
 * convention established in {@code CPISwapTest} we instead build a
 * helper-bootstrap index ({@code ii}) without a curve handle, trigger the
 * bootstrap eagerly, then construct a second index ({@code ii2}) bound to
 * the bootstrapped curve. UKRPI fixings are stored in the
 * {@code IndexManager} and shared across instances, so {@code ii2} sees the
 * same historical fixings as {@code ii}.
 *
 * <h3>Phase 2x deferred items</h3>
 * <ul>
 *   <li><b>{@code testCPILegWithoutBaseCPI}</b> — requires a standalone
 *       {@code CPILeg} builder class (with {@code .withBaseDate()},
 *       {@code .withSubtractInflationNominal()},
 *       {@code .withObservationInterpolation()} fluent setters) and the
 *       {@code CashFlows::npv} / {@code CashFlows::accruedAmount} static
 *       overloads on a {@code Leg} with explicit dirty/clean settle dates.
 *       Java has neither: {@code CPISwap}/{@code CPIBond} build the leg
 *       inline, and {@code CashFlows} is an instance singleton with NPV but
 *       no leg-level static accruedAmount. Phase 2x candidate (would
 *       require porting {@code ql/cashflows/cpicoupon.{hpp,cpp}}'s
 *       {@code CPILeg} builder class).</li>
 * </ul>
 */
public class CPIBondTest {

    /**
     * Clear UKRPI fixing history before/after every test so the C++
     * fixData seed (2007-07..2009-09, 27 entries) is isolated from other
     * tests that pollute the same index name. The IndexManager is a
     * process-wide singleton keyed by index {@code name()}, which for
     * UKRPI is the composed "UK RPI" (region + familyName).
     */
    @Before
    public void clearRpiHistoryBefore() {
        IndexManager.getInstance().clearHistory("UK RPI");
    }

    @After
    public void clearRpiHistoryAfter() {
        IndexManager.getInstance().clearHistory("UK RPI");
    }

    // ===================================================================
    // CommonVars — port of inflationcpibond.cpp:76-159
    // ===================================================================

    /**
     * Mirror of the C++ {@code CommonVars} struct
     * ({@code inflationcpibond.cpp:76-159}). Replicated inline so each test
     * can construct its own fresh fixture (same as C++ — every
     * {@code BOOST_AUTO_TEST_CASE} declares {@code CommonVars common;}).
     *
     * <p>Per the class-javadoc Observer-cycle workaround:
     * {@link #ii} is the helper-bootstrap index (no curve handle);
     * {@link #ii2} is the bond-consumer index linked to the bootstrapped
     * curve. UKRPI fixings live in {@code IndexManager} keyed by
     * {@code "UK RPI"} so both share the historical series.
     */
    static final class CommonVars {

        final Calendar calendar;
        final BusinessDayConvention convention;
        final Date evaluationDate;
        final Period observationLag;
        final DayCounter dayCounter;

        final UKRPI ii;   // helper-bootstrap index (no curve handle)
        final UKRPI ii2;  // bond-consumer index — observes bootstrapped curve

        final Handle<YieldTermStructure> yTS;
        final PiecewiseZeroInflationCurve<Linear> cpiTS;

        CommonVars() {
            calendar = new UnitedKingdom();
            convention = BusinessDayConvention.ModifiedFollowing;
            // C++ uses today=25-Nov-2009; UK calendar.adjust() is a no-op
            // for that Wednesday.
            final Date today = new Date(25, Month.November, 2009);
            evaluationDate = calendar.adjust(today, convention);
            new Settings().setEvaluationDate(evaluationDate);
            dayCounter = new ActualActual(ActualActual.Convention.ISDA);

            // UK RPI index (no curve handle) for helper bootstrap.
            ii = new UKRPI(Frequency.Monthly, false, false);

            // C++ MakeSchedule().from(1-Jul-2007).to(1-Sep-2009).withFrequency(Monthly).
            // Java MakeSchedule lacks fluent .from().to() — build the date
            // list manually (equivalent semantics for a monthly tenor).
            final List<Date> rpiSchedule = new ArrayList<>();
            Date d = new Date(1, Month.July, 2007);
            final Date rpiEnd = new Date(1, Month.September, 2009);
            while (d.le(rpiEnd)) {
                rpiSchedule.add(d);
                d = d.add(new Period(1, TimeUnit.Months));
            }
            final double[] fixData = {
                    206.1, 207.3, 208.0, 208.9, 209.7, 210.9,    // 2007-07..2007-12
                    209.8, 211.4, 212.1, 214.0, 215.1, 216.8,    // 2008-01..2008-06
                    216.5, 217.2, 218.4, 217.7, 216.0, 212.9,    // 2008-07..2008-12
                    210.1, 211.4, 211.3, 211.5, 212.8, 213.4,    // 2009-01..2009-06
                    213.4, 213.4, 214.4                          // 2009-07..2009-09
            };
            for (int i = 0; i < rpiSchedule.size(); ++i) {
                ii.addFixing(rpiSchedule.get(i), fixData[i], true);
            }

            // Nominal flat-forward 5% curve (cpp:118-119:
            //   yTS.linkTo(make_shared<FlatForward>(evaluationDate, 0.05, dc)))
            final FlatForward nominal =
                    new FlatForward(evaluationDate, 0.05, dayCounter);
            yTS = new Handle<YieldTermStructure>(nominal);

            // Observation lag for ZCIIS bootstrap (cpp:122).
            observationLag = new Period(2, TimeUnit.Months);

            // ZCIIS market data — 17 pillars 2010-11-25..2059-11-25
            // (cpp:124-142).
            final Date[] zciisD = {
                    new Date(25, Month.November, 2010),
                    new Date(25, Month.November, 2011),
                    new Date(26, Month.November, 2012),
                    new Date(25, Month.November, 2013),
                    new Date(25, Month.November, 2014),
                    new Date(25, Month.November, 2015),
                    new Date(25, Month.November, 2016),
                    new Date(25, Month.November, 2017),
                    new Date(25, Month.November, 2018),
                    new Date(25, Month.November, 2019),
                    new Date(25, Month.November, 2021),
                    new Date(25, Month.November, 2024),
                    new Date(26, Month.November, 2029),
                    new Date(27, Month.November, 2034),
                    new Date(25, Month.November, 2039),
                    new Date(25, Month.November, 2049),
                    new Date(25, Month.November, 2059),
            };
            final double[] zciisR = {
                    3.0495, 2.93, 2.9795, 3.029, 3.1425,
                    3.211, 3.2675, 3.3625, 3.405, 3.48,
                    3.576, 3.649, 3.751, 3.77225, 3.77,
                    3.734, 3.714,
            };

            // Bootstrap helpers (CPI::AsIndex), mirrors cpp:55-73 makeHelpers.
            final List<ZeroCouponInflationSwapHelper> helpers = new ArrayList<>();
            for (int i = 0; i < zciisD.length; ++i) {
                final Quote q = new SimpleQuote(zciisR[i] / 100.0);
                final var qh = new Handle<Quote>(q);
                helpers.add(new ZeroCouponInflationSwapHelper(
                        qh, observationLag, zciisD[i],
                        calendar, convention, dayCounter, ii,
                        CPI.InterpolationType.AsIndex));
            }

            // Phase 2u L0 A.2: ii.lastFixingDate() now exists. Returns the
            // first day of the inflation period containing the last stored
            // fixing — for monthly UKRPI seeded through 2009-09-01 that's
            // 2009-09-01 itself. (cpp:148.)
            final Date baseDate = ii.lastFixingDate();
            cpiTS = new PiecewiseZeroInflationCurve<>(
                    Linear.class, evaluationDate, baseDate,
                    ii.frequency(), dayCounter, helpers);
            // Trigger lazy bootstrap before the cycle-introducing ii2 is
            // constructed (matches C++ recalculate()).
            cpiTS.dates();

            // Bond-consumer index — observes the bootstrapped curve.
            // UKRPI shares fixings via IndexManager so ii2 inherits ii's
            // historical UK RPI series.
            ii2 = new UKRPI(Frequency.Monthly, false, false,
                    new Handle<>(cpiTS));
        }
    }

    // ===================================================================
    // BOOST_AUTO_TEST_CASE(testCleanPrice) — inflationcpibond.cpp:162-214
    // ===================================================================
    /**
     * Verifies cached dirty/clean price for a CPIBond against the C++
     * stored values 396.47045891 and 394.79676679 (tolerance 1e-8 per
     * cpp:198).
     *
     * <p>The C++ test constructs a 45-year semi-annual CPIBond
     * (2007-10-02..2052-10-02) at face 1e6, baseCPI=206.1, fixedRate=0.1,
     * Actual365Fixed day count, ModifiedFollowing payment convention, UK
     * calendar, contractObservationLag=3M, CPI::Flat interpolation,
     * settlementDays=3.
     */
    @Test
    public void testCleanPrice() {
        final CommonVars common = new CommonVars();

        final double notional = 1_000_000.0;
        final double[] fixedRates = { 0.1 };
        final DayCounter fixedDayCount = new Actual365Fixed();
        final BusinessDayConvention fixedPaymentConvention =
                BusinessDayConvention.ModifiedFollowing;
        // Bond uses common.ii2 (the curve-bound index) so future-dated
        // coupon fixings can be forecast from the bootstrapped curve.
        // The C++ test passes common.ii (which is the relinkable-handle
        // version that linkTo'd the bootstrapped curve in CommonVars
        // setup); our ii2 is the equivalent under the observer-cycle
        // workaround.
        final UKRPI fixedIndex = common.ii2;
        final Period contractObservationLag = new Period(3, TimeUnit.Months);
        final CPI.InterpolationType observationInterpolation =
                CPI.InterpolationType.Flat;
        final int settlementDays = 3;

        final double baseCPI = 206.1;
        final Date startDate = new Date(2, Month.October, 2007);
        final Date endDate = new Date(2, Month.October, 2052);
        // C++ MakeSchedule().from(startDate).to(endDate).withTenor(6M)
        //   .withCalendar(UK()).withConvention(Unadjusted).backwards();
        final Schedule fixedSchedule = new MakeSchedule(
                startDate, endDate, new Period(6, TimeUnit.Months),
                new UnitedKingdom(), BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .backwards()
                .schedule();

        final CPIBond bond = new CPIBond(settlementDays, notional,
                baseCPI, contractObservationLag, fixedIndex,
                observationInterpolation, fixedSchedule,
                fixedRates, fixedDayCount, fixedPaymentConvention);

        final DiscountingBondEngine engine =
                new DiscountingBondEngine(common.yTS);
        bond.setPricingEngine(engine);

        // C++ stored: dirtyPrice = 396.47045891, tol = 1.0e-8.
        final double storedDirtyPrice = 396.47045891;
        final double calculatedDirty = bond.dirtyPrice();
        final double tolerance = 1.0e-8;
        if (Math.abs(calculatedDirty - storedDirtyPrice) > tolerance) {
            fail("failed to reproduce expected CPI-bond dirty price"
                    + "\n  expected:   " + storedDirtyPrice
                    + "\n  calculated: " + calculatedDirty
                    + "\n  diff:       " + Math.abs(calculatedDirty - storedDirtyPrice));
        }

        // C++ stored: cleanPrice = 394.79676679, same tolerance.
        final double storedCleanPrice = 394.79676679;
        final double calculatedClean = bond.cleanPrice();
        if (Math.abs(calculatedClean - storedCleanPrice) > tolerance) {
            fail("failed to reproduce expected CPI-bond clean price"
                    + "\n  expected:   " + storedCleanPrice
                    + "\n  calculated: " + calculatedClean
                    + "\n  diff:       " + Math.abs(calculatedClean - storedCleanPrice));
        }
    }

    // ===================================================================
    // BOOST_AUTO_TEST_CASE(testCPILegWithoutBaseCPI) — inflationcpibond.cpp:216-292
    // ===================================================================
    /**
     * Verifies that a {@code CPILeg} built with an explicit base date
     * agrees with one built with an explicit base CPI fixing (and matches
     * the same stored clean-price 394.79676680 within 1e-8).
     *
     * <p>The C++ test uses the {@code CPILeg(schedule, index, baseCPI,
     * observationLag)} fluent builder with chained
     * {@code .withBaseDate(...)}, {@code .withSubtractInflationNominal(...)},
     * {@code .withNotionals(...)}, {@code .withFixedRates(...)},
     * {@code .withPaymentDayCounter(...)}, {@code .withObservationInterpolation(...)},
     * {@code .withPaymentAdjustment(...)}, {@code .withPaymentCalendar(...)}.
     * It then calls {@code CashFlows::npv(leg, **yTS, false, settlementDate,
     * settlementDate)} and {@code CashFlows::accruedAmount(leg, false,
     * settlementDate)} on each leg.
     */
    @Test
    public void testCPILegWithoutBaseCPI() {
        final CommonVars common = new CommonVars();

        final double notional = 1_000_000.0;
        final double[] fixedRates = { 0.1 };
        final DayCounter fixedDayCount = new Actual365Fixed();
        final BusinessDayConvention fixedPaymentConvention =
                BusinessDayConvention.ModifiedFollowing;
        final Calendar fixedPaymentCalendar = new UnitedKingdom();
        final Period contractObservationLag = new Period(3, TimeUnit.Months);
        final CPI.InterpolationType observationInterpolation = CPI.InterpolationType.Flat;
        final int settlementDays = 3;
        final boolean growthOnly = false;
        final double baseCPI = 206.1;
        final Date baseDate = new Date(1, Month.July, 2007);
        final Date startDate = new Date(2, Month.October, 2007);
        final Date endDate = new Date(2, Month.October, 2052);

        final Schedule fixedSchedule = new MakeSchedule(
                        startDate, endDate, new Period(6, TimeUnit.Months),
                        fixedPaymentCalendar, BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .backwards()
                .schedule();

        final Leg legWithBaseDate = new CPILeg(
                        fixedSchedule, common.ii2, Constants.NULL_REAL, contractObservationLag)
                .withSubtractInflationNominal(growthOnly)
                .withNotionals(notional)
                .withBaseDate(baseDate)
                .withFixedRates(fixedRates)
                .withPaymentDayCounter(fixedDayCount)
                .withObservationInterpolation(observationInterpolation)
                .withPaymentAdjustment(fixedPaymentConvention)
                .withPaymentCalendar(fixedPaymentCalendar)
                .Leg();

        final Leg legWithBaseCPI = new CPILeg(
                        fixedSchedule, common.ii2, baseCPI, contractObservationLag)
                .withSubtractInflationNominal(growthOnly)
                .withNotionals(notional)
                .withFixedRates(fixedRates)
                .withPaymentDayCounter(fixedDayCount)
                .withObservationInterpolation(observationInterpolation)
                .withPaymentAdjustment(fixedPaymentConvention)
                .withPaymentCalendar(fixedPaymentCalendar)
                .Leg();

        final Date settlementDate = fixedPaymentCalendar.advance(
                common.evaluationDate,
                new Period(settlementDays, TimeUnit.Days),
                fixedPaymentConvention);

        final YieldTermStructure yTS = common.yTS.currentLink();
        final double npvWithBaseDate = CashFlows.npv(
                legWithBaseDate, yTS, false, settlementDate, settlementDate);
        final double accruedsBaseDate = CashFlows.accruedAmount(
                legWithBaseDate, false, settlementDate);

        final double npvWithBaseCPI = CashFlows.npv(
                legWithBaseCPI, yTS, false, settlementDate, settlementDate);
        final double accruedsBaseCPI = CashFlows.accruedAmount(
                legWithBaseCPI, false, settlementDate);

        final double cleanPriceWithBaseDate =
                (npvWithBaseDate - accruedsBaseDate) * 100.0 / notional;
        final double cleanPriceWithBaseCPI =
                (npvWithBaseCPI - accruedsBaseCPI) * 100.0 / notional;

        // Tier: tight (1e-8 absolute), per C++ tolerance.
        final double tolerance = 1.0e-8;
        if (Math.abs(cleanPriceWithBaseDate - cleanPriceWithBaseCPI) > tolerance) {
            fail("prices of CPI leg with base date and explicit base CPI fixing are not equal"
                    + "\n  clean npv of leg with baseDate:        " + cleanPriceWithBaseDate
                    + "\n  clean npv of leg with explicit baseCPI: " + cleanPriceWithBaseCPI);
        }
        // Compare to expected price from C++ stored value.
        final double storedPrice = 394.79676680;
        if (Math.abs(cleanPriceWithBaseDate - storedPrice) > tolerance) {
            fail("failed to reproduce expected CPI-bond clean price"
                    + "\n  expected:   " + storedPrice
                    + "\n  calculated: " + cleanPriceWithBaseDate
                    + "\n  diff:       " + Math.abs(cleanPriceWithBaseDate - storedPrice));
        }
    }
}
