/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo
 Copyright (C) 2017 BN Algorithms Ltd

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.Callability;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Bond;
import org.jquantlib.instruments.CallabilitySchedule;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops.DoubleOp;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Callable bond base class.
 * <p>
 * Port of C++ v1.42.1
 * {@code ql/experimental/callablebonds/callablebond.{hpp,cpp}}.
 * <p>
 * Base callable bond class for fixed and zero coupon bonds. Defines
 * commonalities between fixed and zero coupon callable bonds. At present, only
 * European and Bermudan put/call schedules are supported (no American
 * optionality), as defined by the {@link Callability} class.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ {@code class CallableBond::engine} is a typedef for
 *     {@code GenericEngine<arguments,results>}; in Java the engine class is
 *     spelled {@link CallableBondEngineImpl} for clarity.
 * <li>{@code OAS}, {@code cleanPriceOAS}, {@code effectiveDuration}, and
 *     {@code effectiveConvexity} require setting a continuously-compounded
 *     spread on the engine and re-pricing. JQuantLib's
 *     {@code OneFactorModel.ShortRateTree} does not yet expose
 *     {@code setSpread}; the OAS family throws {@link UnsupportedOperationException}
 *     until the underlying tree-engine spread machinery is wired up.
 * <li>The {@code tradingExCoupon} predicate does not yet exist on
 *     {@link CashFlow}; ex-coupon adjustments to the call price are therefore
 *     skipped (mirrors the C++ branch only when the cash flow does NOT trade
 *     ex-coupon).
 * </ul>
 */
public class CallableBond extends Bond {

    protected DayCounter paymentDayCounter_;
    protected Frequency frequency_;
    protected CallabilitySchedule putCallSchedule_;
    protected double faceAmount_;

    protected CallableBond(final int settlementDays, final Date maturityDate,
            final Calendar calendar, final DayCounter paymentDayCounter, final double faceAmount,
            final Date issueDate, final CallabilitySchedule putCallSchedule) {
        super(settlementDays, calendar, issueDate);
        this.paymentDayCounter_ = paymentDayCounter;
        this.putCallSchedule_ = putCallSchedule != null ? putCallSchedule : new CallabilitySchedule();
        this.faceAmount_ = faceAmount;
        this.maturityDate_ = maturityDate;

        if (!this.putCallSchedule_.isEmpty()) {
            Date finalOptionDate = Date.minDate();
            for (final Callability c : this.putCallSchedule_) {
                if (c.date().gt(finalOptionDate)) {
                    finalOptionDate = c.date();
                }
            }
            QL.require(finalOptionDate.le(maturityDate_),
                    "Bond cannot mature before last call/put date");
        }

        // derived classes must set cashflows_ and frequency_
    }

    /** return the bond's put/call schedule */
    public CallabilitySchedule callability() {
        return putCallSchedule_;
    }

    /**
     * Returns the Black implied forward yield volatility.
     * <p>
     * The forward yield volatility (see Hull, Fourth Edition, Chapter 20, pg
     * 536) is relevant only to European put/call schedules.
     */
    public double impliedVolatility(final Callability.Price targetPrice,
            final Handle<YieldTermStructure> discountCurve, final double accuracy,
            final int maxEvaluations, final double minVol, final double maxVol) {
        QL.require(!isExpired(), "instrument expired");

        final double dirtyTargetPrice;
        switch (targetPrice.type()) {
            case Dirty:
                dirtyTargetPrice = targetPrice.amount();
                break;
            case Clean:
                dirtyTargetPrice = targetPrice.amount() + accruedAmount();
                break;
            default:
                throw new IllegalArgumentException("unknown price type");
        }

        final double targetValue = dirtyTargetPrice * faceAmount_ / 100.0;
        final double guess = 0.5 * (minVol + maxVol);
        final ImpliedVolHelper f = new ImpliedVolHelper(this, discountCurve, targetValue, false);
        final Brent solver = new Brent();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(f, accuracy, guess, minVol, maxVol);
    }

