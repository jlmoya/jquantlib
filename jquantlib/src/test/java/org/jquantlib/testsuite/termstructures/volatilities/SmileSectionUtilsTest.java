/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for SmileSectionUtils against QuantLib v1.42.1 via
 migration-harness/references/termstructures/volatility/smile_section_utils.json.
 Phase 2j WI-4.0b.
*/
package org.jquantlib.testsuite.termstructures.volatilities;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.math.Constants;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSectionUtils;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link SmileSectionUtils}: moneyGrid, strikeGrid,
 * callPrices, atmLevel, arbitragefreeRegion, arbitragefreeIndices —
 * all at TIGHT tier.
 *
 * <p>Six scenario groups driven by reference JSON:
 * <ul>
 *   <li>flat_lognorm_default — FlatSmileSection, ShiftedLognormal, default grid</li>
 *   <li>flat_lognorm_custom — FlatSmileSection, ShiftedLognormal, custom grid</li>
 *   <li>flat_lognorm_atm_override — FlatSmileSection, explicit ATM override</li>
 *   <li>flat_normal_default — FlatSmileSection, Normal, default grid</li>
 *   <li>flat_shiftedlognorm — FlatSmileSection, ShiftedLognormal with shift=0.02</li>
 *   <li>flat_deleteArb — FlatSmileSection, deleteArbitragePoints=true</li>
 * </ul>
 */
public class SmileSectionUtilsTest {

    private static final String REF_GROUP = "termstructures/volatility/smile_section_utils";
    private static final Actual365Fixed DC = new Actual365Fixed();

    // -----------------------------------------------------------------------
    // Section builders — must exactly match probe parameters
    // -----------------------------------------------------------------------

    private static SmileSectionUtils buildFlatLognormDefault() {
        // ATM=0.05, vol=0.20, T=1.0, shift=0 (ShiftedLognormal), default moneyness grid
        final FlatSmileSection sec = new FlatSmileSection(1.0, 0.20, DC, 0.05);
        return new SmileSectionUtils(sec, new double[0], Constants.NULL_REAL, false);
    }

    private static SmileSectionUtils buildFlatLognormCustom() {
        // ATM=0.03, vol=0.15, T=0.5, custom grid = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0}
        final FlatSmileSection sec = new FlatSmileSection(0.5, 0.15, DC, 0.03);
        final double[] grid = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
        return new SmileSectionUtils(sec, grid, Constants.NULL_REAL, false);
    }

    private static SmileSectionUtils buildFlatLognormAtmOverride() {
        // ATM=0.04, vol=0.18, T=1.0, grid = {0.75, 1.0, 1.25, 1.5, 2.0, 3.0}, atm=0.04
        final FlatSmileSection sec = new FlatSmileSection(1.0, 0.18, DC, 0.04);
        final double[] grid = {0.75, 1.0, 1.25, 1.5, 2.0, 3.0};
        return new SmileSectionUtils(sec, grid, 0.04, false);
    }

    private static SmileSectionUtils buildFlatNormalDefault() {
        // ATM=0.02, vol=0.005 (normal), T=1.0, default Normal grid
        final FlatSmileSection sec = new FlatSmileSection(
                1.0, 0.005, DC, 0.02, VolatilityType.Normal, 0.0);
        return new SmileSectionUtils(sec, new double[0], Constants.NULL_REAL, false);
    }

    private static SmileSectionUtils buildFlatShiftedLognorm() {
        // ATM=0.02, vol=0.15, T=1.0, shift=0.02 (ShiftedLognormal), default grid
        final FlatSmileSection sec = new FlatSmileSection(
                1.0, 0.15, DC, 0.02, VolatilityType.ShiftedLognormal, 0.02);
        return new SmileSectionUtils(sec, new double[0], Constants.NULL_REAL, false);
    }

    private static SmileSectionUtils buildFlatDeleteArb() {
        // ATM=0.05, vol=0.20, T=1.0, custom grid = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0},
        // deleteArbitragePoints=true
        final FlatSmileSection sec = new FlatSmileSection(1.0, 0.20, DC, 0.05);
        final double[] grid = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
        return new SmileSectionUtils(sec, grid, Constants.NULL_REAL, true);
    }

    // -----------------------------------------------------------------------
    // Single @Test with collect-all-failures pattern
    // -----------------------------------------------------------------------

