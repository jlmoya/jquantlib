/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for AtmSmileSection against QuantLib v1.42.1 via
 migration-harness/references/termstructures/volatility/atm_smile_section.json.
 Phase 2j.5 Track C.2.
*/
package org.jquantlib.testsuite.termstructures.volatilities;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.AtmSmileSection;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link AtmSmileSection}:
 * atmLevel, minStrike, maxStrike, exerciseTime, shift, volatility, variance
 * — all at TIGHT tier.
 *
 * <p>Four scenario groups (matching {@code atm_smile_section_probe.cpp}):
 * <ul>
 *   <li>A: no ATM override (inherits source.atmLevel()=0.05), vol=0.20, T=367/365</li>
 *   <li>B: explicit atm=0.06, source.atmLevel()=0.05, vol=0.20, T=367/365</li>
 *   <li>C: exerciseTime-based source, explicit atm=0.045, vol=0.15, T=367/365</li>
 *   <li>D: shifted source (shift=0.01), no ATM override, vol=0.18, T=1.0</li>
 * </ul>
 *
 * @see AtmSmileSection
 */
public class AtmSmileSectionTest {

    private static final String REF_GROUP = "termstructures/volatility/atm_smile_section";

    /** 367/365 year fraction — Actual365Fixed from 2020-01-01 to 2021-01-02. */
    private static final double T = 367.0 / 365.0;

    private static final Actual365Fixed DC = new Actual365Fixed();

    // -----------------------------------------------------------------------
    // Section builders — must exactly match probe construction
    // -----------------------------------------------------------------------

    /** Scenario A: no ATM override; source=FlatSmileSection(vol=0.20, atm=0.05). */
    private static AtmSmileSection buildA() {
        final FlatSmileSection src = new FlatSmileSection(T, 0.20, DC, 0.05);
        return new AtmSmileSection(src);  // atm = Double.NaN → inherits 0.05
    }

    /** Scenario B: explicit atm=0.06; source=FlatSmileSection(vol=0.20, atm=0.05). */
    private static AtmSmileSection buildB() {
        final FlatSmileSection src = new FlatSmileSection(T, 0.20, DC, 0.05);
        return new AtmSmileSection(src, 0.06);
    }

    /** Scenario C: exerciseTime-based source, explicit atm=0.045; vol=0.15, atm=0.04. */
    private static AtmSmileSection buildC() {
        final FlatSmileSection src = new FlatSmileSection(T, 0.15, DC, 0.04);
        return new AtmSmileSection(src, 0.045);
    }

    /** Scenario D: shifted source (shift=0.01), no ATM override; vol=0.18, atm=0.03, T=1.0. */
    private static AtmSmileSection buildD() {
        final FlatSmileSection src = new FlatSmileSection(
                1.0, 0.18, DC, 0.03, VolatilityType.ShiftedLognormal, 0.01);
        return new AtmSmileSection(src);  // atm = Double.NaN → inherits 0.03
    }

    // -----------------------------------------------------------------------
    // Single @Test — collect-all-failures
    // -----------------------------------------------------------------------

    @Test
    public void atmSmileSection_matchesCpp() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);

        final AtmSmileSection secA = buildA();
        final AtmSmileSection secB = buildB();
        final AtmSmileSection secC = buildC();
        final AtmSmileSection secD = buildD();

        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            try {
                checkCase(name, (JSONObject) c.expectedRaw(), secA, secB, secC, secD, mismatches);
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
            final AtmSmileSection secA,
            final AtmSmileSection secB,
            final AtmSmileSection secC,
            final AtmSmileSection secD,
            final List<String> mismatches) {

        final AtmSmileSection sec;
        if (name.startsWith("A_")) {
            sec = secA;
        } else if (name.startsWith("B_")) {
            sec = secB;
        } else if (name.startsWith("C_")) {
            sec = secC;
        } else if (name.startsWith("D_")) {
            sec = secD;
        } else {
            mismatches.add(name + ": UNRECOGNIZED prefix");
            return;
        }

        if (name.endsWith("_atmLevel")) {
            final double expected = exp.getDouble("value");
            final double actual   = sec.atmLevel();
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.endsWith("_minStrike")) {
            // minStrike = -Double.MAX_VALUE for FlatSmileSection (unshifted)
            final double expected = exp.getDouble("value");
            final double actual   = sec.minStrike();
            if (!Tolerance.exact(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.endsWith("_maxStrike")) {
            final double expected = exp.getDouble("value");
            final double actual   = sec.maxStrike();
            if (!Tolerance.exact(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.endsWith("_exerciseTime")) {
            final double expected = exp.getDouble("value");
            final double actual   = sec.exerciseTime();
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.endsWith("_shift")) {
            final double expected = exp.getDouble("value");
            final double actual   = sec.shift();
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.contains("_vol_k")) {
            // strike is encoded in the expected object
            final double strike   = exp.getDouble("strike");
            final double expected = exp.getDouble("value");
            final double actual   = sec.volatility(strike);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.contains("_var_k")) {
            final double strike   = exp.getDouble("strike");
            final double expected = exp.getDouble("value");
            final double actual   = sec.variance(strike);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.endsWith("_vol_atm")) {
            // null strike → ATM query (NaN in Java mirrors Null<Real>() in C++)
            final double expected = exp.getDouble("value");
            final double actual   = sec.volatility(Double.NaN);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.endsWith("_var_atm")) {
            final double expected = exp.getDouble("value");
            final double actual   = sec.variance(Double.NaN);
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
