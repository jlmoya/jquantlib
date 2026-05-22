/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.capfloor.gaussian1d;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gsr;
import org.jquantlib.pricingengines.capfloor.gaussian1d.Gaussian1dCapFloorEngine;
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
 * Phase 2j WI-2.2 fingerprint test for {@link Gaussian1dCapFloorEngine}.
 *
 * <p>Cross-validates {@code CapFloor.NPV()} for a 54-cell grid of cap/floor
 * instruments priced under a vol-step Gsr Gaussian1d model against
 * {@code migration-harness/cpp/probes/pricingengines/capfloor/gaussian1d_capfloor_engine_probe.cpp}
 * (oracle: C++ QuantLib v1.42.1).
 *
 * <p>The grid covers three engine variants (default, flat-extrapolation,
 * no-extrapolation/narrow-stddevs), three tenors (2Y, 4Y, 6Y), two types
 * (Cap, Floor) and three strikes (2%/3%/4%). Single {@code @Test} with
 * collect-all-failures pattern.
 *
 * <p>Tier: {@link Tolerance#loose} (A19 — numerical Gaussian quadrature
 * integration; accumulated errors reach ~1e-9, well within LOOSE but above
 * TIGHT, same justification as WI-2.1 Gaussian1dSwaptionEngine).
 */
public class Gaussian1dCapFloorEngineTest {

    private static final Date EVAL = new Date(15, Month.January, 2026);
    private static final double FLAT_RATE = 0.03;
    private static final double REVERSION = 0.01;
    private static final double NOMINAL = 100.0;

    @Test
    public void gaussian1dCapFloorEngine_npvMatchesCpp() {
        new Settings().setEvaluationDate(EVAL);

        // ── Fixture (mirrors gaussian1d_capfloor_engine_probe.cpp) ──
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();

        final YieldTermStructure flat = new FlatForward(
                EVAL, new Handle<Quote>(new SimpleQuote(FLAT_RATE)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx = new Euribor3M(ts);

        // Schedule starts 3M after eval (mirrors probe — avoids past-fixing issues).
        final Date schedStart = cal.advance(EVAL, new Period(3, TimeUnit.Months));

        final List<Date> volStepDates = new ArrayList<>();
        volStepDates.add(EVAL.add(new Period(1, TimeUnit.Years)));
        volStepDates.add(EVAL.add(new Period(2, TimeUnit.Years)));
        volStepDates.add(EVAL.add(new Period(5, TimeUnit.Years)));
        final double[] vols = new double[]{0.01, 0.012, 0.014, 0.016};
        final Gsr gsr = new Gsr(ts, volStepDates, vols, REVERSION);

        // ── Walk reference cases ──
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/capfloor/gaussian1d_capfloor_engine");

        final List<String> failures = new ArrayList<>();

        for (final String name : reader.caseNames()) {
            final Case c = reader.getCase(name);
            final JSONObject in = c.inputs();
            final JSONObject exp = (JSONObject) c.expectedRaw();
            try {
                final double npv = priceCase(cal, idx, dc, gsr, schedStart, in);
                final double cpp = exp.getDouble("npv");
                // A19: numerical Gaussian quadrature integration engine;
                // accumulated errors reach ~1e-9 — LOOSE tier required.
                if (!Tolerance.loose(npv, cpp)) {
                    failures.add(name + ": LOOSE mismatch java=" + npv
                            + " cpp=" + cpp + " (diff="
                            + Math.abs(npv - cpp) + ")");
                }
            } catch (final RuntimeException e) {
                failures.add(name + ": exception "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("Gaussian1dCapFloorEngineTest: " + failures.size()
                    + " mismatch(es)\n  "
                    + String.join("\n  ", failures.subList(0,
                            Math.min(30, failures.size())))
                    + (failures.size() > 30
                            ? "\n  ... (" + (failures.size() - 30) + " more)" : ""));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //   Helpers
    // ──────────────────────────────────────────────────────────────────────

    private static double priceCase(
            final Calendar cal,
            final Euribor3M idx,
            final DayCounter dc,
            final Gsr gsr,
            final Date schedStart,
            final JSONObject in) {

        final int integrationPoints = in.getInt("integration_points");
        final double stddevs = in.getDouble("stddevs");
        final boolean extrapolatePayoff = in.getBoolean("extrapolate_payoff");
        final boolean flatPayoffExtrapolation =
                in.getBoolean("flat_payoff_extrapolation");
        final int tenorYears = in.getInt("tenor_years");
        final String typeStr = in.getString("type");
        final double strike = in.getDouble("strike");

        final Date end = cal.advance(schedStart,
                new Period(tenorYears, TimeUnit.Years),
                BusinessDayConvention.ModifiedFollowing);

        final Schedule sched = new Schedule(
                schedStart, end, new Period(3, TimeUnit.Months), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        // Build Leg from IborCoupons — mirrors C++ IborLeg in the probe.
        final Leg leg = new IborLeg(sched, idx)
                .withNotionals(NOMINAL)
                .withPaymentAdjustment(BusinessDayConvention.ModifiedFollowing)
                .Leg();

        final List<Double> strikes = new ArrayList<>();
        strikes.add(strike);

        final CapFloor.Type cfType = "Cap".equals(typeStr)
                ? CapFloor.Type.Cap : CapFloor.Type.Floor;
        final CapFloor capFloor = new CapFloor(cfType, leg, strikes,
                new Handle<YieldTermStructure>(), null);

        capFloor.setPricingEngine(new Gaussian1dCapFloorEngine(
                gsr, integrationPoints, stddevs,
                extrapolatePayoff, flatPayoffExtrapolation,
                new Handle<YieldTermStructure>()));

        return capFloor.NPV();
    }
}
