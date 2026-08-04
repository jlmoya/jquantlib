/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2009 Roland Lichters
 Copyright (C) 2009 Ferdinando Ametrano
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.Date;

import java.util.List;

/**
 * Compounding overnight-indexed coupon pricer.
 * <p>
 * Port of C++ QuantLib v1.43 {@code ql/cashflows/overnightindexedcouponpricer.hpp/cpp}
 * {@code CompoundingOvernightIndexedCouponPricer::compute} — lookback / lockout / observation-shift / daily-spread
 * compounding all supported.
 * <p>
 * The v1.43 rework: one {@code growthFactor} helper covers both the fixed and the projected periods; the telescopic
 * range is bounded by a start index (partial first period when the first interest date is a fixing holiday) and an
 * end index (lockout, and a partial last period when pricing to a date inside it); the rate is annualised over the
 * span actually compounded rather than over the coupon's accrued period; and the forecast curve must demonstrably
 * cover the coupon.
 *
 * @author JQuantLib migration team
 * @category cashflows
 */
public class CompoundingOvernightIndexedCouponPricer extends OvernightIndexedCouponPricer {

    private double swapletRate_;
    private double effectiveSpread_;
    private double effectiveIndexFixing_;

    public CompoundingOvernightIndexedCouponPricer() {
        super();
    }

    public CompoundingOvernightIndexedCouponPricer(final Handle< OptionletVolatilityStructure > v,
            final boolean effectiveVolatilityInput) {
        super(v, effectiveVolatilityInput);
    }

    /**
     * Single-fixing growth factors — mirror of the C++ {@code growthFactor} lambda in {@code compute}
     * (overnightindexedcouponpricer.cpp:117-125, v1.43).
     * <p>
     * Returns {@code {gf, gfSpread}} where {@code gf = 1 + fixing*span} and, when the spread is compounded daily,
     * {@code gfSpread = gf + spread*span}; otherwise {@code gfSpread == gf}.
     * <p>
     * With observation shift the sub-period span is always the coupon's own {@code dt[idx]} — the interest dates then
     * no longer bracket {@code date}, so truncating on {@code date} would be meaningless.
     */
    private static double[] growthFactor(final double fixing, final int idx, final Date date,
            final List< Date > interestDates, final double[] dt, final DayCounter dc,
            final boolean applyObservationShift, final boolean compoundSpreadDaily, final double couponSpread) {
        final double span = (applyObservationShift || !date.lt(interestDates.get(idx + 1)))
                ? dt[idx]
                : dc.yearFraction(interestDates.get(idx), date);
        final double gf = 1.0 + fixing * span;
        final double gfSpread = compoundSpreadDaily ? gf + couponSpread * span : gf;
        return new double[] { gf, gfSpread };
    }

    @Override
    public double swapletRate() {
        final double[] result = compute(coupon_.accrualEndDate());
        swapletRate_ = result[0];
        effectiveSpread_ = result[1];
        effectiveIndexFixing_ = result[2];
        return swapletRate_;
    }

    @Override
    public double averageRate(final Date date) {
        return compute(date)[0];
    }

    // ── Cap/floor pricing intentionally not provided ─────────────────────────
    //
    // CompoundingOvernightIndexedCouponPricer is an aggregate-rate pricer: it
    // returns the realized compounded swaplet rate over the full coupon period
    // and does not value the embedded optionality of a per-fixing cap or floor.
    // To price a CappedFlooredOvernightIndexedCoupon, use the Black-formula
    // pricer {@link BlackOvernightIndexedCouponPricer} (or one of its derived
    // variants) which supports caplet/floorlet rates.
    //
    // Mirrors C++ v1.42.1 ql/cashflows/overnightindexedcouponpricer.hpp:113-117
    // (QL_FAIL("swapletPrice not available") etc.).

