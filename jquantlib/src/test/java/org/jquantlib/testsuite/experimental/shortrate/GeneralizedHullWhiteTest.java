/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4c — GeneralizedHullWhite cross-validation tests.

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
package org.jquantlib.testsuite.experimental.shortrate;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.shortrate.GeneralizedHullWhite;
import org.jquantlib.instruments.Option;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Cross-validated tests for {@link GeneralizedHullWhite} against v1.42.1
 * reference values in
 * {@code migration-harness/references/experimental/shortrate/generalized_hull_white.json}.
 *
 * <p>Tolerance: rel 1e-6 for analytical formulas (B/V are Simpson-style
 * numerical integrals with capped step count, so platform-libm 1-ULP
 * slack accumulates a touch). Discount-bond-option puts at very small
 * NPV (~2e-21) use absolute tolerance.
 */
public class GeneralizedHullWhiteTest {

    private static final String GROUP = "experimental/shortrate/generalized_hull_white";
    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    private static final double REL = 1e-6;
    private static final double ABS = 1e-12;

    public GeneralizedHullWhiteTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private Handle<YieldTermStructure> flat() {
        new Settings().setEvaluationDate(new Date(15, Month.February, 2002));
        final DayCounter dc = new Actual365Fixed();
        return new Handle<YieldTermStructure>(
                new FlatForward(new Date(15, Month.February, 2002), 0.05, dc));
    }

    @Test
    public void classicalAgreesWithStandardHullWhite_t0_T1() { runClassicalDisc("classical_disc_t0.0_T1.0"); }

    @Test
    public void classicalAgreesWithStandardHullWhite_t0_T5() { runClassicalDisc("classical_disc_t0.0_T5.0"); }

    @Test
    public void classicalAgreesWithStandardHullWhite_t1_T5() { runClassicalDisc("classical_disc_t1.0_T5.0"); }

    @Test
    public void classicalAgreesWithStandardHullWhite_t2_T10() { runClassicalDisc("classical_disc_t2.0_T10.0"); }

    @Test
    public void classicalDiscountBondOption() {
        final Case c = REF.getCase("classical_dbo_strike_0.6_T_1_to_5");
        final JSONObject in = c.inputs();
        final JSONObject e = (JSONObject) c.expectedRaw();

        final double a = in.getDouble("a");
        final double sigma = in.getDouble("sigma");
        final double strike = in.getDouble("strike");
        final double T = in.getDouble("maturity");
        final double bondT = in.getDouble("bondMaturity");

        final Handle<YieldTermStructure> ts = flat();
        final GeneralizedHullWhite ghw = new GeneralizedHullWhite(ts, a, sigma);

        final double call = ghw.discountBondOption(Option.Type.Call, strike, T, bondT);
        final double put = ghw.discountBondOption(Option.Type.Put, strike, T, bondT);

        assertEquals("call", e.getDouble("call"), call,
                Math.max(ABS, REL * Math.abs(e.getDouble("call"))));
        // Put is ~1e-21, way below numerical-integral resolution; use ABS.
        assertEquals("put", e.getDouble("put"), put, 1e-15);
    }

    @Test
    public void piecewiseSingleSegment() {
        final Case c = REF.getCase("piecewise_single_segment");
        final JSONObject in = c.inputs();
        final JSONObject e = (JSONObject) c.expectedRaw();

        final double a = in.getDouble("a");
        final double sigma = in.getDouble("sigma");
        final double r = in.getDouble("r");
        final double T = in.getDouble("T");
        final double strike = in.getDouble("strike");
        final double matT = in.getDouble("maturity");
        final double bondT = in.getDouble("bondMaturity");

        final Handle<YieldTermStructure> ts = flat();
        final List<Date> ds = Arrays.asList(new Date(15, Month.February, 2002));
        final GeneralizedHullWhite ghw = new GeneralizedHullWhite(
                ts, ds, ds, new double[]{a}, new double[]{sigma});
        final double disc = ghw.discountBond(0.0, T, r);
        final double call = ghw.discountBondOption(Option.Type.Call, strike, matT, bondT);

        assertEquals("disc", e.getDouble("discount_bond"), disc,
                Math.max(ABS, REL * Math.abs(e.getDouble("discount_bond"))));
        assertEquals("call", e.getDouble("call"), call,
                Math.max(ABS, REL * Math.abs(e.getDouble("call"))));
    }

