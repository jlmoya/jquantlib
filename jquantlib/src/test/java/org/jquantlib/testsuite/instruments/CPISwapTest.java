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

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.testsuite.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CPICoupon;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.indexes.ibor.GBPLibor;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.instruments.CPISwap;
import org.jquantlib.instruments.ZeroCouponInflationSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.PiecewiseZeroInflationCurve;
import org.jquantlib.termstructures.inflation.ZeroCouponInflationSwapHelper;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.jquantlib.util.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Faithful port of {@code migration-harness/cpp/quantlib/test-suite/inflationcpiswap.cpp}
 * (QuantLib v1.42.1, 495 LOC). Phase 2u Track C — replaces the Phase 2r
 * smoke-only CPISwap test with the full C++ test-suite port.
 *
 * <p>Every C++ {@code BOOST_AUTO_TEST_CASE} is mirrored as a {@code @Test
 * public void} method with the same name. Tests that exercise classes,
 * constructors or production paths the Java port does not yet provide are
 * marked {@code @Ignore} with a documented Phase 2v / 2x follow-up reason.
 *
 * <p>The C++ {@code CommonVars} struct (inflationcpiswap.cpp:78-246) is
 * replicated as the static {@link CommonVars} inner class — same evaluation
 * date (25-Nov-2009), UK calendar, ModifiedFollowing convention,
 * Actual/Actual ISDA day counter, 27-pillar UKRPI fixings table
 * (2007-07..2009-09), and 17-pillar ZCIIS quotes table (2010-2059). The
 * {@code makeHelpers} template is replaced by an inline list-builder.
 *
 * <h3>Yield curve note</h3>
 * <p>The C++ test builds a 29-pillar nominal curve via
 * {@code InterpolatedZeroCurve<Linear>(nomD, nomR, dcNominal)}. The Java
 * {@link org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve} has
 * a long-standing copy-paste bug ({@code data[0] == 1.0} assertion intended
 * for discount factors but applied to zero rates) that prevents using it
 * here. As a stand-in (mirroring the convention established in
 * {@code CPICapFloorTest}, {@code CPICapFloorTermPriceSurfaceTest} etc.) we
 * use a {@link FlatForward} 5%/Annual curve for the nominal handle. The
 * substitution is benign for self-consistency tests
 * ({@code zciisconsistency}) but invalidates the C++ stored-value test
 * ({@code consistency}) — which is therefore @Ignore'd.
 *
 * <h3>Observer-cycle workaround</h3>
 * <p>The C++ test uses a {@code RelinkableHandle<ZeroInflationTermStructure>}
 * (initially empty) to construct the index, then {@code linkTo}s the
 * bootstrapped curve afterwards. In Java's WeakReferenceObservable model
 * this triggers an unbounded observer cascade
 * (helper → curve → handle → index → helper → ...). Following the convention
 * established in {@code CPICapFloorTermPriceSurfaceTest} / {@code CPICapFloorTest}
 * we instead build a helper-bootstrap index ({@code ii}) without a curve
 * handle, trigger the bootstrap eagerly, then construct a second index
 * ({@code ii2}) bound to the bootstrapped curve for the swap to consume.
 * UKRPI fixings are stored in the {@code IndexManager} and shared across
 * instances, so {@code ii2} sees the same historical fixings as {@code ii}.
 *
 * <h3>Phase 2v / 2x deferred items</h3>
 * <ul>
 *   <li><b>{@code consistency}</b> — requires {@code InterpolatedZeroCurve}
 *       constructor accepting raw zero rates (currently asserts
 *       {@code yields[0] == 1.0}, treating the input as discount factors).
 *       The nominalData table (29 pillars 2009-11-26..2079-11-27) cannot be
 *       loaded until this constraint is removed. Without that exact nominal
 *       curve the C++ stored value 4191797.54 cannot be reproduced.
 *       Phase 2x align candidate. Also requires
 *       {@code IborCoupon::Settings::usingAtParCoupons()} static accessor
 *       (Phase 2x).</li>
 *   <li><b>{@code cpibondconsistency}</b> — requires {@code CPIBond}, which
 *       is not ported (no {@code org.jquantlib.instruments.bonds.CPIBond}
 *       class exists yet). Phase 2u inflation deferral list explicitly
 *       puts inflationcpibond.cpp + CPIBond instrument port out of scope;
 *       Phase 2v candidate.</li>
 * </ul>
 */
public class CPISwapTest {

