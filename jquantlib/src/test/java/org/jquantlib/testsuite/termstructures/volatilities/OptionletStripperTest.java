/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.termstructures.volatilities;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.MakeCapFloor;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.capfloor.CapFloorTermVolSurface;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletStripper1;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.volatilities.optionlet.StrippedOptionletAdapter;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/optionletstripper.cpp} v1.42.1
 * (991 LOC, 8 test cases).
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong> — Java has only
 * the {@link
 * org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure}
 * abstract type and the {@link
 * org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility}
 * concrete; the stripper machinery
 * ({@code OptionletStripper1}, {@code OptionletStripper2}, {@code
 * StrippedOptionletAdapter}, {@code CapFloorTermVolCurve},
 * {@code CapFloorTermVolSurface}) is not yet ported.
 *
 * <ul>
 *   <li>{@code testFlatTermVolatilityStripping1} — flat-vol stripping</li>
 *   <li>{@code testTermVolatilityStripping1} — full surface stripping (LN)</li>
 *   <li>{@code testTermVolatilityStrippingNormalVol} — normal-vol</li>
 *   <li>{@code testTermVolatilityStrippingShiftedLogNormalVol} — SLN</li>
 *   <li>{@code testFlatTermVolatilityStripping2} — flat-vol stripper2</li>
 *   <li>{@code testTermVolatilityStripping2} — surface stripper2</li>
 *   <li>{@code testSwitchStrike} — switch-strike conversion</li>
 *   <li>{@code testTermVolatilityStripping1ON} — overnight-index variant</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/optionletstripper.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class OptionletStripperTest {

    @Test
    public void testFlatTermVolatilityStripping1() {
        // Mirrors C++ test-suite/optionletstripper.cpp::testFlatTermVolatilityStripping1
        // (Phase 5g.5 smoke-test — validates the OptionletStripper1 +
        //  StrippedOptionletAdapter port end-to-end against a flat term-vol
        //  surface, where the round-trip must reproduce constant-vol prices
        //  to TIGHT (1e-6) tolerance.)
        new Settings().setEvaluationDate(new Date(28, Month.October, 2013));

        final Calendar calendar = new Target();
        final DayCounter dayCounter = new Actual365Fixed();
        final double flatFwdRate = 0.04;
        final Handle<YieldTermStructure> yieldTermStructure =
                new Handle<YieldTermStructure>(new FlatForward(0, calendar, flatFwdRate, dayCounter));

        final int nTenors = 10;
        final List<Period> optionTenors = new ArrayList<Period>(nTenors);
        for (int i = 0; i < nTenors; ++i) {
            optionTenors.add(new Period(i + 1, TimeUnit.Years));
        }
        final int nStrikes = 10;
        final double[] strikes = new double[nStrikes];
        for (int j = 0; j < nStrikes; ++j) {
            strikes[j] = (j + 1) / 100.0;
        }

        final double flatVol = 0.18;
        final Matrix termV = new Matrix(nTenors, nStrikes);
        for (int i = 0; i < nTenors; ++i) {
            for (int j = 0; j < nStrikes; ++j) {
                termV.set(i, j, flatVol);
            }
        }
        final CapFloorTermVolSurface flatTermVolSurface = new CapFloorTermVolSurface(
                0, calendar, BusinessDayConvention.Following,
                optionTenors, strikes, termV, dayCounter);

        final IborIndex iborIndex = new Euribor6M(yieldTermStructure);

        final double accuracy = 1.0e-6;
        final double tolerance = 2.5e-8;

        final OptionletStripper1 stripper = new OptionletStripper1(
                flatTermVolSurface, iborIndex,
                Constants.NULL_REAL,
                accuracy, 100,
                new Handle<YieldTermStructure>(),
                org.jquantlib.model.VolatilityType.ShiftedLognormal, 0.0,
                false, null);

        final StrippedOptionletAdapter adapter = new StrippedOptionletAdapter(stripper);
        final Handle<OptionletVolatilityStructure> vol =
                new Handle<OptionletVolatilityStructure>(adapter);
        adapter.enableExtrapolation();

        final BlackCapFloorEngine strippedVolEngine = new BlackCapFloorEngine(
                yieldTermStructure, vol);

        for (int t = 0; t < optionTenors.size(); ++t) {
            for (int s = 0; s < strikes.length; ++s) {
                final CapFloor cap = new MakeCapFloor(CapFloor.Type.Cap,
                        optionTenors.get(t), iborIndex, strikes[s],
                        new Period(0, TimeUnit.Days))
                        .withPricingEngine(strippedVolEngine)
                        .value();
                final double priceFromStrippedVolatility = cap.NPV();

                final BlackCapFloorEngine constantVolEngine = new BlackCapFloorEngine(
                        yieldTermStructure, termV.get(t, s), dayCounter);
                cap.setPricingEngine(constantVolEngine);
                final double priceFromConstantVolatility = cap.NPV();

                final double error = Math.abs(priceFromStrippedVolatility - priceFromConstantVolatility);
                assertTrue("flat-stripping mismatch: tenor=" + optionTenors.get(t)
                        + " strike=" + strikes[s]
                        + " stripped=" + priceFromStrippedVolatility
                        + " constant=" + priceFromConstantVolatility
                        + " error=" + error
                        + " tol=" + tolerance,
                        error <= tolerance);
            }
        }
    }

    @Ignore("Phase 5f.5 — OptionletStripper1 + CapFloorTermVolSurface not ported")
    @Test
    public void testTermVolatilityStripping1() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Normal-vol stripping not ported")
    @Test
    public void testTermVolatilityStrippingNormalVol() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Shifted-log-normal stripping not ported")
    @Test
    public void testTermVolatilityStrippingShiftedLogNormalVol() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — OptionletStripper2 not ported")
    @Test
    public void testFlatTermVolatilityStripping2() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — OptionletStripper2 + surface not ported")
    @Test
    public void testTermVolatilityStripping2() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — switch-strike conversion utility not ported")
    @Test
    public void testSwitchStrike() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — overnight-index optionlet stripping not ported")
    @Test
    public void testTermVolatilityStripping1ON() { fail("not implemented"); }
}
