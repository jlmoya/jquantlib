/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2026 Zain Mughal

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.MakeMultipleResetsSwap;
import org.jquantlib.instruments.MultipleResetsSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Rate helper for bootstrapping over multiple-resets swap quotes.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/termstructures/yield/multipleresetsswaphelper.hpp/cpp}
 * {@code MultipleResetsSwapRateHelper}.
 *
 * <p>The helper builds an internal {@link MultipleResetsSwap} via
 * {@link MakeMultipleResetsSwap} and exposes the swap's fair fixed rate as the implied quote — the bootstrap iterates
 * on the term structure until the quoted rate matches.
 *
 * <p>Mirrors the C++ contract:
 * <ul>
 *   <li>The supplied {@code iborIndex} is cloned so it forwards rates from
 *       {@link #termStructureHandle_} (the bootstrap curve), but is
 *       <em>not</em> registered as an observer of that handle (otherwise
 *       bootstrap iteration would cascade observer notifications).</li>
 *   <li>{@code discountingCurve} is optional — when empty, the bootstrap
 *       curve doubles as the discount curve.</li>
 *   <li>The pillar / latestRelevantDate is the max of the last fixed-leg
 *       cashflow date and the last floating-leg cashflow date (mirrors
 *       C++ {@code latestRelevantDate_ = latestDate_ =
 *       std::max(swap_->fixedLeg().back()->date(),
 *       swap_->floatingLeg().back()->date())}).</li>
 * </ul>
 *
 * <p>Phase 5e.5b-CFC-d-186 port.
 *
 * @author JQuantLib migration team
 * @category termstructures
 */
public class MultipleResetsSwapRateHelper extends RelativeDateRateHelper {

    protected final int settlementDays_;
    protected final Period tenor_;
    /** Clone of the user-supplied ibor index, re-linked to {@link #termStructureHandle_}. */
    protected final IborIndex iborIndex_;
    protected final int resetsPerCoupon_;
    protected final RateAveraging.Type averagingMethod_;
    protected final double spread_;
    protected final Frequency fixedFrequency_;
    protected final DayCounter fixedDayCount_;
    protected final BusinessDayConvention fixedConvention_;
    protected final Handle< YieldTermStructure > discountHandle_;
    protected final RelinkableHandle< YieldTermStructure > termStructureHandle_ = new RelinkableHandle< YieldTermStructure >(
            null);
    protected final RelinkableHandle< YieldTermStructure > discountRelinkableHandle_ = new RelinkableHandle< YieldTermStructure >(
            null);
    protected MultipleResetsSwap swap_;

    /**
     * Most-common-case constructor (defaults: empty discountingCurve so the bootstrap curve is also the discount curve;
     * Compound averaging; zero spread; default fixed frequency derived from
     * {@code resetsPerCoupon * iborIndex.tenor()}; fixed day count from the index; ModifiedFollowing fixed
     * convention).
     */
    public MultipleResetsSwapRateHelper(final int settlementDays, final Period tenor, final Handle< Quote > fixedRate,
            final IborIndex iborIndex, final int resetsPerCoupon) {
        this(settlementDays, tenor, fixedRate, iborIndex, resetsPerCoupon, new Handle< YieldTermStructure >(),
                RateAveraging.Type.Compound, 0.0, Frequency.NoFrequency,
                null /* fixedDayCount -> defaults to iborIndex.dayCounter() */,
                BusinessDayConvention.ModifiedFollowing);
    }

    /**
     * Full constructor mirroring C++ ctor signature.
     *
     * @param fixedDayCount may be {@code null} (or empty) to default to {@code iborIndex.dayCounter()}.
     */
    public MultipleResetsSwapRateHelper(final int settlementDays, final Period tenor, final Handle< Quote > fixedRate,
            final IborIndex iborIndex, final int resetsPerCoupon, final Handle< YieldTermStructure > discountingCurve,
            final RateAveraging.Type averagingMethod, final double spread, final Frequency fixedFrequency,
            final DayCounter fixedDayCount, final BusinessDayConvention fixedConvention) {
        super(fixedRate);
        this.settlementDays_ = settlementDays;
        this.tenor_ = tenor;
        this.resetsPerCoupon_ = resetsPerCoupon;
        this.averagingMethod_ = averagingMethod;
        this.spread_ = spread;
        this.fixedFrequency_ = fixedFrequency;
        this.fixedDayCount_ = (fixedDayCount != null && !fixedDayCount.empty())
                ? fixedDayCount
                : iborIndex.dayCounter();
        this.fixedConvention_ = fixedConvention;
        this.discountHandle_ = discountingCurve;

        // Clone the index so its forwarding curve is the bootstrap curve
        // (termStructureHandle_), not whatever the caller supplied.
        // Mirrors C++ multipleresetsswaphelper.cpp:48
        //   iborIndex_ = dynamic_pointer_cast<IborIndex>(
        //                    iborIndex->clone(termStructureHandle_));
        // Java does not maintain the C++ pattern of explicit
        // `unregisterWith(termStructureHandle_)` because clone() in Java
        // does not register the clone as an observer of the handle — it
        // only wires the handle as the forwarding term structure.
        this.iborIndex_ = iborIndex.clone(termStructureHandle_).currentLink();

        this.iborIndex_.addObserver(this);
        if ( !discountHandle_.empty() ) {
            discountHandle_.currentLink().addObserver(this);
        }
        initializeDates();
    }

    @Override
    protected void initializeDates() {
        // Delegate swap construction to MakeMultipleResetsSwap to mirror C++
        // exactly. Port of C++ multipleresetsswaphelper.cpp:57-65.
        final MakeMultipleResetsSwap make = new MakeMultipleResetsSwap(tenor_, iborIndex_,
                resetsPerCoupon_).withFixedRate(0.0).withSettlementDays(settlementDays_)
                .withFixedLegFrequency(fixedFrequency_).withFixedLegDayCount(fixedDayCount_)
                .withFixedLegConvention(fixedConvention_).withFloatingLegSpread(spread_)
                .withAveragingMethod(averagingMethod_).withDiscountingTermStructure(discountRelinkableHandle_);
        swap_ = make.value();

        // Discounting: exogenous if provided, otherwise use the bootstrap
        // curve via termStructureHandle_ (relinked in setTermStructure).
        // We always set the engine ourselves rather than relying solely on
        // MakeMultipleResetsSwap.withDiscountingTermStructure — Java's
        // Handle.empty() semantics mean a RelinkableHandle with a null link
        // returns true from empty(), so MakeMultipleResetsSwap's engine
        // creation may be a no-op when discountRelinkableHandle_ is not yet
        // linked at construction time. Re-applying the engine here keeps
        // the helper robust against that interaction.
        if ( discountHandle_.empty() ) {
            swap_.setPricingEngine(new DiscountingSwapEngine(termStructureHandle_));
        } else {
            swap_.setPricingEngine(new DiscountingSwapEngine(discountHandle_));
        }

        // Port of C++ multipleresetsswaphelper.cpp:69-71:
        //   earliestDate_ = swap_->startDate();
        //   latestRelevantDate_ = latestDate_ =
        //       std::max(swap_->fixedLeg().back()->date(),
        //                swap_->floatingLeg().back()->date());
        earliestDate = swap_.startDate();
        final Leg fixedLeg = swap_.fixedLeg();
        final Leg floatingLeg = swap_.floatingLeg();
        final Date lastFixedDate = fixedLeg.get(fixedLeg.size() - 1).date();
        final Date lastFloatDate = floatingLeg.get(floatingLeg.size() - 1).date();
        latestDate = lastFixedDate.gt(lastFloatDate) ? lastFixedDate : lastFloatDate;
    }

    @Override
    public void setTermStructure(final YieldTermStructure t) {
        // Do not register as observer — bootstrap drives recalculation.
        // Mirrors C++ multipleresetsswaphelper.cpp:74-83.
        termStructureHandle_.linkTo(t, false);
        if ( discountHandle_.empty() ) {
            discountRelinkableHandle_.linkTo(t, false);
        } else {
            discountRelinkableHandle_.linkTo(discountHandle_.currentLink(), false);
        }
        super.setTermStructure(t);
    }

    @Override
    public double impliedQuote() {
        QL.require(termStructure != null, "term structure not set");
        swap_.recalculate();
        return swap_.fairRate();
    }

    /** @return the internal bootstrap swap. */
    public MultipleResetsSwap swap() {
        return swap_;
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< MultipleResetsSwapRateHelper > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
