/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for ZeroCouponInflationSwap against
 QuantLib v1.42.1 via
 migration-harness/references/instruments/zero_coupon_inflation_swap.json
 (Phase 2p A.3).
*/
package org.jquantlib.testsuite.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.instruments.ZeroCouponInflationSwap;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedZeroInflationCurve;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link ZeroCouponInflationSwap}.
 *
 * <p>Reproduces the C++ probe setup
 * (migration-harness/cpp/probes/instruments/zero_coupon_inflation_swap_probe.cpp):
 * UKRPI index seeded with monthly fixings 2005-01..2007-07, bound to a
 * 6-pillar Linear-interpolated zero-inflation curve, with a flat-forward 5%
 * Continuous Actual365Fixed nominal discount curve. Five swap cases exercise
 * Payer/Receiver, 5Y/10Y maturity, AsIndex/Flat/Linear observation, 2M/3M lag.
 *
 * <h3>Tier rationale</h3>
 * <ul>
 *   <li>Calendar arithmetic outputs ({@code *_serial}) — exact.</li>
 *   <li>{@code fairRate} — TIGHT. Closed form from the inflation cashflow's
 *       amount/notional ratio: deterministic from seeded fixings + curve
 *       interpolation, no Newton solver involved.</li>
 *   <li>{@code fixedLegBPS} — TIGHT. Closed form from {@code dfSigned} and
 *       {@code pow}.</li>
 *   <li>{@code npv} / {@code legNPV} — TIGHT. The DiscountingSwapEngine NPV
 *       is a single discount-factor-times-amount product per leg; FlatForward
 *       discount factors are exact closed-form ({@code exp(-rT)}).</li>
 * </ul>
 */
public class ZeroCouponInflationSwapTest {

    private static final String REF_GROUP = "instruments/zero_coupon_inflation_swap";

