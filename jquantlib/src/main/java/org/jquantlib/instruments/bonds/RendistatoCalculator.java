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
 Copyright (C) 2010, 2011 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/

package org.jquantlib.instruments.bonds;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor;
import org.jquantlib.instruments.MakeVanillaSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.cashflow.CashFlows.Duration;
import org.jquantlib.pricingengines.bond.BondFunctions;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.util.LazyObject;

/**
 * Italian Rendistato (average yield of a fixed-tenor BTP basket) calculator.
 *
 * <p>Java port of QuantLib v1.42.1 {@code RendistatoCalculator}
 * (ql/instruments/bonds/btp.{hpp,cpp}).
 *
 * <p>Performs two parallel sweeps each {@link #performCalculations()}:
 * <ol>
 *   <li>per-BTP yield + modified duration (using the basket clean-price
 *       quotes), then the weighted-average duration.</li>
 *   <li>per-swap-tenor (1..15Y) fair rate, then a synthetic fixed-rate
 *       bond yield + modified duration. The first swap whose synthetic
 *       bond duration exceeds the basket-weighted duration locates the
 *       equivalent-swap index (the previous one, conservative).</li>
 * </ol>
 *
 * <p>Equivalent-swap proxies expose the rate/yield/duration/length/spread
 * of that single swap. The spread is the simple {@code yield - swapRate}
 * difference.
 *
 * @author Jose Moya
 */
public class RendistatoCalculator extends LazyObject {

    private final RendistatoBasket basket_;
    private final Euribor euriborIndex_;
    private final Handle< YieldTermStructure > discountCurve_;

    private final List< Double > yields_;
    private final List< Double > durations_;
    private double duration_;
    private int equivalentSwapIndex_;

    private static final int N_SWAPS = 15;
    private final List< VanillaSwap > swaps_;
    private final List< Double > swapLengths_;
    private final List< Double > swapBondDurations_;
    private final List< Double > swapBondYields_;
    private final List< Double > swapRates_;

    public RendistatoCalculator(final RendistatoBasket basket, final Euribor euriborIndex,
            final Handle< YieldTermStructure > discountCurve) {
        this.basket_ = basket;
        this.euriborIndex_ = euriborIndex;
        this.discountCurve_ = discountCurve;

        // mutable per-calc result buffers
        final int n = basket_.size();
        this.yields_ = new ArrayList<>(n);
        this.durations_ = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            yields_.add(0.05);
            durations_.add(Constants.NULL_REAL);
        }

        // swap-tenor buffers (1..N_SWAPS years)
        this.swaps_ = new ArrayList<>(N_SWAPS);
        this.swapLengths_ = new ArrayList<>(N_SWAPS);
        this.swapBondDurations_ = new ArrayList<>(N_SWAPS);
        this.swapBondYields_ = new ArrayList<>(N_SWAPS);
        this.swapRates_ = new ArrayList<>(N_SWAPS);

        // basket / index / discount-curve observation
        basket_.addObserver(this);
        euriborIndex_.addObserver(this);
        discountCurve_.addObserver(this);