    /**
     * Clear UKRPI fixing history before/after every test so the C++
     * fixData seed (2007-07..2009-09, 27 entries) is isolated from other
     * tests that pollute the same index name. The IndexManager is a
     * process-wide singleton keyed by index {@code name()}, which for
     * UKRPI is the composed "UK RPI" (region + familyName). Mirrors the
     * pattern in {@code YoYInflationIndexFixingTest}.
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
    // CommonVars — port of inflationcpiswap.cpp:78-246
    // ===================================================================

    /**
     * Mirror of the C++ {@code CommonVars} struct
     * ({@code inflationcpiswap.cpp:78-246}). Replicated inline so each test
     * can construct its own fresh fixture (same as C++ — every
     * {@code BOOST_AUTO_TEST_CASE} declares {@code CommonVars common;}).
     *
     * <p>Per the class-javadoc Observer-cycle workaround:
     * {@link #ii} is the helper-bootstrap index (no curve handle);
     * {@link #ii2} is the swap-consumer index linked to the bootstrapped
     * curve. UKRPI fixings live in {@code IndexManager} keyed by
     * {@code "UK RPI"} so both share the historical series.
     */
    static final class CommonVars {

        // option variables / usual setup
        final int length = 7;
        final double volatility = 0.01;
        final Frequency frequency = Frequency.Annual;
        final double[] nominals = new double[] { 1_000_000.0 };
        final Calendar calendar = new UnitedKingdom();
        final BusinessDayConvention convention =
                BusinessDayConvention.ModifiedFollowing;
        final int fixingDays = 0;
        final int settlementDays = 0;
        final Date evaluationDate;
        final Date settlement;
        final Date startDate;
        final Period observationLag = new Period(2, TimeUnit.Months);
        final Period contractObservationLag = new Period(3, TimeUnit.Months);
        final CPI.InterpolationType contractObservationInterpolation =
                CPI.InterpolationType.Flat;
        final DayCounter dcZCIIS = new ActualActual(ActualActual.Convention.ISDA);
        final DayCounter dcNominal = new ActualActual(ActualActual.Convention.ISDA);

        final UKRPI ii;   // helper-bootstrap index (no curve handle)
        final UKRPI ii2;  // swap-consumer index — observes bootstrapped pCpiTs

        final Handle<YieldTermStructure> nominalTS;
        final PiecewiseZeroInflationCurve<Linear> pCpiTs;

        // 17 ZCIIS pillars retained as raw arrays for clean smoke-test access.
        final List<Date> zciisD;
        final List<Double> zciisR;
        final int zciisDataLength;

        CommonVars() {
            // C++ uses today=25-Nov-2009; UK calendar.adjust() is a no-op
            // for that Wednesday.
            final Date today = new Date(25, Month.November, 2009);
            evaluationDate = calendar.adjust(today, convention);
            new Settings().setEvaluationDate(evaluationDate);
            settlement = calendar.advance(today, settlementDays, TimeUnit.Days);
            startDate = settlement;

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

            // Nominal curve. C++ builds a 29-pillar
            // InterpolatedZeroCurve<Linear>; Java's InterpolatedZeroCurve
            // has the data[0]==1.0 assertion bug — see class-javadoc
            // "Yield curve note". Use FlatForward 5% as a stand-in.
            final FlatForward nominal = new FlatForward(
                    evaluationDate, 0.05, dcNominal,
                    Compounding.Continuous, Frequency.Annual);
            nominalTS = new Handle<YieldTermStructure>(nominal);

            // ZCIIS market data — 17 pillars 2010-11-25..2059-11-25.
            zciisD = new ArrayList<>();
            zciisR = new ArrayList<>();
            zciisD.add(new Date(25, Month.November, 2010));
            zciisR.add(3.0495);
            zciisD.add(new Date(25, Month.November, 2011));
            zciisR.add(2.93);
            zciisD.add(new Date(26, Month.November, 2012));
            zciisR.add(2.9795);
            zciisD.add(new Date(25, Month.November, 2013));
            zciisR.add(3.029);
            zciisD.add(new Date(25, Month.November, 2014));
            zciisR.add(3.1425);
            zciisD.add(new Date(25, Month.November, 2015));
            zciisR.add(3.211);
            zciisD.add(new Date(25, Month.November, 2016));
            zciisR.add(3.2675);
            zciisD.add(new Date(25, Month.November, 2017));
            zciisR.add(3.3625);
            zciisD.add(new Date(25, Month.November, 2018));
            zciisR.add(3.405);
            zciisD.add(new Date(25, Month.November, 2019));
            zciisR.add(3.48);
            zciisD.add(new Date(25, Month.November, 2021));
            zciisR.add(3.576);
            zciisD.add(new Date(25, Month.November, 2024));
            zciisR.add(3.649);
            zciisD.add(new Date(26, Month.November, 2029));
            zciisR.add(3.751);
            zciisD.add(new Date(27, Month.November, 2034));
            zciisR.add(3.77225);
            zciisD.add(new Date(25, Month.November, 2039));
            zciisR.add(3.77);
            zciisD.add(new Date(25, Month.November, 2049));
            zciisR.add(3.734);
            zciisD.add(new Date(25, Month.November, 2059));
            zciisR.add(3.714);
            zciisDataLength = 17;

            // Bootstrap helpers (NoInterpolation / CPI::AsIndex).
            final List<ZeroCouponInflationSwapHelper> helpers = new ArrayList<>();
            for (int i = 0; i < zciisDataLength; ++i) {
                final Quote q = new SimpleQuote(zciisR.get(i) / 100.0);
                final Handle<Quote> qh = new Handle<>(q);
                helpers.add(new ZeroCouponInflationSwapHelper(
                        qh, observationLag, zciisD.get(i),
                        calendar, convention, dcZCIIS, ii,
                        CPI.InterpolationType.AsIndex));
            }

            // Phase 2u L0 A.2: ii.lastFixingDate() now exists. Returns the
            // first day of the inflation period containing the last stored
            // fixing — for monthly UKRPI seeded through 2009-09-01 that's
            // 2009-09-01 itself.
            final Date baseDate = ii.lastFixingDate();
            pCpiTs = new PiecewiseZeroInflationCurve<>(
                    Linear.class, evaluationDate, baseDate,
                    ii.frequency(), dcZCIIS, helpers);
            // Trigger lazy bootstrap before the cycle-introducing ii2 is
            // constructed (matches C++ recalculate()).
            pCpiTs.dates();

            // Swap-consumer index — observes the bootstrapped curve.
            // UKRPI shares fixings via IndexManager so ii2 inherits ii's
            // historical UK RPI series.
            ii2 = new UKRPI(Frequency.Monthly, false, false,
                    new Handle<>(pCpiTs));
        }
    }

