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
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.CPICapFloorTermPriceSurface;
import org.jquantlib.experimental.inflation.InterpolatedCPICapFloorTermPriceSurface;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.instruments.CPICapFloor;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.interpolations.factories.Bilinear;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.inflation.InterpolatingCPICapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
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
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Faithful port of {@code migration-harness/cpp/quantlib/test-suite/inflationcpicapfloor.cpp}
 * (QuantLib v1.42.1, 434 LOC). Phase 2u Track D — replaces the prior smoke
 * test with full inflation cap/floor surface + engine cross-validation.
 *
 * <p>Each {@code BOOST_AUTO_TEST_CASE} is mirrored as a {@code @Test} method
 * with the same name. The shared {@code CommonVars} struct is mirrored as a
 * private static inner class.
 *
 * <h3>Tolerance tiers</h3>
 * <ul>
 *   <li>Surface reproduction at grid points: tight {@code 1e-7}
 *       (matches C++ {@code QL_REQUIRE} threshold).</li>
 *   <li>Surface {@code price()} at grid corner: tight {@code 1e-12}
 *       (matches C++ {@code BOOST_ERROR} threshold).</li>
 *   <li>Engine NPV at grid point: tight {@code 1e-10}
 *       (matches C++ threshold).</li>
 * </ul>
 *
 * <h3>Yield curve note</h3>
 * <p>The C++ test builds a 32-pillar zero curve via {@code
 * InterpolatedZeroCurve<Linear>(dates, rates, dcNominal)}. The Java
 * {@link org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve} has
 * a long-standing copy-paste bug ({@code data[0] == 1.0} assertion intended
 * for discount factors but applied to zero rates) that prevents using it
 * here. This is out of Track D scope; it is queued for a future Phase 2x
 * align. As a stand-in we use a {@link FlatForward} 5%/Annual curve for the
 * nominal handle.
 *
 * <p>This substitution does not affect the test outcomes because the
 * {@code performCalculations()} discount-factor path inside
 * {@link InterpolatedCPICapFloorTermPriceSurface} is only exercised when
 * filling cap-strike rows with floor data and vice-versa via put/call
 * parity. All asserted strikes in this test are present in the original
 * input grids ({@code cStrikes}, {@code fStrikes}), so the surface returns
 * the directly-stored prices (not parity-extrapolated values), and the
 * choice of nominal curve has no effect on the asserted residuals.
 *
 * <h3>Observer-cycle workaround</h3>
 * <p>The C++ test uses a {@code RelinkableHandle<ZeroInflationTermStructure>}
 * (initially empty) to construct the index, then {@code linkTo}s the
 * bootstrapped curve afterwards. In Java's WeakReferenceObservable model this
 * triggers an unbounded observer cascade
 * (helper → curve → handle → index → helper → ...). Following the convention
 * established in {@code CPICapFloorTermPriceSurfaceTest} we instead build a
 * helper-bootstrap index without a curve handle, then construct a second
 * index ({@code ii2}) bound to the bootstrapped curve for the surface to
 * consume. UKRPI fixings are stored in the {@code IndexManager} and shared
 * across instances, so {@code ii2} sees the same historical fixings as
 * {@code ii}.
 */
public class CPICapFloorTest {

    /**
     * Mirror of the C++ {@code CommonVars} struct
     * ({@code inflationcpicapfloor.cpp:81-301}). Rebuilds on each test
     * invocation; the C++ relies on the same constructor running per
     * {@code BOOST_AUTO_TEST_CASE} case.
     */
    private static final class CommonVars {

        // option variables / usual setup
        final int length = 7;
        final double volatility = 0.01;
        final Frequency frequency = Frequency.Annual;
        final List<Double> nominals = new ArrayList<>();
        final Calendar calendar = new UnitedKingdom();
        final BusinessDayConvention convention = BusinessDayConvention.ModifiedFollowing;
        final int fixingDays = 0;
        final int settlementDays = 0;
        final Date evaluationDate;
        final Date settlement;
        final Date startDate;
        final Period observationLag = new Period(2, TimeUnit.Months);
        final Period contractObservationLag = new Period(3, TimeUnit.Months);
        final CPI.InterpolationType contractObservationInterpolation = CPI.InterpolationType.Flat;
        final DayCounter dcZCIIS = new ActualActual(ActualActual.Convention.ISDA);
        final DayCounter dcNominal = new ActualActual(ActualActual.Convention.ISDA);

        // ii is the helper-bootstrap index (no curve handle); ii2 is the
        // surface-consumer index linked to the bootstrapped pCPIts. See
        // class-javadoc "Observer-cycle workaround" for rationale.
        final UKRPI ii;
        final UKRPI ii2;
        final Handle<YieldTermStructure> nominalUK;

