/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2007, 2008, 2009, 2010 Ferdinando Ametrano
 Copyright (C) 2007 Chiara Fornarola
 Copyright (C) 2009 StatPro Italia srl
 Copyright (C) 2009 Nathan Abbott

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/
package org.jquantlib.pricingengines.bond;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.CashFlows.Duration;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Bond;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;

/**
 * Bond adapters of {@link CashFlows} functions. Faithful Java port of
 * {@code ql/pricingengines/bond/bondfunctions.{hpp,cpp}} v1.42.1 (sha
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>These adapters call into {@link CashFlows} passing as input the bond
 * cashflows, the dirty price (i.e. NPV) calculated from the clean price, the bond settlement date (unless another date
 * is given), zero ex-dividend days, and excluding any cashflow on the settlement date
 * ({@code includeSettlementDateFlows = false}).
 *
 * <p>Prices are always clean, as per market convention.
 *
 * <p>The C++ {@code BondFunctions} struct is rendered as a Java class with
 * a private constructor and {@code static} methods. Phase 5e.5b-CFC-d-12.
 *
 * <p>Phase 5e.5b-CFC-d-35: the IRR-discounted stepwise helpers
 * (previously private to this class) were promoted to public statics on {@link CashFlows} so callers can share the
 * C++-faithful semantics for non-additive day counters (Thirty/360 USA on the 31st, etc.). This class now delegates
 * {@code dirtyPrice}, {@code yield}, {@code duration}, {@code convexity}, {@code basisPointValue}, and
 * {@code yieldValueBasisPoint} to {@link CashFlows#npv(Leg, InterestRate, boolean, Date, Date)},
 * {@link CashFlows#duration(Leg, InterestRate, Duration, boolean, Date, Date)}, and
 * {@link CashFlows#convexity(Leg, InterestRate, boolean, Date, Date)}.
 */
public final class BondFunctions {

    private BondFunctions() {
        // utility class
    }

    public static Date startDate(final Bond bond) {
        return CashFlows.getInstance().startDate(bond.cashflows());
    }

    //
    // Date inspectors
    //

    public static Date maturityDate(final Bond bond) {
        return CashFlows.getInstance().maturityDate(bond.cashflows());
    }

    public static boolean isTradable(final Bond bond) {
        return isTradable(bond, new Date());
    }

    public static boolean isTradable(final Bond bond, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        return bond.notional(settlement) != 0.0;
    }

    public static double previousCouponRate(final Bond bond) {
        return previousCouponRate(bond, new Date());
    }

    //
    // Coupon inspectors
    //

    public static double previousCouponRate(final Bond bond, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        return CashFlows.getInstance().previousCouponRate(bond.cashflows(), settlement);
    }

    public static double nextCouponRate(final Bond bond) {
        return nextCouponRate(bond, new Date());
    }

    public static double nextCouponRate(final Bond bond, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        return CashFlows.getInstance().nextCouponRate(bond.cashflows(), settlement);
    }

    public static double accruedAmount(final Bond bond) {
        return accruedAmount(bond, new Date());
    }

    /**
     * Mirrors C++ {@code BondFunctions::accruedAmount} (bondfunctions.cpp:224). Returns 0 if the bond is not tradable;
     * otherwise scales the underlying leg accrued amount to the bond notional. Delegates the per-coupon accrual
     * aggregation to {@link Bond#accruedAmount(Date)} (which mirrors the C++ aggregation logic in
     * {@code CashFlows::accruedAmount}).
     */
    public static double accruedAmount(final Bond bond, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        if ( !isTradable(bond, settlement) ) {
            return 0.0;
        }
        // Bond.accruedAmount already scales by 100/notional; matches the C++
        // semantics of CashFlows::accruedAmount(...) * 100 / bond.notional().
        return bond.accruedAmount(settlement);
    }

    public static double cleanPrice(final Bond bond, final YieldTermStructure discountCurve) {
        return cleanPrice(bond, discountCurve, new Date());
    }

    //
    // YieldTermStructure functions
    //

    public static double cleanPrice(final Bond bond, final YieldTermStructure discountCurve, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        return dirtyPrice(bond, discountCurve, settlement) - bond.accruedAmount(settlement);
    }

    public static double dirtyPrice(final Bond bond, final YieldTermStructure discountCurve) {
        return dirtyPrice(bond, discountCurve, new Date());
    }

    public static double dirtyPrice(final Bond bond, final YieldTermStructure discountCurve, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " settlement date (maturity being " + bond.maturityDate() + ")");