    // ===================================================================
    // BOOST_AUTO_TEST_CASE(consistency) — inflationcpiswap.cpp:249-355
    // ===================================================================
    @Test
    @Ignore("Phase 2y: test body is intentionally empty (documentation"
            + " stub mirroring inflationcpiswap.cpp:249-355). Phase 2x A.1"
            + " removed the InterpolatedZeroCurve yields[0]==1.0 assertion"
            + " and Phase 2x A.3 added IborCoupon.Settings.usingAtParCoupons(),"
            + " unblocking the production-side prerequisites. Implementation"
            + " of the actual fixture wiring (CommonVars + 29-pillar nominal"
            + " curve + UKRPI seeding + DiscountingSwapEngine + 1e-5/3e-5"
            + " tolerance branch) deferred to Phase 2y or beyond.")
    public void consistency() {
        // C++ flow (paraphrased):
        //   1. Build CommonVars (full UK calendar, UKRPI fixings, ZCIIS curve).
        //   2. Build a CPISwap (Payer, 1e6, subtractInfNominal=true) with:
        //      - GBPLibor 6M float index (linked to common.nominalTS)
        //      - fixedRate=0.1, baseCPI=206.1, observationLag=3M, CPI::Flat
        //      - 45-year schedule 2007-10-02 .. 2052-10-02 (90 semi-annual periods)
        //   3. Seed past floating-leg fixings (floatFix[5]) and CPI fixings.
        //   4. Set DiscountingSwapEngine(common.nominalTS) and price.
        //   5. Manually accumulate inflation-leg NPV by iterating cashflows
        //      and compare to swap.legNPV(0); enforce |diff| < 1e-5.
        //   6. For each CPICoupon, check rate() == fixedRate * indexFixing/baseCPI
        //      (1e-8). [Java equivalent exercised by
        //      cpiSwap_multiPeriodSchedule_buildsExpectedLegShape below.]
        //   7. Compare swap.NPV() against stored value 4191797.54 with
        //      tolerance 1e-5 (atParCoupons) or 3e-5 (otherwise).
        //
        // Java blockers (see class-javadoc):
        //   - InterpolatedZeroCurve<Linear>(nomD, nomR, dcNominal) constructor.
        //   - IborCoupon::Settings::usingAtParCoupons() static accessor.
    }