        final double baseZeroRate;
        final PiecewiseZeroInflationCurve<
                org.jquantlib.math.interpolations.factories.Linear> pCPIts;

        // CF surface input
        final double[] cStrikesUK;
        final double[] fStrikesUK;
        final Period[] cfMaturitiesUK;
        final Matrix cPriceUK;
        final Matrix fPriceUK;

        CommonVars() {
            nominals.add(1_000_000.0);

            final Date today = new Date(1, Month.June, 2010);
            evaluationDate = calendar.adjust(today);
            new Settings().setEvaluationDate(evaluationDate);
            settlement = calendar.advance(today, settlementDays, TimeUnit.Days);
            startDate = settlement;

            // UK RPI index (no curve handle) for helper bootstrap.
            ii = new UKRPI(Frequency.Monthly, false, false);

            // C++ MakeSchedule().from(1-Jul-2007).to(1-Apr-2010).withFrequency(Monthly)
            final Schedule rpiSchedule = new MakeSchedule(
                    new Date(1, Month.July, 2007),
                    new Date(1, Month.April, 2010),
                    new Period(1, TimeUnit.Months), calendar, convention)
                    .schedule();
            final double[] fixData = {
                    206.1, 207.3, 208.0, 208.9, 209.7, 210.9,
                    209.8, 211.4, 212.1, 214.0, 215.1, 216.8,   //  2008
                    216.5, 217.2, 218.4, 217.7, 216.0, 212.9,
                    210.1, 211.4, 211.3, 211.5, 212.8, 213.4,   //  2009
                    213.4, 214.4, 215.3, 216.0, 216.6, 218.0,
                    217.9, 219.2, 220.7, 222.8                  //  2010
            };
            for (int i = 0; i < rpiSchedule.size(); i++) {
                ii.addFixing(rpiSchedule.date(i), fixData[i], true);
            }

            // Nominal curve. C++ builds a 32-pillar InterpolatedZeroCurve<Linear>;
            // Java's InterpolatedZeroCurve has a known data[0]==1.0 assertion
            // bug (Phase 2x backlog). Use FlatForward 5% as a stand-in — see
            // class-javadoc rationale above.
            final FlatForward nominalTS = new FlatForward(
                    evaluationDate, 0.05, dcNominal,
                    Compounding.Continuous, Frequency.Annual);
            nominalUK = new Handle<YieldTermStructure>(nominalTS);

            // Build zero inflation curve.
            // ZCIIS market data (rates in percent, divide by 100 in helper).
            final Datum[] zciisData = {
                    new Datum(new Date(1, Month.June, 2011), 3.087),
                    new Datum(new Date(1, Month.June, 2012), 3.12),
                    new Datum(new Date(1, Month.June, 2013), 3.059),
                    new Datum(new Date(1, Month.June, 2014), 3.11),
                    new Datum(new Date(1, Month.June, 2015), 3.15),
                    new Datum(new Date(1, Month.June, 2016), 3.207),
                    new Datum(new Date(1, Month.June, 2017), 3.253),
                    new Datum(new Date(1, Month.June, 2018), 3.288),
                    new Datum(new Date(1, Month.June, 2019), 3.314),
                    new Datum(new Date(1, Month.June, 2020), 3.401),
                    new Datum(new Date(1, Month.June, 2022), 3.458),
                    new Datum(new Date(1, Month.June, 2025), 3.52),
                    new Datum(new Date(1, Month.June, 2030), 3.655),
                    new Datum(new Date(1, Month.June, 2035), 3.668),
                    new Datum(new Date(1, Month.June, 2040), 3.695),
                    new Datum(new Date(1, Month.June, 2050), 3.634),
                    new Datum(new Date(1, Month.June, 2060), 3.629),
            };

            final List<ZeroCouponInflationSwapHelper> helpers = new ArrayList<>();
            for (final Datum d : zciisData) {
                final Quote q = new SimpleQuote(d.rate / 100.0);
                final Handle<Quote> qh = new Handle<>(q);
                helpers.add(new ZeroCouponInflationSwapHelper(
                        qh, observationLag, d.date, calendar,
                        convention, dcZCIIS, ii, CPI.InterpolationType.AsIndex));
            }

            // C++: baseZeroRate = zciisData[0].rate / 100.0
            baseZeroRate = zciisData[0].rate / 100.0;
            final Date baseDate = ii.lastFixingDate();
            pCPIts = new PiecewiseZeroInflationCurve<>(
                    org.jquantlib.math.interpolations.factories.Linear.class,
                    evaluationDate, baseDate, ii.frequency(), dcZCIIS, helpers);
            // Trigger lazy bootstrap before the cycle-introducing ii2 is
            // constructed (matches C++ recalculate()).
            pCPIts.dates();

            // Surface-consumer index — observes the bootstrapped curve.
            // UKRPI shares fixings via IndexManager so ii2 inherits ii's
            // historical UK RPI series.
            ii2 = new UKRPI(Frequency.Monthly, false, false, new Handle<>(pCPIts));

            // CPI cap/floor price surface input data
            cfMaturitiesUK = new Period[] {
                    new Period(3, TimeUnit.Years),
                    new Period(5, TimeUnit.Years),
                    new Period(7, TimeUnit.Years),
                    new Period(10, TimeUnit.Years),
                    new Period(15, TimeUnit.Years),
                    new Period(20, TimeUnit.Years),
                    new Period(30, TimeUnit.Years)
            };
            cStrikesUK = new double[] {0.03, 0.04, 0.05, 0.06};
            fStrikesUK = new double[] {-0.01, 0.0, 0.01, 0.02};
            final int ncStrikes = 4, nfStrikes = 4, ncfMaturities = 7;

            // C++ stores price[maturity][strike]; the surface needs
            // priceUK[strike][maturity], so transpose on copy.
            final double[][] cPrice = {
                    {227.6, 100.27, 38.8, 14.94},
                    {345.32, 127.9, 40.59, 14.11},
                    {477.95, 170.19, 50.62, 16.88},
                    {757.81, 303.95, 107.62, 43.61},
                    {1140.73, 481.89, 168.4, 63.65},
                    {1537.6, 607.72, 172.27, 54.87},
                    {2211.67, 839.24, 184.75, 45.03}};
            final double[][] fPrice = {
                    {15.62, 28.38, 53.61, 104.6},
                    {21.45, 36.73, 66.66, 129.6},
                    {24.45, 42.08, 77.04, 152.24},
                    {39.25, 63.52, 109.2, 203.44},
                    {36.82, 63.62, 116.97, 232.73},
                    {39.7, 67.47, 121.79, 238.56},
                    {41.48, 73.9, 139.75, 286.75}};

            cPriceUK = new Matrix(ncStrikes, ncfMaturities);
            fPriceUK = new Matrix(nfStrikes, ncfMaturities);
            for (int i = 0; i < ncStrikes; i++) {
                for (int j = 0; j < ncfMaturities; j++) {
                    cPriceUK.set(i, j, cPrice[j][i] / 10000.0);
                }
            }
            for (int i = 0; i < nfStrikes; i++) {
                for (int j = 0; j < ncfMaturities; j++) {
                    fPriceUK.set(i, j, fPrice[j][i] / 10000.0);
                }
            }
        }
    }

