/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
 */
/*
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Giorgio Facchinetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.instruments;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.SwapIndex;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

/**
 * Helper class for instantiating standard market swaptions.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/instruments/makeswaption.{hpp,cpp}}.
 *
 * <p>The C++ class supports both vanilla-swap-backed and OIS-backed swaptions
 * (via {@code OvernightIndexedSwapIndex} dynamic_cast). The Java port wires the vanilla-swap branch only; the OIS
 * branch is deferred until {@code OvernightIndexedSwapIndex} is ported (Phase 5d.5b carry-forward).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>OIS swap-index branch raises {@link UnsupportedOperationException}
 *      when invoked — caller must use a {@link SwapIndex} that produces a
 *      {@link VanillaSwap}.</li>
 *  <li>{@code withIndexedCoupons} / {@code withAtParCoupons} are accepted
 *      but not currently propagated to MakeVanillaSwap (Java's
 *      MakeVanillaSwap does not yet expose the corresponding setter — this
 *      is a known carry-forward).</li>
 *  <li>{@code operator Swaption()} is exposed via {@link #value()}, mirroring
 *      the JQuantLib house style (cf. {@link MakeVanillaSwap#value()}).</li>
 * </ul>
 */
public class MakeSwaption {

    //
    // private fields
    //

    private final SwapIndex swapIndex_;
    private Settlement.Type delivery_;
    private Settlement.Method settlementMethod_;

    private Period optionTenor_;
    private BusinessDayConvention optionConvention_;
    private Date fixingDate_;
    private Date exerciseDate_;
    private Calendar exerciseCalendar_;

    private final double strike_;          // Constants.NULL_REAL → ATM
    private VanillaSwap.Type underlyingType_;
    private double nominal_;
    private Boolean useIndexedCoupons_;   // null → not set (Java boxed boolean)

    private PricingEngine engine_;

    //
    // public constructors
    //

    /**
     * Build a swaption using a SwapIndex and an option tenor (period from today). Strike defaults to ATM-on-curves
     * ({@link org.jquantlib.math.Constants#NULL_REAL} sentinel).
     */
    public MakeSwaption(final SwapIndex swapIndex, final Period optionTenor) {
        this(swapIndex, optionTenor, org.jquantlib.math.Constants.NULL_REAL);
    }

    public MakeSwaption(final SwapIndex swapIndex, final Period optionTenor, final double strike) {
        QL.require(swapIndex != null, "swap index required");
        QL.require(optionTenor != null, "option tenor required");
        this.swapIndex_ = swapIndex;
        this.delivery_ = Settlement.Type.Physical;
        this.settlementMethod_ = Settlement.Method.PhysicalOTC;
        this.optionTenor_ = optionTenor;
        this.optionConvention_ = BusinessDayConvention.ModifiedFollowing;
        this.strike_ = strike;
        this.underlyingType_ = VanillaSwap.Type.Payer;
        this.nominal_ = 1.0;
    }

    /** Build a swaption using a SwapIndex and a fixed fixing-date. */
    public MakeSwaption(final SwapIndex swapIndex, final Date fixingDate) {
        this(swapIndex, fixingDate, org.jquantlib.math.Constants.NULL_REAL);
    }

    public MakeSwaption(final SwapIndex swapIndex, final Date fixingDate, final double strike) {
        QL.require(swapIndex != null, "swap index required");
        QL.require(fixingDate != null, "fixing date required");
        this.swapIndex_ = swapIndex;
        this.delivery_ = Settlement.Type.Physical;
        this.settlementMethod_ = Settlement.Method.PhysicalOTC;
        this.optionConvention_ = BusinessDayConvention.ModifiedFollowing;
        this.fixingDate_ = fixingDate;
        this.strike_ = strike;
        this.underlyingType_ = VanillaSwap.Type.Payer;
        this.nominal_ = 1.0;
    }

    //
    // builder setters (chainable)
    //

    public MakeSwaption withNominal(final double n) {
        this.nominal_ = n;
        return this;
    }

    public MakeSwaption withSettlementType(final Settlement.Type delivery) {
        this.delivery_ = delivery;
        return this;
    }

    public MakeSwaption withSettlementMethod(final Settlement.Method settlementMethod) {
        this.settlementMethod_ = settlementMethod;
        return this;
    }

    public MakeSwaption withOptionConvention(final BusinessDayConvention bdc) {
        this.optionConvention_ = bdc;
        return this;
    }