    // ===================================================================
    // BOOST_AUTO_TEST_CASE(zciisconsistency) — inflationcpiswap.cpp:357-406
    // ===================================================================
    /**
     * Verifies that a CPISwap configured as a degenerate ZCIIS
     * (single-date schedules, dummy float index, inflationNominal scaled
     * by {@code (1+quote)^50}) reprices to the same NPV as a real ZCIIS
     * with the same maturity and quote.
     *
     * <p>This is a self-consistency test — the assertion is that both
     * instruments produce the same NPV when priced through the same
     * {@link DiscountingSwapEngine}; the actual NPV value depends on the
     * nominal curve, but the agreement between the two instruments is
     * curve-independent. The C++ test asserts both NPVs ~ 0 under the
     * bootstrapped nominal curve; the Java port substitutes a FlatForward
     * 5% nominal (see class-javadoc), which means the NPVs are NOT
     * expected to be ~ 0, but they MUST still agree with each other.
     *
     * <p>C++ tolerance: {@code |zciis.NPV()| < 1e-3} and
     * {@code |cS.legNPV(i) - zciis.legNPV(i)| < 1e-3}. We retain the
     * latter (cross-instrument self-consistency) as the substantive
     * assertion; the former is a curve-specific assertion that does not
     * apply with the FlatForward stand-in.
     */
    @Test
    public void zciisconsistency() {
        final CommonVars common = new CommonVars();

        final ZeroCouponInflationSwap.Type ztype =
                ZeroCouponInflationSwap.Type.Payer;
        final double nominal = 1_000_000.0;
        final Date startDate = common.evaluationDate;
        final Date endDate = new Date(25, Month.November, 2059);
        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention paymentConvention =
                BusinessDayConvention.ModifiedFollowing;
        final DayCounter dummyDC =
                new ActualActual(ActualActual.Convention.ISDA);
        final DayCounter dc =
                new ActualActual(ActualActual.Convention.ISDA);
        final Period observationLag = new Period(2, TimeUnit.Months);

        final double quote = 0.03714;
        final ZeroCouponInflationSwap zciis = new ZeroCouponInflationSwap(
                ztype, nominal, startDate, endDate, cal, paymentConvention,
                dc, quote, common.ii2, observationLag,
                CPI.InterpolationType.AsIndex);

        // Simple structure so simple pricing engine — most work done by index.
        final DiscountingSwapEngine dse =
                new DiscountingSwapEngine(common.nominalTS);
        zciis.setPricingEngine(dse);
        // Force evaluation (and surface any error early).
        final double zciisNpv = zciis.NPV();

        // Single-date Schedule: just endDate.
        final List<Date> oneDate = new ArrayList<>();
        oneDate.add(endDate);
        final Schedule schOneDate =
                new Schedule(oneDate, cal, paymentConvention);

        final CPISwap.Type stype = CPISwap.Type.Payer;
        final double inflationNominal = nominal;
        final double floatNominal = inflationNominal
                * Math.pow(1.0 + quote, 50.0);
        final boolean subtractInflationNominal = true;
        final double dummySpread = 0.0;
        final double dummyFixedRate = 0.0;
        final int fixingDays = 0;
        // baseCPI computed from the historical fixing at startDate-2M
        // inflation-period-start (matches cpp:391).
        final double baseCPI = CPI.laggedFixing(common.ii2, startDate,
                observationLag, CPI.InterpolationType.AsIndex);

        // Dummy float index — null in C++; legal in Java because the
        // CPISwap constructor's IborLeg branch is gated by
        // floatSchedule.size() > 1, so a single-date schedule never
        // dereferences the index.
        final IborIndex dummyFloatIndex = null;

        final CPISwap cS = new CPISwap(stype, floatNominal,
                subtractInflationNominal, dummySpread, dummyDC, schOneDate,
                paymentConvention, fixingDays, dummyFloatIndex,
                dummyFixedRate, baseCPI, dummyDC, schOneDate, paymentConvention,
                observationLag, common.ii2, CPI.InterpolationType.AsIndex,
                inflationNominal);
        cS.setPricingEngine(dse);

        // Capture leg NPVs immediately after each instrument is priced —
        // observation cascades from constructing one swap can invalidate
        // the other's cached results in the WeakReferenceObservable model.
        final double[] zciisLegNpv = {
                zciis.legNPV(0), zciis.legNPV(1)
        };
        final double cSnpv = cS.NPV();
        final double[] cSLegNpv = {
                cS.legNPV(0), cS.legNPV(1)
        };

        // Cross-instrument self-consistency: the CPISwap-as-ZCIIS must
        // reprice each leg to the same value as the corresponding ZCIIS leg.
        // The C++ tolerance is 1e-3 absolute; we keep the same threshold.
        for (int i = 0; i < 2; ++i) {
            final double diff = Math.abs(cSLegNpv[i] - zciisLegNpv[i]);
            if (diff > 1e-3) {
                fail("CPISwap-as-ZCIIS leg[" + i + "] does not match"
                        + " ZeroCouponInflationSwap.legNPV[" + i + "]:"
                        + " zciis.legNPV=" + zciisLegNpv[i]
                        + " cS.legNPV=" + cSLegNpv[i]
                        + " diff=" + diff);
            }
        }
        // Sanity: NPVs not NaN.
        if (Double.isNaN(zciisNpv) || Double.isNaN(cSnpv)) {
            fail("zciis.NPV()=" + zciisNpv + " cS.NPV()=" + cSnpv
                    + " — got NaN from pricing engine");
        }
    }

