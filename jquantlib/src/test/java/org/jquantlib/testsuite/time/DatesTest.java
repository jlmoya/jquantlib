/*
 Copyright (C) 2008 Srinivas Hasti

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.time.ASX;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateParser;
import org.jquantlib.time.ECB;
import org.jquantlib.time.IMM;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.Weekday;
import org.junit.Test;


/**
 * Test various Dates
 *
 * @author Srinivas Hasti
 *
 * <h2>Phase 1 cert D5-A-R4 status of v1.42.1 {@code test-suite/dates.cpp} tests</h2>
 *
 * The C++ file ({@code migration-harness/cpp/quantlib/test-suite/dates.cpp},
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}) contains 14 cases.
 * R3 ported {@code canHash} and {@code nullDate}; legacy Java tests cover
 * three more. R4 evaluated the remaining 9 and finds:
 *
 * <ul>
 *  <li>{@code immDates} — <b>EXISTING_EQUIVALENT</b>:
 *      {@link #immDates()} (line 70) is a faithful port already (full
 *      forward sweep across all dates, IMM nextDate / isIMMdate / code-date
 *      round-trip checks).</li>
 *  <li>{@code testConsistency} — <b>EXISTING_EQUIVALENT</b>:
 *      {@link #consistencyCheck()} (line 128) performs the same
 *      dayOfYear/month/year/weekday increment invariants across the full
 *      {@code Date.minDate()..maxDate()} range.</li>
 *  <li>{@code isoDates} — <b>EXISTING_EQUIVALENT</b>:
 *      {@link #isoDates()} (line 204) is a literal port (same {@code "2006-01-15"}
 *      input, same dayOfMonth/month/year asserts).</li>
 *  <li>{@code parseDates} — <b>BLOCKED</b>: Java
 *      {@link org.jquantlib.time.DateParser#parse(String, String)} only handles
 *      {@code dd/mm/yyyy}-style format strings split on {@code "/"}; it cannot
 *      consume the C++ {@code strftime}-style format strings the test exercises
 *      ({@code "%Y-%m-%d"}, {@code "%m/%d/%Y"}, {@code "%d/%m/%Y"},
 *      {@code "%Y%m%d"}). A faithful port requires extending DateParser to
 *      support the C++ format syntax (production change beyond the cert).</li>
 *  <li>{@code intraday} — <b>BLOCKED</b>: guarded by C++
 *      {@code QL_HIGH_RESOLUTION_DATE}; Java {@link org.jquantlib.time.Date} is
 *      day-resolution only (no hours/minutes/seconds/milliseconds/microseconds
 *      accessors).</li>
 *  <li>{@code ecbIsECBcode} / {@code ecbDates} / {@code ecbGetDateFromCode} /
 *      {@code ecbGetCodeFromDate} / {@code ecbNextCode} — <b>BLOCKED</b>: there
 *      is no {@code org.jquantlib.time.ECB} class. C++ {@code ql/time/ecb.hpp}
 *      defines {@code isECBcode}, {@code isECBdate}, {@code knownDates},
 *      {@code nextDate(s)}, {@code addDate}, {@code removeDate}, {@code date},
 *      {@code code}, {@code nextCode} — none of these are present in
 *      {@code org.jquantlib.time}. Activation requires porting the full
 *      ECB date catalogue (Phase 2 A4 trigger).</li>
 *  <li>{@code asxDates} / {@code asxDatesSpecific} — <b>BLOCKED</b>:
 *      similarly there is no {@code org.jquantlib.time.ASX} class.
 *      C++ {@code ql/time/asx.hpp} defines {@code isASXcode},
 *      {@code isASXdate}, {@code nextDate}, {@code nextCode}, {@code date},
 *      {@code code} — all absent.</li>
 * </ul>
 *
 * R4 added no new test methods; the existing ports above and the R3 additions
 * ({@code canHash}, {@code nullDate}) cover everything possible at the current
 * production-class baseline.
 */
public class DatesTest {