    @Test
    public void piecewiseTwoSegments() {
        final Case c = REF.getCase("piecewise_two_segments");
        final JSONObject e = (JSONObject) c.expectedRaw();

        final Handle<YieldTermStructure> ts = flat();
        final Date d0 = new Date(15, Month.February, 2002);
        final Date d5 = d0.add(365 * 5);
        final List<Date> ds = Arrays.asList(d0, d5);
        final GeneralizedHullWhite ghw = new GeneralizedHullWhite(
                ts, ds, ds, new double[]{0.05, 0.10}, new double[]{0.01, 0.02});
        final double call = ghw.discountBondOption(Option.Type.Call, 0.7, 2.0, 6.0);
        assertEquals("call", e.getDouble("call"), call,
                Math.max(ABS, REL * Math.abs(e.getDouble("call"))));
    }

    @Test
    public void dynamicsThrowsAndHWdynamicsWorks() {
        final Handle<YieldTermStructure> ts = flat();
        final GeneralizedHullWhite ghw = new GeneralizedHullWhite(ts, 0.05, 0.01);
        try {
            ghw.dynamics();
            fail("expected LibraryException — dynamics() unsupported, use HWdynamics()");
        } catch (LibraryException expected) {
            // expected
        }
        // HWdynamics() should return a valid ShortRateDynamics whose
        // process is a constant-coefficient OrnsteinUhlenbeckProcess.
        final org.jquantlib.model.shortrate.onefactormodels.OneFactorModel.ShortRateDynamics
                dyn = ghw.HWdynamics();
        assertTrue("HWdynamics returns non-null", dyn != null);
    }

    @Test
    public void fixedReversionFlagsAllReversionTrue() {
        final Handle<YieldTermStructure> ts = flat();
        // Classical-mode constructor pads to 2 (a, a) and 2 (sigma, sigma)
        // entries to satisfy Java's >= 2-point factory requirement; this
        // diverges from C++ (which keeps 1 each, totalling 2). Both
        // halves of fixedReversion() still tag a-slots as fixed and
        // sigma-slots as free, just with 4 entries instead of 2.
        final GeneralizedHullWhite ghw = new GeneralizedHullWhite(ts, 0.05, 0.01);
        final boolean[] mask = ghw.fixedReversion();
        assertEquals(4, mask.length);
        assertTrue("a[0] fixed", mask[0]);
        assertTrue("a[1] fixed", mask[1]);
        assertTrue("sigma[0] free", !mask[2]);
        assertTrue("sigma[1] free", !mask[3]);
    }

    private void runClassicalDisc(final String name) {
        final Case c = REF.getCase(name);
        final JSONObject in = c.inputs();
        final JSONObject e = (JSONObject) c.expectedRaw();

        final double a = in.getDouble("a");
        final double sigma = in.getDouble("sigma");
        final double t = in.getDouble("t");
        final double T = in.getDouble("T");
        final double r = in.getDouble("r");

        final Handle<YieldTermStructure> ts = flat();
        final GeneralizedHullWhite ghw = new GeneralizedHullWhite(ts, a, sigma);
        final HullWhite hw = new HullWhite(ts, a, sigma);

        final double ghw_disc = ghw.discountBond(t, T, r);
        final double hw_disc = hw.discountBond(t, T, r);

        // Match C++ probe's GHW value.
        assertEquals("ghw", e.getDouble("ghw_discount_bond"), ghw_disc,
                Math.max(ABS, REL * Math.abs(e.getDouble("ghw_discount_bond"))));
        // Sanity: classical GHW should be close to standard HullWhite, but
        // they differ slightly because GHW uses Simpson-style integrals
        // for B(t,T) and V(t,T) instead of analytical expressions. The
        // difference is bounded by ~1e-6 for the configurations probed.
        assertEquals("ghw close to hw", e.getDouble("hw_discount_bond"), hw_disc,
                Math.max(ABS, REL * Math.abs(e.getDouble("hw_discount_bond"))));
        assertTrue("|ghw-hw| < 1e-5",
                Math.abs(ghw_disc - hw_disc) < 1e-5);
    }
}
