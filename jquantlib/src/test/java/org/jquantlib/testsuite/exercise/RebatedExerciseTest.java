/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.testsuite.exercise;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.BermudanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.exercise.RebatedExercise;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Cross-validation of {@link RebatedExercise} against C++ QuantLib v1.42.1
 * ({@code ql/rebatedexercise.hpp} + {@code ql/rebatedexercise.cpp}).
 *
 * <p>Fully deterministic (no Monte-Carlo / numeric engines). The rebate payment
 * dates are derived BY HAND from the C++ formula
 * {@code rebatePaymentCalendar.advance(exerciseDate, rebateSettlementDays, Days, convention)}
 * (C++ rebatedexercise.hpp:75-77) using a {@link Target} (TARGET) calendar and
 * {@code Following}, then asserted EXACTLY.
 *
 * <p>Hand-derivation (TARGET; +2 business days, Following; no nearby holidays in
 * the chosen windows — July 2015 has none, 18..22 Dec 2015 precede the 25/26 Dec
 * holidays):
 * <ul>
 *   <li>Mon 13-Jul-2015 +2bd -> Tue 14, Wed 15 = 15-Jul-2015</li>
 *   <li>Thu 16-Jul-2015 +2bd -> Fri 17, [skip Sat 18/Sun 19], Mon 20 = 20-Jul-2015</li>
 *   <li>Fri 18-Dec-2015 +2bd -> [skip Sat 19/Sun 20], Mon 21, Tue 22 = 22-Dec-2015</li>
 * </ul>
 */
public class RebatedExerciseTest {

    public RebatedExerciseTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // three sorted exercise dates forming a valid Bermudan schedule
    private static final Date D1 = new Date(13, Month.July, 2015);
    private static final Date D2 = new Date(16, Month.July, 2015);
    private static final Date D3 = new Date(18, Month.December, 2015);

    private static BermudanExercise bermudan() {
        return new BermudanExercise(new Date[] { D1, D2, D3 });
    }

    @Test
    public void testVectorConstructorBermudan() {
        QL.info("Testing RebatedExercise vector constructor (Bermudan)...");

        final List<Double> rebates = Arrays.asList(1.0, 2.5, -3.0);
        final RebatedExercise re = new RebatedExercise(
                bermudan(), rebates, 2, new Target(), BusinessDayConvention.Following);

        // type is copied from the base exercise (C++ Exercise(exercise))
        assertEquals(Exercise.Type.Bermudan, re.type());
        assertEquals(3, re.size());

        // rebate(i) == per-date rebate
        assertEquals(1.0, re.rebate(0), 0.0);
        assertEquals(2.5, re.rebate(1), 0.0);
        assertEquals(-3.0, re.rebate(2), 0.0);

        // rebates() returns the full vector
        assertEquals(rebates, re.rebates());

        // rebatePaymentDate(i) — C++ rebatedexercise.hpp:75-77; values derived by hand above
        assertTrue(new Date(15, Month.July, 2015).eq(re.rebatePaymentDate(0)));
        assertTrue(new Date(20, Month.July, 2015).eq(re.rebatePaymentDate(1)));
        assertTrue(new Date(22, Month.December, 2015).eq(re.rebatePaymentDate(2)));
    }

    @Test
    public void testScalarConstructorBroadcastBermudan() {
        QL.info("Testing RebatedExercise scalar constructor broadcast (Bermudan)...");

        // C++ rebatedexercise.cpp:30 -> rebates_(std::vector<Real>(dates().size(), rebate))
        final RebatedExercise re = new RebatedExercise(
                bermudan(), 7.5, 2, new Target(), BusinessDayConvention.Following);

        assertEquals(Exercise.Type.Bermudan, re.type());
        assertEquals(3, re.rebates().size());
        for (int i = 0; i < re.size(); i++) {
            assertEquals(7.5, re.rebate(i), 0.0);
        }

        // payment dates identical to the vector-ctor case (same dates/days/calendar/conv)
        assertTrue(new Date(15, Month.July, 2015).eq(re.rebatePaymentDate(0)));
        assertTrue(new Date(20, Month.July, 2015).eq(re.rebatePaymentDate(1)));
        assertTrue(new Date(22, Month.December, 2015).eq(re.rebatePaymentDate(2)));
    }

