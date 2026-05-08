/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for CPICapFloor against
 QuantLib v1.42.1 via
 migration-harness/references/instruments/inflation_cap_floor.json (Phase 2r C.1).
*/
package org.jquantlib.testsuite.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.instruments.CPICapFloor;
import org.jquantlib.instruments.Option;
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
 * Probe-driven structural tests for {@link CPICapFloor}.
 *
 * <p>Mirrors the C++ probe (instruments/inflation_cap_floor_probe.cpp). Tests
 * structural metadata (fixingDate, payDate, observationLag, strike) that
 * doesn't require pricing — pricing engines for CPICapFloor are not part of
 * Phase 2r scope and would require a CPI vol surface (deferred).
 *
 * <p>Tier rationale: structural metadata — exact / TIGHT.
 */
public class CPICapFloorTest {

    private static final String REF_GROUP = "instruments/inflation_cap_floor";

    @Test
    public void cpiCapFloor_matchesCpp() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period observationLag = new Period(3, TimeUnit.Months);

        final ZeroInflationIndex zeroIndex = new UKRPI(freq, false, false);

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        // Cap (Call)
        final Date startDate = evalDate;
        final Date maturity = new Date(13, Month.August, 2012);
        final double baseCPI = 100.0;

        final CPICapFloor cap = new CPICapFloor(Option.Type.Call, 1.0e6,
                startDate, baseCPI, maturity, cal, bdc, cal, bdc, 0.03,
                zeroIndex, observationLag, CPI.InterpolationType.AsIndex);
        checkInstrument("cpicf_cap_AsIndex", cap, ref.getCase("cpicf_cap_AsIndex"),
                mismatches);

        // Floor (Put)
        final CPICapFloor floor = new CPICapFloor(Option.Type.Put, 1.0e6,
                startDate, baseCPI, maturity, cal, bdc, cal, bdc, 0.01,
                zeroIndex, observationLag, CPI.InterpolationType.AsIndex);
        checkInstrument("cpicf_floor_AsIndex", floor, ref.getCase("cpicf_floor_AsIndex"),
                mismatches);

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static void checkInstrument(final String label, final CPICapFloor inst,
                                        final Case c, final List<String> mismatches) {
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final String expOptionType = expected.getString("option_type");
        final String actOptionType = inst.type() == Option.Type.Call ? "Call" : "Put";
        if (!expOptionType.equals(actOptionType)) {
            mismatches.add(label + ".option_type: expected=" + expOptionType
                    + " actual=" + actOptionType);
        }

        if (!Tolerance.tight(inst.strike(), expected.getDouble("strike"))) {
            mismatches.add(label + ".strike: expected=" + expected.getDouble("strike")
                    + " actual=" + inst.strike());
        }
        if (!Tolerance.tight(inst.nominal(), expected.getDouble("nominal"))) {
            mismatches.add(label + ".nominal: expected=" + expected.getDouble("nominal")
                    + " actual=" + inst.nominal());
        }
        if (inst.fixingDate().serialNumber() != expected.getLong("fixingDate_serial")) {
            mismatches.add(label + ".fixingDate_serial: expected="
                    + expected.getLong("fixingDate_serial")
                    + " actual=" + inst.fixingDate().serialNumber());
        }
        if (inst.payDate().serialNumber() != expected.getLong("payDate_serial")) {
            mismatches.add(label + ".payDate_serial: expected="
                    + expected.getLong("payDate_serial")
                    + " actual=" + inst.payDate().serialNumber());
        }
        if (inst.observationLag().length() != expected.getInt("observationLag_months")) {
            mismatches.add(label + ".observationLag_months: expected="
                    + expected.getInt("observationLag_months")
                    + " actual=" + inst.observationLag().length());
        }
    }
}
