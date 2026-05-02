// jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/onefactormodels/gaussian1d/GsrTest.java
//
// Phase 2j WI-1.3 — Gsr concrete model cross-validation against
// migration-harness/references/models/shortrate/onefactormodels/gsr.json
// (oracle: C++ QuantLib v1.42.1, gsr_probe.cpp).
package org.jquantlib.testsuite.model.shortrate.onefactormodels.gaussian1d;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gsr;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
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

}
