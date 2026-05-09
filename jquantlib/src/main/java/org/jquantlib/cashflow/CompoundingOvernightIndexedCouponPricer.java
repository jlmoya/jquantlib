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

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Compounding overnight-indexed coupon pricer.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/cashflows/overnightindexedcouponpricer.hpp/cpp}
 * {@code CompoundingOvernightIndexedCouponPricer::compute}.
 * <p>
 * <b>Phase 5d.5 MVP:</b> implements the canonical telescopic compounding
 * formula (start_disc / end_disc) for the forward portion plus per-fixing
 * compounding for the historical portion. Spread is added at the end (no
 * daily-spread compounding).
 *
 * @category cashflows
 *
 * @author JQuantLib migration team
 */
public class CompoundingOvernightIndexedCouponPricer extends OvernightIndexedCouponPricer {

    private double swapletRate_;

    @Override
    public double swapletRate() {
        swapletRate_ = computeRate(coupon_.accrualEndDate());
        return swapletRate_;
    }

    @Override
    public double averageRate(final Date date) {
        return computeRate(date);
    }

    /**
     * Compute the compounded average rate up to {@code date}, including
     * coupon spread and gearing.
     */
    private double computeRate(final Date date) {
        final Date today = new Settings().evaluationDate();
        final OvernightIndex index = coupon_.overnightIndex();

        final List<Date> fixingDates = coupon_.fixingDates();
        final List<Date> valueDates = coupon_.valueDates();
        final List<Date> interestDates = coupon_.interestDates();
        final double[] dt = coupon_.dt();
        final boolean applyObservationShift = coupon_.applyObservationShift();

        int i = 0;
        final int n = determineNumberOfFixings(interestDates, date, applyObservationShift);

        double compoundFactor = 1.0;

        // historical portion (fixing < today)
        while (i < n && fixingDates.get(i).lt(today)) {
            final double fixing = index.fixing(fixingDates.get(i));
            QL.require(fixing != Constants.NULL_REAL,
                "Missing " + index.name() + " fixing for " + fixingDates.get(i));
            final double span = !date.lt(interestDates.get(i + 1))
                    ? dt[i]
                    : index.dayCounter().yearFraction(interestDates.get(i), date);
            compoundFactor *= (1.0 + fixing * span);
            ++i;
        }

        // today: might or might not have been fixed
        if (i < n && fixingDates.get(i).equals(today)) {
            try {
                final double fixing = index.fixing(fixingDates.get(i));
                if (fixing != Constants.NULL_REAL) {
                    final double span = !date.lt(interestDates.get(i + 1))
                            ? dt[i]
                            : index.dayCounter().yearFraction(interestDates.get(i), date);
                    compoundFactor *= (1.0 + fixing * span);
                    ++i;
                }
            } catch (final Exception e) {
                // fall through and forecast
            }
        }

        // forward portion using telescopic formula where possible
        if (i < n) {
            final Handle<YieldTermStructure> curve = index.termStructure();
            QL.require(!curve.empty(),
                "null term structure set to this instance of " + index.name());

            if (!coupon_.canApplyTelescopicFormula()) {
                // fixing-by-fixing
                while (i < n) {
                    final double fixing = index.fixing(fixingDates.get(i));
                    final double span = !date.lt(interestDates.get(i + 1))
                            ? dt[i]
                            : index.dayCounter().yearFraction(interestDates.get(i), date);
                    compoundFactor *= (1.0 + fixing * span);
                    ++i;
                }
            } else {
                // telescopic formula: ratio of discount factors
                final double startDiscount = curve.currentLink().discount(valueDates.get(i));
                if (interestDates.get(n).equals(date)) {
                    final double endDiscount = curve.currentLink().discount(valueDates.get(n));
                    compoundFactor *= startDiscount / endDiscount;
                } else {
                    // last sub-period date != accrual end date: telescopic
                    // up to n-1, then a partial last fixing.
                    final double endDiscount = curve.currentLink().discount(valueDates.get(n - 1));
                    compoundFactor *= startDiscount / endDiscount;
                    final double fixing = index.fixing(fixingDates.get(n - 1));
                    final double span = !date.lt(interestDates.get(n))
                            ? dt[n - 1]
                            : index.dayCounter().yearFraction(interestDates.get(n - 1), date);
                    compoundFactor *= (1.0 + fixing * span);
                }
            }
        }

        final double accruedPeriod = coupon_.dayCounter().yearFraction(
                coupon_.accrualStartDate(),
                Date.min(date, coupon_.accrualEndDate()),
                coupon_.referencePeriodStart(),
                coupon_.referencePeriodEnd());
        final double rate = (compoundFactor - 1.0) / accruedPeriod;
        return coupon_.gearing() * rate + coupon_.spread();
    }
}
