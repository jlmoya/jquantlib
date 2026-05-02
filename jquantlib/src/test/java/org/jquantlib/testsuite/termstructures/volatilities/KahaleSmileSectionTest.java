/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for KahaleSmileSection against QuantLib v1.42.1 via
 migration-harness/references/termstructures/volatility/kahale_smile_section.json.
 Phase 2j WI-4.0c.
*/
package org.jquantlib.testsuite.termstructures.volatilities;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.math.Constants;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.KahaleSmileSection;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link KahaleSmileSection}:
 * atmLevel, coreIndices, volatility, optionPrice — all at TIGHT tier.
 *
 * <p>Five scenario groups:
 * <ul>
 *   <li>A: interpolate=false, exponentialExtrapolation=false, default grid, shift=0</li>
 *   <li>B: interpolate=true,  exponentialExtrapolation=false, compact grid, shift=0</li>
 *   <li>C: interpolate=false, exponentialExtrapolation=true,  default grid, shift=0</li>
 *   <li>D: interpolate=true,  exponentialExtrapolation=true,  compact grid, shift=0</li>
 *   <li>E: interpolate=false, exponentialExtrapolation=false, default grid, shift=0.02</li>
 * </ul>
 *
 * <p>C++ exerciseTime = Actual365Fixed.yearFraction(2020-01-01, 2021-01-02) = 367/365.
 *
 * @see KahaleSmileSection
 */
public class KahaleSmileSectionTest {

    private static final String REF_GROUP = "termstructures/volatility/kahale_smile_section";

    // Actual365Fixed year fraction from 2020-01-01 to 2021-01-02 = 367 days
    private static final double T = 367.0 / 365.0;

    // Compact grid matching probe scenario B/D
    private static final double[] COMPACT_GRID = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};

    private static final Actual365Fixed DC = new Actual365Fixed();

    // -----------------------------------------------------------------------
    // Section builders — must exactly match probe construction
    // -----------------------------------------------------------------------

    /** Scenario A: default grid, interp=false, expExtra=false, shift=0. */
    private static KahaleSmileSection buildA() {
        final FlatSmileSection sec = new FlatSmileSection(T, 0.20, DC, 0.05);
        return new KahaleSmileSection(sec, Constants.NULL_REAL, false, false, false);
    }

    /** Scenario B: compact grid, interp=true, expExtra=false, shift=0. */
    private static KahaleSmileSection buildB() {
        final FlatSmileSection sec = new FlatSmileSection(T, 0.20, DC, 0.05);
        return new KahaleSmileSection(sec, Constants.NULL_REAL, true, false, false, COMPACT_GRID);
    }

    /** Scenario C: default grid, interp=false, expExtra=true, shift=0. */
    private static KahaleSmileSection buildC() {
        final FlatSmileSection sec = new FlatSmileSection(T, 0.20, DC, 0.05);
        return new KahaleSmileSection(sec, Constants.NULL_REAL, false, true, false);
    }

    /** Scenario D: compact grid, interp=true, expExtra=true, shift=0. */
    private static KahaleSmileSection buildD() {
        final FlatSmileSection sec = new FlatSmileSection(T, 0.20, DC, 0.05);
        return new KahaleSmileSection(sec, Constants.NULL_REAL, true, true, false, COMPACT_GRID);
    }

    /** Scenario E: default grid, interp=false, expExtra=false, shift=0.02. */
    private static KahaleSmileSection buildE() {
        final FlatSmileSection sec = new FlatSmileSection(
                T, 0.15, DC, 0.02, VolatilityType.ShiftedLognormal, 0.02);
        return new KahaleSmileSection(sec, Constants.NULL_REAL, false, false, false);
    }

    // -----------------------------------------------------------------------
    // Single @Test — collect-all-failures
    // -----------------------------------------------------------------------

    @Test
    public void kahaleSmileSection_matchesCpp() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);

        // Build all instances once
        final KahaleSmileSection ksA = buildA();
        final KahaleSmileSection ksB = buildB();
        final KahaleSmileSection ksC = buildC();
        final KahaleSmileSection ksD = buildD();
        final KahaleSmileSection ksE = buildE();

        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            try {
                checkCase(name, (JSONObject) c.expectedRaw(), ksA, ksB, ksC, ksD, ksE, mismatches);
            } catch (final Exception e) {
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

    private void checkCase(final String name, final JSONObject exp,
            final KahaleSmileSection ksA,
            final KahaleSmileSection ksB,
            final KahaleSmileSection ksC,
            final KahaleSmileSection ksD,
            final KahaleSmileSection ksE,
            final List<String> mismatches) {

        final KahaleSmileSection ks;
        if (name.startsWith("A_")) {
            ks = ksA;
        } else if (name.startsWith("B_")) {
            ks = ksB;
        } else if (name.startsWith("C_")) {
            ks = ksC;
        } else if (name.startsWith("D_")) {
            ks = ksD;
        } else if (name.startsWith("E_")) {
            ks = ksE;
        } else {
            mismatches.add(name + ": UNRECOGNIZED prefix");
            return;
        }

        if (name.endsWith("_atmLevel")) {
            final double expected = exp.getDouble("value");
            final double actual   = ks.atmLevel();
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.endsWith("_coreLeft")) {
            final int expected = exp.getInt("value");
            final int actual   = ks.coreIndices()[0];
            if (actual != expected) {
                mismatches.add(name + ": expected=" + expected + " actual=" + actual);
            }
        } else if (name.endsWith("_coreRight")) {
            final int expected = exp.getInt("value");
            final int actual   = ks.coreIndices()[1];
            if (actual != expected) {
                mismatches.add(name + ": expected=" + expected + " actual=" + actual);
            }
        } else if (name.contains("_vol_k")) {
            final double strike   = exp.getDouble("strike");
            final double expected = exp.getDouble("value");
            final double actual   = ks.volatility(strike);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.contains("_call_k")) {
            final double strike   = exp.getDouble("strike");
            final double expected = exp.getDouble("value");
            final double actual   = ks.optionPrice(strike,
                    org.jquantlib.instruments.Option.Type.Call, 1.0);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else {
            mismatches.add(name + ": UNRECOGNIZED suffix");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }
}
