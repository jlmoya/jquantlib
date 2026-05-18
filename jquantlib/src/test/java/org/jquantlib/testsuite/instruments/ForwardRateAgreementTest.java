/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.instruments.ForwardRateAgreement;
import org.jquantlib.instruments.Position;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Bootstrap;
import org.jquantlib.termstructures.IterativeBootstrap;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.ForwardRate;
import org.jquantlib.termstructures.yieldcurves.FraRateHelper;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.math.interpolations.factories.Cubic;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Port of {@code test-suite/forwardrateagreement.cpp} v1.42.1 (1 case).
 *
 * <p>Exercises {@link org.jquantlib.instruments.ForwardRateAgreement}: an
 * FRA constructed before its discount curve handle is linked must defer
 * any curve dereference until {@code performCalculations()}, mirroring
 * the C++ contract whereby the constructor stores fields only and the
 * forward rate is computed at calculation time.
 *
 * <p>The test bootstraps a {@code PiecewiseYieldCurve<ForwardRate, Cubic>}
 * from three {@code FraRateHelper} pillars with initially-empty quotes,
 * constructs two FRAs (one with maturity inferred from the index, one
 * with explicit maturity), then sets the quote values and asserts the
 * resulting {@code forwardRate()} matches the first FRA helper's quote
 * (0.01) within {@code 1e-6}.
 *
 * <p>Source: {@code test-suite/forwardrateagreement.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class ForwardRateAgreementTest {

    @Test
    public void testConstructionWithoutACurve() {

        // Pin evaluation date so the FRA pillars and the FraRateHelper
        // initialiseDates() snapshot of evaluationDate agree.
        final Settings settings = new Settings();
        final Date savedEval = settings.evaluationDate();
        settings.setEvaluationDate(new Date(15, Month.May, 2026));
        try {
            final Date today = settings.evaluationDate();

            // set up the index
            final RelinkableHandle<YieldTermStructure> curveHandle =
                new RelinkableHandle<YieldTermStructure>();
            final IborIndex index =
                new USDLibor(new Period(3, TimeUnit.Months), curveHandle);

            // determine the settlement date for an FRA
            final Date settlementDate = index.fixingCalendar().advance(
                today,
                new Period(index.fixingDays(), TimeUnit.Days));

            // set up quotes with no values (NULL_REAL)
            final SimpleQuote[] quotes = new SimpleQuote[] {
                new SimpleQuote(),
                new SimpleQuote(),
                new SimpleQuote()
            };

            // set up the curve via three FRA pillars at 1y, 2y, 3y
            final RateHelper[] helpers = new RateHelper[3];
            helpers[0] = new FraRateHelper(new Handle<Quote>(quotes[0]),
                                           new Period(1, TimeUnit.Years), index);
            helpers[1] = new FraRateHelper(new Handle<Quote>(quotes[1]),
                                           new Period(2, TimeUnit.Years), index);
            helpers[2] = new FraRateHelper(new Handle<Quote>(quotes[2]),
                                           new Period(3, TimeUnit.Years), index);

            @SuppressWarnings({ "unchecked", "rawtypes" })
            final PiecewiseYieldCurve<ForwardRate, Cubic, IterativeBootstrap> curve =
                new PiecewiseYieldCurve<ForwardRate, Cubic, IterativeBootstrap>(
                        ForwardRate.class, Cubic.class, IterativeBootstrap.class,
                        today, helpers, index.dayCounter());

            curveHandle.linkTo(curve);

            // --- (1) Construct FRA with maturity inferred from the index
            // (mirrors C++: ForwardRateAgreement(index, valueDate, type,
            // strike, notional, curveHandle))
            //
            // NB: construct BEFORE values are set on quotes — the constructor
            // must NOT dereference the curve.
            final ForwardRateAgreement fra = new ForwardRateAgreement(
                    index,
                    settlementDate.add(new Period(12, TimeUnit.Months)),
                    Position.Long,
                    0.0,
                    1.0,
                    curveHandle);

            // --- (2) Construct FRA with explicit maturity date
            final ForwardRateAgreement fra2 = new ForwardRateAgreement(
                    index,
                    settlementDate.add(new Period(12, TimeUnit.Months)),
                    settlementDate.add(new Period(15, TimeUnit.Months)),
                    Position.Long,
                    0.0,
                    1.0,
                    curveHandle);

            // finally put values in the quotes — this triggers a re-bootstrap
            quotes[0].setValue(0.01);
            quotes[1].setValue(0.02);
            quotes[2].setValue(0.03);

            final double rate = fra.forwardRate().rate();
            assertTrue("grid creation failed for FRA without maturityDate, got rate "
                       + rate + " expected " + 0.01,
                       Math.abs(rate - 0.01) <= 1.0e-6);

            final double rate2 = fra2.forwardRate().rate();
            assertTrue("grid creation failed for FRA with maturityDate, got rate "
                       + rate2 + " expected " + 0.01,
                       Math.abs(rate2 - 0.01) <= 1.0e-6);

        } finally {
            settings.setEvaluationDate(savedEval);
        }
    }
}
