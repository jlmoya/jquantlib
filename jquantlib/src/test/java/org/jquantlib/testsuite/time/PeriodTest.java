/*
 Copyright (C) 2026 Jose Moya

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

/*
 Copyright (C) 2007, 2014 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.testsuite.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;

import org.jquantlib.QL;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;


/**
 * Tests for {@link org.jquantlib.time.Period}.
 *
 * Includes Phase 1-cert D5-A-R3 ports of v1.42.1 test-suite/period.cpp:
 * testYearsMonthsAlgebra, testWeeksDaysAlgebra, testOperators,
 * testConvertToYears, testConvertToMonths, testConvertToWeeks, testNormalization.
 */
public class PeriodTest {

       /**
        * Helper mirroring C++ {@code Period::normalized()} (out-of-place):
        * Java's {@code Period.normalize()} mutates in place, so we clone first
        * and then call the in-place {@code normalize()} on the copy.
        */
       private static Period normalized(final Period p) {
           // Period.clone is protected; use the (length, units) ctor.
           final Period n = new Period(p.length(), p.units());
           n.normalize();
           return n;
       }

	   @Test
	    public void testEqualsandHashCode() {

	        QL.info("Testing equals and hashcode");

	        final Period oneDay = new Period(1, TimeUnit.Days);
	        final Period oneYear = new Period(1, TimeUnit.Years);
	        final Period oneDayAgo = new Period(-1, TimeUnit.Days);
	        final Period oneDayAgain = new Period(1, TimeUnit.Days);
	        final Period oneNull = new Period(1, null);
	        final Period oneNullAgain = new Period(1, null);
	        final Period twoNull = new Period(1, null);

	        assertFalse(oneDay.equals(twoNull));
	        assertEquals(oneNull, oneNullAgain);

	        assertFalse(oneDay.equals(null));
	        assertFalse(oneDay.equals(oneNull));
	        assertEquals(oneDay, oneDay);
	        assertFalse(oneDay.equals(oneDayAgo));
	        assertFalse(oneDay.equals(oneYear));
	        assertEquals(oneDay, oneDayAgain);

	        HashSet<Period> testSet = new HashSet<Period>();
	        testSet.add(oneDay);
	        testSet.add(oneDayAgo);

	        assertTrue(testSet.contains(oneDay));
	        assertFalse(testSet.contains(oneYear));

	    }

    /**
     * Port of v1.42.1 test-suite/period.cpp::testYearsMonthsAlgebra (lines 31-82).
     * Verifies division, addition (in-place), and length/units invariants
     * for years/months periods. C++ uses arithmetic equality (Months↔Years);
     * Java mirrors via {@link Period#eq(Period)} which now has the same
     * unit-conversion specialization (CFC-d-318).
     */
    @Test
    public void testYearsMonthsAlgebra() {
        QL.info("Testing period algebra on years/months...");

        final Period OneYear = new Period(1, TimeUnit.Years);
        final Period SixMonths = new Period(6, TimeUnit.Months);
        final Period ThreeMonths = new Period(3, TimeUnit.Months);

        int n = 4;
        if (OneYear.div(n).neq(ThreeMonths)) {
            fail("division error: " + OneYear + "/" + n + " not equal to " + ThreeMonths);
        }
        n = 2;
        if (OneYear.div(n).neq(SixMonths)) {
            fail("division error: " + OneYear + "/" + n + " not equal to " + SixMonths);
        }

        // Period sum = ThreeMonths; sum += SixMonths
        final Period sum = new Period(ThreeMonths.length(), ThreeMonths.units());
        sum.addAssign(SixMonths);
        if (sum.neq(new Period(9, TimeUnit.Months))) {
            fail("sum error: " + ThreeMonths + " + " + SixMonths + " != " + new Period(9, TimeUnit.Months));
        }

        sum.addAssign(OneYear);
        if (sum.neq(new Period(21, TimeUnit.Months))) {
            fail("sum error: " + ThreeMonths + " + " + SixMonths + " + " + OneYear
                    + " != " + new Period(21, TimeUnit.Months));
        }

        final Period TwelveMonths = new Period(12, TimeUnit.Months);
        if (TwelveMonths.length() != 12) {
            fail("normalization error: TwelveMonths.length() is "
                    + TwelveMonths.length() + " instead of 12");
        }
        if (TwelveMonths.units() != TimeUnit.Months) {
            fail("normalization error: TwelveMonths.units() is "
                    + TwelveMonths.units() + " instead of " + TimeUnit.Months);
        }

        final Period NormalizedTwelveMonths = new Period(12, TimeUnit.Months);
        NormalizedTwelveMonths.normalize();
        if (NormalizedTwelveMonths.length() != 1) {
            fail("normalization error: NormalizedTwelveMonths.length() is "
                    + NormalizedTwelveMonths.length() + " instead of 1");
        }
        if (NormalizedTwelveMonths.units() != TimeUnit.Years) {
            fail("normalization error: NormalizedTwelveMonths.units() is "
                    + NormalizedTwelveMonths.units() + " instead of " + TimeUnit.Years);
        }
    }

