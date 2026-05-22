/*
 Copyright (C) 2013, 2016 Peter Caspers

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
 Copyright (C) 2013, 2016 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

/*! \file nonstandardswap.hpp
    \brief vanilla swap but possibly with period dependent nominal and strike
*/

package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.cashflow.*;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Schedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Non-standard swap: a generalization of {@link VanillaSwap} where each coupon period may carry its own notional and
 * fixed rate.
 *
 * <p>Mirrors C++ v1.42.1 {@code ql/instruments/nonstandardswap.hpp} /
 * {@code .cpp} (author Peter Caspers, 2013/2016).
 *
 * <p>Phase 2j.5 Track A.1.
 */
public class NonstandardSwap extends Swap {

    // ── private fields (mirrors C++ member variables) ────────────────────────

    private static final double QL_EPSILON = 2.220446049250313e-16;
    private final VanillaSwap.Type type_;
    private final Schedule fixedSchedule_;
    private final DayCounter fixedDayCount_;
    private final Schedule floatingSchedule_;
    private final IborIndex iborIndex_;
    private final boolean singleSpreadAndGearing_;
    private final DayCounter floatingDayCount_;
    private final BusinessDayConvention paymentConvention_;
    private final boolean intermediateCapitalExchange_;
    private final boolean finalCapitalExchange_;
    // Mutable because intermediateCapitalExchange / finalCapitalExchange insert
    // redemption flows, changing the vector sizes.
    private double[] fixedNominal_;
    private double[] floatingNominal_;
    private double[] fixedRate_;
    private final double[] spread_;     // per floating coupon

    // ── Constructor from VanillaSwap ─────────────────────────────────────────
    private final double[] gearing_;    // per floating coupon

    // ── Constructor with scalar gearing and spread ───────────────────────────

    /**
     * Construct a NonstandardSwap from a plain VanillaSwap. Mirrors C++
     * {@code NonstandardSwap(const FixedVsFloatingSwap& fromVanilla)}.
     */
    public NonstandardSwap(final VanillaSwap fromVanilla) {
        super(2);
        this.type_ = fromVanilla.type();

        final int nFixed = fromVanilla.fixedLeg().size();
        final int nFloat = fromVanilla.floatingLeg().size();

        this.fixedNominal_ = new double[nFixed];
        Arrays.fill(this.fixedNominal_, fromVanilla.nominal());
        this.floatingNominal_ = new double[nFloat];
        Arrays.fill(this.floatingNominal_, fromVanilla.nominal());
        this.fixedSchedule_ = fromVanilla.fixedSchedule();
        this.fixedRate_ = new double[nFixed];
        Arrays.fill(this.fixedRate_, fromVanilla.fixedRate());
        this.fixedDayCount_ = fromVanilla.fixedDayCount();
        this.floatingSchedule_ = fromVanilla.floatingSchedule();
        this.iborIndex_ = fromVanilla.iborIndex();
        this.spread_ = new double[nFloat];
        Arrays.fill(this.spread_, fromVanilla.spread());
        this.gearing_ = new double[nFloat];
        Arrays.fill(this.gearing_, 1.0);
        this.singleSpreadAndGearing_ = true;
        this.floatingDayCount_ = fromVanilla.floatingDayCount();
        this.paymentConvention_ = fromVanilla.paymentConvention();
        this.intermediateCapitalExchange_ = false;
        this.finalCapitalExchange_ = false;
        init();
    }

    /**
     * Construct with per-coupon notionals and rates, but a single scalar gearing and spread applied uniformly to all
     * floating coupons. Mirrors C++ {@code NonstandardSwap(Swap::Type, ..., Real gearing, Spread spread, ...)}.
     */
    public NonstandardSwap(final VanillaSwap.Type type, final double[] fixedNominal, final double[] floatingNominal,
            final Schedule fixedSchedule, final double[] fixedRate, final DayCounter fixedDayCount,
            final Schedule floatingSchedule, final IborIndex iborIndex, final double gearing, final double spread,
            final DayCounter floatingDayCount, final boolean intermediateCapitalExchange,
            final boolean finalCapitalExchange) {
        this(type, fixedNominal, floatingNominal, fixedSchedule, fixedRate, fixedDayCount, floatingSchedule, iborIndex,
                gearing, spread, floatingDayCount, intermediateCapitalExchange, finalCapitalExchange,
                null /*paymentConvention — use floatingSchedule.businessDayConvention*/);
    }

    // ── Constructor with vector gearings and spreads ─────────────────────────