    /**
     * Calculate the Option Adjusted Spread (OAS).
     * <p>
     * Not yet supported in JQuantLib — see class-level note about the missing
     * {@code setSpread} on {@code ShortRateTree}.
     */
    public double OAS(final double cleanPrice, final Handle<YieldTermStructure> engineTS,
            final DayCounter dayCounter, final org.jquantlib.termstructures.Compounding compounding,
            final Frequency frequency, final Date settlementDate, final double accuracy,
            final int maxIterations, final double guess) {
        throw new UnsupportedOperationException(
                "CallableBond.OAS requires ShortRateTree.setSpread which is not yet ported to JQuantLib");
    }

    /**
     * Calculate the clean price based on the given option-adjust-spread (oas)
     * over the given yield term structure (engineTS).
     */
    public double cleanPriceOAS(final double oas, final Handle<YieldTermStructure> engineTS,
            final DayCounter dayCounter, final org.jquantlib.termstructures.Compounding compounding,
            final Frequency frequency, final Date settlementDate) {
        throw new UnsupportedOperationException(
                "CallableBond.cleanPriceOAS requires ShortRateTree.setSpread which is not yet ported to JQuantLib");
    }

    /**
     * Calculate the effective duration: first differential of dirty price w.r.t.
     * a parallel shift of the yield term structure divided by current dirty price.
     */
    public double effectiveDuration(final double oas,
            final Handle<YieldTermStructure> engineTS, final DayCounter dayCounter,
            final org.jquantlib.termstructures.Compounding compounding, final Frequency frequency,
            final double bump) {
        throw new UnsupportedOperationException(
                "CallableBond.effectiveDuration requires ShortRateTree.setSpread which is not yet ported to JQuantLib");
    }

    /**
     * Calculate the effective convexity: second differential of dirty price w.r.t.
     * a parallel shift of the yield term structure divided by current dirty price.
     */
    public double effectiveConvexity(final double oas,
            final Handle<YieldTermStructure> engineTS, final DayCounter dayCounter,
            final org.jquantlib.termstructures.Compounding compounding, final Frequency frequency,
            final double bump) {
        throw new UnsupportedOperationException(
                "CallableBond.effectiveConvexity requires ShortRateTree.setSpread which is not yet ported to JQuantLib");
    }

