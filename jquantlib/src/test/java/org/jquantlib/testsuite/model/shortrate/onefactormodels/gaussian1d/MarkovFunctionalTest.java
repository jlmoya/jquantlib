// jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/onefactormodels/gaussian1d/MarkovFunctionalTest.java
//
// Phase 2j.5 Track C.3 — MarkovFunctional concrete-model cross-validation
// against migration-harness/references/models/shortrate/onefactormodels/markov_functional.json
// (oracle: C++ QuantLib v1.42.1, markov_functional_probe.cpp).
package org.jquantlib.testsuite.model.shortrate.onefactormodels.gaussian1d;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.EURLibor6M;
import org.jquantlib.indexes.EurLiborSwapIsdaFixA;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.MarkovFunctional;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
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
 * Cross-validates the Java {@link MarkovFunctional} model against the C++
 * v1.42.1 probe. Tiers (collect-all-failures pattern, single {@code @Test}):
 * <ul>
 *   <li><b>TIGHT</b> (1e-12 rel, 1e-14 abs) — pure-deterministic outputs:
 *       {@code numeraireTime}, post-construction {@code sigma} readback.
 *       These are not downstream of the Brent calibration loop.</li>
 *   <li><b>LOOSE-with-A20</b> (1e-8 rel/abs, see Phase 2j.5 plan A20) —
 *       outputs downstream of {@code updateNumeraireTabulation}: numeraire
 *       and zerobond evaluations, model-zerorate fit. The C++ model uses
 *       a Brent root-finder with explicit accuracy {@code 1e-7} and a
 *       32-point Gauss-Hermite outer integration; with {@link
 *       org.jquantlib.math.transcendental.JQuantMath#exp} matching C++ msun
 *       to within 1 ULP per call, the accumulated FP residual on hundreds of
 *       GH evaluations sits at ~1e-11 (worst observed in this fixture). All
 *       residuals are uniform-magnitude (no systematic drift) — A20 manifests
 *       as expected accumulated noise, NOT as iteration-order divergence
 *       (which would produce orders-of-magnitude bigger residuals).
 *       Calibration sigma matches C++ EXACT in this 1-vol-step fixture
 *       (since C++ does not vary sigma in the calibration; sigma is a
 *       Phase 2j.5 input not an output here — multi-step calibration is
 *       deferred to a Phase 2k variant).</li>
 * </ul>
 */
public class MarkovFunctionalTest {

    private static final Date REFERENCE_DATE = new Date(14, Month.November, 2012);

    private static MarkovFunctional buildSwaptionMF() {
        new Settings().setEvaluationDate(REFERENCE_DATE);

        final Handle<YieldTermStructure> flatYts = new Handle<YieldTermStructure>(
                new FlatForward(REFERENCE_DATE, 0.03, new Actual365Fixed()));

        final Handle<SwaptionVolatilityStructure> flatSwaptionVts =
                new Handle<SwaptionVolatilityStructure>(
                        new ConstantSwaptionVolatility(
                                0, new Target(),
                                BusinessDayConvention.ModifiedFollowing,
                                0.20, new Actual365Fixed()));

        final SwapIndex swapIndexBase = new EurLiborSwapIsdaFixA(
                new Period(1, TimeUnit.Years));

        final List<Date> volStepDates = new ArrayList<Date>();
        final double[] vols = new double[]{0.01};

        final List<Date> expiries = new ArrayList<Date>();
        expiries.add(REFERENCE_DATE.add(new Period(5, TimeUnit.Years)));
        final List<Period> tenors = new ArrayList<Period>();
        tenors.add(new Period(10, TimeUnit.Years));

        final MarkovFunctional.ModelSettings settings = new MarkovFunctional.ModelSettings()
                .withYGridPoints(64)
                .withYStdDevs(7.0)
                .withGaussHermitePoints(32)
                .withDigitalGap(1e-5)
                .withMarketRateAccuracy(1e-7)
                .withLowerRateBound(0.0)
                .withUpperRateBound(2.0)
                .withAdjustments(MarkovFunctional.ADJUST_NONE);

        return new MarkovFunctional(
                flatYts, 0.01, volStepDates, vols, flatSwaptionVts,
                expiries, tenors, swapIndexBase, settings);
    }

    private static MarkovFunctional buildCapletMF() {
        new Settings().setEvaluationDate(REFERENCE_DATE);

        final Handle<YieldTermStructure> flatYts = new Handle<YieldTermStructure>(
                new FlatForward(REFERENCE_DATE, 0.03, new Actual365Fixed()));

        final Handle<OptionletVolatilityStructure> flatOptionletVts =
                new Handle<OptionletVolatilityStructure>(
                        new ConstantOptionletVolatility(
                                0, new Target(),
                                BusinessDayConvention.ModifiedFollowing,
                                new Handle<org.jquantlib.quotes.Quote>(new SimpleQuote(0.20)),
                                new Actual365Fixed()));

        final IborIndex iborIndex = new EURLibor6M();

        final List<Date> volStepDates = new ArrayList<Date>();
        final double[] vols = new double[]{0.01};

        final List<Date> expiries = new ArrayList<Date>();
        expiries.add(REFERENCE_DATE.add(new Period(2, TimeUnit.Years)));

        final MarkovFunctional.ModelSettings settings = new MarkovFunctional.ModelSettings()
                .withYGridPoints(64)
                .withYStdDevs(7.0)
                .withGaussHermitePoints(32)
                .withDigitalGap(1e-5)
                .withMarketRateAccuracy(1e-7)
                .withLowerRateBound(0.0)
                .withUpperRateBound(2.0)
                .withAdjustments(MarkovFunctional.ADJUST_NONE);

        return new MarkovFunctional(
                flatYts, 0.01, volStepDates, vols, flatOptionletVts,
                expiries, iborIndex, settings);
    }

    @Test
    public void testCrossValidate() {
        final ReferenceReader ref = ReferenceReader.load(
                "models/shortrate/onefactormodels/markov_functional");

        final MarkovFunctional swMF = buildSwaptionMF();
        final MarkovFunctional cpMF = buildCapletMF();

        final List<String> failures = new ArrayList<String>();

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final JSONObject exp = (JSONObject) c.expectedRaw();
            try {
                if (name.equals("swaption_numeraire_time")) {
                    assertTight(failures, name, swMF.numeraireTime(), exp.getDouble("value"));
                } else if (name.equals("swaption_sigma_size")) {
                    assertExactLong(failures, name, swMF.volatility().size(), exp.getLong("value"));
                } else if (name.startsWith("swaption_sigma_")) {
                    final int idx = parseIndexSuffix(name);
                    assertTight(failures, name, swMF.volatility().get(idx), exp.getDouble("value"));
                } else if (name.startsWith("swaption_num_")) {
                    final JSONObject in = c.inputs();
                    final double t = in.getDouble("t");
                    final double y = in.getDouble("y");
                    // LOOSE-with-A20 — see class javadoc.
                    assertLoose(failures, name, swMF.numeraire(t, y), exp.getDouble("value"));
                } else if (name.startsWith("swaption_zb_")) {
                    final JSONObject in = c.inputs();
                    final double T = in.getDouble("T");
                    final double t = in.getDouble("t");
                    final double y = in.getDouble("y");
                    assertLoose(failures, name, swMF.zerobond(T, t, y), exp.getDouble("value"));
                } else if (name.startsWith("swaption_mkt_zr_") || name.startsWith("swaption_mdl_zr_")) {
                    final int idx = parseIndexSuffix(name);
                    final MarkovFunctional.ModelOutputs mo = swMF.modelOutputs();
                    final double javaVal = name.contains("mkt_zr")
                            ? mo.marketZerorate_.get(idx)
                            : mo.modelZerorate_.get(idx);
                    assertLoose(failures, name, javaVal, exp.getDouble("value"));
                } else if (name.equals("caplet_numeraire_time")) {
                    assertTight(failures, name, cpMF.numeraireTime(), exp.getDouble("value"));
                } else if (name.equals("caplet_sigma_size")) {
                    assertExactLong(failures, name, cpMF.volatility().size(), exp.getLong("value"));
                } else if (name.startsWith("caplet_sigma_")) {
                    final int idx = parseIndexSuffix(name);
                    assertTight(failures, name, cpMF.volatility().get(idx), exp.getDouble("value"));
                } else if (name.startsWith("caplet_num_")) {
                    final JSONObject in = c.inputs();
                    final double t = in.getDouble("t");
                    final double y = in.getDouble("y");
                    assertLoose(failures, name, cpMF.numeraire(t, y), exp.getDouble("value"));
                } else if (name.startsWith("caplet_zb_")) {
                    final JSONObject in = c.inputs();
                    final double T = in.getDouble("T");
                    final double t = in.getDouble("t");
                    final double y = in.getDouble("y");
                    assertLoose(failures, name, cpMF.zerobond(T, t, y), exp.getDouble("value"));
                } else {
                    failures.add(name + ": no dispatcher branch");
                }
            } catch (final RuntimeException e) {
                failures.add(name + ": exception " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("MarkovFunctionalTest: " + failures.size() + " mismatch(es)\n  "
                    + String.join("\n  ", failures.subList(0, Math.min(30, failures.size())))
                    + (failures.size() > 30 ? "\n  ... (" + (failures.size() - 30) + " more)" : ""));
        }
    }

    private static int parseIndexSuffix(final String name) {
        int i = name.length();
        while (i > 0 && Character.isDigit(name.charAt(i - 1))) i--;
        return Integer.parseInt(name.substring(i));
    }

    private static void assertExactLong(final List<String> failures, final String name,
                                        final long java, final long cpp) {
        if (java != cpp) {
            failures.add(name + ": EXACT-long mismatch java=" + java + " cpp=" + cpp);
        }
    }

    private static void assertTight(final List<String> failures, final String name,
                                    final double java, final double cpp) {
        if (!Tolerance.tight(java, cpp)) {
            failures.add(name + ": TIGHT mismatch java=" + java + " cpp=" + cpp
                    + " (diff=" + Math.abs(java - cpp) + ")");
        }
    }

    private static void assertLoose(final List<String> failures, final String name,
                                    final double java, final double cpp) {
        if (!Tolerance.loose(java, cpp)) {
            failures.add(name + ": LOOSE-with-A20 mismatch java=" + java + " cpp=" + cpp
                    + " (diff=" + Math.abs(java - cpp) + ")");
        }
    }
}
