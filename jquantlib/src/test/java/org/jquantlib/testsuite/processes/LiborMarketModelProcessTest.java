/*
 Copyright (C) 2005, 2006 Klaus Spanderen (C++ original).
 Copyright (C) 2026 JQuantLib migration contributors (Java port).

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.processes;

import static org.junit.Assert.fail;

import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.processes.LiborForwardModelProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of {@code test-suite/libormarketmodelprocess.cpp} v1.42.1
 * (327 LOC, 3 test cases).
 *
 * <p>Status (Phase 5e.5b-CFC-d-135):
 * <ul>
 *   <li>{@code testInitialisation} — <strong>body-filled</strong>. Exercises
 *       {@link LiborForwardModelProcess#nextIndexReset(double)} as a
 *       std::upper_bound search on the fixing-time vector for every fixing
 *       index across a 5-year sweep of evaluation dates. Now that the Java
 *       process ctor properly initialises {@code fixingTimes_} via
 *       {@code cashFlows()} (rather than calling {@code List.set} on an
 *       empty list), this matches C++ verbatim.</li>
 *   <li>{@code testLambdaBootstrapping} — still deferred: requires
 *       {@code LfmHullWhiteParameterization} (not yet ported).</li>
 *   <li>{@code testMonteCarloCapletPricing} — still deferred: requires
 *       the {@code MultiPathGenerator} + {@code LowDiscrepancy} RNG
 *       pipeline plus {@code LfmHullWhiteParameterization}.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/libormarketmodelprocess.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class LiborMarketModelProcessTest {

    @Test
    public void testInitialisation() {
        // Mirror of C++ BOOST_AUTO_TEST_CASE(testInitialisation) lines 107-146.
        final DayCounter dayCounter = new Actual360();
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<YieldTermStructure>(
                        new FlatForward(Date.todaysDate(), 0.04, dayCounter));

        final IborIndex index = new Euribor6M(termStructure);

        // ConstantOptionletVolatility is constructed but never read by the
        // C++ test (lines 115-121); omitted here.

        final Calendar calendar = index.fixingCalendar();

        for (int daysOffset = 0; daysOffset < 1825 /* 5 year */; daysOffset += 8) {
            final Date todaysDate = calendar.adjust(Date.todaysDate().add(daysOffset));
            new Settings().setEvaluationDate(todaysDate);
            final Date settlementDate =
                    calendar.advance(todaysDate, index.fixingDays(), TimeUnit.Days);

            termStructure.linkTo(new FlatForward(settlementDate, 0.04, dayCounter));

            final LiborForwardModelProcess process = new LiborForwardModelProcess(60, index);

            final List<Double> fixings = process.fixingTimes();
            for (int i = 1; i < fixings.size() - 1; ++i) {
                final int ileft  = process.nextIndexReset(fixings.get(i) - 0.000001);
                final int iright = process.nextIndexReset(fixings.get(i) + 0.000001);
                final int ii     = process.nextIndexReset(fixings.get(i));

                if ((ileft != i) || (iright != i + 1) || (ii != i + 1)) {
                    fail("Failed to next index resets"
                            + "\n    daysOffset: " + daysOffset
                            + "\n    i:          " + i
                            + "\n    ileft:      " + ileft
                            + "\n    iright:     " + iright
                            + "\n    ii:         " + ii);
                }
            }
        }
    }

    @Ignore("Phase 5f.5 — LFM lambda bootstrap needs LfmHullWhiteParameterization")
    @Test
    public void testLambdaBootstrapping() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — LFM MC caplet pricing pipeline (MultiPathGenerator + "
            + "LowDiscrepancy + LfmHullWhiteParameterization) not ported")
    @Test
    public void testMonteCarloCapletPricing() { fail("not implemented"); }
}