    /**
     * Visibility-widened override of {@link Bond#setupArguments(PricingEngine.Arguments)}
     * so cross-package helpers (engines, ImpliedVolHelper) can call it directly,
     * mirroring C++ {@code CallableBond::setupArguments} which is public.
     */
    @Override
    public void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);

        QL.require(args instanceof CallableBondArgumentsImpl, "no arguments given");
        final CallableBondArgumentsImpl arguments = (CallableBondArgumentsImpl) args;

        final Date settlement = arguments.settlementDate;

        arguments.faceAmount = faceAmount_;
        arguments.redemption = redemption().amount();
        arguments.redemptionDate = redemption().date();

        final Leg cfs = cashflows();
        final CashFlow redemptionCf = redemption();

        arguments.couponDates = new ArrayList<Date>();
        arguments.couponAmounts = new ArrayList<Double>();

        // C++ skips cashflows.size()-1 (i.e. excludes the last) on the
        // assumption that the redemption is the last cash flow. JQuantLib's
        // EarlierThanCashFlowComparator is not a stable sort for equal-date
        // cash flows, so the last index may be a coupon (not the redemption);
        // identify the redemption by reference and skip it explicitly.
        for (int i = 0; i < cfs.size(); i++) {
            final CashFlow cf = cfs.get(i);
            if (cf == redemptionCf) {
                continue;
            }
            // Java port: tradingExCoupon predicate is not yet available on
            // CashFlow; treat all non-occurred cash flows as in-coupon.
            if (!cf.hasOccurred(settlement, false)) {
                arguments.couponDates.add(cf.date());
                arguments.couponAmounts.add(cf.amount());
            }
        }

        arguments.callabilityPrices = new ArrayList<Double>();
        arguments.callabilityDates = new ArrayList<Date>();

        arguments.paymentDayCounter = paymentDayCounter_;
        arguments.frequency = frequency_;

        arguments.putCallSchedule = putCallSchedule_;
        for (final Callability c : putCallSchedule_) {
            if (!c.hasOccurred(settlement, false)) {
                arguments.callabilityDates.add(c.date());
                arguments.callabilityPrices.add(c.price().amount());

                if (c.price().type() == Callability.Price.Type.Clean) {
                    /* Convert clean call price to dirty using accrued interest
                       at the call date. We ignore ex-coupon conventions here
                       because the call is an issuer action governed by the
                       indenture: the holder receives the call price plus accrued
                       from the last payment date. */
                    final Date callDate = c.date();
                    double callAccrued = 0.0;
                    for (final CashFlow cf : cashflows_) {
                        if (!cf.hasOccurred(callDate, false)) {
                            if (cf instanceof Coupon) {
                                final Coupon coupon = (Coupon) cf;
                                final double acc = coupon.accruedAmount(callDate);
                                callAccrued = acc / notional(callDate) * 100.0;
                            }
                            break;
                        }
                    }
                    final int last = arguments.callabilityPrices.size() - 1;
                    arguments.callabilityPrices.set(last,
                            arguments.callabilityPrices.get(last) + callAccrued);
                }
            }
        }

        arguments.spread = 0.0;
    }

    /**
     * Helper class for Black implied volatility calculation. Mirrors the
     * private helper inside C++ {@code callablebond.cpp}.
     */
    static class ImpliedVolHelper implements DoubleOp {
        private final PricingEngine engine_;
        private final double targetValue_;
        private final boolean matchNPV_;
        private final SimpleQuote vol_;
        private final CallableBondResultsImpl results_;

        ImpliedVolHelper(final CallableBond bond, final Handle<YieldTermStructure> discountCurve,
                final double targetValue, final boolean matchNPV) {
            this.targetValue_ = targetValue;
            this.matchNPV_ = matchNPV;
            this.vol_ = new SimpleQuote(0.0);
            this.engine_ = new BlackCallableFixedRateBondEngine(
                    new Handle<Quote>(this.vol_), discountCurve);

            bond.setupArguments(this.engine_.getArguments());
            this.results_ = (CallableBondResultsImpl) this.engine_.getResults();
        }

        @Override
        public double op(final double x) {
            vol_.setValue(x);
            engine_.calculate();
            final double value = matchNPV_ ? results_.value : results_.settlementValue;
            return value - targetValue_;
        }
    }

    //
    // inner classes (mirrors C++ CallableBond::arguments / results / engine)
    //

    /** Marker interface for callable bond arguments. */
    public interface Arguments extends Bond.Arguments {
    }

    /** Marker interface for callable bond results. */
    public interface Results extends Bond.Results {
    }

    /** Concrete arguments implementation. */
    public static class CallableBondArgumentsImpl extends Bond.ArgumentsImpl
            implements CallableBond.Arguments {

        public List<Date> couponDates;
        public List<Double> couponAmounts;
        public double faceAmount;
        /** redemption = face amount * redemption / 100. */
        public double redemption;
        public Date redemptionDate;
        public DayCounter paymentDayCounter;
        public Frequency frequency;
        public CallabilitySchedule putCallSchedule;
        /** bond full/dirty/cash prices */
        public List<Double> callabilityPrices;
        public List<Date> callabilityDates;
        /**
         * Spread to apply to the valuation. This is a continuously compounded
         * rate added to the model. Currently only applied by the
         * {@code TreeCallableFixedRateBondEngine} (when wired up).
         */
        public double spread;

        @Override
        public void validate() {
            QL.require(settlementDate != null && !settlementDate.isNull(),
                    "null settlement date");
            QL.require(redemption != Constants.NULL_REAL, "null redemption");
            QL.require(redemption >= 0.0, "positive redemption required");
            QL.require(callabilityDates.size() == callabilityPrices.size(),
                    "different number of callability dates and prices");
            QL.require(couponDates.size() == couponAmounts.size(),
                    "different number of coupon dates and amounts");
        }
    }

    /** Concrete results implementation. */
    public static class CallableBondResultsImpl extends Bond.ResultsImpl
            implements CallableBond.Results {
        // no extra results set yet
    }

    /** Base class for callable bond pricing engines. */
    public abstract static class CallableBondEngineImpl
            extends GenericEngine<CallableBond.Arguments, CallableBond.Results>
            implements Bond.Engine {

        protected CallableBondEngineImpl() {
            super(new CallableBondArgumentsImpl(), new CallableBondResultsImpl());
        }
    }
}