    @Override
    public double swapletPrice() {
        throw new LibraryException(
                "CompoundingOvernightIndexedCouponPricer: swapletPrice not available "
                        + "(aggregate-rate pricer; use a Black pricer for capped/floored coupons)");
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        throw new LibraryException(
                "CompoundingOvernightIndexedCouponPricer: capletPrice not available "
                        + "(aggregate-rate pricer; use BlackOvernightIndexedCouponPricer for cap pricing)");
    }

    @Override
    public double capletRate(final double effectiveCap) {
        throw new LibraryException(
                "CompoundingOvernightIndexedCouponPricer: capletRate not available "
                        + "(aggregate-rate pricer; use BlackOvernightIndexedCouponPricer for cap pricing)");
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        throw new LibraryException(
                "CompoundingOvernightIndexedCouponPricer: floorletPrice not available "
                        + "(aggregate-rate pricer; use BlackOvernightIndexedCouponPricer for floor pricing)");
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        throw new LibraryException(
                "CompoundingOvernightIndexedCouponPricer: floorletRate not available "
                        + "(aggregate-rate pricer; use BlackOvernightIndexedCouponPricer for floor pricing)");
    }

    @Override
    public double capletRate(final double effectiveCap, final boolean dailyCapFloor) {
        throw new LibraryException(
                "CompoundingOvernightIndexedCouponPricer.capletRate(Rate, bool) not implemented");
    }

    @Override
    public double floorletRate(final double effectiveFloor, final boolean dailyCapFloor) {
        throw new LibraryException(
                "CompoundingOvernightIndexedCouponPricer.floorletRate(Rate, bool) not implemented");
    }

    public double effectiveSpread() {
        effectiveSpread_ = compute(coupon_.accrualEndDate())[1];
        return effectiveSpread_;
    }

    public double effectiveIndexFixing() {
        effectiveIndexFixing_ = compute(coupon_.accrualEndDate())[2];
        return effectiveIndexFixing_;
    }

