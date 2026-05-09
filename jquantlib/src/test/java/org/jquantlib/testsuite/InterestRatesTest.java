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

package org.jquantlib.testsuite;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Rounding;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/interestrates.cpp (Phase 5a).
 *
 * <p>Single BOOST_AUTO_TEST_CASE ({@code testConversions}). For each row in
 * a 30-row table of (rate, compounding, frequency, time, compounding2,
 * frequency2, expectedRate, precision):
 * <ol>
 *   <li>verifies {@code compoundFactor * discountFactor == 1};</li>
 *   <li>verifies the equivalent rate with the same conventions returns
 *       the input rate;</li>
 *   <li>verifies the equivalent rate with different conventions matches
 *       the expected rounded rate.</li>
 * </ol>
 */
public class InterestRatesTest {

    public InterestRatesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * C++ test-suite helper {@code timeToDays(Time t, Integer daysPerYear = 360)}.
     */
    private static int timeToDays(final double t) {
        return (int) Math.round(t * 360.0);
    }

    private static final class Row {
        final double r;
        final Compounding comp;
        final Frequency freq;
        final double t;
        final Compounding comp2;
        final Frequency freq2;
        final double expected;
        final int precision;

        Row(final double r, final Compounding comp, final Frequency freq,
            final double t, final Compounding comp2, final Frequency freq2,
            final double expected, final int precision) {
            this.r = r;
            this.comp = comp;
            this.freq = freq;
            this.t = t;
            this.comp2 = comp2;
            this.freq2 = freq2;
            this.expected = expected;
            this.precision = precision;
        }
    }

    private static final Row[] CASES = new Row[] {
        // data from "Option Pricing Formulas", Haug, pag.181-182
        new Row(0.0800, Compounding.Compounded, Frequency.Quarterly,        1.00, Compounding.Continuous, Frequency.Annual,           0.0792, 4),
        new Row(0.1200, Compounding.Continuous, Frequency.Annual,           1.00, Compounding.Compounded, Frequency.Annual,           0.1275, 4),
        new Row(0.0800, Compounding.Compounded, Frequency.Quarterly,        1.00, Compounding.Compounded, Frequency.Annual,           0.0824, 4),
        new Row(0.0700, Compounding.Compounded, Frequency.Quarterly,        1.00, Compounding.Compounded, Frequency.Semiannual,       0.0706, 4),
        // undocumented, but reasonable :)
        new Row(0.0100, Compounding.Compounded, Frequency.Annual,           1.00, Compounding.Simple,     Frequency.Annual,           0.0100, 4),
        new Row(0.0200, Compounding.Simple,     Frequency.Annual,           1.00, Compounding.Compounded, Frequency.Annual,           0.0200, 4),
        new Row(0.0300, Compounding.Compounded, Frequency.Semiannual,       0.50, Compounding.Simple,     Frequency.Annual,           0.0300, 4),
        new Row(0.0400, Compounding.Simple,     Frequency.Annual,           0.50, Compounding.Compounded, Frequency.Semiannual,       0.0400, 4),
        new Row(0.0500, Compounding.Compounded, Frequency.EveryFourthMonth, 1.0/3, Compounding.Simple,    Frequency.Annual,           0.0500, 4),
        new Row(0.0600, Compounding.Simple,     Frequency.Annual,           1.0/3, Compounding.Compounded, Frequency.EveryFourthMonth,0.0600, 4),
        new Row(0.0500, Compounding.Compounded, Frequency.Quarterly,        0.25, Compounding.Simple,     Frequency.Annual,           0.0500, 4),
        new Row(0.0600, Compounding.Simple,     Frequency.Annual,           0.25, Compounding.Compounded, Frequency.Quarterly,        0.0600, 4),
        new Row(0.0700, Compounding.Compounded, Frequency.Bimonthly,        1.0/6, Compounding.Simple,    Frequency.Annual,           0.0700, 4),
        new Row(0.0800, Compounding.Simple,     Frequency.Annual,           1.0/6, Compounding.Compounded, Frequency.Bimonthly,       0.0800, 4),
        new Row(0.0900, Compounding.Compounded, Frequency.Monthly,          1.0/12, Compounding.Simple,   Frequency.Annual,           0.0900, 4),
        new Row(0.1000, Compounding.Simple,     Frequency.Annual,           1.0/12, Compounding.Compounded, Frequency.Monthly,        0.1000, 4),

        new Row(0.0300, Compounding.SimpleThenCompounded, Frequency.Semiannual, 0.25, Compounding.Simple,           Frequency.Annual,     0.0300, 4),
        new Row(0.0300, Compounding.SimpleThenCompounded, Frequency.Semiannual, 0.25, Compounding.Simple,           Frequency.Semiannual, 0.0300, 4),
        new Row(0.0300, Compounding.SimpleThenCompounded, Frequency.Semiannual, 0.25, Compounding.Simple,           Frequency.Quarterly,  0.0300, 4),
        new Row(0.0300, Compounding.SimpleThenCompounded, Frequency.Semiannual, 0.50, Compounding.Simple,           Frequency.Annual,     0.0300, 4),
        new Row(0.0300, Compounding.SimpleThenCompounded, Frequency.Semiannual, 0.50, Compounding.Simple,           Frequency.Semiannual, 0.0300, 4),
        new Row(0.0300, Compounding.SimpleThenCompounded, Frequency.Semiannual, 0.75, Compounding.Compounded,       Frequency.Semiannual, 0.0300, 4),

        new Row(0.0400, Compounding.Simple,     Frequency.Semiannual,       0.25, Compounding.SimpleThenCompounded, Frequency.Quarterly,  0.0400, 4),
        new Row(0.0400, Compounding.Simple,     Frequency.Semiannual,       0.25, Compounding.SimpleThenCompounded, Frequency.Semiannual, 0.0400, 4),
        new Row(0.0400, Compounding.Simple,     Frequency.Semiannual,       0.25, Compounding.SimpleThenCompounded, Frequency.Annual,     0.0400, 4),

        new Row(0.0400, Compounding.Compounded, Frequency.Quarterly,        0.50, Compounding.SimpleThenCompounded, Frequency.Quarterly,  0.0400, 4),
        new Row(0.0400, Compounding.Simple,     Frequency.Semiannual,       0.50, Compounding.SimpleThenCompounded, Frequency.Semiannual, 0.0400, 4),
        new Row(0.0400, Compounding.Simple,     Frequency.Semiannual,       0.50, Compounding.SimpleThenCompounded, Frequency.Annual,     0.0400, 4),

        new Row(0.0400, Compounding.Compounded, Frequency.Quarterly,        0.75, Compounding.SimpleThenCompounded, Frequency.Quarterly,  0.0400, 4),
        new Row(0.0400, Compounding.Compounded, Frequency.Semiannual,       0.75, Compounding.SimpleThenCompounded, Frequency.Semiannual, 0.0400, 4),
        new Row(0.0400, Compounding.Simple,     Frequency.Semiannual,       0.75, Compounding.SimpleThenCompounded, Frequency.Annual,     0.0400, 4)
    };

