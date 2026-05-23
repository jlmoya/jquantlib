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
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/overnightindexedcouponpricer.hpp/cpp}
 * {@code CompoundingOvernightIndexedCouponPricer::compute} — lookback / lockout / observation-shift / daily-spread
 * compounding all supported (Phase 5e.5b-CFC-d-107).
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
     * Single-fixing effective rate {@code span * (fixing + spreadToAdd)} — mirror of the C++ lambda in {@code compute}
     * (overnightindexedcouponpricer.cpp:174-182). Returns a 1-element {@code double[]} so the caller code reads
     * symmetrically.
     */
    private static double[] effectiveRate(final OvernightIndex index, final List< Date > fixingDates, final Date date,
            final List< Date > interestDates, final double[] dt, final double couponSpread, final int position,
            final boolean compoundSpreadDaily) {
        final double fixing = index.fixing(fixingDates.get(position));
        final double span = !date.lt(interestDates.get(position + 1))
                ? dt[position]
                : index.dayCounter().yearFraction(interestDates.get(position), date);
        final double spreadToAdd = compoundSpreadDaily ? couponSpread : 0.0;
        return new double[] { span * (fixing + spreadToAdd) };
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
     * (overnightindexedcouponpricer.cpp:108-259).
     */
    private double[] compute(final Date date) {
        final Date today = new Settings().evaluationDate();
        final OvernightIndex index = coupon_.overnightIndex();

        final List< Date > fixingDates = coupon_.fixingDates();
        final List< Date > valueDates = coupon_.valueDates();
        final List< Date > interestDates = coupon_.interestDates();
        final double[] dt = coupon_.dt();
        final boolean applyObservationShift = coupon_.applyObservationShift();
        final boolean compoundSpreadDaily = coupon_.compoundSpreadDaily();
        final double couponSpread = coupon_.spread();

        int i = 0;
        final int n = determineNumberOfFixings(interestDates, date, applyObservationShift);

        double compoundFactor = 1.0;
        double compoundFactorWithoutSpread = 1.0;

        // historical portion (fixing < today)
        while ( i < n && fixingDates.get(i).lt(today) ) {
            double fixing = index.fixing(fixingDates.get(i));
            QL.require(fixing != Constants.NULL_REAL, "Missing " + index.name() + " fixing for " + fixingDates.get(i));
            final double span = !date.lt(interestDates.get(i + 1))
                    ? dt[i]
                    : index.dayCounter().yearFraction(interestDates.get(i), date);
            if ( compoundSpreadDaily ) {
                compoundFactorWithoutSpread *= (1.0 + fixing * span);
                fixing += couponSpread;
            }
            compoundFactor *= (1.0 + fixing * span);
            ++i;
        }

        // today: might or might not have been fixed
        if ( i < n && fixingDates.get(i).equals(today) ) {
            try {
                double fixing = index.fixing(fixingDates.get(i));
                if ( fixing != Constants.NULL_REAL ) {
                    final double span = !date.lt(interestDates.get(i + 1))
                            ? dt[i]
                            : index.dayCounter().yearFraction(interestDates.get(i), date);
                    if ( compoundSpreadDaily ) {
                        compoundFactorWithoutSpread *= (1.0 + fixing * span);
                        fixing += couponSpread;
                    }
                    compoundFactor *= (1.0 + fixing * span);
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

            if ( !coupon_.canApplyTelescopicFormula() ) {
                // With lookback applied (and not the special obs-shift +
                // zero-fixing-delay case), telescopic cannot be used: project
                // each fixing.
                while ( i < n ) {
                    final double[] er = effectiveRate(index, fixingDates, date, interestDates, dt, couponSpread, i,
                            false);
                    final double[] erWith = effectiveRate(index, fixingDates, date, interestDates, dt, couponSpread, i,
                            compoundSpreadDaily);
                    compoundFactorWithoutSpread *= (1.0 + er[0]);
                    compoundFactor *= (1.0 + erWith[0]);
                    ++i;
                }
            } else {
                // No lookback (or special obs-shift case). Apply telescopic
                // formula up to potential lockout. A lockout may interrupt.
                final int nLockout = n - coupon_.lockoutDays();
                final boolean isLockoutApplied = coupon_.lockoutDays() > 0;

                final double startDiscount = curve.currentLink().discount(valueDates.get(Math.min(nLockout, i)));
                if ( interestDates.get(n).equals(date) || isLockoutApplied ) {
                    final double endDiscount = curve.currentLink().discount(valueDates.get(Math.min(nLockout, n)));
                    compoundFactor *= startDiscount / endDiscount;
                    compoundFactorWithoutSpread *= startDiscount / endDiscount;
                    // After the telescopic part, handle the locked-out tail
                    // fixing-by-fixing.
                    i = Math.max(nLockout, i);
                    while ( i < n ) {
                        final double[] er = effectiveRate(index, fixingDates, date, interestDates, dt, couponSpread, i,
                                false);
                        final double[] erWith = effectiveRate(index, fixingDates, date, interestDates, dt, couponSpread,
                                i, compoundSpreadDaily);
                        compoundFactorWithoutSpread *= (1.0 + er[0]);
                        compoundFactor *= (1.0 + erWith[0]);
                        ++i;
                    }
                } else {
                    // No lockout, date != last interest date: telescopic up
                    // to n-1, then a partial last fixing.
                    final double endDiscount = curve.currentLink().discount(valueDates.get(n - 1));
                    compoundFactor *= startDiscount / endDiscount;
                    compoundFactorWithoutSpread *= startDiscount / endDiscount;
                    final double[] erWith = effectiveRate(index, fixingDates, date, interestDates, dt, couponSpread,
                            n - 1, compoundSpreadDaily);
                    final double[] erWithout = effectiveRate(index, fixingDates, date, interestDates, dt, couponSpread,
                            n - 1, false);
                    compoundFactor *= (1.0 + erWith[0]);
                    compoundFactorWithoutSpread *= (1.0 + erWithout[0]);
                }
            }
        }

        // tau = day fraction over the full value-date span — used to back out
        // the effective spread when spread is compounded daily.
        final double tau = index.dayCounter().yearFraction(valueDates.get(0), valueDates.get(valueDates.size() - 1));
        final double accruedPeriodToDate = coupon_.dayCounter()
                .yearFraction(coupon_.accrualStartDate(), Date.min(date, coupon_.accrualEndDate()),
                        coupon_.referencePeriodStart(), coupon_.referencePeriodEnd());
        final double rate = (compoundFactor - 1.0) / accruedPeriodToDate;
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
