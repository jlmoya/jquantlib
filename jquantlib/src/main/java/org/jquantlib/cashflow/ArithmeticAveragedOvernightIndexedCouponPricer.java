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

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Arithmetic-averaging overnight-indexed coupon pricer.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/cashflows/overnightindexedcouponpricer.hpp/cpp}
 * {@code ArithmeticAveragedOvernightIndexedCouponPricer}.
 * <p>
 * <b>Phase 5d.5 MVP:</b> exact (non-Takada-approximation) implementation.
 * Uses the telescopic forward formula
 * {@code log(D_start/D_end)} for the forward portion.
 *
 * @category cashflows
 *
 * @author JQuantLib migration team
 */
public class ArithmeticAveragedOvernightIndexedCouponPricer extends OvernightIndexedCouponPricer {

    private final boolean byApprox_;
    @SuppressWarnings("unused")
    private final double mrs_;
    @SuppressWarnings("unused")
    private final double vol_;

    public ArithmeticAveragedOvernightIndexedCouponPricer() {
        this(false, 0.03, 0.00);
    }

    public ArithmeticAveragedOvernightIndexedCouponPricer(final boolean byApprox) {
        this(byApprox, 0.03, 0.00);
    }

    public ArithmeticAveragedOvernightIndexedCouponPricer(
            final boolean byApprox, final double meanReversion, final double vol) {
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
        final List<Date> fixingDates = coupon_.fixingDates();
        final List<Date> interestDates = coupon_.interestDates();
        final List<Date> valueDates = coupon_.valueDates();
        final double[] dt = coupon_.dt();
        final boolean applyObservationShift = coupon_.applyObservationShift();

        int i = 0;
        final int n = determineNumberOfFixings(interestDates, date, applyObservationShift);

        double accumulatedRate = 0.0;
        final Date today = new Settings().evaluationDate();

        // historical portion
        while (i < n && fixingDates.get(i).lt(today)) {
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
        if (i < n && fixingDates.get(i).equals(today)) {
            try {
                final double pastFixing = index.fixing(fixingDates.get(i));
                if (pastFixing != Constants.NULL_REAL) {
                    final double span = !date.lt(interestDates.get(i + 1))
                            ? dt[i]
                            : index.dayCounter().yearFraction(interestDates.get(i), date);
                    accumulatedRate += pastFixing * span;
                    ++i;
                }
            } catch (final Exception e) {
                // fall through and forecast
            }
        }

        // forward portion
        if (i < n) {
            final Handle<YieldTermStructure> curve = index.termStructure();
            QL.require(!curve.empty(),
                "null term structure set to this instance of " + index.name());

            if (byApprox_) {
                // Takada approximation: log(D_start/D_end), no convexity adj
                // (Phase 5d.5 MVP: convexity left out — caller should set
                // byApprox=false if convexity matters).
                final double startDiscount = curve.currentLink().discount(valueDates.get(i));
                final double endDiscount = curve.currentLink().discount(valueDates.get(n));
                accumulatedRate += Math.log(startDiscount / endDiscount);
            } else {
                while (i < n) {
                    final double forecastFixing = index.fixing(fixingDates.get(i));
                    final double span = !date.lt(interestDates.get(i + 1))
                            ? dt[i]
                            : index.dayCounter().yearFraction(interestDates.get(i), date);
                    accumulatedRate += forecastFixing * span;
                    ++i;
                }
            }
        }

        final double accruedPeriod = coupon_.dayCounter().yearFraction(
                coupon_.accrualStartDate(),
                Date.min(date, coupon_.accrualEndDate()),
                coupon_.referencePeriodStart(),
                coupon_.referencePeriodEnd());
        final double rate = accumulatedRate / accruedPeriod;
        return coupon_.gearing() * rate + coupon_.spread();
    }
}
