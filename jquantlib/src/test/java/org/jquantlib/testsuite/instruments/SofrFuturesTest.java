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
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.instruments.OvernightIndexFuture;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.IterativeBootstrap;
import org.jquantlib.termstructures.Pillar;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.Discount;
import org.jquantlib.termstructures.yieldcurves.OvernightIndexFutureRateHelper;
import org.jquantlib.termstructures.yieldcurves.OvernightIndexFutureRateHelper.SofrFutureRateHelper;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Port of QuantLib v1.42.1 {@code test-suite/sofrfutures.cpp} (221 LOC).
 *
 * <p>Exercises {@link Sofr}, {@link OvernightIndexFuture},
 * {@link SofrFutureRateHelper} / {@link OvernightIndexFutureRateHelper} and
 * {@code PiecewiseYieldCurve<Discount, Linear>} bootstrapping with
 * {@link Pillar} support — all ported in Phase 5e.5b-CFC-d-74.
 *
 * <p>Reference: {@code test-suite/sofrfutures.cpp} @ v1.42.1.
 *
 * @author Jose Moya
 */
public class SofrFuturesTest {

    public SofrFuturesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Holds one SOFR future quote row from the C++ fixture.
     */
    private static final class SofrQuotes {
        final Frequency freq;
        final Month month;
        final int year;
        final double price;

        SofrQuotes(final Frequency freq, final Month month, final int year,
                   final double price) {
            this.freq = freq;
            this.month = month;
            this.year = year;
            this.price = price;
        }
    }

    /**
     * Port of C++ {@code sofrfutures.cpp::testBootstrap} (lines 45-118).
     *
     * <p>Bootstraps a discount curve with 13 SOFR future quotes (Oct 2018 -
     * Sep 2020), then verifies a Mar 2019 - Jun 2019 future re-prices to
     * within {@code 1e-9} of the expected price for both convexity
     * adjustments {0.0, 0.1}.
     */
    @Test
    public void testBootstrap() {
        QL.info("Testing bootstrap over SOFR futures...");

        // Clean SOFR history before adding fixtures: a previous test might
        // have populated it. Java's IndexManager is shared across tests.
        IndexManager.getInstance().clearHistory("SOFR");

        final Date today = new Date(26, Month.October, 2018);
        new Settings().setEvaluationDate(today);

        final SofrQuotes[] sofrQuotes = new SofrQuotes[] {
            new SofrQuotes(Frequency.Monthly,   Month.October,   2018, 97.8175),
            new SofrQuotes(Frequency.Monthly,   Month.November,  2018, 97.770),
            new SofrQuotes(Frequency.Monthly,   Month.December,  2018, 97.685),
            new SofrQuotes(Frequency.Monthly,   Month.January,   2019, 97.595),
            new SofrQuotes(Frequency.Monthly,   Month.February,  2019, 97.590),
            new SofrQuotes(Frequency.Monthly,   Month.March,     2019, 97.525),
            new SofrQuotes(Frequency.Quarterly, Month.March,     2019, 97.440),
            new SofrQuotes(Frequency.Quarterly, Month.June,      2019, 97.295),
            new SofrQuotes(Frequency.Quarterly, Month.September, 2019, 97.220),
            new SofrQuotes(Frequency.Quarterly, Month.December,  2019, 97.170),
            new SofrQuotes(Frequency.Quarterly, Month.March,     2020, 97.160),
            new SofrQuotes(Frequency.Quarterly, Month.June,      2020, 97.165),
            new SofrQuotes(Frequency.Quarterly, Month.September, 2020, 97.175),
        };

        final OvernightIndex index = new Sofr();
        index.addFixing(new Date( 1, Month.October, 2018), 0.0222);
        index.addFixing(new Date( 2, Month.October, 2018), 0.022);
        index.addFixing(new Date( 3, Month.October, 2018), 0.022);
        index.addFixing(new Date( 4, Month.October, 2018), 0.0218);
        index.addFixing(new Date( 5, Month.October, 2018), 0.0216);
        index.addFixing(new Date( 9, Month.October, 2018), 0.0215);
        index.addFixing(new Date(10, Month.October, 2018), 0.0215);
        index.addFixing(new Date(11, Month.October, 2018), 0.0217);
        index.addFixing(new Date(12, Month.October, 2018), 0.0218);
        index.addFixing(new Date(15, Month.October, 2018), 0.0221);
        index.addFixing(new Date(16, Month.October, 2018), 0.0218);
        index.addFixing(new Date(17, Month.October, 2018), 0.0218);
        index.addFixing(new Date(18, Month.October, 2018), 0.0219);
        index.addFixing(new Date(19, Month.October, 2018), 0.0219);
        index.addFixing(new Date(22, Month.October, 2018), 0.0218);
        index.addFixing(new Date(23, Month.October, 2018), 0.0217);
        index.addFixing(new Date(24, Month.October, 2018), 0.0218);
        index.addFixing(new Date(25, Month.October, 2018), 0.0219);

        final List<RateHelper> helpers = new ArrayList<RateHelper>();
        for (final SofrQuotes q : sofrQuotes) {
            helpers.add(new SofrFutureRateHelper(q.price, q.month, q.year, q.freq));
        }
        final RateHelper[] helperArray = helpers.toArray(new RateHelper[0]);

        final PiecewiseYieldCurve<Discount, Linear, IterativeBootstrap> curve =
                new PiecewiseYieldCurve<Discount, Linear, IterativeBootstrap>(
                        Discount.class, Linear.class, IterativeBootstrap.class,
                        today, helperArray, new Actual365Fixed());

        // test curve with one of the futures
        final OvernightIndex sofr = new Sofr(new Handle<YieldTermStructure>(curve));
        final SimpleQuote convQuote = new SimpleQuote();
        final OvernightIndexFuture sf = new OvernightIndexFuture(
                sofr,
                new Date(20, Month.March, 2019),
                new Date(19, Month.June,  2019),
                new Handle<Quote>(convQuote));

        final double tolerance = 1.0e-9;
        for (final double convAdj : new double[] { 0.0, 0.1 }) {
            convQuote.setValue(convAdj);
            final double expectedPrice = 100.0 * (1.0 - (0.0256 + convAdj));
            final double error = Math.abs(sf.NPV() - expectedPrice);
            if (error > tolerance) {
                fail("sample futures:"
                    + "\n estimated price: " + sf.NPV()
                    + "\n expected price:  " + expectedPrice
                    + "\n error:           " + error
                    + "\n tolerance:       " + tolerance);
            }
        }
        assertTrue(true);
    }

