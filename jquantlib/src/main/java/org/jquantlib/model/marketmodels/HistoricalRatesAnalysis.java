/*
 Copyright (C) 2026 JQuantLib contributors

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
 Copyright (C) 2007 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.model.marketmodels;

import org.jquantlib.QL;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Statistical analysis of historical rates: collects relative differences in successive
 * fixings of a vector of {@link InterestRateIndex}es and feeds them into a
 * {@link SequenceStatistics} accumulator.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/models/marketmodels/historicalratesanalysis.{hpp,cpp}}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Iterates over historical business dates between {@code startDate} and {@code endDate},
 * stepping by {@code step}, and records {@code rate[i] / prevRate[i] - 1.0} into the
 * statistics. Dates whose fixing lookup throws are recorded in {@link #skippedDates()}
 * along with the exception message in {@link #skippedDatesErrorMessage()}.
 *
 * <p>The free-function {@link #historicalRatesAnalysis} carries the algorithm; the
 * {@code HistoricalRatesAnalysis} class is a convenience wrapper that retains the
 * pre-allocated stats and per-date skip diagnostics.
 *
 * @author Ferdinando Ametrano (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public class HistoricalRatesAnalysis {

    private final SequenceStatistics stats_;
    private final List< Date > skippedDates_ = new ArrayList<>();
    private final List< String > skippedDatesErrorMessage_ = new ArrayList<>();

    /**
     * Runs the analysis at construction time, mirroring the C++ ctor pattern. The provided
     * {@code stats} is reset to {@code indexes.size()} dimensions and populated in-place.
     *
     * @param stats     accumulator owned by the caller; reset and filled by this analysis
     * @param startDate first calendar date (will be advanced to the next business day)
     * @param endDate   last calendar date (inclusive)
     * @param step      stepping period (e.g. 1 day, 1 week)
     * @param indexes   list of indexes to fix at each historical date
     */
    public HistoricalRatesAnalysis(final SequenceStatistics stats, final Date startDate, final Date endDate,
            final Period step, final List< ? extends InterestRateIndex > indexes) {
        this.stats_ = stats;
        historicalRatesAnalysis(stats_, skippedDates_, skippedDatesErrorMessage_, startDate, endDate, step, indexes);
    }

    /**
     * Free-function form mirroring C++ {@code QuantLib::historicalRatesAnalysis}. Resets
     * {@code statistics} to {@code indexes.size()} dimensions and appends
     * {@code (rate[i]/prevRate[i] - 1)} observations as it walks the historical dataset.
     * <p>
     * <strong>A20 discipline:</strong> the algorithm exactly mirrors {@code historicalratesanalysis.cpp}
     * lines 27-80 of v1.42.1 — same iteration order, same {@code std::swap} of buffers,
     * same try/catch granularity on per-date fixing lookup.
     *
     * @param statistics                  accumulator (reset to {@code indexes.size()} on entry)
     * @param skippedDates                output list of dates that threw during fixing lookup
     * @param skippedDatesErrorMessage    output list of per-skip exception messages (parallel to skippedDates)
     * @param startDate                   first calendar date (advanced to next business day via Following)
     * @param endDate                     last calendar date (inclusive)
     * @param step                        stepping period
     * @param indexes                     list of InterestRateIndexes to fix
     */
    public static void historicalRatesAnalysis(final SequenceStatistics statistics, final List< Date > skippedDates,
            final List< String > skippedDatesErrorMessage, final Date startDate, final Date endDate,
            final Period step, final List< ? extends InterestRateIndex > indexes) {

        skippedDates.clear();
        skippedDatesErrorMessage.clear();

        QL.require(! indexes.isEmpty(), "no indexes given");
        final int nRates = indexes.size();
        statistics.reset(nRates);

        final double[] sample = new double[nRates];
        double[] prevSample = new double[nRates];
        final double[] sampleDiff = new double[nRates];

        final Calendar cal = indexes.get(0).fixingCalendar();
        // start with a valid business date
        Date currentDate = cal.advance(startDate, new Period(1, org.jquantlib.time.TimeUnit.Days),
                BusinessDayConvention.Following);

        boolean isFirst = true;
        // Loop over the historical dataset
        while ( currentDate.le(endDate) ) {
            try {
                for ( int i = 0; i < nRates; i++ ) {
                    final double fixing = indexes.get(i).fixing(currentDate, false);
                    sample[i] = fixing;
                }
            } catch ( RuntimeException e ) {
                skippedDates.add(currentDate);
                skippedDatesErrorMessage.add(e.getMessage());
                currentDate = cal.advance(currentDate, step, BusinessDayConvention.Following);
                continue;
            }

            // From 2nd step onwards, calculate forward rate relative differences
            if ( ! isFirst ) {
                for ( int i = 0; i < nRates; i++ ) {
                    sampleDiff[i] = sample[i] / prevSample[i] - 1.0;
                }
                statistics.add(new Array(sampleDiff));
            } else {
                isFirst = false;
            }

            // Store last calculated forward rates — mirror std::swap(prevSample, sample).
            // sampleDiff has already been computed from sample for this iteration, so it's
            // safe to swap the references. We use a temp because Java has no std::swap.
            final double[] tmp = prevSample;
            prevSample = new double[nRates];
            System.arraycopy(sample, 0, prevSample, 0, nRates);
            // tmp would be the old prevSample contents; not needed (mirrors C++ swap semantics:
            // the old prevSample contents become sample, but we never read sample again before
            // the next iteration overwrites it).
            System.arraycopy(tmp, 0, sample, 0, nRates);  // not strictly needed but mirrors std::swap

            currentDate = cal.advance(currentDate, step, BusinessDayConvention.Following);
        }
    }

    /** @return dates whose fixing lookup threw (parallel to {@link #skippedDatesErrorMessage()}). */
    public List< Date > skippedDates() {
        return Collections.unmodifiableList(skippedDates_);
    }

    /** @return error messages for {@link #skippedDates()}, in matching order. */
    public List< String > skippedDatesErrorMessage() {
        return Collections.unmodifiableList(skippedDatesErrorMessage_);
    }

    /** @return the {@link SequenceStatistics} that was filled. */
    public SequenceStatistics stats() {
        return stats_;
    }
}
