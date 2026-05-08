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
/*
 Copyright (C) 2007, 2009 Chris Kenyon
 Copyright (C) 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.inflation;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BootstrapHelper;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.util.Pair;

/**
 * Bootstrap helper for {@link PiecewiseZeroInflationCurve}, anchored to a
 * synthetic zero-coupon inflation-indexed swap (ZCIIS).
 *
 * <p>Java port of QuantLib v1.42.1 {@code ZeroCouponInflationSwapHelper},
 * specialized to the NoInterpolation observation case
 * ({@code CPI::AsIndex} when the index is itself non-interpolated, which is
 * the v1.42 default for UK/EU CPI / RPI families).
 *
 * <h3>Pre-A.3 simplification</h3>
 * <p>The C++ helper internally constructs a {@code ZeroCouponInflationSwap}
 * and queries its {@code fairRate()}. The Java {@code ZeroCouponInflationSwap}
 * instrument is scheduled for Phase 2p sub-layer A.3 and does not yet exist.
 *
 * <p>For the NoInterpolation observation path with a flat nominal curve
 * (which is what the C++ helper itself uses by default — see
 * {@code inflationhelpers.cpp:48} where {@code FlatForward(0)} is constructed),
 * the swap's {@code fairRate()} reduces algebraically to the closed form
 * implemented here. From the C++ formula in
 * {@code zerocouponinflationswap.cpp::fairRate}:
 * <pre>
 *   growth = I(T) / I(0)
 *   fairRate = growth^(1/T) - 1
 * </pre>
 * where {@code I(T)} is the projected inflation index fixing at the swap's
 * fixing date (= maturity − observation lag, snapped to the inflation period
 * start for NoInterpolation), and {@code T = dayCounter.yearFraction(start, maturity)}.
 *
 * <p>The projection {@code I(T)} comes from the index in turn:
 * {@code I(T) = I(baseDate) * (1 + curve.zeroRate(fixingDate))^t}, where
 * {@code t = dayCounter.yearFraction(trueBaseDate, fixingDate)} and
 * {@code trueBaseDate} is the inflation-period end of the curve's baseDate.
 *
 * <p>This matches the C++ semantics bit-by-bit when the bootstrapping
 * constructor's nominal curve is the default {@code FlatForward(0,...,0.0,...)}
 * (yielding equal discount factors that cancel between fixed and inflation
 * legs, as documented in {@code inflationhelpers.cpp:67-69}).
 *
 * <p>When A.3 ports {@code ZeroCouponInflationSwap}, this helper should be
 * refactored to delegate to that instrument's {@code fairRate()} for full
 * generality (custom nominal curves, payment-leg adjustments, etc.).
 *
 * @see PiecewiseZeroInflationCurve
 * @see InflationTraits
 */
public class ZeroCouponInflationSwapHelper extends BootstrapHelper<ZeroInflationTermStructure> {

    //
    // protected fields
    //

    protected final Period swapObsLag;
    protected final Date startDate;
    protected final Date maturity;
    protected final Calendar calendar;
    protected final BusinessDayConvention paymentConvention;
    protected final DayCounter dayCounter;
    protected final ZeroInflationIndex zii;

    //
    // public constructors
    //

    /**
     * Construct a ZCIIS bootstrap helper with the swap's effective date
     * defaulted to today's evaluation date.
     *
     * @param quote             the swap's fair-rate quote
     * @param swapObsLag        observation lag from index publication (e.g. 3M)
     * @param maturity          swap maturity date (pre-adjustment)
     * @param calendar          calendar for payment-date adjustment
     * @param paymentConvention business-day convention for payments
     * @param dayCounter        day counter for time computations
     * @param zii               zero-inflation index used for fixings
     */
    public ZeroCouponInflationSwapHelper(
            final Handle<Quote> quote,
            final Period swapObsLag,
            final Date maturity,
            final Calendar calendar,
            final BusinessDayConvention paymentConvention,
            final DayCounter dayCounter,
            final ZeroInflationIndex zii) {
        super(quote);
        this.swapObsLag = swapObsLag;
        this.startDate = new Settings().evaluationDate();
        this.maturity = maturity;
        this.calendar = calendar;
        this.paymentConvention = paymentConvention;
        this.dayCounter = dayCounter;
        this.zii = zii;

        // Compute pillar / earliest / latest as the inflation-period start of
        // the fixing date (matches C++ NoInterpolation branch).
        final Pair<Date, Date> fixingPeriod = InflationTermStructure.inflationPeriod(
                maturity.sub(swapObsLag), zii.frequency());
        this.earliestDate = fixingPeriod.first();
        this.latestDate = fixingPeriod.first();

        zii.addObserver(this);
    }

