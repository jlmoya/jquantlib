// jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/onefactormodels/gaussian1d/GsrTest.java
//
// Phase 2j WI-1.3 — Gsr concrete model cross-validation against
// migration-harness/references/models/shortrate/onefactormodels/gsr.json
// (oracle: C++ QuantLib v1.42.1, gsr_probe.cpp).
//
// Phase1-cert-D5-D-R2 — ports of 4 v1.42.1 test-suite/gsr.cpp tests appended.
package org.jquantlib.testsuite.model.shortrate.onefactormodels.gaussian1d;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.MakeVanillaSwap;
import org.jquantlib.instruments.NonstandardSwaption;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.methods.montecarlo.PathGenerator;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gsr;
import org.jquantlib.pricingengines.swaption.JamshidianSwaptionEngine;
import org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dJamshidianSwaptionEngine;
import org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dNonstandardSwaptionEngine;
import org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dSwaptionEngine;
import org.jquantlib.processes.HullWhiteForwardProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.processes.gsr.GsrProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates the Java {@link Gsr} model against the C++ v1.42.1 probe.
 * <p>
 * Single {@code @Test} with collect-all-failures pattern (per Phase 2i.5/2i.6
 * precedent — keeps the JUnit count from inflating with hundreds of cases).
 * Tier: {@link Tolerance#tight} for all closed-form GSR formulas.
 */
public class GsrTest {

    private static final Date EVAL_DATE = new Date(15, Month.May, 2026);

    /**
     * Builds the standard Gsr fixture matching the C++ probe:
     * <pre>
     *   today           = 15-May-2026
     *   yts             = FlatForward(today, 0.03, Actual360())
     *   volStepDates    = [today + 1Y, today + 2Y]
     *   volatilities    = [0.01, 0.012, 0.015]
     *   reversion       = 0.01    (constant)
     * </pre>
     */
    private static Gsr buildStandardGsr() {
        new Settings().setEvaluationDate(EVAL_DATE);
        final Handle<YieldTermStructure> yts = new Handle<YieldTermStructure>(
                new FlatForward(EVAL_DATE, 0.03, new Actual360()));
        final List<Date> volStepDates = new ArrayList<Date>();
        volStepDates.add(EVAL_DATE.add(new Period(1, TimeUnit.Years)));
        volStepDates.add(EVAL_DATE.add(new Period(2, TimeUnit.Years)));
        final double[] vols = new double[]{0.01, 0.012, 0.015};
        return new Gsr(yts, volStepDates, vols, 0.01);
    }

    /** Piecewise-reversion fixture (3 reversions matching 3 vols). */
    private static Gsr buildPiecewiseRevGsr() {
        new Settings().setEvaluationDate(EVAL_DATE);
        final Handle<YieldTermStructure> yts = new Handle<YieldTermStructure>(
                new FlatForward(EVAL_DATE, 0.03, new Actual360()));
        final List<Date> volStepDates = new ArrayList<Date>();
        volStepDates.add(EVAL_DATE.add(new Period(1, TimeUnit.Years)));
        volStepDates.add(EVAL_DATE.add(new Period(2, TimeUnit.Years)));
        final double[] vols = new double[]{0.01, 0.012, 0.015};
        final double[] revs = new double[]{0.01, 0.015, 0.02};
        return new Gsr(yts, volStepDates, vols, revs);
    }

    /** Custom-T fixture (T=30) used by numeraire_time cases. */
    private static Gsr buildT30Gsr() {
        new Settings().setEvaluationDate(EVAL_DATE);
        final Handle<YieldTermStructure> yts = new Handle<YieldTermStructure>(
                new FlatForward(EVAL_DATE, 0.03, new Actual360()));
        final List<Date> volStepDates = new ArrayList<Date>();
        volStepDates.add(EVAL_DATE.add(new Period(1, TimeUnit.Years)));
        volStepDates.add(EVAL_DATE.add(new Period(2, TimeUnit.Years)));
        final double[] vols = new double[]{0.01, 0.012, 0.015};
        return new Gsr(yts, volStepDates, vols, 0.01, 30.0);
    }

    @Test
    public void gsr_concreteModelMatchesCpp() {
        final ReferenceReader ref =
                ReferenceReader.load("models/shortrate/onefactormodels/gsr");

        final Gsr gsr  = buildStandardGsr();
        final Gsr gsrP = buildPiecewiseRevGsr();
        final Gsr gsrT = buildT30Gsr();

        // The "after_set" mutation case requires a stateful instance shared
        // across two ordered cases — build it once here and reuse for both.
        final Gsr gsrTset = buildT30Gsr();
        gsrTset.numeraireTime(45.0);

        final List<String> failures = new ArrayList<String>();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            // The probe wraps every expected payload as a JSON object. Pull
            // the canonical {"value": ...} field once per case.
            final JSONObject exp = (JSONObject) c.expectedRaw();
            try {
                if (name.equals("param_vol_size")) {
                    assertExactLong(failures, name, gsr.volatility().size(), exp.getLong("value"));
                } else if (name.equals("param_rev_size")) {
                    assertExactLong(failures, name, gsr.reversion().size(), exp.getLong("value"));
                } else if (name.startsWith("param_vol_")) {
                    final int idx = parseIndexSuffix(name);
                    assertExactDouble(failures, name, gsr.volatility().get(idx), exp.getDouble("value"));
                } else if (name.startsWith("param_rev_")) {
                    final int idx = parseIndexSuffix(name);
                    assertExactDouble(failures, name, gsr.reversion().get(idx), exp.getDouble("value"));
                } else if (name.equals("numeraire_time")) {
                    assertTight(failures, name, gsr.numeraireTime(), exp.getDouble("value"));
                } else if (name.startsWith("zb_")) {
                    final JSONObject in = c.inputs();
                    final double T = in.getDouble("T");
                    final double t = in.getDouble("t");
                    final double x = in.getDouble("x");
                    assertTight(failures, name, gsr.zerobond(T, t, x), exp.getDouble("value"));
                } else if (name.startsWith("num_")) {
                    final JSONObject in = c.inputs();
                    final double t = in.getDouble("t");
                    final double x = in.getDouble("x");
                    assertTight(failures, name, gsr.numeraire(t, x), exp.getDouble("value"));
                } else if (name.equals("pw_rev_size")) {
                    assertExactLong(failures, name, gsrP.reversion().size(), exp.getLong("value"));
                } else if (name.startsWith("pw_rev_")) {
                    final int idx = parseIndexSuffix(name);
                    assertExactDouble(failures, name, gsrP.reversion().get(idx), exp.getDouble("value"));
                } else if (name.startsWith("pw_zb_")) {
                    final JSONObject in = c.inputs();
                    final double T = in.getDouble("T");
                    final double t = in.getDouble("t");
                    final double x = in.getDouble("x");
                    assertTight(failures, name, gsrP.zerobond(T, t, x), exp.getDouble("value"));
                } else if (name.equals("numeraire_time_30")) {
                    assertTight(failures, name, gsrT.numeraireTime(), exp.getDouble("value"));
                } else if (name.equals("numeraire_time_after_set")) {
                    assertTight(failures, name, gsrTset.numeraireTime(), exp.getDouble("value"));
                } else if (name.equals("numeraire_time_zb_20")) {
                    final JSONObject in = c.inputs();
                    final double T = in.getDouble("T");
                    final double t = in.getDouble("t");
                    final double x = in.getDouble("x");
                    assertTight(failures, name, gsrTset.zerobond(T, t, x), exp.getDouble("value"));
                } else {
                    failures.add(name + ": no dispatcher branch");
                }
            } catch (final RuntimeException e) {
                failures.add(name + ": exception " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("GsrTest: " + failures.size() + " mismatch(es)\n  "
                    + String.join("\n  ", failures.subList(0, Math.min(30, failures.size())))
                    + (failures.size() > 30 ? "\n  ... (" + (failures.size() - 30) + " more)" : ""));
        }
    }

    /** Parses the trailing "_NN" or "_NNN" digit suffix (one or more digits) from a name. */
    private static int parseIndexSuffix(final String name) {
        int i = name.length();
        while (i > 0 && Character.isDigit(name.charAt(i - 1))) i--;
        return Integer.parseInt(name.substring(i));
    }

    private static void assertExactLong(final List<String> failures, final String name,
                                        final long java, final long cpp) {
        if (!Tolerance.exact(java, cpp)) {
            failures.add(name + ": EXACT-long mismatch java=" + java + " cpp=" + cpp);
        }
    }

    private static void assertExactDouble(final List<String> failures, final String name,
                                          final double java, final double cpp) {
        if (!Tolerance.exact(java, cpp)) {
            failures.add(name + ": EXACT-double mismatch java=" + java + " cpp=" + cpp);
        }
    }

    private static void assertTight(final List<String> failures, final String name,
                                    final double java, final double cpp) {
        if (!Tolerance.tight(java, cpp)) {
            failures.add(name + ": TIGHT mismatch java=" + java + " cpp=" + cpp
                    + " (diff=" + Math.abs(java - cpp) + ")");
        }
    }

    // -------------------------------------------------------------------
    // v1.42.1 test-suite/gsr.cpp direct ports (Phase1-cert-D5-D-R2)
    // -------------------------------------------------------------------

    /**
     * Direct port of v1.42.1 {@code BOOST_AUTO_TEST_CASE(testGsrProcess)}
     * (test-suite/gsr.cpp). Verifies the {@link GsrProcess} state process
     * (in both step-date and no-step-date parameterisations) matches the
     * {@link HullWhiteForwardProcess} in conditional expectation and
     * variance across a grid of {@code T, t, w, xw} values.
     *
     * <p>Tolerance {@code 1e-8}: matches v1.42.1 C++ {@code tol = 1E-8}.
     */
    @Test
    public void testGsrProcess() {
        final Date refDate = new Settings().evaluationDate();

        final double tol = 1.0E-8;
        final double reversion = 0.01;
        final double modelvol = 0.01;

        final Handle<YieldTermStructure> yts0 = new Handle<YieldTermStructure>(
                new FlatForward(0, new Target(), 0.00, new Actual365Fixed()));

        final List<Date> stepDates0 = new ArrayList<Date>();
        final double[] vols0 = { modelvol };
        final double[] reversions0 = { reversion };

        final List<Date> stepDates1 = new ArrayList<Date>();
        for (int i = 1; i < 60; ++i) {
            stepDates1.add(refDate.add(new Period(i * 6, TimeUnit.Months)));
        }
        final double[] vols1 = new double[stepDates1.size() + 1];
        final double[] reversions1 = new double[stepDates1.size() + 1];
        for (int i = 0; i < vols1.length; ++i) { vols1[i] = modelvol; reversions1[i] = reversion; }

        double T = 10.0;
        do {
            final Gsr model = new Gsr(yts0, stepDates0, vols0, reversions0, T);
            final StochasticProcess1D gsrProcess = model.stateProcess();
            final Gsr model2 = new Gsr(yts0, stepDates1, vols1, reversions1, T);
            final StochasticProcess1D gsrProcess2 = model2.stateProcess();

            final HullWhiteForwardProcess hwProcess = new HullWhiteForwardProcess(yts0, reversion, modelvol);
            hwProcess.setForwardMeasureTime(T);

            double t = 0.5;
            do {
                double w = 0.0;
                do {
                    double xw = -0.1;
                    do {
                        final double hwExp = hwProcess.expectation(w, xw, t - w);
                        final double gsrExp = gsrProcess.expectation(w, xw, t - w);
                        final double gsr2Exp = gsrProcess2.expectation(w, xw, t - w);
                        if (Math.abs(hwExp - gsrExp) > tol) {
                            fail("Expectation E^{T=" + T + "}(x(" + t + ") | x(" + w + ") = " + xw
                                    + " differs HW=" + hwExp + " GSR=" + gsrExp);
                        }
                        if (Math.abs(hwExp - gsr2Exp) > tol) {
                            fail("Expectation E^{T=" + T + "}(x(" + t + ") | x(" + w + ") = " + xw
                                    + " differs HW=" + hwExp + " GSR2=" + gsr2Exp);
                        }

                        final double hwVar = hwProcess.variance(w, xw, t - w);
                        final double gsrVar = gsrProcess.variance(w, xw, t - w);
                        final double gsr2Var = gsrProcess2.variance(w, xw, t - w);
                        if (Math.abs(hwVar - gsrVar) > tol) {
                            fail("Variance V(x(" + t + ") | x(" + w + ") = " + xw
                                    + " differs HW=" + hwVar + " GSR=" + gsrVar);
                        }
                        if (Math.abs(hwVar - gsr2Var) > tol) {
                            fail("Variance V(x(" + t + ") | x(" + w + ") = " + xw
                                    + " differs HW=" + hwVar + " GSR2=" + gsr2Var);
                        }
                        xw += 0.01;
                    } while (xw <= 0.1);
                    w += t / 5.0;
                } while (w <= t - 0.1);
                t += T / 20.0;
            } while (t <= T - 0.1);
            T += 10.0;
        } while (T <= 30.0);

        // Time-dependent reversion + vol — instantiation smoke test only
        // (matches C++ "add more test cases here ..." comment — no asserts
        // beyond constructibility).
        final double[] times = { 1.0, 2.0 };
        final double[] vols = { 0.2, 0.3, 0.4 };
        final double[] reversionsArr = { 0.50, 0.80, 1.30 };
        final GsrProcess p = new GsrProcess(times, vols, reversionsArr, 60.0);
        p.setForwardMeasureTime(10.0);
    }

    /**
     * Direct port of v1.42.1 {@code BOOST_AUTO_TEST_CASE(testGsrModel)}
     * (test-suite/gsr.cpp). Cross-validates the closed-form zerobond and
     * Gaussian1d swaption pricing against the {@link HullWhite} model and
     * {@link JamshidianSwaptionEngine}.
     *
     * <p>Zerobond tolerance {@code 1e-8}: matches v1.42.1 C++ {@code tol0}.
     * Swaption-NPV tolerance {@code 5e-5} (loose-tier): matches v1.42.1 C++
     * {@code 0.00005} — Gaussian1d engines use numerical quadrature
     * (Gauss-Hermite, 64 nodes) so are limited to ~5 sig figs vs. the
     * Jamshidian closed-form swaption NPV. Not a loosening; not an A2
     * trigger — upstream tolerance.
     */
    @Test
    public void testGsrModel() {
        final Date refDate = new Settings().evaluationDate();

        final double modelvol = 0.01;
        final double reversion = 0.01;

        final List<Date> stepDates = new ArrayList<Date>();
        final double[] vols = { modelvol };
        final double[] reversions = { reversion };

        final List<Date> stepDates1 = new ArrayList<Date>();
        for (int i = 1; i < 60; ++i) {
            stepDates1.add(refDate.add(new Period(i * 6, TimeUnit.Months)));
        }
        final double[] vols1 = new double[stepDates1.size() + 1];
        final double[] reversions1 = new double[stepDates1.size() + 1];
        for (int i = 0; i < vols1.length; ++i) { vols1[i] = modelvol; reversions1[i] = reversion; }

        final Handle<YieldTermStructure> yts = new Handle<YieldTermStructure>(
                new FlatForward(0, new Target(), 0.03, new Actual365Fixed()));
        final Gsr model = new Gsr(yts, stepDates, vols, reversions, 50.0);
        final Gsr model2 = new Gsr(yts, stepDates1, vols1, reversions1, 50.0);
        final HullWhite hw = new HullWhite(yts, reversion, modelvol);

        // Closed-form zerobond cross-validation
        final double tol0 = 1.0E-8;
        double w = 0.1;
        do {
            double t = w + 0.1;
            do {
                double xw = -0.10;
                do {
                    final double yw = (xw - model.stateProcess().expectation(0.0, 0.0, w))
                            / model.stateProcess().stdDeviation(0.0, 0.0, w);
                    final double rw = xw + 0.03; // instantaneous forward is 0.03
                    final double gsrVal = model.zerobond(t, w, yw);
                    final double gsr2Val = model2.zerobond(t, w, yw);
                    final double hwVal = hw.discountBond(w, t, rw);
                    if (Math.abs(gsrVal - hwVal) > tol0) {
                        fail("Zerobond P(" + w + "," + t + " | x=" + xw + " / y=" + yw
                                + ") differs HW=" + hwVal + " Gsr=" + gsrVal);
                    }
                    if (Math.abs(gsr2Val - hwVal) > tol0) {
                        fail("Zerobond P(" + w + "," + t + " | x=" + xw + " / y=" + yw
                                + ") differs HW=" + hwVal + " Gsr2=" + gsr2Val);
                    }
                    xw += 0.01;
                } while (xw <= 0.10);
                t += 2.5;
            } while (t <= 50.0);
            w += 5.0;
        } while (w <= 50.0);

        // Swaption engine cross-validation
        final Date expiry = new Target().advance(refDate, new Period(5, TimeUnit.Years));
        final Period tenor = new Period(10, TimeUnit.Years);
        final SwapIndex swpIdx = new EuriborSwapIsdaFixA(tenor, yts);
        final double forward = swpIdx.fixing(expiry);

        final VanillaSwap underlyingFixed = new MakeVanillaSwap(
                new Period(10, TimeUnit.Years), swpIdx.iborIndex(), forward)
                .withEffectiveDate(swpIdx.valueDate(expiry))
                .withFixedLegCalendar(swpIdx.fixingCalendar())
                .withFixedLegDayCount(swpIdx.dayCounter())
                .withFixedLegTenor(swpIdx.fixedLegTenor())
                .withFixedLegConvention(swpIdx.fixedLegConvention())
                .withFixedLegTerminationDateConvention(swpIdx.fixedLegConvention())
                .value();

        final EuropeanExercise exercise = new EuropeanExercise(expiry);
        final Swaption stdswaption = new Swaption(underlyingFixed, exercise);
        final NonstandardSwaption nonstdswaption = new NonstandardSwaption(stdswaption);

        stdswaption.setPricingEngine(new JamshidianSwaptionEngine(hw, yts));
        final double HwJamNpv = stdswaption.NPV();

        nonstdswaption.setPricingEngine(new Gaussian1dNonstandardSwaptionEngine(model, 64, 7.0, true, false));
        stdswaption.setPricingEngine(new Gaussian1dSwaptionEngine(model, 64, 7.0, true, false));
        final double GsrNonStdNpv = nonstdswaption.NPV();
        final double GsrStdNpv = stdswaption.NPV();
        stdswaption.setPricingEngine(new Gaussian1dJamshidianSwaptionEngine(model));
        final double GsrJamNpv = stdswaption.NPV();

        // Loose tolerance per v1.42.1 — Gaussian1d numerical quadrature vs Jamshidian closed-form
        final double engineTol = 0.00005;
        if (Math.abs(HwJamNpv - GsrNonStdNpv) > engineTol) {
            fail("Jamshidian HW NPV (" + HwJamNpv + ") deviates from Gaussian1dNonstandardSwaptionEngine NPV ("
                    + GsrNonStdNpv + ")");
        }
        if (Math.abs(HwJamNpv - GsrStdNpv) > engineTol) {
            fail("Jamshidian HW NPV (" + HwJamNpv + ") deviates from Gaussian1dSwaptionEngine NPV ("
                    + GsrStdNpv + ")");
        }
        if (Math.abs(HwJamNpv - GsrJamNpv) > engineTol) {
            fail("Jamshidian HW NPV (" + HwJamNpv + ") deviates from Gaussian1dJamshidianEngine NPV ("
                    + GsrJamNpv + ")");
        }
    }

    /**
     * Direct port of v1.42.1
     * {@code BOOST_AUTO_TEST_CASE(testGsrProcessWithPathGenerator)}: verifies
     * a {@link GsrProcess} survives path generation via {@link PathGenerator}
     * + {@link MersenneTwisterUniformRng}-backed pseudo-random sequence
     * (regression against C++ dangling-reference bug in array storage).
     */
    @Test
    public void testGsrProcessWithPathGenerator() {
        final int timeSteps = 4;
        final double length = 2.0;

        // Build process with single-element arrays (mimics SWIG temporary
        // ownership semantics from the original C++ regression).
        final double[] times = { 1.0 };
        final double[] vols = { 0.005, 0.005 };
        final double[] reversions = { 0.03 };
        final GsrProcess process = new GsrProcess(times, vols, reversions, 60.0);

        // Build path generator (mirrors C++ PseudoRandom::make_sequence_generator).
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(42L);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> rsgUnif =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, timeSteps, rng);
        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal> rsg =
                new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>(rsgUnif, new InverseCumulativeNormal());

        final PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                InverseCumulativeNormal>> generator =
                new PathGenerator<InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                        InverseCumulativeNormal>>(process, length, timeSteps, rsg, false);

        // First path
        final Sample<Path> sample = generator.next();
        final Path path = sample.value();

        if (path.length() != timeSteps + 1) {
            fail("path length: expected " + (timeSteps + 1) + " got " + path.length());
        }
        if (path.front() != 0.0) {
            fail("path.front() expected 0.0 got " + path.front());
        }
        for (int i = 0; i < path.length(); ++i) {
            final double v = path.get(i);
            if (!Double.isFinite(v)) {
                fail("Path value at index " + i + " is not finite: " + v);
            }
        }

        // Stability: 10 more paths
        for (int n = 0; n < 10; ++n) {
            final Sample<Path> s = generator.next();
            for (int i = 0; i < s.value().length(); ++i) {
                final double v = s.value().get(i);
                if (!Double.isFinite(v)) {
                    fail("Path " + n + " value at index " + i + " is not finite: " + v);
                }
            }
        }
    }

    /**
     * Direct port of v1.42.1
     * {@code BOOST_AUTO_TEST_CASE(testGsrModelQuoteUpdate)}: verifies the
     * Gsr / Gaussian1d engine chain re-prices correctly when a
     * {@link SimpleQuote} backing the yield curve is updated (observer
     * propagation invariant).
     */
    @Test
    public void testGsrModelQuoteUpdate() {
        final Date refDate = new Settings().evaluationDate();
        final double modelvol = 0.01;
        final double reversion = 0.01;

        final List<Date> stepDates = new ArrayList<Date>();
        final double[] vols = { modelvol };
        final double[] reversions = { reversion };

        final SimpleQuote rate = new SimpleQuote(0.03);
        final Handle<YieldTermStructure> yts = new Handle<YieldTermStructure>(
                new FlatForward(0, new Target(), new Handle<org.jquantlib.quotes.Quote>(rate),
                        new Actual365Fixed()));

        final Gsr model = new Gsr(yts, stepDates, vols, reversions, 50.0);
        @SuppressWarnings("unused")
        final HullWhite hw = new HullWhite(yts, reversion, modelvol);

        final Date expiry = new Target().advance(refDate, new Period(5, TimeUnit.Years));
        final Period tenor = new Period(10, TimeUnit.Years);
        final SwapIndex swpIdx = new EuriborSwapIsdaFixA(tenor, yts);
        final double forward = swpIdx.fixing(expiry);

        final VanillaSwap underlyingFixed = new MakeVanillaSwap(
                new Period(10, TimeUnit.Years), swpIdx.iborIndex(), forward)
                .withEffectiveDate(swpIdx.valueDate(expiry))
                .withFixedLegCalendar(swpIdx.fixingCalendar())
                .withFixedLegDayCount(swpIdx.dayCounter())
                .withFixedLegTenor(swpIdx.fixedLegTenor())
                .withFixedLegConvention(swpIdx.fixedLegConvention())
                .withFixedLegTerminationDateConvention(swpIdx.fixedLegConvention())
                .value();

        final EuropeanExercise exercise = new EuropeanExercise(expiry);
        final Swaption stdswaption = new Swaption(underlyingFixed, exercise);

        stdswaption.setPricingEngine(new Gaussian1dSwaptionEngine(model, 64, 7.0, true, false));
        final double before = stdswaption.NPV();

        rate.setValue(0.04);
        final double after = stdswaption.NPV();

        if (Math.abs(before - after) <= 0.01) {
            fail("Quote update did not propagate: before=" + before + " after=" + after
                    + " diff=" + Math.abs(before - after));
        }
    }
}