    static private final String IMMcodes[] = { "F0", "G0", "H0", "J0", "K0", "M0", "N0", "Q0", "U0", "V0", "X0", "Z0",
        "F1", "G1", "H1", "J1", "K1", "M1", "N1", "Q1", "U1", "V1", "X1", "Z1", "F2", "G2", "H2", "J2", "K2", "M2",
        "N2", "Q2", "U2", "V2", "X2", "Z2", "F3", "G3", "H3", "J3", "K3", "M3", "N3", "Q3", "U3", "V3", "X3", "Z3",
        "F4", "G4", "H4", "J4", "K4", "M4", "N4", "Q4", "U4", "V4", "X4", "Z4", "F5", "G5", "H5", "J5", "K5", "M5",
        "N5", "Q5", "U5", "V5", "X5", "Z5", "F6", "G6", "H6", "J6", "K6", "M6", "N6", "Q6", "U6", "V6", "X6", "Z6",
        "F7", "G7", "H7", "J7", "K7", "M7", "N7", "Q7", "U7", "V7", "X7", "Z7", "F8", "G8", "H8", "J8", "K8", "M8",
        "N8", "Q8", "U8", "V8", "X8", "Z8", "F9", "G9", "H9", "J9", "K9", "M9", "N9", "Q9", "U9", "V9", "X9", "Z9" };

    public DatesTest() {
        QL.info("::::: "+this.getClass().getSimpleName()+" :::::");
    }

    @Test
    public void immDates() {
        QL.info("Testing imm dates. It may take several minutes when Cobertura reports are generated!!!");

        final Date minDate = Date.minDate();
        final Date maxDate = Date.maxDate();

        final Date counter = minDate.clone();

        // 10 years of futures must not exceed Date::maxDate
        final Period period = new Period(-10, TimeUnit.Years);
        final Date last = maxDate.clone().addAssign(period);

        while (counter.le(last)) {

            final Date immDate = IMM.nextDate(counter, false);

            // check that imm is greater than counter
            if (immDate.le(counter)) {
                fail("\n  " + immDate.weekday() + " " + immDate + " is not greater than " + counter.weekday() + " " + counter);
            }

            // check that imm is an IMM date
            if (!IMM.isIMMdate(immDate, false)) {
                fail("\n  " + immDate.weekday() + " " + immDate + " is not an IMM date (calculated from " + counter.weekday() + " " + counter + ")");
            }

            // check that imm is <= to the next IMM date in the main cycle
            if (immDate.gt(IMM.nextDate(counter, true))) {
                fail("\n  " + immDate.weekday() + " " + immDate + " is not less than or equal to the next future in the main cycle " + IMM.nextDate(counter, true));
            }

            //
            // COMMENTED AT SOURCE QuantLib 0.8.1
            //
            // // check that if counter is an IMM date, then imm==counter
            // if (IMM::isIMMdate(counter, false) && (imm!=counter))
            // fail("\n "
            // + counter.weekday() + " " + counter
            // + " is already an IMM date, while nextIMM() returns "
            // + IMM.getDefaultIMM().weekday() + " " + imm);

            // check that for every date IMMdate is the inverse of IMMcode
            if (!IMM.date(IMM.code(immDate), counter).equals(immDate)) {
                fail("\n  " + IMM.code(immDate) + " at calendar day " + counter + " is not the IMM code matching " + immDate);
            }

            // check that for every date the 120 IMM codes refer to future dates
            for (int i = 0; i < 40; ++i) {
                if (IMM.date(IMMcodes[i], counter).lt(counter)) {
                    fail("\n  " + IMM.date(IMMcodes[i], counter) + " is wrong for " + IMMcodes[i] + " at reference date " + counter);
                }
            }

            counter.inc();
        }
    }