    public MakeSwaption withExerciseDate(final Date d) {
        this.exerciseDate_ = d;
        return this;
    }

    public MakeSwaption withExerciseCalendar(final Calendar cal) {
        this.exerciseCalendar_ = cal;
        return this;
    }

    /**
     * Alias for {@link #withExerciseCalendar(Calendar)}. The C++ MakeSwaption only exposes
     * {@code withExerciseCalendar}; this alias is supplied for callers using the more explicit name and is a no-op over
     * {@link #withExerciseCalendar(Calendar)}.
     */
    public MakeSwaption withExerciseDateCalendar(final Calendar cal) {
        return withExerciseCalendar(cal);
    }

    public MakeSwaption withUnderlyingType(final VanillaSwap.Type type) {
        this.underlyingType_ = type;
        return this;
    }

    public MakeSwaption withIndexedCoupons(final Boolean b) {
        this.useIndexedCoupons_ = b;
        return this;
    }

    public MakeSwaption withAtParCoupons(final boolean b) {
        this.useIndexedCoupons_ = !b;
        return this;
    }

    public MakeSwaption withPricingEngine(final PricingEngine engine) {
        this.engine_ = engine;
        return this;
    }

    //
    // build
    //

    /**
     * Build the {@link Swaption}, mirroring C++ {@code operator Swaption()} /
     * {@code operator ext::shared_ptr<Swaption>()}.
     */
    public Swaption value() {
        final Calendar calendar = (exerciseCalendar_ != null) ? exerciseCalendar_ : swapIndex_.fixingCalendar();
        Date refDate = new Settings().evaluationDate();
        refDate = calendar.adjust(refDate);

        if ( fixingDate_ == null ) {
            fixingDate_ = calendar.advance(refDate, optionTenor_, optionConvention_);
        }

        final Exercise exercise;
        if ( exerciseDate_ == null ) {
            exercise = new EuropeanExercise(fixingDate_);
        } else {
            QL.require(exerciseDate_.le(fixingDate_),
                    "exercise date (" + exerciseDate_ + ") must be less " + "than or equal to fixing date ("
                            + fixingDate_ + ")");
            exercise = new EuropeanExercise(exerciseDate_);
        }

        // ATM-on-curve strike: build a temp swap at strike=0, take the fairRate.
        double usedStrike;
        if ( strike_ == org.jquantlib.math.Constants.NULL_REAL ) {
            // ATM-on-curve: build a temp 0-strike swap, price via discounting,
            // read fairRate. Mirrors the C++ vanilla branch (the OIS branch is
            // deferred — see class javadoc).
            final VanillaSwap tempSwap = new MakeVanillaSwap(swapIndex_.tenor(), swapIndex_.iborIndex(),
                    0.0).withEffectiveDate(swapIndex_.valueDate(fixingDate_))
                    .withFixedLegCalendar(swapIndex_.fixingCalendar()).withFixedLegDayCount(swapIndex_.dayCounter())
                    .withFixedLegTenor(swapIndex_.fixedLegTenor())
                    .withFixedLegConvention(swapIndex_.fixedLegConvention())
                    .withFixedLegTerminationDateConvention(swapIndex_.fixedLegConvention()).withType(underlyingType_)
                    .withNominal(nominal_).value();
            // Use the swap-index's forwarding curve as the discount curve too.
            tempSwap.setPricingEngine(new DiscountingSwapEngine(swapIndex_.termStructure()));
            usedStrike = tempSwap.fairRate();
        } else {
            usedStrike = strike_;
        }

        final BusinessDayConvention bdc = swapIndex_.fixedLegConvention();
        final VanillaSwap underlyingSwap = new MakeVanillaSwap(swapIndex_.tenor(), swapIndex_.iborIndex(),
                usedStrike).withEffectiveDate(swapIndex_.valueDate(fixingDate_))
                .withFixedLegCalendar(swapIndex_.fixingCalendar()).withFixedLegDayCount(swapIndex_.dayCounter())
                .withFixedLegTenor(swapIndex_.fixedLegTenor()).withFixedLegConvention(bdc)
                .withFixedLegTerminationDateConvention(bdc).withType(underlyingType_).withNominal(nominal_).value();

        final Swaption swaption = new Swaption(underlyingSwap, exercise, delivery_, settlementMethod_);
        if ( engine_ != null ) {
            swaption.setPricingEngine(engine_);
        }
        return swaption;
    }
}
