/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for BlackKarasinski arguments_-indirection (Phase 2b WI-3) and
 the unstubbed tree(grid) calibration (Phase 2c WI-5). The reflection
 test verifies the indirection structurally; the fingerprint test
 cross-validates the tree-pricing path against C++ v1.42.1.
 */
package org.jquantlib.testsuite.model.shortrate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.methods.lattices.TreeLattice1D;
import org.jquantlib.model.Parameter;
import org.jquantlib.model.shortrate.onefactormodels.BlackKarasinski;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeGrid;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * BlackKarasinski tests:
 * <ul>
 *   <li>{@code parameterAccessors_routeThroughArguments} — Phase 2b WI-3
 *       reflection test that {@code aParam()} / {@code sigmaParam()} return
 *       the live {@code arguments_[i]} instances.</li>
 *   <li>{@code treeFingerprint_matchesCpp} — Phase 2c WI-5 fingerprint
 *       cross-validating the now-unstubbed {@code tree(grid)} calibration
 *       against C++ v1.42.1 reference values (loose tier; see inline
 *       justification on the assertions).</li>
 * </ul>
 */
public class BlackKarasinskiCalibrationTest {

    @Test
    public void parameterAccessors_routeThroughArguments() throws Exception {
        final Date today = new Date(22, Month.April, 2026);
        new Settings().setEvaluationDate(today);
        final YieldTermStructure ts = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.04)), new Actual365Fixed());

        final double a = 0.1;
        final double sigma = 0.01;
        final BlackKarasinski model = new BlackKarasinski(
                new Handle<YieldTermStructure>(ts), a, sigma);

        final Method aParam = BlackKarasinski.class.getDeclaredMethod("aParam");
        final Method sigmaParam = BlackKarasinski.class.getDeclaredMethod("sigmaParam");
        aParam.setAccessible(true);
        sigmaParam.setAccessible(true);

        final Parameter aP = (Parameter) aParam.invoke(model);
        final Parameter sP = (Parameter) sigmaParam.invoke(model);
        assertNotNull("aParam() must not be null", aP);
        assertNotNull("sigmaParam() must not be null", sP);
        assertEquals("aParam value must match ctor a", a, aP.get(0.0), 0.0);
        assertEquals("sigmaParam value must match ctor sigma", sigma, sP.get(0.0), 0.0);

        // Walk the inheritance chain (BlackKarasinski -> OneFactorModel ->
        // ShortRateModel -> CalibratedModel) to find the inherited
        // arguments_ field; the accessor must return the SAME instance
        // that lives in the slot.
        Class<?> c = BlackKarasinski.class;
        while (c != null && !c.getSimpleName().equals("CalibratedModel")) {
            c = c.getSuperclass();
        }
        assertNotNull("CalibratedModel must be in the inheritance chain", c);
        final Field argsField = c.getDeclaredField("arguments_");
        argsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<Parameter> args = (List<Parameter>) argsField.get(model);
        assertSame("aParam() must be same instance as arguments_.get(0)", aP, args.get(0));
        assertSame("sigmaParam() must be same instance as arguments_.get(1)", sP, args.get(1));
    }

    @Test
    public void treeFingerprint_matchesCpp() {
        final ReferenceReader reader = ReferenceReader.load("model/shortrate/blackkarasinski_tree");
        final Case c = reader.getCase("bk_tree_grid_4steps");
        final JSONObject in = c.inputs();

        final Date today = new Date(22, Month.April, 2026);
        new Settings().setEvaluationDate(today);
        final YieldTermStructure rCurve = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(in.getDouble("r_curve"))),
                new Actual365Fixed());

        final BlackKarasinski model = new BlackKarasinski(
                new Handle<YieldTermStructure>(rCurve),
                in.getDouble("a"), in.getDouble("sigma"));

        final TimeGrid grid = new TimeGrid(in.getDouble("grid_end"), in.getInt("grid_steps"));
        final Lattice lattice = model.tree(grid);
        assertNotNull("tree(grid) must not return null after WI-5 unstub", lattice);
        final TreeLattice1D tree = (TreeLattice1D) lattice;

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        // Loose tier (1e-8 abs + 1e-8 rel) per design §4.3 per-test
        // loosening allowance. Rationale: BlackKarasinski.tree(grid)
        // calibrates a per-step phi via Brent solver targeting 1e-7
        // tolerance on phi (both Java and C++ v1.42.1 use the same
        // bracketed root finder with the same target). The dominant
        // ~8.5e-3 structural error from the prior TrinomialTree.dx_
        // off-by-one was eliminated in commit 1cc6b3a; the residual
        // ~1.7e-11 is genuine solver-noise-floor as the per-step phi
        // searches converge to slightly different points within the
        // 1e-7 phi tolerance in Java vs C++ (a 1e-7 phi delta
        // propagates to ~1.7e-11 in exp(-phi*dt) discount values).
        // Tolerance.tight (1e-12 rel + 1e-14 abs) cannot accommodate
        // this without tightening Brent globally, which would change
        // BK behavior and require C++-side parity review — out of
        // WI-5 scope. This is the textbook per-test loosening case
        // the design explicitly allows.
        for (int k = 0; k < samples.length(); k++) {
            final JSONObject s = samples.getJSONObject(k);
            final int i = s.getInt("i");
            final int j = s.getInt("j");
            final double expDiscount = s.getDouble("discount");
            final double expUnderlying = s.getDouble("underlying");
            final double gotDiscount = tree.discount(i, j);
            final double gotUnderlying = tree.underlying(i, j);
            if (!Tolerance.loose(gotUnderlying, expUnderlying)) {
                fail("underlying[i=" + i + ",j=" + j + "]: exp="
                        + expUnderlying + " got=" + gotUnderlying);
            }
            if (!Tolerance.loose(gotDiscount, expDiscount)) {
                fail("discount[i=" + i + ",j=" + j + "]: exp="
                        + expDiscount + " got=" + gotDiscount);
            }
        }
    }
}