    /**
     * Compute {@code (swapletRate, effectiveSpread, effectiveIndexFixing)} up to {@code date}.
     * <p>
     * Mirror of C++ {@code CompoundingOvernightIndexedCouponPricer::compute}
     * (v1.43 overnightindexedcouponpricer.cpp:100-215).
     */
    private double[] compute(final Date date) {
        final Date today = new Settings().evaluationDate();
        final OvernightIndex index = coupon_.overnightIndex();

        final List< Date > fixingDates = coupon_.fixingDates();
        final List< Date > valueDates = coupon_.valueDates();
        final List< Date > interestDates = coupon_.interestDates();
        final double[] dt = coupon_.dt();
        // C++ v1.43: observation shift only bites when the coupon actually has a
        // lookback; with fixingDays() == 0 value dates and interest dates coincide
        // in the interior, so the flag would be a no-op at best and wrong at worst.
        final boolean applyObservationShift = coupon_.applyObservationShift() && coupon_.fixingDays() > 0;
        final DayCounter dc = index.dayCounter();
        final boolean compoundSpreadDaily = coupon_.compoundSpreadDaily();
        final double couponSpread = coupon_.spread();

        int i = 0;
        final int n = determineNumberOfFixings(interestDates, date);

        double compoundFactor = 1.0;
        double compoundFactorWithoutSpread = 1.0;

        // historical portion (fixing < today)
        while ( i < n && fixingDates.get(i).lt(today) ) {
            final double fixing = index.fixing(fixingDates.get(i));
            QL.require(fixing != Constants.NULL_REAL, "Missing " + index.name() + " fixing for " + fixingDates.get(i));
            final double[] g = growthFactor(fixing, i, date, interestDates, dt, dc, applyObservationShift,
                    compoundSpreadDaily, couponSpread);
            compoundFactorWithoutSpread *= g[0];
            compoundFactor *= g[1];
            ++i;
        }

        // today: might or might not have been fixed
        if ( i < n && fixingDates.get(i).equals(today) ) {
            try {
                final double fixing = index.fixing(fixingDates.get(i));
                if ( fixing != Constants.NULL_REAL ) {
                    final double[] g = growthFactor(fixing, i, date, interestDates, dt, dc, applyObservationShift,
                            compoundSpreadDaily, couponSpread);
                    compoundFactorWithoutSpread *= g[0];
                    compoundFactor *= g[1];
                    ++i;
                }
            } catch ( final Exception e ) {
                // fall through and forecast
            }
        }

        // forward portion using telescopic formula where possible
        if ( i < n ) {
            final Handle< YieldTermStructure > curve = index.termStructure();
            QL.require(!curve.empty(), "null term structure set to this instance of " + index.name());
            final YieldTermStructure ts = curve.currentLink();
            QL.require(ts.referenceDate().le(valueDates.get(i))
                            && (ts.allowsExtrapolation() || valueDates.get(n).le(ts.maxDate())),
                    "coupon requires a range [" + valueDates.get(i) + ", " + valueDates.get(n)
                            + "] wider than is supported by the term structure for instance of" + index.name() + " ["
                            + ts.referenceDate() + ", " + (ts.allowsExtrapolation() ? Date.maxDate() : ts.maxDate())
                            + "]");

            if ( coupon_.canApplyTelescopicFormula() ) {
                // Handle a partial accrual of the first fixing when the first interest
                // date lands on a fixing holiday: the first value date then precedes
                // the first interest date, so its growth factor cannot be telescoped.
                final int telescopicStartIdx =
                        (i == 0 && !applyObservationShift && valueDates.get(0).lt(interestDates.get(0))) ? 1 : i;
                // Telescopic formula up to a potential lockout, and up to the partial
                // accrual at `date`.
                final int endDateIdx = Math.min(n, valueDates.size() - 1 - coupon_.lockoutDays());
                final int telescopicEndIdx = endDateIdx
                        - ((applyObservationShift || valueDates.get(endDateIdx).le(date)) ? 0 : 1);
                if ( telescopicStartIdx < telescopicEndIdx ) {
                    while ( i < telescopicStartIdx ) {
                        // compound up any periods ahead of the telescopic range
                        final double[] g = growthFactor(index.fixing(fixingDates.get(i)), i, date, interestDates, dt,
                                dc, applyObservationShift, compoundSpreadDaily, couponSpread);
                        compoundFactorWithoutSpread *= g[0];
                        compoundFactor *= g[1];
                        ++i;
                    }
                    final double startDiscount = ts.discount(valueDates.get(telescopicStartIdx));
                    final double endDiscount = ts.discount(valueDates.get(telescopicEndIdx));
                    compoundFactor *= startDiscount / endDiscount;
                    compoundFactorWithoutSpread *= startDiscount / endDiscount;
                    i = telescopicEndIdx;
                }
            }
            // compound up any remaining periods
            while ( i < n ) {
                final double[] g = growthFactor(index.fixing(fixingDates.get(i)), i, date, interestDates, dt, dc,
                        applyObservationShift, compoundSpreadDaily, couponSpread);
                compoundFactorWithoutSpread *= g[0];
                compoundFactor *= g[1];
                ++i;
            }
        }

        // The rate is annualised over the span the compounding actually covered:
        // the observation (value) dates under observation shift, otherwise the
        // interest dates truncated at `date`.
        final Date rateAccrualStartDate = applyObservationShift ? valueDates.get(0) : interestDates.get(0);
        final Date rateAccrualEndDate = applyObservationShift
                ? valueDates.get(n)
                : Date.min(date, interestDates.get(n));
        final double tau = dc.yearFraction(rateAccrualStartDate, rateAccrualEndDate);
        final double rate = (compoundFactor - 1.0) / tau;
        double swapletRate = coupon_.gearing() * rate;
        final double effectiveSpread;
        final double effectiveIndexFixing;
        if ( !compoundSpreadDaily ) {
            swapletRate += couponSpread;
            effectiveSpread = couponSpread;
            effectiveIndexFixing = rate;
        } else {
            effectiveSpread = rate - (compoundFactorWithoutSpread - 1.0) / tau;
            effectiveIndexFixing = rate - effectiveSpread;
        }
        return new double[] { swapletRate, effectiveSpread, effectiveIndexFixing };
    }
}