    /**
     * Overload with explicit paymentConvention (null means use floatingSchedule's).
     */
    public NonstandardSwap(final VanillaSwap.Type type, final double[] fixedNominal, final double[] floatingNominal,
            final Schedule fixedSchedule, final double[] fixedRate, final DayCounter fixedDayCount,
            final Schedule floatingSchedule, final IborIndex iborIndex, final double gearing, final double spread,
            final DayCounter floatingDayCount, final boolean intermediateCapitalExchange,
            final boolean finalCapitalExchange, final BusinessDayConvention paymentConvention) {
        super(2);
        this.type_ = type;
        this.fixedNominal_ = fixedNominal.clone();
        this.floatingNominal_ = floatingNominal.clone();
        this.fixedSchedule_ = fixedSchedule;
        this.fixedRate_ = fixedRate.clone();
        this.fixedDayCount_ = fixedDayCount;
        this.floatingSchedule_ = floatingSchedule;
        this.iborIndex_ = iborIndex;
        // expand scalar spread / gearing to per-coupon arrays
        this.spread_ = new double[floatingNominal.length];
        Arrays.fill(this.spread_, spread);
        this.gearing_ = new double[floatingNominal.length];
        Arrays.fill(this.gearing_, gearing);
        this.singleSpreadAndGearing_ = true;
        this.floatingDayCount_ = floatingDayCount;
        this.paymentConvention_ = (paymentConvention != null)
                ? paymentConvention
                : floatingSchedule.businessDayConvention();
        this.intermediateCapitalExchange_ = intermediateCapitalExchange;
        this.finalCapitalExchange_ = finalCapitalExchange;
        init();
    }

    /**
     * Construct with fully per-coupon gearings and spreads. Mirrors C++
     * {@code NonstandardSwap(Swap::Type, ..., vector<Real> gearing, vector<Spread> spread, ...)}.
     */
    public NonstandardSwap(final VanillaSwap.Type type, final double[] fixedNominal, final double[] floatingNominal,
            final Schedule fixedSchedule, final double[] fixedRate, final DayCounter fixedDayCount,
            final Schedule floatingSchedule, final IborIndex iborIndex, final double[] gearing, final double[] spread,
            final DayCounter floatingDayCount) {
        this(type, fixedNominal, floatingNominal, fixedSchedule, fixedRate, fixedDayCount, floatingSchedule, iborIndex,
                gearing, spread, floatingDayCount, false, false, null);
    }

    // ── Inspectors ──────────────────────────────────────────────────────────

    /**
     * Full vector overload with capital-exchange flags and explicit convention.
     */
    public NonstandardSwap(final VanillaSwap.Type type, final double[] fixedNominal, final double[] floatingNominal,
            final Schedule fixedSchedule, final double[] fixedRate, final DayCounter fixedDayCount,
            final Schedule floatingSchedule, final IborIndex iborIndex, final double[] gearing, final double[] spread,
            final DayCounter floatingDayCount, final boolean intermediateCapitalExchange,
            final boolean finalCapitalExchange, final BusinessDayConvention paymentConvention) {
        super(2);
        this.type_ = type;
        this.fixedNominal_ = fixedNominal.clone();
        this.floatingNominal_ = floatingNominal.clone();
        this.fixedSchedule_ = fixedSchedule;
        this.fixedRate_ = fixedRate.clone();
        this.fixedDayCount_ = fixedDayCount;
        this.floatingSchedule_ = floatingSchedule;
        this.iborIndex_ = iborIndex;
        this.spread_ = spread.clone();
        this.gearing_ = gearing.clone();
        this.singleSpreadAndGearing_ = false;
        this.floatingDayCount_ = floatingDayCount;
        this.paymentConvention_ = (paymentConvention != null)
                ? paymentConvention
                : floatingSchedule.businessDayConvention();
        this.intermediateCapitalExchange_ = intermediateCapitalExchange;
        this.finalCapitalExchange_ = finalCapitalExchange;
        init();
    }

    /**
     * Insert {@code value} at {@code index} in {@code arr}, returning a new array one element longer. Mirrors C++
     * {@code std::vector::insert}.
     */
    private static double[] insertDouble(final double[] arr, final int index, final double value) {
        final double[] result = new double[arr.length + 1];
        System.arraycopy(arr, 0, result, 0, index);
        result[index] = value;
        System.arraycopy(arr, index, result, index + 1, arr.length - index);
        return result;
    }

