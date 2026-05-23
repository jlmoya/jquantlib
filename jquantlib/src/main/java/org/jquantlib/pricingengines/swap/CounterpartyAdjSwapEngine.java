/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2015 Jose Aparicio
*/

package org.jquantlib.pricingengines.swap;

import org.jquantlib.QL;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.MakeVanillaSwap;
import org.jquantlib.instruments.Swap;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swaption.BlackSwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Bilateral (CVA and DVA) default-adjusted vanilla-swap pricing engine.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/swap/cvaswapengine.{hpp,cpp}} (Phase 2 L3-D). Collateral and wrong-way risk are not modelled
 * (default times are taken independent of rates).
 *
 * <p>Algorithm (Sorensen-Bollier 1994 / Brigo-Masetti 2005):
 * for each remaining fixed coupon, price a forward-starting swaption (and its reverse) on the residual swap and weight
 * by the counterparty / investor marginal default probabilities; the CVA leg subtracts {@code (1-R)} times the
 * cumulative call value, the DVA leg adds {@code (1-R')} times the cumulative put value.
 *
 * <p>The investor default curve defaults to a near-zero hazard
 * {@link FlatHazardRate}{@code (1e-12)} (matches C++ default).
 */
public class CounterpartyAdjSwapEngine extends VanillaSwap.EngineImpl {

    private final Handle< PricingEngine > baseSwapEngine_;
    private final Handle< PricingEngine > swaptionletEngine_;
    private final Handle< YieldTermStructure > discountCurve_;
    private final Handle< DefaultProbabilityTermStructure > defaultTS_;
    private final double ctptyRecoveryRate_;
    private final Handle< DefaultProbabilityTermStructure > invstDTS_;
    private final double invstRecoveryRate_;

    /** Arbitrary-swaption-engine constructor. */
    public CounterpartyAdjSwapEngine(final Handle< YieldTermStructure > discountCurve,
            final Handle< PricingEngine > swaptionEngine,
            final Handle< DefaultProbabilityTermStructure > ctptyDTS, final double ctptyRecoveryRate,
            final Handle< DefaultProbabilityTermStructure > invstDTS, final double invstRecoveryRate) {
        super();
        QL.require(discountCurve != null && !discountCurve.empty(), "null discount curve");
        QL.require(ctptyDTS != null && !ctptyDTS.empty(), "null ctpty default curve");
        this.baseSwapEngine_ = new Handle< PricingEngine >(new DiscountingSwapEngine(discountCurve));
        this.swaptionletEngine_ = swaptionEngine;
        this.discountCurve_ = discountCurve;
        this.defaultTS_ = ctptyDTS;
        this.ctptyRecoveryRate_ = ctptyRecoveryRate;
        this.invstDTS_ = (invstDTS == null || invstDTS.empty())
                ? new Handle< DefaultProbabilityTermStructure >(
                        new FlatHazardRate(0, new NullCalendar(), 1e-12, ctptyDTS.currentLink().dayCounter()))
                : invstDTS;
        this.invstRecoveryRate_ = invstRecoveryRate;
        discountCurve_.addObserver(this);
        defaultTS_.addObserver(this);
        invstDTS_.addObserver(this);
        if ( swaptionletEngine_ != null ) {
            swaptionletEngine_.addObserver(this);
        }
    }

    public CounterpartyAdjSwapEngine(final Handle< YieldTermStructure > discountCurve,
            final Handle< PricingEngine > swaptionEngine,
            final Handle< DefaultProbabilityTermStructure > ctptyDTS, final double ctptyRecoveryRate) {
        this(discountCurve, swaptionEngine, ctptyDTS, ctptyRecoveryRate, null, 0.999);
    }

    /** Black-vol convenience constructor. */
    public CounterpartyAdjSwapEngine(final Handle< YieldTermStructure > discountCurve, final double blackVol,
            final Handle< DefaultProbabilityTermStructure > ctptyDTS, final double ctptyRecoveryRate,
            final Handle< DefaultProbabilityTermStructure > invstDTS, final double invstRecoveryRate) {
        this(discountCurve, new Handle< PricingEngine >(new BlackSwaptionEngine(discountCurve, blackVol)), ctptyDTS,
                ctptyRecoveryRate, invstDTS, invstRecoveryRate);
    }

    public CounterpartyAdjSwapEngine(final Handle< YieldTermStructure > discountCurve, final double blackVol,
            final Handle< DefaultProbabilityTermStructure > ctptyDTS, final double ctptyRecoveryRate) {
        this(discountCurve, blackVol, ctptyDTS, ctptyRecoveryRate, null, 0.999);
    }

    /**
     * Black-vol-as-quote convenience factory.
     *
     * <p>Java port note: the C++ ctor overload taking {@code Handle<Quote>}
     * collides with {@code Handle<PricingEngine>} under Java's type erasure, so it is exposed as a static factory
     * instead of a constructor.
     */
    public static CounterpartyAdjSwapEngine fromBlackVolQuote(final Handle< YieldTermStructure > discountCurve,
            final Handle< Quote > blackVol, final Handle< DefaultProbabilityTermStructure > ctptyDTS,
            final double ctptyRecoveryRate, final Handle< DefaultProbabilityTermStructure > invstDTS,
            final double invstRecoveryRate) {
        // BlackSwaptionEngine in Java doesn't accept a Handle<Quote> directly; we resolve the quote value once.
        final double vol = blackVol.currentLink().value();
        final CounterpartyAdjSwapEngine e = new CounterpartyAdjSwapEngine(discountCurve, vol, ctptyDTS,
                ctptyRecoveryRate, invstDTS, invstRecoveryRate);
        blackVol.addObserver(e);
        return e;
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        QL.require(discountCurve_ != null && !discountCurve_.empty(), "no discount term structure set");
        QL.require(defaultTS_ != null && !defaultTS_.empty(), "no ctpty default term structure set");
        QL.require(swaptionletEngine_ != null && !swaptionletEngine_.empty(), "no swap option engine set");

        final VanillaSwap.ArgumentsImpl args = (VanillaSwap.ArgumentsImpl) arguments_;
        final VanillaSwap.ResultsImpl results = (VanillaSwap.ResultsImpl) results_;
        QL.require(!Double.isNaN(args.nominal), "non-constant nominals are not supported yet");

        final Date priceDate = defaultTS_.currentLink().referenceDate();

        double cumOptVal = 0.0;
        double cumPutVal = 0.0;

        // skip past fixed-pay dates
        int nextFD = 0;
        Date swapletStart = priceDate;
        while ( nextFD < args.fixedPayDates.size() && args.fixedPayDates.get(nextFD).lt(priceDate) ) {
            nextFD++;
        }

        // Compute fair spread for strike value by pricing the underlying swap with the no-CVA engine.
        final PricingEngine baseEngine = baseSwapEngine_.currentLink();
        final Swap.ArgumentsImpl noCVAArgs = (Swap.ArgumentsImpl) baseEngine.getArguments();
        QL.require(noCVAArgs != null, "wrong argument type");
        noCVAArgs.legs = args.legs;
        noCVAArgs.payer = args.payer;
        baseEngine.calculate();

        final FixedRateCoupon coupon;
        try {
            coupon = (FixedRateCoupon) args.legs.get(0).get(0);
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("dynamic cast of fixed leg coupon failed.");
        }
        final double baseSwapRate = coupon.rate();
        final Swap.ResultsImpl vSResults = (Swap.ResultsImpl) baseEngine.getResults();
        QL.require(vSResults != null, "wrong result type");

        final double baseSwapFairRate = -baseSwapRate * vSResults.legNPV[1] / vSResults.legNPV[0];
        final double baseSwapNPV = vSResults.value;

        final VanillaSwap.Type reversedType = (args.type == VanillaSwap.Type.Payer) ? VanillaSwap.Type.Receiver
                : VanillaSwap.Type.Payer;

        // Swaplet options summatory
        while ( nextFD < args.fixedPayDates.size() ) {
            final FloatingRateCoupon floatCoupon;
            try {
                floatCoupon = (FloatingRateCoupon) args.legs.get(1).get(0);
            } catch ( final ClassCastException e ) {
                throw new RuntimeException("dynamic cast of floating leg coupon failed.");
            }
            final IborIndex swapIndex;
            try {
                swapIndex = (IborIndex) floatCoupon.index();
            } catch ( final ClassCastException e ) {
                throw new RuntimeException("dynamic cast of floating leg index failed.");
            }

            final Date lastFD = args.fixedPayDates.get(args.fixedPayDates.size() - 1);
            final Period baseSwapsTenor = new Period(
                    (int) (lastFD.serialNumber() - swapletStart.serialNumber()), TimeUnit.Days);

            final VanillaSwap swaplet = new MakeVanillaSwap(baseSwapsTenor, swapIndex, baseSwapFairRate)
                    .withType(args.type)
                    .withNominal(args.nominal)
                    .withEffectiveDate(swapletStart)
                    .withTerminationDate(lastFD)
                    .value();
            final VanillaSwap revSwaplet = new MakeVanillaSwap(baseSwapsTenor, swapIndex, baseSwapFairRate)
                    .withType(reversedType)
                    .withNominal(args.nominal)
                    .withEffectiveDate(swapletStart)
                    .withTerminationDate(lastFD)
                    .value();

            final Swaption swaptionlet = new Swaption(swaplet, new EuropeanExercise(swapletStart));
            final Swaption putSwaplet = new Swaption(revSwaplet, new EuropeanExercise(swapletStart));
            swaptionlet.setPricingEngine(swaptionletEngine_.currentLink());
            putSwaplet.setPricingEngine(swaptionletEngine_.currentLink());

            // atm underlying swap => value(put) == value(call); however we keep them separate to mirror C++.
            cumOptVal += swaptionlet.NPV()
                    * defaultTS_.currentLink().defaultProbability(swapletStart, args.fixedPayDates.get(nextFD));
            cumPutVal += putSwaplet.NPV()
                    * invstDTS_.currentLink().defaultProbability(swapletStart, args.fixedPayDates.get(nextFD));

            swapletStart = args.fixedPayDates.get(nextFD);
            nextFD++;
        }

        results.value = baseSwapNPV - (1.0 - ctptyRecoveryRate_) * cumOptVal
                + (1.0 - invstRecoveryRate_) * cumPutVal;
        results.fairRate = -baseSwapRate
                * (vSResults.legNPV[1] - (1.0 - ctptyRecoveryRate_) * cumOptVal
                        + (1.0 - invstRecoveryRate_) * cumPutVal)
                / vSResults.legNPV[0];
    }
}
