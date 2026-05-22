/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.model.shortrate.calibrationhelpers;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.math.Constants;
import org.jquantlib.model.BlackCalibrationHelper;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swaption.BlackSwaptionEngine;
import org.jquantlib.pricingengines.swaption.DiscretizedSwaption;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.swaption.ConstantSwaptionVolatility;
import org.jquantlib.time.*;
import org.jquantlib.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.List;

/**
 * Swaption calibration helper.
 * <p>
 * Port of C++ v1.42.1 {@code ql/models/shortrate/calibrationhelpers/swaptionhelper.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>Only the {@link VanillaSwap} (ibor-index) underlying is supported. C++
 *     also dispatches on {@code OvernightIndex} for an
 *     {@code OvernightIndexedSwap}; the OIS path is deferred until
 *     {@code OvernightIndexedSwap} is ported. Passing an overnight index
 *     would produce a meaningless model price (mirrors C++ warning that
 *     using an OIS with a model-based engine is at best a decent proxy).
 * <li>{@code RateAveraging::Type} parameter is omitted (only meaningful for
 *     OIS).
 * <li>The C++ {@code blackPrice} dispatches on {@code volatilityType_}:
 *     ShiftedLognormal → BlackSwaptionEngine, Normal → BachelierSwaptionEngine.
 *     Java currently only ports BlackSwaptionEngine; Normal is rejected with
 *     an {@link UnsupportedOperationException} (mirrors CapHelper).
 * <li>{@code shift_} is captured on the helper but unused — the Java
 *     {@code BlackSwaptionEngine} doesn't yet take a displacement ctor
 *     argument. All current call-sites pass {@code shift = 0.0} so the
 *     value is preserved exactly.
 * </ul>
 */
public class SwaptionHelper extends BlackCalibrationHelper {

    private final Period maturity_;
    private final Period length_;
    private final Period fixedLegTenor_;
    private final IborIndex index_;
    private final Handle< YieldTermStructure > termStructure_;
    private final DayCounter fixedLegDayCounter_;
    private final DayCounter floatingLegDayCounter_;
    private final double strike_;
    private final double nominal_;
    private final Date exerciseDate_;
    private final Date endDate_;
    /** Result of performCalculations(). Nullable until calculate() runs. */
    private VanillaSwap swap_;
    private Swaption swaption_;
    private Exercise exercise_;
    private double exerciseRate_;

    /**
     * Period-based ctor: maturity + length. Mirrors the first C++ ctor.
     */
    public SwaptionHelper(final Period maturity, final Period length, final Handle< Quote > volatility,
            final IborIndex index, final Period fixedLegTenor, final DayCounter fixedLegDayCounter,
            final DayCounter floatingLegDayCounter, final Handle< YieldTermStructure > termStructure,
            final CalibrationErrorType errorType, final double strike, final double nominal,
            final VolatilityType volType, final double shift) {
        super(volatility, errorType, volType, shift);
        this.maturity_ = maturity;
        this.length_ = length;
        this.fixedLegTenor_ = fixedLegTenor;
        this.index_ = index;
        this.termStructure_ = termStructure;
        this.fixedLegDayCounter_ = fixedLegDayCounter;
        this.floatingLegDayCounter_ = floatingLegDayCounter;
        this.strike_ = strike;
        this.nominal_ = nominal;
        this.exerciseDate_ = null;
        this.endDate_ = null;

        if ( this.index_ != null ) {
            this.index_.addObserver(this);
        }
        if ( this.termStructure_ != null ) {
            this.termStructure_.addObserver(this);
        }
    }

    /**
     * Convenience ctor with sensible defaults (RelativePriceError, ShiftedLognormal, no strike, nominal 1.0, shift 0).
     */
    public SwaptionHelper(final Period maturity, final Period length, final Handle< Quote > volatility,
            final IborIndex index, final Period fixedLegTenor, final DayCounter fixedLegDayCounter,
            final DayCounter floatingLegDayCounter, final Handle< YieldTermStructure > termStructure) {
        this(maturity, length, volatility, index, fixedLegTenor, fixedLegDayCounter, floatingLegDayCounter,
                termStructure, CalibrationErrorType.RelativePriceError, Constants.NULL_REAL, 1.0,
                VolatilityType.ShiftedLognormal, 0.0);
    }

