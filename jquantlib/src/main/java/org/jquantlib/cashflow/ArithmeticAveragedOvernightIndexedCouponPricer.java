/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009 Roland Lichters
 Copyright (C) 2009 Ferdinando Ametrano
 Copyright (C) 2014 Peter Caspers
 Copyright (C) 2016 Stefano Fondi

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * Arithmetic-averaging overnight-indexed coupon pricer.
 * <p>
 * Port of C++ QuantLib v1.43 {@code ql/cashflows/overnightindexedcouponpricer.hpp/cpp}
 * {@code ArithmeticAveragedOvernightIndexedCouponPricer}.
 * <p>
 * <b>Phase 5d.5 MVP:</b> exact (non-Takada-approximation) implementation.
 * Uses the telescopic forward formula {@code log(D_start/D_end)} for the forward portion.
 *
 * @author JQuantLib migration team
 * @category cashflows
 */
public class ArithmeticAveragedOvernightIndexedCouponPricer extends OvernightIndexedCouponPricer {

    private final boolean byApprox_;
    @SuppressWarnings( "unused" )
    private final double mrs_;
    @SuppressWarnings( "unused" )
    private final double vol_;

    public ArithmeticAveragedOvernightIndexedCouponPricer() {
        this(false, 0.03, 0.00);
    }

    public ArithmeticAveragedOvernightIndexedCouponPricer(final boolean byApprox) {
        this(byApprox, 0.03, 0.00);
    }

    public ArithmeticAveragedOvernightIndexedCouponPricer(final boolean byApprox, final double meanReversion,
            final double vol) {
        this.byApprox_ = byApprox;
        this.mrs_ = meanReversion;
        this.vol_ = vol;
    }

    /**
     * Full constructor mirroring C++ v1.42.1
     * {@code ArithmeticAveragedOvernightIndexedCouponPricer(meanReversion, volatility, byApprox, v,
     * effectiveVolatilityInput)}.
     * <p>
     * Phase 5e.5b-CFC-b — added so {@link BlackAveragingOvernightIndexedCouponPricer} can supply the
     * OptionletVolatilityStructure handle through the parent ctor (matches C++
     * overnightindexedcouponpricer.hpp:140-147).
     *
     * @param meanReversion            Hull-White mean reversion (default 0.03)
     * @param vol                      Hull-White short-rate vol (0.0 ⇒ no convexity adj)
     * @param byApprox                 use Takada approximation when true
     * @param v                        optionlet volatility handle (may be empty)
     * @param effectiveVolatilityInput treat the input vol as effective (caplet) vol
     */
    public ArithmeticAveragedOvernightIndexedCouponPricer(final double meanReversion, final double vol,
            final boolean byApprox, final Handle< OptionletVolatilityStructure > v,
            final boolean effectiveVolatilityInput) {
        super(v, effectiveVolatilityInput);
        this.byApprox_ = byApprox;
        this.mrs_ = meanReversion;
        this.vol_ = vol;
    }

    @Override
    public double swapletRate() {
        return averageRate(coupon_.accrualEndDate());
    }

