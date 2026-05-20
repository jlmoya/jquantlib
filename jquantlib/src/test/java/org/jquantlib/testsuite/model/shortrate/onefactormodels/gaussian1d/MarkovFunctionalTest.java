// jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/onefactormodels/gaussian1d/MarkovFunctionalTest.java
//
// Phase 2j.5 Track C.3 — MarkovFunctional concrete-model cross-validation
// against migration-harness/references/models/shortrate/onefactormodels/markov_functional.json
// (oracle: C++ QuantLib v1.42.1, markov_functional_probe.cpp).
package org.jquantlib.testsuite.model.shortrate.onefactormodels.gaussian1d;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.EURLibor6M;
import org.jquantlib.indexes.EurLiborSwapIsdaFixA;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.MarkovFunctional;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.processes.MfStateProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.InterpolatedSmileSection;
import org.jquantlib.termstructures.volatilities.KahaleSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.volatilities.SmileSectionUtils;
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

    /**
     * Smoke test: verify that {@code SABR_SMILE} adjustment no longer throws
     * {@code IllegalStateException} after Phase 2k Track A wiring.
     *
     * <p>Uses the same swaption fixture as {@link #buildSwaptionMF()} but with
     * {@code SABR_SMILE | SMILE_EXPONENTIAL_EXTRAPOLATION} adjustments (mirroring
     * the natural pairing in C++). The test simply asserts that construction and
     * {@code numeraireTime()} are reachable — numeric cross-validation of the
     * SABR-calibrated MF is out of scope for this smoke (that would require a
     * dedicated C++ probe, deferred to Phase 2k.5).
     */
    @Test
    public void testSabrSmileNoLongerThrows() {
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

        // Use SABR_SMILE | SMILE_EXPONENTIAL_EXTRAPOLATION (natural C++ pairing).
        // lowerRateBound must be 0.0 when KahaleSmile is active; here KAHALE is
        // NOT set so any lowerRateBound_ is valid. We leave defaults.
        final MarkovFunctional.ModelSettings settings = new MarkovFunctional.ModelSettings()
                .withYGridPoints(64)
                .withYStdDevs(7.0)
                .withGaussHermitePoints(32)
                .withDigitalGap(1e-5)
                .withMarketRateAccuracy(1e-7)
                .withLowerRateBound(0.0)
                .withUpperRateBound(2.0)
                .withAdjustments(
                        MarkovFunctional.SABR_SMILE
                        | MarkovFunctional.SMILE_EXPONENTIAL_EXTRAPOLATION);

        final MarkovFunctional mf = new MarkovFunctional(
                flatYts, 0.01, volStepDates, vols, flatSwaptionVts,
                expiries, tenors, swapIndexBase, settings);

        // Access numeraireTime() to force lazy-initialization (triggers updateSmiles).
        // Asserts that no UnsupportedOperationException / IllegalStateException is thrown.
        final double nt = mf.numeraireTime();
        // numeraireTime must be > 0 (5Y from reference date)
        if (nt <= 0.0) {
            fail("testSabrSmileNoLongerThrows: unexpected numeraireTime=" + nt);
        }
    }

    // -----------------------------------------------------------------------
    //  Phase 1 D5-D R3 — three additional cross-validation tests ported from
    //  v1.42.1 test-suite/markovfunctional.cpp lines 523-890.
    // -----------------------------------------------------------------------

    /**
     * Mirrors C++ {@code testMfStateProcess} (markovfunctional.cpp:523-638).
     * Pure deterministic check of the {@link MfStateProcess} drift, diffusion
     * and variance recurrences against bit-exact closed-form expected values
     * for two piecewise-constant volatility surfaces (reversion=0.00 and 0.01)
     * — no Monte Carlo, no Brent, hence TIGHT tolerance {@code 1E-10}.
     */
    @Test
    public void testMfStateProcess() {
        final double tolerance = 1E-10;

        // sp1: empty times, single vol 1.0 -> variance(dt) == dt.
        final MfStateProcess sp1 = new MfStateProcess(0.00, new double[0], new double[]{1.0});
        final double var11 = sp1.variance(0.0, 0.0, 1.0);
        final double var12 = sp1.variance(0.0, 0.0, 2.0);
        if (Math.abs(var11 - 1.0) > tolerance) {
            fail("process 1 has not variance 1.0 for dt = 1.0 but " + var11);
        }
        if (Math.abs(var12 - 2.0) > tolerance) {
            fail("process 1 has not variance 1.0 for dt = 1.0 but " + var12);
        }

        // sp2: times = [1, 2], vols = [1, 2, 3], reversion=0.
        final double[] times2 = new double[]{1.0, 2.0};
        final double[] vols2 = new double[]{1.0, 2.0, 3.0};
        final MfStateProcess sp2 = new MfStateProcess(0.00, times2, vols2);

        // diffusion: piecewise lookup by upper_bound(times_, t).
        if (Math.abs(sp2.diffusion(0.0, 0.0) - 1.0) > tolerance) {
            fail("process 2 has wrong drift at 0.0, should be 1.0 but is "
                    + sp2.diffusion(0.0, 0.0));
        }
        if (Math.abs(sp2.diffusion(0.99, 0.0) - 1.0) > tolerance) {
            fail("process 2 has wrong drift at 0.99, should be 1.0 but is "
                    + sp2.diffusion(0.99, 0.0));
        }
        if (Math.abs(sp2.diffusion(1.0, 0.0) - 2.0) > tolerance) {
            fail("process 2 has wrong drift at 1.0, should be 2.0 but is "
                    + sp2.diffusion(1.0, 0.0));
        }
        if (Math.abs(sp2.diffusion(1.9, 0.0) - 2.0) > tolerance) {
            fail("process 2 has wrong drift at 1.9, should be 2.0 but is "
                    + sp2.diffusion(1.9, 0.0));
        }
        if (Math.abs(sp2.diffusion(2.0, 0.0) - 3.0) > tolerance) {
            fail("process 2 has wrong drift at 2.0, should be 3.0 but is "
                    + sp2.diffusion(2.0, 0.0));
        }
        if (Math.abs(sp2.diffusion(3.0, 0.0) - 3.0) > tolerance) {
            fail("process 2 has wrong drift at 3.0, should be 3.0 but is "
                    + sp2.diffusion(3.0, 0.0));
        }
        if (Math.abs(sp2.diffusion(5.0, 0.0) - 3.0) > tolerance) {
            fail("process 2 has wrong drift at 5.0, should be 3.0 but is "
                    + sp2.diffusion(5.0, 0.0));
        }

        // variance with reversion = 0: piecewise-constant integration.
        if (Math.abs(sp2.variance(0.0, 0.0, 0.0) - 0.0) > tolerance) {
            fail("process 2 wrong variance at 0.0, expected 0.0 got "
                    + sp2.variance(0.0, 0.0, 0.0));
        }
        if (Math.abs(sp2.variance(0.0, 0.0, 0.5) - 0.5) > tolerance) {
            fail("process 2 wrong variance at 0.5, expected 0.5 got "
                    + sp2.variance(0.0, 0.0, 0.5));
        }
        if (Math.abs(sp2.variance(0.0, 0.0, 1.0) - 1.0) > tolerance) {
            fail("process 2 wrong variance at 1.0, expected 1.0 got "
                    + sp2.variance(0.0, 0.0, 1.0));
        }
        if (Math.abs(sp2.variance(0.0, 0.0, 1.5) - 3.0) > tolerance) {
            fail("process 2 wrong variance at 1.5, expected 3.0 got "
                    + sp2.variance(0.0, 0.0, 1.5));
        }
        if (Math.abs(sp2.variance(0.0, 0.0, 3.0) - 14.0) > tolerance) {
            fail("process 2 wrong variance at 3.0, expected 14.0 got "
                    + sp2.variance(0.0, 0.0, 3.0));
        }
        if (Math.abs(sp2.variance(0.0, 0.0, 5.0) - 32.0) > tolerance) {
            fail("process 2 wrong variance at 5.0, expected 32.0 got "
                    + sp2.variance(0.0, 0.0, 5.0));
        }
        if (Math.abs(sp2.variance(1.2, 0.0, 1.0) - 5.0) > tolerance) {
            fail("process 2 wrong variance between 1.2 and 2.2, expected 5.0 got "
                    + sp2.variance(1.2, 0.0, 1.0));
        }

        // sp3: same times/vols, reversion = 0.01 — exponential integration.
        final MfStateProcess sp3 = new MfStateProcess(0.01, times2, vols2);
        if (Math.abs(sp3.variance(0.0, 0.0, 0.0) - 0.0) > tolerance) {
            fail("process 3 wrong variance at 0.0, expected 0.0 got "
                    + sp3.variance(0.0, 0.0, 0.0));
        }
        if (Math.abs(sp3.variance(0.0, 0.0, 0.5) - 0.502508354208) > tolerance) {
            fail("process 3 wrong variance at 0.5, expected 0.502508354208 got "
                    + sp3.variance(0.0, 0.0, 0.5));
        }
        if (Math.abs(sp3.variance(0.0, 0.0, 1.0) - 1.01006700134) > tolerance) {
            fail("process 3 wrong variance at 1.0, expected 1.01006700134 got "
                    + sp3.variance(0.0, 0.0, 1.0));
        }
        if (Math.abs(sp3.variance(0.0, 0.0, 1.5) - 3.06070578669) > tolerance) {
            fail("process 3 wrong variance at 1.5, expected 3.06070578669 got "
                    + sp3.variance(0.0, 0.0, 1.5));
        }
        if (Math.abs(sp3.variance(0.0, 0.0, 3.0) - 14.5935513933) > tolerance) {
            fail("process 3 wrong variance at 3.0, expected 14.5935513933 got "
                    + sp3.variance(0.0, 0.0, 3.0));
        }
        if (Math.abs(sp3.variance(0.0, 0.0, 5.0) - 34.0940185819) > tolerance) {
            fail("process 3 wrong variance at 5.0, expected 34.0940185819 got "
                    + sp3.variance(0.0, 0.0, 5.0));
        }
        if (Math.abs(sp3.variance(1.2, 0.0, 1.0) - 5.18130257358) > tolerance) {
            fail("process 3 wrong variance between 1.2 and 2.2, expected 5.18130257358 got "
                    + sp3.variance(1.2, 0.0, 1.0));
        }
    }

    /**
     * Mirrors C++ {@code testKahaleSmileSection} (markovfunctional.cpp:640-843).
     * Tests Kahale smile reproduction, interpolation, no-arbitrage of digitals,
     * and exponential extrapolation. Uses {@code blackFormula} +
     * {@code blackFormulaImpliedStdDev} to seed an {@link InterpolatedSmileSection}
     * and then constructs several {@link KahaleSmileSection} instances with
     * varying flags. Tolerance {@code 1E-8} mirrors the C++ test.
     */
    @Test
    public void testKahaleSmileSection() {
        final double tol = 1E-8;
        final double atm = 0.05;
        final double t = 1.0;

        final double[] strikes = new double[]{
                0.01, 0.02, 0.03, 0.04, 0.05,
                0.06, 0.07, 0.08, 0.09, 0.10};
        final double[] money = new double[strikes.length];
        final double[] calls0 = new double[strikes.length];

        for (int i = 0; i < strikes.length; i++) {
            money[i] = strikes[i] / atm;
            calls0[i] = BlackFormula.blackFormula(
                    Option.Type.Call, strikes[i], atm, 0.50 * Math.sqrt(t), 1.0, 0.0);
        }

        final double[] stdDevs0 = impliedStdDevs(atm, strikes, calls0);
        final SmileSection sec1 = new InterpolatedSmileSection(
                t, strikes, stdDevs0, atm, new Linear(),
                new Actual365Fixed(), VolatilityType.ShiftedLognormal, 0.0, false);

        // ---- Test arbitrage-free smile reproduction (ksec11) ----
        final KahaleSmileSection ksec11 = new KahaleSmileSection(
                sec1, atm, false, false, false, money);

        if (Math.abs(ksec11.leftCoreStrike() - 0.01) > tol) {
            fail("smile11 left af strike is " + ksec11.leftCoreStrike() + " expected 0.01");
        }
        if (Math.abs(ksec11.rightCoreStrike() - 0.10) > tol) {
            fail("smile11 right af strike is " + ksec11.rightCoreStrike() + " expected 0.10");
        }
        for (double k = strikes[0]; k <= strikes[strikes.length - 1] + tol; k += 0.0001) {
            final double pric0 = sec1.optionPrice(k, Option.Type.Call, 1.0);
            final double pric1 = ksec11.optionPrice(k, Option.Type.Call, 1.0);
            if (Math.abs(pric0 - pric1) > tol) {
                fail("smile11 not reproduced at strike " + k
                        + " input smile call price " + pric0
                        + " kahale smile call price " + pric1);
            }
        }

        // ---- Test interpolation (ksec12) ----
        final KahaleSmileSection ksec12 = new KahaleSmileSection(
                sec1, atm, true, false, false, money);

        // C++ admits 0.01 or 0.02 here due to platform numerical differences.
        if (Math.abs(ksec12.leftCoreStrike() - 0.02) > tol
                && Math.abs(ksec12.leftCoreStrike() - 0.01) > tol) {
            fail("smile12 left af strike is " + ksec12.leftCoreStrike()
                    + " expected 0.01 or 0.02");
        }
        if (Math.abs(ksec12.rightCoreStrike() - 0.10) > tol) {
            fail("smile12 right af strike is " + ksec12.rightCoreStrike() + " expected 0.10");
        }
        for (int i = 1; i < strikes.length; i++) {
            final double pric0 = sec1.optionPrice(strikes[i], Option.Type.Call, 1.0);
            final double pric1 = ksec12.optionPrice(strikes[i], Option.Type.Call, 1.0);
            if (Math.abs(pric0 - pric1) > tol) {
                fail("smile12 not reproduced on grid at strike " + strikes[i]
                        + " input " + pric0 + " kahale " + pric1);
            }
        }

        // ---- Global no-arbitrage check of digitals ----
        {
            double dig00 = 1.0;
            double dig10 = 1.0;
            for (double k = 0.0010; k <= 2.0 * strikes[strikes.length - 1] + tol; k += 0.0001) {
                final double dig0 = ksec11.digitalOptionPrice(k, Option.Type.Call, 1.0, 1e-5);
                final double dig1 = ksec12.digitalOptionPrice(k, Option.Type.Call, 1.0, 1e-5);
                if (!(dig0 <= dig00 + tol && dig0 >= 0.0)) {
                    fail("arbitrage in digitals11 (" + dig00 + "," + dig0 + ") at strike " + k);
                }
                if (!(dig1 <= dig10 + tol && dig1 >= 0.0)) {
                    fail("arbitrage in digitals12 (" + dig10 + "," + dig1 + ") at strike " + k);
                }
                dig00 = dig0;
                dig10 = dig1;
            }
        }

        // ---- Exponential extrapolation (ksec13) ----
        final KahaleSmileSection ksec13 = new KahaleSmileSection(
                sec1, atm, false, true, false, money);

        {
            double k = strikes[strikes.length - 1];
            double dig0 = ksec13.digitalOptionPrice(k - 0.0010, Option.Type.Call, 1.0, 1e-5);
            while (k <= 10.0 * strikes[strikes.length - 1] + tol) {
                final double dig = ksec13.digitalOptionPrice(k, Option.Type.Call, 1.0, 1e-5);
                if (!(dig <= dig0 + tol && dig >= 0.0)) {
                    fail("arbitrage in digitals13 (" + dig0 + "," + dig + ") at strike " + k);
                }
                k += 0.0001;
            }
        }

        // ---- Arbitrageable smile (leftmost point) ----
        final double[] calls1 = calls0.clone();
        calls1[0] = (atm - strikes[0]) + 0.0010; // introduce arbitrage
        final double[] stdDevs1 = impliedStdDevs(atm, strikes, calls1);
        final SmileSection sec2 = new InterpolatedSmileSection(
                t, strikes, stdDevs1, atm, new Linear(),
                new Actual365Fixed(), VolatilityType.ShiftedLognormal, 0.0, false);

        final KahaleSmileSection ksec21 = new KahaleSmileSection(
                sec2, atm, false, false, false, money);
        final KahaleSmileSection ksec22 = new KahaleSmileSection(
                sec2, atm, true, false, true, money);

        if (Math.abs(ksec21.leftCoreStrike() - 0.02) > tol) {
            fail("smile21 left af strike is " + ksec21.leftCoreStrike() + " expected 0.02");
        }
        if (Math.abs(ksec22.leftCoreStrike() - 0.02) > tol) {
            fail("smile22 left af strike is " + ksec22.leftCoreStrike() + " expected 0.02");
        }
        if (Math.abs(ksec21.rightCoreStrike() - 0.10) > tol) {
            fail("smile21 right af strike is " + ksec21.rightCoreStrike() + " expected 0.10");
        }
        if (Math.abs(ksec22.rightCoreStrike() - 0.10) > tol) {
            fail("smile22 right af strike is " + ksec22.rightCoreStrike() + " expected 0.10");
        }

        {
            double dig00 = 1.0;
            double dig10 = 1.0;
            for (double k = 0.0010; k <= 2.0 * strikes[strikes.length - 1] + tol; k += 0.0001) {
                final double dig0 = ksec21.digitalOptionPrice(k, Option.Type.Call, 1.0, 1e-5);
                final double dig1 = ksec22.digitalOptionPrice(k, Option.Type.Call, 1.0, 1e-5);
                if (!(dig0 <= dig00 + tol && dig0 >= 0.0)) {
                    fail("arbitrage in digitals21 (" + dig00 + "," + dig0 + ") at strike " + k);
                }
                if (!(dig1 <= dig10 + tol && dig1 >= 0.0)) {
                    fail("arbitrage in digitals22 (" + dig10 + "," + dig1 + ") at strike " + k);
                }
                dig00 = dig0;
                dig10 = dig1;
            }
        }

        // ---- Arbitrageable smile (second-but-rightmost point) ----
        final double[] calls2 = calls0.clone();
        calls2[8] = 0.9 * calls2[9] + 0.1 * calls2[8]; // introduce arbitrage
        final double[] stdDevs2 = impliedStdDevs(atm, strikes, calls2);
        final SmileSection sec3 = new InterpolatedSmileSection(
                t, strikes, stdDevs2, atm, new Linear(),
                new Actual365Fixed(), VolatilityType.ShiftedLognormal, 0.0, false);

        final KahaleSmileSection ksec31 = new KahaleSmileSection(
                sec3, atm, false, false, false, money);
        final KahaleSmileSection ksec32 = new KahaleSmileSection(
                sec3, atm, true, false, true, money);

        if (Math.abs(ksec31.leftCoreStrike() - 0.01) > tol) {
            fail("smile31 left af strike is " + ksec31.leftCoreStrike() + " expected 0.01");
        }
        if (Math.abs(ksec32.leftCoreStrike() - 0.02) > tol
                && Math.abs(ksec32.leftCoreStrike() - 0.01) > tol) {
            fail("smile32 left af strike is " + ksec32.leftCoreStrike()
                    + " expected 0.01 or 0.02");
        }
        if (Math.abs(ksec31.rightCoreStrike() - 0.08) > tol) {
            fail("smile31 right af strike is " + ksec31.rightCoreStrike() + " expected 0.08");
        }
        if (Math.abs(ksec32.rightCoreStrike() - 0.10) > tol) {
            fail("smile32 right af strike is " + ksec32.rightCoreStrike() + " expected 0.10");
        }

        {
            double dig00 = 1.0;
            double dig10 = 1.0;
            for (double k = 0.0010; k <= 2.0 * strikes[strikes.length - 1] + tol; k += 0.0001) {
                final double dig0 = ksec31.digitalOptionPrice(k, Option.Type.Call, 1.0, 1e-5);
                final double dig1 = ksec32.digitalOptionPrice(k, Option.Type.Call, 1.0, 1e-5);
                if (!(dig0 <= dig00 + tol && dig0 >= 0.0)) {
                    fail("arbitrage in digitals31 (" + dig00 + "," + dig0 + ") at strike " + k);
                }
                if (!(dig1 <= dig10 + tol && dig1 >= 0.0)) {
                    fail("arbitrage in digitals32 (" + dig10 + "," + dig1 + ") at strike " + k);
                }
                dig00 = dig0;
                dig10 = dig1;
            }
        }
    }

    /**
     * Mirrors C++ {@code testSmileSectionUtilsWShapedSmile} (markovfunctional.cpp:845-890).
     * Regression test for QuantLib issue #2184: a W-shaped smile with arbitrage
     * around the ATM point forces {@link SmileSectionUtils} to push its central
     * index toward the grid boundary. Before the fix, the while loop evaluated
     * {@code af()} before the bounds check causing OOB on {@code c_[]}. Test
     * passes iff construction does not throw and a non-empty arbitrage-free
     * region is returned.
     */
    @Test
    public void testSmileSectionUtilsWShapedSmile() {
        final double atm = 0.05;
        final double t = 1.0;

        final double[] strikes = new double[]{
                0.01, 0.02, 0.03, 0.04, 0.05,
                0.06, 0.07, 0.08, 0.09, 0.10};
        final double[] vols = new double[]{
                0.35, 0.15, 0.40, 0.15, 0.35,
                0.15, 0.40, 0.15, 0.35, 0.20};

        final double[] money = new double[strikes.length];
        final double[] calls = new double[strikes.length];
        for (int i = 0; i < strikes.length; i++) {
            money[i] = strikes[i] / atm;
            calls[i] = BlackFormula.blackFormula(
                    Option.Type.Call, strikes[i], atm, vols[i] * Math.sqrt(t), 1.0, 0.0);
        }

        final double[] stdDevs = impliedStdDevs(atm, strikes, calls);
        final SmileSection sec = new InterpolatedSmileSection(
                t, strikes, stdDevs, atm, new Linear(),
                new Actual365Fixed(), VolatilityType.ShiftedLognormal, 0.0, false);

        // SmileSectionUtils must construct without crashing.
        // The central index will be pushed rightward through the arbitrageable
        // region; the fix ensures the loop terminates at the boundary instead
        // of reading past the end of c_[].
        final SmileSectionUtils utils = new SmileSectionUtils(sec, money, atm);

        // The arbitrage-free region should be valid (non-empty).
        final int[] idx = utils.arbitragefreeIndices();
        assertTrue("arbitrage-free region is empty: left=" + idx[0]
                + ", right=" + idx[1], idx[1] > idx[0]);
    }

    // ----- shared helper -----

    /**
     * Java port of C++ {@code impliedStdDevs(atm, strikes, prices)} from
     * markovfunctional.cpp lines 507-520. Inverts black formula to recover
     * std-devs from call prices, using initial guess 0.2 and accuracy 1E-8.
     *
     * <p>The C++ call passes maxIterations=1000 as the last positional
     * argument; the Java signature does not accept that overload (default
     * iteration cap is hard-coded in {@link BlackFormula}). Argument mapping:
     * discount=1.0, displacement=0.0, guess=0.2, accuracy=1E-8.
     */
    private static double[] impliedStdDevs(final double atm, final double[] strikes,
                                           final double[] prices) {
        final double[] result = new double[prices.length];
        for (int i = 0; i < prices.length; i++) {
            result[i] = BlackFormula.blackFormulaImpliedStdDev(
                    Option.Type.Call, strikes[i], atm, prices[i],
                    1.0, 0.2, 1E-8, 0.0);
        }
        return result;
    }
}
