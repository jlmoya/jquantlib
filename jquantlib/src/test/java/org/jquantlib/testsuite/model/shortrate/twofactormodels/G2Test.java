/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for the Phase 2e WI-1 G2++ body port. Cross-validates the
 freshly-ported analytic + tree paths against C++ v1.42.1 reference
 values captured by g2_probe.
 */
package org.jquantlib.testsuite.model.shortrate.twofactormodels;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.instruments.Option;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.methods.lattices.TreeLattice;
import org.jquantlib.model.shortrate.twofactormodels.G2;
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

/**
 * G2++ tests:
 * <ul>
 *   <li>{@code testAnalyticDiscountFingerprint} — tight tier on
 *       {@code G2.discount(t)} (TermStructureConsistentModel passthrough).</li>
 *   <li>{@code testDiscountBondOptionFingerprint} — tight tier on
 *       {@code discountBondOption(Call|Put, k, 5.0, 10.0)} (sigmaP +
 *       blackFormula closed form).</li>
 *   <li>{@code testTreeFingerprint} — loose tier on
 *       {@code TwoFactorModel.ShortRateTree.discount(i, index)} for the
 *       full 2D state of {@code TimeGrid(end=10.0, steps=5)}. Loose
 *       justification is inline at the assertion site (Brent solver
 *       inside TermStructureFittingParameter — Phase 2c WI-5 BK
 *       precedent).</li>
 * </ul>
 */
public class G2Test {

    private static final Date EVAL_DATE = new Date(15, Month.January, 2026);

    private G2 buildModel(final JSONObject in) {
        new Settings().setEvaluationDate(EVAL_DATE);
        final YieldTermStructure ts = new FlatForward(EVAL_DATE,
                new Handle<Quote>(new SimpleQuote(in.getDouble("r_curve"))),
                new Actual365Fixed());
        return new G2(new Handle<YieldTermStructure>(ts),
                in.getDouble("a"),
                in.getDouble("sigma"),
                in.getDouble("b"),
                in.getDouble("eta"),
                in.getDouble("rho"));
    }

    @Test
    public void testAnalyticDiscountFingerprint() {
        final ReferenceReader reader =
                ReferenceReader.load("model/shortrate/twofactormodels/g2");
        final Case c = reader.getCase("g2_discount_fingerprint");
        final G2 model = buildModel(c.inputs());

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int k = 0; k < samples.length(); k++) {
            final JSONObject s = samples.getJSONObject(k);
            final double t = s.getDouble("t");
            final double expDiscount = s.getDouble("discount");
            final double got = model.discount(t);
            // Tight tier: closed-form analytic discount-curve passthrough.
            if (!Tolerance.tight(got, expDiscount)) {
                fail("discount(t=" + t + "): exp=" + expDiscount + " got=" + got);
            }
        }
    }

    @Test
    public void testDiscountBondOptionFingerprint() {
        final ReferenceReader reader =
                ReferenceReader.load("model/shortrate/twofactormodels/g2");
        final Case c = reader.getCase("g2_discountBondOption_fingerprint");
        final G2 model = buildModel(c.inputs());

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int k = 0; k < samples.length(); k++) {
            final JSONObject s = samples.getJSONObject(k);
            final double strike = s.getDouble("strike");
            final double maturity = s.getDouble("maturity");
            final double bondMaturity = s.getDouble("bondMaturity");
            final double expCall = s.getDouble("call");
            final double expPut  = s.getDouble("put");
            final double gotCall = model.discountBondOption(
                    Option.Type.Call, strike, maturity, bondMaturity);
            final double gotPut  = model.discountBondOption(
                    Option.Type.Put,  strike, maturity, bondMaturity);
            // Tight tier: sigmaP + blackFormula closed form.
            if (!Tolerance.tight(gotCall, expCall)) {
                fail("call[k=" + strike + ",T=" + maturity + ",Tb=" + bondMaturity
                        + "]: exp=" + expCall + " got=" + gotCall);
            }
            if (!Tolerance.tight(gotPut, expPut)) {
                fail("put[k=" + strike + ",T=" + maturity + ",Tb=" + bondMaturity
                        + "]: exp=" + expPut + " got=" + gotPut);
            }
        }
    }

    @Test
    public void testTreeFingerprint() {
        final ReferenceReader reader =
                ReferenceReader.load("model/shortrate/twofactormodels/g2");
        final Case c = reader.getCase("g2_tree_fingerprint");
        final JSONObject in = c.inputs();
        final G2 model = buildModel(in);

        final TimeGrid grid = new TimeGrid(in.getDouble("grid_end"), in.getInt("grid_steps"));
        final Lattice lattice = model.tree(grid);
        assertNotNull("tree(grid) must not return null after WI-1 G2 body port", lattice);
        // TwoFactorModel.ShortRateTree extends TreeLattice2D extends
        // TreeLattice — the discount(i, index) method we need lives on
        // the TreeLattice base (Lattice itself does not expose it).
        final TreeLattice tree = (TreeLattice) lattice;

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        // Loose tier (1e-8 abs + 1e-8 rel) per design §4.2 per-test
        // loosening allowance. Rationale: the G2 ShortRateTree pulls
        // phi(t) through the TermStructureFittingParameter (analytic
        // here, but the Phase 2c WI-5 BK precedent established the
        // tier for tree fingerprints driven by Brent solvers and
        // Parameter-based fittings — same reasoning applies). The
        // 2D recombining-tree probability matrix uses an integer
        // /36.0 divisor, and the underlying OrnsteinUhlenbeckProcess
        // discretization compounds floating-point round-off across
        // 5 time steps, putting the comparison just outside tight
        // (1e-12 rel) for several mid-grid cells. Loose tier covers
        // it without changing tree behavior.
        for (int k = 0; k < samples.length(); k++) {
            final JSONObject s = samples.getJSONObject(k);
            final int i = s.getInt("i");
            final int index = s.getInt("index");
            final double expDiscount = s.getDouble("discount");
            final double got = tree.discount(i, index);
            if (!Tolerance.loose(got, expDiscount)) {
                fail("tree.discount[i=" + i + ",index=" + index + "]: exp="
                        + expDiscount + " got=" + got);
            }
        }
    }
}
