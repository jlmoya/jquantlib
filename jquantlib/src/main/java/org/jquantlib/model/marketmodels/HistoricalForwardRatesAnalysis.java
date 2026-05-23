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
 Copyright (C) 2007 François du Vignaud
 Copyright (C) 2007 Marco Bianchetti
 Copyright (C) 2007 Katiuscia Manzoni

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.model.marketmodels;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Bootstrap;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.DepositRateHelper;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.termstructures.yieldcurves.SwapRateHelper;
import org.jquantlib.termstructures.yieldcurves.Traits;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Statistical analysis of historical forward rates: bootstraps a yield curve at every
 * historical date and feeds the relative differences of successive forward rates into a
 * {@link SequenceStatistics} accumulator.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/models/marketmodels/historicalforwardratesanalysis.hpp}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The C++ original is a template over {@code Traits} (Discount / ZeroYield / ForwardRate)
 * and {@code Interpolator} (LogLinear / Linear / Cubic). The Java port translates this into
 * the {@link PiecewiseYieldCurve} type-token pattern: the caller passes {@code classT, classI,
 * classB} explicitly. Both the static {@link #historicalForwardRatesAnalysis} free function
 * and the {@link HistoricalForwardRatesAnalysisImpl} convenience wrapper accept these tokens.
 *
 * @author Ferdinando Ametrano (C++ original)
 * @author François du Vignaud (C++ original)
 * @author Marco Bianchetti (C++ original)
 * @author Katiuscia Manzoni (C++ original)
 * @author JQuantLib contributors (Java port)
 */
public interface HistoricalForwardRatesAnalysis {

    /** Dates whose fixing lookup threw (parallel to {@link #skippedDatesErrorMessage()}). */
    List< Date > skippedDates();

    /** Error messages for {@link #skippedDates()}, in matching order. */
    List< String > skippedDatesErrorMessage();

    /** Dates whose curve bootstrap (or forward-rate computation) threw. */
    List< Date > failedDates();

    /** Error messages for {@link #failedDates()}, in matching order. */
    List< String > failedDatesErrorMessage();

    /** The fixing-period grid used by the forward-rate computation. */
    List< Period > fixingPeriods();

    /**
     * Performs the historical-forward-rates analysis. Mirrors the C++ free function
     * {@code historicalForwardRatesAnalysis<Traits, Interpolator>} in
     * {@code historicalforwardratesanalysis.hpp} lines 43-195 of v1.42.1.
     *
     * <p>For each historical date {@code currentDate} between {@code startDate} and {@code endDate}
     * (advanced by {@code step}), this:
     * <ol>
     *   <li>Sets the evaluation date to {@code currentDate};</li>
     *   <li>Looks up the day's historical fixings for the {@code iborIndexes} and {@code swapIndexes}
     *       and pushes them into the {@link SimpleQuote} placeholders backing the rate helpers;</li>
     *   <li>Bootstraps a {@link PiecewiseYieldCurve} of type {@code (classT, classI, classB)};</li>
     *   <li>Computes simple-compounded forward rates at each {@code currentDate + fixingPeriods[i]}
     *       with tenor equal to the {@code fwdIndex} tenor;</li>
     *   <li>From the second date onward, accumulates the relative differences against the prior
     *       day's forwards into {@code statistics}.</li>
     * </ol>
     *
     * <p>Per-date exceptions in step 2 land in {@code skippedDates}; per-date exceptions in steps
     * 3-4 land in {@code failedDates}.
     *
     * @param classT                  Traits token (e.g. {@code Discount.class})
     * @param classI                  Interpolator token (e.g. {@code LogLinear.class})
     * @param classB                  Bootstrap token (e.g. {@code IterativeBootstrap.class})
     * @param statistics              accumulator (reset to {@code fixingPeriods.size()} on entry)
     * @param skippedDates            output list of dates that threw during fixing lookup
     * @param skippedDatesErrorMessage output list of per-skip exception messages
     * @param failedDates             output list of dates that threw during bootstrap/forward
     * @param failedDatesErrorMessage output list of per-fail exception messages
     * @param fixingPeriods           output list of fixing periods (populated from initialGap / horizon)
     * @param startDate               first calendar date (advanced to next business day via Following)
     * @param endDate                 last calendar date (inclusive)
     * @param step                    stepping period
     * @param fwdIndex                forward-rate index whose tenor seeds the fixing-period grid
     * @param initialGap              first fixing period
     * @param horizon                 last fixing period (inclusive)
     * @param iborIndexes             IBOR indexes whose fixings drive the deposit helpers
     * @param swapIndexes             swap indexes whose fixings drive the swap helpers
     * @param yieldCurveDayCounter    day-counter for the bootstrapped yield curve
     * @param yieldCurveAccuracy      bootstrap accuracy tolerance (e.g. 1.0e-12)
     */
    static < T extends Traits, I extends Interpolation.Interpolator,
            B extends Bootstrap > void historicalForwardRatesAnalysis(
            final Class< T > classT, final Class< I > classI, final Class< B > classB,
            final SequenceStatistics statistics,
            final List< Date > skippedDates, final List< String > skippedDatesErrorMessage,
            final List< Date > failedDates, final List< String > failedDatesErrorMessage,
            final List< Period > fixingPeriods, final Date startDate, final Date endDate, final Period step,
            final InterestRateIndex fwdIndex, final Period initialGap, final Period horizon,
            final List< ? extends IborIndex > iborIndexes, final List< ? extends SwapIndex > swapIndexes,
            final DayCounter yieldCurveDayCounter, final double yieldCurveAccuracy) {

        statistics.reset();
        skippedDates.clear();
        skippedDatesErrorMessage.clear();
        failedDates.clear();
        failedDatesErrorMessage.clear();
        fixingPeriods.clear();

        // Save and restore the evaluation-date / enforces-fixings settings manually since
        // {@code org.jquantlib.SavedSettings} is currently a stub (Phase 2 follow-up).
        final Settings settings = new Settings();
        final Date savedEvalDate = settings.evaluationDate();
        final boolean savedEnforcesFixings = settings.isEnforcesTodaysHistoricFixings();
        try {
            settings.setEnforcesTodaysHistoricFixings(true);

            final List< RateHelper > rateHelpers = new ArrayList<>();

            // Create DepositRateHelpers
            final List< SimpleQuote > iborQuotes = new ArrayList<>();
            for ( final IborIndex ibor : iborIndexes ) {
                final SimpleQuote quote = new SimpleQuote(0.0);
                iborQuotes.add(quote);
                final Handle< Quote > quoteHandle = new Handle< Quote >(quote);
                rateHelpers.add(new DepositRateHelper(quoteHandle, ibor.tenor(), ibor.fixingDays(),
                        ibor.fixingCalendar(), ibor.businessDayConvention(), ibor.endOfMonth(), ibor.dayCounter()));
            }

            // Create SwapRateHelpers
            final List< SimpleQuote > swapQuotes = new ArrayList<>();
            for ( final SwapIndex swap : swapIndexes ) {
                final SimpleQuote quote = new SimpleQuote(0.0);
                swapQuotes.add(quote);
                final Handle< Quote > quoteHandle = new Handle< Quote >(quote);
                rateHelpers.add(new SwapRateHelper(quoteHandle, swap.tenor(), swap.fixingCalendar(),
                        swap.fixedLegTenor().frequency(), swap.fixedLegConvention(), swap.dayCounter(),
                        swap.iborIndex()));
            }

            // Set up the forward-rates time grid
            final Period indexTenor = fwdIndex.tenor();
            Period fixingPeriod = initialGap;
            while ( fixingPeriod.le(horizon) ) {
                fixingPeriods.add(fixingPeriod);
                fixingPeriod = fixingPeriod.add(indexTenor);
            }

            final int nRates = fixingPeriods.size();
            statistics.reset(nRates);
            final double[] fwdRates = new double[nRates];
            double[] prevFwdRates = new double[nRates];
            final double[] fwdRatesDiff = new double[nRates];
            final DayCounter indexDayCounter = fwdIndex.dayCounter();
            final Calendar cal = fwdIndex.fixingCalendar();

            // Bootstrap the yield curve at the current date — once, then mutated by setting
            // evaluationDate.
            final int settlementDays = 0;
            final RateHelper[] helperArr = rateHelpers.toArray(new RateHelper[0]);
            final PiecewiseYieldCurve< T, I, B > yc = new PiecewiseYieldCurve< T, I, B >(classT, classI, classB,
                    settlementDays, cal, helperArr, yieldCurveDayCounter);

            // start with a valid business date
            Date currentDate = cal.advance(startDate, new Period(1, TimeUnit.Days), BusinessDayConvention.Following);
            boolean isFirst = true;
            // Loop over the historical dataset
            while ( currentDate.le(endDate) ) {
                // move evaluation date and refresh rate-helper dates
                new Settings().setEvaluationDate(currentDate);

                try {
                    // update the quotes
                    for ( int i = 0; i < iborIndexes.size(); i++ ) {
                        final double fixing = iborIndexes.get(i).fixing(currentDate, false);
                        iborQuotes.get(i).setValue(fixing);
                    }
                    for ( int i = 0; i < swapIndexes.size(); i++ ) {
                        final double fixing = swapIndexes.get(i).fixing(currentDate, false);
                        swapQuotes.get(i).setValue(fixing);
                    }
                } catch ( RuntimeException e ) {
                    skippedDates.add(currentDate);
                    skippedDatesErrorMessage.add(e.getMessage());
                    currentDate = cal.advance(currentDate, step, BusinessDayConvention.Following);
                    continue;
                }

                try {
                    for ( int i = 0; i < nRates; i++ ) {
                        // Time-to-go forwards
                        final Date d = currentDate.add(fixingPeriods.get(i));
                        // C++ calls yc.forwardRate(d, indexTenor, indexDayCounter, Simple).
                        // The Java surface requires an explicit Frequency for the (Date,Period,DC,Comp)
                        // overload; with Compounding.Simple the Frequency argument is unused (the
                        // simple-compounding formula has no frequency), so we pass Annual as a
                        // placeholder — matches v1.42.1 behavior.
                        fwdRates[i] = yc.forwardRate(d, indexTenor, indexDayCounter, Compounding.Simple,
                                Frequency.Annual).rate();
                    }
                } catch ( RuntimeException e ) {
                    failedDates.add(currentDate);
                    failedDatesErrorMessage.add(e.getMessage());
                    currentDate = cal.advance(currentDate, step, BusinessDayConvention.Following);
                    continue;
                }

                // From 2nd step onwards, calculate forward-rate relative differences
                if ( ! isFirst ) {
                    for ( int i = 0; i < nRates; i++ ) {
                        fwdRatesDiff[i] = fwdRates[i] / prevFwdRates[i] - 1.0;
                    }
                    statistics.add(new Array(fwdRatesDiff));
                } else {
                    isFirst = false;
                }

                // std::swap(prevFwdRates, fwdRates) equivalent
                final double[] tmp = prevFwdRates;
                prevFwdRates = new double[nRates];
                System.arraycopy(fwdRates, 0, prevFwdRates, 0, nRates);
                // (tmp content not needed — same as C++ swap: old prev becomes the new sample buffer,
                // which will be overwritten on the next iteration before being read.)
                System.arraycopy(tmp, 0, fwdRates, 0, nRates);

                currentDate = cal.advance(currentDate, step, BusinessDayConvention.Following);
            }
        } finally {
            settings.setEvaluationDate(savedEvalDate);
            settings.setEnforcesTodaysHistoricFixings(savedEnforcesFixings);
        }
    }

    /**
     * Concrete implementation that owns its {@link SequenceStatistics} and exposes the diagnostic
     * lists. Mirrors C++ {@code HistoricalForwardRatesAnalysisImpl<Traits, Interpolator>}.
     */
    final class HistoricalForwardRatesAnalysisImpl implements HistoricalForwardRatesAnalysis {

        private final SequenceStatistics stats_;
        private final List< Date > skippedDates_ = new ArrayList<>();
        private final List< String > skippedDatesErrorMessage_ = new ArrayList<>();
        private final List< Date > failedDates_ = new ArrayList<>();
        private final List< String > failedDatesErrorMessage_ = new ArrayList<>();
        private final List< Period > fixingPeriods_ = new ArrayList<>();

        /**
         * Runs the analysis at construction time, mirroring the C++ ctor pattern.
         *
         * @param classT                Traits token (e.g. {@code Discount.class})
         * @param classI                Interpolator token (e.g. {@code LogLinear.class})
         * @param classB                Bootstrap token (e.g. {@code IterativeBootstrap.class})
         * @param stats                 accumulator owned by this analysis (caller can introspect afterwards)
         * @param startDate             first calendar date
         * @param endDate               last calendar date (inclusive)
         * @param step                  stepping period
         * @param fwdIndex              forward-rate index whose tenor seeds the fixing-period grid
         * @param initialGap            first fixing period
         * @param horizon               last fixing period
         * @param iborIndexes           IBOR indexes whose fixings drive the deposit helpers
         * @param swapIndexes           swap indexes whose fixings drive the swap helpers
         * @param yieldCurveDayCounter  day-counter for the bootstrapped yield curve
         * @param yieldCurveAccuracy    bootstrap accuracy tolerance (e.g. 1.0e-12)
         */
        public < T extends Traits, I extends Interpolation.Interpolator,
                B extends Bootstrap > HistoricalForwardRatesAnalysisImpl(
                final Class< T > classT, final Class< I > classI, final Class< B > classB,
                final SequenceStatistics stats, final Date startDate, final Date endDate, final Period step,
                final InterestRateIndex fwdIndex, final Period initialGap, final Period horizon,
                final List< ? extends IborIndex > iborIndexes, final List< ? extends SwapIndex > swapIndexes,
                final DayCounter yieldCurveDayCounter, final double yieldCurveAccuracy) {

            QL.require(stats != null, "stats must not be null");
            this.stats_ = stats;
            historicalForwardRatesAnalysis(classT, classI, classB, stats_,
                    skippedDates_, skippedDatesErrorMessage_, failedDates_, failedDatesErrorMessage_,
                    fixingPeriods_, startDate, endDate, step, fwdIndex, initialGap, horizon, iborIndexes,
                    swapIndexes, yieldCurveDayCounter, yieldCurveAccuracy);
        }

        @Override
        public List< Date > skippedDates() {
            return Collections.unmodifiableList(skippedDates_);
        }

        @Override
        public List< String > skippedDatesErrorMessage() {
            return Collections.unmodifiableList(skippedDatesErrorMessage_);
        }

        @Override
        public List< Date > failedDates() {
            return Collections.unmodifiableList(failedDates_);
        }

        @Override
        public List< String > failedDatesErrorMessage() {
            return Collections.unmodifiableList(failedDatesErrorMessage_);
        }

        @Override
        public List< Period > fixingPeriods() {
            return Collections.unmodifiableList(fixingPeriods_);
        }

        public SequenceStatistics stats() {
            return stats_;
        }
    }
}