    /** Append {@code value} to the end of {@code arr}. */
    private static double[] appendDouble(final double[] arr, final double value) {
        final double[] result = new double[arr.length + 1];
        System.arraycopy(arr, 0, result, 0, arr.length);
        result[arr.length] = value;
        return result;
    }

    /**
     * Mirrors C++ {@code QuantLib::close(Real x, Real y)} with default n = 42 epsilon multiples.
     */
    private static boolean close(final double x, final double y) {
        final double diff = Math.abs(x - y);
        if ( diff == 0.0 )
            return true;
        final double eps = QL_EPSILON;
        final double relTol = 42.0 * eps;
        return diff <= relTol * Math.abs(x) || diff <= relTol * Math.abs(y);
    }

    /** Mirrors C++ {@code NonstandardSwap::type()}. */
    public VanillaSwap.Type type() {
        return type_;
    }

    /** Mirrors C++ {@code NonstandardSwap::fixedNominal()}. */
    public double[] fixedNominal() {
        return fixedNominal_;
    }

    /** Mirrors C++ {@code NonstandardSwap::floatingNominal()}. */
    public double[] floatingNominal() {
        return floatingNominal_;
    }

    /** Mirrors C++ {@code NonstandardSwap::fixedSchedule()}. */
    public Schedule fixedSchedule() {
        return fixedSchedule_;
    }

    /** Mirrors C++ {@code NonstandardSwap::fixedRate()}. */
    public double[] fixedRate() {
        return fixedRate_;
    }

    /** Mirrors C++ {@code NonstandardSwap::fixedDayCount()}. */
    public DayCounter fixedDayCount() {
        return fixedDayCount_;
    }

    /** Mirrors C++ {@code NonstandardSwap::floatingSchedule()}. */
    public Schedule floatingSchedule() {
        return floatingSchedule_;
    }

    /** Mirrors C++ {@code NonstandardSwap::iborIndex()}. */
    public IborIndex iborIndex() {
        return iborIndex_;
    }

    /**
     * Scalar spread accessor (valid only when constructed with scalar spread). Mirrors C++
     * {@code NonstandardSwap::spread()}.
     */
    public double spread() {
        QL.require(singleSpreadAndGearing_, "spread is a vector, use spreads() inspector instead");
        return spread_[0];
    }

    /**
     * Scalar gearing accessor (valid only when constructed with scalar gearing). Mirrors C++
     * {@code NonstandardSwap::gearing()}.
     */
    public double gearing() {
        QL.require(singleSpreadAndGearing_, "gearing is a vector, use gearings() inspector instead");
        return gearing_[0];
    }

    /** Per-coupon spreads. Mirrors C++ {@code NonstandardSwap::spreads()}. */
    public double[] spreads() {
        return spread_;
    }

    /** Per-coupon gearings. Mirrors C++ {@code NonstandardSwap::gearings()}. */
    public double[] gearings() {
        return gearing_;
    }

    // ── Pricing engine plumbing ──────────────────────────────────────────────

    /** Mirrors C++ {@code NonstandardSwap::floatingDayCount()}. */
    public DayCounter floatingDayCount() {
        return floatingDayCount_;
    }

    /** Mirrors C++ {@code NonstandardSwap::paymentConvention()}. */
    public BusinessDayConvention paymentConvention() {
        return paymentConvention_;
    }

    /** Fixed leg (legs_[0]). Mirrors C++ {@code NonstandardSwap::fixedLeg()}. */
    public Leg fixedLeg() {
        return legs.get(0);
    }

    // ── Private initialisation ───────────────────────────────────────────────

    /** Floating leg (legs_[1]). Mirrors C++ {@code NonstandardSwap::floatingLeg()}. */
    public Leg floatingLeg() {
        return legs.get(1);
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
    }

    // ── Array helpers ─────────────────────────────────────────────────────────

