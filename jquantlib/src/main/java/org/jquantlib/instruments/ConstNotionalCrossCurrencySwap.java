/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2016 Quaternion Risk Management Ltd
 Copyright (C) 2025 Paolo D'Elia

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

package org.jquantlib.instruments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.currencies.Currency;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Generic constant-notional cross-currency swap.
 * <p>
 * The first leg holds the pay-currency cashflows and the second leg holds the receive-currency cashflows.
 * <p>
 * Ported from C++ QuantLib v1.43 {@code ql/instruments/constnotionalcrosscurrencyswap.{hpp,cpp}} — new in that release.
 *
 * @author Jose Moya
 * @category instruments
 */
public class ConstNotionalCrossCurrencySwap extends Swap {

    protected List< Currency > currencies;

    private double[] inCcyLegNPV;
    private double[] inCcyLegBPS;
    private double[] npvDateDiscounts;

    //
    // public constructors
    //

    /**
     * Two-leg constructor: the first leg is paid, the second is received.
     *
     * @param firstLeg     cashflows of the first (paid) leg
     * @param firstLegCcy  currency the first leg's cashflows are denominated in
     * @param secondLeg    cashflows of the second (received) leg
     * @param secondLegCcy currency the second leg's cashflows are denominated in
     */
    public ConstNotionalCrossCurrencySwap(final Leg firstLeg, final Currency firstLegCcy, final Leg secondLeg,
            final Currency secondLegCcy) {
        super(firstLeg, secondLeg);
        this.currencies = new ArrayList<>(2);
        this.currencies.add(firstLegCcy);
        this.currencies.add(secondLegCcy);
        allocateInCcyResults(2);
    }

    /**
     * Multi-leg constructor. The {@code payer} flags give each leg's direction and {@code currencies} its denomination.
     */
    public ConstNotionalCrossCurrencySwap(final List< Leg > legs, final boolean[] payer,
            final List< Currency > currencies) {
        super(legs, payer);
        QL.require(payer.length == currencies.size(),
                "size mismatch between payer (" + payer.length + ") and currencies (" + currencies.size() + ")");
        this.currencies = new ArrayList<>(currencies);
        allocateInCcyResults(legs.size());
    }

    //
    // protected constructors
    //

    /**
     * Leg-count constructor for derived classes that build their legs themselves. Mirrors the C++ {@code explicit
     * ConstNotionalCrossCurrencySwap(Size legs)}.
     * <p>
     * Unlike C++'s {@code std::vector} resize, {@link Swap#Swap(int)} leaves the leg list empty, so this fills it with
     * placeholder legs that subclasses overwrite through {@link #setLeg(int, Leg)}.
     */
    protected ConstNotionalCrossCurrencySwap(final int legs) {
        super(legs);
        for ( int i = 0; i < legs; ++i ) {
            this.legs.add(new Leg());
        }
        this.currencies = new ArrayList<>(legs);
        for ( int i = 0; i < legs; ++i ) {
            this.currencies.add(new Currency());
        }
        allocateInCcyResults(legs);
    }

    private void allocateInCcyResults(final int n) {
        this.inCcyLegNPV = new double[n];
        this.inCcyLegBPS = new double[n];
        this.npvDateDiscounts = new double[n];
    }

    /**
     * Replaces leg {@code j}. For use by derived-class constructors only — the C++ code assigns {@code legs_[j]}
     * directly, which Java's {@code List} cannot express before the slot exists.
     */
    protected void setLeg(final int j, final Leg leg) {
        this.legs.set(j, leg);
    }

    //
    // public methods
    //

    /**
     * Returns the currency of leg {@code j}.
     */
    public Currency legCurrency(final int j) /* @ReadOnly */ {
        QL.require(j < legs.size(), "leg# " + j + " doesn't exist!");
        return currencies.get(j);
    }

    /**
     * Returns the BPS of leg {@code j} expressed in that leg's own currency (i.e. before FX conversion).
     */
    public /*@Real*/ double inCcyLegBPS(final int j) /* @ReadOnly */ {
        QL.require(j < legs.size(), "leg# " + j + " doesn't exist!");
        calculate();
        return inCcyLegBPS[j];
    }

    /**
     * Returns the NPV of leg {@code j} expressed in that leg's own currency (i.e. before FX conversion).
     */
    public /*@Real*/ double inCcyLegNPV(final int j) /* @ReadOnly */ {
        QL.require(j < legs.size(), "leg #" + j + " doesn't exist!");
        calculate();
        return inCcyLegNPV[j];
    }

    /**
     * Returns the discount factor applied to leg {@code j} at the NPV date.
     */
    public /*@DiscountFactor*/ double npvDateDiscounts(final int j) /* @ReadOnly */ {
        QL.require(j < legs.size(), "leg #" + j + " doesn't exist!");
        calculate();
        return npvDateDiscounts[j];
    }

    //
    // overrides Swap
    //

