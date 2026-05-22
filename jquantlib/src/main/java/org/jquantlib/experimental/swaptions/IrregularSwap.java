/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006, 2007 StatPro Italia srl
 Copyright (C) 2006, 2008 Ferdinando Ametrano
 Copyright (C) 2010 Andre Miemiec

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.swaptions;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.instruments.Swap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * Irregular swap: fixed vs floating leg.
 *
 * <p>Phase 4i port of C++ QuantLib v1.42.1
 * {@code ql/experimental/swaptions/irregularswap.{hpp,cpp}}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Allows building a swap from arbitrary fixed and floating legs (variable
 * notionals, irregular schedules), unlike {@link VanillaSwap} which assumes regular schedules. Both legs are passed in
 * pre-built; this class only registers them as observers and surfaces the necessary arguments to engines.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *   <li>The C++ {@code IrregularSwap::Type} alias is mapped to
 *       {@link VanillaSwap.Type} ({@code Receiver}/{@code Payer}).</li>
 *   <li>{@code Type} is exposed via the existing {@link VanillaSwap#Type}
 *       enum to avoid creating a duplicate symbol.</li>
 *   <li>{@code fairRate}/{@code fairSpread} preserve the C++ {@code 0.0}
 *       fallback when computed via {@link #fetchResults(PricingEngine.Results)}
 *       (legacy DEBUG semantics; both result fields default to {@code 0.0}).</li>
 * </ul>
 */
public class IrregularSwap extends Swap {

    private final VanillaSwap.Type type_;
    // results
    private double fairRate_;
    private double fairSpread_;
    /**
     * Constructs an irregular swap from explicit legs.
     *
     * @param type     {@link VanillaSwap.Type#Payer} pays fixed, receives floating; {@link VanillaSwap.Type#Receiver}
     *                 the opposite.
     * @param fixLeg   pre-built leg of {@link FixedRateCoupon}s
     * @param floatLeg pre-built leg of {@link IborCoupon}s
     */
    public IrregularSwap(final VanillaSwap.Type type, final Leg fixLeg, final Leg floatLeg) {
        super(2);
        this.type_ = type;

        switch ( type ) {
        case Payer:
            payer[0] = -1.0;
            payer[1] = +1.0;
            break;
        case Receiver:
            payer[0] = +1.0;
            payer[1] = -1.0;
            break;
        default:
            throw new LibraryException("Unknown Irregular-swap type");
        }

        // Fixed leg (index 0)
        final List< Leg > legList = new ArrayList<>();
        legList.add(fixLeg);
        legList.add(floatLeg);
        this.legs = legList;

        for ( final CashFlow cf : fixLeg ) {
            cf.addObserver(this);
        }
        for ( final CashFlow cf : floatLeg ) {
            cf.addObserver(this);
        }
    }

    public VanillaSwap.Type type() {
        return type_;
    }

    //
    // public inspectors
    //

    public Leg fixedLeg() {
        return legs.get(0);
    }

    public Leg floatingLeg() {
        return legs.get(1);
    }

    public double fixedLegBPS() {
        calculate();
        QL.require(!Double.isNaN(legBPS[0]), "result not available");
        return legBPS[0];
    }

    //
    // public results
    //

    public double fixedLegNPV() {
        calculate();
        QL.require(!Double.isNaN(legNPV[0]), "result not available");
        return legNPV[0];
    }

    public double fairRate() {
        calculate();
        QL.require(!Double.isNaN(fairRate_), "result not available");
        return fairRate_;
    }

    public double floatingLegBPS() {
        calculate();
        QL.require(!Double.isNaN(legBPS[1]), "result not available");
        return legBPS[1];
    }

    public double floatingLegNPV() {
        calculate();
        QL.require(!Double.isNaN(legNPV[1]), "result not available");
        return legNPV[1];
    }

    public double fairSpread() {
        calculate();
        QL.require(!Double.isNaN(fairSpread_), "result not available");
        return fairSpread_;
    }

    @Override
    public void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        super.setupArguments(args);

        if ( !(args instanceof IrregularSwap.ArgumentsImpl) ) {
            // it might be a plain Swap engine — return silently (mirrors C++).
            return;
        }

        final IrregularSwap.ArgumentsImpl arguments = (IrregularSwap.ArgumentsImpl) args;
        arguments.type = type_;

        final Leg fixedCoupons = fixedLeg();
        final int nF = fixedCoupons.size();
        arguments.fixedResetDates = new Date[nF];
        arguments.fixedPayDates = new Date[nF];
        arguments.fixedNominals = new double[nF];
        arguments.fixedCoupons = new double[nF];

        for ( int i = 0; i < nF; ++i ) {
            final FixedRateCoupon coupon = (FixedRateCoupon) fixedCoupons.get(i);
            arguments.fixedPayDates[i] = coupon.date();
            arguments.fixedResetDates[i] = coupon.accrualStartDate();
            arguments.fixedCoupons[i] = coupon.amount();
            arguments.fixedNominals[i] = coupon.nominal();
        }

        final Leg floatingCoupons = floatingLeg();
        final int nL = floatingCoupons.size();
        arguments.floatingResetDates = new Date[nL];
        arguments.floatingPayDates = new Date[nL];
        arguments.floatingFixingDates = new Date[nL];
        arguments.floatingAccrualTimes = new double[nL];
        arguments.floatingSpreads = new double[nL];
        arguments.floatingNominals = new double[nL];
        arguments.floatingCoupons = new double[nL];

        for ( int i = 0; i < nL; ++i ) {
            final IborCoupon coupon = (IborCoupon) floatingCoupons.get(i);
            arguments.floatingResetDates[i] = coupon.accrualStartDate();
            arguments.floatingPayDates[i] = coupon.date();
            arguments.floatingFixingDates[i] = coupon.fixingDate();
            arguments.floatingAccrualTimes[i] = coupon.accrualPeriod();
            arguments.floatingSpreads[i] = coupon.spread();
            arguments.floatingNominals[i] = coupon.nominal();
            try {
                arguments.floatingCoupons[i] = coupon.amount();
            } catch ( final Exception e ) {
                arguments.floatingCoupons[i] = Constants.NULL_REAL;
            }
        }
    }

    //
    // overrides Swap
    //

    @Override
    public void fetchResults(final PricingEngine.Results r) /* @ReadOnly */ {
        super.fetchResults(r);

        if (r instanceof IrregularSwap.ResultsImpl results) {
            fairRate_ = results.fairRate;
            fairSpread_ = results.fairSpread;
        } else {
            fairRate_ = Constants.NULL_REAL;
            fairSpread_ = Constants.NULL_REAL;
        }

        if ( Double.isNaN(fairRate_) ) {
            // mirrors C++ DEBUG fallback: 0.0 if BPS is available
            if ( !Double.isNaN(legBPS[0]) ) {
                fairRate_ = 0.0;
            }
        }
        if ( Double.isNaN(fairSpread_) ) {
            if ( !Double.isNaN(legBPS[1]) ) {
                fairSpread_ = 0.0;
            }
        }
    }

    @Override
    protected void setupExpired() /* @ReadOnly */ {
        super.setupExpired();
        legBPS[0] = legBPS[1] = 0.0;
        fairRate_ = Constants.NULL_REAL;
        fairSpread_ = Constants.NULL_REAL;
    }

    public interface Arguments extends Swap.Arguments { /* marker */
    }

    //
    // public inner interfaces
    //

    public interface Results extends Swap.Results { /* marker */
    }

    /** Type alias to {@link VanillaSwap.Type} (Payer/Receiver). */
    public static final class TypeRef {
        private TypeRef() {
        }
    }

    //
    // public inner classes
    //

    public static class ArgumentsImpl extends Swap.ArgumentsImpl implements IrregularSwap.Arguments {

        public VanillaSwap.Type type = VanillaSwap.Type.Receiver;

        public Date[] fixedResetDates;
        public Date[] fixedPayDates;
        public double[] fixedCoupons;
        public double[] fixedNominals;

        public Date[] floatingResetDates;
        public Date[] floatingFixingDates;
        public Date[] floatingPayDates;
        public double[] floatingAccrualTimes;
        public double[] floatingNominals;
        public double[] floatingSpreads;
        public double[] floatingCoupons;

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();

            QL.require(fixedResetDates.length == fixedPayDates.length,
                    "number of fixed start dates different from number of fixed payment dates");
            QL.require(fixedPayDates.length == fixedCoupons.length,
                    "number of fixed payment dates different from number of fixed coupon amounts");
            QL.require(floatingResetDates.length == floatingPayDates.length,
                    "number of floating start dates different from number of floating payment dates");
            QL.require(floatingFixingDates.length == floatingPayDates.length,
                    "number of floating fixing dates different from number of floating payment dates");
            QL.require(floatingAccrualTimes.length == floatingPayDates.length,
                    "number of floating accrual times different from number of floating payment dates");
            QL.require(floatingSpreads.length == floatingPayDates.length,
                    "number of floating spreads different from number of floating payment dates");
            QL.require(floatingPayDates.length == floatingCoupons.length,
                    "number of floating payment dates different from number of floating coupon amounts");
        }
    }

    public static class ResultsImpl extends Swap.ResultsImpl implements IrregularSwap.Results {

        public double fairRate;
        public double fairSpread;

        @Override
        public void reset() {
            super.reset();
            fairRate = Constants.NULL_REAL;
            fairSpread = Constants.NULL_REAL;
        }
    }

    public abstract static class EngineImpl extends GenericEngine< IrregularSwap.Arguments, IrregularSwap.Results > {

        protected EngineImpl() {
            super(new IrregularSwap.ArgumentsImpl(), new IrregularSwap.ResultsImpl());
        }
    }
}
