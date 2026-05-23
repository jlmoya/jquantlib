// jquantlib/src/test/java/org/jquantlib/testsuite/model/marketmodels/HistoricalRatesAnalysisTest.java
//
// Phase 2 L4-B+C — smoke-test HistoricalRatesAnalysis port.
//
// Verifies the algorithm wiring against a synthetic 5-day fixing series.
// Cross-validates by computing the relative differences by hand and comparing
// against SequenceStatistics.mean()/samples() output.
package org.jquantlib.testsuite.model.marketmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.jquantlib.model.marketmodels.HistoricalRatesAnalysis;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeSeries;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Smoke-test for {@link HistoricalRatesAnalysis} — verifies the per-step
 * {@code rate[i] / prevRate[i] - 1.0} accumulator wiring against a synthetic
 * 1-index fixings series.
 */
public class HistoricalRatesAnalysisTest {

    /**
     * Seeds 5 consecutive business-day fixings into a single Euribor3M and confirms that
     * HistoricalRatesAnalysis accumulates the 4 expected relative differences.
     */
    @Test
    public void testSingleIndexRelativeDifferences() {
        // Pick a historical window of 5 consecutive business days, then move the
        // evaluation date FAR forward so every fixing date qualifies as "past" and
        // InterestRateIndex.fixing(d, false) reads from history (matching the C++ usage
        // pattern: historical analysis runs against pre-known fixings).
        final Date windowStart = new Date(11, Month.May, 2026); // Monday
        final Date eval = new Date(31, Month.December, 2026); // forward enough for all dates to be "past"
        new Settings().setEvaluationDate(eval);

        // Fresh index — seed an empty history first so addFixing works.
        final Euribor3M idx = new Euribor3M();
        IndexManager.getInstance().setHistory(idx.name(), new TimeSeries< Double >(Double.class));

        final Calendar cal = idx.fixingCalendar();
        // 5 consecutive business days starting at windowStart.
        final double[] fixings = { 0.0100, 0.0105, 0.0110, 0.0102, 0.0108 };
        final List< Date > dates = new ArrayList<>();
        Date d = windowStart;
        for ( int i = 0; i < fixings.length; i++ ) {
            // Advance to next business day
            while ( ! cal.isBusinessDay(d) ) {
                d = d.add(1);
            }
            dates.add(d);
            idx.addFixing(d, fixings[i], true);
            d = d.add(1);
        }

        // Run the analysis from one business day before the first fixing date through
        // one business day after the last, stepping by 1 day. The internal advance(Following)
        // will roll the start to the first business day.
        final SequenceStatistics stats = new SequenceStatistics(1);
        final List< ? extends InterestRateIndex > indexes = Arrays.asList(idx);
        final Date start = dates.get(0).sub(2);
        final Date end = dates.get(dates.size() - 1);
        final Period step = new Period(1, TimeUnit.Days);

        final HistoricalRatesAnalysis hra = new HistoricalRatesAnalysis(stats, start, end, step, indexes);

        // The accumulator should have (N-1) observations (no first-day diff).
        assertEquals("expected fixings.length-1 observations", fixings.length - 1, stats.samples());

        // Compute the expected mean of relative-differences by hand.
        double sum = 0.0;
        for ( int i = 1; i < fixings.length; i++ ) {
            sum += fixings[i] / fixings[i - 1] - 1.0;
        }
        final double expectedMean = sum / (fixings.length - 1);
        final Array meanArr = stats.mean();
        assertEquals("mean[0] should match hand-computed mean of relative diffs", expectedMean, meanArr.get(0),
                1.0e-12);

        // No skips expected.
        assertTrue("no dates should be skipped", hra.skippedDates().isEmpty());
        assertTrue("no error messages expected", hra.skippedDatesErrorMessage().isEmpty());

        // Clean up history.
        IndexManager.getInstance().clearHistory(idx.name());
    }
}
