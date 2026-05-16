/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.exoticoptions;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.exoticoptions.MakeMCPagodaEngine;
import org.jquantlib.experimental.exoticoptions.PagodaOption;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Java port of {@code test-suite/pagodaoption.cpp::testCached} v1.42.1.
 *
 * <p>Phase 5e.5b-CFC-d-27: un-ignored + body-filled after MT long-seed
 * dispatch (commit {@code 3bfef9c2}) and PseudoSqrt(Spectral) column-order
 * fix (commit {@code b83f5776}).
 *
 * <p>Source: {@code test-suite/pagodaoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class PagodaOptionTest {

    @Test
    public void testCached() {
        final Date today = new org.jquantlib.Settings().evaluationDate();

        final DayCounter dc = new Actual360();
        final Calendar cal = new NullCalendar();
        final List<Date> fixingDates = new ArrayList<Date>(4);
        for (int i = 1; i <= 4; ++i) {
            fixingDates.add(today.add(i * 90));
        }

        final double roof = 0.20;
        final double fraction = 0.62;

        final PagodaOption option = new PagodaOption(fixingDates, roof, fraction);

        final Handle<YieldTermStructure> riskFreeRate =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.05, dc));

        final List<StochasticProcess1D> processes = new ArrayList<StochasticProcess1D>(4);
        processes.add(makeBsm(today, 0.15, 0.01, 0.30, riskFreeRate, dc, cal));
        processes.add(makeBsm(today, 0.20, 0.05, 0.35, riskFreeRate, dc, cal));
        processes.add(makeBsm(today, 0.35, 0.04, 0.25, riskFreeRate, dc, cal));
        processes.add(makeBsm(today, 0.30, 0.03, 0.20, riskFreeRate, dc, cal));

        final Matrix correlation = new Matrix(new double[][] {
                { 1.00, 0.50, 0.30, 0.10 },
                { 0.50, 1.00, 0.20, 0.40 },
                { 0.30, 0.20, 1.00, 0.60 },
                { 0.10, 0.40, 0.60, 1.00 }
        });

        final long seed = 86421L;
        final int fixedSamples = 1023;

        final StochasticProcessArray process =
                new StochasticProcessArray(processes, correlation);

        option.setPricingEngine(new MakeMCPagodaEngine(process)
                .withSamples(fixedSamples)
                .withSeed(seed)
                .value());

        final double value = option.NPV();
        final double storedValue = 0.01221094;
        final double tolerance = 1.0e-8;

        if (Math.abs(value - storedValue) > tolerance) {
            fail("calculated value: " + value + "\n    expected:         " + storedValue);
        }

        final double minimumTol = 1.0e-2;
        double tolerance2 = option.errorEstimate();
        tolerance2 = Math.min(tolerance2 / 2.0, minimumTol * value);

        option.setPricingEngine(new MakeMCPagodaEngine(process)
                .withAbsoluteTolerance(tolerance2)
                .withSeed(seed)
                .value());

        option.NPV();
        final double accuracy = option.errorEstimate();
        if (accuracy > tolerance2) {
            fail("reached accuracy: " + accuracy + "\n    expected:         " + tolerance2);
        }
    }

    private static StochasticProcess1D makeBsm(
            final Date today, final double S, final double q, final double vol,
            final Handle<YieldTermStructure> rTS,
            final DayCounter dc, final Calendar cal) {
        final Handle<? extends Quote> spot = new Handle<Quote>(new SimpleQuote(S));
        final Handle<YieldTermStructure> qTS =
                new Handle<YieldTermStructure>(new FlatForward(today, q, dc));
        final Handle<BlackVolTermStructure> volTS =
                new Handle<BlackVolTermStructure>(new BlackConstantVol(today, cal, vol, dc));
        return new BlackScholesMertonProcess(spot, qTS, rTS, volTS);
    }
}