    @Test
    public void zeroCouponInflationSwap_matchesCpp() {
        // ---------- Match probe setup exactly ----------
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period swapObsLag3M = new Period(3, TimeUnit.Months);

        final Date refDate = cal.adjust(evalDate, bdc);

        // 6-pillar zero-inflation curve (matches probe nodes & rates).
        final Date[] nodeDates = new Date[] {
                new Date(1,  Month.May,    2007),  // baseDate (inflationPeriod start)
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] nodeRates = new double[] {
                0.025, 0.030, 0.032, 0.034, 0.036, 0.038
        };
        final var zeroCurve = new InterpolatedZeroInflationCurve<Linear>(Linear.class,
                        refDate, nodeDates, nodeRates, freq, dc);
        zeroCurve.enableExtrapolation();

        // UKRPI bound to forecast curve.
        final Handle<ZeroInflationTermStructure> ts =
                new Handle<ZeroInflationTermStructure>(zeroCurve);
        final UKRPI ukRpi = new UKRPI(Frequency.Monthly, false, false, ts);

        final Date[] fixDates = new Date[] {
                new Date(1, Month.January,   2005), new Date(1, Month.February,  2005),
                new Date(1, Month.March,     2005), new Date(1, Month.April,     2005),
                new Date(1, Month.May,       2005), new Date(1, Month.June,      2005),
                new Date(1, Month.July,      2005), new Date(1, Month.August,    2005),
                new Date(1, Month.September, 2005), new Date(1, Month.October,   2005),
                new Date(1, Month.November,  2005), new Date(1, Month.December,  2005),
                new Date(1, Month.January,   2006), new Date(1, Month.February,  2006),
                new Date(1, Month.March,     2006), new Date(1, Month.April,     2006),
                new Date(1, Month.May,       2006), new Date(1, Month.June,      2006),
                new Date(1, Month.July,      2006), new Date(1, Month.August,    2006),
                new Date(1, Month.September, 2006), new Date(1, Month.October,   2006),
                new Date(1, Month.November,  2006), new Date(1, Month.December,  2006),
                new Date(1, Month.January,   2007), new Date(1, Month.February,  2007),
                new Date(1, Month.March,     2007), new Date(1, Month.April,     2007),
                new Date(1, Month.May,       2007), new Date(1, Month.June,      2007),
                new Date(1, Month.July,      2007),
        };
        final double[] fixVals = new double[] {
                189.9, 189.9, 190.5, 191.6, 192.0, 192.2, 192.2, 192.6, 193.1, 193.3, 193.6, 194.1,
                193.4, 194.2, 195.0, 196.5, 197.7, 198.5, 198.5, 199.2, 200.1, 200.4, 201.1, 202.7,
                201.6, 203.1, 204.4, 205.4, 206.2, 207.3, 206.1
        };
        for (int i = 0; i < fixDates.length; ++i) {
            ukRpi.addFixing(fixDates[i], fixVals[i], true /* force overwrite */);
        }

        // Nominal discount curve: 5% Continuous Actual365Fixed (matches probe).
        final FlatForward nominalCurve = new FlatForward(refDate, 0.05, dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> nominalTS =
                new Handle<YieldTermStructure>(nominalCurve);
        final DiscountingSwapEngine engine = new DiscountingSwapEngine(nominalTS);

        // ---------- Cross-validate every case ----------
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            try {
                checkCase(name, c, ukRpi, evalDate, cal, bdc, dc, engine, mismatches);
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
                                  final UKRPI ukRpi,
                                  final Date evalDate,
                                  final Calendar cal,
                                  final BusinessDayConvention bdc,
                                  final DayCounter dc,
                                  final DiscountingSwapEngine engine,
                                  final List<String> mismatches) {
        final JSONObject inputs = c.inputs();
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final ZeroCouponInflationSwap.Type type =
                ZeroCouponInflationSwap.Type.valueOf(inputs.getString("type"));
        final double nominal = inputs.getDouble("nominal");
        final long maturitySerial = inputs.getLong("maturity_serial");
        final int obsLagMonths = inputs.getInt("observationLag_months");
        final String interpStr = inputs.getString("interpolation");
        final double fixedRate = inputs.getDouble("fixedRate");

        final Date maturity = new Date(maturitySerial);
        final Period observationLag = new Period(obsLagMonths, TimeUnit.Months);
        final CPI.InterpolationType interp = CPI.InterpolationType.valueOf(interpStr);

        final ZeroCouponInflationSwap zcis = new ZeroCouponInflationSwap(
                type, nominal, evalDate, maturity, cal, bdc, dc, fixedRate,
                ukRpi, observationLag, interp);
        zcis.setPricingEngine(engine);

        // startDate / maturityDate (calendar arithmetic — exact)
        check(name, "startDate_actual_serial", expected, mismatches,
                zcis.startDate().serialNumber());
        check(name, "maturityDate_actual_serial", expected, mismatches,
                zcis.maturityDate().serialNumber());

        // Payment dates (calendar arithmetic — exact)
        check(name, "fixedLegPaymentDate_serial", expected, mismatches,
                zcis.fixedLeg().get(0).date().serialNumber());
        check(name, "inflationLegPaymentDate_serial", expected, mismatches,
                zcis.inflationLeg().get(0).date().serialNumber());

        // baseDate / fixingDate from the inflation cashflow (calendar arithmetic — exact)
        final org.jquantlib.cashflow.IndexedCashFlow icf =
                (org.jquantlib.cashflow.IndexedCashFlow) zcis.inflationLeg().get(0);
        check(name, "baseDate_serial", expected, mismatches,
                icf.baseDate().serialNumber());
        check(name, "fixingDate_serial", expected, mismatches,
                icf.fixingDate().serialNumber());

        // fairRate — TIGHT (closed-form from fixings + curve)
        checkTight(name, "fairRate", expected, mismatches, zcis.fairRate());
        // fixedLegBPS — TIGHT (closed form from df + pow)
        checkTight(name, "fixedLegBPS", expected, mismatches, zcis.fixedLegBPS());

        // NPVs — TIGHT (DiscountingSwapEngine = sum of df * amount per leg;
        // FlatForward discount factors are exact closed-form exp(-rT)).
        checkTight(name, "fixedLegNPV", expected, mismatches, zcis.fixedLegNPV());
        checkTight(name, "inflationLegNPV", expected, mismatches, zcis.inflationLegNPV());
        checkTight(name, "npv", expected, mismatches, zcis.NPV());
    }

    private static void check(final String name, final String key,
                              final JSONObject expected, final List<String> mismatches,
                              final long actual) {
        final long exp = expected.getLong(key);
        if (!Tolerance.exact(actual, exp)) {
            mismatches.add(name + "." + key + ": expected=" + exp + " actual=" + actual);
        }
    }

    private static void checkTight(final String name, final String key,
                                   final JSONObject expected, final List<String> mismatches,
                                   final double actual) {
        final double exp = expected.getDouble(key);
        if (!Tolerance.tight(actual, exp)) {
            mismatches.add(fmt(name + "." + key, exp, actual));
        }
    }

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }
}
