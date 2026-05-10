/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007, 2008 Ferdinando Ametrano
 Copyright (C) 2007 François du Vignaud
 Copyright (C) 2007 Katiuscia Manzoni
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2015 Peter Caspers
 Copyright (C) 2015 Michael von den Driesch

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.termstructures.volatilities.optionlet;

import org.jquantlib.QL;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.instruments.CapFloor;
import org.jquantlib.instruments.MakeCapFloor;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.capfloor.BachelierCapFloorEngine;
import org.jquantlib.pricingengines.capfloor.BlackCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.capfloor.CapFloorTermVolSurface;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Helper class to strip optionlet (i.e. caplet/floorlet) volatilities
 * (a.k.a. forward-forward volatilities) from the (cap/floor) term
 * volatilities of a {@link CapFloorTermVolSurface}.
 *
 * <p>Port of C++ QuantLib v1.42.1
 * {@code ql/termstructures/volatility/optionlet/optionletstripper1.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>{@code Null<Rate>()} → {@link Constants#NULL_REAL}.</li>
 *  <li>The Java {@code blackFormulaImpliedStdDev} variant doesn't accept a
 *      maxIter parameter (hardcoded to 100 inside Java BlackFormula); the
 *      ctor's {@code maxIter} parameter is therefore stored only and not
 *      forwarded.</li>
 *  <li>Normal-vol bootstrap path uses {@link BlackFormula#bachelierBlackFormulaImpliedVol}
 *      (Jäckel inverse-PhiTilde closed-form, ported in Phase 5g.5b) — both
 *      ShiftedLognormal (default) and Normal volatility types are now
 *      operational.</li>
 * </ul>
 */
public class OptionletStripper1 extends OptionletStripper {

    //
    // private fields
    //

    private Matrix capFloorPrices_, optionletPrices_;
    private Matrix capFloorVols_;
    private Matrix optionletStDevs_, capletVols_;

    private final boolean floatingSwitchStrike_;
    private double switchStrike_;
    private final double accuracy_;
    @SuppressWarnings("unused")
    private final int maxIter_;
    private final boolean dontThrow_;

    //
    // public constructors
    //

    public OptionletStripper1(final CapFloorTermVolSurface termVolSurface,
                              final IborIndex index,
                              final double switchStrike,
                              final double accuracy,
                              final int maxIter,
                              final Handle<YieldTermStructure> discount,
                              final VolatilityType type,
                              final double displacement,
                              final boolean dontThrow,
                              final Period optionletFrequency) {
        super(termVolSurface, index, discount, type, displacement, optionletFrequency);
        this.floatingSwitchStrike_ = (switchStrike == Constants.NULL_REAL);
        this.switchStrike_ = switchStrike;
        this.accuracy_ = accuracy;
        this.maxIter_ = maxIter;
        this.dontThrow_ = dontThrow;

        this.capFloorPrices_ = new Matrix(nOptionletTenors_, nStrikes_);
        this.optionletPrices_ = new Matrix(nOptionletTenors_, nStrikes_);
        this.capletVols_ = new Matrix(nOptionletTenors_, nStrikes_);
        this.capFloorVols_ = new Matrix(nOptionletTenors_, nStrikes_);

        // C++ comment: "guess is only used for shifted lognormal vols"
        final double firstGuess = 0.14;
        this.optionletStDevs_ = new Matrix(nOptionletTenors_, nStrikes_);
        for (int i = 0; i < nOptionletTenors_; ++i) {
            for (int j = 0; j < nStrikes_; ++j) {
                optionletStDevs_.set(i, j, firstGuess);
            }
        }
    }

    /** Convenience: ATM switch strike, default tolerances, ShiftedLognormal. */
    public OptionletStripper1(final CapFloorTermVolSurface termVolSurface,
                              final IborIndex index) {
        this(termVolSurface, index,
                Constants.NULL_REAL,
                1.0e-6, 100,
                new Handle<YieldTermStructure>(),
                VolatilityType.ShiftedLognormal, 0.0,
                false, null);
    }

    //
    // LazyObject interface
    //

    @Override
    protected void performCalculations() {
        // Update dates by re-creating a dummy cap on each tenor and reading
        // the last floating-rate coupon's fixing/payment dates. Mirrors C++
        // 1:1 (optionletstripper1.cpp lines 62-85).
        final Date referenceDate = termVolSurface_.referenceDate();
        final DayCounter dc = termVolSurface_.dayCounter();
        // Discounting curve does not matter for this dummy engine — we only
        // pull fixing/payment dates from the constructed cap.
        final BlackCapFloorEngine dummy = new BlackCapFloorEngine(
                iborIndex_.termStructure(), 0.20, dc);
        // Phase 5g.5f: when iborIndex is an OvernightIndex, the index's
        // native tenor() is 1*Days; OvernightLeg.leg() needs a payment-period
        // schedule (e.g., 3M) to build OvernightIndexedCoupons whose internal
        // sub-schedule is non-degenerate. Use the configured
        // optionletFrequency_ as the floating-leg tenor in that case.
        // (For an IborIndex this stays at the default index.tenor().)
        final boolean indexIsOvernight = iborIndex_ instanceof OvernightIndex;
        for (int i = 0; i < nOptionletTenors_; ++i) {
            final MakeCapFloor mcf = new MakeCapFloor(CapFloor.Type.Cap,
                    capFloorLengths_.get(i), iborIndex_,
                    0.04, // dummy strike
                    new Period(0, TimeUnit.Days))
                    .withPricingEngine(dummy);
            if (indexIsOvernight) {
                mcf.withTenor(optionletFrequency_);
            }
            final CapFloor temp = mcf.value();
            final FloatingRateCoupon lFRC = (FloatingRateCoupon)
                    temp.floatingLeg().get(temp.floatingLeg().size() - 1);
            optionletDates_.set(i, lFRC.fixingDate());
            optionletPaymentDates_.set(i, lFRC.date());
            optionletAccrualPeriods_.set(i, lFRC.accrualPeriod());
            optionletTimes_.set(i, dc.yearFraction(referenceDate, optionletDates_.get(i)));
            atmOptionletRate_.set(i, lFRC.indexFixing());
        }

        if (floatingSwitchStrike_) {
            double sum = 0.0;
            for (int i = 0; i < nOptionletTenors_; ++i) {
                sum += atmOptionletRate_.get(i);
            }
            switchStrike_ = sum / nOptionletTenors_;
        }

        final Handle<YieldTermStructure> discountCurve =
                (discount_ == null || discount_.empty())
                        ? iborIndex_.termStructure() : discount_;

        final double[] strikes = termVolSurface_.strikes();

        final SimpleQuote volQuote = new SimpleQuote(0.0);
        final PricingEngine capFloorEngine;
        if (volatilityType_ == VolatilityType.ShiftedLognormal) {
            // Phase 5g.5f: forward displacement_ to inner Black engine so the
            // shifted-lognormal stripping path handles negative-strike caps
            // (matches C++ optionletstripper1.cpp lines 105-109).
            capFloorEngine = new BlackCapFloorEngine(discountCurve,
                    new Handle<Quote>(volQuote), dc, displacement_);
        } else if (volatilityType_ == VolatilityType.Normal) {
            capFloorEngine = new BachelierCapFloorEngine(discountCurve,
                    new Handle<Quote>(volQuote), dc);
        } else {
            throw new UnsupportedOperationException(
                    "unknown volatility type: " + volatilityType_);
        }

        for (int j = 0; j < nStrikes_; ++j) {
            // using out-of-the-money options
            final CapFloor.Type capFloorType =
                    strikes[j] < switchStrike_ ? CapFloor.Type.Floor : CapFloor.Type.Cap;
            final org.jquantlib.instruments.Option.Type optionletType =
                    strikes[j] < switchStrike_
                            ? org.jquantlib.instruments.Option.Type.Put
                            : org.jquantlib.instruments.Option.Type.Call;

            double previousCapFloorPrice = 0.0;
            for (int i = 0; i < nOptionletTenors_; ++i) {
                capFloorVols_.set(i, j, termVolSurface_.volatility(
                        capFloorLengths_.get(i), strikes[j], true));
                volQuote.setValue(capFloorVols_.get(i, j));
                final MakeCapFloor mcf = new MakeCapFloor(capFloorType,
                        capFloorLengths_.get(i), iborIndex_, strikes[j],
                        new Period(0, TimeUnit.Days))
                        .withPricingEngine(capFloorEngine);
                if (indexIsOvernight) {
                    mcf.withTenor(optionletFrequency_);
                }
                final CapFloor capFloor = mcf.value();
                final double price = capFloor.NPV();
                capFloorPrices_.set(i, j, price);
                optionletPrices_.set(i, j, price - previousCapFloorPrice);
                previousCapFloorPrice = price;
                final double d = discountCurve.currentLink().discount(
                        optionletPaymentDates_.get(i));
                final double optionletAnnuity = optionletAccrualPeriods_.get(i) * d;
                try {
                    if (volatilityType_ == VolatilityType.ShiftedLognormal) {
                        final double stdDev = BlackFormula.blackFormulaImpliedStdDev(
                                optionletType, strikes[j], atmOptionletRate_.get(i),
                                optionletPrices_.get(i, j), optionletAnnuity,
                                optionletStDevs_.get(i, j), accuracy_, displacement_);
                        optionletStDevs_.set(i, j, stdDev);
                    } else if (volatilityType_ == VolatilityType.Normal) {
                        // Phase 5g.5b: bachelierBlackFormulaImpliedVol now ported.
                        // Mirrors C++ optionletstripper1.cpp lines 149-155:
                        //   stdDev = sqrt(tte) * bachelierBlackFormulaImpliedVol(...)
                        final double stdDev = Math.sqrt(optionletTimes_.get(i))
                                * BlackFormula.bachelierBlackFormulaImpliedVol(
                                        optionletType, strikes[j], atmOptionletRate_.get(i),
                                        optionletTimes_.get(i), optionletPrices_.get(i, j),
                                        optionletAnnuity);
                        optionletStDevs_.set(i, j, stdDev);
                    } else {
                        QL.error("Unknown volatility type: " + volatilityType_);
                    }
                } catch (final ArithmeticException e) {
                    if (dontThrow_) {
                        optionletStDevs_.set(i, j, 0.0);
                    } else {
                        QL.error("could not bootstrap optionlet:"
                                + "\n type:    " + optionletType
                                + "\n strike:  " + strikes[j]
                                + "\n atm:     " + atmOptionletRate_.get(i)
                                + "\n price:   " + optionletPrices_.get(i, j)
                                + "\n annuity: " + optionletAnnuity
                                + "\n expiry:  " + optionletDates_.get(i)
                                + "\n error:   " + e.getMessage());
                    }
                } catch (final RuntimeException e) {
                    if (dontThrow_) {
                        optionletStDevs_.set(i, j, 0.0);
                    } else {
                        throw e;
                    }
                }
                optionletVolatilities_.get(i).set(j,
                        optionletStDevs_.get(i, j) / Math.sqrt(optionletTimes_.get(i)));
            }
        }
    }

    //
    // public inspectors
    //

    public Matrix capletVols() {
        calculate();
        return capletVols_;
    }

    public Matrix capFloorPrices() {
        calculate();
        return capFloorPrices_;
    }

    public Matrix capFloorVolatilities() {
        calculate();
        return capFloorVols_;
    }

    public Matrix optionletPrices() {
        calculate();
        return optionletPrices_;
    }

    public double switchStrike() {
        if (floatingSwitchStrike_) {
            calculate();
        }
        return switchStrike_;
    }
}