    /**
     * Date-based ctor (exerciseDate + length). Mirrors the second C++ ctor.
     */
    public SwaptionHelper(final Date exerciseDate, final Period length, final Handle< Quote > volatility,
            final IborIndex index, final Period fixedLegTenor, final DayCounter fixedLegDayCounter,
            final DayCounter floatingLegDayCounter, final Handle< YieldTermStructure > termStructure,
            final CalibrationErrorType errorType, final double strike, final double nominal,
            final VolatilityType volType, final double shift) {
        super(volatility, errorType, volType, shift);
        this.maturity_ = new Period(0, TimeUnit.Days);
        this.length_ = length;
        this.fixedLegTenor_ = fixedLegTenor;
        this.index_ = index;
        this.termStructure_ = termStructure;
        this.fixedLegDayCounter_ = fixedLegDayCounter;
        this.floatingLegDayCounter_ = floatingLegDayCounter;
        this.strike_ = strike;
        this.nominal_ = nominal;
        this.exerciseDate_ = exerciseDate;
        this.endDate_ = null;

        if ( this.index_ != null ) {
            this.index_.addObserver(this);
        }
        if ( this.termStructure_ != null ) {
            this.termStructure_.addObserver(this);
        }
    }

    /**
     * Date-based ctor (exerciseDate + endDate). Mirrors the third C++ ctor.
     */
    public SwaptionHelper(final Date exerciseDate, final Date endDate, final Handle< Quote > volatility,
            final IborIndex index, final Period fixedLegTenor, final DayCounter fixedLegDayCounter,
            final DayCounter floatingLegDayCounter, final Handle< YieldTermStructure > termStructure,
            final CalibrationErrorType errorType, final double strike, final double nominal,
            final VolatilityType volType, final double shift) {
        super(volatility, errorType, volType, shift);
        this.maturity_ = new Period(0, TimeUnit.Days);
        this.length_ = new Period(0, TimeUnit.Days);
        this.fixedLegTenor_ = fixedLegTenor;
        this.index_ = index;
        this.termStructure_ = termStructure;
        this.fixedLegDayCounter_ = fixedLegDayCounter;
        this.floatingLegDayCounter_ = floatingLegDayCounter;
        this.strike_ = strike;
        this.nominal_ = nominal;
        this.exerciseDate_ = exerciseDate;
        this.endDate_ = endDate;

        if ( this.index_ != null ) {
            this.index_.addObserver(this);
        }
        if ( this.termStructure_ != null ) {
            this.termStructure_.addObserver(this);
        }
    }

    //
    // public inspectors
    //

    public VanillaSwap underlying() {
        calculate();
        return swap_;
    }

    public Swaption swaption() {
        calculate();
        return swaption_;
    }

    //
    // overrides BlackCalibrationHelper / LazyObject
    //

    @Override
    protected void performCalculations() {
        final Calendar calendar = index_.fixingCalendar();
        Date exerciseDate = exerciseDate_;
        if ( exerciseDate == null ) {
            exerciseDate = calendar.advance(termStructure_.currentLink().referenceDate(), maturity_,
                    index_.businessDayConvention());
        }
        // C++ uses settlementDays_ (Null<Size>() default → fall through to
        // index.valueDate(...)). Java doesn't expose a settlement-days override
        // on the helper — always use the index value-date path.
        final Date startDate = index_.valueDate(
                index_.fixingCalendar().adjust(exerciseDate, BusinessDayConvention.Following));

        Date endDate = endDate_;
        if ( endDate == null ) {
            endDate = calendar.advance(startDate, length_, index_.businessDayConvention());
        }
        final Schedule fixedSchedule = new Schedule(startDate, endDate, fixedLegTenor_, calendar,
                index_.businessDayConvention(), index_.businessDayConvention(), DateGeneration.Rule.Forward, false);
        final Schedule floatSchedule = new Schedule(startDate, endDate, index_.tenor(), calendar,
                index_.businessDayConvention(), index_.businessDayConvention(), DateGeneration.Rule.Forward, false);

        final DiscountingSwapEngine swapEngine = new DiscountingSwapEngine(termStructure_);
        // Default to Receiver (mirrors C++).
        VanillaSwap.Type type = VanillaSwap.Type.Receiver;
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final VanillaSwap temp = makeSwap(fixedSchedule, floatSchedule, 0.0, VanillaSwap.Type.Receiver);
        temp.setPricingEngine(swapEngine);
        final double forward = temp.fairRate();
        if ( strike_ == Constants.NULL_REAL ) {
            this.exerciseRate_ = forward;
        } else {
            this.exerciseRate_ = strike_;
            type = (strike_ <= forward) ? VanillaSwap.Type.Receiver : VanillaSwap.Type.Payer;
        }

        this.swap_ = makeSwap(fixedSchedule, floatSchedule, exerciseRate_, type);
        this.swap_.setPricingEngine(swapEngine);
        this.exercise_ = exercise;
        this.swaption_ = new Swaption(this.swap_, exercise);

        super.performCalculations();
    }