    /** Mirrors the C++ {@code Datum} POD (date/rate pair). */
    private static final class Datum {
        final Date date;
        final double rate;

        Datum(final Date date, final double rate) {
            this.date = date;
            this.rate = rate;
        }
    }

    /**
     * Port of {@code BOOST_AUTO_TEST_CASE(cpicapfloorpricesurface)}
     * ({@code inflationcpicapfloor.cpp:304-368}).
     *
     * <p>Builds an {@link InterpolatedCPICapFloorTermPriceSurface} from the
     * standard UK CPI cap/floor data and verifies that the surface
     * reproduces every input price within {@code 1e-7}, then verifies that
     * {@link CPICapFloorTermPriceSurface#price(Period, double)} correctly
     * picks the floor branch for an in-the-money (vs ATM) strike.
     */
    @Test
    public void cpicapfloorpricesurface() {
        final CommonVars common = new CommonVars();

        final double nominal = 1.0;
        final InterpolatedCPICapFloorTermPriceSurface<Bilinear> cpiSurf =
                new InterpolatedCPICapFloorTermPriceSurface<>(
                        Bilinear.class,
                        nominal,
                        common.baseZeroRate,
                        common.observationLag,
                        common.calendar,
                        common.convention,
                        common.dcZCIIS,
                        common.ii2,
                        CPI.InterpolationType.Flat,
                        common.nominalUK,
                        common.cStrikesUK,
                        common.fStrikesUK,
                        common.cfMaturitiesUK,
                        common.cPriceUK,
                        common.fPriceUK);

        // Floor strikes — assert each input grid point reproduces.
        for (int i = 0; i < common.fStrikesUK.length; i++) {
            final double qK = common.fStrikesUK[i];
            for (int j = 0; j < common.cfMaturitiesUK.length; j++) {
                final Period t = common.cfMaturitiesUK[j];
                final double a = common.fPriceUK.get(i, j);
                final double b = cpiSurf.floorPrice(t, qK);
                assertTrue(String.format(
                        "cannot reproduce cpi floor data from surface at "
                                + "(strike=%g, maturity=%s): expected=%g actual=%g diff=%g",
                        qK, t, a, b, Math.abs(a - b)),
                        Math.abs(a - b) < 1.0e-7);
            }
        }

        // Cap strikes — assert each input grid point reproduces.
        for (int i = 0; i < common.cStrikesUK.length; i++) {
            final double qK = common.cStrikesUK[i];
            for (int j = 0; j < common.cfMaturitiesUK.length; j++) {
                final Period t = common.cfMaturitiesUK[j];
                final double a = common.cPriceUK.get(i, j);
                final double b = cpiSurf.capPrice(t, qK);
                assertTrue(String.format(
                        "cannot reproduce cpi cap data from surface at "
                                + "(strike=%g, maturity=%s): expected=%g actual=%g diff=%g",
                        qK, t, a, b, Math.abs(a - b)),
                        Math.abs(a - b) < 1.0e-7);
            }
        }

        // price(3y, 0.01) — 0.01 is ITM-floor (below ATM), so price() should
        // return floorPrice, which equals the input fPriceUK[2][0] = 53.61bps.
        final double premium = cpiSurf.price(new Period(3, TimeUnit.Years), 0.01);
        final double expPremium = common.fPriceUK.get(2, 0);
        if (Math.abs(premium - expPremium) > 1.0e-12) {
            fail("The requested premium, " + premium
                    + ", does not equal the expected premium, " + expPremium + ".");
        }

        // C++ resets common.hcpi to break the surface↔index circular reference.
        // Java GC is reachability-based, so this is unnecessary; left as
        // a doc-only equivalent.
    }

