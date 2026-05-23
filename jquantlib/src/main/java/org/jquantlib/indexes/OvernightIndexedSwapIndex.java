/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.indexes;

import org.jquantlib.QL;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.currencies.Currency;
import org.jquantlib.instruments.MakeOIS;
import org.jquantlib.instruments.OvernightIndexedSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Base class for overnight indexed swap indexes.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/indexes/swapindex.hpp/cpp}
 * {@code OvernightIndexedSwapIndex}.
 *
 * @author JQuantLib migration team
 * @category indexes
 */
public class OvernightIndexedSwapIndex extends SwapIndex {

    //
    // protected fields
    //

    protected final OvernightIndex overnightIndex_;
    protected final boolean telescopicValueDates_;
    protected final RateAveraging.Type averagingMethod_;
    // cache data to avoid swap recreation when the same fixing date
    // is used multiple time to forecast changing fixing
    protected OvernightIndexedSwap lastSwap_;
    protected Date lastFixingDate_;

    //
    // public constructors
    //

    public OvernightIndexedSwapIndex(final String familyName,
                                     final Period tenor,
                                     final /*@Natural*/ int settlementDays,
                                     final Currency currency,
                                     final OvernightIndex overnightIndex,
                                     final boolean telescopicValueDates,
                                     final RateAveraging.Type averagingMethod) {
        super(familyName,
              tenor,
              settlementDays,
              currency,
              overnightIndex.fixingCalendar(),
              new Period(1, TimeUnit.Years),
              BusinessDayConvention.ModifiedFollowing,
              overnightIndex.dayCounter(),
              // SwapIndex requires an IborIndex; the overnight index is itself
              // an IborIndex subclass (OvernightIndex extends IborIndex), so we
              // can pass it directly to satisfy the parent's iborIndex field
              // (mirrors C++ which forwards overnightIndex into the SwapIndex
              // ctor at swapindex.cpp:192).
              overnightIndex);
        this.overnightIndex_ = overnightIndex;
        this.telescopicValueDates_ = telescopicValueDates;
        this.averagingMethod_ = averagingMethod;
    }

    public OvernightIndexedSwapIndex(final String familyName,
                                     final Period tenor,
                                     final /*@Natural*/ int settlementDays,
                                     final Currency currency,
                                     final OvernightIndex overnightIndex,
                                     final boolean telescopicValueDates) {
        this(familyName, tenor, settlementDays, currency, overnightIndex,
             telescopicValueDates, RateAveraging.Type.Compound);
    }

    public OvernightIndexedSwapIndex(final String familyName,
                                     final Period tenor,
                                     final /*@Natural*/ int settlementDays,
                                     final Currency currency,
                                     final OvernightIndex overnightIndex) {
        this(familyName, tenor, settlementDays, currency, overnightIndex,
             false, RateAveraging.Type.Compound);
    }

    //
    // public methods
    //

    public OvernightIndex overnightIndex() {
        return overnightIndex_;
    }

    /**
     * Returns the underlying OIS for the given fixing date.
     * <p>
     * <b>Warning:</b> relinking the term structure underlying the index will
     * not have effect on the returned swap.
     */
    public OvernightIndexedSwap underlyingOvernightIndexedSwap(final Date fixingDate) {
        QL.require(fixingDate != null && !fixingDate.isNull(), "null fixing date");

        // caching mechanism
        if (lastFixingDate_ == null || !lastFixingDate_.eq(fixingDate)) {
            final double fixedRate = 0.0;
            this.lastSwap_ = new MakeOIS(this.tenor, overnightIndex_, fixedRate)
                .withEffectiveDate(valueDate(fixingDate))
                .withFixedLegDayCount(dayCounter)
                .withTelescopicValueDates(telescopicValueDates_)
                .withAveragingMethod(averagingMethod_)
                .value();
            this.lastFixingDate_ = fixingDate;
        }
        return lastSwap_;
    }

    /**
     * SwapIndex#underlyingSwap returns a {@link VanillaSwap}; OIS indexes do
     * not produce a VanillaSwap. C++ overloads the method by return type which
     * is not legal in Java, so we expose the OIS via
     * {@link #underlyingOvernightIndexedSwap(Date)} above and override
     * {@code underlyingSwap} to throw — callers must use the OIS-typed accessor.
     */
    @Override
    public VanillaSwap underlyingSwap(final Date fixingDate) {
        throw new UnsupportedOperationException(
                "OvernightIndexedSwapIndex does not expose a VanillaSwap; "
                        + "use underlyingOvernightIndexedSwap(Date) instead");
    }

    @Override
    public Date maturityDate(final Date valueDate) {
        final Date fixDate = fixingDate(valueDate);
        return underlyingOvernightIndexedSwap(fixDate).maturityDate();
    }
}
