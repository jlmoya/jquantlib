/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for AnalyticCapFloorEngine NPV cross-validation (Phase 2f WI-1).
 */
package org.jquantlib.testsuite.pricingengines.capfloor;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.capfloor.AnalyticCapFloorEngine;
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
 * Phase 2f WI-1 fingerprint test for {@link AnalyticCapFloorEngine}.
 *
 * <p>Cross-validates a 5Y Euribor3M cap struck at 5% under HullWhite
 * (a=0.1, sigma=0.01) against a C++ probe-generated reference.
 *
 * <p><strong>Tolerance tier</strong> — tight (1e-12 rel + 1e-14 abs).
 * The pricing path is closed-form: HullWhite::discountBondOption is the
 * Jamshidian decomposition (analytic Black formula on bond options),
 * fed deterministic schedule arithmetic. No iterative solver. Java and
 * C++ should agree to within floating-point noise.
 */
public class AnalyticCapFloorEngineTest {

    @Test
    public void hullWhiteCap_npvMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/capfloor/analyticcapfloorengine");
        final Case ref = reader.getCase("hw_5y_cap_at_5pct");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (must match analyticcapfloorengine_probe.cpp exactly) ----
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;

        final double flatRate = in.getDouble("flat_rate");
        final YieldTermStructure flat = new FlatForward(
                eval, new Handle<Quote>(new SimpleQuote(flatRate)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final IborIndex idx = new Euribor3M(ts);

        final int years = in.getInt("cap_years");
        final Period idxTenor = new Period(in.getInt("index_tenor_months"),
                TimeUnit.Months);
        // Start the schedule one period after eval so the first fixing date
        // is strictly in the future. This avoids relying on an
        // IborIndex past-fixing lookup (Java's IborIndex.fixing path NPEs
        // on missing-but-required-on-eval fixings, vs C++ falling through
        // to the forecast); the probe fixture mirrors the same shift.
        final Date scheduleStart = eval.add(idxTenor);
        final Date maturity = eval.add(new Period(years, TimeUnit.Years));
        final Schedule schedule = new Schedule(
                scheduleStart, maturity, idxTenor, cal, bdc, bdc,
                DateGeneration.Rule.Forward, false);

        final double nominal = in.getDouble("nominal");
        final Leg floatingLeg = new IborLeg(schedule, idx)
                .withNotionals(new Array(new double[] { nominal }))
                .withPaymentAdjustment(idx.businessDayConvention())
                .withFixingDays(0)
                .Leg();

        final double capStrike = in.getDouble("cap_strike");
        final CapFloor cap = new CapFloor(
                CapFloor.Type.Cap, floatingLeg,
                new ArrayList<Double>(Arrays.asList(Double.valueOf(capStrike))),
                ts, null);

        final double hwA = in.getDouble("hw_a");
        final double hwSigma = in.getDouble("hw_sigma");
        final HullWhite hw = new HullWhite(ts, hwA, hwSigma);
        cap.setPricingEngine(new AnalyticCapFloorEngine(hw, ts));

        final double npv = cap.NPV();
        final double expected = exp.getDouble("analytic_cap_npv");
        if (!Tolerance.tight(npv, expected)) {
            fail("AnalyticCapFloorEngine NPV: exp=" + expected + " got=" + npv);
        }
    }
}
