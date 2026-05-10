/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2010 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.optionlet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.MakeCapFloor;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.capfloor.CapFloorTermVolCurve;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Helper class to extend an {@link OptionletStripper1} object stripping
 * additional optionlet (i.e. caplet/floorlet) volatilities (a.k.a.
 * forward-forward volatilities) from the (cap/floor) At-The-Money term
 * volatilities of a {@link CapFloorTermVolCurve}.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/optionlet/optionletstripper2.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>Brent solver in Java is parameterized on {@link Ops.DoubleOp} — the
 *      inner {@code ObjectiveFunction} implements that, mirroring C++
 *      {@code Real operator()(Volatility) const}.</li>
 *  <li>{@code CapFloor.atmRate(YieldTermStructure)} is not exposed; we
 *      compute the ATM directly from the floating leg via
 *      {@code CashFlows.atmRate(leg, discountCurve)}.</li>
 * </ul>
 */
public class OptionletStripper2 extends OptionletStripper {

    //
    // private fields
    //

    private final OptionletStripper1 stripper1_;
    private final Handle<CapFloorTermVolCurve> atmCapFloorTermVolCurve_;
    private final DayCounter dc_;
    private final int nOptionExpiries_;
    private final List<Double> atmCapFloorStrikes_;
    private final List<Double> atmCapFloorPrices_;
    private List<Double> spreadsVolImplied_;
    private final List<CapFloor> caps_;
    private int maxEvaluations_ = 10000;
    private double accuracy_ = 1.0e-6;

    //
    // public constructor
    //

    public OptionletStripper2(final OptionletStripper1 optionletStripper1,
                              final Handle<CapFloorTermVolCurve> atmCapFloorTermVolCurve) {
        super(optionletStripper1.termVolSurface(),
                optionletStripper1.iborIndex(),
                new Handle<YieldTermStructure>(),
                optionletStripper1.volatilityType(),
                optionletStripper1.displacement(),
                optionletStripper1.optionletFrequency());
        this.stripper1_ = optionletStripper1;
        this.atmCapFloorTermVolCurve_ = atmCapFloorTermVolCurve;
        this.dc_ = stripper1_.termVolSurface().dayCounter();
        this.nOptionExpiries_ = atmCapFloorTermVolCurve.currentLink().optionTenors().size();
        this.atmCapFloorStrikes_ = newFilledList(nOptionExpiries_, 0.0);
        this.atmCapFloorPrices_ = newFilledList(nOptionExpiries_, 0.0);
        this.spreadsVolImplied_ = newFilledList(nOptionExpiries_, 0.0);
        this.caps_ = new ArrayList<CapFloor>(Collections.<CapFloor>nCopies(nOptionExpiries_, null));

        stripper1_.addObserver(this);
        atmCapFloorTermVolCurve_.addObserver(this);

        QL.require(dc_.equals(atmCapFloorTermVolCurve.currentLink().dayCounter()),
                "different day counters provided");
    }

    //
    // LazyObject interface
    //

    @Override
    protected void performCalculations() {
        // copy stripper1 data through into our protected mutable state
        copyList(stripper1_.optionletFixingDates(), optionletDates_);
        copyList(stripper1_.optionletPaymentDates(), optionletPaymentDates_);
        copyList(stripper1_.optionletAccrualPeriods(), optionletAccrualPeriods_);
        copyList(stripper1_.optionletFixingTimes(), optionletTimes_);
        copyList(stripper1_.atmOptionletRates(), atmOptionletRate_);
        for (int i = 0; i < optionletTimes_.size(); ++i) {
            optionletStrikes_.set(i, new ArrayList<Double>(stripper1_.optionletStrikes(i)));
            optionletVolatilities_.set(i, new ArrayList<Double>(stripper1_.optionletVolatilities(i)));
        }

        final List<Period> optionExpiriesTenors =
                atmCapFloorTermVolCurve_.currentLink().optionTenors();
        final double[] optionExpiriesTimes =
                atmCapFloorTermVolCurve_.currentLink().optionTimes();

        for (int j = 0; j < nOptionExpiries_; ++j) {
            final double atmOptionVol = atmCapFloorTermVolCurve_.currentLink()
                    .volatility(optionExpiriesTimes[j], 33.3333); // dummy strike
            final BlackCapFloorEngine engine = new BlackCapFloorEngine(
                    iborIndex_.termStructure(), atmOptionVol, dc_);
            final CapFloor cap = new MakeCapFloor(CapFloor.Type.Cap,
                    optionExpiriesTenors.get(j), iborIndex_,
                    Constants.NULL_REAL, new Period(0, TimeUnit.Days))
                    .withPricingEngine(engine)
                    .value();
            caps_.set(j, cap);
            // ATM = CashFlows.atmRate over the cap's floating leg
            final Leg leg = cap.floatingLeg();
            final Handle<YieldTermStructure> fwd = iborIndex_.termStructure();
            atmCapFloorStrikes_.set(j, CashFlows.getInstance().atmRate(leg, fwd,
                    fwd.currentLink().referenceDate(),
                    fwd.currentLink().referenceDate(), 0, 0.0));
            atmCapFloorPrices_.set(j, cap.NPV());
        }

        spreadsVolImplied_ = spreadsVolImplied();

        final StrippedOptionletAdapter adapter = new StrippedOptionletAdapter(stripper1_);
        adapter.enableExtrapolation();

        for (int j = 0; j < nOptionExpiries_; ++j) {
            for (int i = 0; i < optionletVolatilities_.size(); ++i) {
                if (i <= caps_.get(j).floatingLeg().size()) {
                    final double unadjustedVol = adapter.volatility(
                            optionletTimes_.get(i), atmCapFloorStrikes_.get(j));
                    final double adjustedVol = unadjustedVol + spreadsVolImplied_.get(j);

                    // Insert (atmStrike, adjustedVol) into the per-tenor smile
                    // at lower_bound position, mirroring C++ std::lower_bound.
                    final List<Double> strikes = optionletStrikes_.get(i);
                    final List<Double> vols = optionletVolatilities_.get(i);
                    int insertIndex = lowerBound(strikes, atmCapFloorStrikes_.get(j));
                    strikes.add(insertIndex, atmCapFloorStrikes_.get(j));
                    vols.add(insertIndex, adjustedVol);
                }
            }
        }
    }