    //
    // BootstrapHelper hooks
    //

    @Override
    public void setTermStructure(final ZeroInflationTermStructure ts) {
        super.setTermStructure(ts);
        // Force the helper's index to use the curve being bootstrapped for
        // forecast fixings.
        // We rely on the existing Java ZeroInflationIndex API (the C++ port
        // uses zii->clone(handle) but Java's existing class has no clone()
        // so we leave the index linked to whatever curve handle the test
        // provided. For TIGHT match, the test must construct its index
        // pointing at this curve (or use forecast by hand).
    }

    /**
     * Implied ZCIIS fair-rate quote from the underlying curve.
     *
     * <p>Closed-form derivation matching {@code ZeroCouponInflationSwap::fairRate()}
     * for NoInterpolation observation + flat-zero nominal curve (the helper's
     * default per C++ {@code inflationhelpers.cpp}):
     * <pre>
     *   T          = dayCounter.yearFraction(startDate, maturity)
     *   fixingDate = inflationPeriod(maturity - swapObsLag, frequency).first
     *   tCurve     = dayCounter.yearFraction(curve.baseDate, fixingDate)
     *   growth     = (1 + curve.zeroRate(fixingDate))^tCurve
     *   fairRate   = growth^(1/T) - 1
     * </pre>
     *
     * <p>Note that the curve's {@code zeroRate(date)} method already snaps the
     * date to its inflation-period start, so calling it with either {@code maturity}
     * or {@code fixingDate} produces the same time argument internally. We use
     * {@code fixingDate} for clarity.
     */
    @Override
    public double impliedQuote() {
        QL.ensure(termStructure != null, "term structure not set");

        final Date baseDate = termStructure.baseDate();

        // Time from start of swap to maturity (the K^T compounding period).
        final double bigT = dayCounter.yearFraction(startDate, maturity);

        // The fixing date for the inflation observation. NoInterpolation: snap
        // (maturity - swapObsLag) to inflation-period start.
        final Pair<Date, Date> fixingPeriod = InflationTermStructure.inflationPeriod(
                maturity.sub(swapObsLag), zii.frequency());
        final Date fixingDate = fixingPeriod.first();

        // Time from curve base date to fixing date, computed using the
        // C++ {@code inflationYearFraction(NoInterpolation)} convention:
        // {@code dc.yearFraction(period(baseDate).first, period(fixingDate).first)}.
        // Note this differs from the existing Java
        // {@link org.jquantlib.indexes.ZeroInflationIndex#fixing} which uses
        // {@code period(baseDate).second()} as anchor — that is a Java-side
        // divergence from C++ v1.42.1 that pre-dates this phase. We use the
        // v1.42.1 convention here for bit-faithful bootstrap math.
        final Pair<Date, Date> baseLim = InflationTermStructure.inflationPeriod(
                baseDate, zii.frequency());
        final double tCurve = termStructure.dayCounter().yearFraction(
                baseLim.first(), fixingDate);

        // Curve already snaps the date to its inflation-period start.
        final double zRate = termStructure.zeroRate(fixingDate, true /* extrapolate */);

        // Guard against pow of negative base with non-integer exponent (matches
        // C++ ZeroInflationIndex::forecastFixing).
        if (zRate <= -1.0) return 0.0;

        final double growth = JQuantMath.pow(1.0 + zRate, tCurve);
        return JQuantMath.pow(growth, 1.0 / bigT) - 1.0;
    }

    //
    // inspectors
    //

    public Period swapObsLag() { return swapObsLag; }
    public Date maturityDate() { return maturity; }
    public ZeroInflationIndex inflationIndex() { return zii; }

    /**
     * Pillar date — the curve node that this helper anchors. For the
     * NoInterpolation case (the only case supported in Phase 2p A.1) this is
     * the inflation-period start of {@code maturity - swapObsLag}.
     */
    public Date pillarDate() {
        return latestDate;
    }

    // Suppress unused-import noise: IndexManager is referenced in javadoc comments
    // about future cloned-handle behavior (kept for context when A.3 lands).
    @SuppressWarnings("unused")
    private static final Class<?> __unused_indexmgr = IndexManager.class;
}