        final double npv = CashFlows.npv(bond.cashflows(), discountCurve, false, settlement, null);
        return npv * 100.0 / bond.notional(settlement);
    }

    public static double bps(final Bond bond, final YieldTermStructure discountCurve) {
        return bps(bond, discountCurve, new Date());
    }

    public static double bps(final Bond bond, final YieldTermStructure discountCurve, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        final Handle< YieldTermStructure > handle = new Handle< YieldTermStructure >(discountCurve);
        return CashFlows.getInstance().bps(bond.cashflows(), handle, settlement) * 100.0 / bond.notional(settlement);
    }

    public static double atmRate(final Bond bond, final YieldTermStructure discountCurve) {
        return atmRate(bond, discountCurve, new Date(), new Price());
    }

    public static double atmRate(final Bond bond, final YieldTermStructure discountCurve, final Date settlement) {
        return atmRate(bond, discountCurve, settlement, new Price());
    }

    /**
     * Mirrors C++ {@code BondFunctions::atmRate(bond, discountCurve, settlement, price)} (bondfunctions.cpp:280-304).
     *
     * <p>If {@code price} is invalid (default-constructed, no amount), the
     * underlying {@link CashFlows#atmRate} computes its own NPV from the leg. Otherwise the dirty NPV is derived from
     * the supplied price (with accrued added if the price is clean), scaled by the bond notional.
     */
    public static double atmRate(final Bond bond, final YieldTermStructure discountCurve, Date settlement,
            final Price price) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        double npv = 0.0;
        if ( price != null && price.isValid() ) {
            double dirty = price.amount();
            if ( price.type() == Price.Type.Clean ) {
                dirty += bond.accruedAmount(settlement);
            }
            final double currentNotional = bond.notional(settlement);
            npv = dirty / 100.0 * currentNotional;
        }
        final Handle< YieldTermStructure > handle = new Handle< YieldTermStructure >(discountCurve);
        return CashFlows.getInstance().atmRate(bond.cashflows(), handle, settlement, settlement, 0, npv);
    }

    public static double cleanPrice(final Bond bond, final InterestRate yield) {
        return cleanPrice(bond, yield, new Date());
    }

    //
    // Yield (a.k.a. Internal Rate of Return, IRR) functions
    //

    public static double cleanPrice(final Bond bond, final InterestRate yield, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        return dirtyPrice(bond, yield, settlement) - bond.accruedAmount(settlement);
    }

    public static double cleanPrice(final Bond bond, final double yield, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency) {
        return cleanPrice(bond, yield, dayCounter, compounding, frequency, new Date());
    }

    public static double cleanPrice(final Bond bond, final double yield, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Date settlement) {
        return cleanPrice(bond, new InterestRate(yield, dayCounter, compounding, frequency), settlement);
    }

    public static double dirtyPrice(final Bond bond, final InterestRate yield) {
        return dirtyPrice(bond, yield, new Date());
    }

    public static double dirtyPrice(final Bond bond, final InterestRate yield, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        // Delegate to the C++-faithful stepwise NPV
        // (CashFlows.npv(leg, InterestRate, includeSettlementDateFlows,
        // settlementDate, npvDate)). The legacy
        // CashFlows.npv(leg, irr, settlement) variant accumulates
        // yearFractions from settlement directly, which differs from the
        // C++ stepwise accumulation for non-additive day counters such as
        // Thirty/360 USA.
        return CashFlows.npv(bond.cashflows(), yield, false, settlement, settlement) * 100.0 / bond.notional(
                settlement);
    }

    public static double dirtyPrice(final Bond bond, final double yield, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Date settlement) {
        return dirtyPrice(bond, new InterestRate(yield, dayCounter, compounding, frequency), settlement);
    }

    public static double bps(final Bond bond, final InterestRate yield) {
        return bps(bond, yield, new Date());
    }

    public static double bps(final Bond bond, final InterestRate yield, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        return CashFlows.getInstance().bps(bond.cashflows(), yield, settlement) * 100.0 / bond.notional(settlement);
    }

    public static double bps(final Bond bond, final double yield, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Date settlement) {
        return bps(bond, new InterestRate(yield, dayCounter, compounding, frequency), settlement);
    }

    /**
     * Yield from a (clean or dirty) price. Mirrors C++
     * {@code BondFunctions::yield(bond, price, dayCounter, compounding, frequency, settlement, accuracy, maxIterations,
     * guess)} (bondfunctions.cpp:373-387).
     *
     * <p>The C++ implementation defaults to a {@code NewtonSafe} solver;
     * the Java {@link CashFlows#irr} uses a Brent solver. For test consistency the absolute root accuracy and the
     * converged yield agree within the C++ tolerances at every cross-validated test below {@code 1e-4}.
     */
    public static double yield(final Bond bond, final Price price, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency) {
        return BondFunctions.yield(bond, price, dayCounter, compounding, frequency, new Date(), 1.0e-10, 100, 0.05);
    }

    public static double yield(final Bond bond, final Price price, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Date settlement) {
        return BondFunctions.yield(bond, price, dayCounter, compounding, frequency, settlement, 1.0e-10, 100, 0.05);
    }

    public static double yield(final Bond bond, final Price price, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, Date settlement, final double accuracy,
            final int maxIterations, final double guess) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        // Mirror C++ bondfunctions.hpp:179-189 — convert clean→dirty and
        // un-scale by 100/notional, then solve for IRR using stepwise NPV
        // (matches C++ NewtonSafe-via-CashFlows::yield in the IRR root,
        // independent of the underlying solver — both converge to the
        // unique zero of (target - npv(y)) within the supplied accuracy).
        double amount = price.amount();
        if ( price.type() == Price.Type.Clean ) {
            amount += bond.accruedAmount(settlement);
        }
        amount /= 100.0 / bond.notional(settlement);

        final double targetNpv = amount;
        final Date settle = settlement;
        final Leg leg = bond.cashflows();
        final Ops.DoubleOp irrFinder = new Ops.DoubleOp() {
            @Override
            public double op(final double y) {
                return targetNpv - CashFlows.npv(leg, new InterestRate(y, dayCounter, compounding, frequency), false,
                        settle, settle);
            }
        };

        final Brent solver = new Brent();
        solver.setMaxEvaluations(maxIterations);
        return solver.solve(irrFinder, accuracy, guess, guess / 10.0);
    }

    public static double duration(final Bond bond, final InterestRate yield) {
        return duration(bond, yield, Duration.Modified, new Date());
    }

    public static double duration(final Bond bond, final InterestRate yield, final Duration type) {
        return duration(bond, yield, type, new Date());
    }

    public static double duration(final Bond bond, final InterestRate yield, final Duration type, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        return CashFlows.duration(bond.cashflows(), yield, type, false, settlement, settlement);
    }

    public static double duration(final Bond bond, final double yield, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Duration type, final Date settlement) {
        return duration(bond, new InterestRate(yield, dayCounter, compounding, frequency), type, settlement);
    }

    public static double convexity(final Bond bond, final InterestRate yield) {
        return convexity(bond, yield, new Date());
    }

    public static double convexity(final Bond bond, final InterestRate yield, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        return CashFlows.convexity(bond.cashflows(), yield, false, settlement, settlement);
    }

    public static double convexity(final Bond bond, final double yield, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Date settlement) {
        return convexity(bond, new InterestRate(yield, dayCounter, compounding, frequency), settlement);
    }

    public static double basisPointValue(final Bond bond, final InterestRate yield) {
        return basisPointValue(bond, yield, new Date());
    }

    /**
     * Basis-point value (DV01). Mirrors C++ {@code BondFunctions::basisPointValue(bond, yield, settlement)}
     * (bondfunctions.cpp:440). Re-implements the underlying {@code CashFlows::basisPointValue} formula here because the
     * Java {@link CashFlows} class declares its private {@code basisPointValue} helper {@code private}
     * (cashflows.cpp:404 derivation: {@code dy = 1bp; dP = -modDur * P * dy + 0.5 * conv/100 * P * dy^2}).
     */
    public static double basisPointValue(final Bond bond, final InterestRate yield, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        final double shift = 1.0e-4;
        final double dirty = CashFlows.npv(bond.cashflows(), yield, false, settlement, settlement);
        final double modDur = CashFlows.duration(bond.cashflows(), yield, Duration.Modified, false, settlement,
                settlement);
        final double conv = CashFlows.convexity(bond.cashflows(), yield, false, settlement, settlement);

        double delta = -modDur * dirty;
        double gamma = (conv / 100.0) * dirty;
        delta *= shift;
        gamma *= shift * shift;
        return delta + 0.5 * gamma;
    }

    public static double basisPointValue(final Bond bond, final double yield, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Date settlement) {
        return basisPointValue(bond, new InterestRate(yield, dayCounter, compounding, frequency), settlement);
    }

    public static double yieldValueBasisPoint(final Bond bond, final InterestRate yield) {
        return yieldValueBasisPoint(bond, yield, new Date());
    }

    public static double yieldValueBasisPoint(final Bond bond, final InterestRate yield, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        // Mirror C++ CashFlows::yieldValueBasisPoint = shift / (-npv * modDur)
        // (cashflows.cpp:1104-1128). Implement with stepwise NPV/duration.
        final double npv = CashFlows.npv(bond.cashflows(), yield, false, settlement, settlement);
        final double modDur = CashFlows.duration(bond.cashflows(), yield, Duration.Modified, false, settlement,
                settlement);
        final double shift = 0.01;
        return (1.0 / (-npv * modDur)) * shift;
    }

    public static double yieldValueBasisPoint(final Bond bond, final double yield, final DayCounter dayCounter,
            final Compounding compounding, final Frequency frequency, final Date settlement) {
        return yieldValueBasisPoint(bond, new InterestRate(yield, dayCounter, compounding, frequency), settlement);
    }

    /**
     * Clean price from a Z-spread. Mirrors C++
     * {@code BondFunctions::cleanPrice(bond, discount, zSpread, comp, freq, settlement)} (bondfunctions.cpp:488).
     */
    public static double cleanPrice(final Bond bond, final YieldTermStructure discount, final double zSpread,
            final Compounding compounding, final Frequency frequency, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        return dirtyPrice(bond, discount, zSpread, compounding, frequency, settlement) - bond.accruedAmount(settlement);
    }

    //
    // Z-spread functions
    //

    /**
     * Dirty price from a Z-spread. Mirrors C++
     * {@code BondFunctions::dirtyPrice(bond, discount, zSpread, comp, freq, settlement)} (bondfunctions.cpp:510-528).
     * Delegates to {@link Bond#dirtyPriceFromZSpread} for parity with the existing Java machinery (which already wraps
     * the {@link DiscountingBondEngine}-driven z-spread NPV).
     */
    public static double dirtyPrice(final Bond bond, final YieldTermStructure discount, final double zSpread,
            final Compounding compounding, final Frequency frequency, Date settlement) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        // Use the existing Bond Z-spread plumbing, which mirrors C++
        // dirtyPriceFromZSpreadFunction and divides by notional already.
        return bond.dirtyPriceFromZSpread(zSpread, discount.dayCounter(), compounding, frequency, settlement);
    }

    /**
     * Implied Z-spread from a bond price. Mirrors C++
     * {@code BondFunctions::zSpread(bond, price, discount, comp, freq, settlement, accuracy, maxIterations, guess)}
     * (bondfunctions.cpp:540-568): converts the supplied {@code price} to the dirty NPV on the bond's cashflows
     * ({@code price.amount() + accrued} if clean, then re-scaled by {@code notional/100}), and delegates the
     * root-finding to {@link CashFlows#zSpread}.
     *
     * <p>Phase 5e.5b-CFC-d-98.
     */
    public static double zSpread(final Bond bond, final Price price, final YieldTermStructure discount,
            final Compounding compounding, final Frequency frequency, Date settlement, final double accuracy,
            final int maxIterations, final double guess) {
        if ( settlement == null || settlement.isNull() ) {
            settlement = bond.settlementDate();
        }
        QL.require(isTradable(bond, settlement),
                "non tradable at " + settlement + " (maturity being " + bond.maturityDate() + ")");

        double dirty = price.amount();
        if ( price.type() == Price.Type.Clean ) {
            dirty += bond.accruedAmount(settlement);
        }
        // C++: dirty /= 100.0 / bond.notional(settlement);
        dirty /= 100.0 / bond.notional(settlement);

        return CashFlows.zSpread(bond.cashflows(), dirty, discount, compounding, frequency, false, settlement,
                settlement, accuracy, maxIterations, guess);
    }

    /**
     * Convenience overload of
     * {@link #zSpread(Bond, Price, YieldTermStructure, Compounding, Frequency, Date, double, int, double)} with the C++
     * canonical defaults {@code (accuracy=1e-10, maxIterations=100, guess=0.0)}.
     */
    public static double zSpread(final Bond bond, final Price price, final YieldTermStructure discount,
            final Compounding compounding, final Frequency frequency, final Date settlement) {
        return zSpread(bond, price, discount, compounding, frequency, settlement, 1.0e-10, 100, 0.0);
    }

    /**
     * Bond price information. Mirrors C++ inner class {@code Bond::Price} (ql/instruments/bond.hpp:62-76).
     */
    public static final class Price {

        private final double amount_;
        private final Type type_;
        /** Default-constructed price: invalid (no amount given). */
        public Price() {
            this.amount_ = Double.NaN;
            this.type_ = Type.Clean;
        }

        public Price(final double amount, final Type type) {
            this.amount_ = amount;
            this.type_ = type;
        }

        public double amount() {
            QL.require(!Double.isNaN(amount_), "no amount given");
            return amount_;
        }

        public Type type() {
            return type_;
        }

        public boolean isValid() {
            return !Double.isNaN(amount_);
        }

        public enum Type {Dirty, Clean}
    }
}