    /**
     * Mirrors C++ {@code NonstandardSwap::setupArguments()}. Delegates Swap-level fields first, then populates
     * NonstandardSwap-specific fields when the engine implements {@link NonstandardSwap.Arguments}.
     */
    @Override
    public void setupArguments(final PricingEngine.Arguments args) {
        super.setupArguments(args);

        if ( !(args instanceof NonstandardSwap.Arguments) ) {
            return; // plain Swap engine — just the legs/payer arrays are needed
        }
        final NonstandardSwap.ArgumentsImpl arguments = (NonstandardSwap.ArgumentsImpl) args;

        arguments.type = type_;
        arguments.fixedNominal = fixedNominal_.clone();
        arguments.floatingNominal = floatingNominal_.clone();
        arguments.fixedRate = fixedRate_.clone();

        // ── Fixed leg ───────────────────────────────────────────────────────
        final Leg fixedCoupons = fixedLeg();
        final int nFixed = fixedCoupons.size();
        arguments.fixedResetDates = new ArrayList<>(Collections.nCopies(nFixed, null));
        arguments.fixedPayDates = new ArrayList<>(Collections.nCopies(nFixed, null));
        arguments.fixedCoupons = new ArrayList<>(Collections.nCopies(nFixed, null));
        arguments.fixedIsRedemptionFlow = new boolean[nFixed];  // default false

        for ( int i = 0; i < nFixed; i++ ) {
            final CashFlow cf = fixedCoupons.get(i);
            if (cf instanceof FixedRateCoupon coupon) {
                arguments.fixedPayDates.set(i, coupon.date());
                arguments.fixedResetDates.set(i, coupon.accrualStartDate());
                arguments.fixedCoupons.set(i, coupon.amount());
            } else {
                // Redemption / SimpleCashFlow — find the matching coupon pay date
                // and copy reset date from it (mirrors C++ logic).
                final Date cfDate = cf.date();
                int jIdx = -1;
                for ( int j = 0; j < i; j++ ) {
                    if ( cfDate.equals(arguments.fixedPayDates.get(j)) ) {
                        jIdx = j;
                        break;
                    }
                }
                QL.require(jIdx >= 0, "nominal redemption on " + cfDate + " has no corresponding coupon");
                arguments.fixedIsRedemptionFlow[i] = true;
                arguments.fixedCoupons.set(i, cf.amount());
                arguments.fixedResetDates.set(i, arguments.fixedResetDates.get(jIdx));
                arguments.fixedPayDates.set(i, cfDate);
            }
        }

        // ── Floating leg ─────────────────────────────────────────────────────
        final Leg floatingCoupons = floatingLeg();
        final int nFloat = floatingCoupons.size();
        arguments.floatingResetDates = new ArrayList<>(Collections.nCopies(nFloat, null));
        arguments.floatingPayDates = new ArrayList<>(Collections.nCopies(nFloat, null));
        arguments.floatingFixingDates = new ArrayList<>(Collections.nCopies(nFloat, null));
        arguments.floatingAccrualTimes = new ArrayList<>(Collections.nCopies(nFloat, null));
        arguments.floatingSpreads = new ArrayList<>(Collections.nCopies(nFloat, null));
        arguments.floatingGearings = new ArrayList<>(Collections.nCopies(nFloat, null));
        arguments.floatingCoupons = new ArrayList<>(Collections.nCopies(nFloat, null));
        arguments.floatingIsRedemptionFlow = new boolean[nFloat];  // default false

        for ( int i = 0; i < nFloat; i++ ) {
            final CashFlow cf = floatingCoupons.get(i);
            if (cf instanceof IborCoupon coupon) {
                arguments.floatingResetDates.set(i, coupon.accrualStartDate());
                arguments.floatingPayDates.set(i, coupon.date());
                arguments.floatingFixingDates.set(i, coupon.fixingDate());
                arguments.floatingAccrualTimes.set(i, coupon.accrualPeriod());
                arguments.floatingSpreads.set(i, coupon.spread());
                arguments.floatingGearings.set(i, coupon.gearing());
                try {
                    arguments.floatingCoupons.set(i, coupon.amount());
                } catch ( final Exception e ) {
                    arguments.floatingCoupons.set(i, Constants.NULL_REAL);
                }
            } else {
                // Redemption flow — find matching coupon pay date
                final Date cfDate = cf.date();
                int jIdx = -1;
                for ( int j = 0; j < i; j++ ) {
                    if ( cfDate.equals(arguments.floatingPayDates.get(j)) ) {
                        jIdx = j;
                        break;
                    }
                }
                QL.require(jIdx >= 0, "nominal redemption on " + cfDate + " has no corresponding coupon");
                arguments.floatingIsRedemptionFlow[i] = true;
                arguments.floatingCoupons.set(i, cf.amount());
                arguments.floatingResetDates.set(i, arguments.floatingResetDates.get(jIdx));
                arguments.floatingFixingDates.set(i, arguments.floatingFixingDates.get(jIdx));
                arguments.floatingAccrualTimes.set(i, 0.0);
                arguments.floatingSpreads.set(i, 0.0);
                arguments.floatingGearings.set(i, 1.0);
                arguments.floatingPayDates.set(i, cfDate);
            }
        }

        arguments.iborIndex = iborIndex();
    }

