/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2005 Klaus Spanderen
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.model.equity;

import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.lang.annotation.Time;
import org.jquantlib.model.BlackCalibrationHelper;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;

import java.util.ArrayList;

/**
 * Heston-model calibration helper.
 * <p>
 * Java port of v1.42.1 {@code ql/models/equity/hestonmodelhelper.{hpp,cpp}}.
 * <p>
 * Wraps a vanilla European call/put used as a market reference during Heston/Bates calibration. The C++ implementation
 * auto-selects the option type so the chosen leg is in-the-money under the discounted-forward comparison
 * ({@code strike * P_r >= S0 * P_q ? Call : Put}). The Java port mirrors this exactly.
 *
 * <p>Phase 4n.5d port (Phase 5h.5-Bates-c carry-forward) — required by
 * {@link BatesModel#calibrate} via {@link org.jquantlib.testsuite.model.equity.BatesModelTest#testDAXCalibration}.
 */
public class HestonModelHelper extends BlackCalibrationHelper {

    private final Period maturity_;
    private final Calendar calendar_;
    private final Handle< Quote > s0_;
    private final double strikePrice_;
    private final Handle< YieldTermStructure > riskFreeRate_;
    private final Handle< YieldTermStructure > dividendYield_;

    private Date exerciseDate_;
    private double tau_;
    private Option.Type type_;
    private EuropeanOption option_;

    /**
     * Convenience overload taking a raw spot value (wrapped in a {@link SimpleQuote}). Mirrors the first C++ ctor.
     */
    public HestonModelHelper(final Period maturity, final Calendar calendar, final double s0, final double strikePrice,
            final Handle< Quote > volatility, final Handle< YieldTermStructure > riskFreeRate,
            final Handle< YieldTermStructure > dividendYield, final CalibrationErrorType errorType) {
        this(maturity, calendar, new Handle< Quote >(new SimpleQuote(s0)), strikePrice, volatility, riskFreeRate,
                dividendYield, errorType);
    }

    /**
     * Full ctor — mirrors the second C++ ctor verbatim. Registers {@code s0}, {@code riskFreeRate}, and
     * {@code dividendYield} as observables so the helper invalidates whenever any market quote moves.
     */
    public HestonModelHelper(final Period maturity, final Calendar calendar, final Handle< Quote > s0,
            final double strikePrice, final Handle< Quote > volatility, final Handle< YieldTermStructure > riskFreeRate,
            final Handle< YieldTermStructure > dividendYield, final CalibrationErrorType errorType) {
        super(volatility, errorType, VolatilityType.ShiftedLognormal, 0.0);
        this.maturity_ = maturity;
        this.calendar_ = calendar;
        this.s0_ = s0;
        this.strikePrice_ = strikePrice;
        this.riskFreeRate_ = riskFreeRate;
        this.dividendYield_ = dividendYield;
        s0.addObserver(this);
        riskFreeRate.addObserver(this);
        dividendYield.addObserver(this);
    }

    /** Default ctor — RelativePriceError error type. */
    public HestonModelHelper(final Period maturity, final Calendar calendar, final double s0, final double strikePrice,
            final Handle< Quote > volatility, final Handle< YieldTermStructure > riskFreeRate,
            final Handle< YieldTermStructure > dividendYield) {
        this(maturity, calendar, s0, strikePrice, volatility, riskFreeRate, dividendYield,
                CalibrationErrorType.RelativePriceError);
    }

    @Override
    protected void performCalculations() {
        exerciseDate_ = calendar_.advance(riskFreeRate_.currentLink().referenceDate(), maturity_);
        tau_ = riskFreeRate_.currentLink().timeFromReference(exerciseDate_);
        // Mirror C++: Call iff strike*P_r >= s0*P_q (in-the-money side).
        type_ = (strikePrice_ * riskFreeRate_.currentLink().discount(tau_)
                >= s0_.currentLink().value() * dividendYield_.currentLink().discount(tau_))
                ? Option.Type.Call
                : Option.Type.Put;
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(type_, strikePrice_);
        final Exercise exercise = new EuropeanExercise(exerciseDate_);
        option_ = new EuropeanOption(payoff, exercise);
        super.performCalculations();
    }

    @Override
    public double modelValue() {
        calculate();
        option_.setPricingEngine(engine_);
        return option_.NPV();
    }

    @Override
    public double blackPrice(final double volatility) {
        calculate();
        final double stdDev = volatility * Math.sqrt(maturity());
        return BlackFormula.blackFormula(type_, strikePrice_ * riskFreeRate_.currentLink().discount(tau_),
                s0_.currentLink().value() * dividendYield_.currentLink().discount(tau_), stdDev);
    }

    /** Maturity in years (year-fraction from reference under YTS day count). */
    public double maturity() {
        calculate();
        return tau_;
    }

    @Override
    public void addTimesTo(final ArrayList< Time > times) {
        // Mirror C++ HestonModelHelper::addTimesTo — empty body.
    }
}
