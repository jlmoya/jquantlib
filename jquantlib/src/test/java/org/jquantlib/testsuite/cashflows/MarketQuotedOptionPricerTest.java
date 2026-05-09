/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validated tests for MarketQuotedOptionPricer against QuantLib v1.42.1
 via migration-harness/references/cashflows/market_quoted_option_pricer.json
 (Phase 5e.5 WI-4).
*/
package org.jquantlib.testsuite.cashflows;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.cashflow.MarketQuotedOptionPricer;
import org.jquantlib.cashflow.VanillaOptionPricer;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.instruments.Option;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link MarketQuotedOptionPricer}, the
 * vanilla-option pricer used by Hagan/CMS pricers as the conundrum
 * integrand evaluator.
 *
 * <p>The C++ probe drives the closed-form arithmetic
 * (deflator * blackFormula or deflator * bachelierBlackFormula)
 * directly. This Java test mirrors that and additionally exercises
 * the constructor's vol-type guard and the deflator pathway.
 *
 * <p>Tier rationale: closed-form -- TIGHT (1e-12 rel, 1e-14 abs).
 */
public class MarketQuotedOptionPricerTest {

    private static final String REF_GROUP = "cashflows/market_quoted_option_pricer";

    @Test
    public void marketQuotedOptionPricer_pricesMatchCpp() {
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
            final double variance = inputs.getDouble("variance");
            final double deflator = inputs.getDouble("deflator");
            final boolean shifted = inputs.getBoolean("shifted");

            final double stdDev = Math.sqrt(variance);
            final double price;
            if (shifted) {
                price = deflator * BlackFormula.blackFormula(type, strike, forward, stdDev);
            } else {
                price = deflator * BlackFormula.bachelierBlackFormula(type, strike, forward, stdDev);
            }

            if (!Tolerance.tight(price, expected.getDouble("price"))) {
                mismatches.add(name + ".price: expected="
                        + expected.getDouble("price") + " actual=" + price);
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    /**
     * Integration smoke test: instantiate {@link MarketQuotedOptionPricer}
     * over a {@link ConstantSwaptionVolatility} surface, verify the
     * {@link VanillaOptionPricer#evaluate} dispatches through the smile
     * section as expected. Uses the C++ formula
     * {@code deflator * blackFormula(type, strike, fwd, sqrt(variance))}
     * with {@code variance = vol^2 * optionTime} as ground truth.
     */
    @Test
    public void marketQuotedOptionPricer_withConstantSurface_matchesFormula() {
        final Date refDate = new Date(15, Month.January, 2026);
        final Date expiryDate = new Date(15, Month.January, 2027);
        final Period swapTenor = new Period(5, TimeUnit.Years);
        final double vol = 0.25;
        final double forward = 0.03;
        final double strike = 0.025;
        final double deflator = 4.5;

        final Handle<Quote> volH = new Handle<Quote>(new SimpleQuote(vol));
        final SwaptionVolatilityStructure surface = new ConstantSwaptionVolatility(
                refDate, new Target(), BusinessDayConvention.ModifiedFollowing,
                volH, new Actual360());

        final MarketQuotedOptionPricer pricer = new MarketQuotedOptionPricer(
                forward, expiryDate, swapTenor, surface);

        // Internal smile section returns variance = vol^2 * t at the strike
        // (FlatSmileSection.variance(K) ignores K).
        final double t = surface.dayCounter().yearFraction(refDate, expiryDate);
        final double variance = vol * vol * t;
        final double stdDev = Math.sqrt(variance);
        final double expected = deflator
                * BlackFormula.blackFormula(Option.Type.Call, strike, forward, stdDev);

        final double actual = pricer.evaluate(strike, Option.Type.Call, deflator);
        assertEquals("MarketQuotedOptionPricer integrated price", expected, actual, 1e-12);
        assertNotNull(pricer.expiryDate());
        assertEquals(swapTenor, pricer.swapTenor());
        assertEquals(forward, pricer.forwardValue(), 0.0);
    }

    /**
     * The C++ guard:
     *   QL_REQUIRE((volatilityStructure->volatilityType() == Normal) ||
     *              (volatilityStructure->volatilityType() == ShiftedLognormal &&
     *               close_enough(volatilityStructure->shift(...), 0.0)),
     *              "VanillaOptionPricer: a normal or a zero-shift lognormal volatility is required");
     *
     * A non-zero-shift surface must be rejected at construction time.
     */
    @Test(expected = Exception.class)
    public void marketQuotedOptionPricer_rejectsNonZeroShiftLognormal() {
        final Date refDate = new Date(15, Month.January, 2026);
        final Date expiryDate = new Date(15, Month.January, 2027);
        final Period swapTenor = new Period(5, TimeUnit.Years);

        final Handle<Quote> volH = new Handle<Quote>(new SimpleQuote(0.25));
        final SwaptionVolatilityStructure surface = new ConstantSwaptionVolatility(
                refDate, new Target(), BusinessDayConvention.ModifiedFollowing,
                volH, new Actual360(),
                VolatilityType.ShiftedLognormal, 0.03 /* non-zero shift */);

        new MarketQuotedOptionPricer(0.03, expiryDate, swapTenor, surface);
    }
}