    /** Mirrors C++ {@code NonstandardSwap::fetchResults()}. */
    @Override
    public void fetchResults(final PricingEngine.Results results) {
        super.fetchResults(results);
    }

    /**
     * Builds both legs, applies capital-exchange insertions, registers floating-leg cashflows as observers, and sets
     * payer signs. Mirrors C++ {@code NonstandardSwap::init()}.
     */
    private void init() {
        QL.require(fixedNominal_.length == fixedRate_.length,
                "Fixed nominal size (" + fixedNominal_.length + ") does not match fixed rate size (" + fixedRate_.length
                        + ")");
        QL.require(fixedNominal_.length == fixedSchedule_.size() - 1,
                "Fixed nominal size (" + fixedNominal_.length + ") does not match schedule size ("
                        + fixedSchedule_.size() + ") - 1");
        QL.require(floatingNominal_.length == floatingSchedule_.size() - 1,
                "Floating nominal size (" + floatingNominal_.length + ") does not match schedule size ("
                        + floatingSchedule_.size() + ") - 1");
        QL.require(floatingNominal_.length == spread_.length,
                "Floating nominal size (" + floatingNominal_.length + ") does not match spread size (" + spread_.length
                        + ")");
        QL.require(floatingNominal_.length == gearing_.length,
                "Floating nominal size (" + floatingNominal_.length + ") does not match gearing size ("
                        + gearing_.length + ")");

        // C++ dirty trick: if gearing is zero, replace with QL_EPSILON so that
        // IborLeg does not produce fixed coupons, which confuse the engines.
        for ( int i = 0; i < gearing_.length; i++ ) {
            if ( Math.abs(gearing_[i]) < QL_EPSILON ) {
                gearing_[i] = QL_EPSILON;
            }
        }

        // Build the fixed leg
        final Leg fixedLeg = new FixedRateLeg(fixedSchedule_, fixedDayCount_).withNotionals(fixedNominal_)
                .withCouponRates(fixedRate_).withPaymentAdjustment(paymentConvention_).Leg();

        // Build the floating leg
        final Leg floatingLeg = new IborLeg(floatingSchedule_, iborIndex_).withNotionals(new Array(floatingNominal_))
                .withPaymentDayCounter(floatingDayCount_).withPaymentAdjustment(paymentConvention_)
                .withSpreads(new Array(spread_)).withGearings(new Array(gearing_)).Leg();

        // ── intermediateCapitalExchange ───────────────────────────────────────
        if ( intermediateCapitalExchange_ ) {
            // Fixed leg redemptions
            int i = 0;
            while ( i < fixedLeg.size() - 1 ) {
                final double cap = fixedNominal_[i] - fixedNominal_[i + 1];
                if ( !close(cap, 0.0) ) {
                    // Insert redemption after position i (at position i+1)
                    final Date payDate = fixedLeg.get(i).date();
                    fixedLeg.add(i + 1, new SimpleCashFlow(cap, payDate));
                    // Expand fixedNominal_ and fixedRate_
                    fixedNominal_ = insertDouble(fixedNominal_, i + 1, fixedNominal_[i]);
                    fixedRate_ = insertDouble(fixedRate_, i + 1, 0.0);
                    i += 2; // skip the inserted element
                } else {
                    i++;
                }
            }
            // Floating leg redemptions
            int j = 0;
            while ( j < floatingLeg.size() - 1 ) {
                final double cap = floatingNominal_[j] - floatingNominal_[j + 1];
                if ( !close(cap, 0.0) ) {
                    final Date payDate = floatingLeg.get(j).date();
                    floatingLeg.add(j + 1, new SimpleCashFlow(cap, payDate));
                    floatingNominal_ = insertDouble(floatingNominal_, j + 1, floatingNominal_[j]);
                    j += 2;
                } else {
                    j++;
                }
            }
        }

        // ── finalCapitalExchange ───────────────────────────────────────────────
        if ( finalCapitalExchange_ ) {
            // Fixed leg
            final double fixedFinalNom = fixedNominal_[fixedNominal_.length - 1];
            final Date fixedFinalDate = fixedLeg.get(fixedLeg.size() - 1).date();
            fixedLeg.add(new SimpleCashFlow(fixedFinalNom, fixedFinalDate));
            fixedNominal_ = appendDouble(fixedNominal_, fixedFinalNom);
            fixedRate_ = appendDouble(fixedRate_, 0.0);
            // Floating leg
            final double floatFinalNom = floatingNominal_[floatingNominal_.length - 1];
            final Date floatFinalDate = floatingLeg.get(floatingLeg.size() - 1).date();
            floatingLeg.add(new SimpleCashFlow(floatFinalNom, floatFinalDate));
            floatingNominal_ = appendDouble(floatingNominal_, floatFinalNom);
        }

        // Register floating leg cashflows as observers (for re-pricing).
        for ( final CashFlow cf : floatingLeg ) {
            cf.addObserver(this);
        }

        // Add legs to the Swap parent (Swap(int) leaves legs empty).
        super.legs.add(fixedLeg);
        super.legs.add(floatingLeg);

        // Set payer signs: Payer pays fixed (−1) receives floating (+1).
        if ( type_ == VanillaSwap.Type.Payer ) {
            super.payer[0] = -1.0;
            super.payer[1] = +1.0;
        } else {
            super.payer[0] = +1.0;
            super.payer[1] = -1.0;
        }
    }

