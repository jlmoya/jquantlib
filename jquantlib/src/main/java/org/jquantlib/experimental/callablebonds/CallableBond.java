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
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

import java.util.ArrayList;
import java.util.List;

/**
 * Callable bond base class.
 * <p>
 * Port of C++ v1.42.1 {@code ql/experimental/callablebonds/callablebond.{hpp,cpp}}.
 * <p>
 * Base callable bond class for fixed and zero coupon bonds. Defines commonalities between fixed and zero coupon
 * callable bonds. At present, only European and Bermudan put/call schedules are supported (no American optionality), as
 * defined by the {@link Callability} class.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ {@code class CallableBond::engine} is a typedef for
 *     {@code GenericEngine<arguments,results>}; in Java the engine class is
 *     spelled {@link CallableBondEngineImpl} for clarity.
 * <li>{@code OAS} / {@code cleanPriceOAS} / {@code effectiveDuration} /
 *     {@code effectiveConvexity} are implemented in terms of the engine's
 *     {@code arguments.spread} hook; tree-engine spread support is wired
 *     through {@link org.jquantlib.model.shortrate.onefactormodels.OneFactorModel.ShortRateTree#setSpread(double)}.
 * <li>Phase 5e.5b-CFC-d-253 — the {@code tradingExCoupon} ex-coupon
 *     filter on coupon dates/amounts and the ex-coupon branch of the
 *     Call-price clean→dirty conversion (mirroring C++
 *     {@code callablebond.cpp:426-430} and {@code :463-464}) are now
 *     wired so OAS stays continuous through the ex-coupon window
 *     (see QL GitHub issue #2236).
 * </ul>
 */
public class CallableBond extends Bond {

    protected DayCounter paymentDayCounter_;
    protected Frequency frequency_;
    protected CallabilitySchedule putCallSchedule_;
    protected double faceAmount_;

    protected CallableBond(final int settlementDays, final Date maturityDate, final Calendar calendar,
            final DayCounter paymentDayCounter, final double faceAmount, final Date issueDate,
            final CallabilitySchedule putCallSchedule) {
        super(settlementDays, calendar, issueDate);
        this.paymentDayCounter_ = paymentDayCounter;
        this.putCallSchedule_ = putCallSchedule != null ? putCallSchedule : new CallabilitySchedule();
        this.faceAmount_ = faceAmount;
        this.maturityDate_ = maturityDate;

        if ( !this.putCallSchedule_.isEmpty() ) {
            Date finalOptionDate = Date.minDate();
            for ( final Callability c : this.putCallSchedule_ ) {
                if ( c.date().gt(finalOptionDate) ) {
                    finalOptionDate = c.date();
                }
            }
            QL.require(finalOptionDate.le(maturityDate_), "Bond cannot mature before last call/put date");
        }

        // derived classes must set cashflows_ and frequency_
    }

    /** Convert a continuous spread to a conventional spread vs. {@code yts}. */
    private static double continuousToConv(final double oas, final Bond b, final Handle< YieldTermStructure > yts,
            final DayCounter dayCounter, final Compounding compounding, final Frequency frequency) {
        final YieldTermStructure ts = yts.currentLink();
        final double zz = ts.zeroRate(b.maturityDate(), dayCounter, Compounding.Continuous, Frequency.NoFrequency)
                .rate();
        final InterestRate baseRate = new InterestRate(zz, dayCounter, Compounding.Continuous, Frequency.NoFrequency);
        final InterestRate spreadedRate = new InterestRate(oas + zz, dayCounter, Compounding.Continuous,
                Frequency.NoFrequency);
        final double br = baseRate.equivalentRate(ts.referenceDate(), b.maturityDate(), dayCounter, compounding,
                frequency).rate();
        final double sr = spreadedRate.equivalentRate(ts.referenceDate(), b.maturityDate(), dayCounter, compounding,
                frequency).rate();
        return sr - br;
    }

    /** Convert a conventional spread vs. {@code yts} to a continuous spread. */
    private static double convToContinuous(final double oas, final Bond b, final Handle< YieldTermStructure > yts,
            final DayCounter dayCounter, final Compounding compounding, final Frequency frequency) {
        final YieldTermStructure ts = yts.currentLink();
        final double zz = ts.zeroRate(b.maturityDate(), dayCounter, compounding, frequency).rate();
        final InterestRate baseRate = new InterestRate(zz, dayCounter, compounding, frequency);
        final InterestRate spreadedRate = new InterestRate(oas + zz, dayCounter, compounding, frequency);
        final double br = baseRate.equivalentRate(ts.referenceDate(), b.maturityDate(), dayCounter,
                Compounding.Continuous, Frequency.NoFrequency).rate();
        final double sr = spreadedRate.equivalentRate(ts.referenceDate(), b.maturityDate(), dayCounter,
                Compounding.Continuous, Frequency.NoFrequency).rate();
        return sr - br;
    }

