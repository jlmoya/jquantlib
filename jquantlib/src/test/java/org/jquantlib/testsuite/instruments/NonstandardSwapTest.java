// jquantlib/src/test/java/org/jquantlib/testsuite/instruments/NonstandardSwapTest.java
//
// Phase 2j.5 Track A.1 — NonstandardSwap cross-validation test.
//
// Mirrors the fixture in
//   migration-harness/cpp/probes/instruments/nonstandard_swap_probe.cpp
// and validates all 19 cases at TIGHT tier (abs 1e-14 + rel 1e-12).
// NPV fingerprints come from C++ QuantLib v1.42.1 (DiscountingSwapEngine).
// No transcendental functions are involved (pure arithmetic discount factor
// products), so TIGHT is achievable.
//
// Single @Test with collect-all-failures pattern.

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.instruments.NonstandardSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 2j.5 Track A.1 fingerprint test for {@link NonstandardSwap}.
 *
 * <p>Validates {@code legNPV(0)}, {@code legNPV(1)}, {@code NPV()},
 * {@code fixedLeg().size()}, and {@code floatingLeg().size()} for all 19
 * reference cases against C++ QuantLib v1.42.1 oracle values.
 *
 * <p>Tier: TIGHT (abs {@value Tolerance#TIGHT_ABS} + rel
 * {@value Tolerance#TIGHT_REL}). NonstandardSwap NPV is pure arithmetic
 * (discount-factor products, no transcendental functions), so TIGHT is
 * achievable.
 */
public class NonstandardSwapTest {

    private static final Date EVAL   = new Date(15, Month.January, 2026);
    private static final double FLAT_RATE = 0.04;

    @Test
    public void nonstandardSwap_npvMatchesCpp() {
        new Settings().setEvaluationDate(EVAL);

        // ── Fixture (mirrors nonstandard_swap_probe.cpp) ──────────────────────
        final DayCounter dc      = new Actual365Fixed();
        final DayCounter fixedDc = new Thirty360(Thirty360.Convention.European);
        final Calendar   cal     = new Target();

        final YieldTermStructure flat = new FlatForward(
                EVAL, FLAT_RATE, dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx = new Euribor3M(ts);
        final DiscountingSwapEngine dse = new DiscountingSwapEngine(ts);

        final ReferenceReader reader = ReferenceReader.load("instruments/nonstandard_swap");
        final List<String> failures = new ArrayList<String>();

        // ── Helper: spot start date ───────────────────────────────────────────
        // C++: cal.advance(EVAL, Period(2, Days))  with TARGET ModifiedFollowing
        final Date start = cal.advance(EVAL, new Period(2, TimeUnit.Days),
                BusinessDayConvention.ModifiedFollowing, false);

        // ── Group A: from VanillaSwap conversion ──────────────────────────────
        {
            // Mirrors the C++ vcs[] loop in the probe.
            class VC {
                int tenor; VanillaSwap.Type type; double rate; double nominal; double spread;
                VC(int t, VanillaSwap.Type tp, double r, double n, double s) {
                    tenor=t; type=tp; rate=r; nominal=n; spread=s; }
            }
            VC[] vcs = {
                new VC(3, VanillaSwap.Type.Payer,    0.03, 100.0,  0.0),
                new VC(5, VanillaSwap.Type.Payer,    0.04, 200.0,  0.001),
                new VC(5, VanillaSwap.Type.Receiver, 0.035,150.0,  0.0),
                new VC(2, VanillaSwap.Type.Receiver, 0.025,100.0, -0.001),
            };
            for (int i = 0; i < vcs.length; i++) {
                final VC vc = vcs[i];
                final String name = "from_vanilla_" + i;
                try {
                    final Date end1Y = cal.advance(start,
                            new Period(1, TimeUnit.Years), BusinessDayConvention.ModifiedFollowing, false);

                    final Schedule fixedSch = makeSchedule(cal, start, vc.tenor, 1, TimeUnit.Years);
                    final Schedule floatSch = makeSchedule(cal, start, vc.tenor, 3, TimeUnit.Months);

                    final VanillaSwap vanilla = new VanillaSwap(
                            vc.type, vc.nominal, fixedSch, vc.rate, fixedDc,
                            floatSch, idx, vc.spread, dc,
                            BusinessDayConvention.ModifiedFollowing);
                    final NonstandardSwap ns = new NonstandardSwap(vanilla);
                    ns.setPricingEngine(dse);

                    final Case ref = reader.getCase(name);
                    final JSONObject exp = (JSONObject) ref.expectedRaw();

                    check(failures, name, "fixedLegNPV",
                            ns.legNPV(0), exp.getDouble("fixedLegNPV"));
                    check(failures, name, "floatingLegNPV",
                            ns.legNPV(1), exp.getDouble("floatingLegNPV"));
                    check(failures, name, "npv",
                            ns.NPV(), exp.getDouble("npv"));
                    checkExact(failures, name, "nFixedCoupons",
                            ns.fixedLeg().size(), exp.getInt("nFixedCoupons"));
                    checkExact(failures, name, "nFloatCoupons",
                            ns.floatingLeg().size(), exp.getInt("nFloatCoupons"));
                    checkTight(failures, name, "fixedRate0",
                            ns.fixedRate()[0], exp.getDouble("fixedRate0"));
                    checkTight(failures, name, "fixedNominal0",
                            ns.fixedNominal()[0], exp.getDouble("fixedNominal0"));
                    checkTight(failures, name, "spreadScalar",
                            ns.spread(), exp.getDouble("spreadScalar"));
                    checkTight(failures, name, "gearingScalar",
                            ns.gearing(), exp.getDouble("gearingScalar"));
                } catch (final Exception e) {
                    failures.add("[" + name + "] unexpected exception: " + e.getMessage());
                }
            }
        }

        // ── Group B: per-coupon notionals, scalar gearing/spread ──────────────
        {
            // per_coupon_scalar_0: 3Y amortising fixed, flat float
            checkGroup(failures, reader, dse, idx, dc, fixedDc, cal, start,
                    "per_coupon_scalar_0",
                    VanillaSwap.Type.Payer,
                    new double[]{100.0, 80.0, 60.0},
                    repeat(100.0, 12),
                    3, 1, TimeUnit.Years,
                    3, 3, TimeUnit.Months,
                    new double[]{0.03, 0.03, 0.03},
                    1.0, 0.0, false, false);

            // per_coupon_scalar_1: 3Y accreting
            checkGroup(failures, reader, dse, idx, dc, fixedDc, cal, start,
                    "per_coupon_scalar_1",
                    VanillaSwap.Type.Receiver,
                    new double[]{50.0, 75.0, 100.0},
                    new double[]{50,50,50, 62.5,62.5,62.5, 87.5,87.5,87.5, 100,100,100},
                    3, 1, TimeUnit.Years,
                    3, 3, TimeUnit.Months,
                    new double[]{0.04, 0.04, 0.04},
                    1.0, 0.0, false, false);

            // per_coupon_scalar_2: 5Y flat notional, varying rates
            checkGroup(failures, reader, dse, idx, dc, fixedDc, cal, start,
                    "per_coupon_scalar_2",
                    VanillaSwap.Type.Payer,
                    repeat(100.0, 5),
                    repeat(100.0, 20),
                    5, 1, TimeUnit.Years,
                    5, 3, TimeUnit.Months,
                    new double[]{0.025, 0.03, 0.035, 0.04, 0.045},
                    1.0, 0.001, false, false);

            // per_coupon_scalar_3: 5Y gearing = 0.5
            checkGroup(failures, reader, dse, idx, dc, fixedDc, cal, start,
                    "per_coupon_scalar_3",
                    VanillaSwap.Type.Receiver,
                    repeat(100.0, 5),
                    repeat(100.0, 20),
                    5, 1, TimeUnit.Years,
                    5, 3, TimeUnit.Months,
                    repeat(0.04, 5),
                    0.5, -0.001, false, false);
        }

        // ── Group C: per-coupon gearings and spreads (vector overload) ─────────
        {
            final Schedule fixedSch = makeSchedule(cal, start, 3, 1, TimeUnit.Years);
            final Schedule floatSch = makeSchedule(cal, start, 3, 3, TimeUnit.Months);

            final double[] fixedNom  = {100.0, 90.0, 80.0};
            final double[] floatNom  = {100, 100, 100,
                                         90,  90,  90,
                                         80,  80,  80,
                                         75,  75,  75};
            final double[] fixedRates = {0.03, 0.035, 0.04};
            final double[] gearings   = repeat(1.0, 12);
            gearings[0]  = 1.1;
            gearings[6]  = 0.9;
            final double[] spreads    = repeat(0.001, 12);
            spreads[3]  = 0.0015;
            spreads[9]  = 0.0005;

            // vector_gearing_spread (Payer)
            try {
                final String name = "vector_gearing_spread";
                final NonstandardSwap ns = new NonstandardSwap(
                        VanillaSwap.Type.Payer,
                        fixedNom, floatNom, fixedSch, fixedRates, fixedDc,
                        floatSch, idx, gearings, spreads, dc);
                ns.setPricingEngine(dse);
                final Case ref = reader.getCase(name);
                final JSONObject exp = (JSONObject) ref.expectedRaw();
                check(failures, name, "fixedLegNPV",    ns.legNPV(0), exp.getDouble("fixedLegNPV"));
                check(failures, name, "floatingLegNPV", ns.legNPV(1), exp.getDouble("floatingLegNPV"));
                check(failures, name, "npv",            ns.NPV(),     exp.getDouble("npv"));
                checkExact(failures, name, "nFixedCoupons",  ns.fixedLeg().size(),    exp.getInt("nFixedCoupons"));
                checkExact(failures, name, "nFloatCoupons",  ns.floatingLeg().size(), exp.getInt("nFloatCoupons"));
            } catch (final Exception e) {
                failures.add("[vector_gearing_spread] exception: " + e.getMessage());
            }

            // vector_gearing_spread_receiver (Receiver)
            try {
                final String name = "vector_gearing_spread_receiver";
                final NonstandardSwap ns = new NonstandardSwap(
                        VanillaSwap.Type.Receiver,
                        fixedNom, floatNom, fixedSch, fixedRates, fixedDc,
                        floatSch, idx, gearings, spreads, dc);
                ns.setPricingEngine(dse);
                final Case ref = reader.getCase(name);
                final JSONObject exp = (JSONObject) ref.expectedRaw();
                check(failures, name, "fixedLegNPV",    ns.legNPV(0), exp.getDouble("fixedLegNPV"));
                check(failures, name, "floatingLegNPV", ns.legNPV(1), exp.getDouble("floatingLegNPV"));
                check(failures, name, "npv",            ns.NPV(),     exp.getDouble("npv"));
                checkExact(failures, name, "nFixedCoupons",  ns.fixedLeg().size(),    exp.getInt("nFixedCoupons"));
                checkExact(failures, name, "nFloatCoupons",  ns.floatingLeg().size(), exp.getInt("nFloatCoupons"));
            } catch (final Exception e) {
                failures.add("[vector_gearing_spread_receiver] exception: " + e.getMessage());
            }
        }

        // ── Groups D, E, F: capital exchange flags ────────────────────────────
        {
            final Schedule fixedSch = makeSchedule(cal, start, 3, 1, TimeUnit.Years);
            final Schedule floatSch = makeSchedule(cal, start, 3, 3, TimeUnit.Months);

            final double[] fixedNom  = {100.0, 70.0, 50.0};
            final double[] floatNom  = {100, 100, 100,
                                          70,  70,  70,
                                          50,  50,  50,
                                          40,  40,  40};
            final double[] fixedRates = {0.03, 0.035, 0.04};

            checkCapEx(failures, reader, dse, idx, dc, fixedDc,
                    fixedNom, floatNom, fixedSch, floatSch, fixedRates,
                    "intermediate_capital_exchange", true,  false);
            checkCapEx(failures, reader, dse, idx, dc, fixedDc,
                    fixedNom, floatNom, fixedSch, floatSch, fixedRates,
                    "final_capital_exchange", false, true);
            checkCapEx(failures, reader, dse, idx, dc, fixedDc,
                    fixedNom, floatNom, fixedSch, floatSch, fixedRates,
                    "both_capital_exchanges",   true,  true);
        }

        // ── Group G: flat rates, varied tenors ────────────────────────────────
        {
            class GC {
                double rate; int tenor; double spread;
                GC(double r, int t, double s) { rate=r; tenor=t; spread=s; }
            }
            GC[] gcs = {
                new GC(0.02, 2, 0.0),
                new GC(0.03, 3, 0.0),
                new GC(0.04, 5, 0.0),
                new GC(0.05, 5, 0.002),
                new GC(0.06, 7, 0.0),
                new GC(0.035,10, 0.001),
            };
            for (int i = 0; i < gcs.length; i++) {
                final GC gc = gcs[i];
                final String name = "flat_rate_" + i;
                try {
                    final Schedule fixedSch = makeSchedule(cal, start, gc.tenor, 1, TimeUnit.Years);
                    final Schedule floatSch = makeSchedule(cal, start, gc.tenor, 3, TimeUnit.Months);
                    final int nFixed = fixedSch.size() - 1;
                    final int nFloat = floatSch.size() - 1;
                    final double[] fixedNom   = repeat(100.0, nFixed);
                    final double[] floatNom   = repeat(100.0, nFloat);
                    final double[] fixedRates = repeat(gc.rate, nFixed);

                    final NonstandardSwap ns = new NonstandardSwap(
                            VanillaSwap.Type.Payer,
                            fixedNom, floatNom, fixedSch, fixedRates, fixedDc,
                            floatSch, idx, 1.0, gc.spread, dc, false, false);
                    ns.setPricingEngine(dse);

                    final Case ref = reader.getCase(name);
                    final JSONObject exp = (JSONObject) ref.expectedRaw();
                    check(failures, name, "fixedLegNPV",    ns.legNPV(0), exp.getDouble("fixedLegNPV"));
                    check(failures, name, "floatingLegNPV", ns.legNPV(1), exp.getDouble("floatingLegNPV"));
                    check(failures, name, "npv",            ns.NPV(),     exp.getDouble("npv"));
                    checkExact(failures, name, "nFixedCoupons",  ns.fixedLeg().size(),    exp.getInt("nFixedCoupons"));
                    checkExact(failures, name, "nFloatCoupons",  ns.floatingLeg().size(), exp.getInt("nFloatCoupons"));
                } catch (final Exception e) {
                    failures.add("[" + name + "] exception: " + e.getMessage());
                }
            }
        }

        // ── Final report ──────────────────────────────────────────────────────
        if (!failures.isEmpty()) {
            final StringBuilder sb = new StringBuilder(
                    "NonstandardSwapTest: " + failures.size() + " failure(s):\n");
            for (final String f : failures) sb.append("  ").append(f).append('\n');
            fail(sb.toString());
        }
    }


    // ── Private helpers ───────────────────────────────────────────────────────

    /** Build a schedule anchored at {@code start}, advancing {@code tenorYears}
     *  years in steps of {@code period}. Mirrors the probe's makeSchedule(). */
    private static Schedule makeSchedule(final Calendar cal, final Date start,
            final int tenorYears, final int periodValue, final TimeUnit unit) {
        final Date end = cal.advance(start,
                new Period(tenorYears, TimeUnit.Years),
                BusinessDayConvention.ModifiedFollowing, false);
        return new Schedule(start, end,
                new Period(periodValue, unit), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);
    }

    private static double[] repeat(final double v, final int n) {
        final double[] a = new double[n];
        Arrays.fill(a, v);
        return a;
    }

    private void checkGroup(
            final List<String> failures,
            final ReferenceReader reader,
            final DiscountingSwapEngine dse,
            final Euribor3M idx,
            final DayCounter dc,
            final DayCounter fixedDc,
            final Calendar cal,
            final Date start,
            final String name,
            final VanillaSwap.Type type,
            final double[] fixedNom,
            final double[] floatNom,
            final int fixedTenorYears, final int fixedPeriod, final TimeUnit fixedUnit,
            final int floatTenorYears, final int floatPeriod, final TimeUnit floatUnit,
            final double[] fixedRates,
            final double gearing,
            final double spread,
            final boolean intermCap,
            final boolean finalCap) {
        try {
            final Schedule fixedSch = makeSchedule(cal, start, fixedTenorYears, fixedPeriod, fixedUnit);
            final Schedule floatSch = makeSchedule(cal, start, floatTenorYears, floatPeriod, floatUnit);

            final NonstandardSwap ns = new NonstandardSwap(
                    type, fixedNom, floatNom, fixedSch, fixedRates, fixedDc,
                    floatSch, idx, gearing, spread, dc, intermCap, finalCap);
            ns.setPricingEngine(dse);

            final Case ref = reader.getCase(name);
            final JSONObject exp = (JSONObject) ref.expectedRaw();
            check(failures, name, "fixedLegNPV",    ns.legNPV(0), exp.getDouble("fixedLegNPV"));
            check(failures, name, "floatingLegNPV", ns.legNPV(1), exp.getDouble("floatingLegNPV"));
            check(failures, name, "npv",            ns.NPV(),     exp.getDouble("npv"));
            checkExact(failures, name, "nFixedCoupons",  ns.fixedLeg().size(),    exp.getInt("nFixedCoupons"));
            checkExact(failures, name, "nFloatCoupons",  ns.floatingLeg().size(), exp.getInt("nFloatCoupons"));
        } catch (final Exception e) {
            failures.add("[" + name + "] exception: " + e);
        }
    }

    private void checkCapEx(
            final List<String> failures,
            final ReferenceReader reader,
            final DiscountingSwapEngine dse,
            final Euribor3M idx,
            final DayCounter dc,
            final DayCounter fixedDc,
            final double[] fixedNom,
            final double[] floatNom,
            final Schedule fixedSch,
            final Schedule floatSch,
            final double[] fixedRates,
            final String name,
            final boolean intermCap,
            final boolean finalCap) {
        try {
            final NonstandardSwap ns = new NonstandardSwap(
                    VanillaSwap.Type.Payer,
                    fixedNom, floatNom, fixedSch, fixedRates, fixedDc,
                    floatSch, idx, 1.0, 0.0, dc, intermCap, finalCap);
            ns.setPricingEngine(dse);

            final Case ref = reader.getCase(name);
            final JSONObject exp = (JSONObject) ref.expectedRaw();
            check(failures, name, "fixedLegNPV",    ns.legNPV(0), exp.getDouble("fixedLegNPV"));
            check(failures, name, "floatingLegNPV", ns.legNPV(1), exp.getDouble("floatingLegNPV"));
            check(failures, name, "npv",            ns.NPV(),     exp.getDouble("npv"));
            checkExact(failures, name, "nFixedCoupons",  ns.fixedLeg().size(),    exp.getInt("nFixedCoupons"));
            checkExact(failures, name, "nFloatCoupons",  ns.floatingLeg().size(), exp.getInt("nFloatCoupons"));
        } catch (final Exception e) {
            failures.add("[" + name + "] exception: " + e);
        }
    }

    // ── Assertion helpers ─────────────────────────────────────────────────────

    /**
     * TIGHT check: abs 1e-14 + rel 1e-12. Used for NPV values.
     * (NonstandardSwap NPV = pure discount arithmetic, no transcendentals.)
     */
    private static void check(final List<String> failures,
            final String caseName, final String field,
            final double got, final double want) {
        if (!Tolerance.tight(got, want)) {
            failures.add(String.format(
                    "[%s.%s] got=%.15e  want=%.15e  diff=%.3e",
                    caseName, field, got, want, Math.abs(got - want)));
        }
    }

    private static void checkTight(final List<String> failures,
            final String caseName, final String field,
            final double got, final double want) {
        check(failures, caseName, field, got, want);
    }

    private static void checkExact(final List<String> failures,
            final String caseName, final String field,
            final int got, final int want) {
        if (got != want) {
            failures.add(String.format(
                    "[%s.%s] got=%d  want=%d", caseName, field, got, want));
        }
    }
}