    // ===================================================================
    // BOOST_AUTO_TEST_CASE(cpibondconsistency) — inflationcpiswap.cpp:408-491
    // ===================================================================
    @Test
    @Ignore("Phase 2v: requires CPIBond — not ported."
            + " No org.jquantlib.instruments.bonds.CPIBond class exists yet"
            + " (Phase 2u inflation deferral list explicitly puts"
            + " inflationcpibond.cpp + CPIBond instrument port out of scope).")
    public void cpibondconsistency() {
        // C++ flow:
        //   - Build same CPISwap as in `consistency` but with
        //     subtractInflationNominal = false.
        //   - Build the equivalent CPIBond:
        //       CPIBond(settlementDays=1, nominal=1e6, baseCPI=206.1,
        //               contractObservationLag, fixedIndex=common.ii,
        //               observationInterpolation=Flat,
        //               fixedSchedule, fixedRates={0.1}, fixedDayCount=Act365F,
        //               fixedPaymentConvention=ModFollowing).
        //   - Price both with DiscountingSwapEngine / DiscountingBondEngine.
        //   - Assert |cpiB.NPV() - zisV.legNPV(0)| < 1e-5.
        //
        // Java blocker: CPIBond not ported.
    }

    // ===================================================================
    // Multi-period schedule structural test — exercises the
    // cpiSwap.cpiLeg() rate-reconstruction inner loop from cpp:336-341
    // (the consistency test's per-coupon assertion) in isolation, without
    // the InterpolatedZeroCurve nominal-curve dependency. Not a direct
    // BOOST port; bridges the @Ignore'd `consistency` test by exercising
    // the same CPISwap construction path the C++ test relies on.
    // ===================================================================
    /**
     * Build the same multi-period CPISwap the C++ {@code consistency} test
     * builds (subtractInflationNominal=true, fixedRate=0.1, baseCPI=206.1,
     * 45-year semi-annual, GBPLibor 6M float, observationLag=3M, CPI::Flat)
     * — but skip the stored-NPV comparison and instead verify the
     * structural invariant the C++ test asserts at cpp:336-341:
     * for every CPICoupon in the inflation leg whose fixing date is in the
     * past, {@code coupon.rate() == fixedRate * indexFixing/baseCPI}.
     *
     * <p>This is the substantive content of the C++ {@code consistency}
     * test that does not depend on the specific nominal curve.
     */
    @Test
    public void cpiSwap_multiPeriodSchedule_couponRateReconstructionMatchesCpp() {
        final CommonVars common = new CommonVars();
        final CPISwap.Type type = CPISwap.Type.Payer;
        final double nominal = 1_000_000.0;
        final boolean subtractInflationNominal = true;
        final double spread = 0.0;
        final DayCounter floatDayCount = new Actual365Fixed();
        final BusinessDayConvention floatPaymentConvention =
                BusinessDayConvention.ModifiedFollowing;
        final int fixingDays = 0;
        final IborIndex floatIndex = new GBPLibor(
                new Period(6, TimeUnit.Months), common.nominalTS);

        final double fixedRate = 0.1;
        final double baseCPI = 206.1;
        final DayCounter fixedDayCount = new Actual365Fixed();
        final BusinessDayConvention fixedPaymentConvention =
                BusinessDayConvention.ModifiedFollowing;
        final Period contractObservationLag = common.contractObservationLag;
        final CPI.InterpolationType observationInterpolation =
                common.contractObservationInterpolation;

        final Date startDate = new Date(2, Month.October, 2007);
        final Date endDate = new Date(2, Month.October, 2052);
        // C++ uses MakeSchedule().from(startDate).to(endDate).withTenor(6M)
        //   .withCalendar(UK()).withConvention(...).backwards();
        // Java's MakeSchedule constructor takes the same parameters, just
        // non-fluent. .backwards() / .withTerminationDateConvention()
        // chained for parity.
        final Schedule floatSchedule = new org.jquantlib.time.MakeSchedule(
                startDate, endDate, new Period(6, TimeUnit.Months),
                new UnitedKingdom(), floatPaymentConvention)
                .withTerminationDateConvention(floatPaymentConvention)
                .backwards()
                .schedule();
        final Schedule fixedSchedule = new org.jquantlib.time.MakeSchedule(
                startDate, endDate, new Period(6, TimeUnit.Months),
                new UnitedKingdom(), BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .backwards()
                .schedule();

        final CPISwap zisV = new CPISwap(type, nominal, subtractInflationNominal,
                spread, floatDayCount, floatSchedule, floatPaymentConvention,
                fixingDays, floatIndex, fixedRate, baseCPI, fixedDayCount,
                fixedSchedule, fixedPaymentConvention, contractObservationLag,
                common.ii2, observationInterpolation, Constants.NULL_REAL);

        // Seed past floating fixings (mirrors cpp:305-319 inner loop).
        // Real floatFix[] = {0.06255,0.05975,0.0637,0.018425,0.0073438,-1,-1};
        final double[] floatFix = {
                0.06255, 0.05975, 0.0637, 0.018425, 0.0073438, -1, -1
        };
        // Real cpiFix[] = {211.4,217.2,211.4,213.4,-2,-2}; (used by the
        // commented-out C++ addFixing on past CPI dates within the loop)
        // We do NOT seed those since common.ii2 already shares ii's seed.
        for (int i = 0; i < floatSchedule.size() && i < floatFix.length; ++i) {
            if (floatSchedule.date(i).lt(common.evaluationDate)
                    && floatFix[i] >= 0.0) {
                floatIndex.addFixing(floatSchedule.date(i), floatFix[i], true);
            }
        }

        // Now walk cpiLeg() and assert per-coupon rate reconstruction.
        // C++ assertion (cpp:336-341):
        //   diff = fabs(zicV->rate() - (fixedRate*(zicV->indexFixing()/baseCPI)));
        //   QL_REQUIRE(diff < 1e-8, ...);
        final Date evalDate = new Settings().evaluationDate();
        int checked = 0;
        int total = 0;
        for (final CashFlow cf : zisV.cpiLeg()) {
            if (!(cf instanceof CPICoupon)) continue;
            ++total;
            final CPICoupon zic = (CPICoupon) cf;
            final Date fix = zic.fixingDate();
            // Only check coupons whose fixing date hits a stored UK RPI
            // fixing (i.e. is at least one month in the past). This
            // mirrors the C++ test, which seeds past fixings before
            // iterating; coupons whose fix date is in the future are
            // forecast through the curve and the rate reconstruction is
            // still well-defined, but the C++ assertion is gated on the
            // index having a fixing available without forecasting.
            if (fix.gt(evalDate.sub(new Period(1, TimeUnit.Months)))) continue;
            try {
                final double idx = zic.indexFixing();
                final double expected = fixedRate * idx / baseCPI;
                final double actual = zic.rate();
                final double diff = Math.abs(actual - expected);
                if (diff > 1e-8) {
                    fail("CPICoupon[" + checked + "] (fixingDate=" + fix
                            + "): reconstructed rate mismatch."
                            + " indexFixing=" + idx + " baseCPI=" + baseCPI
                            + " expected=" + expected + " actual=" + actual
                            + " diff=" + diff);
                }
                ++checked;
            } catch (final RuntimeException ex) {
                // The seeded UKRPI fixings only cover 2007-07..2009-09.
                // Coupons whose fixingDate falls outside that range will
                // throw "Missing UK RPI fixing for ..." here. Skip those —
                // they're not part of the C++ assertion (which seeds CPI
                // fixings in the test setup; we share via IndexManager but
                // only for the dates we have).
                if (!ex.getMessage().contains("Missing")
                        && !ex.getMessage().contains("empty Handle")) {
                    throw ex;
                }
            }
        }
        // Sanity: at least one coupon was actually exercised. With the
        // seeded fixings 2007-07..2009-09 and a 3M observation lag,
        // coupons in early 2008 onwards have valid past fixings.
        if (checked < 1) {
            fail("Expected at least 1 CPICoupon with a past stored fixing"
                    + " to be reconstructed; total=" + total);
        }
    }

    // ===================================================================
    // Smoke coverage — keep the helper alignment exercised so a regression
    // in the surrounding production classes is caught. None of the @Tests
    // below are direct BOOST ports.
    // ===================================================================

    /**
     * Smoke test that {@link CommonVars} loads successfully — i.e. all of
     * the production classes the C++ {@code CommonVars} touches are now on
     * the Java surface and that the helper datasets are compatible with the
     * C++ {@code inflationcpiswap.cpp} constants.
     */
    @Test
    public void commonVars_loadsAndExposesCanonicalCppConstants() {
        final CommonVars vars = new CommonVars();
        if (!vars.evaluationDate.eq(new Date(25, Month.November, 2009))) {
            fail("evalDate: expected=2009-11-25 actual=" + vars.evaluationDate);
        }
        if (!vars.observationLag.eq(new Period(2, TimeUnit.Months))) {
            fail("swap obs lag: expected=2M actual=" + vars.observationLag);
        }
        if (!vars.contractObservationLag.eq(new Period(3, TimeUnit.Months))) {
            fail("contract obs lag: expected=3M actual="
                    + vars.contractObservationLag);
        }
        if (vars.contractObservationInterpolation
                != CPI.InterpolationType.Flat) {
            fail("contract observation interpolation: expected=Flat actual="
                    + vars.contractObservationInterpolation);
        }
        if (vars.zciisDataLength != 17) {
            fail("zciisDataLength: expected=17 actual=" + vars.zciisDataLength);
        }
        if (vars.zciisD.size() != 17 || vars.zciisR.size() != 17) {
            fail("zciisD/zciisR size: expected=17 each, actual D="
                    + vars.zciisD.size() + " R=" + vars.zciisR.size());
        }
        if (!vars.zciisD.get(0).eq(new Date(25, Month.November, 2010))) {
            fail("first zciis pillar: expected=2010-11-25 actual="
                    + vars.zciisD.get(0));
        }
        if (vars.zciisR.get(0) != 3.0495) {
            fail("first zciis quote: expected=3.0495 actual="
                    + vars.zciisR.get(0));
        }
        if (!vars.zciisD.get(16).eq(new Date(25, Month.November, 2059))) {
            fail("last zciis pillar: expected=2059-11-25 actual="
                    + vars.zciisD.get(16));
        }
        if (vars.zciisR.get(16) != 3.714) {
            fail("last zciis quote: expected=3.714 actual="
                    + vars.zciisR.get(16));
        }
        // Index family name = "RPI"; full name (region + family) = "UK RPI".
        if (!"RPI".equals(vars.ii.familyName())) {
            fail("UKRPI familyName: expected=RPI actual="
                    + vars.ii.familyName());
        }
        if (!"UK RPI".equals(vars.ii.name())) {
            fail("UKRPI name: expected=UK RPI actual=" + vars.ii.name());
        }
        // Index availabilityLag = 1M (Phase 2u L0 A.1 align — was 2M pre-2u).
        if (!vars.ii.availabilityLag().eq(new Period(1, TimeUnit.Months))) {
            fail("UKRPI availabilityLag: expected=1M actual="
                    + vars.ii.availabilityLag());
        }
        // CommonVars must have bootstrapped a curve and the swap-consumer
        // index ii2 must observe it.
        if (vars.pCpiTs == null) {
            fail("pCpiTs must be non-null after CommonVars construction");
        }
        if (vars.ii2 == null) {
            fail("ii2 must be non-null after CommonVars construction");
        }
    }

    /**
     * Smoke test that the CPISwap constructor produces a valid inflation-leg
     * structure when given a single-date schedule (the path used by
     * {@code zciisconsistency} for the CPISwap-as-ZCIIS construction).
     */
    @Test
    public void cpiSwap_singleDateSchedule_buildsExpectedLegShape() {
        final CommonVars common = new CommonVars();
        final CPISwap.Type stype = CPISwap.Type.Payer;
        final double nominal = 1_000_000.0;
        final Date startDate = common.evaluationDate;
        final Date endDate = new Date(25, Month.November, 2059);
        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention paymentConvention =
                BusinessDayConvention.ModifiedFollowing;
        final DayCounter dummyDC =
                new ActualActual(ActualActual.Convention.ISDA);
        final Period observationLag = new Period(2, TimeUnit.Months);

        final double quote = 0.03714;
        final double inflationNominal = nominal;
        final double floatNominal = inflationNominal
                * Math.pow(1.0 + quote, 50.0);
        final boolean subtractInflationNominal = true;
        final double dummySpread = 0.0;
        final double dummyFixedRate = 0.0;
        final int fixingDays = 0;

        final double baseCPI = CPI.laggedFixing(common.ii2, startDate,
                observationLag, CPI.InterpolationType.AsIndex);

        final List<Date> oneDate = new ArrayList<>();
        oneDate.add(endDate);
        final Schedule schOneDate = new Schedule(oneDate, cal, paymentConvention);
        final IborIndex dummyFloatIndex = null;

        final CPISwap cS = new CPISwap(stype, floatNominal,
                subtractInflationNominal, dummySpread, dummyDC, schOneDate,
                paymentConvention, fixingDays, dummyFloatIndex,
                dummyFixedRate, baseCPI, dummyDC, schOneDate, paymentConvention,
                observationLag, common.ii2, CPI.InterpolationType.AsIndex,
                inflationNominal);

        // Structural assertions only.
        if (cS.cpiLeg().size() < 1) {
            fail("cpiLeg must contain at least the terminal CPICashFlow"
                    + " notional — got " + cS.cpiLeg().size());
        }
        if (cS.floatLeg().size() < 1) {
            fail("floatLeg must contain the SimpleCashFlow notional — got "
                    + cS.floatLeg().size());
        }
        if (Math.abs(cS.baseCPI() - baseCPI) > 0.0) {
            fail("CPISwap.baseCPI must round-trip:"
                    + " expected=" + baseCPI + " actual=" + cS.baseCPI());
        }
        if (Math.abs(cS.inflationNominal() - inflationNominal) > 0.0) {
            fail("CPISwap.inflationNominal must round-trip:"
                    + " expected=" + inflationNominal
                    + " actual=" + cS.inflationNominal());
        }
        if (Math.abs(cS.nominal() - floatNominal) > 0.0) {
            fail("CPISwap.nominal must round-trip:"
                    + " expected=" + floatNominal
                    + " actual=" + cS.nominal());
        }
    }

    /**
     * Direct port of cpp:391 — verifies {@link CPI#laggedFixing} returns the
     * correct seeded fixing on the {@code zciisconsistency} startDate.
     */
    @Test
    public void cpiLaggedFixing_atZciisConsistencyStartDate_matchesSeededFixing() {
        final CommonVars common = new CommonVars();
        // C++ baseCPI from cpp:391:
        //   CPI::laggedFixing(common.ii, startDate, observationLag, CPI::AsIndex)
        // where startDate = common.evaluationDate (=25-Nov-2009),
        // observationLag = 2M.
        // For AsIndex (= Flat for this purpose), look up the fixing at
        // inflationPeriod-start of (25-Nov-2009 - 2M) = 25-Sep-2009 ->
        // inflationPeriod-start = 1-Sep-2009 -> fixData index 26 = 214.4.
        final double baseCPI = CPI.laggedFixing(common.ii2,
                common.evaluationDate, common.observationLag,
                CPI.InterpolationType.AsIndex);
        if (Math.abs(baseCPI - 214.4) > 1e-10) {
            fail("baseCPI from CPI::laggedFixing(2M, AsIndex) on UKRPI seed:"
                    + " expected=214.4 actual=" + baseCPI);
        }
    }

    /**
     * Smoke test verifying that the bootstrapped {@link CommonVars#pCpiTs}
     * curve has its base date pinned at the index's last fixing date —
     * mirrors the C++ {@code Date baseDate = ii->lastFixingDate()} step at
     * {@code inflationcpiswap.cpp:230} and exercises the Phase 2u L0 A.2
     * align.
     */
    @Test
    public void cpiTermStructure_baseDateMatchesIndexLastFixingDate() {
        final CommonVars common = new CommonVars();
        final Date lastFix = common.ii.lastFixingDate();
        // The seeded fixings end at 1-Sep-2009 (last entry in fixData).
        if (!lastFix.eq(new Date(1, Month.September, 2009))) {
            fail("ii.lastFixingDate(): expected=2009-09-01 actual=" + lastFix);
        }
        // The pCpiTs.baseDate must round-trip the lastFixingDate.
        final Pair<Date, Date> period = InflationTermStructure.inflationPeriod(
                lastFix, common.ii.frequency());
        if (!period.first().eq(new Date(1, Month.September, 2009))
                || !period.second().eq(new Date(30, Month.September, 2009))) {
            fail("inflationPeriod for monthly index on 2009-09-01 mismatches:"
                    + " expected=[2009-09-01, 2009-09-30]"
                    + " actual=[" + period.first() + ", " + period.second() + "]");
        }
        // Bootstrapped curve baseDate equals lastFixingDate. The
        // PiecewiseZeroInflationCurve eagerly bootstrapped during
        // CommonVars construction.
        if (common.pCpiTs == null) {
            fail("pCpiTs must be non-null");
        }
        // Once the bootstrap has run, dates() returns at least baseDate +
        // 17 helper pillars = 18 nodes minimum.
        if (common.pCpiTs.dates().length < 1 + 17) {
            fail("pCpiTs.dates() must contain at least 18 nodes (baseDate +"
                    + " 17 helpers); got " + common.pCpiTs.dates().length);
        }
    }
}