    /** return the bond's put/call schedule */
    public CallabilitySchedule callability() {
        return putCallSchedule_;
    }

    /**
     * Returns the Black implied forward yield volatility.
     * <p>
     * The forward yield volatility (see Hull, Fourth Edition, Chapter 20, pg 536) is relevant only to European put/call
     * schedules.
     */
    public double impliedVolatility(final Callability.Price targetPrice,
            final Handle< YieldTermStructure > discountCurve, final double accuracy, final int maxEvaluations,
            final double minVol, final double maxVol) {
        QL.require(!isExpired(), "instrument expired");

        final double dirtyTargetPrice;
        switch ( targetPrice.type() ) {
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
     * Mirrors C++ v1.42.1 callablebond.cpp::OAS — solves for the continuously-compounded spread that, when added to the
     * engine's short-rate, reproduces the dirty target price; the result is then converted to the requested
     * compounding/frequency convention.
     */
    public double OAS(final double cleanPrice, final Handle< YieldTermStructure > engineTS, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Date settlementDate, final double accuracy,
            final int maxIterations, final double guess) {
        final Date settle = (settlementDate == null || settlementDate.isNull()) ? settlementDate() : settlementDate;
        double dirtyPrice = cleanPrice + accruedAmount(settle);
        dirtyPrice /= 100.0 / notional(settle);

        final NPVSpreadHelper f = new NPVSpreadHelper(this);
        final OASHelper obj = new OASHelper(f, dirtyPrice);
        final Brent solver = new Brent();
        solver.setMaxEvaluations(maxIterations);
        final double step = 0.001;
        final double oas = solver.solve(obj, accuracy, guess, step);

        return continuousToConv(oas, this, engineTS, dayCounter, compounding, frequency);
    }

    /** Two-arg convenience overload: defaults match C++ defaults. */
    public double OAS(final double cleanPrice, final Handle< YieldTermStructure > engineTS, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency) {
        return OAS(cleanPrice, engineTS, dayCounter, compounding, frequency, new Date(), 1.0e-10, 100, 0.0);
    }

    /**
     * Calculate the clean price based on the given option-adjust-spread (oas) over the given yield term structure
     * (engineTS). Mirrors C++ v1.42.1 callablebond.cpp::cleanPriceOAS.
     */
    public double cleanPriceOAS(final double oas, final Handle< YieldTermStructure > engineTS,
            final DayCounter dayCounter, final Compounding compounding, final Frequency frequency,
            final Date settlementDate) {
        final Date settle = (settlementDate == null || settlementDate.isNull()) ? settlementDate() : settlementDate;
        final double oasCont = convToContinuous(oas, this, engineTS, dayCounter, compounding, frequency);
        final NPVSpreadHelper f = new NPVSpreadHelper(this);
        final double P = f.op(oasCont) * 100.0 / notional(settle) - accruedAmount(settle);
        return P;
    }

    /** Convenience overload: default settlement = bond's settlementDate(). */
    public double cleanPriceOAS(final double oas, final Handle< YieldTermStructure > engineTS,
            final DayCounter dayCounter, final Compounding compounding, final Frequency frequency) {
        return cleanPriceOAS(oas, engineTS, dayCounter, compounding, frequency, new Date());
    }

    // -----------------------------------------------------------------------
    // OAS helper plumbing — mirrors anonymous-namespace helpers in C++
    // callablebond.cpp (continuousToConv / convToContinuous / NPVSpreadHelper
    // / OASHelper).
    // -----------------------------------------------------------------------

    /**
     * Calculate the effective duration: first differential of dirty price w.r.t. a parallel shift of the yield term
     * structure divided by current dirty price.
     */
    public double effectiveDuration(final double oas, final Handle< YieldTermStructure > engineTS,
            final DayCounter dayCounter, final Compounding compounding, final Frequency frequency, final double bump) {
        final double P = cleanPriceOAS(oas, engineTS, dayCounter, compounding, frequency);
        final double Ppp = cleanPriceOAS(oas + bump, engineTS, dayCounter, compounding, frequency);
        final double Pmm = cleanPriceOAS(oas - bump, engineTS, dayCounter, compounding, frequency);
        if ( P == 0.0 ) {
            return 0.0;
        }
        return (Pmm - Ppp) / (2.0 * P * bump);
    }

    /**
     * Calculate the effective convexity: second differential of dirty price w.r.t. a parallel shift of the yield term
     * structure divided by current dirty price.
     */
    public double effectiveConvexity(final double oas, final Handle< YieldTermStructure > engineTS,
            final DayCounter dayCounter, final Compounding compounding, final Frequency frequency, final double bump) {
        final double P = cleanPriceOAS(oas, engineTS, dayCounter, compounding, frequency);
        final double Ppp = cleanPriceOAS(oas + bump, engineTS, dayCounter, compounding, frequency);
        final double Pmm = cleanPriceOAS(oas - bump, engineTS, dayCounter, compounding, frequency);
        if ( P == 0.0 ) {
            return 0.0;
        }
        return (Ppp + Pmm - 2.0 * P) / (P * bump * bump);
    }

    /**
     * Visibility-widened override of {@link Bond#setupArguments(PricingEngine.Arguments)} so cross-package helpers
     * (engines, ImpliedVolHelper) can call it directly, mirroring C++ {@code CallableBond::setupArguments} which is
     * public.
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

        arguments.couponDates = new ArrayList<>();
        arguments.couponAmounts = new ArrayList<>();

        // C++ skips cashflows.size()-1 (i.e. excludes the last) on the
        // assumption that the redemption is the last cash flow. JQuantLib's
        // EarlierThanCashFlowComparator is not a stable sort for equal-date
        // cash flows, so the last index may be a coupon (not the redemption);
        // identify the redemption by reference and skip it explicitly.
        for ( int i = 0; i < cfs.size(); i++ ) {
            final CashFlow cf = cfs.get(i);
            if ( cf == redemptionCf ) {
                continue;
            }
            // Mirrors C++ callablebond.cpp:426-430 — exclude cashflows that
            // either have already occurred OR are trading ex-coupon at the
            // settlement date. Phase 5e.5b-CFC-d-253 wires the ex-coupon
            // filter so that OAS continuity holds across the ex-coupon
            // window (see QL GitHub issue #2236).
            if ( !cf.hasOccurred(settlement, false) && !cf.tradingExCoupon(settlement) ) {
                arguments.couponDates.add(cf.date());
                arguments.couponAmounts.add(cf.amount());
            }
        }

        arguments.callabilityPrices = new ArrayList<>();
        arguments.callabilityDates = new ArrayList<>();

        arguments.paymentDayCounter = paymentDayCounter_;
        arguments.frequency = frequency_;

        arguments.putCallSchedule = putCallSchedule_;
        for ( final Callability c : putCallSchedule_ ) {
            if ( !c.hasOccurred(settlement, false) ) {
                arguments.callabilityDates.add(c.date());
                arguments.callabilityPrices.add(c.price().amount());

                if ( c.price().type() == Callability.Price.Type.Clean ) {
                    /* Convert clean call price to dirty using accrued interest
                       at the call date. We ignore ex-coupon conventions here
                       because the call is an issuer action governed by the
                       indenture: the holder receives the call price plus accrued
                       from the last payment date. Using market (ex-coupon)
                       accrued would create an inconsistency with the tree's
                       continuation value, which includes future coupons filtered
                       at the settlement date (see QL GitHub issue #2236). */
                    final Date callDate = c.date();
                    double callAccrued = 0.0;
                    for ( final CashFlow cf : cashflows_ ) {
                        if ( !cf.hasOccurred(callDate, false) ) {
                            if ( cf instanceof Coupon ) {
                                final Coupon coupon = (Coupon) cf;
                                double acc = coupon.accruedAmount(callDate);
                                // Mirrors C++ callablebond.cpp:463-464 — when
                                // the call date falls in the coupon's ex-coupon
                                // window, accruedAmount returns the *negative*
                                // ex-coupon accrual; add back the full coupon
                                // amount so the issuer call price stays
                                // consistent with the tree continuation value.
                                if ( coupon.tradingExCoupon(callDate) ) {
                                    acc = coupon.amount() + acc;
                                }
                                callAccrued = acc / notional(callDate) * 100.0;
                            }
                            break;
                        }
                    }
                    final int last = arguments.callabilityPrices.size() - 1;
                    arguments.callabilityPrices.set(last, arguments.callabilityPrices.get(last) + callAccrued);
                }
            }
        }

        arguments.spread = 0.0;
    }

    /** Marker interface for callable bond arguments. */
    public interface Arguments extends Bond.Arguments {
    }

    /** Marker interface for callable bond results. */
    public interface Results extends Bond.Results {
    }

    /** Re-prices the bond with the engine's {@code spread} argument set to {@code x}. */
    private static final class NPVSpreadHelper implements DoubleOp {
        private final CallableBond bond_;
        private final CallableBondResultsImpl results_;

        NPVSpreadHelper(final CallableBond bond) {
            this.bond_ = bond;
            bond.setupArguments(bond.engine.getArguments());
            this.results_ = (CallableBondResultsImpl) bond.engine.getResults();
        }

        @Override
        public double op(final double x) {
            final CallableBondArgumentsImpl args = (CallableBondArgumentsImpl) bond_.engine.getArguments();
            final double saved = args.spread;
            try {
                args.spread = x;
                bond_.engine.calculate();
                return results_.value;
            } finally {
                args.spread = saved;
            }
        }
    }

    //
    // inner classes (mirrors C++ CallableBond::arguments / results / engine)
    //

    /** Brent-objective: targetValue - NPV(spread). */
    private static final class OASHelper implements DoubleOp {
        private final NPVSpreadHelper npv_;
        private final double targetValue_;

        OASHelper(final NPVSpreadHelper npv, final double targetValue) {
            this.npv_ = npv;
            this.targetValue_ = targetValue;
        }

        @Override
        public double op(final double x) {
            return targetValue_ - npv_.op(x);
        }
    }

    /**
     * Helper class for Black implied volatility calculation. Mirrors the private helper inside C++
     * {@code callablebond.cpp}.
     */
    static class ImpliedVolHelper implements DoubleOp {
        private final PricingEngine engine_;
        private final double targetValue_;
        private final boolean matchNPV_;
        private final SimpleQuote vol_;
        private final CallableBondResultsImpl results_;

        ImpliedVolHelper(final CallableBond bond, final Handle< YieldTermStructure > discountCurve,
                final double targetValue, final boolean matchNPV) {
            this.targetValue_ = targetValue;
            this.matchNPV_ = matchNPV;
            this.vol_ = new SimpleQuote(0.0);
            this.engine_ = new BlackCallableFixedRateBondEngine(new Handle< Quote >(this.vol_), discountCurve);

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

    /** Concrete arguments implementation. */
    public static class CallableBondArgumentsImpl extends Bond.ArgumentsImpl implements CallableBond.Arguments {

        public List< Date > couponDates;
        public List< Double > couponAmounts;
        public double faceAmount;
        /** redemption = face amount * redemption / 100. */
        public double redemption;
        public Date redemptionDate;
        public DayCounter paymentDayCounter;
        public Frequency frequency;
        public CallabilitySchedule putCallSchedule;
        /** bond full/dirty/cash prices */
        public List< Double > callabilityPrices;
        public List< Date > callabilityDates;
        /**
         * Spread to apply to the valuation. This is a continuously compounded rate added to the model. Currently only
         * applied by the {@code TreeCallableFixedRateBondEngine} (when wired up).
         */
        public double spread;

        @Override
        public void validate() {
            QL.require(settlementDate != null && !settlementDate.isNull(), "null settlement date");
            QL.require(redemption != Constants.NULL_REAL, "null redemption");
            QL.require(redemption >= 0.0, "positive redemption required");
            QL.require(callabilityDates.size() == callabilityPrices.size(),
                    "different number of callability dates and prices");
            QL.require(couponDates.size() == couponAmounts.size(), "different number of coupon dates and amounts");
        }
    }

    /** Concrete results implementation. */
    public static class CallableBondResultsImpl extends Bond.ResultsImpl implements CallableBond.Results {
        // no extra results set yet
    }

    /** Base class for callable bond pricing engines. */
    public abstract static class CallableBondEngineImpl
            extends GenericEngine< CallableBond.Arguments, CallableBond.Results > implements Bond.Engine {

        protected CallableBondEngineImpl() {
            super(new CallableBondArgumentsImpl(), new CallableBondResultsImpl());
        }
    }
}