    // ── Inner interfaces and classes ──────────────────────────────────────────

    /**
     * Marking interface for NonstandardSwap engine arguments. Mirrors C++ {@code NonstandardSwap::arguments}.
     */
    public interface Arguments extends Swap.Arguments { /* marker */
    }

    /**
     * Marking interface for NonstandardSwap engine results. Mirrors C++ {@code NonstandardSwap::results}.
     */
    public interface Results extends Swap.Results { /* marker */
    }

    /**
     * Concrete arguments implementation. Mirrors C++ {@code NonstandardSwap::arguments}.
     */
    public static class ArgumentsImpl extends Swap.ArgumentsImpl implements NonstandardSwap.Arguments {

        public VanillaSwap.Type type = VanillaSwap.Type.Receiver;
        public double[] fixedNominal;
        public double[] floatingNominal;

        public List< Date > fixedResetDates;
        public List< Date > fixedPayDates;
        public List< Double > floatingAccrualTimes;
        public List< Date > floatingResetDates;
        public List< Date > floatingFixingDates;
        public List< Date > floatingPayDates;

        public List< Double > fixedCoupons;
        public double[] fixedRate;
        public List< Double > floatingSpreads;
        public List< Double > floatingGearings;
        public List< Double > floatingCoupons;

        public IborIndex iborIndex;

        public boolean[] fixedIsRedemptionFlow;
        public boolean[] floatingIsRedemptionFlow;

        @Override
        public void validate() {
            super.validate();
            QL.require(fixedNominal.length == fixedPayDates.size(),
                    "number of fixed leg nominals plus redemption flows " + "different from number of payment dates");
            QL.require(fixedRate.length == fixedPayDates.size(),
                    "number of fixed rates plus redemption flows different from " + "number of payment dates");
            QL.require(floatingNominal.length == floatingPayDates.size(),
                    "number of float leg nominals different from number of payment dates");
            QL.require(fixedResetDates.size() == fixedPayDates.size(),
                    "number of fixed start dates different from number of fixed payment dates");
            QL.require(fixedPayDates.size() == fixedCoupons.size(),
                    "number of fixed payment dates different from number of fixed coupon amounts");
            QL.require(floatingResetDates.size() == floatingPayDates.size(),
                    "number of floating start dates different from " + "number of floating payment dates");
            QL.require(floatingFixingDates.size() == floatingPayDates.size(),
                    "number of floating fixing dates different from " + "number of floating payment dates");
            QL.require(floatingAccrualTimes.size() == floatingPayDates.size(),
                    "number of floating accrual Times different from " + "number of floating payment dates");
            QL.require(floatingSpreads.size() == floatingPayDates.size(),
                    "number of floating spreads different from " + "number of floating payment dates");
            QL.require(floatingPayDates.size() == floatingCoupons.size(),
                    "number of floating payment dates different from " + "number of floating coupon amounts");
        }
    }

    /**
     * Concrete results implementation. Mirrors C++ {@code NonstandardSwap::results}.
     */
    public static class ResultsImpl extends Swap.ResultsImpl implements NonstandardSwap.Results {

        @Override
        public void reset() {
            super.reset();
        }
    }

    /**
     * Abstract engine for NonstandardSwap. Mirrors C++ {@code NonstandardSwap::engine}.
     */
    public abstract static class EngineImpl
            extends GenericEngine< NonstandardSwap.Arguments, NonstandardSwap.Results > {

        protected EngineImpl() {
            super(new ArgumentsImpl(), new ResultsImpl());
        }
    }
}
