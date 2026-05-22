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

package org.jquantlib.testsuite.util;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.ZeroCouponInflationSwapHelper;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;

/**
 * Test fixture for inflation tests, mirroring the helpers defined at the top
 * of {@code migration-harness/cpp/quantlib/test-suite/inflation.cpp}.
 *
 * <p>The C++ file does not use a single {@code CommonVars} struct; instead it
 * defines:
 * <ul>
 *   <li>a {@code Datum} POD ({@code Date date; Rate rate}),</li>
 *   <li>a {@code nominalTermStructure()} factory returning a flat 5%
 *       Actual360 yield curve dated 13-August-2007,</li>
 *   <li>a templated {@code makeHelpers} that wraps a vector of
 *       {@code Datum} into bootstrap helpers via a caller-supplied factory,</li>
 *   <li>a {@code checkSeasonality} routine that exercises a curve's
 *       {@link org.jquantlib.termstructures.inflation.MultiplicativePriceSeasonality}
 *       behaviour.</li>
 * </ul>
 *
 * <p>This Java helper bundles the same ingredients as static utilities so that
 * {@link org.jquantlib.testsuite.inflation.InflationTest} can mirror the C++
 * test cases verbatim. It is package-utility code, not the curve-builder
 * fixture used by Phase 2p/2q probe-driven tests.
 *
 * <p>Phase 2t A.1 — first test-suite phase under the rigor directive
 * (2026-05-08).
 */
public final class InflationCommonVars {

    private InflationCommonVars() {
        // utility class
    }

    /**
     * Mirrors the C++ {@code Datum} POD: a date paired with a market quote
     * (typically a percentage already in percent units, i.e. 2.93 for
     * 2.93%).
     */
    public static final class Datum {
        public final Date date;
        public final double rate;

        public Datum(final Date date, final double rate) {
            this.date = date;
            this.rate = rate;
        }
    }

    /**
     * Functional interface mirroring the C++
     * {@code std::function<ext::shared_ptr<BootstrapHelper<T>>(const Handle<Quote>&, const Date&)>}
     * factory passed into {@code makeHelpers}.
     */
    public interface ZeroHelperFactory {
        ZeroCouponInflationSwapHelper make(Handle<SimpleQuote> quote, Date maturity);
    }

    /**
     * Mirrors {@code nominalTermStructure()} from inflation.cpp. Returns a
     * flat 5% forward Actual360 curve dated 13-August-2007.
     */
    public static YieldTermStructure nominalTermStructure() {
        final Date evaluationDate = new Date(13, Month.August, 2007);
        return new FlatForward(evaluationDate, 0.05, new Actual360());
    }

    /**
     * Mirrors the C++ template {@code makeHelpers<ZeroInflationTermStructure>}.
     * Wraps each {@link Datum} in a {@link SimpleQuote} (after dividing the
     * percentage rate by 100) and forwards to the supplied factory.
     */
    public static List<ZeroCouponInflationSwapHelper> makeZeroHelpers(
            final List<Datum> data, final ZeroHelperFactory factory) {
        final List<ZeroCouponInflationSwapHelper> instruments = new ArrayList<>();
        for (final Datum d : data) {
            final var quote = new Handle<SimpleQuote>(new SimpleQuote(d.rate / 100.0));
            instruments.add(factory.make(quote, d.date));
        }
        return instruments;
    }

    /**
     * Returns the canonical UK RPI fixing dataset used by the C++
     * inflation.cpp tests covering {@code testZeroIndex},
     * {@code testZeroTermStructure}, {@code testRatioYYIndex} and
     * {@code testYYTermStructure}. Monthly fixings 2005-01-01..2007-08-01.
     */
    public static double[] ukRpiFixData() {
        return new double[] {
                189.9, 189.9, 189.6, 190.5, 191.6, 192.0,
                192.2, 192.2, 192.6, 193.1, 193.3, 193.6,
                194.1, 193.4, 194.2, 195.0, 196.5, 197.7,
                198.5, 198.5, 199.2, 200.1, 200.4, 201.1,
                202.7, 201.6, 203.1, 204.4, 205.4, 206.2,
                207.3, 206.1
        };
    }

    /**
     * Returns the standard UKRPI monthly fixing schedule from
     * 1-January-2005 through 1-August-2007 (32 entries).
     */
    public static List<Date> ukRpiFixDates() {
        final List<Date> dates = new ArrayList<>(32);
        Date d = new Date(1, Month.January, 2005);
        for (int i = 0; i < 32; ++i) {
            dates.add(d);
            d = d.add(new Period(1, TimeUnit.Months));
        }
        return dates;
    }

    /**
     * Default UK calendar used by inflation.cpp tests (used for evaluation
     * date adjustment).
     */
    public static Calendar ukCalendar() {
        return new UnitedKingdom();
    }

    /**
     * Default modified-following business-day convention used by
     * inflation.cpp tests.
     */
    public static BusinessDayConvention ukBdc() {
        return BusinessDayConvention.ModifiedFollowing;
    }