    @Override
    public void setupArguments(final PricingEngine.Arguments args) /* @ReadOnly */ {
        super.setupArguments(args);
        QL.require(args instanceof ConstNotionalCrossCurrencySwap.ArgumentsImpl,
                "the arguments are not of type cross currency swap");
        final ConstNotionalCrossCurrencySwap.ArgumentsImpl a = (ConstNotionalCrossCurrencySwap.ArgumentsImpl) args;
        a.currencies = new ArrayList<>(currencies);
    }

    @Override
    public void fetchResults(final PricingEngine.Results r) /* @ReadOnly */ {
        super.fetchResults(r);
        QL.require(r instanceof ConstNotionalCrossCurrencySwap.ResultsImpl,
                "the results are not of type cross currency swap");
        final ConstNotionalCrossCurrencySwap.ResultsImpl results = (ConstNotionalCrossCurrencySwap.ResultsImpl) r;

        if ( results.inCcyLegNPV != null && results.inCcyLegNPV.length > 0 ) {
            QL.require(results.inCcyLegNPV.length == inCcyLegNPV.length,
                    "wrong number of in currency leg NPVs returned by engine");
            inCcyLegNPV = results.inCcyLegNPV;
        } else {
            Arrays.fill(inCcyLegNPV, Constants.NULL_REAL);
        }

        if ( results.inCcyLegBPS != null && results.inCcyLegBPS.length > 0 ) {
            QL.require(results.inCcyLegBPS.length == inCcyLegBPS.length,
                    "wrong number of in currency leg BPSs returned by engine");
            inCcyLegBPS = results.inCcyLegBPS;
        } else {
            Arrays.fill(inCcyLegBPS, Constants.NULL_REAL);
        }

        if ( results.npvDateDiscounts != null && results.npvDateDiscounts.length > 0 ) {
            QL.require(results.npvDateDiscounts.length == npvDateDiscounts.length,
                    "wrong number of npv date discounts returned by engine");
            npvDateDiscounts = results.npvDateDiscounts;
        } else {
            Arrays.fill(npvDateDiscounts, Constants.NULL_REAL);
        }
    }

    @Override
    protected void setupExpired() /* @ReadOnly */ {
        super.setupExpired();
        Arrays.fill(inCcyLegBPS, 0.0);
        Arrays.fill(inCcyLegNPV, 0.0);
        Arrays.fill(npvDateDiscounts, 0.0);
    }

    //
    // protected static methods
    //

    /**
     * Prepends the initial notional exchange to {@code leg} and appends the final one, so that a cross-currency leg
     * exchanges principal at both ends. Mirrors C++
     * {@code ConstNotionalCrossCurrencySwap::addNotionalExchangesToLeg}.
     */
    protected static void addNotionalExchangesToLeg(final Leg leg, final Calendar calendar, final Date earliestDate,
            final Date maturityDate, final int paymentLag, final BusinessDayConvention legBdc, final double nominal) {
        Date aDate = calendar.advance(earliestDate, new Period(paymentLag, TimeUnit.Days), legBdc);
        CashFlow aCashflow = new SimpleCashFlow(-nominal, aDate);
        leg.add(0, aCashflow);

        aDate = calendar.advance(maturityDate, new Period(paymentLag, TimeUnit.Days), legBdc);
        aCashflow = new SimpleCashFlow(nominal, aDate);
        leg.add(aCashflow);
    }

    //
    // inner interfaces
    //

    public interface Arguments extends Swap.Arguments { /* marking interface */
    }

    public interface Results extends Swap.Results { /* marking interface */
    }

    //
    // inner classes
    //

    static public class ArgumentsImpl extends Swap.ArgumentsImpl implements ConstNotionalCrossCurrencySwap.Arguments {

        public List< Currency > currencies;

        @Override
        public void validate() /* @ReadOnly */ {
            super.validate();
            QL.require(legs.size() == currencies.size(), "number of legs is not equal to number of currencies");
        }
    }

    static public class ResultsImpl extends Swap.ResultsImpl implements ConstNotionalCrossCurrencySwap.Results {

        public double[] inCcyLegNPV;
        public double[] inCcyLegBPS;
        public double[] npvDateDiscounts;

        @Override
        public void reset() {
            super.reset();
            inCcyLegNPV = new double[0];
            inCcyLegBPS = new double[0];
            npvDateDiscounts = new double[0];
        }
    }

    static public abstract class EngineImpl
            extends GenericEngine< ConstNotionalCrossCurrencySwap.Arguments, ConstNotionalCrossCurrencySwap.Results > {

        protected EngineImpl() {
            super(new ConstNotionalCrossCurrencySwap.ArgumentsImpl(), new ConstNotionalCrossCurrencySwap.ResultsImpl());
        }

        protected EngineImpl(final ConstNotionalCrossCurrencySwap.ArgumentsImpl arguments,
                final ConstNotionalCrossCurrencySwap.ResultsImpl results) {
            super(arguments, results);
        }
    }
}