    @Test
    public void testScalarConstructorEuropean() {
        QL.info("Testing RebatedExercise scalar constructor (European)...");

        // scalar ctor performs NO exercise-type check (C++ rebatedexercise.cpp:25-33)
        final EuropeanExercise eu = new EuropeanExercise(D1);
        final RebatedExercise re = new RebatedExercise(
                eu, 4.0, 2, new Target(), BusinessDayConvention.Following);

        assertEquals(Exercise.Type.European, re.type());
        assertEquals(1, re.rebates().size());
        assertEquals(4.0, re.rebate(0), 0.0);

        // rebatePaymentDate valid for European (C++ rebatedexercise.hpp:72)
        // Mon 13-Jul-2015 +2bd Following -> 15-Jul-2015
        assertTrue(new Date(15, Month.July, 2015).eq(re.rebatePaymentDate(0)));
    }

    @Test
    public void testDefaultConstructorZeroRebate() {
        QL.info("Testing RebatedExercise default (zero-rebate) constructor...");

        // C++ default args: rebate=0.0, settlementDays=0, NullCalendar, Following
        final RebatedExercise re = new RebatedExercise(bermudan());
        assertEquals(3, re.rebates().size());
        for (int i = 0; i < re.size(); i++) {
            assertEquals(0.0, re.rebate(i), 0.0);
        }
        // settlementDays == 0 -> advance returns the (adjusted) exercise date itself.
        // All three exercise dates are business days, so payment date == exercise date.
        assertTrue(D1.eq(re.rebatePaymentDate(0)));
        assertTrue(D2.eq(re.rebatePaymentDate(1)));
        assertTrue(D3.eq(re.rebatePaymentDate(2)));
    }

    @Test
    public void testVectorConstructorSizeMismatchThrows() {
        QL.info("Testing RebatedExercise vector-ctor size mismatch (C++ QL_REQUIRE)...");

        // C++ rebatedexercise.cpp:48-52 — rebates.size() must equal dates().size()
        try {
            new RebatedExercise(bermudan(), Arrays.asList(1.0, 2.0), 2,
                    new Target(), BusinessDayConvention.Following);
            fail("expected LibraryException for rebates/dates size mismatch");
        } catch (final LibraryException expected) {
            // expected
        }
    }

    @Test
    public void testVectorConstructorNonBermudanThrows() {
        QL.info("Testing RebatedExercise vector-ctor non-Bermudan (C++ QL_REQUIRE)...");

        // C++ rebatedexercise.cpp:44-46 — a rebate vector is allowed only for Bermudan
        final EuropeanExercise eu = new EuropeanExercise(D1);
        try {
            new RebatedExercise(eu, Arrays.asList(1.0), 2,
                    new Target(), BusinessDayConvention.Following);
            fail("expected LibraryException: rebate vector allowed only for Bermudan");
        } catch (final LibraryException expected) {
            // expected
        }
    }

    @Test
    public void testRebateIndexOutOfRangeThrows() {
        QL.info("Testing RebatedExercise rebate(index) out-of-range (C++ QL_REQUIRE)...");

        // C++ rebatedexercise.hpp:64-69
        final RebatedExercise re = new RebatedExercise(
                bermudan(), Arrays.asList(1.0, 2.0, 3.0), 2,
                new Target(), BusinessDayConvention.Following);
        try {
            re.rebate(3);
            fail("expected LibraryException for rebate index out of range");
        } catch (final LibraryException expected) {
            // expected
        }
    }

    @Test
    public void testRebatePaymentDateAmericanThrows() {
        QL.info("Testing RebatedExercise rebatePaymentDate American guard (C++ QL_REQUIRE)...");

        // C++ rebatedexercise.hpp:72-74 — invalid for American style
        final AmericanExercise am = new AmericanExercise(D1, D3);
        final RebatedExercise re = new RebatedExercise(
                am, 1.0, 2, new Target(), BusinessDayConvention.Following);
        assertEquals(Exercise.Type.American, re.type());
        try {
            re.rebatePaymentDate(0);
            fail("expected LibraryException: American rebatePaymentDate must be client-computed");
        } catch (final LibraryException expected) {
            // expected
        }
    }

}