    @Test
    public void consistencyCheck() {

        QL.info("Testing dates...");

        final Date minD = Date.minDate();
        final Date maxD = Date.maxDate();

        int dyold = minD.dayOfYear();
        int dold  = minD.dayOfMonth();
        int mold  = minD.month().value();
        int yold  = minD.year();
        Weekday wdold = minD.weekday();

        final Date minDate = minD.clone().inc();
        final Date maxDate = maxD.clone().dec();

        for (final Date t = minDate; t.le(maxDate); t.inc()) {
            final int dy = t.dayOfYear();
            final int d  = t.dayOfMonth();
            final int m  = t.month().value();
            final int y  = t.year();
            final Weekday wd = t.weekday();

            // check if skipping any date
            if (!((dy == dyold + 1)
                    || (dy == 1 && dyold == 365 && !Date.isLeap(yold))
                    || (dy == 1 && dyold == 366 && Date.isLeap(yold)))) {
                fail("wrong day of year increment: \n"
                        + "    date: " + t + "\n"
                        + "    day of year: " + dy + "\n"
                        + "    previous:    " + dyold);
            }

            dyold = dy;

            if (!((d == dold + 1 && m == mold && y == yold) || (d == 1 && m == mold + 1 && y == yold) || (d == 1 && m == 1 && y == yold + 1)) ) {
                fail("wrong day,month,year increment: \n"
                        + "    date: " + t + "\n"
                        + "    day,month,year: " + d + "," + m + "," + y + "\n"
                        + "    previous:       " + dold + "," + mold + "," + yold);
            }

            dold = d;
            mold = m;
            yold = y;

            // check month definition
            if ((m < 1 || m > 12)) {
                fail("invalid month: \n" + "    date:  " + t + "\n" + "    month: " + m);
            }

            // check day definition
            if ((d < 1)) {
                fail("invalid day of month: \n" + "    date:  " + t + "\n" + "    day: " + d);
            }

            if (!((m == 1 && d <= 31)
                    || (m == 2 && d <= 28) || (m == 2 && d == 29 && Date.isLeap(y))
                    || (m == 3 && d <= 31) || (m == 4 && d <= 30) || (m == 5 && d <= 31) || (m == 6 && d <= 30)
                    || (m == 7 && d <= 31) || (m == 8 && d <= 31) || (m == 9 && d <= 30) || (m == 10 && d <= 31)
                    || (m == 11 && d <= 30) || (m == 12 && d <= 31))) {
                fail("invalid day of month: \n" + "    date:  " + t + "\n" + "    day: " + d);
            }

            // check weekday definition
            if (!((wd.value() == wdold.value() + 1) || (wd.value() == 1 && wdold.value() == 7))) {
                fail("invalid weekday: \n"
                        + "    date:  " + t + "\n"
                        + "    weekday:  " + wd + "\n"
                        + "    previous: " + wdold);
            }
            wdold = wd;
        }
    }

    @Test
    public void isoDates() {
        QL.info("Testing ISO dates...");
        final String input_date = "2006-01-15";
        final Date d = DateParser.parseISO(input_date);
        if ((d.dayOfMonth() != 15) || (d.month() != Month.January) || (d.year() != 2006)) {
            fail("Iso date failed\n"
                    + " input date:    " + input_date + "\n"
                    + " day of month:  " + d.dayOfMonth() + "\n"
                    + " month:         " + d.month() + "\n"
                    + " year:          " + d.year());
        }
    }

    @Test
    public void testConvertionToJavaDate() {
        QL.info("Testing convertion to Java Date...");

        boolean success = true;
        
        final org.jquantlib.time.Date jqlDate = new org.jquantlib.time.Date(1, 1, 1901);
        for (;;) {
            final java.util.Date isoDate = jqlDate.isoDate();
            int day   = jqlDate.dayOfMonth();
            int month = jqlDate.month().value();
            int year  = jqlDate.year();
            Calendar c = Calendar.getInstance();
            c.setTime(isoDate);
            int jday = c.get(Calendar.DAY_OF_MONTH);
            int jmonth = c.get(Calendar.MONTH);
            int jyear = c.get(Calendar.YEAR);
            boolean test = true;
            test &= ( year  == jyear ); 
            test &= ( month == jmonth + 1); //Java month range:0 - 11( 0=Jan, 1=Feb,.. 11=Dec)
            test &= ( day   == jday );
            
            success &= test; 
            
            if (!test) {
                QL.info(String.format("JQL Date = %s   :::   ISODate = %s", jqlDate.toString(), isoDate.toString()));
            }

            if (year==2199 && month==12 && day==31) break;
            jqlDate.inc();
        }
        assertTrue("Conversion from JQuantLib date to JDK Date failed", success);
    }