    private List<Double> spreadsVolImplied() {
        final Brent solver = new Brent();
        final List<Double> result = newFilledList(nOptionExpiries_, 0.0);
        final double guess = 1.0e-4;
        final double minSpread = -0.1;
        final double maxSpread = 0.1;
        for (int j = 0; j < nOptionExpiries_; ++j) {
            final ObjectiveFunction f = new ObjectiveFunction(stripper1_,
                    caps_.get(j), atmCapFloorPrices_.get(j));
            solver.setMaxEvaluations(maxEvaluations_);
            final double root = solver.solve(f, accuracy_, guess, minSpread, maxSpread);
            result.set(j, root);
        }
        return result;
    }

    //
    // public inspectors
    //

    public List<Double> spreadsVol() {
        calculate();
        return spreadsVolImplied_;
    }

    public List<Double> atmCapFloorStrikes() {
        calculate();
        return atmCapFloorStrikes_;
    }

    public List<Double> atmCapFloorPrices() {
        calculate();
        return atmCapFloorPrices_;
    }

    public void setMaxEvaluations(final int n) {
        this.maxEvaluations_ = n;
    }

    public void setAccuracy(final double a) {
        this.accuracy_ = a;
    }

    //
    // helpers
    //

    private static <T> List<T> newFilledList(final int n, final T initial) {
        final List<T> out = new ArrayList<T>(n);
        for (int i = 0; i < n; ++i) {
            out.add(initial);
        }
        return out;
    }

    private static int lowerBound(final List<Double> a, final double v) {
        int lo = 0, hi = a.size();
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (a.get(mid) < v) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static <T> void copyList(final List<T> src, final List<T> dst) {
        // The protected dst lists are pre-allocated to the right size by
        // OptionletStripper's ctor — copy element-wise.
        for (int i = 0; i < dst.size(); ++i) {
            dst.set(i, src.get(i));
        }
    }

    //
    // ObjectiveFunction — mirrors C++ inner class
    //

    /**
     * Mirrors C++ {@code OptionletStripper2::ObjectiveFunction}: builds a
     * {@link SpreadedOptionletVolatility} wrapping the stripper-1 adapter
     * with a controllable {@link SimpleQuote} spread; each {@code op(s)} call
     * resets the spread, repriced the cap, and returns NPV - target.
     */
    private static final class ObjectiveFunction implements Ops.DoubleOp {
        private final SimpleQuote spreadQuote_;
        private final CapFloor cap_;
        private final double targetValue_;

        ObjectiveFunction(final OptionletStripper1 stripper1,
                          final CapFloor cap,
                          final double targetValue) {
            this.cap_ = cap;
            this.targetValue_ = targetValue;
            final StrippedOptionletAdapter adapter = new StrippedOptionletAdapter(stripper1);
            adapter.enableExtrapolation();

            // Implausible initial value forces calculation on first op() call.
            this.spreadQuote_ = new SimpleQuote(-1.0);

            final SpreadedOptionletVolatility spreaded = new SpreadedOptionletVolatility(
                    new Handle<OptionletVolatilityStructure>(adapter),
                    new Handle<Quote>(spreadQuote_));

            final BlackCapFloorEngine engine = new BlackCapFloorEngine(
                    stripper1.iborIndex().termStructure(),
                    new Handle<OptionletVolatilityStructure>(spreaded));
            cap_.setPricingEngine(engine);
        }

        @Override
        public double op(final double s) {
            if (s != spreadQuote_.value()) {
                spreadQuote_.setValue(s);
            }
            return cap_.NPV() - targetValue_;
        }
    }
}