    /**
     * Seeds {@code rpi} with the canonical UKRPI dataset
     * ({@link #ukRpiFixData()} truncated to {@code maxIndex} entries).
     * This lets a test choose between the two C++ fixings tables: 32 entries
     * (with the trailing 206.1 used by {@code testZeroIndex}) or 31 entries
     * (used by {@code testZeroTermStructure} and {@code testYYTermStructure},
     * which omit the trailing point).
     *
     * @param rpi      the index to seed
     * @param maxIndex 31 or 32 — number of fixings to add
     */
    public static void addCanonicalUkRpiFixings(final ZeroInflationIndex rpi, final int maxIndex) {
        final double[] fixData = ukRpiFixData();
        final List<Date> fixDates = ukRpiFixDates();
        for (int i = 0; i < maxIndex && i < fixData.length; ++i) {
            rpi.addFixing(fixDates.get(i), fixData[i], true);
        }
    }

    /**
     * Convenience for tests that want a fresh, fixings-seeded UKRPI index
     * bound to a given handle. Seeds with all 32 monthly UK RPI points.
     */
    public static UKRPI seedUkRpi(final Handle<ZeroInflationTermStructure> handle) {
        final UKRPI rpi = new UKRPI(Frequency.Monthly, false, false, handle);
        addCanonicalUkRpiFixings(rpi, 32);
        return rpi;
    }

    /**
     * Builds the canonical 14-pillar UK ZC inflation swap dataset used by
     * {@code testZeroTermStructure} and {@code testZeroTermStructureLazyBaseDate}.
     */
    public static List<Datum> ukZcSwapData() {
        final List<Datum> out = new ArrayList<>();
        out.add(new Datum(new Date(13, Month.August, 2008), 2.93));
        out.add(new Datum(new Date(13, Month.August, 2009), 2.95));
        out.add(new Datum(new Date(13, Month.August, 2010), 2.965));
        out.add(new Datum(new Date(15, Month.August, 2011), 2.98));
        out.add(new Datum(new Date(13, Month.August, 2012), 3.0));
        out.add(new Datum(new Date(13, Month.August, 2014), 3.06));
        out.add(new Datum(new Date(13, Month.August, 2017), 3.175));
        out.add(new Datum(new Date(13, Month.August, 2019), 3.243));
        out.add(new Datum(new Date(15, Month.August, 2022), 3.293));
        out.add(new Datum(new Date(14, Month.August, 2027), 3.338));
        out.add(new Datum(new Date(13, Month.August, 2032), 3.348));
        out.add(new Datum(new Date(15, Month.August, 2037), 3.348));
        out.add(new Datum(new Date(13, Month.August, 2047), 3.308));
        out.add(new Datum(new Date(13, Month.August, 2057), 3.228));
        return out;
    }

    /**
     * Builds the canonical 15-pillar UK YoY inflation swap dataset used by
     * {@code testYYTermStructure} and {@code testExtrapolationRegression}.
     */
    public static List<Datum> ukYoYSwapData() {
        final List<Datum> out = new ArrayList<>();
        out.add(new Datum(new Date(13, Month.August, 2008), 2.95));
        out.add(new Datum(new Date(13, Month.August, 2009), 2.95));
        out.add(new Datum(new Date(13, Month.August, 2010), 2.93));
        out.add(new Datum(new Date(15, Month.August, 2011), 2.955));
        out.add(new Datum(new Date(13, Month.August, 2012), 2.945));
        out.add(new Datum(new Date(13, Month.August, 2013), 2.985));
        out.add(new Datum(new Date(13, Month.August, 2014), 3.01));
        out.add(new Datum(new Date(13, Month.August, 2015), 3.035));
        out.add(new Datum(new Date(13, Month.August, 2016), 3.055));
        out.add(new Datum(new Date(13, Month.August, 2017), 3.075));
        out.add(new Datum(new Date(13, Month.August, 2019), 3.105));
        out.add(new Datum(new Date(15, Month.August, 2022), 3.135));
        out.add(new Datum(new Date(13, Month.August, 2027), 3.155));
        out.add(new Datum(new Date(13, Month.August, 2032), 3.145));
        out.add(new Datum(new Date(13, Month.August, 2037), 3.145));
        return out;
    }

    /**
     * Returns the seasonality factors used in {@code checkSeasonality} and
     * {@code testZeroTermStructure}'s seasonality block. 12 monthly factors
     * close to but not equal to 1.0.
     */
    public static double[] seasonalityFactors() {
        return new double[] {
                1.003245, 1.000000, 0.999715, 1.000495,
                1.000929, 0.998687, 0.995949, 0.994682,
                0.995949, 1.000519, 1.003705, 1.004186
        };
    }

    /**
     * Mirrors the C++ {@code REPORT_FAILURE} macro at inflation.cpp lines
     * 62-66. Returns a string suitable for inclusion in an assertion failure
     * message.
     */
    public static String reportPeriodFailure(final Date d, final Date first,
                                             final Date second, final String periodName) {
        return "wrong " + periodName + " inflation period for Date (1 "
                + d + "), Start Date (" + first + "), End Date (" + second + ")";
    }

    /**
     * Day-array helper for {@code testPeriod}: number of days in each month
     * with index 1-12. Index 0 is unused (matches C++ array convention).
     * Caller mutates {@code days[2]} (February) for leap-year handling.
     */
    public static int[] daysInMonthArray() {
        return new int[] {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    }
}
