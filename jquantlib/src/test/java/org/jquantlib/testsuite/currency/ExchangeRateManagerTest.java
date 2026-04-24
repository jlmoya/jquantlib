/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.currency;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.currencies.America.USDCurrency;
import org.jquantlib.currencies.Asia.JPYCurrency;
import org.jquantlib.currencies.ExchangeRate;
import org.jquantlib.currencies.ExchangeRateManager;
import org.jquantlib.currencies.Europe.ATSCurrency;
import org.jquantlib.currencies.Europe.DEMCurrency;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.currencies.Europe.GBPCurrency;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Before;
import org.junit.Test;

/**
 * Cross-validation of {@link ExchangeRateManager} against QuantLib C++ v1.42.1.
 * Reference values come from
 * {@code migration-harness/cpp/probes/currencies/exchangerate_manager_probe.cpp}.
 */
public class ExchangeRateManagerTest {

    private static final ReferenceReader REF =
            ReferenceReader.load("currencies/exchangerate_manager");

    private ExchangeRateManager erm;

    @Before
    public void setUp() {
        // clear() re-populates addKnownRates so each test starts from the
        // C++-equivalent default state.
        erm = ExchangeRateManager.getInstance();
        erm.clear();
    }

    @Test
    public void knownRateEurAts() {
        final ExchangeRate r = erm.lookup(new EURCurrency(), new ATSCurrency(),
                new Date(1, Month.January, 2002), ExchangeRate.Type.Direct);
        assertTrue("EUR/ATS fixed known rate must match C++ v1.42.1 exactly",
                Tolerance.exact(r.rate(),
                        REF.getCase("knownRateEurAts").expectedDouble()));
    }

    @Test
    public void customAddedUsdEur() {
        erm.add(new ExchangeRate(new USDCurrency(), new EURCurrency(), 0.92),
                new Date(1, Month.January, 2020),
                new Date(31, Month.December, 2020));
        final ExchangeRate r = erm.lookup(new USDCurrency(), new EURCurrency(),
                new Date(15, Month.June, 2020), ExchangeRate.Type.Direct);
        assertTrue("Custom-added USD/EUR rate must be returned by lookup",
                Tolerance.exact(r.rate(),
                        REF.getCase("customAddedUsdEur").expectedDouble()));
    }

    @Test
    public void multipleEntries2019() {
        // Add two non-overlapping GBP/JPY entries; look up date in 2019 range.
        // Historically a Java regression (fetch bug returning null when the
        // matching index equals rates.size()-1) — this test guards against it.
        erm.add(new ExchangeRate(new GBPCurrency(), new JPYCurrency(), 150.0),
                new Date(1, Month.January, 2020),
                new Date(31, Month.December, 2020));
        erm.add(new ExchangeRate(new GBPCurrency(), new JPYCurrency(), 140.0),
                new Date(1, Month.January, 2019),
                new Date(31, Month.December, 2019));
        final ExchangeRate r = erm.lookup(new GBPCurrency(), new JPYCurrency(),
                new Date(1, Month.July, 2019), ExchangeRate.Type.Direct);
        assertTrue("GBP/JPY date-range lookup must find the 2019 entry",
                Tolerance.exact(r.rate(),
                        REF.getCase("multipleEntries2019").expectedDouble()));
    }

    @Test
    public void multipleEntries2020() {
        erm.add(new ExchangeRate(new GBPCurrency(), new JPYCurrency(), 150.0),
                new Date(1, Month.January, 2020),
                new Date(31, Month.December, 2020));
        erm.add(new ExchangeRate(new GBPCurrency(), new JPYCurrency(), 140.0),
                new Date(1, Month.January, 2019),
                new Date(31, Month.December, 2019));
        final ExchangeRate r = erm.lookup(new GBPCurrency(), new JPYCurrency(),
                new Date(1, Month.July, 2020), ExchangeRate.Type.Direct);
        assertTrue("GBP/JPY date-range lookup must find the 2020 entry",
                Tolerance.exact(r.rate(),
                        REF.getCase("multipleEntries2020").expectedDouble()));
    }

    @Test
    public void knownRateEurDemAfterClear() {
        // clear() should also re-populate the known rates (matches C++ behavior).
        erm.clear();
        final ExchangeRate r = erm.lookup(new EURCurrency(), new DEMCurrency(),
                new Date(1, Month.January, 2001), ExchangeRate.Type.Direct);
        assertTrue("EUR/DEM still available after clear() because clear() re-populates",
                Tolerance.exact(r.rate(),
                        REF.getCase("knownRateEurDemAfterClear").expectedDouble()));
    }

    @Test
    public void directLookupThrowsWhenNotFound() {
        // Lookup a pair we haven't added. C++ throws QL_FAIL; Java throws
        // LibraryException. Both are Error/RuntimeException — assert that
        // some unchecked exception is thrown.
        try {
            erm.lookup(new USDCurrency(), new JPYCurrency(),
                    new Date(1, Month.January, 2010), ExchangeRate.Type.Direct);
            fail("expected exception for missing USD/JPY direct lookup");
        } catch (final RuntimeException expected) {
            // LibraryException is a RuntimeException subclass — catch the parent.
            // pass
        }
    }
}
