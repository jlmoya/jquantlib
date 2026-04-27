/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for BachelierCapFloorEngine NPV cross-validation (Phase 2f WI-1).
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
import org.jquantlib.pricingengines.capfloor.BachelierCapFloorEngine;
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
 * Phase 2f WI-1 fingerprint test for {@link BachelierCapFloorEngine}.
 *
 * <p>Cross-validates a 5Y Euribor3M cap struck at 5% under a constant
 * normal (Bachelier) volatility of 100 bp absolute.
 *
 * <p><strong>Tolerance tier</strong> — tight (1e-12 rel + 1e-14 abs).
 * Closed-form Bachelier formula sum over optionlets, plus deterministic
 * schedule arithmetic. No solver. Java and C++ should agree to within
 * floating-point noise.
 */
public class BachelierCapFloorEngineTest {

    @Test
    public void bachelierCap_npvMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/capfloor/bacheliercapfloorengine");
        final Case ref = reader.getCase("bachelier_5y_cap_at_5pct");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (must match bacheliercapfloorengine_probe.cpp exactly) ----
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
        final Date scheduleStart = eval.add(idxTenor);
        final Date maturity = eval.add(new Period(years, TimeUnit.Years));
        final Schedule schedule = new Schedule(
                scheduleStart, maturity, idxTenor, cal, bdc, bdc,
                DateGeneration.Rule.Forward, false);

        final double nominal = in.getDouble("nominal");
        // Use the index's default fixingDays (2 for Euribor3M); see
        // AnalyticCapFloorEngineTest for rationale.
        final Leg floatingLeg = new IborLeg(schedule, idx)
                .withNotionals(new Array(new double[] { nominal }))
                .withPaymentAdjustment(idx.businessDayConvention())
                .Leg();

        final double capStrike = in.getDouble("cap_strike");
        final CapFloor cap = new CapFloor(
                CapFloor.Type.Cap, floatingLeg,
                new ArrayList<Double>(Arrays.asList(Double.valueOf(capStrike))),
                ts, null);

        final double normalVol = in.getDouble("normal_vol");
        cap.setPricingEngine(new BachelierCapFloorEngine(ts, normalVol, dc));

        final double npv = cap.NPV();
        final double expected = exp.getDouble("bachelier_cap_npv");
        if (!Tolerance.tight(npv, expected)) {
            fail("BachelierCapFloorEngine NPV: exp=" + expected + " got=" + npv);
        }
    }
}
