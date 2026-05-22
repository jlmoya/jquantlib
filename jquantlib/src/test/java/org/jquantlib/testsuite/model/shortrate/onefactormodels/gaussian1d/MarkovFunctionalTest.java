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
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.BermudanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.EURLibor6M;
import org.jquantlib.indexes.Euribor;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.EurLiborSwapIsdaFixA;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.MakeCapFloor;
import org.jquantlib.instruments.MakeVanillaSwap;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.interpolations.factories.LogLinear;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.model.CalibrationHelper;
import org.jquantlib.model.shortrate.calibrationhelpers.SwaptionHelper;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.MarkovFunctional;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.pricingengines.capfloor.gaussian1d.Gaussian1dCapFloorEngine;
import org.jquantlib.pricingengines.swaption.BlackSwaptionEngine;
import org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dSwaptionEngine;
import org.jquantlib.processes.MfStateProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.IterativeBootstrap;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.InterpolatedSmileSection;
import org.jquantlib.termstructures.volatilities.KahaleSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.volatilities.SmileSectionUtils;
import org.jquantlib.termstructures.volatilities.capfloor.CapFloorTermVolSurface;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletStripper;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletStripper1;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.volatilities.optionlet.StrippedOptionletAdapter;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.termstructures.volatilities.swaption.SabrSwaptionVolatilityCube;
import org.jquantlib.termstructures.volatilities.swaption.SwaptionVolatilityMatrix;
import org.jquantlib.termstructures.yieldcurves.Discount;
import org.jquantlib.termstructures.yieldcurves.DepositRateHelper;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.termstructures.yieldcurves.FraRateHelper;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.termstructures.yieldcurves.SwapRateHelper;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Assume;
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

        final List<Date> volStepDates = new ArrayList<>();
        final double[] vols = new double[]{0.01};

        final List<Date> expiries = new ArrayList<>();
        expiries.add(REFERENCE_DATE.add(new Period(5, TimeUnit.Years)));
        final List<Period> tenors = new ArrayList<>();
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

        final List<Date> volStepDates = new ArrayList<>();
        final double[] vols = new double[]{0.01};

        final List<Date> expiries = new ArrayList<>();
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

        final List<String> failures = new ArrayList<>();

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

        final List<Date> volStepDates = new ArrayList<>();
        final double[] vols = new double[]{0.01};

        final List<Date> expiries = new ArrayList<>();
        expiries.add(REFERENCE_DATE.add(new Period(5, TimeUnit.Years)));
        final List<Period> tenors = new ArrayList<>();
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

    // -----------------------------------------------------------------------
    //  Phase 1 closure A7-F R563 — port v1.42.1 testBermudanSwaption
    //  (markovfunctional.cpp:1641-1719). Heavy-fixture port: md0Yts (60 rate
    //  helpers, PiecewiseYieldCurve<Discount,LogLinear>), md0SwaptionVts
    //  (SabrSwaptionVolatilityCube on top of SwaptionVolatilityMatrix),
    //  expiriesCalBasket3 / tenorsCalBasket3 (coterminal 10y basket).
    //  Tolerance 1bp (1e-4) absolute against cached values, matching C++.
    // -----------------------------------------------------------------------

    /**
     * Mirrors C++ {@code testBermudanSwaption} (markovfunctional.cpp:1641-1719).
     *
     * <p>Builds a {@link MarkovFunctional} model calibrated to a coterminal
     * 10y basket of swaptions (9 expiries × decreasing tenors) using the
     * Md0 yield curve and Sabr swaption vol cube. Prices 9 European swaptions
     * and 1 Bermudan via {@link Gaussian1dSwaptionEngine}; compares each NPV
     * to the C++ cached value within 1bp absolute (the same tolerance the C++
     * test uses).
     */
    @Test
    public void testBermudanSwaption() {
        final double tol0 = 0.0001; // 1bp tolerance against cached values

        final Date referenceDate = new Date(14, Month.November, 2012);
        new Settings().setEvaluationDate(referenceDate);

        final Handle<YieldTermStructure> md0Yts_ = md0Yts();
        final Handle<SwaptionVolatilityStructure> md0SwaptionVts_ = md0SwaptionVts();

        final SwapIndex swapIndexBase = new EuriborSwapIsdaFixA(
                new Period(1, TimeUnit.Years));

        final List<Date> volStepDates = new ArrayList<>();
        final double[] vols = new double[]{1.0};

        final IborIndex iborIndex1 = new Euribor6M(md0Yts_);

        final List<Date> expiries = expiriesCalBasket3(referenceDate);
        final List<Period> tenors = tenorsCalBasket3();

        final MarkovFunctional.ModelSettings settings =
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(32)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(16)
                        .withMarketRateAccuracy(1e-7)
                        .withDigitalGap(1e-5)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0);

        final MarkovFunctional mf1 = new MarkovFunctional(
                md0Yts_, 0.01, volStepDates, vols, md0SwaptionVts_,
                expiries, tenors, swapIndexBase, settings);

        final PricingEngine mfSwaptionEngine1 = new Gaussian1dSwaptionEngine(mf1, 64, 7.0);

        // Underlying call: 10y payer vanilla swap fixed=3%, effective in 2 days.
        final VanillaSwap underlyingCall =
                new MakeVanillaSwap(new Period(10, TimeUnit.Years), iborIndex1, 0.03)
                        .withEffectiveDate(new Target().advance(referenceDate, 2, TimeUnit.Days))
                        .receiveFixed(false)
                        .value();

        final List<Exercise> europeanExercises = new ArrayList<>();
        final List<Swaption> europeanSwaptions = new ArrayList<>();
        for (int i = 0; i < expiries.size(); i++) {
            europeanExercises.add(new EuropeanExercise(expiries.get(i)));
            final Swaption sw = new Swaption(underlyingCall, europeanExercises.get(i));
            sw.setPricingEngine(mfSwaptionEngine1);
            europeanSwaptions.add(sw);
        }

        final Exercise bermudanExercise = new BermudanExercise(
                expiries.toArray(new Date[0]));
        final Swaption bermudanSwaption = new Swaption(underlyingCall, bermudanExercise);
        bermudanSwaption.setPricingEngine(mfSwaptionEngine1);

        final double[] cachedValues = new double[]{
                0.0030757, 0.0107344, 0.0179862,
                0.0225881, 0.0243215, 0.0229148,
                0.0191415, 0.0139035, 0.0076354};
        final double cachedValue = 0.0327776;

        for (int i = 0; i < expiries.size(); i++) {
            final double npv = europeanSwaptions.get(i).NPV();
            if (Math.abs(npv - cachedValues[i]) > tol0) {
                fail("European swaption value (" + npv
                        + ") deviates from cached value (" + cachedValues[i] + ")");
            }
        }

        final double npv = bermudanSwaption.NPV();
        if (Math.abs(npv - cachedValue) > tol0) {
            fail("Bermudan swaption value (" + npv
                    + ") deviates from cached value (" + cachedValue + ")");
        }
    }

    // -----------------------------------------------------------------------
    //  Phase 1 closure A7-I R563 — port v1.42.1 testCalibrationOneInstrumentSet
    //  (markovfunctional.cpp:892-1118). Builds 4 MarkovFunctional models across
    //  the cartesian product {basket1 swaption, basket2 caplet} ×
    //  {flat termstructures, md0 termstructures} and verifies that, after
    //  numeraire calibration, modelOutputs() reports market zerorate / call
    //  / put premia matching the market values to 1bp absolute.
    //  Slow-gated to mirror C++ {@code if_speed(Slow)}.
    // -----------------------------------------------------------------------

    /**
     * Mirrors C++ {@code testCalibrationOneInstrumentSet}
     * (markovfunctional.cpp:892-1118).
     *
     * <p>For each of 4 calibration scenarios, builds a {@link MarkovFunctional}
     * and reads {@link MarkovFunctional#modelOutputs()} after the (internal)
     * numeraire calibration. C++ tolerances:
     * <ul>
     *   <li>{@code tol0 = 1e-4} for {@code marketZerorate_} vs
     *       {@code modelZerorate_} (1bp on zero rates implied by the smile
     *       calibration of the numeraire);</li>
     *   <li>{@code tol1 = 1e-4} for {@code marketCallPremium_} /
     *       {@code marketPutPremium_} vs the corresponding {@code modelXxxPremium_}
     *       (1bp on call/put premia).</li>
     * </ul>
     * Both are absolute tolerances matching C++; the test does not loosen
     * either tier.
     */
    @Test
    public void testCalibrationOneInstrumentSet() {
        Assume.assumeTrue("test gated -Dql.slowTests=1 to mirror C++ if_speed(Slow)",
                System.getProperty("ql.slowTests") != null);

        final double tol0 = 0.0001; // 1bp tolerance market/model zero rate
        final double tol1 = 0.0001; // 1bp tolerance market/model call/put premia

        final Date referenceDate = new Date(14, Month.November, 2012);
        new Settings().setEvaluationDate(referenceDate);

        final Handle<YieldTermStructure> flatYts_ = flatYts();
        final Handle<YieldTermStructure> md0Yts_ = md0Yts();
        final Handle<SwaptionVolatilityStructure> flatSwaptionVts_ = flatSwaptionVts();
        final Handle<SwaptionVolatilityStructure> md0SwaptionVts_ = md0SwaptionVts();
        final Handle<OptionletVolatilityStructure> flatOptionletVts_ = flatOptionletVts();
        final Handle<OptionletVolatilityStructure> md0OptionletVts_ = md0OptionletVts();

        final SwapIndex swapIndexBase = new EuriborSwapIsdaFixA(
                new Period(1, TimeUnit.Years));
        final IborIndex iborIndex = new Euribor(new Period(6, TimeUnit.Months));

        final List<Date> volStepDates = new ArrayList<>();
        final double[] vols = new double[]{1.0};

        // use a grid with fewer points for smile arbitrage
        // testing and model outputs than the default grid
        final double[] money = new double[]{
                0.1, 0.25, 0.50, 0.75, 1.0, 1.25, 1.50, 2.0, 5.0};

        // Calibration Basket 1 / flat yts, vts -------------------------------
        final MarkovFunctional mf1 = new MarkovFunctional(
                flatYts_, 0.01, volStepDates, vols, flatSwaptionVts_,
                expiriesCalBasket1(referenceDate), tenorsCalBasket1(),
                swapIndexBase,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(64)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(32)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withAdjustments(MarkovFunctional.KAHALE_SMILE
                                | MarkovFunctional.SMILE_EXPONENTIAL_EXTRAPOLATION)
                        .withSmileMoneynessCheckpoints(money));
        final MarkovFunctional.ModelOutputs outputs1 = mf1.modelOutputs();
        checkZerorates(outputs1, tol0, "Basket 1 / flat termstructures");
        checkPremia(outputs1, tol1, "Basket 1 / flat termstructures");

        // Calibration Basket 2 / flat yts, vts -------------------------------
        final MarkovFunctional mf2 = new MarkovFunctional(
                flatYts_, 0.01, volStepDates, vols, flatOptionletVts_,
                expiriesCalBasket2(referenceDate), iborIndex,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(64)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(32)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withAdjustments(MarkovFunctional.ADJUST_NONE)
                        .withSmileMoneynessCheckpoints(money));
        final MarkovFunctional.ModelOutputs outputs2 = mf2.modelOutputs();
        checkZerorates(outputs2, tol0, "Basket 2 / flat termstructures");
        checkPremia(outputs2, tol1, "Basket 2 / flat termstructures");

        // Calibration Basket 1 / real yts, vts -------------------------------
        final MarkovFunctional mf3 = new MarkovFunctional(
                md0Yts_, 0.01, volStepDates, vols, md0SwaptionVts_,
                expiriesCalBasket1(referenceDate), tenorsCalBasket1(),
                swapIndexBase,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(128) // use more points to increase accuracy
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(64)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withSmileMoneynessCheckpoints(money));
        final MarkovFunctional.ModelOutputs outputs3 = mf3.modelOutputs();
        checkZerorates(outputs3, tol0, "Basket 1 / real termstructures");
        checkPremia(outputs3, tol1, "Basket 1 / real termstructures");

        // Calibration Basket 2 / real yts, vts -------------------------------
        final MarkovFunctional mf4 = new MarkovFunctional(
                md0Yts_, 0.01, volStepDates, vols, md0OptionletVts_,
                expiriesCalBasket2(referenceDate), iborIndex,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(64)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(32)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withSmileMoneynessCheckpoints(money));
        final MarkovFunctional.ModelOutputs outputs4 = mf4.modelOutputs();
        checkZerorates(outputs4, tol0, "Basket 2 / real termstructures");
        checkPremia(outputs4, tol1, "Basket 2 / real termstructures");
    }

    private static void checkZerorates(final MarkovFunctional.ModelOutputs out,
            final double tol, final String tag) {
        for (int i = 0; i < out.expiries_.size(); i++) {
            final double m = out.marketZerorate_.get(i);
            final double mo = out.modelZerorate_.get(i);
            if (Math.abs(m - mo) > tol) {
                fail(tag + ": Market zero rate (" + m
                        + ") and model zero rate (" + mo + ") do not agree.");
            }
        }
    }

    private static void checkPremia(final MarkovFunctional.ModelOutputs out,
            final double tol, final String tag) {
        for (int i = 0; i < out.expiries_.size(); i++) {
            for (int j = 0; j < out.smileStrikes_.get(i).size(); j++) {
                final double mc = out.marketCallPremium_.get(i).get(j);
                final double moc = out.modelCallPremium_.get(i).get(j);
                if (Math.abs(mc - moc) > tol) {
                    fail(tag + ": Market call premium (" + mc
                            + ") does not match model premium (" + moc + ")");
                }
                final double mp = out.marketPutPremium_.get(i).get(j);
                final double mop = out.modelPutPremium_.get(i).get(j);
                if (Math.abs(mp - mop) > tol) {
                    fail(tag + ": Market put premium (" + mp
                            + ") does not match model premium (" + mop + ")");
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Phase 1 closure A8-B R563 — port v1.42.1 testCalibrationTwoInstrumentSets
    //  (markovfunctional.cpp:1401-1639). Smoke test: drive the secondary
    //  LevenbergMarquardt loop over SwaptionHelper instruments to verify that
    //  the new {@code Gaussian1dModel.calibrate(...)} surface (CalibratedModel
    //  composition delegate from align(model.Gaussian1dModel) — same batch)
    //  completes without throwing. C++ uses {@code BOOST_TEST_MESSAGE} for
    //  all discrepancy reports rather than {@code BOOST_CHECK}, i.e. the test
    //  is also a smoke test in C++ — it logs deviations but does not fail.
    //  Fast-gated per C++ {@code *precondition(if_speed(Fast))}.
    // -----------------------------------------------------------------------

    /**
     * Mirrors C++ {@code testCalibrationTwoInstrumentSets} (markovfunctional.cpp:1401-1639).
     *
     * <p>Builds two {@link MarkovFunctional} instances (flat and md0 termstructures),
     * primary-calibrates each via the Gaussian1dSwaptionEngine, then runs a
     * secondary calibration over four coterminal SwaptionHelpers driven by
     * {@link LevenbergMarquardt}. The C++ test uses {@code BOOST_TEST_MESSAGE}
     * for all reporting, so the only failure mode tested here is that the
     * {@link MarkovFunctional#calibrate} surface resolves and runs to
     * completion. Discrepancies (vs Black engine) are logged via {@code
     * System.out} for informational purposes only — matching C++ semantics
     * exactly (Phase 1 closure A8-B-563).
     *
     * <p><strong>Fast-gated</strong> via {@code -Dql.fastTests=1} to mirror C++
     * {@code *precondition(if_speed(Fast))}. The full secondary calibration
     * (two LM loops × 4 helpers × ~1000 iterations of full numeraire
     * tabulation per evaluation) is multi-minute wall time — exclude from
     * default builds.
     */
    @Test
    public void testCalibrationTwoInstrumentSets() {
        Assume.assumeTrue("test gated -Dql.fastTests=1 to mirror C++ if_speed(Fast)",
                System.getProperty("ql.fastTests") != null);

        final double tol1 = 0.1; // 0.1 times vega tolerance (C++ markovfunctional.cpp:1403-1404)

        final Date referenceDate = new Date(14, Month.November, 2012);
        new Settings().setEvaluationDate(referenceDate);

        final Handle<YieldTermStructure> flatYts_ = flatYts();
        final Handle<YieldTermStructure> md0Yts_ = md0Yts();
        final Handle<SwaptionVolatilityStructure> flatSwaptionVts_ = flatSwaptionVts();
        final Handle<SwaptionVolatilityStructure> md0SwaptionVts_ = md0SwaptionVts();

        final SwapIndex swapIndexBase = new EuriborSwapIsdaFixA(
                new Period(1, TimeUnit.Years));

        final List<Date> volStepDates = new ArrayList<>();
        volStepDates.add(new Target().advance(referenceDate, new Period(1, TimeUnit.Years)));
        volStepDates.add(new Target().advance(referenceDate, new Period(2, TimeUnit.Years)));
        volStepDates.add(new Target().advance(referenceDate, new Period(3, TimeUnit.Years)));
        volStepDates.add(new Target().advance(referenceDate, new Period(4, TimeUnit.Years)));

        final double[] vols = new double[]{1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] money = new double[]{
                0.1, 0.25, 0.50, 0.75, 1.0, 1.25, 1.50, 2.0, 5.0};

        final OptimizationMethod om = new LevenbergMarquardt();
        final EndCriteria ec = new EndCriteria(1000, 500, 1e-2, 1e-2, 1e-2);

        // ──────────────────────────────────────────────────────────────────
        // Calibration Basket 1 / flat yts, vts / Secondary set = coterminals
        // ──────────────────────────────────────────────────────────────────

        final IborIndex iborIndex1 = new Euribor(new Period(6, TimeUnit.Months), flatYts_);

        final double[] calibrationHelperVols1 = new double[]{0.20, 0.20, 0.20, 0.20};

        final List<CalibrationHelper> calibrationHelper1 = new ArrayList<>();
        calibrationHelper1.add(new SwaptionHelper(
                new Period(1, TimeUnit.Years), new Period(4, TimeUnit.Years),
                new Handle<Quote>(new SimpleQuote(calibrationHelperVols1[0])),
                iborIndex1, new Period(1, TimeUnit.Years), new Thirty360(),
                new Actual360(), flatYts_));
        calibrationHelper1.add(new SwaptionHelper(
                new Period(2, TimeUnit.Years), new Period(3, TimeUnit.Years),
                new Handle<Quote>(new SimpleQuote(calibrationHelperVols1[1])),
                iborIndex1, new Period(1, TimeUnit.Years), new Thirty360(),
                new Actual360(), flatYts_));
        calibrationHelper1.add(new SwaptionHelper(
                new Period(3, TimeUnit.Years), new Period(2, TimeUnit.Years),
                new Handle<Quote>(new SimpleQuote(calibrationHelperVols1[2])),
                iborIndex1, new Period(1, TimeUnit.Years), new Thirty360(),
                new Actual360(), flatYts_));
        calibrationHelper1.add(new SwaptionHelper(
                new Period(4, TimeUnit.Years), new Period(1, TimeUnit.Years),
                new Handle<Quote>(new SimpleQuote(calibrationHelperVols1[3])),
                iborIndex1, new Period(1, TimeUnit.Years), new Thirty360(),
                new Actual360(), flatYts_));

        final MarkovFunctional mf1 = new MarkovFunctional(
                flatYts_, 0.01, volStepDates, vols, flatSwaptionVts_,
                expiriesCalBasket1(referenceDate), tenorsCalBasket1(),
                swapIndexBase,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(64)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(32)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withSmileMoneynessCheckpoints(money));

        final PricingEngine mfSwaptionEngine1 = new Gaussian1dSwaptionEngine(mf1, 64, 7.0);
        for (final CalibrationHelper h : calibrationHelper1) {
            ((SwaptionHelper) h).setPricingEngine(mfSwaptionEngine1);
        }

        // Drive secondary calibration via the freshly hoisted
        // Gaussian1dModel.calibrate(...) surface (Phase 1 closure A8-B).
        // Smoke validate — completion without exception is the assertion.
        // Mirrors C++ MarkovFunctional::calibrate override (markovfunctional.hpp:340)
        // which fixes vols[0] so that #free-params (4) == #instruments (4) —
        // LevenbergMarquardt requires equal or more functions than free parameters.
        final boolean[] fixFirstVol1 = new boolean[]{true, false, false, false, false};
        mf1.calibrate(calibrationHelper1, om, ec, new NoConstraint(), null, fixFirstVol1);

        // C++ uses BOOST_TEST_MESSAGE (informational only); we mirror by
        // logging to System.out without failing the test.
        for (int i = 0; i < calibrationHelper1.size(); i++) {
            final SwaptionHelper sh = (SwaptionHelper) calibrationHelper1.get(i);
            final Swaption swp = sh.swaption();
            final BlackSwaptionEngine blackEngine = new BlackSwaptionEngine(flatYts_, calibrationHelperVols1[i]);
            swp.setPricingEngine(blackEngine);
            final double blackPrice = swp.NPV();
            final Object vegaObj = ((Instrument.ResultsImpl) blackEngine.getResults())
                    .additionalResults().get("vega");
            final double blackVega = vegaObj instanceof Number ? ((Number) vegaObj).doubleValue() : 0.0;
            swp.setPricingEngine(mfSwaptionEngine1);
            final double mfPrice = swp.NPV();
            if (blackVega > 0.0 && Math.abs(blackPrice - mfPrice) / blackVega > tol1) {
                System.out.println("Basket 1 / flat yts, vts: Secondary instrument set "
                        + "calibration deviation for instrument #" + i
                        + " black premium=" + blackPrice
                        + " model premium=" + mfPrice
                        + " (vega=" + blackVega + ")");
            }
        }

        // ──────────────────────────────────────────────────────────────────
        // Calibration Basket 1 / real yts, vts / Secondary set = coterminals
        // ──────────────────────────────────────────────────────────────────

        final IborIndex iborIndex2 = new Euribor(new Period(6, TimeUnit.Months), md0Yts_);

        final MarkovFunctional mf2 = new MarkovFunctional(
                md0Yts_, 0.01, volStepDates, vols, md0SwaptionVts_,
                expiriesCalBasket1(referenceDate), tenorsCalBasket1(),
                swapIndexBase,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(64)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(32)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withSmileMoneynessCheckpoints(money));

        // ATM-strike vols from the SabrSwaptionVolatilityCube.
        final SabrSwaptionVolatilityCube cube =
                (SabrSwaptionVolatilityCube) md0SwaptionVts_.currentLink();
        final double[] calibrationHelperVols2 = new double[]{
                md0SwaptionVts_.currentLink().volatility(
                        new Period(1, TimeUnit.Years), new Period(4, TimeUnit.Years),
                        cube.atmStrike(new Period(1, TimeUnit.Years), new Period(4, TimeUnit.Years))),
                md0SwaptionVts_.currentLink().volatility(
                        new Period(2, TimeUnit.Years), new Period(3, TimeUnit.Years),
                        cube.atmStrike(new Period(2, TimeUnit.Years), new Period(3, TimeUnit.Years))),
                md0SwaptionVts_.currentLink().volatility(
                        new Period(3, TimeUnit.Years), new Period(2, TimeUnit.Years),
                        cube.atmStrike(new Period(3, TimeUnit.Years), new Period(2, TimeUnit.Years))),
                md0SwaptionVts_.currentLink().volatility(
                        new Period(4, TimeUnit.Years), new Period(1, TimeUnit.Years),
                        cube.atmStrike(new Period(4, TimeUnit.Years), new Period(1, TimeUnit.Years)))};

        final List<CalibrationHelper> calibrationHelper2 = new ArrayList<>();
        calibrationHelper2.add(new SwaptionHelper(
                new Period(1, TimeUnit.Years), new Period(4, TimeUnit.Years),
                new Handle<Quote>(new SimpleQuote(calibrationHelperVols2[0])),
                iborIndex2, new Period(1, TimeUnit.Years), new Thirty360(),
                new Actual360(), md0Yts_));
        calibrationHelper2.add(new SwaptionHelper(
                new Period(2, TimeUnit.Years), new Period(3, TimeUnit.Years),
                new Handle<Quote>(new SimpleQuote(calibrationHelperVols2[1])),
                iborIndex2, new Period(1, TimeUnit.Years), new Thirty360(),
                new Actual360(), md0Yts_));
        calibrationHelper2.add(new SwaptionHelper(
                new Period(3, TimeUnit.Years), new Period(2, TimeUnit.Years),
                new Handle<Quote>(new SimpleQuote(calibrationHelperVols2[2])),
                iborIndex2, new Period(1, TimeUnit.Years), new Thirty360(),
                new Actual360(), md0Yts_));
        calibrationHelper2.add(new SwaptionHelper(
                new Period(4, TimeUnit.Years), new Period(1, TimeUnit.Years),
                new Handle<Quote>(new SimpleQuote(calibrationHelperVols2[3])),
                iborIndex2, new Period(1, TimeUnit.Years), new Thirty360(),
                new Actual360(), md0Yts_));

        final PricingEngine mfSwaptionEngine2 = new Gaussian1dSwaptionEngine(mf2, 64, 7.0);
        for (final CalibrationHelper h : calibrationHelper2) {
            ((SwaptionHelper) h).setPricingEngine(mfSwaptionEngine2);
        }

        final boolean[] fixFirstVol2 = new boolean[]{true, false, false, false, false};
        mf2.calibrate(calibrationHelper2, om, ec, new NoConstraint(), null, fixFirstVol2);

        for (int i = 0; i < calibrationHelper2.size(); i++) {
            final SwaptionHelper sh = (SwaptionHelper) calibrationHelper2.get(i);
            final Swaption swp = sh.swaption();
            final BlackSwaptionEngine blackEngine = new BlackSwaptionEngine(md0Yts_, calibrationHelperVols2[i]);
            swp.setPricingEngine(blackEngine);
            final double blackPrice = swp.NPV();
            final Object vegaObj = ((Instrument.ResultsImpl) blackEngine.getResults())
                    .additionalResults().get("vega");
            final double blackVega = vegaObj instanceof Number ? ((Number) vegaObj).doubleValue() : 0.0;
            swp.setPricingEngine(mfSwaptionEngine2);
            final double mfPrice = swp.NPV();
            if (blackVega > 0.0 && Math.abs(blackPrice - mfPrice) / blackVega > tol1) {
                System.out.println("Basket 1 / real yts, vts: Secondary instrument set "
                        + "calibration deviation for instrument #" + i
                        + " black premium=" + blackPrice
                        + " model premium=" + mfPrice
                        + " (vega=" + blackVega + ")");
            }
        }

        // No tolerance assertion — C++ uses BOOST_TEST_MESSAGE throughout.
        // Test passes if both calibrate() calls complete without throwing.
        assertTrue("testCalibrationTwoInstrumentSets completed", true);
    }
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    //  Phase 1 closure A7-I R563 — port v1.42.1 testVanillaEngines
    //  (markovfunctional.cpp:1120-1399). Compares Black engine NPVs vs
    //  Gaussian1dSwaptionEngine/Gaussian1dCapFloorEngine NPVs across the cube
    //  of {basket1 swaption, basket2 caplet} × {flat termstructures, md0
    //  termstructures} × strikes/expiries from modelOutputs. C++ tol1=1e-4 abs.
    //  Slow-gated to mirror C++ {@code if_speed(Slow)}.
    // -----------------------------------------------------------------------

    /**
     * Mirrors C++ {@code testVanillaEngines} (markovfunctional.cpp:1120-1399).
     *
     * <p>The "real termstructures" baskets carry a smile correction term
     * {@code marketCallPremium_ - marketRawCallPremium_} that compensates for
     * the Sabr-cube re-fit; the test sums that into the difference before
     * comparing to {@code tol1 = 1e-4} absolute, matching C++ exactly.
     */
    @Test
    public void testVanillaEngines() {
        Assume.assumeTrue("test gated -Dql.slowTests=1 to mirror C++ if_speed(Slow)",
                System.getProperty("ql.slowTests") != null);

        final double tol1 = 0.0001; // 1bp tolerance

        final Date referenceDate = new Date(14, Month.November, 2012);
        new Settings().setEvaluationDate(referenceDate);

        final Handle<YieldTermStructure> flatYts_ = flatYts();
        final Handle<YieldTermStructure> md0Yts_ = md0Yts();
        final Handle<SwaptionVolatilityStructure> flatSwaptionVts_ = flatSwaptionVts();
        final Handle<SwaptionVolatilityStructure> md0SwaptionVts_ = md0SwaptionVts();
        final Handle<OptionletVolatilityStructure> flatOptionletVts_ = flatOptionletVts();
        final Handle<OptionletVolatilityStructure> md0OptionletVts_ = md0OptionletVts();

        final SwapIndex swapIndexBase = new EuriborSwapIsdaFixA(
                new Period(1, TimeUnit.Years));

        final List<Date> volStepDates = new ArrayList<>();
        final double[] vols = new double[]{1.0};
        final double[] money = new double[]{
                0.1, 0.25, 0.50, 0.75, 1.0, 1.25, 1.50, 2.0, 5.0};

        final Target target = new Target();

        // Calibration Basket 1 / flat yts, vts -------------------------------
        final IborIndex iborIndex1 = new Euribor(new Period(6, TimeUnit.Months), flatYts_);
        final MarkovFunctional mf1 = new MarkovFunctional(
                flatYts_, 0.01, volStepDates, vols, flatSwaptionVts_,
                expiriesCalBasket1(referenceDate), tenorsCalBasket1(),
                swapIndexBase,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(64)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(32)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withSmileMoneynessCheckpoints(money));
        final MarkovFunctional.ModelOutputs outputs1 = mf1.modelOutputs();
        final PricingEngine mfSwaptionEngine1 = new Gaussian1dSwaptionEngine(mf1, 64, 7.0);
        final PricingEngine blackSwaptionEngine1 = new BlackSwaptionEngine(
                flatYts_, flatSwaptionVts_);

        for (int i = 0; i < outputs1.expiries_.size(); i++) {
            for (int j = 0; j < outputs1.smileStrikes_.get(0).size(); j++) {
                final double strike = outputs1.smileStrikes_.get(i).get(j);
                final VanillaSwap underlyingCall = new MakeVanillaSwap(
                        outputs1.tenors_.get(i), iborIndex1, strike)
                        .withEffectiveDate(target.advance(
                                outputs1.expiries_.get(i), 2, TimeUnit.Days))
                        .receiveFixed(false).value();
                final VanillaSwap underlyingPut = new MakeVanillaSwap(
                        outputs1.tenors_.get(i), iborIndex1, strike)
                        .withEffectiveDate(target.advance(
                                outputs1.expiries_.get(i), 2, TimeUnit.Days))
                        .receiveFixed(true).value();
                final Exercise exercise = new EuropeanExercise(outputs1.expiries_.get(i));
                final Swaption swaptionC = new Swaption(underlyingCall, exercise);
                final Swaption swaptionP = new Swaption(underlyingPut, exercise);
                swaptionC.setPricingEngine(blackSwaptionEngine1);
                swaptionP.setPricingEngine(blackSwaptionEngine1);
                final double blackPriceCall = swaptionC.NPV();
                final double blackPricePut = swaptionP.NPV();
                swaptionC.setPricingEngine(mfSwaptionEngine1);
                swaptionP.setPricingEngine(mfSwaptionEngine1);
                final double mfPriceCall = swaptionC.NPV();
                final double mfPricePut = swaptionP.NPV();
                if (Math.abs(blackPriceCall - mfPriceCall) > tol1) {
                    fail("Basket 1 / flat termstructures: Call premium market ("
                            + blackPriceCall + ") does not match model premium ("
                            + mfPriceCall + ")");
                }
                if (Math.abs(blackPricePut - mfPricePut) > tol1) {
                    fail("Basket 1 / flat termstructures: Put premium market ("
                            + blackPricePut + ") does not match model premium ("
                            + mfPricePut + ")");
                }
            }
        }

        // Calibration Basket 2 / flat yts, vts -------------------------------
        final IborIndex iborIndex2 = new Euribor(new Period(6, TimeUnit.Months), flatYts_);
        final MarkovFunctional mf2 = new MarkovFunctional(
                flatYts_, 0.01, volStepDates, vols, flatOptionletVts_,
                expiriesCalBasket2(referenceDate), iborIndex2,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(64)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(16)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withSmileMoneynessCheckpoints(money));
        mf2.modelOutputs(); // touch — parity with C++
        final PricingEngine blackCapFloorEngine2 = new BlackCapFloorEngine(
                flatYts_, flatOptionletVts_);
        final PricingEngine mfCapFloorEngine2 = new Gaussian1dCapFloorEngine(mf2, 64, 7.0);
        final List<CapFloor> c2 = new ArrayList<>();
        final double[] strikesBasket2Flat = new double[]{
                0.01, 0.02, 0.03, 0.04, 0.05, 0.07, 0.10};
        for (final double s : strikesBasket2Flat) {
            c2.add(new MakeCapFloor(CapFloor.Type.Cap,
                    new Period(5, TimeUnit.Years), iborIndex2, s).value());
        }
        for (final double s : strikesBasket2Flat) {
            c2.add(new MakeCapFloor(CapFloor.Type.Floor,
                    new Period(5, TimeUnit.Years), iborIndex2, s).value());
        }
        for (final CapFloor cf : c2) {
            cf.setPricingEngine(blackCapFloorEngine2);
            final double blackPrice = cf.NPV();
            cf.setPricingEngine(mfCapFloorEngine2);
            final double mfPrice = cf.NPV();
            if (Math.abs(blackPrice - mfPrice) > tol1) {
                fail("Basket 2 / flat termstructures: Cap/Floor premium market ("
                        + blackPrice + ") does not match model premium ("
                        + mfPrice + ")");
            }
        }

        // Calibration Basket 1 / real yts, vts -------------------------------
        final IborIndex iborIndex3 = new Euribor(new Period(6, TimeUnit.Months), md0Yts_);
        final MarkovFunctional mf3 = new MarkovFunctional(
                md0Yts_, 0.01, volStepDates, vols, md0SwaptionVts_,
                expiriesCalBasket1(referenceDate), tenorsCalBasket1(),
                swapIndexBase,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(64)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(32)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withSmileMoneynessCheckpoints(money));
        final PricingEngine mfSwaptionEngine3 = new Gaussian1dSwaptionEngine(mf3, 64, 7.0);
        final PricingEngine blackSwaptionEngine3 = new BlackSwaptionEngine(
                md0Yts_, md0SwaptionVts_);
        final MarkovFunctional.ModelOutputs outputs3 = mf3.modelOutputs();

        for (int i = 0; i < outputs3.expiries_.size(); i++) {
            for (int j = 0; j < outputs3.smileStrikes_.get(0).size(); j++) {
                final double strike = outputs3.smileStrikes_.get(i).get(j);
                final VanillaSwap underlyingCall = new MakeVanillaSwap(
                        outputs3.tenors_.get(i), iborIndex3, strike)
                        .withEffectiveDate(target.advance(
                                outputs3.expiries_.get(i), 2, TimeUnit.Days))
                        .receiveFixed(false).value();
                final VanillaSwap underlyingPut = new MakeVanillaSwap(
                        outputs3.tenors_.get(i), iborIndex3, strike)
                        .withEffectiveDate(target.advance(
                                outputs3.expiries_.get(i), 2, TimeUnit.Days))
                        .receiveFixed(true).value();
                final Exercise exercise = new EuropeanExercise(outputs3.expiries_.get(i));
                final Swaption swaptionC = new Swaption(underlyingCall, exercise);
                final Swaption swaptionP = new Swaption(underlyingPut, exercise);
                swaptionC.setPricingEngine(blackSwaptionEngine3);
                swaptionP.setPricingEngine(blackSwaptionEngine3);
                final double blackPriceCall = swaptionC.NPV();
                final double blackPricePut = swaptionP.NPV();
                swaptionC.setPricingEngine(mfSwaptionEngine3);
                swaptionP.setPricingEngine(mfSwaptionEngine3);
                final double mfPriceCall = swaptionC.NPV();
                final double mfPricePut = swaptionP.NPV();
                final double smileCorrectionCall =
                        outputs3.marketCallPremium_.get(i).get(j)
                                - outputs3.marketRawCallPremium_.get(i).get(j);
                final double smileCorrectionPut =
                        outputs3.marketPutPremium_.get(i).get(j)
                                - outputs3.marketRawPutPremium_.get(i).get(j);
                if (Math.abs(blackPriceCall - mfPriceCall + smileCorrectionCall) > tol1) {
                    fail("Basket 1 / real termstructures: Call premium market ("
                            + blackPriceCall + ") does not match model premium ("
                            + mfPriceCall + ")");
                }
                if (Math.abs(blackPricePut - mfPricePut + smileCorrectionPut) > tol1) {
                    fail("Basket 1 / real termstructures: Put premium market ("
                            + blackPricePut + ") does not match model premium ("
                            + mfPricePut + ")");
                }
            }
        }

        // Calibration Basket 2 / real yts, vts -------------------------------
        final IborIndex iborIndex4 = new Euribor(new Period(6, TimeUnit.Months), md0Yts_);
        final MarkovFunctional mf4 = new MarkovFunctional(
                md0Yts_, 0.01, volStepDates, vols, md0OptionletVts_,
                expiriesCalBasket2(referenceDate), iborIndex4,
                new MarkovFunctional.ModelSettings()
                        .withYGridPoints(64)
                        .withYStdDevs(7.0)
                        .withGaussHermitePoints(32)
                        .withDigitalGap(1e-5)
                        .withMarketRateAccuracy(1e-7)
                        .withLowerRateBound(0.0)
                        .withUpperRateBound(2.0)
                        .withSmileMoneynessCheckpoints(money));
        mf4.modelOutputs(); // touch — parity with C++

        final PricingEngine blackCapFloorEngine4 = new BlackCapFloorEngine(
                md0Yts_, md0OptionletVts_);
        final PricingEngine mfCapFloorEngine4 = new Gaussian1dCapFloorEngine(mf4, 64, 7.0);

        // C++ excludes strike 0.10 because the caplet stripper fails for it.
        final double[] strikesBasket2Real = new double[]{
                0.01, 0.02, 0.03, 0.04, 0.05, 0.06};
        final List<CapFloor> c4 = new ArrayList<>();
        for (final double s : strikesBasket2Real) {
            c4.add(new MakeCapFloor(CapFloor.Type.Cap,
                    new Period(5, TimeUnit.Years), iborIndex4, s).value());
        }
        for (final double s : strikesBasket2Real) {
            c4.add(new MakeCapFloor(CapFloor.Type.Floor,
                    new Period(5, TimeUnit.Years), iborIndex4, s).value());
        }
        for (final CapFloor cf : c4) {
            cf.setPricingEngine(blackCapFloorEngine4);
            final double blackPrice = cf.NPV();
            cf.setPricingEngine(mfCapFloorEngine4);
            final double mfPrice = cf.NPV();
            if (Math.abs(blackPrice - mfPrice) > tol1) {
                fail("Basket 2 / real termstructures: Cap/Floor premium market ("
                        + blackPrice + ") does not match model premium ("
                        + mfPrice + ")");
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Fixtures: md0Yts, md0SwaptionVts, calibration baskets — direct ports
    //  of markovfunctional.cpp helper functions (lines 65-505).
    // -----------------------------------------------------------------------

    /**
     * Java port of C++ {@code md0Yts()} (markovfunctional.cpp:95-167).
     * Builds the EUR 6m discounting curve as of 14.11.2012 from 10 deposits,
     * 15 FRAs and 35 swaps (skipping 6/12/18-month overlaps with FRAs at the
     * swap side, skipping 6/12/18-month at the FRA side too). Uses
     * {@code PiecewiseYieldCurve<Discount, LogLinear>} per the C++ source.
     */
    private static Handle<YieldTermStructure> md0Yts() {
        final IborIndex euribor6mEmpty = new Euribor6M();

        final List<Handle<Quote>> q6m = new ArrayList<>();

        final double[] q6mh = new double[]{
                0.0001,  0.0001,  0.0001,  0.0003,  0.00055, 0.0009,
                0.0014,  0.0019,  0.0025,  0.0031,  0.00325, 0.00313,
                0.0031,  0.00307, 0.00309, 0.00339, 0.00316, 0.00326,
                0.00335, 0.00343, 0.00358, 0.00351, 0.00388, 0.00404,
                0.00425, 0.00442, 0.00462, 0.00386, 0.00491, 0.00647,
                0.00837, 0.01033, 0.01218, 0.01382, 0.01527, 0.01654,
                0.0177,  0.01872, 0.01959, 0.0203,  0.02088, 0.02132,
                0.02164, 0.02186, 0.02202, 0.02213, 0.02222, 0.02229,
                0.02234, 0.02238, 0.02241, 0.02243, 0.02244, 0.02245,
                0.02247, 0.0225,  0.02284, 0.02336, 0.02407, 0.0245};

        final Period[] q6mh1 = new Period[]{
                new Period(1, TimeUnit.Days),
                new Period(1, TimeUnit.Days),
                new Period(1, TimeUnit.Days),
                new Period(1, TimeUnit.Weeks),
                new Period(1, TimeUnit.Months),
                new Period(2, TimeUnit.Months),
                new Period(3, TimeUnit.Months),
                new Period(4, TimeUnit.Months),
                new Period(5, TimeUnit.Months),
                new Period(6, TimeUnit.Months)};

        final Period[] q6mh2 = new Period[]{
                new Period(7, TimeUnit.Months),  new Period(8, TimeUnit.Months),
                new Period(9, TimeUnit.Months),  new Period(10, TimeUnit.Months),
                new Period(11, TimeUnit.Months), new Period(1, TimeUnit.Years),
                new Period(13, TimeUnit.Months), new Period(14, TimeUnit.Months),
                new Period(15, TimeUnit.Months), new Period(16, TimeUnit.Months),
                new Period(17, TimeUnit.Months), new Period(18, TimeUnit.Months),
                new Period(19, TimeUnit.Months), new Period(20, TimeUnit.Months),
                new Period(21, TimeUnit.Months), new Period(22, TimeUnit.Months),
                new Period(23, TimeUnit.Months), new Period(2, TimeUnit.Years),
                new Period(3, TimeUnit.Years),   new Period(4, TimeUnit.Years),
                new Period(5, TimeUnit.Years),   new Period(6, TimeUnit.Years),
                new Period(7, TimeUnit.Years),   new Period(8, TimeUnit.Years),
                new Period(9, TimeUnit.Years),   new Period(10, TimeUnit.Years),
                new Period(11, TimeUnit.Years),  new Period(12, TimeUnit.Years),
                new Period(13, TimeUnit.Years),  new Period(14, TimeUnit.Years),
                new Period(15, TimeUnit.Years),  new Period(16, TimeUnit.Years),
                new Period(17, TimeUnit.Years),  new Period(18, TimeUnit.Years),
                new Period(19, TimeUnit.Years),  new Period(20, TimeUnit.Years),
                new Period(21, TimeUnit.Years),  new Period(22, TimeUnit.Years),
                new Period(23, TimeUnit.Years),  new Period(24, TimeUnit.Years),
                new Period(25, TimeUnit.Years),  new Period(26, TimeUnit.Years),
                new Period(27, TimeUnit.Years),  new Period(28, TimeUnit.Years),
                new Period(29, TimeUnit.Years),  new Period(30, TimeUnit.Years),
                new Period(35, TimeUnit.Years),  new Period(40, TimeUnit.Years),
                new Period(50, TimeUnit.Years),  new Period(60, TimeUnit.Years)};

        for (final double v : q6mh) {
            q6m.add(new Handle<Quote>(new SimpleQuote(v)));
        }

        final List<RateHelper> r6m = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            r6m.add(new DepositRateHelper(
                    q6m.get(i), q6mh1[i],
                    i < 2 ? i : 2, new Target(),
                    BusinessDayConvention.ModifiedFollowing, false, new Actual360()));
        }
        for (int i = 0; i < 18; i++) {
            if (i + 1 != 6 && i + 1 != 12 && i + 1 != 18) {
                r6m.add(new FraRateHelper(
                        q6m.get(10 + i), i + 1, i + 7, 2, new Target(),
                        BusinessDayConvention.ModifiedFollowing, false, new Actual360()));
            }
        }
        for (int i = 0; i < 15 + 35; i++) {
            if (i + 7 == 12 || i + 7 == 18 || i + 7 >= 24) {
                r6m.add(new SwapRateHelper(
                        q6m.get(10 + i), q6mh2[i], new Target(), Frequency.Annual,
                        BusinessDayConvention.ModifiedFollowing, new Actual360(),
                        euribor6mEmpty));
            }
        }

        final RateHelper[] helperArray = r6m.toArray(new RateHelper[0]);
        final PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap> curve =
                new PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap>(
                        Discount.class, LogLinear.class, IterativeBootstrap.class,
                        0, new Target(), helperArray, new Actual365Fixed());
        curve.enableExtrapolation();

        return new Handle<YieldTermStructure>(curve);
    }

    /**
     * Java port of C++ {@code md0SwaptionVts()} (markovfunctional.cpp:172-351).
     * Builds the EUR swaption ATM vol matrix (20×14 grid) wrapped in a
     * {@link SabrSwaptionVolatilityCube} (smile cube on 6×5 sub-grid × 9
     * strike spreads). The data corresponds to a 14.11.2012 market snapshot;
     * the C++ deliberately passes a large maxErrorTolerance (0.0050) to get a
     * smooth fitted cube for testing rather than a tight calibration.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Handle<SwaptionVolatilityStructure> md0SwaptionVts() {
        final List<Period> optionTenors = new ArrayList<>();
        for (final Period p : new Period[]{
                new Period(1, TimeUnit.Months),  new Period(2, TimeUnit.Months),
                new Period(3, TimeUnit.Months),  new Period(6, TimeUnit.Months),
                new Period(9, TimeUnit.Months),  new Period(1, TimeUnit.Years),
                new Period(18, TimeUnit.Months), new Period(2, TimeUnit.Years),
                new Period(3, TimeUnit.Years),   new Period(4, TimeUnit.Years),
                new Period(5, TimeUnit.Years),   new Period(6, TimeUnit.Years),
                new Period(7, TimeUnit.Years),   new Period(8, TimeUnit.Years),
                new Period(9, TimeUnit.Years),   new Period(10, TimeUnit.Years),
                new Period(15, TimeUnit.Years),  new Period(20, TimeUnit.Years),
                new Period(25, TimeUnit.Years),  new Period(30, TimeUnit.Years)}) {
            optionTenors.add(p);
        }

        final List<Period> swapTenors = new ArrayList<>();
        for (final Period p : new Period[]{
                new Period(1, TimeUnit.Years),  new Period(2, TimeUnit.Years),
                new Period(3, TimeUnit.Years),  new Period(4, TimeUnit.Years),
                new Period(5, TimeUnit.Years),  new Period(6, TimeUnit.Years),
                new Period(7, TimeUnit.Years),  new Period(8, TimeUnit.Years),
                new Period(9, TimeUnit.Years),  new Period(10, TimeUnit.Years),
                new Period(15, TimeUnit.Years), new Period(20, TimeUnit.Years),
                new Period(25, TimeUnit.Years), new Period(30, TimeUnit.Years)}) {
            swapTenors.add(p);
        }

        final double[] qSwAtmh = new double[]{
                1.81,  0.897, 0.819, 0.692, 0.551, 0.47,  0.416, 0.379, 0.357,
                0.335, 0.283, 0.279, 0.283, 0.287, 1.717, 0.886, 0.79,  0.69,
                0.562, 0.481, 0.425, 0.386, 0.359, 0.339, 0.29,  0.287, 0.292,
                0.296, 1.762, 0.903, 0.804, 0.693, 0.582, 0.5,   0.448, 0.411,
                0.387, 0.365, 0.31,  0.307, 0.312, 0.317, 1.662, 0.882, 0.764,
                0.67,  0.586, 0.513, 0.468, 0.432, 0.408, 0.388, 0.331, 0.325,
                0.33,  0.334, 1.53,  0.854, 0.728, 0.643, 0.565, 0.503, 0.464,
                0.435, 0.415, 0.393, 0.337, 0.33,  0.333, 0.338, 1.344, 0.786,
                0.683, 0.609, 0.54,  0.488, 0.453, 0.429, 0.411, 0.39,  0.335,
                0.329, 0.332, 0.336, 1.1,   0.711, 0.617, 0.548, 0.497, 0.456,
                0.43,  0.408, 0.392, 0.374, 0.328, 0.323, 0.326, 0.33,  0.956,
                0.638, 0.553, 0.496, 0.459, 0.427, 0.403, 0.385, 0.371, 0.359,
                0.321, 0.318, 0.323, 0.327, 0.671, 0.505, 0.45,  0.42,  0.397,
                0.375, 0.36,  0.347, 0.337, 0.329, 0.305, 0.303, 0.309, 0.313,
                0.497, 0.406, 0.378, 0.36,  0.348, 0.334, 0.323, 0.315, 0.309,
                0.304, 0.289, 0.289, 0.294, 0.297, 0.404, 0.352, 0.334, 0.322,
                0.313, 0.304, 0.296, 0.291, 0.288, 0.286, 0.278, 0.277, 0.281,
                0.282, 0.345, 0.312, 0.302, 0.294, 0.286, 0.28,  0.276, 0.274,
                0.273, 0.273, 0.267, 0.265, 0.268, 0.269, 0.305, 0.285, 0.277,
                0.271, 0.266, 0.262, 0.26,  0.259, 0.26,  0.262, 0.259, 0.256,
                0.257, 0.256, 0.282, 0.265, 0.259, 0.254, 0.251, 0.25,  0.25,
                0.251, 0.253, 0.256, 0.253, 0.25,  0.249, 0.246, 0.263, 0.248,
                0.244, 0.241, 0.24,  0.24,  0.242, 0.245, 0.249, 0.252, 0.249,
                0.245, 0.243, 0.238, 0.242, 0.234, 0.232, 0.232, 0.233, 0.235,
                0.239, 0.243, 0.247, 0.249, 0.246, 0.242, 0.237, 0.231, 0.233,
                0.234, 0.241, 0.246, 0.249, 0.253, 0.257, 0.261, 0.263, 0.264,
                0.251, 0.236, 0.222, 0.214, 0.262, 0.26,  0.262, 0.263, 0.263,
                0.266, 0.268, 0.269, 0.269, 0.265, 0.237, 0.214, 0.202, 0.196,
                0.26,  0.26,  0.261, 0.261, 0.258, 0.255, 0.252, 0.248, 0.245,
                0.24,  0.207, 0.187, 0.182, 0.176, 0.236, 0.223, 0.221, 0.218,
                0.214, 0.21,  0.207, 0.204, 0.202, 0.2,   0.175, 0.167, 0.163,
                0.158};

        final List<List<Handle<? extends Quote>>> qSwAtm =
                new ArrayList<List<Handle<? extends Quote>>>();
        for (int i = 0; i < 20; i++) {
            final List<Handle<? extends Quote>> row =
                    new ArrayList<>();
            for (int j = 0; j < 14; j++) {
                row.add(new Handle<Quote>(new SimpleQuote(qSwAtmh[i * 14 + j])));
            }
            qSwAtm.add(row);
        }

        final SwaptionVolatilityMatrix swaptionVolAtmRaw = new SwaptionVolatilityMatrix(
                new Target(), BusinessDayConvention.ModifiedFollowing,
                optionTenors, swapTenors, qSwAtm, new Actual365Fixed(),
                false, VolatilityType.ShiftedLognormal, null);
        final Handle<SwaptionVolatilityStructure> swaptionVolAtm =
                new Handle<SwaptionVolatilityStructure>(swaptionVolAtmRaw);

        final List<Period> optionTenorsSmile = new ArrayList<>();
        for (final Period p : new Period[]{
                new Period(3, TimeUnit.Months),  new Period(1, TimeUnit.Years),
                new Period(5, TimeUnit.Years),   new Period(10, TimeUnit.Years),
                new Period(20, TimeUnit.Years),  new Period(30, TimeUnit.Years)}) {
            optionTenorsSmile.add(p);
        }
        final List<Period> swapTenorsSmile = new ArrayList<>();
        for (final Period p : new Period[]{
                new Period(2, TimeUnit.Years),  new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years), new Period(20, TimeUnit.Years),
                new Period(30, TimeUnit.Years)}) {
            swapTenorsSmile.add(p);
        }
        final List<Double> strikeSpreads = new ArrayList<>();
        for (final double d : new double[]{-0.02, -0.01, -0.0050, -0.0025, 0.0,
                0.0025, 0.0050, 0.01, 0.02}) {
            strikeSpreads.add(d);
        }

        final double[] qSwSmileh = new double[]{
                2.2562,  2.2562,  2.2562,  0.1851,  0.0,     -0.0389, -0.0507,
                -0.0571, -0.06,   14.9619, 14.9619, 0.1249,  0.0328,  0.0,
                -0.0075, -0.005,  0.0078,  0.0328,  0.2296,  0.2296,  0.0717,
                0.0267,  0.0,     -0.0115, -0.0126, -0.0002, 0.0345,  0.6665,
                0.1607,  0.0593,  0.0245,  0.0,     -0.0145, -0.0204, -0.0164,
                0.0102,  0.6509,  0.1649,  0.0632,  0.027,   0.0,     -0.018,
                -0.0278, -0.0303, -0.0105, 0.6303,  0.6303,  0.6303,  0.1169,
                0.0,     -0.0469, -0.0699, -0.091,  -0.1065, 0.4437,  0.4437,
                0.0944,  0.0348,  0.0,     -0.0206, -0.0327, -0.0439, -0.0472,
                2.1557,  0.1501,  0.0531,  0.0225,  0.0,     -0.0161, -0.0272,
                -0.0391, -0.0429, 0.4365,  0.1077,  0.0414,  0.0181,  0.0,
                -0.0137, -0.0237, -0.0354, -0.0401, 0.4415,  0.1117,  0.0437,
                0.0193,  0.0,     -0.015,  -0.0264, -0.0407, -0.0491, 0.4301,
                0.0776,  0.0283,  0.0122,  0.0,     -0.0094, -0.0165, -0.0262,
                -0.035,  0.2496,  0.0637,  0.0246,  0.0109,  0.0,     -0.0086,
                -0.0153, -0.0247, -0.0334, 0.1912,  0.0569,  0.023,   0.0104,
                0.0,     -0.0085, -0.0155, -0.0256, -0.0361, 0.2095,  0.06,
                0.0239,  0.0107,  0.0,     -0.0087, -0.0156, -0.0254, -0.0348,
                0.2357,  0.0669,  0.0267,  0.012,   0.0,     -0.0097, -0.0174,
                -0.0282, -0.0383, 0.1291,  0.0397,  0.0158,  0.007,   0.0,
                -0.0056, -0.01,   -0.0158, -0.0203, 0.1281,  0.0397,  0.0159,
                0.0071,  0.0,     -0.0057, -0.0102, -0.0164, -0.0215, 0.1547,
                0.0468,  0.0189,  0.0085,  0.0,     -0.0069, -0.0125, -0.0205,
                -0.0283, 0.1851,  0.0522,  0.0208,  0.0093,  0.0,     -0.0075,
                -0.0135, -0.0221, -0.0304, 0.1782,  0.0506,  0.02,    0.0089,
                0.0,     -0.0071, -0.0128, -0.0206, -0.0276, 0.2665,  0.0654,
                0.0255,  0.0113,  0.0,     -0.0091, -0.0163, -0.0265, -0.0367,
                0.2873,  0.0686,  0.0269,  0.0121,  0.0,     -0.0098, -0.0179,
                -0.0298, -0.043,  0.2993,  0.0688,  0.0273,  0.0123,  0.0,
                -0.0103, -0.0189, -0.0324, -0.0494, 0.1869,  0.0501,  0.0202,
                0.0091,  0.0,     -0.0076, -0.014,  -0.0239, -0.0358, 0.1573,
                0.0441,  0.0178,  0.008,   0.0,     -0.0066, -0.0121, -0.0202,
                -0.0294, 0.196,   0.0525,  0.0204,  0.009,   0.0,     -0.0071,
                -0.0125, -0.0197, -0.0253, 0.1795,  0.0497,  0.0197,  0.0088,
                0.0,     -0.0071, -0.0128, -0.0208, -0.0286, 0.1401,  0.0415,
                0.0171,  0.0078,  0.0,     -0.0066, -0.0122, -0.0209, -0.0318,
                0.112,   0.0344,  0.0142,  0.0065,  0.0,     -0.0055, -0.01,
                -0.0171, -0.0256, 0.1077,  0.0328,  0.0134,  0.0061,  0.0,
                -0.005,  -0.0091, -0.0152, -0.0216};

        final List<List<Handle<Quote>>> qSwSmile = new ArrayList<List<Handle<Quote>>>();
        for (int i = 0; i < 30; i++) {
            final List<Handle<Quote>> row = new ArrayList<>();
            for (int j = 0; j < 9; j++) {
                row.add(new Handle<Quote>(new SimpleQuote(qSwSmileh[i * 9 + j])));
            }
            qSwSmile.add(row);
        }

        final double[] qSwSmileh1 = new double[]{
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2,
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2,
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2,
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2,
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2,
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2,
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2,
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2,
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2,
                0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2, 0.01, 0.2, 0.8, -0.2};

        final boolean[] parameterFixed = new boolean[]{false, false, false, false};

        final List<List<Handle<Quote>>> parameterGuess =
                new ArrayList<List<Handle<Quote>>>();
        for (int i = 0; i < 30; i++) {
            final List<Handle<Quote>> row = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                row.add(new Handle<Quote>(new SimpleQuote(qSwSmileh1[i * 4 + j])));
            }
            parameterGuess.add(row);
        }

        final EndCriteria ec = new EndCriteria(50000, 250, 1E-6, 1E-6, 1E-6);

        final SwapIndex swapIndex = new EuriborSwapIsdaFixA(
                new Period(30, TimeUnit.Years), md0Yts());
        final SwapIndex shortSwapIndex = new EuriborSwapIsdaFixA(
                new Period(1, TimeUnit.Years), md0Yts());

        // 19-arg ctor: maxErrorTolerance=0.0050 per C++ (big tolerance — we
        // just want a smooth cube). optMethod/errorAccept/useMaxError/
        // maxGuesses/backwardFlat/cutoffStrike: take Java defaults that match
        // the convenience-ctor wiring.
        final SabrSwaptionVolatilityCube cube = new SabrSwaptionVolatilityCube(
                swaptionVolAtm, optionTenorsSmile, swapTenorsSmile,
                strikeSpreads, qSwSmile, swapIndex, shortSwapIndex, true,
                parameterGuess, parameterFixed, true, ec,
                0.0050 /* maxErrorTolerance */, (OptimizationMethod) null,
                Double.NaN /* errorAccept -> defaults to maxErr/5 */,
                false /* useMaxError */, 50 /* maxGuesses */,
                false /* backwardFlat */, 0.0001 /* cutoffStrike */);

        final Handle<SwaptionVolatilityStructure> res =
                new Handle<SwaptionVolatilityStructure>(cube);
        res.currentLink().enableExtrapolation();
        return res;
    }

    /**
     * Java port of C++ {@code expiriesCalBasket3()} (markovfunctional.cpp:481-498).
     * Coterminal 10y basket: 9 yearly fixings 1y..9y from reference date.
     */
    private static List<Date> expiriesCalBasket3(final Date referenceDate) {
        final List<Date> res = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            res.add(new Target().advance(referenceDate, new Period(i, TimeUnit.Years)));
        }
        return res;
    }

    /**
     * Java port of C++ {@code tenorsCalBasket3()} (markovfunctional.cpp:500-505).
     * Decreasing tenors 9y..1y (coterminal with 10y).
     */
    private static List<Period> tenorsCalBasket3() {
        final List<Period> res = new ArrayList<>();
        for (final int n : new int[]{9, 8, 7, 6, 5, 4, 3, 2, 1}) {
            res.add(new Period(n, TimeUnit.Years));
        }
        return res;
    }

    /**
     * Java port of C++ {@code flatYts()} (markovfunctional.cpp:65-71).
     * Flat 3% yield term structure on TARGET / Actual365Fixed.
     */
    private static Handle<YieldTermStructure> flatYts() {
        return new Handle<YieldTermStructure>(new FlatForward(
                0, new Target(), 0.03, new Actual365Fixed()));
    }

    /**
     * Java port of C++ {@code flatSwaptionVts()} (markovfunctional.cpp:73-81).
     * Flat 20% (shifted-lognormal) ATM swaption vol on TARGET / ModifiedFollowing.
     */
    private static Handle<SwaptionVolatilityStructure> flatSwaptionVts() {
        return new Handle<SwaptionVolatilityStructure>(
                new ConstantSwaptionVolatility(
                        0, new Target(),
                        BusinessDayConvention.ModifiedFollowing,
                        0.20, new Actual365Fixed()));
    }

    /**
     * Java port of C++ {@code flatOptionletVts()} (markovfunctional.cpp:83-91).
     * Flat 20% optionlet vol on TARGET / ModifiedFollowing.
     */
    private static Handle<OptionletVolatilityStructure> flatOptionletVts() {
        return new Handle<OptionletVolatilityStructure>(
                new ConstantOptionletVolatility(
                        0, new Target(),
                        BusinessDayConvention.ModifiedFollowing,
                        0.20, new Actual365Fixed()));
    }

    /**
     * Java port of C++ {@code expiriesCalBasket1()} (markovfunctional.cpp:439-448).
     * 5 yearly expiries 1y..5y from the reference date — CMS10y swaption basket.
     */
    private static List<Date> expiriesCalBasket1(final Date referenceDate) {
        final List<Date> res = new ArrayList<>();
        final Target t = new Target();
        for (int i = 1; i <= 5; i++) {
            res.add(t.advance(referenceDate, new Period(i, TimeUnit.Years)));
        }
        return res;
    }

    /**
     * Java port of C++ {@code tenorsCalBasket1()} (markovfunctional.cpp:450-455).
     * 5 × 10y constant tenors — CMS10y swaption basket.
     */
    private static List<Period> tenorsCalBasket1() {
        final List<Period> res = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            res.add(new Period(10, TimeUnit.Years));
        }
        return res;
    }

    /**
     * Java port of C++ {@code expiriesCalBasket2()} (markovfunctional.cpp:459-477).
     * 10 semi-annual expiries 6m..60m from the reference date — 6m caplet basket.
     */
    private static List<Date> expiriesCalBasket2(final Date referenceDate) {
        final List<Date> res = new ArrayList<>();
        final Target t = new Target();
        for (int n : new int[]{6, 12, 18, 24, 30, 36, 42, 48, 54, 60}) {
            res.add(t.advance(referenceDate, new Period(n, TimeUnit.Months)));
        }
        return res;
    }

    /**
     * Java port of C++ {@code md0OptionletVts()} (markovfunctional.cpp:356-435).
     * Builds the EUR 6m caplet vol surface as of 14.11.2012 from a 16×12
     * (option tenors × strikes) cap vol matrix wrapped in a
     * {@link CapFloorTermVolSurface}, stripped via {@link OptionletStripper1}
     * driven by a {@link Euribor 6m} index off {@link #md0Yts()}, and exposed
     * to consumers via a {@link StrippedOptionletAdapter}. The last strike
     * (10%) is intentionally dropped because it causes bootstrap exceptions
     * (C++ inline comment, line 365-366).
     */
    private static Handle<OptionletVolatilityStructure> md0OptionletVts() {
        final int nOptTen = 16;
        final int nStrikes = 12;

        final List<Period> optionTenors = new ArrayList<>();
        for (final Period p : new Period[]{
                new Period(1, TimeUnit.Years),  new Period(18, TimeUnit.Months),
                new Period(2, TimeUnit.Years),  new Period(3, TimeUnit.Years),
                new Period(4, TimeUnit.Years),  new Period(5, TimeUnit.Years),
                new Period(6, TimeUnit.Years),  new Period(7, TimeUnit.Years),
                new Period(8, TimeUnit.Years),  new Period(9, TimeUnit.Years),
                new Period(10, TimeUnit.Years), new Period(12, TimeUnit.Years),
                new Period(15, TimeUnit.Years), new Period(20, TimeUnit.Years),
                new Period(25, TimeUnit.Years), new Period(30, TimeUnit.Years)}) {
            optionTenors.add(p);
        }

        final double[] strikes = new double[]{
                0.0025, 0.0050, 0.0100, 0.0150, 0.0200, 0.0225,
                0.0250, 0.0300, 0.0350, 0.0400, 0.0500, 0.0600};

        // C++ stores the matrix transposed: volsa[strike][optTen]; we then
        // copy into vols[optTen][strike]. We mirror the literal C++ layout
        // (13 strike rows × 16 expiries) and then drop the 13th row since
        // we only ship 12 strikes — but the C++ literally allocates 13 rows
        // and only fills the first 12 via the i<nStrikes loop. To match
        // C++ bit-for-bit we replicate the same 12-row sub-view.
        final double[][] volsa = new double[][]{
                {1.3378, 1.3032, 1.2514, 1.081,  1.019,  0.961,
                 0.907,  0.862,  0.822,  0.788,  0.758,  0.709,
                 0.66,   0.619,  0.597,  0.579},
                {1.1882, 1.1057, 0.9823, 0.879,  0.828,  0.779,
                 0.736,  0.7,    0.67,   0.644,  0.621,  0.582,
                 0.544,  0.513,  0.496,  0.482},
                {1.1646, 1.0356, 0.857,  0.742,  0.682,  0.626,
                 0.585,  0.553,  0.527,  0.506,  0.488,  0.459,
                 0.43,   0.408,  0.396,  0.386},
                {1.1932, 1.0364, 0.8291, 0.691,  0.618,  0.553,
                 0.509,  0.477,  0.452,  0.433,  0.417,  0.391,
                 0.367,  0.35,   0.342,  0.335},
                {1.2233, 1.0489, 0.8268, 0.666,  0.582,  0.51,
                 0.463,  0.43,   0.405,  0.387,  0.372,  0.348,
                 0.326,  0.312,  0.306,  0.301},
                {1.2369, 1.0555, 0.8283, 0.659,  0.57,   0.495,
                 0.447,  0.414,  0.388,  0.37,   0.355,  0.331,
                 0.31,   0.298,  0.293,  0.289},
                {1.2498, 1.0622, 0.8307, 0.653,  0.56,   0.483,
                 0.434,  0.4,    0.374,  0.356,  0.341,  0.318,
                 0.297,  0.286,  0.282,  0.279},
                {1.2719, 1.0747, 0.8368, 0.646,  0.546,  0.465,
                 0.415,  0.38,   0.353,  0.335,  0.32,   0.296,
                 0.277,  0.268,  0.265,  0.263},
                {1.2905, 1.0858, 0.8438, 0.643,  0.536,  0.453,
                 0.403,  0.367,  0.339,  0.32,   0.305,  0.281,
                 0.262,  0.255,  0.254,  0.252},
                {1.3063, 1.0953, 0.8508, 0.642,  0.53,   0.445,
                 0.395,  0.358,  0.329,  0.31,   0.294,  0.271,
                 0.252,  0.246,  0.246,  0.244},
                {1.332,  1.1108, 0.8631, 0.642,  0.521,  0.436,
                 0.386,  0.348,  0.319,  0.298,  0.282,  0.258,
                 0.24,   0.237,  0.237,  0.236},
                {1.3513, 1.1226, 0.8732, 0.645,  0.517,  0.43,
                 0.381,  0.344,  0.314,  0.293,  0.277,  0.252,
                 0.235,  0.233,  0.234,  0.233}};

        final Matrix vols = new Matrix(nOptTen, nStrikes);
        for (int i = 0; i < nStrikes; i++) {
            for (int j = 0; j < nOptTen; j++) {
                vols.set(j, i, volsa[i][j]);
            }
        }

        final IborIndex iborIndex = new Euribor(new Period(6, TimeUnit.Months), md0Yts());
        final CapFloorTermVolSurface cf = new CapFloorTermVolSurface(
                0, new Target(), BusinessDayConvention.ModifiedFollowing,
                optionTenors, strikes, vols, new Actual365Fixed());
        final OptionletStripper stripper = new OptionletStripper1(cf, iborIndex);

        return new Handle<OptionletVolatilityStructure>(
                new StrippedOptionletAdapter(stripper));
    }
}
