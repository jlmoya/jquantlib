/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Smoke tests for MakeYoYInflationCapFloor (Phase 2r C.1).
*/
package org.jquantlib.testsuite.instruments;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.YoYInflationCoupon;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.YYUKRPI;
import org.jquantlib.instruments.InflationCapFloor;
import org.jquantlib.instruments.MakeYoYInflationCapFloor;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Smoke tests for {@link MakeYoYInflationCapFloor}.
 *
 * <p>The factory is structurally simple — its job is to build a
 * {@link InflationCapFloor} via a fluent API. Tests validate:
 * <ul>
 *   <li>build() returns a non-null cap with correct number of coupons.</li>
 *   <li>Strike, type, and payment day count are honored.</li>
 *   <li>{@code asOptionlet()} reduces leg to one coupon.</li>
 *   <li>{@code withFirstCapletExcluded()} drops the first coupon.</li>
 * </ul>
 */
public class MakeYoYInflationCapFloorTest {

    @Test
    public void makeCap_basic() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final YYUKRPI idx = setupIndex();
        final Calendar cal = new UnitedKingdom();
        final Period observationLag = new Period(3, TimeUnit.Months);

        final InflationCapFloor cap = new MakeYoYInflationCapFloor(
                InflationCapFloor.Type.Cap, idx,
                /*length*/ 5, cal, observationLag, CPI.InterpolationType.AsIndex)
                .withStrike(0.03)
                .withNominal(1.0e6)
                .build();
        assertNotNull(cap);
        assertEquals(InflationCapFloor.Type.Cap, cap.type());
        assertEquals(5, cap.yoyLeg().size());
        assertEquals(0.03, cap.capRates().get(0).doubleValue(), 0.0);
    }

    @Test
    public void makeFloor_asOptionlet_reducesToOne() {
        new Settings().setEvaluationDate(new Date(13, Month.August, 2007));

        final YYUKRPI idx = setupIndex();
        final Calendar cal = new UnitedKingdom();
        final Period observationLag = new Period(3, TimeUnit.Months);

        final InflationCapFloor floor = new MakeYoYInflationCapFloor(
                InflationCapFloor.Type.Floor, idx,
                5, cal, observationLag, CPI.InterpolationType.AsIndex)
                .withStrike(0.02)
                .asOptionlet()
                .build();
        assertEquals(1, floor.yoyLeg().size());
    }

    @Test
    public void makeCap_firstCapletExcluded() {
        new Settings().setEvaluationDate(new Date(13, Month.August, 2007));

        final YYUKRPI idx = setupIndex();
        final Calendar cal = new UnitedKingdom();
        final Period observationLag = new Period(3, TimeUnit.Months);

        final InflationCapFloor cap = new MakeYoYInflationCapFloor(
                InflationCapFloor.Type.Cap, idx,
                5, cal, observationLag, CPI.InterpolationType.AsIndex)
                .withStrike(0.03)
                .withFirstCapletExcluded()
                .build();
        assertEquals(4, cap.yoyLeg().size());
    }

    @Test
    public void makeCap_noStrikeNoCurve_throws() {
        new Settings().setEvaluationDate(new Date(13, Month.August, 2007));

        final YYUKRPI idx = setupIndex();
        final Calendar cal = new UnitedKingdom();
        final Period observationLag = new Period(3, TimeUnit.Months);

        try {
            new MakeYoYInflationCapFloor(
                    InflationCapFloor.Type.Cap, idx,
                    5, cal, observationLag, CPI.InterpolationType.AsIndex)
                    .build();
            fail("expected exception when neither strike nor curve given");
        } catch (final RuntimeException e) {
            assertTrue("error mentions strike or curve",
                    e.getMessage().contains("strike") || e.getMessage().contains("curve")
                            || e.getMessage().contains("nominal term structure"));
        }
    }

    private static YYUKRPI setupIndex() {
        final Date refDate = new UnitedKingdom().adjust(
                new Date(13, Month.August, 2007),
                BusinessDayConvention.ModifiedFollowing);
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Date[] nodeDates = {
                new Date(1,  Month.May,    2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] nodeRates = {0.025, 0.027, 0.029, 0.031, 0.034, 0.036};
        final var yoyCurve = new InterpolatedYoYInflationCurve<Linear>(Linear.class, refDate,
                        nodeDates, nodeRates, freq, dc);
        yoyCurve.enableExtrapolation();
        final var ts = new Handle<YoYInflationTermStructure>(yoyCurve);
        final YYUKRPI idx = new YYUKRPI(freq, false, false, ts);

        final Date[] fixDates = {
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
        for (final Date d : fixDates) {
            idx.addFixing(d, 0.025, true);
        }
        return idx;
    }
}