    @Test
    public void testConversions() {
        QL.info("Testing interest-rate conversions...");

        final DayCounter dc = new Actual360();
        final Date d1 = Date.todaysDate();

        for (final Row row : CASES) {
            final InterestRate ir = new InterestRate(row.r, dc, row.comp, row.freq);
            final Date d2 = d1.add(timeToDays(row.t));
            final Rounding roundingPrecision = new Rounding(row.precision);

            // (1) discountFactor is the inverse of compoundFactor
            final double compoundf = ir.compoundFactor(d1, d2);
            final double disc      = ir.discountFactor(d1, d2);
            double error = Math.abs(disc - 1.0 / compoundf);
            if (error > 1e-15) {
                fail("\n  " + ir
                        + "\n  1.0/compound_factor: " + (1.0 / compoundf)
                        + "\n  discount_factor:     " + disc
                        + "\n  error:               " + error);
            }

            // (2) equivalentRate with same conventions returns same rate
            final InterestRate ir2 = ir.equivalentRate(d1, d2, ir.dayCounter(),
                    ir.compounding(), ir.frequency());
            error = Math.abs(ir.rate() - ir2.rate());
            if (error > 1e-15) {
                fail("\n    original interest rate: " + ir
                        + "\n  equivalent interest rate: " + ir2
                        + "\n                rate error: " + error);
            }
            if (!ir.dayCounter().equals(ir2.dayCounter())) {
                fail("\n day counter error"
                        + "\n original interest rate:   " + ir
                        + "\n equivalent interest rate: " + ir2);
            }
            if (ir.compounding() != ir2.compounding()) {
                fail("\n compounding error"
                        + "\n original interest rate:   " + ir
                        + "\n equivalent interest rate: " + ir2);
            }
            if (ir.frequency() != ir2.frequency()) {
                fail("\n frequency error"
                        + "\n    original interest rate: " + ir
                        + "\n  equivalent interest rate: " + ir2);
            }

            // (3) equivalentRate with different conventions matches expected
            final InterestRate ir3 = ir.equivalentRate(d1, d2, ir.dayCounter(), row.comp2, row.freq2);
            final InterestRate expectedIR = new InterestRate(row.expected, ir.dayCounter(), row.comp2, row.freq2);
            final double r3 = roundingPrecision.operator(ir3.rate());
            error = Math.abs(r3 - expectedIR.rate());
            if (error > 1.0e-17) {
                fail("\n               original interest rate: " + ir
                        + "\n  calculated equivalent interest rate: " + ir3
                        + "\n            truncated equivalent rate: " + r3
                        + "\n    expected equivalent interest rate: " + expectedIR
                        + "\n                           rate error: " + error);
            }
            if (!ir3.dayCounter().equals(expectedIR.dayCounter())) {
                fail("\n day counter error"
                        + "\n    original interest rate: " + ir3
                        + "\n  equivalent interest rate: " + expectedIR);
            }
            if (ir3.compounding() != expectedIR.compounding()) {
                fail("\n compounding error"
                        + "\n    original interest rate: " + ir3
                        + "\n  equivalent interest rate: " + expectedIR);
            }
            if (ir3.frequency() != expectedIR.frequency()) {
                fail("\n frequency error"
                        + "\n    original interest rate: " + ir3
                        + "\n  equivalent interest rate: " + expectedIR);
            }
        }
    }
}