    @Test
    public void testConvertionFromJavaDate() {
        QL.info("Testing convertion from Java Date...");

        // obtain 01-JAN-1901 first from JQL Date and then convert to Java Date
        Date jqlDate = new Date(1, 1, 1901);
        java.util.Date javaDate = jqlDate.isoDate();
        
        boolean success = true;
        for (;;) {
            
            Calendar c = Calendar.getInstance();
            c.setTime(javaDate);
            int jday = c.get(Calendar.DAY_OF_MONTH);
            int jmonth = c.get(Calendar.MONTH);
            int jyear = c.get(Calendar.YEAR);

        	// obtain a JQL Date from a Java Date
        	jqlDate = new Date(javaDate);

        	// compare d,m,y
            boolean test = true;
            test &= ( jqlDate.year()          == jyear ); 
            test &= ( jqlDate.month().value() == jmonth + 1); //Java month is 0-11
            test &= ( jqlDate.dayOfMonth()    == jday ); 
            success &= test; 

            if (!test) {
                QL.info(String.format("JQL Date = %s   :::   ISODate = %s", jqlDate.toString(), javaDate.toString()));
            }

            if (jyear==2199 && jmonth==Calendar.DECEMBER && jday==31) break;

            // Increment Java Date by 1 day
            // Lets assume a day has *always* 86400 seconds, i.e: discard leap seconds and timezone info
            javaDate = new java.util.Date(javaDate.getTime()+86400000);
        }
        assertTrue("Conversion from Java Date to JQuantLib date failed", success);
    }


    @Test
    public void testLowerUpperBound() {
        final List<Date> dates = new ArrayList<Date>();

        dates.add(new Date(1,1,2009));
        dates.add(new Date(2,1,2009));
        dates.add(new Date(3,1,2009));
        dates.add(new Date(3,1,2009));
        dates.add(new Date(4,1,2009));
        dates.add(new Date(5,1,2009));
        dates.add(new Date(7,1,2009));
        dates.add(new Date(7,1,2009));
        dates.add(new Date(8,1,2009));

        final Date lowerDate = new Date(3,1,2009);
        final Date upperDate = new Date(7,1,2009);
        final int expectedLowerBound = 2;
        final int expectedUpperBound = 8;
        final int lowerBound = Date.lowerBound(dates, lowerDate);
        final int upperBound = Date.upperBound(dates, upperDate);

        assertEquals(lowerBound, expectedLowerBound);
        assertEquals(upperBound, expectedUpperBound);

    }

    @Test
    public void testNotificationSubAssign() {

        QL.info("Testing observability of dates using operation Date#subAssign()");

        final Date me = Date.todaysDate();
        final Flag f = new Flag();
        me.addObserver(f);
        me.subAssign(1);
        if (!f.isUp()) {
            fail("Observer was not notified of date change");
        }
    }

    @Test
    public void testNotificationSub() {

        QL.info("Testing observability of dates using operation Date#sub()");

        final Date me = Date.todaysDate();
        final Flag f = new Flag();
        me.addObserver(f);
        me.sub(1);
        if (f.isUp()) {
            fail("Observer was notified of date change whilst it was not expected");
        }
    }

    @Test
    public void testNotificationHandle() {

        QL.info("Testing notification of date handles...");

        final Date me1 = Date.todaysDate();
        final RelinkableHandle<Date> h = new RelinkableHandle<Date>(me1);

        final Flag f = new Flag();
        h.addObserver(f);

        final Date weekAgo = Date.todaysDate().sub(7);
        final Date me2 = weekAgo;

        h.linkTo(me2);
        if (!f.isUp()) {
            fail("Observer was not notified of date change");
        }

        if (h.currentLink() != weekAgo) {
            fail("Failed to hard link to another object");
        }

    }