    /**
     * Port of C++ {@code sofrfutures.cpp::testBootstrapWithJuneteenth}
     * (lines 121-171).
     *
     * <p>Bootstraps when the third Wednesday falls on Juneteenth (US holiday
     * since 2021); 5 SOFR future quotes (Jun 2024 - Jun 2025) verifying that
     * the Juneteenth holiday properly extends the futures period.
     */
    @Test
    public void testBootstrapWithJuneteenth() {
        QL.info("Testing bootstrap over SOFR futures when third Wednesday "
              + "falls on Juneteenth...");

        IndexManager.getInstance().clearHistory("SOFR");

        final Date today = new Date(27, Month.June, 2024);
        new Settings().setEvaluationDate(today);

        final SofrQuotes[] sofrQuotes = new SofrQuotes[] {
            new SofrQuotes(Frequency.Quarterly, Month.June,      2024, 97.220),
            new SofrQuotes(Frequency.Quarterly, Month.September, 2024, 97.170),
            new SofrQuotes(Frequency.Quarterly, Month.December,  2024, 97.160),
            new SofrQuotes(Frequency.Quarterly, Month.March,     2025, 97.165),
            new SofrQuotes(Frequency.Quarterly, Month.June,      2025, 97.175),
        };

        final OvernightIndex index = new Sofr();
        index.addFixing(new Date(18, Month.June, 2024), 0.02);
        index.addFixing(new Date(20, Month.June, 2024), 0.02);
        index.addFixing(new Date(21, Month.June, 2024), 0.02);
        index.addFixing(new Date(24, Month.June, 2024), 0.02);
        index.addFixing(new Date(25, Month.June, 2024), 0.02);
        index.addFixing(new Date(26, Month.June, 2024), 0.02);
        index.addFixing(new Date(27, Month.June, 2024), 0.02);

        final List<RateHelper> helpers = new ArrayList<RateHelper>();
        for (final SofrQuotes q : sofrQuotes) {
            helpers.add(new SofrFutureRateHelper(q.price, q.month, q.year, q.freq));
        }
        final RateHelper[] helperArray = helpers.toArray(new RateHelper[0]);

        final PiecewiseYieldCurve<Discount, Linear, IterativeBootstrap> curve =
                new PiecewiseYieldCurve<Discount, Linear, IterativeBootstrap>(
                        Discount.class, Linear.class, IterativeBootstrap.class,
                        today, helperArray, new Actual365Fixed());

        final OvernightIndex sofr = new Sofr(new Handle<YieldTermStructure>(curve));
        final OvernightIndexFuture sf = new OvernightIndexFuture(
                sofr,
                new Date(19, Month.June,      2024),
                new Date(18, Month.September, 2024));

        final double expectedPrice = 97.220;
        final double tolerance = 1.0e-9;

        final double error = Math.abs(sf.NPV() - expectedPrice);
        if (error > tolerance) {
            fail("sample futures:"
                + "\n estimated price: " + sf.NPV()
                + "\n expected price:  " + expectedPrice
                + "\n error:           " + error
                + "\n tolerance:       " + tolerance);
        }
        assertTrue(true);
    }