    @Test
    public void smileSectionUtils_matchesCpp() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);

        // Build all section utils instances (done once, reused per case group)
        final SmileSectionUtils utilsLognormDefault = buildFlatLognormDefault();
        final SmileSectionUtils utilsLognormCustom  = buildFlatLognormCustom();
        final SmileSectionUtils utilsAtmOverride    = buildFlatLognormAtmOverride();
        final SmileSectionUtils utilsNormalDefault  = buildFlatNormalDefault();
        final SmileSectionUtils utilsShiftedLognorm = buildFlatShiftedLognorm();
        final SmileSectionUtils utilsDeleteArb      = buildFlatDeleteArb();

        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            final Object expectedRaw = c.expectedRaw();

            // Dispatch by prefix → correct SmileSectionUtils instance + accessor
            try {
                checkCase(name, expectedRaw,
                        utilsLognormDefault, utilsLognormCustom, utilsAtmOverride,
                        utilsNormalDefault, utilsShiftedLognorm, utilsDeleteArb,
                        mismatches);
            } catch (Exception e) {
                mismatches.add(name + ": EXCEPTION " + e.getMessage());
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es) at TIGHT tier:\n"
                    + String.join("\n", mismatches));
        }
    }

    // -----------------------------------------------------------------------
    // Case dispatcher
    // -----------------------------------------------------------------------

    private void checkCase(final String name, final Object expectedRaw,
            final SmileSectionUtils lognormDefault,
            final SmileSectionUtils lognormCustom,
            final SmileSectionUtils atmOverride,
            final SmileSectionUtils normalDefault,
            final SmileSectionUtils shiftedLognorm,
            final SmileSectionUtils deleteArb,
            final List<String> mismatches) {

        final SmileSectionUtils utils;
        final String suffix;

        if (name.startsWith("flat_lognorm_default_")) {
            utils  = lognormDefault;
            suffix = name.substring("flat_lognorm_default_".length());
        } else if (name.startsWith("flat_lognorm_custom_")) {
            utils  = lognormCustom;
            suffix = name.substring("flat_lognorm_custom_".length());
        } else if (name.startsWith("flat_lognorm_atm_override_")) {
            utils  = atmOverride;
            suffix = name.substring("flat_lognorm_atm_override_".length());
        } else if (name.startsWith("flat_normal_default_")) {
            utils  = normalDefault;
            suffix = name.substring("flat_normal_default_".length());
        } else if (name.startsWith("flat_shiftedlognorm_")) {
            utils  = shiftedLognorm;
            suffix = name.substring("flat_shiftedlognorm_".length());
        } else if (name.startsWith("flat_deleteArb_")) {
            utils  = deleteArb;
            suffix = name.substring("flat_deleteArb_".length());
        } else {
            mismatches.add(name + ": UNRECOGNIZED prefix");
            return;
        }

        final JSONObject exp = (JSONObject) expectedRaw;

        switch (suffix) {
            case "atmLevel": {
                final double actual   = utils.atmLevel();
                final double expected = exp.getDouble("value");
                if (!Tolerance.tight(actual, expected)) {
                    mismatches.add(fmt(name, expected, actual));
                }
                break;
            }
            case "af_region": {
                final double[] region = utils.arbitragefreeRegion();
                checkDouble(name + ".kL", exp.getDouble("kL"), region[0], mismatches);
                checkDouble(name + ".kR", exp.getDouble("kR"), region[1], mismatches);
                break;
            }
            case "af_indices": {
                final int[] idx = utils.arbitragefreeIndices();
                checkExact(name + ".iL", exp.getInt("iL"), idx[0], mismatches);
                checkExact(name + ".iR", exp.getInt("iR"), idx[1], mismatches);
                break;
            }
            case "moneyGrid": {
                final double[] actual   = utils.moneyGrid();
                final JSONArray expected = exp.getJSONArray("values");
                checkArray(name, expected, actual, mismatches);
                break;
            }
            case "strikeGrid": {
                final double[] actual   = utils.strikeGrid();
                final JSONArray expected = exp.getJSONArray("values");
                checkArray(name, expected, actual, mismatches);
                break;
            }
            case "callPrices": {
                final double[] actual   = utils.callPrices();
                final JSONArray expected = exp.getJSONArray("values");
                checkArray(name, expected, actual, mismatches);
                break;
            }
            default:
                mismatches.add(name + ": UNRECOGNIZED suffix '" + suffix + "'");
        }
    }

    // -----------------------------------------------------------------------
    // Helper checks
    // -----------------------------------------------------------------------

    private static void checkDouble(final String name, final double expected,
                                    final double actual, final List<String> out) {
        if (!Tolerance.tight(actual, expected)) {
            out.add(fmt(name, expected, actual));
        }
    }

    private static void checkExact(final String name, final int expected,
                                   final int actual, final List<String> out) {
        if (actual != expected) {
            out.add(name + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void checkArray(final String name, final JSONArray expected,
                                   final double[] actual, final List<String> out) {
        if (actual.length != expected.length()) {
            out.add(name + ": length mismatch expected=" + expected.length()
                    + " actual=" + actual.length);
            return;
        }
        for (int i = 0; i < actual.length; i++) {
            final double exp = expected.getDouble(i);
            if (!Tolerance.tight(actual[i], exp)) {
                out.add(fmt(name + "[" + i + "]", exp, actual[i]));
            }
        }
    }

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }
}
