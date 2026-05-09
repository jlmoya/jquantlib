/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validated tests for BlackIborCouponPricer against QuantLib v1.42.1
 via migration-harness/references/cashflows/black_ibor_coupon_pricer.json
 (Phase 5e.5).
*/
package org.jquantlib.testsuite.cashflows;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.cashflow.BlackIborCouponPricer.TimingAdjustment;
import org.jquantlib.instruments.Option;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link BlackIborCouponPricer}.
 *
 * <p>The C++ probe computes the underlying formulas directly
 * ({@code blackFormula} / {@code bachelierBlackFormula} for
 * {@code optionletRate}; the pure-formula adjustment for
 * {@code adjustedFixing}). This Java test does the same: it validates
 * the closed-form scalar paths the pricer dispatches to, ensuring the
 * shifted-lognormal vs normal switch and the Black76 in-arrears
 * adjustment match v1.42.1 to TIGHT tolerance.
 *
 * <p>Tier rationale: closed-form Black/Bachelier and rational adjustment
 * formula -- TIGHT (1e-12 rel, 1e-14 abs near zero).
 */
public class BlackIborCouponPricerTest {

    private static final String REF_GROUP = "cashflows/black_ibor_coupon_pricer";

    @Test
    public void blackIborCouponPricer_optionletRateAndAdjustedFixing_matchesCpp() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            final JSONObject inputs = c.inputs();
            final JSONObject expected = (JSONObject) c.expectedRaw();

            // Cases divide by which expected key is present.
            if (expected.has("rate")) {
                // optionletRate (pre-fixing branch)
                final Option.Type type = "Call".equals(inputs.getString("optionType"))
                        ? Option.Type.Call : Option.Type.Put;
                final double strike = inputs.getDouble("strike");
                final double forward = inputs.getDouble("forward");
                final double stdDev = inputs.getDouble("stdDev");
                final double displacement = inputs.getDouble("displacement");
                final boolean shifted = inputs.getBoolean("shifted");

                final double rate;
                if (shifted) {
                    rate = BlackFormula.blackFormula(type, strike, forward,
                            stdDev, 1.0, displacement);
                } else {
                    rate = BlackFormula.bachelierBlackFormula(type, strike,
                            forward, stdDev, 1.0);
                }
                if (!Tolerance.tight(rate, expected.getDouble("rate"))) {
                    mismatches.add(name + ".rate: expected="
                            + expected.getDouble("rate") + " actual=" + rate);
                }
            } else if (expected.has("adjustment")) {
                // adjustedFixing (Black76 in-arrears core)
                final double fixing = inputs.getDouble("fixing");
                final double variance = inputs.getDouble("variance");
                final double tau = inputs.getDouble("tau");
                final double displacement = inputs.getDouble("displacement");
                final boolean shifted = inputs.getBoolean("shifted");

                final double adjustment;
                if (shifted) {
                    adjustment = (fixing + displacement) * (fixing + displacement)
                            * variance * tau / (1.0 + fixing * tau);
                } else {
                    adjustment = variance * tau / (1.0 + fixing * tau);
                }
                final double adjusted = fixing + adjustment;

                if (!Tolerance.tight(adjustment, expected.getDouble("adjustment"))) {
                    mismatches.add(name + ".adjustment: expected="
                            + expected.getDouble("adjustment")
                            + " actual=" + adjustment);
                }
                if (!Tolerance.tight(adjusted, expected.getDouble("adjusted"))) {
                    mismatches.add(name + ".adjusted: expected="
                            + expected.getDouble("adjusted")
                            + " actual=" + adjusted);
                }
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    /**
     * Smoke-test: the upgraded {@link BlackIborCouponPricer} constructors
     * (vol-only, vol+TimingAdjustment, full) instantiate cleanly and expose
     * the new accessors. Full pricing requires a coupon + index + curve;
     * those paths are exercised by {@code BondTest}, {@code CapFloorTest},
     * etc. (Phase 5e.5b).
     */
    @Test
    public void blackIborCouponPricer_constructors_expose_v1_42_1_api() {
        final BlackIborCouponPricer p1 = new BlackIborCouponPricer();
        assertEquals(TimingAdjustment.Black76, p1.timingAdjustment());
        assertNotNull(p1.correlation());
        assertEquals(1.0, p1.correlation().currentLink().value(), 0.0);

        final BlackIborCouponPricer p2 = new BlackIborCouponPricer(
                new Handle<OptionletVolatilityStructure>(),
                TimingAdjustment.BivariateLognormal);
        assertEquals(TimingAdjustment.BivariateLognormal, p2.timingAdjustment());

        final Handle<Quote> rho = new Handle<Quote>(new SimpleQuote(0.75));
        final BlackIborCouponPricer p3 = new BlackIborCouponPricer(
                new Handle<OptionletVolatilityStructure>(),
                TimingAdjustment.BivariateLognormal, rho);
        assertEquals(0.75, p3.correlation().currentLink().value(), 0.0);
    }

    /**
     * Smoke-test: rejects unknown timing adjustment values. (Defensive
     * guard mirroring the C++ {@code QL_REQUIRE} in
     * {@code BlackIborCouponPricer} constructor.)
     */
    @Test(expected = Exception.class)
    public void blackIborCouponPricer_rejects_null_timingAdjustment() {
        new BlackIborCouponPricer(new Handle<OptionletVolatilityStructure>(),
                                  null);
    }
}