        final double dummyRate = 0.05;
        for (int i = 0; i < N_SWAPS; ++i) {
            final double len = (double) (i + 1);
            swapLengths_.add(len);
            swapBondDurations_.add(Constants.NULL_REAL);
            swapBondYields_.add(0.05);
            swapRates_.add(Constants.NULL_REAL);
            final VanillaSwap s = new MakeVanillaSwap(new Period((int) len, TimeUnit.Years), euriborIndex_,
                    dummyRate, new Period(1, TimeUnit.Days))
                    .withDiscountingTermStructure(discountCurve_).value();
            swaps_.add(s);
        }
    }

    //
    // Calculations
    //

    public /* @Rate */ double yield() {
        // inner_product: sum(weights[i] * yields[i])
        final List< Double > w = basket_.weights();
        final List< Double > ys = yields();
        double acc = 0.0;
        for (int i = 0; i < w.size(); ++i) {
            acc += w.get(i) * ys.get(i);
        }
        return acc;
    }

    public /* @Time */ double duration() {
        calculate();
        return duration_;
    }

    // bonds
    public List< Double > yields() {
        calculate();
        return yields_;
    }

    public List< Double > durations() {
        calculate();
        return durations_;
    }

    // swaps
    public List< Double > swapLengths() {
        return swapLengths_;
    }

    public List< Double > swapRates() {
        calculate();
        return swapRates_;
    }

    public List< Double > swapYields() {
        calculate();
        return swapBondYields_;
    }

    public List< Double > swapDurations() {
        calculate();
        return swapBondDurations_;
    }

    //
    // Equivalent-swap proxies
    //

    public VanillaSwap equivalentSwap() {
        calculate();
        return swaps_.get(equivalentSwapIndex_);
    }

    public /* @Rate */ double equivalentSwapRate() {
        calculate();
        return swapRates_.get(equivalentSwapIndex_);
    }

    public /* @Rate */ double equivalentSwapYield() {
        calculate();
        return swapBondYields_.get(equivalentSwapIndex_);
    }

    public /* @Time */ double equivalentSwapDuration() {
        calculate();
        return swapBondDurations_.get(equivalentSwapIndex_);
    }

    public /* @Time */ double equivalentSwapLength() {
        calculate();
        return swapLengths_.get(equivalentSwapIndex_);
    }

    public /* @Spread */ double equivalentSwapSpread() {
        return this.yield() - equivalentSwapRate();
    }

    //
    // LazyObject interface
    //
    @Override
    protected void performCalculations() {
        final List< BTP > btps = basket_.btps();
        final List< Handle< Quote > > quotes = basket_.cleanPriceQuotes();
        final Date bondSettlementDate = btps.get(0).settlementDate();
        final ActualActual aaIsma = new ActualActual(ActualActual.Convention.ISMA);

        for (int i = 0; i < basket_.size(); ++i) {
            final double y = BondFunctions.yield(btps.get(i),
                    new BondFunctions.Price(quotes.get(i).currentLink().value(), BondFunctions.Price.Type.Clean),
                    aaIsma, Compounding.Compounded, Frequency.Annual, bondSettlementDate,
                    /* accuracy */ 1.0e-10, /* maxIterations */ 100, /* guess */ yields_.get(i));
            yields_.set(i, y);
            final double d = BondFunctions.duration(btps.get(i), y, aaIsma, Compounding.Compounded, Frequency.Annual,
                    Duration.Modified, bondSettlementDate);
            durations_.set(i, d);
        }

        // weighted-average duration
        final List< Double > w = basket_.weights();
        double acc = 0.0;
        for (int i = 0; i < w.size(); ++i) {
            acc += w.get(i) * durations_.get(i);
        }
        duration_ = acc;

        final int settlDays = 2;
        final DayCounter fixedDayCount = swaps_.get(0).fixedDayCount();
        equivalentSwapIndex_ = N_SWAPS - 1;
        swapRates_.set(0, swaps_.get(0).fairRate());
        FixedRateBond swapBond0 = new FixedRateBond(settlDays, /* faceAmount */ 100.0, swaps_.get(0).fixedSchedule(),
                new double[] { swapRates_.get(0) }, fixedDayCount, BusinessDayConvention.Following, /* redemption */ 100.0);
        double sy0 = BondFunctions.yield(swapBond0,
                new BondFunctions.Price(100.0, BondFunctions.Price.Type.Clean), aaIsma,
                Compounding.Compounded, Frequency.Annual, bondSettlementDate,
                1.0e-10, 100, swapBondYields_.get(0));
        swapBondYields_.set(0, sy0);
        swapBondDurations_.set(0, BondFunctions.duration(swapBond0, sy0, aaIsma, Compounding.Compounded,
                Frequency.Annual, Duration.Modified, bondSettlementDate));

        for (int i = 1; i < N_SWAPS; ++i) {
            swapRates_.set(i, swaps_.get(i).fairRate());
            FixedRateBond swapBond = new FixedRateBond(settlDays, 100.0, swaps_.get(i).fixedSchedule(),
                    new double[] { swapRates_.get(i) }, fixedDayCount, BusinessDayConvention.Following, 100.0);
            double sy = BondFunctions.yield(swapBond,
                    new BondFunctions.Price(100.0, BondFunctions.Price.Type.Clean), aaIsma,
                    Compounding.Compounded, Frequency.Annual, bondSettlementDate,
                    1.0e-10, 100, swapBondYields_.get(i));
            swapBondYields_.set(i, sy);
            swapBondDurations_.set(i, BondFunctions.duration(swapBond, sy, aaIsma, Compounding.Compounded,
                    Frequency.Annual, Duration.Modified, bondSettlementDate));
            if (swapBondDurations_.get(i) > duration_) {
                equivalentSwapIndex_ = i - 1;
                break; // exit the loop
            }
        }
    }
}