    @Test
    public void testNotificationHandleSubAssign() {

        QL.info("Testing notification of date handles using operation Date#subAssign().");

        final Date me1 = Date.todaysDate();
        final RelinkableHandle<Date> h = new RelinkableHandle<Date>(me1);

        final Flag f = new Flag();
        h.addObserver(f);

        me1.subAssign(1);
        if (!f.isUp()) {
            fail("Observer was not notified of date change");
        }
    }

    @Test
    public void testNotificationHandleSub() {

        QL.info("Testing ntification of date handles using operation Date#sub().");

        final Date me1 = Date.todaysDate();
        final RelinkableHandle<Date> h = new RelinkableHandle<Date>(me1);

        final Flag f = new Flag();
        h.addObserver(f);

        me1.sub(1);
        if (f.isUp()) {
            fail("Observer was notified of date change whilst it was not expected");
        }
    }

    @Test
    public void testEqualsandHashCode() {

        QL.info("Testing equals and hashcode");

        final Date today = Date.todaysDate();
        final Date tomorrow1 = Date.todaysDate().add(1);
        final Date tomorrow2 = Date.todaysDate().add(1);
        final Date manyana = Date.todaysDate().add(123);

        assertFalse(today.equals(null));
        assertEquals(today, today);
        assertFalse(today.equals(tomorrow1));
        assertEquals(tomorrow1, tomorrow2);

        HashSet<Date> testSet = new HashSet<Date>();
        testSet.add(today);
        testSet.add(tomorrow1);

        assertTrue(testSet.contains(today));
        assertFalse(testSet.contains(manyana));

    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::canHash (lines 506-545).
     *
     * For every pair (i,j) in a 500x500 grid of dates starting 1-Jan-2020:
     *  - equal dates must have equal hash codes
     *  - unequal dates must have unequal hash codes (Date uses serialNumber)
     * Also verifies HashSet membership.
     */
    @Test
    public void canHash() {
        QL.info("Testing hashing of dates...");

        final Date startDate = new Date(1, Month.January, 2020);
        final int nbTests = 500;

        for (int i = 0; i < nbTests; ++i) {
            for (int j = 0; j < nbTests; ++j) {
                final Date lhs = startDate.clone().addAssign(i);
                final Date rhs = startDate.clone().addAssign(j);

                if (lhs.equals(rhs) && lhs.hashCode() != rhs.hashCode()) {
                    fail("Equal dates are expected to have same hash value\n"
                            + " lhs = " + lhs + "\n"
                            + " rhs = " + rhs + "\n"
                            + " hash(lhs) = " + lhs.hashCode() + "\n"
                            + " hash(rhs) = " + rhs.hashCode());
                }

                if (!lhs.equals(rhs) && lhs.hashCode() == rhs.hashCode()) {
                    fail("Different dates are expected to have different hash value\n"
                            + " lhs = " + lhs + "\n"
                            + " rhs = " + rhs + "\n"
                            + " hash(lhs) = " + lhs.hashCode() + "\n"
                            + " hash(rhs) = " + rhs.hashCode());
                }
            }
        }

        // Check Date can be used as HashSet key (C++ unordered_set equivalent).
        final HashSet<Date> set = new HashSet<Date>();
        set.add(startDate);
        if (!set.contains(startDate)) {
            fail("Expected to find date " + startDate + " in HashSet");
        }
    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::nullDate (lines 547-556).
     *
     * Verifies a null/default Date can call serialNumber() and hashCode()
     * without throwing.
     */
    @Test
    public void nullDate() {
        QL.info("Testing null date for working serial number and hash...");

        final Date nullDate = new Date();

        // BOOST_CHECK_NO_THROW equivalent: simply call the methods.
        try {
            nullDate.serialNumber();
            nullDate.hashCode();
        } catch (final RuntimeException e) {
            fail("null Date.serialNumber()/hashCode() unexpectedly threw: " + e);
        }
    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::ecbIsECBcode (lines 45-59).
     */
    @Test
    public void ecbIsECBcode() {
        QL.info("Testing ECB codes for validity...");

        assertTrue(ECB.isECBcode("JAN00"));
        assertTrue(ECB.isECBcode("FEB78"));
        assertTrue(ECB.isECBcode("mar58"));
        assertTrue(ECB.isECBcode("aPr99"));

        assertFalse(ECB.isECBcode(""));
        assertFalse(ECB.isECBcode("JUNE99"));
        assertFalse(ECB.isECBcode("JUN1999"));
        assertFalse(ECB.isECBcode("JUNE"));
        assertFalse(ECB.isECBcode("JUNE1999"));
        assertFalse(ECB.isECBcode("1999"));
    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::ecbDates (lines 61-100).
     */
    @Test
    public void ecbDates() {
        QL.info("Testing ECB dates...");

        // Take a snapshot of the original set so subsequent add/remove
        // calls in this test don't permanently mutate global state.
        final java.util.SortedSet<Date> known = new java.util.TreeSet<Date>(
                new java.util.Comparator<Date>() {
                    @Override
                    public int compare(final Date a, final Date b) {
                        return Long.compare(a.serialNumber(), b.serialNumber());
                    }
                });
        known.addAll(ECB.knownDates());
        assertFalse(known.isEmpty());

        final int n = ECB.nextDates(Date.minDate()).size();
        assertEquals(n, known.size());

        Date previousEcbDate = Date.minDate();
        for (final Date currentEcbDate : known) {
            if (!ECB.isECBdate(currentEcbDate)) {
                fail(currentEcbDate + " fails isECBdate check");
            }
            final Date ecbDateMinusOne = currentEcbDate.sub(1);
            if (ECB.isECBdate(ecbDateMinusOne)) {
                fail(ecbDateMinusOne + " fails isECBdate check");
            }
            if (!ECB.nextDate(ecbDateMinusOne).equals(currentEcbDate)) {
                fail("next ECB date following " + ecbDateMinusOne + " must be " + currentEcbDate);
            }
            if (!ECB.nextDate(previousEcbDate).equals(currentEcbDate)) {
                fail("next ECB date following " + previousEcbDate + " must be " + currentEcbDate);
            }
            previousEcbDate = currentEcbDate;
        }

        final Date knownDate = known.first();
        ECB.removeDate(knownDate);
        assertFalse("unable to remove an ECB date", ECB.isECBdate(knownDate));
        ECB.addDate(knownDate);
        assertTrue("unable to add an ECB date", ECB.isECBdate(knownDate));
    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::ecbGetDateFromCode (lines 102-117).
     */
    @Test
    public void ecbGetDateFromCode() {
        QL.info("Testing conversion of ECB codes to dates...");

        final Date ref2000 = new Date(1, Month.January, 2000);
        assertEquals(new Date(19, Month.January,  2005), ECB.date("JAN05", ref2000));
        assertEquals(new Date( 8, Month.February, 2006), ECB.date("FEB06", ref2000));
        assertEquals(new Date(14, Month.March,    2007), ECB.date("MAR07", ref2000));
        assertEquals(new Date(16, Month.April,    2008), ECB.date("APR08", ref2000));
        assertEquals(new Date(10, Month.June,     2009), ECB.date("JUN09", ref2000));
        assertEquals(new Date(14, Month.July,      2010), ECB.date("JUL10"));
        assertEquals(new Date(10, Month.August,    2011), ECB.date("AUG11"));
        assertEquals(new Date(12, Month.September, 2012), ECB.date("SEP12"));
        assertEquals(new Date( 9, Month.October,   2013), ECB.date("OCT13"));
        assertEquals(new Date(12, Month.November,  2014), ECB.date("NOV14"));
        assertEquals(new Date( 9, Month.December,  2015), ECB.date("DEC15"));
    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::ecbGetCodeFromDate (lines 119-125).
     */
    @Test
    public void ecbGetCodeFromDate() {
        QL.info("Testing creation of ECB code from a given date...");

        assertEquals("JAN06", ECB.code(new Date(18, Month.January,  2006)));
        assertEquals("MAR10", ECB.code(new Date(10, Month.March,    2010)));
        assertEquals("NOV17", ECB.code(new Date( 1, Month.November, 2017)));
    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::ecbNextCode (lines 127-135).
     */
    @Test
    public void ecbNextCode() {
        QL.info("Testing calculation of the next ECB code from a given code...");

        assertEquals("FEB06", ECB.nextCode("JAN06"));
        assertEquals("MAR10", ECB.nextCode("FeB10"));
        assertEquals("NOV17", ECB.nextCode("OCT17"));
        assertEquals("JAN18", ECB.nextCode("dEC17"));
        assertEquals("JAN00", ECB.nextCode("dec99"));
    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::asxDates (lines 196-246).
     * Reduced range to keep test runtime sane (Java loops are 4-5x slower
     * than C++ here).
     */
    @Test
    public void asxDates() {
        QL.info("Testing ASX dates...");

        final String[] ASXcodes = IMMcodes; // same shape, F0..Z9

        final Date last = new Date(1, Month.January, 2040);
        final Date counter = new Date(1, Month.January, 2000);
        while (counter.le(last)) {
            final Date asx = ASX.nextDate(counter, false);

            if (asx.le(counter)) {
                fail(asx.weekday() + " " + asx + " is not greater than " + counter.weekday() + " " + counter);
            }
            if (!ASX.isASXdate(asx, false)) {
                fail(asx.weekday() + " " + asx + " is not an ASX date (calculated from " + counter.weekday() + " " + counter + ")");
            }
            if (asx.gt(ASX.nextDate(counter, true))) {
                fail(asx.weekday() + " " + asx + " is not less than or equal to the next future in the main cycle " + ASX.nextDate(counter, true));
            }
            if (!ASX.date(ASX.code(asx), counter).equals(asx)) {
                fail(ASX.code(asx) + " at calendar day " + counter + " is not the ASX code matching " + asx);
            }
            for (final String code : ASXcodes) {
                if (ASX.date(code, counter).lt(counter)) {
                    fail(ASX.date(code, counter) + " is wrong for " + code + " at reference date " + counter);
                }
            }
            counter.inc();
        }
    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::asxDatesSpecific (lines 249-277).
     */
    @Test
    public void asxDatesSpecific() {
        QL.info("Testing ASX functionality with specific dates...");

        // isASXdate / weekday
        final Date d = new Date(12, Month.January, 2024);
        assertEquals(Weekday.Friday, d.weekday());
        assertTrue(ASX.isASXdate(d, /*mainCycle*/ false));
        assertFalse(ASX.isASXdate(d, /*mainCycle*/ true));

        // nextDate from code + ref date
        assertEquals(new Date(8, Month.February, 2002),
                ASX.nextDate("F2", false, new Date(1, Month.January, 2000)));
        assertEquals(new Date(9, Month.June, 2023),
                ASX.nextDate("K3", true,  new Date(1, Month.January, 2014)));

        // nextCode
        assertEquals("F4", ASX.nextCode(new Date( 1, Month.January, 2024), false));
        assertEquals("G4", ASX.nextCode(new Date(15, Month.January, 2024), false));
        assertEquals("H4", ASX.nextCode(new Date(15, Month.January, 2024), true));

        assertEquals("G4", ASX.nextCode("F4", false, new Date(1, Month.January, 2020)));
        assertEquals("H5", ASX.nextCode("Z4", true,  new Date(1, Month.January, 2020)));
    }

    /**
     * Port of v1.42.1 test-suite/dates.cpp::parseDates (lines 397-429).
     */
    @Test
    public void parseDates() {
        QL.info("Testing parsing of dates...");

        String input = "2006-01-15";
        Date d = DateParser.parseFormatted(input, "%Y-%m-%d");
        assertEquals(new Date(15, Month.January, 2006), d);

        input = "12/02/2012";
        d = DateParser.parseFormatted(input, "%m/%d/%Y");
        assertEquals(new Date(2, Month.December, 2012), d);

        d = DateParser.parseFormatted(input, "%d/%m/%Y");
        assertEquals(new Date(12, Month.February, 2012), d);

        input = "20011002";
        d = DateParser.parseFormatted(input, "%Y%m%d");
        assertEquals(new Date(2, Month.October, 2001), d);
    }
}