    /**
     * Port of C++ {@code sofrfutures.cpp::testPillarDates} (lines 173-217).
     *
     * <p>Verifies {@code Pillar::LastRelevantDate} (default) /
     * {@code Pillar::MaturityDate} / {@code Pillar::CustomDate} behavior,
     * plus the after-maturity exception, and the SOFR helper custom pillar.
     */
    @Test
    public void testPillarDates() {
        QL.info("Testing pillar date support in SOFR futures helpers...");

        IndexManager.getInstance().clearHistory("SOFR");

        final Date today = new Date(15, Month.March, 2024);
        new Settings().setEvaluationDate(today);

        final Handle<Quote> price = new Handle<Quote>(new SimpleQuote(99.0));
        final Sofr index = new Sofr();

        final Date valueDate    = new Date(20, Month.March, 2024);
        final Date maturityDate = new Date(20, Month.June,  2024);

        // Default pillar (LastRelevantDate)
        final OvernightIndexFutureRateHelper h1 =
                new OvernightIndexFutureRateHelper(price, valueDate, maturityDate, index);
        assertEquals(maturityDate, h1.pillarDate());

        // maturity pillar
        final OvernightIndexFutureRateHelper h2 = new OvernightIndexFutureRateHelper(
                price, valueDate, maturityDate, index,
                new Handle<Quote>(),
                org.jquantlib.cashflow.RateAveraging.Type.Compound,
                Pillar.Choice.MaturityDate,
                new Date());
        assertEquals(maturityDate, h2.pillarDate());

        // Custom pillar
        final Date custom = new Date(20, Month.April, 2024);
        final OvernightIndexFutureRateHelper h3 = new OvernightIndexFutureRateHelper(
                price, valueDate, maturityDate, index,
                new Handle<Quote>(),
                org.jquantlib.cashflow.RateAveraging.Type.Compound,
                Pillar.Choice.CustomDate,
                custom);
        assertEquals(custom, h3.pillarDate());

        // Invalid custom pillar (after maturity) — must throw with
        // "after end of reference period".
        final Date badCustom = new Date(20, Month.July, 2024);
        try {
            new OvernightIndexFutureRateHelper(
                    price, valueDate, maturityDate, index,
                    new Handle<Quote>(),
                    org.jquantlib.cashflow.RateAveraging.Type.Compound,
                    Pillar.Choice.CustomDate,
                    badCustom);
            fail("expected exception for custom pillar after end of reference period");
        } catch (final Exception expected) {
            assertTrue("exception message must mention 'after end of reference period', got: "
                    + expected.getMessage(),
                    expected.getMessage() != null
                    && expected.getMessage().contains("after end of reference period"));
        }

        // SOFR helper custom pillar
        final Date sofrCustom = new Date(15, Month.July, 2024);
        final SofrFutureRateHelper sh = new SofrFutureRateHelper(
                price, Month.June, 2024, Frequency.Quarterly,
                new Handle<Quote>(),
                Pillar.Choice.CustomDate,
                sofrCustom);
        assertEquals(sofrCustom, sh.pillarDate());
    }
}