    @Override
    public void addTimesTo(final ArrayList< Time > times) {
        calculate();
        // Compute mandatory times for parity with C++ (validates
        // DiscretizedSwaption construction); we don't push the doubles into
        // the caller's ArrayList<Time> because @Time is an annotation type,
        // not a numeric wrapper — the abstract signature in
        // BlackCalibrationHelper / CapHelper inherits this Java-port quirk.
        // Consumers who need the raw mandatory times should call
        // mandatoryTimes() via the helper directly (deferred follow-up).
        final Swaption.ArgumentsImpl args = new Swaption.ArgumentsImpl();
        // Populate manually (Swaption.setupArguments is protected; we are
        // outside org.jquantlib.instruments). Fields are public on
        // Swaption.ArgumentsImpl by design.
        args.swap = swap_;
        args.exercise = exercise_;
        args.settlementType = swaption_.settlementType();
        args.settlementMethod = swaption_.settlementMethod();
        final List< Double > swTimes = new DiscretizedSwaption(args, termStructure_.currentLink().referenceDate(),
                termStructure_.currentLink().dayCounter()).mandatoryTimes();
        assert swTimes != null;
    }

    @Override
    public double modelValue() {
        calculate();
        swaption_.setPricingEngine(engine_);
        return swaption_.NPV();
    }

    @Override
    public double blackPrice(final double sigma) {
        calculate();
        final Handle< Quote > vol = new Handle< Quote >(new SimpleQuote(sigma));
        // Phase 2f WI-2: Java collapses Black76 and Bachelier into a single
        // BlackSwaptionEngine that branches on vol.volatilityType(). The
        // helper builds a ConstantSwaptionVolatility carrying the right
        // type/shift so the engine takes the matching path. This mirrors C++
        // {@code SwaptionHelper::blackPrice} which constructs either a
        // BlackSwaptionEngine or a BachelierSwaptionEngine depending on the
        // helper's volatilityType_.
        final Handle< SwaptionVolatilityStructure > volSurface = switch (volatilityType_) {
            case ShiftedLognormal -> new Handle< SwaptionVolatilityStructure >(
                    new ConstantSwaptionVolatility(0, new NullCalendar(), BusinessDayConvention.Following, vol,
                            new Actual365Fixed(), VolatilityType.ShiftedLognormal, shift_));
            case Normal -> new Handle< SwaptionVolatilityStructure >(
                    new ConstantSwaptionVolatility(0, new NullCalendar(), BusinessDayConvention.Following, vol,
                            new Actual365Fixed(), VolatilityType.Normal, 0.0));
            default -> throw new IllegalStateException("unknown volatility type");
        };
        final PricingEngine engine = new BlackSwaptionEngine(termStructure_, volSurface);
        swaption_.setPricingEngine(engine);
        final double value = swaption_.NPV();
        // Restore the model-based engine.
        if ( engine_ != null ) {
            swaption_.setPricingEngine(engine_);
        }
        return value;
    }

    //
    // private helpers
    //

    private VanillaSwap makeSwap(final Schedule fixedSchedule, final Schedule floatSchedule, final double exerciseRate,
            final VanillaSwap.Type type) {
        return new VanillaSwap(type, nominal_, fixedSchedule, exerciseRate, fixedLegDayCounter_, floatSchedule, index_,
                0.0, floatingLegDayCounter_);
    }
}
