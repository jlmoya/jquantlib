/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for Black/UnitDisplaced/Bachelier YoYInflationCouponPricer
 against QuantLib v1.42.1 via
 migration-harness/references/cashflows/yoy_optionlet_pricer.json (Phase 2r C.3).
*/
package org.jquantlib.testsuite.cashflows;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.cashflow.BachelierYoYInflationCouponPricer;
import org.jquantlib.cashflow.BlackYoYInflationCouponPricer;
import org.jquantlib.cashflow.UnitDisplacedBlackYoYInflationCouponPricer;
import org.jquantlib.cashflow.YoYInflationCouponPricer;
import org.jquantlib.instruments.Option;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for the YoY-inflation optionlet pricer family:
 * {@link BlackYoYInflationCouponPricer},
 * {@link UnitDisplacedBlackYoYInflationCouponPricer},
 * {@link BachelierYoYInflationCouponPricer}.
 *
 * <p>The C++ probe computes the underlying formula directly
 * ({@code blackFormula} / {@code bachelierBlackFormula}), bypassing the
 * vol-surface dependency. This Java test does the same: it invokes the
 * static {@link BlackFormula} helpers to validate the formulas the pricer
 * subclasses dispatch to in their {@code optionletPriceImp} methods.
 *
 * <p>Tier rationale: closed-form Black/Bachelier formulas — TIGHT.
 */
public class YoYOptionletPricersTest {

    private static final String REF_GROUP = "cashflows/yoy_optionlet_pricer";

    @Test
    public void yoyOptionletPricers_matchesCpp() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            final JSONObject inputs = c.inputs();
            final JSONObject expected = (JSONObject) c.expectedRaw();

            final Option.Type type = "Call".equals(inputs.getString("optionType"))
                    ? Option.Type.Call : Option.Type.Put;
            final double strike = inputs.getDouble("strike");
            final double forward = inputs.getDouble("forward");
            final double stdDev = inputs.getDouble("stdDev");

            // The pricer subclasses dispatch to BlackFormula / BachelierBlackFormula
            // for their optionletPriceImp(forward, strike, stdDev).
            final double black = BlackFormula.blackFormula(type, strike, forward, stdDev);
            final double udb = BlackFormula.blackFormula(type,
                    strike + 1.0, forward + 1.0, stdDev);
            final double bach = BlackFormula.bachelierBlackFormula(type,
                    strike, forward, stdDev);

            if (!Tolerance.tight(black, expected.getDouble("black"))) {
                mismatches.add(name + ".black: expected=" + expected.getDouble("black")
                        + " actual=" + black);
            }
            if (!Tolerance.tight(udb, expected.getDouble("unitDisplacedBlack"))) {
                mismatches.add(name + ".unitDisplacedBlack: expected="
                        + expected.getDouble("unitDisplacedBlack") + " actual=" + udb);
            }
            if (!Tolerance.tight(bach, expected.getDouble("bachelier"))) {
                mismatches.add(name + ".bachelier: expected="
                        + expected.getDouble("bachelier") + " actual=" + bach);
            }
        }

        // Smoke-test: the pricer subclasses can be instantiated without
        // raising. The full optionletRate path requires a YoYOptionletVolatilitySurface
        // (Track B); the formula correctness is validated by the comparison above.
        new BlackYoYInflationCouponPricer();
        new UnitDisplacedBlackYoYInflationCouponPricer();
        new BachelierYoYInflationCouponPricer();
        // Verify the parent class also instantiates
        new YoYInflationCouponPricer();

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }
}