    /**
     * Port of v1.42.1 test-suite/period.cpp::testWeeksDaysAlgebra (lines 84-130).
     * Verifies division (Weeks/2 → Weeks; Week/7 → Day), addition with
     * unit-conversion (Days+Weeks), and length/units invariants.
     */
    @Test
    public void testWeeksDaysAlgebra() {
        QL.info("Testing period algebra on weeks/days...");

        final Period TwoWeeks = new Period(2, TimeUnit.Weeks);
        final Period OneWeek = new Period(1, TimeUnit.Weeks);
        final Period ThreeDays = new Period(3, TimeUnit.Days);
        final Period OneDay = new Period(1, TimeUnit.Days);
        final Period ZeroDays = new Period(0, TimeUnit.Days);

        int n = 2;
        if (TwoWeeks.div(n).neq(OneWeek)) {
            fail("division error: " + TwoWeeks + "/" + n + " not equal to " + OneWeek);
        }
        n = 7;
        if (OneWeek.div(n).neq(OneDay)) {
            fail("division error: " + OneWeek + "/" + n + " not equal to " + OneDay);
        }

        final Period sum = new Period(ThreeDays.length(), ThreeDays.units());
        sum.addAssign(OneDay);
        if (sum.neq(new Period(4, TimeUnit.Days))) {
            fail("sum error: " + ThreeDays + " + " + OneDay + " != " + new Period(4, TimeUnit.Days));
        }

        sum.addAssign(OneWeek);
        if (sum.neq(new Period(11, TimeUnit.Days))) {
            fail("sum error: " + ThreeDays + " + " + OneDay + " + " + OneWeek
                    + " != " + new Period(11, TimeUnit.Days));
        }

        assertTrue("OneWeek + ZeroDays == OneWeek", OneWeek.add(ZeroDays).eq(OneWeek));
        assertTrue("OneWeek + 3*OneDay == Period(10, Days)",
                OneWeek.add(OneDay.mul(3)).eq(new Period(10, TimeUnit.Days)));
        assertTrue("OneWeek + 7*OneDay == TwoWeeks",
                OneWeek.add(OneDay.mul(7)).eq(TwoWeeks));

        final Period SevenDays = new Period(7, TimeUnit.Days);
        if (SevenDays.length() != 7) {
            fail("normalization error: SevenDays.length() is "
                    + SevenDays.length() + " instead of 7");
        }
        if (SevenDays.units() != TimeUnit.Days) {
            fail("normalization error: SevenDays.units() is "
                    + SevenDays.units() + " instead of " + TimeUnit.Days);
        }
    }

    /**
     * Port of v1.42.1 test-suite/period.cpp::testOperators (lines 132-141).
     * Compound assignment operators: {@code *=} (Period::mul -> in-place not
     * available, but {@code Period(p.mul(n).length, units)} is equivalent)
     * and {@code -=} via {@link Period#subAssign(Period)}.
     */
    @Test
    public void testOperators() {
        QL.info("Testing period operators...");

        // C++: Period p(3, Days); p *= 2; p == Period(6, Days)
        // Java's Period has no mulAssign; we use mul to compute the new value
        // then write it back via a fresh ctor.
        Period p = new Period(3, TimeUnit.Days);
        p = p.mul(2);
        assertTrue("p *= 2 == Period(6, Days)", p.eq(new Period(6, TimeUnit.Days)));

        // C++: p -= Period(2, Days); p == Period(4, Days)
        p.subAssign(new Period(2, TimeUnit.Days));
        assertTrue("p -= Period(2, Days) == Period(4, Days)", p.eq(new Period(4, TimeUnit.Days)));
    }

    /**
     * Port of v1.42.1 test-suite/period.cpp::testConvertToYears (lines 143-155).
     * Verifies {@code years(Period)} conversion of years/months periods. Note:
     * Days/Weeks → years is undecidable and throws in both C++ and Java.
     * Java's {@link Period#years(Period)} is an instance method taking the
     * period (matches the C++ free function {@code years(Period)}).
     */
    @Test
    public void testConvertToYears() {
        QL.info("Testing conversion of periods to years...");

        // Helper period instance just to invoke the (non-static) years() method.
        final Period helper = new Period();

        assertEquals(0.0, helper.years(new Period(0, TimeUnit.Years)), 0.0);
        assertEquals(1.0, helper.years(new Period(1, TimeUnit.Years)), 0.0);
        assertEquals(5.0, helper.years(new Period(5, TimeUnit.Years)), 0.0);

        final double tol = 1.0e-15;
        assertEquals(1.0 / 12.0, helper.years(new Period(1, TimeUnit.Months)), tol);
        assertEquals(8.0 / 12.0, helper.years(new Period(8, TimeUnit.Months)), tol);
        assertEquals(1.0, helper.years(new Period(12, TimeUnit.Months)), tol);
        assertEquals(1.5, helper.years(new Period(18, TimeUnit.Months)), tol);
    }

