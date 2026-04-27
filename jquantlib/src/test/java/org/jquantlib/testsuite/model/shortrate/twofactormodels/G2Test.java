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
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.methods.lattices.TreeLattice;
import org.jquantlib.model.shortrate.twofactormodels.G2;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeGrid;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
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
    public void testSwaptionIntegralFingerprint() {
        final ReferenceReader reader =
                ReferenceReader.load("model/shortrate/twofactormodels/g2");
        final Case c = reader.getCase("g2_swaption_integral_fingerprint");
        final JSONObject in = c.inputs();
        final JSONObject exp = (JSONObject) c.expectedRaw();

        // ---- Fixture (mirrors g2_probe.cpp swaption block exactly) ----
        new Settings().setEvaluationDate(EVAL_DATE);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final double rCurve = in.getDouble("r_curve");
        final double nominal = in.getDouble("nominal");
        final double dummyRate = in.getDouble("dummy_fixed_rate");

        final YieldTermStructure flat = new FlatForward(
                EVAL_DATE, new Handle<Quote>(new SimpleQuote(rCurve)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx = new Euribor3M(ts);

        final Date exerciseDate = cal.advance(EVAL_DATE,
                new Period(in.getInt("exercise_years"), TimeUnit.Years),
                BusinessDayConvention.Following);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final Date startDate = cal.advance(exerciseDate, 2, TimeUnit.Days,
                BusinessDayConvention.Following, false);
        final Date maturity = cal.advance(startDate,
                new Period(in.getInt("swap_years"), TimeUnit.Years),
                BusinessDayConvention.Following);

        final DayCounter fixedDc = new Thirty360(Thirty360.Convention.European);

        final Schedule fixedSchedule = new Schedule(
                startDate, maturity, new Period(1, TimeUnit.Years), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);
        final Schedule floatSchedule = new Schedule(
                startDate, maturity,
                new Period(in.getInt("float_tenor_months"), TimeUnit.Months),
                cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        final VanillaSwap swap0 = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, dummyRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        swap0.setPricingEngine(new DiscountingSwapEngine(ts));
        final double atmRate = swap0.fairRate();

        final VanillaSwap swap = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, atmRate, fixedDc,
                floatSchedule, idx, 0.0, dc);

        final Swaption swaption = new Swaption(swap, exercise);
        // Manually populate Swaption.ArgumentsImpl — Swaption.setupArguments
        // is package-private and the projection chain (VanillaSwap → Swap)
        // does not propagate the swap reference required by G2.swaption.
        // Mirrors C++ swaption.setupArguments(&swaptionArgs) call site.
        final Swaption.ArgumentsImpl args = new Swaption.ArgumentsImpl();
        args.swap = swap;
        args.exercise = exercise;
        args.settlementType = swaption.settlementType();
        args.settlementMethod = swaption.settlementMethod();

        final G2 model = new G2(ts,
                in.getDouble("a"), in.getDouble("sigma"),
                in.getDouble("b"), in.getDouble("eta"), in.getDouble("rho"));

        final double range = in.getDouble("range");
        final int intervals = in.getInt("intervals");
        final double got = model.swaption(args, atmRate, range, intervals);

        // Sanity: the par-rate fixture must agree with the C++ reference.
        final double expAtm = exp.getDouble("atm_rate");
        if (!Tolerance.tight(atmRate, expAtm)) {
            fail("atmRate: exp=" + expAtm + " got=" + atmRate);
        }

        // Loose tier (1e-8 abs + 1e-8 rel). Justification: G2.swaption
        // composes SegmentIntegral over an inner Brent solver. Both the
        // outer trapezoid sum (50 sub-intervals) and the inner Brent
        // root-finding (1e-6 accuracy plus a known Java/C++ pre-loop
        // initialisation divergence in Brent.solveImpl — see
        // JamshidianSwaptionEngineTest class-level note) compound a
        // floating-point noise floor well below 1e-8 absolute. Tightening
        // requires aligning Java Brent with C++ brent.hpp first; deferred.
        final double expSwaption = exp.getDouble("swaption_integral");
        if (!Tolerance.loose(got, expSwaption)) {
            fail("g2.swaption: exp=" + expSwaption + " got=" + got);
        }
        // Suppress unused-import warnings if Settlement isn't read elsewhere.
        assertNotNull(Settlement.Type.Physical);
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
