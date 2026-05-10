/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2007 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Constants;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Helper class for instantiating standard market cap and floor.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/instruments/makecapfloor.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>{@code operator CapFloor()} / {@code operator shared_ptr<CapFloor>()}
 *      → {@link #value()} (matches the JQuantLib {@code MakeVanillaSwap}
 *      / {@code MakeSwaption} idiom).</li>
 *  <li>The {@code MakeVanillaSwap} field is mutated in place (Java's
 *      method-chaining returns this); behavior matches C++.</li>
 *  <li>{@code Null<Rate>()} → {@link Constants#NULL_REAL}.</li>
 * </ul>
 */
public class MakeCapFloor {

    //
    // private fields
    //

    private final CapFloor.Type capFloorType_;
    private double strike_;
    private boolean firstCapletExcluded_;
    private boolean asOptionlet_ = false;

    private final MakeVanillaSwap makeVanillaSwap_;

    private PricingEngine engine_;

    //
    // public constructors
    //

    public MakeCapFloor(final CapFloor.Type capFloorType,
                        final Period tenor,
                        final IborIndex iborIndex,
                        final double strike,
                        final Period forwardStart) {
        this.capFloorType_ = capFloorType;
        this.strike_ = strike;
        this.firstCapletExcluded_ = forwardStart.equals(new Period(0, TimeUnit.Days));
        // setting the fixed leg tenor avoids that MakeVanillaSwap throws
        // because of an unknown fixed leg default tenor for a currency,
        // notice that only the floating leg of the swap is used anyway
        this.makeVanillaSwap_ = new MakeVanillaSwap(tenor, iborIndex, 0.0, forwardStart)
                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                .withFixedLegDayCount(new Actual365Fixed());
    }

    /** Convenience: ATM strike, immediate forward start. */
    public MakeCapFloor(final CapFloor.Type capFloorType,
                        final Period tenor,
                        final IborIndex iborIndex) {
        this(capFloorType, tenor, iborIndex, Constants.NULL_REAL,
                new Period(0, TimeUnit.Days));
    }

    /** Convenience: explicit strike, immediate forward start. */
    public MakeCapFloor(final CapFloor.Type capFloorType,
                        final Period tenor,
                        final IborIndex iborIndex,
                        final double strike) {
        this(capFloorType, tenor, iborIndex, strike, new Period(0, TimeUnit.Days));
    }

    //
    // operator-style accessor (mirrors C++ operator CapFloor()/shared_ptr<CapFloor>())
    //

    public CapFloor value() {
        final VanillaSwap swap = makeVanillaSwap_.value();
        Leg leg = swap.floatingLeg();

        if (firstCapletExcluded_ && !leg.isEmpty()) {
            leg = removeFirst(leg);
        }
        if (asOptionlet_ && leg.size() > 1) {
            leg = lastOnly(leg);
        }

        final List<Double> strikeVector = new ArrayList<Double>(1);
        double s = strike_;
        if (s == Constants.NULL_REAL) {
            // temporary patch... should be fixed for every CapFloor::Engine
            QL.require(engine_ instanceof BlackCapFloorEngine,
                    "cannot calculate ATM without a BlackCapFloorEngine");
            final BlackCapFloorEngine bce = (BlackCapFloorEngine) engine_;
            final Handle<YieldTermStructure> discountCurve = bce.termStructure();
            s = CashFlows.getInstance().atmRate(leg, discountCurve,
                    discountCurve.currentLink().referenceDate(),
                    discountCurve.currentLink().referenceDate(),
                    0, 0.0);
        }
        strikeVector.add(s);

        // CapFloor v1.42.1-style ctor (no termStructure): pass null.
        final CapFloor capFloor = new CapFloor(capFloorType_, leg, strikeVector,
                (Handle<YieldTermStructure>) null, engine_);
        // engine already wired by ctor when non-null; explicit call kept for parity.
        if (engine_ != null) {
            capFloor.setPricingEngine(engine_);
        }
        return capFloor;
    }

    //
    // builder methods (chainable)
    //

    public MakeCapFloor withNominal(final double n) {
        makeVanillaSwap_.withNominal(n);
        return this;
    }

    public MakeCapFloor withEffectiveDate(final Date effectiveDate,
                                          final boolean firstCapletExcluded) {
        makeVanillaSwap_.withEffectiveDate(effectiveDate);
        this.firstCapletExcluded_ = firstCapletExcluded;
        return this;
    }

    public MakeCapFloor withTenor(final Period t) {
        makeVanillaSwap_.withFloatingLegTenor(t);
        return this;
    }

    public MakeCapFloor withCalendar(final Calendar cal) {
        makeVanillaSwap_.withFloatingLegCalendar(cal);
        return this;
    }

    public MakeCapFloor withConvention(final BusinessDayConvention bdc) {
        makeVanillaSwap_.withFloatingLegConvention(bdc);
        return this;
    }

    public MakeCapFloor withTerminationDateConvention(final BusinessDayConvention bdc) {
        makeVanillaSwap_.withFloatingLegTerminationDateConvention(bdc);
        return this;
    }

    public MakeCapFloor withRule(final DateGeneration.Rule r) {
        makeVanillaSwap_.withFloatingLegRule(r);
        return this;
    }

    public MakeCapFloor withEndOfMonth(final boolean flag) {
        makeVanillaSwap_.withFloatingLegEndOfMonth(flag);
        return this;
    }

    public MakeCapFloor withFirstDate(final Date d) {
        makeVanillaSwap_.withFloatingLegFirstDate(d);
        return this;
    }

    public MakeCapFloor withNextToLastDate(final Date d) {
        makeVanillaSwap_.withFloatingLegNextToLastDate(d);
        return this;
    }

    public MakeCapFloor withDayCount(final DayCounter dc) {
        makeVanillaSwap_.withFloatingLegDayCount(dc);
        return this;
    }

    /** Only keep the last coupon (turns the cap/floor into a single optionlet). */
    public MakeCapFloor asOptionlet(final boolean b) {
        this.asOptionlet_ = b;
        return this;
    }

    /** Only keep the last coupon. */
    public MakeCapFloor asOptionlet() {
        return asOptionlet(true);
    }

    public MakeCapFloor withPricingEngine(final PricingEngine engine) {
        this.engine_ = engine;
        return this;
    }

    //
    // helpers
    //

    private static Leg removeFirst(final Leg leg) {
        final Leg out = new Leg(leg.size() - 1);
        for (int i = 1; i < leg.size(); ++i) {
            out.add(leg.get(i));
        }
        return out;
    }

    private static Leg lastOnly(final Leg leg) {
        final Leg out = new Leg(1);
        out.add(leg.get(leg.size() - 1));
        return out;
    }
}