    /**
     * Port of v1.42.1 test-suite/period.cpp::testConvertToMonths (lines 157-166).
     */
    @Test
    public void testConvertToMonths() {
        QL.info("Testing conversion of periods to months...");

        final Period helper = new Period();

        assertEquals(0.0, helper.months(new Period(0, TimeUnit.Months)), 0.0);
        assertEquals(1.0, helper.months(new Period(1, TimeUnit.Months)), 0.0);
        assertEquals(5.0, helper.months(new Period(5, TimeUnit.Months)), 0.0);

        assertEquals(12.0, helper.months(new Period(1, TimeUnit.Years)), 0.0);
        assertEquals(36.0, helper.months(new Period(3, TimeUnit.Years)), 0.0);
    }

    /**
     * Port of v1.42.1 test-suite/period.cpp::testConvertToWeeks (lines 168-179).
     */
    @Test
    public void testConvertToWeeks() {
        QL.info("Testing conversion of periods to weeks...");

        final Period helper = new Period();

        assertEquals(0.0, helper.weeks(new Period(0, TimeUnit.Weeks)), 0.0);
        assertEquals(1.0, helper.weeks(new Period(1, TimeUnit.Weeks)), 0.0);
        assertEquals(5.0, helper.weeks(new Period(5, TimeUnit.Weeks)), 0.0);

        final double tol = 1.0e-15;
        assertEquals(1.0 / 7.0, helper.weeks(new Period(1, TimeUnit.Days)), tol);
        assertEquals(3.0 / 7.0, helper.weeks(new Period(3, TimeUnit.Days)), tol);
        assertEquals(11.0 / 7.0, helper.weeks(new Period(11, TimeUnit.Days)), tol);
    }

    /**
     * Port of v1.42.1 test-suite/period.cpp::testNormalization (lines 181-244).
     * For each period in a representative test set, verifies:
     *  - normalized(p) compares-equal (arithmetic eq) to p
     *  - any two periods that compare-equal must normalize to the same (length, units)
     *  - any two periods that normalize to the same (length, units) must compare-equal
     * Java's {@link Period#normalize()} is in-place; we use a local helper
     * {@code normalized(Period)} which clones-and-normalizes.
     */
    @Test
    public void testNormalization() {
        QL.info("Testing period normalization...");

        final Period[] testValues = {
                new Period(0, TimeUnit.Days),
                new Period(0, TimeUnit.Weeks),
                new Period(0, TimeUnit.Months),
                new Period(0, TimeUnit.Years),
                new Period(3, TimeUnit.Days),
                new Period(7, TimeUnit.Days),
                new Period(14, TimeUnit.Days),
                new Period(30, TimeUnit.Days),
                new Period(60, TimeUnit.Days),
                new Period(365, TimeUnit.Days),
                new Period(1, TimeUnit.Weeks),
                new Period(2, TimeUnit.Weeks),
                new Period(4, TimeUnit.Weeks),
                new Period(8, TimeUnit.Weeks),
                new Period(52, TimeUnit.Weeks),
                new Period(1, TimeUnit.Months),
                new Period(2, TimeUnit.Months),
                new Period(6, TimeUnit.Months),
                new Period(12, TimeUnit.Months),
                new Period(18, TimeUnit.Months),
                new Period(24, TimeUnit.Months),
                new Period(1, TimeUnit.Years),
                new Period(2, TimeUnit.Years)
        };

        for (final Period p1 : testValues) {
            final Period n1 = normalized(p1);
            if (n1.neq(p1)) {
                fail("Normalizing " + p1 + " yields " + n1 + ", which compares different");
            }

            for (final Period p2 : testValues) {
                final Period n2 = normalized(p2);
                // C++ uses ext::optional<bool> + catch; Java's eq() may throw
                // (UNDECIDABLE_COMPARISON) when units mix (Days↔Months etc.) so
                // we use the same try/catch pattern, leaving comparison "absent"
                // for undecidable pairs.
                Boolean comparison = null;
                try {
                    comparison = Boolean.valueOf(p1.eq(p2));
                } catch (final RuntimeException e) {
                    comparison = null;
                }

                if (comparison != null && comparison.booleanValue()) {
                    // Periods comparing equal must normalize to exactly the same period.
                    if (n1.units() != n2.units() || n1.length() != n2.length()) {
                        fail(p1 + " and " + p2 + " compare equal, but normalize to "
                                + n1 + " and " + n2 + " respectively");
                    }
                }

                if (n1.units() == n2.units() && n1.length() == n2.length()) {
                    // Periods normalizing to exactly the same period must compare equal.
                    if (p1.neq(p2)) {
                        fail(p1 + " and " + p2 + " compare different, but normalize to "
                                + n1 + " and " + n2 + " respectively");
                    }
                }
            }
        }
    }
}