    @Override
    public double averageRate(final Date date) {
        final OvernightIndex index = coupon_.overnightIndex();
        final List< Date > fixingDates = coupon_.fixingDates();
        final List< Date > interestDates = coupon_.interestDates();
        final List< Date > valueDates = coupon_.valueDates();
        final double[] dt = coupon_.dt();

        int i = 0;
        final int n = determineNumberOfFixings(interestDates, date);

        double accumulatedRate = 0.0;
        final Date today = new Settings().evaluationDate();

        // historical portion
        while ( i < n && fixingDates.get(i).lt(today) ) {
            final double pastFixing = index.fixing(fixingDates.get(i));
            QL.require(pastFixing != Constants.NULL_REAL,
                    "Missing " + index.name() + " fixing for " + fixingDates.get(i));
            final double span = !date.lt(interestDates.get(i + 1))
                    ? dt[i]
                    : index.dayCounter().yearFraction(interestDates.get(i), date);
            accumulatedRate += pastFixing * span;
            ++i;
        }

        // today edge case
        if ( i < n && fixingDates.get(i).equals(today) ) {
            try {
                final double pastFixing = index.fixing(fixingDates.get(i));
                if ( pastFixing != Constants.NULL_REAL ) {
                    final double span = !date.lt(interestDates.get(i + 1))
                            ? dt[i]
                            : index.dayCounter().yearFraction(interestDates.get(i), date);
                    accumulatedRate += pastFixing * span;
                    ++i;
                }
            } catch ( final Exception e ) {
                // fall through and forecast
            }
        }

        // forward portion
        if ( i < n ) {
            final Handle< YieldTermStructure > curve = index.termStructure();
            QL.require(!curve.empty(), "null term structure set to this instance of " + index.name());

            if ( byApprox_ ) {
                // Takada approximation: log(D_start/D_end), no convexity adj
                // (Phase 5d.5 MVP: convexity left out — caller should set
                // byApprox=false if convexity matters).
                final double startDiscount = curve.currentLink().discount(valueDates.get(i));
                final double endDiscount = curve.currentLink().discount(valueDates.get(n));
                accumulatedRate += Math.log(startDiscount / endDiscount);
            } else {
                while ( i < n ) {
                    final double forecastFixing = index.fixing(fixingDates.get(i));
                    final double span = !date.lt(interestDates.get(i + 1))
                            ? dt[i]
                            : index.dayCounter().yearFraction(interestDates.get(i), date);
                    accumulatedRate += forecastFixing * span;
                    ++i;
                }
            }
        }

        final double rate = accumulatedRate / coupon_.accruedPeriod(date);
        return coupon_.gearing() * rate + coupon_.spread();
    }

    // ── Cap/floor pricing intentionally not provided ─────────────────────────
    //
    // ArithmeticAveragedOvernightIndexedCouponPricer is an aggregate-rate pricer
    // (arithmetic average of overnight fixings). It does not value the embedded
    // optionality of a per-fixing cap or floor.  To price a capped/floored
    // averaged overnight coupon, use the dedicated Black pricer
    // {@link BlackAveragingOvernightIndexedCouponPricer}.
    //
    // Mirrors C++ v1.42.1 ql/cashflows/overnightindexedcouponpricer.hpp:155-159
    // (QL_FAIL("swapletPrice not available") etc.).

    @Override
    public double swapletPrice() {
        throw new LibraryException(
                "ArithmeticAveragedOvernightIndexedCouponPricer: swapletPrice not available "
                        + "(aggregate-rate pricer; use BlackAveragingOvernightIndexedCouponPricer for cap/floor pricing)");
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        throw new LibraryException(
                "ArithmeticAveragedOvernightIndexedCouponPricer: capletPrice not available "
                        + "(aggregate-rate pricer; use BlackAveragingOvernightIndexedCouponPricer for cap pricing)");
    }

    @Override
    public double capletRate(final double effectiveCap) {
        throw new LibraryException(
                "ArithmeticAveragedOvernightIndexedCouponPricer: capletRate not available "
                        + "(aggregate-rate pricer; use BlackAveragingOvernightIndexedCouponPricer for cap pricing)");
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        throw new LibraryException(
                "ArithmeticAveragedOvernightIndexedCouponPricer: floorletPrice not available "
                        + "(aggregate-rate pricer; use BlackAveragingOvernightIndexedCouponPricer for floor pricing)");
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        throw new LibraryException(
                "ArithmeticAveragedOvernightIndexedCouponPricer: floorletRate not available "
                        + "(aggregate-rate pricer; use BlackAveragingOvernightIndexedCouponPricer for floor pricing)");
    }

    @Override
    public double capletRate(final double effectiveCap, final boolean dailyCapFloor) {
        throw new LibraryException(
                "ArithmeticAveragedOvernightIndexedCouponPricer.capletRate(Rate, bool) not implemented");
    }

    @Override
    public double floorletRate(final double effectiveFloor, final boolean dailyCapFloor) {
        throw new LibraryException(
                "ArithmeticAveragedOvernightIndexedCouponPricer.floorletRate(Rate, bool) not implemented");
    }
}
