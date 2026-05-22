/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for MultiplicativePriceSeasonality + KerkhofSeasonality
 against QuantLib v1.42.1 via
 migration-harness/references/termstructures/inflation/seasonality.json
 (Phase 2q L1 Track C — Seasonality).
*/
package org.jquantlib.testsuite.termstructures.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve;
import org.jquantlib.termstructures.inflation.InterpolatedZeroInflationCurve;
import org.jquantlib.termstructures.inflation.KerkhofSeasonality;
import org.jquantlib.termstructures.inflation.MultiplicativePriceSeasonality;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link MultiplicativePriceSeasonality} and
 * {@link KerkhofSeasonality}.
 *
 * <p>Reproduces the C++ probe setup
 * (migration-harness/cpp/probes/termstructures/inflation/seasonality_probe.cpp):
 * 12 monthly factors with seasonalityBaseDate = Jan 1 2007, applied to the
 * same 6-pillar zero-inflation curve. Tier: TIGHT.
 */
public class SeasonalityTest {

    private static final String REF_GROUP = "termstructures/inflation/seasonality";

    @Test
    public void seasonality_matchesCpp() {
        // ---------- Match probe setup exactly ----------
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;

        final Date refDate = cal.adjust(evalDate, bdc);
        final Date[] nodeDates = new Date[] {
                new Date(1, Month.May,    2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] nodeRates = new double[] { 0.025, 0.030, 0.032, 0.034, 0.036, 0.038 };

        final var curveUnadjusted = new InterpolatedZeroInflationCurve<Linear>(Linear.class,
                        refDate, nodeDates, nodeRates, freq, dc);
        curveUnadjusted.enableExtrapolation();

        // Same monthly factors as the probe.
        final double[] factors = {
                1.0030, 1.0010, 1.0050, 1.0030, 1.0050, 1.0070,
                0.9990, 0.9990, 1.0050, 1.0030, 1.0030, 0.9970
        };
        final Date seasonalityBaseDate = new Date(1, Month.January, 2007);

        // Multiplicative seasonality
        final MultiplicativePriceSeasonality seasM =
                new MultiplicativePriceSeasonality(seasonalityBaseDate, Frequency.Monthly, factors);

        // Kerkhof seasonality
        final KerkhofSeasonality seasK =
                new KerkhofSeasonality(seasonalityBaseDate, factors);

        // Adjusted-curve overlays (one per seasonality class).
        final var curveSeasM = new InterpolatedZeroInflationCurve<Linear>(Linear.class,
                        refDate, nodeDates, nodeRates, freq, dc);
        curveSeasM.enableExtrapolation();
        curveSeasM.setSeasonality(seasM);

        final var curveSeasK = new InterpolatedZeroInflationCurve<Linear>(Linear.class,
                        refDate, nodeDates, nodeRates, freq, dc);
        curveSeasK.enableExtrapolation();
        curveSeasK.setSeasonality(seasK);

        // Phase 2q D.2: parallel YoY curve to validate the
        // InterpolatedYoYInflationCurve.yoyRate seasonality wiring.
        // Same nodes as the probe.
        final double[] yoyNodeRates = new double[]{
                0.025, 0.027, 0.029, 0.031, 0.034, 0.036
        };
        final var yoyCurveUnadjusted = new InterpolatedYoYInflationCurve<Linear>(Linear.class,
                        refDate, nodeDates, yoyNodeRates, freq, dc);
        yoyCurveUnadjusted.enableExtrapolation();

        final var yoyCurveSeasM = new InterpolatedYoYInflationCurve<Linear>(Linear.class,
                        refDate, nodeDates, yoyNodeRates, freq, dc);
        yoyCurveSeasM.enableExtrapolation();
        yoyCurveSeasM.setSeasonality(seasM);

        // ---------- Cross-validate every case ----------
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            try {
                checkCase(name, c, seasM, seasK,
                          curveUnadjusted, curveSeasM, curveSeasK,
                          yoyCurveUnadjusted, yoyCurveSeasM, mismatches);
            } catch (final Exception e) {
                mismatches.add(name + ": EXCEPTION " + e.getClass().getSimpleName()
                        + " " + e.getMessage());
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static void checkCase(final String name, final Case c,
                                  final MultiplicativePriceSeasonality seasM,
                                  final KerkhofSeasonality seasK,
                                  final InterpolatedZeroInflationCurve<Linear> curveUnadjusted,
                                  final InterpolatedZeroInflationCurve<Linear> curveSeasM,
                                  final InterpolatedZeroInflationCurve<Linear> curveSeasK,
                                  final InterpolatedYoYInflationCurve<Linear> yoyCurveUnadjusted,
                                  final InterpolatedYoYInflationCurve<Linear> yoyCurveSeasM,
                                  final List<String> mismatches) {
        final JSONObject expected = (JSONObject) c.expectedRaw();

        if (name.equals("M_factorCount")) {
            final long exp = expected.getLong("value");
            final long act = seasM.seasonalityFactors().length;
            if (!Tolerance.exact(act, exp)) {
                mismatches.add(name + ": expected=" + exp + " actual=" + act);
            }
        } else if (name.equals("M_baseDate_serial")) {
            final long exp = expected.getLong("value");
            final long act = seasM.seasonalityBaseDate().serialNumber();
            if (!Tolerance.exact(act, exp)) {
                mismatches.add(name + ": expected=" + exp + " actual=" + act);
            }
        } else if (name.startsWith("M_seasonalityFactor_")) {
            final double exp = expected.getDouble("value");
            final long ds = c.inputs().getLong("date_serial");
            final double act = seasM.seasonalityFactor(new Date(ds));
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name, exp, act));
            }
        } else if (name.startsWith("M_correctZeroRate_grid_")) {
            // Validate both the unadjusted (sanity) and adjusted rates.
            final double expUnadj = expected.getDouble("unadjusted");
            final double expAdj = expected.getDouble("adjusted");
            final long ds = c.inputs().getLong("date_serial");
            final Date d = new Date(ds);
            final double actUnadj = curveUnadjusted.zeroRate(d, true);
            if (!Tolerance.tight(actUnadj, expUnadj)) {
                mismatches.add(fmt(name + ".unadjusted", expUnadj, actUnadj));
            }
            final double actAdj = curveSeasM.zeroRate(d, true);
            if (!Tolerance.tight(actAdj, expAdj)) {
                mismatches.add(fmt(name + ".adjusted", expAdj, actAdj));
            }
        } else if (name.startsWith("M_correctYoYRate_curve_")) {
            // Phase 2q D.2: validates that
            // InterpolatedYoYInflationCurve.yoyRate(d, ext) applies seasonality
            // when one is installed via setSeasonality. With stationary
            // monthly factors, factor(d) / factor(d-1Y) is 1 to within
            // floating-point roundoff so the adjusted/unadjusted columns are
            // numerically identical — the test's value here is to confirm
            // that the wiring path is exercised without throwing and produces
            // a value matching the C++ reference at TIGHT tolerance.
            final double expUnadj = expected.getDouble("unadjusted");
            final double expAdj = expected.getDouble("adjusted");
            final long ds = c.inputs().getLong("date_serial");
            final Date d = new Date(ds);
            final double actUnadj = yoyCurveUnadjusted.yoyRate(d, true);
            if (!Tolerance.tight(actUnadj, expUnadj)) {
                mismatches.add(fmt(name + ".unadjusted", expUnadj, actUnadj));
            }
            final double actAdj = yoyCurveSeasM.yoyRate(d, true);
            if (!Tolerance.tight(actAdj, expAdj)) {
                mismatches.add(fmt(name + ".adjusted", expAdj, actAdj));
            }
        } else if (name.startsWith("M_correctYoYRate_")) {
            final double exp = expected.getDouble("value");
            final long ds = c.inputs().getLong("date_serial");
            final double inputRate = c.inputs().getDouble("input_rate");
            final double act = seasM.correctYoYRate(new Date(ds), inputRate, curveUnadjusted);
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name, exp, act));
            }
        } else if (name.equals("M_isConsistent")) {
            final boolean exp = expected.getBoolean("value");
            final boolean act = seasM.isConsistent(curveUnadjusted);
            if (exp != act) {
                mismatches.add(name + ": expected=" + exp + " actual=" + act);
            }
        } else if (name.startsWith("K_seasonalityFactor_")) {
            final double exp = expected.getDouble("value");
            final long ds = c.inputs().getLong("date_serial");
            final double act = seasK.seasonalityFactor(new Date(ds));
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name, exp, act));
            }
        } else if (name.startsWith("K_correctZeroRate_grid_")) {
            final double expUnadj = expected.getDouble("unadjusted");
            final double expAdj = expected.getDouble("adjusted");
            final long ds = c.inputs().getLong("date_serial");
            final Date d = new Date(ds);
            final double actUnadj = curveUnadjusted.zeroRate(d, true);
            if (!Tolerance.tight(actUnadj, expUnadj)) {
                mismatches.add(fmt(name + ".unadjusted", expUnadj, actUnadj));
            }
            final double actAdj = curveSeasK.zeroRate(d, true);
            if (!Tolerance.tight(actAdj, expAdj)) {
                mismatches.add(fmt(name + ".adjusted", expAdj, actAdj));
            }
        } else {
            mismatches.add(name + ": UNRECOGNIZED case");
        }
    }

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }
}