    /**
     * Port of {@code BOOST_AUTO_TEST_CASE(cpicapfloorpricer)}
     * ({@code inflationcpicapfloor.cpp:370-430}).
     *
     * <p>Builds the same surface as {@code cpicapfloorpricesurface}, then
     * constructs a 3-year ATM-Call CPI cap with {@code observationLag = 2M}
     * and {@code observationInterpolation = AsIndex}, and verifies the
     * {@link InterpolatingCPICapFloorEngine} reproduces the cached price
     * (227.6 bps from the input grid) within {@code 1e-10}.
     */
    @Test
    public void cpicapfloorpricer() {
        final CommonVars common = new CommonVars();

        final double nominal = 1.0;
        final CPICapFloorTermPriceSurface cpiCFpriceSurf =
                new InterpolatedCPICapFloorTermPriceSurface<>(
                        Bilinear.class,
                        nominal,
                        common.baseZeroRate,
                        common.observationLag,
                        common.calendar,
                        common.convention,
                        common.dcZCIIS,
                        common.ii2,
                        CPI.InterpolationType.Flat,
                        common.nominalUK,
                        common.cStrikesUK,
                        common.fStrikesUK,
                        common.cfMaturitiesUK,
                        common.cPriceUK,
                        common.fPriceUK);

        final Date startDate = new Settings().evaluationDate();
        final Date maturity = startDate.add(new Period(3, TimeUnit.Years));
        final Calendar fixCalendar = new UnitedKingdom();
        final Calendar payCalendar = new UnitedKingdom();
        final BusinessDayConvention fixConvention = BusinessDayConvention.Unadjusted;
        final BusinessDayConvention payConvention = BusinessDayConvention.ModifiedFollowing;
        final double strike = 0.03;
        final CPI.InterpolationType observationInterpolation = CPI.InterpolationType.AsIndex;
        final double baseCPI = CPI.laggedFixing(common.ii2, startDate,
                common.observationLag, observationInterpolation);

        final CPICapFloor aCap = new CPICapFloor(
                Option.Type.Call, nominal,
                startDate,    // start date of contract (only)
                baseCPI,
                maturity,     // pre-adjustment
                fixCalendar, fixConvention,
                payCalendar, payConvention,
                strike,
                common.ii2,
                common.observationLag,
                observationInterpolation);

        final Handle<CPICapFloorTermPriceSurface> cpiCFsurfUKh =
                new Handle<>(cpiCFpriceSurf);
        final PricingEngine engine = new InterpolatingCPICapFloorEngine(cpiCFsurfUKh);

        aCap.setPricingEngine(engine);

        // We should get back the cap premium at strike 0.03 i.e. 227.6 bps.
        final double cached = common.cPriceUK.get(0, 0);
        final double npv = aCap.NPV();

        assertTrue(String.format(
                "InterpolatingCPICapFloorEngine does not reproduce cached price: "
                        + "expected=%g actual=%g diff=%g",
                cached, npv, Math.abs(cached - npv)),
                Math.abs(cached - npv) < 1.0e-10);

        // C++ resets common.hcpi to break the surface↔index circular reference.
        // Java GC is reachability-based, so this is unnecessary.
    }
}
