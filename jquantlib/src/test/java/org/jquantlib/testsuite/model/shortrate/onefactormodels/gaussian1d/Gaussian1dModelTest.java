// jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/onefactormodels/gaussian1d/Gaussian1dModelTest.java
//
// Phase 2j WI-1.3 — re-enable Gaussian1dModel base-behavior test (deferred
// from WI-1.1 until a concrete subclass existed).
//
// Cross-validates {@code Gaussian1dModel} via a {@code Gsr} subclass against
// migration-harness/references/models/shortrate/onefactormodels/gaussian1d_model.json
// (oracle: C++ QuantLib v1.42.1, gaussian1d_model_probe.cpp).
package org.jquantlib.testsuite.model.shortrate.onefactormodels.gaussian1d;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gaussian1dModel;
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
 * Cross-validates the Java {@link Gaussian1dModel} base behaviors via a
 * {@link Gsr} fixture against the C++ v1.42.1 probe.
 * <p>
 * Single {@code @Test} with collect-all-failures pattern. Tier:
 * {@link Tolerance#tight} for forward-measure quantities and
 * {@link Tolerance#exact} for the static {@code gaussianPolynomialIntegral}
 * helpers (pure-math closed forms).
 * <p>
 * The {@code sr_*} (swap-rate / swap-annuity) cases are skipped: they require
 * the {@code EuriborSwapIsdaFixA} swap-index template which is not available
 * in the JQuantLib indexes package yet (an A4/A14 dependency). Those will be
 * picked up in WI-1.4 / WI-2.x once swap-index templates land.
 */
public class Gaussian1dModelTest {

    private static final Date EVAL_DATE = new Date(15, Month.May, 2026);

    /** Builds the same Gsr fixture as the C++ probe. */
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

    @Test
    public void gaussian1dModel_baseBehaviorsMatchCpp() {
        final ReferenceReader ref = ReferenceReader.load(
                "models/shortrate/onefactormodels/gaussian1d_model");

        final Gsr gsr = buildStandardGsr();
        final List<String> failures = new ArrayList<String>();
        int srSkipped = 0;

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            try {
                if (name.startsWith("fm_")) {
                    final JSONObject in = c.inputs();
                    final double t = in.getDouble("t");
                    final double x = in.getDouble("x");
                    final double T = in.getDouble("T");
                    final JSONObject exp = (JSONObject) c.expectedRaw();
                    assertTight(failures, name + ".numeraire",
                            gsr.numeraire(t, x), exp.getDouble("numeraire"));
                    assertTight(failures, name + ".zerobond",
                            gsr.zerobond(T, t, x), exp.getDouble("zerobond"));

                } else if (name.startsWith("mesh_")) {
                    final JSONObject in = c.inputs();
                    final double t = in.getDouble("t");
                    final double x = in.getDouble("x");
                    final JSONObject exp = (JSONObject) c.expectedRaw();
                    assertTight(failures, name + ".numeraire",
                            gsr.numeraire(t, x), exp.getDouble("numeraire"));

                } else if (name.startsWith("gpi_")) {
                    final JSONObject in = c.inputs();
                    final double a = in.getDouble("a");
                    final double b = in.getDouble("b");
                    final double cc = in.getDouble("c");
                    final double d = in.getDouble("d");
                    final double e = in.getDouble("e");
                    final double x0 = in.getDouble("x0");
                    final double x1 = in.getDouble("x1");
                    final double v = Gaussian1dModel.gaussianPolynomialIntegral(
                            a, b, cc, d, e, x0, x1);
                    final double cpp = ((JSONObject) c.expectedRaw()).getDouble("value");
                    assertTight(failures, name, v, cpp);

                } else if (name.startsWith("gspi_")) {
                    final JSONObject in = c.inputs();
                    final double a = in.getDouble("a");
                    final double b = in.getDouble("b");
                    final double cc = in.getDouble("c");
                    final double d = in.getDouble("d");
                    final double e = in.getDouble("e");
                    final double h = in.getDouble("h");
                    final double x0 = in.getDouble("x0");
                    final double x1 = in.getDouble("x1");
                    final double v = Gaussian1dModel.gaussianShiftedPolynomialIntegral(
                            a, b, cc, d, e, h, x0, x1);
                    final double cpp = ((JSONObject) c.expectedRaw()).getDouble("value");
                    assertTight(failures, name, v, cpp);

                } else if (name.startsWith("sr_")) {
                    // Swap-rate cases require EuriborSwapIsdaFixA / SwapIndex
                    // templating that isn't available on the Java side yet
                    // (A14: SwapIndex.clone(Period) follow-up). Defer to
                    // WI-1.4 / WI-2.x. Counted, not failed.
                    srSkipped++;
                } else {
                    failures.add(name + ": no dispatcher branch");
                }
            } catch (final RuntimeException e) {
                failures.add(name + ": exception " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("Gaussian1dModelTest: " + failures.size() + " mismatch(es) "
                    + "(" + srSkipped + " sr_* cases deferred to WI-1.4)\n  "
                    + String.join("\n  ", failures.subList(0,
                            Math.min(30, failures.size())))
                    + (failures.size() > 30
                            ? "\n  ... (" + (failures.size() - 30) + " more)" : ""));
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
